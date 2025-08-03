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

#include <ExecutionBurstServer.h>
#include <HalInterfaces.h>
#include <SampleDriver.h>
#include <ValidateHal.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <cassert>
#include <chrono>
#include <iterator>
#include <map>
#include <queue>
#include <set>
#include <string>
#include <thread>
#include <tuple>
#include <utility>
#include <vector>

#include "CompilationBuilder.h"
#include "HalUtils.h"
#include "Manager.h"
#include "NeuralNetworks.h"
#include "NeuralNetworksOEM.h"
#include "TestNeuralNetworksWrapper.h"

namespace {

using namespace ::android;
namespace V1_0 = ::android::hardware::neuralnetworks::V1_0;
namespace V1_1 = ::android::hardware::neuralnetworks::V1_1;
namespace V1_2 = ::android::hardware::neuralnetworks::V1_2;
namespace V1_3 = ::android::hardware::neuralnetworks::V1_3;

using CompilationBuilder = nn::CompilationBuilder;
using Device = nn::Device;
using DeviceManager = nn::DeviceManager;
using ExecutePreference = nn::test_wrapper::ExecutePreference;
using ExecutionBurstServer = nn::ExecutionBurstServer;
using HidlModel = V1_3::Model;
using Result = nn::test_wrapper::Result;
using SampleDriver = nn::sample_driver::SampleDriver;
using SamplePreparedModel = nn::sample_driver::SamplePreparedModel;
using SampleFencedExecutionCallback = nn::sample_driver::SampleFencedExecutionCallback;
using WrapperModel = nn::test_wrapper::Model;
using WrapperOperandType = nn::test_wrapper::OperandType;
using WrapperType = nn::test_wrapper::Type;
using nn::convertToV1_0;
using nn::convertToV1_3;

template <typename T>
using MQDescriptorSync = hardware::MQDescriptorSync<T>;

constexpr V1_2::Timing kBadTiming = {.timeOnDevice = UINT64_MAX, .timeInDriver = UINT64_MAX};
constexpr V1_2::Timing kGoodUnfencedTiming = {.timeOnDevice = 123, .timeInDriver = 456};
constexpr V1_2::Timing kGoodFencedTiming = {.timeOnDevice = 23, .timeInDriver = 56};

// This is an IDevice for testing purposes. The test driver has customized
// getCapabilities_1_3 and getSupportedOperations_1_3.
class TestDriver : public SampleDriver {
   public:
    TestDriver(const char* name, V1_3::Capabilities capabilities,
               const std::vector<bool>& supportedOps)
        : SampleDriver(name), mCapabilities(capabilities), mSupportedOps(supportedOps) {}
    ~TestDriver() override {}

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
        const size_t count = model.main.operations.size();
        std::vector<bool> supported(count);
        std::transform(model.main.operations.begin(), model.main.operations.end(),
                       supported.begin(), [this](V1_3::Operation op) {
                           return mSupportedOps[static_cast<int32_t>(op.type)];
                       });
        cb(V1_3::ErrorStatus::NONE, supported);
        return hardware::Void();
    }

   private:
    V1_3::Capabilities mCapabilities;
    std::vector<bool> mSupportedOps;
};

class IntrospectionControlTest : public ::testing::Test {
   protected:
    void SetUp() override {}
    void TearDown() override {
        if (mEvent) {
            ANeuralNetworksEvent_free(mEvent);
        }
        if (mExecution) {
            ANeuralNetworksExecution_free(mExecution);
        }
        if (mCompilation) {
            ANeuralNetworksCompilation_free(mCompilation);
        }
        DeviceManager::get()->forTest_reInitializeDeviceList();
    }

    struct DeviceSpecification {
        DeviceSpecification(const std::string& name, float perf, std::vector<bool>& supportedOps)
            : mName(name), mSupportedOps(supportedOps) {
            V1_0::PerformanceInfo perfInfo = {.execTime = perf, .powerUsage = perf};
            mCapabilities = {
                    .relaxedFloat32toFloat16PerformanceScalar = perfInfo,
                    .relaxedFloat32toFloat16PerformanceTensor = perfInfo,
                    .operandPerformance =
                            nn::nonExtensionOperandPerformance<nn::HalVersion::V1_3>(perfInfo),
                    .ifPerformance = perfInfo,
                    .whilePerformance = perfInfo};
        }
        std::string mName;
        V1_3::Capabilities mCapabilities;
        std::vector<bool> mSupportedOps;
    };

    // From a vector of DeviceSpecification, register new Devices.
    void registerDevices(std::vector<DeviceSpecification> specifications) {
        for (const auto& specification : specifications) {
            DeviceManager::get()->forTest_registerDevice(nn::makeSharedDevice(
                    specification.mName.c_str(),
                    new TestDriver(specification.mName.c_str(), specification.mCapabilities,
                                   specification.mSupportedOps)));
        }
    }

    bool selectDeviceByName(const std::string& name) {
        uint32_t numDevices = 0;
        EXPECT_EQ(ANeuralNetworks_getDeviceCount(&numDevices), ANEURALNETWORKS_NO_ERROR);
        EXPECT_GE(numDevices, (uint32_t)1);

        for (uint32_t i = 0; i < numDevices; i++) {
            ANeuralNetworksDevice* device = nullptr;
            EXPECT_EQ(ANeuralNetworks_getDevice(i, &device), ANEURALNETWORKS_NO_ERROR);
            const char* buffer = nullptr;
            int result = ANeuralNetworksDevice_getName(device, &buffer);
            if (result == ANEURALNETWORKS_NO_ERROR && name.compare(buffer) == 0) {
                mDevices.push_back(device);
                return true;
            }
        }
        return false;
    }

    bool isSupportedOpListExpected(const std::vector<bool>& expected) {
        const uint32_t kMaxNumberOperations = 256;
        EXPECT_LE(expected.size(), kMaxNumberOperations);
        ANeuralNetworksModel* modelHandle = mModel.getHandle();
        bool supported[kMaxNumberOperations] = {false};
        EXPECT_EQ(ANeuralNetworksModel_getSupportedOperationsForDevices(
                          modelHandle, mDevices.data(), mDevices.size(), supported),
                  ANEURALNETWORKS_NO_ERROR);
        return std::equal(expected.begin(), expected.end(), supported);
    }

    int prepareForExecution(bool measureTiming = false) {
        ANeuralNetworksModel* modelHandle = mModel.getHandle();
        int result = ANeuralNetworksCompilation_createForDevices(modelHandle, mDevices.data(),
                                                                 mDevices.size(), &mCompilation);
        if (result != ANEURALNETWORKS_NO_ERROR) {
            return result;
        }
        EXPECT_EQ(ANeuralNetworksCompilation_finish(mCompilation), ANEURALNETWORKS_NO_ERROR);
        EXPECT_EQ(ANeuralNetworksExecution_create(mCompilation, &mExecution),
                  ANEURALNETWORKS_NO_ERROR);
        if (measureTiming) {
            // Don't call setMeasureTiming unless we need to -- cannot call this
            // API unless there is exactly one device.
            EXPECT_EQ(ANeuralNetworksExecution_setMeasureTiming(mExecution, true),
                      ANEURALNETWORKS_NO_ERROR);
        }
        return ANEURALNETWORKS_NO_ERROR;
    }

    std::vector<ANeuralNetworksDevice*> mDevices;
    ANeuralNetworksEvent* mEvent = nullptr;
    ANeuralNetworksExecution* mExecution = nullptr;
    ANeuralNetworksCompilation* mCompilation = nullptr;
    WrapperModel mModel;
};

void createSimpleAddModel(WrapperModel* model) {
    WrapperOperandType type0(WrapperType::TENSOR_FLOAT32, {2});
    WrapperOperandType type1(WrapperType::INT32, {});
    // Phase 1, operands
    auto op1 = model->addOperand(&type0);
    auto op2 = model->addOperand(&type0);
    auto act = model->addOperand(&type1);
    auto op3 = model->addOperand(&type0);
    // Phase 2, operations
    static int32_t act_init[] = {0};
    model->setOperandValue(act, act_init, sizeof(act_init));
    model->addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
    // Phase 3, inputs and outputs
    model->identifyInputsAndOutputs({op1, op2}, {op3});
    model->finish();
    ASSERT_TRUE(model->isValid());
}

