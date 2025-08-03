/*
 * Copyright 2018 The Android Open Source Project
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

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <android/binder_manager.h>
#include <android/binder_stability.h>
#include <android/system/suspend/BnSuspendCallback.h>
#include <android/system/suspend/BnWakelockCallback.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <cutils/native_handle.h>
#include <ftw.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <hidl/HidlTransportSupport.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <sys/types.h>

#include <chrono>
#include <cmath>
#include <csignal>
#include <cstdlib>
#include <future>
#include <string>
#include <thread>

#include "SuspendControlService.h"
#include "SystemSuspend.h"
#include "SystemSuspendAidl.h"
#include "WakeupList.h"

using aidl::android::system::suspend::ISystemSuspend;
using aidl::android::system::suspend::IWakeLock;
using aidl::android::system::suspend::SystemSuspendAidl;
using aidl::android::system::suspend::WakeLockType;
using android::sp;
using android::base::Result;
using android::base::Socketpair;
using android::base::unique_fd;
using android::base::WriteStringToFd;
using android::base::WriteStringToFile;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::Return;
using android::hardware::Void;
using android::system::suspend::BnSuspendCallback;
using android::system::suspend::BnWakelockCallback;
using android::system::suspend::ISuspendControlService;
using android::system::suspend::internal::ISuspendControlServiceInternal;
using android::system::suspend::internal::WakeLockInfo;
using android::system::suspend::internal::WakeupInfo;
using android::system::suspend::V1_0::readFd;
using android::system::suspend::V1_0::SleepTimeConfig;
using android::system::suspend::V1_0::SuspendControlService;
using android::system::suspend::V1_0::SuspendControlServiceInternal;
using android::system::suspend::V1_0::SuspendStats;
using android::system::suspend::V1_0::SystemSuspend;
using android::system::suspend::V1_0::TimestampType;
using android::system::suspend::V1_0::WakeupList;
using namespace std::chrono_literals;

namespace android {

static constexpr char kServiceName[] = "TestService";
static constexpr char kControlServiceName[] = "TestControlService";
static constexpr char kControlServiceInternalName[] = "TestControlServiceInternal";

static bool isReadBlocked(int fd, int timeout_ms = 20) {
    struct pollfd pfd {
        .fd = fd, .events = POLLIN,
    };
    return poll(&pfd, 1, timeout_ms) == 0;
}

class SystemSuspendTest : public ::testing::Test {
   protected:
    static void registerTestService() {
        std::thread testService([] {
            configureRpcThreadpool(1, true /* callerWillJoin */);

            sp<SuspendControlService> suspendControl = new SuspendControlService();
            auto controlStatus = ::android::defaultServiceManager()->addService(
                android::String16(kControlServiceName), suspendControl);
            if (android::OK != controlStatus) {
                LOG(FATAL) << "Unable to register service " << kControlServiceName << controlStatus;
            }

            sp<SuspendControlServiceInternal> suspendControlInternal =
                new SuspendControlServiceInternal();
            controlStatus = ::android::defaultServiceManager()->addService(
                android::String16(kControlServiceInternalName), suspendControlInternal);
            if (android::OK != controlStatus) {
                LOG(FATAL) << "Unable to register service " << kControlServiceInternalName
                           << controlStatus;
            }

            // Create non-HW binder threadpool for SuspendControlService.
            sp<android::ProcessState> ps{android::ProcessState::self()};
            ps->startThreadPool();

            wakeupReasonsFd =
                unique_fd(TEMP_FAILURE_RETRY(open(wakeupReasonsFile.path, O_CLOEXEC | O_RDONLY)));

            suspendTimeFd =
                unique_fd(TEMP_FAILURE_RETRY(open(suspendTimeFile.path, O_CLOEXEC | O_RDONLY)));

            systemSuspend = new SystemSuspend(
                std::move(wakeupCountFds[1]), std::move(stateFds[1]),
                unique_fd(-1) /*suspendStatsFd*/, 1 /* maxNativeStatsEntries */,
                unique_fd(-1) /* kernelWakelockStatsFd */, std::move(wakeupReasonsFd),
                std::move(suspendTimeFd), kSleepTimeConfig, suspendControl, suspendControlInternal);

            std::shared_ptr<SystemSuspendAidl> suspendAidl =
                ndk::SharedRefBase::make<SystemSuspendAidl>(systemSuspend.get());
            auto aidlBinder = suspendAidl->asBinder();
            AIBinder_forceDowngradeToLocalStability(aidlBinder.get());
            auto aidlStatus = AServiceManager_addService(aidlBinder.get(), kServiceName);
            CHECK(aidlStatus == STATUS_OK);

            joinRpcThreadpool();
        });
        testService.detach();
    }

    static void SetUpTestSuite() {
        Socketpair(SOCK_STREAM, &wakeupCountFds[0], &wakeupCountFds[1]);
        Socketpair(SOCK_STREAM, &stateFds[0], &stateFds[1]);

        wakeupCountFd = wakeupCountFds[0];
        stateFd = stateFds[0];

        registerTestService();
        std::shared_ptr<ISystemSuspend> suspendService = ISystemSuspend::fromBinder(
            ndk::SpAIBinder(AServiceManager_waitForService(kServiceName)));
        ASSERT_NE(suspendService, nullptr) << "failed to get suspend service";

        sp<IBinder> control =
            android::defaultServiceManager()->getService(android::String16(kControlServiceName));
        ASSERT_NE(control, nullptr) << "failed to get the suspend control service";
        sp<ISuspendControlService> controlService = interface_cast<ISuspendControlService>(control);

        sp<IBinder> controlInternal = android::defaultServiceManager()->getService(
            android::String16(kControlServiceInternalName));
        ASSERT_NE(controlInternal, nullptr) << "failed to get the suspend control internal service";
        sp<ISuspendControlServiceInternal> controlServiceInternal =
            interface_cast<ISuspendControlServiceInternal>(controlInternal);

        // Start auto-suspend.
        bool enabled = false;
        controlServiceInternal->enableAutosuspend(new BBinder(), &enabled);
        ASSERT_EQ(enabled, true) << "failed to start autosuspend";
    }

    static void TearDownTestSuite() {
        unblockSystemSuspendFromWakeupCount();
        systemSuspend->disableAutosuspend();
    }

   public:
    virtual void SetUp() override {
        suspendService = ISystemSuspend::fromBinder(
            ndk::SpAIBinder(AServiceManager_waitForService(kServiceName)));
        ASSERT_NE(suspendService, nullptr) << "failed to get suspend service";

        sp<IBinder> control =
            android::defaultServiceManager()->getService(android::String16(kControlServiceName));
        ASSERT_NE(control, nullptr) << "failed to get the suspend control service";
        controlService = interface_cast<ISuspendControlService>(control);

        sp<IBinder> controlInternal = android::defaultServiceManager()->getService(
            android::String16(kControlServiceInternalName));
        ASSERT_NE(controlInternal, nullptr) << "failed to get the suspend control internal service";
        controlServiceInternal = interface_cast<ISuspendControlServiceInternal>(controlInternal);

        systemSuspend->enableAutosuspend(new BBinder());

        // SystemSuspend HAL should not have written back to wakeupCountFd or stateFd yet.
        ASSERT_TRUE(isReadBlocked(wakeupCountFd));
        ASSERT_TRUE(isReadBlocked(stateFd));
    }

    virtual void TearDown() override {
        // Allow some time for the autosuspend loop to happen, if unblocked
        std::this_thread::sleep_for(100ms);

        if (!isReadBlocked(wakeupCountFd)) readFd(wakeupCountFd);
        if (!isReadBlocked(stateFd)) readFd(stateFd);

        ASSERT_TRUE(isReadBlocked(wakeupCountFd));
        ASSERT_TRUE(isReadBlocked(stateFd));
    }

    static void unblockSystemSuspendFromWakeupCount() {
        std::string wakeupCount = std::to_string(rand());
        ASSERT_TRUE(WriteStringToFd(wakeupCount, wakeupCountFd));
    }

    bool isSystemSuspendBlocked(int timeout_ms = 20) {
        // Allow some time for the autosuspend loop to happen, if unblocked
        std::this_thread::sleep_for(100ms);

        return isReadBlocked(stateFd, timeout_ms);
    }

    std::shared_ptr<IWakeLock> acquireWakeLock(const std::string& name = "TestLock") {
        std::shared_ptr<IWakeLock> wl = nullptr;
        auto status = suspendService->acquireWakeLock(WakeLockType::PARTIAL, name, &wl);
        return wl;
    }

    size_t getActiveWakeLockCount() {
        std::vector<WakeLockInfo> wlStats;
        controlServiceInternal->getWakeLockStats(&wlStats);
        return count_if(wlStats.begin(), wlStats.end(), [](auto entry) { return entry.isActive; });
    }

    void checkLoop(int numIter) {
        for (int i = 0; i < numIter; i++) {
            // Mock value for /sys/power/wakeup_count.
            std::string wakeupCount = std::to_string(rand());
            ASSERT_TRUE(WriteStringToFd(wakeupCount, wakeupCountFd));
            ASSERT_EQ(readFd(wakeupCountFd), wakeupCount)
                << "wakeup count value written by SystemSuspend is not equal to value given to it";
            ASSERT_EQ(readFd(stateFd), "mem")
                << "SystemSuspend failed to write correct sleep state.";
        }
    }

    void checkWakelockLoop(int numIter, const std::string name) {
        for (int i = 0; i < numIter; i++) {
            std::shared_ptr<IWakeLock> testLock = acquireWakeLock(name);
            testLock->release();
        }
    }

    void suspendFor(std::chrono::milliseconds suspendTime, int numberOfSuspends) {
        std::string suspendStr =
            "0.001 " /* placeholder */ +
            std::to_string(
                std::chrono::duration_cast<std::chrono::duration<double>>(suspendTime).count());
        ASSERT_TRUE(WriteStringToFile(suspendStr, suspendTimeFile.path));
        checkLoop(numberOfSuspends);
    }

    void checkSleepTime(std::chrono::milliseconds expected) {
        // There is a race window where sleepTime can be checked in the tests,
        // before it is updated in autoSuspend
        while (!isReadBlocked(wakeupCountFd)) {
        }
        std::chrono::milliseconds actual = systemSuspend->getSleepTime();
        ASSERT_EQ(actual.count(), expected.count()) << "incorrect sleep time";
    }

    std::shared_ptr<ISystemSuspend> suspendService;
    sp<ISuspendControlService> controlService;
    sp<ISuspendControlServiceInternal> controlServiceInternal;
    static sp<SystemSuspend> systemSuspend;
    static unique_fd wakeupCountFds[2];
    static unique_fd stateFds[2];
    static unique_fd wakeupReasonsFd;
    static unique_fd suspendTimeFd;
    static int wakeupCountFd;
    static int stateFd;
    static TemporaryFile wakeupReasonsFile;
    static TemporaryFile suspendTimeFile;

    static constexpr SleepTimeConfig kSleepTimeConfig = {
        .baseSleepTime = 100ms,
        .maxSleepTime = 400ms,
        .sleepTimeScaleFactor = 1.9,
        .backoffThreshold = 1,
        .shortSuspendThreshold = 100ms,
        .failedSuspendBackoffEnabled = true,
        .shortSuspendBackoffEnabled = true,
    };
};

