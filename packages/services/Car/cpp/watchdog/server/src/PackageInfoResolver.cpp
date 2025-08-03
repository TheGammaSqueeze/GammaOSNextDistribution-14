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

#include "PackageInfoResolver.h"

#include <aidl/android/automotive/watchdog/internal/ApplicationCategoryType.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <aidl/android/automotive/watchdog/internal/UidType.h>
#include <android-base/strings.h>
#include <cutils/android_filesystem_config.h>
#include <processgroup/sched_policy.h>

#include <inttypes.h>
#include <pthread.h>

#include <future>  // NOLINT(build/c++11)
#include <iterator>
#include <string_view>

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::internal::ApplicationCategoryType;
using ::aidl::android::automotive::watchdog::internal::ComponentType;
using ::aidl::android::automotive::watchdog::internal::PackageInfo;
using ::aidl::android::automotive::watchdog::internal::UidType;
using ::android::sp;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StartsWith;

using GetpwuidFunction = std::function<struct passwd*(uid_t)>;
using PackageToAppCategoryMap = std::unordered_map<std::string, ApplicationCategoryType>;

namespace {

constexpr const char* kSharedPackagePrefix = "shared:";
constexpr const char* kServiceName = "PkgInfoResolver";

const int32_t MSG_RESOLVE_PACKAGE_NAME = 0;

ComponentType getComponentTypeForNativeUid(uid_t uid, std::string_view packageName,
                                           const std::vector<std::string>& vendorPackagePrefixes) {
    for (const auto& prefix : vendorPackagePrefixes) {
        if (StartsWith(packageName, prefix)) {
            return ComponentType::VENDOR;
        }
    }
    if ((uid >= AID_OEM_RESERVED_START && uid <= AID_OEM_RESERVED_END) ||
        (uid >= AID_OEM_RESERVED_2_START && uid <= AID_OEM_RESERVED_2_END) ||
        (uid >= AID_ODM_RESERVED_START && uid <= AID_ODM_RESERVED_END)) {
        return ComponentType::VENDOR;
    }
    /**
     * There are no third party native services. Thus all non-vendor services are considered system
     * services.
     */
    return ComponentType::SYSTEM;
}

Result<PackageInfo> getPackageInfoForNativeUid(
        uid_t uid, const std::vector<std::string>& vendorPackagePrefixes,
        const GetpwuidFunction& getpwuidHandler) {
    PackageInfo packageInfo;
    passwd* usrpwd = getpwuidHandler(uid);
    if (!usrpwd) {
        return Error() << "Failed to fetch package name";
    }
    const char* packageName = usrpwd->pw_name;
    packageInfo.packageIdentifier.name = packageName;
    packageInfo.packageIdentifier.uid = uid;
    packageInfo.uidType = UidType::NATIVE;
    packageInfo.componentType =
            getComponentTypeForNativeUid(uid, packageName, vendorPackagePrefixes);
    packageInfo.appCategoryType = ApplicationCategoryType::OTHERS;
    packageInfo.sharedUidPackages = {};

    return packageInfo;
}

}  // namespace

sp<PackageInfoResolver> PackageInfoResolver::sInstance = nullptr;
GetpwuidFunction PackageInfoResolver::sGetpwuidHandler = &getpwuid;

sp<PackageInfoResolverInterface> PackageInfoResolver::getInstance() {
    if (sInstance == nullptr) {
        sInstance = sp<PackageInfoResolver>::make();
    }
    return sInstance;
}

void PackageInfoResolver::terminate() {
    sInstance->mShouldTerminateLooper.store(true);
    sInstance->mHandlerLooper->removeMessages(sInstance->mMessageHandler);
    sInstance->mHandlerLooper->wake();
    if (sInstance->mHandlerThread.joinable()) {
        sInstance->mHandlerThread.join();
    }
    sInstance.clear();
}

