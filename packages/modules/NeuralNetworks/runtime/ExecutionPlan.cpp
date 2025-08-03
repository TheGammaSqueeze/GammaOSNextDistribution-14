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

#define LOG_TAG "ExecutionPlan"

#include "ExecutionPlan.h"

#include <ControlFlow.h>
#include <CpuExecutor.h>
#include <GraphDump.h>
#include <LegacyUtils.h>
#include <MetaModel.h>
#include <OperationsUtils.h>
#include <TokenHasher.h>
#include <Tracing.h>
#include <android-base/logging.h>
#include <fcntl.h>
#include <nnapi/IBurst.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <algorithm>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <queue>
#include <set>
#include <string>
#include <type_traits>
#include <unordered_set>
#include <utility>
#include <vector>

#include "BurstBuilder.h"
#include "CompilationBuilder.h"
#include "ExecutionBuilder.h"
#include "ExecutionCallback.h"
#include "Manager.h"
#include "ModelBuilder.h"
#include "TypeManager.h"

namespace android {
namespace nn {

namespace {

// The index of the main model in SourceModels.
constexpr uint32_t kMainModelInSourceModels = 0;

constexpr uint32_t kNoPadding = 1;

static bool updateTokenFromMetaData(TokenHasher* token,
                                    const std::vector<TokenValuePair>& metaData) {
    // Combines the TokenValuePair and corresponding extension name.
    std::vector<std::tuple<const char*, uint16_t, const uint8_t*, size_t>> metaDataWithExtension;
    for (auto p : metaData) {
        uint16_t prefix = static_cast<uint32_t>(p.token) >> kExtensionTypeBits;
        uint16_t extensionEnum = static_cast<uint32_t>(p.token) & kTypeWithinExtensionMask;
        const Extension* extension;
        if (!TypeManager::get()->getExtensionInfo(prefix, &extension)) {
            LOG(ERROR) << "Prefix " << prefix << " could not be found";
            return false;
        }
        metaDataWithExtension.push_back(std::make_tuple(extension->name.c_str(), extensionEnum,
                                                        p.value.data(), p.value.size()));
    }
    // Sort with extension name and extension enum.
    std::sort(metaDataWithExtension.begin(), metaDataWithExtension.end(),
              [](const auto& a, const auto& b) {
                  if (int r = strcmp(std::get<0>(a), std::get<0>(b))) {
                      return r < 0;
                  } else {
                      return std::get<1>(a) < std::get<1>(b);
                  }
              });
    // Update the cache token with the sorted array.
    for (auto [extensionName, extensionEnum, value, valueSize] : metaDataWithExtension) {
        if (!token->updateFromString(extensionName) ||
            !token->update(&extensionEnum, sizeof(uint16_t)) || !token->update(value, valueSize)) {
            return false;
        }
    }
    return true;
}

// Compiles the model on device.
// If compilation caching is available, depending on ExecutionPlan::mState, the token may only have
// been initialized by the user provided token (SIMPLE body), or is already re-hashed by the
// operation indices to be executed (COMPOUND body). The token will be re-hashed further by the
// device name, device version string, and the execution preference in this function.
int compile(const Device& device, const ModelBuilder& model, int executionPreference,
            int compilationPriority, const OptionalTimePoint& deadline, const CacheInfo& cacheInfo,
            TokenHasher* token, const std::vector<TokenValuePair>& metaData,
            std::shared_ptr<RuntimePreparedModel>* preparedModel) {
    CHECK(token != nullptr);
    CHECK(preparedModel != nullptr);
    *preparedModel = nullptr;

    std::optional<CacheToken> cacheToken;
    if (device.isCachingSupported() && token->ok() &&
        token->updateFromString(device.getName().c_str()) &&
        token->updateFromString(device.getVersionString().c_str()) &&
        token->update(&executionPreference, sizeof(executionPreference)) &&
        token->update(&compilationPriority, sizeof(compilationPriority)) &&
        updateTokenFromMetaData(token, metaData) && token->finish()) {
        cacheToken = CacheToken{};
        const uint8_t* tokenPtr = token->getCacheToken();
        std::copy(tokenPtr, tokenPtr + cacheToken->size(), cacheToken->begin());
    }

    const ModelFactory makeModel = [&model] { return model.makeModel(); };
    const ExecutionPreference preference = static_cast<ExecutionPreference>(executionPreference);
    const Priority priority = convertToCanonicalPriority(compilationPriority);
    std::vector<ExtensionNameAndPrefix> extensionNameAndPrefix =
            TypeManager::get()->getExtensionNameAndPrefix(metaData);
    const auto [n, returnedPreparedModel] =
            device.prepareModel(makeModel, preference, priority, deadline, cacheInfo, cacheToken,
                                metaData, extensionNameAndPrefix);
    *preparedModel = returnedPreparedModel;
    return n;
}

typedef std::function<void(uint32_t)> OperationReadyCallback;

int copyOperandExtraParams(ModelBuilder& model, uint32_t toOperandIndex,
                           const Operand& fromOperand) {
    if (fromOperand.type == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL &&
        std::holds_alternative<Operand::SymmPerChannelQuantParams>(fromOperand.extraParams)) {
        auto& fromChannelQuant =
                std::get<Operand::SymmPerChannelQuantParams>(fromOperand.extraParams);
        ANeuralNetworksSymmPerChannelQuantParams toChannelQuant = {
                .channelDim = fromChannelQuant.channelDim,
                .scaleCount = static_cast<uint32_t>(fromChannelQuant.scales.size()),
                .scales = fromChannelQuant.scales.data(),
        };
        return model.setOperandSymmPerChannelQuantParams(toOperandIndex, toChannelQuant);
    } else if (isExtension(fromOperand.type) &&
               std::holds_alternative<Operand::ExtensionParams>(fromOperand.extraParams)) {
        auto extensionData = std::get<Operand::ExtensionParams>(fromOperand.extraParams);
        return model.setOperandExtensionData(toOperandIndex, extensionData.data(),
                                             extensionData.size());
    } else if (!std::holds_alternative<Operand::NoParams>(fromOperand.extraParams) ||
               fromOperand.type == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL) {
        LOG(ERROR) << "Type " << fromOperand.type
                   << " has an unexpected extraParams variant: " << fromOperand.extraParams.index();
        return ANEURALNETWORKS_BAD_DATA;
    } else {
        return ANEURALNETWORKS_NO_ERROR;
    }
}

// This class tracks whether we know the value of an operand as operations
// are processed.
class OperandTracker {
   public:
    // Creates the tracker for this model. Figure out which operations can be
    // executed right away and cb for each one of them.
    OperandTracker(const ModelBuilder* model, OperationReadyCallback cb);
    // Mark the specified operation as having been processed. The output
    // of the operation now being known, this may make new operations to be
    // able to run.  Call cb for each one of them.
    void markProcessed(uint32_t operationIndex, OperationReadyCallback cb);

   private:
    const ModelBuilder* mModel;
    std::multimap<uint32_t, uint32_t> mOperandToOperations;
    std::vector<uint32_t> mUnknownInputCount;  // For each operation
};

OperandTracker::OperandTracker(const ModelBuilder* model, OperationReadyCallback cb)
    : mModel(model) {
    const auto& operations = mModel->getOperations();
    mUnknownInputCount.resize(operations.size());
    for (uint32_t operationIndex = 0; operationIndex < operations.size(); operationIndex++) {
        const Operation& operation = operations[operationIndex];
        uint32_t count = 0;
        for (uint32_t operandIndex : operation.inputs) {
            auto lifetime = mModel->getOperand(operandIndex).lifetime;
            if (lifetime == Operand::LifeTime::TEMPORARY_VARIABLE ||
                lifetime == Operand::LifeTime::SUBGRAPH_OUTPUT) {
                count++;
                mOperandToOperations.emplace(operandIndex, operationIndex);
            }
        }
        if (count == 0) {
            cb(operationIndex);
        }
        mUnknownInputCount[operationIndex] = count;
    }
}

void OperandTracker::markProcessed(uint32_t operationIndex, OperationReadyCallback cb) {
    // Mark all its outputs as known.
    const Operation& operation = mModel->getOperations()[operationIndex];
    for (uint32_t operandIndex : operation.outputs) {
        auto range = mOperandToOperations.equal_range(operandIndex);
        for (auto i = range.first; i != range.second; i++) {
            uint32_t& count = mUnknownInputCount[i->second];
            if (--count == 0) {
                cb(i->second);
            }
        }
    }
}

StaticTemporaryLocation addTemporary(uint32_t* totalSizeOfTemporaries, uint32_t size,
                                     uint32_t alignment, uint32_t padding) {
    // TODO: what about overflow?
    *totalSizeOfTemporaries = roundUp(*totalSizeOfTemporaries, alignment);
    const uint32_t offset = *totalSizeOfTemporaries;
    size = roundUp(size, padding);
    *totalSizeOfTemporaries += size;
    return {.offset = offset, .paddedLength = size};
};

std::string toString(SourceOperandIndex sourceOperandIndex) {
    return "(" + std::to_string(sourceOperandIndex.first) + ", " +
           std::to_string(sourceOperandIndex.second) + ")";
};

// A helper class to analyze the step roles of all partition boundary operands.
//
// To use, call StepRoleAnalyzer::analyze and pass in a setup function that configures the analyzer
// with the following two methods:
//   - addRole: Add a step role to a boundary operand
//   - setUsedBy: Specify that the memory of the "source" operand may be directly used by the "dest"
//     operand. All of the step roles of the "dest" operand are also possible step roles of the
//     "source" operand. This is useful for interpreted control flow, e.g., the outer input operand
//     of an interpreted IF operation may be directly used as all step roles of the corresponding
//     input operand of the then and else models. Note that this relationship is directional --
//     (A->B && B->C) implies A->C, but (A->C && B->C) does not imply A->B or B->A (A->B is a
//     shorthand for setUsedBy(A, B)). The setup function must guarantee that the final graph
//     produced by the used-by relationship is acyclic. This is true for the partitioner algorithm
//     because there must be a root operand of each step role for the memory to be allocated on
//     behalf of.
//
class StepRoleAnalyzer {
   public:
    static std::map<SourceOperandIndex, std::set<StepRole>> analyze(
            const std::function<void(StepRoleAnalyzer&)>& setup) {
        StepRoleAnalyzer analyzer;
        setup(analyzer);
        return analyzer.finish();
    }

    void addRole(const ExecutionStep& step, uint32_t operandIndex, IOType type,
                 uint32_t stepIOIndex) {
        SourceOperandIndex source = {step.getSourceModelIndex(), operandIndex};
        mRoles[source].emplace(step.getIndex(), type, stepIOIndex);
    }

    void setUsedBy(const SourceOperandIndex& source, const SourceOperandIndex& dest) {
        mUsedBy[source].emplace(dest);
    }

   private:
    StepRoleAnalyzer() = default;

    // Merges the step roles of the destination operands to the source operands
    // and returns the final map.
    std::map<SourceOperandIndex, std::set<StepRole>> finish() {
        for (const auto& [source, _] : mUsedBy) {
            finishHelper(source);
        }
        return std::move(mRoles);
    }

    void finishHelper(SourceOperandIndex current) {
        if (mProcessedOperands.count(current) > 0) return;
        mProcessedOperands.insert(current);
        const auto it = mUsedBy.find(current);
        if (it != mUsedBy.end()) {
            auto& roles = mRoles[current];
            // Merge the step roles of the destination operands.
            for (const auto& dest : it->second) {
                finishHelper(dest);
                const auto& destRoles = mRoles[dest];
                roles.insert(destRoles.begin(), destRoles.end());
            }
        }
    }

