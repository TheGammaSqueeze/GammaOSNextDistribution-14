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

#define LOG_TAG "carwatchdogd"
#define DEBUG false  // STOPSHIP if true.

#include "IoOveruseMonitor.h"

#include "PackageInfoResolver.h"
#include "ServiceManager.h"

#include <WatchdogProperties.sysprop.h>
#include <aidl/android/automotive/watchdog/IResourceOveruseListener.h>
#include <aidl/android/automotive/watchdog/ResourceOveruseStats.h>
#include <aidl/android/automotive/watchdog/internal/PackageIdentifier.h>
#include <aidl/android/automotive/watchdog/internal/UidType.h>
#include <android-base/file.h>
#include <android-base/strings.h>
#include <android/util/ProtoOutputStream.h>
#include <binder/IPCThreadState.h>
#include <log/log.h>
#include <processgroup/sched_policy.h>

#include <pthread.h>

#include <limits>
#include <thread>  // NOLINT(build/c++11)

namespace android {
namespace automotive {
namespace watchdog {

namespace {

using ::aidl::android::automotive::watchdog::IoOveruseStats;
using ::aidl::android::automotive::watchdog::IResourceOveruseListener;
using ::aidl::android::automotive::watchdog::PerStateBytes;
using ::aidl::android::automotive::watchdog::internal::ComponentType;
using ::aidl::android::automotive::watchdog::internal::IoOveruseConfiguration;
using ::aidl::android::automotive::watchdog::internal::IoUsageStats;
using ::aidl::android::automotive::watchdog::internal::PackageIdentifier;
using ::aidl::android::automotive::watchdog::internal::PackageInfo;
using ::aidl::android::automotive::watchdog::internal::PackageIoOveruseStats;
using ::aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration;
using ::aidl::android::automotive::watchdog::internal::ResourceOveruseStats;
using ::aidl::android::automotive::watchdog::internal::ResourceStats;
using ::aidl::android::automotive::watchdog::internal::UidType;
using ::aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats;
using ::android::IPCThreadState;
using ::android::sp;
using ::android::base::EndsWith;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::android::util::ProtoOutputStream;
using ::ndk::ScopedAIBinder_DeathRecipient;
using ::ndk::SpAIBinder;

constexpr int64_t kMaxInt32 = std::numeric_limits<int32_t>::max();
constexpr int64_t kMaxInt64 = std::numeric_limits<int64_t>::max();
// Minimum written bytes to sync the stats with the Watchdog service.
constexpr int64_t kMinSyncWrittenBytes = 100 * 1024;
// Minimum percentage of threshold to warn killable applications.
constexpr double kDefaultIoOveruseWarnPercentage = 80;
// Maximum numer of system-wide stats (from periodic monitoring) to cache.
constexpr size_t kMaxPeriodicMonitorBufferSize = 1000;
constexpr const char* kHelpText =
        "\n%s dump options:\n"
        "%s <package name>, <package name>,...: Reset resource overuse stats for the given package "
        "names. Value for this flag is a comma-separated value containing package names.\n";

std::string uniquePackageIdStr(const std::string& name, userid_t userId) {
    return StringPrintf("%s:%" PRId32, name.c_str(), userId);
}

std::string uniquePackageIdStr(const PackageIdentifier& id) {
    return uniquePackageIdStr(id.name, multiuser_get_user_id(id.uid));
}

PerStateBytes sum(const PerStateBytes& lhs, const PerStateBytes& rhs) {
    const auto sum = [](const int64_t& l, const int64_t& r) -> int64_t {
        return (kMaxInt64 - l) > r ? (l + r) : kMaxInt64;
    };
    PerStateBytes result;
    result.foregroundBytes = sum(lhs.foregroundBytes, rhs.foregroundBytes);
    result.backgroundBytes = sum(lhs.backgroundBytes, rhs.backgroundBytes);
    result.garageModeBytes = sum(lhs.garageModeBytes, rhs.garageModeBytes);
    return result;
}

PerStateBytes diff(const PerStateBytes& lhs, const PerStateBytes& rhs) {
    const auto sub = [](const int64_t& l, const int64_t& r) -> int64_t {
        return l >= r ? (l - r) : 0;
    };
    PerStateBytes result;
    result.foregroundBytes = sub(lhs.foregroundBytes, rhs.foregroundBytes);
    result.backgroundBytes = sub(lhs.backgroundBytes, rhs.backgroundBytes);
    result.garageModeBytes = sub(lhs.garageModeBytes, rhs.garageModeBytes);
    return result;
}

std::tuple<int64_t, int64_t> calculateStartAndDuration(struct tm currentTm) {
    // The stats are stored per-day so the start time is always the beginning of the day.
    auto startTm = currentTm;
    startTm.tm_sec = 0;
    startTm.tm_min = 0;
    startTm.tm_hour = 0;

    int64_t startTime = static_cast<int64_t>(mktime(&startTm));
    int64_t currentEpochSeconds = static_cast<int64_t>(mktime(&currentTm));
    return std::make_tuple(startTime, currentEpochSeconds - startTime);
}

int64_t totalPerStateBytes(PerStateBytes perStateBytes) {
    const auto sum = [](const int64_t& l, const int64_t& r) -> int64_t {
        return kMaxInt64 - l > r ? (l + r) : kMaxInt64;
    };
    return sum(perStateBytes.foregroundBytes,
               sum(perStateBytes.backgroundBytes, perStateBytes.garageModeBytes));
}

std::tuple<int32_t, PerStateBytes> calculateOveruseAndForgivenBytes(PerStateBytes writtenBytes,
                                                                    PerStateBytes threshold) {
    const auto div = [](const int64_t& l, const int64_t& r) -> int32_t {
        return r > 0 ? (l / r) : 1;
    };
    const auto mul = [](const int32_t& l, const int32_t& r) -> int32_t {
        if (l == 0 || r == 0) {
            return 0;
        }
        return (kMaxInt32 / r) > l ? (l * r) : kMaxInt32;
    };
    const auto sum = [](const int32_t& l, const int32_t& r) -> int32_t {
        return (kMaxInt32 - l) > r ? (l + r) : kMaxInt32;
    };
    int32_t foregroundOveruses = div(writtenBytes.foregroundBytes, threshold.foregroundBytes);
    int32_t backgroundOveruses = div(writtenBytes.backgroundBytes, threshold.backgroundBytes);
    int32_t garageModeOveruses = div(writtenBytes.garageModeBytes, threshold.garageModeBytes);
    int32_t totalOveruses = sum(foregroundOveruses, sum(backgroundOveruses, garageModeOveruses));

    PerStateBytes forgivenWriteBytes;
    forgivenWriteBytes.foregroundBytes = mul(foregroundOveruses, threshold.foregroundBytes);
    forgivenWriteBytes.backgroundBytes = mul(backgroundOveruses, threshold.backgroundBytes);
    forgivenWriteBytes.garageModeBytes = mul(garageModeOveruses, threshold.garageModeBytes);

    return std::make_tuple(totalOveruses, forgivenWriteBytes);
}

void onBinderDied(void* cookie) {
    const auto& thiz = ServiceManager::getInstance()->getIoOveruseMonitor();
    if (thiz == nullptr) {
        return;
    }
    thiz->handleBinderDeath(cookie);
}

}  // namespace

std::tuple<int64_t, int64_t> calculateStartAndDuration(const time_point_millis& currentTime) {
    auto timeInSeconds = std::chrono::system_clock::to_time_t(currentTime);
    struct tm currentGmt;
    gmtime_r(&timeInSeconds, &currentGmt);
    return calculateStartAndDuration(currentGmt);
}

IoOveruseMonitor::IoOveruseMonitor(
        const android::sp<WatchdogServiceHelperInterface>& watchdogServiceHelper) :
      mMinSyncWrittenBytes(kMinSyncWrittenBytes),
      mWatchdogServiceHelper(watchdogServiceHelper),
      mDeathRegistrationWrapper(sp<AIBinderDeathRegistrationWrapper>::make()),
      mDidReadTodayPrevBootStats(false),
      mSystemWideWrittenBytes({}),
      mPeriodicMonitorBufferSize(0),
      mLastSystemWideIoMonitorTime(0),
      mUserPackageDailyIoUsageById({}),
      mIoOveruseWarnPercentage(0),
      mLastUserPackageIoMonitorTime(time_point_millis::min()),
      mOveruseListenersByUid({}),
      mBinderDeathRecipient(
              ScopedAIBinder_DeathRecipient(AIBinder_DeathRecipient_new(onBinderDied))) {}

Result<void> IoOveruseMonitor::init() {
    std::unique_lock writeLock(mRwMutex);
    if (isInitializedLocked()) {
        return Error() << "Cannot initialize " << name() << " more than once";
    }
    mPeriodicMonitorBufferSize = static_cast<size_t>(
            sysprop::periodicMonitorBufferSize().value_or(kDefaultPeriodicMonitorBufferSize));
    if (mPeriodicMonitorBufferSize == 0 ||
        mPeriodicMonitorBufferSize > kMaxPeriodicMonitorBufferSize) {
        return Error() << "Periodic monitor buffer size cannot be zero or above "
                       << kDefaultPeriodicMonitorBufferSize << ". Received "
                       << mPeriodicMonitorBufferSize;
    }
    mIoOveruseWarnPercentage = static_cast<double>(
            sysprop::ioOveruseWarnPercentage().value_or(kDefaultIoOveruseWarnPercentage));
    mIoOveruseConfigs = sp<IoOveruseConfigs>::make();
    mPackageInfoResolver = PackageInfoResolver::getInstance();
    mPackageInfoResolver->setPackageConfigurations(mIoOveruseConfigs->vendorPackagePrefixes(),
                                                   mIoOveruseConfigs->packagesToAppCategories());
    if (DEBUG) {
        ALOGD("Initialized %s data processor", name().c_str());
    }
    return {};
}

void IoOveruseMonitor::terminate() {
    std::unique_lock writeLock(mRwMutex);

    ALOGW("Terminating %s", name().c_str());
    mWatchdogServiceHelper.clear();
    mIoOveruseConfigs.clear();
    mSystemWideWrittenBytes.clear();
    mUserPackageDailyIoUsageById.clear();
    for (const auto& [_, listener] : mOveruseListenersByUid) {
        AIBinder* aiBinder = listener->asBinder().get();
        mDeathRegistrationWrapper->unlinkToDeath(aiBinder, mBinderDeathRecipient.get(),
                                                 static_cast<void*>(aiBinder));
    }
    mOveruseListenersByUid.clear();
    if (DEBUG) {
        ALOGD("Terminated %s data processor", name().c_str());
    }
    return;
}

void IoOveruseMonitor::onCarWatchdogServiceRegistered() {
    std::unique_lock writeLock(mRwMutex);
    if (!mDidReadTodayPrevBootStats) {
        requestTodayIoUsageStatsLocked();
    }
}

Result<void> IoOveruseMonitor::onPeriodicCollection(
        time_point_millis time, SystemState systemState,
        const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
        [[maybe_unused]] const android::wp<ProcStatCollectorInterface>& procStatCollector,
        ResourceStats* resourceStats) {
    android::sp<UidStatsCollectorInterface> uidStatsCollectorSp = uidStatsCollector.promote();
    if (uidStatsCollectorSp == nullptr) {
        return Error() << "Per-UID I/O stats collector must not be null";
    }

    auto timeInSeconds = std::chrono::system_clock::to_time_t(time);

    std::unique_lock writeLock(mRwMutex);
    if (!mDidReadTodayPrevBootStats) {
        requestTodayIoUsageStatsLocked();
    }
    struct tm prevGmt, curGmt;
    auto mLastUserPackageIoMonitorTimeInSeconds =
            std::chrono::system_clock::to_time_t(mLastUserPackageIoMonitorTime);
    gmtime_r(&mLastUserPackageIoMonitorTimeInSeconds, &prevGmt);
    gmtime_r(&timeInSeconds, &curGmt);
    if (prevGmt.tm_yday != curGmt.tm_yday || prevGmt.tm_year != curGmt.tm_year) {
        /*
         * Date changed so reset the daily I/O usage cache. CarWatchdogService automatically handles
         * date change on |CarWatchdogService.latestIoOveruseStats| call.
         */
        mUserPackageDailyIoUsageById.clear();
    }
    mLastUserPackageIoMonitorTime = time;
    const auto [startTime, durationInSeconds] = calculateStartAndDuration(curGmt);

    auto uidStats = uidStatsCollectorSp->deltaStats();
    if (uidStats.empty()) {
        return {};
    }
    std::unordered_map<uid_t, IoOveruseStats> overusingNativeStats;
    bool isGarageModeActive = systemState == SystemState::GARAGE_MODE;
    for (const auto& curUidStats : uidStats) {
        if (curUidStats.ioStats.sumWriteBytes() == 0 || !curUidStats.hasPackageInfo()) {
            /* 1. Ignore UIDs with zero written bytes since the last collection because they are
             * either already accounted for or no writes made since system start.
             *
             * 2. UID stats without package info is not useful because the stats isn't attributed to
             * any package/service.
             */
            continue;
        }
        UserPackageIoUsage curUsage(curUidStats.packageInfo, curUidStats.ioStats,
                                    isGarageModeActive);

        if (!mPrevBootIoUsageStatsById.empty()) {
            if (auto prevBootStats = mPrevBootIoUsageStatsById.find(curUsage.id());
                prevBootStats != mPrevBootIoUsageStatsById.end()) {
                curUsage += prevBootStats->second;
                mPrevBootIoUsageStatsById.erase(prevBootStats);
            }
        }
        UserPackageIoUsage* dailyIoUsage;
        if (auto cachedUsage = mUserPackageDailyIoUsageById.find(curUsage.id());
            cachedUsage != mUserPackageDailyIoUsageById.end()) {
            cachedUsage->second += curUsage;
            dailyIoUsage = &cachedUsage->second;
        } else {
            const auto& [it, wasInserted] = mUserPackageDailyIoUsageById.insert(
                    std::pair(curUsage.id(), std::move(curUsage)));
            dailyIoUsage = &it->second;
        }

        const auto threshold = mIoOveruseConfigs->fetchThreshold(dailyIoUsage->packageInfo);

        const auto deltaWrittenBytes =
                diff(dailyIoUsage->writtenBytes, dailyIoUsage->forgivenWriteBytes);
        const auto [currentOveruses, forgivenWriteBytes] =
                calculateOveruseAndForgivenBytes(deltaWrittenBytes, threshold);
        dailyIoUsage->totalOveruses += currentOveruses;
        dailyIoUsage->forgivenWriteBytes =
                sum(dailyIoUsage->forgivenWriteBytes, forgivenWriteBytes);

        PackageIoOveruseStats stats;
        stats.uid = curUidStats.packageInfo.packageIdentifier.uid;
        stats.shouldNotify = false;
        stats.forgivenWriteBytes = dailyIoUsage->forgivenWriteBytes;
        stats.ioOveruseStats.startTime = startTime;
        stats.ioOveruseStats.durationInSeconds = durationInSeconds;
        stats.ioOveruseStats.writtenBytes = dailyIoUsage->writtenBytes;
        stats.ioOveruseStats.totalOveruses = dailyIoUsage->totalOveruses;
        stats.ioOveruseStats.remainingWriteBytes = diff(threshold, deltaWrittenBytes);
        stats.ioOveruseStats.killableOnOveruse =
                mIoOveruseConfigs->isSafeToKill(dailyIoUsage->packageInfo);

        const auto& remainingWriteBytes = stats.ioOveruseStats.remainingWriteBytes;
        const auto exceedsWarnThreshold = [&](double remaining, double threshold) {
            if (threshold == 0) {
                return true;
            }
            double usedPercent = (100 - (remaining / threshold) * 100);
            return usedPercent > mIoOveruseWarnPercentage;
        };
        bool shouldSyncWatchdogService =
                (totalPerStateBytes(dailyIoUsage->writtenBytes) -
                 dailyIoUsage->lastSyncedWrittenBytes) >= mMinSyncWrittenBytes;
        if (currentOveruses > 0) {
            dailyIoUsage->isPackageWarned = false;
            /*
             * Send notifications for native service I/O overuses as well because system listeners
             * need to be notified of all I/O overuses.
             */
            stats.shouldNotify = true;
            if (dailyIoUsage->packageInfo.uidType == UidType::NATIVE) {
                overusingNativeStats[stats.uid] = stats.ioOveruseStats;
            }
            shouldSyncWatchdogService = true;
        } else if (dailyIoUsage->packageInfo.uidType == UidType::APPLICATION &&
                   stats.ioOveruseStats.killableOnOveruse && !dailyIoUsage->isPackageWarned &&
                   (exceedsWarnThreshold(remainingWriteBytes.foregroundBytes,
                                         threshold.foregroundBytes) ||
                    exceedsWarnThreshold(remainingWriteBytes.backgroundBytes,
                                         threshold.backgroundBytes) ||
                    exceedsWarnThreshold(remainingWriteBytes.garageModeBytes,
                                         threshold.garageModeBytes))) {
            /*
             * No need to warn native services or applications that won't be killed on I/O overuse
             * as they will be sent a notification when they exceed their daily threshold.
             */
            stats.shouldNotify = true;
            // Avoid duplicate warning before the daily threshold exceeded notification is sent.
            dailyIoUsage->isPackageWarned = true;
            shouldSyncWatchdogService = true;
        }
        if (shouldSyncWatchdogService) {
            dailyIoUsage->lastSyncedWrittenBytes = totalPerStateBytes(dailyIoUsage->writtenBytes);
            mLatestIoOveruseStats.emplace_back(std::move(stats));
        }
    }
    if (!overusingNativeStats.empty()) {
        notifyNativePackagesLocked(overusingNativeStats);
    }
    if (mLatestIoOveruseStats.empty()) {
        return {};
    }
    if (!(resourceStats->resourceOveruseStats).has_value()) {
        resourceStats->resourceOveruseStats = std::make_optional<ResourceOveruseStats>({});
    }
    resourceStats->resourceOveruseStats->packageIoOveruseStats = mLatestIoOveruseStats;
    // Clear the cache
    mLatestIoOveruseStats.clear();
    return {};
}

Result<void> IoOveruseMonitor::onCustomCollection(
        time_point_millis time, SystemState systemState,
        [[maybe_unused]] const std::unordered_set<std::string>& filterPackages,
        const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
        const android::wp<ProcStatCollectorInterface>& procStatCollector,
        ResourceStats* resourceStats) {
    // Nothing special for custom collection.
    return onPeriodicCollection(time, systemState, uidStatsCollector, procStatCollector,
                                resourceStats);
}

Result<void> IoOveruseMonitor::onPeriodicMonitor(
        time_t time, const android::wp<ProcDiskStatsCollectorInterface>& procDiskStatsCollector,
        const std::function<void()>& alertHandler) {
    if (procDiskStatsCollector == nullptr) {
        return Error() << "Proc disk stats collector must not be null";
    }

    std::unique_lock writeLock(mRwMutex);
    if (mLastSystemWideIoMonitorTime == 0) {
        /*
         * Do not record the first disk stats as it reflects the aggregated disks stats since the
         * system boot up and is not in sync with the polling period. This will lead to spurious
         * I/O overuse alerting.
         */
        mLastSystemWideIoMonitorTime = time;
        return {};
    }
    const auto diskStats = procDiskStatsCollector.promote()->deltaSystemWideDiskStats();
    mSystemWideWrittenBytes.push_back(
            {.pollDurationInSecs = difftime(time, mLastSystemWideIoMonitorTime),
             .bytesInKib = diskStats.numKibWritten});
    for (const auto& threshold : mIoOveruseConfigs->systemWideAlertThresholds()) {
        int64_t accountedWrittenKib = 0;
        double accountedDurationInSecs = 0;
        size_t accountedPolls = 0;
        for (auto rit = mSystemWideWrittenBytes.rbegin(); rit != mSystemWideWrittenBytes.rend();
             ++rit) {
            accountedWrittenKib += rit->bytesInKib;
            accountedDurationInSecs += rit->pollDurationInSecs;
            ++accountedPolls;
            if (accountedDurationInSecs >= threshold.durationInSeconds) {
                break;
            }
        }
        // Heuristic to handle spurious alerting when the buffer is partially filled.
        if (const size_t bufferSize = mSystemWideWrittenBytes.size();
            accountedPolls == bufferSize && bufferSize < mPeriodicMonitorBufferSize + 1 &&
            threshold.durationInSeconds > accountedDurationInSecs) {
            continue;
        }
        const double thresholdKbps = threshold.writtenBytesPerSecond / 1024.0;
        if (const auto kbps = accountedWrittenKib / accountedDurationInSecs;
            kbps >= thresholdKbps) {
            alertHandler();
            break;
        }
    }
    if (mSystemWideWrittenBytes.size() > mPeriodicMonitorBufferSize) {
        mSystemWideWrittenBytes.erase(mSystemWideWrittenBytes.begin());  // Erase the oldest entry.
    }
    mLastSystemWideIoMonitorTime = time;
    return {};
}

Result<void> IoOveruseMonitor::onDump([[maybe_unused]] int fd) const {
    // TODO(b/183436216): Dump the list of killed/disabled packages. Dump the list of packages that
    //  exceed xx% of their threshold.
    return {};
}

Result<void> IoOveruseMonitor::onDumpProto(
        [[maybe_unused]] const CollectionIntervals& collectionIntervals,
        [[maybe_unused]] ProtoOutputStream& outProto) const {
    // TODO(b/296123577): Dump the list of killed/disabled packages in proto format.
    return {};
}

bool IoOveruseMonitor::dumpHelpText(int fd) const {
    return WriteStringToFd(StringPrintf(kHelpText, name().c_str(), kResetResourceOveruseStatsFlag),
                           fd);
}

void IoOveruseMonitor::requestTodayIoUsageStatsLocked() {
    if (const auto status = mWatchdogServiceHelper->requestTodayIoUsageStats(); !status.isOk()) {
        // Request made only after CarWatchdogService connection is established. Logging the error
        // is enough in this case.
        ALOGE("Failed to request today I/O usage stats collected during previous boot: %s",
              status.getMessage());
        return;
    }
    if (DEBUG) {
        ALOGD("Requested today's I/O usage stats collected during previous boot.");
    }
}

Result<void> IoOveruseMonitor::onTodayIoUsageStatsFetched(
        const std::vector<UserPackageIoUsageStats>& userPackageIoUsageStats) {
    std::unique_lock writeLock(mRwMutex);
    if (mDidReadTodayPrevBootStats) {
        return {};
    }
    for (const auto& statsEntry : userPackageIoUsageStats) {
        std::string uniqueId = uniquePackageIdStr(statsEntry.packageName,
                                                  static_cast<userid_t>(statsEntry.userId));
        if (auto it = mUserPackageDailyIoUsageById.find(uniqueId);
            it != mUserPackageDailyIoUsageById.end()) {
            it->second += statsEntry.ioUsageStats;
            continue;
        }
        mPrevBootIoUsageStatsById.insert(std::pair(uniqueId, statsEntry.ioUsageStats));
    }
    mDidReadTodayPrevBootStats = true;
    return {};
}

void IoOveruseMonitor::notifyNativePackagesLocked(
        const std::unordered_map<uid_t, IoOveruseStats>& statsByUid) {
    for (const auto& [uid, ioOveruseStats] : statsByUid) {
        IResourceOveruseListener* listener;
        if (const auto it = mOveruseListenersByUid.find(uid); it == mOveruseListenersByUid.end()) {
            continue;
        } else {
            listener = it->second.get();
        }
        aidl::android::automotive::watchdog::ResourceOveruseStats stats;
        stats.set<aidl::android::automotive::watchdog::ResourceOveruseStats::ioOveruseStats>(
                ioOveruseStats);
        listener->onOveruse(stats);
    }
    if (DEBUG) {
        ALOGD("Notified native packages on I/O overuse");
    }
}

Result<void> IoOveruseMonitor::updateResourceOveruseConfigurations(
        const std::vector<ResourceOveruseConfiguration>& configs) {
    std::unique_lock writeLock(mRwMutex);
    if (!isInitializedLocked()) {
        return Error(EX_ILLEGAL_STATE) << name() << " is not initialized";
    }
    if (const auto result = mIoOveruseConfigs->update(configs); !result.ok()) {
        return result;
    }
    std::thread writeToDiskThread([&]() {
        if (set_sched_policy(0, SP_BACKGROUND) != 0) {
            ALOGW("Failed to set background scheduling priority for writing resource overuse "
                  "configs to disk");
        }
        if (int result = pthread_setname_np(pthread_self(), "ResOveruseCfgWr"); result != 0) {
            ALOGE("Failed to set thread name to 'ResOveruseCfgWr'");
        }
        std::unique_lock writeLock(mRwMutex);
        if (mIoOveruseConfigs == nullptr) {
            ALOGE("IoOveruseConfigs instance is null");
            return;
        }
        if (const auto result = mIoOveruseConfigs->writeToDisk(); !result.ok()) {
            ALOGE("Failed to write resource overuse configs to disk: %s",
                  result.error().message().c_str());
        }
    });

    writeToDiskThread.detach();
    return {};
}

Result<void> IoOveruseMonitor::getResourceOveruseConfigurations(
        std::vector<ResourceOveruseConfiguration>* configs) const {
    std::shared_lock readLock(mRwMutex);
    if (!isInitializedLocked()) {
        return Error(EX_ILLEGAL_STATE) << name() << " is not initialized";
    }
    mIoOveruseConfigs->get(configs);
    return {};
}

Result<void> IoOveruseMonitor::addIoOveruseListener(
        const std::shared_ptr<IResourceOveruseListener>& listener) {
    if (listener == nullptr) {
        return Error(EX_ILLEGAL_ARGUMENT) << "Must provide non-null listener";
    }
    auto binder = listener->asBinder();
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    {
        std::unique_lock writeLock(mRwMutex);
        if (!isInitializedLocked()) {
            // mBinderDeathRecipient is initialized inside init.
            return Error(EX_ILLEGAL_STATE) << "Service is not initialized";
        }
        if (findListenerAndProcessLocked(reinterpret_cast<uintptr_t>(binder.get()), nullptr)) {
            ALOGW("Failed to register the I/O overuse listener (pid: %d, uid: %d) as it is already "
                  "registered",
                  callingPid, callingUid);
            return {};
        }
        mOveruseListenersByUid[callingUid] = listener;
    }
    AIBinder* aiBinder = binder.get();
    auto status = mDeathRegistrationWrapper->linkToDeath(aiBinder, mBinderDeathRecipient.get(),
                                                         static_cast<void*>(aiBinder));
    if (!status.isOk()) {
        std::unique_lock writeLock(mRwMutex);
        if (const auto& it = mOveruseListenersByUid.find(callingUid);
            it != mOveruseListenersByUid.end() && it->second->asBinder() == binder) {
            mOveruseListenersByUid.erase(it);
        }
        return Error(EX_ILLEGAL_STATE) << "Failed to add I/O overuse listener: (pid " << callingPid
                                       << ", uid: " << callingUid << ") is dead";
    }
    if (DEBUG) {
        ALOGD("Added I/O overuse listener for uid: %d", callingUid);
    }
    return {};
}

Result<void> IoOveruseMonitor::removeIoOveruseListener(
        const std::shared_ptr<IResourceOveruseListener>& listener) {
    if (listener == nullptr) {
        return Error(EX_ILLEGAL_ARGUMENT) << "Must provide non-null listener";
    }
    std::unique_lock writeLock(mRwMutex);
    if (!isInitializedLocked()) {
        // mBinderDeathRecipient is initialized inside init.
        return Error(EX_ILLEGAL_STATE) << "Service is not initialized";
    }
    const auto processor = [&](ListenersByUidMap& listeners, ListenersByUidMap::const_iterator it) {
        AIBinder* aiBinder = it->second->asBinder().get();
        mDeathRegistrationWrapper->unlinkToDeath(aiBinder, mBinderDeathRecipient.get(),
                                                 static_cast<void*>(aiBinder));
        listeners.erase(it);
    };
    if (!findListenerAndProcessLocked(reinterpret_cast<uintptr_t>(listener->asBinder().get()),
                                      processor)) {
        return Error(EX_ILLEGAL_ARGUMENT) << "Listener is not previously registered";
    }
    if (DEBUG) {
        ALOGD("Removed I/O overuse listener for uid: %d", IPCThreadState::self()->getCallingUid());
    }
    return {};
}

Result<void> IoOveruseMonitor::getIoOveruseStats(IoOveruseStats* ioOveruseStats) const {
    if (!isInitialized()) {
        return Error(EX_ILLEGAL_STATE) << "I/O overuse monitor is not initialized";
    }
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    const auto packageInfosByUid = mPackageInfoResolver->getPackageInfosForUids({callingUid});
    const PackageInfo* packageInfo;
    if (const auto it = packageInfosByUid.find(callingUid); it == packageInfosByUid.end()) {
        return Error(EX_ILLEGAL_ARGUMENT)
                << "Package information not available for calling UID(" << callingUid << ")";
    } else {
        packageInfo = &it->second;
    }
    std::shared_lock readLock(mRwMutex);
    const UserPackageIoUsage* dailyIoUsage;
    if (const auto it = mUserPackageDailyIoUsageById.find(
                uniquePackageIdStr(packageInfo->packageIdentifier));
        it == mUserPackageDailyIoUsageById.end()) {
        return Error(EX_ILLEGAL_ARGUMENT)
                << "Calling UID " << callingUid << " doesn't have I/O overuse stats";
    } else {
        dailyIoUsage = &it->second;
    }
    ioOveruseStats->killableOnOveruse = mIoOveruseConfigs->isSafeToKill(*packageInfo);
    const auto thresholdBytes = mIoOveruseConfigs->fetchThreshold(*packageInfo);
    ioOveruseStats->remainingWriteBytes =
            diff(thresholdBytes,
                 diff(dailyIoUsage->writtenBytes, dailyIoUsage->forgivenWriteBytes));
    ioOveruseStats->totalOveruses = dailyIoUsage->totalOveruses;
    ioOveruseStats->writtenBytes = dailyIoUsage->writtenBytes;
    const auto [startTime, durationInSeconds] =
            calculateStartAndDuration(mLastUserPackageIoMonitorTime);
    ioOveruseStats->startTime = startTime;
    ioOveruseStats->durationInSeconds = durationInSeconds;
    if (DEBUG) {
        ALOGD("Returning I/O overuse stats for uid: %d", callingUid);
    }
    return {};
}

Result<void> IoOveruseMonitor::resetIoOveruseStats(const std::vector<std::string>& packageNames) {
    if (const auto status = mWatchdogServiceHelper->resetResourceOveruseStats(packageNames);
        !status.isOk()) {
        return Error() << "Failed to reset stats in watchdog service: " << status.getDescription();
    }
    std::unordered_set<std::string> uniquePackageNames;
    std::copy(packageNames.begin(), packageNames.end(),
              std::inserter(uniquePackageNames, uniquePackageNames.end()));
    std::unique_lock writeLock(mRwMutex);
    for (auto& [key, usage] : mUserPackageDailyIoUsageById) {
        if (uniquePackageNames.find(usage.packageInfo.packageIdentifier.name) !=
            uniquePackageNames.end()) {
            usage.resetStats();
        }
    }
    return {};
}

void IoOveruseMonitor::removeStatsForUser(userid_t userId) {
    std::unique_lock writeLock(mRwMutex);
    for (auto it = mUserPackageDailyIoUsageById.begin();
         it != mUserPackageDailyIoUsageById.end();) {
        if (multiuser_get_user_id(it->second.packageInfo.packageIdentifier.uid) == userId) {
            it = mUserPackageDailyIoUsageById.erase(it);
        } else {
            ++it;
        }
    }
    // |mPrevBootIoUsageStatsById| keys are constructed using |uniquePackageIdStr| method. Thus, the
    // key suffix would contain the userId. The value in this map is |IoUsageStats|, which doesn't
    // contain the userId, so this is the only way to delete cached previous boot stats for
    // the removed user.
    std::string keySuffix = StringPrintf(":%" PRId32, userId);
    for (auto it = mPrevBootIoUsageStatsById.begin(); it != mPrevBootIoUsageStatsById.end();) {
        if (EndsWith(it->first, keySuffix)) {
            it = mPrevBootIoUsageStatsById.erase(it);
        } else {
            ++it;
        }
    }
    for (auto it = mLatestIoOveruseStats.begin(); it != mLatestIoOveruseStats.end();) {
        if (multiuser_get_user_id(it->uid) == userId) {
            it = mLatestIoOveruseStats.erase(it);
        } else {
            ++it;
        }
    }
}

void IoOveruseMonitor::handleBinderDeath(void* cookie) {
    uintptr_t cookieId = reinterpret_cast<uintptr_t>(cookie);

    std::unique_lock writeLock(mRwMutex);
    findListenerAndProcessLocked(cookieId,
                                 [&](ListenersByUidMap& listeners,
                                     ListenersByUidMap::const_iterator it) {
                                     ALOGW("Resource overuse notification handler died for uid(%d)",
                                           it->first);
                                     listeners.erase(it);
                                 });
}

bool IoOveruseMonitor::findListenerAndProcessLocked(uintptr_t binderPtrId,
                                                    const Processor& processor) {
    for (auto it = mOveruseListenersByUid.begin(); it != mOveruseListenersByUid.end(); ++it) {
        uintptr_t curBinderPtrId = reinterpret_cast<uintptr_t>(it->second->asBinder().get());
        if (curBinderPtrId != binderPtrId) {
            continue;
        }
        if (processor != nullptr) {
            processor(mOveruseListenersByUid, it);
        }
        return true;
    }
    return false;
}

IoOveruseMonitor::UserPackageIoUsage::UserPackageIoUsage(const PackageInfo& pkgInfo,
                                                         const UidIoStats& uidIoStats,
                                                         const bool isGarageModeActive) {
    packageInfo = pkgInfo;
    if (isGarageModeActive) {
        writtenBytes.garageModeBytes = uidIoStats.sumWriteBytes();
    } else {
        writtenBytes.foregroundBytes = uidIoStats.metrics[WRITE_BYTES][FOREGROUND];
        writtenBytes.backgroundBytes = uidIoStats.metrics[WRITE_BYTES][BACKGROUND];
    }
}

IoOveruseMonitor::UserPackageIoUsage& IoOveruseMonitor::UserPackageIoUsage::operator+=(
        const UserPackageIoUsage& r) {
    if (id() == r.id()) {
        packageInfo = r.packageInfo;
    }
    writtenBytes = sum(writtenBytes, r.writtenBytes);
    return *this;
}

IoOveruseMonitor::UserPackageIoUsage& IoOveruseMonitor::UserPackageIoUsage::operator+=(
        const IoUsageStats& ioUsageStats) {
    writtenBytes = sum(writtenBytes, ioUsageStats.writtenBytes);
    forgivenWriteBytes = sum(forgivenWriteBytes, ioUsageStats.forgivenWriteBytes);
    totalOveruses += ioUsageStats.totalOveruses;
    return *this;
}

const std::string IoOveruseMonitor::UserPackageIoUsage::id() const {
    return uniquePackageIdStr(packageInfo.packageIdentifier);
}

void IoOveruseMonitor::UserPackageIoUsage::resetStats() {
    writtenBytes = {};
    forgivenWriteBytes = {};
    totalOveruses = 0;
    isPackageWarned = false;
    lastSyncedWrittenBytes = 0;
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
