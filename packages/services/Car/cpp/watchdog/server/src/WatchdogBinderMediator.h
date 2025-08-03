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

#ifndef CPP_WATCHDOG_SERVER_SRC_WATCHDOGBINDERMEDIATOR_H_
#define CPP_WATCHDOG_SERVER_SRC_WATCHDOGBINDERMEDIATOR_H_

#include "IoOveruseMonitor.h"
#include "WatchdogInternalHandler.h"
#include "WatchdogPerfService.h"
#include "WatchdogProcessService.h"
#include "WatchdogServiceHelper.h"

#include <aidl/android/automotive/watchdog/BnCarWatchdog.h>
#include <aidl/android/automotive/watchdog/ICarWatchdogClient.h>
#include <aidl/android/automotive/watchdog/ICarWatchdogMonitor.h>
#include <aidl/android/automotive/watchdog/IResourceOveruseListener.h>
#include <aidl/android/automotive/watchdog/ResourceOveruseStats.h>
#include <aidl/android/automotive/watchdog/ResourceType.h>
#include <aidl/android/automotive/watchdog/StateType.h>
#include <aidl/android/automotive/watchdog/TimeoutLength.h>
#include <android-base/result.h>
#include <android/binder_auto_utils.h>
#include <android/binder_libbinder.h>
#include <android/binder_manager.h>
#include <gtest/gtest_prod.h>
#include <utils/Errors.h>
#include <utils/RefBase.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>

#include <functional>

namespace android {
namespace automotive {
namespace watchdog {

class ServiceManager;

// Forward declaration for testing use only.
namespace internal {

class WatchdogBinderMediatorPeer;

}  // namespace internal

class WatchdogBinderMediatorInterface : public aidl::android::automotive::watchdog::BnCarWatchdog {
public:
    virtual android::base::Result<void> init() = 0;
    virtual void terminate() = 0;
};

// WatchdogBinderMediator implements the public carwatchdog binder APIs such that it forwards
// the calls either to process ANR or performance services.
class WatchdogBinderMediator final : public WatchdogBinderMediatorInterface {
public:
    WatchdogBinderMediator(
            const android::sp<WatchdogProcessServiceInterface>& watchdogProcessService,
            const android::sp<WatchdogPerfServiceInterface>& watchdogPerfService,
            const android::sp<WatchdogServiceHelperInterface>& watchdogServiceHelper,
            const android::sp<IoOveruseMonitorInterface>& ioOveruseMonitor,
            const std::function<android::base::Result<void>(const char*, ndk::ICInterface*, bool,
                                                            int)>& addServiceHandler = nullptr);
    ~WatchdogBinderMediator() { terminate(); }

    // Implements ICarWatchdog.aidl APIs.
    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;
    ndk::ScopedAStatus registerClient(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>& client,
            aidl::android::automotive::watchdog::TimeoutLength timeout) override;
    ndk::ScopedAStatus unregisterClient(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>& client)
            override;
    ndk::ScopedAStatus tellClientAlive(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>& client,
            int32_t sessionId) override;
    ndk::ScopedAStatus addResourceOveruseListener(
            const std::vector<aidl::android::automotive::watchdog::ResourceType>& resourceTypes,
            const std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>&
                    listener);
    ndk::ScopedAStatus removeResourceOveruseListener(
            const std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>&
                    listener);
    ndk::ScopedAStatus getResourceOveruseStats(
            const std::vector<aidl::android::automotive::watchdog::ResourceType>& resourceTypes,
            std::vector<aidl::android::automotive::watchdog::ResourceOveruseStats>*
                    resourceOveruseStats);

    // Deprecated APIs.
    ndk::ScopedAStatus registerMediator(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>&
                    mediator) override;
    ndk::ScopedAStatus unregisterMediator(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>&
                    mediator) override;
    ndk::ScopedAStatus registerMonitor(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogMonitor>&
                    monitor) override;
    ndk::ScopedAStatus unregisterMonitor(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogMonitor>&
                    monitor) override;
    ndk::ScopedAStatus tellMediatorAlive(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>&
                    mediator,
            const std::vector<int32_t>& clientsNotResponding, int32_t sessionId) override;
    ndk::ScopedAStatus tellDumpFinished(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogMonitor>&
                    monitor,
            int32_t pid) override;
    ndk::ScopedAStatus notifySystemStateChange(aidl::android::automotive::watchdog::StateType type,
                                               int32_t arg1, int32_t arg2) override;

protected:
    android::base::Result<void> init();

    void terminate() {
        mWatchdogProcessService.clear();
        mWatchdogPerfService.clear();
        mIoOveruseMonitor.clear();
        if (mWatchdogInternalHandler != nullptr) {
            mWatchdogInternalHandler->terminate();
            mWatchdogInternalHandler.reset();
        }
    }

private:
    android::sp<WatchdogProcessServiceInterface> mWatchdogProcessService;
    android::sp<WatchdogPerfServiceInterface> mWatchdogPerfService;
    android::sp<WatchdogServiceHelperInterface> mWatchdogServiceHelper;
    android::sp<IoOveruseMonitorInterface> mIoOveruseMonitor;
    std::shared_ptr<WatchdogInternalHandlerInterface> mWatchdogInternalHandler;

    // Used by tests to stub the call to IServiceManager.
    std::function<android::base::Result<void>(const char*, ndk::ICInterface*, bool, int)>
            mAddServiceHandler;

    friend class ServiceManager;

    // For unit tests.
    friend class internal::WatchdogBinderMediatorPeer;
    FRIEND_TEST(WatchdogBinderMediatorTest, TestInit);
    FRIEND_TEST(WatchdogBinderMediatorTest, TestErrorOnInitWithNullServiceInstances);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_SRC_WATCHDOGBINDERMEDIATOR_H_