    // A map from the source operand to its step roles.
    std::map<SourceOperandIndex, std::set<StepRole>> mRoles;
    // A map from the source operand to a set of destination operands that may directly
    // use the memory of the source operand.
    std::map<SourceOperandIndex, std::set<SourceOperandIndex>> mUsedBy;
    // Used in finish to track which operand has been processed.
    std::set<SourceOperandIndex> mProcessedOperands;
};

}  // namespace

void DynamicTemporaries::vlogDump(const char* context) const {
    if (empty()) {
        return;
    }
    if (context) {
        VLOG(EXECUTION) << "DynamicTemporaries: \"" << context << "\"";
    }
    for (const auto& temp : mSourceOperandToTemporary) {
        VLOG(EXECUTION) << "DynamicTemporaries: sourceOperandIndex = " << toString(temp.first)
                        << ", stepIndex = " << temp.second.stepIndex
                        << ", offset = " << temp.second.offset
                        << ", dimensions = " << toString(temp.second.dimensions)
                        << ", paddedLength = " << temp.second.paddedLength
                        << ", alignment = " << temp.second.alignment
                        << ", padding = " << temp.second.padding;
    }
}

void DynamicTemporaries::declare(SourceOperandIndex sourceOperandIndex, uint32_t stepIndex,
                                 const Dimensions& initialDimensions, uint32_t initialLength,
                                 uint32_t alignment, uint32_t padding) {
    VLOG(EXECUTION) << "DynamicTemporaries::declare(sourceOperandIndex = "
                    << toString(sourceOperandIndex) << ", stepIndex = " << stepIndex
                    << ", initialDimensions = " << toString(initialDimensions)
                    << ", initialLength = " << initialLength << ", alignment = " << alignment
                    << ", padding = " << padding << ")";
    CHECK(!mDeclared);
    CHECK_GT(initialLength, 0u);
    const uint32_t paddedLength = roundUp(initialLength, padding);
    auto [_, isNew] = mSourceOperandToTemporary.emplace(
            sourceOperandIndex, InternalLocationAndShape{stepIndex, 0, initialDimensions,
                                                         paddedLength, alignment, padding});
    CHECK(isNew);
    mStepIndexToSourceOperandIndexes[stepIndex].emplace_back(sourceOperandIndex);
}

bool DynamicTemporaries::redeclare(SourceOperandIndex sourceOperandIndex,
                                   const Dimensions& newDimensions, uint32_t newLength) {
    auto createAndLogResult = [sourceOperandIndex, &newDimensions, newLength](bool changedShape) {
        VLOG(EXECUTION) << "DynamicTemporaries::redeclare(sourceOperandIndex = "
                        << toString(sourceOperandIndex)
                        << ", newDimensions = " << toString(newDimensions)
                        << ", newLength = " << newLength << ") -> " << toString(changedShape);
        return changedShape;
    };

    CHECK(mDeclared);
    CHECK_GT(newLength, 0u);

    InternalLocationAndShape& temp = mSourceOperandToTemporary.at(sourceOperandIndex);
    const uint32_t paddedLength = roundUp(newLength, temp.padding);
    if (temp.paddedLength == paddedLength && temp.dimensions == newDimensions) {
        return createAndLogResult(false);
    }
    if (temp.paddedLength < paddedLength) {
        // Otherwise allocation remains valid, even if it may be suboptimal
        // (because it uses more space than needed).  Use case: Don't force
        // client to allocate again just because the client reported more
        // accurate shape information.
        mAllocatedStepIndexes.erase(temp.stepIndex);
    }
    temp.paddedLength = paddedLength;
    temp.dimensions = newDimensions;
    return createAndLogResult(true);
}

int DynamicTemporaries::allocate(uint32_t stepIndex) {
    VLOG(EXECUTION) << "DynamicTemporaries::allocate(stepIndex = " << stepIndex << ")";

    CHECK(mDeclared);

    const auto sourceOperandIndexesI = mStepIndexToSourceOperandIndexes.find(stepIndex);
    if (sourceOperandIndexesI == mStepIndexToSourceOperandIndexes.end()) {
        return ANEURALNETWORKS_NO_ERROR;
    }

    // perform layout
    uint32_t newSize = 0;
    for (const auto& sourceOperandIndex : sourceOperandIndexesI->second) {
        InternalLocationAndShape& temp = mSourceOperandToTemporary.at(sourceOperandIndex);
        // temp.paddedLength is already padded in declare and redeclare.
        CHECK(temp.paddedLength % temp.padding == 0);
        temp.offset = addTemporary(&newSize, temp.paddedLength, temp.alignment, kNoPadding).offset;
    }

    // perform (re-)allocation
    // TODO: Today we may shrink the allocation in order to avoid wasting memory.  Is this important
    //       to conserve memory, or do we waste time reallocating?
    const double kWaste = 0.2 /* arbitrary */;  // Willing to waste space to avoid
                                                // deallocation/reallocation overhead
    auto& memory = mStepIndexToMemory[stepIndex];
    const uint32_t oldSize = (memory ? memory->getSize() : 0);
    if ((oldSize >= newSize) && (oldSize <= newSize * (1 + kWaste))) {
        // Suitable allocation already exists; nothing to do
    } else {
        int n;
        std::tie(n, memory) = MemoryAshmem::create(newSize);
        if (n != ANEURALNETWORKS_NO_ERROR) {
            LOG(ERROR) << "Failed to allocate dynamic temporaries of size " << newSize
                       << " for step " << stepIndex;
            mAllocatedStepIndexes.erase(stepIndex);
            return n;
        }
    }

    mAllocatedStepIndexes.insert(stepIndex);
    return ANEURALNETWORKS_NO_ERROR;
}

bool DynamicTemporaries::allocated(uint32_t stepIndex) const {
    return (mStepIndexToSourceOperandIndexes.find(stepIndex) ==
            mStepIndexToSourceOperandIndexes.end()) ||
           mAllocatedStepIndexes.count(stepIndex);
}

std::optional<DynamicTemporaries::LocationAndShape> DynamicTemporaries::lookup(
        SourceOperandIndex sourceOperandIndex, bool mustBeAllocated) const {
    CHECK(mDeclared);
    if (auto it = mSourceOperandToTemporary.find(sourceOperandIndex);
        it != mSourceOperandToTemporary.end()) {
        const InternalLocationAndShape& temp = it->second;
        const bool isAllocated = allocated(temp.stepIndex);
        if (mustBeAllocated) {
            CHECK(isAllocated) << "Source operand " << toString(sourceOperandIndex)
                               << " must be allocated";
        }
        if (isAllocated) {
            return LocationAndShape{mStepIndexToMemory.at(temp.stepIndex).get(), temp.offset,
                                    &temp.dimensions, temp.paddedLength};
        } else {
            return LocationAndShape{nullptr, ~uint32_t(0), &temp.dimensions, temp.paddedLength};
        }
    }
    return std::nullopt;
}

ExecutionStep::ExecutionStep(ExecutionPlan* plan, uint32_t stepIndex, uint32_t sourceModelIndex,
                             std::shared_ptr<Device> device)
    : mPlan(plan),
      mIndex(stepIndex),
      mSourceModelIndex(sourceModelIndex),
      mStepModel(),
      mDevice(device),
      mToken(plan->getCacheToken()) {}

// Adds an operand if it has not been added already.
// Sets the index in the step model for the corresponding operand.
int ExecutionStep::addOperand(uint32_t sourceOperandIndex, uint32_t* stepOperandIndex,
                              OperandKind kind) {
    // Have we added this operand already?
    auto i = mOperandMap.find(sourceOperandIndex);
    if (i != mOperandMap.end()) {
        CHECK(kind == INPUT);
        *stepOperandIndex = i->second;
        return ANEURALNETWORKS_NO_ERROR;
    }

    // First time we add this operand.
    *stepOperandIndex = mStepModel.operandCount();
    mOperandMap.emplace(sourceOperandIndex, *stepOperandIndex);

    // Add the operand to the step model.
    const ModelBuilder& sourceModel = *getSourceModel();
    const Operand& operand = sourceModel.getOperand(sourceOperandIndex);
    ANeuralNetworksOperandType type = {
            .type = static_cast<int32_t>(operand.type),
            .dimensionCount = static_cast<uint32_t>(operand.dimensions.size()),
            .dimensions = operand.dimensions.size() > 0 ? operand.dimensions.data() : nullptr,
            .scale = operand.scale,
            .zeroPoint = operand.zeroPoint,
    };

    int n = mStepModel.addOperand(type);
    if (n != ANEURALNETWORKS_NO_ERROR) {
        LOG(ERROR) << "Previous error occurred when partitioning the graph";
        return n;
    }

    n = copyOperandExtraParams(mStepModel, *stepOperandIndex, operand);
    if (n != ANEURALNETWORKS_NO_ERROR) {
        LOG(ERROR) << "Error when copying extra parameters to the operand";
        return n;
    }

    // Sets its value.
    switch (operand.lifetime) {
        case Operand::LifeTime::CONSTANT_COPY: {
            const uint8_t* data = sourceModel.getPointerToOperandValue(operand.location.offset);
            n = mStepModel.setOperandValue(*stepOperandIndex, data, operand.location.length);
        } break;
        case Operand::LifeTime::CONSTANT_REFERENCE: {
            const RuntimeMemory* memory = sourceModel.getMemories()[operand.location.poolIndex];
            n = mStepModel.setOperandValueFromMemory(
                    *stepOperandIndex, memory, operand.location.offset, operand.location.length);
        } break;
        case Operand::LifeTime::NO_VALUE: {
            n = mStepModel.setOperandValue(*stepOperandIndex, nullptr, 0);
        } break;
        case Operand::LifeTime::TEMPORARY_VARIABLE: {  // handled similarly to SUBGRAPH_OUTPUT
            if (kind == INPUT) {
                // The first time we've seen this operand is as an
                // input.  That means it must be defined by a
                // different partition, and is an input to this one.
                mTempsAsStepModelInputs.emplace_back(sourceOperandIndex, *stepOperandIndex);
            } else {
                // The first time we've seen this operand is as an
                // output.  It may be an input to a different
                // partition, so keep track of it.
                mPlan->recordTemporaryDef(SourceOperandIndex(mSourceModelIndex, sourceOperandIndex),
                                          mIndex);
            }
        } break;
        case Operand::LifeTime::SUBGRAPH_INPUT: {
            mModelInputs.emplace_back(sourceOperandIndex, *stepOperandIndex);
        } break;
        case Operand::LifeTime::SUBGRAPH_OUTPUT: {  // handled similarly to TEMPORARY_VARIABLE
            if (kind == INPUT) {
                // The first time we've seen this operand is as an
                // input.  That means it must be defined by a
                // different partition, and is an input to this one.
                mOutputsAsStepModelInputs.emplace_back(sourceOperandIndex, *stepOperandIndex);
            } else {
                // The first time we've seen this operand is as an
                // output.
                mModelOutputs.emplace_back(sourceOperandIndex, *stepOperandIndex);
                // It may be an input to a different partition, so keep track of
                // it.
                mPlan->recordOutputDef(SourceOperandIndex(mSourceModelIndex, sourceOperandIndex),
                                       mIndex);
            }
        } break;
        case Operand::LifeTime::SUBGRAPH: {
            const ModelBuilder* model = sourceModel.getReferencedModel(operand);
            n = mStepModel.setOperandValueFromModel(*stepOperandIndex, model);
        } break;
        case Operand::LifeTime::POINTER: {
            const void* data = std::get<const void*>(operand.location.pointer);
            n = mStepModel.setOperandValue(*stepOperandIndex, data, operand.location.length);
        } break;
    }

    if (n != ANEURALNETWORKS_NO_ERROR) {
        LOG(ERROR) << "Previous error occurred when partitioning the graph";
    }
    return n;
}

int ExecutionStep::addOperation(int operationIndex) {
    const Operation& operation = getSourceModel()->getOperation(operationIndex);
    if (mToken.ok()) {
        mToken.update(&mSourceModelIndex, sizeof(mSourceModelIndex));
        mToken.update(&operationIndex, sizeof(operationIndex));
    }

    // Convert the input and output operand indexes.
    //
    // We expect operations to be added in topological order.  Therefore:
    //
    // - We may not have seen an input if it is a model input, a
    //   constant, or an operand written by a different partition.
    //
    // - We should not have seen any outputs.
    auto addOperands = [this](const std::vector<uint32_t>& sourceModelOperands,
                              std::vector<uint32_t>* stepModelOperands, OperandKind kind) -> int {
        const uint32_t operandCount = static_cast<uint32_t>(sourceModelOperands.size());
        for (uint32_t i = 0; i < operandCount; i++) {
            NN_RETURN_IF_ERROR(addOperand(sourceModelOperands[i], &stepModelOperands->at(i), kind));
        }
        return ANEURALNETWORKS_NO_ERROR;
    };

    const uint32_t inputCount = static_cast<uint32_t>(operation.inputs.size());
    const uint32_t outputCount = static_cast<uint32_t>(operation.outputs.size());
    std::vector<uint32_t> inputs(inputCount);
    std::vector<uint32_t> outputs(outputCount);
    NN_RETURN_IF_ERROR(addOperands(operation.inputs, &inputs, INPUT));
    NN_RETURN_IF_ERROR(addOperands(operation.outputs, &outputs, OUTPUT));
    return mStepModel.addOperation(static_cast<uint32_t>(operation.type), inputCount, inputs.data(),
                                   outputCount, outputs.data());
}

void ExecutionStep::mapInputsAndOutputs(
        std::shared_ptr<StepExecutor> executor,
        const std::vector<OutputShape>* mainModelOutputShapes, const RuntimeMemory* temporaryMemory,
        const std::map<SourceOperandIndex, StaticTemporaryLocation>&
                sourceOperandToLocationOfTemporary,
        const DynamicTemporaries& dynamicTemporaries,
        const std::map<SourceOperandIndex, uint32_t>& sourceOperandToInputIndex,
        const std::map<SourceOperandIndex, uint32_t>& sourceOperandToOutputIndex,
        const std::map<SourceOperandIndex, ConstantReferenceLocation>&
                sourceOperandToConstantReference) const {
    auto mapInput = [&](uint32_t stepModelOperandIndex, uint32_t stepInputIndex) {
        SourceOperandIndex sourceOperandIndex(mSourceModelIndex, stepModelOperandIndex);
        if (auto it = sourceOperandToLocationOfTemporary.find(sourceOperandIndex);
            it != sourceOperandToLocationOfTemporary.end()) {
            const auto& loc = it->second;
            executor->setInputFromMemory(stepInputIndex, temporaryMemory, loc.offset,
                                         loc.paddedLength);
        } else if (auto loc = dynamicTemporaries.lookup(sourceOperandIndex); loc != std::nullopt) {
            executor->setInputFromMemory(stepInputIndex, loc->memory, loc->offset,
                                         loc->paddedLength, *loc->dimensions);
        } else if (auto it = sourceOperandToInputIndex.find(sourceOperandIndex);
                   it != sourceOperandToInputIndex.end()) {
            executor->mapInput(it->second, stepInputIndex);
        } else if (auto it = sourceOperandToOutputIndex.find(sourceOperandIndex);
                   it != sourceOperandToOutputIndex.end()) {
            executor->mapOutputToInput(it->second, stepInputIndex,
                                       mainModelOutputShapes
                                               ? &mainModelOutputShapes->at(it->second).dimensions
                                               : nullptr);
        } else if (auto it = sourceOperandToConstantReference.find(sourceOperandIndex);
                   it != sourceOperandToConstantReference.end()) {
            // Constant partition boundary operand. This could be an IF branch
            // model input or a WHILE variable initializer.
            const auto& loc = it->second;
            executor->setInputFromMemory(stepInputIndex, loc.memory, loc.offset, loc.length);
        } else {
            CHECK(false) << "Cannot map step input " << stepInputIndex << " from operand "
                         << toString(sourceOperandIndex);
        }
    };
    auto mapOutput = [&](uint32_t stepModelOperandIndex, uint32_t stepOutputIndex) {
        SourceOperandIndex sourceOperandIndex(mSourceModelIndex, stepModelOperandIndex);
        if (auto it = sourceOperandToLocationOfTemporary.find(sourceOperandIndex);
            it != sourceOperandToLocationOfTemporary.end()) {
            const auto& loc = it->second;
            executor->setOutputFromMemory(stepOutputIndex, temporaryMemory, loc.offset,
                                          loc.paddedLength);
        } else if (auto loc = dynamicTemporaries.lookup(sourceOperandIndex); loc != std::nullopt) {
            executor->setOutputFromMemory(stepOutputIndex, loc->memory, loc->offset,
                                          loc->paddedLength, *loc->dimensions);
        } else if (auto it = sourceOperandToOutputIndex.find(sourceOperandIndex);
                   it != sourceOperandToOutputIndex.end()) {
            executor->mapOutput(it->second, stepOutputIndex);
        } else {
            CHECK(false) << "Cannot map step output " << stepOutputIndex << " from operand "
                         << toString(sourceOperandIndex);
        }
    };
    for (uint32_t i = 0, n = mStepModelInputs.size(); i < n; ++i) {
        mapInput(mStepModelInputs[i].first, i);
    }
    for (uint32_t i = 0, n = mStepModelOutputs.size(); i < n; ++i) {
        mapOutput(mStepModelOutputs[i].first, i);
    }
}

void ExecutionPlan::CompoundBody::findModelOutputsThatAreDownstreamInputs() {
    auto declareModelOutputIsDownstreamInput =
            [this](const SourceOperandIndex& sourceOperandIndex) {
                const auto it = mOutputToDefiningExecutionStep.find(sourceOperandIndex);
                CHECK(it != mOutputToDefiningExecutionStep.end());
                uint32_t stepIndex = it->second;
                CHECK_LT(stepIndex, mSteps.size());
                VLOG(COMPILATION)
                        << "ExecutionStep(" << stepIndex
                        << ")->declareModelOutputIsDownstreamInput(mSourceOperandToOutputIndex.at"
                        << toString(sourceOperandIndex) << ")";
                CHECK(mSourceOperandToOutputIndex.find(sourceOperandIndex) !=
                      mSourceOperandToOutputIndex.end());
                mSteps[stepIndex]->executionStep()->declareModelOutputIsDownstreamInput(
                        mSourceOperandToOutputIndex.at(sourceOperandIndex));
            };
    for (const auto& logicalStep : mSteps) {
        if (const ExecutionStep* step = logicalStep->tryExecutionStep()) {
            for (const auto& output : step->getOutputsAsStepModelInputs()) {
                SourceOperandIndex sourceOperandIndex(step->getSourceModelIndex(), output.first);
                declareModelOutputIsDownstreamInput(sourceOperandIndex);
            }
        }
    }
}

void ExecutionPlan::CompoundBody::findTempsAsStepModelOutputs() {
    auto recordAsOutputIfTemporary = [this](const SourceOperandIndex& sourceOperandIndex) {
        const auto it = mTemporaryToDefiningExecutionStep.find(sourceOperandIndex);
        if (it == mTemporaryToDefiningExecutionStep.end()) {
            // The operand is not a temporary or is not defined by an
            // ExecutionStep (i.e. it's an output of an IF or a WHILE).
            // The latter case is handled by ExecutionPlan::makeController().
            return;
        }
        uint32_t stepIndex = it->second;
        CHECK_LT(stepIndex, mSteps.size());
        mSteps[stepIndex]->executionStep()->recordTempAsStepModelOutput(sourceOperandIndex.second);
    };
    for (const auto& logicalStep : mSteps) {
        if (const ExecutionStep* step = logicalStep->tryExecutionStep()) {
            for (const auto& input : step->getTempsAsStepModelInputs()) {
                SourceOperandIndex sourceOperandIndex(step->getSourceModelIndex(), input.first);
                recordAsOutputIfTemporary(sourceOperandIndex);
            }
        } else if (const IfStep* step = logicalStep->tryIfStep()) {
            recordAsOutputIfTemporary(step->conditionOperandIndex);
            for (const SourceOperandIndex& sourceOperandIndex : step->outerInputOperands) {
                recordAsOutputIfTemporary(sourceOperandIndex);
            }
        } else if (const WhileStep* step = logicalStep->tryWhileStep()) {
            for (const SourceOperandIndex& sourceOperandIndex : step->outerInputOperands) {
                recordAsOutputIfTemporary(sourceOperandIndex);
            }
        } else {
            CHECK(logicalStep->isGoto());
        }
    }
}

void ExecutionStep::declareModelOutputIsDownstreamInput(uint32_t mainModelOutputIndex) {
    VLOG(COMPILATION) << "ExecutionStep(" << mIndex << ")::declareModelOutputIsDownstreamInput("
                      << mainModelOutputIndex << ")";
    const auto it = std::find(mOutputIndexStepModelToMainModel.begin(),
                              mOutputIndexStepModelToMainModel.end(), mainModelOutputIndex);
    CHECK(it != mOutputIndexStepModelToMainModel.end());
    const uint32_t stepModelOutputIndex = it - mOutputIndexStepModelToMainModel.begin();
    CHECK(stepModelOutputIndex < mModelOutputs.size());
    mModelOutputsThatAreDownstreamInputs.insert(stepModelOutputIndex);
}

void ExecutionStep::recordTempAsStepModelOutput(uint32_t stepOperandIndex) {
    const auto it = mOperandMap.find(stepOperandIndex);
    CHECK(it != mOperandMap.end());
    mTempsAsStepModelOutputs.emplace(stepOperandIndex, it->second);
}

const ModelBuilder* ExecutionStep::getSourceModel() const {
    return mPlan->getSourceModels().getModel(mSourceModelIndex);
}

void ExecutionStep::logStepModel() const {
    VLOG(COMPILATION) << "ExecutionStep::finishStepModel, step " << mIndex;

    auto logRemapEntry = [](std::string& toLog, const std::pair<uint32_t, uint32_t>& e) {
        if (!toLog.empty()) {
            toLog += ", ";
        }
        toLog += toString(e.first);
        toLog += "->";
        toLog += toString(e.second);
    };

    auto logRemapVector = [&logRemapEntry](const char* name, const RemapVectorType& map) {
        std::string toLog;
        for (const auto& e : map) {
            logRemapEntry(toLog, e);
        }
        VLOG(COMPILATION) << name << ": " << toLog;
    };
    auto logRemapSet = [&logRemapEntry](const char* name, const StepModelOutputSetType& set) {
        std::string toLog;
        for (const auto& e : set) {
            logRemapEntry(toLog, e);
        }
        VLOG(COMPILATION) << name << ": " << toLog;
    };

    logRemapVector("step model inputs", mStepModelInputs);
    logRemapVector("step model outputs", mStepModelOutputs);
    logRemapVector("model inputs", mModelInputs);
    logRemapVector("model outputs", mModelOutputs);
    logRemapVector("temps as step model inputs", mTempsAsStepModelInputs);
    logRemapSet("temps as step model outputs", mTempsAsStepModelOutputs);
    logRemapVector("outputs as step model inputs", mOutputsAsStepModelInputs);
}

static bool hasUnknownSize(const Operand& operand) {
    if (operand.dimensions.empty()) {
        return TypeManager::get()->isTensorType(operand.type);
    }
    for (const Dimension& dimension : operand.dimensions) {
        if (dimension == 0) {
            return true;
        }
    }
    return false;
}

int ExecutionStep::finishStepModel(const ModelBuilder* mainModel, bool* hasOutputOfUnknownSize,
                                   int32_t executionPreference, int32_t priority) {
    CHECK(mDevice != nullptr);

    for (const auto& stepModelOutput : mTempsAsStepModelOutputs) {
        const Operand& operand = mStepModel.getOperand(stepModelOutput.second);
        if (hasUnknownSize(operand)) {
            *hasOutputOfUnknownSize = true;
            VLOG(COMPILATION) << "StepModelOutput (operand#" << stepModelOutput.first
                              << " of source graph) has unknown size: " << operand;
        }
    }

    mStepModel.relaxComputationFloat32toFloat16(mainModel->isComputationFloat32RelaxedToFloat16());

    mStepModelInputs.insert(mStepModelInputs.end(), mModelInputs.begin(), mModelInputs.end());
    mStepModelInputs.insert(mStepModelInputs.end(), mTempsAsStepModelInputs.begin(),
                            mTempsAsStepModelInputs.end());
    mStepModelInputs.insert(mStepModelInputs.end(), mOutputsAsStepModelInputs.begin(),
                            mOutputsAsStepModelInputs.end());

    mStepModelOutputs.insert(mStepModelOutputs.end(), mModelOutputs.begin(), mModelOutputs.end());
    mStepModelOutputs.insert(mStepModelOutputs.end(), mTempsAsStepModelOutputs.begin(),
                             mTempsAsStepModelOutputs.end());

    // A step model with no inputs or no outputs is an invalid model. Note that we would like to
    // attempt full CPU fallback if allowed, so we return OP_FAILED here rather than BAD_DATA from
    // model validation.
    if (hasNoInputsOrNoOutputs()) {
        VLOG(COMPILATION) << "ExecutionStep::finishStepModel: finishing step model with no inputs "
                             "or no outputs";
        return ANEURALNETWORKS_OP_FAILED;
    }

    if (mSourceModelIndex == kMainModelInSourceModels) {
        std::map<uint32_t, uint32_t> mainModelOperandToInputIndex;
        for (uint32_t i = 0, n = mainModel->inputCount(); i < n; ++i) {
            mainModelOperandToInputIndex[mainModel->getInputOperandIndex(i)] = i;
        }
        std::map<uint32_t, uint32_t> mainModelOperandToOutputIndex;
        for (uint32_t i = 0, n = mainModel->outputCount(); i < n; ++i) {
            mainModelOperandToOutputIndex[mainModel->getOutputOperandIndex(i)] = i;
        }

        // mInputIndexStepModelToMainModel is ordered by step model input index and relies on
        // mModelInputs being the first inputs, as specified by mStepModelInputs.
        mInputIndexStepModelToMainModel.resize(mModelInputs.size());
        std::transform(mModelInputs.begin(), mModelInputs.end(),
                       mInputIndexStepModelToMainModel.begin(),
                       [&mainModelOperandToInputIndex](auto& e) {
                           uint32_t sourceOperandIndex = e.first;
                           return mainModelOperandToInputIndex[sourceOperandIndex];
                       });

        // mOutputIndexStepModelToMainModel is ordered by step model output index and relies on
        // mModelOutputs being the first outputs, as specified by mStepModelOutputs.
        mOutputIndexStepModelToMainModel.resize(mModelOutputs.size());
        std::transform(mModelOutputs.begin(), mModelOutputs.end(),
                       mOutputIndexStepModelToMainModel.begin(),
                       [&mainModelOperandToOutputIndex](auto& e) {
                           uint32_t sourceOperandIndex = e.first;
                           return mainModelOperandToOutputIndex[sourceOperandIndex];
                       });

        // mOutputsAsStepModelInputsIndexToMainModel is ordered by step model input index and relies
        // on mOutputsAsStepModelInputs being the first outputs.
        mOutputsAsStepModelInputsIndexToMainModel.resize(mOutputsAsStepModelInputs.size());
        std::transform(mOutputsAsStepModelInputs.begin(), mOutputsAsStepModelInputs.end(),
                       mOutputsAsStepModelInputsIndexToMainModel.begin(),
                       [&mainModelOperandToOutputIndex](auto& e) {
                           uint32_t sourceOperandIndex = e.first;
                           return mainModelOperandToOutputIndex[sourceOperandIndex];
                       });
    }

    if (VLOG_IS_ON(COMPILATION)) {
        logStepModel();
    }

    std::vector<uint32_t> inputs(mStepModelInputs.size());
    std::vector<uint32_t> outputs(mStepModelOutputs.size());
    std::transform(mStepModelInputs.begin(), mStepModelInputs.end(), inputs.begin(),
                   [](auto& e) { return e.second; });
    std::transform(mStepModelOutputs.begin(), mStepModelOutputs.end(), outputs.begin(),
                   [](auto& e) { return e.second; });
    NN_RETURN_IF_ERROR(mStepModel.identifyInputsAndOutputs(inputs.size(), inputs.data(),
                                                           outputs.size(), outputs.data()));
    NN_RETURN_IF_ERROR(mStepModel.finish());

    // TODO: Move compilation elsewhere?
    VLOG(COMPILATION) << "ExecutionStep::finishStepModel, compilation on " << mDevice->getName();
    return compile(*mDevice, mStepModel, executionPreference, priority, {}, *mPlan->getCacheInfo(),
                   &mToken, {}, &mPreparedStepModel);
}

void ExecutionStep::dump() const {
    if (VLOG_IS_ON(COMPILATION)) {
        VLOG(COMPILATION) << "Step#" << mIndex << ": execute on " << mDevice->getName();
        logModelToInfo(mStepModel.makeModel());
    }
}

std::ostream& operator<<(std::ostream& os, const IfStep& step) {
    return os << "Step#" << step.index << ": if " << toString(step.conditionOperandIndex)
              << " then=" << step.thenStepIndex << " else=" << step.elseStepIndex;
}

std::ostream& operator<<(std::ostream& os, const WhileStep& step) {
    return os << "Step#" << step.index << ": while cond=" << step.condStepIndex
              << " body=" << step.bodyStepIndex << " exit=" << step.exitStepIndex;
}

std::ostream& operator<<(std::ostream& os, const GotoStep& step) {
    return os << "Step#" << step.index << ": goto " << step.gotoStepIndex;
}

void LogicalStep::dump() const {
    if (VLOG_IS_ON(COMPILATION)) {
        if (const IfStep* step = tryIfStep()) {
            VLOG(COMPILATION) << *step;
        } else if (const WhileStep* step = tryWhileStep()) {
            VLOG(COMPILATION) << *step;
        } else if (const GotoStep* step = tryGotoStep()) {
            VLOG(COMPILATION) << *step;
        } else {
            executionStep()->dump();
        }
    }
}

int ExecutionPlan::CompoundBody::finish(const SourceModels* sourceModels,
                                        int32_t executionPreference, int32_t priority,
                                        const OptionalTimePoint& deadline,
                                        const std::vector<TokenValuePair>& metadata,
                                        int simulateFailureResultCode) {
    CHECK(!mSuccessfulFinish);
    CHECK(!deadline.has_value());
    CHECK(metadata.empty());

    const ModelBuilder* mainModel = sourceModels->getModel(kMainModelInSourceModels);

    auto containsUnknownSize = [sourceModels](const std::vector<SourceOperandIndex>& operands) {
        for (const auto& sourceOperandIndex : operands) {
            const ModelBuilder* sourceModel = sourceModels->getModel(sourceOperandIndex.first);
            const Operand& operand = sourceModel->getOperand(sourceOperandIndex.second);
            if (hasUnknownSize(operand)) {
                return true;
            }
        }
        return false;
    };

    findTempsAsStepModelOutputs();
    for (const auto& logicalStep : mSteps) {
        if (ExecutionStep* step = logicalStep->tryExecutionStep()) {
            bool stepHasDynamicTemporaries = false;
            int n = step->finishStepModel(mainModel, &stepHasDynamicTemporaries,
                                          executionPreference, priority);
            if (stepHasDynamicTemporaries) {
                mHasDynamicTemporaries = true;
                if (!isCompliantVersion(kHalVersionV1_2ToApi.canonical,
                                        step->getDevice()->getFeatureLevel())) {
                    // Until HAL 1.2, an Operand with lifetime SUBGRAPH_OUTPUT
                    // must have fully specified dimensions either in the
                    // Operand or in the RequestArgument.  In the case of a
                    // dynamic temporary, we won't be able to supply fully
                    // specified dimensions in either.
                    VLOG(COMPILATION)
                            << "ExecutionPlan::CompoundBody::finish -- step#" << step->getIndex()
                            << " defines dynamic temporaries but is scheduled on pre-1.2 device "
                            << step->getDevice()->getName();
                    if (n == ANEURALNETWORKS_NO_ERROR) {
                        n = ANEURALNETWORKS_OP_FAILED;
                    }
                }
            }
            if (n != ANEURALNETWORKS_NO_ERROR) {
                VLOG(COMPILATION)
                        << "ExecutionPlan::CompoundBody::finish -- finishStepModel failed";
                return n;
            }
        } else if (IfStep* step = logicalStep->tryIfStep()) {
            // The partitioner does not support dynamic temporaries (b/132458982).
            CHECK(!containsUnknownSize(step->outerInputOperands));
            CHECK(!containsUnknownSize(step->outerOutputOperands));
            // step->conditionOperandIndex has a static shape. See b/158557728.
            CHECK(!containsUnknownSize(step->thenBranchInputOperands));
            CHECK(!containsUnknownSize(step->thenBranchOutputOperands));
            CHECK(!containsUnknownSize(step->elseBranchInputOperands));
            CHECK(!containsUnknownSize(step->elseBranchOutputOperands));
        } else if (WhileStep* step = logicalStep->tryWhileStep()) {
            // The partitioner does not support dynamic temporaries (b/132458982).
            CHECK(!containsUnknownSize(step->outerInputOperands));
            CHECK(!containsUnknownSize(step->outerOutputOperands));
            CHECK(!containsUnknownSize(step->condInputOperands));
            // step->condOutputOperand has a static shape. See b/158557728.
            CHECK(!containsUnknownSize(step->bodyInputOperands));
            CHECK(!containsUnknownSize(step->bodyOutputOperands));
        } else {
            CHECK(logicalStep->isGoto());
        }
    }

    if (simulateFailureResultCode != ANEURALNETWORKS_NO_ERROR) {
        VLOG(COMPILATION) << "ExecutionPlan::CompoundeBody::finish: simulating failure, ResultCode "
                          << simulateFailureResultCode;
        return simulateFailureResultCode;
    }

    for (uint32_t i = 0, n = mainModel->inputCount(); i < n; ++i) {
        SourceOperandIndex index(kMainModelInSourceModels, mainModel->getInputOperandIndex(i));
        mSourceOperandToInputIndex[index] = i;
    }
    for (uint32_t i = 0, n = mainModel->outputCount(); i < n; ++i) {
        SourceOperandIndex index(kMainModelInSourceModels, mainModel->getOutputOperandIndex(i));
        mSourceOperandToOutputIndex[index] = i;
    }

    findControlFlowBoundaryConstants(sourceModels);
    findModelOutputsThatAreDownstreamInputs();
    findMemoryStepRoles();

    mSuccessfulFinish = true;
    LOG(INFO) << "ExecutionPlan::CompoundBody::finish: compilation finished successfully";
    return ANEURALNETWORKS_NO_ERROR;
}

void ExecutionPlan::CompoundBody::findControlFlowBoundaryConstants(
        const SourceModels* sourceModels) {
    auto handleBoundaryConstants = [this,
                                    sourceModels](const SourceOperandIndex& sourceOperandIndex) {
        const ModelBuilder* sourceModel = sourceModels->getModel(sourceOperandIndex.first);
        const Operand& operand = sourceModel->getOperand(sourceOperandIndex.second);
        const DataLocation& location = operand.location;
        if (operand.lifetime == Operand::LifeTime::CONSTANT_COPY) {
            mSourceOperandToBoundaryConstantCopy[sourceOperandIndex] = {
                    .buffer = sourceModel->getPointerToOperandValue(location.offset),
                    .length = location.length,
            };
        } else if (operand.lifetime == Operand::LifeTime::POINTER) {
            mSourceOperandToBoundaryConstantCopy[sourceOperandIndex] = {
                    .buffer = static_cast<const uint8_t*>(std::get<const void*>(location.pointer)),
                    .length = location.length,
            };
        } else if (operand.lifetime == Operand::LifeTime::CONSTANT_REFERENCE) {
            mSourceOperandToBoundaryConstantReference[sourceOperandIndex] = {
                    .memory = sourceModel->getMemories()[location.poolIndex],
                    .offset = location.offset,
                    .length = location.length,
            };
        }
    };
    for (const auto& logicalStep : mSteps) {
        if (const IfStep* step = logicalStep->tryIfStep()) {
            handleBoundaryConstants(step->conditionOperandIndex);
            for (const auto& sourceOperandIndex : step->outerInputOperands) {
                handleBoundaryConstants(sourceOperandIndex);
            }
        } else if (const WhileStep* step = logicalStep->tryWhileStep()) {
            for (const auto& sourceOperandIndex : step->outerInputOperands) {
                handleBoundaryConstants(sourceOperandIndex);
            }
        }
    }
}

void ExecutionPlan::CompoundBody::findMemoryStepRoles() {
    mSourceOperandToStepRoles = StepRoleAnalyzer::analyze([this](StepRoleAnalyzer& analyzer) {
        for (const auto& logicalStep : mSteps) {
            if (const ExecutionStep* step = logicalStep->tryExecutionStep()) {
                const auto& stepModelInputs = step->getStepModelInputs();
                for (uint32_t i = 0; i < stepModelInputs.size(); i++) {
                    const auto& [sourceIndex, stepIndex] = stepModelInputs[i];
                    analyzer.addRole(*step, sourceIndex, IOType::INPUT, i);
                }
                const auto& stepModelOutputs = step->getStepModelOutputs();
                for (uint32_t i = 0; i < stepModelOutputs.size(); i++) {
                    const auto& [sourceIndex, stepIndex] = stepModelOutputs[i];
                    analyzer.addRole(*step, sourceIndex, IOType::OUTPUT, i);
                }
            } else if (const IfStep* step = logicalStep->tryIfStep()) {
                // See ExecutionPlan::nextCompound(const IfStep*, ...).
                //
                // For interpreted IF operation, the outer input memories may be directly used by
                // the SUBGRAPH_INPUTs of the then and else model.
                CHECK_EQ(step->thenBranchInputOperands.size(), step->outerInputOperands.size());
                CHECK_EQ(step->elseBranchInputOperands.size(), step->outerInputOperands.size());
                for (uint32_t i = 0; i < step->outerInputOperands.size(); i++) {
                    analyzer.setUsedBy(step->outerInputOperands[i],
                                       step->thenBranchInputOperands[i]);
                    analyzer.setUsedBy(step->outerInputOperands[i],
                                       step->elseBranchInputOperands[i]);
                }
                // For interpreted IF operation, the outer output memories may be directly used by
                // the SUBGRAPH_OUTPUTs of the then and else model.
                CHECK_EQ(step->thenBranchOutputOperands.size(), step->outerOutputOperands.size());
                CHECK_EQ(step->elseBranchOutputOperands.size(), step->outerOutputOperands.size());
                for (uint32_t i = 0; i < step->outerOutputOperands.size(); i++) {
                    analyzer.setUsedBy(step->outerOutputOperands[i],
                                       step->thenBranchOutputOperands[i]);
                    analyzer.setUsedBy(step->outerOutputOperands[i],
                                       step->elseBranchOutputOperands[i]);
                }
            } else if (const WhileStep* step = logicalStep->tryWhileStep()) {
                // See ExecutionPlan::nextCompound(const WhileStep*, ...).
                //
                // For interpreted WHILE operation, the following memories are involved:
                // a. the outer input memories to the WHILE operation
                // b. the outer output memories to the WHILE operation
                // c. the output memory of the condition model
                // d. one set of output memories of the body model
                // e. another set of output memories of the body model
                //
                // The memories are used in the following ways:
                //
                // - Condition model:
                //   * In the first iteration: inputs use (a); output uses (c)
                //   * In the following iterations: inputs use (d) or (e) for input-output and
                //     state-only operands, and (a) for input-only operands; output uses (c)
                //
                // - Body model:
                //   * In all iterations: inputs are the same as the condition model; outputs use
                //                        (d) or (e)
                //
                // Therefore, we configure the analyzer with the following used-by relationships:
                // - The outer input memories (a) may be directly used by the SUBGRAPH_INPUTs of
                //   the condition model for all inputs in the first iteration, as well as the
                //   input-only operands in the following iterations.
                CHECK_EQ(step->condInputOperands.size(), step->outerInputOperands.size());
                for (uint32_t i = 0; i < step->outerInputOperands.size(); i++) {
                    analyzer.setUsedBy(step->outerInputOperands[i], step->condInputOperands[i]);
                }
                // - The output memories of the body model (d) and (e) may be directly used by the
                //   SUBGRAPH_INPUTs of the condition model for input-output and state-only operands
                //   after the first iteration.
                CHECK_GE(step->condInputOperands.size(), step->bodyOutputOperands.size());
                for (uint32_t i = 0; i < step->bodyOutputOperands.size(); i++) {
                    analyzer.setUsedBy(step->bodyOutputOperands[i], step->condInputOperands[i]);
                }
                // - The SUBGRAPH_INPUTs of the condition model are directly used by the
                //   SUBGRAPH_INPUTs of the body model for all inputs in all iterations.
                CHECK_EQ(step->bodyInputOperands.size(), step->condInputOperands.size());
                for (uint32_t i = 0; i < step->bodyInputOperands.size(); i++) {
                    analyzer.setUsedBy(step->condInputOperands[i], step->bodyInputOperands[i]);
                }
            } else if (logicalStep->isGoto()) {
                // Nothing to do.
            } else {
                CHECK(false) << "Unexpected LogicalStep kind";
            }
        }
    });
}

int ExecutionPlan::SimpleBody::finish(const SourceModels*, int32_t executionPreference,
                                      int32_t priority, const OptionalTimePoint& deadline,
                                      const std::vector<TokenValuePair>& metadata,
                                      int simulateFailureResultCode) {
    CHECK(!mSuccessfulFinish);
    CHECK(mDevice != nullptr);
    VLOG(COMPILATION) << "ExecutionPlan::SimpleBody::finish, compilation";
    int n = compile(*mDevice, *mModel, executionPreference, priority, deadline, *mCacheInfo,
                    &mToken, metadata, &mPreparedModel);
    if (n == ANEURALNETWORKS_NO_ERROR && simulateFailureResultCode != ANEURALNETWORKS_NO_ERROR) {
        VLOG(COMPILATION) << "ExecutionPlan::SimpleBody::finish: simulating failure, ResultCode "
                          << simulateFailureResultCode;
        n = simulateFailureResultCode;
    }
    mSuccessfulFinish = (n == ANEURALNETWORKS_NO_ERROR);
    if (mSuccessfulFinish) {
        LOG(INFO) << "ExecutionPlan::SimpleBody::finish: compilation finished successfully on "
                  << mDevice->getName();
    }
    return n;
}

int ExecutionPlan::finish(int32_t executionPreference, int32_t priority,
                          const OptionalTimePoint& deadline,
                          const std::vector<TokenValuePair>& metadata,
                          int simulateFailureResultCode) {
    CHECK(mBody != nullptr);
    return mBody->finish(&getSourceModels(), executionPreference, priority, deadline, metadata,
                         simulateFailureResultCode);
}

ExecutionPlan::Controller::Controller(
        const ExecutionPlan* plan, ExecutionBuilder* executionBuilder,
        const BurstBuilder* burstBuilder, uint32_t totalSizeOfTemporaries,
        std::map<SourceOperandIndex, StaticTemporaryLocation> sourceOperandToLocationOfTemporary,
        std::map<SourceOperandIndex, StaticTemporaryLocation> sourceOperandToLocationOfTemporary2,
        std::map<SourceOperandIndex, uint32_t> sourceOperandToInputIndex,
        std::map<SourceOperandIndex, uint32_t> sourceOperandToOutputIndex,
        const std::map<SourceOperandIndex, ConstantCopyLocation>& sourceOperandToConstantCopy,
        std::map<SourceOperandIndex, ConstantReferenceLocation> sourceOperandToConstantReference,
        DynamicTemporaries dynamicTemporaries)
    : mPlan(plan),
      mExecutionBuilder(executionBuilder),
      mBurstBuilder(burstBuilder),
      mSourceOperandToLocationOfTemporary(std::move(sourceOperandToLocationOfTemporary)),
      mSourceOperandToLocationOfTemporary2(std::move(sourceOperandToLocationOfTemporary2)),
      mSourceOperandToInputIndex(std::move(sourceOperandToInputIndex)),
      mSourceOperandToOutputIndex(std::move(sourceOperandToOutputIndex)),
      mSourceOperandToConstantReference(std::move(sourceOperandToConstantReference)),
      mDynamicTemporaries(std::move(dynamicTemporaries)),
      mNextStepIndex(0),
      mFallbackNextStepIndex(kBadStepIndex),
      mLastStepSyncFd(-1) {
    if (totalSizeOfTemporaries == 0) {
        return;
    }
    int n;
    std::tie(n, mTemporaries) = MemoryAshmem::create(totalSizeOfTemporaries);
    if (n != ANEURALNETWORKS_NO_ERROR) {
        LOG(ERROR) << "ExecutionPlan::Controller failed to allocate temporaries";
        mNextStepIndex = kBadStepIndex;
    }
    for (const auto& [sourceOperandIndex, location] : sourceOperandToConstantCopy) {
        memcpy(mTemporaries->getPointer() +
                       mSourceOperandToLocationOfTemporary[sourceOperandIndex].offset,
               location.buffer, location.length);
    }
}

// Attempt to create a burst object for each PreparedModel/Partition. If the
// burst controller object cannot be made, return a nullptr in its place to
// indicate the regular execution path should be used. This can occur either
// because PreparedModel was nullptr (cpu was best choice), or because the
// IPreparedModel was of insufficient version or failed to configure the burst.
std::vector<SharedBurst> ExecutionPlan::makeBursts() const {
    switch (mState) {
        // burst object for each partition in the compound case
        case COMPOUND: {
            std::vector<SharedBurst> bursts;
            bursts.reserve(compound()->mSteps.size());
            for (const auto& logicalStep : compound()->mSteps) {
                if (!logicalStep->isExecution()) {
                    bursts.push_back(nullptr);
                    continue;
                }
                if (const auto preparedModel =
                            logicalStep->executionStep()->getPreparedStepModel()) {
                    const auto maybeBurst = preparedModel->configureExecutionBurst();
                    if (!maybeBurst.has_value()) {
                        LOG(ERROR) << "preparedModel->configureExecutionBurst() failed with "
                                   << maybeBurst.error().code << ": " << maybeBurst.error().message;
                    }
                    bursts.push_back(maybeBurst.value_or(nullptr));
                } else {
                    bursts.push_back(nullptr);
                }
            }
            return bursts;
        }
        // single burst object for the simple case
        case SIMPLE: {
            std::vector<SharedBurst> burst;
            auto simpleBody = simple();
            if (const auto preparedModel = simpleBody->mPreparedModel) {
                const auto maybeBurst = preparedModel->configureExecutionBurst();
                if (!maybeBurst.has_value()) {
                    LOG(ERROR) << "preparedModel->configureExecutionBurst() failed with "
                               << maybeBurst.error().code << ": " << maybeBurst.error().message;
                }
                burst.push_back(maybeBurst.value_or(nullptr));
            } else {
                burst.push_back(nullptr);
            }
            return burst;
        }
        // no burst objects made
        default:
            return {};
    }
}

std::shared_ptr<ExecutionPlan::Controller> ExecutionPlan::makeController(
        ExecutionBuilder* executionBuilder, const BurstBuilder* burstBuilder) const {
    CHECK(isValid());
    CHECK(mState != SIMPLE);
    const auto* body = compound();
    // Create the layout for a RuntimeMemory object big enough to hold
    // - every partition boundary TEMPORARY operand that is not a dynamic temporary, and
    // - buffers required by the control flow implementation.
    //
    // TODO: Rethink this approach for managing temporaries.  Some
    // alternatives:
    //
    // 1) Adopt a memory layout scheme analogous to stack allocation,
    // where objects of non-overlapping lifetime can occupy the same
    // storage.  We would still have a single Memory object in this
    // case.
    //
    // 2) Do something like what CpuExecutor does, and do allocations
    // and deallocations on the fly (during execution) before first
    // reference and after last reference, respectively.  This would
    // mean having one Memory object per TEMPORARY; or, in a more
    // complicated implementation, one Memory object per set of
    // temporaries that have the same lifetime.  Note that the Android
    // system limits the number of shared memory objects, which are
    // what our Memory objects represent.
    //
    uint32_t totalSizeOfTemporaries = 0;
    // This function has two modes of operation:
    // 1. When lifetime is TEMPORARY_VARIABLE, we allocate memory for
    //    TEMPORARY_VARIABLE source operands that are not dynamic temporaries,
    //    skip TEMPORARY_VARIABLE source operands that are dynamic temporaries,
    //    skip SUBGRAPH_OUTPUT source operands, and panic if we see a source
    //    operand of another lifetime.
    // 2. When lifetime is SUBGRAPH_OUTPUT, we allocate memory for
    //    SUBGRAPH_OUTPUT source operands and panic if we see a source operand
    //    of another lifetime.
    auto mapTemporary = [body, executionBuilder, &totalSizeOfTemporaries](
                                const SourceOperandIndex& sourceOperandIndex,
                                std::map<SourceOperandIndex, StaticTemporaryLocation>*
                                        sourceOperandToLocationOfTemporary,
                                Operand::LifeTime lifetime =
                                        Operand::LifeTime::TEMPORARY_VARIABLE) {
        CHECK(lifetime == Operand::LifeTime::TEMPORARY_VARIABLE ||
              lifetime == Operand::LifeTime::SUBGRAPH_OUTPUT);
        const Operand& sourceOperand = executionBuilder->getSourceOperand(sourceOperandIndex);
        if (lifetime == Operand::LifeTime::TEMPORARY_VARIABLE &&
            sourceOperand.lifetime == Operand::LifeTime::SUBGRAPH_OUTPUT) {
            // See the caller for explanation.
            return;
        }
        CHECK_EQ(sourceOperand.lifetime, lifetime);
        const uint32_t size = TypeManager::get()->getSizeOfData(sourceOperand);
        if (size != 0u) {
            const auto memoryPreference =
                    body->getMemoryPreferenceOfSourceOperand(sourceOperandIndex);
            const auto loc = addTemporary(&totalSizeOfTemporaries, size, memoryPreference.alignment,
                                          memoryPreference.padding);
            auto [_, isNew] = sourceOperandToLocationOfTemporary->emplace(sourceOperandIndex, loc);
            CHECK(isNew);
            VLOG(EXECUTION) << "temp: operand " << toString(sourceOperandIndex)
                            << " offset = " << loc.offset << " paddedLength = " << loc.paddedLength;
        } else {
            // Unknown size, hence dynamic temporary.  The mapping will
            // be established elsewhere (DynamicTemporaries::allocate()).
            CHECK_EQ(lifetime, Operand::LifeTime::TEMPORARY_VARIABLE);
            CHECK_EQ(sourceOperand.lifetime, Operand::LifeTime::TEMPORARY_VARIABLE);
        }
    };
    std::map<SourceOperandIndex, StaticTemporaryLocation> sourceOperandToLocationOfTemporary;
    std::map<SourceOperandIndex, StaticTemporaryLocation> sourceOperandToLocationOfTemporary2;
    for (const auto& logicalStep : body->mSteps) {
        if (const ExecutionStep* step = logicalStep->tryExecutionStep()) {
            // Allocate memory for ExecutionStep temporary outputs that are
            // inputs to other steps, as determined by
            // ExecutionPlan::CompoundBody::findTempsAsStepModelOutputs().
            //
            // We don't allocate memory for step model output operands with
            // source operand lifetime SUBGRAPH_OUTPUT because they will be
            // - managed by the client (main model outputs),
            // - assigned a location of another operand (when this step model
            //   output is a branch model output of an IF; see
            //   ExecutionPlan::nextCompound(const IfStep*, ...)), or
            // - allocated by a WHILE (when this step model output
            //   is a condition or body model output of a WHILE; see the
            //   step->bodyOutputOperands and step->condOutputOperand handling
            //   below).
            for (const auto& output : step->getTempsAsStepModelOutputs()) {
                mapTemporary(SourceOperandIndex(step->getSourceModelIndex(), output.first),
                             &sourceOperandToLocationOfTemporary);
            }
        } else if (const IfStep* step = logicalStep->tryIfStep()) {
            // Allocate memory for all temporary outputs of an IfStep because
            // they are going to be written to by a branch model. We don't
            // perform unused output operand optimisation for referenced models.
            //
            // We don't allocate memory for branch output operands because they
            // use the same location as the corresponding outer output operands,
            // as established in ExecutionPlan::nextCompound(const IfStep*, ...)
            //
            // We don't allocate memory for outer output operands with source
            // operand lifetime SUBGRAPH_OUTPUT because they will be
            // - managed by the client (main model outputs),
            // - assigned a location of another operand (when this IF outer
            //   output is a branch model output of another IF; see
            //   ExecutionPlan::nextCompound(const IfStep*, ...)), or
            // - allocated by a WHILE (when this IF outer output
            //   is a condition or body model output of a WHILE; see the
            //   step->bodyOutputOperands and step->condOutputOperand handling
            //   below).
            for (const auto& sourceOperandIndex : step->outerOutputOperands) {
                mapTemporary(sourceOperandIndex, &sourceOperandToLocationOfTemporary);
            }
        } else if (const WhileStep* step = logicalStep->tryWhileStep()) {
            // Allocate memory for all temporary outputs of an WhileStep because
            // they are going to be written to by the WHILE loop.
            //
            // We don't allocate memory for outer output operands with source
            // operand lifetime SUBGRAPH_OUTPUT because they will be
            // - managed by the client (main model outputs),
            // - assigned a location of another operand (when this WHILE outer
            //   output is a branch model output of an IF; see
            //   ExecutionPlan::nextCompound(const IfStep*, ...)), or
            // - allocated by another WHILE (when this WHILE outer output
            //   is a condition or body model output of another WHILE; see the
            //   step->bodyOutputOperands and step->condOutputOperand handling
            //   below).
            for (const auto& sourceOperandIndex : step->outerOutputOperands) {
                mapTemporary(sourceOperandIndex, &sourceOperandToLocationOfTemporary);
            }
            // Allocate memory for body model outputs. Note that we could use
            // the outer output operand memory instead but we currently don't do
            // so (b/148206073).
            for (const auto& sourceOperandIndex : step->bodyOutputOperands) {
                mapTemporary(sourceOperandIndex, &sourceOperandToLocationOfTemporary,
                             Operand::LifeTime::SUBGRAPH_OUTPUT);
                // Allocate another set of temporaries for double buffering.
                mapTemporary(sourceOperandIndex, &sourceOperandToLocationOfTemporary2,
                             Operand::LifeTime::SUBGRAPH_OUTPUT);
            }
            // Allocate memory for condition model output.
            // TODO: Share one condition output memory region between all loops.
            mapTemporary(step->condOutputOperand, &sourceOperandToLocationOfTemporary,
                         Operand::LifeTime::SUBGRAPH_OUTPUT);
        } else {
            CHECK(logicalStep->isGoto());
        }
    }
    // Allocate temporary memory for boundary CONSTANT_COPY operands.
    for (const auto& [sourceOperandIndex, location] : body->mSourceOperandToBoundaryConstantCopy) {
        const auto memoryPreference = body->getMemoryPreferenceOfSourceOperand(sourceOperandIndex);
        const auto loc = addTemporary(&totalSizeOfTemporaries, location.length,
                                      memoryPreference.alignment, memoryPreference.padding);
        sourceOperandToLocationOfTemporary.emplace(sourceOperandIndex, loc);
        VLOG(EXECUTION) << "temp (boundary constant): operand " << toString(sourceOperandIndex)
                        << " offset = " << loc.offset << " paddedLength = " << loc.paddedLength;
    }
    // Collect dynamic temporaries.
    // TODO(b/157236079): Move some or all of this work to compilation time?
    DynamicTemporaries dynamicTemporaries;
    const TypeManager* typeManager = TypeManager::get();
    forEachDynamicTemporary([body, typeManager, &dynamicTemporaries](
                                    SourceOperandIndex sourceOperandIndex,
                                    const Operand& sourceOperand, uint32_t definingStepIndex) {
        CHECK(typeManager->isTensorType(sourceOperand.type));
        const auto memoryPreference = body->getMemoryPreferenceOfSourceOperand(sourceOperandIndex);
        // TODO: For now we guess an initial size equal to element
        // size, which is overly conservative.
        const uint32_t size = typeManager->getSizeOfData(sourceOperand.type, {1});
        dynamicTemporaries.declare(sourceOperandIndex, definingStepIndex, sourceOperand.dimensions,
                                   size, memoryPreference.alignment, memoryPreference.padding);
    });
    dynamicTemporaries.endDeclarations();
    dynamicTemporaries.vlogDump("finished declarations");

    return std::shared_ptr<Controller>(new Controller(
            this, executionBuilder, burstBuilder, totalSizeOfTemporaries,
            std::move(sourceOperandToLocationOfTemporary),
            std::move(sourceOperandToLocationOfTemporary2), body->mSourceOperandToInputIndex,
            body->mSourceOperandToOutputIndex, body->mSourceOperandToBoundaryConstantCopy,
            body->mSourceOperandToBoundaryConstantReference, std::move(dynamicTemporaries)));
}

// TODO: Find a better way to provide this functionality.
int ExecutionPlan::fallback(std::shared_ptr<Controller> controller,
                            std::shared_ptr<StepExecutor>* executor, SharedBurst* burstController,
                            const std::vector<OutputShape>* mainModelOutputShapes) const {
    *executor = nullptr;
    if (burstController != nullptr) {
        *burstController = nullptr;
    }

    VLOG(EXECUTION) << "ExecutionPlan::fallback(" << SHOW_IF_DEBUG(controller << ", " << executor)
                    << "): mFallbackNextStepIndex = " << controller->mFallbackNextStepIndex;

    if (controller->mFallbackNextStepIndex == Controller::kBadStepIndex) {
        // We haven't called next().
        return ANEURALNETWORKS_OP_FAILED;
    }

    if (controller->mNextStepIndex == Controller::kBadStepIndex) {
        // The last call to next() did not produce an executor.
        return ANEURALNETWORKS_OP_FAILED;
    }

    controller->mNextStepIndex = controller->mFallbackNextStepIndex;
    return next(controller, executor, burstController, mainModelOutputShapes);
}

ExecutionPlan::Buffer::Buffer(void* pointer, uint32_t size)
    : mInfo(RunTimePoolInfo::createFromExistingBuffer(static_cast<uint8_t*>(pointer), size)),
      mOffset(0) {}

ExecutionPlan::Buffer::Buffer(RunTimePoolInfo info, uint32_t offset)
    : mInfo(std::move(info)), mOffset(offset) {}

void* ExecutionPlan::Buffer::getPointer() const {
    return mInfo.getBuffer() + mOffset;
}

uint32_t ExecutionPlan::Buffer::getSize() const {
    return mInfo.getSize() - mOffset;
}

void ExecutionPlan::Buffer::flush() const {
    mInfo.flush();
}

std::optional<ExecutionPlan::Buffer> ExecutionPlan::getBufferFromModelArgumentInfo(
        const ModelArgumentInfo& info, const ExecutionBuilder* executionBuilder) const {
    switch (info.state()) {
        case ModelArgumentInfo::POINTER: {
            return Buffer(info.buffer(), info.length());
        } break;
        case ModelArgumentInfo::MEMORY: {
            if (std::optional<RunTimePoolInfo> poolInfo =
                        executionBuilder->getRunTimePoolInfo(info.locationAndLength().poolIndex)) {
                return Buffer(*poolInfo, info.locationAndLength().offset);
            } else {
                LOG(ERROR) << "Unable to map operand memory pool";
                return std::nullopt;
            }
        } break;
        case ModelArgumentInfo::HAS_NO_VALUE: {
            LOG(ERROR) << "Attempting to read an operand that has no value";
            return std::nullopt;
        } break;
        default: {
            LOG(ERROR) << "Unexpected operand memory state: " << static_cast<int>(info.state());
            return std::nullopt;
        } break;
    }
}

std::optional<ExecutionPlan::Buffer> ExecutionPlan::getBuffer(
        std::shared_ptr<Controller> controller, SourceOperandIndex operandIndex) const {
    const auto& sourceOperandToLocationOfTemporary =
            controller->mSourceOperandToLocationOfTemporary;
    const auto& sourceOperandToInputIndex = controller->mSourceOperandToInputIndex;
    const auto& sourceOperandToOutputIndex = controller->mSourceOperandToOutputIndex;
    const auto& sourceOperandToConstantReference = controller->mSourceOperandToConstantReference;
    if (auto it = sourceOperandToLocationOfTemporary.find(operandIndex);
        it != sourceOperandToLocationOfTemporary.end()) {
        const uint32_t offset = it->second.offset;
        const std::unique_ptr<MemoryAshmem>& memory = controller->mTemporaries;
        return Buffer(memory->getPointer() + offset, memory->getSize() - offset);
    } else if (auto it = sourceOperandToInputIndex.find(operandIndex);
               it != sourceOperandToInputIndex.end()) {
        const ModelArgumentInfo& info = controller->mExecutionBuilder->getInputInfo(it->second);
        return getBufferFromModelArgumentInfo(info, controller->mExecutionBuilder);
    } else if (auto it = sourceOperandToOutputIndex.find(operandIndex);
               it != sourceOperandToOutputIndex.end()) {
        const ModelArgumentInfo& info = controller->mExecutionBuilder->getOutputInfo(it->second);
        return getBufferFromModelArgumentInfo(info, controller->mExecutionBuilder);
    } else if (auto it = sourceOperandToConstantReference.find(operandIndex);
               it != sourceOperandToConstantReference.end()) {
        const ConstantReferenceLocation& location = it->second;
        const std::optional<RunTimePoolInfo> info = location.memory->getRunTimePoolInfo();
        if (info == std::nullopt) {
            return std::nullopt;
        }
        return Buffer(info->getBuffer() + location.offset, location.length);
    }
    return std::nullopt;
}

int ExecutionPlan::readConditionValue(std::shared_ptr<Controller> controller,
                                      SourceOperandIndex operandIndex, bool* value) const {
    std::optional<ExecutionPlan::Buffer> buffer = getBuffer(controller, operandIndex);
    if (buffer == std::nullopt) {
        LOG(ERROR) << "Unable to read operand " << toString(operandIndex);
        return ANEURALNETWORKS_OP_FAILED;
    }
    CHECK_GE(buffer->getSize(), sizeof(bool8));
    bool8 value8 = *static_cast<bool8*>(buffer->getPointer());
    *value = static_cast<bool>(value8);
    VLOG(EXECUTION) << "readConditionValue: " << *value;
    return ANEURALNETWORKS_NO_ERROR;
}

int ExecutionPlan::next(std::shared_ptr<Controller> controller,
                        std::shared_ptr<StepExecutor>* executor, SharedBurst* burstController,
                        const std::vector<OutputShape>* mainModelOutputShapes,
                        int syncFdOfLastStep) const {
    CHECK(mState == COMPOUND);

    controller->mLastStepSyncFd = syncFdOfLastStep;
    *executor = nullptr;
    if (burstController != nullptr) {
        *burstController = nullptr;
    }

    VLOG(EXECUTION) << "ExecutionPlan::next(" << SHOW_IF_DEBUG(controller << ", " << executor)
                    << "): mNextStepIndex = " << controller->mNextStepIndex;

    if (controller->mNextStepIndex == Controller::kBadStepIndex) {
        return ANEURALNETWORKS_OP_FAILED;
    }

    return nextCompound(controller, executor, burstController, mainModelOutputShapes);
}

int ExecutionPlan::nextCompound(std::shared_ptr<Controller> controller,
                                std::shared_ptr<StepExecutor>* executor,
                                SharedBurst* burstController,
                                const std::vector<OutputShape>* mainModelOutputShapes) const {
    if (controller->mNextStepIndex == Controller::kBadStepIndex) {
        return ANEURALNETWORKS_OP_FAILED;
    }

    auto compoundBody = compound();
    if (controller->mNextStepIndex == compoundBody->mSteps.size()) {
        controller->mNextStepIndex = Controller::kBadStepIndex;  // end
        return ANEURALNETWORKS_NO_ERROR;
    }

    const auto& logicalStep = compoundBody->mSteps[controller->mNextStepIndex];
    if (const IfStep* step = logicalStep->tryIfStep()) {
        return nextCompound(step, controller, executor, burstController, mainModelOutputShapes);
    } else if (const WhileStep* step = logicalStep->tryWhileStep()) {
        return nextCompound(step, controller, executor, burstController, mainModelOutputShapes);
    } else if (const GotoStep* step = logicalStep->tryGotoStep()) {
        return nextCompound(step, controller, executor, burstController, mainModelOutputShapes);
    } else if (const ExecutionStep* step = logicalStep->tryExecutionStep()) {
        return nextCompound(step, controller, executor, burstController, mainModelOutputShapes);
    } else {
        CHECK(false) << "Unknown step variant";
        return ANEURALNETWORKS_BAD_STATE;
    }
}

int ExecutionPlan::nextCompound(const ExecutionStep* step, std::shared_ptr<Controller> controller,
                                std::shared_ptr<StepExecutor>* executor,
                                SharedBurst* burstController,
                                const std::vector<OutputShape>* mainModelOutputShapes) const {
    VLOG(EXECUTION) << "next: Step#" << controller->mNextStepIndex << ": execute on "
                    << step->getDevice()->getName();

    NN_RETURN_IF_ERROR(controller->mDynamicTemporaries.allocate(step->getIndex()));
    controller->mDynamicTemporaries.vlogDump("finished allocating for a step");

    *executor = std::make_shared<StepExecutor>(controller->mExecutionBuilder, step->getStepModel(),
                                               step->getDevice(), step->getPreparedStepModel(),
                                               /*reusable=*/false, step,
                                               &controller->mDynamicTemporaries);

    step->mapInputsAndOutputs(
            *executor, mainModelOutputShapes, controller->mTemporaries.get(),
            controller->mSourceOperandToLocationOfTemporary, controller->mDynamicTemporaries,
            controller->mSourceOperandToInputIndex, controller->mSourceOperandToOutputIndex,
            controller->mSourceOperandToConstantReference);
    if (burstController != nullptr && controller->mBurstBuilder != nullptr) {
        *burstController = controller->mBurstBuilder->getControllerAt(controller->mNextStepIndex);
    }

    controller->mFallbackNextStepIndex = controller->mNextStepIndex;
    controller->mNextStepIndex++;
    return ANEURALNETWORKS_NO_ERROR;
}

// The first argument is the "source" operand, the second operand is the "destination".
void ExecutionPlan::Controller::setInput(const SourceOperandIndex& outerOperand,
                                         const SourceOperandIndex& innerOperand) {
    VLOG(EXECUTION) << "mapping input " << toString(innerOperand) << " from "
                    << toString(outerOperand);
#ifdef NN_DEBUGGABLE
    CHECK_LE(mSourceOperandToLocationOfTemporary.count(innerOperand) +
                     mSourceOperandToInputIndex.count(innerOperand) +
                     mSourceOperandToOutputIndex.count(innerOperand) +
                     mSourceOperandToConstantReference.count(innerOperand),
             1u);
#endif
    mSourceOperandToLocationOfTemporary.erase(innerOperand);
    mSourceOperandToInputIndex.erase(innerOperand);
    mSourceOperandToOutputIndex.erase(innerOperand);
    mSourceOperandToConstantReference.erase(innerOperand);
    if (auto it = mSourceOperandToLocationOfTemporary.find(outerOperand);
        it != mSourceOperandToLocationOfTemporary.end()) {
        mSourceOperandToLocationOfTemporary.emplace(innerOperand, it->second);
    } else if (auto it = mSourceOperandToInputIndex.find(outerOperand);
               it != mSourceOperandToInputIndex.end()) {
        mSourceOperandToInputIndex.emplace(innerOperand, it->second);
    } else if (auto it = mSourceOperandToOutputIndex.find(outerOperand);
               it != mSourceOperandToOutputIndex.end()) {
        mSourceOperandToOutputIndex.emplace(innerOperand, it->second);
    } else if (auto it = mSourceOperandToConstantReference.find(outerOperand);
               it != mSourceOperandToConstantReference.end()) {
        mSourceOperandToConstantReference.emplace(innerOperand, it->second);
    } else {
        CHECK(false) << "Cannot set step model input operand " << toString(innerOperand)
                     << " from operand " << toString(outerOperand);
    }
}

// The first argument is the "source" operand, the second operand is the "destination".
void ExecutionPlan::Controller::setOutput(const SourceOperandIndex& outerOperand,
                                          const SourceOperandIndex& innerOperand) {
    VLOG(EXECUTION) << "mapping output " << toString(innerOperand) << " from "
                    << toString(outerOperand);
#ifdef NN_DEBUGGABLE
    CHECK_LE(mSourceOperandToLocationOfTemporary.count(innerOperand) +
                     mSourceOperandToOutputIndex.count(innerOperand),
             1u);
#endif
    mSourceOperandToLocationOfTemporary.erase(innerOperand);
    mSourceOperandToOutputIndex.erase(innerOperand);
    if (auto it = mSourceOperandToLocationOfTemporary.find(outerOperand);
        it != mSourceOperandToLocationOfTemporary.end()) {
        mSourceOperandToLocationOfTemporary.emplace(innerOperand, it->second);
    } else if (auto it = mSourceOperandToOutputIndex.find(outerOperand);
               it != mSourceOperandToOutputIndex.end()) {
        mSourceOperandToOutputIndex.emplace(innerOperand, it->second);
    } else {
        CHECK(false) << "Cannot set step model output operand " << toString(innerOperand)
                     << " from operand " << toString(outerOperand);
    }
}

int ExecutionPlan::Controller::waitForLastStepSyncFence() const {
    if (mLastStepSyncFd == -1) {
        return ANEURALNETWORKS_NO_ERROR;
    }
    VLOG(EXECUTION) << "wait for mLastStepSyncFd " << mLastStepSyncFd;
    auto r = syncWait(mLastStepSyncFd, -1);
    int n = ANEURALNETWORKS_NO_ERROR;
    if (r != FenceState::SIGNALED) {
        LOG(ERROR) << "syncWait failed, fd: " << mLastStepSyncFd;
        n = ANEURALNETWORKS_OP_FAILED;
    }
    return n;
}

// Invocations of Controller::setInput/setOutput in this function must match with invocations of
// StepRoleAnalyzer::setUsedBy in the IfStep branch in
// ExecutionPlan::CompoundBody::findMemoryStepRoles.
int ExecutionPlan::nextCompound(const IfStep* step, std::shared_ptr<Controller> controller,
                                std::shared_ptr<StepExecutor>* executor,
                                SharedBurst* burstController,
                                const std::vector<OutputShape>* mainModelOutputShapes) const {
    VLOG(EXECUTION) << "next: " << *step;
    // If the last step has a sync fence, wait for it to signal before reading the condition value.
    // This is safe because the steps are serialized when doing fenced compute.
    NN_RETURN_IF_ERROR(controller->waitForLastStepSyncFence());
    bool condValue;
    NN_RETURN_IF_ERROR(readConditionValue(controller, step->conditionOperandIndex, &condValue));
    controller->mNextStepIndex = condValue ? step->thenStepIndex : step->elseStepIndex;
    const std::vector<SourceOperandIndex>& branchInputOperands =
            condValue ? step->thenBranchInputOperands : step->elseBranchInputOperands;
    const std::vector<SourceOperandIndex>& branchOutputOperands =
            condValue ? step->thenBranchOutputOperands : step->elseBranchOutputOperands;
    CHECK_EQ(branchInputOperands.size(), step->outerInputOperands.size());
    CHECK_EQ(branchOutputOperands.size(), step->outerOutputOperands.size());
    for (uint32_t i = 0, n = step->outerInputOperands.size(); i < n; ++i) {
        // We have to do this assignment just before executing this step to
        // accommodate cases when the IF resides within a WHILE condition or
        // body model and for some j the i-th input of the IF branch model is
        // - an input of the WHILE condition model (whileStep->condInputOperands[j]),
        // - an input of the WHILE body model (whileStep->bodyInputOperands[j]), or
        // - an output of the WHILE body model (whileStep->bodyOutputOperands[j]).
        // In such cases, the WhileStep modifies the location of
        // step->outerInputOperands[i] to implement double buffering.
        controller->setInput(step->outerInputOperands[i], branchInputOperands[i]);
    }
    for (uint32_t i = 0, n = step->outerOutputOperands.size(); i < n; ++i) {
        // We have to do this assignment just before executing this step to
        // accommodate the case when the IF resides within a WHILE body
        // model and the i-th output of the IF branch model is an
        // output of the WHILE body model (whileStep->bodyOutputOperands[j] for
        // some j). In that case, the WhileStep modifies the location of
        // step->outerOutputOperands[i] to implement double buffering.
        controller->setOutput(step->outerOutputOperands[i], branchOutputOperands[i]);
    }
    return nextCompound(controller, executor, burstController, mainModelOutputShapes);
}

// Invocations of Controller::setInput in this function must match with invocations of
// StepRoleAnalyzer::setUsedBy in the WhileStep branch in
// ExecutionPlan::CompoundBody::findMemoryStepRoles.
int ExecutionPlan::nextCompound(const WhileStep* step, std::shared_ptr<Controller> controller,
                                std::shared_ptr<StepExecutor>* executor,
                                SharedBurst* burstController,
                                const std::vector<OutputShape>* mainModelOutputShapes) const {
    WhileState& state = controller->mWhileState[controller->mNextStepIndex];
    if (state.stage == WhileState::EVALUATE_CONDITION) {
        state.iteration = state.iteration == WhileState::kOutsideLoop ? 0 : state.iteration + 1;
        VLOG(EXECUTION) << "next: " << *step << ": iteration " << state.iteration
                        << ": evaluating condition";
        controller->mNextStepIndex = step->condStepIndex;

        if (state.iteration == 0) {
            state.startTime = Clock::now();
        }

        // iteration = 0   cond inputs = outer inputs
        // iteration = 1   cond inputs = body outputs
        // iteration = 2   cond inputs = body outputs
        // iteration = 3   cond inputs = ...
        uint32_t loopBodyOutputCount = step->bodyOutputOperands.size();
        CHECK_EQ(step->condInputOperands.size(), step->outerInputOperands.size());
        CHECK_GE(step->condInputOperands.size(), loopBodyOutputCount);
        for (uint32_t i = 0, n = step->condInputOperands.size(); i < n; ++i) {
            bool operandIsInputOnly = i >= loopBodyOutputCount;
            controller->setInput((state.iteration == 0 || operandIsInputOnly)
                                         ? step->outerInputOperands[i]
                                         : step->bodyOutputOperands[i],
                                 step->condInputOperands[i]);
        }

        state.stage = WhileState::EVALUATE_BODY;
        return nextCompound(controller, executor, burstController, mainModelOutputShapes);
    }

    CHECK(state.stage == WhileState::EVALUATE_BODY);
    std::chrono::nanoseconds timeoutDuration(
            controller->mExecutionBuilder->getLoopTimeoutDuration());
    auto duration = Clock::now() - state.startTime;
    if (duration > timeoutDuration) {
        LOG(ERROR) << "WHILE loop timed out after "
                   << std::chrono::duration_cast<std::chrono::milliseconds>(duration).count()
                   << " ms";
        return ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT;
    }

    // If the last step has a sync fence, wait for it to signal before reading the condition value.
    // This is safe because the steps are serialized when doing fenced compute.
    NN_RETURN_IF_ERROR(controller->waitForLastStepSyncFence());
    bool condValue;
    NN_RETURN_IF_ERROR(readConditionValue(controller, step->condOutputOperand, &condValue));
    if (condValue) {
        VLOG(EXECUTION) << "next: " << *step << ": iteration " << state.iteration
                        << ": evaluating body";
        controller->mNextStepIndex = step->bodyStepIndex;

        // iteration = 0   body inputs = cond inputs = outer inputs   body outputs = tmp1
        // iteration = 1   body inputs = cond inputs = tmp1           body outputs = tmp2
        // iteration = 2   body inputs = cond inputs = tmp2           body outputs = tmp1
        // iteration = 3   body inputs = cond inputs = ...            body outputs = ...
#ifdef NN_DEBUGGABLE
        CHECK_GE(step->bodyInputOperands.size(), step->bodyOutputOperands.size());
        CHECK_EQ(step->bodyInputOperands.size(), step->outerInputOperands.size());
        CHECK_EQ(step->bodyInputOperands.size(), step->condInputOperands.size());
        CHECK_GE(step->bodyOutputOperands.size(), step->outerOutputOperands.size());
#endif
        for (uint32_t i = 0, n = step->bodyInputOperands.size(); i < n; ++i) {
            controller->setInput(step->condInputOperands[i], step->bodyInputOperands[i]);
        }
        if (state.iteration != 0) {
            for (const SourceOperandIndex& outputOperand : step->bodyOutputOperands) {
#ifdef NN_DEBUGGABLE
                CHECK_EQ(controller->mSourceOperandToInputIndex.count(outputOperand), 0u);
                CHECK_EQ(controller->mSourceOperandToOutputIndex.count(outputOperand), 0u);
                CHECK_EQ(controller->mSourceOperandToLocationOfTemporary.count(outputOperand), 1u);
                CHECK_EQ(controller->mSourceOperandToLocationOfTemporary2.count(outputOperand), 1u);
#endif
                std::swap(controller->mSourceOperandToLocationOfTemporary[outputOperand],
                          controller->mSourceOperandToLocationOfTemporary2[outputOperand]);
            }
        }
    } else {
        VLOG(EXECUTION) << "next: " << *step << ": iteration " << state.iteration
                        << ": exiting loop";
        controller->mNextStepIndex = step->exitStepIndex;

        // Copy body outputs to outer outputs.
        // TODO: Use outer outputs instead of tmp2 to avoid copying?
        CHECK_LE(step->outerOutputOperands.size(), step->bodyOutputOperands.size());
        for (uint32_t i = 0, n = step->outerOutputOperands.size(); i < n; ++i) {
            // condInputOperands[i] points to a body output operand from the
            // last iteration if we've executed at least one iteration and to a
            // WHILE operation input operand otherwise.
            const SourceOperandIndex& innerOperand = step->condInputOperands[i];
            const SourceOperandIndex& outerOperand = step->outerOutputOperands[i];
            std::optional<Buffer> outerBuffer = getBuffer(controller, outerOperand);
            if (outerBuffer == std::nullopt) {
                // This should never happen.
                LOG(ERROR) << "Unable to get outerBuffer for operand " << toString(outerOperand);
                return ANEURALNETWORKS_OP_FAILED;
            }
            const Operand& sourceOperand =
                    controller->mExecutionBuilder->getSourceOperand(outerOperand);
            const uint32_t size = TypeManager::get()->getSizeOfData(sourceOperand);
            CHECK_NE(size, 0u);
            std::optional<Buffer> innerBuffer = getBuffer(controller, innerOperand);
            if (innerBuffer == std::nullopt) {
                // This should never happen.
                LOG(ERROR) << "Unable to get innerBuffer for operand " << toString(innerOperand);
                return ANEURALNETWORKS_OP_FAILED;
            }
            CHECK_LE(size, innerBuffer->getSize());
            CHECK_LE(size, outerBuffer->getSize());
            memcpy(outerBuffer->getPointer(), innerBuffer->getPointer(), size);
            outerBuffer->flush();
        }
        state.iteration = WhileState::kOutsideLoop;
    }

    state.stage = WhileState::EVALUATE_CONDITION;
    return nextCompound(controller, executor, burstController, mainModelOutputShapes);
}

int ExecutionPlan::nextCompound(const GotoStep* step, std::shared_ptr<Controller> controller,
                                std::shared_ptr<StepExecutor>* executor,
                                SharedBurst* burstController,
                                const std::vector<OutputShape>* mainModelOutputShapes) const {
    VLOG(EXECUTION) << "next: " << *step;
    controller->mNextStepIndex = step->gotoStepIndex;
    return nextCompound(controller, executor, burstController, mainModelOutputShapes);
}

std::shared_ptr<StepExecutor> ExecutionPlan::makeStepExecutor(
        bool reusable, ExecutionBuilder* executionBuilder) const {
    auto simpleBody = simple();
    auto executor = std::make_shared<StepExecutor>(executionBuilder, simpleBody->mModel,
                                                   simpleBody->mDevice, simpleBody->mPreparedModel,
                                                   reusable);
    executor->mapInputsAndOutputsTrivially();
    return executor;
}

void ExecutionPlan::becomeCompoundIfEmpty() {
    CHECK(mState != SIMPLE);
    if (mState == EMPTY) {
        mBody = new CompoundBody(this);
        mState = COMPOUND;
    }
}

ExecutionStep* ExecutionPlan::createNewExecutionStep(uint32_t sourceModelIndex,
                                                     const std::shared_ptr<Device> device) {
    becomeCompoundIfEmpty();
    auto step = std::make_shared<LogicalStep>(std::in_place_type<ExecutionStep>, this,
                                              compound()->mSteps.size(), sourceModelIndex, device);
    compound()->mSteps.push_back(step);
    return step->executionStep();
}

IfStep* ExecutionPlan::createNewIfStep() {
    becomeCompoundIfEmpty();
    auto step = std::make_shared<LogicalStep>(std::in_place_type<IfStep>);
    step->ifStep()->index = compound()->mSteps.size();
    compound()->mSteps.push_back(step);
    return step->ifStep();
}

WhileStep* ExecutionPlan::createNewWhileStep() {
    becomeCompoundIfEmpty();
    auto step = std::make_shared<LogicalStep>(std::in_place_type<WhileStep>);
    step->whileStep()->index = compound()->mSteps.size();
    compound()->mSteps.push_back(step);
    return step->whileStep();
}

GotoStep* ExecutionPlan::createNewGotoStep() {
    becomeCompoundIfEmpty();
    auto step = std::make_shared<LogicalStep>(std::in_place_type<GotoStep>);
    step->gotoStep()->index = compound()->mSteps.size();
    compound()->mSteps.push_back(step);
    return step->gotoStep();
}

void ExecutionPlan::becomeSingleStep(const std::shared_ptr<Device> device,
                                     const ModelBuilder* model) {
    CHECK(mState == EMPTY);
    mBody = new SimpleBody(device, model, mCacheInfo, mToken);
    mState = SIMPLE;
}

void ExecutionPlan::recordOutputDef(SourceOperandIndex sourceOperandIndex, uint32_t stepIndex) {
    auto [it, isNew] =
            compound()->mOutputToDefiningExecutionStep.emplace(sourceOperandIndex, stepIndex);
    CHECK(isNew) << "Step " << stepIndex << " redefines output operand "
                 << toString(sourceOperandIndex) << " already defined by step " << it->second;
}

void ExecutionPlan::recordTemporaryDef(SourceOperandIndex sourceOperandIndex, uint32_t stepIndex) {
    auto [it, isNew] =
            compound()->mTemporaryToDefiningExecutionStep.emplace(sourceOperandIndex, stepIndex);
    CHECK(isNew) << "Step " << stepIndex << " redefines temporary operand "
                 << toString(sourceOperandIndex) << " already defined by step " << it->second;
}

void ExecutionPlan::dump() const {
    if (mBody) {
        mBody->dump();
    } else {
        VLOG(COMPILATION) << "EMPTY";
    }
}

void ExecutionPlan::reset() {
    if (mBody) {
        delete mBody;
        mBody = nullptr;
    }
    mState = EMPTY;
}

bool ExecutionPlan::isSimpleCpu() const {
    return isSimple() && simple()->mDevice == DeviceManager::getCpuDevice();
}

ExecutionPlan::Kind ExecutionPlan::forTest_getKind() const {
    switch (mState) {
        case EMPTY:
            return Kind::EMPTY;
        case SIMPLE:
            CHECK(mBody);
            return mBody->mSuccessfulFinish ? Kind::SIMPLE : Kind::ERROR;
        case COMPOUND:
            CHECK(mBody);
            return mBody->mSuccessfulFinish ? Kind::COMPOUND : Kind::ERROR;
        default:
            LOG(FATAL) << "unexpected state";
            return Kind::ERROR;
    }
}

std::shared_ptr<const Device> ExecutionPlan::forTest_simpleGetDevice() const {
    return simple()->mDevice;
}

const std::vector<std::shared_ptr<LogicalStep>>& ExecutionPlan::forTest_compoundGetSteps() const {
    return compound()->mSteps;
}

std::set<uint32_t> ExecutionPlan::forTest_flatGetDynamicTemporaries() const {
    CHECK_EQ(getSourceModels().size(), size_t(1));
    std::set<uint32_t> ret;
    forEachDynamicTemporary([&ret](SourceOperandIndex dynTemp, const Operand&, uint32_t) {
        ret.insert(dynTemp.second);
    });
    return ret;
}

bool ExecutionPlan::hasDynamicTemporaries() const {
    return mBody == nullptr ? false : mBody->hasDynamicTemporaries();
}

bool ExecutionPlan::forTest_hasStepModelWithNoInputsOrNoOutputs() const {
    return mBody == nullptr ? false : mBody->hasStepModelWithNoInputsOrNoOutputs();
}

bool ExecutionPlan::CompoundBody::hasStepModelWithNoInputsOrNoOutputs() const {
    return std::any_of(mSteps.begin(), mSteps.end(), [](const auto& logicalStep) {
        const ExecutionStep* step = logicalStep->tryExecutionStep();
        return step != nullptr && step->hasNoInputsOrNoOutputs();
    });
}

const uint8_t* ExecutionPlan::forTest_simpleGetCacheToken() const {
    return simple()->mToken.getCacheToken();
}

void ExecutionPlan::SimpleBody::dump() const {
    VLOG(COMPILATION) << "SIMPLE for " << mDevice->getName();
}

void ExecutionPlan::CompoundBody::dump() const {
    for (const auto& step : mSteps) {
        step->dump();
    }
}

SourceOperandIndex ExecutionPlan::getInputSourceOperand(uint32_t index) const {
    const auto* mainModel = getSourceModels().getModel(kMainModelInSourceModels);
    CHECK_LT(index, mainModel->inputCount());
    const auto operandIndex = mainModel->getInputOperandIndex(index);
    return {kMainModelInSourceModels, operandIndex};
}

SourceOperandIndex ExecutionPlan::getOutputSourceOperand(uint32_t index) const {
    const auto* mainModel = getSourceModels().getModel(kMainModelInSourceModels);
    CHECK_LT(index, mainModel->outputCount());
    const auto operandIndex = mainModel->getOutputOperandIndex(index);
    return {kMainModelInSourceModels, operandIndex};
}

void ExecutionPlan::SimpleBody::forEachStepRoleOfInput(uint32_t index,
                                                       const StepRoleCallback& callback) const {
    callback(mPreparedModel.get(), IOType::INPUT, index);
}

void ExecutionPlan::SimpleBody::forEachStepRoleOfOutput(uint32_t index,
                                                        const StepRoleCallback& callback) const {
    callback(mPreparedModel.get(), IOType::OUTPUT, index);
}

// Map an input role of the main model to the input/output roles in the step models.
void ExecutionPlan::CompoundBody::forEachStepRoleOfInput(uint32_t index,
                                                         const StepRoleCallback& callback) const {
    const auto sourceOperandIndex = mPlan->getInputSourceOperand(index);
    forEachStepRoleOfSourceOperand(sourceOperandIndex, callback);
}

// Map an output role of the main model to the input/output roles in the step models.
void ExecutionPlan::CompoundBody::forEachStepRoleOfOutput(uint32_t index,
                                                          const StepRoleCallback& callback) const {
    const auto sourceOperandIndex = mPlan->getOutputSourceOperand(index);
    forEachStepRoleOfSourceOperand(sourceOperandIndex, callback);
}

void ExecutionPlan::CompoundBody::forEachStepRoleOfSourceOperand(
        const SourceOperandIndex& index, const StepRoleCallback& callback) const {
    const auto it = mSourceOperandToStepRoles.find(index);
    if (it == mSourceOperandToStepRoles.end()) return;
    for (const auto& [stepIndex, type, ioIndex] : it->second) {
        CHECK_LT(stepIndex, mSteps.size());
        const auto* step = mSteps[stepIndex]->executionStep();
        callback(step->getPreparedStepModel().get(), type, ioIndex);
    }
}

MemoryPreference ExecutionPlan::getMemoryPreference(IOType type, uint32_t index) const {
    CHECK(mState == SIMPLE || mState == COMPOUND);
    if (mState == SIMPLE) {
        return simple()->mPreparedModel->getMemoryPreference();
    } else {
        const auto sourceOperandIndex = type == IOType::INPUT ? getInputSourceOperand(index)
                                                              : getOutputSourceOperand(index);
        return compound()->getMemoryPreferenceOfSourceOperand(sourceOperandIndex);
    }
}

MemoryPreference ExecutionPlan::CompoundBody::getMemoryPreferenceOfSourceOperand(
        const SourceOperandIndex& index) const {
    uint32_t alignment = kMinMemoryAlignment, padding = kMinMemoryPadding;
    forEachStepRoleOfSourceOperand(
            index, [&alignment, &padding](const auto* preparedModel, IOType, uint32_t) {
                const auto preference = preparedModel->getMemoryPreference();
                alignment = std::max(alignment, preference.alignment);
                padding = std::max(padding, preference.padding);
            });
    return {alignment, padding};
}

void ExecutionPlan::forEachDynamicTemporary(
        const std::function<void(SourceOperandIndex, const Operand&, uint32_t definingStepIndex)>&
                fn) const {
    if (mState != COMPOUND) {
        return;
    }

    for (const auto& logicalStep : compound()->mSteps) {
        if (const ExecutionStep* step = logicalStep->tryExecutionStep()) {
            const uint32_t stepIndex = step->getIndex();
            const uint32_t sourceModelIndex = step->getSourceModelIndex();
            for (const auto& entry : step->getTempsAsStepModelOutputs()) {
                const auto sourceOperandIndex = SourceOperandIndex(sourceModelIndex, entry.first);
                const auto& sourceOperand = getSourceOperand(sourceOperandIndex);
                if (hasUnknownSize(sourceOperand)) {
                    fn(sourceOperandIndex, sourceOperand, stepIndex);
                }
            }
        }
    }
}

int ModelBuilder::partitionTheWork(const std::vector<std::shared_ptr<Device>>& devices,
                                   uint32_t preference, uint32_t priority,
                                   const OptionalTimePoint& deadline, ExecutionPlan* plan,
                                   const std::vector<TokenValuePair>& metaData,
                                   int simulateFailureResultCode) const {
    uint32_t sourceModelIndex = plan->getSourceModels().addModel(this);
    NN_RETURN_IF_ERROR(partitionTheWorkInternal(sourceModelIndex, devices, preference, priority,
                                                deadline, plan));
    int n = plan->finish(preference, priority, deadline, metaData, simulateFailureResultCode);
    if (VLOG_IS_ON(COMPILATION)) {
        VLOG(COMPILATION) << "ModelBuilder::partitionTheWork: source model: ";
        logModelToInfo(makeModel());
        plan->dump();
    }
    return n;
}

int ModelBuilder::partitionTheWorkInternal(uint32_t sourceModelIndex,
                                           const std::vector<std::shared_ptr<Device>>& devices,
                                           uint32_t preference, uint32_t priority,
                                           const OptionalTimePoint& deadline,
                                           ExecutionPlan* plan) const {
    // This function uses a heuristic approach to partitioning the graph.
    // It should be good enough for the first release.

    SourceModels* sourceModels = &plan->getSourceModels();
    const size_t deviceCount = devices.size();
    const size_t operationCount = mOperations.size();

    VLOG(COMPILATION) << "ModelBuilder::partitionTheWork: "
                      << "sourceModelIndex = " << sourceModelIndex << ", "
                      << "deviceCount = " << deviceCount << ", "
                      << "operationCount = " << operationCount;

    // Figure out where each operation will best execute.
    // The value of the vector is the index in the devices vector.
    std::vector<int> bestDeviceForOperation(operationCount);
    NN_RETURN_IF_ERROR(
            findBestDeviceForEachOperation(preference, devices, &bestDeviceForOperation));

    // A special value produced by findBestDeviceForEachOperation meaning that
    // this is a control flow operation scheduled for interpreted execution
    // (see LogicalStep).
    const int kControlFlowInterpreter = deviceCount;

    // If one device will run all the operations, we don't need to split the
    // work. This shortcut does not apply when recursively partitioning
    // referenced models because our plan representation is flat.
    if (sourceModelIndex == kMainModelInSourceModels &&
        std::adjacent_find(bestDeviceForOperation.begin(), bestDeviceForOperation.end(),
                           std::not_equal_to<int>()) == bestDeviceForOperation.end()) {
        const int bestDeviceIndex = bestDeviceForOperation[0];
        // Bypass the partitioning process unless the only operation is a
        // control flow operation scheduled for interpreted execution.
        if (bestDeviceIndex != kControlFlowInterpreter) {
            VLOG(COMPILATION) << "ModelBuilder::partitionTheWork: only one best device: "
                              << bestDeviceIndex << " = " << devices[bestDeviceIndex]->getName();
            plan->becomeSingleStep(devices[bestDeviceIndex], this);
            return ANEURALNETWORKS_NO_ERROR;
        }
    }

    // No easy solution, we need to split the work.

    // We keep track of the operations that are ready to run for each device.
    // perDeviceQueue[deviceCount] is for interpreted execution of control flow
    // (see LogicalStep).
    std::vector<std::queue<uint32_t>> perDeviceQueue(deviceCount + 1);

    // This helper function produces a device name.
    auto deviceName = [&devices, kControlFlowInterpreter,
                       deviceCount](int deviceIndex) -> std::string {
        if (deviceIndex == kControlFlowInterpreter) {
            return "NNAPI";
        } else if (deviceIndex < 0 || size_t(deviceIndex) >= deviceCount) {
            return "{unknown}";
        } else {
            return devices.at(deviceIndex)->getName();
        }
    };

    // This helper function enqueues the operation on the appropriate queue.
    auto enqueueOnAppropriateDevice = [&](uint32_t operationIndex) {
        int deviceIndex = bestDeviceForOperation[operationIndex];
        perDeviceQueue[deviceIndex].push(operationIndex);
        VLOG(COMPILATION) << "enqueueOnAppropriateDevice " << operationIndex << " onto "
                          << deviceIndex << " (" << deviceName(deviceIndex) << ")";
    };

    // This helper function finds a device that has operations ready to process.
    // We start by looking at the control flow queue, and then look at the
    // devices in reverse order (i.e., starting at the end of the devices
    // vector). Earlier devices have a chance to prepare more of the inputs
    // required by other devices. This function returns -1 if all queues are
    // empty.
    auto findNextDeviceToProcess = [&]() -> int {
        for (int i = perDeviceQueue.size() - 1; i >= 0; i--) {
            if (!perDeviceQueue[i].empty()) {
                return i;
            }
        }
        return -1;
    };

    OperandTracker tracker(this, enqueueOnAppropriateDevice);
    // For each iteration of this loop, we'll create either an execution step or
    // an interpreted control flow construct (including nested execution steps
    // and interpreted control flow constructs).
    while (true) {
        // Find the device we'll do this step for.
        int deviceIndex = findNextDeviceToProcess();
        VLOG(COMPILATION) << "findNextDeviceToProcess: " << deviceIndex << " ("
                          << deviceName(deviceIndex) << ")";
        if (deviceIndex < 0) {
            break;
        }

        // Assign as much as possible to this device.
        auto& queue = perDeviceQueue[deviceIndex];
        if (deviceIndex != kControlFlowInterpreter) {
            ExecutionStep* step =
                    plan->createNewExecutionStep(sourceModelIndex, devices[deviceIndex]);
            while (!queue.empty()) {
                uint32_t operationIndex = queue.front();
                queue.pop();
                int n = step->addOperation(operationIndex);
                if (n != ANEURALNETWORKS_NO_ERROR) {
                    LOG(ERROR) << "failed to add operation " << operationIndex << " to step";
                    return n;
                }
                tracker.markProcessed(operationIndex, enqueueOnAppropriateDevice);
            }
        } else {
            while (!queue.empty()) {
                uint32_t operationIndex = queue.front();
                queue.pop();
                const Operation& operation = getOperation(operationIndex);
                if (operation.type == OperationType::IF) {
                    namespace op = operation_if;
                    const Operand& thenOperand =
                            getOperand(operation.inputs[op::kThenModelOperand]);
                    const Operand& elseOperand =
                            getOperand(operation.inputs[op::kElseModelOperand]);
                    const ModelBuilder* thenModel = getReferencedModel(thenOperand);
                    const ModelBuilder* elseModel = getReferencedModel(elseOperand);
                    uint32_t thenModelIndex = sourceModels->addModel(thenModel);
                    uint32_t elseModelIndex = sourceModels->addModel(elseModel);

                    // Emits the following:
                    // Index  Step
                    //   i    if then=(i + 1) else=(j + 1)
                    //  ...   (then model steps)
                    //   j    goto k
                    //  ...   (else model steps)
                    //   k    (steps after the IF)
                    IfStep* ifStep = plan->createNewIfStep();
                    ifStep->conditionOperandIndex = SourceOperandIndex(
                            sourceModelIndex, operation.inputs[op::kCondBoolOperand]);
                    ifStep->thenStepIndex = plan->getNextStepIndex();
                    NN_RETURN_IF_ERROR(thenModel->partitionTheWorkInternal(
                            thenModelIndex, devices, preference, priority, deadline, plan));
                    GotoStep* afterThenBranch = plan->createNewGotoStep();
                    ifStep->elseStepIndex = plan->getNextStepIndex();
                    NN_RETURN_IF_ERROR(elseModel->partitionTheWorkInternal(
                            elseModelIndex, devices, preference, priority, deadline, plan));
                    afterThenBranch->gotoStepIndex = plan->getNextStepIndex();

                    // Outer model operands.
                    for (uint32_t i = op::kFirstInput, n = operation.inputs.size(); i < n; ++i) {
                        ifStep->outerInputOperands.emplace_back(sourceModelIndex,
                                                                operation.inputs[i]);
                    }
                    for (uint32_t i = 0, n = operation.outputs.size(); i < n; ++i) {
                        ifStep->outerOutputOperands.emplace_back(sourceModelIndex,
                                                                 operation.outputs[i]);
                    }
                    // Then model operands.
                    for (uint32_t i = 0, n = thenModel->inputCount(); i < n; ++i) {
                        ifStep->thenBranchInputOperands.emplace_back(
                                thenModelIndex, thenModel->getInputOperandIndex(i));
                    }
                    for (uint32_t i = 0, n = thenModel->outputCount(); i < n; ++i) {
                        ifStep->thenBranchOutputOperands.emplace_back(
                                thenModelIndex, thenModel->getOutputOperandIndex(i));
                    }
                    // Else model operands.
                    for (uint32_t i = 0, n = elseModel->inputCount(); i < n; ++i) {
                        ifStep->elseBranchInputOperands.emplace_back(
                                elseModelIndex, elseModel->getInputOperandIndex(i));
                    }
                    for (uint32_t i = 0, n = elseModel->outputCount(); i < n; ++i) {
                        ifStep->elseBranchOutputOperands.emplace_back(
                                elseModelIndex, elseModel->getOutputOperandIndex(i));
                    }
                } else if (operation.type == OperationType::WHILE) {
                    namespace op = operation_while;
                    const Operand& condOperand =
                            getOperand(operation.inputs[op::kCondModelOperand]);
                    const Operand& bodyOperand =
                            getOperand(operation.inputs[op::kBodyModelOperand]);
                    const ModelBuilder* condModel = getReferencedModel(condOperand);
                    const ModelBuilder* bodyModel = getReferencedModel(bodyOperand);
                    uint32_t condModelIndex = sourceModels->addModel(condModel);
                    uint32_t bodyModelIndex = sourceModels->addModel(bodyModel);

                    // Emits the following:
                    // Index  Step
                    //   i    while cond=(i + 1) body=(j + 1) exit=(k + 1)
                    //  ...   (cond model steps)
                    //   j    goto i
                    //  ...   (body model steps)
                    //   k    goto i
                    //  ...   (steps after the WHILE)
                    //
                    //  Note that WhileStep has WhileState associated with it.
                    WhileStep* whileStep = plan->createNewWhileStep();
                    whileStep->condStepIndex = plan->getNextStepIndex();
                    NN_RETURN_IF_ERROR(condModel->partitionTheWorkInternal(
                            condModelIndex, devices, preference, priority, deadline, plan));
                    GotoStep* afterCond = plan->createNewGotoStep();
                    afterCond->gotoStepIndex = whileStep->index;
                    whileStep->bodyStepIndex = plan->getNextStepIndex();
                    NN_RETURN_IF_ERROR(bodyModel->partitionTheWorkInternal(
                            bodyModelIndex, devices, preference, priority, deadline, plan));
                    GotoStep* afterBody = plan->createNewGotoStep();
                    afterBody->gotoStepIndex = whileStep->index;
                    whileStep->exitStepIndex = plan->getNextStepIndex();

                    // Outer model operands.
                    for (uint32_t i = op::kFirstInput, n = operation.inputs.size(); i < n; ++i) {
                        whileStep->outerInputOperands.emplace_back(sourceModelIndex,
                                                                   operation.inputs[i]);
                    }
                    for (uint32_t i = 0, n = operation.outputs.size(); i < n; ++i) {
                        whileStep->outerOutputOperands.emplace_back(sourceModelIndex,
                                                                    operation.outputs[i]);
                    }
                    // Cond model operands.
                    for (uint32_t i = 0, n = condModel->inputCount(); i < n; ++i) {
                        whileStep->condInputOperands.emplace_back(
                                condModelIndex, condModel->getInputOperandIndex(i));
                    }
                    whileStep->condOutputOperand =
                            SourceOperandIndex(condModelIndex, condModel->getOutputOperandIndex(0));
                    // Body model operands.
                    for (uint32_t i = 0, n = bodyModel->inputCount(); i < n; ++i) {
                        whileStep->bodyInputOperands.emplace_back(
                                bodyModelIndex, bodyModel->getInputOperandIndex(i));
                    }
                    for (uint32_t i = 0, n = bodyModel->outputCount(); i < n; ++i) {
                        whileStep->bodyOutputOperands.emplace_back(
                                bodyModelIndex, bodyModel->getOutputOperandIndex(i));
                    }
                } else {
                    CHECK(false) << operation.type << " is not a control flow operation";
                }
                tracker.markProcessed(operationIndex, enqueueOnAppropriateDevice);
            }
        }
    }
    return ANEURALNETWORKS_NO_ERROR;
}

float ModelBuilder::getPerformance(uint32_t preference,
                                   const std::shared_ptr<Device> device) const {
    // Note that we will call this method multiple times per compilation with
    // the same arguments if there are nested control flow operations and we
    // decide to execute the outer operation on the ExecutionPlan::next()
    // interpreter.
    //
    // This is a potential compilation performance problem. To work around it,
    // the performance value could be cached for the duration of a compilation.
    float perf = 0;
    const size_t operationCount = mOperations.size();
    for (size_t operationIndex = 0; operationIndex < operationCount; operationIndex++) {
        perf += getPerformance(preference, device, operationIndex);
    }
    return perf;
}

float ModelBuilder::getPerformance(uint32_t preference, const std::shared_ptr<Device> device,
                                   uint32_t operationIndex) const {
    auto applyPreference = [preference](const Capabilities::PerformanceInfo& perf) {
        return preference == ANEURALNETWORKS_PREFER_LOW_POWER ? perf.powerUsage : perf.execTime;
    };

    const Operation& operation = getOperation(operationIndex);

    if (operation.type == OperationType::IF) {
        namespace op = operation_if;
        const Operand& thenOperand = getOperand(operation.inputs[op::kThenModelOperand]);
        const Operand& elseOperand = getOperand(operation.inputs[op::kElseModelOperand]);
        const ModelBuilder* thenModel = getReferencedModel(thenOperand);
        const ModelBuilder* elseModel = getReferencedModel(elseOperand);
        return applyPreference(device->getIfPerformance()) +
               0.5 * (thenModel->getPerformance(preference, device) +
                      elseModel->getPerformance(preference, device));
    }

    if (operation.type == OperationType::WHILE) {
        namespace op = operation_while;
        const Operand& condOperand = getOperand(operation.inputs[op::kCondModelOperand]);
        const Operand& bodyOperand = getOperand(operation.inputs[op::kBodyModelOperand]);
        const ModelBuilder* condModel = getReferencedModel(condOperand);
        const ModelBuilder* bodyModel = getReferencedModel(bodyOperand);
        return applyPreference(device->getWhilePerformance()) +
               condModel->getPerformance(preference, device) +
               bodyModel->getPerformance(preference, device);
    }

    // TODO This assumes that the type is dictated by the first operand. This is
    // currently the case but is not a safe assumption to make in the long term.
    const uint32_t operandIndex = operation.inputs[0];
    const OperandType operandType = mOperands[operandIndex].type;
    switch (operandType) {
        case OperandType::FLOAT32:
            if (mRelaxComputationFloat32toFloat16) {
                return applyPreference(device->getRelaxedFloat32toFloat16PerformanceScalar());
            }
            break;
        case OperandType::TENSOR_FLOAT32:
            if (mRelaxComputationFloat32toFloat16) {
                return applyPreference(device->getRelaxedFloat32toFloat16PerformanceTensor());
            }
            break;
        default:
            break;
    }

    return applyPreference(device->getPerformance(operandType));
}

bool ModelBuilder::isControlFlowOperationWithOperandOfUnknownSize(uint32_t operationIndex) const {
    auto containsUnknownSize = [](const ModelBuilder* model,
                                  const std::vector<uint32_t>& operandIndexes) {
        for (uint32_t operandIndex : operandIndexes) {
            if (hasUnknownSize(model->getOperand(operandIndex))) {
                return true;
            }
        }
        return false;
    };

    const Operation& operation = getOperation(operationIndex);

    if (operation.type == OperationType::IF) {
        namespace op = operation_if;
        const Operand& thenOperand = getOperand(operation.inputs[op::kThenModelOperand]);
        const Operand& elseOperand = getOperand(operation.inputs[op::kElseModelOperand]);
        const ModelBuilder* thenModel = getReferencedModel(thenOperand);
        const ModelBuilder* elseModel = getReferencedModel(elseOperand);
        return containsUnknownSize(this, operation.inputs) ||
               containsUnknownSize(this, operation.outputs) ||
               containsUnknownSize(thenModel, thenModel->getInputOperandIndexes()) ||
               containsUnknownSize(thenModel, thenModel->getOutputOperandIndexes()) ||
               containsUnknownSize(elseModel, elseModel->getInputOperandIndexes()) ||
               containsUnknownSize(elseModel, elseModel->getOutputOperandIndexes());
    }

    if (operation.type == OperationType::WHILE) {
        namespace op = operation_while;
        const Operand& condOperand = getOperand(operation.inputs[op::kCondModelOperand]);
        const Operand& bodyOperand = getOperand(operation.inputs[op::kBodyModelOperand]);
        const ModelBuilder* condModel = getReferencedModel(condOperand);
        const ModelBuilder* bodyModel = getReferencedModel(bodyOperand);
        return containsUnknownSize(this, operation.inputs) ||
               containsUnknownSize(this, operation.outputs) ||
               containsUnknownSize(condModel, condModel->getInputOperandIndexes()) ||
               containsUnknownSize(condModel, condModel->getOutputOperandIndexes()) ||
               containsUnknownSize(bodyModel, bodyModel->getInputOperandIndexes()) ||
               containsUnknownSize(bodyModel, bodyModel->getOutputOperandIndexes());
    }

    // Not a control flow operation.
    return false;
}

bool ModelBuilder::supportedByControlFlowInterpreter(uint32_t operationIndex) const {
    const Operation& operation = getOperation(operationIndex);
    return (operation.type == OperationType::IF || operation.type == OperationType::WHILE) &&
           // The partitioner does not support dynamic temporaries (b/132458982).
           !isControlFlowOperationWithOperandOfUnknownSize(operationIndex);
}

namespace {

// This class determines whether a given device can execute a given operation
class CanDo {
   public:
    CanDo() {}

