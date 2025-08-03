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

#include "MockIoOveruseMonitor.h"
#include "MockWatchdogPerfService.h"
#include "MockWatchdogProcessService.h"
#include "MockWatchdogServiceHelper.h"
#include "ThreadPriorityController.h"
#include "WatchdogBinderMediator.h"
#include "WatchdogInternalHandler.h"
#include "WatchdogServiceHelper.h"

#include <aidl/android/automotive/watchdog/internal/BootPhase.h>
#include <aidl/android/automotive/watchdog/internal/GarageMode.h>
#include <aidl/android/automotive/watchdog/internal/PowerCycle.h>
#include <aidl/android/automotive/watchdog/internal/UserPackageIoUsageStats.h>
#include <aidl/android/automotive/watchdog/internal/UserState.h>
#include <android-base/result.h>
#include <binder/IPCThreadState.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <private/android_filesystem_config.h>
#include <utils/RefBase.h>

#include <errno.h>
#include <sched.h>
#include <unistd.h>

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::internal::BootPhase;
using ::aidl::android::automotive::watchdog::internal::GarageMode;
using ::aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor;
using ::aidl::android::automotive::watchdog::internal::ICarWatchdogMonitorDefault;
using ::aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem;
using ::aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystemDefault;
using ::aidl::android::automotive::watchdog::internal::PowerCycle;
using ::aidl::android::automotive::watchdog::internal::ProcessIdentifier;
using ::aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration;
using ::aidl::android::automotive::watchdog::internal::StateType;
using ::aidl::android::automotive::watchdog::internal::ThreadPolicyWithPriority;
using ::aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats;
using ::aidl::android::automotive::watchdog::internal::UserState;
using ::android::sp;
using ::android::String16;
using ::android::base::Result;
using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;
using ::ndk::SpAIBinder;
using ::testing::_;
using ::testing::ByMove;
using ::testing::DoAll;
using ::testing::Eq;
using ::testing::Pointer;
using ::testing::Return;
using ::testing::SaveArg;

namespace {

constexpr const char kFailOnNonSystemCallingUidMessage[] =
        "should fail with non-system calling uid";
constexpr const char kFailOnWatchdogServiceHelperErrMessage[] =
        "should fail on watchdog service helper error";

class ScopedChangeCallingUid final : public RefBase {
public:
    explicit ScopedChangeCallingUid(uid_t uid) {
        mCallingUid = IPCThreadState::self()->getCallingUid();
        mCallingPid = IPCThreadState::self()->getCallingPid();
        if (mCallingUid == uid) {
            return;
        }
        mChangedUid = uid;
        int64_t token = ((int64_t)mChangedUid << 32) | mCallingPid;
        IPCThreadState::self()->restoreCallingIdentity(token);
    }
    ~ScopedChangeCallingUid() {
        if (mCallingUid == mChangedUid) {
            return;
        }
        int64_t token = ((int64_t)mCallingUid << 32) | mCallingPid;
        IPCThreadState::self()->restoreCallingIdentity(token);
    }

private:
    uid_t mCallingUid;
    uid_t mChangedUid;
    pid_t mCallingPid;
};

MATCHER_P(PriorityEq, priority, "") {
    return (arg->sched_priority) == priority;
}

class MockThreadPriorityController : public ThreadPriorityControllerInterface {
public:
    MOCK_METHOD(Result<void>, setThreadPriority,
                (int pid, int tid, int uid, int policy, int priority), (override));
    MOCK_METHOD(Result<void>, getThreadPriority,
                (int pid, int tid, int uid, ThreadPolicyWithPriority* result), (override));
};

}  // namespace

namespace internal {

class WatchdogInternalHandlerPeer final {
public:
    explicit WatchdogInternalHandlerPeer(WatchdogInternalHandler* handler) : mHandler(handler) {}

    void setThreadPriorityController(
            std::unique_ptr<ThreadPriorityControllerInterface> controller) {
        mHandler->setThreadPriorityController(std::move(controller));
    }

private:
    WatchdogInternalHandler* mHandler;
};

}  // namespace internal

class WatchdogInternalHandlerTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        mMockWatchdogProcessService = sp<MockWatchdogProcessService>::make();
        mMockWatchdogPerfService = sp<MockWatchdogPerfService>::make();
        mMockWatchdogServiceHelper = sp<MockWatchdogServiceHelper>::make();
        mMockIoOveruseMonitor = sp<MockIoOveruseMonitor>::make();
        mWatchdogInternalHandler =
                SharedRefBase::make<WatchdogInternalHandler>(mMockWatchdogServiceHelper,
                                                             mMockWatchdogProcessService,
                                                             mMockWatchdogPerfService,
                                                             mMockIoOveruseMonitor);
        internal::WatchdogInternalHandlerPeer peer(mWatchdogInternalHandler.get());
        std::unique_ptr<MockThreadPriorityController> threadPriorityController =
                std::make_unique<MockThreadPriorityController>();
        mThreadPriorityController = threadPriorityController.get();
        peer.setThreadPriorityController(std::move(threadPriorityController));
    }
    virtual void TearDown() {
        mMockWatchdogServiceHelper.clear();
        mMockWatchdogProcessService.clear();
        mMockWatchdogPerfService.clear();
        mMockIoOveruseMonitor.clear();
        mWatchdogInternalHandler.reset();
        mScopedChangeCallingUid.clear();
    }

    // Sets calling UID to imitate System's process.
    void setSystemCallingUid() {
        mScopedChangeCallingUid = sp<ScopedChangeCallingUid>::make(AID_SYSTEM);
    }

    sp<MockWatchdogServiceHelper> mMockWatchdogServiceHelper;
    sp<MockWatchdogProcessService> mMockWatchdogProcessService;
    sp<MockWatchdogPerfService> mMockWatchdogPerfService;
    sp<MockIoOveruseMonitor> mMockIoOveruseMonitor;
    std::shared_ptr<WatchdogInternalHandler> mWatchdogInternalHandler;
    sp<ScopedChangeCallingUid> mScopedChangeCallingUid;
    MockThreadPriorityController* mThreadPriorityController;
};

TEST_F(WatchdogInternalHandlerTest, TestTerminate) {
    ASSERT_NE(mWatchdogInternalHandler->mWatchdogServiceHelper, nullptr);
    ASSERT_NE(mWatchdogInternalHandler->mWatchdogProcessService, nullptr);
    ASSERT_NE(mWatchdogInternalHandler->mWatchdogPerfService, nullptr);
    ASSERT_NE(mWatchdogInternalHandler->mIoOveruseMonitor, nullptr);

    mWatchdogInternalHandler->terminate();

    ASSERT_EQ(mWatchdogInternalHandler->mWatchdogServiceHelper, nullptr);
    ASSERT_EQ(mWatchdogInternalHandler->mWatchdogProcessService, nullptr);
    ASSERT_EQ(mWatchdogInternalHandler->mWatchdogPerfService, nullptr);
    ASSERT_EQ(mWatchdogInternalHandler->mIoOveruseMonitor, nullptr);
}

TEST_F(WatchdogInternalHandlerTest, TestDump) {
    ASSERT_EQ(mWatchdogInternalHandler->dump(-1, /*args=*/nullptr, /*numArgs=*/0), OK);
}

