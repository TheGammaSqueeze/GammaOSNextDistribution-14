/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <android-base/scopeguard.h>
#include <gtest/gtest.h>
#include <nnapi/SharedMemory.h>
#include <nnapi/Types.h>
#include <nnapi/Validation.h>

#include "GeneratedTestUtils.h"
#include "Memory.h"
#include "ModelBuilder.h"
#include "TestNeuralNetworksWrapper.h"

#ifdef __ANDROID__
#include <android/hardware_buffer.h>
#endif  // __ANDROID__

namespace android::nn::compliance_test {

using namespace test_helper;
using WrapperModel = test_wrapper::Model;
using WrapperOperandType = test_wrapper::OperandType;
using WrapperType = test_wrapper::Type;

// Tag for the compilance tests
class ComplianceTest : public ::testing::Test {};

// Verifies the earliest supported version for the model.
static void testAvailableSinceVersion(const WrapperModel& wrapperModel, const Version testVersion) {
    // Creates a canonical model from a wrapper model.
    auto modelBuilder = reinterpret_cast<const ModelBuilder*>(wrapperModel.getHandle());
    EXPECT_TRUE(modelBuilder->isFinished());
    EXPECT_TRUE(modelBuilder->isValid());
    Model model = modelBuilder->makeModel();
    const auto modelVersion = validate(model);
    ASSERT_TRUE(modelVersion.ok()) << modelVersion.error();
    ASSERT_EQ(testVersion, modelVersion.value());
}

// Verifies the earliest supported version for the request.
static void testAvailableSinceVersion(const Request& request, const Version testVersion) {
    const auto requestVersion = validate(request);
    ASSERT_TRUE(requestVersion.ok()) << requestVersion.error();
    ASSERT_EQ(testVersion, requestVersion.value());
}

static const WrapperOperandType kTypeTensorFloat(WrapperType::TENSOR_FLOAT32, {1});
static const WrapperOperandType kTypeTensorFloatRank0(WrapperType::TENSOR_FLOAT32, {});
static const WrapperOperandType kTypeInt32(WrapperType::INT32, {});

const int32_t kNoActivation = ANEURALNETWORKS_FUSED_NONE;

TEST_F(ComplianceTest, Rank0TensorModelInput) {
    // A simple ADD operation: op1 ADD op2 = op3, with op1 and op2 of rank 0.
    WrapperModel model;
    auto op1 = model.addOperand(&kTypeTensorFloatRank0);
    auto op2 = model.addOperand(&kTypeTensorFloatRank0);
    auto op3 = model.addOperand(&kTypeTensorFloat);
    auto act = model.addConstantOperand(&kTypeInt32, kNoActivation);
    model.addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
    model.identifyInputsAndOutputs({op1, op2}, {op3});
    ASSERT_TRUE(model.isValid());
    model.finish();
    testAvailableSinceVersion(model, kVersionFeatureLevel3);
}

TEST_F(ComplianceTest, Rank0TensorModelOutput) {
    // A simple ADD operation: op1 ADD op2 = op3, with op3 of rank 0.
    WrapperModel model;
    auto op1 = model.addOperand(&kTypeTensorFloat);
    auto op2 = model.addOperand(&kTypeTensorFloat);
    auto op3 = model.addOperand(&kTypeTensorFloatRank0);
    auto act = model.addConstantOperand(&kTypeInt32, kNoActivation);
    model.addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
    model.identifyInputsAndOutputs({op1, op2}, {op3});
    ASSERT_TRUE(model.isValid());
    model.finish();
    testAvailableSinceVersion(model, kVersionFeatureLevel3);
}

TEST_F(ComplianceTest, Rank0TensorTemporaryVariable) {
    // Two ADD operations: op1 ADD op2 = op3, op3 ADD op4 = op5, with op3 of rank 0.
    WrapperModel model;
    auto op1 = model.addOperand(&kTypeTensorFloat);
    auto op2 = model.addOperand(&kTypeTensorFloat);
    auto op3 = model.addOperand(&kTypeTensorFloatRank0);
    auto op4 = model.addOperand(&kTypeTensorFloat);
    auto op5 = model.addOperand(&kTypeTensorFloat);
    auto act = model.addConstantOperand(&kTypeInt32, kNoActivation);
    model.addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
    model.addOperation(ANEURALNETWORKS_ADD, {op3, op4, act}, {op5});
    model.identifyInputsAndOutputs({op1, op2, op4}, {op5});
    ASSERT_TRUE(model.isValid());
    model.finish();
    testAvailableSinceVersion(model, kVersionFeatureLevel3);
}

// Hardware buffers are an Android concept, which aren't necessarily
// available on other platforms such as ChromeOS, which also build NNAPI.
#if defined(__ANDROID__)
TEST_F(ComplianceTest, HardwareBufferModel) {
    const size_t memorySize = 20;
    AHardwareBuffer_Desc desc{
            .width = memorySize,
            .height = 1,
            .layers = 1,
            .format = AHARDWAREBUFFER_FORMAT_BLOB,
            .usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
    };

    AHardwareBuffer* buffer = nullptr;
    ASSERT_EQ(AHardwareBuffer_allocate(&desc, &buffer), 0);
    auto allocateGuard =
            android::base::make_scope_guard([buffer]() { AHardwareBuffer_release(buffer); });

    test_wrapper::Memory memory(buffer);
    ASSERT_TRUE(memory.isValid());

    // A simple ADD operation: op1 ADD op2 = op3, with op2 using a const hardware buffer.
    WrapperModel model;
    auto op1 = model.addOperand(&kTypeTensorFloat);
    auto op2 = model.addOperand(&kTypeTensorFloat);
    auto op3 = model.addOperand(&kTypeTensorFloat);
    auto act = model.addConstantOperand(&kTypeInt32, kNoActivation);
    model.setOperandValueFromMemory(op2, &memory, 0, sizeof(float));
    model.addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
    model.identifyInputsAndOutputs({op1}, {op3});
    ASSERT_TRUE(model.isValid());
    model.finish();
    testAvailableSinceVersion(model, kVersionFeatureLevel3);
}

TEST_F(ComplianceTest, HardwareBufferRequest) {
    constexpr size_t kAhwbMemorySize = 1024;
    const auto [n, ahwb] = MemoryRuntimeAHWB::create(kAhwbMemorySize);
    ASSERT_EQ(n, ANEURALNETWORKS_NO_ERROR);
    const Request::MemoryPool ahwbMemoryPool = ahwb->getMemoryPool();

    constexpr size_t kSharedMemorySize = 1024;
    auto maybeSharedMemoryPool = createSharedMemory(kSharedMemorySize);
    ASSERT_TRUE(maybeSharedMemoryPool.ok()) << maybeSharedMemoryPool.error().message;
    const Request::MemoryPool sharedMemoryPool = std::move(maybeSharedMemoryPool).value();

    // AHardwareBuffer as input.
    testAvailableSinceVersion(
            Request{
                    .inputs = {{.lifetime = Request::Argument::LifeTime::POOL,
                                .location = {.poolIndex = 0, .length = kAhwbMemorySize},
                                .dimensions = {}}},
                    .outputs = {{.lifetime = Request::Argument::LifeTime::POOL,
                                 .location = {.poolIndex = 1, .length = kSharedMemorySize},
                                 .dimensions = {}}},
                    .pools = {ahwbMemoryPool, sharedMemoryPool},
            },
            kVersionFeatureLevel3);

    // AHardwareBuffer as output.
    testAvailableSinceVersion(
            Request{
                    .inputs = {{.lifetime = Request::Argument::LifeTime::POOL,
                                .location = {.poolIndex = 0, .length = kSharedMemorySize},
                                .dimensions = {}}},
                    .outputs = {{.lifetime = Request::Argument::LifeTime::POOL,
                                 .location = {.poolIndex = 1, .length = kAhwbMemorySize},
                                 .dimensions = {}}},
                    .pools = {sharedMemoryPool, ahwbMemoryPool},
            },
            kVersionFeatureLevel3);
}
#endif

TEST_F(ComplianceTest, DeviceMemory) {
    constexpr size_t kSharedMemorySize = 1024;
    auto maybeSharedMemoryPool = createSharedMemory(kSharedMemorySize);
    ASSERT_TRUE(maybeSharedMemoryPool.ok()) << maybeSharedMemoryPool.error().message;
    const Request::MemoryPool sharedMemoryPool = std::move(maybeSharedMemoryPool).value();
    const Request::MemoryPool deviceMemoryPool = Request::MemoryDomainToken(1);

    // Device memory as input.
    testAvailableSinceVersion(
            Request{
                    .inputs = {{.lifetime = Request::Argument::LifeTime::POOL,
                                .location = {.poolIndex = 0},
                                .dimensions = {}}},
                    .outputs = {{.lifetime = Request::Argument::LifeTime::POOL,
                                 .location = {.poolIndex = 1, .length = kSharedMemorySize},
                                 .dimensions = {}}},
                    .pools = {deviceMemoryPool, sharedMemoryPool},
            },
            kVersionFeatureLevel4);

    // Device memory as output.
    testAvailableSinceVersion(
            Request{
                    .inputs = {{.lifetime = Request::Argument::LifeTime::POOL,
                                .location = {.poolIndex = 0, .length = kSharedMemorySize},
                                .dimensions = {}}},
                    .outputs = {{.lifetime = Request::Argument::LifeTime::POOL,
                                 .location = {.poolIndex = 1},
                                 .dimensions = {}}},
                    .pools = {sharedMemoryPool, deviceMemoryPool},
            },
            kVersionFeatureLevel4);
}

class GeneratedComplianceTest : public generated_tests::GeneratedTestBase {};

TEST_P(GeneratedComplianceTest, Test) {
    generated_tests::GeneratedModel model;
    generated_tests::createModel(testModel, &model);
    ASSERT_TRUE(model.isValid());
    model.finish();
    switch (testModel.minSupportedVersion) {
        // TODO(b/209797313): Unify HalVersion and Version.
        case TestHalVersion::V1_0:
            testAvailableSinceVersion(model, kVersionFeatureLevel1);
            break;
        case TestHalVersion::V1_1:
            testAvailableSinceVersion(model, kVersionFeatureLevel2);
            break;
        case TestHalVersion::V1_2:
            testAvailableSinceVersion(model, kVersionFeatureLevel3);
            break;
        case TestHalVersion::V1_3:
            testAvailableSinceVersion(model, kVersionFeatureLevel4);
            break;
        case TestHalVersion::AIDL_V1:
            testAvailableSinceVersion(model, kVersionFeatureLevel5);
            break;
        case TestHalVersion::AIDL_V2:
            testAvailableSinceVersion(model, kVersionFeatureLevel6);
            break;
        case TestHalVersion::AIDL_V3:
            testAvailableSinceVersion(model, kVersionFeatureLevel7);
            break;
        case TestHalVersion::UNKNOWN:
            FAIL();
    }
}

INSTANTIATE_GENERATED_TEST(GeneratedComplianceTest, [](const TestModel& testModel) {
    return !testModel.expectFailure && testModel.minSupportedVersion != TestHalVersion::UNKNOWN;
});

}  // namespace android::nn::compliance_test
