/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "ExecutionBuilder"

#include "ExecutionBuilder.h"

#include <ControlFlow.h>
#include <CpuExecutor.h>
#include <LegacyUtils.h>
#include <Tracing.h>
#include <android-base/logging.h>
#include <nnapi/IBurst.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Types.h>

#include <algorithm>
#include <limits>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <tuple>
#include <utility>
#include <vector>

#include "BurstBuilder.h"
#include "CompilationBuilder.h"
#include "Manager.h"
#include "ModelArgumentInfo.h"
#include "ModelBuilder.h"
#include "Telemetry.h"
#include "TypeManager.h"

namespace android {
namespace nn {

// Partial validation of output shapes returned from driver, to ensure they
// conform to a very specific set of rules.
static bool validateOutputShapesFromDriver(ErrorStatus executionStatus, const ModelBuilder* model,
                                           const std::vector<OutputShape>& shapes) {
    // Enforces the following rules (some of which are from b/154054474):
    // - shapes vector is empty except in the case of NONE or OUTPUT_INSUFFICIENT_SIZE.
    //   If the vector is not empty, it must have as many entries as the step model has outputs.
    // - If NONE, then either shapes vector is empty, or every shape is
    //   marked isSufficient and, if a tensor, has known rank.
    // - If OUTPUT_INSUFFICIENT_SIZE, then the vector is not empty.  At least one entry
    //   is marked !isSufficient.
    switch (executionStatus) {
        case ErrorStatus::NONE: {
            NN_RET_CHECK(shapes.size() == 0 || shapes.size() == model->outputCount())
                    << "With execution ErrorStatus " << executionStatus
                    << " output shapes vector must be empty or of length " << model->outputCount()
                    << " but has length " << shapes.size();
            NN_RET_CHECK(std::all_of(shapes.begin(), shapes.end(),
                                     [](const OutputShape& shape) { return shape.isSufficient; }))
                    << "With execution ErrorStatus " << executionStatus
                    << " at least one output shape is unexpectedly marked !isSufficient";

            const TypeManager* tm = TypeManager::get();
            for (uint32_t outputIndex = 0, outputCount = shapes.size(); outputIndex < outputCount;
                 ++outputIndex) {
                const Operand& outputOperand = model->getOutputOperand(outputIndex);
                NN_RET_CHECK(!tm->isTensorType(outputOperand.type) ||
                             (shapes[outputIndex].dimensions.size() != 0))
                        << "With execution ErrorStatus " << executionStatus << " output#"
                        << outputIndex << " shape unexpectedly has zero rank";
            }

            break;
        }
        case ErrorStatus::OUTPUT_INSUFFICIENT_SIZE: {
            NN_RET_CHECK(shapes.size() == model->outputCount())
                    << "With execution ErrorStatus " << executionStatus
                    << " output shapes vector must be of length " << model->outputCount()
                    << " but has length " << shapes.size();
            NN_RET_CHECK(std::any_of(shapes.begin(), shapes.end(),
                                     [](const OutputShape& shape) { return !shape.isSufficient; }))
                    << "With execution ErrorStatus " << executionStatus
                    << " at least one output shape must have been marked !isSufficient";
            break;
        }
        default: {
            NN_RET_CHECK(shapes.size() == 0)
                    << "With execution ErrorStatus " << executionStatus
                    << " output shapes vector must be empty but has length " << shapes.size();
            break;
        }
    }
    return true;
}
static bool validateOutputShapesFromDriver(int executionResultCode, const ModelBuilder* model,
                                           const std::vector<OutputShape>& shapes) {
    return validateOutputShapesFromDriver(convertResultCodeToErrorStatus(executionResultCode),
                                          model, shapes);
}

static MeasureTiming measureTiming(const ExecutionBuilder* execution) {
    return execution->measureTiming() ? MeasureTiming::YES : MeasureTiming::NO;
}

static bool checkDimensionInfo(const Operand& operand, const ANeuralNetworksOperandType* newType,
                               const char* tag, bool allowUnspecified) {
    if (newType != nullptr) {
        const Extension::OperandTypeInformation* info = nullptr;
        if (isExtension(operand.type)) {
            NN_RET_CHECK(TypeManager::get()->getExtensionOperandTypeInfo(operand.type, &info));
        }
        if (validateOperandType(*newType, info, tag, allowUnspecified) !=
            ANEURALNETWORKS_NO_ERROR) {
            LOG(ERROR) << tag << ": Invalid newType";
            return false;
        }
        if (operand.dimensions.size() == 0) {
            return true;
        }
        if (operand.dimensions.size() != newType->dimensionCount) {
            LOG(ERROR) << tag << ": Setting with incompatible dimension count (existing = "
                       << operand.dimensions.size() << ", new = " << newType->dimensionCount << ")";
            return false;
        }
        for (uint32_t i = 0; i < newType->dimensionCount; i++) {
            if (operand.dimensions[i] != newType->dimensions[i] && operand.dimensions[i] != 0) {
                LOG(ERROR) << tag << ": Overriding a fully specified dimension is disallowed";
                return false;
            }
        }
    } else {
        if (!allowUnspecified && TypeManager::get()->isTensorType(operand.type) &&
            tensorHasUnspecifiedDimensions(operand)) {
            LOG(ERROR) << tag << ": Setting with operand type that is not fully specified";
            return false;
        }
    }
    return true;
}

ExecutionBuilder::ExecutionBuilder(const CompilationBuilder* compilation)
    : mCompilation(compilation),
      mModel(compilation->mModel),
      mPlan(&compilation->mPlan),
      mAllowCpuFallback(DeviceManager::partitioningAllowsFallback(compilation->mPartitioning)),
      mInputs(mModel->inputCount()),
      mOutputs(mModel->outputCount()) {
    VLOG(EXECUTION) << "ExecutionBuilder::ExecutionBuilder with " << mInputs.size()
                    << " inputs and " << mOutputs.size() << " outputs";
}

SimpleExecutionBuilder::SimpleExecutionBuilder(const CompilationBuilder* compilation)
    : ExecutionBuilder(compilation) {
    CHECK(mPlan->isSimple());
}

CompoundExecutionBuilder::CompoundExecutionBuilder(const CompilationBuilder* compilation)
    : ExecutionBuilder(compilation) {
    CHECK(mPlan->isCompound());
}

const ModelBuilder* ExecutionBuilder::getSourceModel(uint32_t index) const {
    return mPlan->getSourceModels().getModel(index);
}

int ExecutionBuilder::setInput(uint32_t index, const ANeuralNetworksOperandType* type,
                               const void* buffer, size_t length) {
    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInput called after the "
                      "execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    uint32_t count = static_cast<uint32_t>(mInputs.size());
    if (index >= count) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInput bad index " << index << " " << count;
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (!checkDimensionInfo(mModel->getInputOperand(index), type,
                            "ANeuralNetworksExecution_setInput", buffer == nullptr)) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (length > 0xFFFFFFFF) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInput input exceeds max length " << length;
        return ANEURALNETWORKS_BAD_DATA;
    }
    uint32_t l = static_cast<uint32_t>(length);
    if (!mInputs[index].unspecified()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInput called when an input has already been "
                      "provided";
        return ANEURALNETWORKS_BAD_STATE;
    }
    int n;
    std::tie(n, mInputs[index]) = ModelArgumentInfo::createFromPointer(
            mModel->getInputOperand(index), type, const_cast<void*>(buffer), l,
            mInputAndOutputPaddingEnabled);
    mHasCalledSetInputOutput = true;
    return n;
}

int ExecutionBuilder::setInputFromMemory(uint32_t index, const ANeuralNetworksOperandType* type,
                                         const RuntimeMemory* memory, size_t offset,
                                         size_t length) {
    // Should be similar to StepExecutor::setInputOrOutputFromMemory()

    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInputFromMemory called after the "
                      "execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    uint32_t count = static_cast<uint32_t>(mInputs.size());
    if (index >= count) {
        LOG(ERROR) << "ANeuralNetworksExecution_setInputFromMemory bad index " << index << " "
                   << count;
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (!checkDimensionInfo(mModel->getInputOperand(index), type,
                            "ANeuralNetworksExecution_setInputFromMemory", false)) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (!memory->getValidator().validate(mCompilation, IOType::INPUT, index, type, offset,
                                         length)) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    // For some types of memory, e.g. MemoryRuntimeAHWB allocated from ANNMemory_createFromDesc, we
    // allow the client to specify offset == 0 && length == 0 indicating that the entire memory
    // region is used. We update the length here because the drivers are still expecting a real
    // length. For other memories that do not allow this semantic, it is checked in
    // MemoryValidatorBase::validate before reaching here.
    if (validate(memory->getMemory()).ok() && offset == 0 && length == 0) {
        length = memory->getSize();
    }
    // TODO validate the rest
    uint32_t poolIndex = mMemories.add(memory);
    if (!mInputs[index].unspecified()) {
        LOG(ERROR)
                << "ANeuralNetworksExecution_setInputFromMemory called when an input has already "
                   "been provided";
        return ANEURALNETWORKS_BAD_STATE;
    }
    int n;
    std::tie(n, mInputs[index]) =
            ModelArgumentInfo::createFromMemory(mModel->getInputOperand(index), type, poolIndex,
                                                offset, length, mInputAndOutputPaddingEnabled);
    mHasCalledSetInputOutput = true;
    return n;
}

int ExecutionBuilder::setOutput(uint32_t index, const ANeuralNetworksOperandType* type,
                                void* buffer, size_t length) {
    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutput called after the "
                      "execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    uint32_t count = static_cast<uint32_t>(mOutputs.size());
    if (index >= count) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutput bad index " << index << " " << count;
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (!checkDimensionInfo(mModel->getOutputOperand(index), type,
                            "ANeuralNetworksExecution_setOutput", true)) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (length > 0xFFFFFFFF) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutput input exceeds max length " << length;
        return ANEURALNETWORKS_BAD_DATA;
    }
    uint32_t l = static_cast<uint32_t>(length);
    if (!mOutputs[index].unspecified()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutput called when an output has already been "
                      "provided";
        return ANEURALNETWORKS_BAD_STATE;
    }
    int n;
    std::tie(n, mOutputs[index]) = ModelArgumentInfo::createFromPointer(
            mModel->getOutputOperand(index), type, buffer, l, mInputAndOutputPaddingEnabled);
    mHasCalledSetInputOutput = true;
    return n;
}

int ExecutionBuilder::setOutputFromMemory(uint32_t index, const ANeuralNetworksOperandType* type,
                                          const RuntimeMemory* memory, size_t offset,
                                          size_t length) {
    // Should be similar to StepExecutor::setInputOrOutputFromMemory()

    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutputFromMemory called after the "
                      "execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    uint32_t count = static_cast<uint32_t>(mOutputs.size());
    if (index >= count) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutputFromMemory bad index " << index << " "
                   << count;
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (!checkDimensionInfo(mModel->getOutputOperand(index), type,
                            "ANeuralNetworksExecution_setOutputFromMemory", true)) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (!memory->getValidator().validate(mCompilation, IOType::OUTPUT, index, type, offset,
                                         length)) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    // For some types of memory, e.g. MemoryRuntimeAHWB allocated from ANNMemory_createFromDesc, we
    // allow the client to specify offset == 0 && length == 0 indicating that the entire memory
    // region is used. We update the length here because the drivers are still expecting a real
    // length. For other memories that do not allow this semantic, it is checked in
    // MemoryValidatorBase::validate before reaching here.
    if (validate(memory->getMemory()).ok() && offset == 0 && length == 0) {
        length = memory->getSize();
    }
    // TODO validate the rest
    uint32_t poolIndex = mMemories.add(memory);
    if (!mOutputs[index].unspecified()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setOutputFromMemory called when an output has "
                      "already been provided";
        return ANEURALNETWORKS_BAD_STATE;
    }
    int n;
    std::tie(n, mOutputs[index]) =
            ModelArgumentInfo::createFromMemory(mModel->getOutputOperand(index), type, poolIndex,
                                                offset, length, mInputAndOutputPaddingEnabled);
    mHasCalledSetInputOutput = true;
    return n;
}

int ExecutionBuilder::setMeasureTiming(bool measure) {
    if (!mCompilation->mExplicitDeviceList || (mCompilation->mDevices.size() != 1)) {
        LOG(ERROR) << "ANeuralNetworksExecution_setMeasureTiming called on "
                   << "an ANeuralNetworksExecution created from an ANeuralNetworksCompilation "
                   << "that was not created by ANeuralNetworksCompilation_createForDevices "
                   << "with numDevices = 1";
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setMeasureTiming called after the "
                      "execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    mMeasureTiming = measure;
    return ANEURALNETWORKS_NO_ERROR;
}

int ExecutionBuilder::getDuration(int32_t durationCode, uint64_t* duration) const {
    if (!completed()) {
        LOG(ERROR) << "ANeuralNetworksExecution_getDuration called before the "
                      "execution has finished.";
        *duration = UINT64_MAX;
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (completedWith() != Completion::NO_ERROR) {
        LOG(ERROR) << "ANeuralNetworksExecution_getDuration called on an execution "
                      "that has encountered an error.";
        *duration = UINT64_MAX;
        return ANEURALNETWORKS_BAD_STATE;
    }

    if (!mMeasureTiming) {
        *duration = UINT64_MAX;
        return ANEURALNETWORKS_BAD_STATE;
    }

    Timing timingLaunched = mTimingWithoutFencedExecutionCallback;
    Timing timingFenced = timingLaunched;
    if (mFencedExecutionCallback != nullptr) {
        auto result = mFencedExecutionCallback();
        if (!result.has_value()) {
            LOG(ERROR) << "Fenced execution callback failed: " << result.error().message;
            *duration = UINT64_MAX;
            return ANEURALNETWORKS_BAD_STATE;
        }
        std::tie(timingLaunched, timingFenced) = std::move(result).value();
    }
    const OptionalDuration selectedDuration = [durationCode, &timingLaunched,
                                               &timingFenced]() -> OptionalDuration {
        switch (durationCode) {
            case ANEURALNETWORKS_DURATION_ON_HARDWARE:
                return timingLaunched.timeOnDevice;
            case ANEURALNETWORKS_DURATION_IN_DRIVER:
                return timingLaunched.timeInDriver;
            case ANEURALNETWORKS_FENCED_DURATION_ON_HARDWARE:
                return timingFenced.timeOnDevice;
            case ANEURALNETWORKS_FENCED_DURATION_IN_DRIVER:
                return timingFenced.timeInDriver;
            default:
                LOG(FATAL) << "unexpected";
                return std::nullopt;
        }
    }();
    if (selectedDuration.has_value()) {
        constexpr uint64_t kMaxTiming = std::numeric_limits<uint64_t>::max() - 1;
        using CommonType = std::common_type_t<Duration::rep, uint64_t>;
        const auto count = std::min<CommonType>(selectedDuration.value().count(), kMaxTiming);
        *duration = static_cast<uint64_t>(count);
    } else {
        constexpr uint64_t kNoTiming = std::numeric_limits<uint64_t>::max();
        *duration = kNoTiming;
    }

    VLOG(EXECUTION) << "getDuration(" << durationCode << "): " << *duration;
    return ANEURALNETWORKS_NO_ERROR;
}

int ExecutionBuilder::setTimeoutDuration(uint64_t duration) {
    if (!mCompilation->mExplicitDeviceList || (mCompilation->mDevices.size() != 1)) {
        LOG(ERROR) << "ANeuralNetworksExecution_setTimeout called on an ANeuralNetworksExecution "
                      "created from an ANeuralNetworksCompilation that was not created by "
                      "ANeuralNetworksCompilation_createForDevices with numDevices = 1";
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setTimeout called after the execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (duration > 0) {
        mTimeoutDuration = duration;
    } else {
        mTimeoutDuration.reset();
    }
    return ANEURALNETWORKS_NO_ERROR;
}

std::optional<uint64_t> ExecutionBuilder::getTimeoutDuration() const {
    return mTimeoutDuration;
}

TimePoint ExecutionBuilder::getComputeStartTimePoint() const {
    CHECK(computationStarted()) << "getComputeStartTimePoint called before "
                                << "execution has started.";
    return mComputeStartTimePoint;
}

int ExecutionBuilder::setLoopTimeout(uint64_t duration) {
    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setLoopTimeout called after the "
                      "execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (duration > operation_while::kTimeoutNsMaximum) {
        LOG(WARNING) << "ANeuralNetworksExecution_setLoopTimeout input exceeds the maximum allowed "
                     << "duration: " << duration << " > " << operation_while::kTimeoutNsMaximum;
        duration = operation_while::kTimeoutNsMaximum;
    }
    mLoopTimeoutDuration = duration;
    return ANEURALNETWORKS_NO_ERROR;
}

int ExecutionBuilder::enableInputAndOutputPadding(bool enable) {
    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_enableInputAndOutputPadding called after the "
                      "execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (mHasCalledSetInputOutput) {
        LOG(ERROR) << "ANeuralNetworksExecution_enableInputAndOutputPadding called after an input "
                      "or output is set.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    mInputAndOutputPaddingEnabled = enable;
    return ANEURALNETWORKS_NO_ERROR;
}

int ExecutionBuilder::setReusable(bool reusable) {
    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_setReusable called after the "
                      "execution has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    mReusable = reusable;
    return ANEURALNETWORKS_NO_ERROR;
}

int ExecutionBuilder::addExtensionAttribute(const char* extensionName,
                                            uint16_t attributeCodeWithinExtension, const void* data,
                                            size_t length) {
    if (!mCompilation->mExplicitDeviceList || (mCompilation->mDevices.size() != 1)) {
        LOG(ERROR) << "ANeuralNetworksExecution_addExtensionAttribute called on an "
                      "ANeuralNetworksExecution created from an ANeuralNetworksCompilation that "
                      "was not created by ANeuralNetworksCompilation_createForDevices with "
                      "numDevices = 1";
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (computationStarted()) {
        LOG(ERROR) << "ANeuralNetworksExecution_addExtensionAttribute called after the execution "
                      "has started.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    int32_t attributeToken = 0;
    if (!TypeManager::get()->getExtensionType(extensionName, attributeCodeWithinExtension,
                                              &attributeToken)) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    if (std::find_if(mMetadata.begin(), mMetadata.end(), [attributeToken](const auto& entry) {
            return attributeToken == entry.token;
        }) != mMetadata.end()) {
        LOG(ERROR) << "ANeuralNetworksCompilation_addExtensionAttribute called more than once for "
                      "the same attribute";
        return ANEURALNETWORKS_BAD_DATA;
    }
    const uint8_t* dataPtr = reinterpret_cast<const uint8_t*>(data);
    mMetadata.push_back({attributeToken, std::vector<uint8_t>(dataPtr, dataPtr + length)});
    return ANEURALNETWORKS_NO_ERROR;
}

int ExecutionBuilder::getOutputOperandDimensions(uint32_t index, uint32_t* dimensions) {
    if (!completed()) {
        LOG(ERROR) << "ANeuralNetworksExecution_getOutputOperandDimensions called before the "
                      "execution has finished.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (completedWith() == Completion::OTHER_ERROR) {
        LOG(ERROR) << "ANeuralNetworksExecution_getOutputOperandDimensions called on an execution "
                      "that has encountered an error.";
        return ANEURALNETWORKS_BAD_STATE;
    }

    uint32_t count = static_cast<uint32_t>(mOutputs.size());
    if (index >= count) {
        LOG(ERROR) << "ANeuralNetworksExecution_getOutputOperandDimensions bad index " << index
                   << " " << count;
        return ANEURALNETWORKS_BAD_DATA;
    }
    const auto& dims = mOutputs[index].dimensions();
    if (dims.empty()) {
        LOG(ERROR) << "ANeuralNetworksExecution_getOutputOperandDimensions can not query "
                      "dimensions of a scalar";
        return ANEURALNETWORKS_BAD_DATA;
    }
    std::copy(dims.begin(), dims.end(), dimensions);
    return mOutputs[index].isSufficient() ? ANEURALNETWORKS_NO_ERROR
                                          : ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE;
}

int ExecutionBuilder::getOutputOperandRank(uint32_t index, uint32_t* rank) {
    if (!completed()) {
        LOG(ERROR) << "ANeuralNetworksExecution_getOutputOperandRank called before the "
                      "execution has finished.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (completedWith() == Completion::OTHER_ERROR) {
        LOG(ERROR) << "ANeuralNetworksExecution_getOutputOperandRank called on an execution "
                      "that has encountered an error.";
        return ANEURALNETWORKS_BAD_STATE;
    }
    uint32_t count = static_cast<uint32_t>(mOutputs.size());
    if (index >= count) {
        LOG(ERROR) << "ANeuralNetworksExecution_getOutputOperandRank bad index " << index << " "
                   << count;
        return ANEURALNETWORKS_BAD_DATA;
    }
    *rank = static_cast<uint32_t>(mOutputs[index].dimensions().size());
    return mOutputs[index].isSufficient() ? ANEURALNETWORKS_NO_ERROR
                                          : ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE;
}

bool ExecutionBuilder::checkAndSetComputationState(const char* name) {
    std::lock_guard<std::mutex> lock(mStateMutex);
    if (!mReusable && mState == State::COMPLETED) {
        LOG(ERROR) << "ANeuralNetworksExecution_" << name
                   << " called on a non-reusable execution that has already completed";
        return false;
    }
    if (mState == State::COMPUTATION) {
        LOG(ERROR) << "ANeuralNetworksExecution_" << name
                   << " called on an execution that has already started";
        return false;
    }
    mState = State::COMPUTATION;
    return true;
}

// TODO(b/132321855): validate that we have full types for all inputs and outputs,
// that the graph is not cyclic,
static int validateRequest(const std::vector<ModelArgumentInfo>& inputs,
                           const std::vector<ModelArgumentInfo>& outputs) {
    for (auto& p : inputs) {
        if (p.state() == ModelArgumentInfo::UNSPECIFIED) {
            LOG(ERROR) << "ANeuralNetworksExecution starts compute when not all inputs specified";
            return ANEURALNETWORKS_BAD_DATA;
        }
    }
    for (auto& p : outputs) {
        if (p.state() == ModelArgumentInfo::UNSPECIFIED) {
            LOG(ERROR) << "ANeuralNetworksExecution starts compute when not all outputs specified";
            return ANEURALNETWORKS_BAD_DATA;
        }
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ExecutionBuilder::getValidationResultCode() {
    if (!mValidationResultCode.has_value()) {
        mValidationResultCode = validateRequest(mInputs, mOutputs);
    }
    return mValidationResultCode.value();
}

bool ExecutionBuilder::areOutputsFullySpecified() {
    if (!mOutputsFullySpecified.has_value()) {
        mOutputsFullySpecified = true;
        for (uint32_t i = 0; i < mOutputs.size(); i++) {
            if (mOutputs[i].state() != ModelArgumentInfo::HAS_NO_VALUE &&
                TypeManager::get()->isTensorType(mModel->getOutputOperand(i).type) &&
                tensorHasUnspecifiedDimensions(mModel->getOutputOperand(i).type,
                                               mOutputs[i].initialDimensions())) {
                mOutputsFullySpecified = false;
                break;
            }
        }
    }
    return mOutputsFullySpecified.value();
}

int ExecutionBuilder::prepareForCompute(const char* name, ExecutionMode mode) {
    if (!checkAndSetComputationState(name)) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (int n = getValidationResultCode(); n != ANEURALNETWORKS_NO_ERROR) {
        return finishComputation(n, {}, mode);
    }
    return ANEURALNETWORKS_NO_ERROR;
}

// Attempt synchronous execution of full model on CPU.
// TODO: How should we handle timing in this case?
//       For Q this is irrelevant: We only support timing in conjunction
//         with an explicit device list; and we do not support CPU fallback
//         with an explicit device list.  See CompilationBuilder::mExplicitDeviceList.
static std::tuple<int, std::vector<OutputShape>, Timing> cpuFallbackFull(
        ExecutionBuilder* executionBuilder) {
    CHECK(executionBuilder != nullptr);
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "cpuFallbackFull");
    VLOG(EXECUTION) << "cpuFallbackFull";

    // Get fallback executor.
    StepExecutor executor(executionBuilder, executionBuilder->getModel(),
                          DeviceManager::getCpuDevice(), /*preparedModel=*/nullptr,
                          /*reusable=*/false);
    executor.mapInputsAndOutputsTrivially();

    // Attempt fallback execution.
    return executor.computeOnCpuFallback();
}

// Attempt synchronous execution on CPU.
// TODO: How should we handle timing in this case?
//       For Q this is irrelevant: We only support timing in conjunction
//         with an explicit device list; and we do not support CPU fallback
//         with an explicit device list.  See CompilationBuilder::mExplicitDeviceList.
static std::tuple<int, std::vector<OutputShape>, Timing, std::shared_ptr<StepExecutor>>
cpuFallbackPartial(const ExecutionPlan& plan,
                   std::shared_ptr<ExecutionPlan::Controller> controller) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "cpuFallbackPartial");
    VLOG(EXECUTION) << "cpuFallbackPartial";

    // Get fallback executor.
    std::shared_ptr<StepExecutor> executor;
    int n1 = plan.fallback(controller, &executor, nullptr, nullptr);
    if (n1 != ANEURALNETWORKS_NO_ERROR) {
        return {n1, {}, {}, nullptr};
    }
    CHECK(executor != nullptr);

    // Attempt fallback execution.
    auto [n2, outputShapes, timing] = executor->computeOnCpuFallback();
    return {n2, std::move(outputShapes), timing, executor};
}

std::tuple<int, std::vector<OutputShape>, Timing> SimpleExecutionBuilder::computeInternal(
        const OptionalTimePoint& deadline, BurstBuilder* burstBuilder) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "SimpleExecutionBuilder::computeInternal");
    VLOG(EXECUTION) << "SimpleExecutionBuilder::computeInternal";

    if (mExecutor == nullptr) {
        mExecutor = mPlan->makeStepExecutor(mReusable, this);
    }

    auto burstController = burstBuilder ? burstBuilder->getControllerAt(0) : nullptr;
    auto [n, outputShapes, timing] = mExecutor->compute(deadline, burstController);

    if (n == ANEURALNETWORKS_NO_ERROR) {
        return {n, std::move(outputShapes), timing};
    }

    // ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE is not recoverable.
    if (n == ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE) {
        return {n, std::move(outputShapes), {}};
    }

    // If CPU fallback is not allowed and there was an error, end execution.
    if (!mAllowCpuFallback) {
        return {n, {}, {}};
    }

    // If CPU execution was already attempted, do not perform CPU fallback.
    if (mExecutor->isCpu()) {
        return {n, {}, {}};
    }

    // If the code has reached this point, a potentially recoverable error
    // occurred during the execution. Do an execution fallback on the CPU.
    return cpuFallbackFull(this);
}

std::tuple<int, std::vector<OutputShape>, Timing> CompoundExecutionBuilder::computeInternal(
        const OptionalTimePoint& deadline, BurstBuilder* burstBuilder) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "CompoundExecutionBuilder::computeInternal");
    VLOG(EXECUTION) << "CompoundExecutionBuilder::computeInternal (from plan, iteratively)";

