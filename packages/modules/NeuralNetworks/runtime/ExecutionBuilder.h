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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_BUILDER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_BUILDER_H

#include <ControlFlow.h>
#include <CpuExecutor.h>
#include <android-base/thread_annotations.h>
#include <nnapi/IBurst.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Types.h>
#include <nnapi/Validation.h>

#include <memory>
#include <set>
#include <string>
#include <tuple>
#include <utility>
#include <vector>

#include "ExecutionCallback.h"
#include "Memory.h"
#include "ModelArgumentInfo.h"
#include "ModelBuilder.h"
#include "NeuralNetworks.h"

namespace android {
namespace nn {

class BurstBuilder;
class CompilationBuilder;
class Device;
class DynamicTemporaries;
class ExecutionPlan;
class ExecutionStep;
class ModelBuilder;
class RuntimeMemory;
class RuntimePreparedModel;
class RuntimeExecution;
class StepExecutor;

// Execution modes
enum class ExecutionMode { ASYNC, SYNC, BURST, ASYNC_WITH_DEPS };

class ExecutionBuilder {
    friend class StepExecutor;

   public:
    explicit ExecutionBuilder(const CompilationBuilder* compilation);
    virtual ~ExecutionBuilder() = default;

    int setInput(uint32_t index, const ANeuralNetworksOperandType* type, const void* buffer,
                 size_t length);
    int setInputFromMemory(uint32_t index, const ANeuralNetworksOperandType* type,
                           const RuntimeMemory* memory, size_t offset, size_t length);
    int setOutput(uint32_t index, const ANeuralNetworksOperandType* type, void* buffer,
                  size_t length);
    int setOutputFromMemory(uint32_t index, const ANeuralNetworksOperandType* type,
                            const RuntimeMemory* memory, size_t offset, size_t length);

    int setMeasureTiming(bool measure);

    int getDuration(int32_t durationCode, uint64_t* duration) const;

    int setTimeoutDuration(uint64_t duration);

    std::optional<uint64_t> getTimeoutDuration() const;

    int setLoopTimeout(uint64_t duration);

    uint64_t getLoopTimeoutDuration() const { return mLoopTimeoutDuration; }

    int enableInputAndOutputPadding(bool enable);

    int setReusable(bool reusable);

    int addExtensionAttribute(const char* extensionName, uint16_t attributeCodeWithinExtension,
                              const void* data, size_t length);

    int computeFenced(const std::vector<int>& wait_for, uint64_t timeoutDurationAfterFence,
                      int* sync_fence);

    int computeAsynchronously(std::shared_ptr<ExecutionCallback>* synchronizationCallback) {
        CHECK(synchronizationCallback != nullptr);
        return compute(synchronizationCallback);
    }
    int computeSynchronously() { return compute(nullptr); }
    int burstCompute(BurstBuilder* burst) { return compute(nullptr, burst); }

    // Initialize output dimensional information from ModelArgumentInfo.
    std::vector<OutputShape> getInitialOutputShapes() const;

    int getOutputOperandDimensions(uint32_t index, uint32_t* dimensions);
    int getOutputOperandRank(uint32_t index, uint32_t* rank);

    // Handshake with lower-level execution support
    bool measureTiming() const { return mMeasureTiming; }
    void reportTimingWithoutFencedExecutionCallback(Timing timing) {
        mTimingWithoutFencedExecutionCallback = timing;
    }

    const CompilationBuilder* getCompilation() const { return mCompilation; }
    const ModelBuilder* getModel() const { return mModel; }
    const ModelBuilder* getSourceModel(uint32_t index) const;
    const Operand& getSourceOperand(const std::pair<uint32_t, uint32_t>& sourceOperandIndex) const {
        return getSourceModel(sourceOperandIndex.first)->getOperand(sourceOperandIndex.second);
    }

