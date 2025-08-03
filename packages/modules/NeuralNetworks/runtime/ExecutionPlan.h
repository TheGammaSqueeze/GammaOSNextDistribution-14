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

// Classes used to plan how to execute a model across multiple devices.

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_PLAN_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_PLAN_H

#include <LegacyUtils.h>
#include <TokenHasher.h>
#include <android-base/logging.h>
#include <nnapi/IBurst.h>
#include <nnapi/Types.h>

#include <algorithm>
#include <chrono>
#include <functional>
#include <map>
#include <memory>
#include <ostream>
#include <set>
#include <string>
#include <tuple>
#include <unordered_map>
#include <utility>
#include <variant>
#include <vector>

#include "Memory.h"
#include "ModelArgumentInfo.h"
#include "ModelBuilder.h"
#include "NeuralNetworks.h"

namespace android {
namespace nn {

class BurstBuilder;
class CompilationBuilder;
class Device;
class ExecutionBuilder;
class ExecutionPlan;
class RuntimeMemory;
class RuntimePreparedModel;
class StepExecutor;

struct ConstantReferenceLocation;
struct CacheInfo;

// NNAPI Control Flow allows referring to an NNAPI model inside another NNAPI
// model using OperandType::SUBGRAPH. For example, an IF operation within a
// model mey refer to two other models corresponding to then and else branches.
//
// The partitioning process transforms this nested representation into a list
// of LogicalSteps.
//
// The following terms are used:
// - The main model is the top-level model being compiled (not referenced by any
//   OperandType::SUBGRAPH operand within the compilation).
// - A referenced model is a non-top-level model being compiled (referenced by
//   at least one OperandType::SUBGRAPH operand within the set of models being
//   compiled).
// - A source model is either the main model or a referenced model.
// - A step model is a model excerpted from a source model during the
//   partitioning process.
// - A partition is a LogicalStep representing at least one operation of a
//   source model. In particular, ExecutionStep represents a step model, IfStep
//   represents an IF operation, WhileStep represents a WHILE operation.
//   A GotoStep is not a partition.
// - A partition boundary operand is a source model operand that is an input or
//   output of a partition. For ExecutionStep, the inputs and outputs of the
//   step model are boundary operands; for IfStep and WhileStep, the inputs and
//   outputs of the corresponding operation are boundary operands.
// - A partition boundary static temporary is a partition boundary
//   operand which is of lifetime TEMPORARY_VARIABLE in the source model and
//   whose dimensions are fully specified.
// - A partition boundary dynamic temporary is a partition boundary
//   operand which is of lifetime TEMPORARY_VARIABLE in the source model and
//   whose dimensions are not fully specified.
// - A main execution is the execution of a main model.
//
// Referenced models can be sources of partition boundary operands. For example,
// this happens when a referenced model is partitioned into one or more
// LogicalSteps.
//
// (model index, operand index within model)
typedef std::pair<uint32_t, uint32_t> SourceOperandIndex;

// A collection of source models.
class SourceModels {
   public:
    uint32_t addModel(const ModelBuilder* model) {
        uint32_t modelIndex = mModels.size();
        mModels.push_back(model);
        return modelIndex;
    }

    const ModelBuilder* getModel(uint32_t index) const { return mModels[index]; }

    uint32_t size() const { return mModels.size(); }

   private:
    std::vector<const ModelBuilder*> mModels;
};

// Represents all partition boundary dynamic temporaries for a particular main
// execution.
//
// Usage pattern:
// - declare() every partition boundary dynamic temporary.
// - endDeclarations().  After this point, lookup() is permitted.
// - Before executing an ExecutionStep, call allocate().
// - After executing an ExecutionStep, call redeclare() for every partition
//   boundary dynamic temporary for which we've learned or guessed more about
//   the dimensions or length.
//
// Each partition boundary temporary has a location assigned by allocate() for
// its defining step (see declare() and allocate()).  That location remains
// valid until redeclare() increases the length of some temporary in its defining
// step or allocate() is called again for its defining step.
class DynamicTemporaries {
    DISALLOW_COPY_AND_ASSIGN(DynamicTemporaries);

   public:
    DynamicTemporaries() = default;
    DynamicTemporaries(DynamicTemporaries&&) = default;
    DynamicTemporaries& operator=(DynamicTemporaries&&) = default;

    // Declare a dynamic temporary.  stepIndex is the step that defines the
    // temporary (i.e., in which the temporary appears as an operation output
    // operand).  initialDimensions and initialLength indicate what we know or
    // (in the case of length) guess about those properties.
    void declare(SourceOperandIndex sourceOperandIndex, uint32_t stepIndex,
                 const Dimensions& initialDimensions, uint32_t initialLength, uint32_t alignment,
                 uint32_t padding);

    // Indicate that we've finished declaring all dynamic temporaries.
    void endDeclarations() {
        CHECK(!mDeclared);
        mDeclared = true;
    }

    // Redeclare a dynamic temporary, indicating what we've learned about it.
    // This may invalidate the location of temporaries defined by its step.
    // Returns true if dimensions or length changed, false otherwise.
    bool redeclare(SourceOperandIndex sourceOperandIndex, const Dimensions& newDimensions,
                   uint32_t newLength);

    // Ensure that all dynamic temporaries defined by the specified step have
    // locations.  The return value is a ResultCode (e.g.,
    // ANEURALNETWORKS_NO_ERROR).
    //
    // Even if dynamic temporaries have already been allocated for this step,
    // this call may reallocate them.  A reallocation is not guaranteed to
    // preserve location (LocationAndShape.memory, LocationAndShape.offset) or
    // contents of temporaries.
    int allocate(uint32_t stepIndex);