    auto controller = mPlan->makeController(this, burstBuilder);
    std::vector<OutputShape> outputShapes = getInitialOutputShapes();

    // On this iteration, do I need to repeat the previous step because it
    // reported insufficient size?
    bool doInsufficientSizeFallback = false;

    while (true) {
        VLOG(EXECUTION) << "looking for next StepExecutor";

        // Get the current step of the execution.
        std::shared_ptr<StepExecutor> executor;
        SharedBurst burstController;
        int n = doInsufficientSizeFallback
                        ? mPlan->fallback(controller, &executor, &burstController, &outputShapes)
                        : mPlan->next(controller, &executor, &burstController, &outputShapes);
        doInsufficientSizeFallback = false;
        if (n != ANEURALNETWORKS_NO_ERROR) {
            // During the interpreted execution of control flow, a loop timeout
            // might occur in ExecutionPlan::next().
            bool missedDeadline = n == ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT ||
                                  n == ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT;
            if (mAllowCpuFallback && !missedDeadline) break;
            return {n, {}, {}};
        }

        // If the code reached the end of the plan without error, then return
        // with no error.
        if (executor == nullptr) {
            return {ANEURALNETWORKS_NO_ERROR, outputShapes, {}};
        }
        const bool executorIsCpu = executor->isCpu();

        // Attempt to execute a single step of the execution.
        auto [stepN, stepOutputShapes, _] = executor->compute(deadline, burstController);

        // Update global outputs and dynamic temporaries.
        StepExecutor::UpdateOutputShapes updateOutputShapes = {};
        if (!executor->updateOutputShapes(stepN, stepOutputShapes, &outputShapes,
                                          &updateOutputShapes)) {
            stepN = ANEURALNETWORKS_OP_FAILED;
        }

        // If execution was successful, continue to next step.
        if (stepN == ANEURALNETWORKS_NO_ERROR) {
            if (updateOutputShapes.zeroSizedInput) {
                // We'll need to do full model CPU fallback
                VLOG(EXECUTION) << "updateOutputShapes.zeroSizedInput";
                stepN = ANEURALNETWORKS_OP_FAILED;
            } else {
                CHECK(executor->areDynamicTemporariesAllocated());
                continue;
            }
        }

        if (stepN == ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE) {
            VLOG(EXECUTION) << "OUTPUT_INSUFFICIENT_SIZE: " << toString(updateOutputShapes);
            if (updateOutputShapes.mainOutputInsufficient ||
                !updateOutputShapes.updatedDynamicTemporary) {
                // Either:
                // - At least one main model output is not of sufficient size; or
                // - we didn't learn anything new about dynamic temporaries.
                // Neither of these is recoverable, so end execution.
                return {stepN, outputShapes, {}};
            }
            // Every main model output is of sufficient size.  This implies that
            // at least one dynamic temporary is not of sufficient size.  This
            // is recoverable.
            doInsufficientSizeFallback = true;
            continue;
        }

        // If CPU fallback is not allowed and there was an error, end execution.
        if (!mAllowCpuFallback) {
            return {stepN, {}, {}};
        }

        // If CPU execution was already attempted, perform a full CPU fallback.
        if (executorIsCpu) {
            break;
        }

        // If the code reaches this point, attempt a partial fallback to CPU.
        CHECK(mAllowCpuFallback);
        if (updateOutputShapes.zeroSizedInput) {
            // Do not attempt a partial fallback.
            break;
        }
        while (true) {
            auto [fallbackN, fallbackOutputShapes, _, fallbackExecutor] =
                    cpuFallbackPartial(*mPlan, controller);

            // Update global outputs and dynamic temporaries.
            StepExecutor::UpdateOutputShapes fallbackUpdateOutputShapes = {};
            if (fallbackExecutor != nullptr &&
                !fallbackExecutor->updateOutputShapes(fallbackN, fallbackOutputShapes,
                                                      &outputShapes, &fallbackUpdateOutputShapes)) {
                fallbackN = ANEURALNETWORKS_OP_FAILED;
            }

            // If execution was successful, continue to next step.
            if (fallbackN == ANEURALNETWORKS_NO_ERROR) {
                if (fallbackUpdateOutputShapes.zeroSizedInput) {
                    // We'll need to do full model CPU fallback
                    VLOG(EXECUTION) << "fallbackUpdateOutputShapes.zeroSizedInput";
                    fallbackN = ANEURALNETWORKS_OP_FAILED;
                    break;
                }
                CHECK(fallbackExecutor->areDynamicTemporariesAllocated());
                goto nextStep;
            }

            if (fallbackN == ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE) {
                VLOG(EXECUTION) << "OUTPUT_INSUFFICIENT_SIZE: "
                                << toString(fallbackUpdateOutputShapes);
                if (fallbackUpdateOutputShapes.mainOutputInsufficient ||
                    !fallbackUpdateOutputShapes.updatedDynamicTemporary) {
                    // Either:
                    // - At least one main model output is not of sufficient size; or
                    // - we didn't learn anything new about dynamic temporaries.
                    // Neither of these is recoverable, so end execution.
                    return {fallbackN, outputShapes, {}};
                }
                // Every main model output is of sufficient size.  This implies
                // that at least one dynamic temporary is not of sufficient
                // size.  This is recoverable.
                continue;
            }

            // If the code reaches this point, then there was an error with the
            // fallback. In this case, attempt full fallback.
            break;
        }

        // If the code reaches this point, then there was an error with the
        // fallback. In this case, attempt full fallback.
        break;

    nextStep:
        // Bottom of the outer loop
        continue;
    }

