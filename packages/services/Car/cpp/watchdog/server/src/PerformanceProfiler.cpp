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

#include "PerformanceProfiler.h"

#include <WatchdogProperties.sysprop.h>
#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android/util/ProtoOutputStream.h>
#include <log/log.h>

#include <inttypes.h>

#include <iomanip>
#include <limits>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include <carwatchdog_daemon_dump.proto.h>
#include <performance_stats.proto.h>

namespace android {
namespace automotive {
namespace watchdog {
using ::aidl::android::automotive::watchdog::PerStateBytes;
using ::aidl::android::automotive::watchdog::internal::PackageIdentifier;
using ::aidl::android::automotive::watchdog::internal::ProcessCpuUsageStats;
using ::aidl::android::automotive::watchdog::internal::ResourceStats;
using ::aidl::android::automotive::watchdog::internal::ResourceUsageStats;
using ::aidl::android::automotive::watchdog::internal::SystemSummaryUsageStats;
using ::aidl::android::automotive::watchdog::internal::UidIoUsageStats;
using ::aidl::android::automotive::watchdog::internal::UidResourceUsageStats;
using ::android::wp;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::android::util::ProtoOutputStream;

namespace {

constexpr int32_t kDefaultTopNStatsPerCategory = 10;
constexpr int32_t kDefaultTopNStatsPerSubcategory = 5;
constexpr int32_t kDefaultMaxUserSwitchEvents = 5;
constexpr std::chrono::seconds kSystemEventDataCacheDurationSec = 1h;
constexpr const char kBootTimeCollectionTitle[] = "%s\nBoot-time performance report:\n%s\n";
constexpr const char kPeriodicCollectionTitle[] = "%s\nLast N minutes performance report:\n%s\n";
constexpr const char kUserSwitchCollectionTitle[] =
        "%s\nUser-switch events performance report:\n%s\n";
constexpr const char kUserSwitchCollectionSubtitle[] = "Number of user switch events: %zu\n";
constexpr const char kWakeUpCollectionTitle[] = "%s\nWake-up performance report:\n%s\n";
constexpr const char kCustomCollectionTitle[] = "%s\nCustom performance data report:\n%s\n";
constexpr const char kUserSwitchEventTitle[] = "\nEvent %zu: From: %d To: %d\n%s\n";
constexpr const char kCollectionTitle[] =
        "Collection duration: %.f seconds\nNumber of collections: %zu\n";
constexpr const char kRecordTitle[] = "\nCollection %zu: <%s>\n%s\n%s";
constexpr const char kCpuTimeTitle[] = "\nTop N CPU Times:\n%s\n";
constexpr const char kCpuTimeHeader[] = "Android User ID, Package Name, CPU Time (ms), Percentage "
                                        "of total CPU time, CPU Cycles\n\tCommand, CPU Time (ms), "
                                        "Percentage of UID's CPU Time, CPU Cycles\n";
constexpr const char kIoReadsTitle[] = "\nTop N Storage I/O Reads:\n%s\n";
constexpr const char kIoWritesTitle[] = "\nTop N Storage I/O Writes:\n%s\n";
constexpr const char kIoStatsHeader[] =
        "Android User ID, Package Name, Foreground Bytes, Foreground Bytes %%, Foreground Fsync, "
        "Foreground Fsync %%, Background Bytes, Background Bytes %%, Background Fsync, "
        "Background Fsync %%\n";
constexpr const char kIoBlockedTitle[] = "\nTop N I/O waiting UIDs:\n%s\n";
constexpr const char kIoBlockedHeader[] =
        "Android User ID, Package Name, Number of owned tasks waiting for I/O, Percentage of owned "
        "tasks waiting for I/O\n\tCommand, Number of I/O waiting tasks, Percentage of UID's tasks "
        "waiting for I/O\n";
constexpr const char kMajorPageFaultsTitle[] = "\nTop N major page faults:\n%s\n";
constexpr const char kMajorFaultsHeader[] =
        "Android User ID, Package Name, Number of major page faults, Percentage of total major "
        "page faults\n\tCommand, Number of major page faults, Percentage of UID's major page "
        "faults\n";
constexpr const char kMajorFaultsSummary[] =
        "Number of major page faults since last collection: %" PRIu64 "\n"
        "Percentage of change in major page faults since last collection: %.2f%%\n";

double percentage(uint64_t numer, uint64_t denom) {
    return denom == 0 ? 0.0 : (static_cast<double>(numer) / static_cast<double>(denom)) * 100.0;
}

void addUidIoStats(const int64_t entry[][UID_STATES], int64_t total[][UID_STATES]) {
    const auto sum = [](int64_t lhs, int64_t rhs) -> int64_t {
        return std::numeric_limits<int64_t>::max() - lhs > rhs
                ? lhs + rhs
                : std::numeric_limits<int64_t>::max();
    };
    total[READ_BYTES][FOREGROUND] =
            sum(total[READ_BYTES][FOREGROUND], entry[READ_BYTES][FOREGROUND]);
    total[READ_BYTES][BACKGROUND] =
            sum(total[READ_BYTES][BACKGROUND], entry[READ_BYTES][BACKGROUND]);
    total[WRITE_BYTES][FOREGROUND] =
            sum(total[WRITE_BYTES][FOREGROUND], entry[WRITE_BYTES][FOREGROUND]);
    total[WRITE_BYTES][BACKGROUND] =
            sum(total[WRITE_BYTES][BACKGROUND], entry[WRITE_BYTES][BACKGROUND]);
    total[FSYNC_COUNT][FOREGROUND] =
            sum(total[FSYNC_COUNT][FOREGROUND], entry[FSYNC_COUNT][FOREGROUND]);
    total[FSYNC_COUNT][BACKGROUND] =
            sum(total[FSYNC_COUNT][BACKGROUND], entry[FSYNC_COUNT][BACKGROUND]);
    return;
}

bool cacheTopNStats(const UserPackageStats& curUserPackageStats,
                    std::vector<UserPackageStats>* topNStats) {
    uint64_t curValue = curUserPackageStats.getValue();
    if (curValue == 0) {
        return false;
    }
    for (auto it = topNStats->begin(); it != topNStats->end(); ++it) {
        if (curValue > it->getValue()) {
            topNStats->insert(it, curUserPackageStats);
            topNStats->pop_back();
            return true;
        }
    }
    return false;
}

Result<void> checkDataCollectors(const sp<UidStatsCollectorInterface>& uidStatsCollector,
                                 const sp<ProcStatCollectorInterface>& procStatCollector) {
    if (uidStatsCollector != nullptr && procStatCollector != nullptr) {
        return {};
    }
    std::string error;
    if (uidStatsCollector == nullptr) {
        error = "Per-UID stats collector must not be null";
    }
    if (procStatCollector == nullptr) {
        StringAppendF(&error, "%s%s", error.empty() ? "" : ", ",
                      "Proc stats collector must not be null");
    }
    return Error() << "Invalid data collectors: " << error;
}

// Calculates the milliseconds since the UID started.
int64_t calculateUidUptimeMillis(int64_t elapsedTimeSinceBootMillis,
                                 const UidProcStats& procStats) {
    int64_t earliestProcStartTimeMillis = std::numeric_limits<int64_t>::max();
    for (auto [_, processStats] : procStats.processStatsByPid) {
        if (processStats.startTimeMillis < earliestProcStartTimeMillis) {
            earliestProcStartTimeMillis = processStats.startTimeMillis;
        }
    }
    return elapsedTimeSinceBootMillis - earliestProcStartTimeMillis;
}

UidResourceUsageStats constructUidResourceUsageStats(
        PackageIdentifier packageIdentifier, int64_t uidUptimeMillis, int64_t totalCpuTimeMillis,
        bool isGarageModeActive, const UserPackageStats::ProcCpuStatsView& procCpuStatsView,
        const UserPackageStats::IoStatsView& ioReadsStatsView,
        const UserPackageStats::IoStatsView& ioWritesStatsView) {
    std::vector<ProcessCpuUsageStats> processCpuUsageStats;
    processCpuUsageStats.reserve(procCpuStatsView.topNProcesses.size());
    for (const auto& processCpuValue : procCpuStatsView.topNProcesses) {
        processCpuUsageStats.push_back({
                .pid = processCpuValue.pid,
                .name = processCpuValue.comm,
                .cpuTimeMillis = processCpuValue.cpuTimeMillis,
                .cpuCycles = processCpuValue.cpuCycles,
        });
    }

    UidIoUsageStats ioUsageStats = {};
    if (isGarageModeActive) {
        ioUsageStats.readBytes.garageModeBytes = ioReadsStatsView.totalBytes();
        ioUsageStats.writtenBytes.garageModeBytes = ioWritesStatsView.totalBytes();
    } else {
        ioUsageStats.readBytes.foregroundBytes = ioReadsStatsView.bytes[UidState::FOREGROUND];
        ioUsageStats.readBytes.backgroundBytes = ioReadsStatsView.bytes[UidState::BACKGROUND];
        ioUsageStats.writtenBytes.foregroundBytes = ioWritesStatsView.bytes[UidState::FOREGROUND];
        ioUsageStats.writtenBytes.backgroundBytes = ioWritesStatsView.bytes[UidState::BACKGROUND];
    }

    // clang-format off
    return UidResourceUsageStats{
            .packageIdentifier = std::move(packageIdentifier),
            .uidUptimeMillis = uidUptimeMillis,
            .cpuUsageStats = {
                    .cpuTimeMillis = procCpuStatsView.cpuTimeMillis,
                    .cpuCycles = procCpuStatsView.cpuCycles,
                    .cpuTimePercentage
                            = percentage(procCpuStatsView.cpuTimeMillis, totalCpuTimeMillis),
            },
            .processCpuUsageStats = std::move(processCpuUsageStats),
            .ioUsageStats = ioUsageStats,
    };
    // clang-format on
}

SystemSummaryUsageStats constructSystemSummaryUsageStats(
        bool isGarageModeActive, const SystemSummaryStats& systemStats,
        const UserPackageSummaryStats& userPackageStats) {
    const auto sum = [](int64_t lhs, int64_t rhs) -> int64_t {
        return std::numeric_limits<int64_t>::max() - lhs > rhs
                ? lhs + rhs
                : std::numeric_limits<int64_t>::max();
    };

    PerStateBytes totalIoReads = {};
    PerStateBytes totalIoWrites = {};
    if (isGarageModeActive) {
        totalIoReads.garageModeBytes =
                sum(userPackageStats.totalIoStats[MetricType::READ_BYTES][UidState::FOREGROUND],
                    userPackageStats.totalIoStats[MetricType::READ_BYTES][UidState::BACKGROUND]);
        totalIoWrites.garageModeBytes =
                sum(userPackageStats.totalIoStats[MetricType::WRITE_BYTES][UidState::FOREGROUND],
                    userPackageStats.totalIoStats[MetricType::WRITE_BYTES][UidState::BACKGROUND]);
    } else {
        totalIoReads.foregroundBytes =
                userPackageStats.totalIoStats[MetricType::READ_BYTES][UidState::FOREGROUND];
        totalIoReads.backgroundBytes =
                userPackageStats.totalIoStats[MetricType::READ_BYTES][UidState::BACKGROUND];
        totalIoWrites.foregroundBytes =
                userPackageStats.totalIoStats[MetricType::WRITE_BYTES][UidState::FOREGROUND];
        totalIoWrites.backgroundBytes =
                userPackageStats.totalIoStats[MetricType::WRITE_BYTES][UidState::BACKGROUND];
    }

    return SystemSummaryUsageStats{
            // Currently total CPU cycles derive from thread level CPU stats,
            // hence they don't include idle information.
            .cpuNonIdleCycles = static_cast<int64_t>(systemStats.totalCpuCycles),
            .cpuNonIdleTimeMillis = systemStats.totalCpuTimeMillis - systemStats.cpuIdleTimeMillis,
            .cpuIdleTimeMillis = systemStats.cpuIdleTimeMillis,
            .contextSwitchesCount = static_cast<int64_t>(systemStats.contextSwitchesCount),
            .ioBlockedProcessCount = static_cast<int32_t>(systemStats.ioBlockedProcessCount),
            .totalProcessCount = static_cast<int32_t>(systemStats.totalProcessCount),
            .totalMajorPageFaults = static_cast<int32_t>(userPackageStats.totalMajorFaults),
            .totalIoReads = totalIoReads,
            .totalIoWrites = totalIoWrites,
    };
}

}  // namespace

UserPackageStats::UserPackageStats(MetricType metricType, const UidStats& uidStats) {
    const UidIoStats& ioStats = uidStats.ioStats;
    uid = uidStats.uid();
    genericPackageName = uidStats.genericPackageName();
    statsView = UserPackageStats::
            IoStatsView{.bytes = {ioStats.metrics[metricType][UidState::FOREGROUND],
                                  ioStats.metrics[metricType][UidState::BACKGROUND]},
                        .fsync = {ioStats.metrics[MetricType::FSYNC_COUNT][UidState::FOREGROUND],
                                  ioStats.metrics[MetricType::FSYNC_COUNT][UidState::BACKGROUND]}};
}

UserPackageStats::UserPackageStats(ProcStatType procStatType, const UidStats& uidStats,
                                   int topNProcessCount) {
    uint64_t value = procStatType == CPU_TIME        ? uidStats.cpuTimeMillis
            : procStatType == IO_BLOCKED_TASKS_COUNT ? uidStats.procStats.ioBlockedTasksCount
                                                     : uidStats.procStats.totalMajorFaults;
    uid = uidStats.uid();
    genericPackageName = uidStats.genericPackageName();
    if (procStatType == CPU_TIME) {
        statsView = UserPackageStats::ProcCpuStatsView{.cpuTimeMillis = static_cast<int64_t>(value),
                                                       .cpuCycles = static_cast<int64_t>(
                                                               uidStats.procStats.cpuCycles)};
        auto& procCpuStatsView = std::get<UserPackageStats::ProcCpuStatsView>(statsView);
        procCpuStatsView.topNProcesses.resize(topNProcessCount);
        cacheTopNProcessCpuStats(uidStats, topNProcessCount, &procCpuStatsView.topNProcesses);
        return;
    }
    statsView = UserPackageStats::ProcSingleStatsView{.value = value};
    auto& procStatsView = std::get<UserPackageStats::ProcSingleStatsView>(statsView);
    procStatsView.topNProcesses.resize(topNProcessCount);
    cacheTopNProcessSingleStats(procStatType, uidStats, topNProcessCount,
                                &procStatsView.topNProcesses);
}

uint64_t UserPackageStats::getValue() const {
    return std::visit(
            [](auto&& arg) -> uint64_t {
                using T = std::decay_t<decltype(arg)>;
                if constexpr (std::is_same_v<T, UserPackageStats::IoStatsView>) {
                    return arg.totalBytes();
                }
                if constexpr (std::is_same_v<T, UserPackageStats::ProcSingleStatsView>) {
                    return arg.value;
                }
                if constexpr (std::is_same_v<T, UserPackageStats::ProcCpuStatsView>) {
                    return arg.cpuTimeMillis;
                }
                // Unknown stats view
                return 0;
            },
            statsView);
}

std::string UserPackageStats::toString(MetricType metricsType,
                                       const int64_t totalIoStats[][UID_STATES]) const {
    std::string buffer;
    StringAppendF(&buffer, "%" PRIu32 ", %s", multiuser_get_user_id(uid),
                  genericPackageName.c_str());
    const auto& ioStatsView = std::get<UserPackageStats::IoStatsView>(statsView);
    for (int i = 0; i < UID_STATES; ++i) {
        StringAppendF(&buffer, ", %" PRIi64 ", %.2f%%, %" PRIi64 ", %.2f%%", ioStatsView.bytes[i],
                      percentage(ioStatsView.bytes[i], totalIoStats[metricsType][i]),
                      ioStatsView.fsync[i],
                      percentage(ioStatsView.fsync[i], totalIoStats[FSYNC_COUNT][i]));
    }
    StringAppendF(&buffer, "\n");
    return buffer;
}

std::string UserPackageStats::toString(int64_t totalValue) const {
    std::string buffer;
    auto procCpuStatsView = std::get_if<UserPackageStats::ProcCpuStatsView>(&statsView);
    if (procCpuStatsView != nullptr) {
        StringAppendF(&buffer, "%" PRIu32 ", %s, %" PRIu64 ", %.2f%%, %" PRIu64 "\n",
                      multiuser_get_user_id(uid), genericPackageName.c_str(),
                      procCpuStatsView->cpuTimeMillis,
                      percentage(procCpuStatsView->cpuTimeMillis, totalValue),
                      procCpuStatsView->cpuCycles);
        for (const auto& processCpuValue : procCpuStatsView->topNProcesses) {
            StringAppendF(&buffer, "\t%s, %" PRIu64 ", %.2f%%, %" PRIu64 "\n",
                          processCpuValue.comm.c_str(), processCpuValue.cpuTimeMillis,
                          percentage(processCpuValue.cpuTimeMillis,
                                     procCpuStatsView->cpuTimeMillis),
                          processCpuValue.cpuCycles);
        }
        return buffer;
    }
    const auto& procStatsView = std::get<UserPackageStats::ProcSingleStatsView>(statsView);
    StringAppendF(&buffer, "%" PRIu32 ", %s, %" PRIu64 ", %.2f%%\n", multiuser_get_user_id(uid),
                  genericPackageName.c_str(), procStatsView.value,
                  percentage(procStatsView.value, totalValue));
    for (const auto& processValue : procStatsView.topNProcesses) {
        StringAppendF(&buffer, "\t%s, %" PRIu64 ", %.2f%%\n", processValue.comm.c_str(),
                      processValue.value, percentage(processValue.value, procStatsView.value));
    }
    return buffer;
}

void UserPackageStats::cacheTopNProcessSingleStats(
        ProcStatType procStatType, const UidStats& uidStats, int topNProcessCount,
        std::vector<UserPackageStats::ProcSingleStatsView::ProcessValue>* topNProcesses) {
    int cachedProcessCount = 0;
    for (const auto& [_, processStats] : uidStats.procStats.processStatsByPid) {
        uint64_t value = procStatType == IO_BLOCKED_TASKS_COUNT ? processStats.ioBlockedTasksCount
                                                                : processStats.totalMajorFaults;
        if (value == 0) {
            continue;
        }
        for (auto it = topNProcesses->begin(); it != topNProcesses->end(); ++it) {
            if (value > it->value) {
                topNProcesses->insert(it,
                                      UserPackageStats::ProcSingleStatsView::ProcessValue{
                                              .comm = processStats.comm,
                                              .value = value,
                                      });
                topNProcesses->pop_back();
                ++cachedProcessCount;
                break;
            }
        }
    }
    if (cachedProcessCount < topNProcessCount) {
        topNProcesses->erase(topNProcesses->begin() + cachedProcessCount, topNProcesses->end());
    }
}

void UserPackageStats::cacheTopNProcessCpuStats(
        const UidStats& uidStats, int topNProcessCount,
        std::vector<UserPackageStats::ProcCpuStatsView::ProcessCpuValue>* topNProcesses) {
    int cachedProcessCount = 0;
    for (const auto& [pid, processStats] : uidStats.procStats.processStatsByPid) {
        int64_t cpuTimeMillis = processStats.cpuTimeMillis;
        if (cpuTimeMillis == 0) {
            continue;
        }
        for (auto it = topNProcesses->begin(); it != topNProcesses->end(); ++it) {
            if (cpuTimeMillis > it->cpuTimeMillis) {
                topNProcesses->insert(it,
                                      UserPackageStats::ProcCpuStatsView::ProcessCpuValue{
                                              .pid = pid,
                                              .comm = processStats.comm,
                                              .cpuTimeMillis = cpuTimeMillis,
                                              .cpuCycles = static_cast<int64_t>(
                                                      processStats.totalCpuCycles),
                                      });
                topNProcesses->pop_back();
                ++cachedProcessCount;
                break;
            }
        }
    }
    if (cachedProcessCount < topNProcessCount) {
        topNProcesses->erase(topNProcesses->begin() + cachedProcessCount, topNProcesses->end());
    }
}

std::string UserPackageSummaryStats::toString() const {
    std::string buffer;
    if (!topNCpuTimes.empty()) {
        StringAppendF(&buffer, kCpuTimeTitle, std::string(16, '-').c_str());
        StringAppendF(&buffer, kCpuTimeHeader);
        for (const auto& stats : topNCpuTimes) {
            StringAppendF(&buffer, "%s", stats.toString(totalCpuTimeMillis).c_str());
        }
    }
    if (!topNIoReads.empty()) {
        StringAppendF(&buffer, kIoReadsTitle, std::string(24, '-').c_str());
        StringAppendF(&buffer, kIoStatsHeader);
        for (const auto& stats : topNIoReads) {
            StringAppendF(&buffer, "%s",
                          stats.toString(MetricType::READ_BYTES, totalIoStats).c_str());
        }
    }
    if (!topNIoWrites.empty()) {
        StringAppendF(&buffer, kIoWritesTitle, std::string(25, '-').c_str());
        StringAppendF(&buffer, kIoStatsHeader);
        for (const auto& stats : topNIoWrites) {
            StringAppendF(&buffer, "%s",
                          stats.toString(MetricType::WRITE_BYTES, totalIoStats).c_str());
        }
    }
    if (!topNIoBlocked.empty()) {
        StringAppendF(&buffer, kIoBlockedTitle, std::string(23, '-').c_str());
        StringAppendF(&buffer, kIoBlockedHeader);
        for (const auto& stats : topNIoBlocked) {
            const auto it = taskCountByUid.find(stats.uid);
            if (it == taskCountByUid.end()) {
                continue;
            }
            StringAppendF(&buffer, "%s", stats.toString(it->second).c_str());
        }
    }
    if (!topNMajorFaults.empty()) {
        StringAppendF(&buffer, kMajorPageFaultsTitle, std::string(24, '-').c_str());
        StringAppendF(&buffer, kMajorFaultsHeader);
        for (const auto& stats : topNMajorFaults) {
            StringAppendF(&buffer, "%s", stats.toString(totalMajorFaults).c_str());
        }
        StringAppendF(&buffer, kMajorFaultsSummary, totalMajorFaults, majorFaultsPercentChange);
    }
    return buffer;
}

std::string SystemSummaryStats::toString() const {
    std::string buffer;
    StringAppendF(&buffer, "Total CPU time (ms): %" PRIu64 "\n", totalCpuTimeMillis);
    StringAppendF(&buffer, "Total CPU cycles: %" PRIu64 "\n", totalCpuCycles);
    StringAppendF(&buffer, "Total idle CPU time (ms)/percent: %" PRIu64 " / %.2f%%\n",
                  cpuIdleTimeMillis, percentage(cpuIdleTimeMillis, totalCpuTimeMillis));
    StringAppendF(&buffer, "CPU I/O wait time (ms)/percent: %" PRIu64 " / %.2f%%\n",
                  cpuIoWaitTimeMillis, percentage(cpuIoWaitTimeMillis, totalCpuTimeMillis));
    StringAppendF(&buffer, "Number of context switches: %" PRIu64 "\n", contextSwitchesCount);
    StringAppendF(&buffer, "Number of I/O blocked processes/percent: %" PRIu32 " / %.2f%%\n",
                  ioBlockedProcessCount, percentage(ioBlockedProcessCount, totalProcessCount));
    return buffer;
}

std::string PerfStatsRecord::toString() const {
    std::string buffer;
    StringAppendF(&buffer, "%s%s", systemSummaryStats.toString().c_str(),
                  userPackageSummaryStats.toString().c_str());
    return buffer;
}

std::string CollectionInfo::toString() const {
    if (records.empty()) {
        return kEmptyCollectionMessage;
    }
    std::string buffer;
    double duration =
            difftime(std::chrono::system_clock::to_time_t(records.back().collectionTimeMillis),
                     std::chrono::system_clock::to_time_t(records.front().collectionTimeMillis));
    StringAppendF(&buffer, kCollectionTitle, duration, records.size());
    for (size_t i = 0; i < records.size(); ++i) {
        const auto& record = records[i];
        std::stringstream timestamp;
        auto timeInSeconds = std::chrono::system_clock::to_time_t(record.collectionTimeMillis);
        timestamp << std::put_time(std::localtime(&timeInSeconds), "%c %Z");
        StringAppendF(&buffer, kRecordTitle, i, timestamp.str().c_str(),
                      std::string(45, '=').c_str(), record.toString().c_str());
    }
    return buffer;
}

Result<void> PerformanceProfiler::init() {
    Mutex::Autolock lock(mMutex);
    if (mTopNStatsPerCategory != 0 || mTopNStatsPerSubcategory != 0) {
        return Error() << "Cannot initialize " << name() << " more than once";
    }
    mTopNStatsPerCategory = static_cast<int>(
            sysprop::topNStatsPerCategory().value_or(kDefaultTopNStatsPerCategory));
    mTopNStatsPerSubcategory = static_cast<int>(
            sysprop::topNStatsPerSubcategory().value_or(kDefaultTopNStatsPerSubcategory));
    mMaxUserSwitchEvents = static_cast<size_t>(
            sysprop::maxUserSwitchEvents().value_or(kDefaultMaxUserSwitchEvents));
    mSystemEventDataCacheDurationSec =
            std::chrono::seconds(sysprop::systemEventDataCacheDuration().value_or(
                    kSystemEventDataCacheDurationSec.count()));
    size_t periodicCollectionBufferSize = static_cast<size_t>(
            sysprop::periodicCollectionBufferSize().value_or(kDefaultPeriodicCollectionBufferSize));
    mBoottimeCollection = {
            .maxCacheSize = std::numeric_limits<std::size_t>::max(),
            .records = {},
    };
    mPeriodicCollection = {
            .maxCacheSize = periodicCollectionBufferSize,
            .records = {},
    };
    mWakeUpCollection = {
            .maxCacheSize = std::numeric_limits<std::size_t>::max(),
            .records = {},
    };
    mCustomCollection = {
            .maxCacheSize = std::numeric_limits<std::size_t>::max(),
            .records = {},
    };
    return {};
}

void PerformanceProfiler::terminate() {
    Mutex::Autolock lock(mMutex);

    ALOGW("Terminating %s", name().c_str());

    mBoottimeCollection.records.clear();
    mBoottimeCollection = {};

    mPeriodicCollection.records.clear();
    mPeriodicCollection = {};

    mUserSwitchCollections.clear();

    mCustomCollection.records.clear();
    mCustomCollection = {};
}

Result<void> PerformanceProfiler::onDump(int fd) const {
    Mutex::Autolock lock(mMutex);
    if (!WriteStringToFd(StringPrintf(kBootTimeCollectionTitle, std::string(75, '-').c_str(),
                                      std::string(33, '=').c_str()),
                         fd) ||
        !WriteStringToFd(mBoottimeCollection.toString(), fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to dump the boot-time collection report.";
    }
    if (!WriteStringToFd(StringPrintf(kWakeUpCollectionTitle, std::string(75, '-').c_str(),
                                      std::string(27, '=').c_str()),
                         fd) ||
        !WriteStringToFd(mWakeUpCollection.toString(), fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to dump the boot-time collection report.";
    }
    if (const auto& result = onUserSwitchCollectionDump(fd); !result.ok()) {
        return result.error();
    }
    if (!WriteStringToFd(StringPrintf(kPeriodicCollectionTitle, std::string(75, '-').c_str(),
                                      std::string(38, '=').c_str()),
                         fd) ||
        !WriteStringToFd(mPeriodicCollection.toString(), fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to dump the periodic collection report.";
    }
    return {};
}

Result<void> PerformanceProfiler::onDumpProto(
        const CollectionIntervals& collectionIntervals, ProtoOutputStream& outProto) const {
    Mutex::Autolock lock(mMutex);

    uint64_t performanceStatsToken = outProto.start(PerformanceProfilerDump::PERFORMANCE_STATS);

    uint64_t bootTimeStatsToken = outProto.start(PerformanceStats::BOOT_TIME_STATS);
    outProto.write(StatsCollection::COLLECTION_INTERVAL_MILLIS,
                   collectionIntervals.mBoottimeIntervalMillis.count());
    dumpStatsRecordsProto(mBoottimeCollection, outProto);
    outProto.end(bootTimeStatsToken);

    uint64_t wakeUpStatsToken = outProto.start(PerformanceStats::WAKE_UP_STATS);
    outProto.write(StatsCollection::COLLECTION_INTERVAL_MILLIS,
                   collectionIntervals.mWakeUpIntervalMillis.count());
    dumpStatsRecordsProto(mWakeUpCollection, outProto);
    outProto.end(wakeUpStatsToken);

    for (const auto& userSwitchCollection : mUserSwitchCollections) {
        uint64_t userSwitchStatsToken = outProto.start(PerformanceStats::USER_SWITCH_STATS);
        outProto.write(UserSwitchStatsCollection::TO_USER_ID,
                       static_cast<int>(userSwitchCollection.to));
        outProto.write(UserSwitchStatsCollection::FROM_USER_ID,
                       static_cast<int>(userSwitchCollection.from));
        uint64_t userSwitchCollectionToken =
                outProto.start(UserSwitchStatsCollection::USER_SWITCH_COLLECTION);
        outProto.write(StatsCollection::COLLECTION_INTERVAL_MILLIS,
                       collectionIntervals.mUserSwitchIntervalMillis.count());
        dumpStatsRecordsProto(userSwitchCollection, outProto);
        outProto.end(userSwitchCollectionToken);
        outProto.end(userSwitchStatsToken);
    }

    uint64_t lastNMinutesStatsToken = outProto.start(PerformanceStats::LAST_N_MINUTES_STATS);
    outProto.write(StatsCollection::COLLECTION_INTERVAL_MILLIS,
                   collectionIntervals.mPeriodicIntervalMillis.count());
    dumpStatsRecordsProto(mPeriodicCollection, outProto);
    outProto.end(lastNMinutesStatsToken);

    uint64_t customCollectionStatsToken = outProto.start(PerformanceStats::CUSTOM_COLLECTION_STATS);
    outProto.write(StatsCollection::COLLECTION_INTERVAL_MILLIS,
                   collectionIntervals.mCustomIntervalMillis.count());
    dumpStatsRecordsProto(mCustomCollection, outProto);
    outProto.end(customCollectionStatsToken);

    outProto.end(performanceStatsToken);

    return {};
}

void PerformanceProfiler::dumpStatsRecordsProto(const CollectionInfo& collection,
                                                ProtoOutputStream& outProto) const {
    int id = 0;
    for (const auto& record : collection.records) {
        uint64_t statsRecordToken = outProto.start(StatsCollection::RECORDS);

        outProto.write(StatsRecord::ID, id++);
        struct tm timeinfo;
        memset(&timeinfo, 0, sizeof(timeinfo));
        auto dateTime = std::chrono::system_clock::to_time_t(record.collectionTimeMillis);
        if (!localtime_r(&dateTime, &timeinfo)) {
            ALOGE("Failed to obtain localtime: %s", strerror(errno));
            return;
        }

        auto collectionTimeMillis = std::chrono::duration_cast<std::chrono::milliseconds>(
                record.collectionTimeMillis - std::chrono::system_clock::from_time_t(dateTime));

        uint64_t dateToken = outProto.start(StatsRecord::DATE);
        outProto.write(Date::YEAR, timeinfo.tm_year + 1900);
        outProto.write(Date::MONTH, timeinfo.tm_mon);
        outProto.write(Date::DAY, timeinfo.tm_mday);
        outProto.end(dateToken);

        uint64_t timeOfDayToken = outProto.start(StatsRecord::TIME);
        outProto.write(TimeOfDay::HOURS, timeinfo.tm_hour);
        outProto.write(TimeOfDay::MINUTES, timeinfo.tm_min);
        outProto.write(TimeOfDay::SECONDS, timeinfo.tm_sec);
        outProto.write(TimeOfDay::MILLIS, collectionTimeMillis.count());
        outProto.end(timeOfDayToken);

        uint64_t systemWideStatsToken = outProto.start(StatsRecord::SYSTEM_WIDE_STATS);
        outProto.write(SystemWideStats::IO_WAIT_TIME_MILLIS,
                       record.systemSummaryStats.cpuIoWaitTimeMillis);
        outProto.write(SystemWideStats::IDLE_CPU_TIME_MILLIS,
                       record.systemSummaryStats.cpuIdleTimeMillis);
        outProto.write(SystemWideStats::TOTAL_CPU_TIME_MILLIS,
                       record.systemSummaryStats.totalCpuTimeMillis);
        outProto.write(SystemWideStats::TOTAL_CPU_CYCLES,
                       static_cast<int>(record.systemSummaryStats.totalCpuCycles));
        outProto.write(SystemWideStats::TOTAL_CONTEXT_SWITCHES,
                       static_cast<int>(record.systemSummaryStats.contextSwitchesCount));
        outProto.write(SystemWideStats::TOTAL_IO_BLOCKED_PROCESSES,
                       static_cast<int>(record.systemSummaryStats.ioBlockedProcessCount));
        outProto.write(SystemWideStats::TOTAL_MAJOR_PAGE_FAULTS,
                       static_cast<int>(record.userPackageSummaryStats.totalMajorFaults));

        uint64_t totalStorageIoStatsToken = outProto.start(SystemWideStats::TOTAL_STORAGE_IO_STATS);
        outProto.write(StorageIoStats::FG_BYTES,
                       record.userPackageSummaryStats.totalIoStats[WRITE_BYTES][FOREGROUND]);
        outProto.write(StorageIoStats::FG_FSYNC,
                       record.userPackageSummaryStats.totalIoStats[FSYNC_COUNT][FOREGROUND]);
        outProto.write(StorageIoStats::BG_BYTES,
                       record.userPackageSummaryStats.totalIoStats[WRITE_BYTES][BACKGROUND]);
        outProto.write(StorageIoStats::BG_FSYNC,
                       record.userPackageSummaryStats.totalIoStats[FSYNC_COUNT][BACKGROUND]);
        outProto.end(totalStorageIoStatsToken);

        outProto.end(systemWideStatsToken);

        dumpPackageCpuStatsProto(record.userPackageSummaryStats.topNCpuTimes, outProto);

        dumpPackageStorageIoStatsProto(record.userPackageSummaryStats.topNIoReads,
                                       StatsRecord::PACKAGE_STORAGE_IO_READ_STATS, outProto);

        dumpPackageStorageIoStatsProto(record.userPackageSummaryStats.topNIoWrites,
                                       StatsRecord::PACKAGE_STORAGE_IO_WRITE_STATS, outProto);

        dumpPackageTaskStateStatsProto(record.userPackageSummaryStats.topNIoBlocked,
                                       record.userPackageSummaryStats.taskCountByUid, outProto);

        dumpPackageMajorPageFaultsProto(record.userPackageSummaryStats.topNMajorFaults, outProto);

        outProto.end(statsRecordToken);
    }
}

void PerformanceProfiler::dumpPackageCpuStatsProto(
        const std::vector<UserPackageStats>& topNCpuTimes, ProtoOutputStream& outProto) const {
    for (const auto& userPackageStats : topNCpuTimes) {
        uint64_t packageCpuStatsToken = outProto.start(StatsRecord::PACKAGE_CPU_STATS);
        const auto& procCpuStatsView =
                std::get_if<UserPackageStats::ProcCpuStatsView>(&userPackageStats.statsView);

        uint64_t userPackageInfoToken = outProto.start(PackageCpuStats::USER_PACKAGE_INFO);
        outProto.write(UserPackageInfo::USER_ID,
                       static_cast<int>(multiuser_get_user_id(userPackageStats.uid)));
        outProto.write(UserPackageInfo::PACKAGE_NAME, userPackageStats.genericPackageName);
        outProto.end(userPackageInfoToken);

        uint64_t cpuStatsToken = outProto.start(PackageCpuStats::CPU_STATS);
        outProto.write(PackageCpuStats::CpuStats::CPU_TIME_MILLIS,
                       static_cast<int>(procCpuStatsView->cpuTimeMillis));
        outProto.write(PackageCpuStats::CpuStats::CPU_CYCLES,
                       static_cast<int>(procCpuStatsView->cpuCycles));
        outProto.end(cpuStatsToken);

        for (const auto& processCpuStat : procCpuStatsView->topNProcesses) {
            uint64_t processCpuStatToken = outProto.start(PackageCpuStats::PROCESS_CPU_STATS);
            outProto.write(PackageCpuStats::ProcessCpuStats::COMMAND, processCpuStat.comm);

            uint64_t processCpuValueToken =
                    outProto.start(PackageCpuStats::ProcessCpuStats::CPU_STATS);
            outProto.write(PackageCpuStats::CpuStats::CPU_TIME_MILLIS,
                           static_cast<int>(processCpuStat.cpuTimeMillis));
            outProto.write(PackageCpuStats::CpuStats::CPU_CYCLES,
                           static_cast<int>(processCpuStat.cpuCycles));
            outProto.end(processCpuValueToken);

            outProto.end(processCpuStatToken);
        }
        outProto.end(packageCpuStatsToken);
    }
}

void PerformanceProfiler::dumpPackageStorageIoStatsProto(
        const std::vector<UserPackageStats>& userPackageStats, const uint64_t storageStatsFieldId,
        ProtoOutputStream& outProto) const {
    for (const auto& userPackageStats : userPackageStats) {
        uint64_t token = outProto.start(storageStatsFieldId);
        const auto& ioStatsView =
                std::get_if<UserPackageStats::IoStatsView>(&userPackageStats.statsView);

        uint64_t userPackageInfoToken = outProto.start(PackageStorageIoStats::USER_PACKAGE_INFO);
        outProto.write(UserPackageInfo::USER_ID,
                       static_cast<int>(multiuser_get_user_id(userPackageStats.uid)));
        outProto.write(UserPackageInfo::PACKAGE_NAME, userPackageStats.genericPackageName);
        outProto.end(userPackageInfoToken);

        uint64_t storageIoStatsToken = outProto.start(PackageStorageIoStats::STORAGE_IO_STATS);
        outProto.write(StorageIoStats::FG_BYTES, static_cast<int>(ioStatsView->bytes[FOREGROUND]));
        outProto.write(StorageIoStats::FG_FSYNC, static_cast<int>(ioStatsView->fsync[FOREGROUND]));
        outProto.write(StorageIoStats::BG_BYTES, static_cast<int>(ioStatsView->bytes[BACKGROUND]));
        outProto.write(StorageIoStats::BG_FSYNC, static_cast<int>(ioStatsView->fsync[BACKGROUND]));
        outProto.end(storageIoStatsToken);

        outProto.end(token);
    }
}

void PerformanceProfiler::dumpPackageTaskStateStatsProto(
        const std::vector<UserPackageStats>& topNIoBlocked,
        const std::unordered_map<uid_t, uint64_t>& taskCountByUid,
        ProtoOutputStream& outProto) const {
    for (const auto& userPackageStats : topNIoBlocked) {
        const auto taskCount = taskCountByUid.find(userPackageStats.uid);
        if (taskCount == taskCountByUid.end()) {
            continue;
        }

        uint64_t packageTaskStateStatsToken = outProto.start(StatsRecord::PACKAGE_TASK_STATE_STATS);
        const auto& procSingleStatsView =
                std::get_if<UserPackageStats::ProcSingleStatsView>(&userPackageStats.statsView);

        uint64_t userPackageInfoToken = outProto.start(PackageTaskStateStats::USER_PACKAGE_INFO);
        outProto.write(UserPackageInfo::USER_ID,
                       static_cast<int>(multiuser_get_user_id(userPackageStats.uid)));
        outProto.write(UserPackageInfo::PACKAGE_NAME, userPackageStats.genericPackageName);
        outProto.end(userPackageInfoToken);

        outProto.write(PackageTaskStateStats::IO_BLOCKED_TASK_COUNT,
                       static_cast<int>(procSingleStatsView->value));
        outProto.write(PackageTaskStateStats::TOTAL_TASK_COUNT,
                       static_cast<int>(taskCount->second));

        for (const auto& processValue : procSingleStatsView->topNProcesses) {
            uint64_t processTaskStateStatsToken =
                    outProto.start(PackageTaskStateStats::PROCESS_TASK_STATE_STATS);
            outProto.write(PackageTaskStateStats::ProcessTaskStateStats::COMMAND,
                           processValue.comm);
            outProto.write(PackageTaskStateStats::ProcessTaskStateStats::IO_BLOCKED_TASK_COUNT,
                           static_cast<int>(processValue.value));
            outProto.end(processTaskStateStatsToken);
        }

        outProto.end(packageTaskStateStatsToken);
    }
}

void PerformanceProfiler::dumpPackageMajorPageFaultsProto(
        const std::vector<UserPackageStats>& topNMajorFaults, ProtoOutputStream& outProto) const {
    for (const auto& userPackageStats : topNMajorFaults) {
        uint64_t packageMajorPageFaultsToken =
                outProto.start(StatsRecord::PACKAGE_MAJOR_PAGE_FAULTS);
        const auto& procSingleStatsView =
                std::get_if<UserPackageStats::ProcSingleStatsView>(&userPackageStats.statsView);

        uint64_t userPackageInfoToken = outProto.start(PackageMajorPageFaults::USER_PACKAGE_INFO);
        outProto.write(UserPackageInfo::USER_ID,
                       static_cast<int>(multiuser_get_user_id(userPackageStats.uid)));
        outProto.write(UserPackageInfo::PACKAGE_NAME, userPackageStats.genericPackageName);
        outProto.end(userPackageInfoToken);

        outProto.write(PackageMajorPageFaults::MAJOR_PAGE_FAULTS_COUNT,
                       static_cast<int>(procSingleStatsView->value));

        outProto.end(packageMajorPageFaultsToken);
    }
}

Result<void> PerformanceProfiler::onCustomCollectionDump(int fd) {
    if (fd == -1) {
        // Custom collection ends so clear the cache.
        mCustomCollection.records.clear();
        mCustomCollection = {
                .maxCacheSize = std::numeric_limits<std::size_t>::max(),
                .records = {},
        };
        return {};
    }

    if (!WriteStringToFd(StringPrintf(kCustomCollectionTitle, std::string(75, '-').c_str(),
                                      std::string(75, '-').c_str()),
                         fd) ||
        !WriteStringToFd(mCustomCollection.toString(), fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to write custom I/O collection report.";
    }

    return {};
}

Result<void> PerformanceProfiler::onSystemStartup() {
    Mutex::Autolock lock(mMutex);
    mBoottimeCollection.records.clear();
    mWakeUpCollection.records.clear();
    return {};
}

void PerformanceProfiler::onCarWatchdogServiceRegistered() {
    Mutex::Autolock lock(mMutex);
    mDoSendResourceUsageStats =
            sysprop::syncResourceUsageStatsWithCarServiceEnabled().value_or(false);
}

Result<void> PerformanceProfiler::onBoottimeCollection(
        time_point_millis time, const wp<UidStatsCollectorInterface>& uidStatsCollector,
        const wp<ProcStatCollectorInterface>& procStatCollector, ResourceStats* resourceStats) {
    const sp<UidStatsCollectorInterface> uidStatsCollectorSp = uidStatsCollector.promote();
    const sp<ProcStatCollectorInterface> procStatCollectorSp = procStatCollector.promote();
    auto result = checkDataCollectors(uidStatsCollectorSp, procStatCollectorSp);
    if (!result.ok()) {
        return result;
    }
    Mutex::Autolock lock(mMutex);
    return processLocked(time, SystemState::NORMAL_MODE, std::unordered_set<std::string>(),
                         uidStatsCollectorSp, procStatCollectorSp, &mBoottimeCollection,
                         resourceStats);
}

Result<void> PerformanceProfiler::onPeriodicCollection(
        time_point_millis time, SystemState systemState,
        const wp<UidStatsCollectorInterface>& uidStatsCollector,
        const wp<ProcStatCollectorInterface>& procStatCollector, ResourceStats* resourceStats) {
    const sp<UidStatsCollectorInterface> uidStatsCollectorSp = uidStatsCollector.promote();
    const sp<ProcStatCollectorInterface> procStatCollectorSp = procStatCollector.promote();
    clearExpiredSystemEventCollections(time);
    auto result = checkDataCollectors(uidStatsCollectorSp, procStatCollectorSp);
    if (!result.ok()) {
        return result;
    }
    Mutex::Autolock lock(mMutex);
    return processLocked(time, systemState, std::unordered_set<std::string>(), uidStatsCollectorSp,
                         procStatCollectorSp, &mPeriodicCollection, resourceStats);
}

Result<void> PerformanceProfiler::onUserSwitchCollection(
        time_point_millis time, userid_t from, userid_t to,
        const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
        const android::wp<ProcStatCollectorInterface>& procStatCollector) {
    const sp<UidStatsCollectorInterface> uidStatsCollectorSp = uidStatsCollector.promote();
    const sp<ProcStatCollectorInterface> procStatCollectorSp = procStatCollector.promote();
    auto result = checkDataCollectors(uidStatsCollectorSp, procStatCollectorSp);
    if (!result.ok()) {
        return result;
    }
    Mutex::Autolock lock(mMutex);
    if (mUserSwitchCollections.empty() || mUserSwitchCollections.back().from != from ||
        mUserSwitchCollections.back().to != to) {
        UserSwitchCollectionInfo userSwitchCollection = {
                {
                        .maxCacheSize = std::numeric_limits<std::size_t>::max(),
                        .records = {},
                },
                .from = from,
                .to = to,
        };
        mUserSwitchCollections.push_back(userSwitchCollection);
    }
    if (mUserSwitchCollections.size() > mMaxUserSwitchEvents) {
        mUserSwitchCollections.erase(mUserSwitchCollections.begin());
    }
    return processLocked(time, SystemState::NORMAL_MODE, std::unordered_set<std::string>(),
                         uidStatsCollectorSp, procStatCollectorSp, &mUserSwitchCollections.back(),
                         /*resourceStats=*/nullptr);
}

Result<void> PerformanceProfiler::onWakeUpCollection(
        time_point_millis time, const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
        const android::wp<ProcStatCollectorInterface>& procStatCollector) {
    const sp<UidStatsCollectorInterface> uidStatsCollectorSp = uidStatsCollector.promote();
    const sp<ProcStatCollectorInterface> procStatCollectorSp = procStatCollector.promote();
    auto result = checkDataCollectors(uidStatsCollectorSp, procStatCollectorSp);
    if (!result.ok()) {
        return result;
    }
    Mutex::Autolock lock(mMutex);
    return processLocked(time, SystemState::NORMAL_MODE, std::unordered_set<std::string>(),
                         uidStatsCollectorSp, procStatCollectorSp, &mWakeUpCollection,
                         /*resourceStats=*/nullptr);
}

Result<void> PerformanceProfiler::onCustomCollection(
        time_point_millis time, SystemState systemState,
        const std::unordered_set<std::string>& filterPackages,
        const wp<UidStatsCollectorInterface>& uidStatsCollector,
        const wp<ProcStatCollectorInterface>& procStatCollector, ResourceStats* resourceStats) {
    const sp<UidStatsCollectorInterface> uidStatsCollectorSp = uidStatsCollector.promote();
    const sp<ProcStatCollectorInterface> procStatCollectorSp = procStatCollector.promote();
    auto result = checkDataCollectors(uidStatsCollectorSp, procStatCollectorSp);
    if (!result.ok()) {
        return result;
    }
    Mutex::Autolock lock(mMutex);
    return processLocked(time, systemState, filterPackages, uidStatsCollectorSp,
                         procStatCollectorSp, &mCustomCollection, resourceStats);
}

Result<void> PerformanceProfiler::processLocked(
        time_point_millis time, SystemState systemState,
        const std::unordered_set<std::string>& filterPackages,
        const sp<UidStatsCollectorInterface>& uidStatsCollector,
        const sp<ProcStatCollectorInterface>& procStatCollector, CollectionInfo* collectionInfo,
        ResourceStats* resourceStats) {
    if (collectionInfo->maxCacheSize == 0) {
        return Error() << "Maximum cache size cannot be 0";
    }
    PerfStatsRecord record{
            .collectionTimeMillis = time,
    };
    bool isGarageModeActive = systemState == SystemState::GARAGE_MODE;
    bool shouldSendResourceUsageStats = mDoSendResourceUsageStats && (resourceStats != nullptr);
    std::vector<UidResourceUsageStats>* uidResourceUsageStats =
            shouldSendResourceUsageStats ? new std::vector<UidResourceUsageStats>() : nullptr;
    processProcStatLocked(procStatCollector, &record.systemSummaryStats);
    // The system-wide CPU time should be the same as CPU time aggregated here across all UID, so
    // reuse the total CPU time from SystemSummaryStat
    int64_t totalCpuTimeMillis = record.systemSummaryStats.totalCpuTimeMillis;
    record.userPackageSummaryStats.totalCpuTimeMillis = totalCpuTimeMillis;
    processUidStatsLocked(isGarageModeActive, totalCpuTimeMillis, filterPackages, uidStatsCollector,
                          uidResourceUsageStats, &record.userPackageSummaryStats);
    // The system-wide CPU cycles are the aggregate of all the UID's CPU cycles collected during
    // each poll.
    record.systemSummaryStats.totalCpuCycles = record.userPackageSummaryStats.totalCpuCycles;
    if (collectionInfo->records.size() > collectionInfo->maxCacheSize) {
        collectionInfo->records.erase(collectionInfo->records.begin());  // Erase the oldest record.
    }
    collectionInfo->records.push_back(record);

    if (!shouldSendResourceUsageStats) {
        return {};
    }

    // The durationInMillis field is set in WatchdogPerfService, which tracks the last
    // collection time.
    ResourceUsageStats resourceUsageStats = {
            .startTimeEpochMillis = time.time_since_epoch().count(),
    };
    resourceUsageStats.systemSummaryUsageStats =
            constructSystemSummaryUsageStats(isGarageModeActive, record.systemSummaryStats,
                                             record.userPackageSummaryStats);
    resourceUsageStats.uidResourceUsageStats = *uidResourceUsageStats;

    resourceStats->resourceUsageStats = std::make_optional(resourceUsageStats);

    return {};
}

void PerformanceProfiler::processUidStatsLocked(
        bool isGarageModeActive, int64_t totalCpuTimeMillis,
        const std::unordered_set<std::string>& filterPackages,
        const sp<UidStatsCollectorInterface>& uidStatsCollector,
        std::vector<UidResourceUsageStats>* uidResourceUsageStats,
        UserPackageSummaryStats* userPackageSummaryStats) {
    const std::vector<UidStats>& uidStats = uidStatsCollector->deltaStats();
    if (uidStats.empty()) {
        return;
    }
    if (filterPackages.empty()) {
        userPackageSummaryStats->topNCpuTimes.resize(mTopNStatsPerCategory);
        userPackageSummaryStats->topNIoReads.resize(mTopNStatsPerCategory);
        userPackageSummaryStats->topNIoWrites.resize(mTopNStatsPerCategory);
        userPackageSummaryStats->topNIoBlocked.resize(mTopNStatsPerCategory);
        userPackageSummaryStats->topNMajorFaults.resize(mTopNStatsPerCategory);
    }
    int64_t elapsedTimeSinceBootMs = kGetElapsedTimeSinceBootMillisFunc();
    for (const auto& curUidStats : uidStats) {
        // Set the overall stats.
        userPackageSummaryStats->totalCpuCycles += curUidStats.procStats.cpuCycles;
        addUidIoStats(curUidStats.ioStats.metrics, userPackageSummaryStats->totalIoStats);
        userPackageSummaryStats->totalMajorFaults += curUidStats.procStats.totalMajorFaults;

        // Transform |UidStats| to |UserPackageStats| for each stats view.
        auto ioReadsPackageStats = UserPackageStats(MetricType::READ_BYTES, curUidStats);
        auto ioWritesPackageStats = UserPackageStats(MetricType::WRITE_BYTES, curUidStats);
        auto cpuTimePackageStats =
                UserPackageStats(CPU_TIME, curUidStats, mTopNStatsPerSubcategory);
        auto ioBlockedPackageStats =
                UserPackageStats(IO_BLOCKED_TASKS_COUNT, curUidStats, mTopNStatsPerSubcategory);
        auto majorFaultsPackageStats =
                UserPackageStats(MAJOR_FAULTS, curUidStats, mTopNStatsPerSubcategory);

        if (filterPackages.empty()) {
            cacheTopNStats(ioReadsPackageStats, &userPackageSummaryStats->topNIoReads);
            cacheTopNStats(ioWritesPackageStats, &userPackageSummaryStats->topNIoWrites);
            cacheTopNStats(cpuTimePackageStats, &userPackageSummaryStats->topNCpuTimes);
            if (cacheTopNStats(ioBlockedPackageStats, &userPackageSummaryStats->topNIoBlocked)) {
                userPackageSummaryStats->taskCountByUid[ioBlockedPackageStats.uid] =
                        curUidStats.procStats.totalTasksCount;
            }
            cacheTopNStats(majorFaultsPackageStats, &userPackageSummaryStats->topNMajorFaults);
        } else if (filterPackages.count(curUidStats.genericPackageName()) != 0) {
            userPackageSummaryStats->topNIoReads.push_back(ioReadsPackageStats);
            userPackageSummaryStats->topNIoWrites.push_back(ioWritesPackageStats);
            userPackageSummaryStats->topNCpuTimes.push_back(cpuTimePackageStats);
            userPackageSummaryStats->topNIoBlocked.push_back(ioBlockedPackageStats);
            userPackageSummaryStats->topNMajorFaults.push_back(majorFaultsPackageStats);
            userPackageSummaryStats->taskCountByUid[ioBlockedPackageStats.uid] =
                    curUidStats.procStats.totalTasksCount;
        }

        // A null value in uidResourceUsageStats indicates that uid resource usage stats
        // will not be sent to CarService. Hence, there is no need to populate it.
        if (uidResourceUsageStats == nullptr) {
            continue;
        }

        PackageIdentifier packageIdentifier = {
                .name = curUidStats.genericPackageName(),
                .uid = static_cast<int32_t>(curUidStats.uid()),
        };
        int64_t uidUptimeMillis =
                calculateUidUptimeMillis(elapsedTimeSinceBootMs, curUidStats.procStats);

        const auto& procCpuStatsView =
                std::get<UserPackageStats::ProcCpuStatsView>(cpuTimePackageStats.statsView);
        const auto& ioReadsStatsView =
                std::get<UserPackageStats::IoStatsView>(ioReadsPackageStats.statsView);
        const auto& ioWritesStatsView =
                std::get<UserPackageStats::IoStatsView>(ioWritesPackageStats.statsView);

        UidResourceUsageStats usageStats =
                constructUidResourceUsageStats(std::move(packageIdentifier), uidUptimeMillis,
                                               totalCpuTimeMillis, isGarageModeActive,
                                               procCpuStatsView, ioReadsStatsView,
                                               ioWritesStatsView);
        uidResourceUsageStats->push_back(std::move(usageStats));
    }
    if (mLastMajorFaults != 0) {
        int64_t increase = userPackageSummaryStats->totalMajorFaults - mLastMajorFaults;
        userPackageSummaryStats->majorFaultsPercentChange =
                (static_cast<double>(increase) / static_cast<double>(mLastMajorFaults)) * 100.0;
    }
    mLastMajorFaults = userPackageSummaryStats->totalMajorFaults;

    const auto removeEmptyStats = [](std::vector<UserPackageStats>& userPackageStats) {
        for (auto it = userPackageStats.begin(); it != userPackageStats.end(); ++it) {
            /* std::monostate is the first alternative in the variant. When the variant is
             * uninitialized, the index points to this alternative.
             */
            if (it->statsView.index() == 0) {
                userPackageStats.erase(it, userPackageStats.end());
                break;
            }
        }
    };
    removeEmptyStats(userPackageSummaryStats->topNCpuTimes);
    removeEmptyStats(userPackageSummaryStats->topNIoReads);
    removeEmptyStats(userPackageSummaryStats->topNIoWrites);
    removeEmptyStats(userPackageSummaryStats->topNIoBlocked);
    removeEmptyStats(userPackageSummaryStats->topNMajorFaults);
}

void PerformanceProfiler::processProcStatLocked(
        const sp<ProcStatCollectorInterface>& procStatCollector,
        SystemSummaryStats* systemSummaryStats) const {
    const ProcStatInfo& procStatInfo = procStatCollector->deltaStats();
    systemSummaryStats->cpuIoWaitTimeMillis = procStatInfo.cpuStats.ioWaitTimeMillis;
    systemSummaryStats->cpuIdleTimeMillis = procStatInfo.cpuStats.idleTimeMillis;
    systemSummaryStats->totalCpuTimeMillis = procStatInfo.totalCpuTimeMillis();
    systemSummaryStats->contextSwitchesCount = procStatInfo.contextSwitchesCount;
    systemSummaryStats->ioBlockedProcessCount = procStatInfo.ioBlockedProcessCount;
    systemSummaryStats->totalProcessCount = procStatInfo.totalProcessCount();
}

Result<void> PerformanceProfiler::onUserSwitchCollectionDump(int fd) const {
    if (!WriteStringToFd(StringPrintf(kUserSwitchCollectionTitle, std::string(75, '-').c_str(),
                                      std::string(38, '=').c_str()),
                         fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to dump the user-switch collection report.";
    }
    if (!mUserSwitchCollections.empty() &&
        !WriteStringToFd(StringPrintf(kUserSwitchCollectionSubtitle, mUserSwitchCollections.size()),
                         fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to dump the user-switch collection report.";
    }
    if (mUserSwitchCollections.empty() && !WriteStringToFd(kEmptyCollectionMessage, fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to dump the user-switch collection report.";
    }
    for (size_t i = 0; i < mUserSwitchCollections.size(); ++i) {
        const auto& userSwitchCollection = mUserSwitchCollections[i];
        if (!WriteStringToFd(StringPrintf(kUserSwitchEventTitle, i, userSwitchCollection.from,
                                          userSwitchCollection.to, std::string(26, '=').c_str()),
                             fd) ||
            !WriteStringToFd(userSwitchCollection.toString(), fd)) {
            return Error(FAILED_TRANSACTION) << "Failed to dump the user-switch collection report.";
        }
    }
    return {};
}

void PerformanceProfiler::clearExpiredSystemEventCollections(time_point_millis now) {
    Mutex::Autolock lock(mMutex);
    auto clearExpiredSystemEvent = [&](CollectionInfo* info) -> bool {
        if (info->records.empty() ||
            now - info->records.back().collectionTimeMillis < mSystemEventDataCacheDurationSec) {
            return false;
        }
        info->records.clear();
        return true;
    };
    if (clearExpiredSystemEvent(&mBoottimeCollection)) {
        ALOGI("Cleared boot-time collection stats");
    }
    if (clearExpiredSystemEvent(&mWakeUpCollection)) {
        ALOGI("Cleared wake-up collection stats");
    }
    if (!mUserSwitchCollections.empty() &&
        clearExpiredSystemEvent(&mUserSwitchCollections.front())) {
        mUserSwitchCollections.erase(mUserSwitchCollections.begin());
        ALOGI("Cleared the oldest user-switch event collection stats");
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