// This test verifies that a simple ADD model is able to run on a single device that claims being
// able to handle all operations.
TEST_F(IntrospectionControlTest, SimpleAddModel) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    // TODO(miaowang): remove once b/72506261 is fixed.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    createSimpleAddModel(&mModel);

    std::string driverName = "test-all";
    std::vector<bool> ops(android::nn::kNumberOfOperationTypes, true);
    registerDevices({{driverName, 0.9, ops}});

    EXPECT_TRUE(selectDeviceByName(driverName));
    EXPECT_TRUE(isSupportedOpListExpected({true}));
    EXPECT_EQ(prepareForExecution(), ANEURALNETWORKS_NO_ERROR);

    // Verify that the mCompilation is actually using the "test-all" device.
    CompilationBuilder* c = reinterpret_cast<CompilationBuilder*>(mCompilation);
    const std::string& deviceNameBuffer =
            c->forTest_getExecutionPlan().forTest_simpleGetDevice()->getName();
    EXPECT_EQ(driverName, deviceNameBuffer);

    float input1[2] = {1.0f, 2.0f};
    float input2[2] = {3.0f, 4.0f};
    float output[2];
    EXPECT_EQ(ANeuralNetworksExecution_setInput(mExecution, 0, nullptr, input1, sizeof(input1)),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_setInput(mExecution, 1, nullptr, input2, sizeof(input2)),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_setOutput(mExecution, 0, nullptr, output, sizeof(output)),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_setMeasureTiming(mExecution, true),
              ANEURALNETWORKS_NO_ERROR);

    EXPECT_EQ(ANeuralNetworksExecution_startCompute(mExecution, &mEvent), ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksEvent_wait(mEvent), ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(output[0], input1[0] + input2[0]);
    EXPECT_EQ(output[1], input1[1] + input2[1]);

    uint64_t timeOnHardware, timeInDriver;
    EXPECT_EQ(ANeuralNetworksExecution_getDuration(mExecution, ANEURALNETWORKS_DURATION_ON_HARDWARE,
                                                   &timeOnHardware),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_getDuration(mExecution, ANEURALNETWORKS_DURATION_IN_DRIVER,
                                                   &timeInDriver),
              ANEURALNETWORKS_NO_ERROR);
    if (timeOnHardware != UINT64_MAX && timeInDriver != UINT64_MAX) {
        EXPECT_LE(timeOnHardware, timeInDriver);
    }
}

/*-- Begin test drivers -------------------------------------------------------------------------*/

namespace test_drivers {

enum class Success : uint32_t {
    // ASYNC: Return ErrorStatus::GENERAL_FAILURE; notify ErrorStatus::GENERAL_FAILURE and
    // kBadTiming
    // SYNC, BURST: Return ErrorStatus::GENERAL_FAILURE and kBadTiming
    // FENCED: Return ErrorStatus::GENERAL_FAILURE, empty hidl_handle, and a nullptr callback
    FAIL_LAUNCH,

    // ASYNC: Return ErrorStatus::NONE; notify ErrorStatus::GENERAL_FAILURE and kBadTiming
    FAIL_WAIT,

    // Bit representation for PASS: One bit set to indicate PASS rather than
    // FAIL, one bit for each of the four timing fields (Unfenced, Fenced) x
    // (OnDevice, InDriver) to distinguish between unavailable timing (bit is
    // clear) and available timing (bit is set), and one bit to call out the
    // special case of CPU.
    PASS_BIT = 1 << 4,
    PASS_UNFENCED_DEVICE_BIT = 1 << 5,
    PASS_UNFENCED_DRIVER_BIT = 1 << 6,
    PASS_FENCED_DEVICE_BIT = 1 << 7,
    PASS_FENCED_DRIVER_BIT = 1 << 8,
    PASS_CPU_BIT = 1 << 9,

    // Each of the four timing fields may be either unavailable or 0
    PASS_CPU = PASS_BIT | PASS_CPU_BIT,

    // ASYNC: Return ErrorStatus::NONE; notify ErrorStatus::NONE and timing
    // SYNC, BURST: Return ErrorStatus::NONE and timing
    // FENCED: Return ErrorStatus::NONE, empty hidl_handle, and a callback with timing.
    //
    // For each PASS other than PASS_CPU, an enum name has the form
    // PASS_${UNFENCED_TIME}_${FENCED_TIME}.  For example, PASS_NEITHER_BOTH
    // means that only fenced timing is available (both timeOnDevice and
    // timeInDriver).  If _${FENCED_TIME} is omitted, it is equivalent to
    // _NEITHER; so PASS_BOTH means that only unfenced timing is available (both
    // timeOnDevice and timeInDriver).
    PASS_NEITHER = PASS_BIT,
    PASS_DEVICE = PASS_BIT | PASS_UNFENCED_DEVICE_BIT,
    PASS_DRIVER = PASS_BIT | PASS_UNFENCED_DRIVER_BIT,
    PASS_BOTH = PASS_BIT | PASS_UNFENCED_DEVICE_BIT | PASS_UNFENCED_DRIVER_BIT,
    PASS_NEITHER_DEVICE = PASS_BIT | PASS_FENCED_DEVICE_BIT,
    PASS_NEITHER_DRIVER = PASS_BIT | PASS_FENCED_DRIVER_BIT,
    PASS_NEITHER_BOTH = PASS_BIT | PASS_FENCED_DEVICE_BIT | PASS_FENCED_DRIVER_BIT,
    PASS_DEVICE_DEVICE = PASS_DEVICE | PASS_NEITHER_DEVICE,
    PASS_DEVICE_DRIVER = PASS_DEVICE | PASS_NEITHER_DRIVER,
    PASS_DEVICE_BOTH = PASS_DEVICE | PASS_NEITHER_BOTH,
    PASS_DRIVER_DEVICE = PASS_DRIVER | PASS_NEITHER_DEVICE,
    PASS_DRIVER_DRIVER = PASS_DRIVER | PASS_NEITHER_DRIVER,
    PASS_DRIVER_BOTH = PASS_DRIVER | PASS_NEITHER_BOTH,
    PASS_BOTH_DEVICE = PASS_BOTH | PASS_NEITHER_DEVICE,
    PASS_BOTH_DRIVER = PASS_BOTH | PASS_NEITHER_DRIVER,
    PASS_BOTH_BOTH = PASS_BOTH | PASS_NEITHER_BOTH,
};

bool hasBit(Success mask, Success bit) {
    const uint32_t bitAsInt = static_cast<uint32_t>(bit);
    CHECK(bitAsInt && (bitAsInt & (bitAsInt - 1)) == 0)
            << "second argument must be a single bit rather than " << static_cast<uint32_t>(bit);
    return static_cast<uint32_t>(mask) & bitAsInt;
}

Success clearBit(Success mask, Success bit) {
    const uint32_t bitAsInt = static_cast<uint32_t>(bit);
    CHECK(bitAsInt && (bitAsInt & (bitAsInt - 1)) == 0)
            << "second argument must be a single bit rather than " << static_cast<uint32_t>(bit);
    return static_cast<Success>(static_cast<uint32_t>(mask) & ~bitAsInt);
}

std::ostream& operator<<(std::ostream& os, Success success) {
    switch (success) {
        case Success::FAIL_LAUNCH:
            return os << "FAIL_LAUNCH";
        case Success::FAIL_WAIT:
            return os << "FAIL_WAIT";
        case Success::PASS_CPU:
            return os << "PASS_CPU";
        default:
            break;
    }

    static const std::vector<std::pair<Success, const char*>> bits = {
            {Success::PASS_BIT, "PASS"},
            {Success::PASS_UNFENCED_DEVICE_BIT, "UNFENCED_DEVICE"},
            {Success::PASS_UNFENCED_DRIVER_BIT, "UNFENCED_DRIVER"},
            {Success::PASS_FENCED_DEVICE_BIT, "FENCED_DEVICE"},
            {Success::PASS_FENCED_DRIVER_BIT, "FENCED_DRIVER"},
    };
    bool gotOutput = false;
    for (const auto& b : bits) {
        if (hasBit(success, b.first)) {
            if (gotOutput) {
                os << '|';
            } else {
                gotOutput = true;
            }
            os << b.second;
            success = clearBit(success, b.first);
        }
    }
    if (uint32_t successAsInt = static_cast<uint32_t>(success)) {
        if (gotOutput) {
            os << '|';
        }
        os << successAsInt;
    }
    return os;
}

// Returns (unfenced timing, fenced timing).
// Not for PASS_CPU.
std::pair<V1_2::Timing, V1_2::Timing> getExpectedTiming(Success s, bool fencedExecution) {
    CHECK_NE(s, Success::PASS_CPU);

    if (!hasBit(s, Success::PASS_BIT)) {
        return {kBadTiming, kBadTiming};
    }

    std::pair<V1_2::Timing, V1_2::Timing> result;
    result.first.timeOnDevice = hasBit(s, Success::PASS_UNFENCED_DEVICE_BIT)
                                        ? kGoodUnfencedTiming.timeOnDevice
                                        : UINT64_MAX;
    result.first.timeInDriver = hasBit(s, Success::PASS_UNFENCED_DRIVER_BIT)
                                        ? kGoodUnfencedTiming.timeInDriver
                                        : UINT64_MAX;
    if (fencedExecution) {
        result.second.timeOnDevice = hasBit(s, Success::PASS_FENCED_DEVICE_BIT)
                                             ? kGoodFencedTiming.timeOnDevice
                                             : UINT64_MAX;
        result.second.timeInDriver = hasBit(s, Success::PASS_FENCED_DRIVER_BIT)
                                             ? kGoodFencedTiming.timeInDriver
                                             : UINT64_MAX;
    } else {
        result.second = result.first;
    }
    return result;
}

// For these tests we don't care about actually running an inference -- we
// just want to placeholder up execution status and timing results, and control
// when the execution finishes.
class TestPreparedModelLatest : public SamplePreparedModel {
   public:
    TestPreparedModelLatest(const HidlModel& model, const SampleDriver* driver, Success success)
        : SamplePreparedModel(model, driver, V1_1::ExecutionPreference::FAST_SINGLE_ANSWER, uid_t{},
                              nn::kDefaultPriority13),
          mSuccess(success) {}