    // Do the dynamic temporaries defined by this step have valid allocations?
    // (Will be true if there are no dynamic temporaries defined by this step.)
    bool allocated(uint32_t stepIndex) const;

    // Dump information to VLOG(EXECUTION).
    void vlogDump(const char* context = nullptr) const;

    // If the specified operand is a dynamic temporary, return location and
    // shape information; otherwise, return std::nullopt.
    //
    // If temporary exists but does not have a valid allocation, then:
    //  - If mustBeAllocated == true, then trigger a failed CHECK().
    //  - If mustBeAllocated == false, then memory == nullptr and offset == ~0.
    struct LocationAndShape {
        const RuntimeMemory* memory;
        uint32_t offset;
        const Dimensions* dimensions;
        uint32_t paddedLength;
    };
    std::optional<LocationAndShape> lookup(SourceOperandIndex sourceOperandIndex,
                                           bool mustBeAllocated = true) const;

    // Have any dynamic temporaries been declared?
    bool empty() const { return mSourceOperandToTemporary.empty(); }

   private:
    // The same as LocationAndShape, except that:
    // - the base of the location is represented not by memory but by defining stepIndex
    // - it additionally contains information about the preferred alignment and padding
    struct InternalLocationAndShape {
        uint32_t stepIndex;
        uint32_t offset;
        Dimensions dimensions;
        uint32_t paddedLength;
        uint32_t alignment;
        uint32_t padding;
    };
    std::map<SourceOperandIndex, InternalLocationAndShape> mSourceOperandToTemporary;

    // Every dynamic temporary defined at a given stepIndex.
    std::map<uint32_t, std::vector<SourceOperandIndex>> mStepIndexToSourceOperandIndexes;

    std::map<uint32_t, std::unique_ptr<MemoryAshmem>> mStepIndexToMemory;

    // For a given defining stepIndex, we consider either all its dynamic
    // temporaries to be allocated (have valid locations) or none of them to be.
    std::set<uint32_t> mAllocatedStepIndexes;

    // Has endDeclarations() been called?
    bool mDeclared = false;
};

// The location of a static temporary.
struct StaticTemporaryLocation {
    // The offset relative to ExecutionPlan::Controller::mTemporaries during execution.
    uint32_t offset;
    uint32_t paddedLength;
};

// An excerpt of a source model to be run by a specific device.
class ExecutionStep {
   public:
    typedef std::vector<std::pair<uint32_t, uint32_t>> RemapVectorType;
    typedef std::set<std::pair<uint32_t, uint32_t>> StepModelOutputSetType;

    enum OperandKind { INPUT, OUTPUT };

    ExecutionStep(ExecutionPlan* plan, uint32_t stepIndex, uint32_t sourceModelIndex,
                  std::shared_ptr<Device> device);

    int addOperation(int operationIndex);
    int addOperand(uint32_t sourceOperandIndex, uint32_t* stepOperandIndex, OperandKind kind);

    // Each container entry is of the form (source model operand index, step model operand index)
    const RemapVectorType& getStepModelInputs() const { return mStepModelInputs; }
    const RemapVectorType& getStepModelOutputs() const { return mStepModelOutputs; }
    const RemapVectorType& getModelInputs() const { return mModelInputs; }
    const RemapVectorType& getModelOutputs() const { return mModelOutputs; }
    const RemapVectorType& getTempsAsStepModelInputs() const { return mTempsAsStepModelInputs; }
    const StepModelOutputSetType& getTempsAsStepModelOutputs() const {
        return mTempsAsStepModelOutputs;
    }
    const RemapVectorType& getOutputsAsStepModelInputs() const { return mOutputsAsStepModelInputs; }
    const std::vector<uint32_t>& getInputIndexStepModelToMainModel() const {
        return mInputIndexStepModelToMainModel;
    }
    const std::vector<uint32_t>& getOutputIndexStepModelToMainModel() const {
        return mOutputIndexStepModelToMainModel;
    }
    const std::vector<uint32_t>& getOutputsAsStepModelInputsIndexToMainModel() const {
        return mOutputsAsStepModelInputsIndexToMainModel;
    }

    const std::set<uint32_t>& getModelOutputsThatAreDownstreamInputs() const {
        return mModelOutputsThatAreDownstreamInputs;
    }

    uint32_t getIndex() const { return mIndex; }
    uint32_t getSourceModelIndex() const { return mSourceModelIndex; }

    void declareModelOutputIsDownstreamInput(uint32_t mainModelOutputIndex);
    void recordTempAsStepModelOutput(uint32_t stepOperandIndex);

    // If this step has a step model output of unknown size, sets
    // *hasOutputOfUnknownSize to true; otherwise, leaves it
    // unchanged.
    int finishStepModel(const ModelBuilder* mainModel, bool* hasOutputOfUnknownSize,
                        int32_t executionPreference, int32_t priority);

    const ModelBuilder* getStepModel() const { return &mStepModel; }
    std::shared_ptr<Device> getDevice() const { return mDevice; }

    // only available after calling finishStepModel()
    std::shared_ptr<RuntimePreparedModel> getPreparedStepModel() const {
        return mPreparedStepModel;
    }

