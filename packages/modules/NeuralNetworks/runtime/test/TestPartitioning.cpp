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

#include <ControlFlow.h>
#include <HalInterfaces.h>
#include <SampleDriver.h>
#include <Utils.h>
#include <ValidateHal.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <filesystem>
#include <functional>
#include <iostream>
#include <map>
#include <memory>
#include <numeric>
#include <queue>
#include <set>
#include <string>
#include <tuple>
#include <type_traits>
#include <utility>
#include <vector>

#include "CompilationBuilder.h"
#include "ExecutionPlan.h"
#include "HalUtils.h"
#include "Manager.h"
#include "ModelBuilder.h"
#include "NeuralNetworks.h"
#include "NeuralNetworksOEM.h"
#include "TestNeuralNetworksWrapper.h"
#include "TmpDirectoryUtils.h"

// Uncomment the following line to generate some debugging output that
// may be useful when analyzing failures:
//
// #define VERBOSE VERBOSE

// These tests do whitebox testing of the graph partitioning
// algorithm.  It is "whitebox" in the sense that we're not evaluating
// whether a particular partitioning is legal, or "good enough"
// according to some metric, but whether it exactly matches the
// expected behavior of the current partitioning algorithm.
//
// A key part of the current partitioning algorithm is to determine
// which device among the available devices should be the one to
// execute a particular operation from the graph.  This determination
// is made "locally" -- i.e., it does not depend on the graph
// topology, only on the properties of the operation in question.
// IDevice::getSupportedOperations() indicates which operations in a
// graph can be executed on a device, and IDevice::getCapabilities()
// indicates how "good" that device is for executing particular kinds
// of operations.  For each operation, the partitioning algorithm
// picks the "best" device that is capable of executing that
// operation; if no device can do so, then the algorithm picks the
// cpu.
//
// As part of this testing approach, we want to make it easy to
// specify which operations in a test graph can be executed on which
// devices.  We accomplish this in the following way:
// - A unary OEM operation is available.
// - There is a collection of operations (each of which has two inputs
//   and one output):
//   - Eight kinds of operations available at driver version V1_0 or
//     later.  They are represented in the graph as ADD or MUL with a
//     particular activation function -- two opcodes times four
//     activation functions means eight available operation kinds.
//     This is a low-level representation detail -- when we specify the
//     behavior of the device or build a graph, we do so in terms of
//     operation encodings 0..7.
//   - Eight kinds of operations available at driver version V1_1 or
//     later.  They are represented in the graph as DIV or SUB with
//     a particular activation function, exactly analogous to ADD
//     and MUL above.  We use operation encodings 8..15 for them.
//   - Four kinds of operations available at driver version V1_2 or
//     later.  They are represented in the graph as MAXIMUM,
//     MINIMUM, POW, or PRELU.  These operations take no activation
//     function, so we only get 4 operation kinds, for which we
//     use operation encodings 16..19.
// - There is another collection of operations (each of which has one input
//   and one output):
//   - Single operation available at driver version V1_3 or
//     later.  It is represented in the graph as HARD_SWISH.
//     These operations take no activation function, for which we
//     use operation encodings 20..20.

// When we instantiate a device for testing purposes, we specify what subset of
// those operations the device is able to execute.
//
// In order to determine whether or not a partitioning matches the
// expected partitioning, we check the number of partitions, check
// which device each partition targets, and compare each partition's
// subgraph, model inputs, model outputs, step model inputs, and
// step model outputs against what is expected.  In order to perform
// that comparison, we build a model to compare against a partition's
// step model and run a graph comparison algorithm on it.  The graph
// comparison and the inputs and outputs comparisons are syntactic
// rather than semantic comparisons -- they don't allow for
// reorderings of inputs and outputs.  Because of this, we need to
// know exactly how the partitioning algorithm orders inputs and
// outputs in order to construct the models and operand lists to
// compare against.  Here are some relevant behaviors of the
// partitioning algorithm:
//
// - It builds a subgraph by walking operations in forward topological
//   order, and adding each operation's input operands and output
//   operands in index order (input followed by output) when that
//   operation is added.  (It does not add an input that has already
//   been added.)
// - It finds model inputs, model outputs, and step model inputs in
//   the order the corresponding operands were added to the subgraph
//   (see ExecutionStep methods getModelInputs(), getModelOutputs(),
//   getTempsAsStepModelInputs(), getOutputsAsStepModelInputs()).
// - It finds temps as step model outputs in numerical order of corresponding
//   operand number in the original model (see ExecutionStep method
//   getTempsAsStepModelOutputs()).
// - When it calls identifyInputsAndOutputs() on the step model, it
//   passes inputs from getModelInputs() in order, followed by temps as
//   step model inputs from getTempsAsStepModelInputs() in order,
//   followed by outputs as step model inputs from
//   getOutputsAsStepModelInputs() in order; and it passes outputs from
//   getModelOutputs() in order followed by step model outputs from
//   getTempsAsStepModelOutputs() in order.
//
// TODO: Maybe the logic for comparing a partition to an expected
//       model should be changed to tolerate reorderings of inputs and
//       outputs, so that when we build models and lists to compare
//       against, we don't need to worry about input and output
//       orderings.  But is there a way to do this that still lets us
//       verify that we have the correct relationships between
//       an (original) model's inputs and outputs and each step model's
//       inputs and outputs, as well as the correct relationship
//       between step model inputs and outputs across partitions?

namespace {

namespace hardware = android::hardware;
namespace V1_0 = ::android::hardware::neuralnetworks::V1_0;
namespace V1_1 = ::android::hardware::neuralnetworks::V1_1;
namespace V1_2 = ::android::hardware::neuralnetworks::V1_2;
namespace V1_3 = ::android::hardware::neuralnetworks::V1_3;
using CompilationBuilder = ::android::nn::CompilationBuilder;
using Device = ::android::nn::Device;
using DeviceManager = ::android::nn::DeviceManager;
using ExecutePreference = ::android::nn::test_wrapper::ExecutePreference;
using ExecutePriority = ::android::nn::test_wrapper::ExecutePriority;
using ExecutionPlan = ::android::nn::ExecutionPlan;
using ExecutionStep = ::android::nn::ExecutionStep;
using HalCacheToken = ::android::nn::HalCacheToken;
using HalVersion = ::android::nn::HalVersion;
using HidlModel = V1_3::Model;
using IOType = ::android::nn::IOType;
using LogicalStep = ::android::nn::LogicalStep;
using ModelBuilder = ::android::nn::ModelBuilder;
using Operand = ::android::nn::Operand;
using Operation = ::android::nn::Operation;
using OptionalTimePoint = ::android::nn::OptionalTimePoint;
using Result = ::android::nn::test_wrapper::Result;
using SampleDriver = ::android::nn::sample_driver::SampleDriver;
using SharedDevice = ::android::nn::SharedDevice;
using SourceOperandIndex = ::android::nn::SourceOperandIndex;
using StepRole = ::android::nn::StepRole;
using WrapperCompilation = ::android::nn::test_wrapper::Compilation;
using WrapperExecution = ::android::nn::test_wrapper::Execution;
using WrapperModel = ::android::nn::test_wrapper::Model;
using WrapperOperandType = ::android::nn::test_wrapper::OperandType;
using WrapperSymmPerChannelQuantParams = ::android::nn::test_wrapper::SymmPerChannelQuantParams;
using WrapperType = ::android::nn::test_wrapper::Type;
using android::sp;

void update(V1_3::Capabilities* capabilities, V1_3::OperandType type, float perf) {
    V1_0::PerformanceInfo perfInfo = {.execTime = perf, .powerUsage = perf};
    ::android::nn::update(&capabilities->operandPerformance, type, perfInfo);
}

float lookupExecTime(const V1_3::Capabilities& capabilities, V1_3::OperandType type) {
    return ::android::nn::lookup(capabilities.operandPerformance, type).execTime;
}

HalVersion min(HalVersion a, HalVersion b) {
    return int32_t(a) < int32_t(b) ? a : b;
}

const uint32_t kNumFuseCodes = 4;
const uint32_t kBadOperation = ~0;

// V1_0 operations
const uint32_t kFirstEncodingADD = 0;
const uint32_t kFirstEncodingMUL = kFirstEncodingADD + kNumFuseCodes;
const uint32_t kFirstEncodingV1_0 = kFirstEncodingADD;
const uint32_t kLastEncodingV1_0 = kFirstEncodingMUL + kNumFuseCodes - 1;

// V1_1 operations
const uint32_t kFirstEncodingDIV = kLastEncodingV1_0 + 1;
const uint32_t kFirstEncodingSUB = kFirstEncodingDIV + kNumFuseCodes;
const uint32_t kFirstEncodingV1_1 = kFirstEncodingDIV;
const uint32_t kLastEncodingV1_1 = kFirstEncodingSUB + kNumFuseCodes - 1;

// V1_2 operations
const uint32_t kFirstEncodingMAXIMUM = kLastEncodingV1_1 + 1;
const uint32_t kFirstEncodingMINIMUM = kFirstEncodingMAXIMUM + 1;
const uint32_t kFirstEncodingPOW = kFirstEncodingMINIMUM + 1;
const uint32_t kFirstEncodingPRELU = kFirstEncodingPOW + 1;
const uint32_t kFirstEncodingV1_2 = kFirstEncodingMAXIMUM;
const uint32_t kLastEncodingV1_2 = kFirstEncodingPRELU;

// V1_3 operations
const uint32_t kFirstEncodingHARD_SWISH = kLastEncodingV1_2 + 1;
const uint32_t kFirstEncodingV1_3 = kFirstEncodingHARD_SWISH;
const uint32_t kLastEncodingV1_3 = kFirstEncodingHARD_SWISH;

const std::map<V1_3::OperationType, uint32_t> operationToFirstEncoding = {
        {V1_3::OperationType::ADD, kFirstEncodingADD},
        {V1_3::OperationType::MUL, kFirstEncodingMUL},
        {V1_3::OperationType::DIV, kFirstEncodingDIV},
        {V1_3::OperationType::SUB, kFirstEncodingSUB},
        {V1_3::OperationType::MAXIMUM, kFirstEncodingMAXIMUM},
        {V1_3::OperationType::MINIMUM, kFirstEncodingMINIMUM},
        {V1_3::OperationType::POW, kFirstEncodingPOW},
        {V1_3::OperationType::PRELU, kFirstEncodingPRELU},
        {V1_3::OperationType::HARD_SWISH, kFirstEncodingHARD_SWISH},
};

// Sorted in reverse order (std::greater) so that we can use map::lower_bound to
// find an entry whose key is numerically less than or equal to a search value.
// mapped_type is (OperandCode, hasFuseCode).
const std::map<uint32_t, std::pair<uint32_t, bool>, std::greater<>> firstEncodingToOperation = {
        {kFirstEncodingADD, {ANEURALNETWORKS_ADD, true}},
        {kFirstEncodingMUL, {ANEURALNETWORKS_MUL, true}},
        {kFirstEncodingDIV, {ANEURALNETWORKS_DIV, true}},
        {kFirstEncodingSUB, {ANEURALNETWORKS_SUB, true}},
        {kFirstEncodingMAXIMUM, {ANEURALNETWORKS_MAXIMUM, false}},
        {kFirstEncodingMINIMUM, {ANEURALNETWORKS_MINIMUM, false}},
        {kFirstEncodingPOW, {ANEURALNETWORKS_POW, false}},
        {kFirstEncodingPRELU, {ANEURALNETWORKS_PRELU, false}},
        {kFirstEncodingHARD_SWISH, {ANEURALNETWORKS_HARD_SWISH, false}},
};

// Look up the operation with the specified index in a graph, and return the
// operation encoding; or, if for some reason this is not one of the encoded
// operations, then return kBadOperation.
uint32_t lookupOperation(std::function<const V1_3::Operation&(uint32_t)> getOperation,
                         std::function<const V1_3::Operand&(uint32_t)> getOperand,
                         std::function<const uint8_t*(uint32_t)> getValue,
                         uint32_t operationIndex) {
    const V1_3::Operation& operation = getOperation(operationIndex);
    switch (operation.type) {
        case V1_3::OperationType::ADD:
        case V1_3::OperationType::MUL:
        case V1_3::OperationType::DIV:
        case V1_3::OperationType::SUB: {
            // input2 is the fused activation function
            const V1_3::Operand& input2 = getOperand(operation.inputs[2]);
            if ((input2.type == V1_3::OperandType::INT32) &&
                (input2.lifetime == V1_3::OperandLifeTime::CONSTANT_COPY)) {
                int32_t value;
                CHECK_EQ(sizeof(value), input2.location.length);
                memcpy(&value, getValue(input2.location.offset), input2.location.length);
                return value + operationToFirstEncoding.at(operation.type);
            }
            break;
        }
        default: {
            auto it = operationToFirstEncoding.find(operation.type);
            if (it != operationToFirstEncoding.end()) {
                return it->second;
            }
            break;
        }
    }
    return kBadOperation;
}

uint32_t lookupOperation(const HidlModel& model, const V1_3::Subgraph& subgraph,
                         uint32_t operationIndex) {
    return lookupOperation(
            [&subgraph](uint32_t index) -> const V1_3::Operation& {
                return subgraph.operations[index];
            },
            [&subgraph](uint32_t index) -> const V1_3::Operand& {
                return subgraph.operands[index];
            },
            [&model](uint32_t offset) { return &model.operandValues[offset]; }, operationIndex);
}

#ifdef VERBOSE
// This is a debugging utility function
void dump(const char* name, const ModelBuilder* model) {
    const HidlModel hidlModel = model->makeHidlModel();
    std::cout << name << ": " << hidlModel << std::endl;
    std::cout << "inputs: " << hidlModel.main.inputIndexes << std::endl;
    std::cout << "outputs: " << hidlModel.main.outputIndexes << std::endl;
    for (size_t i = 0, e = hidlModel.main.operations.size(); i < e; i++) {
        std::cout << "operation[" << i << "]: " << hidlModel.main.operations[i] << std::endl;
    }
}
#endif

// This is an IDevice for testing purposes.  It only has a few interesting
// properties, all of which are specified as constructor arguments: device
// capabilities; which subset of operation kinds (0..19) does the device
// support; does the device support the OEM operation; does the device support
// other operations.  The subset is represented with a bitmask, in which
// operation kind K corresponds to the bit (1 << K).  The other operations are
// represented by a set of OperationType.
class PartitioningDriver : public SampleDriver {
   public:
    enum OEM {
        OEMNo,          // rejected by getSupportedOperations and prepareModel
        OEMIndecisive,  // accepted by getSupportedOperations but not prepareModel
        OEMYes,         // accepted by getSupportedOperations and prepareModel
    };

    PartitioningDriver(const char* name, const char* version, V1_3::Capabilities capabilities,
                       uint32_t operationMask, OEM oem = OEMNo,
                       std::set<V1_3::OperationType> operationTypes = {})
        : SampleDriver(name),
          mVersionString(version),
          mCapabilities(capabilities),
          mOperationMask(operationMask),
          mOEM(oem),
          mOperationTypes(std::move(operationTypes)) {
        CHECK_EQ(mOperationTypes.count(V1_3::OperationType::OEM_OPERATION), size_t(0));
        if (operationMask) {
            std::for_each(mOperationTypes.begin(), mOperationTypes.end(),
                          [](V1_3::OperationType type) {
                              CHECK_EQ(operationToFirstEncoding.count(type), size_t(0));
                          });
        }
    }
    ~PartitioningDriver() override {}

    hardware::Return<void> getVersionString(getVersionString_cb cb) override {
        cb(V1_0::ErrorStatus::NONE, mVersionString);
        return hardware::Void();
    }

    hardware::Return<V1_3::ErrorStatus> prepareModel_1_3(
            const V1_3::Model& model, V1_1::ExecutionPreference preference, V1_3::Priority priority,
            const V1_3::OptionalTimePoint& deadline,
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_3::IPreparedModelCallback>& callback) override {
        if (mOEM == OEMIndecisive) {
            for (const auto& operation : model.main.operations) {
                if (operation.type == V1_3::OperationType::OEM_OPERATION) {
                    callback->notify_1_3(V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr);
                    return V1_3::ErrorStatus::INVALID_ARGUMENT;
                }
            }
        }

        // NOTE: We verify that all operations in the model are supported.
        V1_3::ErrorStatus outStatus = V1_3::ErrorStatus::INVALID_ARGUMENT;
        auto ret = getSupportedOperations_1_3(
                model, [&outStatus](V1_3::ErrorStatus inStatus,
                                    const hardware::hidl_vec<bool>& supportedOperations) {
                    if (inStatus == V1_3::ErrorStatus::NONE) {
                        if (std::all_of(supportedOperations.begin(), supportedOperations.end(),
                                        [](bool v) { return v; })) {
                            outStatus = V1_3::ErrorStatus::NONE;
                        }
                    }
                });
        if (ret.isOk() && (outStatus == V1_3::ErrorStatus::NONE)) {
            return SampleDriver::prepareModel_1_3(model, preference, priority, deadline, modelCache,
                                                  dataCache, token, callback);
        } else {
            callback->notify_1_3(V1_3::ErrorStatus::INVALID_ARGUMENT, nullptr);
            return V1_3::ErrorStatus::INVALID_ARGUMENT;
        }
    }

    hardware::Return<V1_0::DeviceStatus> getStatus() override {
        return V1_0::DeviceStatus::AVAILABLE;
    }

    hardware::Return<void> getCapabilities_1_3(getCapabilities_1_3_cb cb) override {
        cb(V1_3::ErrorStatus::NONE, mCapabilities);
        return hardware::Void();
    }

    hardware::Return<void> getSupportedOperations_1_3(const V1_3::Model& model,
                                                      getSupportedOperations_1_3_cb cb) override {
        if (!android::nn::validateModel(model)) {
            cb(V1_3::ErrorStatus::INVALID_ARGUMENT, std::vector<bool>());
            return hardware::Void();
        }
        cb(V1_3::ErrorStatus::NONE, getSupportedOperationsForSubgraph(model, model.main));
        return hardware::Void();
    }

    hardware::Return<void> getNumberOfCacheFilesNeeded(getNumberOfCacheFilesNeeded_cb cb) override {
        cb(V1_0::ErrorStatus::NONE, /*numModelCache=*/1, /*numDataCache=*/1);
        return hardware::Void();
    }

   private:
    std::vector<bool> getSupportedOperationsForSubgraph(const V1_3::Model& model,
                                                        const V1_3::Subgraph& subgraph) {
        CHECK(&subgraph == &model.main ||
              std::find_if(model.referenced.begin(), model.referenced.end(),
                           [&subgraph](const V1_3::Subgraph& refSubgraph) {
                               return &subgraph == &refSubgraph;
                           }) != model.referenced.end());
        auto supportsEntireSubgraph = [this, &model, &subgraph](uint32_t refSubgraphOperandIndex) {
            CHECK_LT(refSubgraphOperandIndex, subgraph.operands.size());
            const V1_3::Operand& refSubgraphOperand = subgraph.operands[refSubgraphOperandIndex];
            CHECK(refSubgraphOperand.lifetime == V1_3::OperandLifeTime::SUBGRAPH);
            CHECK_LT(refSubgraphOperand.location.offset, model.referenced.size());
            const V1_3::Subgraph& refSubgraph =
                    model.referenced[refSubgraphOperand.location.offset];
            std::vector<bool> supported = getSupportedOperationsForSubgraph(model, refSubgraph);
            return std::all_of(supported.begin(), supported.end(), [](bool x) { return x; });
        };
        const size_t count = subgraph.operations.size();
        std::vector<bool> supported(count);
        for (size_t i = 0; i < count; i++) {
            const V1_3::Operation& operation = subgraph.operations[i];
            if (mOperationTypes.count(operation.type)) {
                if (operation.type == V1_3::OperationType::IF) {
                    namespace op = android::nn::operation_if;
                    CHECK_GE(operation.inputs.size(), op::kFirstInput);
                    supported[i] =
                            supportsEntireSubgraph(operation.inputs[op::kThenModelOperand]) &&
                            supportsEntireSubgraph(operation.inputs[op::kElseModelOperand]);
                } else if (operation.type == V1_3::OperationType::WHILE) {
                    namespace op = android::nn::operation_while;
                    CHECK_GE(operation.inputs.size(), op::kFirstInput);
                    supported[i] =
                            supportsEntireSubgraph(operation.inputs[op::kCondModelOperand]) &&
                            supportsEntireSubgraph(operation.inputs[op::kBodyModelOperand]);
                } else {
                    supported[i] = true;
                }
                continue;
            }
            if (operation.type == V1_3::OperationType::OEM_OPERATION) {
                supported[i] = (mOEM != OEMNo);
                continue;
            }
            supported[i] = false;
            uint32_t operationEncoding = lookupOperation(model, subgraph, i);
            if ((operationEncoding != kBadOperation) &&
                (mOperationMask & (1 << operationEncoding))) {
                supported[i] = true;
            }
        }
        return supported;
    }

