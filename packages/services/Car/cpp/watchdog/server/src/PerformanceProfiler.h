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

#ifndef CPP_WATCHDOG_SERVER_SRC_PERFORMANCEPROFILER_H_
#define CPP_WATCHDOG_SERVER_SRC_PERFORMANCEPROFILER_H_

#include "ProcDiskStatsCollector.h"
#include "ProcStatCollector.h"
#include "UidStatsCollector.h"
#include "WatchdogPerfService.h"

#include <android-base/chrono_utils.h>
#include <android-base/result.h>
#include <android/util/ProtoOutputStream.h>
#include <cutils/multiuser.h>
#include <gtest/gtest_prod.h>
#include <utils/Errors.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>
#include <utils/SystemClock.h>

#include <ctime>
#include <string>
#include <unordered_set>
#include <variant>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

// Number of periodic collection records to cache in memory.
constexpr int32_t kDefaultPeriodicCollectionBufferSize = 180;
constexpr const char kEmptyCollectionMessage[] = "No collection recorded\n";

// Forward declaration for testing use only.
namespace internal {

class PerformanceProfilerPeer;

}  // namespace internal

// Below classes, structs and enums should be used only by the implementation and unit tests.
enum ProcStatType {
    IO_BLOCKED_TASKS_COUNT = 0,
    MAJOR_FAULTS,
    CPU_TIME,
    PROC_STAT_TYPES,
};

// UserPackageStats represents the user package performance stats.
class UserPackageStats {
public:
    struct IoStatsView {
        int64_t bytes[UID_STATES] = {0};
        int64_t fsync[UID_STATES] = {0};

        int64_t totalBytes() const {
            return std::numeric_limits<int64_t>::max() - bytes[UidState::FOREGROUND] >
                            bytes[UidState::BACKGROUND]
                    ? bytes[UidState::FOREGROUND] + bytes[UidState::BACKGROUND]
                    : std::numeric_limits<int64_t>::max();
        }
    };
    struct ProcSingleStatsView {
        uint64_t value = 0;
        struct ProcessValue {
            std::string comm = "";
            uint64_t value = 0;
        };
        std::vector<ProcessValue> topNProcesses = {};
    };
    struct ProcCpuStatsView {
        int64_t cpuTimeMillis = 0;
        int64_t cpuCycles = 0;
        struct ProcessCpuValue {
            int32_t pid = -1;
            std::string comm = "";
            int64_t cpuTimeMillis = 0;
            int64_t cpuCycles = 0;
        };
        std::vector<ProcessCpuValue> topNProcesses = {};
    };

    UserPackageStats(MetricType metricType, const UidStats& uidStats);
    UserPackageStats(ProcStatType procStatType, const UidStats& uidStats, int topNProcessCount);

    // Class must be DefaultInsertable for std::vector<T>::resize to work
    UserPackageStats() : uid(0), genericPackageName("") {}
    // For unit test case only
    UserPackageStats(
            uid_t uid, std::string genericPackageName,
            std::variant<std::monostate, IoStatsView, ProcSingleStatsView, ProcCpuStatsView>
                    statsView) :
          uid(uid),
          genericPackageName(std::move(genericPackageName)),
          statsView(std::move(statsView)) {}

    // Returns the primary value of the current StatsView. If the variant has value
    // |std::monostate|, returns 0.
    //
    // This value should be used to sort the StatsViews.
    uint64_t getValue() const;
    std::string toString(MetricType metricsType, const int64_t totalIoStats[][UID_STATES]) const;
    std::string toString(int64_t totalValue) const;

    uid_t uid;
    std::string genericPackageName;
    std::variant<std::monostate, IoStatsView, ProcSingleStatsView, ProcCpuStatsView> statsView;

private:
    void cacheTopNProcessSingleStats(
            ProcStatType procStatType, const UidStats& uidStats, int topNProcessCount,
            std::vector<UserPackageStats::ProcSingleStatsView::ProcessValue>* topNProcesses);
    void cacheTopNProcessCpuStats(
            const UidStats& uidStats, int topNProcessCount,
            std::vector<UserPackageStats::ProcCpuStatsView::ProcessCpuValue>* topNProcesses);
};

/**
 * User package summary performance stats collected from the `/proc/uid_io/stats`,
 * `/proc/[pid]/stat`, `/proc/[pid]/task/[tid]/stat`, and /proc/[pid]/status` files.
 */