    hardware::Return<V1_0::ErrorStatus> execute(
            const V1_0::Request&, const sp<V1_0::IExecutionCallback>& callback) override {
        switch (mSuccess) {
            case Success::PASS_NEITHER:
                std::thread([callback] {
                    dummyExecution();
                    callback->notify(V1_0::ErrorStatus::NONE);
                }).detach();
                return V1_0::ErrorStatus::NONE;
            case Success::FAIL_LAUNCH:
                dummyExecution();
                callback->notify(V1_0::ErrorStatus::GENERAL_FAILURE);
                return V1_0::ErrorStatus::GENERAL_FAILURE;
            case Success::FAIL_WAIT:
                std::thread([callback] {
                    dummyExecution();
                    callback->notify(V1_0::ErrorStatus::GENERAL_FAILURE);
                }).detach();
                return V1_0::ErrorStatus::NONE;
            default:
                ADD_FAILURE() << "Unexpected Success kind";
                return V1_0::ErrorStatus::GENERAL_FAILURE;
        }
    }

    hardware::Return<V1_0::ErrorStatus> execute_1_2(
            const V1_0::Request&, V1_2::MeasureTiming measure,
            const sp<V1_2::IExecutionCallback>& callback) override {
        EXPECT_EQ(measure, V1_2::MeasureTiming::YES);
        switch (mSuccess) {
            case Success::PASS_NEITHER:
            case Success::PASS_DEVICE:
            case Success::PASS_DRIVER:
            case Success::PASS_BOTH:
                std::thread([this, callback] {
                    dummyExecution();
                    callback->notify_1_2(V1_0::ErrorStatus::NONE, {},
                                         getExpectedTiming(mSuccess, false).first);
                }).detach();
                return V1_0::ErrorStatus::NONE;
            case Success::FAIL_LAUNCH:
                dummyExecution();
                callback->notify(V1_0::ErrorStatus::GENERAL_FAILURE);
                return V1_0::ErrorStatus::GENERAL_FAILURE;
            case Success::FAIL_WAIT:
                std::thread([callback] {
                    dummyExecution();
                    callback->notify(V1_0::ErrorStatus::GENERAL_FAILURE);
                }).detach();
                return V1_0::ErrorStatus::NONE;
            default:
                ADD_FAILURE() << "Unexpected Success kind";
                return V1_0::ErrorStatus::GENERAL_FAILURE;
        }
    }

    hardware::Return<V1_3::ErrorStatus> execute_1_3(
            const V1_3::Request&, V1_2::MeasureTiming measure, const V1_3::OptionalTimePoint&,
            const V1_3::OptionalTimeoutDuration&,
            const sp<V1_3::IExecutionCallback>& callback) override {
        // Use a placeholder V1_0::Request because execute_1_2 ignores request entirely.
        const V1_0::ErrorStatus status = execute_1_2(V1_0::Request{}, measure, callback);
        return convertToV1_3(status);
    }

    hardware::Return<void> executeSynchronously(const V1_0::Request&, V1_2::MeasureTiming measure,
                                                executeSynchronously_cb cb) override {
        EXPECT_EQ(measure, V1_2::MeasureTiming::YES);
        switch (mSuccess) {
            case Success::PASS_NEITHER:
            case Success::PASS_DEVICE:
            case Success::PASS_DRIVER:
            case Success::PASS_BOTH:
                dummyExecution();
                cb(V1_0::ErrorStatus::NONE, {}, getExpectedTiming(mSuccess, false).first);
                return hardware::Void();
            case Success::FAIL_WAIT:
                // While this is a synchronous execution method, the NNAPI
                // runtime may call it even for asynchronous execution, so we
                // need to tolerate Success::FAIL_WAIT here, not just
                // Success::FAIL_LAUNCH.
                FALLTHROUGH_INTENDED;
            case Success::FAIL_LAUNCH:
                dummyExecution();
                cb(V1_0::ErrorStatus::GENERAL_FAILURE, {}, kBadTiming);
                return hardware::Void();
            default:
                ADD_FAILURE() << "Unexpected Success kind";
                cb(V1_0::ErrorStatus::GENERAL_FAILURE, {}, kBadTiming);
                return hardware::Void();
        }
    }

    hardware::Return<void> executeSynchronously_1_3(const V1_3::Request&,
                                                    V1_2::MeasureTiming measure,
                                                    const V1_3::OptionalTimePoint&,
                                                    const V1_3::OptionalTimeoutDuration&,
                                                    executeSynchronously_1_3_cb cb) override {
        const auto wrappedCb = [&cb](V1_0::ErrorStatus status,
                                     const hardware::hidl_vec<V1_2::OutputShape>& outputShapes,
                                     V1_2::Timing timing) {
            cb(convertToV1_3(status), outputShapes, timing);
        };
        // Use a placeholder V1_0::Request because executeSynchronously ignores request entirely.
        return executeSynchronously(V1_0::Request{}, measure, wrappedCb);
    }

    // ExecutionBurstServer::create has an overload that will use
    // IPreparedModel::executeSynchronously(), so we can rely on that, rather
    // than having to implement ExecutionBurstServer::IExecutorWithCache.
    hardware::Return<void> configureExecutionBurst(
            const sp<V1_2::IBurstCallback>& callback,
            const MQDescriptorSync<V1_2::FmqRequestDatum>& requestChannel,
            const MQDescriptorSync<V1_2::FmqResultDatum>& resultChannel,
            configureExecutionBurst_cb cb) override {
        const sp<V1_2::IBurstContext> burst = ExecutionBurstServer::create(
                callback, requestChannel, resultChannel, this, std::chrono::microseconds{0});

        cb(burst == nullptr ? V1_0::ErrorStatus::GENERAL_FAILURE : V1_0::ErrorStatus::NONE, burst);
        return hardware::Void();
    }

    hardware::Return<void> executeFenced(const V1_3::Request&,
                                         const hardware::hidl_vec<hardware::hidl_handle>&,
                                         V1_2::MeasureTiming measure,
                                         const V1_3::OptionalTimePoint&,
                                         const V1_3::OptionalTimeoutDuration&,
                                         const V1_3::OptionalTimeoutDuration&,
                                         executeFenced_cb callback) override {
        EXPECT_EQ(measure, V1_2::MeasureTiming::YES);
        if (hasBit(mSuccess, Success::PASS_BIT)) {
            dummyExecution();
            const auto expectedTiming = getExpectedTiming(mSuccess, true);
            sp<SampleFencedExecutionCallback> fencedExecutionCallback =
                    new SampleFencedExecutionCallback(expectedTiming.first, expectedTiming.second,
                                                      V1_3::ErrorStatus::NONE);
            callback(V1_3::ErrorStatus::NONE, hardware::hidl_handle(nullptr),
                     fencedExecutionCallback);
            return hardware::Void();
        }
        switch (mSuccess) {
            case Success::FAIL_WAIT:
                // Due to the limitation of the SampleDriver,
                // FAIL_WAIT behaves the same as FAIL_LAUNCH.
                // If the SampleDriver is updated to return real
                // sync fences, this must be updated.
                FALLTHROUGH_INTENDED;
            case Success::FAIL_LAUNCH:
                dummyExecution();
                callback(V1_3::ErrorStatus::GENERAL_FAILURE, hardware::hidl_handle(nullptr),
                         nullptr);
                return hardware::Void();
            default:
                ADD_FAILURE() << "Unexpected Success kind";
                return hardware::Void();
        }
    }

    // We can place the TestPreparedModelLatest system in a "pause" mode where
    // no execution will complete until the system is taken out of that mode.
    // Initially, the system is not in that mode.
    static void pauseExecutions(bool v) { mPauseExecutions.store(v); }