    std::string mVersionString;
    V1_3::Capabilities mCapabilities;
    uint32_t mOperationMask;
    OEM mOEM;
    std::set<V1_3::OperationType> mOperationTypes;
};

// Like PartitioningDriver, but implementing 1.2
class PartitioningDriverV1_2 : public V1_2::IDevice {
   public:
    PartitioningDriverV1_2(const char* name, const char* version, V1_3::Capabilities capabilities,
                           uint32_t operationMask,
                           PartitioningDriver::OEM oem = PartitioningDriver::OEMNo,
                           std::set<V1_3::OperationType> operationTypes = {})
        : mLatestDriver(new PartitioningDriver(name, version, capabilities, operationMask, oem,
                                               operationTypes)) {}
    hardware::Return<void> getCapabilities_1_2(getCapabilities_1_2_cb _hidl_cb) override {
        return mLatestDriver->getCapabilities_1_2(_hidl_cb);
    }
    hardware::Return<void> getSupportedOperations_1_2(
            const V1_2::Model& model, getSupportedOperations_1_2_cb _hidl_cb) override {
        return mLatestDriver->getSupportedOperations_1_2(model, _hidl_cb);
    }
    hardware::Return<V1_0::ErrorStatus> prepareModel_1_2(
            const V1_2::Model& model, V1_1::ExecutionPreference preference,
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_2::IPreparedModelCallback>& actualCallback) override {
        return mLatestDriver->prepareModel_1_2(model, preference, modelCache, dataCache, token,
                                               actualCallback);
    }
    hardware::Return<void> getVersionString(getVersionString_cb _hidl_cb) override {
        return mLatestDriver->getVersionString(_hidl_cb);
    }
    hardware::Return<void> getType(getType_cb _hidl_cb) override {
        return mLatestDriver->getType(_hidl_cb);
    }
    hardware::Return<void> getSupportedExtensions(getSupportedExtensions_cb _hidl_cb) {
        return mLatestDriver->getSupportedExtensions(_hidl_cb);
    }
    hardware::Return<void> getNumberOfCacheFilesNeeded(getNumberOfCacheFilesNeeded_cb _hidl_cb) {
        return mLatestDriver->getNumberOfCacheFilesNeeded(_hidl_cb);
    }
    hardware::Return<V1_0::ErrorStatus> prepareModelFromCache(
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_2::IPreparedModelCallback>& callback) {
        return mLatestDriver->prepareModelFromCache(modelCache, dataCache, token, callback);
    }
    hardware::Return<void> getCapabilities_1_1(getCapabilities_1_1_cb _hidl_cb) override {
        return mLatestDriver->getCapabilities_1_1(_hidl_cb);
    }
    hardware::Return<void> getSupportedOperations_1_1(
            const V1_1::Model& model, getSupportedOperations_1_1_cb _hidl_cb) override {
        return mLatestDriver->getSupportedOperations_1_1(model, _hidl_cb);
    }
    hardware::Return<V1_0::ErrorStatus> prepareModel_1_1(
            const V1_1::Model& model, V1_1::ExecutionPreference preference,
            const sp<V1_0::IPreparedModelCallback>& actualCallback) override {
        return mLatestDriver->prepareModel_1_1(model, preference, actualCallback);
    }
    hardware::Return<V1_0::DeviceStatus> getStatus() override { return mLatestDriver->getStatus(); }
    hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override {
        return mLatestDriver->getCapabilities(_hidl_cb);
    }
    hardware::Return<void> getSupportedOperations(const V1_0::Model& model,
                                                  getSupportedOperations_cb _hidl_cb) override {
        return mLatestDriver->getSupportedOperations(model, _hidl_cb);
    }
    hardware::Return<V1_0::ErrorStatus> prepareModel(
            const V1_0::Model& model,
            const sp<V1_0::IPreparedModelCallback>& actualCallback) override {
        return mLatestDriver->prepareModel(model, actualCallback);
    }

   private:
    const sp<V1_3::IDevice> mLatestDriver;
};

// Like PartitioningDriver, but implementing 1.1
class PartitioningDriverV1_1 : public V1_1::IDevice {
   public:
    PartitioningDriverV1_1(const char* name, const char* version, V1_3::Capabilities capabilities,
                           uint32_t operationMask,
                           PartitioningDriver::OEM oem = PartitioningDriver::OEMNo,
                           std::set<V1_3::OperationType> operationTypes = {})
        : mLatestDriver(new PartitioningDriver(name, version, capabilities, operationMask, oem,
                                               operationTypes)) {}
    hardware::Return<void> getCapabilities_1_1(getCapabilities_1_1_cb _hidl_cb) override {
        return mLatestDriver->getCapabilities_1_1(_hidl_cb);
    }
    hardware::Return<void> getSupportedOperations_1_1(
            const V1_1::Model& model, getSupportedOperations_1_1_cb _hidl_cb) override {
        return mLatestDriver->getSupportedOperations_1_1(model, _hidl_cb);
    }
    hardware::Return<V1_0::ErrorStatus> prepareModel_1_1(
            const V1_1::Model& model, V1_1::ExecutionPreference preference,
            const sp<V1_0::IPreparedModelCallback>& actualCallback) override {
        return mLatestDriver->prepareModel_1_1(model, preference, actualCallback);
    }
    hardware::Return<V1_0::DeviceStatus> getStatus() override { return mLatestDriver->getStatus(); }
    hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override {
        return mLatestDriver->getCapabilities(_hidl_cb);
    }
    hardware::Return<void> getSupportedOperations(const V1_0::Model& model,
                                                  getSupportedOperations_cb _hidl_cb) override {
        return mLatestDriver->getSupportedOperations(model, _hidl_cb);
    }
    hardware::Return<V1_0::ErrorStatus> prepareModel(
            const V1_0::Model& model,
            const sp<V1_0::IPreparedModelCallback>& actualCallback) override {
        return mLatestDriver->prepareModel(model, actualCallback);
    }

   private:
    const sp<V1_3::IDevice> mLatestDriver;
};

// Like PartitioningDriver, but implementing 1.0
class PartitioningDriverV1_0 : public V1_0::IDevice {
   public:
    PartitioningDriverV1_0(const char* name, const char* version, V1_3::Capabilities capabilities,
                           uint32_t operationMask,
                           PartitioningDriver::OEM oem = PartitioningDriver::OEMNo,
                           std::set<V1_3::OperationType> operationTypes = {})
        : mLatestDriver(new PartitioningDriver(name, version, capabilities, operationMask, oem,
                                               operationTypes)) {}
    hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override {
        return mLatestDriver->getCapabilities(_hidl_cb);
    }
    hardware::Return<void> getSupportedOperations(const V1_0::Model& model,
                                                  getSupportedOperations_cb _hidl_cb) override {
        return mLatestDriver->getSupportedOperations(model, _hidl_cb);
    }
    hardware::Return<V1_0::ErrorStatus> prepareModel(
            const V1_0::Model& model,
            const sp<V1_0::IPreparedModelCallback>& actualCallback) override {
        return mLatestDriver->prepareModel(model, actualCallback);
    }
    hardware::Return<V1_0::DeviceStatus> getStatus() override { return mLatestDriver->getStatus(); }

   private:
    const sp<V1_3::IDevice> mLatestDriver;
};

enum class Dimensioned {
    NO,      // either a scalar, or a tensor of either unspecified rank (usually)
             // or specified rank but with no specified dimensions (where
             // specifically stated)
    RANK_1,  // tensor of shape { 0 }    -- i.e., rank 1, unspecified dimensions
    RANK_2,  // tensor of shape { 0, 0 } -- i.e., rank 2, unspecified dimensions
    YES_1,   // tensor of shape { 1 }
    YES_2,   // tensor of shape { 2 }
    YES_4,   // tensor of shape { 4 }
    YES = YES_1
};

std::vector<uint32_t> dimensions(Dimensioned dimensioned) {
    switch (dimensioned) {
        default:
            EXPECT_TRUE(false) << "Unknown value";
            FALLTHROUGH_INTENDED;
        case Dimensioned::NO:
            return {};
        case Dimensioned::RANK_1:
            return {0};
        case Dimensioned::RANK_2:
            return {0, 0};
        case Dimensioned::YES_1:
            return {1};
        case Dimensioned::YES_2:
            return {2};
        case Dimensioned::YES_4:
            return {4};
    }
}

// "dimensioned" must be a fully specified kind
uint32_t numberOfElements(Dimensioned dimensioned) {
    auto dims = dimensions(dimensioned);
    uint32_t result = std::reduce(dims.begin(), dims.end(), 1u, std::multiplies<>());
    CHECK_GT(result, 0u);
    return result;
}

std::string toString(Dimensioned dimensioned) {
    switch (dimensioned) {
        default:
            return "<Unknown value>";
        case Dimensioned::NO:
            return "NO";
        case Dimensioned::RANK_1:
            return "RANK_1";
        case Dimensioned::RANK_2:
            return "RANK_2";
        case Dimensioned::YES_1:
            return "YES_1";
        case Dimensioned::YES_2:
            return "YES_2";
        case Dimensioned::YES_4:
            return "YES_4";
    }
}

// This class adds some simple abstractions and utilities on top of
// WrapperModel.  For example, it provides methods that work in terms of
// operation kind (0..7); and because we care about graph topology rather than
// details of operand types and values, it greatly simplifies the process of
// creating operands.
class PartitioningModel : private WrapperModel {
   public:
    using WrapperModel::finish;
    using WrapperModel::getHandle;
    using WrapperModel::identifyInputsAndOutputs;
    using WrapperModel::isValid;
    using WrapperModel::relaxComputationFloat32toFloat16;
    using WrapperModel::setOperandValue;

    // Create a tensor operand of the specified type, and return the
    // corresponding operand index.
    uint32_t addIntOperand(Dimensioned dimensioned = Dimensioned::YES) {
        return addOperand(WrapperType::TENSOR_INT32, dimensioned);
    }
    uint32_t addIntScalarOperand(std::optional<int> v = std::nullopt) {
        uint32_t opnd = addOperand(WrapperType::INT32);
        if (v.has_value()) {
            setOperandValue(opnd, &v.value());
        }
        return opnd;
    }
    uint32_t addFloatOperand(Dimensioned dimensioned = Dimensioned::YES) {
        return addOperand(WrapperType::TENSOR_FLOAT32, dimensioned);
    }
    uint32_t addQuantOperand(Dimensioned dimensioned = Dimensioned::YES) {
        return addOperand(WrapperType::TENSOR_QUANT8_ASYMM, dimensioned);
    }
    uint32_t addBooleanOperand(Dimensioned dimensioned = Dimensioned::YES) {
        return addOperand(WrapperType::TENSOR_BOOL8, dimensioned);
    }
    uint32_t addFloatZeroOperand(Dimensioned dimensioned = Dimensioned::YES) {
        uint32_t opnd = addFloatOperand(dimensioned);
        std::vector<float> values(numberOfElements(dimensioned), 0.0f);
        uint32_t size = values.size() * sizeof(float);
        // Make sure the values are immediately copied so that it is safe to free the buffer after
        // the setOperandValue call
        CHECK_LE(size, ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES);
        setOperandValue(opnd, values.data(), size);
        return opnd;
    }

    // Create an operand of the specified type, and return the corresponding
    // operand index.
    uint32_t addOperand(WrapperType wrapperType, Dimensioned dimensioned = Dimensioned::YES) {
        switch (static_cast<int>(wrapperType)) {
            case ANEURALNETWORKS_BOOL:
            case ANEURALNETWORKS_FLOAT16:
            case ANEURALNETWORKS_FLOAT32:
            case ANEURALNETWORKS_INT32:
            case ANEURALNETWORKS_UINT32:
            case ANEURALNETWORKS_MODEL:
            case ANEURALNETWORKS_OEM_SCALAR:
                return addOperand(WrapperOperandType{wrapperType, {}});

            case ANEURALNETWORKS_TENSOR_BOOL8:
            case ANEURALNETWORKS_TENSOR_FLOAT16:
            case ANEURALNETWORKS_TENSOR_FLOAT32:
            case ANEURALNETWORKS_TENSOR_OEM_BYTE:
                return addOperand(WrapperOperandType{wrapperType, dimensions(dimensioned)});

            case ANEURALNETWORKS_TENSOR_INT32:
            case ANEURALNETWORKS_TENSOR_QUANT8_ASYMM:
            case ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED:
            case ANEURALNETWORKS_TENSOR_QUANT8_SYMM:
            case ANEURALNETWORKS_TENSOR_QUANT16_ASYMM:
            case ANEURALNETWORKS_TENSOR_QUANT16_SYMM:
                return addOperand(WrapperOperandType{wrapperType, dimensions(dimensioned), 1.0f});

            case ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL:
                return addOperand(WrapperOperandType{wrapperType, dimensions(dimensioned),
                                                     WrapperSymmPerChannelQuantParams({1.0f}, 0)});

            default:
                ADD_FAILURE() << "Unexpected type " << static_cast<uint32_t>(wrapperType);
                return ~uint32_t(0);
        }
    }

    // Create an operand of the specified operand type, and return the
    // corresponding operand index.
    uint32_t addOperand(const WrapperOperandType& wrapperOperandType) {
        mWrapperOperandType.push_back(wrapperOperandType);
        return WrapperModel::addOperand(&wrapperOperandType);
    }

    // Create an operation with any number of inputs and one output, specifying
    // the operation type (e.g., ANEURALNETWORKS_ADD), the input operand
    // indexes, and the output type (e.g., WrapperType::TENSOR_FLOAT32).
    // Returns the output operand index.
    uint32_t addExplicitOperationXTo1(ANeuralNetworksOperationType operationType,
                                      const std::vector<uint32_t>& inputs, WrapperType outputType,
                                      Dimensioned dimensionedOutput = Dimensioned::YES) {
        uint32_t output = addOperand(outputType, dimensionedOutput);
        addOperation(operationType, inputs, {output});
        return output;
    }

    // Create a V1_0 operation with two inputs and one output, specifying the
    // operation kind (where 0 is the first V1_0 operation) and the input
    // operand indexes.
    // Returns the output operand index.
    uint32_t addOperation2To1V1_0(uint32_t operation, const uint32_t input0, const uint32_t input1,
                                  Dimensioned dimensionedOutput = Dimensioned::YES) {
        CHECK_LE(operation, kLastEncodingV1_0 - kFirstEncodingV1_0);
        return addOperation2To1(operation + kFirstEncodingV1_0, input0, input1, dimensionedOutput);
    }

    // Create a V1_1 operation with two inputs and one output, specifying the
    // operation kind (where 0 is the first V1_1 operation) and the input
    // operand indexes.
    // Returns the output operand index.
    uint32_t addOperation2To1V1_1(uint32_t operation, const uint32_t input0, const uint32_t input1,
                                  Dimensioned dimensionedOutput = Dimensioned::YES) {
        CHECK_LE(operation, kLastEncodingV1_1 - kFirstEncodingV1_1);
        return addOperation2To1(operation + kFirstEncodingV1_1, input0, input1, dimensionedOutput);
    }

    // Create a V1_2 operation with two inputs and one output, specifying the
    // operation kind (where 0 is the first V1_2 operation) and the input
    // operand indexes.
    // Returns the output operand index.
    uint32_t addOperation2To1V1_2(uint32_t operation, const uint32_t input0, const uint32_t input1,
                                  Dimensioned dimensionedOutput = Dimensioned::YES) {
        CHECK_LE(operation, kLastEncodingV1_2 - kFirstEncodingV1_2);
        return addOperation2To1(operation + kFirstEncodingV1_2, input0, input1, dimensionedOutput);
    }

    // Create a V1_3 operation with two inputs and one output, specifying the
    // operation kind (where 0 is the first V1_3 operation) and the input
    // operand indexes.
    // Returns the output operand index.
    uint32_t addOperation1To1V1_3(uint32_t operation, const uint32_t input0,
                                  Dimensioned dimensionedOutput = Dimensioned::YES) {
        CHECK_LE(operation, kLastEncodingV1_3 - kFirstEncodingV1_3);
        return addOperation1To1(operation + kFirstEncodingV1_3, input0, dimensionedOutput);
    }

    // Create an OEM operation with one input and one output,
    // specifying the input operand index.  Returns the output operand
    // index.
    uint32_t addOperationOEM1To1(const uint32_t input,
                                 Dimensioned dimensionedOutput = Dimensioned::YES) {
        uint32_t output = addOperandOfSameType(input, dimensionedOutput);
        addOperation(ANEURALNETWORKS_OEM_OPERATION, {input}, {output});
        return output;
    }

    // Create an IF operation with the given condition operand and two
    // referenced models for the true and false cases.
    void addIfOperation(const uint32_t cond, const PartitioningModel& trueModel,
                        const PartitioningModel& falseModel, const std::vector<uint32_t>& inputs,
                        const std::vector<uint32_t>& outputs) {
        const uint32_t opndTrue = addRefModelOperand(trueModel);
        const uint32_t opndFalse = addRefModelOperand(falseModel);
        std::vector<uint32_t> ifInputs = {cond, opndTrue, opndFalse};
        ifInputs.insert(ifInputs.end(), inputs.begin(), inputs.end());
        addOperation(ANEURALNETWORKS_IF, ifInputs, outputs);
    }

    // Create a WHILE operation with the given condition and body referenced models.
    void addWhileOperation(const PartitioningModel& condModel, const PartitioningModel& bodyModel,
                           const std::vector<uint32_t>& inputs,
                           const std::vector<uint32_t>& outputs) {
        const uint32_t condOperand = addRefModelOperand(condModel);
        const uint32_t bodyOperand = addRefModelOperand(bodyModel);
        std::vector<uint32_t> whileInputs = {condOperand, bodyOperand};
        whileInputs.insert(whileInputs.end(), inputs.begin(), inputs.end());
        addOperation(ANEURALNETWORKS_WHILE, whileInputs, outputs);
    }

    // Run the partitioning algorithm to create an ExecutionPlan.
    int partitionTheWork(const std::vector<std::shared_ptr<Device>>& devices,
                         ExecutePreference preference, ExecutePriority priority,
                         const OptionalTimePoint& deadline, ExecutionPlan* plan) {
        return reinterpret_cast<ModelBuilder*>(getHandle())
                ->partitionTheWork(devices, static_cast<uint32_t>(preference),
                                   static_cast<int32_t>(priority), deadline, plan, {});
    }

#ifdef VERBOSE
    // This is a debugging utility function.
    void dump(const char* name) const {
        const ModelBuilder* mb = reinterpret_cast<const ModelBuilder*>(getHandle());
        ::dump(name, mb);
    }
#endif

   private:
    // Create an operation with two inputs and one output, specifying
    // the operation kind and the input operand indexes.
    // Returns the output operand index.
    uint32_t addOperation2To1(uint32_t operation, const uint32_t input0, const uint32_t input1,
                              Dimensioned dimensionedOutput = Dimensioned::YES) {
        auto it = firstEncodingToOperation.lower_bound(operation);
        CHECK(it != firstEncodingToOperation.end());
        ANeuralNetworksOperationType type = it->second.first;
        if (it->second.second) {
            int32_t fuseCode = operation - it->first;
            uint32_t input2 = addIntOperand(fuseCode);
            uint32_t output = addOperandOfSameType(input0, dimensionedOutput);
            addOperation(type, {input0, input1, input2}, {output});
            return output;
        } else {
            uint32_t output = addOperandOfSameType(input0, dimensionedOutput);
            addOperation(type, {input0, input1}, {output});
            return output;
        }
    }

    // Create an operation with one inputs and one output, specifying
    // the operation kind and the input operand indexes.
    // Returns the output operand index.
    uint32_t addOperation1To1(uint32_t operation, const uint32_t input0,
                              Dimensioned dimensionedOutput = Dimensioned::YES) {
        auto it = firstEncodingToOperation.lower_bound(operation);
        CHECK(it != firstEncodingToOperation.end());
        ANeuralNetworksOperationType type = it->second.first;

        uint32_t output = addOperandOfSameType(input0, dimensionedOutput);
        addOperation(type, {input0}, {output});
        return output;
    }

    // Create a scalar integer operand of the specified value, and
    // return the corresponding operand index.
    uint32_t addIntOperand(int32_t value) {
        uint32_t operand = addOperand(WrapperType::INT32);
        setOperandValue(operand, &value, sizeof(value));
        return operand;
    }

    // Create an operand from a model for control flow graphs.
    uint32_t addRefModelOperand(const PartitioningModel& model) {
        const uint32_t index = addOperand(WrapperType::MODEL);
        WrapperModel::setOperandValueFromModel(index, &model);
        return index;
    }

    // Create an operand of the same type as the specified operand,
    // and return the operand index of the new operand.
    //
    // If a tensor, the new operand will have the same rank as the specified
    // operand.  If dimensioned == Dimensioned::NO, then all dimensions of a new
    // tensor operand will be unspecified.  If dimensioned != Dimensioned::NO,
    // then all dimensions of a new tensor operand will have the implied value
    // (e.g., YES_1 means each dimension will have the value "1").
    uint32_t addOperandOfSameType(uint32_t operand, Dimensioned dimensioned = Dimensioned::YES) {
        WrapperOperandType type = mWrapperOperandType.at(operand);

        const auto d = dimensions(dimensioned);
        EXPECT_TRUE(d.size() <= 1);
        for (auto& dimension : type.dimensions) {
            dimension = (dimensioned == Dimensioned::NO ? 0 : d[0]);
        }

        mWrapperOperandType.push_back(type);
        return WrapperModel::addOperand(&type);
    }

    // operand index to operand type
    std::vector<WrapperOperandType> mWrapperOperandType;
};

// This class adds some utilities on top of WrapperCompilation.
class PartitioningCompilation : public WrapperCompilation {
   public:
    PartitioningCompilation(const PartitioningModel* model,
                            const std::vector<std::shared_ptr<Device>>& devices) {
        ModelBuilder* m = reinterpret_cast<ModelBuilder*>(model->getHandle());
        CompilationBuilder* c = nullptr;
        int result = m->createCompilation(&c, devices);
        EXPECT_EQ(result, 0);
        mCompilation = reinterpret_cast<ANeuralNetworksCompilation*>(c);
    }

    Result setPartitioning(uint32_t partitioning) {
        return static_cast<Result>(builder()->forTest_setPartitioning(partitioning));
    }

    // Simulate recoverable partitioning failure.
    Result failPartitioning() {
        return static_cast<Result>(
                builder()->forTest_failPartitioning(static_cast<int>(Result::OP_FAILED)));
    }

    using WrapperCompilation::finish;

    const ExecutionPlan& getExecutionPlan() const { return builder()->forTest_getExecutionPlan(); }

   private:
    CompilationBuilder* builder() { return reinterpret_cast<CompilationBuilder*>(getHandle()); }