    // If the code has reached this point, a potentially recoverable error
    // occurred during the step executions. Instead, do a full execution
    // fallback on the CPU.
    return cpuFallbackFull(this);
}

static bool waitForSyncFences(const std::vector<int>& waitFor) {
    for (int syncFd : waitFor) {
        if (syncFd > 0) {
            auto r = syncWait(syncFd, -1);
            if (r != FenceState::SIGNALED) {
                VLOG(EXECUTION) << "syncWait failed, fd: " << syncFd;
                return false;
            }
        }
    }
    return true;
}

std::tuple<int, int, ExecuteFencedInfoCallback> SimpleExecutionBuilder::computeFencedInternal(
        const std::vector<int>& waitFor, uint64_t timeoutDurationAfterFence,
        const OptionalTimePoint& deadline) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "SimpleExecutionBuilder::computeFencedInternal");
    VLOG(EXECUTION) << "SimpleExecutionBuilder::computeFencedInternal";

    if (mExecutor == nullptr) {
        mExecutor = mPlan->makeStepExecutor(mReusable, this);
    }

    auto [n, syncFd, callback] =
            mExecutor->computeFenced(waitFor, timeoutDurationAfterFence, deadline);

    if (n == ANEURALNETWORKS_NO_ERROR) {
        return {ANEURALNETWORKS_NO_ERROR, syncFd, callback};
    }

    // If CPU fallback is not allowed and there was an error, end execution.
    if (!mAllowCpuFallback) {
        return {n, -1, nullptr};
    }

    // If CPU execution was already attempted, return from the function with an error.
    if (mExecutor->isCpu()) {
        return {n, -1, nullptr};
    }

    // If the code has reached this point, a potentially recoverable error
    // occurred during the step executions. Instead, do a full execution
    // fallback on the CPU.
    VLOG(EXECUTION) << "Performing full fallback on the CPU.";
    if (!waitForSyncFences(waitFor)) {
        return {ANEURALNETWORKS_OP_FAILED, -1, nullptr};
    }
    auto [fallbackN, fallbackOutputShapes, fallbackTiming] = cpuFallbackFull(this);
    reportTimingWithoutFencedExecutionCallback(fallbackTiming);
    return {fallbackN, -1, nullptr};
}

