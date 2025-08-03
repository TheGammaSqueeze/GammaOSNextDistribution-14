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
#include <aidl/android/automotive/watchdog/BnCarWatchdogClient.h>
#include <aidl/android/automotive/watchdog/BnResourceOveruseListener.h>
#include <aidl/android/automotive/watchdog/ICarWatchdog.h>
#include <aidl/android/automotive/watchdog/ResourceOveruseStats.h>
#include <aidl/android/automotive/watchdog/ResourceType.h>
#include <android-base/chrono_utils.h>
#include <android-base/properties.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <gmock/gmock.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>

#include <unistd.h>

#include <future>  // NOLINT(build/c++11)

using ::aidl::android::automotive::watchdog::BnCarWatchdogClient;
using ::aidl::android::automotive::watchdog::BnResourceOveruseListener;
using ::aidl::android::automotive::watchdog::ICarWatchdog;
using ::aidl::android::automotive::watchdog::ResourceOveruseStats;
using ::aidl::android::automotive::watchdog::ResourceType;
using ::aidl::android::automotive::watchdog::TimeoutLength;
using ::android::Condition;
using ::android::Mutex;
using ::android::OK;
using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;
using ::testing::_;
using ::testing::InitGoogleTest;
using ::testing::Invoke;
using ::testing::Return;
using ::testing::TestWithParam;
using ::testing::ValuesIn;

namespace {

bool isInQemu() {
    return android::base::GetBoolProperty("ro.boot.qemu", false) ||
            android::base::GetBoolProperty("ro.kernel.qemu", false);
}

// Emulators run on QEMU and tend to have significant worse performance than physical devices.
// In order for emulators to be as test compliant as possible, 15s wait time is used instead of the
// 6s to account for the emulator's poor performance.
const std::chrono::nanoseconds kMaxWatchdogPingWaitTimeNs = isInQemu() ? 15s : 6s;

class MockCarWatchdogClient : public BnCarWatchdogClient {
public:
    MockCarWatchdogClient() {}

    MOCK_METHOD(ScopedAStatus, checkIfAlive, (int32_t, TimeoutLength), (override));
    MOCK_METHOD(ScopedAStatus, prepareProcessTermination, (), (override));

    void expectCheckIfAlive() {
        EXPECT_CALL(*this, checkIfAlive(_, _))
                .WillOnce(Invoke(
                        [&](int32_t sessionId, TimeoutLength timeoutLength) -> ScopedAStatus {
                            Mutex::Autolock lock(mMutex);
                            mSessionId = sessionId;
                            mTimeoutLength = timeoutLength;
                            mCond.signal();
                            return ScopedAStatus::ok();
                        }));
    }

    void waitCheckIfAlive(TimeoutLength expectedTimeoutLength, int32_t* actualSessionId) {
        Mutex::Autolock lock(mMutex);
        ASSERT_THAT(mCond.waitRelative(mMutex, kMaxWatchdogPingWaitTimeNs.count()), OK);
        ASSERT_THAT(mTimeoutLength, expectedTimeoutLength);
        *actualSessionId = mSessionId;
    }

private:
    Mutex mMutex;
    Condition mCond GUARDED_BY(mMutex);
    int32_t mSessionId GUARDED_BY(mMutex);
    TimeoutLength mTimeoutLength GUARDED_BY(mMutex);
};

class MockResourceOveruseListener : public BnResourceOveruseListener {
public:
    MockResourceOveruseListener() {}
    ~MockResourceOveruseListener() {}

    MOCK_METHOD(ndk::ScopedAStatus, onOveruse,
                (const aidl::android::automotive::watchdog::ResourceOveruseStats&), (override));
};

}  // namespace

class WatchdogAidlTest : public TestWithParam<std::string> {
public:
    void SetUp() override {
        watchdogServer = ICarWatchdog::fromBinder(
                ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));
        ASSERT_NE(watchdogServer.get(), nullptr);
    }

    std::shared_ptr<ICarWatchdog> watchdogServer;
};

TEST_P(WatchdogAidlTest, TestWatchdogClient) {
    std::shared_ptr<MockCarWatchdogClient> mockClient =
            SharedRefBase::make<MockCarWatchdogClient>();
    mockClient->expectCheckIfAlive();
    ScopedAStatus status =
            watchdogServer->registerClient(mockClient, TimeoutLength::TIMEOUT_CRITICAL);
    ASSERT_TRUE(status.isOk()) << "Failed to register client: " << status.getMessage();
    int32_t sessionId;
    ASSERT_NO_FATAL_FAILURE(
            mockClient->waitCheckIfAlive(TimeoutLength::TIMEOUT_CRITICAL, &sessionId));
    status = watchdogServer->tellClientAlive(mockClient, sessionId);
    ASSERT_TRUE(status.isOk()) << "Failed to tell client alive: " << status.getMessage();
    status = watchdogServer->unregisterClient(mockClient);
    ASSERT_TRUE(status.isOk()) << "Failed to unregister client: " << status.getMessage();
}