    // This function is only guaranteed to work in the following pattern:
    // Consider thread A as primary thread
    // - thread A: pauseExecutions(true);
    // - thread A: launch execution (as thread B)
    // - thread A: waitForExecutionToBegin(), block until call to dummyExecution by
    //                                        thread B makes mExecutionsInFlight nonzero
    // - thread B: dummyExecution(), which makes mExecutionsInFlight nonzero and blocks
    //                               until thread A calls pauseExecutions(false)
    // - thread A: waitForExecutionToBegin() returns
    // - thread A: pauseExecutions(false), allowing dummyExecution() on thread B to continue
    // - thread B: dummyExecution() zeroes mExecutionsInFlight and returns
    // - thread B: thread exits
    static void waitForExecutionToBegin() {
        CHECK(mPauseExecutions.load());
        while (mExecutionsInFlight.load() == 0) {
        }
    }

   private:
    Success mSuccess;

    static std::atomic<bool> mPauseExecutions;
    static std::atomic<unsigned int> mExecutionsInFlight;

    static void dummyExecution() {
        CHECK_EQ(mExecutionsInFlight.fetch_add(1), 0u) << "We do not support concurrent executions";
        while (mPauseExecutions.load()) {
        }
        mExecutionsInFlight.fetch_sub(1);
    }
};
std::atomic<bool> TestPreparedModelLatest::mPauseExecutions = false;
std::atomic<unsigned int> TestPreparedModelLatest::mExecutionsInFlight = 0;

using TestPreparedModel13 = TestPreparedModelLatest;

// Like TestPreparedModelLatest, but implementing 1.2
class TestPreparedModel12 : public V1_2::IPreparedModel {
   public:
    TestPreparedModel12(const HidlModel& model, const SampleDriver* driver, Success success)
        : mLatestPreparedModel(new TestPreparedModelLatest(model, driver, success)) {}

    hardware::Return<V1_0::ErrorStatus> execute(
            const V1_0::Request& request, const sp<V1_0::IExecutionCallback>& callback) override {
        return mLatestPreparedModel->execute(request, callback);
    }

    hardware::Return<V1_0::ErrorStatus> execute_1_2(
            const V1_0::Request& request, V1_2::MeasureTiming measure,
            const sp<V1_2::IExecutionCallback>& callback) override {
        return mLatestPreparedModel->execute_1_2(request, measure, callback);
    }

    hardware::Return<void> executeSynchronously(const V1_0::Request& request,
                                                V1_2::MeasureTiming measure,
                                                executeSynchronously_cb cb) override {
        return mLatestPreparedModel->executeSynchronously(request, measure, cb);
    }

    hardware::Return<void> configureExecutionBurst(
            const sp<V1_2::IBurstCallback>& callback,
            const MQDescriptorSync<V1_2::FmqRequestDatum>& requestChannel,
            const MQDescriptorSync<V1_2::FmqResultDatum>& resultChannel,
            configureExecutionBurst_cb cb) override {
        return mLatestPreparedModel->configureExecutionBurst(callback, requestChannel,
                                                             resultChannel, cb);
    }

   private:
    const sp<V1_3::IPreparedModel> mLatestPreparedModel;
};

// Like TestPreparedModelLatest, but implementing 1.0
class TestPreparedModel10 : public V1_0::IPreparedModel {
   public:
    TestPreparedModel10(const HidlModel& model, const SampleDriver* driver, Success success)
        : mLatestPreparedModel(new TestPreparedModelLatest(model, driver, success)) {}

    hardware::Return<V1_0::ErrorStatus> execute(
            const V1_0::Request& request, const sp<V1_0::IExecutionCallback>& callback) override {
        return mLatestPreparedModel->execute(request, callback);
    }

   private:
    const sp<V1_3::IPreparedModel> mLatestPreparedModel;
};

// Behaves like SampleDriver, except that it produces customized IPrepareModel.
class TestDriver13 : public SampleDriver {
   public:
    TestDriver13(const std::string& name, Success success)
        : SampleDriver(name.c_str()), mSuccess(success) {}

    hardware::Return<void> getCapabilities_1_3(getCapabilities_1_3_cb _hidl_cb) override {
        android::nn::initVLogMask();
        V1_3::Capabilities capabilities = nn::makeCapabilities(0.75f);
        _hidl_cb(V1_3::ErrorStatus::NONE, capabilities);
        return hardware::Void();
    }

    hardware::Return<void> getSupportedOperations_1_3(const HidlModel& model,
                                                      getSupportedOperations_1_3_cb cb) override {
        if (nn::validateModel(model)) {
            std::vector<bool> supported(model.main.operations.size(), true);
            cb(V1_3::ErrorStatus::NONE, supported);
        } else {
            cb(V1_3::ErrorStatus::INVALID_ARGUMENT, {});
        }
        return hardware::Void();
    }

    hardware::Return<void> getSupportedOperations_1_2(const V1_2::Model& model,
                                                      getSupportedOperations_1_2_cb cb) override {
        if (nn::validateModel(model)) {
            std::vector<bool> supported(model.operations.size(), true);
            cb(V1_0::ErrorStatus::NONE, supported);
        } else {
            std::vector<bool> supported;
            cb(V1_0::ErrorStatus::INVALID_ARGUMENT, supported);
        }
        return hardware::Void();
    }

    hardware::Return<V1_3::ErrorStatus> prepareModel_1_3(
            const HidlModel& model, V1_1::ExecutionPreference, V1_3::Priority,
            const V1_3::OptionalTimePoint&, const hardware::hidl_vec<hardware::hidl_handle>&,
            const hardware::hidl_vec<hardware::hidl_handle>&, const nn::HalCacheToken&,
            const sp<V1_3::IPreparedModelCallback>& callback) override {
        callback->notify_1_3(V1_3::ErrorStatus::NONE,
                             new TestPreparedModel13(model, this, mSuccess));
        return V1_3::ErrorStatus::NONE;
    }

    hardware::Return<V1_0::ErrorStatus> prepareModel_1_2(
            const V1_2::Model& model, V1_1::ExecutionPreference,
            const hardware::hidl_vec<hardware::hidl_handle>&,
            const hardware::hidl_vec<hardware::hidl_handle>&, const nn::HalCacheToken&,
            const sp<V1_2::IPreparedModelCallback>& callback) override {
        callback->notify_1_2(V1_0::ErrorStatus::NONE,
                             new TestPreparedModel12(nn::convertToV1_3(model), this, mSuccess));
        return V1_0::ErrorStatus::NONE;
    }

    hardware::Return<V1_0::ErrorStatus> prepareModel_1_1(
            const V1_1::Model& model, V1_1::ExecutionPreference,
            const sp<V1_0::IPreparedModelCallback>& callback) override {
        callback->notify(V1_0::ErrorStatus::NONE,
                         new TestPreparedModel10(nn::convertToV1_3(model), this, mSuccess));
        return V1_0::ErrorStatus::NONE;
    }

    hardware::Return<V1_0::ErrorStatus> prepareModel(
            const V1_0::Model& model, const sp<V1_0::IPreparedModelCallback>& callback) override {
        return prepareModel_1_1(nn::convertToV1_1(model),
                                V1_1::ExecutionPreference::FAST_SINGLE_ANSWER, callback);
    }