// In case of partitioned execution, computeFencedInternal call will return the sync
// fence and the fenced compute callback returned from the last partition.
// Any failed partition will result in whole execution fallback to CPU if
// mAllowCpuFallback is set to true.
std::tuple<int, int, ExecuteFencedInfoCallback> CompoundExecutionBuilder::computeFencedInternal(
        const std::vector<int>& waitFor, uint64_t timeoutDurationAfterFence,
        const OptionalTimePoint& deadline) {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "CompoundExecutionBuilder::computeFencedInternal");
    VLOG(EXECUTION) << "CompoundExecutionBuilder::computeFencedInternal (from plan, iteratively)";

    // We should have detected this earlier in the call chain and fallen back to
    // non-fenced execution.  This is an implementation limitation: In order to
    // support dynamic temporarires in this code, we'd need to implement
    // something like the following:
    // - If a partition has outputs of unknown size, compute that partition in a
    //   non fenced fashion, just as if it were scheduled on a driver that does
    //   not support fenced execution.
    // - Implement something similar to the code in CompoundExecutionBuilder::computeInternal()
    //   that handles a step execution that fails with
    //   ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE.
    CHECK(!mCompilation->hasDynamicTemporaries());

    // Initiate waitForFds, syncFence for the first step.
    std::vector<int> waitForFds = waitFor;
    base::unique_fd syncFence;
    ExecuteFencedInfoCallback executeFencedInfoCallback;

    std::shared_ptr<ExecutionPlan::Controller> controller = mPlan->makeController(this, nullptr);
    while (true) {
        VLOG(EXECUTION) << "looking for next StepExecutor";

        // Get the current step of the execution.
        std::shared_ptr<StepExecutor> executor;
        int n = mPlan->next(controller, &executor, nullptr, nullptr, syncFence.get());
        if (n != ANEURALNETWORKS_NO_ERROR) {
            // During the interpreted execution of control flow, a loop timeout
            // might occur in ExecutionPlan::next().
            bool missedDeadline = n == ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT ||
                                  n == ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT;
            if (mAllowCpuFallback && !missedDeadline) break;
            // Return -1 for the sync fence fd, and nullptr for the callback.
            return {n, -1, nullptr};
        }

        // If the code reached the end of the plan without error, then return
        // with no error.
        if (executor == nullptr) {
            return {ANEURALNETWORKS_NO_ERROR, syncFence.release(), executeFencedInfoCallback};
        }

        // Attempt to compute a single step of the execution.
        auto [stepN, syncFd, callback] =
                executor->computeFenced(waitForFds, timeoutDurationAfterFence, deadline);

        // Update waitForFds, syncFence for the next step.
        syncFence.reset(syncFd);
        executeFencedInfoCallback = callback;
        waitForFds.clear();
        if (syncFd >= 0) {
            waitForFds = {syncFd};
        }

        // If execution was successful, continue to next step.
        if (stepN == ANEURALNETWORKS_NO_ERROR) {
            continue;
        }
        // If CPU fallback is not allowed and there was an error, end execution.
        if (!mAllowCpuFallback) {
            return {stepN, -1, nullptr};
        }

        // If the code reaches this point, then there was an error with the
        // fallback. In this case, attempt full fallback.
        break;
    }

    // If the code has reached this point, a potentially recoverable error
    // occurred during the step executions. Instead, do a full execution
    // fallback on the CPU.
    VLOG(EXECUTION) << "Performing full fallback on the CPU.";
    if (!waitForSyncFences(waitFor)) {
        return {ANEURALNETWORKS_OP_FAILED, -1, nullptr};
    }
    auto [fullN, fullOutputShapes, _] = cpuFallbackFull(this);
    return {fullN, -1, nullptr};
}

