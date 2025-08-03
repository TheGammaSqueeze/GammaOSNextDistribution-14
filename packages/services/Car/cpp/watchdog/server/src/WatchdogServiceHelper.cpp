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

#define LOG_TAG "carwatchdogd"
#define DEBUG false  // STOPSHIP if true.

#include "WatchdogServiceHelper.h"

#include "ServiceManager.h"

#include <android/binder_ibinder.h>

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::TimeoutLength;
using ::aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem;
using ::aidl::android::automotive::watchdog::internal::PackageInfo;
using ::aidl::android::automotive::watchdog::internal::PackageIoOveruseStats;
using ::aidl::android::automotive::watchdog::internal::ResourceStats;
using ::aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats;
using ::android::sp;
using ::android::wp;
using ::android::base::Error;
using ::android::base::Result;
using ::ndk::ScopedAIBinder_DeathRecipient;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

namespace {

ScopedAStatus fromExceptionCodeWithMessage(binder_exception_t exceptionCode,
                                           const std::string& message) {
    ALOGW("%s.", message.c_str());
    return ScopedAStatus::fromExceptionCodeWithMessage(exceptionCode, message.c_str());
}

void onBinderDied(void* cookie) {
    const auto& thiz = ServiceManager::getInstance()->getWatchdogServiceHelper();
    if (thiz == nullptr) {
        return;
    }
    thiz->handleBinderDeath(cookie);
}

}  // namespace

WatchdogServiceHelper::WatchdogServiceHelper() :
      mWatchdogProcessService(nullptr),
      mWatchdogServiceDeathRecipient(
              ScopedAIBinder_DeathRecipient(AIBinder_DeathRecipient_new(onBinderDied))),
      mDeathRegistrationWrapper(sp<AIBinderDeathRegistrationWrapper>::make()),
      mService(nullptr) {}

Result<void> WatchdogServiceHelper::init(
        const sp<WatchdogProcessServiceInterface>& watchdogProcessService) {
    if (watchdogProcessService == nullptr) {
        return Error() << "Must provide a non-null watchdog process service instance";
    }
    mWatchdogProcessService = watchdogProcessService;
    return {};
}

ScopedAStatus WatchdogServiceHelper::registerService(
        const std::shared_ptr<ICarWatchdogServiceForSystem>& service) {
    if (service == nullptr) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT, "Must provide non-null service");
    }
    const auto binder = service->asBinder();
    AIBinder* aiBinder = binder.get();
    {
        std::unique_lock writeLock(mRWMutex);
        if (mWatchdogProcessService == nullptr) {
            return fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                "Must initialize watchdog service helper before "
                                                "registering car watchdog service");
        }
        if (mService != nullptr && mService->asBinder() == binder) {
            return ScopedAStatus::ok();
        }
        unregisterServiceLocked(/*doUnregisterFromProcessService=*/true);
        if (auto status = mWatchdogProcessService
                                  ->registerCarWatchdogService(binder,
                                                               sp<WatchdogServiceHelperInterface>::
                                                                       fromExisting(this));
            !status.isOk()) {
            return status;
        }
        mService = service;
    }
    auto ret =
            mDeathRegistrationWrapper->linkToDeath(aiBinder, mWatchdogServiceDeathRecipient.get(),
                                                   static_cast<void*>(aiBinder));
    if (!ret.isOk()) {
        std::unique_lock writeLock(mRWMutex);
        if (mService != nullptr && mService->asBinder() == binder) {
            mWatchdogProcessService->unregisterCarWatchdogService(binder);
            mService.reset();
        }
        return fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                            "Failed to register car watchdog service as it is "
                                            "dead");
    }
    if (DEBUG) {
        ALOGW("CarWatchdogService is registered");
    }
    return ScopedAStatus::ok();
}

ScopedAStatus WatchdogServiceHelper::unregisterService(
        const std::shared_ptr<ICarWatchdogServiceForSystem>& service) {
    if (service == nullptr) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT, "Must provide non-null service");
    }
    std::unique_lock writeLock(mRWMutex);
    if (const auto binder = service->asBinder();
        mService == nullptr || binder != mService->asBinder()) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                            "Failed to unregister car watchdog service as it is "
                                            "not registered");
    }
    unregisterServiceLocked(/*doUnregisterFromProcessService=*/true);

    if (DEBUG) {
        ALOGW("CarWatchdogService is unregistered");
    }
    return ScopedAStatus::ok();
}

void WatchdogServiceHelper::handleBinderDeath(void* cookie) {
    std::unique_lock writeLock(mRWMutex);
    if (mService == nullptr) {
        return;
    }
    const auto curBinder = mService->asBinder();
    if (reinterpret_cast<uintptr_t>(curBinder.get()) != reinterpret_cast<uintptr_t>(cookie)) {
        return;
    }
    ALOGW("Car watchdog service had died.");
    mService.reset();
    mWatchdogProcessService->unregisterCarWatchdogService(curBinder);
}

void WatchdogServiceHelper::terminate() {
    std::unique_lock writeLock(mRWMutex);
    unregisterServiceLocked(/*doUnregisterFromProcessService=*/true);
    mWatchdogProcessService.clear();
}