// SystemSuspendTest test suite resources
sp<SystemSuspend> SystemSuspendTest::systemSuspend;
unique_fd SystemSuspendTest::wakeupCountFds[2];
unique_fd SystemSuspendTest::stateFds[2];
unique_fd SystemSuspendTest::wakeupReasonsFd;
unique_fd SystemSuspendTest::suspendTimeFd;
int SystemSuspendTest::wakeupCountFd;
int SystemSuspendTest::stateFd;
TemporaryFile SystemSuspendTest::wakeupReasonsFile;
TemporaryFile SystemSuspendTest::suspendTimeFile;

// Tests that autosuspend thread can only be enabled once.
TEST_F(SystemSuspendTest, OnlyOneEnableAutosuspend) {
    bool enabled = false;
    controlServiceInternal->enableAutosuspend(new BBinder(), &enabled);
    ASSERT_EQ(enabled, false);
}

// Tests that autosuspend thread can only enabled again after its been disabled.
TEST_F(SystemSuspendTest, EnableAutosuspendAfterDisableAutosuspend) {
    bool enabled = false;

    checkLoop(1);
    controlServiceInternal->enableAutosuspend(new BBinder(), &enabled);
    ASSERT_FALSE(enabled);

    systemSuspend->disableAutosuspend();
    unblockSystemSuspendFromWakeupCount();

    controlServiceInternal->enableAutosuspend(new BBinder(), &enabled);
    ASSERT_TRUE(enabled);
}

TEST_F(SystemSuspendTest, DisableAutosuspendBlocksSuspend) {
    checkLoop(1);
    systemSuspend->disableAutosuspend();
    unblockSystemSuspendFromWakeupCount();
    ASSERT_TRUE(isSystemSuspendBlocked());

    // Re-enable autosuspend
    bool enabled = false;
    controlServiceInternal->enableAutosuspend(new BBinder(), &enabled);
    ASSERT_TRUE(enabled);
}

TEST_F(SystemSuspendTest, BlockAutosuspendIfBinderIsDead) {
    class DeadBinder : public BBinder {
        android::status_t pingBinder() override { return android::UNKNOWN_ERROR; }
    };

    auto token = sp<DeadBinder>::make();

    systemSuspend->disableAutosuspend();
    unblockSystemSuspendFromWakeupCount();
    ASSERT_TRUE(isSystemSuspendBlocked());

    bool enabled = false;
    controlServiceInternal->enableAutosuspend(token, &enabled);
    unblockSystemSuspendFromWakeupCount();

    ASSERT_TRUE(isSystemSuspendBlocked(150));
}

TEST_F(SystemSuspendTest, UnresponsiveClientDoesNotBlockAcquireRelease) {
    static std::mutex _lock;
    static std::condition_variable inPingBinderCondVar;
    static bool inPingBinder = false;

    class UnresponsiveBinder : public BBinder {
        android::status_t pingBinder() override {
            auto lock = std::unique_lock(_lock);
            inPingBinder = true;
            inPingBinderCondVar.notify_all();

            // Block pingBinder until test finishes and releases its lock
            inPingBinderCondVar.wait(lock);
            return android::UNKNOWN_ERROR;
        }
    };

    systemSuspend->disableAutosuspend();
    unblockSystemSuspendFromWakeupCount();
    ASSERT_TRUE(isSystemSuspendBlocked());

    auto token = sp<UnresponsiveBinder>::make();
    bool enabled = false;
    controlServiceInternal->enableAutosuspend(token, &enabled);
    unblockSystemSuspendFromWakeupCount();

    auto lock = std::unique_lock(_lock);
    // wait until pingBinder has been called.
    if (!inPingBinder) {
        inPingBinderCondVar.wait(lock);
    }
    // let pingBinder finish once we release the test lock
    inPingBinderCondVar.notify_all();

    std::condition_variable wakeLockAcquired;
    std::thread(
        [this](std::condition_variable& wakeLockAcquired) {
            std::shared_ptr<IWakeLock> testLock = acquireWakeLock("testLock");
            testLock->release();
            wakeLockAcquired.notify_all();
        },
        std::ref(wakeLockAcquired))
        .detach();

    std::mutex _acquireReleaseLock;
    auto acquireReleaseLock = std::unique_lock(_acquireReleaseLock);
    bool timedOut = wakeLockAcquired.wait_for(acquireReleaseLock, 200ms) == std::cv_status::timeout;

    ASSERT_FALSE(timedOut);
}

TEST_F(SystemSuspendTest, AutosuspendLoop) {
    checkLoop(5);
}

// Tests that upon WakeLock destruction SystemSuspend HAL is unblocked.
TEST_F(SystemSuspendTest, WakeLockDestructor) {
    {
        std::shared_ptr<IWakeLock> wl = acquireWakeLock();
        ASSERT_NE(wl, nullptr);
        unblockSystemSuspendFromWakeupCount();
        ASSERT_TRUE(isSystemSuspendBlocked());
    }
    ASSERT_FALSE(isSystemSuspendBlocked());
}

// Tests that upon WakeLock::release() SystemSuspend HAL is unblocked.
TEST_F(SystemSuspendTest, WakeLockRelease) {
    std::shared_ptr<IWakeLock> wl = acquireWakeLock();
    ASSERT_NE(wl, nullptr);
    unblockSystemSuspendFromWakeupCount();
    ASSERT_TRUE(isSystemSuspendBlocked());
    wl->release();
    ASSERT_FALSE(isSystemSuspendBlocked());
}

// Tests that multiple WakeLocks correctly block SystemSuspend HAL.
TEST_F(SystemSuspendTest, MultipleWakeLocks) {
    {
        std::shared_ptr<IWakeLock> wl1 = acquireWakeLock();
        ASSERT_NE(wl1, nullptr);
        ASSERT_TRUE(isSystemSuspendBlocked());
        {
            std::shared_ptr<IWakeLock> wl2 = acquireWakeLock();
            ASSERT_NE(wl2, nullptr);
            unblockSystemSuspendFromWakeupCount();
            ASSERT_TRUE(isSystemSuspendBlocked());
        }
        ASSERT_TRUE(isSystemSuspendBlocked());
    }
    ASSERT_FALSE(isSystemSuspendBlocked());
}