Result<void> PackageInfoResolver::initWatchdogServiceHelper(
        const sp<WatchdogServiceHelperInterface>& watchdogServiceHelper) {
    std::unique_lock writeLock(mRWMutex);
    if (watchdogServiceHelper == nullptr) {
        return Error() << "Must provide a non-null watchdog service helper instance";
    }
    if (mWatchdogServiceHelper != nullptr) {
        return Error() << "Duplicate initialization";
    }
    mWatchdogServiceHelper = watchdogServiceHelper;
    return {};
}

void PackageInfoResolver::setPackageConfigurations(
        const std::unordered_set<std::string>& vendorPackagePrefixes,
        const PackageToAppCategoryMap& packagesToAppCategories) {
    std::unique_lock writeLock(mRWMutex);
    mVendorPackagePrefixes.clear();
    std::copy(vendorPackagePrefixes.begin(), vendorPackagePrefixes.end(),
              std::back_inserter(mVendorPackagePrefixes));
    mPackagesToAppCategories = packagesToAppCategories;
    // Clear the package info cache as the package configurations have changed.
    mUidToPackageInfoMapping.clear();
}

void PackageInfoResolver::updatePackageInfos(const std::vector<uid_t>& uids) {
    std::unique_lock writeLock(mRWMutex);
    std::vector<int32_t> missingUids;
    for (const uid_t uid : uids) {
        if (mUidToPackageInfoMapping.find(uid) != mUidToPackageInfoMapping.end()) {
            continue;
        }
        if (uid >= AID_APP_START) {
            missingUids.emplace_back(static_cast<int32_t>(uid));
            continue;
        }
        auto result = getPackageInfoForNativeUid(uid, mVendorPackagePrefixes,
                                                 PackageInfoResolver::sGetpwuidHandler);
        if (!result.ok()) {
            missingUids.emplace_back(static_cast<int32_t>(uid));
            continue;
        }
        mUidToPackageInfoMapping[uid] = *result;
        if (StartsWith(result->packageIdentifier.name, kSharedPackagePrefix)) {
            // When the UID is shared, poll car watchdog service to fetch the shared packages info.
            missingUids.emplace_back(static_cast<int32_t>(uid));
        }
    }

    /*
     * There is delay between creating package manager instance and initializing watchdog service
     * helper. Thus check the watchdog service helper instance before proceeding further.
     */
    if (missingUids.empty() || mWatchdogServiceHelper == nullptr ||
        !mWatchdogServiceHelper->isServiceConnected()) {
        return;
    }

    std::vector<PackageInfo> packageInfos;
    auto status =
            mWatchdogServiceHelper->getPackageInfosForUids(missingUids, mVendorPackagePrefixes,
                                                           &packageInfos);
    if (!status.isOk()) {
        ALOGE("Failed to fetch package infos from car watchdog service: %s", status.getMessage());
        return;
    }
    for (auto& packageInfo : packageInfos) {
        const auto& id = packageInfo.packageIdentifier;
        if (id.name.empty()) {
            continue;
        }
        if (packageInfo.uidType == UidType::APPLICATION) {
            if (const auto it = mPackagesToAppCategories.find(id.name);
                it != mPackagesToAppCategories.end()) {
                packageInfo.appCategoryType = it->second;
            } else if (!packageInfo.sharedUidPackages.empty()) {
                /* The recommendation for the OEMs is to define the application category mapping
                 * by the shared package names. However, this a fallback to catch if any mapping is
                 * defined by the individual package name.
                 */
                for (const auto& packageName : packageInfo.sharedUidPackages) {
                    if (const auto it = mPackagesToAppCategories.find(packageName);
                        it != mPackagesToAppCategories.end()) {
                        packageInfo.appCategoryType = it->second;
                        break;
                    }
                }
            }
        }
        mUidToPackageInfoMapping[id.uid] = packageInfo;
    }
}