   private:
    Success mSuccess;
};

// Like TestDriver, but implementing 1.1
class TestDriver11 : public V1_1::IDevice {
   public:
    TestDriver11(const std::string& name, Success success)
        : mLatestDriver(new TestDriver13(name, success)) {}
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

}  // namespace test_drivers

/*-- End   test drivers -------------------------------------------------------------------------*/

/*-- Begin timing tests -------------------------------------------------------------------------*/

namespace timing_tests {

using namespace test_drivers;

enum class DriverKind {
    CPU,
    OLD,  // too old to support timing (1.1 or earlier)
    NEW   // new enough to support timing (1.2 or later)
};

std::ostream& operator<<(std::ostream& os, DriverKind kind) {
    const char* names[] = {"CPU", "OLD", "NEW"};
    const uint32_t index = static_cast<uint32_t>(kind);
    CHECK(index < std::size(names));
    return os << names[index];
}

enum class Compute { ASYNC, SYNC, BURST, FENCED };

std::ostream& operator<<(std::ostream& os, Compute compute) {
    const char* names[] = {"ASYNC", "SYNC", "BURST", "FENCED"};
    const uint32_t index = static_cast<uint32_t>(compute);
    CHECK(index < std::size(names));
    return os << names[index];
}

class TimingTest : public IntrospectionControlTest,
                   public ::testing::WithParamInterface<std::tuple<DriverKind, Success, Compute>> {
   public:
    TimingTest()
        : kDriverKind(std::get<0>(GetParam())),
          kSuccess(std::get<1>(GetParam())),
          kCompute(std::get<2>(GetParam())) {}

   protected:
    const DriverKind kDriverKind;
    const Success kSuccess;
    const Compute kCompute;
};

TEST_P(TimingTest, Test) {
    // There's no straightforward way to force CPU execution to fail.
    ASSERT_EQ(kDriverKind == DriverKind::CPU, kSuccess == Success::PASS_CPU);

    // FAIL_WAIT only makes sense for ASYNC and FENCED.
    ASSERT_TRUE(kCompute == Compute::ASYNC || kCompute == Compute::FENCED ||
                kSuccess != Success::FAIL_WAIT);

    if (DeviceManager::get()->getUseCpuOnly() != (kDriverKind == DriverKind::CPU)) {
        // We don't have an elegant way to request the CPU driver.  Therefore,
        // we rely on our test framework to make the choice between CPU and
        // non-CPU.
        GTEST_SKIP();
    }

    createSimpleAddModel(&mModel);

    switch (kDriverKind) {
        case DriverKind::CPU: {
            // There should be only one driver -- the CPU
            const std::string& name = DeviceManager::get()->getDrivers()[0]->getName();
            ASSERT_TRUE(selectDeviceByName(name));
            break;
        }
        case DriverKind::OLD: {
            static const char name[] = "old";
            DeviceManager::get()->forTest_registerDevice(
                    nn::makeSharedDevice(name, new TestDriver11(name, kSuccess)));
            ASSERT_TRUE(selectDeviceByName(name));
            break;
        }
        case DriverKind::NEW: {
            static const char name[] = "new";
            DeviceManager::get()->forTest_registerDevice(
                    nn::makeSharedDevice(name, new TestDriver13(name, kSuccess)));
            ASSERT_TRUE(selectDeviceByName(name));
            break;
        }
        default:
            FAIL() << "Unexpected DriverKind";
    }

    EXPECT_EQ(prepareForExecution(true /*measureTiming*/), ANEURALNETWORKS_NO_ERROR);

    float input1[2] = {1.0f, 2.0f};
    float input2[2] = {3.0f, 4.0f};
    float output[2];
    EXPECT_EQ(ANeuralNetworksExecution_setInput(mExecution, 0, nullptr, input1, sizeof(input1)),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_setInput(mExecution, 1, nullptr, input2, sizeof(input2)),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_setOutput(mExecution, 0, nullptr, output, sizeof(output)),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_setMeasureTiming(mExecution, true),
              ANEURALNETWORKS_NO_ERROR);

    auto Check = [](bool expectPass, int result) {
        if (expectPass) {
            ASSERT_EQ(result, ANEURALNETWORKS_NO_ERROR);
        } else {
            ASSERT_NE(result, ANEURALNETWORKS_NO_ERROR);
        }
    };

    const bool isPass = hasBit(kSuccess, Success::PASS_BIT);
    const int expectedGetDurationResultCode =
            isPass ? ANEURALNETWORKS_NO_ERROR : ANEURALNETWORKS_BAD_STATE;

    const auto getDurationWhileRunning = [this] {
        if (kDriverKind == DriverKind::CPU) {
            // Testing DriverKind::CPU would require modifying the CPU execution
            // path to control execution completion, similarly to how this test
            // case does with TestPreparedModel::dummyExecution(). This does not
            // seem worthwhile -- it's intrusive into the runtime code solely
            // for the sake of testing, and we do not expect that the code paths
            // needed to ensure correct behavior of
            // ANeuralNetworksExecution_getDuration() on a running execution
            // would be any different for CPU than for actual drivers.
            return;
        }
        TestPreparedModelLatest::waitForExecutionToBegin();
        for (int durationCode :
             std::vector{ANEURALNETWORKS_DURATION_ON_HARDWARE, ANEURALNETWORKS_DURATION_IN_DRIVER,
                         ANEURALNETWORKS_FENCED_DURATION_ON_HARDWARE,
                         ANEURALNETWORKS_FENCED_DURATION_IN_DRIVER}) {
            uint64_t time;
            // Cannot query duration while execution is running
            EXPECT_EQ(ANeuralNetworksExecution_getDuration(mExecution, durationCode, &time),
                      ANEURALNETWORKS_BAD_STATE);
        }
    };

    switch (kCompute) {
        case Compute::ASYNC: {
            // Ideally what we'd like to do here is
            //
            //     Check(kSuccess != Success::FAIL_LAUNCH,
            //         ANeuralNetworksExecution_startCompute(mExecution, &mEvent));
            //     Check(isPass, ANeuralNetworksEvent_wait(mEvent));
            //
            // However, in the current implementation of the runtime, a launch
            // failure at the HAL level does not show up as a launch failure at
            // the NDK level ("startCompute"): The NNAPI runtime does not call a
            // driver until it (the runtime) begins execution, so a launch
            // failure at the HAL level looks like an execution failure at the
            // NDK level ("wait").
            SCOPED_TRACE("ASYNC startCompute");
            TestPreparedModelLatest::pauseExecutions(true);
            Check(true,  // rather than kSuccess != Success::FAIL_LAUNCH
                  ANeuralNetworksExecution_startCompute(mExecution, &mEvent));
            getDurationWhileRunning();
            TestPreparedModelLatest::pauseExecutions(false);
            SCOPED_TRACE("ASYNC wait");
            Check(isPass, ANeuralNetworksEvent_wait(mEvent));
            break;
        }
        case Compute::SYNC: {
            SCOPED_TRACE("SYNC");
            TestPreparedModelLatest::pauseExecutions(true);
            std::thread run([this, Check, isPass] {
                Check(isPass, ANeuralNetworksExecution_compute(mExecution));
            });
            getDurationWhileRunning();
            TestPreparedModelLatest::pauseExecutions(false);
            run.join();
            break;
        }
        case Compute::BURST: {
            SCOPED_TRACE("BURST");
            ANeuralNetworksBurst* burst;
            ASSERT_EQ(ANeuralNetworksBurst_create(mCompilation, &burst), ANEURALNETWORKS_NO_ERROR);
            TestPreparedModelLatest::pauseExecutions(true);
            std::thread run([this, Check, isPass, burst] {
                Check(isPass, ANeuralNetworksExecution_burstCompute(mExecution, burst));
            });
            getDurationWhileRunning();
            TestPreparedModelLatest::pauseExecutions(false);
            run.join();
            ANeuralNetworksBurst_free(burst);
            break;
        }
        case Compute::FENCED: {
            SCOPED_TRACE("FENCED startComputeWithDependencies");
            TestPreparedModelLatest::pauseExecutions(true);

            // Note, due to the limitation of SampleDriver implementation, the call is synchronous.
            // If the SampleDriver is updated to return real sync fence, this must be updated.
            std::thread run([this, Check, isPass] {
                Check(isPass, ANeuralNetworksExecution_startComputeWithDependencies(
                                      mExecution, nullptr, 0, 0, &mEvent));
            });
            getDurationWhileRunning();
            TestPreparedModelLatest::pauseExecutions(false);
            run.join();
            SCOPED_TRACE("FENCED wait");
            Check(isPass, ANeuralNetworksEvent_wait(mEvent));
            break;
        }
        default:
            FAIL() << "unreachable";
    }

    uint64_t timeOnHardware, timeInDriver, timeOnHardwareFenced, timeInDriverFenced;
    EXPECT_EQ(ANeuralNetworksExecution_getDuration(mExecution, ANEURALNETWORKS_DURATION_ON_HARDWARE,
                                                   &timeOnHardware),
              expectedGetDurationResultCode);
    EXPECT_EQ(ANeuralNetworksExecution_getDuration(mExecution, ANEURALNETWORKS_DURATION_IN_DRIVER,
                                                   &timeInDriver),
              expectedGetDurationResultCode);
    EXPECT_EQ(
            ANeuralNetworksExecution_getDuration(
                    mExecution, ANEURALNETWORKS_FENCED_DURATION_ON_HARDWARE, &timeOnHardwareFenced),
            expectedGetDurationResultCode);
    EXPECT_EQ(ANeuralNetworksExecution_getDuration(
                      mExecution, ANEURALNETWORKS_FENCED_DURATION_IN_DRIVER, &timeInDriverFenced),
              expectedGetDurationResultCode);
    switch (kDriverKind) {
        case DriverKind::CPU: {
            // TODO: Should we require timing to be reported as 0?
            EXPECT_TRUE(timeOnHardware == 0 || timeOnHardware == UINT64_MAX)
                    << "timeOnHardware = " << timeOnHardware;
            EXPECT_TRUE(timeInDriver == 0 || timeInDriver == UINT64_MAX)
                    << "timeInDriver = " << timeOnHardware;
            EXPECT_TRUE(timeOnHardwareFenced == 0 || timeOnHardwareFenced == UINT64_MAX)
                    << "timeOnHardwareFenced = " << timeOnHardwareFenced;
            EXPECT_TRUE(timeInDriverFenced == 0 || timeInDriverFenced == UINT64_MAX)
                    << "timeInDriver = " << timeInDriverFenced;
            break;
        }
        case DriverKind::OLD: {
            EXPECT_EQ(timeOnHardware, UINT64_MAX);
            EXPECT_EQ(timeInDriver, UINT64_MAX);
            EXPECT_EQ(timeOnHardwareFenced, UINT64_MAX);
            EXPECT_EQ(timeInDriverFenced, UINT64_MAX);
            break;
        }
        case DriverKind::NEW: {
            auto microsToNanos = [](uint64_t micros) {
                constexpr uint64_t kNanosPerMicro = 1000;
                return micros == UINT64_MAX ? UINT64_MAX : kNanosPerMicro * micros;
            };
            auto expectedTiming = getExpectedTiming(kSuccess, kCompute == Compute::FENCED);
            EXPECT_EQ(timeOnHardware, microsToNanos(expectedTiming.first.timeOnDevice));
            EXPECT_EQ(timeInDriver, microsToNanos(expectedTiming.first.timeInDriver));
            EXPECT_EQ(timeOnHardwareFenced, microsToNanos(expectedTiming.second.timeOnDevice));
            EXPECT_EQ(timeInDriverFenced, microsToNanos(expectedTiming.second.timeInDriver));
            break;
        }
        default:
            FAIL() << "unreachable";
    }
    if (kCompute != Compute::FENCED) {
        EXPECT_EQ(timeOnHardware, timeOnHardwareFenced);
        EXPECT_EQ(timeInDriver, timeInDriverFenced);
    }
    auto expectTimingLe = [](uint64_t a, const char* aName, uint64_t b, const char* bName) {
        if (a != UINT64_MAX && b != UINT64_MAX) {
            EXPECT_LE(a, b) << aName << " exceeds " << bName;
        }
    };
#define EXPECT_TIMING_LE(a, b) expectTimingLe(a, #a, b, #b)
    EXPECT_TIMING_LE(timeOnHardware, timeInDriver);
    EXPECT_TIMING_LE(timeOnHardwareFenced, timeInDriverFenced);

    EXPECT_TIMING_LE(timeOnHardwareFenced, timeOnHardware);
    EXPECT_TIMING_LE(timeInDriverFenced, timeInDriver);
#undef EXPECT_TIMING_LE
}

auto kTimingTestUnfencedValues = ::testing::Values(
        // NOTE: We cannot force CPU execution to fail
        std::make_tuple(DriverKind::CPU, Success::PASS_CPU, Compute::ASYNC),
        std::make_tuple(DriverKind::CPU, Success::PASS_CPU, Compute::SYNC),
        std::make_tuple(DriverKind::CPU, Success::PASS_CPU, Compute::BURST),

        // NOTE: OLD driver does not provide timing
        std::make_tuple(DriverKind::OLD, Success::PASS_NEITHER, Compute::ASYNC),
        std::make_tuple(DriverKind::OLD, Success::PASS_NEITHER, Compute::SYNC),
        std::make_tuple(DriverKind::OLD, Success::PASS_NEITHER, Compute::BURST),

        std::make_tuple(DriverKind::OLD, Success::FAIL_LAUNCH, Compute::ASYNC),
        std::make_tuple(DriverKind::OLD, Success::FAIL_LAUNCH, Compute::SYNC),
        std::make_tuple(DriverKind::OLD, Success::FAIL_LAUNCH, Compute::BURST),

        // NOTE: Only ASYNC is paired with a wait
        std::make_tuple(DriverKind::OLD, Success::FAIL_WAIT, Compute::ASYNC),

        std::make_tuple(DriverKind::NEW, Success::PASS_NEITHER, Compute::ASYNC),
        std::make_tuple(DriverKind::NEW, Success::PASS_NEITHER, Compute::SYNC),
        std::make_tuple(DriverKind::NEW, Success::PASS_NEITHER, Compute::BURST),

        std::make_tuple(DriverKind::NEW, Success::PASS_DEVICE, Compute::ASYNC),
        std::make_tuple(DriverKind::NEW, Success::PASS_DEVICE, Compute::SYNC),
        std::make_tuple(DriverKind::NEW, Success::PASS_DEVICE, Compute::BURST),

        std::make_tuple(DriverKind::NEW, Success::PASS_DRIVER, Compute::ASYNC),
        std::make_tuple(DriverKind::NEW, Success::PASS_DRIVER, Compute::SYNC),
        std::make_tuple(DriverKind::NEW, Success::PASS_DRIVER, Compute::BURST),

        std::make_tuple(DriverKind::NEW, Success::PASS_BOTH, Compute::ASYNC),
        std::make_tuple(DriverKind::NEW, Success::PASS_BOTH, Compute::SYNC),
        std::make_tuple(DriverKind::NEW, Success::PASS_BOTH, Compute::BURST),

        std::make_tuple(DriverKind::NEW, Success::FAIL_LAUNCH, Compute::ASYNC),
        std::make_tuple(DriverKind::NEW, Success::FAIL_LAUNCH, Compute::SYNC),
        std::make_tuple(DriverKind::NEW, Success::FAIL_LAUNCH, Compute::BURST),

        // NOTE: Only ASYNC is paired with a wait
        std::make_tuple(DriverKind::NEW, Success::FAIL_WAIT, Compute::ASYNC));

auto kTimingTestFencedValues = ::testing::Values(
        // NOTE: We cannot force CPU execution to fail
        std::make_tuple(DriverKind::CPU, Success::PASS_CPU, Compute::FENCED),

        // NOTE: OLD driver does not provide timing
        std::make_tuple(DriverKind::OLD, Success::PASS_NEITHER, Compute::FENCED),

        std::make_tuple(DriverKind::OLD, Success::FAIL_LAUNCH, Compute::FENCED),

        std::make_tuple(DriverKind::NEW, Success::PASS_NEITHER, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_DEVICE, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_DRIVER, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_BOTH, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_NEITHER_DEVICE, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_NEITHER_DRIVER, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_NEITHER_BOTH, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_DEVICE_DEVICE, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_DEVICE_DRIVER, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_DEVICE_BOTH, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_DRIVER_DEVICE, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_DRIVER_DRIVER, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_DRIVER_BOTH, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_BOTH_DEVICE, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_BOTH_DRIVER, Compute::FENCED),
        std::make_tuple(DriverKind::NEW, Success::PASS_BOTH_BOTH, Compute::FENCED),

        std::make_tuple(DriverKind::NEW, Success::FAIL_LAUNCH, Compute::FENCED));

INSTANTIATE_TEST_SUITE_P(Unfenced, TimingTest, kTimingTestUnfencedValues);
INSTANTIATE_TEST_SUITE_P(Fenced, TimingTest, kTimingTestFencedValues);

}  // namespace timing_tests

/*-- End   timing tests -------------------------------------------------------------------------*/

const float kSimpleCeiling = 2.0f;

void createAddMaxModel(WrapperModel* model, bool reverseOrder) {
    WrapperOperandType type0(WrapperType::TENSOR_FLOAT32, {2});
    WrapperOperandType type1(WrapperType::INT32, {});
    // Phase 1, operands
    auto op1 = model->addOperand(&type0);
    auto op2 = model->addOperand(&type0);
    auto act = model->addOperand(&type1);
    auto op3 = model->addOperand(&type0);
    auto op4 = model->addOperand(&type0);
    auto op5 = model->addOperand(&type0);
    // Phase 2, operations
    static int32_t act_init[] = {0};
    model->setOperandValue(act, act_init, sizeof(act_init));
    static float ceiling[] = {kSimpleCeiling, kSimpleCeiling};
    model->setOperandValue(op4, ceiling, sizeof(ceiling));
    if (reverseOrder) {
        // In this case, add MAXIMUM first, but the execution order is still ADD -> MAXIMUM.
        model->addOperation(ANEURALNETWORKS_MAXIMUM, {op3, op4}, {op5});
        model->addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
    } else {
        model->addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
        model->addOperation(ANEURALNETWORKS_MAXIMUM, {op3, op4}, {op5});
    }
    // Phase 3, inputs and outputs
    model->identifyInputsAndOutputs({op1, op2}, {op5});
    model->finish();
    ASSERT_TRUE(model->isValid());
}

TEST_F(IntrospectionControlTest, SlicingAddMax) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    using namespace test_drivers;