    // Map inputs and outputs from ExecutionBuilder to StepExecutor.
    //
    // This method only reads map entries for which the first element of
    // SourceOperandIndex is mSourceModelIndex.
    //
    // mainModelOutputShapes may be nullptr if the only main model outputs that are
    //     inputs of this step are of fully specified shape.
    void mapInputsAndOutputs(
            std::shared_ptr<StepExecutor> stepExecutor,
            const std::vector<OutputShape>* mainModelOutputShapes,
            const RuntimeMemory* temporaryMemory,  // for static temporaries
            const std::map<SourceOperandIndex, StaticTemporaryLocation>&
                    sourceOperandToLocationOfTemporary,  // for static temporaries
            const DynamicTemporaries& dynamicTemporaries,
            const std::map<SourceOperandIndex, uint32_t>& sourceOperandToInputIndex,
            const std::map<SourceOperandIndex, uint32_t>& sourceOperandToOutputIndex,
            const std::map<SourceOperandIndex, ConstantReferenceLocation>&
                    sourceOperandToConstantReference) const;

    bool hasNoInputsOrNoOutputs() const {
        return mStepModelInputs.empty() || mStepModelOutputs.empty();
    }

    void dump() const;

    // For test only, get the transformed cache token.
    const uint8_t* forTest_getCacheToken() const { return mToken.getCacheToken(); }

   private:
    void logStepModel() const;
    const ModelBuilder* getSourceModel() const;

    // TODO: Some of the data is working state information that
    // shouldn't be needed after we've constructed but not executed
    // the step.

    ExecutionPlan* mPlan;
    uint32_t mIndex;  // index of step within plan
    uint32_t mSourceModelIndex;
    ModelBuilder mStepModel;  // An excerpt of a source model to be run by one device.
    std::shared_ptr<Device> mDevice;
    std::shared_ptr<RuntimePreparedModel> mPreparedStepModel;

    // All inputs of this step model:
    //     (source model operand index, step model operand index)
    //
    // Depending on whether the source operand is an input or output of the main
    // model, the memory should be mapped using
    // ExecutionPlan::CompoundBody::mSourceOperandToInputIndex,
    // ExecutionPlan::Controller::mSourceOperandToLocationOfTemporary, or
    // ExecutionPlan::Controller::mDynamicTemporaries, or
    // ExecutionPlan::CompoundBody::mSourceOperandToOutputIndex.
    RemapVectorType mStepModelInputs;
    // All outputs of this step model:
    //     (source model operand index, step model operand index)
    //
    // Depending on whether the source operand is an output of the main model,
    // the memory should be mapped using
    // ExecutionPlan::CompoundBody::mSourceOperandToOutputIndex,
    // ExecutionPlan::Controller::mSourceOperandToLocationOfTemporary, or
    // ExecutionPlan::Controller::mDynamicTemporaries.
    //
    // mOutputIndexStepModelToMainModel and declareModelOutputIsDownstreamInput()
    // rely on mModelOutputs being a prefix of mStepModelOutputs.
    RemapVectorType mStepModelOutputs;
    // Inputs of main model that are also inputs of this step model:
    //     (main model operand index, step model operand index)
    RemapVectorType mModelInputs;
    // Outputs of main model that are also outputs of this step model:
    //     (main model operand index, step model operand index)
    RemapVectorType mModelOutputs;
    // Temporaries of source model that are inputs of this step model:
    //     (source model operand index, step model operand index)
    RemapVectorType mTempsAsStepModelInputs;
    // Temporaries of source model that are outputs of this step model:
    //     (source model operand index, step model operand index)
    StepModelOutputSetType mTempsAsStepModelOutputs;
    // Outputs of main model that are inputs of this step model:
    //     (main model operand index, step model operand index)
    RemapVectorType mOutputsAsStepModelInputs;
    // Converts operand indexes from the source model to the step model.
    std::unordered_map<uint32_t, uint32_t> mOperandMap;
    // Converts input indexes from the step model to the main model
    // (these are input indexes, not operand indexes).  This vector
    // only describes inputs of the step model that are also inputs of
    // the main model -- that is, mModelInputs but not mTempsAsStepModelInputs.
    std::vector<uint32_t> mInputIndexStepModelToMainModel;
    // Converts output indexes from the step model to the main model
    // (these are output indexes, not operand indexes).  This vector
    // only describes outputs of the step model that are also outputs of
    // the main model -- that is, mModelOutputs but not
    // mTempsAsStepModelOutputs.
    std::vector<uint32_t> mOutputIndexStepModelToMainModel;
    // Converts indexes into mOutputsAsStepModelInputs to indexes into
    // main model outputs (these are input and output indexes, not
    // operand indexes).  To be specific, if the main model outputs
    // are mainModelOutputs,
    //
    //     mOutputsAsStepModelInputsIndexToMainModel.size() ==
    //     mOutputsAsStepModelInputs.size()
    //
    // and when (0 <= i < mOutputsAsStepModelInputs.size()),
    //
    //     mainModelOutputs[mOutputsAsStepModelInputsIndexToMainModel[i]] ==
    //     mOutputsAsStepModelInputs[i].first
    std::vector<uint32_t> mOutputsAsStepModelInputsIndexToMainModel;

    // Step model output indexes (not operand indexes) that are outputs of the
    // main model used as inputs to some other partition.
    std::set<uint32_t> mModelOutputsThatAreDownstreamInputs;

