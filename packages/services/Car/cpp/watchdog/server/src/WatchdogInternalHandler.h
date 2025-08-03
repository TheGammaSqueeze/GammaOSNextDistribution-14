/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_WATCHDOG_SERVER_SRC_WATCHDOGINTERNALHANDLER_H_
#define CPP_WATCHDOG_SERVER_SRC_WATCHDOGINTERNALHANDLER_H_

#include "IoOveruseMonitor.h"
#include "ThreadPriorityController.h"
#include "WatchdogPerfService.h"
#include "WatchdogProcessService.h"
#include "WatchdogServiceHelper.h"

#include <aidl/android/automotive/watchdog/internal/BnCarWatchdog.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <aidl/android/automotive/watchdog/internal/ICarWatchdogMonitor.h>
#include <aidl/android/automotive/watchdog/internal/ICarWatchdogServiceForSystem.h>
#include <aidl/android/automotive/watchdog/internal/PowerCycle.h>
#include <aidl/android/automotive/watchdog/internal/ProcessIdentifier.h>
#include <aidl/android/automotive/watchdog/internal/ResourceOveruseConfiguration.h>
#include <aidl/android/automotive/watchdog/internal/StateType.h>
#include <aidl/android/automotive/watchdog/internal/UserPackageIoUsageStats.h>
#include <aidl/android/automotive/watchdog/internal/UserState.h>
#include <android/binder_auto_utils.h>
#include <gtest/gtest_prod.h>
#include <utils/Errors.h>
#include <utils/RefBase.h>
#include <utils/String16.h>
#include <utils/Vector.h>

namespace android {
namespace automotive {
namespace watchdog {

// Forward declaration for testing use only.
namespace internal {

class WatchdogInternalHandlerPeer;

}  // namespace internal

class WatchdogInternalHandlerInterface :
      public aidl::android::automotive::watchdog::internal::BnCarWatchdog {
public:
    virtual void terminate() = 0;
};

class WatchdogInternalHandler final : public WatchdogInternalHandlerInterface {
public:
    WatchdogInternalHandler(
            const android::sp<WatchdogServiceHelperInterface>& watchdogServiceHelper,
            const android::sp<WatchdogProcessServiceInterface>& watchdogProcessService,
            const android::sp<WatchdogPerfServiceInterface>& watchdogPerfService,
            const android::sp<IoOveruseMonitorInterface>& ioOveruseMonitor) :
          mWatchdogServiceHelper(watchdogServiceHelper),
          mWatchdogProcessService(watchdogProcessService),
          mWatchdogPerfService(watchdogPerfService),
          mIoOveruseMonitor(ioOveruseMonitor),
          mThreadPriorityController(std::make_unique<ThreadPriorityController>()) {}
    ~WatchdogInternalHandler() { terminate(); }

    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;
    ndk::ScopedAStatus registerCarWatchdogService(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service) override;
    ndk::ScopedAStatus unregisterCarWatchdogService(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service) override;
    ndk::ScopedAStatus registerMonitor(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor)
            override;
    ndk::ScopedAStatus unregisterMonitor(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor)
            override;
    ndk::ScopedAStatus tellCarWatchdogServiceAlive(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service,
            const std::vector<aidl::android::automotive::watchdog::internal::ProcessIdentifier>&
                    clientsNotResponding,
            int32_t sessionId) override;
    ndk::ScopedAStatus tellDumpFinished(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor,
            const aidl::android::automotive::watchdog::internal::ProcessIdentifier&
                    processIdentifier) override;
    ndk::ScopedAStatus notifySystemStateChange(
            aidl::android::automotive::watchdog::internal::StateType type, int32_t arg1,
            int32_t arg2) override;
    ndk::ScopedAStatus updateResourceOveruseConfigurations(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>&
                    configs) override;
    ndk::ScopedAStatus getResourceOveruseConfigurations(
            std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>*
                    configs) override;
    ndk::ScopedAStatus controlProcessHealthCheck(bool enable) override;
    ndk::ScopedAStatus setThreadPriority(int pid, int tid, int uid, int policy,
                                         int priority) override;
    ndk::ScopedAStatus getThreadPriority(
            int pid, int tid, int uid,
            aidl::android::automotive::watchdog::internal::ThreadPolicyWithPriority*
                    threadPolicyWithPriority) override;
    ndk::ScopedAStatus onAidlVhalPidFetched(int pid) override;
    ndk::ScopedAStatus onTodayIoUsageStatsFetched(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats>&
                    userPackageIoUsageStats) override;

    void terminate() override {
        mWatchdogServiceHelper.clear();
        mWatchdogProcessService.clear();
        mWatchdogPerfService.clear();
        mIoOveruseMonitor.clear();
    }

private:
    status_t dumpServices(int fd);
    status_t dumpProto(int fd);
    status_t dumpHelpText(const int fd, const std::string& errorMsg);
    void checkAndRegisterIoOveruseMonitor();
    ndk::ScopedAStatus handlePowerCycleChange(
            aidl::android::automotive::watchdog::internal::PowerCycle powerCycle);
    ndk::ScopedAStatus handleUserStateChange(
            userid_t userId,
            const aidl::android::automotive::watchdog::internal::UserState& userState);
    void setThreadPriorityController(std::unique_ptr<ThreadPriorityControllerInterface> controller);

    android::sp<WatchdogServiceHelperInterface> mWatchdogServiceHelper;
    android::sp<WatchdogProcessServiceInterface> mWatchdogProcessService;
    android::sp<WatchdogPerfServiceInterface> mWatchdogPerfService;
    android::sp<IoOveruseMonitorInterface> mIoOveruseMonitor;
    std::unique_ptr<ThreadPriorityControllerInterface> mThreadPriorityController;

    // For unit tests.
    friend class internal::WatchdogInternalHandlerPeer;
    FRIEND_TEST(WatchdogInternalHandlerTest, TestTerminate);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_SRC_WATCHDOGINTERNALHANDLER_H_
