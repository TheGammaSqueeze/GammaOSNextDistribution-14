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

#ifndef CPP_WATCHDOG_SERVER_SRC_WATCHDOGSERVICEHELPER_H_
#define CPP_WATCHDOG_SERVER_SRC_WATCHDOGSERVICEHELPER_H_

#include "AIBinderDeathRegistrationWrapper.h"
#include "WatchdogProcessService.h"

#include <aidl/android/automotive/watchdog/TimeoutLength.h>
#include <aidl/android/automotive/watchdog/internal/ICarWatchdogServiceForSystem.h>
#include <aidl/android/automotive/watchdog/internal/PackageInfo.h>
#include <aidl/android/automotive/watchdog/internal/PackageIoOveruseStats.h>
#include <aidl/android/automotive/watchdog/internal/ResourceStats.h>
#include <aidl/android/automotive/watchdog/internal/UserPackageIoUsageStats.h>
#include <android-base/result.h>
#include <android/binder_auto_utils.h>
#include <gtest/gtest_prod.h>
#include <utils/Mutex.h>
#include <utils/StrongPointer.h>

#include <shared_mutex>

namespace android {
namespace automotive {
namespace watchdog {

class ServiceManager;

// Forward declaration for testing use only.
namespace internal {

class WatchdogServiceHelperPeer;

}  // namespace internal

class WatchdogServiceHelperInterface : virtual public android::RefBase {
public:
    virtual bool isServiceConnected() = 0;
    virtual ndk::ScopedAStatus registerService(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service) = 0;
    virtual ndk::ScopedAStatus unregisterService(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service) = 0;
    virtual void handleBinderDeath(void* cookie) = 0;

    // Helper methods for APIs in ICarWatchdogServiceForSystem.aidl.
    virtual ndk::ScopedAStatus checkIfAlive(
            const ndk::SpAIBinder& who, int32_t sessionId,
            aidl::android::automotive::watchdog::TimeoutLength timeout) const = 0;
    virtual ndk::ScopedAStatus prepareProcessTermination(const ndk::SpAIBinder& who) = 0;
    virtual ndk::ScopedAStatus getPackageInfosForUids(
            const std::vector<int32_t>& uids, const std::vector<std::string>& vendorPackagePrefixes,
            std::vector<aidl::android::automotive::watchdog::internal::PackageInfo>* packageInfos)
            const = 0;
    virtual ndk::ScopedAStatus resetResourceOveruseStats(
            const std::vector<std::string>& packageNames) const = 0;
    virtual ndk::ScopedAStatus onLatestResourceStats(
            const std::vector<aidl::android::automotive::watchdog::internal::ResourceStats>&
                    resourceStats) const = 0;
    virtual ndk::ScopedAStatus requestAidlVhalPid() const = 0;
    virtual ndk::ScopedAStatus requestTodayIoUsageStats() const = 0;

protected:
    virtual android::base::Result<void> init(
            const android::sp<WatchdogProcessServiceInterface>& watchdogProcessService) = 0;
    virtual void terminate() = 0;

private:
    friend class ServiceManager;
};

// WatchdogServiceHelper implements the helper functions for the outbound API requests to
// the CarWatchdogService. This class doesn't handle the inbound APIs requests from
// CarWatchdogService except the registration APIs.
class WatchdogServiceHelper final : public WatchdogServiceHelperInterface {
public:
    WatchdogServiceHelper();

    bool isServiceConnected() {
        std::shared_lock readLock(mRWMutex);
        return mService != nullptr;
    }
    ndk::ScopedAStatus registerService(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service) override;
    ndk::ScopedAStatus unregisterService(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service) override;
    void handleBinderDeath(void* cookie) override;

    // Helper methods for ICarWatchdogServiceForSystem.aidl.
    ndk::ScopedAStatus checkIfAlive(
            const ndk::SpAIBinder& who, int32_t sessionId,
            aidl::android::automotive::watchdog::TimeoutLength timeout) const override;
    ndk::ScopedAStatus prepareProcessTermination(const ndk::SpAIBinder& who) override;
    ndk::ScopedAStatus getPackageInfosForUids(
            const std::vector<int32_t>& uids, const std::vector<std::string>& vendorPackagePrefixes,
            std::vector<aidl::android::automotive::watchdog::internal::PackageInfo>* packageInfos)
            const override;
    ndk::ScopedAStatus resetResourceOveruseStats(
            const std::vector<std::string>& packageNames) const override;
    ndk::ScopedAStatus onLatestResourceStats(
            const std::vector<aidl::android::automotive::watchdog::internal::ResourceStats>&
                    resourceStats) const override;
    ndk::ScopedAStatus requestAidlVhalPid() const override;
    ndk::ScopedAStatus requestTodayIoUsageStats() const override;

protected:
    android::base::Result<void> init(
            const android::sp<WatchdogProcessServiceInterface>& watchdogProcessService);
    void terminate();

private:
    void unregisterServiceLocked(bool doUnregisterFromProcessService);

    android::sp<WatchdogProcessServiceInterface> mWatchdogProcessService;
    ndk::ScopedAIBinder_DeathRecipient mWatchdogServiceDeathRecipient;
    android::sp<AIBinderDeathRegistrationWrapperInterface> mDeathRegistrationWrapper;

    mutable std::shared_mutex mRWMutex;
    std::shared_ptr<aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>
            mService GUARDED_BY(mRWMutex);

    friend class ServiceManager;

    // For unit tests.
    friend class internal::WatchdogServiceHelperPeer;
    FRIEND_TEST(WatchdogServiceHelperTest, TestInit);
    FRIEND_TEST(WatchdogServiceHelperTest,
                TestErrorOnInitWithErrorFromWatchdogProcessServiceRegistration);
    FRIEND_TEST(WatchdogServiceHelperTest, TestErrorOnInitWithNullWatchdogProcessServiceInstance);
    FRIEND_TEST(WatchdogServiceHelperTest, TestTerminate);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_SRC_WATCHDOGSERVICEHELPER_H_