    static const char name[] = "driver11";
    DeviceManager::get()->forTest_registerDevice(
            nn::makeSharedDevice(name, new TestDriver11(name, Success::PASS_BOTH)));
    ASSERT_TRUE(selectDeviceByName(name));

    createAddMaxModel(&mModel, false);
    EXPECT_TRUE(isSupportedOpListExpected({true, false}));
}

TEST_F(IntrospectionControlTest, SlicingMaxAdd) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    using namespace test_drivers;

    static const char name[] = "driver11";
    DeviceManager::get()->forTest_registerDevice(
            nn::makeSharedDevice(name, new TestDriver11(name, Success::PASS_BOTH)));
    ASSERT_TRUE(selectDeviceByName(name));

    createAddMaxModel(&mModel, true);
    EXPECT_TRUE(isSupportedOpListExpected({false, true}));
}

const float kSimpleMultiplier = 2.0f;

void createAddMulModel(WrapperModel* model, bool reverseOrder) {
    WrapperOperandType type0(WrapperType::TENSOR_FLOAT32, {2});
    WrapperOperandType type1(WrapperType::INT32, {});
    // Phase 1, operands
    auto op1 = model->addOperand(&type0);
    auto op2 = model->addOperand(&type0);
    auto act = model->addOperand(&type1);
    auto op3 = model->addOperand(&type0);
    auto op4 = model->addOperand(&type0);
    auto op5 = model->addOperand(&type0);
    // Phase 2, operations
    static int32_t act_init[] = {0};
    model->setOperandValue(act, act_init, sizeof(act_init));
    static float multiplier[] = {kSimpleMultiplier, kSimpleMultiplier};
    model->setOperandValue(op4, multiplier, sizeof(multiplier));
    if (reverseOrder) {
        // In this case, add MUL first, but the execution order is still ADD -> MUL.
        model->addOperation(ANEURALNETWORKS_MUL, {op3, op4, act}, {op5});
        model->addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
    } else {
        model->addOperation(ANEURALNETWORKS_ADD, {op1, op2, act}, {op3});
        model->addOperation(ANEURALNETWORKS_MUL, {op3, op4, act}, {op5});
    }
    // Phase 3, inputs and outputs
    model->identifyInputsAndOutputs({op1, op2}, {op5});
    model->finish();
    ASSERT_TRUE(model->isValid());
}