TEST_P(WatchdogAidlTest, TestFailsRegisterClientWithNullptrClient) {
    ASSERT_FALSE(watchdogServer->registerClient(nullptr, TimeoutLength::TIMEOUT_CRITICAL).isOk())
            << "Should fail to register null client";
}

TEST_P(WatchdogAidlTest, TestFailsToTellClientAliveForNotRegisteredClient) {
    std::shared_ptr<MockCarWatchdogClient> mockClient =
            SharedRefBase::make<MockCarWatchdogClient>();
    ASSERT_FALSE(watchdogServer->tellClientAlive(mockClient, 0).isOk())
            << "Should fail tell client alive for not registered client";
}

TEST_P(WatchdogAidlTest, TestFailsToUnRegisterNotRegisteredClient) {
    std::shared_ptr<MockCarWatchdogClient> mockClient =
            SharedRefBase::make<MockCarWatchdogClient>();
    ASSERT_FALSE(watchdogServer->unregisterClient(mockClient).isOk())
            << "Should fail to unregister not registered client";
}

TEST_P(WatchdogAidlTest, TestResourceOveruseListener) {
    std::shared_ptr<MockResourceOveruseListener> mockListener =
            SharedRefBase::make<MockResourceOveruseListener>();
    ScopedAStatus status =
            watchdogServer->addResourceOveruseListener({ResourceType::IO}, mockListener);
    ASSERT_TRUE(status.isOk()) << "Failed to add resource overuse listener: "
                               << status.getMessage();
    status = watchdogServer->removeResourceOveruseListener(mockListener);
    ASSERT_TRUE(status.isOk()) << "Failed to remove resource overuse listener: "
                               << status.getMessage();
}

TEST_P(WatchdogAidlTest, TestFailsAddResourceOveruseListenerWithNoResourceType) {
    std::shared_ptr<MockResourceOveruseListener> mockListener =
            SharedRefBase::make<MockResourceOveruseListener>();
    ASSERT_FALSE(watchdogServer->addResourceOveruseListener({}, mockListener).isOk())
            << "Should fail to add resource overuse listener with no resource type";
}

TEST_P(WatchdogAidlTest, TestFailsAddResourceOveruseListenerWithNullptrListener) {
    ASSERT_FALSE(watchdogServer->addResourceOveruseListener({ResourceType::IO}, nullptr).isOk())
            << "Should fail to add null resource overuse listener";
}

TEST_P(WatchdogAidlTest, TestFailsToRemoveNotAddedResourceOveruseListener) {
    std::shared_ptr<MockResourceOveruseListener> mockListener =
            SharedRefBase::make<MockResourceOveruseListener>();
    ASSERT_FALSE(watchdogServer->removeResourceOveruseListener(mockListener).isOk())
            << "Should fail to remote listener that is not added";
}

/*
 * getResourceOveruseStats AIDL method is not tested as it requires writing to disk and waiting
 * until the watchdog server has read I/O stats. The waiting duration depends on the watchdog
 * server's performance data collection frequency, which varies between 20 - 60 seconds depending
 * on the build type. The core implementation is tested in ATS with the help of custom performance
 * data collection, which requires dumpsys access and this is not available to VTS. Thus skipping
 * this test in VTS.
 */

TEST_P(WatchdogAidlTest, TestFailsGetResourceOveruseStatsWithNoResourceTypes) {
    std::vector<ResourceOveruseStats> resourceOveruseStats;
    ASSERT_FALSE(watchdogServer->getResourceOveruseStats({}, &resourceOveruseStats).isOk())
            << "Should fail to fetch resource overuse stats with no resource types";
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(WatchdogAidlTest);
INSTANTIATE_TEST_SUITE_P(CarWatchdogServer, WatchdogAidlTest,
                         ValuesIn(android::getAidlHalInstanceNames(ICarWatchdog::descriptor)),
                         android::PrintInstanceNameToString);

int main(int argc, char** argv) {
    InitGoogleTest(&argc, argv);
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    ABinderProcess_startThreadPool();
    return RUN_ALL_TESTS();
}