// Tests that upon thread deallocation WakeLock is destructed and SystemSuspend HAL is unblocked.
TEST_F(SystemSuspendTest, ThreadCleanup) {
    std::thread clientThread([this] {
        std::shared_ptr<IWakeLock> wl = acquireWakeLock();
        ASSERT_NE(wl, nullptr);
        unblockSystemSuspendFromWakeupCount();
        ASSERT_TRUE(isSystemSuspendBlocked());
    });
    clientThread.join();
    ASSERT_FALSE(isSystemSuspendBlocked());
}

// Test that binder driver correctly deallocates acquired WakeLocks, even if the client processs
// is terminated without ability to do clean up.
TEST_F(SystemSuspendTest, CleanupOnAbort) {
    ASSERT_EXIT(
        {
            std::shared_ptr<IWakeLock> wl = acquireWakeLock();
            ASSERT_NE(wl, nullptr);
            std::abort();
        },
        ::testing::KilledBySignal(SIGABRT), "");
    ASSERT_TRUE(isSystemSuspendBlocked());
    unblockSystemSuspendFromWakeupCount();
    // Timing of the wake lock clean-up after process death is scheduler-dependent.
    // Increase the timeout to avoid flakes.
    ASSERT_FALSE(isSystemSuspendBlocked(200));
}

// Stress test acquiring/releasing WakeLocks.
TEST_F(SystemSuspendTest, WakeLockStressTest) {
    // numThreads threads will acquire/release numLocks locks each.
    constexpr int numThreads = 10;
    constexpr int numLocks = 10000;
    std::thread tds[numThreads];

    for (int i = 0; i < numThreads; i++) {
        tds[i] = std::thread([this] {
            for (int j = 0; j < numLocks; j++) {
                std::shared_ptr<IWakeLock> wl1 = acquireWakeLock();
                std::shared_ptr<IWakeLock> wl2 = acquireWakeLock();
                wl2->release();
            }
        });
    }
    for (int i = 0; i < numThreads; i++) {
        tds[i].join();
    }
    ASSERT_EQ(getActiveWakeLockCount(), 0);
}

TEST_F(SystemSuspendTest, SuspendBackoffLongSuspendTest) {
    // Sleep time shall be set to base sleep time after a long suspend
    suspendFor(10000ms, 1);
    checkSleepTime(kSleepTimeConfig.baseSleepTime);
}

TEST_F(SystemSuspendTest, BackoffThresholdTest) {
    // Sleep time shall be set to base sleep time after a long suspend
    suspendFor(10000ms, 1);
    checkSleepTime(kSleepTimeConfig.baseSleepTime);

    // Sleep time shall back off after the configured backoff threshold
    std::chrono::milliseconds expectedSleepTime = std::chrono::round<std::chrono::milliseconds>(
        kSleepTimeConfig.baseSleepTime * kSleepTimeConfig.sleepTimeScaleFactor);
    suspendFor(10ms, kSleepTimeConfig.backoffThreshold);
    checkSleepTime(kSleepTimeConfig.baseSleepTime);
    suspendFor(10ms, 1);
    checkSleepTime(expectedSleepTime);

    // Sleep time shall return to base sleep time after a long suspend
    suspendFor(10000ms, 1);
    checkSleepTime(kSleepTimeConfig.baseSleepTime);
}

TEST_F(SystemSuspendTest, SuspendBackoffMaxTest) {
    // Sleep time shall be set to base sleep time after a long suspend
    suspendFor(10000ms, 1);
    checkSleepTime(kSleepTimeConfig.baseSleepTime);

    // Sleep time shall be capped at the configured maximum
    suspendFor(10ms, 3 + kSleepTimeConfig.backoffThreshold);
    checkSleepTime(kSleepTimeConfig.maxSleepTime);

    // Sleep time shall return to base sleep time after a long suspend
    suspendFor(10000ms, 1);
    checkSleepTime(kSleepTimeConfig.baseSleepTime);
}

// Callbacks are passed around as sp<>. However, mock expectations are verified when mock objects
// are destroyed, i.e. the test needs to control lifetime of the mock object.
// MockCallbackImpl can be destroyed independently of its wrapper MockCallback which is passed to
// SystemSuspend.
struct MockCallbackImpl {
    binder::Status notifyWakeup([[maybe_unused]] bool success,
                                const std::vector<std::string>& wakeupReasons) {
        mWakeupReasons = wakeupReasons;
        mNumWakeups++;
        return binder::Status::ok();
    }

    std::vector<std::string> mWakeupReasons;
    int mNumWakeups = 0;
};

class MockCallback : public BnSuspendCallback {
   public:
    MockCallback(MockCallbackImpl* impl) : mImpl(impl), mDisabled(false) {}
    binder::Status notifyWakeup(bool x, const std::vector<std::string>& wakeupReasons) {
        return mDisabled ? binder::Status::ok() : mImpl->notifyWakeup(x, wakeupReasons);
    }
    // In case we pull the rug from under MockCallback, but SystemSuspend still has an sp<> to the
    // object.
    void disable() { mDisabled = true; }

   private:
    MockCallbackImpl* mImpl;
    bool mDisabled;
};

// Tests that nullptr can't be registered as callbacks.
TEST_F(SystemSuspendTest, RegisterInvalidCallback) {
    bool retval = false;
    controlService->registerCallback(nullptr, &retval);
    ASSERT_FALSE(retval);
}

// Tests that SystemSuspend HAL correctly notifies wakeup events.
TEST_F(SystemSuspendTest, CallbackNotifyWakeup) {
    constexpr int numWakeups = 5;
    MockCallbackImpl impl;
    sp<MockCallback> cb = new MockCallback(&impl);
    bool retval = false;
    controlService->registerCallback(cb, &retval);
    ASSERT_TRUE(retval);
    checkLoop(numWakeups + 1);
    cb->disable();
    // SystemSuspend should suspend numWakeup + 1 times. However, it might
    // only be able to notify numWakeup times. The test case might have
    // finished by the time last notification completes.
    ASSERT_GE(impl.mNumWakeups, numWakeups);
}

// Tests that SystemSuspend HAL correctly notifies wakeup subscribers with wakeup reasons.
TEST_F(SystemSuspendTest, CallbackNotifyWakeupReason) {
    int i;
    const std::string wakeupReason0 = "";
    const std::string wakeupReason1 = " ";
    const std::string wakeupReason2 = "\n\n";
    const std::string wakeupReason3 = "100 :android,wakeup-reason-1";
    const std::string wakeupReason4 = "Abort: android,wakeup-reason-2\n";
    const std::string wakeupReason5 =
        "999 :android,wakeup-reason-3\nAbort: android,wakeup-reason-3\n";
    const std::string referenceWakeupUnknown = "unknown";
    const std::string referenceWakeupReason3 = "100 :android,wakeup-reason-1";
    const std::string referenceWakeupReason4 = "Abort: android,wakeup-reason-2";
    const std::vector<std::string> referenceWakeupReason5 = {"999 :android,wakeup-reason-3",
                                                             "Abort: android,wakeup-reason-3"};

    unique_fd wakeupReasonsWriteFd = unique_fd(
        TEMP_FAILURE_RETRY(open(SystemSuspendTest::wakeupReasonsFile.path, O_CLOEXEC | O_WRONLY)));

    MockCallbackImpl impl;
    sp<MockCallback> cb = new MockCallback(&impl);

    bool retval = false;
    controlService->registerCallback(cb, &retval);
    ASSERT_TRUE(retval);

    // wakeupReason0 empty wakeup reason
    // Following assert check may happen before a callback been executed, iterate few checkLoop to
    // make sure at least one callback been finished.
    checkLoop(3);
    ASSERT_EQ(impl.mWakeupReasons.size(), 1);
    ASSERT_EQ(impl.mWakeupReasons[0], referenceWakeupUnknown);

    // wakeupReason1 single invalid wakeup reason with only space.
    ASSERT_TRUE(WriteStringToFd(wakeupReason1, wakeupReasonsWriteFd));
    checkLoop(3);
    ASSERT_EQ(impl.mWakeupReasons.size(), 1);
    ASSERT_EQ(impl.mWakeupReasons[0], referenceWakeupUnknown);

    // wakeupReason2 two empty wakeup reasons.
    lseek(wakeupReasonsWriteFd, 0, SEEK_SET);
    ASSERT_TRUE(WriteStringToFd(wakeupReason2, wakeupReasonsWriteFd));
    checkLoop(3);
    ASSERT_EQ(impl.mWakeupReasons.size(), 1);
    ASSERT_EQ(impl.mWakeupReasons[0], referenceWakeupUnknown);

    // wakeupReason3 single wakeup reasons.
    lseek(wakeupReasonsWriteFd, 0, SEEK_SET);
    ASSERT_TRUE(WriteStringToFd(wakeupReason3, wakeupReasonsWriteFd));
    checkLoop(3);
    ASSERT_EQ(impl.mWakeupReasons.size(), 1);
    ASSERT_EQ(impl.mWakeupReasons[0], referenceWakeupReason3);

    // wakeupReason4 two wakeup reasons with one empty.
    lseek(wakeupReasonsWriteFd, 0, SEEK_SET);
    ASSERT_TRUE(WriteStringToFd(wakeupReason4, wakeupReasonsWriteFd));
    checkLoop(3);
    ASSERT_EQ(impl.mWakeupReasons.size(), 1);
    ASSERT_EQ(impl.mWakeupReasons[0], referenceWakeupReason4);

    // wakeupReason5 two wakeup reasons.
    lseek(wakeupReasonsWriteFd, 0, SEEK_SET);
    ASSERT_TRUE(WriteStringToFd(wakeupReason5, wakeupReasonsWriteFd));
    checkLoop(3);
    ASSERT_EQ(impl.mWakeupReasons.size(), 2);
    i = 0;
    for (auto wakeupReason : impl.mWakeupReasons) {
        ASSERT_EQ(wakeupReason, referenceWakeupReason5[i++]);
    }
    cb->disable();
}