    // This method will be called at the end of all computation paths to change the state
    // of the execution object and update output shapes / memories.
    int finishComputation(int result, const std::vector<OutputShape>& outputShapes,
                          ExecutionMode mode);
    ErrorStatus finishComputation(ErrorStatus error, const std::vector<OutputShape>& outputShapes,
                                  ExecutionMode mode) {
        const int result =
                finishComputation(convertErrorStatusToResultCode(error), outputShapes, mode);
        return convertResultCodeToErrorStatus(result);
    }

    const ExecuteFencedInfoCallback& getExecuteFencedInfoCallback() {
        return mFencedExecutionCallback;
    }

    bool inFlight() const {
        std::lock_guard<std::mutex> lock(mStateMutex);
        return mState == State::COMPUTATION;
    }
    bool completed() const {
        std::lock_guard<std::mutex> lock(mStateMutex);
        return mState == State::COMPLETED;
    }

    // Retrieve a computation start point
    TimePoint getComputeStartTimePoint() const;

    const ModelArgumentInfo& getInputInfo(uint32_t index) const { return mInputs[index]; }
    const ModelArgumentInfo& getOutputInfo(uint32_t index) const { return mOutputs[index]; }

    std::optional<RunTimePoolInfo> getRunTimePoolInfo(uint32_t poolIndex) const {
        return mMemories[poolIndex]->getRunTimePoolInfo();
    }

    const std::vector<TokenValuePair>& getMetadata() const { return mMetadata; }

   protected:
    // If a callback is provided, then this is asynchronous. If a callback is
    // not provided (i.e., is nullptr), then this is synchronous.
    //
    // If burst is provided, then the burst path will be used. If a burst is not
    // provided (i.e., is nullptr), then a synchronous execution will occur.
    //
    // Providing both synchronizationCallback and burstBuilder is an error.
    int compute(std::shared_ptr<ExecutionCallback>* synchronizationCallback,
                BurstBuilder* burstBuilder = nullptr);

    virtual std::tuple<int, std::vector<OutputShape>, Timing> computeInternal(
            const OptionalTimePoint& deadline, BurstBuilder* burstBuilder) = 0;

    virtual std::tuple<int, int, ExecuteFencedInfoCallback> computeFencedInternal(
            const std::vector<int>& waitFor, uint64_t timeoutDurationAfterFence,
            const OptionalTimePoint& deadline) = 0;

    // This method handles the common preparation and validation logic of compute and computeFenced.
    // It will be called at the start of every computation.
    int prepareForCompute(const char* name, ExecutionMode mode);

    const CompilationBuilder* mCompilation;

    // Update output dimensional information from OutputShape to ModelArgumentInfo.
    bool updateOutputShapes(ErrorStatus status, const std::vector<OutputShape>& outputShapes);

    bool updateMemories();

    const ModelBuilder* mModel;
    const ExecutionPlan* mPlan;

    // Whether CPU fallback is allowed based on the value of DeviceManager::kPartitioning* captured
    // from CompilationBuilder when the ExecutionBuilder is constructed.
    bool mAllowCpuFallback;

    // The information we'll send to the driver about the inputs and outputs.
    // Note that we build this in two steps:
    // 1. As the arguments are specified, set the corresponding mInputs or mOutputs element.
    //    If set from a pointer, don't set the location in the Request::Argument but store it
    //    instead in mInputBuffers or mOutputBuffers.
    // 2. Once we have all the inputs and outputs, if needed, allocate shared memory for
    //    the m*Buffers entries.  Copy the input values into the shared memory.
    // We do this to avoid creating a lot of shared memory objects if we have a lot of
    // parameters specified via pointers.  We also avoid copying in the case where
    // some of the nodes will interpreted on the CPU anyway.
    std::vector<ModelArgumentInfo> mInputs;
    std::vector<ModelArgumentInfo> mOutputs;
    MemoryTracker mMemories;

    // Do we ask the driver to measure timing?
    bool mMeasureTiming = false;

    // Timepoint of computation start, used to evaluate timing
    // from runtime perspective
    TimePoint mComputeStartTimePoint;

    // Timing reported from the driver.  This field is only used if
    // mFencedExecutionCallback is nullptr.
    Timing mTimingWithoutFencedExecutionCallback = {};