    // The compilation caching token.
    TokenHasher mToken;
};

// An IF operation to be run on the ExecutionPlan::next() interpreter. The
// branch models might run on devices. See LogicalStep.
//
// Execution plan structure:
// Index  Step
//   i    if then=(i + 1) else=(j + 1)
//  ...   (then model steps)
//   j    goto k
//  ...   (else model steps)
//   k    (steps after the IF)
struct IfStep {
    // The index of this step.
    size_t index = ~size_t(0);
    // The index of the first step of the "then" branch.
    size_t thenStepIndex = ~size_t(0);
    // The index of the first step of the "else" branch.
    size_t elseStepIndex = ~size_t(0);
    // The boolean condition input of the IF operation. The value of this
    // operand determines the branch of the IF operation to be executed.
    SourceOperandIndex conditionOperandIndex = {~uint32_t(0), ~uint32_t(0)};
    // Input operands of the IF operation to be passed to a branch model.
    std::vector<SourceOperandIndex> outerInputOperands;
    // Output operands of the IF operation.
    std::vector<SourceOperandIndex> outerOutputOperands;
    // Input operands of the "then" branch model.
    std::vector<SourceOperandIndex> thenBranchInputOperands;
    // Output operands of the "then" branch model.
    std::vector<SourceOperandIndex> thenBranchOutputOperands;
    // Input operands of the "else" branch model.
    std::vector<SourceOperandIndex> elseBranchInputOperands;
    // Output operands of the "else" branch model.
    std::vector<SourceOperandIndex> elseBranchOutputOperands;
};

// A WHILE operation to be run on the ExecutionPlan::next() interpreter. The
// condition and body models might run other devices. See LogicalStep.
//
// Execution plan structure:
// Index  Step
//   i    while cond=(i + 1) body=(j + 1) exit=(k + 1)
//  ...   (cond model steps)
//   j    goto i
//  ...   (body model steps)
//   k    goto i
//  ...   (steps after the WHILE)
//
//  Note that WhileStep has WhileState associated with it.
struct WhileStep {
    // The index of this step.
    size_t index = ~size_t(0);
    // The index of the first step of the condition model.
    size_t condStepIndex = ~size_t(0);
    // The index of the first step of the body model.
    size_t bodyStepIndex = ~size_t(0);
    // The index of the first step after the loop.
    size_t exitStepIndex = ~size_t(0);
    // Input operands of the WHILE operation to be passed to the condition and
    // body models.
    std::vector<SourceOperandIndex> outerInputOperands;
    // Output operands of the WHILE operation.
    std::vector<SourceOperandIndex> outerOutputOperands;
    // Input operands of the condition model.
    std::vector<SourceOperandIndex> condInputOperands;
    // Output operand of the condition model. The value of this operand
    // determines whether to continue execution or exit the loop.
    SourceOperandIndex condOutputOperand = {~uint32_t(0), ~uint32_t(0)};
    // Input operands of the body model.
    std::vector<SourceOperandIndex> bodyInputOperands;
    // Output operands of the body model.
    std::vector<SourceOperandIndex> bodyOutputOperands;
};

// A helper step. See LogicalStep.
struct GotoStep {
    // The index of this step.
    size_t index = ~size_t(0);
    // The index of the step to go to.
    size_t gotoStepIndex = ~size_t(0);
};

// One of ExecutionStep, IfStep, WhileStep, or GotoStep.
//
// When ExecutionPlan::next() is called, it interprets logical steps until it
// encounters an ExecutionStep ("interpreted execution").
// - For an IfStep, it decides which branch to take and proceeds to the
//   corresponding step.
// - For a WhileStep, it decides whether to execute the condition or body (based
//   on WhileState), or exit the loop (based on the condition model output), and
//   proceeds to the corresponding step.
// - For a GotoStep, it proceeds to the indicated step unconditionally.
class LogicalStep {
   public:
    template <typename... Args>
    explicit LogicalStep(Args&&... args) : mStep(std::forward<Args>(args)...) {}

    bool isExecution() const { return std::holds_alternative<ExecutionStep>(mStep); }
    bool isIf() const { return std::holds_alternative<IfStep>(mStep); }
    bool isWhile() const { return std::holds_alternative<WhileStep>(mStep); }
    bool isGoto() const { return std::holds_alternative<GotoStep>(mStep); }

    // Returns a non-null pointer or crashes.
    ExecutionStep* executionStep() { return &std::get<ExecutionStep>(mStep); }
    IfStep* ifStep() { return &std::get<IfStep>(mStep); }
    WhileStep* whileStep() { return &std::get<WhileStep>(mStep); }
    GotoStep* gotoStep() { return &std::get<GotoStep>(mStep); }

    // Returns a non-null pointer or crashes.
    const ExecutionStep* executionStep() const { return &std::get<ExecutionStep>(mStep); }
    const IfStep* ifStep() const { return &std::get<IfStep>(mStep); }
    const WhileStep* whileStep() const { return &std::get<WhileStep>(mStep); }
    const GotoStep* gotoStep() const { return &std::get<GotoStep>(mStep); }

    // May return nullptr.
    ExecutionStep* tryExecutionStep() { return std::get_if<ExecutionStep>(&mStep); }
    IfStep* tryIfStep() { return std::get_if<IfStep>(&mStep); }
    WhileStep* tryWhileStep() { return std::get_if<WhileStep>(&mStep); }
    GotoStep* tryGotoStep() { return std::get_if<GotoStep>(&mStep); }

    // May return nullptr.
    const ExecutionStep* tryExecutionStep() const { return std::get_if<ExecutionStep>(&mStep); }
    const IfStep* tryIfStep() const { return std::get_if<IfStep>(&mStep); }
    const WhileStep* tryWhileStep() const { return std::get_if<WhileStep>(&mStep); }
    const GotoStep* tryGotoStep() const { return std::get_if<GotoStep>(&mStep); }

    void dump() const;