// Tests that SystemSuspend HAL correctly deals with a dead callback.
TEST_F(SystemSuspendTest, DeadCallback) {
    ASSERT_EXIT(
        {
            sp<MockCallback> cb = new MockCallback(nullptr);
            bool retval = false;
            controlService->registerCallback(cb, &retval);
            ASSERT_TRUE(retval);
            std::exit(0);
        },
        ::testing::ExitedWithCode(0), "");

    // Dead process callback must still be dealt with either by unregistering it
    // or checking isOk() on every call.
    checkLoop(3);
}

// Callback that registers another callback.
class CbRegisteringCb : public BnSuspendCallback {
   public:
    CbRegisteringCb(sp<ISuspendControlService> controlService) : mControlService(controlService) {}
    binder::Status notifyWakeup([[maybe_unused]] bool x,
                                [[maybe_unused]] const std::vector<std::string>& wakeupReasons) {
        sp<MockCallback> cb = new MockCallback(nullptr);
        cb->disable();
        bool retval = false;
        mControlService->registerCallback(cb, &retval);
        return binder::Status::ok();
    }

   private:
    sp<ISuspendControlService> mControlService;
};

// Tests that callback registering another callback doesn't result in a deadlock.
TEST_F(SystemSuspendTest, CallbackRegisterCallbackNoDeadlock) {
    sp<CbRegisteringCb> cb = new CbRegisteringCb(controlService);
    bool retval = false;
    controlService->registerCallback(cb, &retval);
    ASSERT_TRUE(retval);
    checkLoop(3);
}

struct MockWakelockCallbackImpl {
    MOCK_METHOD0(notifyAcquired, binder::Status());
    MOCK_METHOD0(notifyReleased, binder::Status());
};

class MockWakelockCallback : public BnWakelockCallback {
   public:
    MockWakelockCallback(MockWakelockCallbackImpl* impl) : mImpl(impl), mDisabled(false) {}
    binder::Status notifyAcquired(void) {
        return mDisabled ? binder::Status::ok() : mImpl->notifyAcquired();
    }
    binder::Status notifyReleased(void) {
        return mDisabled ? binder::Status::ok() : mImpl->notifyReleased();
    }
    // In case we pull the rug from under MockWakelockCallback, but SystemSuspend still has an sp<>
    // to the object.
    void disable() { mDisabled = true; }

   private:
    MockWakelockCallbackImpl* mImpl;
    bool mDisabled;
};

// Tests that nullptr can't be registered as wakelock callbacks.
TEST_F(SystemSuspendTest, RegisterInvalidWakelockCallback) {
    bool retval = false;
    controlService->registerWakelockCallback(nullptr, "testLock", &retval);
    ASSERT_FALSE(retval);
}

// Tests that the a callback cannot be registeed with a wakelock twice.
TEST_F(SystemSuspendTest, RegisterCallbackTwice) {
    bool retval = false;
    MockWakelockCallbackImpl impl;
    sp<MockWakelockCallback> cb = new MockWakelockCallback(&impl);

    controlService->registerWakelockCallback(cb, "testLock", &retval);
    ASSERT_TRUE(retval);
    controlService->registerWakelockCallback(cb, "testLock", &retval);
    ASSERT_FALSE(retval);

    cb->disable();
}

// Tests that the same callback can be registered with two wakelocks.
TEST_F(SystemSuspendTest, RegisterSameCallbackForTwoWakelocks) {
    bool retval = false;
    MockWakelockCallbackImpl impl;
    sp<MockWakelockCallback> cb = new MockWakelockCallback(&impl);

    controlService->registerWakelockCallback(cb, "testLock1", &retval);
    ASSERT_TRUE(retval);
    controlService->registerWakelockCallback(cb, "testLock2", &retval);
    ASSERT_TRUE(retval);

    cb->disable();
}

// Tests that the two callbacks can be registered with the same wakelock.
TEST_F(SystemSuspendTest, RegisterTwoCallbacksForSameWakelock) {
    bool retval = false;
    MockWakelockCallbackImpl impl;
    sp<MockWakelockCallback> cb1 = new MockWakelockCallback(&impl);
    sp<MockWakelockCallback> cb2 = new MockWakelockCallback(&impl);

    controlService->registerWakelockCallback(cb1, "testLock", &retval);
    ASSERT_TRUE(retval);
    controlService->registerWakelockCallback(cb2, "testLock", &retval);
    ASSERT_TRUE(retval);

    cb1->disable();
    cb2->disable();
}

// Tests that SystemSuspend HAL correctly deals with a dead wakelock callback.
TEST_F(SystemSuspendTest, DeadWakelockCallback) {
    ASSERT_EXIT(
        {
            sp<MockWakelockCallback> cb = new MockWakelockCallback(nullptr);
            bool retval = false;
            controlService->registerWakelockCallback(cb, "testLock", &retval);
            ASSERT_TRUE(retval);
            std::exit(0);
        },
        ::testing::ExitedWithCode(0), "");

    // Dead process callback must still be dealt with either by unregistering it
    // or checking isOk() on every call.
    std::shared_ptr<IWakeLock> testLock = acquireWakeLock("testLock");
    ASSERT_TRUE(testLock->release().isOk());
}

// Wakelock callback that registers another callback.
class WakelockCbRegisteringCb : public BnWakelockCallback {
   public:
    WakelockCbRegisteringCb(sp<ISuspendControlService> controlService)
        : mControlService(controlService) {}
    binder::Status notifyAcquired(void) {
        sp<MockWakelockCallback> cb = new MockWakelockCallback(nullptr);
        cb->disable();
        bool retval = false;
        mControlService->registerWakelockCallback(cb, "testLock", &retval);
        return binder::Status::ok();
    }
    binder::Status notifyReleased(void) {
        sp<MockWakelockCallback> cb = new MockWakelockCallback(nullptr);
        cb->disable();
        bool retval = false;
        mControlService->registerWakelockCallback(cb, "testLock", &retval);
        return binder::Status::ok();
    }

   private:
    sp<ISuspendControlService> mControlService;
};

TEST_F(SystemSuspendTest, WakelockCallbackRegisterCallbackNoDeadlock) {
    sp<WakelockCbRegisteringCb> cb = new WakelockCbRegisteringCb(controlService);
    bool retval = false;
    controlService->registerWakelockCallback(cb, "testLock", &retval);
    ASSERT_TRUE(retval);

    checkWakelockLoop(3, "testLock");
}

// Tests that SystemSuspend HAL correctly notifies wakelock events.
TEST_F(SystemSuspendTest, CallbackNotifyWakelock) {
    bool retval = false;
    MockWakelockCallbackImpl impl1;
    MockWakelockCallbackImpl impl2;
    sp<MockWakelockCallback> cb1 = new MockWakelockCallback(&impl1);
    sp<MockWakelockCallback> cb2 = new MockWakelockCallback(&impl2);

    controlService->registerWakelockCallback(cb1, "testLock1", &retval);
    ASSERT_TRUE(retval);
    controlService->registerWakelockCallback(cb2, "testLock2", &retval);
    ASSERT_TRUE(retval);

    EXPECT_CALL(impl1, notifyAcquired).Times(4);
    EXPECT_CALL(impl1, notifyReleased).Times(4);
    EXPECT_CALL(impl2, notifyAcquired).Times(3);
    EXPECT_CALL(impl2, notifyReleased).Times(3);

    checkWakelockLoop(4, "testLock1");
    checkWakelockLoop(3, "testLock2");

    cb1->disable();
    cb2->disable();
}

class SystemSuspendSameThreadTest : public ::testing::Test {
   public:
    std::shared_ptr<IWakeLock> acquireWakeLock(const std::string& name = "TestLock") {
        std::shared_ptr<IWakeLock> wl = nullptr;
        auto status = suspendService->acquireWakeLock(WakeLockType::PARTIAL, name, &wl);
        return wl;
    }