int ExecutionBuilder::computeFenced(const std::vector<int>& waitFor,
                                    uint64_t timeoutDurationAfterFence, int* syncFence) {
    CHECK(syncFence != nullptr);
    NN_RETURN_IF_ERROR(
            prepareForCompute("startComputeWithDependencies", ExecutionMode::ASYNC_WITH_DEPS));
    if (timeoutDurationAfterFence > 0) {
        if (!mCompilation->mExplicitDeviceList || (mCompilation->mDevices.size() != 1)) {
            LOG(ERROR)
                    << "ANeuralNetworksExecution_startComputeWithDependencies called with non-zero "
                       "duration on an ANeuralNetworksExecution "
                       "created from an ANeuralNetworksCompilation that was not created by "
                       "ANeuralNetworksCompilation_createForDevices with numDevices = 1";
            return finishComputation(ANEURALNETWORKS_BAD_DATA, {}, ExecutionMode::ASYNC_WITH_DEPS);
        }
    }
    if (!areOutputsFullySpecified()) {
        LOG(ERROR) << "ANeuralNetworksExecution_startComputeWithDependencies"
                      " not all outputs have fully specified dimensions";
        return finishComputation(ANEURALNETWORKS_BAD_DATA, {}, ExecutionMode::ASYNC_WITH_DEPS);
    }

    // Unlike ExecutionBuilder::compute, we do not need to reset output dimensions here because
    // fenced executions do not support dynamic output shape.

    mComputeStartTimePoint = Clock::now();
    VLOG(EXECUTION) << "ExecutionBuilder::computeFenced";
    int result;
    const auto deadline = makeDeadline(mTimeoutDuration);
    std::tie(result, *syncFence, mFencedExecutionCallback) =
            computeFencedInternal(waitFor, timeoutDurationAfterFence, deadline);
    // If there is an error, call finishComputation to mark the computation as completed.
    // Otherwise, we will call finishComputation in SyncFenceEvent::wait().
    if (result != ANEURALNETWORKS_NO_ERROR) {
        // TODO(miaowang): support dynamic output shape only with memory domain.
        // For now just return empty output shapes.
        result = finishComputation(result, {}, ExecutionMode::ASYNC_WITH_DEPS);
    }
    return result;
}

int ExecutionBuilder::compute(std::shared_ptr<ExecutionCallback>* synchronizationCallback,
                              BurstBuilder* burstBuilder) {
    CHECK(synchronizationCallback == nullptr || burstBuilder == nullptr)
            << "synchronizationCallback and burstBuilder cannot simultaneously be used";

    const bool synchronous = (synchronizationCallback == nullptr);
    if (!synchronous) {
        *synchronizationCallback = nullptr;
    }

    const char* name = burstBuilder ? "burstCompute" : synchronous ? "compute" : "startCompute";
    const ExecutionMode mode = burstBuilder
                                       ? ExecutionMode::BURST
                                       : synchronous ? ExecutionMode::SYNC : ExecutionMode::ASYNC;
    NN_RETURN_IF_ERROR(prepareForCompute(name, mode));

    // Validate input memory dimensions. We need to do the validation in every computation because
    // the memory dimensions may change between computations.
    for (auto& p : mInputs) {
        if (p.state() == ModelArgumentInfo::MEMORY) {
            const RuntimeMemory* memory = mMemories[p.locationAndLength().poolIndex];
            if (!memory->getValidator().validateInputDimensions(p.dimensions())) {
                return finishComputation(ANEURALNETWORKS_OP_FAILED, {}, mode);
            }
        }
    }

    // Reset output dimensions.
    if (!areOutputsFullySpecified()) {
        for (auto& output : mOutputs) {
            output.reset();
        }
    }

    const auto deadline = makeDeadline(mTimeoutDuration);
    mComputeStartTimePoint = Clock::now();
    if (synchronous) {
        if (burstBuilder) {
            VLOG(EXECUTION) << "ExecutionBuilder::compute (synchronous API, burst)";
        } else {
            VLOG(EXECUTION) << "ExecutionBuilder::compute (synchronous API)";
        }
        const auto [n, outputShapes, timing] = computeInternal(deadline, burstBuilder);
        if (mMeasureTiming) {
            mTimingWithoutFencedExecutionCallback = timing;
        }
        return finishComputation(n, outputShapes, mode);
    } else /* asynchronous */ {
        // TODO: For asynchronous execution, entire plan-based-path should run in an
        // asynchronous thread -- take the asynchronous thread logic out of
        // CpuExecution::compute() and use it to wrap the plan-based-path.

        // TODO: use a thread pool
        // TODO(mikie): this could have NNTRACE so we could measure the overhead
        //              of spinning up a new thread.

        // Prepare the callback for asynchronous execution.
        // std::shared_ptr<ExecutionCallback> object is returned when the
        // execution has been successfully launched, otherwise a
        // nullptr is returned.  The executionCallback is
        // abstracted in the NN API as an "event".
        auto executionCallback = std::make_shared<ExecutionCallback>();
        executionCallback->setOnFinish(
                [this, mode](ErrorStatus error, const std::vector<OutputShape>& outputShapes) {
                    return finishComputation(error, outputShapes, mode);
                });
        const auto asyncStartCompute = [this, deadline, executionCallback] {
            const auto [n, outputShapes, timing] = computeInternal(deadline, nullptr);
            const auto status = convertResultCodeToErrorStatus(n);
            executionCallback->notify(status, outputShapes, timing);
        };
        if (DeviceManager::get()->syncExecRuntime()) {
            VLOG(EXECUTION) << "ExecutionBuilder::compute (asynchronous API, non-threaded)";
            asyncStartCompute();
        } else {
            VLOG(EXECUTION) << "ExecutionBuilder::compute (asynchronous API)";
            std::thread asyncExecution(asyncStartCompute);
            executionCallback->bindThread(std::move(asyncExecution));
        }
        *synchronizationCallback = executionCallback;
        return ANEURALNETWORKS_NO_ERROR;
    }
}

