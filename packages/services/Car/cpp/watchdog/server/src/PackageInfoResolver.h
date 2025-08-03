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

#ifndef CPP_WATCHDOG_SERVER_SRC_PACKAGEINFORESOLVER_H_
#define CPP_WATCHDOG_SERVER_SRC_PACKAGEINFORESOLVER_H_

#include "LooperWrapper.h"
#include "WatchdogServiceHelper.h"

#include <aidl/android/automotive/watchdog/internal/ApplicationCategoryType.h>
#include <aidl/android/automotive/watchdog/internal/PackageInfo.h>
#include <android-base/result.h>
#include <gtest/gtest_prod.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>
#include <utils/StrongPointer.h>

#include <pwd.h>

#include <atomic>
#include <functional>
#include <queue>
#include <shared_mutex>
#include <thread>  // NOLINT(build/c++11)
#include <unordered_map>
#include <unordered_set>

namespace android {
namespace automotive {
namespace watchdog {

class ServiceManager;
class IoOveruseMonitor;
class IoOveruseConfigs;

// Forward declaration for testing use only.
namespace internal {

class PackageInfoResolverPeer;

}  // namespace internal

class PackageInfoResolverInterface : virtual public android::RefBase {
public:
    virtual void asyncFetchPackageNamesForUids(
            const std::vector<uid_t>& uids,
            const std::function<void(std::unordered_map<uid_t, std::string>)>& callback) = 0;
    virtual std::unordered_map<uid_t, aidl::android::automotive::watchdog::internal::PackageInfo>
    getPackageInfosForUids(const std::vector<uid_t>& uids) = 0;

protected:
    virtual android::base::Result<void> initWatchdogServiceHelper(
            const android::sp<WatchdogServiceHelperInterface>& watchdogServiceHelper) = 0;
    virtual void setPackageConfigurations(
            const std::unordered_set<std::string>& vendorPackagePrefixes,
            const std::unordered_map<
                    std::string,
                    aidl::android::automotive::watchdog::internal::ApplicationCategoryType>&
                    packagesToAppCategories) = 0;

private:
    friend class ServiceManager;
    friend class IoOveruseMonitor;
    friend class IoOveruseConfigs;
};

/*
 * PackageInfoResolver maintains a cache of the UID to PackageInfo mapping in the car watchdog
 * daemon. PackageInfoResolver is a singleton and must be accessed only via the public static
 * methods.
 *
 * TODO(b/158131194): Extend IUidObserver in WatchdogBinderMediator and use the onUidGone API to
 *  keep the local mapping cache up-to-date.
 */
class PackageInfoResolver final : public PackageInfoResolverInterface {
public:
    ~PackageInfoResolver() {
        std::unique_lock writeLock(mRWMutex);
        mWatchdogServiceHelper.clear();
        mUidToPackageInfoMapping.clear();
    }

    /*
     * Initializes the PackageInfoResolver's singleton instance only on the first call. Main thread
     * should make the first call as this method doesn't offer multi-threading protection.
     */
    static android::sp<PackageInfoResolverInterface> getInstance();

    android::base::Result<void> initWatchdogServiceHelper(
            const android::sp<WatchdogServiceHelperInterface>& watchdogServiceHelper);

    static void terminate();

    /*
     * Resolves the given |uids| and returns a mapping of uids to package names via callback. If the
     * mapping doesn't exist in the local cache, queries the car watchdog service for application
     * uids and getpwuid for native uids. Logs any error observed during this process.
     */
    void asyncFetchPackageNamesForUids(
            const std::vector<uid_t>& uids,
            const std::function<void(std::unordered_map<uid_t, std::string>)>& callback);

    /*
     * Similar to asyncFetchPackageNamesForUids, resolves the given |uids| and returns a mapping of
     * uids to package infos.
     */
    std::unordered_map<uid_t, aidl::android::automotive::watchdog::internal::PackageInfo>
    getPackageInfosForUids(const std::vector<uid_t>& uids);

    virtual void setPackageConfigurations(
            const std::unordered_set<std::string>& vendorPackagePrefixes,
            const std::unordered_map<
                    std::string,
                    aidl::android::automotive::watchdog::internal::ApplicationCategoryType>&
                    packagesToAppCategories);

    class MessageHandlerImpl final : public MessageHandler {
    public:
        explicit MessageHandlerImpl(PackageInfoResolver* service) : kService(service) {}

        void handleMessage(const Message& message) override;

    private:
        PackageInfoResolver* kService;
    };

private:
    // PackageInfoResolver instance can only be obtained via |getInstance|.
    PackageInfoResolver() :
          mWatchdogServiceHelper(nullptr),
          mUidToPackageInfoMapping({}),
          mVendorPackagePrefixes({}),
          mShouldTerminateLooper(false),
          mHandlerLooper(android::sp<LooperWrapper>::make()),
          mMessageHandler(android::sp<MessageHandlerImpl>::make(this)) {
        startLooper();
    }

    void updatePackageInfos(const std::vector<uid_t>& uids);

    void resolvePackageName();

    void startLooper();

    // Singleton instance.
    static android::sp<PackageInfoResolver> sInstance;

    mutable std::shared_mutex mRWMutex;

    /*
     * ServiceManager::startServices initializes PackageInfoResolver. However, between the
     * |getInstance| and |initWatchdogServiceHelper| calls it initializes few other services, which
     * may call |asyncFetchPackageNamesForUids| or |getPackageInfosForUids| simultaneously on a
     * separate thread. In order to avoid a race condition between |initWatchdogServiceHelper| and
     * |getPackage*ForUids| calls, mWatchdogServiceHelper is guarded by a read-write lock.
     */
    android::sp<WatchdogServiceHelperInterface> mWatchdogServiceHelper GUARDED_BY(mRWMutex);
    std::unordered_map<uid_t, aidl::android::automotive::watchdog::internal::PackageInfo>
            mUidToPackageInfoMapping GUARDED_BY(mRWMutex);
    std::vector<std::string> mVendorPackagePrefixes GUARDED_BY(mRWMutex);
    std::unordered_map<std::string,
                       aidl::android::automotive::watchdog::internal::ApplicationCategoryType>
            mPackagesToAppCategories GUARDED_BY(mRWMutex);
    std::atomic<bool> mShouldTerminateLooper;
    std::thread mHandlerThread;
    android::sp<LooperWrapper> mHandlerLooper;
    android::sp<MessageHandlerImpl> mMessageHandler;
    std::vector<std::pair<std::vector<uid_t>,
                          std::function<void(std::unordered_map<uid_t, std::string>)>>>
            mPendingPackageNames GUARDED_BY(mRWMutex);

    // Required to instantiate the class in |getInstance|.
    friend class android::sp<PackageInfoResolver>;

    // For unit tests.
    static std::function<struct passwd*(uid_t)> sGetpwuidHandler;

    friend class internal::PackageInfoResolverPeer;
    FRIEND_TEST(PackageInfoResolverTest, TestResolvesNativeUid);
    FRIEND_TEST(PackageInfoResolverTest, TestResolvesApplicationUidFromWatchdogServiceHelper);
    FRIEND_TEST(PackageInfoResolverTest, TestResolvesApplicationUidFromLocalCache);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_SRC_PACKAGEINFORESOLVER_H_