    const CompilationBuilder* builder() const {
        return reinterpret_cast<const CompilationBuilder*>(getHandle());
    }
};

#ifdef VERBOSE
#define RETURN_TRUE()                                                 \
    {                                                                 \
        std::cerr << "returning true from " << __LINE__ << std::endl; \
        return true;                                                  \
    }
#else
#define RETURN_TRUE() \
    { return true; }
#endif
#ifdef VERBOSE
#define RETURN_FALSE(MESSAGE)                                                  \
    {                                                                          \
        std::cerr << "returning false from " << __LINE__ MESSAGE << std::endl; \
        return false;                                                          \
    }
#else
#define RETURN_FALSE(MESSAGE) \
    { return false; }
#endif

class PartitioningTest : public ::testing::Test {
   protected:
    using DynamicTemporariesType = decltype(ExecutionPlan().forTest_flatGetDynamicTemporaries());
    using RemapVectorType = ExecutionStep::RemapVectorType;
    using StepModelOutputSetType = ExecutionStep::StepModelOutputSetType;

    // Used for PartitioningTest::checkExecutionPlanSteps.
    static constexpr const char* kIfStep = "IF";
    static constexpr const char* kWhileStep = "WHILE";
    static constexpr const char* kGotoStep = "GOTO";

    virtual void SetUp() {}

    // From a vector of DeviceSpecification, create a vector of
    // Devices.
    struct DeviceSpecification {
        DeviceSpecification(const std::string& name, const V1_3::Capabilities& capabilities,
                            uint32_t operationMask,
                            PartitioningDriver::OEM oem = PartitioningDriver::OEMNo)
            : mName(name),
              mVersionString(kVersionString),
              mCapabilities(capabilities),
              mOperationMask(operationMask),
              mOEM(oem) {}
        DeviceSpecification(const std::string& name, float perf, uint32_t operationMask,
                            PartitioningDriver::OEM oem = PartitioningDriver::OEMNo,
                            HalVersion halVersion = HalVersion::LATEST,
                            std::set<V1_3::OperationType> operationTypes = {})
            : DeviceSpecification(name, perf, perf, operationMask, oem, halVersion,
                                  operationTypes) {}
        DeviceSpecification(const std::string& name, float perf, float perfRelaxed,
                            uint32_t operationMask,
                            PartitioningDriver::OEM oem = PartitioningDriver::OEMNo,
                            HalVersion halVersion = HalVersion::LATEST,
                            std::set<V1_3::OperationType> operationTypes = {})
            : DeviceSpecification(name, kVersionString, perf, perfRelaxed, operationMask, oem,
                                  halVersion, operationTypes) {}
        DeviceSpecification(const std::string& name, const std::string& version, float perf,
                            uint32_t operationMask,
                            PartitioningDriver::OEM oem = PartitioningDriver::OEMNo,
                            HalVersion halVersion = HalVersion::LATEST,
                            std::set<V1_3::OperationType> operationTypes = {})
            : DeviceSpecification(name, version, perf, perf, operationMask, oem, halVersion,
                                  operationTypes) {}
        DeviceSpecification(const std::string& name, const std::string& version, float perf,
                            float perfRelaxed, uint32_t operationMask,
                            PartitioningDriver::OEM oem = PartitioningDriver::OEMNo,
                            HalVersion halVersion = HalVersion::LATEST,
                            std::set<V1_3::OperationType> operationTypes = {})
            : mName(name),
              mVersionString(version),
              mHalVersion(halVersion),
              mOperationMask(operationMask),
              mOEM(oem),
              mOperationTypes(std::move(operationTypes)) {
            V1_0::PerformanceInfo perfInfo = {.execTime = perf, .powerUsage = perf};
            V1_0::PerformanceInfo perfRelaxedInfo = {.execTime = perfRelaxed,
                                                     .powerUsage = perfRelaxed};
            mCapabilities = {
                    .relaxedFloat32toFloat16PerformanceScalar = perfRelaxedInfo,
                    .relaxedFloat32toFloat16PerformanceTensor = perfRelaxedInfo,
                    .operandPerformance =
                            ::android::nn::nonExtensionOperandPerformance<HalVersion::V1_3>(
                                    perfInfo),
                    .ifPerformance = perfInfo,
                    .whilePerformance = perfInfo};
        }
        DeviceSpecification(const std::string& name, float perf, HalVersion halVersion,
                            uint32_t operationMaskV1_0, uint32_t operationMaskV1_1 = 0,
                            uint32_t operationMaskV1_2 = 0, uint32_t operationMaskV1_3 = 0)
            : DeviceSpecification(
                      name, perf, perf,
                      makeOperationMask(halVersion, operationMaskV1_0, operationMaskV1_1,
                                        operationMaskV1_2, operationMaskV1_3)) {
            mHalVersion = halVersion;
        }

        std::string mName;
        std::string mVersionString;
        V1_3::Capabilities mCapabilities;
        HalVersion mHalVersion = HalVersion::LATEST;
        uint32_t mOperationMask;
        PartitioningDriver::OEM mOEM = PartitioningDriver::OEMNo;
        std::set<V1_3::OperationType> mOperationTypes;

        static constexpr char kVersionString[] = "JUST_AN_EXAMPLE";

       private:
        // This function takes three operation masks aligned at the low-order
        // bit -- one mask each for V1_0, V1_1, and V1_2 -- and produces a single
        // composite operation mask, formed by shifting each of the input
        // operation masks appropriately and ORing the results together.
        //
        // For convenience, any bits of an input mask that are too high order
        // for that mask are discarded -- this allows ~0 to be a legal input
        // mask.
        //
        // For the sake of example, assume that each low order mask is 4 bits
        // wide, and take some artistic license to write literals in binary.
        // Then:
        //
        //     assert(makeOperationMask(HalVersion::V1_2, 0b0110, 0b1001, 0b0101) ==
        //            0b 0101 1001 0110);
        //
        // This is used by a DeviceSpecification constructor to build a mask of
        // operations to be supported by the device.
        static uint32_t makeOperationMask(HalVersion halVersion, uint32_t operationMaskV1_0,
                                          uint32_t operationMaskV1_1, uint32_t operationMaskV1_2,
                                          uint32_t operationMaskV1_3) {
            if (halVersion < HalVersion::V1_3) {
                CHECK(!operationMaskV1_3);
            }
            if (halVersion < HalVersion::V1_2) {
                CHECK(!operationMaskV1_2);
            }
            if (halVersion < HalVersion::V1_1) {
                CHECK(!operationMaskV1_1);
            }
            auto maskOfWidth = [](uint32_t width) -> uint32_t { return (1U << width) - 1; };
            static const uint32_t kOperationMaskV1_0 =
                    maskOfWidth(kLastEncodingV1_0 - kFirstEncodingV1_0 + 1);
            static const uint32_t kOperationMaskV1_1 =
                    maskOfWidth(kLastEncodingV1_1 - kFirstEncodingV1_1 + 1);
            static const uint32_t kOperationMaskV1_2 =
                    maskOfWidth(kLastEncodingV1_2 - kFirstEncodingV1_2 + 1);
            static const uint32_t kOperationMaskV1_3 =
                    maskOfWidth(kLastEncodingV1_3 - kFirstEncodingV1_3 + 1);
            return ((operationMaskV1_0 & kOperationMaskV1_0) << kFirstEncodingV1_0) |
                   ((operationMaskV1_1 & kOperationMaskV1_1) << kFirstEncodingV1_1) |
                   ((operationMaskV1_2 & kOperationMaskV1_2) << kFirstEncodingV1_2) |
                   ((operationMaskV1_3 & kOperationMaskV1_3) << kFirstEncodingV1_3);
        }
    };
    static std::vector<std::shared_ptr<Device>> makeDevices(
            std::vector<DeviceSpecification> specifications) {
        std::vector<std::shared_ptr<Device>> devices;
        for (const auto& specification : specifications) {
            SharedDevice device = nullptr;
            switch (specification.mHalVersion) {
                case HalVersion::V1_3:
                    device = android::nn::makeSharedDevice(
                            specification.mName,
                            new PartitioningDriver(specification.mName.c_str(),
                                                   specification.mVersionString.c_str(),
                                                   specification.mCapabilities,
                                                   specification.mOperationMask, specification.mOEM,
                                                   specification.mOperationTypes));
                    break;
                case HalVersion::V1_2:
                    device = android::nn::makeSharedDevice(
                            specification.mName,
                            new PartitioningDriverV1_2(
                                    specification.mName.c_str(),
                                    specification.mVersionString.c_str(),
                                    specification.mCapabilities, specification.mOperationMask,
                                    specification.mOEM, specification.mOperationTypes));
                    break;
                case HalVersion::V1_1:
                    device = android::nn::makeSharedDevice(
                            specification.mName,
                            new PartitioningDriverV1_1(
                                    specification.mName.c_str(),
                                    specification.mVersionString.c_str(),
                                    specification.mCapabilities, specification.mOperationMask,
                                    specification.mOEM, specification.mOperationTypes));
                    break;
                case HalVersion::V1_0:
                    device = android::nn::makeSharedDevice(
                            specification.mName,
                            new PartitioningDriverV1_0(
                                    specification.mName.c_str(),
                                    specification.mVersionString.c_str(),
                                    specification.mCapabilities, specification.mOperationMask,
                                    specification.mOEM, specification.mOperationTypes));
                    break;
                default:
                    ADD_FAILURE() << "Unexpected";
            }
            auto driverDevice = DeviceManager::forTest_makeDriverDevice(device);
            devices.push_back(std::move(driverDevice));
        }
        devices.push_back(DeviceManager::getCpuDevice());
        return devices;
    }

    static std::string stepsToString(const std::vector<std::string>& steps) {
        std::stringstream ss;
        ss << "[ ";
        for (const auto& step : steps) {
            ss << step << " ";
        }
        ss << "]";
        return ss.str();
    }