    // Amount of time to complete or abort the execution.
    std::optional<uint64_t> mTimeoutDuration;

    // Amount of time to complete or abort a loop.
    uint64_t mLoopTimeoutDuration = operation_while::kTimeoutNsDefault;

    // The state of the execution.
    // Properties can only been set when the execution is in the state State::PREPARATION.
    // Timing and output shapes can only be queried when the execution is in the state
    // State::COMPLETED.
    enum class State { PREPARATION, COMPUTATION, COMPLETED };
    State mState GUARDED_BY(mStateMutex) = State::PREPARATION;
    bool computationStarted() const {
        std::lock_guard<std::mutex> lock(mStateMutex);
        return mState != State::PREPARATION;
    }

    // Mutex to guard mState. Note that this not strictly needed because we provide
    // no thread-safety guarantee to the ANeuralNetworksExecution object.
    mutable std::mutex mStateMutex;

    // Return false if the execution is in a bad state for starting computation.
    // Otherwise, return true and set the state to State::COMPUTATION.
    bool checkAndSetComputationState(const char* name);

    // With what error status has execution completed?
    enum class Completion { NO_ERROR, OUTPUT_INSUFFICIENT_SIZE, OTHER_ERROR };
    Completion mCompletion = Completion::OTHER_ERROR;
    Completion completedWith() const {
        CHECK(completed());
        return mCompletion;
    }

    // The result code of request validation.
    // It is only evaluated once at the first time it's needed.
    std::optional<int> mValidationResultCode;
    int getValidationResultCode();

    // Does every tensor output operand of the model have a fully specified shape?
    // It is only evaluated once at the first time it's needed.
    std::optional<bool> mOutputsFullySpecified;
    bool areOutputsFullySpecified();

    // The callback used to query execution related info in the case of fenced
    // execution; otherwise, nullptr.  If the execution plan has multiple steps,
    // this is the callback associated with the last step.  If the last step
    // doesn't support fenced execution (e.g., the driver is too old), or if the
    // launch of execution on the driver fails, then this callback will be
    // nullptr.
    ExecuteFencedInfoCallback mFencedExecutionCallback;

    // Whether set{Input,Output}[FromMemory] can accept padded length or not.
    bool mInputAndOutputPaddingEnabled = false;

    // enableInputAndOutputPadding may only be called before any call of
    // set{Input,Output}[FromMemory]
    bool mHasCalledSetInputOutput = false;

    // Can compute APIs be invoked multiple times on the execution object?
    bool mReusable = false;

    // Vendor specific metadata
    std::vector<TokenValuePair> mMetadata;
};

// For execution plan with a SIMPLE body, i.e. the whole model will be executed on a single device.
class SimpleExecutionBuilder : public ExecutionBuilder {
   public:
    SimpleExecutionBuilder(const CompilationBuilder* compilation);

    std::tuple<int, std::vector<OutputShape>, Timing> computeInternal(
            const OptionalTimePoint& deadline, BurstBuilder* burstBuilder) override;

    std::tuple<int, int, ExecuteFencedInfoCallback> computeFencedInternal(
            const std::vector<int>& waitFor, uint64_t timeoutDurationAfterFence,
            const OptionalTimePoint& deadline) override;

   private:
    std::shared_ptr<StepExecutor> mExecutor;
};

// For execution plan with a COMPOUND body, i.e. partitioned execution with multiple steps.
class CompoundExecutionBuilder : public ExecutionBuilder {
   public:
    CompoundExecutionBuilder(const CompilationBuilder* compilation);

    std::tuple<int, std::vector<OutputShape>, Timing> computeInternal(
            const OptionalTimePoint& deadline, BurstBuilder* burstBuilder) override;