   private:
    std::variant<ExecutionStep, IfStep, WhileStep, GotoStep> mStep;
};

std::ostream& operator<<(std::ostream& os, const IfStep& step);
std::ostream& operator<<(std::ostream& os, const WhileStep& step);
std::ostream& operator<<(std::ostream& os, const GotoStep& step);

// Describes the state of WhileStep.
struct WhileState {
    // A pseudo iteration number indicating the loop is not being executed.
    static constexpr uint64_t kOutsideLoop = ~uint64_t(0);
    // Whether we need to evaluate the condition or body next.
    enum Stage { EVALUATE_CONDITION, EVALUATE_BODY } stage = EVALUATE_CONDITION;
    // Current iteration number. Must be set to kOutsideLoop when exiting the
    // loop.
    uint64_t iteration = kOutsideLoop;
    // Time point when the loop started executing.
    TimePoint startTime;
};

struct ConstantCopyLocation {
    const uint8_t* buffer;
    uint32_t length;
};

struct ConstantReferenceLocation {
    const RuntimeMemory* memory;
    uint32_t offset;
    uint32_t length;
};

// A tuple of {execution_step_index, io_type, io_index} specifying an input/output role of an
// ExecutionStep.
using StepRole = std::tuple<uint32_t, IOType, uint32_t>;

// A callback function that takes the prepared_model, io_type, and io_index of a step role.
using StepRoleCallback = std::function<void(const RuntimePreparedModel*, IOType, uint32_t)>;

class ExecutionPlan {
   public:
    ExecutionPlan(const ExecutionPlan&) = delete;
    ExecutionPlan& operator=(const ExecutionPlan&) = delete;

    ExecutionPlan() {}
    ~ExecutionPlan() { delete mBody; }

    // Controller is part of the interface to a mechanism for performing a
    // main execution in N steps.
    //
    // The value of N may not be known beforehand if the model contains WHILE
    // loops. See LogicalStep.
    //
    // Usage pattern:
    // - Instantiate Controller with ExecutionPlan::makeController().
    // - Call ExecutionPlan::next() on Controller N+1 times.  The first N times,
    //   *executor is set to point to a new StepExecutor corresponding
    //   to that step.  The N+1st time, *executor is set to nullptr,
    //   signifying there are no more steps.
    // - If ExecutionPlan::next() returns anything other than ANEURALNETWORKS_NO_ERROR,
    //   a problem has occurred.
    class Controller {
        friend class ExecutionPlan;

       private:
        Controller(const Controller&) = delete;
        Controller& operator=(const Controller&) = delete;

        static const size_t kBadStepIndex = ~size_t(0);

        // A constructor for mState == COMPOUND.
        Controller(const ExecutionPlan* plan, ExecutionBuilder* executionBuilder,
                   const BurstBuilder* burstBuilder,

                   // static temporaries
                   uint32_t totalSizeOfTemporaries,
                   std::map<SourceOperandIndex, StaticTemporaryLocation>
                           sourceOperandToLocationOfTemporary,
                   std::map<SourceOperandIndex, StaticTemporaryLocation>
                           sourceOperandToLocationOfTemporary2,

                   std::map<SourceOperandIndex, uint32_t> sourceOperandToInputIndex,
                   std::map<SourceOperandIndex, uint32_t> sourceOperandToOutputIndex,
                   const std::map<SourceOperandIndex, ConstantCopyLocation>&
                           sourceOperandToConstantCopy,
                   std::map<SourceOperandIndex, ConstantReferenceLocation>
                           sourceOperandToConstantReference,
                   DynamicTemporaries dynamicTemporaries);

        // Sets the location of innerOperand to be the same as the location of outerOperand.
        void setInput(const SourceOperandIndex& outerOperand,
                      const SourceOperandIndex& innerOperand);
        void setOutput(const SourceOperandIndex& outerOperand,
                       const SourceOperandIndex& innerOperand);

        // Wait for mLastStepSyncFd to signal.
        // No-op if mLastStepSyncFd is -1 which the mLastStepSyncFd is initialized to.
        // mLastStepSyncFd will also be set to -1 when the most recently processed step
        // does not generate a sync fence.
        int waitForLastStepSyncFence() const;

        [[maybe_unused]] const ExecutionPlan* mPlan;
        ExecutionBuilder* mExecutionBuilder;
        const BurstBuilder* mBurstBuilder;
        // Map from source operand index to an offset into mTemporaries used
        // to represent that operand as an inter-partition input or output.
        //
        // The four maps
        // - mSourceOperandToLocationOfTemporary
        // - mSourceOperandToInputIndex
        // - mSourceOperandToOutputIndex
        // - mSourceOperandToConstantReference
        // are initialized from similarly named fields of ExecutionPlan::CompoundBody.
        //
        // A particular key appears in at most one map at any given time. This
        // restriction does not apply to mSourceOperandToLocationOfTemporary2.
        //
        // The maps are modified during the execution of IfStep and WhileStep.
        // See ExecutionPlan::nextCompound().
        std::map<SourceOperandIndex, StaticTemporaryLocation> mSourceOperandToLocationOfTemporary;
        // Map from source operand index to an additional offset into
        // mTemporaries used for double buffering of WHILE loop output operands.
        std::map<SourceOperandIndex, StaticTemporaryLocation> mSourceOperandToLocationOfTemporary2;
        // Map from source operand index to an input index of the main model.
        std::map<SourceOperandIndex, uint32_t> mSourceOperandToInputIndex;
        // Map from source operand index to an output index of the main model.
        std::map<SourceOperandIndex, uint32_t> mSourceOperandToOutputIndex;
        // Map from source operand index to a constant reference location.
        // Used for WHILE loop operand initializers that are constant references.
        std::map<SourceOperandIndex, ConstantReferenceLocation> mSourceOperandToConstantReference;