    // Checks the type of each logical step in an execution plan.
    // Each entry of "expected" is either: kIfStep for IfStep, kWhileStep for WhileStep,
    // kGotoStep for GotoStep, or the device name for ExecutionStep.
    void checkExecutionPlanSteps(const ExecutionPlan& plan,
                                 const std::vector<std::string>& expected) {
        ASSERT_GT(expected.size(), 0u);

        std::vector<std::string> actual;
        if (expected.size() == 1) {
            ASSERT_EQ(plan.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
            actual.emplace_back(plan.forTest_simpleGetDevice()->getName());
        } else {
            ASSERT_EQ(plan.forTest_getKind(), ExecutionPlan::Kind::COMPOUND);
            const auto& steps = plan.forTest_compoundGetSteps();
            for (const auto& step : steps) {
                if (step->isIf()) {
                    actual.emplace_back(kIfStep);
                } else if (step->isWhile()) {
                    actual.emplace_back(kWhileStep);
                } else if (step->isGoto()) {
                    actual.emplace_back(kGotoStep);
                } else if (step->isExecution()) {
                    actual.emplace_back(step->executionStep()->getDevice()->getName());
                } else {
                    ASSERT_FALSE(true) << "Unknown LogicalStep";
                }
            }
        }
        ASSERT_TRUE(actual == expected)
                << "expected: " << stepsToString(expected) << ", actual: " << stepsToString(actual);
    }

    /*-- Graph comparision ----------------------------------------------------------------*/

    // An operand with certain values for its lifetime does not have a
    // defining operation in the graph.  For the purposes of the graph
    // comparison algorithm, we encode the "defining operation" index of
    // such an operand as follows:
    // - NO_VALUE       kPseudoDefiningOperationNoValue
    // - SUBGRAPH_INPUT kPseudoDefiningOperationModelInput0 + (position in list of inputs)
    // - CONSTANT_COPY  kPseudoDefiningOperationConstantCopy0 + (constant value)
    //                    Note: For the graphs we build in this test, we
    //                          only expect to see 4-byte constants within
    //                          a very restricted range, so we only make
    //                          room for such constants in our encoding
    //                          space.
    // We do not expect to see CONSTANT_REFERENCE, and so we do not handle
    // it.
    //
    // The encoding is intended to be relatively human readable; it is not
    // designed to represent some optimal balance of ranges for the items
    // within its scope (actual operations, inputs, constants).

    enum PseudoDefiningOperationEncodings : uint32_t {
        kPseudoDefiningOperationModelInput0 = 0x80000000U,
        kPseudoDefiningOperationConstantCopy0 = 0x90000000U,
        kPseudoDefiningOperationNoValue = 0xeeeeeeeeU,

        // lowest value for special encoding
        kPseudoDefiningOperationBase = 0x80000000U,

        // range of encoded input or constant
        kPseudoDefiningOperationRange = 0x10000000U,
    };

    // Build a map from operand to defining operation.
    // TODO: Replace map with vector?
    void buildDefinitionMap(const ModelBuilder* model, std::map<uint32_t, uint32_t>* defMap) {
        // actual definitions
        ASSERT_LT(model->operationCount(), kPseudoDefiningOperationBase);
        for (uint32_t i = 0, e = model->operationCount(); i < e; i++) {
            const V1_3::Operation& operation = android::nn::convertToV1_3(model->getOperation(i));
            for (uint32_t output : operation.outputs) {
                (*defMap)[output] = i;
            }
        }
        // inputs
        ASSERT_LT(model->inputCount(), kPseudoDefiningOperationRange);
        for (uint32_t i = 0, e = model->inputCount(); i < e; i++) {
            (*defMap)[model->getInputOperandIndex(i)] = kPseudoDefiningOperationModelInput0 + i;
        }
        // look for NO_VALUE and CONSTANT_COPY
        for (uint32_t i = 0, e = model->operandCount(); i < e; i++) {
            const V1_3::Operand& operand = android::nn::convertToV1_3(model->getOperand(i));
            switch (operand.lifetime) {
                case V1_3::OperandLifeTime::NO_VALUE:
                    (*defMap)[i] = kPseudoDefiningOperationNoValue;
                    break;
                case V1_3::OperandLifeTime::CONSTANT_COPY: {
                    ASSERT_EQ(operand.location.length, sizeof(uint32_t));
                    uint32_t value;
                    memcpy(&value, model->getPointerToOperandValue(operand.location.offset),
                           sizeof(uint32_t));
                    ASSERT_LT(value, kPseudoDefiningOperationNoValue);
                    (*defMap)[i] = kPseudoDefiningOperationConstantCopy0 + value;
                    break;
                }
                case V1_3::OperandLifeTime::TEMPORARY_VARIABLE:
                case V1_3::OperandLifeTime::SUBGRAPH_INPUT:
                case V1_3::OperandLifeTime::SUBGRAPH_OUTPUT:
                    // already handled
                    break;
                default:
                    FAIL();
                    break;
            }
        }
        // validity check
        ASSERT_EQ(model->operandCount(), defMap->size());
    }

#ifdef VERBOSE
    void dump(const char* name, const std::map<uint32_t, uint32_t>* aMap) {
        auto writeNum = [](uint32_t num) {
            if (num >= kPseudoDefiningOperationBase) {
                std::cout << "0x" << std::hex << num << std::dec;
            } else {
                std::cout << num;
            }
        };

        std::cout << name << ": { ";
        bool gotOne = false;
        for (const auto& entry : *aMap) {
            if (gotOne) {
                std::cout << ", ";
            } else {
                gotOne = true;
            }
            std::cout << "(";
            writeNum(entry.first);
            std::cout << ", ";
            writeNum(entry.second);
            std::cout << ")";
        }
        std::cout << " }" << std::endl;
    }
#endif

    bool compare(const Operand& operandA, const Operand& operandB) {
        if (operandA.type != operandB.type || operandA.dimensions != operandB.dimensions ||
            operandA.scale != operandB.scale || operandA.zeroPoint != operandB.zeroPoint) {
            return false;
        }
        return true;
    }

    // Compare two graphs.  We ignore operand and operation indexes (i.e.,
    // two nodes can be the same even if they are numbered differently)
    // but we also ignore semantics (e.g., even if an operation kind is
    // such that the operand is commutative, we still pay attention to the
    // order of its input operands).
    //
    // The comparison algorithm works by walking modelA from outputs
    // towards inputs, along the edge from each operand to its
    // defining operation, and then along the edges to the operation's
    // input operands.  At each step along the way, we try to match up
    // operands and operations from modelA with equivalent operands
    // and operations from modelB.
    //
    // We start by assuming that modelA's outputs and modelB's outputs
    // match positionally (e.g., modelA's first output operand is
    // equivalent to modelB's first output operand).  Once we've
    // discovered two equivalent operands (such as those outputs), we
    // place them in a work queue.  We repeatedly pull operands off
    // the queue and compare their defining operations and those
    // operations' input operands, to discover more pairs of
    // equivalent operands.  If we ever find operations that do not
    // match (e.g., because operation kind differs), or operands that
    // do not match (e.g., because operand type differs); or if we
    // ever find a conflict (we've already decided that operand A's
    // equivalent operand is B0, but it looks like we need its
    // equivalent operand to be B1); then the graphs compare unequal.
    // Otherwise, we'll eventually exhaust the work queue, and
    // conclude that the graphs compare equal.
    //
    // As a side effect of the comparison, we produce a map
    // *inputsAndOutputsBToA that maps from each of the model input and output
    // operand numbers of modelB to the corresponding operand numbers of modelA.
    // If the comparison returns false, the contents of the map are undefined.
    bool compare(const ModelBuilder* modelA, const ModelBuilder* modelB,
                 std::map<uint32_t, uint32_t>* inputsAndOutputsBToA) {
        CHECK(inputsAndOutputsBToA != nullptr);
        EXPECT_TRUE(inputsAndOutputsBToA->empty());

#ifdef VERBOSE
        ::dump("compare(A)", modelA);
        ::dump("compare(B)", modelB);
#endif

        if (modelA->operandCount() != modelB->operandCount() ||
            modelA->operationCount() != modelB->operationCount() ||
            modelA->inputCount() != modelB->inputCount() ||
            modelA->outputCount() != modelB->outputCount()) {
            RETURN_FALSE();
        }

        // Maps from operand index to index of defining operation.
        std::map<uint32_t, uint32_t> defsA, defsB;
        buildDefinitionMap(modelA, &defsA);
        buildDefinitionMap(modelB, &defsB);
        if (HasFatalFailure()) return false;

        // Maps from operand index in modelA to equivalent operand index
        // in modelB; and from operation index in modelA to equivalent
        // operation index in modelB.
        std::map<uint32_t, uint32_t> equivalentOperandsAToB;
        std::map<uint32_t, uint32_t> equivalentOperationsAToB;

        // Queue of operand indexes from modelA, each of whose defining
        // operations are to be checked for equivalence with modelB.
        std::queue<uint32_t> workQueueOperandsA;

        // Seed operand equivalence map and work queue from model outputs.
        for (uint32_t i = 0, e = modelA->outputCount(); i < e; i++) {
            uint32_t outputA = modelA->getOutputOperandIndex(i);
            uint32_t outputB = modelB->getOutputOperandIndex(i);
            if (!compare(modelA->getOperand(outputA), modelB->getOperand(outputB))) {
#ifdef VERBOSE
                std::cout << "modelA.output[" << i << "] = operand[" << outputA
                          << "] = " << toString(modelA->getOperand(outputA)) << std::endl;
                std::cout << "modelB.output[" << i << "] = operand[" << outputB
                          << "] = " << toString(modelB->getOperand(outputB)) << std::endl;
#endif
                RETURN_FALSE();
            }
            equivalentOperandsAToB[outputA] = outputB;
            workQueueOperandsA.push(outputA);
        }

#ifdef VERBOSE
        dump("defsA", &defsA);
        dump("defsB", &defsB);
#endif

        // Process the queue.
        uint32_t pseudoDefinitionCount = 0;
        while (!workQueueOperandsA.empty()) {
#ifdef VERBOSE
            dump("equivalentOperandsAToB", &equivalentOperandsAToB);
            dump("equivalentOperationsAToB", &equivalentOperationsAToB);
#endif
            uint32_t operandIndexA = workQueueOperandsA.front();
#ifdef VERBOSE
            std::cout << "operandIndexA: " << operandIndexA << std::endl;
#endif
            workQueueOperandsA.pop();
            uint32_t operandIndexB = equivalentOperandsAToB.at(operandIndexA);

            uint32_t operationIndexA = defsA.at(operandIndexA);
            uint32_t operationIndexB = defsB.at(operandIndexB);
            auto it = equivalentOperationsAToB.find(operationIndexA);
            if (it != equivalentOperationsAToB.end()) {
                if (it->second != operationIndexB) {
                    RETURN_FALSE();
                }
                continue;
            }

            // We haven't identified an equivalent operation for
            // operationIndexA.

            if ((operationIndexA >= kPseudoDefiningOperationBase) !=
                (operationIndexB >= kPseudoDefiningOperationBase)) {
                RETURN_FALSE();
            }
            // Either both operands have pseudo-definitions, or neither
            // does.
            if (operationIndexA >= kPseudoDefiningOperationBase) {
                // Both operands have pseudo-definitions.
                if (operationIndexA != operationIndexB) {
                    RETURN_FALSE();
                }
                equivalentOperationsAToB[operationIndexA] = operationIndexB;
                ++pseudoDefinitionCount;
                continue;
            }

            // If we get here, neither operation A nor operation B is a
            // pseudo-definition.

            const Operation& operationA = modelA->getOperation(operationIndexA);
            const Operation& operationB = modelB->getOperation(operationIndexB);
            if (operationA.type != operationB.type ||
                operationA.inputs.size() != operationB.inputs.size() ||
                operationA.outputs.size() != operationB.outputs.size()) {
                RETURN_FALSE();
            }
            equivalentOperationsAToB[operationIndexA] = operationIndexB;
            for (uint32_t i = 0, e = operationA.inputs.size(); i < e; i++) {
                uint32_t inputA = operationA.inputs[i];
                uint32_t inputB = operationB.inputs[i];
                auto it = equivalentOperandsAToB.find(inputA);
                if (it != equivalentOperandsAToB.end()) {
                    if (it->second != inputB) {
                        RETURN_FALSE();
                    }
                    continue;
                }
                // We haven't identified an equivalent operand for inputA.
                if (!compare(modelA->getOperand(inputA), modelB->getOperand(inputB))) {
#ifdef VERBOSE
                    std::cout << "modelA.input[" << i << "] = operand[" << inputA
                              << "] = " << toString(modelA->getOperand(inputA)) << std::endl;
                    std::cout << "modelB.input[" << i << "] = operand[" << inputB
                              << "] = " << toString(modelB->getOperand(inputB)) << std::endl;
#endif
                    RETURN_FALSE();
                }
                equivalentOperandsAToB[inputA] = inputB;
                workQueueOperandsA.push(inputA);
            }
        }

        // Validity check
        if (modelA->operandCount() != defsA.size() || modelA->operandCount() != defsB.size() ||
            modelA->operandCount() != equivalentOperandsAToB.size() ||
            modelA->operationCount() + pseudoDefinitionCount != equivalentOperationsAToB.size()) {
            RETURN_FALSE();
        }

        // Build *inputsAndOutputsBToA
        for (uint32_t aInputIndex : modelA->getInputOperandIndexes()) {
            (*inputsAndOutputsBToA)[equivalentOperandsAToB.at(aInputIndex)] = aInputIndex;
        }
        for (uint32_t aOutputIndex : modelA->getOutputOperandIndexes()) {
            (*inputsAndOutputsBToA)[equivalentOperandsAToB.at(aOutputIndex)] = aOutputIndex;
        }

        RETURN_TRUE();
    }

    /*-------------------------------------------------------------------------------------*/

    // As a side effect of the comparison, we produce a map
    // *inputsAndOutputsModelToStep that maps from each of the model input and
    // output operand numbers of "model" to the corresponding operand numbers of
    // the step model from "step".  If the comparison returns false, the contents
    // of the map are undefined.
    bool compare(const ExecutionStep* step, const PartitioningModel* model,
                 std::shared_ptr<Device> device,
                 std::map<uint32_t, uint32_t>* inputsAndOutputsModelToStep) {
        return (step->getDevice() == device) &&
               compare(step->getStepModel(),
                       reinterpret_cast<const ModelBuilder*>(model->getHandle()),
                       inputsAndOutputsModelToStep);
    }

    void compare(const std::shared_ptr<LogicalStep> logicalStep, const PartitioningModel* model,
                 std::shared_ptr<Device> device, const RemapVectorType& modelInputs,
                 const RemapVectorType& modelOutputs, const RemapVectorType& tempsAsStepModelInputs,
                 const StepModelOutputSetType& tempsAsStepModelOutputs,
                 const RemapVectorType& outputsAsStepModelInputs,
                 const std::set<uint32_t>& modelOutputsThatAreDownstreamInputs) {
        ASSERT_TRUE(logicalStep->isExecution());
        const ExecutionStep* step = logicalStep->executionStep();
        std::map<uint32_t, uint32_t> inputsAndOutputsModelToStep;
        ASSERT_NO_FATAL_FAILURE(
                ASSERT_TRUE(compare(step, model, device, &inputsAndOutputsModelToStep)));
        ASSERT_TRUE(compareRemapVectors(inputsAndOutputsModelToStep, step->getModelInputs(),
                                        modelInputs));
        ASSERT_TRUE(compareRemapVectors(inputsAndOutputsModelToStep, step->getModelOutputs(),
                                        modelOutputs));
        ASSERT_TRUE(compareRemapVectors(inputsAndOutputsModelToStep,
                                        step->getTempsAsStepModelInputs(), tempsAsStepModelInputs));
        ASSERT_TRUE(compareStepModelOutputSets(inputsAndOutputsModelToStep,
                                               step->getTempsAsStepModelOutputs(),
                                               tempsAsStepModelOutputs));
        ASSERT_TRUE(compareRemapVectors(inputsAndOutputsModelToStep,
                                        step->getOutputsAsStepModelInputs(),
                                        outputsAsStepModelInputs));
        ASSERT_TRUE(modelOutputsThatAreDownstreamInputs ==
                    step->getModelOutputsThatAreDownstreamInputs());
    }

   private:
    static bool compareRemapVectors(const std::map<uint32_t, uint32_t>& inputsAndOutputsModelToStep,
                                    const RemapVectorType& step, RemapVectorType model) {
        std::transform(model.begin(), model.end(), model.begin(),
                       [&inputsAndOutputsModelToStep](const RemapVectorType::value_type& val) {
                           return std::make_pair(val.first,
                                                 inputsAndOutputsModelToStep.at(val.second));
                       });
        return step == model;
    }

    static bool compareStepModelOutputSets(
            const std::map<uint32_t, uint32_t>& inputsAndOutputsModelToStep,
            const StepModelOutputSetType& step, const StepModelOutputSetType& model) {
        StepModelOutputSetType modelTransformed;
        std::transform(
                model.begin(), model.end(), std::inserter(modelTransformed, modelTransformed.end()),
                [&inputsAndOutputsModelToStep](const StepModelOutputSetType::value_type& val) {
                    return std::make_pair(val.first, inputsAndOutputsModelToStep.at(val.second));
                });
        return step == modelTransformed;
    }
};

TEST_F(PartitioningTest, SimpleModel) {
    PartitioningModel model;
    uint32_t opnd0 = model.addFloatOperand();
    uint32_t opnd1 = model.addFloatOperand();
    uint32_t opnd2 = model.addOperation2To1V1_0(0, opnd0, opnd1);
    uint32_t opnd3 = model.addFloatOperand();
    uint32_t opnd4 = model.addOperation2To1V1_0(1, opnd2, opnd3);
    model.identifyInputsAndOutputs({opnd0, opnd1, opnd3}, {opnd4});
    model.finish();
    ASSERT_TRUE(model.isValid());

    // Simple partition (two devices are each capable of everything, one is the best).
    // No need to compare the original model to the model from the plan -- we
    // didn't actually do any partitioning.
    const auto devicesA = makeDevices({{"bad", 0.9, ~0U}, {"good", 0.5, ~0U}});
    ExecutionPlan planA;
    ASSERT_EQ(model.partitionTheWork(devicesA, ExecutePreference::PREFER_LOW_POWER,
                                     ExecutePriority::DEFAULT, {}, &planA),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_TRUE(planA.forTest_flatGetDynamicTemporaries().empty());
    ASSERT_EQ(planA.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
    ASSERT_NE(planA.forTest_simpleGetDevice().get(), nullptr);
    ASSERT_EQ(planA.forTest_simpleGetDevice()->getName(), "good");

    // Simple partition (two devices are each capable of everything, none better than CPU).
    // No need to compare the original model to the model from the plan -- we
    // didn't actually do any partitioning.
    const auto devicesC = makeDevices({{"bad", 1.1, ~0U}, {"bad2", 1.0, ~0U}});
    ExecutionPlan planC;
    ASSERT_EQ(model.partitionTheWork(devicesC, ExecutePreference::PREFER_LOW_POWER,
                                     ExecutePriority::DEFAULT, {}, &planC),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_TRUE(planC.forTest_flatGetDynamicTemporaries().empty());
    ASSERT_EQ(planC.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
    ASSERT_EQ(planC.forTest_simpleGetDevice(), DeviceManager::getCpuDevice());

    // Compound partition (two devices, each is capable of one of the
    // two operations).  We could do more extensive checking here --
    // for example, verify that each step within the plan has the
    // correct (model and step model)x(inputs and outputs).
    const auto devicesB = makeDevices({{"0", 0.9, 1 << 0}, {"1", 0.5, 1 << 1}});
    ExecutionPlan planB;
    ASSERT_EQ(model.partitionTheWork(devicesB, ExecutePreference::PREFER_LOW_POWER,
                                     ExecutePriority::DEFAULT, {}, &planB),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_TRUE(planB.forTest_flatGetDynamicTemporaries().empty());
    ASSERT_EQ(planB.forTest_getKind(), ExecutionPlan::Kind::COMPOUND);
    const auto& stepsB = planB.forTest_compoundGetSteps();
    ASSERT_EQ(stepsB.size(), size_t(2));
    {
        // Build a model to compare against the step model from stepsB[0].
        PartitioningModel modelB0;
        uint32_t b0Opnd0 = modelB0.addFloatOperand();
        uint32_t b0Opnd1 = modelB0.addFloatOperand();
        uint32_t b0Opnd2 = modelB0.addOperation2To1V1_0(0, b0Opnd0, b0Opnd1);
        modelB0.identifyInputsAndOutputs({b0Opnd0, b0Opnd1}, {b0Opnd2});
        modelB0.finish();
        ASSERT_TRUE(modelB0.isValid());

        ASSERT_NO_FATAL_FAILURE(
                compare(stepsB[0], &modelB0, devicesB[0],
                        RemapVectorType{{opnd0, b0Opnd0}, {opnd1, b0Opnd1}},  // modelInputs
                        RemapVectorType{},                                    // modelOutputs
                        RemapVectorType{},                         // tempsAsStepModelInputs
                        StepModelOutputSetType{{opnd2, b0Opnd2}},  // tempsAsStepModelOutputs
                        RemapVectorType{},                         // outputsAsStepModelInputs
                        {}));  // modelOutputsThatAreDownstreamInputs
    }
    {
        // Build a model to compare against the step model from stepsB[1].
        PartitioningModel modelB1;
        uint32_t b1Opnd2 = modelB1.addFloatOperand();
        uint32_t b1Opnd3 = modelB1.addFloatOperand();
        uint32_t b1Opnd4 = modelB1.addOperation2To1V1_0(1, b1Opnd2, b1Opnd3);
        // Note: In the partitioning algorithm, step model inputs follow
        // model inputs.  In the original model "model", opnd2 is not
        // an input; so in the step model "modelB1", the corresponding
        // input b1Opnd2 is a step model input, and must follow the
        // model input b1Opnd3.
        modelB1.identifyInputsAndOutputs({b1Opnd3, b1Opnd2}, {b1Opnd4});
        modelB1.finish();
        ASSERT_TRUE(modelB1.isValid());

        ASSERT_NO_FATAL_FAILURE(compare(
                stepsB[1], &modelB1, devicesB[1], RemapVectorType{{opnd3, b1Opnd3}},  // modelInputs
                RemapVectorType{{opnd4, b1Opnd4}},  // modelOutputs
                RemapVectorType{{opnd2, b1Opnd2}},  // tempsAsStepModelInputs
                StepModelOutputSetType{},           // tempsAsStepModelOutputs
                RemapVectorType{},                  // outputsAsStepModelInputs
                {}));                               // modelOutputsThatAreDownstreamInputs
    }
}

TEST_F(PartitioningTest, SliceModel) {
    PartitioningModel model;
    uint32_t opnd0 = model.addFloatOperand();
    uint32_t opnd1 = model.addFloatOperand();
    uint32_t opnd2 = model.addOperation2To1V1_0(0, opnd0, opnd1);
    uint32_t opnd3 = model.addOperation2To1V1_0(1, opnd0, opnd1);
    uint32_t opnd4 = model.addOperation2To1V1_1(0, opnd0, opnd1);
    uint32_t opnd5 = model.addOperation2To1V1_2(0, opnd2, opnd3);
    uint32_t opnd6 = model.addOperation1To1V1_3(0, opnd2);
    model.identifyInputsAndOutputs({opnd0, opnd1}, {opnd2, opnd4, opnd5, opnd6});
    model.finish();
    ASSERT_TRUE(model.isValid());

    // Simple partition (V1_0, V1_1, V1_2, V1_3 devices are available; V1_3 has best perf).
    // No need to compare the original model to the model from the plan -- we
    // didn't actually do any partitioning.
    const auto devicesA = makeDevices({{"V1_0", 0.8, HalVersion::V1_0, ~0U},
                                       {"V1_1", 0.7, HalVersion::V1_1, ~0U, ~0U},
                                       {"V1_2", 0.6, HalVersion::V1_2, ~0U, ~0U, ~0U},
                                       {"V1_3", 0.5, HalVersion::V1_3, ~0U, ~0U, ~0U, ~0U}});
    ExecutionPlan planA;
    ASSERT_EQ(model.partitionTheWork(devicesA, ExecutePreference::PREFER_LOW_POWER,
                                     ExecutePriority::DEFAULT, {}, &planA),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_TRUE(planA.forTest_flatGetDynamicTemporaries().empty());
    ASSERT_EQ(planA.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
    ASSERT_NE(planA.forTest_simpleGetDevice().get(), nullptr);
    ASSERT_EQ(planA.forTest_simpleGetDevice()->getName(), "V1_3");

    // Compound partition (V1_0, V1_1, V1_2 devices are available, in decreasing
    // order of performance; model is distributed across all three devices).
    const auto devicesB = makeDevices({{"V1_0", 0.6, HalVersion::V1_0, ~0U},
                                       {"V1_1", 0.7, HalVersion::V1_1, ~0U, ~0U},
                                       {"V1_2", 0.8, HalVersion::V1_2, ~0U, ~0U, ~0U},
                                       {"V1_3", 0.9, HalVersion::V1_3, ~0U, ~0U, ~0U, ~0U}});
    ExecutionPlan planB;
    ASSERT_EQ(model.partitionTheWork(devicesB, ExecutePreference::PREFER_LOW_POWER,
                                     ExecutePriority::DEFAULT, {}, &planB),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_TRUE(planB.forTest_flatGetDynamicTemporaries().empty());
    ASSERT_EQ(planB.forTest_getKind(), ExecutionPlan::Kind::COMPOUND);
    const auto& stepsB = planB.forTest_compoundGetSteps();
    ASSERT_EQ(stepsB.size(), size_t(4));
    {
        // Build a model to compare against the step model from stepsB[0].
        PartitioningModel modelB0;
        uint32_t b0Opnd0 = modelB0.addFloatOperand();
        uint32_t b0Opnd1 = modelB0.addFloatOperand();
        uint32_t b0Opnd2 = modelB0.addOperation2To1V1_1(0, b0Opnd0, b0Opnd1);
        modelB0.identifyInputsAndOutputs({b0Opnd0, b0Opnd1}, {b0Opnd2});
        modelB0.finish();
        ASSERT_TRUE(modelB0.isValid());

        ASSERT_NO_FATAL_FAILURE(
                compare(stepsB[0], &modelB0, devicesB[1],
                        RemapVectorType{{opnd0, b0Opnd0}, {opnd1, b0Opnd1}},  // modelInputs
                        RemapVectorType{{opnd4, b0Opnd2}},                    // modelOutputs
                        RemapVectorType{},         // tempsAsStepModelInputs
                        StepModelOutputSetType{},  // tempsAsStepModelOutputs
                        RemapVectorType{},         // outputsAsStepModelInputs
                        {}));                      // modelOutputsThatAreDownstreamInputs
    }
    {
        // Build a model to compare against the step model from stepsB[1].
        PartitioningModel modelB1;
        uint32_t b1Opnd0 = modelB1.addFloatOperand();
        uint32_t b1Opnd1 = modelB1.addFloatOperand();
        uint32_t b1Opnd2 = modelB1.addOperation2To1V1_0(0, b1Opnd0, b1Opnd1);
        uint32_t b1Opnd3 = modelB1.addOperation2To1V1_0(1, b1Opnd0, b1Opnd1);
        modelB1.identifyInputsAndOutputs({b1Opnd0, b1Opnd1}, {b1Opnd2, b1Opnd3});
        modelB1.finish();
        ASSERT_TRUE(modelB1.isValid());

        // Note that this is also an important test that we can detect
        // modelOutputsThatAreDownstreamInputs.
        ASSERT_NO_FATAL_FAILURE(
                compare(stepsB[1], &modelB1, devicesB[0],
                        RemapVectorType{{opnd0, b1Opnd0}, {opnd1, b1Opnd1}},  // modelInputs
                        RemapVectorType{{opnd2, b1Opnd2}},                    // modelOutputs
                        RemapVectorType{},                         // tempsAsStepModelInputs
                        StepModelOutputSetType{{opnd3, b1Opnd3}},  // tempsAsStepModelOutputs
                        RemapVectorType{},                         // outputsAsStepModelInputs
                        {0u}));  // modelOutputsThatAreDownstreamInputs
    }
    {
        // Build a model to compare against the step model from stepsB[2].
        PartitioningModel modelB2;
        uint32_t b2Opnd0 = modelB2.addFloatOperand();
        uint32_t b2Opnd1 = modelB2.addOperation1To1V1_3(0, b2Opnd0);
        // Note: In the partitioning algorithm, temps that are
        // step model inputs precede model outputs that are step model
        // inputs.
        modelB2.identifyInputsAndOutputs({b2Opnd0}, {b2Opnd1});
        modelB2.finish();
        ASSERT_TRUE(modelB2.isValid());

        ASSERT_NO_FATAL_FAILURE(
                compare(stepsB[2], &modelB2, devicesB[3], RemapVectorType{},  // modelInputs
                        RemapVectorType{{opnd6, b2Opnd1}},                    // modelOutputs
                        RemapVectorType{},                  // tempsAsStepModelInputs
                        StepModelOutputSetType{},           // tempsAsStepModelOutputs
                        RemapVectorType{{opnd2, b2Opnd0}},  // outputsAsStepModelInputs
                        {}));                               // modelOutputsThatAreDownstreamInputs
    }
    {
        // Build a model to compare against the step model from stepsB[3].
        PartitioningModel modelB3;
        uint32_t b3Opnd0 = modelB3.addFloatOperand();
        uint32_t b3Opnd1 = modelB3.addFloatOperand();
        uint32_t b3Opnd2 = modelB3.addOperation2To1V1_2(0, b3Opnd0, b3Opnd1);
        // Note: In the partitioning algorithm, temps that are
        // step model inputs precede model outputs that are step model
        // inputs.  In the original model "model", opnd3 is a temp and
        // opnd2 is a model output; so in the step model "modelB3", the
        // corresponding inputs b3Opnd1 and b3Opnd0 must appear in
        // that order.
        modelB3.identifyInputsAndOutputs({b3Opnd1, b3Opnd0}, {b3Opnd2});
        modelB3.finish();
        ASSERT_TRUE(modelB3.isValid());

        ASSERT_NO_FATAL_FAILURE(
                compare(stepsB[3], &modelB3, devicesB[2], RemapVectorType{},  // modelInputs
                        RemapVectorType{{opnd5, b3Opnd2}},                    // modelOutputs
                        RemapVectorType{{opnd3, b3Opnd1}},  // tempsAsStepModelInputs
                        StepModelOutputSetType{},           // tempsAsStepModelOutputs
                        RemapVectorType{{opnd2, b3Opnd0}},  // outputsAsStepModelInputs
                        {}));                               // modelOutputsThatAreDownstreamInputs
    }

    // TODO: Make sure this still works when we have multiple devices
    // of same version available for slicing. An easy (?) choice would
    // be to route the two different V1_0 operations to different
    // devices.
}

TEST_F(PartitioningTest, SliceModelToEmpty) {
    PartitioningModel model;
    uint32_t opnd0 = model.addFloatOperand();
    uint32_t opnd1 = model.addOperation1To1V1_3(0, opnd0);
    model.identifyInputsAndOutputs({opnd0}, {opnd1});
    model.finish();
    ASSERT_TRUE(model.isValid());

    // Only the V1_3 device can handle any operations in the model.
    // No need to compare the original model to the model from the plan -- we
    // didn't actually do any partitioning.
    const auto devices = makeDevices({{"V1_0", 0.6, HalVersion::V1_0, ~0U},
                                      {"V1_1", 0.7, HalVersion::V1_1, ~0U, ~0U},
                                      {"V1_2", 0.8, HalVersion::V1_2, ~0U, ~0U, ~0U},
                                      {"V1_3", 0.9, HalVersion::V1_3, ~0U, ~0U, ~0U, ~0U}});
    ExecutionPlan plan;
    ASSERT_EQ(model.partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                     ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_TRUE(plan.forTest_flatGetDynamicTemporaries().empty());
    ASSERT_EQ(plan.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
    ASSERT_NE(plan.forTest_simpleGetDevice().get(), nullptr);
    ASSERT_EQ(plan.forTest_simpleGetDevice()->getName(), "V1_3");
}

TEST_F(PartitioningTest, Cpu) {
    // Here's a model where some operations execute only on the Cpu.
    // To make things interesting, we produce three partitions --
    // device, cpu, same-device.

    static const uint32_t kCpuOp = 1;
    static const uint32_t kDevOp = 2;

    const auto devices = makeDevices({{"1", 0.5, 1 << kDevOp}});

    PartitioningModel model;

    uint32_t opnd0 = model.addFloatOperand();
    uint32_t opnd1 = model.addFloatOperand();

    uint32_t opnd2 = model.addOperation2To1V1_0(kDevOp, opnd0, opnd1);
    uint32_t opnd3 = model.addOperation2To1V1_0(kDevOp, opnd0, opnd2);

    uint32_t opnd4 = model.addOperation2To1V1_0(kCpuOp, opnd0, opnd3);
    uint32_t opnd5 = model.addOperation2To1V1_0(kCpuOp, opnd2, opnd4);

    uint32_t opnd6 = model.addFloatOperand();

    uint32_t opnd7 = model.addOperation2To1V1_0(kDevOp, opnd3, opnd5);
    uint32_t opnd8 = model.addOperation2To1V1_0(kDevOp, opnd6, opnd7);

    model.identifyInputsAndOutputs({opnd0, opnd1, opnd6}, {opnd4, opnd8});
    model.finish();
    ASSERT_TRUE(model.isValid());

    ExecutionPlan plan;
    ASSERT_EQ(model.partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                     ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_TRUE(plan.forTest_flatGetDynamicTemporaries().empty());
    ASSERT_EQ(plan.forTest_getKind(), ExecutionPlan::Kind::COMPOUND);
    const auto& steps = plan.forTest_compoundGetSteps();
    ASSERT_EQ(steps.size(), size_t(3));
    {
        const auto& step0 = steps[0];

        // Build a model to compare against the step model from steps[0].
        PartitioningModel model0;
        uint32_t m0Opnd0 = model0.addFloatOperand();
        uint32_t m0Opnd1 = model0.addFloatOperand();
        uint32_t m0Opnd2 = model0.addOperation2To1V1_0(kDevOp, m0Opnd0, m0Opnd1);
        uint32_t m0Opnd3 = model0.addOperation2To1V1_0(kDevOp, m0Opnd0, m0Opnd2);
        model0.identifyInputsAndOutputs({m0Opnd0, m0Opnd1}, {m0Opnd2, m0Opnd3});
        model0.finish();
        ASSERT_TRUE(model0.isValid());

        ASSERT_NO_FATAL_FAILURE(
                compare(step0, &model0, devices[0],
                        RemapVectorType{{opnd0, m0Opnd0}, {opnd1, m0Opnd1}},  // modelInputs
                        RemapVectorType{},                                    // modelOutputs
                        RemapVectorType{},  // tempsAsStepModelInputs
                        StepModelOutputSetType{{opnd2, m0Opnd2},
                                               {opnd3, m0Opnd3}},  // tempsAsStepModelOutputs
                        RemapVectorType{},                         // outputsAsStepModelInputs
                        {}));  // modelOutputsThatAreDownstreamInputs
    }
    {
        const auto& step1 = steps[1];

        // Build a model to compare against the step model from steps[1].
        PartitioningModel model1;
        uint32_t m1Opnd0 = model1.addFloatOperand();
        uint32_t m1Opnd3 = model1.addFloatOperand();
        uint32_t m1Opnd4 = model1.addOperation2To1V1_0(kCpuOp, m1Opnd0, m1Opnd3);
        uint32_t m1Opnd2 = model1.addFloatOperand();
        uint32_t m1Opnd5 = model1.addOperation2To1V1_0(kCpuOp, m1Opnd2, m1Opnd4);
        model1.identifyInputsAndOutputs({m1Opnd0, m1Opnd3, m1Opnd2}, {m1Opnd4, m1Opnd5});
        model1.finish();
        ASSERT_TRUE(model1.isValid());

        ASSERT_NO_FATAL_FAILURE(compare(
                step1, &model1, DeviceManager::getCpuDevice(),
                RemapVectorType{{opnd0, m1Opnd0}},                    // modelInputs
                RemapVectorType{{opnd4, m1Opnd4}},                    // modelOutputs
                RemapVectorType{{opnd3, m1Opnd3}, {opnd2, m1Opnd2}},  // tempsAsStepModelInputs
                StepModelOutputSetType{{opnd5, m1Opnd5}},             // tempsAsStepModelOutputs
                RemapVectorType{},                                    // outputsAsStepModelInputs
                {}));  // modelOutputsThatAreDownstreamInputs
    }
    {
        const auto& step2 = steps[2];

        // Build a model to compare against the step model from steps[2].
        PartitioningModel model2;
        uint32_t m2Opnd3 = model2.addFloatOperand();
        uint32_t m2Opnd5 = model2.addFloatOperand();
        uint32_t m2Opnd7 = model2.addOperation2To1V1_0(kDevOp, m2Opnd3, m2Opnd5);
        uint32_t m2Opnd6 = model2.addFloatOperand();
        uint32_t m2Opnd8 = model2.addOperation2To1V1_0(kDevOp, m2Opnd6, m2Opnd7);
        model2.identifyInputsAndOutputs({m2Opnd6, m2Opnd3, m2Opnd5}, {m2Opnd8});
        model2.finish();
        ASSERT_TRUE(model2.isValid());

        ASSERT_NO_FATAL_FAILURE(compare(
                step2, &model2, devices[0], RemapVectorType{{opnd6, m2Opnd6}},  // modelInputs
                RemapVectorType{{opnd8, m2Opnd8}},                              // modelOutputs
                RemapVectorType{{opnd3, m2Opnd3}, {opnd5, m2Opnd5}},  // tempsAsStepModelInputs
                StepModelOutputSetType{},                             // tempsAsStepModelOutputs
                RemapVectorType{},                                    // outputsAsStepModelInputs
                {}));  // modelOutputsThatAreDownstreamInputs
    }
}

TEST_F(PartitioningTest, SetPartitioning) {
    PartitioningModel model;
    uint32_t opnd0 = model.addFloatOperand();
    uint32_t opnd1 = model.addFloatOperand();
    uint32_t opnd2 = model.addOperation2To1V1_0(0, opnd0, opnd1, Dimensioned::NO);
    uint32_t opnd3 = model.addFloatOperand();
    uint32_t opnd4 = model.addOperation2To1V1_0(1, opnd2, opnd3);
    model.identifyInputsAndOutputs({opnd0, opnd1, opnd3}, {opnd4});
    model.finish();
    ASSERT_TRUE(model.isValid());

    // One device that can and should execute operation 0.
    const auto devices = makeDevices({{"hw", 0.5, (1 << 0)}});

    // Test kPartitioningNo.  We should not even attempt partitioning,
    // so there should be a SIMPLE plan on CPU.
    // No need to compare the original model to the model from the plan -- we
    // didn't actually do any partitioning.
    PartitioningCompilation cPNo(&model, devices);
    ASSERT_EQ(cPNo.setPartitioning(DeviceManager::kPartitioningNo), Result::NO_ERROR);
    ASSERT_EQ(cPNo.failPartitioning(), Result::NO_ERROR);
    ASSERT_EQ(cPNo.finish(), Result::NO_ERROR);
    ASSERT_EQ(cPNo.getExecutionPlan().forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
    ASSERT_EQ(cPNo.getExecutionPlan().forTest_simpleGetDevice(), DeviceManager::getCpuDevice());

    // Test kPartitioningWithFallback.  We should attempt partitioning, simulate
    // a recoverable failure, then fallback to CPU with a SIMPLE plan, and
    // finally return success.  No need to compare the original model to the
    // model from the plan -- we didn't actually do any partitioning.
    PartitioningCompilation cPWithFallback(&model, devices);
    ASSERT_EQ(cPWithFallback.setPartitioning(DeviceManager::kPartitioningWithFallback),
              Result::NO_ERROR);
    ASSERT_EQ(cPWithFallback.failPartitioning(), Result::NO_ERROR);
    ASSERT_EQ(cPWithFallback.finish(), Result::NO_ERROR);
    ASSERT_EQ(cPWithFallback.getExecutionPlan().forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
    ASSERT_EQ(cPWithFallback.getExecutionPlan().forTest_simpleGetDevice(),
              DeviceManager::getCpuDevice());

    // Test kPartitioningWithoutFallback.  We should attempt partitioning,
    // simulate a recoverable failure, and fail.
    PartitioningCompilation cPWithoutFallback(&model, devices);
    ASSERT_EQ(cPWithoutFallback.setPartitioning(DeviceManager::kPartitioningWithoutFallback),
              Result::NO_ERROR);
    ASSERT_EQ(cPWithoutFallback.failPartitioning(), Result::NO_ERROR);
    ASSERT_EQ(cPWithoutFallback.finish(), Result::OP_FAILED);
    ASSERT_EQ(cPWithoutFallback.getExecutionPlan().forTest_getKind(), ExecutionPlan::Kind::ERROR);
}

// Regression test for http://b/69166603:
//     "partitioned compilation and execution yields wrong results when model output is step model
//     input"
TEST_F(PartitioningTest, ModelOutputAsStepModelInput) {
    PartitioningModel model;
    uint32_t opnd0 = model.addFloatOperand();
    uint32_t opnd1 = model.addFloatOperand();
    uint32_t opnd2 = model.addOperation2To1V1_0(0, opnd0, opnd1);
    uint32_t opnd3 = model.addOperation2To1V1_0(1, opnd2, opnd2);
    model.identifyInputsAndOutputs({opnd0, opnd1}, {opnd2, opnd3});
    model.finish();
    ASSERT_TRUE(model.isValid());

    // Compound partition (two devices, each is capable of one of the
    // two operations).  We could do more extensive checking here --
    // for example, verify that each step within the plan has the
    // correct (model and step model)x(inputs and outputs).
    const auto devices = makeDevices({{"0", 0.5, 1 << 0}, {"1", 0.5, 1 << 1}});
    ExecutionPlan plan;
    ASSERT_EQ(model.partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                     ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_TRUE(plan.forTest_flatGetDynamicTemporaries().empty());
    ASSERT_EQ(plan.forTest_getKind(), ExecutionPlan::Kind::COMPOUND);
    const auto& steps = plan.forTest_compoundGetSteps();
    ASSERT_EQ(steps.size(), size_t(2));
    {
        // Build a model to compare against the step model from steps[0].
        PartitioningModel model0;
        uint32_t m0Opnd0 = model0.addFloatOperand();
        uint32_t m0Opnd1 = model0.addFloatOperand();
        uint32_t m0Opnd2 = model0.addOperation2To1V1_0(0, m0Opnd0, m0Opnd1);
        model0.identifyInputsAndOutputs({m0Opnd0, m0Opnd1}, {m0Opnd2});
        model0.finish();
        ASSERT_TRUE(model0.isValid());
        ASSERT_NO_FATAL_FAILURE(
                compare(steps[0], &model0, devices[0],
                        RemapVectorType{{opnd0, m0Opnd0}, {opnd1, m0Opnd1}},  // modelInputs
                        RemapVectorType{{opnd2, m0Opnd2}},                    // modelOutputs
                        RemapVectorType{},         // tempsAsStepModelInputs
                        StepModelOutputSetType{},  // tempsAsStepModelOutputs
                        RemapVectorType{},         // outputsAsStepModelInputs
                        {0u}));                    // modelOutputsThatAreDownstreamInputs
    }
    {
        // Build a model to compare against the step model from steps[1].
        PartitioningModel model1;
        uint32_t m1Opnd2 = model1.addFloatOperand();
        uint32_t m1Opnd3 = model1.addOperation2To1V1_0(1, m1Opnd2, m1Opnd2);
        model1.identifyInputsAndOutputs({m1Opnd2}, {m1Opnd3});
        model1.finish();
        ASSERT_TRUE(model1.isValid());

        ASSERT_NO_FATAL_FAILURE(
                compare(steps[1], &model1, devices[1], RemapVectorType{},  // modelInputs
                        RemapVectorType{{opnd3, m1Opnd3}},                 // modelOutputs
                        RemapVectorType{},                                 // tempsAsStepModelInputs
                        StepModelOutputSetType{},           // tempsAsStepModelOutputs
                        RemapVectorType{{opnd2, m1Opnd2}},  // outputsAsStepModelInputs
                        {}));                               // modelOutputsThatAreDownstreamInputs
    }
}

TEST_F(PartitioningTest, OemOperations) {
    // Trivial model consisting solely of OEM operation.
    PartitioningModel model;
    uint32_t opndIn = model.addFloatOperand();
    uint32_t opndOut = model.addOperationOEM1To1(opndIn);
    model.identifyInputsAndOutputs({opndIn}, {opndOut});
    model.finish();
    ASSERT_TRUE(model.isValid());

    // Verify that the best driver than can run an OEM operation is
    // used, even if it is not better than the CPU.
    // No need to compare the original model to the model from the plan -- we
    // didn't actually do any partitioning.
    const auto devicesBestOEM = makeDevices({{"badOEM", 1.5, ~0U, PartitioningDriver::OEMYes},
                                             {"noOEM", 0.5, ~0U, PartitioningDriver::OEMNo},
                                             {"goodOEM", 1.2, ~0U, PartitioningDriver::OEMYes}});
    PartitioningCompilation compilationBestOEM(&model, devicesBestOEM);
    ASSERT_EQ(compilationBestOEM.finish(), Result::NO_ERROR);
    const auto& planBestOEM = compilationBestOEM.getExecutionPlan();
    ASSERT_EQ(planBestOEM.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
    ASSERT_NE(planBestOEM.forTest_simpleGetDevice().get(), nullptr);
    ASSERT_EQ(planBestOEM.forTest_simpleGetDevice()->getName(), "goodOEM");

    // Verify that we get an error if no driver can run an OEM operation.
    const auto devicesNoOEM = makeDevices({{"noOEM", 0.5, ~0U, PartitioningDriver::OEMNo}});
    PartitioningCompilation compilationNoOEM(&model, devicesNoOEM);
    ASSERT_EQ(compilationNoOEM.finish(), Result::BAD_DATA);

    // Verify that we get an error if a driver can SUPPORT but not PREPARE an OEM operation.
    const auto devicesIndecisiveOEM =
            makeDevices({{"indecisiveOEM", 0.5, ~0U, PartitioningDriver::OEMIndecisive}});
    PartitioningCompilation compilationIndecisiveOEM(&model, devicesIndecisiveOEM);
    ASSERT_NE(compilationIndecisiveOEM.finish(), Result::NO_ERROR);

    // Verify that we get an error if there are no drivers (only CPU fallback).
    PartitioningCompilation compilationNoDrivers(&model, makeDevices({}) /* no drivers */);
    ASSERT_EQ(compilationNoDrivers.finish(), Result::BAD_DATA);
}

TEST_F(PartitioningTest, RelaxedFP) {
    const auto devices = makeDevices({// Best choice for non-relaxed model.
                                      {"f32", 0.8, 0.9 /* relaxed */, ~0U},
                                      // Best choice for relaxed model.
                                      {"f16", 0.9, 0.8 /* relaxed */, ~0U}});

    auto TrivialTest = [&devices](bool doRelax, const char* expectDevice) {
        // Trivial model consisting solely of one operation.
        SCOPED_TRACE(expectDevice);
        PartitioningModel model;
        uint32_t opnd0 = model.addFloatOperand();
        uint32_t opnd1 = model.addFloatOperand();
        uint32_t opnd2 = model.addOperation2To1V1_0(0, opnd0, opnd1);
        model.identifyInputsAndOutputs({opnd0, opnd1}, {opnd2});
        model.relaxComputationFloat32toFloat16(doRelax);
        model.finish();
        ASSERT_TRUE(model.isValid());
        // Verify that the model will be executed on the appropriate device.
        // No need to compare the original model to the model from the plan -- we
        // didn't actually do any partitioning.
        ExecutionPlan plan;
        ASSERT_EQ(model.partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                         ExecutePriority::DEFAULT, {}, &plan),
                  ANEURALNETWORKS_NO_ERROR);
        EXPECT_TRUE(plan.forTest_flatGetDynamicTemporaries().empty());
        ASSERT_EQ(plan.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
        ASSERT_EQ(plan.forTest_simpleGetDevice()->getName(), expectDevice);
    };

    ASSERT_NO_FATAL_FAILURE(TrivialTest(false, "f32"));
    ASSERT_NO_FATAL_FAILURE(TrivialTest(true, "f16"));
}

TEST_F(PartitioningTest, Perf) {
    // The various type names used here are confusing.
    //
    // OperandType (from HAL file), WrapperType (from NeuralNetworksWrapper.h),
    // and OperandCode (from NeuralNetworks.h) are different enums representing
    // the same type kind -- e.g., OperandType::FLOAT32, WrapperType::FLOAT32,
    // ANEURALNETWORKS_FLOAT32.  Corresponding enumerators have the same value.
    //
    // WrapperOperandType is the NeuralNetworksWrapper.h representation of a
    // full operand type (WrapperType plus dimensions plus other attributes).

    auto TestType = [](V1_3::OperandType operandType) {
        if (operandType == V1_3::OperandType::SUBGRAPH) {
            // SUBGRAPH capabilities are handled differently.
            return;
        }
        SCOPED_TRACE(toString(operandType));
        // Trivial model consisting solely of OEM operation.  We
        // pick OEM operation because this allows us to use
        // inputs and outputs of any number and type.
        PartitioningModel model;
        uint32_t opndIn = model.addOperand(static_cast<WrapperType>(operandType));
        uint32_t opndOut = model.addOperationOEM1To1(opndIn);
        model.identifyInputsAndOutputs({opndIn}, {opndOut});
        model.finish();
        ASSERT_TRUE(model.isValid());

        const V1_3::Capabilities baseCapabilities = ::android::nn::makeCapabilities(0.5);

        {
            // better than base
            V1_3::Capabilities goodCapabilities = baseCapabilities;
            update(&goodCapabilities, operandType, 0.25);

            const auto devices =
                    makeDevices({{"base", baseCapabilities, ~0U, PartitioningDriver::OEMYes},
                                 {"good", goodCapabilities, ~0U, PartitioningDriver::OEMYes}});

            // Verify that model will be executed on "good".
            // No need to compare the original model to the model from the plan -- we
            // didn't actually do any partitioning.
            ExecutionPlan plan;
            ASSERT_EQ(model.partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                             ExecutePriority::DEFAULT, {}, &plan),
                      ANEURALNETWORKS_NO_ERROR);
            EXPECT_TRUE(plan.forTest_flatGetDynamicTemporaries().empty());
            ASSERT_EQ(plan.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
            ASSERT_EQ(plan.forTest_simpleGetDevice()->getName(), "good");
        }

        {
            // worse than base
            V1_3::Capabilities badCapabilities = baseCapabilities;
            update(&badCapabilities, operandType, 0.75);
            const auto devices =
                    makeDevices({{"base", baseCapabilities, ~0U, PartitioningDriver::OEMYes},
                                 {"bad", badCapabilities, ~0U, PartitioningDriver::OEMYes}});

            // Verify that model will be executed on "base".
            // No need to compare the original model to the model from the plan -- we
            // didn't actually do any partitioning.
            ExecutionPlan plan;
            ASSERT_EQ(model.partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                             ExecutePriority::DEFAULT, {}, &plan),
                      ANEURALNETWORKS_NO_ERROR);
            EXPECT_TRUE(plan.forTest_flatGetDynamicTemporaries().empty());
            ASSERT_EQ(plan.forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
            ASSERT_EQ(plan.forTest_simpleGetDevice()->getName(), "base");
        }
    };

    for (uint32_t type = static_cast<uint32_t>(V1_3::OperandTypeRange::FUNDAMENTAL_MIN);
         type <= static_cast<uint32_t>(V1_3::OperandTypeRange::FUNDAMENTAL_MAX); ++type) {
        TestType(static_cast<V1_3::OperandType>(type));
    }
    for (uint32_t type = static_cast<uint32_t>(V1_3::OperandTypeRange::OEM_MIN);
         type <= static_cast<uint32_t>(V1_3::OperandTypeRange::OEM_MAX); ++type) {
        TestType(static_cast<V1_3::OperandType>(type));
    }
}

TEST_F(PartitioningTest, ZeroInputStepModel) {
    PartitioningModel model;
    const uint32_t opnd0 = model.addFloatZeroOperand();
    const uint32_t opnd1 = model.addOperation1To1V1_3(0, opnd0);
    const uint32_t opnd2 = model.addFloatOperand();
    const uint32_t opnd3 = model.addOperation2To1V1_0(1, opnd1, opnd2);
    model.identifyInputsAndOutputs({opnd2}, {opnd3});
    ASSERT_EQ(model.finish(), Result::NO_ERROR);

    // This will result in 2 partitions: deviceA handles op0, deviceB handles op1.
    // The partition for deviceA does not have any model input, and should result in full CPU
    // fallback.
    const auto devices = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});
    PartitioningCompilation compilation(&model, devices);
    ASSERT_EQ(compilation.finish(), Result::NO_ERROR);
    const auto& cpuDeviceName = DeviceManager::getCpuDevice()->getName();
    checkExecutionPlanSteps(compilation.getExecutionPlan(), {cpuDeviceName});
}

TEST_F(PartitioningTest, ZeroOutputStepModel) {
    PartitioningModel model;
    const uint32_t opnd0 = model.addFloatOperand();
    const uint32_t opnd1 = model.addOperation1To1V1_3(0, opnd0);
    const uint32_t opnd2 = model.addFloatOperand();
    model.addOperation2To1V1_0(1, opnd1, opnd2);
    model.identifyInputsAndOutputs({opnd0, opnd2}, {opnd1});
    ASSERT_EQ(model.finish(), Result::NO_ERROR);

    // This will result in 2 partitions: deviceA handles op0, deviceB handles op1.
    // The partition for deviceB does not have any model output, and should result in full CPU
    // fallback.
    const auto devices = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});
    PartitioningCompilation compilation(&model, devices);
    ASSERT_EQ(compilation.finish(), Result::NO_ERROR);
    const auto& cpuDeviceName = DeviceManager::getCpuDevice()->getName();
    checkExecutionPlanSteps(compilation.getExecutionPlan(), {cpuDeviceName});
}

// Test dynamic temporaries and related parts of the partitioning implementation.
//
// opnd0 = model input                   // tensor to pad
// opnd1 = model input                   // padding
// opnd2 = PAD(opnd1, opnd0)             // model output
// opnd3 = PAD(opnd1, opnd0)
// opnd4 = ADD(opnd2, opnd3, FUSED_NONE) // model output
class DynamicTemporariesTest : public PartitioningTest {
   protected:
    // Call these functions in sequence in order to perform the test.
    // Call to declareOutputDimensions() can be omitted (see the default values below).
    // Call to declareHalVersions() can be omitted (defaults to HalVersion::LATEST).
    void declareOutputDimensions(bool opnd2ModelAndPartitionOutputSpecified,
                                 bool opnd3PartitionOutputSpecified,
                                 bool opnd4ModelOutputSpecified);
    void declareHalVersions(HalVersion padDeviceVersion, HalVersion addDeviceVersion);
    void makeModelAndValidate();
    void compileModelAndComparePlan(bool noFallback = true);
    void executeCompilationAndCompareOutput(bool opnd2ModelOutputBigEnough,
                                            bool opnd4ModelOutputBigEnough);

    // set by declareOutputDimensions()
    bool mOpnd2ModelAndPartitionOutputSpecified = false;
    bool mOpnd3PartitionOutputSpecified = false;
    bool mOpnd4ModelOutputSpecified = false;

    // set by declareHalVersions()
    HalVersion mPadDeviceVersion = HalVersion::LATEST;
    HalVersion mAddDeviceVersion = HalVersion::LATEST;
    HalVersion mMinDeviceVersion = HalVersion::LATEST;  // minimum of the other two device versions

    // created by makeModelAndValidate()
    std::optional<PartitioningModel> mModel;
    std::vector<uint32_t> mOpnds;

    // created by compileModelAndComparePlan();
    std::optional<PartitioningCompilation> mCompilation;

    static bool supportsOutputOfUnknownRank(HalVersion version) {
        return version >= HalVersion::V1_2;
    }

    static Dimensioned dimensionedOutput(HalVersion version, bool specified) {
        return specified ? Dimensioned::YES_4
                         : supportsOutputOfUnknownRank(version) ? Dimensioned::NO
                                                                : Dimensioned::RANK_1;
    }
};

void DynamicTemporariesTest::declareOutputDimensions(bool opnd2ModelAndPartitionOutputSpecified,
                                                     bool opnd3PartitionOutputSpecified,
                                                     bool opnd4ModelOutputSpecified) {
    ASSERT_FALSE(mModel.has_value());
    mOpnd2ModelAndPartitionOutputSpecified = opnd2ModelAndPartitionOutputSpecified;
    mOpnd3PartitionOutputSpecified = opnd3PartitionOutputSpecified;
    mOpnd4ModelOutputSpecified = opnd4ModelOutputSpecified;
}

void DynamicTemporariesTest::declareHalVersions(HalVersion padDeviceVersion,
                                                HalVersion addDeviceVersion) {
    ASSERT_FALSE(mModel.has_value());
    mPadDeviceVersion = padDeviceVersion;
    mAddDeviceVersion = addDeviceVersion;
    mMinDeviceVersion = min(padDeviceVersion, addDeviceVersion);
}

void DynamicTemporariesTest::makeModelAndValidate() {
    ASSERT_FALSE(mModel.has_value());
    mModel = PartitioningModel();

    uint32_t opndActivation = mModel->addIntScalarOperand(ANEURALNETWORKS_FUSED_NONE);

    uint32_t opnd0 = mModel->addFloatOperand(Dimensioned::YES_2);  // tensor to pad
    uint32_t opnd1 = mModel->addIntOperand(Dimensioned::RANK_2);   // paddings
    uint32_t opnd2 = mModel->addExplicitOperationXTo1(
            ANEURALNETWORKS_PAD, {opnd0, opnd1}, WrapperType::TENSOR_FLOAT32,
            dimensionedOutput(mMinDeviceVersion, mOpnd2ModelAndPartitionOutputSpecified));
    uint32_t opnd3 = mModel->addExplicitOperationXTo1(
            ANEURALNETWORKS_PAD, {opnd0, opnd1}, WrapperType::TENSOR_FLOAT32,
            dimensionedOutput(mMinDeviceVersion, mOpnd3PartitionOutputSpecified));
    uint32_t opnd4 = mModel->addExplicitOperationXTo1(
            ANEURALNETWORKS_ADD, {opnd2, opnd3, opndActivation}, WrapperType::TENSOR_FLOAT32,
            dimensionedOutput(mMinDeviceVersion, mOpnd4ModelOutputSpecified));
    mModel->identifyInputsAndOutputs({opnd0, opnd1}, {opnd2, opnd4});
    mModel->finish();
    ASSERT_TRUE(mModel->isValid());

    mOpnds = {opnd0, opnd1, opnd2, opnd3, opnd4};
}

void DynamicTemporariesTest::compileModelAndComparePlan(bool noFallback) {
    ASSERT_TRUE(mModel.has_value());
    ASSERT_TRUE(!mCompilation.has_value());

    auto devices = makeDevices({{"pad",
                                 0.9,
                                 0U,
                                 PartitioningDriver::OEMNo,
                                 mPadDeviceVersion,
                                 {V1_3::OperationType::PAD}},
                                {"add",
                                 0.9,
                                 0U,
                                 PartitioningDriver::OEMNo,
                                 mAddDeviceVersion,
                                 {V1_3::OperationType::ADD}}});

    mCompilation = PartitioningCompilation(&mModel.value(), devices);
    ASSERT_EQ(mCompilation->setPartitioning(DeviceManager::kPartitioningWithoutFallback),
              Result::NO_ERROR);
    if (noFallback) {
        ASSERT_EQ(mCompilation->finish(), Result::NO_ERROR);
        const ExecutionPlan& planA = mCompilation->getExecutionPlan();
        EXPECT_TRUE(planA.forTest_flatGetDynamicTemporaries() ==
                    (mOpnd3PartitionOutputSpecified ? DynamicTemporariesType{}
                                                    : DynamicTemporariesType{mOpnds[3]}));
        ASSERT_EQ(planA.forTest_getKind(), ExecutionPlan::Kind::COMPOUND);
        const auto& stepsA = planA.forTest_compoundGetSteps();
        ASSERT_EQ(stepsA.size(), size_t(2));
        {
            // Build a model to compare against the step model from stepsA[0].
            PartitioningModel modelA0;
            uint32_t a0Opnd0 = modelA0.addFloatOperand(Dimensioned::YES_2);
            uint32_t a0Opnd1 = modelA0.addIntOperand(Dimensioned::RANK_2);
            uint32_t a0Opnd2 = modelA0.addExplicitOperationXTo1(
                    ANEURALNETWORKS_PAD, {a0Opnd0, a0Opnd1}, WrapperType::TENSOR_FLOAT32,
                    dimensionedOutput(mMinDeviceVersion, mOpnd3PartitionOutputSpecified));
            uint32_t a0Opnd3 = modelA0.addExplicitOperationXTo1(
                    ANEURALNETWORKS_PAD, {a0Opnd0, a0Opnd1}, WrapperType::TENSOR_FLOAT32,
                    dimensionedOutput(mMinDeviceVersion, mOpnd2ModelAndPartitionOutputSpecified));
            modelA0.identifyInputsAndOutputs({a0Opnd0, a0Opnd1}, {a0Opnd3, a0Opnd2});
            modelA0.finish();
            ASSERT_TRUE(modelA0.isValid());

            ASSERT_NO_FATAL_FAILURE(compare(
                    stepsA[0], &modelA0, devices[0],
                    RemapVectorType{{mOpnds[0], a0Opnd0}, {mOpnds[1], a0Opnd1}},  // modelInputs
                    RemapVectorType{{mOpnds[2], a0Opnd3}},                        // modelOutputs
                    RemapVectorType{},                             // tempsAsStepModelInputs
                    StepModelOutputSetType{{mOpnds[3], a0Opnd2}},  // tempsAsStepModelOutputs
                    RemapVectorType{},                             // outputsAsStepModelInputs
                    {0u}));  // modelOutputsThatAreDownstreamInputs
        }
        {
            // Build a model to compare against the step model from stepsA[1].
            PartitioningModel modelA1;
            uint32_t a1Opnd2 = modelA1.addFloatOperand(
                    dimensionedOutput(mMinDeviceVersion, mOpnd2ModelAndPartitionOutputSpecified));
            uint32_t a1Opnd3 = modelA1.addFloatOperand(
                    dimensionedOutput(mMinDeviceVersion, mOpnd3PartitionOutputSpecified));
            uint32_t a1Opnd4 = modelA1.addOperation2To1V1_0(
                    0, a1Opnd2, a1Opnd3,
                    dimensionedOutput(mMinDeviceVersion, mOpnd4ModelOutputSpecified));
            modelA1.identifyInputsAndOutputs({a1Opnd3, a1Opnd2}, {a1Opnd4});
            modelA1.finish();
            ASSERT_TRUE(modelA1.isValid());

            ASSERT_NO_FATAL_FAILURE(
                    compare(stepsA[1], &modelA1, devices[1], RemapVectorType{},  // modelInputs
                            RemapVectorType{{mOpnds[4], a1Opnd4}},               // modelOutputs
                            RemapVectorType{{mOpnds[3], a1Opnd3}},  // tempsAsStepModelInputs
                            StepModelOutputSetType{},               // tempsAsStepModelOutputs
                            RemapVectorType{{mOpnds[2], a1Opnd2}},  // outputsAsStepModelInputs
                            {}));  // modelOutputsThatAreDownstreamInputs
        }
    } else {
        ASSERT_EQ(mCompilation->finish(), Result::OP_FAILED);
        // Try again, expecting fallback.
        mCompilation = PartitioningCompilation(&mModel.value(), devices);
        ASSERT_EQ(mCompilation->setPartitioning(DeviceManager::kPartitioningWithFallback),
                  Result::NO_ERROR);
        ASSERT_EQ(mCompilation->finish(), Result::NO_ERROR);
        ASSERT_EQ(mCompilation->getExecutionPlan().forTest_getKind(), ExecutionPlan::Kind::SIMPLE);
        ASSERT_EQ(mCompilation->getExecutionPlan().forTest_simpleGetDevice(),
                  DeviceManager::getCpuDevice());
    }
}

void DynamicTemporariesTest::executeCompilationAndCompareOutput(bool opnd2ModelOutputBigEnough,
                                                                bool opnd4ModelOutputBigEnough) {
    ASSERT_TRUE(opnd2ModelOutputBigEnough || !mOpnd2ModelAndPartitionOutputSpecified);
    ASSERT_TRUE(opnd4ModelOutputBigEnough || !mOpnd4ModelOutputSpecified);

    ASSERT_TRUE(mCompilation.has_value());
    WrapperExecution e(&mCompilation.value());

    WrapperOperandType padTensorValueType(WrapperType::TENSOR_FLOAT32, {2});
    const float padTensorValue[] = {3.0f, 5.0f};
    e.setInput(0, &padTensorValue, &padTensorValueType.operandType);

    WrapperOperandType paddingsType(WrapperType::TENSOR_INT32, {1, 2});
    const int paddings[1][2] = {{1, 1}};
    e.setInput(1, &paddings, &paddingsType.operandType);

    auto setOutput = [&e](uint32_t index, float* buffer, bool bigEnough, bool specified,
                          HalVersion version) {
        const uint32_t elts = bigEnough ? 4 : 3;
        std::fill(buffer, buffer + elts, -1.0f);
        using DimsType = std::vector<uint32_t>;
        WrapperOperandType outputType(
                WrapperType::TENSOR_FLOAT32,
                specified ? DimsType{elts}
                          : supportsOutputOfUnknownRank(version) ? DimsType{} : DimsType{0});
        e.setOutput(index, buffer, elts * sizeof(float), &outputType.operandType);
    };
    float opnd2ModelOutput[4], opnd4ModelOutput[4];
    setOutput(0, opnd2ModelOutput, opnd2ModelOutputBigEnough,
              mOpnd2ModelAndPartitionOutputSpecified, mPadDeviceVersion);
    setOutput(1, opnd4ModelOutput, opnd4ModelOutputBigEnough, mOpnd4ModelOutputSpecified,
              mAddDeviceVersion);

    const Result expectResult = opnd2ModelOutputBigEnough && opnd4ModelOutputBigEnough
                                        ? Result::NO_ERROR
                                        : Result::OUTPUT_INSUFFICIENT_SIZE;
    ASSERT_EQ(e.compute(), expectResult);
    if (expectResult == Result::NO_ERROR) {
        float expected[4] = {0.0f, padTensorValue[0], padTensorValue[1], 0.0f};
        ASSERT_TRUE(std::equal(std::begin(opnd2ModelOutput), std::end(opnd2ModelOutput),
                               std::begin(expected)));
        for (auto& elt : expected) {
            elt *= 2;
        }
        ASSERT_TRUE(std::equal(std::begin(opnd4ModelOutput), std::end(opnd4ModelOutput),
                               std::begin(expected)));
    }
}

TEST_F(DynamicTemporariesTest, ModelOutputsSufficientSize) {
    // The purpose of this test is to confirm that the partitioner and the
    // runtime can handle a model output of unspecified dimensions but
    // sufficient size that is written by one partition and read by another.

    ASSERT_NO_FATAL_FAILURE(declareOutputDimensions(/*opnd2ModelAndPartitionOutputSpecified=*/false,
                                                    /*opnd3PartitionOutputSpecified=*/true,
                                                    /*opnd4ModelOutputSpecified=*/false));
    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(true, true));
}

// TODO(b/174851714): Fix the partitioner and re-enable this test.
TEST_F(DynamicTemporariesTest, DISABLED_ModelOutputsSufficientSize_V1_1) {
    // The purpose of this test is to confirm that the partitioner and the
    // runtime can handle a model output of unspecified dimensions but
    // sufficient size that is written by one partition and read by another.
    // Regression test for http://b/174851714.

    ASSERT_NO_FATAL_FAILURE(declareOutputDimensions(/*opnd2ModelAndPartitionOutputSpecified=*/false,
                                                    /*opnd3PartitionOutputSpecified=*/true,
                                                    /*opnd4ModelOutputSpecified=*/false));
    ASSERT_NO_FATAL_FAILURE(declareHalVersions(/*padDeviceVersion=*/HalVersion::V1_1,
                                               /*addDeviceVersion=*/HalVersion::V1_1));
    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(true, true));
}

TEST_F(DynamicTemporariesTest, DynamicTemporariesUnspecifiedOutputs) {
    // The purpose of this test is to confirm that the partitioner can produce
    // dynamic temporaries and that the runtime can handle them properly.  Note
    // that all model outputs are of unspecified dimensions but sufficient size.

    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(true, true));
}

TEST_F(DynamicTemporariesTest, DynamicTemporariesSpecifiedOutputs) {
    // The purpose of this test is to confirm that the partitioner can produce
    // dynamic temporaries and that the runtime can handle them properly.  Note
    // that all model outputs are of specified dimensions.

    ASSERT_NO_FATAL_FAILURE(declareOutputDimensions(/*opnd2ModelAndPartitionOutputSpecified=*/true,
                                                    /*opnd3PartitionOutputSpecified=*/false,
                                                    /*opnd4ModelOutputSpecified=*/true));
    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(true, true));
}

TEST_F(DynamicTemporariesTest, DynamicTemporariesSpecifiedOutputs_V1_2) {
    // The purpose of this test is to confirm that the partitioner can produce
    // dynamic temporaries and that the runtime can handle them properly.  Note
    // that all model outputs are of specified dimensions.
    // Regression test for http://b/174851714.

    ASSERT_NO_FATAL_FAILURE(declareOutputDimensions(/*opnd2ModelAndPartitionOutputSpecified=*/true,
                                                    /*opnd3PartitionOutputSpecified=*/false,
                                                    /*opnd4ModelOutputSpecified=*/true));
    ASSERT_NO_FATAL_FAILURE(declareHalVersions(/*padDeviceVersion=*/HalVersion::V1_2,
                                               /*addDeviceVersion=*/HalVersion::V1_2));
    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(true, true));
}

TEST_F(DynamicTemporariesTest, DynamicTemporariesSpecifiedOutputs_V1_1) {
    // The purpose of this test is to confirm that the partitioner cannot produce
    // dynamic temporaries for V1_1 but instead does whole-model CPU fallback.  Note
    // that all model outputs are of specified dimensions.
    // Regression test for http://b/174851714.

    ASSERT_NO_FATAL_FAILURE(declareOutputDimensions(/*opnd2ModelAndPartitionOutputSpecified=*/true,
                                                    /*opnd3PartitionOutputSpecified=*/false,
                                                    /*opnd4ModelOutputSpecified=*/true));
    ASSERT_NO_FATAL_FAILURE(declareHalVersions(/*padDeviceVersion=*/HalVersion::V1_1,
                                               /*addDeviceVersion=*/HalVersion::V1_1));
    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan(false));
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(true, true));
}

TEST_F(DynamicTemporariesTest, ModelOutputsInsufficientSizeWithDynamicTemporary) {
    // The purpose of this test is to confirm that the runtime can detect a
    // model output of insufficient size in the presence of a dynamic temporary.

    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(false, false));
}

TEST_F(DynamicTemporariesTest, ModelOutputsInsufficientSizeWithoutDynamicTemporary) {
    // The purpose of this test is to confirm that the runtime can detect a
    // model output of insufficient size in the absence of a dynamic temporary.

    ASSERT_NO_FATAL_FAILURE(declareOutputDimensions(/*opnd2ModelAndPartitionOutputSpecified=*/false,
                                                    /*opnd3PartitionOutputSpecified=*/true,
                                                    /*opnd4ModelOutputSpecified=*/false));
    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(false, false));
}

TEST_F(DynamicTemporariesTest, ModelOutput2InsufficientSizeWithoutDynamicTemporary) {
    // The purpose of this test is to confirm that the runtime can detect a
    // model output of insufficient size in the absence of a dynamic temporary.

    ASSERT_NO_FATAL_FAILURE(declareOutputDimensions(/*opnd2ModelAndPartitionOutputSpecified=*/false,
                                                    /*opnd3PartitionOutputSpecified=*/true,
                                                    /*opnd4ModelOutputSpecified=*/false));
    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(false, true));
}

TEST_F(DynamicTemporariesTest, ModelOutput4InsufficientSizeWithoutDynamicTemporary) {
    // The purpose of this test is to confirm that the runtime can detect a
    // model output of insufficient size in the absence of a dynamic temporary.

    ASSERT_NO_FATAL_FAILURE(declareOutputDimensions(/*opnd2ModelAndPartitionOutputSpecified=*/false,
                                                    /*opnd3PartitionOutputSpecified=*/true,
                                                    /*opnd4ModelOutputSpecified=*/false));
    ASSERT_NO_FATAL_FAILURE(makeModelAndValidate());
    ASSERT_NO_FATAL_FAILURE(compileModelAndComparePlan());
    ASSERT_NO_FATAL_FAILURE(executeCompilationAndCompareOutput(true, false));
}

// Test token rehashing during the compilation step.
class CacheTest : public PartitioningTest {
   protected:
    virtual void SetUp() override {
        PartitioningTest::SetUp();
        char cacheDirTemp[] = NN_TMP_DIR "/TestCompilationCachingXXXXXX";
        char* cacheDir = mkdtemp(cacheDirTemp);
        ASSERT_NE(cacheDir, nullptr);
        mCacheDir = cacheDir;
    }

    virtual void TearDown() override {
        if (!::testing::Test::HasFailure()) {
            std::filesystem::remove_all(mCacheDir);
        }
        PartitioningTest::TearDown();
    }

    void expectUniqueTokens(const std::vector<std::vector<uint8_t>>& tokens) {
        for (uint32_t i = 0; i < tokens.size(); i++) {
            SCOPED_TRACE(i);
            for (uint32_t j = i + 1; j < tokens.size(); j++) {
                SCOPED_TRACE(j);
                EXPECT_NE(tokens[i], tokens[j]);
            }
        }
    }

    // Launch a single run of the partitioner against the provided model and device list with
    // cache token privided as tokenIn. Find the partition for the device with deviceName.
    // Record the transformed token into tokenOut. Two or more partitions may be on the same device.
    // "devicePartitionIndex" specifies the index of the ExecutionStep corresponding to the
    // partition of interest, within the sequence of ExecutionSteps on the target device.
    // If tokenIn is empty, no caching information will be provided to the partitioner.
    void getTransformedCacheTokenSingle(const PartitioningModel& model,
                                        const std::vector<std::shared_ptr<Device>>& devices,
                                        const char* deviceName, const std::vector<uint8_t>& tokenIn,
                                        ExecutePreference preference, ExecutePriority priority,
                                        uint32_t devicePartitionIndex,
                                        std::vector<uint8_t>* tokenOut) {
        // Compile the model and get the execution plan.
        PartitioningCompilation compilation(&model, devices);
        if (!tokenIn.empty()) {
            compilation.setCaching(mCacheDir.c_str(), tokenIn);
        }
        compilation.setPreference(preference);
        compilation.setPriority(priority);
        ASSERT_EQ(compilation.finish(), Result::NO_ERROR);
        const ExecutionPlan& plan = compilation.getExecutionPlan();

        // Find the cache info for the device.
        const uint8_t* token = nullptr;
        if (plan.forTest_getKind() == ExecutionPlan::Kind::SIMPLE) {
            ASSERT_EQ(devicePartitionIndex, 0u);
            ASSERT_EQ(plan.forTest_simpleGetDevice()->getName(), deviceName);
            token = plan.forTest_simpleGetCacheToken();
        } else if (plan.forTest_getKind() == ExecutionPlan::Kind::COMPOUND) {
            const auto& steps = plan.forTest_compoundGetSteps();
            uint32_t executionStepCount = 0;
            for (const auto& step : steps) {
                if (step->isExecution() &&
                    step->executionStep()->getDevice()->getName() == deviceName) {
                    if (devicePartitionIndex == executionStepCount) {
                        token = step->executionStep()->forTest_getCacheToken();
                        break;
                    }
                    executionStepCount++;
                }
            }
        } else {
            FAIL();
        }

        // Retrieve the transformed token from the cache info.
        if (token == nullptr) {
            tokenOut->clear();
        } else {
            tokenOut->resize(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN);
            std::copy(token, token + ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, tokenOut->begin());
        }
    }

    // A wrapper of getTransformedCacheTokenSingle, which runs getTransformedCacheTokenSingle
    // multiple times and checks if the transformation provides consistent result.
    // Two or more partitions may be on the same device. "devicePartitionIndex" specifies the index
    // of the ExecutionStep corresponding to the partition of interest, within the sequence of
    // ExecutionSteps on the target device.
    void getTransformedCacheToken(const PartitioningModel& model,
                                  const std::vector<std::shared_ptr<Device>>& devices,
                                  const char* deviceName, const std::vector<uint8_t>& tokenIn,
                                  ExecutePreference preference, ExecutePriority priority,
                                  std::vector<uint8_t>* tokenOut,
                                  uint32_t devicePartitionIndex = 0) {
        getTransformedCacheTokenSingle(model, devices, deviceName, tokenIn, preference, priority,
                                       devicePartitionIndex, tokenOut);

        // Test if the runtime maps to the same cache token every time for the same compilation
        // setup.
        for (uint32_t i = 0; i < 10; i++) {
            std::vector<uint8_t> token;
            SCOPED_TRACE(i);
            getTransformedCacheTokenSingle(model, devices, deviceName, tokenIn, preference,
                                           priority, devicePartitionIndex, &token);
            EXPECT_EQ(*tokenOut, token);
        }
    }

    void createModelForCachingTests(PartitioningModel* model) {
        uint32_t opnd0 = model->addFloatOperand();
        uint32_t opnd1 = model->addFloatOperand();
        uint32_t opnd2 = model->addOperation2To1V1_0(0, opnd0, opnd1);
        uint32_t opnd3 = model->addFloatOperand();
        uint32_t opnd4 = model->addOperation2To1V1_0(1, opnd2, opnd3);
        model->identifyInputsAndOutputs({opnd0, opnd1, opnd3}, {opnd4});
        model->finish();
        ASSERT_TRUE(model->isValid());
    }

    // The first model returned in "models" is the main model.
    void createControlFlowModelForCachingTests(
            std::vector<std::unique_ptr<PartitioningModel>>* models) {
        CHECK(models != nullptr);

        auto trueModel = std::make_unique<PartitioningModel>();
        {
            const uint32_t opnd0 = trueModel->addFloatOperand();
            const uint32_t opnd1 = trueModel->addFloatOperand();
            const uint32_t opnd2 = trueModel->addOperation2To1V1_0(0, opnd0, opnd1);
            trueModel->identifyInputsAndOutputs({opnd0, opnd1}, {opnd2});
            trueModel->finish();
            ASSERT_TRUE(trueModel->isValid());
        }

        auto falseModel = std::make_unique<PartitioningModel>();
        {
            const uint32_t opnd0 = falseModel->addFloatOperand();
            const uint32_t opnd1 = falseModel->addFloatOperand();
            const uint32_t opnd2 = falseModel->addOperation2To1V1_0(0, opnd0, opnd1);
            falseModel->identifyInputsAndOutputs({opnd0, opnd1}, {opnd2});
            falseModel->finish();
            ASSERT_TRUE(falseModel->isValid());
        }

        auto mainModel = std::make_unique<PartitioningModel>();
        {
            const uint32_t opnd0 = mainModel->addBooleanOperand();
            const uint32_t opnd1 = mainModel->addFloatOperand();
            const uint32_t opnd2 = mainModel->addFloatOperand();
            const uint32_t opnd3 = mainModel->addFloatOperand();
            mainModel->addIfOperation(opnd0, *trueModel, *falseModel, {opnd1, opnd2}, {opnd3});
            mainModel->identifyInputsAndOutputs({opnd0, opnd1, opnd2}, {opnd3});
            mainModel->finish();
            ASSERT_TRUE(mainModel->isValid());
        }

        models->clear();
        models->push_back(std::move(mainModel));
        models->push_back(std::move(trueModel));
        models->push_back(std::move(falseModel));
    }

    std::string mCacheDir;
};

// Test the case when no token is provided by the application and the execution plan has a
// simple body.
TEST_F(CacheTest, CacheTokenNoneSimpleBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // deviceA can execute the whole model.
    const auto deviceA = makeDevices({
            {"deviceA", 0.5, ~0U},
    });

    std::vector<uint8_t> tokenIn, tokenOut;
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut);
    EXPECT_TRUE(tokenOut.empty());
}

// Test if the runtime maps to different cache tokens for devices with different names in
// execution plan with a simple body.
TEST_F(CacheTest, CacheTokenDifferentDeviceNamesSimpleBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // Two devices that can both execute the whole model.
    const auto deviceA = makeDevices({{"deviceA", 0.5, ~0U}});
    const auto deviceB = makeDevices({{"deviceB", 0.5, ~0U}});

    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    std::vector<uint8_t> deviceAToken, deviceBToken;
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &deviceAToken);
    getTransformedCacheToken(model, deviceB, "deviceB", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &deviceBToken);
    expectUniqueTokens({deviceAToken, deviceBToken});
}

// Test if the runtime maps to different cache tokens for devices with different version strings in
// execution plan with a simple body.
TEST_F(CacheTest, CacheTokenDifferentDeviceVersionStringsSimpleBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // Two devices that can both execute the whole model.
    const auto deviceA_1_0 = makeDevices({{"deviceA", "1.0", 0.5, ~0U}});
    const auto deviceA_1_1 = makeDevices({{"deviceA", "1.1", 0.5, ~0U}});

    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    std::vector<uint8_t> deviceA_1_0_Token, deviceA_1_1_Token;
    getTransformedCacheToken(model, deviceA_1_0, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &deviceA_1_0_Token);
    getTransformedCacheToken(model, deviceA_1_1, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &deviceA_1_1_Token);
    expectUniqueTokens({deviceA_1_0_Token, deviceA_1_1_Token});
}

// Test if the runtime maps to different cache tokens for compilations with different preferences
// in execution plan with a simple body.
TEST_F(CacheTest, CacheTokenDifferentPreferencesSimpleBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // One device that can execute the whole model.
    const auto deviceA = makeDevices({{"deviceA", 0.5, ~0U}});

    std::vector<uint8_t> fastToken, powerToken, sustainedToken;
    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &fastToken);
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn,
                             ExecutePreference::PREFER_LOW_POWER, ExecutePriority::DEFAULT,
                             &powerToken);
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn,
                             ExecutePreference::PREFER_SUSTAINED_SPEED, ExecutePriority::DEFAULT,
                             &sustainedToken);
    expectUniqueTokens({fastToken, powerToken, sustainedToken});
}