ScopedAStatus WatchdogServiceHelper::checkIfAlive(const SpAIBinder& who, int32_t sessionId,
                                                  TimeoutLength timeout) const {
    std::shared_ptr<ICarWatchdogServiceForSystem> service;
    if (std::shared_lock readLock(mRWMutex); mService == nullptr || mService->asBinder() != who) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                            "Dropping checkIfAlive request as the given car "
                                            "watchdog service binder isn't registered");
    } else {
        service = mService;
    }
    return service
            ->checkIfAlive(sessionId,
                           static_cast<
                                   aidl::android::automotive::watchdog::internal::TimeoutLength>(
                                   timeout));
}

ScopedAStatus WatchdogServiceHelper::prepareProcessTermination(const SpAIBinder& who) {
    std::shared_ptr<ICarWatchdogServiceForSystem> service;
    if (std::shared_lock readLock(mRWMutex); mService == nullptr || mService->asBinder() != who) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                            "Dropping prepareProcessTermination request as the "
                                            "given car watchdog service binder isn't registered");
    } else {
        service = mService;
    }
    auto status = service->prepareProcessTermination();
    if (status.isOk()) {
        std::unique_lock writeLock(mRWMutex);
        /*
         * prepareTermination callback is called when CarWatchdogService isn't responding, which
         * indicates the CarWatchdogService is stuck, terminating, or restarting.
         *
         * When CarWatchdogService is terminating, it will issue an unregisterService call.
         * If the unregisterService is executed after the previous |readLock| is released and the
         * before current |writeLock| is acquired, the |mService| will be updated to null. Then it
         * won't match |service|.
         *
         * When CarWatchdogService is restarting, it will issue an registerService call. When the
         * registerService is executed between after the previous |readLock| is released and before
         * the current |writeLock| is acquired, the |mService| will be overwritten. This will lead
         * to unregistering the new CarWatchdogService.
         *
         * To avoid this race condition, check mService before proceeding with unregistering the
         * CarWatchdogService.
         */
        if (mService == service) {
            // WatchdogProcessService unregisters the watchdog service before calling
            // prepareProcessTermination. So skip unregistering from WatchdogProcessService.
            unregisterServiceLocked(/*doUnregisterFromProcessService=*/false);
        }
    }
    return status;
}

void WatchdogServiceHelper::unregisterServiceLocked(bool doUnregisterFromProcessService) {
    if (mService == nullptr) return;
    const auto binder = mService->asBinder();
    AIBinder* aiBinder = binder.get();
    mDeathRegistrationWrapper->unlinkToDeath(aiBinder, mWatchdogServiceDeathRecipient.get(),
                                             static_cast<void*>(aiBinder));
    if (doUnregisterFromProcessService) {
        mWatchdogProcessService->unregisterCarWatchdogService(binder);
    }
    mService.reset();
}

ScopedAStatus WatchdogServiceHelper::getPackageInfosForUids(
        const std::vector<int32_t>& uids, const std::vector<std::string>& vendorPackagePrefixes,
        std::vector<PackageInfo>* packageInfos) const {
    std::shared_ptr<ICarWatchdogServiceForSystem> service;
    if (std::shared_lock readLock(mRWMutex); mService == nullptr) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                            "Watchdog service is not initialized");
    } else {
        service = mService;
    }
    /*
     * The expected number of vendor package prefixes is in the order of 10s. Thus the overhead of
     * forwarding these in each get call is very low.
     */
    return service->getPackageInfosForUids(uids, vendorPackagePrefixes, packageInfos);
}

ScopedAStatus WatchdogServiceHelper::resetResourceOveruseStats(
        const std::vector<std::string>& packageNames) const {
    std::shared_ptr<ICarWatchdogServiceForSystem> service;
    if (std::shared_lock readLock(mRWMutex); mService == nullptr) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                            "Watchdog service is not initialized");
    } else {
        service = mService;
    }
    return service->resetResourceOveruseStats(packageNames);
}

ScopedAStatus WatchdogServiceHelper::requestTodayIoUsageStats() const {
    std::shared_ptr<ICarWatchdogServiceForSystem> service;
    if (std::shared_lock readLock(mRWMutex); mService == nullptr) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                            "Watchdog service is not initialized");
    } else {
        service = mService;
    }
    return service->requestTodayIoUsageStats();
}

ScopedAStatus WatchdogServiceHelper::onLatestResourceStats(
        const std::vector<ResourceStats>& resourceStats) const {
    std::shared_ptr<ICarWatchdogServiceForSystem> service;
    if (std::shared_lock readLock(mRWMutex); mService == nullptr) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                            "Watchdog service is not initialized");
    } else {
        service = mService;
    }
    return service->onLatestResourceStats(resourceStats);
}

ScopedAStatus WatchdogServiceHelper::requestAidlVhalPid() const {
    std::shared_ptr<ICarWatchdogServiceForSystem> service;
    if (std::shared_lock readLock(mRWMutex); mService == nullptr) {
        return fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                            "Watchdog service is not initialized");
    } else {
        service = mService;
    }
    return service->requestAidlVhalPid();
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