    /**
     * Returns true if wake lock is found else false.
     */
    bool findWakeLockInfoByName(const std::vector<WakeLockInfo>& wlStats, const std::string& name,
                                WakeLockInfo* info) {
        auto it = std::find_if(wlStats.begin(), wlStats.end(),
                               [&name](const auto& x) { return x.name == name; });
        if (it != wlStats.end()) {
            *info = *it;
            return true;
        }
        return false;
    }

    bool writeStatToFile(int statDirFd, const std::string& fileName, const std::string& stat) {
        unique_fd statFd{TEMP_FAILURE_RETRY(
            openat(statDirFd, fileName.c_str(), O_CREAT | O_CLOEXEC | O_RDWR, S_IRWXU))};
        if (statFd < 0) {
            PLOG(ERROR) << "SystemSuspend: Error opening " << fileName;
            return false;
        }

        if (!WriteStringToFd(stat, statFd.get())) {
            PLOG(ERROR) << "SystemSuspend: Error writing stat to " << fileName;
            return false;
        }

        return true;
    }

    bool writeStatToFile(int statDirFd, const std::string& fileName, int64_t stat) {
        return writeStatToFile(statDirFd, fileName, std::to_string(stat));
    }

    /**
     * Creates a kernel wakelock directory and stats files.
     * Returns true on success else false.
     */
    bool addKernelWakelock(const std::string& name, int64_t activeCount = 42,
                           int64_t activeTime = 42, int64_t eventCount = 42,
                           int64_t expireCount = 42, int64_t lastChange = 42, int64_t maxTime = 42,
                           int64_t preventSuspendTime = 42, int64_t totalTime = 42,
                           int64_t wakeupCount = 42) {
        static int id = 0;
        std::string kwlId = "wakeup" + std::to_string(id++);

        if ((mkdirat(kernelWakelockStatsFd, kwlId.c_str(), S_IRWXU)) < 0) {
            PLOG(ERROR) << "SystemSuspend: Error creating directory for " << kwlId;
            return false;
        }

        unique_fd kernelWakelockFd{TEMP_FAILURE_RETRY(
            openat(kernelWakelockStatsFd, kwlId.c_str(), O_DIRECTORY | O_CLOEXEC | O_RDONLY))};
        if (kernelWakelockFd < 0) {
            PLOG(ERROR) << "SystemSuspend: Error opening " << kwlId;
            return false;
        }

        int fd = kernelWakelockFd.get();

        return writeStatToFile(fd, "name", name) &&
               writeStatToFile(fd, "active_count", activeCount) &&
               writeStatToFile(fd, "active_time_ms", activeTime) &&
               writeStatToFile(fd, "event_count", eventCount) &&
               writeStatToFile(fd, "expire_count", expireCount) &&
               writeStatToFile(fd, "last_change_ms", lastChange) &&
               writeStatToFile(fd, "max_time_ms", maxTime) &&
               writeStatToFile(fd, "prevent_suspend_time_ms", preventSuspendTime) &&
               writeStatToFile(fd, "total_time_ms", totalTime) &&
               writeStatToFile(fd, "wakeup_count", wakeupCount);
    }

    /**
     * Adds Suspend stats files to suspendStatDir.
     * Returns true on success else false.
     */
    bool addSuspendStats(int64_t success = 42, int64_t fail = 42, int64_t failedFreeze = 42,
                         int64_t failedPrepare = 42, int64_t failedSuspend = 42,
                         int64_t failedSuspendLate = 42, int64_t failedSuspendNoirq = 42,
                         int64_t failedResume = 42, int64_t failedResumeEarly = 42,
                         int64_t failedResumeNoirq = 42,
                         const std::string& lastFailedDev = "fakeDev", int64_t lastFailedErrno = 42,
                         const std::string& lastFailedStep = "fakeStep") {
        int fd = suspendStatsFd.get();

        return writeStatToFile(fd, "success", success) && writeStatToFile(fd, "fail", fail) &&
               writeStatToFile(fd, "failed_freeze", failedFreeze) &&
               writeStatToFile(fd, "failed_prepare", failedPrepare) &&
               writeStatToFile(fd, "failed_suspend", failedSuspend) &&
               writeStatToFile(fd, "failed_suspend_late", failedSuspendLate) &&
               writeStatToFile(fd, "failed_suspend_noirq", failedSuspendNoirq) &&
               writeStatToFile(fd, "failed_resume", failedResume) &&
               writeStatToFile(fd, "failed_resume_early", failedResumeEarly) &&
               writeStatToFile(fd, "failed_resume_noirq", failedResumeNoirq) &&
               writeStatToFile(fd, "last_failed_dev", lastFailedDev) &&
               writeStatToFile(fd, "last_failed_errno", lastFailedErrno) &&
               writeStatToFile(fd, "last_failed_step", lastFailedStep);
    }

    bool removeDirectoryEntry(const std::string& path) {
        auto callback = [](const char* child, const struct stat*, int file_type,
                           struct FTW*) -> int {
            switch (file_type) {
                case FTW_D:
                case FTW_DP:
                case FTW_DNR:
                    if (rmdir(child) == -1) {
                        PLOG(ERROR) << "rmdir " << child;
                    }
                    break;
                case FTW_NS:
                default:
                    if (rmdir(child) != -1) break;
                    // FALLTHRU (for gcc, lint, pcc, etc; and following for clang)
                    FALLTHROUGH_INTENDED;
                case FTW_F:
                case FTW_SL:
                case FTW_SLN:
                    if (unlink(child) == -1) {
                        PLOG(ERROR) << "unlink " << child;
                    }
                    break;
            }
            return 0;
        };
        return nftw(path.c_str(), callback, 128, FTW_DEPTH | FTW_MOUNT | FTW_PHYS) == 0;
    }

    /**
     * Removes all entries from directory.
     * Returns true on success else false.
     */
    bool clearDirectory(const std::string& dirPath) {
        std::unique_ptr<DIR, decltype(&closedir)> dp(opendir(dirPath.c_str()), &closedir);
        if (!dp) {
            return false;
        }

        rewinddir(dp.get());
        struct dirent* de;
        while ((de = readdir(dp.get()))) {
            std::string name(de->d_name);
            if ((name == ".") || (name == "..")) {
                continue;
            }
            if (!removeDirectoryEntry(dirPath + "/" + name)) {
                PLOG(ERROR) << "SystemSuspend: Failed to remove " << name;
                return false;
            }
        }

        return true;
    }

    /**
     * Returns wakelock stats.
     */
    std::vector<WakeLockInfo> getWakelockStats() {
        std::vector<WakeLockInfo> wlStats;
        controlServiceInternal->getWakeLockStats(&wlStats);
        return wlStats;
    }

    /**
     * Returns suspend stats.
     */
    Result<SuspendStats> getSuspendStats() { return systemSuspend->getSuspendStats(); }

    virtual void SetUp() override {
        kernelWakelockStatsFd = unique_fd(TEMP_FAILURE_RETRY(
            open(kernelWakelockStatsDir.path, O_DIRECTORY | O_CLOEXEC | O_RDONLY)));
        if (kernelWakelockStatsFd < 0) {
            PLOG(FATAL) << "SystemSuspend: Failed to open kernel wakelock stats directory";
        }

        suspendStatsFd = unique_fd(
            TEMP_FAILURE_RETRY(open(suspendStatsDir.path, O_DIRECTORY | O_CLOEXEC | O_RDONLY)));
        if (suspendStatsFd < 0) {
            PLOG(FATAL) << "SystemSuspend: Failed to open suspend_stats directory";
        }

        // Set up same thread suspend services
        sp<SuspendControlService> suspendControl = new SuspendControlService();
        sp<SuspendControlServiceInternal> suspendControlInternal =
            new SuspendControlServiceInternal();
        controlService = suspendControl;
        controlServiceInternal = suspendControlInternal;
        systemSuspend = new SystemSuspend(
            unique_fd(-1) /* wakeupCountFd */, unique_fd(-1) /* stateFd */,
            unique_fd(dup(suspendStatsFd)), 1 /* maxNativeStatsEntries */,
            unique_fd(dup(kernelWakelockStatsFd.get())), unique_fd(-1) /* wakeupReasonsFd */,
            unique_fd(-1) /* suspendTimeFd */, kSleepTimeConfig, suspendControl,
            suspendControlInternal);

        suspendService = ndk::SharedRefBase::make<SystemSuspendAidl>(systemSuspend.get());
    }

    virtual void TearDown() override {
        systemSuspend->disableAutosuspend();
        ASSERT_TRUE(clearDirectory(kernelWakelockStatsDir.path));
        ASSERT_TRUE(clearDirectory(suspendStatsDir.path));
    }

    sp<SystemSuspend> systemSuspend;
    std::shared_ptr<ISystemSuspend> suspendService;
    sp<ISuspendControlService> controlService;
    sp<ISuspendControlServiceInternal> controlServiceInternal;
    unique_fd kernelWakelockStatsFd;
    unique_fd suspendStatsFd;
    TemporaryDir kernelWakelockStatsDir;
    TemporaryDir suspendStatsDir;