// TODO (b/207721221): add test for AIDL compilation hints.
// Test if the runtime maps to different cache tokens for compilations with different priorities
// in execution plan with a simple body.
TEST_F(CacheTest, CacheTokenDifferentPrioritiesSimpleBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // One device that can execute the whole model.
    const auto deviceA = makeDevices({{"deviceA", 0.5, ~0U}});

    std::vector<uint8_t> lowToken, mediumToken, highToken;
    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::LOW,
                             &lowToken);
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::MEDIUM,
                             &mediumToken);
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::HIGH,
                             &highToken);
    expectUniqueTokens({lowToken, mediumToken, highToken});
}

// Test if the runtime maps to different cache tokens for compilations with different tokens
// provided by application in execution plan with a simple body.
TEST_F(CacheTest, CacheTokenDifferentTokensSimpleBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // One device that can execute the whole model.
    const auto deviceA = makeDevices({{"deviceA", 0.5, ~0U}});

    std::vector<uint8_t> tokenOut1, tokenOut2;
    std::vector<uint8_t> tokenIn1(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    std::vector<uint8_t> tokenIn2(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 1);
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn1,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut1);
    getTransformedCacheToken(model, deviceA, "deviceA", tokenIn2,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut2);
    expectUniqueTokens({tokenOut1, tokenOut2});
}