TEST_F(WatchdogInternalHandlerTest, TestRegisterCarWatchdogService) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockIoOveruseMonitor, isInitialized()).WillOnce(Return(false));
    EXPECT_CALL(*mMockWatchdogPerfService, registerDataProcessor(Eq(mMockIoOveruseMonitor)))
            .WillOnce(Return(Result<void>()));
    EXPECT_CALL(*mMockWatchdogPerfService, onCarWatchdogServiceRegistered()).Times(1);

    std::shared_ptr<ICarWatchdogServiceForSystem> service =
            SharedRefBase::make<ICarWatchdogServiceForSystemDefault>();
    EXPECT_CALL(*mMockWatchdogServiceHelper, registerService(service))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogInternalHandler->registerCarWatchdogService(service);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnRegisterCarWatchdogServiceWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockWatchdogServiceHelper, registerService(_)).Times(0);

    std::shared_ptr<ICarWatchdogServiceForSystem> service =
            SharedRefBase::make<ICarWatchdogServiceForSystemDefault>();
    ASSERT_FALSE(mWatchdogInternalHandler->registerCarWatchdogService(service).isOk())
            << "registerCarWatchdogService " << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnRegisterCarWatchdogServiceWithWatchdogServiceHelperError) {
    setSystemCallingUid();

    std::shared_ptr<ICarWatchdogServiceForSystem> service =
            SharedRefBase::make<ICarWatchdogServiceForSystemDefault>();
    EXPECT_CALL(*mMockWatchdogServiceHelper, registerService(service))
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                                "Illegal state"))));

    ASSERT_FALSE(mWatchdogInternalHandler->registerCarWatchdogService(service).isOk())
            << "registerCarWatchdogService " << kFailOnWatchdogServiceHelperErrMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestUnregisterCarWatchdogService) {
    setSystemCallingUid();

    std::shared_ptr<ICarWatchdogServiceForSystem> service =
            SharedRefBase::make<ICarWatchdogServiceForSystemDefault>();
    EXPECT_CALL(*mMockWatchdogServiceHelper, unregisterService(service))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogInternalHandler->unregisterCarWatchdogService(service);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnUnregisterCarWatchdogServiceWithNonSystemCallingUid) {
    std::shared_ptr<ICarWatchdogServiceForSystem> service =
            SharedRefBase::make<ICarWatchdogServiceForSystemDefault>();
    EXPECT_CALL(*mMockWatchdogServiceHelper, unregisterService(service)).Times(0);

    ASSERT_FALSE(mWatchdogInternalHandler->unregisterCarWatchdogService(service).isOk())
            << "unregisterCarWatchdogService " << kFailOnNonSystemCallingUidMessage;
}
TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnUnregisterCarWatchdogServiceWithWatchdogServiceHelperError) {
    setSystemCallingUid();

    std::shared_ptr<ICarWatchdogServiceForSystem> service =
            SharedRefBase::make<ICarWatchdogServiceForSystemDefault>();
    EXPECT_CALL(*mMockWatchdogServiceHelper, unregisterService(service))
            .WillOnce(Return(
                    ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                       "Illegal argument"))));

    ASSERT_FALSE(mWatchdogInternalHandler->unregisterCarWatchdogService(service).isOk())
            << "unregisterCarWatchdogService " << kFailOnWatchdogServiceHelperErrMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestRegisterMonitor) {
    setSystemCallingUid();

    std::shared_ptr<ICarWatchdogMonitor> monitor =
            SharedRefBase::make<ICarWatchdogMonitorDefault>();
    EXPECT_CALL(*mMockWatchdogProcessService, registerMonitor(monitor))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogInternalHandler->registerMonitor(monitor);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnRegisterMonitorWithNonSystemCallingUid) {
    std::shared_ptr<ICarWatchdogMonitor> monitor =
            SharedRefBase::make<ICarWatchdogMonitorDefault>();
    EXPECT_CALL(*mMockWatchdogProcessService, registerMonitor(monitor)).Times(0);

    ASSERT_FALSE(mWatchdogInternalHandler->registerMonitor(monitor).isOk())
            << "registerMonitor " << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestUnregisterMonitor) {
    setSystemCallingUid();

    std::shared_ptr<ICarWatchdogMonitor> monitor =
            SharedRefBase::make<ICarWatchdogMonitorDefault>();
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterMonitor(monitor))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogInternalHandler->unregisterMonitor(monitor);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnUnregisterMonitorWithNonSystemCallingUid) {
    std::shared_ptr<ICarWatchdogMonitor> monitor =
            SharedRefBase::make<ICarWatchdogMonitorDefault>();
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterMonitor(monitor)).Times(0);

    ASSERT_FALSE(mWatchdogInternalHandler->unregisterMonitor(monitor).isOk())
            << "unregisterMonitor " << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestCarWatchdogServiceAlive) {
    setSystemCallingUid();

    std::shared_ptr<ICarWatchdogServiceForSystem> service =
            SharedRefBase::make<ICarWatchdogServiceForSystemDefault>();
    std::vector<ProcessIdentifier> clientsNotResponding;
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = 123;
    clientsNotResponding.push_back(processIdentifier);
    EXPECT_CALL(*mMockWatchdogProcessService,
                tellCarWatchdogServiceAlive(service, clientsNotResponding, 456))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogInternalHandler->tellCarWatchdogServiceAlive(service,
                                                                        clientsNotResponding, 456);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnCarWatchdogServiceWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockWatchdogProcessService, tellCarWatchdogServiceAlive(_, _, _)).Times(0);

    std::shared_ptr<ICarWatchdogServiceForSystem> service =
            SharedRefBase::make<ICarWatchdogServiceForSystemDefault>();
    std::vector<ProcessIdentifier> clientsNotResponding;
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = 123;
    clientsNotResponding.push_back(processIdentifier);
    auto status = mWatchdogInternalHandler->tellCarWatchdogServiceAlive(service,
                                                                        clientsNotResponding, 456);

    ASSERT_FALSE(status.isOk()) << "tellCarWatchdogServiceAlive "
                                << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestTellDumpFinished) {
    setSystemCallingUid();

    std::shared_ptr<ICarWatchdogMonitor> monitor =
            SharedRefBase::make<ICarWatchdogMonitorDefault>();
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = 456;
    EXPECT_CALL(*mMockWatchdogProcessService, tellDumpFinished(monitor, processIdentifier))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogInternalHandler->tellDumpFinished(monitor, processIdentifier);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnTellDumpFinishedWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockWatchdogProcessService, tellDumpFinished(_, _)).Times(0);

    ProcessIdentifier processIdentifier;
    processIdentifier.pid = 456;
    std::shared_ptr<ICarWatchdogMonitor> monitor =
            SharedRefBase::make<ICarWatchdogMonitorDefault>();

    ASSERT_FALSE(mWatchdogInternalHandler->tellDumpFinished(monitor, processIdentifier).isOk())
            << "tellDumpFinished " << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyPowerCycleChangeToShutdownPrepare) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(/*isEnabled=*/false)).Times(1);

    auto status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(StateType::POWER_CYCLE,
                                              static_cast<int32_t>(
                                                      PowerCycle::POWER_CYCLE_SHUTDOWN_PREPARE),
                                              -1);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyPowerCycleChangeToShutdownEnter) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(/*isEnabled=*/false)).Times(1);
    EXPECT_CALL(*mMockWatchdogPerfService, onShutdownEnter()).Times(1);

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(StateType::POWER_CYCLE,
                                                    static_cast<int32_t>(
                                                            PowerCycle::POWER_CYCLE_SHUTDOWN_ENTER),
                                                    -1);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyPowerCycleChangeToResume) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(/*isEnabled=*/true)).Times(1);

    auto status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(StateType::POWER_CYCLE,
                                              static_cast<int32_t>(PowerCycle::POWER_CYCLE_RESUME),
                                              -1);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyPowerCycleChangeToSuspendExit) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockWatchdogPerfService, onSuspendExit()).Times(1);

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(StateType::POWER_CYCLE,
                                                    static_cast<int32_t>(
                                                            PowerCycle::POWER_CYCLE_SUSPEND_EXIT),
                                                    -1);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnNotifyPowerCycleChangeWithInvalidArgs) {
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(_)).Times(0);
    EXPECT_CALL(*mMockWatchdogPerfService, setSystemState(_)).Times(0);

    StateType type = StateType::POWER_CYCLE;

    ASSERT_FALSE(mWatchdogInternalHandler->notifySystemStateChange(type, -1, -1).isOk())
            << "notifySystemStateChange should fail with negative power cycle";

    ASSERT_FALSE(mWatchdogInternalHandler->notifySystemStateChange(type, 3000, -1).isOk())
            << "notifySystemStateChange should fail with invalid power cycle";
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyGarageModeOn) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockWatchdogPerfService, setSystemState(SystemState::GARAGE_MODE)).Times(1);

    auto status =
            mWatchdogInternalHandler->notifySystemStateChange(StateType::GARAGE_MODE,
                                                              static_cast<int32_t>(
                                                                      GarageMode::GARAGE_MODE_ON),
                                                              -1);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyGarageModeOff) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockWatchdogPerfService, setSystemState(SystemState::NORMAL_MODE)).Times(1);

    auto status =
            mWatchdogInternalHandler->notifySystemStateChange(StateType::GARAGE_MODE,
                                                              static_cast<int32_t>(
                                                                      GarageMode::GARAGE_MODE_OFF),
                                                              -1);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithStartedUser) {
    setSystemCallingUid();
    StateType type = StateType::USER_STATE;

    EXPECT_CALL(*mMockWatchdogProcessService, onUserStateChange(234567, /*isStarted=*/true));

    auto status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(type, 234567,
                                              static_cast<int32_t>(UserState::USER_STATE_STARTED));

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithSwitchingUser) {
    setSystemCallingUid();
    StateType type = StateType::USER_STATE;

    EXPECT_CALL(*mMockWatchdogPerfService,
                onUserStateChange(234567, UserState::USER_STATE_SWITCHING));

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(type, 234567,
                                                    static_cast<int32_t>(
                                                            UserState::USER_STATE_SWITCHING));

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithUnlockingUser) {
    setSystemCallingUid();
    StateType type = StateType::USER_STATE;

    EXPECT_CALL(*mMockWatchdogPerfService,
                onUserStateChange(234567, UserState::USER_STATE_UNLOCKING));

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(type, 234567,
                                                    static_cast<int32_t>(
                                                            UserState::USER_STATE_UNLOCKING));

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithPostUnlockedUser) {
    setSystemCallingUid();
    StateType type = StateType::USER_STATE;

    EXPECT_CALL(*mMockWatchdogPerfService,
                onUserStateChange(234567, UserState::USER_STATE_POST_UNLOCKED));

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(type, 234567,
                                                    static_cast<int32_t>(
                                                            UserState::USER_STATE_POST_UNLOCKED));

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithStoppedUser) {
    setSystemCallingUid();
    StateType type = StateType::USER_STATE;

    EXPECT_CALL(*mMockWatchdogProcessService, onUserStateChange(234567, /*isStarted=*/false));

    auto status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(type, 234567,
                                              static_cast<int32_t>(UserState::USER_STATE_STOPPED));

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithRemovedUser) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockIoOveruseMonitor, removeStatsForUser(/*userId=*/234567));

    StateType type = StateType::USER_STATE;
    auto status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(type, 234567,
                                              static_cast<int32_t>(UserState::USER_STATE_REMOVED));

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnOnUserStateChangeWithInvalidArgs) {
    EXPECT_CALL(*mMockWatchdogProcessService, onUserStateChange(_, _)).Times(0);

    StateType type = StateType::USER_STATE;

    ASSERT_FALSE(mWatchdogInternalHandler->notifySystemStateChange(type, 234567, -1).isOk())
            << "notifySystemStateChange should fail with negative user state";

    ASSERT_FALSE(mWatchdogInternalHandler->notifySystemStateChange(type, 234567, 3000).isOk())
            << "notifySystemStateChange should fail with invalid user state";
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyBootPhaseChange) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockWatchdogPerfService, onBootFinished()).WillOnce(Return(Result<void>()));

    StateType type = StateType::BOOT_PHASE;
    auto status =
            mWatchdogInternalHandler->notifySystemStateChange(type,
                                                              static_cast<int32_t>(
                                                                      BootPhase::BOOT_COMPLETED),
                                                              -1);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyBootPhaseChangeWithNonBootCompletedPhase) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockWatchdogPerfService, onBootFinished()).Times(0);

    StateType type = StateType::BOOT_PHASE;
    auto status = mWatchdogInternalHandler->notifySystemStateChange(type, 0, -1);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnNotifySystemStateChangeWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(_)).Times(0);
    EXPECT_CALL(*mMockWatchdogPerfService, setSystemState(_)).Times(0);

    StateType type = StateType::POWER_CYCLE;
    auto status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(type,
                                              static_cast<int32_t>(
                                                      PowerCycle::POWER_CYCLE_SHUTDOWN_PREPARE),
                                              -1);

    ASSERT_FALSE(status.isOk()) << "notifySystemStateChange " << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestUpdateResourceOveruseConfigurations) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockIoOveruseMonitor, updateResourceOveruseConfigurations(_))
            .WillOnce(Return(Result<void>()));

    auto status = mWatchdogInternalHandler->updateResourceOveruseConfigurations(
            std::vector<ResourceOveruseConfiguration>{});

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnUpdateResourceOveruseConfigurationsWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockIoOveruseMonitor, updateResourceOveruseConfigurations(_)).Times(0);

    auto status = mWatchdogInternalHandler->updateResourceOveruseConfigurations(
            std::vector<ResourceOveruseConfiguration>{});

    ASSERT_FALSE(status.isOk()) << "updateResourceOveruseConfigurations "
                                << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestGetResourceOveruseConfigurations) {
    setSystemCallingUid();

    std::vector<ResourceOveruseConfiguration> configs;
    EXPECT_CALL(*mMockIoOveruseMonitor, getResourceOveruseConfigurations(Pointer(&configs)))
            .WillOnce(Return(Result<void>()));

    auto status = mWatchdogInternalHandler->getResourceOveruseConfigurations(&configs);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnGetResourceOveruseConfigurationsWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockIoOveruseMonitor, getResourceOveruseConfigurations(_)).Times(0);

    std::vector<ResourceOveruseConfiguration> configs;

    ASSERT_FALSE(mWatchdogInternalHandler->getResourceOveruseConfigurations(&configs).isOk())
            << "getResourceOveruseConfigurations " << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestControlProcessHealthCheck) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(/*isEnabled=*/true)).Times(1);

    auto status = mWatchdogInternalHandler->controlProcessHealthCheck(/*enable=*/true);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnControlProcessHealthCheckWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(_)).Times(0);

    ASSERT_FALSE(mWatchdogInternalHandler->controlProcessHealthCheck(/*enable=*/true).isOk())
            << "controlProcessHealthCheck " << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriority) {
    setSystemCallingUid();
    int testPid = 1;
    int testTid = 2;
    int testUid = 3;
    int policy = SCHED_FIFO;
    int priority = 1;
    EXPECT_CALL(*mThreadPriorityController,
                setThreadPriority(testPid, testTid, testUid, policy, priority))
            .WillOnce(Return(Result<void>()));

    auto status = mWatchdogInternalHandler->setThreadPriority(testPid, testTid, testUid, policy,
                                                              priority);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestGetThreadPriority) {
    setSystemCallingUid();
    int testPid = 1;
    int testTid = 2;
    int testUid = 3;
    int expectedPolicy = SCHED_FIFO;
    int expectedPriority = 1;
    EXPECT_CALL(*mThreadPriorityController, getThreadPriority(testPid, testTid, testUid, _))
            .WillOnce([expectedPolicy, expectedPriority](int, int, int,
                                                         ThreadPolicyWithPriority* result) {
                result->policy = expectedPolicy;
                result->priority = expectedPriority;
                return Result<void>();
            });

    ThreadPolicyWithPriority actual;
    auto status = mWatchdogInternalHandler->getThreadPriority(testPid, testTid, testUid, &actual);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
    EXPECT_EQ(actual.policy, expectedPolicy);
    EXPECT_EQ(actual.priority, expectedPriority);
}