std::vector<OutputShape> ExecutionBuilder::getInitialOutputShapes() const {
    std::vector<OutputShape> outputShapes(mOutputs.size());
    std::transform(mOutputs.begin(), mOutputs.end(), outputShapes.begin(),
                   [](const auto& x) -> OutputShape {
                       std::vector<uint32_t> dimensions;
                       if (x.state() != ModelArgumentInfo::HAS_NO_VALUE) {
                           dimensions = x.dimensions();
                       }
                       return {.dimensions = std::move(dimensions), .isSufficient = true};
                   });
    return outputShapes;
}

// Check if the dimensions "to" is updatable by dimensions "from", where "from" must
// have no lower a specification level.
static bool isUpdatable(const std::vector<uint32_t>& to, const std::vector<uint32_t>& from) {
    if (to.size() == 0) return true;
    NN_RET_CHECK_EQ(to.size(), from.size());
    for (uint32_t i = 0; i < to.size(); i++) {
        NN_RET_CHECK(to[i] == from[i] || to[i] == 0);
    }
    return true;
}

static bool isZeroSizedTensor(int executionResultCode, const OutputShape& outputShape) {
    return (executionResultCode == ANEURALNETWORKS_NO_ERROR) && outputShape.isSufficient &&
           outputShape.dimensions.size() &&
           (std::find(outputShape.dimensions.begin(), outputShape.dimensions.end(), uint32_t(0)) !=
            outputShape.dimensions.end());
}

bool ExecutionBuilder::updateOutputShapes(ErrorStatus status,
                                          const std::vector<OutputShape>& outputShapes) {
    NN_RET_CHECK(validateOutputShapesFromDriver(status, mModel, outputShapes));

    if (outputShapes.size() == 0) {
        return true;
    }
    NN_RET_CHECK_EQ(outputShapes.size(), mOutputs.size());
    for (uint32_t i = 0; i < outputShapes.size(); i++) {
        // Check if only unspecified dimensions or rank are overwritten.
        NN_RET_CHECK(isUpdatable(mOutputs[i].dimensions(), outputShapes[i].dimensions));
        const OperandType operandType = mModel->getOutputOperand(i).type;
        NN_RET_CHECK(!TypeManager::get()->sizeOfDataOverflowsUInt32(operandType,
                                                                    outputShapes[i].dimensions));
    }
    for (uint32_t i = 0; i < outputShapes.size(); i++) {
        mOutputs[i].dimensions() = outputShapes[i].dimensions;
        mOutputs[i].isSufficient() = outputShapes[i].isSufficient;
    }
    return true;
}

bool ExecutionBuilder::updateMemories() {
    for (const auto& output : mOutputs) {
        if (output.state() != ModelArgumentInfo::MEMORY) continue;
        const RuntimeMemory* memory = mMemories[output.locationAndLength().poolIndex];
        NN_RET_CHECK(memory->getValidator().updateMetadata({.dimensions = output.dimensions()}));
    }
    return true;
}

int ExecutionBuilder::finishComputation(int result, const std::vector<OutputShape>& outputShapes,
                                        ExecutionMode mode) {
    const auto status = convertResultCodeToErrorStatus(result);
    if (!updateOutputShapes(status, outputShapes) || !updateMemories()) {
        result = ANEURALNETWORKS_OP_FAILED;
    }
    bool success = result == ANEURALNETWORKS_NO_ERROR;
    for (const auto& output : mOutputs) {
        if (output.state() != ModelArgumentInfo::MEMORY) continue;
        const RuntimeMemory* memory = mMemories[output.locationAndLength().poolIndex];
        memory->getValidator().setInitialized(success);
    }
    switch (result) {
        case ANEURALNETWORKS_NO_ERROR:
            mCompletion = Completion::NO_ERROR;
            break;
        case ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE:
            mCompletion = Completion::OUTPUT_INSUFFICIENT_SIZE;
            break;
        default:
            mCompletion = Completion::OTHER_ERROR;
            break;
    }
    {
        std::lock_guard<std::mutex> lock(mStateMutex);
        CHECK(mState != State::PREPARATION)
                << "ExecutionBuilder::finishComputation is called in the preparation state";
        CHECK(mState != State::COMPLETED) << "ExecutionBuilder::finishComputation is called twice";
        mState = State::COMPLETED;
    }
    telemetry::onExecutionFinish(this, mode, result);
    return result;
}

std::string toString(StepExecutor::UpdateOutputShapes updateOutputShapes) {
    return "{ .updatedDynamicTemporary = " +
           std::to_string(updateOutputShapes.updatedDynamicTemporary) +
           ", .mainOutputInsufficient = " +
           std::to_string(updateOutputShapes.mainOutputInsufficient) + "}";
}

