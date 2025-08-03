/*
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

#ifndef CPP_WATCHDOG_SERVER_SRC_IOOVERUSEMONITOR_H_
#define CPP_WATCHDOG_SERVER_SRC_IOOVERUSEMONITOR_H_

#include "AIBinderDeathRegistrationWrapper.h"
#include "IoOveruseConfigs.h"
#include "PackageInfoResolver.h"
#include "ProcStatCollector.h"
#include "UidStatsCollector.h"
#include "WatchdogPerfService.h"

#include <aidl/android/automotive/watchdog/IResourceOveruseListener.h>
#include <aidl/android/automotive/watchdog/PerStateBytes.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <aidl/android/automotive/watchdog/internal/PackageInfo.h>
#include <aidl/android/automotive/watchdog/internal/PackageIoOveruseStats.h>
#include <aidl/android/automotive/watchdog/internal/ResourceOveruseConfiguration.h>
#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <android/binder_auto_utils.h>
#include <android/util/ProtoOutputStream.h>
#include <cutils/multiuser.h>
#include <utils/Mutex.h>

#include <time.h>

#include <ostream>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

// Number of periodically monitored stats to cache in memory.
constexpr int32_t kDefaultPeriodicMonitorBufferSize = 360;
// Dumpsys flags.
constexpr const char* kResetResourceOveruseStatsFlag = "--reset_resource_overuse_stats";

// Forward declaration for testing use only.
namespace internal {

class IoOveruseMonitorPeer;

}  // namespace internal

// Used only in tests.
std::tuple<int64_t, int64_t> calculateStartAndDuration(const time_point_millis& currentTime);

/**
 * IoOveruseMonitorInterface interface defines the methods that the I/O overuse monitoring module
 * should implement.
 */
class IoOveruseMonitorInterface : virtual public DataProcessorInterface {
public:
    // Returns whether or not the monitor is initialized.
    virtual bool isInitialized() const = 0;

    // Dumps the help text.
    virtual bool dumpHelpText(int fd) const = 0;

    // Below API is from internal/ICarWatchdog.aidl. Please refer to the AIDL for description.
    virtual android::base::Result<void> updateResourceOveruseConfigurations(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>&
                    configs) = 0;
    virtual android::base::Result<void> getResourceOveruseConfigurations(
            std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>*
                    configs) const = 0;
    virtual android::base::Result<void> onTodayIoUsageStatsFetched(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats>&
                    userPackageIoUsageStats) = 0;

    // Below methods support APIs from ICarWatchdog.aidl. Please refer to the AIDL for description.
    virtual android::base::Result<void> addIoOveruseListener(
            const std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>&
                    listener) = 0;

    virtual android::base::Result<void> removeIoOveruseListener(
            const std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>&
                    listener) = 0;

    virtual void handleBinderDeath(void* cookie) = 0;

    virtual android::base::Result<void> getIoOveruseStats(
            aidl::android::automotive::watchdog::IoOveruseStats* ioOveruseStats) const = 0;

    virtual android::base::Result<void> resetIoOveruseStats(
            const std::vector<std::string>& packageNames) = 0;

    // Removes stats for the given user from the internal cache.
    virtual void removeStatsForUser(userid_t userId) = 0;
};

class IoOveruseMonitor final : public IoOveruseMonitorInterface {
public:
    explicit IoOveruseMonitor(
            const android::sp<WatchdogServiceHelperInterface>& watchdogServiceHelper);

    ~IoOveruseMonitor() { terminate(); }

    bool isInitialized() const override {
        std::shared_lock readLock(mRwMutex);
        return isInitializedLocked();
    }

    void onCarWatchdogServiceRegistered() override;

    // Below methods implement DataProcessorInterface.
    std::string name() const override { return "IoOveruseMonitor"; }
    friend std::ostream& operator<<(std::ostream& os, const IoOveruseMonitor& monitor);