struct UserPackageSummaryStats {
    std::vector<UserPackageStats> topNCpuTimes = {};
    std::vector<UserPackageStats> topNIoReads = {};
    std::vector<UserPackageStats> topNIoWrites = {};
    std::vector<UserPackageStats> topNIoBlocked = {};
    std::vector<UserPackageStats> topNMajorFaults = {};
    int64_t totalIoStats[METRIC_TYPES][UID_STATES] = {{0}};
    std::unordered_map<uid_t, uint64_t> taskCountByUid = {};
    int64_t totalCpuTimeMillis = 0;
    uint64_t totalCpuCycles = 0;
    uint64_t totalMajorFaults = 0;
    // Percentage of increase/decrease in the major page faults since last collection.
    double majorFaultsPercentChange = 0.0;
    std::string toString() const;
};

// TODO(b/268402964): Calculate the total CPU cycles using the per-UID BPF tool.
// System performance stats collected from the `/proc/stats` file.
struct SystemSummaryStats {
    int64_t cpuIoWaitTimeMillis = 0;
    int64_t cpuIdleTimeMillis = 0;
    int64_t totalCpuTimeMillis = 0;
    uint64_t totalCpuCycles = 0;
    uint64_t contextSwitchesCount = 0;
    uint32_t ioBlockedProcessCount = 0;
    uint32_t totalProcessCount = 0;
    std::string toString() const;
};

// Performance record collected during a sampling/collection period.
struct PerfStatsRecord {
    time_point_millis collectionTimeMillis;
    SystemSummaryStats systemSummaryStats;
    UserPackageSummaryStats userPackageSummaryStats;
    std::string toString() const;
};

// Group of performance records collected for a collection event.
struct CollectionInfo {
    size_t maxCacheSize = 0;               // Maximum cache size for the collection.
    std::vector<PerfStatsRecord> records;  // Cache of collected performance records.
    std::string toString() const;
};

// Group of performance records collected for a user switch collection event.
struct UserSwitchCollectionInfo : CollectionInfo {
    userid_t from = 0;
    userid_t to = 0;
};

// PerformanceProfiler implements the I/O performance data collection module.
class PerformanceProfiler final : public DataProcessorInterface {
public:
    PerformanceProfiler() :
          kGetElapsedTimeSinceBootMillisFunc(elapsedRealtime),
          mTopNStatsPerCategory(0),
          mTopNStatsPerSubcategory(0),
          mMaxUserSwitchEvents(0),
          mSystemEventDataCacheDurationSec(0),
          mBoottimeCollection({}),
          mPeriodicCollection({}),
          mUserSwitchCollections({}),
          mWakeUpCollection({}),
          mCustomCollection({}),
          mLastMajorFaults(0),
          mDoSendResourceUsageStats(false) {}

    ~PerformanceProfiler() { terminate(); }

    std::string name() const override { return "PerformanceProfiler"; }

    // Implements DataProcessorInterface.
    android::base::Result<void> onSystemStartup() override;

    void onCarWatchdogServiceRegistered() override;

    android::base::Result<void> onBoottimeCollection(
            time_point_millis time,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector,
            aidl::android::automotive::watchdog::internal::ResourceStats* resourceStats) override;

    android::base::Result<void> onWakeUpCollection(
            time_point_millis time,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector) override;

    android::base::Result<void> onPeriodicCollection(
            time_point_millis time, SystemState systemState,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector,
            aidl::android::automotive::watchdog::internal::ResourceStats* resourceStats) override;

    android::base::Result<void> onUserSwitchCollection(
            time_point_millis time, userid_t from, userid_t to,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector) override;

    android::base::Result<void> onCustomCollection(
            time_point_millis time, SystemState systemState,
            const std::unordered_set<std::string>& filterPackages,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector,
            aidl::android::automotive::watchdog::internal::ResourceStats* resourceStats) override;

    android::base::Result<void> onPeriodicMonitor(
            [[maybe_unused]] time_t time,
            [[maybe_unused]] const android::wp<ProcDiskStatsCollectorInterface>&
                    procDiskStatsCollector,
            [[maybe_unused]] const std::function<void()>& alertHandler) override {
        // No monitoring done here as this DataProcessor only collects I/O performance records.
        return {};
    }

    android::base::Result<void> onDump(int fd) const override;