TEST_F(IntrospectionControlTest, SlicingFullySupported) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    using namespace test_drivers;

    static const char name[] = "driver11";
    DeviceManager::get()->forTest_registerDevice(
            nn::makeSharedDevice(name, new TestDriver11(name, Success::PASS_BOTH)));
    ASSERT_TRUE(selectDeviceByName(name));

    createAddMulModel(&mModel, false);
    EXPECT_TRUE(isSupportedOpListExpected({true, true}));
}

void createCondModel(WrapperModel* model, bool dynamicRank) {
    const auto dimensions = dynamicRank ? std::vector<uint32_t>{} : std::vector<uint32_t>{1};
    WrapperOperandType floatType(WrapperType::TENSOR_FLOAT32, dimensions);
    WrapperOperandType boolType(WrapperType::TENSOR_BOOL8, {1});
    // Phase 1, operands
    auto op1 = model->addOperand(&floatType);
    auto op2 = model->addOperand(&boolType);
    // Phase 2, operations
    model->addOperation(ANEURALNETWORKS_LESS, {op1, op1}, {op2});
    // Phase 3, inputs and outputs
    model->identifyInputsAndOutputs({op1}, {op2});
    model->finish();
}

void addReluOperation(WrapperModel* model, std::vector<uint32_t>* modelInputIndexes,
                      std::vector<uint32_t>* modelOutputIndexes, bool dynamicRank) {
    const auto dimensions = dynamicRank ? std::vector<uint32_t>{} : std::vector<uint32_t>{1};
    WrapperOperandType type(WrapperType::TENSOR_FLOAT32, dimensions);
    // Phase 1, operands
    auto op1 = model->addOperand(&type);
    auto op2 = model->addOperand(&type);
    // Phase 2, operations
    model->addOperation(ANEURALNETWORKS_RELU, {op1}, {op2});
    // Phase 3, inputs and outputs
    modelInputIndexes->push_back(op1);
    modelOutputIndexes->push_back(op2);
}

void createReluModel(WrapperModel* model, bool dynamicRank) {
    std::vector<uint32_t> modelInputIndexes, modelOutputIndexes;
    addReluOperation(model, &modelInputIndexes, &modelOutputIndexes, dynamicRank);
    model->identifyInputsAndOutputs(modelInputIndexes, modelOutputIndexes);
    model->finish();
}

void addWhileOperation(std::vector<WrapperModel>* extraModels, WrapperModel* mainModel,
                       std::vector<uint32_t>* modelInputIndexes,
                       std::vector<uint32_t>* modelOutputIndexes, bool dynamicRank) {
    const auto dimensions = dynamicRank ? std::vector<uint32_t>{} : std::vector<uint32_t>{1};
    WrapperOperandType floatType(WrapperType::TENSOR_FLOAT32, dimensions);
    WrapperOperandType modelType(WrapperType::MODEL, {});

    extraModels->emplace_back();
    extraModels->emplace_back();
    WrapperModel* condModel = &extraModels->at(extraModels->size() - 2);
    WrapperModel* bodyModel = &extraModels->at(extraModels->size() - 1);
    createCondModel(condModel, dynamicRank);
    createReluModel(bodyModel, dynamicRank);
    ASSERT_TRUE(condModel->isValid());
    ASSERT_TRUE(bodyModel->isValid());

    // Phase 1, operands
    const uint32_t op1 = mainModel->addOperand(&modelType);
    const uint32_t op2 = mainModel->addOperand(&modelType);
    const uint32_t op3 = mainModel->addOperand(&floatType);
    const uint32_t op4 = mainModel->addOperand(&floatType);
    mainModel->setOperandValueFromModel(op1, condModel);
    mainModel->setOperandValueFromModel(op2, bodyModel);
    // Phase 2, operations
    mainModel->addOperation(ANEURALNETWORKS_WHILE, {op1, op2, op3}, {op4});
    // Phase 3, inputs and outputs
    modelInputIndexes->push_back(op3);
    modelOutputIndexes->push_back(op4);
}

void createReluStaticWhileModel(std::vector<WrapperModel>* extraModels, WrapperModel* mainModel) {
    std::vector<uint32_t> modelInputIndexes, modelOutputIndexes;

    // Operation supported in Android API level 27
    addReluOperation(mainModel, &modelInputIndexes, &modelOutputIndexes, /*dynamicRank=*/false);
    // Operation supported in Android API level 30
    addWhileOperation(extraModels, mainModel, &modelInputIndexes, &modelOutputIndexes,
                      /*dynamicRank=*/false);

    mainModel->identifyInputsAndOutputs(modelInputIndexes, modelOutputIndexes);
    mainModel->finish();
    ASSERT_TRUE(mainModel->isValid());
}

TEST_F(IntrospectionControlTest, ControlFlowNotSupported) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    using namespace test_drivers;

    static const char name[] = "driver11";
    DeviceManager::get()->forTest_registerDevice(
            nn::makeSharedDevice(name, new TestDriver11(name, Success::PASS_BOTH)));
    ASSERT_TRUE(selectDeviceByName(name));

    std::vector<WrapperModel> extraModels;
    createReluStaticWhileModel(&extraModels, &mModel);
    EXPECT_TRUE(isSupportedOpListExpected({true, false}));

    // Clear mModel early because it may reference `extraModels`.
    mModel = WrapperModel{};
}

TEST_F(IntrospectionControlTest, ControlFlowSupported) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    using namespace test_drivers;

    static const char name[] = "driver13";
    DeviceManager::get()->forTest_registerDevice(
            nn::makeSharedDevice(name, new TestDriver13(name, Success::PASS_BOTH)));
    ASSERT_TRUE(selectDeviceByName(name));

    std::vector<WrapperModel> extraModels;
    createReluStaticWhileModel(&extraModels, &mModel);
    EXPECT_TRUE(isSupportedOpListExpected({true, true}));

    // Clear mModel early because it may reference `extraModels`.
    mModel = WrapperModel{};
}

void createStaticWhileDynamicWhileModel(std::vector<WrapperModel>* extraModels,
                                        WrapperModel* mainModel) {
    std::vector<uint32_t> modelInputIndexes, modelOutputIndexes;

    // Operation supported in Android API level 30
    addWhileOperation(extraModels, mainModel, &modelInputIndexes, &modelOutputIndexes,
                      /*dynamicRank=*/false);
    // Operation supported only by NNAPI runtime
    addWhileOperation(extraModels, mainModel, &modelInputIndexes, &modelOutputIndexes,
                      /*dynamicRank=*/true);

    mainModel->identifyInputsAndOutputs(modelInputIndexes, modelOutputIndexes);
    mainModel->finish();
    ASSERT_TRUE(mainModel->isValid());
}

TEST_F(IntrospectionControlTest, ControlFlowFailedToSlice) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    using namespace test_drivers;

    static const char name[] = "driver13";
    DeviceManager::get()->forTest_registerDevice(
            nn::makeSharedDevice(name, new TestDriver13(name, Success::PASS_BOTH)));
    ASSERT_TRUE(selectDeviceByName(name));

    std::vector<WrapperModel> extraModels;
    createStaticWhileDynamicWhileModel(&extraModels, &mModel);
    EXPECT_TRUE(isSupportedOpListExpected({false, false}));

    // Clear mModel early because it may reference `extraModels`.
    mModel = WrapperModel{};
}