        // static temporaries
        std::unique_ptr<MemoryAshmem> mTemporaries;

        DynamicTemporaries mDynamicTemporaries;

        // Index of the next step to be processed by ExecutionPlan::next().
        size_t mNextStepIndex;
        // The value to reset mNextStepIndex to for partial CPU fallback.
        size_t mFallbackNextStepIndex;
        // Map from WhileStep index to the associated WhileState.
        std::unordered_map<size_t, WhileState> mWhileState;
        // The sync fence fd of the last step.
        int mLastStepSyncFd;
    };

    std::vector<SharedBurst> makeBursts() const;

    // Only legal to call when mState == COMPOUND.
    std::shared_ptr<Controller> makeController(ExecutionBuilder* executionBuilder,
                                               const BurstBuilder* burstBuilder) const;

    // Sets up a new StepExecutor and burstController (if applicable) if there
    // is a step to execute. See ExecutionPlan::Controller.
    // Handles control flow. See LogicalStep.
    // burstController is nullptr if we are not to do burst execution.
    // mainModelOutputShapes may be nullptr if the only main model outputs that are step model
    //     inputs are of fully specified shape.
    // syncFdOfLastStep is the sync fence fd generated by the most recently processed step.
    // Only legal to call when mState == COMPOUND.
    int next(std::shared_ptr<Controller> controller, std::shared_ptr<StepExecutor>* executor,
             SharedBurst* burstController, const std::vector<OutputShape>* mainModelOutputShapes,
             int syncFdOfLastStep = -1) const;

    // Create the same executor as the last one created by next().
    int fallback(std::shared_ptr<Controller> controller, std::shared_ptr<StepExecutor>* executor,
                 SharedBurst* burstController,
                 const std::vector<OutputShape>* mainModelOutputShapes) const;

    // Only legal to call when mState == SIMPLE.
    // See the constructor of StepExecutor for the semantics of "reusable".
    std::shared_ptr<StepExecutor> makeStepExecutor(bool reusable,
                                                   ExecutionBuilder* executionBuilder) const;

    ExecutionStep* createNewExecutionStep(uint32_t sourceModelIndex,
                                          const std::shared_ptr<Device> device);
    IfStep* createNewIfStep();
    WhileStep* createNewWhileStep();
    GotoStep* createNewGotoStep();

    // Only legal to call when mState == COMPOUND.
    size_t getNextStepIndex() const { return compound()->mSteps.size(); }

    void becomeSingleStep(const std::shared_ptr<Device> device, const ModelBuilder* model);

    // simulateFailureResultCode == ANEURALNETWORKS_NO_ERROR means behave normally.
    int finish(int32_t executionPreference, int32_t priority, const OptionalTimePoint& deadline,
               const std::vector<TokenValuePair>& metaData, int simulateFailureResultCode);

    void recordOutputDef(SourceOperandIndex sourceOperandIndex, uint32_t stepIndex);
    void recordTemporaryDef(SourceOperandIndex sourceOperandIndex, uint32_t stepIndex);

    void dump() const;

    void reset();

    bool isValid() const { return mState != EMPTY && mBody != nullptr && mBody->mSuccessfulFinish; }
    bool isSimple() const { return mState == SIMPLE; }
    bool isCompound() const { return mState == COMPOUND; }
    bool isSimpleCpu() const;

    void setCaching(const CacheInfo* cacheInfo, const uint8_t* token) {
        mCacheInfo = cacheInfo;
        mToken = token;
    }
    const CacheInfo* getCacheInfo() const { return mCacheInfo; }
    const uint8_t* getCacheToken() const { return mToken; }

    // The caller is responsible for making sure the index is within range.
    void forEachStepRoleOfInput(uint32_t index, const StepRoleCallback& callback) const {
        CHECK(mBody != nullptr);
        mBody->forEachStepRoleOfInput(index, callback);
    }
    void forEachStepRoleOfOutput(uint32_t index, const StepRoleCallback& callback) const {
        CHECK(mBody != nullptr);
        mBody->forEachStepRoleOfOutput(index, callback);
    }

    // "type" specifies input or output, and "index" is the main model input or output index.
    // The caller is responsible for making sure the index is within range.
    MemoryPreference getMemoryPreference(IOType type, uint32_t index) const;

    SourceModels& getSourceModels() { return mSourceModels; }
    const SourceModels& getSourceModels() const { return mSourceModels; }

    // "index" is the main model input or output index.
    // The caller is responsible for making sure the index is within range.
    SourceOperandIndex getInputSourceOperand(uint32_t index) const;
    SourceOperandIndex getOutputSourceOperand(uint32_t index) const;

    bool hasDynamicTemporaries() const;

    // These functions are solely intended for use by unit tests of
    // the partitioning algorithm.
    enum class Kind {
        ERROR,
        EMPTY,
        SIMPLE,
        COMPOUND
    };  // See operator<< defined outside this class
    Kind forTest_getKind() const;
    std::shared_ptr<const Device> forTest_simpleGetDevice() const;
    const std::vector<std::shared_ptr<LogicalStep>>& forTest_compoundGetSteps() const;
    void forTest_compoundForEachStepRoleOfSourceOperand(SourceOperandIndex index,
                                                        const StepRoleCallback& callback) const {
        compound()->forEachStepRoleOfSourceOperand(index, callback);
    }
    //     The "flat" in the name signifies that this method requires that the
    //     model not contain any control flow operations.
    std::set<uint32_t> forTest_flatGetDynamicTemporaries() const;
    const uint8_t* forTest_simpleGetCacheToken() const;
    bool forTest_hasStepModelWithNoInputsOrNoOutputs() const;