    android::base::Result<void> onSystemStartup() {
        // No tracking of boot-time and wake-up events in I/O overuse monitoring.
        return {};
    }

    android::base::Result<void> onBoottimeCollection(
            [[maybe_unused]] time_point_millis time,
            [[maybe_unused]] const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            [[maybe_unused]] const android::wp<ProcStatCollectorInterface>& procStatCollector,
            [[maybe_unused]] aidl::android::automotive::watchdog::internal::ResourceStats*
                    resourceStats) override {
        // No I/O overuse monitoring during boot-time.
        return {};
    }

    android::base::Result<void> onWakeUpCollection(
            [[maybe_unused]] time_point_millis time,
            [[maybe_unused]] const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            [[maybe_unused]] const android::wp<ProcStatCollectorInterface>& procStatCollector)
            override {
        // No I/O overuse monitoring during wake up.
        return {};
    }

    android::base::Result<void> onUserSwitchCollection(
            [[maybe_unused]] time_point_millis time, [[maybe_unused]] userid_t from,
            [[maybe_unused]] userid_t to,
            [[maybe_unused]] const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            [[maybe_unused]] const android::wp<ProcStatCollectorInterface>& procStatCollector)
            override {
        // No I/O overuse monitoring during user switch.
        return {};
    }

    android::base::Result<void> onPeriodicCollection(
            time_point_millis time, SystemState systemState,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector,
            aidl::android::automotive::watchdog::internal::ResourceStats* resourceStats) override;

    android::base::Result<void> onCustomCollection(
            time_point_millis time, SystemState systemState,
            const std::unordered_set<std::string>& filterPackages,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector,
            aidl::android::automotive::watchdog::internal::ResourceStats* resourceStats) override;

    android::base::Result<void> onPeriodicMonitor(
            time_t time, const android::wp<ProcDiskStatsCollectorInterface>& procDiskStatsCollector,
            const std::function<void()>& alertHandler) override;

    android::base::Result<void> onDump(int fd) const override;
    android::base::Result<void> onDumpProto(
            const CollectionIntervals& collectionIntervals,
            android::util::ProtoOutputStream& outProto) const override;

    bool dumpHelpText(int fd) const override;

    android::base::Result<void> onCustomCollectionDump([[maybe_unused]] int fd) override {
        // No special processing for custom collection. Thus no custom collection dump.
        return {};
    }

    // Below methods implement AIDL interfaces.
    android::base::Result<void> updateResourceOveruseConfigurations(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>&
                    configs) override;

    android::base::Result<void> getResourceOveruseConfigurations(
            std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>*
                    configs) const override;

    android::base::Result<void> onTodayIoUsageStatsFetched(
            const std::vector<
                    aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats>&
                    userPackageIoUsageStats) override;

    android::base::Result<void> addIoOveruseListener(
            const std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>&
                    listener) override;

    android::base::Result<void> removeIoOveruseListener(
            const std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>&
                    listener) override;

    void handleBinderDeath(void* cookie) override;

    android::base::Result<void> getIoOveruseStats(
            aidl::android::automotive::watchdog::IoOveruseStats* ioOveruseStats) const override;

    android::base::Result<void> resetIoOveruseStats(
            const std::vector<std::string>& packageName) override;

    void removeStatsForUser(userid_t userId) override;

protected:
    android::base::Result<void> init();

    void terminate();

private:
    struct WrittenBytesSnapshot {
        double pollDurationInSecs;
        uint64_t bytesInKib;
    };

    struct UserPackageIoUsage {
        UserPackageIoUsage(
                const aidl::android::automotive::watchdog::internal::PackageInfo& packageInfo,
                const UidIoStats& uidIoStats, const bool isGarageModeActive);
        aidl::android::automotive::watchdog::internal::PackageInfo packageInfo = {};
        aidl::android::automotive::watchdog::PerStateBytes writtenBytes = {};
        aidl::android::automotive::watchdog::PerStateBytes forgivenWriteBytes = {};
        int totalOveruses = 0;
        bool isPackageWarned = false;
        uint64_t lastSyncedWrittenBytes = 0;