// Test the case when no token is provided by the application and the execution plan has a
// compound body.
TEST_F(CacheTest, CacheTokenNoneCompoundBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // DeviceA executes the first operation only.
    const auto devices = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});

    std::vector<uint8_t> tokenIn, tokenOut;
    getTransformedCacheToken(model, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut);
    EXPECT_TRUE(tokenOut.empty());
    getTransformedCacheToken(model, devices, "deviceB", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut);
    EXPECT_TRUE(tokenOut.empty());
}

// Test if the runtime maps to different cache tokens for devices with different names in
// execution plan with a compound body.
TEST_F(CacheTest, CacheTokenDifferentDeviceNamesCompoundBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // DeviceA executes the first operation only.
    const auto devices1 = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceC", 0.5, 1 << 1}});
    // DeviceB executes the first operation only.
    const auto devices2 = makeDevices({{"deviceB", 0.8, ~0U}, {"deviceC", 0.5, 1 << 1}});

    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    std::vector<uint8_t> deviceAToken, deviceBToken;
    getTransformedCacheToken(model, devices1, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &deviceAToken);
    getTransformedCacheToken(model, devices2, "deviceB", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &deviceBToken);
    expectUniqueTokens({deviceAToken, deviceBToken});
}

// Test if the runtime maps to different cache tokens for devices with different names in
// execution plan with a compound body.
TEST_F(CacheTest, CacheTokenDifferentDeviceVersionStringsCompoundBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // DeviceA executes the first operation only.
    const auto devices1 = makeDevices({{"deviceA", "1.0", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});
    // DeviceB executes the first operation only.
    const auto devices2 = makeDevices({{"deviceA", "1.1", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});

    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    std::vector<uint8_t> deviceA_1_0_Token, deviceA_1_1_Token;
    getTransformedCacheToken(model, devices1, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &deviceA_1_0_Token);
    getTransformedCacheToken(model, devices2, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &deviceA_1_1_Token);
    expectUniqueTokens({deviceA_1_0_Token, deviceA_1_1_Token});
}

// Test if the runtime maps to different cache tokens for compilations with different preferences
// in execution plan with a compound body.
TEST_F(CacheTest, CacheTokenDifferentPreferencesCompoundBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // DeviceA executes the first operation only.
    const auto devices = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});

    std::vector<uint8_t> fastToken, powerToken, sustainedToken;
    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    getTransformedCacheToken(model, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &fastToken);
    getTransformedCacheToken(model, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_LOW_POWER, ExecutePriority::DEFAULT,
                             &powerToken);
    getTransformedCacheToken(model, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_SUSTAINED_SPEED, ExecutePriority::DEFAULT,
                             &sustainedToken);
    expectUniqueTokens({fastToken, powerToken, sustainedToken});
}