   private:
    // Becomes a new COMPOUND step if mState == EMPTY, otherwise does nothing.
    // Illegal to call for when mState == SIMPLE.
    void becomeCompoundIfEmpty();

    const Operand& getSourceOperand(const std::pair<uint32_t, uint32_t>& sourceOperandIndex) const {
        return getSourceModels()
                .getModel(sourceOperandIndex.first)
                ->getOperand(sourceOperandIndex.second);
    }

    class Buffer {
       public:
        Buffer(void* pointer, uint32_t size);
        Buffer(RunTimePoolInfo info, uint32_t offset);
        void* getPointer() const;
        uint32_t getSize() const;
        void flush() const;

       private:
        RunTimePoolInfo mInfo;
        uint32_t mOffset;
    };

    // Returns the buffer associated with a partition boundary operand.
    std::optional<Buffer> getBuffer(std::shared_ptr<Controller> controller,
                                    SourceOperandIndex operandIndex) const;
    std::optional<Buffer> getBufferFromModelArgumentInfo(
            const ModelArgumentInfo& info, const ExecutionBuilder* executionBuilder) const;
    // Reads the value of a partition boundary boolean condition operand.
    int readConditionValue(std::shared_ptr<Controller> controller, SourceOperandIndex operandIndex,
                           bool* value) const;

    // Handles control flow. See LogicalStep.
    int nextCompound(std::shared_ptr<Controller> controller,
                     std::shared_ptr<StepExecutor>* executor, SharedBurst* burstController,
                     const std::vector<OutputShape>* mainModelOutputShapes) const;
    int nextCompound(const ExecutionStep* step, std::shared_ptr<Controller> controller,
                     std::shared_ptr<StepExecutor>* executor, SharedBurst* burstController,
                     const std::vector<OutputShape>* mainModelOutputShapes) const;
    int nextCompound(const IfStep* step, std::shared_ptr<Controller> controller,
                     std::shared_ptr<StepExecutor>* executor, SharedBurst* burstController,
                     const std::vector<OutputShape>* mainModelOutputShapes) const;
    int nextCompound(const WhileStep* step, std::shared_ptr<Controller> controller,
                     std::shared_ptr<StepExecutor>* executor, SharedBurst* burstController,
                     const std::vector<OutputShape>* mainModelOutputShapes) const;
    int nextCompound(const GotoStep* step, std::shared_ptr<Controller> controller,
                     std::shared_ptr<StepExecutor>* executor, SharedBurst* burstController,
                     const std::vector<OutputShape>* mainModelOutputShapes) const;

    struct Body {
        virtual ~Body() {}
        virtual void dump() const = 0;
        virtual int finish(const SourceModels* sourceModels, int32_t executionPreference,
                           int32_t priority, const OptionalTimePoint& deadline,
                           const std::vector<TokenValuePair>& metadata,
                           int simulateFailureResultCode) = 0;
        virtual bool hasDynamicTemporaries() const = 0;
        virtual bool hasStepModelWithNoInputsOrNoOutputs() const = 0;
        virtual void forEachStepRoleOfInput(uint32_t index,
                                            const StepRoleCallback& callback) const = 0;
        virtual void forEachStepRoleOfOutput(uint32_t index,
                                             const StepRoleCallback& callback) const = 0;
        bool mSuccessfulFinish = false;
    };

    struct SimpleBody : Body {
        SimpleBody(std::shared_ptr<Device> device, const ModelBuilder* model,
                   const CacheInfo* cacheInfo, const uint8_t* token)
            : mDevice(device), mModel(model), mCacheInfo(cacheInfo), mToken(token) {}

        void dump() const override;
        int finish(const SourceModels* sourceModels, int32_t executionPreference, int32_t priority,
                   const OptionalTimePoint& deadline, const std::vector<TokenValuePair>& metadata,
                   int simulateFailureResultCode) override;
        bool hasDynamicTemporaries() const override { return false; }
        bool hasStepModelWithNoInputsOrNoOutputs() const override { return false; }
        void forEachStepRoleOfInput(uint32_t index,
                                    const StepRoleCallback& callback) const override;
        void forEachStepRoleOfOutput(uint32_t index,
                                     const StepRoleCallback& callback) const override;

        std::shared_ptr<Device> mDevice;
        const ModelBuilder* mModel;
        std::shared_ptr<RuntimePreparedModel> mPreparedModel;

        const CacheInfo* mCacheInfo;
        TokenHasher mToken;
    };

    struct CompoundBody : Body {
        CompoundBody(const ExecutionPlan* plan) : mPlan(plan) { CHECK(plan != nullptr); }

        void dump() const override;
        int finish(const SourceModels* sourceModels, int32_t executionPreference, int32_t priority,
                   const OptionalTimePoint& deadline, const std::vector<TokenValuePair>& metadata,
                   int simulateFailureResultCode) override;
        bool hasDynamicTemporaries() const override { return mHasDynamicTemporaries; }
        bool hasStepModelWithNoInputsOrNoOutputs() const override;
        void forEachStepRoleOfInput(uint32_t index,
                                    const StepRoleCallback& callback) const override;
        void forEachStepRoleOfOutput(uint32_t index,
                                     const StepRoleCallback& callback) const override;
        // Supported for any legal source operand index. For a source operand that doesn't have a
        // step role, the callback will not be invoked at all.
        void forEachStepRoleOfSourceOperand(const SourceOperandIndex& index,
                                            const StepRoleCallback& callback) const;
        // Supported for any legal source operand index.
        MemoryPreference getMemoryPreferenceOfSourceOperand(const SourceOperandIndex& index) const;