bool StepExecutor::updateOutputShapes(int executionResultCode, const std::vector<OutputShape>& from,
                                      std::vector<OutputShape>* to, UpdateOutputShapes* update) {
    CHECK(update != nullptr);
    *update = {.updatedDynamicTemporary = false,
               .mainOutputInsufficient = false,
               .zeroSizedInput = false};

    NN_RET_CHECK(validateOutputShapesFromDriver(executionResultCode, mModel, from));

    if (from.size() == 0) {
        return true;
    }

    if (VLOG_IS_ON(EXECUTION)) {
        for (const auto& shape : from) {
            VLOG(EXECUTION) << "updateOutputShapes: " << shape;
        }
    }

    if (mExecutionStep != nullptr) {
        const auto& indexMapping = mExecutionStep->getOutputIndexStepModelToMainModel();
        NN_RET_CHECK_LE(indexMapping.size(), from.size());
        for (uint32_t i = 0, e = indexMapping.size(); i < e; i++) {
            const uint32_t toIndex = indexMapping[i];
            NN_RET_CHECK_GT(to->size(), toIndex);
            NN_RET_CHECK(isUpdatable(to->at(toIndex).dimensions, from[i].dimensions));
            (*to)[toIndex] = from[i];
            update->mainOutputInsufficient |= !(*to)[toIndex].isSufficient;
            if (mExecutionStep->getModelOutputsThatAreDownstreamInputs().count(toIndex) &&
                isZeroSizedTensor(executionResultCode, from[i])) {
                update->zeroSizedInput = true;
            }
        }

        if (!mDynamicTemporaries->empty()) {
            // TODO(b/157236079): Instead of computing this here, precompute it in ExecutionStep?
            std::map<uint32_t, uint32_t> operandIndexStepModelOutputToSourceModelTemp;
            for (const auto& entry : mExecutionStep->getTempsAsStepModelOutputs()) {
                operandIndexStepModelOutputToSourceModelTemp.emplace(entry.second, entry.first);
            }

            const uint32_t sourceModelIndex = mExecutionStep->getSourceModelIndex();
            for (uint32_t i = 0, e = mModel->outputCount(); i < e; i++) {
                const uint32_t stepModelOperandIndex = mModel->getOutputOperandIndex(i);
                const auto it =
                        operandIndexStepModelOutputToSourceModelTemp.find(stepModelOperandIndex);
                if (it == operandIndexStepModelOutputToSourceModelTemp.end()) {
                    continue;
                }
                const auto sourceOperandIndex = SourceOperandIndex(sourceModelIndex, it->second);
                VLOG(EXECUTION) << "updateOutputShapes checking to see if output#" << i
                                << " sourceOperandIndex = (" << sourceOperandIndex.first << ", "
                                << sourceOperandIndex.second << ") is a dynamic temporary";
                // This is a temporary, but it might not be a dynamic temporary.
                const auto loc = mDynamicTemporaries->lookup(sourceOperandIndex, false);
                if (loc == std::nullopt) {
                    continue;
                }
                NN_RET_CHECK(isUpdatable(*loc->dimensions, from[i].dimensions));
                bool changedShape = false;
                const uint32_t actualSize = TypeManager::get()->getSizeOfData(
                        mModel->getOperand(stepModelOperandIndex).type, from[i].dimensions);
                if (actualSize > 0) {
                    changedShape = mDynamicTemporaries->redeclare(sourceOperandIndex,
                                                                  from[i].dimensions, actualSize);
                } else if (!from[i].isSufficient) {
                    NN_RET_CHECK(loc->paddedLength < UINT32_MAX / 2)
                            << "output#" << i << " paddedLength overflow";
                    changedShape = mDynamicTemporaries->redeclare(
                            sourceOperandIndex, from[i].dimensions, 2 * loc->paddedLength);
                } else {
                    // The combination of not-fully-specified dimensions
                    // and isSufficient means that we have no
                    // information about whether the size of the dynamic
                    // temporary is adequate.
                    VLOG(EXECUTION) << "updateOutputShapes skipping redeclaration for output#" << i;
                    if (executionResultCode == ANEURALNETWORKS_NO_ERROR) {
                        NN_RET_CHECK(isZeroSizedTensor(executionResultCode, from[i]));
                        // This is a zero-sized tensor, and by
                        // definition, any dynamic temporary is an input
                        // to an execution step.
                        update->zeroSizedInput = true;
                    }
                }
                if (changedShape) {
                    // TODO: find a better place for this comment.
                    //
                    // isUpdatable(a, b) imposes a partial ordering a <=
                    // b.  Every fully specified dimensions vector is an
                    // upper bound of that ordering.  Therefore, any
                    // change in dimensions moves towards an upper
                    // bound, and hence there are a finite number of
                    // such changes possible.
                    //
                    // actualSize can only be computed from dimensions
                    // that are an upper bound.  Therefore, once
                    // actualSize is computed, it will not change.
                    //
                    // If dimensions are not fully specified, and
                    // estimated size changes, it increases.  There is
                    // an upper bound on estimated size to avoid
                    // overflow.
                    //
                    // Therefore, if we retry only when dimensions or
                    // size chage, and we stop retrying if we would
                    // otherwise overflow, we should only retry a finite
                    // number of times.
                    update->updatedDynamicTemporary = true;
                }
            }
            mDynamicTemporaries->vlogDump("finished updateOutputShapes");
        }
    } else {
        NN_RET_CHECK_EQ(from.size(), to->size());
        for (uint32_t i = 0, e = from.size(); i < e; i++) {
            NN_RET_CHECK(isUpdatable(to->at(i).dimensions, from[i].dimensions));
            (*to)[i] = from[i];
        }
    }
    return true;
}

StepExecutor::StepExecutor(ExecutionBuilder* executionBuilder, const ModelBuilder* model,
                           std::shared_ptr<Device> device,
                           std::shared_ptr<RuntimePreparedModel> preparedModel, bool reusable,
                           const ExecutionStep* step, DynamicTemporaries* dynamicTemporaries)
    : mExecutionBuilder(executionBuilder),
      mExecutionStep(step),
      mDynamicTemporaries(dynamicTemporaries),
      mModel(model),
      mDevice(device),
      mPreparedModel(preparedModel),
      mInputs(model->inputCount()),
      mOutputs(model->outputCount()),
      mReusable(reusable) {
    CHECK(mDevice != nullptr);
    CHECK_EQ(step == nullptr, dynamicTemporaries == nullptr);
    CHECK(!(reusable && dynamicTemporaries != nullptr));
    VLOG(EXECUTION) << "StepExecutor::StepExecutor with " << mInputs.size() << " inputs and "
                    << mOutputs.size() << " outputs";
}

bool StepExecutor::areDynamicTemporariesAllocated() const {
    return !mDynamicTemporaries || mDynamicTemporaries->allocated(mExecutionStep->getIndex());
}

void StepExecutor::mapInputsAndOutputsTrivially() {
    mInputs = mExecutionBuilder->mInputs;
    mOutputs = mExecutionBuilder->mOutputs;
    mMemories = mExecutionBuilder->mMemories;
}

void StepExecutor::mapInputOrOutput(const ModelArgumentInfo& builderInputOrOutput,
                                    ModelArgumentInfo* executorInputOrOutput,
                                    const Dimensions* builderDimensions) {
    auto updateDimensions = [executorInputOrOutput, builderDimensions] {
        if (!builderDimensions) {
            return;
        }
        executorInputOrOutput->dimensions() = *builderDimensions;
    };

    *executorInputOrOutput = builderInputOrOutput;
    switch (executorInputOrOutput->state()) {
        default:
            CHECK(false) << "unexpected ModelArgumentInfo::state";
            break;
        case ModelArgumentInfo::HAS_NO_VALUE:
        case ModelArgumentInfo::UNSPECIFIED:
            break;
        case ModelArgumentInfo::POINTER:
            updateDimensions();
            break;
        case ModelArgumentInfo::MEMORY: {
            updateDimensions();
            const uint32_t builderPoolIndex = builderInputOrOutput.locationAndLength().poolIndex;
            const RuntimeMemory* memory = mExecutionBuilder->mMemories[builderPoolIndex];
            const uint32_t executorPoolIndex = mMemories.add(memory);
            executorInputOrOutput->locationAndLength().poolIndex = executorPoolIndex;
            break;
        }
    }
}

int StepExecutor::setInputOrOutputFromMemory(const Operand& inputOrOutputOperand,
                                             const RuntimeMemory* memory, uint32_t offset,
                                             uint32_t length, const Dimensions& dimensions,
                                             ModelArgumentInfo* inputOrOutputInfo) {
    // Should be similar to
    //     ExecutionBuilder::setInputFromMemory()
    //     ExecutionBuilder::setOutputFromMemory()

    uint32_t poolIndex = mMemories.add(memory);
    CHECK(inputOrOutputInfo->unspecified());
    int n;
    std::tie(n, *inputOrOutputInfo) =
            ModelArgumentInfo::createFromMemory(inputOrOutputOperand,
                                                /*type=*/nullptr, poolIndex, offset, length);
    if (n == ANEURALNETWORKS_NO_ERROR && dimensions.size()) {
        CHECK(isUpdatable(inputOrOutputInfo->dimensions(), dimensions));
        inputOrOutputInfo->dimensions() = dimensions;
    }
    return n;
}

static std::string toString(std::vector<uint32_t> dimensions) {
    std::string ret = "(";
    bool wroteOne = false;
    for (uint32_t dimension : dimensions) {
        if (wroteOne) {
            ret += ", ";
        } else {
            wroteOne = true;
        }
        ret += std::to_string(dimension);
    }
    ret += ")";
    return ret;
};

static void logArguments(const char* kind, const std::vector<ModelArgumentInfo>& args) {
    for (unsigned i = 0; i < args.size(); i++) {
        const auto& arg = args[i];
        std::string prefix = kind + std::string("[") + std::to_string(i) + "] = ";
        switch (arg.state()) {
            case ModelArgumentInfo::POINTER:
                VLOG(EXECUTION) << prefix << "POINTER(" << SHOW_IF_DEBUG(arg.buffer()) << ") dim"
                                << toString(arg.dimensions());
                break;
            case ModelArgumentInfo::MEMORY:
                VLOG(EXECUTION) << prefix << "MEMORY("
                                << "pool=" << arg.locationAndLength().poolIndex << ", "
                                << "off=" << arg.locationAndLength().offset << ") dim"
                                << toString(arg.dimensions());
                break;
            case ModelArgumentInfo::HAS_NO_VALUE:
                VLOG(EXECUTION) << prefix << "HAS_NO_VALUE";
                break;
            case ModelArgumentInfo::UNSPECIFIED:
                VLOG(EXECUTION) << prefix << "UNSPECIFIED";
                break;
            default:
                VLOG(EXECUTION) << prefix << "state(" << arg.state() << ")";
                break;
        }
    }
}