    void initialize(const MetaModel& metaModel, std::shared_ptr<Device> device) {
        mSupportsOperationByIndex = device->getSupportedOperations(metaModel);
    }

    bool check(size_t operationIndex) const { return mSupportsOperationByIndex[operationIndex]; }

   private:
    std::vector<bool> mSupportsOperationByIndex;
};

}  // anonymous namespace

int ModelBuilder::findBestDeviceForEachOperation(
        uint32_t preference, const std::vector<std::shared_ptr<Device>>& devices,
        std::vector<int>* bestDeviceForOperation) const {
    const MetaModel metaModel(makeModel(), DeviceManager::get()->strictSlicing());

    const size_t deviceCount = devices.size();
    std::vector<CanDo> canDo(deviceCount);
    for (size_t deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
        canDo[deviceIndex].initialize(metaModel, devices[deviceIndex]);
    }

    // Figure out the best driver for each operation.
    const size_t operationCount = mOperations.size();
    for (size_t operationIndex = 0; operationIndex < operationCount; operationIndex++) {
        const Operation& operation = getOperation(operationIndex);
        // Find which device, including CPU fallback, gives the best performance for this operation.
        int bestChoice = -1;

        if (isControlFlowOperationWithOperandOfUnknownSize(operationIndex)) {
            // Do not schedule control flow operations with unknown size to
            // non-CPU devices because this is not supported by the 1.3 HAL.
            // See http://b/159076604#comment5.
            auto cpuDeviceIterator =
                    std::find(devices.begin(), devices.end(), DeviceManager::getCpuDevice());
            if (cpuDeviceIterator != devices.end()) {
                int cpuDeviceIndex = cpuDeviceIterator - devices.begin();
                if (canDo[cpuDeviceIndex].check(operationIndex)) {
                    bestChoice = cpuDeviceIndex;
                }
            }
        } else {
            float bestPerfVal = 0.0;  // Do not check bestPerfVal if bestChoice < 0.
            for (size_t deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
                const auto& device = devices[deviceIndex];
                if (canDo[deviceIndex].check(operationIndex)) {
                    const float perfVal = getPerformance(preference, device, operationIndex);
                    const bool deviceIsPreferred = (device == DeviceManager::getCpuDevice());
                    if (bestChoice < 0 || perfVal < bestPerfVal ||
                        (perfVal == bestPerfVal && deviceIsPreferred)) {
                        bestChoice = deviceIndex;
                        bestPerfVal = perfVal;
                    }
                } else {
                    // Somewhat noisy logging, but only place where the user of NNAPI can get
                    // feedback on why an operation was not run on a specific device.
                    //
                    // Logs O(operationCount * deviceCount) times, but typically deviceCount is
                    // very small.
                    VLOG(COMPILATION) << "Device " << device->getName() << " can't do operation "
                                      << operation.type << ":" << operationIndex;
                }
            }
        }

        if (bestChoice < 0) {
            LOG(ERROR) << "No driver can do operation " << operation.type;
            return ANEURALNETWORKS_BAD_DATA;
        } else if (devices[bestChoice] == DeviceManager::getCpuDevice() &&
                   supportedByControlFlowInterpreter(operationIndex)) {
            // Run control flow on the ExecutionPlan::next() interpreter and try
            // to delegate referenced models.
            const int kControlFlowInterpreter = deviceCount;
            (*bestDeviceForOperation)[operationIndex] = kControlFlowInterpreter;
            VLOG(COMPILATION) << "ModelBuilder::findBestDeviceForEachOperation(" << operation.type
                              << ":" << operationIndex << ") = -1 (NNAPI)";
        } else {
            (*bestDeviceForOperation)[operationIndex] = bestChoice;
            VLOG(COMPILATION) << "ModelBuilder::findBestDeviceForEachOperation(" << operation.type
                              << ":" << operationIndex << ") = " << bestChoice << " ("
                              << devices[bestChoice]->getName() << ")";
        }
    }
    return ANEURALNETWORKS_NO_ERROR;
}

}  // namespace nn
}  // namespace android