    std::tuple<int, int, ExecuteFencedInfoCallback> computeFencedInternal(
            const std::vector<int>& waitFor, uint64_t timeoutDurationAfterFence,
            const OptionalTimePoint& deadline) override;
};

// class StepExecutor is used to execute a single "step" in a
// potentially multiple step execution process.  The graph associated
// with that step is executed in its entirety on a single device (or
// on the CPU).
class StepExecutor {
   public:
    // executionBuilder
    //     Describes the full (possibly multiple-"step") execution.
    // model
    //     The model to be executed by the executor.  Possibly a single
    //     "step" model of a multiple-"step" executionBuilder.
    // driver, preparedModel
    //     The device on which to execute the "step", and the prepared
    //     model to execute on that device. For non-fallback StepExecutor,
    //     neither is nullptr; for fallback StepExecutor, both are ignored in
    //     StepExecutor::computeOnCpuFallback and may be nullptr.
    // reusable
    //     If true, multiple StepExecutor::compute/computeFenced may be called on this
    //     object; otherwise, only one StepExecutor::compute/computeFenced may be called.
    //     reusable must be false if mDynamicTemporaries != nullptr.
    // step
    //     Contains the output index mapping from the excerpted "step" model to
    //     main model if the execution has multiple "steps". Must be nullptr
    //     otherwise.
    //     (step == nullptr) == (dynamicTemporaries == nullptr)
    // dynamicTemporaries
    //     If the execution has multiple "steps", describes the temporaries
    //     of source models that do not have fully specified types and are outputs
    //     of "step" models. Must be nullptr otherwise.
    //     (step == nullptr) == (dynamicTemporaries == nullptr)
    StepExecutor(ExecutionBuilder* executionBuilder, const ModelBuilder* model,
                 std::shared_ptr<Device> device,
                 std::shared_ptr<RuntimePreparedModel> preparedModel, bool reusable,
                 const ExecutionStep* step = nullptr,
                 DynamicTemporaries* dynamicTemporaries = nullptr);

    // Map inputs and outputs from ExecutionBuilder to StepExecutor,
    // in the case where we have a single-"step" execution (i.e., the executor
    // is executing the entire model from the ExecutionBuilder).
    void mapInputsAndOutputsTrivially();

    // Update output shapes with shapes returned from execution.
    struct UpdateOutputShapes {
        // These fields are meaningless unless updateOutputShapes() returns true
        bool updatedDynamicTemporary;  // did shape (dimensions, size) information change for at
                                       // least one dynamic temporary?
        bool mainOutputInsufficient;  // is at least one main model output written by this execution
                                      // marked !isSufficient?
        bool zeroSizedInput;  // is at least one output of this execution step a zero-sized tensor
                              // that needs to be read by some other step of the same execution?
    };
    bool updateOutputShapes(int executionResultCode, const std::vector<OutputShape>& from,
                            std::vector<OutputShape>* to, UpdateOutputShapes* update);

    // Map inputs and outputs from ExecutionBuilder to StepExecutor,
    // one at a time.  Note that these are input/output indexes, not
    // operand indexes.
    //
    // For mapOutputToInput(), outputDimensions may be nullptr if the input
    // operand has fully specified dimensions.
    void mapInput(uint32_t builderIndex, uint32_t executorIndex) {
        mapInputOrOutput(mExecutionBuilder->mInputs[builderIndex], &mInputs[executorIndex]);
    }
    void mapOutput(uint32_t builderIndex, uint32_t executorIndex) {
        mapInputOrOutput(mExecutionBuilder->mOutputs[builderIndex], &mOutputs[executorIndex]);
    }
    void mapOutputToInput(uint32_t builderIndex, uint32_t executorIndex,
                          const Dimensions* outputDimensions) {
        mapInputOrOutput(mExecutionBuilder->mOutputs[builderIndex], &mInputs[executorIndex],
                         outputDimensions);
    }

    // dimensions must either have zero rank or must be
    // consistent with and at least as well specified as operand dimensions
    // (i.e., either rank must match, or operand rank must be zero; and for each
    // individual dimension, either dimension must match, or operand dimension
    // must be zero).
    int setInputFromMemory(uint32_t inputIndex, const RuntimeMemory* memory, uint32_t offset,
                           uint32_t length, const Dimensions& dimensions = {}) {
        return setInputOrOutputFromMemory(mModel->getInputOperand(inputIndex), memory, offset,
                                          length, dimensions, &mInputs.at(inputIndex));
    }
    int setOutputFromMemory(uint32_t outputIndex, const RuntimeMemory* memory, uint32_t offset,
                            uint32_t length, const Dimensions& dimensions = {}) {
        return setInputOrOutputFromMemory(mModel->getOutputOperand(outputIndex), memory, offset,
                                          length, dimensions, &mOutputs.at(outputIndex));
    }