bool StepExecutor::isCpu() const {
    return mDevice == DeviceManager::getCpuDevice();
}

std::pair<int, std::shared_ptr<RuntimeExecution>> StepExecutor::getReusableExecution() {
    CHECK(mReusable);
    if (mExecution == nullptr) {
        CHECK(mPreparedModel != nullptr);
        const MeasureTiming measure = measureTiming(mExecutionBuilder);
        const OptionalDuration loopTimeoutDuration =
                makeTimeoutDuration(mExecutionBuilder->getLoopTimeoutDuration());
        auto [n, execution] = mPreparedModel->createReusableExecution(
                mInputs, mOutputs, mMemories.getObjects(), measure, loopTimeoutDuration,
                mExecutionBuilder->getMetadata());
        if (n != ANEURALNETWORKS_NO_ERROR) {
            return {n, nullptr};
        }
        mExecution = std::move(execution);
    }
    return {ANEURALNETWORKS_NO_ERROR, mExecution};
}

std::tuple<int, std::vector<OutputShape>, Timing> StepExecutor::compute(
        const OptionalTimePoint& deadline, const SharedBurst& burstController) {
    if (VLOG_IS_ON(EXECUTION)) {
        logArguments("input", mInputs);
        logArguments("output", mOutputs);
    }

    int n;
    std::vector<OutputShape> outputShapes;
    Timing timing;
    if (mReusable) {
        auto [nCreate, execution] = getReusableExecution();
        if (nCreate != ANEURALNETWORKS_NO_ERROR) {
            return {nCreate, {}, {}};
        }
        std::tie(n, outputShapes, timing) = execution->compute(burstController, deadline);
    } else {
        CHECK(mPreparedModel != nullptr);
        const MeasureTiming measure = measureTiming(mExecutionBuilder);
        const OptionalDuration loopTimeoutDuration =
                makeTimeoutDuration(mExecutionBuilder->getLoopTimeoutDuration());
        std::tie(n, outputShapes, timing) = mPreparedModel->execute(
                mInputs, mOutputs, mMemories.getObjects(), burstController, measure, deadline,
                loopTimeoutDuration, mExecutionBuilder->getMetadata());
    }
    mExecutionBuilder->reportTimingWithoutFencedExecutionCallback(timing);
    return {n, std::move(outputShapes), std::move(timing)};
}

std::tuple<int, int, ExecuteFencedInfoCallback> StepExecutor::computeFenced(
        const std::vector<int>& waitFor, uint64_t timeoutDurationAfterFence,
        const OptionalTimePoint& deadline) {
    if (VLOG_IS_ON(EXECUTION)) {
        logArguments("input", mInputs);
        logArguments("output", mOutputs);
    }

    OptionalDuration optionalTimeoutDurationAfterFence;
    if (timeoutDurationAfterFence > 0) {
        optionalTimeoutDurationAfterFence = makeTimeoutDuration(timeoutDurationAfterFence);
    }

    int n;
    int syncFenceFd;
    ExecuteFencedInfoCallback executeFencedInfoCallback;
    Timing timing;
    if (mReusable) {
        auto [nCreate, execution] = getReusableExecution();
        if (nCreate != ANEURALNETWORKS_NO_ERROR) {
            return {nCreate, -1, nullptr};
        }
        std::tie(n, syncFenceFd, executeFencedInfoCallback, timing) =
                execution->computeFenced(waitFor, deadline, optionalTimeoutDurationAfterFence);
    } else {
        CHECK(mPreparedModel != nullptr);
        const MeasureTiming measure = measureTiming(mExecutionBuilder);
        const OptionalDuration loopTimeoutDuration =
                makeTimeoutDuration(mExecutionBuilder->getLoopTimeoutDuration());
        std::tie(n, syncFenceFd, executeFencedInfoCallback, timing) = mPreparedModel->executeFenced(
                mInputs, mOutputs, mMemories.getObjects(), waitFor, measure, deadline,
                loopTimeoutDuration, optionalTimeoutDurationAfterFence,
                mExecutionBuilder->getMetadata());
    }
    if (syncFenceFd < 0 && executeFencedInfoCallback == nullptr) {
        mExecutionBuilder->reportTimingWithoutFencedExecutionCallback(timing);
    }
    return {n, syncFenceFd, executeFencedInfoCallback};
}

// For cpuFallback{Partial,Full}, recompile the model on CPU and then start compute.
std::tuple<int, std::vector<OutputShape>, Timing> StepExecutor::computeOnCpuFallback() {
    NNTRACE_RT(NNTRACE_PHASE_EXECUTION, "StepExecutor::computeOnCpuFallback");
    VLOG(EXECUTION) << "Re-compile the model on CPU";
    const ModelFactory makeModel = [this] { return mModel->makeModel(); };
    // TODO: Propagate user preference and compilation priority to this point instead of using
    // default values of ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER and
    // ANEURALNETWORKS_PRIORITY_MEDIUM
    const ExecutionPreference preference =
            static_cast<ExecutionPreference>(ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER);
    const Priority priority = convertToCanonicalPriority(ANEURALNETWORKS_PRIORITY_DEFAULT);
    auto [n, preparedModel] = DeviceManager::getCpuDevice()->prepareModel(
            makeModel, preference, priority, {}, {}, {}, {}, {});
    if (n != ANEURALNETWORKS_NO_ERROR) {
        return {n, {}, {}};
    }

    // Prepare device memories for CPU fallback.
    std::vector<const RuntimeMemory*> memories = mMemories.getObjects();
    std::vector<bool> isUsedAsInput(memories.size(), false);
    std::vector<bool> isUsedAsOutput(memories.size(), false);
    std::vector<std::unique_ptr<RuntimeMemory>> blobAhwbs;

    // Mark the input and output usages.
    for (auto& input : mInputs) {
        if (input.state() == ModelArgumentInfo::MEMORY) {
            const uint32_t poolIndex = input.locationAndLength().poolIndex;
            isUsedAsInput[poolIndex] = true;
        }
    }
    for (auto& output : mOutputs) {
        if (output.state() == ModelArgumentInfo::MEMORY) {
            const uint32_t poolIndex = output.locationAndLength().poolIndex;
            // Cannot allocate output buffers with unknown shapes.
            if (mMemories[poolIndex]->getValidator().createdWithUnknownShape()) {
                LOG(ERROR) << "Cannot fallback to CPU because at least one of the output operands "
                              "has unknown shape.";
                return {ANEURALNETWORKS_OP_FAILED, {}, {}};
            }
            isUsedAsOutput[poolIndex] = true;
        }
    }

    // Allocate BLOB mode AHardwareBuffers and read the data from input device memories.
    for (uint32_t i = 0; i < memories.size(); i++) {
        const RuntimeMemory* memory = mMemories[i];
        if (memory->getIBuffer() != nullptr) {
            const uint32_t size = memory->getValidator().getMetadata().logicalSize;
            auto [nAhwb, blobAhwb] = MemoryRuntimeAHWB::create(size);
            if (nAhwb != ANEURALNETWORKS_NO_ERROR) {
                return {nAhwb, {}, {}};
            }
            if (isUsedAsInput[i]) {
                n = copyIBufferToMemory(memory->getIBuffer(), blobAhwb->getMemory());
                if (n != ANEURALNETWORKS_NO_ERROR) {
                    return {n, {}, {}};
                }
            }
            memories[i] = blobAhwb.get();
            blobAhwbs.push_back(std::move(blobAhwb));
        }
    }

    const MeasureTiming measure = measureTiming(mExecutionBuilder);
    const OptionalDuration loopTimeoutDuration =
            makeTimeoutDuration(mExecutionBuilder->getLoopTimeoutDuration());
    auto [nExecute, outputShapes, timing] = preparedModel->execute(
            mInputs, mOutputs, memories, nullptr, measure, {}, loopTimeoutDuration, {});
    mExecutionBuilder->reportTimingWithoutFencedExecutionCallback(timing);
    if (nExecute != ANEURALNETWORKS_NO_ERROR) {
        return {nExecute, std::move(outputShapes), timing};
    }

    // Write back to output device memories.
    for (uint32_t i = 0; i < memories.size(); i++) {
        const RuntimeMemory* memory = mMemories[i];
        if (memory->getIBuffer() != nullptr && isUsedAsOutput[i]) {
            n = copyMemoryToIBuffer(memories[i]->getMemory(), memory->getIBuffer(), {});
            if (n != ANEURALNETWORKS_NO_ERROR) {
                return {n, {}, {}};
            }
        }
    }
    return {ANEURALNETWORKS_NO_ERROR, std::move(outputShapes), timing};
}

}  // namespace nn
}  // namespace android