    const SleepTimeConfig kSleepTimeConfig = {
        .baseSleepTime = 100ms,
        .maxSleepTime = 400ms,
        .sleepTimeScaleFactor = 1.9,
        .backoffThreshold = 1,
        .shortSuspendThreshold = 100ms,
        .failedSuspendBackoffEnabled = true,
        .shortSuspendBackoffEnabled = true,
    };
};

// Test that getWakeLockStats has correct information about Native WakeLocks.
TEST_F(SystemSuspendSameThreadTest, GetNativeWakeLockStats) {
    std::string fakeWlName = "FakeLock";
    {
        std::shared_ptr<IWakeLock> fakeLock = acquireWakeLock(fakeWlName);
        std::vector<WakeLockInfo> wlStats = getWakelockStats();
        ASSERT_EQ(wlStats.size(), 1);

        WakeLockInfo nwlInfo;
        ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeWlName, &nwlInfo));
        ASSERT_EQ(nwlInfo.name, fakeWlName);
        ASSERT_EQ(nwlInfo.activeCount, 1);
        ASSERT_EQ(nwlInfo.isActive, true);
        ASSERT_FALSE(nwlInfo.isKernelWakelock);

        ASSERT_EQ(nwlInfo.pid, getpid());

        ASSERT_EQ(nwlInfo.eventCount, 0);
        ASSERT_EQ(nwlInfo.expireCount, 0);
        ASSERT_EQ(nwlInfo.preventSuspendTime, 0);
        ASSERT_EQ(nwlInfo.wakeupCount, 0);

        // We sleep so that the wake lock stats entry get updated with a different timestamp.
        std::this_thread::sleep_for(1s);
    }
    std::vector<WakeLockInfo> wlStats = getWakelockStats();
    ASSERT_EQ(wlStats.size(), 1);

    WakeLockInfo nwlInfo;
    ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeWlName, &nwlInfo));
    ASSERT_EQ(nwlInfo.name, fakeWlName);
    ASSERT_EQ(nwlInfo.activeCount, 1);
    ASSERT_GE(nwlInfo.maxTime, 1000);
    ASSERT_GE(nwlInfo.totalTime, 1000);
    ASSERT_EQ(nwlInfo.isActive, false);
    ASSERT_EQ(nwlInfo.activeTime, 0);  // No longer active
    ASSERT_FALSE(nwlInfo.isKernelWakelock);

    ASSERT_EQ(nwlInfo.pid, getpid());

    ASSERT_EQ(nwlInfo.eventCount, 0);
    ASSERT_EQ(nwlInfo.expireCount, 0);
    ASSERT_EQ(nwlInfo.preventSuspendTime, 0);
    ASSERT_EQ(nwlInfo.wakeupCount, 0);
}

// Test that getWakeLockStats has correct information about Kernel WakeLocks.
TEST_F(SystemSuspendSameThreadTest, GetKernelWakeLockStats) {
    std::string fakeKwlName1 = "fakeKwl1";
    std::string fakeKwlName2 = "fakeKwl2";
    addKernelWakelock(fakeKwlName1);
    addKernelWakelock(fakeKwlName2, 10 /* activeCount */);

    std::vector<WakeLockInfo> wlStats = getWakelockStats();

    ASSERT_EQ(wlStats.size(), 2);

    WakeLockInfo kwlInfo1;
    ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeKwlName1, &kwlInfo1));
    ASSERT_EQ(kwlInfo1.name, fakeKwlName1);
    ASSERT_EQ(kwlInfo1.activeCount, 42);
    ASSERT_EQ(kwlInfo1.lastChange, 42);
    ASSERT_EQ(kwlInfo1.maxTime, 42);
    ASSERT_EQ(kwlInfo1.totalTime, 42);
    ASSERT_EQ(kwlInfo1.isActive, true);
    ASSERT_EQ(kwlInfo1.activeTime, 42);
    ASSERT_TRUE(kwlInfo1.isKernelWakelock);

    ASSERT_EQ(kwlInfo1.pid, -1);

    ASSERT_EQ(kwlInfo1.eventCount, 42);
    ASSERT_EQ(kwlInfo1.expireCount, 42);
    ASSERT_EQ(kwlInfo1.preventSuspendTime, 42);
    ASSERT_EQ(kwlInfo1.wakeupCount, 42);

    WakeLockInfo kwlInfo2;
    ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeKwlName2, &kwlInfo2));
    ASSERT_EQ(kwlInfo2.name, fakeKwlName2);
    ASSERT_EQ(kwlInfo2.activeCount, 10);
    ASSERT_EQ(kwlInfo2.lastChange, 42);
    ASSERT_EQ(kwlInfo2.maxTime, 42);
    ASSERT_EQ(kwlInfo2.totalTime, 42);
    ASSERT_EQ(kwlInfo2.isActive, true);
    ASSERT_EQ(kwlInfo2.activeTime, 42);
    ASSERT_TRUE(kwlInfo2.isKernelWakelock);

    ASSERT_EQ(kwlInfo2.pid, -1);

    ASSERT_EQ(kwlInfo2.eventCount, 42);
    ASSERT_EQ(kwlInfo2.expireCount, 42);
    ASSERT_EQ(kwlInfo2.preventSuspendTime, 42);
    ASSERT_EQ(kwlInfo2.wakeupCount, 42);
}

// Test that getWakeLockStats has correct information about Native AND Kernel WakeLocks.
TEST_F(SystemSuspendSameThreadTest, GetNativeAndKernelWakeLockStats) {
    std::string fakeNwlName = "fakeNwl";
    std::string fakeKwlName = "fakeKwl";

    addKernelWakelock(fakeKwlName);

    {
        std::shared_ptr<IWakeLock> fakeLock = acquireWakeLock(fakeNwlName);
        std::vector<WakeLockInfo> wlStats = getWakelockStats();
        ASSERT_EQ(wlStats.size(), 2);

        // Native Wakelock Stats
        WakeLockInfo nwlInfo;
        ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeNwlName, &nwlInfo));
        ASSERT_EQ(nwlInfo.name, fakeNwlName);
        ASSERT_EQ(nwlInfo.activeCount, 1);
        ASSERT_EQ(nwlInfo.isActive, true);
        ASSERT_FALSE(nwlInfo.isKernelWakelock);

        ASSERT_EQ(nwlInfo.pid, getpid());

        ASSERT_EQ(nwlInfo.eventCount, 0);
        ASSERT_EQ(nwlInfo.expireCount, 0);
        ASSERT_EQ(nwlInfo.preventSuspendTime, 0);
        ASSERT_EQ(nwlInfo.wakeupCount, 0);

        // Kernel Wakelock Stats
        WakeLockInfo kwlInfo;
        ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeKwlName, &kwlInfo));
        ASSERT_EQ(kwlInfo.name, fakeKwlName);
        ASSERT_EQ(kwlInfo.activeCount, 42);
        ASSERT_EQ(kwlInfo.lastChange, 42);
        ASSERT_EQ(kwlInfo.maxTime, 42);
        ASSERT_EQ(kwlInfo.totalTime, 42);
        ASSERT_EQ(kwlInfo.isActive, true);
        ASSERT_EQ(kwlInfo.activeTime, 42);
        ASSERT_TRUE(kwlInfo.isKernelWakelock);

        ASSERT_EQ(kwlInfo.pid, -1);

        ASSERT_EQ(kwlInfo.eventCount, 42);
        ASSERT_EQ(kwlInfo.expireCount, 42);
        ASSERT_EQ(kwlInfo.preventSuspendTime, 42);
        ASSERT_EQ(kwlInfo.wakeupCount, 42);

        // We sleep so that the wake lock stats entry get updated with a different timestamp.
        std::this_thread::sleep_for(1s);
    }
    std::vector<WakeLockInfo> wlStats = getWakelockStats();
    ASSERT_EQ(wlStats.size(), 2);

    // Native Wakelock Stats
    WakeLockInfo nwlInfo;
    ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeNwlName, &nwlInfo));
    ASSERT_EQ(nwlInfo.name, fakeNwlName);
    ASSERT_EQ(nwlInfo.activeCount, 1);
    ASSERT_GE(nwlInfo.maxTime, 1000);
    ASSERT_GE(nwlInfo.totalTime, 1000);
    ASSERT_EQ(nwlInfo.isActive, false);
    ASSERT_EQ(nwlInfo.activeTime, 0);  // No longer active
    ASSERT_FALSE(nwlInfo.isKernelWakelock);

    ASSERT_EQ(nwlInfo.pid, getpid());

    ASSERT_EQ(nwlInfo.eventCount, 0);
    ASSERT_EQ(nwlInfo.expireCount, 0);
    ASSERT_EQ(nwlInfo.preventSuspendTime, 0);
    ASSERT_EQ(nwlInfo.wakeupCount, 0);

    // Kernel Wakelock Stats (No changes expected here)
    WakeLockInfo kwlInfo;
    ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeKwlName, &kwlInfo));
    ASSERT_EQ(kwlInfo.name, fakeKwlName);
    ASSERT_EQ(kwlInfo.activeCount, 42);
    ASSERT_EQ(kwlInfo.lastChange, 42);
    ASSERT_EQ(kwlInfo.maxTime, 42);
    ASSERT_EQ(kwlInfo.totalTime, 42);
    ASSERT_EQ(kwlInfo.isActive, true);
    ASSERT_EQ(kwlInfo.activeTime, 42);
    ASSERT_TRUE(kwlInfo.isKernelWakelock);

    ASSERT_EQ(kwlInfo.pid, -1);

    ASSERT_EQ(kwlInfo.eventCount, 42);
    ASSERT_EQ(kwlInfo.expireCount, 42);
    ASSERT_EQ(kwlInfo.preventSuspendTime, 42);
    ASSERT_EQ(kwlInfo.wakeupCount, 42);
}