TEST_F(WatchdogInternalHandlerTest, TestOnAidlVhalPidFetched) {
    setSystemCallingUid();

    int vhalPid = 56423;
    EXPECT_CALL(*mMockWatchdogProcessService, onAidlVhalPidFetched(vhalPid)).Times(1);

    auto status = mWatchdogInternalHandler->onAidlVhalPidFetched(vhalPid);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnOnAidlVhalPidFetchedWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockWatchdogProcessService, onAidlVhalPidFetched(_)).Times(0);

    ASSERT_FALSE(mWatchdogInternalHandler->onAidlVhalPidFetched(56423).isOk())
            << "onAidlVhalPidFetched " << kFailOnNonSystemCallingUidMessage;
}

TEST_F(WatchdogInternalHandlerTest, TestOnTodayIoUsageStatsFetched) {
    setSystemCallingUid();

    std::vector<UserPackageIoUsageStats> userPackageIoUsageStats = {};
    EXPECT_CALL(*mMockIoOveruseMonitor, onTodayIoUsageStatsFetched(userPackageIoUsageStats))
            .Times(1);

    auto status = mWatchdogInternalHandler->onTodayIoUsageStatsFetched(userPackageIoUsageStats);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnOnTodayIoUsageStatsFetchedWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockIoOveruseMonitor, onTodayIoUsageStatsFetched(_)).Times(0);

    ASSERT_FALSE(mWatchdogInternalHandler->onTodayIoUsageStatsFetched({}).isOk())
            << "onTodayIoUsageStatsFetched " << kFailOnNonSystemCallingUidMessage;
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