// TODO(miaowang): add a test to make sure ANNCompilation_create() has CPU
// fallback.
// This test verifies that a device that could only handle ADD would correctly report that an
// ADD->MUL model could not be fully supported.
TEST_F(IntrospectionControlTest, PartialModelNotSupported) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    createAddMulModel(&mModel, false);

    std::string addOnlyDriver = "test-onlyAdd";
    std::vector<bool> addOnlyOp(android::nn::kNumberOfOperationTypes, false);
    addOnlyOp[ANEURALNETWORKS_ADD] = true;

    registerDevices({{addOnlyDriver, 0.9, addOnlyOp}});

    EXPECT_TRUE(selectDeviceByName(addOnlyDriver));
    EXPECT_TRUE(isSupportedOpListExpected({true, false}));

    ANeuralNetworksModel* modelHandle = mModel.getHandle();
    EXPECT_EQ(ANeuralNetworksCompilation_createForDevices(modelHandle, mDevices.data(),
                                                          mDevices.size(), &mCompilation),
              ANEURALNETWORKS_NO_ERROR);
    // The compilation must fail as there is no fallback when using
    // Introspection API.
    EXPECT_NE(ANeuralNetworksCompilation_finish(mCompilation), ANEURALNETWORKS_NO_ERROR);
}

// This test verifies that a device that could only handle ADD would correctly report that an
// ADD->MUL model could not be fully supported. Also verifies that the indices of returned
// supported op list correctly map to the order of operations being added by the user.
TEST_F(IntrospectionControlTest, PartialModelNotSupportedOrder) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    createAddMulModel(&mModel, true);

    std::string addOnlyDriver = "test-onlyAdd";
    std::vector<bool> addOnlyOp(android::nn::kNumberOfOperationTypes, false);
    addOnlyOp[ANEURALNETWORKS_ADD] = true;

    registerDevices({{addOnlyDriver, 0.9, addOnlyOp}});

    EXPECT_TRUE(selectDeviceByName(addOnlyDriver));
    EXPECT_TRUE(isSupportedOpListExpected({false, true}));
}

// TODO(miaowang): update the test to make sure the model is actually running on the test devices.
// This test verifies that an ADD->MUL model is able to run on two selected devices that together
// can handle all operations.
TEST_F(IntrospectionControlTest, ModelNeedTwoDevices) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    createAddMulModel(&mModel, false);

    std::string addOnlyDriver = "test-onlyAdd";
    std::vector<bool> addOnlyOp(android::nn::kNumberOfOperationTypes, false);
    addOnlyOp[ANEURALNETWORKS_ADD] = true;

    std::string mulOnlyDriver = "test-onlyMul";
    std::vector<bool> mulOnlyOp(android::nn::kNumberOfOperationTypes, false);
    mulOnlyOp[ANEURALNETWORKS_MUL] = true;

    registerDevices({
            {addOnlyDriver, 0.9, addOnlyOp},
            {mulOnlyDriver, 0.9, mulOnlyOp},
    });

    EXPECT_TRUE(selectDeviceByName(addOnlyDriver));
    EXPECT_TRUE(selectDeviceByName(mulOnlyDriver));
    EXPECT_TRUE(isSupportedOpListExpected({true, true}));
    EXPECT_EQ(prepareForExecution(), ANEURALNETWORKS_NO_ERROR);

    float input1[2] = {1.0f, 2.0f};
    float input2[2] = {3.0f, 4.0f};
    float output[2];
    EXPECT_EQ(ANeuralNetworksExecution_setInput(mExecution, 0, nullptr, input1, sizeof(input1)),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_setInput(mExecution, 1, nullptr, input2, sizeof(input2)),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksExecution_setOutput(mExecution, 0, nullptr, output, sizeof(output)),
              ANEURALNETWORKS_NO_ERROR);

    EXPECT_EQ(ANeuralNetworksExecution_startCompute(mExecution, &mEvent), ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksEvent_wait(mEvent), ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(output[0], kSimpleMultiplier * (input1[0] + input2[0]));
    EXPECT_EQ(output[1], kSimpleMultiplier * (input1[1] + input2[1]));
}
}  // namespace

#if defined(NN_DEBUGGABLE) && !defined(NNTEST_ONLY_PUBLIC_API)

void forTest_setRuntimeFeatureLevel(int64_t level);  // defined in NeuralNetworks.cpp

namespace {
class WhiteboxFeatureLevelTest : public IntrospectionControlTest {
   protected:
    void TearDown() override {
        forTest_setRuntimeFeatureLevel(0);
        IntrospectionControlTest::TearDown();
    }

   public:
    enum DeviceLevel { V1_1, V1_3 };
    void trial(int64_t setRuntimeFeatureLevel, DeviceLevel deviceLevel,
               int64_t expectDeviceFeatureLevel);
};

void WhiteboxFeatureLevelTest::trial(int64_t setRuntimeFeatureLevel, DeviceLevel deviceLevel,
                                     int64_t expectDeviceFeatureLevel) {
    // This is needed before we have the CPU fallback path being treated as a Device.
    if (DeviceManager::get()->getUseCpuOnly()) {
        GTEST_SKIP();
    }

    using namespace test_drivers;

    forTest_setRuntimeFeatureLevel(setRuntimeFeatureLevel);

    static const char deviceName[] = "trial";
    auto newTestDriver = [deviceLevel]() -> V1_0::IDevice* {
        switch (deviceLevel) {
            case DeviceLevel::V1_1:
                return new TestDriver11(deviceName, Success::PASS_BOTH_BOTH);
            case DeviceLevel::V1_3:
                return new TestDriver13(deviceName, Success::PASS_BOTH_BOTH);
            default:
                assert(!"Unrecognized deviceLevel");
                return nullptr;
        }
    };
    DeviceManager::get()->forTest_registerDevice(nn::makeSharedDevice(deviceName, newTestDriver()));

    ASSERT_TRUE(selectDeviceByName(deviceName));
    int64_t deviceFeatureLevel;
    ASSERT_EQ(mDevices.size(), size_t(1));
    ASSERT_EQ(ANeuralNetworksDevice_getFeatureLevel(mDevices.front(), &deviceFeatureLevel),
              ANEURALNETWORKS_NO_ERROR);
    ASSERT_EQ(deviceFeatureLevel, expectDeviceFeatureLevel);
}

TEST_F(WhiteboxFeatureLevelTest, Default_V1_1) {
    trial(0, DeviceLevel::V1_1, ANEURALNETWORKS_FEATURE_LEVEL_2);
}

TEST_F(WhiteboxFeatureLevelTest, FL3_V1_1) {
    trial(ANEURALNETWORKS_FEATURE_LEVEL_3, DeviceLevel::V1_1, ANEURALNETWORKS_FEATURE_LEVEL_2);
}

TEST_F(WhiteboxFeatureLevelTest, FL2_V1_1) {
    trial(ANEURALNETWORKS_FEATURE_LEVEL_2, DeviceLevel::V1_1, ANEURALNETWORKS_FEATURE_LEVEL_2);
}

TEST_F(WhiteboxFeatureLevelTest, FL1_V1_1) {
    trial(ANEURALNETWORKS_FEATURE_LEVEL_1, DeviceLevel::V1_1, ANEURALNETWORKS_FEATURE_LEVEL_1);
}

TEST_F(WhiteboxFeatureLevelTest, Default_V1_3) {
    trial(0, DeviceLevel::V1_3, ANEURALNETWORKS_FEATURE_LEVEL_4);
}

TEST_F(WhiteboxFeatureLevelTest, FL5_V1_3) {
    trial(ANEURALNETWORKS_FEATURE_LEVEL_5, DeviceLevel::V1_3, ANEURALNETWORKS_FEATURE_LEVEL_4);
}

TEST_F(WhiteboxFeatureLevelTest, FL4_V1_3) {
    trial(ANEURALNETWORKS_FEATURE_LEVEL_4, DeviceLevel::V1_3, ANEURALNETWORKS_FEATURE_LEVEL_4);
}

TEST_F(WhiteboxFeatureLevelTest, FL3_V1_3) {
    trial(ANEURALNETWORKS_FEATURE_LEVEL_3, DeviceLevel::V1_3, ANEURALNETWORKS_FEATURE_LEVEL_3);
}

TEST_F(WhiteboxFeatureLevelTest, FL2_V1_3) {
    trial(ANEURALNETWORKS_FEATURE_LEVEL_2, DeviceLevel::V1_3, ANEURALNETWORKS_FEATURE_LEVEL_2);
}
}  // namespace

#endif  // defined(NN_DEBUGGABLE) && !defined(NNTEST_ONLY_PUBLIC_API)