// Test that the least recently used native wake lock stats entry is evicted after a given
// threshold.
TEST_F(SystemSuspendSameThreadTest, NativeWakeLockStatsLruEviction) {
    std::string fakeWlName1 = "FakeLock1";
    std::string fakeWlName2 = "FakeLock2";

    acquireWakeLock(fakeWlName1);
    acquireWakeLock(fakeWlName2);

    std::vector<WakeLockInfo> wlStats = getWakelockStats();

    // Max number of native stats entries was set to 1 in SystemSuspend constructor.
    ASSERT_EQ(wlStats.size(), 1);
    ASSERT_EQ(wlStats.begin()->name, fakeWlName2);

    WakeLockInfo wlInfo;
    ASSERT_TRUE(findWakeLockInfoByName(wlStats, fakeWlName2, &wlInfo));
    ASSERT_FALSE(findWakeLockInfoByName(wlStats, fakeWlName1, &wlInfo));  // Evicted
}

// Test that GetSuspendStats has correct information.
TEST_F(SystemSuspendSameThreadTest, GetSuspendStats) {
    addSuspendStats();

    Result<SuspendStats> res = getSuspendStats();
    ASSERT_RESULT_OK(res);

    SuspendStats stats = res.value();

    ASSERT_EQ(stats.success, 42);
    ASSERT_EQ(stats.fail, 42);
    ASSERT_EQ(stats.failedFreeze, 42);
    ASSERT_EQ(stats.failedPrepare, 42);
    ASSERT_EQ(stats.failedSuspend, 42);
    ASSERT_EQ(stats.failedSuspendLate, 42);
    ASSERT_EQ(stats.failedSuspendNoirq, 42);
    ASSERT_EQ(stats.failedResume, 42);
    ASSERT_EQ(stats.failedResumeEarly, 42);
    ASSERT_EQ(stats.failedResumeNoirq, 42);
    ASSERT_EQ(stats.lastFailedDev, "fakeDev");
    ASSERT_EQ(stats.lastFailedErrno, 42);
    ASSERT_EQ(stats.lastFailedStep, "fakeStep");
}

class SuspendWakeupTest : public ::testing::Test {
   public:
    virtual void SetUp() override {
        Socketpair(SOCK_STREAM, &wakeupCountTestFd, &wakeupCountServiceFd);
        Socketpair(SOCK_STREAM, &stateTestFd, &stateServiceFd);

        suspendControl = new SuspendControlService();

        suspendControlInternal = new SuspendControlServiceInternal();

        suspendTimeFd =
            unique_fd(TEMP_FAILURE_RETRY(open(suspendTimeFile.path, O_CLOEXEC | O_RDONLY)));

        wakeupReasonsFd =
            unique_fd(TEMP_FAILURE_RETRY(open(wakeupReasonsFile.path, O_CLOEXEC | O_RDONLY)));

        systemSuspend = new SystemSuspend(
            std::move(wakeupCountServiceFd), std::move(stateServiceFd),
            unique_fd(-1) /* suspendStatsFd */, 100 /* maxStatsEntries */,
            unique_fd(-1) /* kernelWakelockStatsFd */, std::move(wakeupReasonsFd),
            std::move(suspendTimeFd), kSleepTimeConfig, suspendControl, suspendControlInternal);

        // Start auto-suspend.
        bool enabled = false;
        suspendControlInternal->enableAutosuspend(new BBinder(), &enabled);
        ASSERT_EQ(enabled, true) << "failed to start autosuspend";
    }

    virtual void TearDown() override { systemSuspend->disableAutosuspend(); }

    std::shared_ptr<IWakeLock> acquireWakeLock(const std::string& name = "TestLock") {
        auto suspendService = ndk::SharedRefBase::make<SystemSuspendAidl>(systemSuspend.get());
        std::shared_ptr<IWakeLock> wl = nullptr;
        auto status = suspendService->acquireWakeLock(WakeLockType::PARTIAL, name, &wl);
        return wl;
    }

    void wakeup(std::string wakeupReason) {
        ASSERT_TRUE(WriteStringToFile(wakeupReason, wakeupReasonsFile.path));
        checkLoop(1);
    }

    void checkLoop(int numIter) {
        for (int i = 0; i < numIter; i++) {
            // Mock value for /sys/power/wakeup_count.
            std::string wakeupCount = std::to_string(rand());
            ASSERT_TRUE(WriteStringToFd(wakeupCount, wakeupCountTestFd));
            ASSERT_EQ(readFd(wakeupCountTestFd), wakeupCount)
                << "wakeup count value written by SystemSuspend is not equal to value given to it";
            ASSERT_EQ(readFd(stateTestFd), "mem")
                << "SystemSuspend failed to write correct sleep state.";
            // There is a race window where sleepTime can be checked in the tests,
            // before it is updated in autoSuspend
            while (!isReadBlocked(wakeupCountTestFd)) {
            }
        }
    }

    void suspendFor(std::chrono::milliseconds suspendTime,
                    std::chrono::milliseconds suspendOverhead, int numberOfSuspends) {
        std::string suspendStr =
            std::to_string(
                std::chrono::duration_cast<std::chrono::duration<double>>(suspendOverhead)
                    .count()) +
            " " +
            std::to_string(
                std::chrono::duration_cast<std::chrono::duration<double>>(suspendTime).count());
        ASSERT_TRUE(WriteStringToFile(suspendStr, suspendTimeFile.path));
        checkLoop(numberOfSuspends);
    }

    void checkSuspendInfo(const SuspendInfo& expected) {
        SuspendInfo actual;
        systemSuspend->getSuspendInfo(&actual);

        ASSERT_EQ(actual.suspendAttemptCount, expected.suspendAttemptCount);
        ASSERT_EQ(actual.failedSuspendCount, expected.failedSuspendCount);
        ASSERT_EQ(actual.shortSuspendCount, expected.shortSuspendCount);
        ASSERT_EQ(actual.suspendTimeMillis, expected.suspendTimeMillis);
        ASSERT_EQ(actual.shortSuspendTimeMillis, expected.shortSuspendTimeMillis);
        ASSERT_EQ(actual.suspendOverheadTimeMillis, expected.suspendOverheadTimeMillis);
        ASSERT_EQ(actual.failedSuspendOverheadTimeMillis, expected.failedSuspendOverheadTimeMillis);
        ASSERT_EQ(actual.newBackoffCount, expected.newBackoffCount);
        ASSERT_EQ(actual.backoffContinueCount, expected.backoffContinueCount);
        ASSERT_EQ(actual.sleepTimeMillis, expected.sleepTimeMillis);
    }

    unique_fd wakeupCountServiceFd;
    unique_fd stateServiceFd;
    unique_fd stateTestFd;
    unique_fd wakeupCountTestFd;
    unique_fd wakeupReasonsFd;
    unique_fd suspendTimeFd;
    TemporaryFile wakeupReasonsFile;
    TemporaryFile suspendTimeFile;
    TemporaryFile stateFile;
    TemporaryFile wakeupCountFile;
    sp<SuspendControlService> suspendControl;
    sp<SuspendControlServiceInternal> suspendControlInternal;
    sp<SystemSuspend> systemSuspend;

    const SleepTimeConfig kSleepTimeConfig = {
        .baseSleepTime = 100ms,
        .maxSleepTime = 400ms,
        .sleepTimeScaleFactor = 1.9,
        .backoffThreshold = 1,
        .shortSuspendThreshold = 100ms,
        .failedSuspendBackoffEnabled = true,
        .shortSuspendBackoffEnabled = true,
    };

    const int64_t kLongSuspendMillis = 10000;  // >= kSleepTimeConfig.shortSuspendThreshold
    const int64_t kShortSuspendMillis = 10;    // < kSleepTimeConfig.shortSuspendThreshold
    const int64_t kSuspendOverheadMillis = 20;
};