// Test if the runtime maps to different cache tokens for compilations with different priorities
// in execution plan with a compound body.
TEST_F(CacheTest, CacheTokenDifferentPrioritiesCompoundBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // DeviceA executes the first operation only.
    const auto devices = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});

    std::vector<uint8_t> lowToken, mediumToken, highToken;
    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    getTransformedCacheToken(model, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::LOW,
                             &lowToken);
    getTransformedCacheToken(model, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::MEDIUM,
                             &mediumToken);
    getTransformedCacheToken(model, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::HIGH,
                             &highToken);
    expectUniqueTokens({lowToken, mediumToken, highToken});
}

// Test if the runtime maps to different cache tokens for compilations with different tokens
// provided by application in execution plan with a compound body.
TEST_F(CacheTest, CacheTokenDifferentTokensCompoundBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // DeviceA executes the first operation only.
    const auto devices = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});

    std::vector<uint8_t> tokenOut1, tokenOut2;
    std::vector<uint8_t> tokenIn1(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    std::vector<uint8_t> tokenIn2(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 1);
    getTransformedCacheToken(model, devices, "deviceA", tokenIn1,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut1);
    getTransformedCacheToken(model, devices, "deviceA", tokenIn2,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut2);
    expectUniqueTokens({tokenOut1, tokenOut2});
}

// Test if the runtime maps to different cache tokens for compilations with different partitioning
// outcome in execution plan with a compound body.
TEST_F(CacheTest, CacheTokenDifferentPartitionsCompoundBody) {
    PartitioningModel model;
    createModelForCachingTests(&model);

    // DeviceA executes the whole model.
    const auto devices1 = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 0U}});
    // DeviceA executes the first operation only.
    const auto devices2 = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});
    // DeviceA executes the second operation only.
    const auto devices3 = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 0}});

    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    std::vector<uint8_t> tokenOut1, tokenOut2, tokenOut3;
    getTransformedCacheToken(model, devices1, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut1);
    getTransformedCacheToken(model, devices2, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut2);
    getTransformedCacheToken(model, devices3, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut3);
    expectUniqueTokens({tokenOut1, tokenOut2, tokenOut3});
}