    // Executes using the (driver, preparedModel) specified at construction time.
    std::tuple<int, std::vector<OutputShape>, Timing> compute(
            const OptionalTimePoint& deadline, const SharedBurst& burstController = nullptr);

    // Re-compiles and executes using the CPU, regardless of the (driver,
    // preparedModel) specified at construction time.
    std::tuple<int, std::vector<OutputShape>, Timing> computeOnCpuFallback();

    bool isCpu() const;

    // Perform fenced execution and return error_code, sync_fence_fd and a
    // callback.
    std::tuple<int, int, ExecuteFencedInfoCallback> computeFenced(
            const std::vector<int>& wait_for, uint64_t timeoutDurationAfterFence,
            const OptionalTimePoint& deadline);

    // Do the dynamic temporaries defined by this step have valid allocations?
    // (true if there are no dynamic temporaries defined by this step.)
    bool areDynamicTemporariesAllocated() const;

   private:
    // builderDimensions may be nullptr if executorInputOrOutput has fully
    // specified dimensions.
    void mapInputOrOutput(const ModelArgumentInfo& builderInputOrOutput,
                          ModelArgumentInfo* executorInputOrOutput,
                          const Dimensions* builderDimensions = nullptr);

    // dimensions must either have zero rank or
    // must be consistent with and at least as well specified as operand
    // dimensions (i.e., either rank must match, or operand rank must be zero;
    // and for each individual dimension, either dimension must match, or
    // operand dimension must be zero).
    int setInputOrOutputFromMemory(const Operand& inputOrOutputOperand, const RuntimeMemory* memory,
                                   uint32_t offset, uint32_t length, const Dimensions& dimensions,
                                   ModelArgumentInfo* inputOrOutputInfo);

    // describes the full (possibly multiple-"step") execution
    ExecutionBuilder* mExecutionBuilder;

    // describes the single execution step
    const ExecutionStep* mExecutionStep;

    // describes the dynamic temporaries
    DynamicTemporaries* mDynamicTemporaries;

    // model to be executed on the executor, in both original and
    // compiled forms; and device on which to execute it
    const ModelBuilder* mModel;
    std::shared_ptr<Device> mDevice;
    std::shared_ptr<RuntimePreparedModel> mPreparedModel;

    // The reusable execution to launch multiple computations.
    // It is only created once at the first time it's needed.
    std::shared_ptr<RuntimeExecution> mExecution;
    // Returns {NO_ERROR, execution} on success, or {result_code, nullptr} on failure.
    std::pair<int, std::shared_ptr<RuntimeExecution>> getReusableExecution();

    // The information we'll send to the driver about the inputs and outputs.
    // Note that we build this in two steps:
    // 1. As the arguments are specified, set the corresponding mInputs or mOutputs element.
    //    If set from a pointer, don't set the location in the Request::Argument but store it
    //    instead in mInputBuffers or mOutputBuffers.
    // 2. Once we have all the inputs and outputs, if needed, allocate shared memory for
    //    the m*Buffers entries.  Copy the input values into the shared memory.
    // We do this to avoid creating a lot of shared memory objects if we have a lot of
    // parameters specified via pointers.  We also avoid copying in the case where
    // some of the nodes will interpreted on the CPU anyway.
    std::vector<ModelArgumentInfo> mInputs;
    std::vector<ModelArgumentInfo> mOutputs;
    MemoryTracker mMemories;

    // Whether compute/computeFenced may be invoked multiple times.
    bool mReusable = false;
};

std::string toString(StepExecutor::UpdateOutputShapes updateOutputShapes);

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_BUILDER_H