TEST_F(SuspendWakeupTest, LongSuspendStat) {
    suspendFor(std::chrono::milliseconds(kLongSuspendMillis),
               std::chrono::milliseconds(kSuspendOverheadMillis), 1);
    SuspendInfo expected;
    expected.suspendAttemptCount = 1;
    expected.suspendTimeMillis = kLongSuspendMillis;
    expected.suspendOverheadTimeMillis = kSuspendOverheadMillis;
    expected.sleepTimeMillis = kSleepTimeConfig.baseSleepTime.count();
    checkSuspendInfo(expected);
}

TEST_F(SuspendWakeupTest, ShortSuspendStat) {
    suspendFor(std::chrono::milliseconds(kShortSuspendMillis),
               std::chrono::milliseconds(kSuspendOverheadMillis), 1);
    SuspendInfo expected;
    expected.suspendAttemptCount = 1;
    expected.shortSuspendCount = 1;
    expected.shortSuspendTimeMillis = kShortSuspendMillis;
    expected.suspendTimeMillis = kShortSuspendMillis;
    expected.suspendOverheadTimeMillis = kSuspendOverheadMillis;
    expected.sleepTimeMillis = kSleepTimeConfig.baseSleepTime.count();
    checkSuspendInfo(expected);
}

TEST_F(SuspendWakeupTest, ShortSuspendBackoffStat) {
    suspendFor(std::chrono::milliseconds(kShortSuspendMillis),
               std::chrono::milliseconds(kSuspendOverheadMillis), 2);
    SuspendInfo expected;
    expected.suspendAttemptCount = 2;
    expected.shortSuspendCount = 2;
    expected.shortSuspendTimeMillis = kShortSuspendMillis * 2;
    expected.suspendTimeMillis = kShortSuspendMillis * 2;
    expected.suspendOverheadTimeMillis = kSuspendOverheadMillis * 2;
    expected.newBackoffCount = 1;
    expected.sleepTimeMillis = kSleepTimeConfig.baseSleepTime.count() * 2;
    checkSuspendInfo(expected);
}

TEST_F(SuspendWakeupTest, ShortSuspendBackoffContinueStat) {
    suspendFor(std::chrono::milliseconds(kShortSuspendMillis),
               std::chrono::milliseconds(kSuspendOverheadMillis), 2);
    SuspendInfo expected;
    expected.suspendAttemptCount = 2;
    expected.shortSuspendCount = 2;
    expected.shortSuspendTimeMillis = kShortSuspendMillis * 2;
    expected.suspendTimeMillis = kShortSuspendMillis * 2;
    expected.suspendOverheadTimeMillis = kSuspendOverheadMillis * 2;
    expected.newBackoffCount = 1;
    expected.sleepTimeMillis = kSleepTimeConfig.baseSleepTime.count() * 2;
    checkSuspendInfo(expected);

    suspendFor(std::chrono::milliseconds(kShortSuspendMillis),
               std::chrono::milliseconds(kSuspendOverheadMillis), 1);
    expected.suspendAttemptCount += 1;
    expected.shortSuspendCount += 1;
    expected.shortSuspendTimeMillis += kShortSuspendMillis;
    expected.suspendTimeMillis += kShortSuspendMillis;
    expected.suspendOverheadTimeMillis += kSuspendOverheadMillis;
    expected.backoffContinueCount += 1;
    expected.sleepTimeMillis +=
        std::chrono::round<std::chrono::milliseconds>(kSleepTimeConfig.baseSleepTime *
                                                      kSleepTimeConfig.sleepTimeScaleFactor)
            .count();
    checkSuspendInfo(expected);
}

TEST_F(SuspendWakeupTest, GetSingleWakeupReasonStat) {
    wakeup("abc");

    std::vector<WakeupInfo> wStats;
    ASSERT_TRUE(suspendControlInternal->getWakeupStats(&wStats).isOk());
    ASSERT_EQ(wStats.size(), 1);
    ASSERT_EQ(wStats[0].name, "abc");
    ASSERT_EQ(wStats[0].count, 1);
}

TEST_F(SuspendWakeupTest, GetChainedWakeupReasonStat) {
    wakeup("a\nb");

    std::vector<WakeupInfo> wStats;
    ASSERT_TRUE(suspendControlInternal->getWakeupStats(&wStats).isOk());
    ASSERT_EQ(wStats.size(), 1);
    ASSERT_EQ(wStats[0].name, "a;b");
    ASSERT_EQ(wStats[0].count, 1);
}

TEST_F(SuspendWakeupTest, GetMultipleWakeupReasonStats) {
    wakeup("abc");
    wakeup("d\ne");
    wakeup("");
    wakeup("");
    wakeup("wxyz\nabc\n");
    wakeup("abc");

    std::vector<WakeupInfo> wStats;
    ASSERT_TRUE(suspendControlInternal->getWakeupStats(&wStats).isOk());
    ASSERT_EQ(wStats.size(), 4);
    ASSERT_EQ(wStats[0].name, "abc");
    ASSERT_EQ(wStats[0].count, 2);
    ASSERT_EQ(wStats[1].name, "wxyz;abc");
    ASSERT_EQ(wStats[1].count, 1);
    ASSERT_EQ(wStats[2].name, "unknown");
    ASSERT_EQ(wStats[2].count, 2);
    ASSERT_EQ(wStats[3].name, "d;e");
    ASSERT_EQ(wStats[3].count, 1);
}

TEST(WakeupListTest, TestEmpty) {
    WakeupList wakeupList(3);

    std::vector<WakeupInfo> wakeups;
    wakeupList.getWakeupStats(&wakeups);

    ASSERT_TRUE(wakeups.empty());
}

TEST(WakeupListTest, TestNoCapacity) {
    WakeupList wakeupList(0);

    wakeupList.update({"a"});

    std::vector<WakeupInfo> wakeups;
    wakeupList.getWakeupStats(&wakeups);

    ASSERT_TRUE(wakeups.empty());
}

TEST(WakeupListTest, TestConcat) {
    WakeupList wakeupList(3);

    wakeupList.update({"a", "b"});

    std::vector<WakeupInfo> wakeups;
    wakeupList.getWakeupStats(&wakeups);

    ASSERT_EQ(wakeups[0].name, "a;b");
    ASSERT_EQ(wakeups[0].count, 1);
}

TEST(WakeupListTest, TestNewEntry) {
    WakeupList wakeupList(3);

    wakeupList.update({"a"});
    wakeupList.update({"b"});

    std::vector<WakeupInfo> wakeups;
    wakeupList.getWakeupStats(&wakeups);

    ASSERT_EQ(wakeups[1].name, "a");
    ASSERT_EQ(wakeups[1].count, 1);
    ASSERT_EQ(wakeups[0].name, "b");
    ASSERT_EQ(wakeups[0].count, 1);
}

TEST(WakeupListTest, TestIncrement) {
    WakeupList wakeupList(3);

    wakeupList.update({"a"});
    wakeupList.update({"b"});
    wakeupList.update({"a"});

    std::vector<WakeupInfo> wakeups;
    wakeupList.getWakeupStats(&wakeups);

    ASSERT_EQ(wakeups[0].name, "a");
    ASSERT_EQ(wakeups[0].count, 2);
    ASSERT_EQ(wakeups[1].name, "b");
    ASSERT_EQ(wakeups[1].count, 1);
}

TEST(WakeupListTest, TestCapacity) {
    WakeupList wakeupList(3);

    wakeupList.update({"a"});
    wakeupList.update({"b"});
    wakeupList.update({"c"});
    wakeupList.update({"d"});

    std::vector<WakeupInfo> wakeups;
    wakeupList.getWakeupStats(&wakeups);

    ASSERT_EQ(wakeups.size(), 3);
    ASSERT_EQ(wakeups[0].name, "d");
    ASSERT_EQ(wakeups[0].count, 1);
    ASSERT_EQ(wakeups[1].name, "c");
    ASSERT_EQ(wakeups[1].count, 1);
    ASSERT_EQ(wakeups[2].name, "b");
    ASSERT_EQ(wakeups[2].count, 1);
}

TEST(WakeupListTest, TestLRUEvict) {
    WakeupList wakeupList(3);

    wakeupList.update({"a"});
    wakeupList.update({"b"});
    wakeupList.update({"a"});
    wakeupList.update({"c"});
    wakeupList.update({"c"});
    wakeupList.update({"c"});
    wakeupList.update({"d"});

    std::vector<WakeupInfo> wakeups;
    wakeupList.getWakeupStats(&wakeups);

    ASSERT_EQ(wakeups.size(), 3);
    ASSERT_EQ(wakeups[0].name, "d");
    ASSERT_EQ(wakeups[0].count, 1);
    ASSERT_EQ(wakeups[1].name, "c");
    ASSERT_EQ(wakeups[1].count, 3);
    ASSERT_EQ(wakeups[2].name, "a");
    ASSERT_EQ(wakeups[2].count, 2);
}

}  // namespace android

int main(int argc, char** argv) {
    android::hardware::details::setTrebleTestingOverride(true);
    ::testing::InitGoogleMock(&argc, argv);
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