void PackageInfoResolver::asyncFetchPackageNamesForUids(
        const std::vector<uid_t>& uids,
        const std::function<void(std::unordered_map<uid_t, std::string>)>& callback) {
    std::shared_lock writeLock(mRWMutex);
    mPendingPackageNames.push_back(std::make_pair(uids, callback));

    mHandlerLooper->removeMessages(mMessageHandler, MSG_RESOLVE_PACKAGE_NAME);
    mHandlerLooper->sendMessage(mMessageHandler, Message(MSG_RESOLVE_PACKAGE_NAME));
}

std::unordered_map<uid_t, PackageInfo> PackageInfoResolver::getPackageInfosForUids(
        const std::vector<uid_t>& uids) {
    std::unordered_map<uid_t, PackageInfo> uidToPackageInfoMapping;
    if (uids.empty()) {
        return uidToPackageInfoMapping;
    }
    updatePackageInfos(uids);
    {
        std::shared_lock readLock(mRWMutex);
        for (const auto& uid : uids) {
            if (mUidToPackageInfoMapping.find(uid) != mUidToPackageInfoMapping.end()) {
                uidToPackageInfoMapping[uid] = mUidToPackageInfoMapping.at(uid);
            }
        }
    }
    return uidToPackageInfoMapping;
}

void PackageInfoResolver::resolvePackageName() {
    std::vector<std::pair<std::vector<uid_t>,
                          std::function<void(std::unordered_map<uid_t, std::string>)>>>
            uidsToCallbacks;
    {
        std::shared_lock writeLock(mRWMutex);
        swap(mPendingPackageNames, uidsToCallbacks);
    }

    std::vector<uid_t> allUids;
    for (const auto& uidsToCallback : uidsToCallbacks) {
        auto uids = uidsToCallback.first;
        allUids.insert(allUids.end(), uids.begin(), uids.end());
    }
    updatePackageInfos(allUids);

    for (const auto& uidsToCallback : uidsToCallbacks) {
        auto uids = uidsToCallback.first;
        auto callback = uidsToCallback.second;
        std::unordered_map<uid_t, std::string> uidToPackageNameMapping;
        if (uids.empty()) {
            callback(uidToPackageNameMapping);
            continue;
        }
        for (const auto& uid : uids) {
            std::shared_lock readLock(mRWMutex);
            if (mUidToPackageInfoMapping.find(uid) != mUidToPackageInfoMapping.end()) {
                uidToPackageNameMapping[uid] =
                        mUidToPackageInfoMapping.at(uid).packageIdentifier.name;
            }
        }
        callback(uidToPackageNameMapping);
    }
}

void PackageInfoResolver::startLooper() {
    auto promise = std::promise<void>();
    auto checkLooperSet = promise.get_future();
    mHandlerThread = std::thread([&]() {
        mHandlerLooper->setLooper(Looper::prepare(/* opts= */ 0));
        if (set_sched_policy(0, SP_BACKGROUND) != 0) {
            ALOGW("Failed to set background scheduling priority to %s thread", kServiceName);
        }
        if (int result = pthread_setname_np(pthread_self(), kServiceName); result != 0) {
            ALOGE("Failed to set %s thread name: %d", kServiceName, result);
        }
        mShouldTerminateLooper.store(false);
        promise.set_value();

        // Loop while PackageInfoResolver is active.
        // This looper is used to handle package name resolution in asyncFetchPackageNamesForUids.
        while (!mShouldTerminateLooper) {
            mHandlerLooper->pollAll(/* timeoutMillis= */ -1);
        }
    });

    // Wait until the looper is initialized to ensure no messages get posted
    // before the looper initialization. Otherwise, messages may be sent to the
    // looper before it is initialized.
    if (checkLooperSet.wait_for(std::chrono::seconds(1)) != std::future_status::ready) {
        ALOGW("Failed to start looper for %s", kServiceName);
    }
}

void PackageInfoResolver::MessageHandlerImpl::handleMessage(const Message& message) {
    switch (message.what) {
        case MSG_RESOLVE_PACKAGE_NAME:
            kService->resolvePackageName();
            break;
        default:
            ALOGW("Unknown message: %d", message.what);
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
