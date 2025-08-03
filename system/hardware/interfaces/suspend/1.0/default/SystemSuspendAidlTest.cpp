/*
 * Copyright 2020 The Android Open Source Project
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

#include <android/system/suspend/BnSuspendCallback.h>
#include <android/system/suspend/BnWakelockCallback.h>
#include <android/system/suspend/ISuspendControlService.h>
#include <binder/IServiceManager.h>
#include <gtest/gtest.h>

using android::sp;
using android::status_t;
using android::String16;
using android::binder::Status;
using android::system::suspend::BnSuspendCallback;
using android::system::suspend::BnWakelockCallback;
using android::system::suspend::ISuspendControlService;

static const std::string kTestWakelockName("test_lock");
static const std::string kTestWakelockName2("test_lock2");

class WakelockCallback : public BnWakelockCallback {
   public:
    Status notifyAcquired() override { return Status::ok(); };
    Status notifyReleased() override { return Status::ok(); };
};

class WakeupCallback : public BnSuspendCallback {
   public:
    Status notifyWakeup([[maybe_unused]] bool success,
                        [[maybe_unused]] const std::vector<std::string>& wakeupReasons) override {
        return Status::ok();
    }
};

class SystemSuspendTest : public testing::Test {
   public:
    sp<ISuspendControlService> suspendControlService = nullptr;

    virtual void SetUp() override {
        status_t err = android::getService<ISuspendControlService>(String16("suspend_control"),
                                                                   &suspendControlService);
        ASSERT_EQ(err, android::OK);
        ASSERT_NE(suspendControlService, nullptr);
    }
};

TEST_F(SystemSuspendTest, RegisterWakeupCallback) {
    bool isRegistered = false;

    Status ret = suspendControlService->registerCallback(new WakeupCallback(), &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);
}

TEST_F(SystemSuspendTest, RegisterMultipleWakeupCallback) {
    bool isRegistered = false;

    Status ret = suspendControlService->registerCallback(new WakeupCallback(), &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);

    isRegistered = false;
    ret = suspendControlService->registerCallback(new WakeupCallback(), &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);
}

TEST_F(SystemSuspendTest, RegisterWakelockCallback) {
    bool isRegistered = false;

    Status ret = suspendControlService->registerWakelockCallback(new WakelockCallback(),
                                                                 kTestWakelockName, &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);
}

TEST_F(SystemSuspendTest, RegisterSameWakelockCallbackWithSameName) {
    bool isRegistered = false;
    sp<WakelockCallback> callback = new WakelockCallback();

    Status ret =
        suspendControlService->registerWakelockCallback(callback, kTestWakelockName, &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);

    isRegistered = false;
    ret =
        suspendControlService->registerWakelockCallback(callback, kTestWakelockName, &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(false, isRegistered);
}

TEST_F(SystemSuspendTest, RegisterSameWakelockCallbackWithDifferentNames) {
    bool isRegistered = false;
    sp<WakelockCallback> callback = new WakelockCallback();

    Status ret =
        suspendControlService->registerWakelockCallback(callback, kTestWakelockName, &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);

    isRegistered = false;
    ret = suspendControlService->registerWakelockCallback(callback, kTestWakelockName2,
                                                          &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);
}

TEST_F(SystemSuspendTest, RegisterDifferentWakelockCallbacksWithSameName) {
    bool isRegistered = false;
    sp<WakelockCallback> callback1 = new WakelockCallback();
    sp<WakelockCallback> callback2 = new WakelockCallback();

    Status ret = suspendControlService->registerWakelockCallback(callback1, kTestWakelockName,
                                                                 &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);

    isRegistered = false;
    ret = suspendControlService->registerWakelockCallback(callback2, kTestWakelockName,
                                                          &isRegistered);
    ASSERT_TRUE(ret.isOk());
    EXPECT_EQ(true, isRegistered);
}