        UserPackageIoUsage& operator+=(const UserPackageIoUsage& r);
        UserPackageIoUsage& operator+=(
                const aidl::android::automotive::watchdog::internal::IoUsageStats& r);

        const std::string id() const;
        void resetStats();
    };

private:
    bool isInitializedLocked() const { return mIoOveruseConfigs != nullptr; }

    void requestTodayIoUsageStatsLocked();

    void notifyNativePackagesLocked(
            const std::unordered_map<uid_t, aidl::android::automotive::watchdog::IoOveruseStats>&
                    statsByUid);

    using ListenersByUidMap = std::unordered_map<
            uid_t, std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>>;
    using Processor = std::function<void(ListenersByUidMap&, ListenersByUidMap::const_iterator)>;
    bool findListenerAndProcessLocked(uintptr_t binderPtrId, const Processor& processor);

    /**
     * Writes in-memory configs to disk asynchronously if configs are not written after latest
     * update.
     */
    void writeConfigsToDiskAsyncLocked();

    // Local PackageInfoResolverInterface instance. Useful to mock in tests.
    sp<PackageInfoResolverInterface> mPackageInfoResolver;

    // Minimum written bytes to sync the stats with the Watchdog service.
    double mMinSyncWrittenBytes;

    // Helper to communicate with the CarWatchdogService.
    android::sp<WatchdogServiceHelperInterface> mWatchdogServiceHelper;

    // AIBinder death registration wrapper. Useful for mocking in tests.
    android::sp<AIBinderDeathRegistrationWrapperInterface> mDeathRegistrationWrapper;

    // Makes sure only one collection is running at any given time.
    mutable std::shared_mutex mRwMutex;

    // Indicates whether or not today's I/O usage stats, that were collected during previous boot,
    // are read from CarService because CarService persists these stats in database across reboot.
    bool mDidReadTodayPrevBootStats GUARDED_BY(mRwMutex);

    // Summary of configs available for all the components and system-wide overuse alert thresholds.
    sp<IoOveruseConfigsInterface> mIoOveruseConfigs GUARDED_BY(mRwMutex);

    /**
     * Delta of system-wide written kib across all disks from the last |mPeriodicMonitorBufferSize|
     * polls along with the polling duration.
     */
    std::vector<WrittenBytesSnapshot> mSystemWideWrittenBytes GUARDED_BY(mRwMutex);
    size_t mPeriodicMonitorBufferSize GUARDED_BY(mRwMutex);
    time_t mLastSystemWideIoMonitorTime GUARDED_BY(mRwMutex);

    // Cache of I/O usage stats from previous boot that happened today. Key is a unique ID with
    // the format `packageName:userId`.
    std::unordered_map<std::string, aidl::android::automotive::watchdog::internal::IoUsageStats>
            mPrevBootIoUsageStatsById GUARDED_BY(mRwMutex);

    // Cache of per user package I/O usage. Key is a unique ID with the format `packageName:userId`.
    std::unordered_map<std::string, UserPackageIoUsage> mUserPackageDailyIoUsageById
            GUARDED_BY(mRwMutex);
    double mIoOveruseWarnPercentage GUARDED_BY(mRwMutex);
    time_point_millis mLastUserPackageIoMonitorTime GUARDED_BY(mRwMutex);
    std::vector<aidl::android::automotive::watchdog::internal::PackageIoOveruseStats>
            mLatestIoOveruseStats;

    ListenersByUidMap mOveruseListenersByUid GUARDED_BY(mRwMutex);
    ndk::ScopedAIBinder_DeathRecipient mBinderDeathRecipient;

    friend class WatchdogPerfService;

    // For unit tests.
    friend class internal::IoOveruseMonitorPeer;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_SRC_IOOVERUSEMONITOR_H_