// Test if the runtime maps different referenced models to different cache tokens.
TEST_F(CacheTest, CacheTokenDifferentReferenceModelPartitions) {
    std::vector<std::unique_ptr<PartitioningModel>> models;
    createControlFlowModelForCachingTests(&models);
    const auto& main = *models[0];

    // DeviceA executes the two referenced models but does not support IF.
    // There will be two partitions on deviceA.
    const auto devices = makeDevices({{"deviceA", 0.8, ~0U}});

    std::vector<uint8_t> tokenIn(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
    std::vector<uint8_t> tokenOut1, tokenOut2;
    getTransformedCacheToken(main, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut1, /*devicePartitionIndex=*/0);
    getTransformedCacheToken(main, devices, "deviceA", tokenIn,
                             ExecutePreference::PREFER_FAST_SINGLE_ANSWER, ExecutePriority::DEFAULT,
                             &tokenOut2, /*devicePartitionIndex=*/1);
    expectUniqueTokens({tokenOut1, tokenOut2});
}

// Very basic tests of some of the PerformanceInfo functionality.
// Placed in this file because partitioning is the consumer of this functionality.
class PerfTest : public ::testing::Test {};

TEST_F(PerfTest, Lookup) {
    // Derive an arbitrary (but reproducible) performance value from an OperandType.
    // We'll use this to ensure that we can save and then recover a type's performance.
    auto typePerf = [](V1_3::OperandType type) { return float(static_cast<uint32_t>(type)); };

    V1_3::Capabilities capabilities = ::android::nn::makeCapabilities(-1.0f);

    for (uint32_t type = static_cast<uint32_t>(V1_3::OperandTypeRange::FUNDAMENTAL_MIN);
         type <= static_cast<uint32_t>(V1_3::OperandTypeRange::FUNDAMENTAL_MAX); ++type) {
        V1_3::OperandType operandType = static_cast<V1_3::OperandType>(type);
        update(&capabilities, operandType, typePerf(operandType));
    }
    for (uint32_t type = static_cast<uint32_t>(V1_3::OperandTypeRange::OEM_MIN);
         type <= static_cast<uint32_t>(V1_3::OperandTypeRange::OEM_MAX); ++type) {
        V1_3::OperandType operandType = static_cast<V1_3::OperandType>(type);
        update(&capabilities, operandType, typePerf(operandType));
    }

    // Make sure lookup retrieves the values stored by update

    for (uint32_t type = static_cast<uint32_t>(V1_3::OperandTypeRange::FUNDAMENTAL_MIN);
         type <= static_cast<uint32_t>(V1_3::OperandTypeRange::FUNDAMENTAL_MAX); ++type) {
        V1_3::OperandType operandType = static_cast<V1_3::OperandType>(type);
        if (operandType == V1_3::OperandType::SUBGRAPH) {
            // SUBGRAPH capabilities are handled differently.
            continue;
        }
        SCOPED_TRACE(toString(operandType));
        EXPECT_EQ(lookupExecTime(capabilities, operandType), typePerf(operandType));
    }
    for (uint32_t type = static_cast<uint32_t>(V1_3::OperandTypeRange::OEM_MIN);
         type <= static_cast<uint32_t>(V1_3::OperandTypeRange::OEM_MAX); ++type) {
        V1_3::OperandType operandType = static_cast<V1_3::OperandType>(type);
        SCOPED_TRACE(toString(operandType));
        EXPECT_EQ(lookupExecTime(capabilities, operandType), typePerf(operandType));
    }

    // Check the behavior of a missing type

    V1_3::OperandType operandType = static_cast<V1_3::OperandType>(
            static_cast<uint32_t>(V1_3::OperandTypeRange::BASE_MAX) + 1);
    EXPECT_EQ(lookupExecTime(capabilities, operandType), FLT_MAX);
}

class ControlFlowPartitioningTest : public PartitioningTest {
   protected:
    // opnd0 --> +-----+
    //           | op0 | --> opnd2
    // opnd1 --> +-----+
    std::unique_ptr<PartitioningModel> createBranchOrBodyModel(Dimensioned dimensioned) {
        auto model = std::make_unique<PartitioningModel>();
        const uint32_t opnd0 = model->addFloatOperand(dimensioned);
        const uint32_t opnd1 = model->addFloatOperand(dimensioned);
        const uint32_t opnd2 = model->addOperation2To1V1_0(0, opnd0, opnd1, dimensioned);
        model->identifyInputsAndOutputs({opnd0, opnd1}, {opnd2});
        model->finish();
        EXPECT_TRUE(model->isValid());
        return model;
    }

    // opnd0 --> +-------+
    //           | EQUAL | --> opnd2
    // opnd1 --> +-------+
    std::unique_ptr<PartitioningModel> createCondModel(Dimensioned dimensioned) {
        auto model = std::make_unique<PartitioningModel>();
        const uint32_t opnd0 = model->addFloatOperand(dimensioned);
        const uint32_t opnd1 = model->addFloatOperand(dimensioned);
        const uint32_t opnd2 = model->addExplicitOperationXTo1(
                ANEURALNETWORKS_EQUAL, {opnd0, opnd1}, WrapperType::TENSOR_BOOL8);
        model->identifyInputsAndOutputs({opnd0, opnd1}, {opnd2});
        model->finish();
        EXPECT_TRUE(model->isValid());
        return model;
    }

    // opnd0 --> +----+
    // opnd1 --> | IF | --> opnd3
    // opnd2 --> +----+
    std::vector<std::unique_ptr<PartitioningModel>> createIfModel(
            Dimensioned dimensionedMain = Dimensioned::YES,
            Dimensioned dimensionedThen = Dimensioned::YES,
            Dimensioned dimensionedElse = Dimensioned::YES) {
        auto thenModel = createBranchOrBodyModel(dimensionedThen);
        auto elseModel = createBranchOrBodyModel(dimensionedElse);

        auto mainModel = std::make_unique<PartitioningModel>();
        const uint32_t opnd0 = mainModel->addBooleanOperand();
        const uint32_t opnd1 = mainModel->addFloatOperand(dimensionedMain);
        const uint32_t opnd2 = mainModel->addFloatOperand(dimensionedMain);
        const uint32_t opnd3 = mainModel->addFloatOperand(dimensionedMain);
        mainModel->addIfOperation(opnd0, *thenModel, *elseModel, {opnd1, opnd2}, {opnd3});
        mainModel->identifyInputsAndOutputs({opnd0, opnd1, opnd2}, {opnd3});
        mainModel->finish();
        EXPECT_TRUE(mainModel->isValid());

        std::vector<std::unique_ptr<PartitioningModel>> models;
        models.push_back(std::move(mainModel));
        models.push_back(std::move(thenModel));
        models.push_back(std::move(elseModel));
        return std::move(models);
    }

    // opnd0 --> +-------+
    //           | WHILE | --> opnd2
    // opnd1 --> +-------+
    std::vector<std::unique_ptr<PartitioningModel>> createWhileModel(
            Dimensioned dimensionedMain = Dimensioned::YES,
            Dimensioned dimensionedCond = Dimensioned::YES,
            Dimensioned dimensionedBody = Dimensioned::YES) {
        auto condModel = createCondModel(dimensionedCond);
        auto bodyModel = createBranchOrBodyModel(dimensionedBody);

        auto mainModel = std::make_unique<PartitioningModel>();
        const uint32_t opnd0 = mainModel->addFloatOperand(dimensionedMain);
        const uint32_t opnd1 = mainModel->addFloatOperand(dimensionedMain);
        const uint32_t opnd2 = mainModel->addFloatOperand(dimensionedMain);
        mainModel->addWhileOperation(*condModel, *bodyModel, {opnd0, opnd1}, {opnd2});
        mainModel->identifyInputsAndOutputs({opnd0, opnd1}, {opnd2});
        mainModel->finish();
        EXPECT_TRUE(mainModel->isValid());

        std::vector<std::unique_ptr<PartitioningModel>> models;
        models.push_back(std::move(mainModel));
        models.push_back(std::move(condModel));
        models.push_back(std::move(bodyModel));
        return std::move(models);
    }

    void testIfUnknownSize(Dimensioned dimensionedMain, Dimensioned dimensionedThen,
                           Dimensioned dimensionedElse);
    void testWhileUnknownSize(Dimensioned dimensionedMain, Dimensioned dimensionedThen,
                              Dimensioned dimensionedElse);
};

TEST_F(ControlFlowPartitioningTest, IF_Interpreted) {
    const auto models = createIfModel();

    // The device supports the referenced models but does not support IF.
    const auto devices = makeDevices({{"V1_0", 0.9, HalVersion::V1_0, ~0U}});

    ExecutionPlan plan;
    ASSERT_EQ(models[0]->partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                          ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    checkExecutionPlanSteps(plan, {kIfStep, "V1_0", kGotoStep, "V1_0"});
}

TEST_F(ControlFlowPartitioningTest, WHILE_Interpreted) {
    const auto models = createWhileModel();

    // The device supports the body model but does not support WHILE or the
    // condition model (because of EQUAL).
    const auto devices = makeDevices({{"V1_0", 0.9, HalVersion::V1_0, ~0U}});

    ExecutionPlan plan;
    ASSERT_EQ(models[0]->partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                          ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    const auto& cpuDeviceName = DeviceManager::getCpuDevice()->getName();
    checkExecutionPlanSteps(plan, {kWhileStep, cpuDeviceName, kGotoStep, "V1_0", kGotoStep});
}

TEST_F(ControlFlowPartitioningTest, IF_SimplePlan) {
    const auto models = createIfModel();

    // The device supports all operations.
    const auto devices = makeDevices({{"ALL",
                                       0.9,
                                       ~0U,
                                       PartitioningDriver::OEMNo,
                                       HalVersion::LATEST,
                                       {V1_3::OperationType::IF}}});

    ExecutionPlan plan;
    ASSERT_EQ(models[0]->partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                          ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    checkExecutionPlanSteps(plan, {"ALL"});
}

TEST_F(ControlFlowPartitioningTest, WHILE_SimplePlan) {
    const auto models = createWhileModel();

    // The device supports all operations.
    const auto devices = makeDevices({{"ALL",
                                       0.9,
                                       ~0U,
                                       PartitioningDriver::OEMNo,
                                       HalVersion::LATEST,
                                       {V1_3::OperationType::WHILE, V1_3::OperationType::EQUAL}}});

    ExecutionPlan plan;
    ASSERT_EQ(models[0]->partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                          ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    checkExecutionPlanSteps(plan, {"ALL"});
}

void ControlFlowPartitioningTest::testIfUnknownSize(Dimensioned dimensionedMain,
                                                    Dimensioned dimensionedThen,
                                                    Dimensioned dimensionedElse) {
    if (dimensionedMain != Dimensioned::NO && dimensionedThen != Dimensioned::NO &&
        dimensionedElse != Dimensioned::NO) {
        // No unknown size.
        return;
    }

    const auto models = createIfModel(dimensionedMain, dimensionedThen, dimensionedElse);

    // The device supports all operations but the partitioner ignores its IF
    // support due to http://b/159076604#comment5.
    const auto devices = makeDevices({{"ALL",
                                       0.9,
                                       ~0U,
                                       PartitioningDriver::OEMNo,
                                       HalVersion::LATEST,
                                       {V1_3::OperationType::IF}}});

    ExecutionPlan plan;
    ASSERT_EQ(models[0]->partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                          ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    // The control flow interpreter does not support unknown size (b/132458982).
    checkExecutionPlanSteps(plan, {DeviceManager::getCpuDevice()->getName()});
}

TEST_F(ControlFlowPartitioningTest, IF_UnknownSize) {
    const std::vector<Dimensioned> configurations = {Dimensioned::NO, Dimensioned::YES};
    for (Dimensioned dimensionedMain : configurations) {
        SCOPED_TRACE(testing::Message() << "dimensionedMain: " << toString(dimensionedMain));
        for (Dimensioned dimensionedThen : configurations) {
            SCOPED_TRACE(testing::Message() << "dimensionedThen: " << toString(dimensionedThen));
            for (Dimensioned dimensionedElse : configurations) {
                SCOPED_TRACE(testing::Message()
                             << "dimensionedElse: " << toString(dimensionedElse));
                testIfUnknownSize(dimensionedMain, dimensionedThen, dimensionedElse);
            }
        }
    }
}

void ControlFlowPartitioningTest::testWhileUnknownSize(Dimensioned dimensionedMain,
                                                       Dimensioned dimensionedCond,
                                                       Dimensioned dimensionedBody) {
    if (dimensionedMain != Dimensioned::NO && dimensionedCond != Dimensioned::NO &&
        dimensionedBody != Dimensioned::NO) {
        // No unknown size.
        return;
    }

    const auto models = createWhileModel(dimensionedMain, dimensionedCond, dimensionedBody);

    // The device supports all operations but the partitioner ignores its WHILE
    // support due to http://b/159076604#comment5.
    const auto devices = makeDevices({{"ALL",
                                       0.9,
                                       ~0U,
                                       PartitioningDriver::OEMNo,
                                       HalVersion::LATEST,
                                       {V1_3::OperationType::WHILE, V1_3::OperationType::EQUAL}}});

    ExecutionPlan plan;
    ASSERT_EQ(models[0]->partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                          ExecutePriority::DEFAULT, {}, &plan),
              ANEURALNETWORKS_NO_ERROR);
    // The control flow interpreter does not support unknown size (b/132458982).
    checkExecutionPlanSteps(plan, {DeviceManager::getCpuDevice()->getName()});
}

TEST_F(ControlFlowPartitioningTest, WHILE_UnknownSize) {
    const std::vector<Dimensioned> configurations = {Dimensioned::NO, Dimensioned::YES};
    for (Dimensioned dimensionedMain : configurations) {
        SCOPED_TRACE(testing::Message() << "dimensionedMain: " << toString(dimensionedMain));
        for (Dimensioned dimensionedCond : configurations) {
            SCOPED_TRACE(testing::Message() << "dimensionedCond: " << toString(dimensionedCond));
            for (Dimensioned dimensionedBody : configurations) {
                SCOPED_TRACE(testing::Message()
                             << "dimensionedBody: " << toString(dimensionedBody));
                testWhileUnknownSize(dimensionedMain, dimensionedCond, dimensionedBody);
            }
        }
    }
}

// Test the memory step role analysis of the partitioning implementation.
class MemoryStepRoleTest : public PartitioningTest {
   protected:
    // A tuple of {device_name, input/output}
    using TestStepRole = std::tuple<std::string, IOType>;

    void SetUp() override {
        PartitioningTest::SetUp();
        mModel = std::make_unique<PartitioningModel>();
    }

    static std::string toString(SourceOperandIndex index) {
        return "{" + std::to_string(index.first) + ", " + std::to_string(index.second) + "}";
    }

    static std::string toString(const std::set<TestStepRole>& roles) {
        std::stringstream ss;
        ss << "[ ";
        for (const auto& [deviceName, type] : roles) {
            ss << "{" << deviceName << ", " << (type == IOType::INPUT ? "INPUT" : "OUTPUT") << "} ";
        }
        ss << "]";
        return ss.str();
    }

    void finishAndPartitionModelForDevices(const std::vector<std::shared_ptr<Device>>& devices) {
        mModel->finish();
        ASSERT_TRUE(mModel->isValid());
        ASSERT_EQ(mModel->partitionTheWork(devices, ExecutePreference::PREFER_LOW_POWER,
                                           ExecutePriority::DEFAULT, {}, &mPlan),
                  ANEURALNETWORKS_NO_ERROR);
    }

    void checkStepRolesOfInput(uint32_t index, const std::set<TestStepRole>& expected) const {
        SCOPED_TRACE("Input: " + std::to_string(index));
        std::set<TestStepRole> actual;
        mPlan.forEachStepRoleOfInput(
                index, [&actual](const auto* preparedModel, IOType type, uint32_t) {
                    actual.emplace(preparedModel->getDevice()->getName(), type);
                });
        EXPECT_TRUE(expected == actual)
                << "expected: " << toString(expected) << ", actual: " << toString(actual);
    }

    void checkStepRolesOfOutput(uint32_t index, const std::set<TestStepRole>& expected) const {
        SCOPED_TRACE("Output: " + std::to_string(index));
        std::set<TestStepRole> actual;
        mPlan.forEachStepRoleOfOutput(
                index, [&actual](const auto* preparedModel, IOType type, uint32_t) {
                    actual.emplace(preparedModel->getDevice()->getName(), type);
                });
        EXPECT_TRUE(expected == actual)
                << "expected: " << toString(expected) << ", actual: " << toString(actual);
    }

    void checkStepRolesOfSourceOperand(SourceOperandIndex index,
                                       const std::set<TestStepRole>& expected) const {
        SCOPED_TRACE("SourceOperandIndex: " + toString(index));
        std::set<TestStepRole> actual;
        mPlan.forTest_compoundForEachStepRoleOfSourceOperand(
                index, [&actual](const auto* preparedModel, IOType type, uint32_t) {
                    actual.emplace(preparedModel->getDevice()->getName(), type);
                });
        EXPECT_TRUE(expected == actual)
                << "expected: " << toString(expected) << ", actual: " << toString(actual);
    }

    std::unique_ptr<PartitioningModel> mModel;
    ExecutionPlan mPlan;
};

// Test a graph with 3 operations, each operation in a separate partition:
//     opnd2 = OP0(opnd0, opnd1)
//     opnd4 = OP1(opnd1, opnd3)
//     opnd5 = OP2(opnd2, opnd4)
TEST_F(MemoryStepRoleTest, NoControlFlow) {
    const uint32_t opnd0 = mModel->addFloatOperand();
    const uint32_t opnd1 = mModel->addFloatOperand();
    const uint32_t opnd2 = mModel->addOperation2To1V1_0(0, opnd0, opnd1);
    const uint32_t opnd3 = mModel->addFloatOperand();
    const uint32_t opnd4 = mModel->addOperation2To1V1_0(1, opnd1, opnd3);
    const uint32_t opnd5 = mModel->addOperation2To1V1_0(2, opnd2, opnd4);
    mModel->identifyInputsAndOutputs({opnd0, opnd1, opnd3}, {opnd2, opnd5});

    // This will result in 3 partitions:
    // deviceA handles op0, deviceB handles op1, deviceC handles op2.
    const auto devices = makeDevices(
            {{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}, {"deviceC", 0.5, 1 << 2}});
    finishAndPartitionModelForDevices(devices);
    checkExecutionPlanSteps(mPlan, {"deviceB", "deviceA", "deviceC"});

    // Check the step roles of the main model inputs and outputs:
    //
    // input0 and input2 are each exclusive for a single partition.
    checkStepRolesOfInput(0, {{"deviceA", IOType::INPUT}});
    checkStepRolesOfInput(2, {{"deviceB", IOType::INPUT}});
    // input1 is shared by two operations in different partitions.
    checkStepRolesOfInput(1, {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}});
    // output0 is a model output that is a downstream input.
    checkStepRolesOfOutput(0, {{"deviceA", IOType::OUTPUT}, {"deviceC", IOType::INPUT}});
    // output1 is only used in a single partition.
    checkStepRolesOfOutput(1, {{"deviceC", IOType::OUTPUT}});

    // Check the step roles of the partition boundary temporaries that we will allocate memory on
    // behalf of (see ExecutionPlan::makeController for the allocation logic):
    //
    // opnd4 is a partition boundary temporary.
    checkStepRolesOfSourceOperand({0, opnd4},
                                  {{"deviceB", IOType::OUTPUT}, {"deviceC", IOType::INPUT}});
}

// Test a graph with an interpreted IF operation.
TEST_F(MemoryStepRoleTest, InterpretedIf) {
    auto thenModel = std::make_unique<PartitioningModel>();
    const uint32_t thenOpnd0 = thenModel->addFloatOperand();
    const uint32_t thenOpnd1 = thenModel->addFloatOperand();
    const uint32_t thenOpnd2 = thenModel->addOperation2To1V1_0(0, thenOpnd0, thenOpnd1);
    thenModel->identifyInputsAndOutputs({thenOpnd0, thenOpnd1}, {thenOpnd2});
    thenModel->finish();
    EXPECT_TRUE(thenModel->isValid());

    auto elseModel = std::make_unique<PartitioningModel>();
    const uint32_t elseOpnd0 = elseModel->addFloatOperand();
    const uint32_t elseOpnd1 = elseModel->addFloatOperand();
    const uint32_t elseOpnd2 = elseModel->addOperation2To1V1_0(1, elseOpnd0, elseOpnd1);
    elseModel->identifyInputsAndOutputs({elseOpnd0, elseOpnd1}, {elseOpnd2});
    elseModel->finish();
    EXPECT_TRUE(elseModel->isValid());

    const uint32_t mainOpnd0 = mModel->addBooleanOperand();
    const uint32_t mainOpnd1 = mModel->addFloatOperand();
    const uint32_t mainOpnd2 = mModel->addFloatOperand();
    const uint32_t mainOpnd3 = mModel->addFloatOperand();
    mModel->addIfOperation(mainOpnd0, *thenModel, *elseModel, {mainOpnd1, mainOpnd2}, {mainOpnd3});
    mModel->identifyInputsAndOutputs({mainOpnd0, mainOpnd1, mainOpnd2}, {mainOpnd3});

    // deviceA handles op0, deviceB handles op1.
    const auto devices = makeDevices({{"deviceA", 0.8, ~0U}, {"deviceB", 0.5, 1 << 1}});
    finishAndPartitionModelForDevices(devices);
    checkExecutionPlanSteps(mPlan, {kIfStep, "deviceA", kGotoStep, "deviceB"});

    // Check the step roles of the main model inputs and outputs:
    //
    // input0 is a condition operand of the interpreted IF that will only be read by the runtime.
    checkStepRolesOfInput(0, {});
    // input1 and input2 are outer inputs of the interpreted IF. The memories may be directly used
    // by the input operands of the then and else model.
    checkStepRolesOfInput(1, {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}});
    checkStepRolesOfInput(2, {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}});
    // output0 is the outer output of the interpreted IF. The memory may be directly
    // used by the output operands of the then and else model.
    checkStepRolesOfOutput(0, {{"deviceA", IOType::OUTPUT}, {"deviceB", IOType::OUTPUT}});

    // There is no partition boundary temporary in this model that we will allocate memory on
    // behalf of (see ExecutionPlan::makeController for the allocation logic).
}

// Test a graph with an interpreted WHILE operation.
TEST_F(MemoryStepRoleTest, InterpretedWhile) {
    // Condition model:
    //     condOpnd3 = OP0(condOpnd0, condOpnd1)
    //     condOpnd4 = EQUAL(condOpnd2, condOpnd3)
    auto condModel = std::make_unique<PartitioningModel>();
    const uint32_t condOpnd0 = condModel->addFloatOperand();
    const uint32_t condOpnd1 = condModel->addFloatOperand();
    const uint32_t condOpnd2 = condModel->addFloatOperand();
    const uint32_t condOpnd3 = condModel->addOperation2To1V1_0(0, condOpnd0, condOpnd1);
    const uint32_t condOpnd4 = condModel->addExplicitOperationXTo1(
            ANEURALNETWORKS_EQUAL, {condOpnd2, condOpnd3}, WrapperType::TENSOR_BOOL8);
    condModel->identifyInputsAndOutputs({condOpnd0, condOpnd1, condOpnd2}, {condOpnd4});
    condModel->finish();
    EXPECT_TRUE(condModel->isValid());

    // Body model:
    //     bodyOpnd3 = OP1(bodyOpnd0, bodyOpnd1)
    //     bodyOpnd4 = OP1(bodyOpnd0, bodyOpnd2)
    auto bodyModel = std::make_unique<PartitioningModel>();
    const uint32_t bodyOpnd0 = bodyModel->addFloatOperand();
    const uint32_t bodyOpnd1 = bodyModel->addFloatOperand();
    const uint32_t bodyOpnd2 = bodyModel->addFloatOperand();
    const uint32_t bodyOpnd3 = bodyModel->addOperation2To1V1_0(1, bodyOpnd0, bodyOpnd1);
    const uint32_t bodyOpnd4 = bodyModel->addOperation2To1V1_0(1, bodyOpnd0, bodyOpnd2);
    bodyModel->identifyInputsAndOutputs({bodyOpnd0, bodyOpnd1, bodyOpnd2}, {bodyOpnd3, bodyOpnd4});
    bodyModel->finish();
    EXPECT_TRUE(bodyModel->isValid());

    const uint32_t mainOpnd0 = mModel->addFloatOperand();
    const uint32_t mainOpnd1 = mModel->addFloatOperand();
    const uint32_t mainOpnd2 = mModel->addFloatOperand();
    const uint32_t mainOpnd3 = mModel->addFloatOperand();
    mModel->addWhileOperation(*condModel, *bodyModel, {mainOpnd0, mainOpnd1, mainOpnd2},
                              {mainOpnd3});
    mModel->identifyInputsAndOutputs({mainOpnd0, mainOpnd1, mainOpnd2}, {mainOpnd3});

    // deviceA handles the cond model, deviceB handles the body model.
    const auto devices = makeDevices({{"deviceA",
                                       0.8,
                                       ~0U,
                                       PartitioningDriver::OEMNo,
                                       HalVersion::LATEST,
                                       {V1_3::OperationType::EQUAL}},
                                      {"deviceB", 0.5, 1 << 1}});
    finishAndPartitionModelForDevices(devices);
    checkExecutionPlanSteps(mPlan, {kWhileStep, "deviceA", kGotoStep, "deviceB", kGotoStep});

    // The subgraph indexes of the condition and body models of the WHILE operation.
    const uint32_t condModelIndex = 1;
    const uint32_t bodyModelIndex = 2;

    // Check the step roles of the main model inputs and outputs:
    //
    // input0 (input-output), input1 (state-only), and input2 (input-only) are outer inputs of the
    // interpreted WHILE. The memories may be directly used by the input operands of the condition
    // and body models.
    checkStepRolesOfInput(0, {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}});
    checkStepRolesOfInput(1, {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}});
    checkStepRolesOfInput(2, {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}});
    // output0 is an outer output of the interpreted WHILE that will only be written by the runtime.
    checkStepRolesOfOutput(0, {});

    // Check the step roles of the partition boundary temporaries that we will allocate memory on
    // behalf of (see ExecutionPlan::makeController for the allocation logic):
    //
    // condOpnd4 is output of the interpreted WHILE condition model.
    checkStepRolesOfSourceOperand({condModelIndex, condOpnd4}, {{"deviceA", IOType::OUTPUT}});
    // bodyOpnd3 (input-output) and bodyOpnd4 (state-only) are outputs of the interpreted WHILE body
    // model. The memories may be directly used by the input operands of the condition and body
    // models.
    checkStepRolesOfSourceOperand(
            {bodyModelIndex, bodyOpnd3},
            {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}, {"deviceB", IOType::OUTPUT}});
    checkStepRolesOfSourceOperand(
            {bodyModelIndex, bodyOpnd4},
            {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}, {"deviceB", IOType::OUTPUT}});
}

// Test a graph with nested interpreted control flow operations: a WHILE operation with IF operation
// in the body model.
TEST_F(MemoryStepRoleTest, NestedInterpretedControlFlow) {
    auto condModel = std::make_unique<PartitioningModel>();
    const uint32_t condOpnd0 = condModel->addFloatOperand();
    const uint32_t condOpnd1 = condModel->addFloatOperand();
    const uint32_t condOpnd2 = condModel->addBooleanOperand();
    const uint32_t condOpnd3 = condModel->addExplicitOperationXTo1(
            ANEURALNETWORKS_EQUAL, {condOpnd0, condOpnd1}, WrapperType::TENSOR_BOOL8);
    condModel->identifyInputsAndOutputs({condOpnd0, condOpnd1, condOpnd2}, {condOpnd3});
    condModel->finish();
    EXPECT_TRUE(condModel->isValid());

    auto thenModel = std::make_unique<PartitioningModel>();
    const uint32_t thenOpnd0 = thenModel->addFloatOperand();
    const uint32_t thenOpnd1 = thenModel->addFloatOperand();
    const uint32_t thenOpnd2 = thenModel->addOperation2To1V1_0(0, thenOpnd0, thenOpnd1);
    thenModel->identifyInputsAndOutputs({thenOpnd0, thenOpnd1}, {thenOpnd2});
    thenModel->finish();
    EXPECT_TRUE(thenModel->isValid());

    auto elseModel = std::make_unique<PartitioningModel>();
    const uint32_t elseOpnd0 = elseModel->addFloatOperand();
    const uint32_t elseOpnd1 = elseModel->addFloatOperand();
    const uint32_t elseOpnd2 = elseModel->addOperation2To1V1_0(1, elseOpnd0, elseOpnd1);
    elseModel->identifyInputsAndOutputs({elseOpnd0, elseOpnd1}, {elseOpnd2});
    elseModel->finish();
    EXPECT_TRUE(elseModel->isValid());

    auto bodyModel = std::make_unique<PartitioningModel>();
    const uint32_t bodyOpnd0 = bodyModel->addFloatOperand();
    const uint32_t bodyOpnd1 = bodyModel->addFloatOperand();
    const uint32_t bodyOpnd2 = bodyModel->addBooleanOperand();
    const uint32_t bodyOpnd3 = bodyModel->addFloatOperand();
    bodyModel->addIfOperation(bodyOpnd2, *thenModel, *elseModel, {bodyOpnd0, bodyOpnd1},
                              {bodyOpnd3});
    bodyModel->identifyInputsAndOutputs({bodyOpnd0, bodyOpnd1, bodyOpnd2}, {bodyOpnd3});
    bodyModel->finish();
    EXPECT_TRUE(bodyModel->isValid());

    const uint32_t mainOpnd0 = mModel->addFloatOperand();
    const uint32_t mainOpnd1 = mModel->addFloatOperand();
    const uint32_t mainOpnd2 = mModel->addBooleanOperand();
    const uint32_t mainOpnd3 = mModel->addFloatOperand();
    mModel->addWhileOperation(*condModel, *bodyModel, {mainOpnd0, mainOpnd1, mainOpnd2},
                              {mainOpnd3});
    mModel->identifyInputsAndOutputs({mainOpnd0, mainOpnd1, mainOpnd2}, {mainOpnd3});

    // deviceA handles the cond model, deviceB handles the then model,
    // deviceC handles the else model.
    const auto devices = makeDevices({{"deviceA",
                                       0.8,
                                       ~0U,
                                       PartitioningDriver::OEMNo,
                                       HalVersion::LATEST,
                                       {V1_3::OperationType::EQUAL}},
                                      {"deviceB", 0.5, 1 << 0},
                                      {"deviceC", 0.5, 1 << 1}});
    finishAndPartitionModelForDevices(devices);
    checkExecutionPlanSteps(mPlan, {kWhileStep, "deviceA", kGotoStep, kIfStep, "deviceB", kGotoStep,
                                    "deviceC", kGotoStep});

    // The subgraph indexes of the condition and body models of the WHILE operation.
    const uint32_t condModelIndex = 1;
    const uint32_t bodyModelIndex = 2;

    // Check the step roles of the main model inputs and outputs:
    //
    // input0 and input1 are outer inputs of the interpreted WHILE. The memories may be directly
    // used by the input operands of the condition and body models, and then be directly used by the
    // input operands of the then and else model of the interpreted IF in the body model.
    checkStepRolesOfInput(
            0,
            {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}, {"deviceC", IOType::INPUT}});
    checkStepRolesOfInput(
            1,
            {{"deviceA", IOType::INPUT}, {"deviceB", IOType::INPUT}, {"deviceC", IOType::INPUT}});
    // input2 is also an outer input of the interpreted WHILE. The memory has no step role in the
    // condition model. In the body model, the memory will be used by the condition operand of the
    // interpreted IF that will only be read by the runtime.
    checkStepRolesOfInput(2, {});
    // output0 is an outer output of the interpreted WHILE that will only be written by the runtime.
    checkStepRolesOfOutput(0, {});

    // Check the step roles of the partition boundary temporaries that we will allocate memory on
    // behalf of (see ExecutionPlan::makeController for the allocation logic):
    //
    // condOpnd2 is output of the interpreted WHILE condition model.
    checkStepRolesOfSourceOperand({condModelIndex, condOpnd3}, {{"deviceA", IOType::OUTPUT}});
    // bodyOpnd3 is output of the interpreted WHILE body model. The memories may be directly used by
    // the input operands of the condition and body models, and then be directly used by the
    // input operands of the then and else model of the interpreted IF in the body model.
    checkStepRolesOfSourceOperand({bodyModelIndex, bodyOpnd3}, {{"deviceA", IOType::INPUT},
                                                                {"deviceB", IOType::INPUT},
                                                                {"deviceB", IOType::OUTPUT},
                                                                {"deviceC", IOType::INPUT},
                                                                {"deviceC", IOType::OUTPUT}});
}

}  // namespace
