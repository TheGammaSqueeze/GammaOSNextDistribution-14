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

#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <aidl/android/frameworks/automotive/powerpolicy/BnCarPowerPolicyChangeCallback.h>
#include <aidl/android/frameworks/automotive/powerpolicy/CarPowerPolicy.h>
#include <aidl/android/frameworks/automotive/powerpolicy/CarPowerPolicyFilter.h>
#include <aidl/android/frameworks/automotive/powerpolicy/ICarPowerPolicyServer.h>
#include <aidl/android/frameworks/automotive/powerpolicy/PowerComponent.h>
#include <android-base/stringprintf.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_status.h>
#include <binder/IBinder.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>

namespace {

using ::aidl::android::frameworks::automotive::powerpolicy::BnCarPowerPolicyChangeCallback;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicyFilter;
using ::aidl::android::frameworks::automotive::powerpolicy::ICarPowerPolicyServer;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;
using ::android::OK;
using ::android::ProcessState;
using ::android::status_t;
using ::android::String16;
using ::android::UNKNOWN_ERROR;
using ::android::base::StringPrintf;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

class MockPowerPolicyChangeCallback : public BnCarPowerPolicyChangeCallback {
   public:
    MockPowerPolicyChangeCallback() {}

    ScopedAStatus onPolicyChanged([[maybe_unused]] const CarPowerPolicy& policy) override {
        return ScopedAStatus::ok();
    }
};

}  // namespace

class PowerPolicyAidlTest : public ::testing::TestWithParam<std::string> {
   public:
    virtual void SetUp() override {
        SpAIBinder binder(AServiceManager_getService(GetParam().c_str()));
        ASSERT_NE(binder.get(), nullptr);
        powerPolicyServer = ICarPowerPolicyServer::fromBinder(binder);
    }

    std::shared_ptr<ICarPowerPolicyServer> powerPolicyServer;
};

TEST_P(PowerPolicyAidlTest, TestGetCurrentPowerPolicy) {
    CarPowerPolicy policy;

    ScopedAStatus status = powerPolicyServer->getCurrentPowerPolicy(&policy);

    ASSERT_TRUE(status.isOk() || status.getServiceSpecificError() == EX_ILLEGAL_STATE);
}

TEST_P(PowerPolicyAidlTest, TestGetPowerComponentState) {
    bool state;
    for (const auto componentId : ndk::enum_range<PowerComponent>()) {
        if (componentId >= PowerComponent::MINIMUM_CUSTOM_COMPONENT_VALUE) {
            continue;
        }
        ScopedAStatus status = powerPolicyServer->getPowerComponentState(componentId, &state);
        std::string errMsg = StringPrintf("Getting state of component(%d) fails", componentId);
        ASSERT_TRUE(status.isOk()) << errMsg;
    }
}

TEST_P(PowerPolicyAidlTest, TestGetPowerComponentState_invalidComponent) {
    bool state;
    PowerComponent invalidComponent = static_cast<PowerComponent>(-1);

    ScopedAStatus status = powerPolicyServer->getPowerComponentState(invalidComponent, &state);

    ASSERT_FALSE(status.isOk());
}

TEST_P(PowerPolicyAidlTest, TestRegisterCallback) {
    std::shared_ptr<MockPowerPolicyChangeCallback> callback =
        ndk::SharedRefBase::make<MockPowerPolicyChangeCallback>();
    CarPowerPolicyFilter filter;
    filter.components.push_back(PowerComponent::AUDIO);

    ScopedAStatus status = powerPolicyServer->registerPowerPolicyChangeCallback(callback, filter);

    ASSERT_TRUE(status.isOk());

    status = powerPolicyServer->unregisterPowerPolicyChangeCallback(callback);

    ASSERT_TRUE(status.isOk());
}

TEST_P(PowerPolicyAidlTest, TestRegisterCallback_doubleRegistering) {
    std::shared_ptr<MockPowerPolicyChangeCallback> callback =
        ndk::SharedRefBase::make<MockPowerPolicyChangeCallback>();
    CarPowerPolicyFilter filter;
    filter.components.push_back(PowerComponent::AUDIO);

    ScopedAStatus status = powerPolicyServer->registerPowerPolicyChangeCallback(callback, filter);

    ASSERT_TRUE(status.isOk());

    status = powerPolicyServer->registerPowerPolicyChangeCallback(callback, filter);

    ASSERT_FALSE(status.isOk());
    ASSERT_EQ(status.getServiceSpecificError(), EX_ILLEGAL_ARGUMENT);
}

TEST_P(PowerPolicyAidlTest, TestUnegisterNotRegisteredCallback) {
    std::shared_ptr<MockPowerPolicyChangeCallback> callback =
        ndk::SharedRefBase::make<MockPowerPolicyChangeCallback>();

    ScopedAStatus status = powerPolicyServer->unregisterPowerPolicyChangeCallback(callback);

    ASSERT_FALSE(status.isOk());
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(PowerPolicyAidlTest);
INSTANTIATE_TEST_SUITE_P(
    CarPowerPolicyServer, PowerPolicyAidlTest,
    ::testing::ValuesIn(android::getAidlHalInstanceNames(ICarPowerPolicyServer::descriptor)),
    android::PrintInstanceNameToString);

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    ProcessState::self()->setThreadPoolMaxThreadCount(1);
    ProcessState::self()->startThreadPool();
    return RUN_ALL_TESTS();
}