    android::base::Result<void> onDumpProto(
            const CollectionIntervals& collectionIntervals,
            android::util::ProtoOutputStream& outProto) const override;

    android::base::Result<void> onCustomCollectionDump(int fd) override;

protected:
    android::base::Result<void> init();

    // Clears in-memory cache.
    void terminate();

private:
    // Processes the collected data.
    android::base::Result<void> processLocked(
            time_point_millis time, SystemState systemState,
            const std::unordered_set<std::string>& filterPackages,
            const android::sp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::sp<ProcStatCollectorInterface>& procStatCollector,
            CollectionInfo* collectionInfo,
            aidl::android::automotive::watchdog::internal::ResourceStats* resourceStats);

    // Processes per-UID performance data.
    void processUidStatsLocked(
            bool isGarageModeActive, int64_t totalCpuTimeMillis,
            const std::unordered_set<std::string>& filterPackages,
            const android::sp<UidStatsCollectorInterface>& uidStatsCollector,
            std::vector<aidl::android::automotive::watchdog::internal::UidResourceUsageStats>*
                    uidResourceUsageStats,
            UserPackageSummaryStats* userPackageSummaryStats);

    // Processes system performance data from the `/proc/stats` file.
    void processProcStatLocked(const android::sp<ProcStatCollectorInterface>& procStatCollector,
                               SystemSummaryStats* systemSummaryStats) const;

    // Dump the user switch collection
    android::base::Result<void> onUserSwitchCollectionDump(int fd) const;

    void clearExpiredSystemEventCollections(time_point_millis time);

    void dumpStatsRecordsProto(const CollectionInfo& collection,
                               android::util::ProtoOutputStream& outProto) const;

    void dumpPackageCpuStatsProto(const std::vector<UserPackageStats>& userPackageStats,
                                  android::util::ProtoOutputStream& outProto) const;

    void dumpPackageStorageIoStatsProto(const std::vector<UserPackageStats>& userPackageStats,
                                        const uint64_t storageStatsFieldId,
                                        android::util::ProtoOutputStream& outProto) const;

    void dumpPackageTaskStateStatsProto(const std::vector<UserPackageStats>& userPackageStats,
                                        const std::unordered_map<uid_t, uint64_t>& taskCountByUid,
                                        android::util::ProtoOutputStream& outProto) const;

    void dumpPackageMajorPageFaultsProto(const std::vector<UserPackageStats>& userPackageStats,
                                         android::util::ProtoOutputStream& outProto) const;

    std::function<int64_t()> kGetElapsedTimeSinceBootMillisFunc;

    // Top N per-UID stats per category.
    int mTopNStatsPerCategory;

    // Top N per-process stats per subcategory.
    int mTopNStatsPerSubcategory;

    // Max amount of user switch events cached in |mUserSwitchCollections|.
    size_t mMaxUserSwitchEvents;

    // Amount of seconds before a system event's cache is cleared.
    std::chrono::seconds mSystemEventDataCacheDurationSec;

    // Makes sure only one collection is running at any given time.
    mutable Mutex mMutex;

    // Info for the boot-time collection event. The cache is persisted until system shutdown/reboot
    // or a wake-up collection occurs.
    CollectionInfo mBoottimeCollection GUARDED_BY(mMutex);

    // Info for the periodic collection event. The cache size is limited by
    // |ro.carwatchdog.periodic_collection_buffer_size|.
    CollectionInfo mPeriodicCollection GUARDED_BY(mMutex);

    // Cache for user switch collection events. Events are cached from oldest to newest.
    std::vector<UserSwitchCollectionInfo> mUserSwitchCollections GUARDED_BY(mMutex);

    // Info for the wake-up collection event. Only the latest wake-up collection is cached.
    CollectionInfo mWakeUpCollection GUARDED_BY(mMutex);

    // Info for the custom collection event. The info is cleared at the end of every custom
    // collection.
    CollectionInfo mCustomCollection GUARDED_BY(mMutex);

    // Major faults delta from last collection. Useful when calculating the percentage change in
    // major faults since last collection.
    uint64_t mLastMajorFaults GUARDED_BY(mMutex);

    // Enables the sending of resource usage stats to CarService.
    bool mDoSendResourceUsageStats GUARDED_BY(mMutex);

    friend class WatchdogPerfService;

    // For unit tests.
    friend class internal::PerformanceProfilerPeer;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_SRC_PERFORMANCEPROFILER_H_