        // TODO: Some of the data is working state information that
        // shouldn't be needed after we've constructed but not
        // executed the plan.

        std::vector<std::shared_ptr<LogicalStep>> mSteps;

        // Map from source operand index to defining ExecutionStep index.
        // Used for all (and only) SUBGRAPH_OUTPUTs that are defined by
        // ExecutionSteps. Those defined by IfSteps and WhileSteps are not in
        // the map.
        std::map<SourceOperandIndex, uint32_t> mOutputToDefiningExecutionStep;

        // Map from source operand index to defining ExecutionStep index.
        // Used for all (and only) TEMPORARY_VARIABLEs that are defined by
        // ExecutionSteps. Those defined by IfSteps and WhileSteps are not in
        // the map.
        std::map<SourceOperandIndex, uint32_t> mTemporaryToDefiningExecutionStep;

        // Map from source operand index to input index of the main model.
        // This map only contains SUBGRAPH_INPUTs of the main model and is used
        // to initialize ExecutionPlan::Controller::mSourceOperandToInputIndex;
        std::map<SourceOperandIndex, uint32_t> mSourceOperandToInputIndex;

        // Map from source operand index to output index of the main model.
        // This map only contains SUBGRAPH_OUTPUTs of the main model and is used
        // to initialize ExecutionPlan::Controller::mSourceOperandToOutputIndex;
        std::map<SourceOperandIndex, uint32_t> mSourceOperandToOutputIndex;

        // Map from source operand index to location of a CONSTANT_COPY or
        // POINTER operand.
        // This map only contains constant partition boundary IF and WHILE
        // operands and is used to create a ExecutionPlan::Controller.
        std::map<SourceOperandIndex, ConstantCopyLocation> mSourceOperandToBoundaryConstantCopy;

        // Map from source operand index to location of a CONSTANT_REFERENCE
        // operand.  This map only contains constant partition boundary IF and
        // WHILE operands and is used to initialize
        // ExecutionPlan::Controller::mSourceOperandToConstantReference.
        std::map<SourceOperandIndex, ConstantReferenceLocation>
                mSourceOperandToBoundaryConstantReference;

        // Map from source operand index of a boundary operand to the step roles that its memory
        // may be used for.
        // This map only contains partition boundary operands that have ExecutionStep roles, that
        // is, SUBGRAPH_INPUTs, SUBGRAPH_OUTPUTs, and partition boundary static and dynamic
        // temporaries. If a partition boundary operand is not found in the map, then the operand
        // does not have any ExecutionStep role (this may happen with interpreted control flow).
        std::map<SourceOperandIndex, std::set<StepRole>> mSourceOperandToStepRoles;

        bool mHasDynamicTemporaries = false;

       private:
        void findTempsAsStepModelOutputs();

        void findModelOutputsThatAreDownstreamInputs();

        // Constant values that are inputs to IF and WHILE operations and lie on
        // a partition boundary ("control flow boundary constants") require
        // special treatment. We need to be able to dynamically associate those
        // values with the corresponding SUBGRAPH_INPUT operands in a referenced
        // model.
        //
        // For CONSTANT_COPY and POINTER boundary operands, we copy those to
        // temporary memory and treat them similarly to TEMPORARY_VARIABLE
        // operands in Controller.
        //
        // For CONSTANT_REFERENCE boundary operands, we keep track of them in
        // ExecutionPlan::Controller::mSourceOperandToConstantReference.
        //
        // Note that for IF inputs and input-only WHILE inputs that are boundary
        // constants, we could embed those inside the referenced model, but we
        // currently don't do so. See b/148216514.
        void findControlFlowBoundaryConstants(const SourceModels* sourceModels);

        // This method will set mSourceOperandToStepRoles.
        void findMemoryStepRoles();

        const ExecutionPlan* mPlan;
    };

    enum { EMPTY, SIMPLE, COMPOUND } mState = EMPTY;
    Body* mBody = nullptr;
    SimpleBody* simple() {
        CHECK(mState == SIMPLE);
        CHECK(mBody != nullptr);
        return static_cast<SimpleBody*>(mBody);
    }
    const SimpleBody* simple() const {
        CHECK(mState == SIMPLE);
        CHECK(mBody != nullptr);
        return static_cast<const SimpleBody*>(mBody);
    }
    CompoundBody* compound() {
        CHECK(mState == COMPOUND);
        CHECK(mBody != nullptr);
        return static_cast<CompoundBody*>(mBody);
    }
    const CompoundBody* compound() const {
        CHECK(mState == COMPOUND);
        CHECK(mBody != nullptr);
        return static_cast<const CompoundBody*>(mBody);
    }

    void forEachDynamicTemporary(const std::function<void(SourceOperandIndex, const Operand&,
                                                          uint32_t definingStepIndex)>&) const;

    // Pointers to compilation caching information in CompilationBuilder.
    const CacheInfo* mCacheInfo = nullptr;
    const uint8_t* mToken = nullptr;

    SourceModels mSourceModels;
};

inline std::ostream& operator<<(std::ostream& out, ExecutionPlan::Kind kind) {
    const int intKind = static_cast<int>(kind);
    if (kind < ExecutionPlan::Kind::ERROR || kind > ExecutionPlan::Kind::COMPOUND) {
        return out << "<UNK(" << intKind << ")>";
    }
    static const char* name[] = {"ERROR", "EMPTY", "SIMPLE", "COMPOUND"};
    return out << name[intKind];
}

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_EXECUTION_PLAN_H
