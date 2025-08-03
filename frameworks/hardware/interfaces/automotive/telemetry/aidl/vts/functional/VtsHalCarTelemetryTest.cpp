/*
 * Copyright (C) 2021 The Android Open Source Project
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
#define LOG_TAG "cartelemetryd_hal_aidl_test"

#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <aidl/android/frameworks/automotive/telemetry/BnCarTelemetryCallback.h>
#include <aidl/android/frameworks/automotive/telemetry/CallbackConfig.h>
#include <aidl/android/frameworks/automotive/telemetry/CarData.h>
#include <aidl/android/frameworks/automotive/telemetry/ICarTelemetry.h>
#include <android-base/logging.h>
#include <android/binder_auto_utils.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/binder_status.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace telemetry {

using ::aidl::android::frameworks::automotive::telemetry::BnCarTelemetryCallback;
using ::aidl::android::frameworks::automotive::telemetry::CallbackConfig;
using ::aidl::android::frameworks::automotive::telemetry::CarData;
using ::aidl::android::frameworks::automotive::telemetry::ICarTelemetry;
using ::ndk::ScopedAStatus;

class MockCarTelemetryCallback : public BnCarTelemetryCallback {
   public:
    MockCarTelemetryCallback() {}

    ScopedAStatus onChange([[maybe_unused]] const std::vector<int32_t>& ids) override {
        return ScopedAStatus::ok();
    }
};

CallbackConfig buildConfig(const std::vector<int32_t>& ids) {
    CallbackConfig config;
    config.carDataIds = ids;
    return config;
}

CarData buildCarData(int id, const std::vector<uint8_t>& content) {
    CarData msg;
    msg.id = id;
    msg.content = content;
    return msg;
}

class CarTelemetryTest : public testing::TestWithParam<std::string> {
   public:
    virtual void SetUp() override {
        mService = ICarTelemetry::fromBinder(
            ndk::SpAIBinder(::AServiceManager_waitForService(GetParam().c_str())));
        ASSERT_NE(nullptr, mService.get()) << "Instance '" << GetParam() << "'' is not available.";
    }

    std::shared_ptr<ICarTelemetry> mService;
};

// TODO(b/182598466): Add verifying contents after adding internal AIDLs for reading
// TODO(b/182598466): Add test for size limit check
TEST_P(CarTelemetryTest, writeReturnsOk) {
    CarData msg = buildCarData(101, {1, 0, 1, 0});

    auto status = mService->write({msg});

    EXPECT_TRUE(status.isOk()) << status.getMessage();
}

TEST_P(CarTelemetryTest, AddCallbackReturnsOk) {
    CallbackConfig config = buildConfig({101, 102, 103});
    std::shared_ptr<MockCarTelemetryCallback> callback =
        ndk::SharedRefBase::make<MockCarTelemetryCallback>();

    auto status = mService->addCallback(config, callback);

    EXPECT_TRUE(status.isOk()) << status.getMessage();
}

TEST_P(CarTelemetryTest, AddCallbackErrorsWhenDuplicate) {
    CallbackConfig config = buildConfig({101, 102, 103});
    std::shared_ptr<MockCarTelemetryCallback> callback =
        ndk::SharedRefBase::make<MockCarTelemetryCallback>();
    mService->addCallback(config, callback);

    auto status = mService->addCallback(config, callback);

    EXPECT_FALSE(status.isOk()) << status.getMessage();
}

TEST_P(CarTelemetryTest, RemoveCallbackReturnsOk) {
    CallbackConfig config = buildConfig({101, 102, 103});
    std::shared_ptr<MockCarTelemetryCallback> callback =
        ndk::SharedRefBase::make<MockCarTelemetryCallback>();
    mService->addCallback(config, callback);

    auto status = mService->removeCallback(callback);

    EXPECT_TRUE(status.isOk()) << status.getMessage();
}

TEST_P(CarTelemetryTest, RemoveCallbackErrorsForNonexistentCallback) {
    std::shared_ptr<MockCarTelemetryCallback> callback =
        ndk::SharedRefBase::make<MockCarTelemetryCallback>();

    auto status = mService->removeCallback(callback);

    EXPECT_FALSE(status.isOk()) << status.getMessage();
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(CarTelemetryTest);
INSTANTIATE_TEST_SUITE_P(
    AutomotiveHal, CarTelemetryTest,
    testing::ValuesIn(::android::getAidlHalInstanceNames(ICarTelemetry::descriptor)),
    ::android::PrintInstanceNameToString);

}  // namespace telemetry
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    ABinderProcess_startThreadPool();
    return RUN_ALL_TESTS();
}
