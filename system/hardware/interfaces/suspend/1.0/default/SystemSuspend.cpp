/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define ATRACE_TAG ATRACE_TAG_POWER
#define ATRACE_TRACK_BACKOFF "suspend_backoff"

#include "SystemSuspend.h"

#include <aidl/android/system/suspend/ISystemSuspend.h>
#include <aidl/android/system/suspend/IWakeLock.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <chrono>
#include <cstdlib>
#include <string>
#include <thread>
using namespace std::chrono_literals;

using ::aidl::android::system::suspend::ISystemSuspend;
using ::aidl::android::system::suspend::IWakeLock;
using ::aidl::android::system::suspend::WakeLockType;
using ::android::base::CachedProperty;
using ::android::base::Error;
using ::android::base::ReadFdToString;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::std::string;

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

struct SuspendTime {
    std::chrono::nanoseconds suspendOverhead;
    std::chrono::nanoseconds suspendTime;
};

static const char kSleepState[] = "mem";
// TODO(b/128923994): we only need /sys/power/wake_[un]lock to export debugging info via
// /sys/kernel/debug/wakeup_sources.
static constexpr char kSysPowerWakeLock[] = "/sys/power/wake_lock";
static constexpr char kSysPowerWakeUnlock[] = "/sys/power/wake_unlock";
static constexpr char kUnknownWakeup[] = "unknown";
// This is used to disable autosuspend when zygote is restarted
// it allows the system to make progress before autosuspend is kicked
// NOTE: If the name of this wakelock is changed then also update the name
// in rootdir/init.zygote32.rc, rootdir/init.zygote64.rc, and
// rootdir/init.zygote64_32.rc
static constexpr char kZygoteKernelWakelock[] = "zygote_kwl";

// This function assumes that data in fd is small enough that it can be read in one go.
// We use this function instead of the ones available in libbase because it doesn't block
// indefinitely when reading from socket streams which are used for testing.
string readFd(int fd) {
    char buf[BUFSIZ];
    ssize_t n = TEMP_FAILURE_RETRY(read(fd, &buf[0], sizeof(buf)));
    if (n < 0) return "";
    return string{buf, static_cast<size_t>(n)};
}

static std::vector<std::string> readWakeupReasons(int fd) {
    std::vector<std::string> wakeupReasons;
    std::string reasonlines;

    lseek(fd, 0, SEEK_SET);

    bool ret = ReadFdToString(fd, &reasonlines);
    if (!ret)
        PLOG(ERROR) << "failed to read wakeup reasons";

    // Return unknown wakeup reason if reading wakeup reasons fails or is empty
    if (!ret || reasonlines.empty())
        return {kUnknownWakeup};

    std::stringstream ss(reasonlines);
    for (std::string reasonline; std::getline(ss, reasonline);) {
        reasonline = ::android::base::Trim(reasonline);

        // Only include non-empty reason lines
        if (!reasonline.empty()) {
            wakeupReasons.push_back(reasonline);
        }
    }

    // Empty wakeup reason found. Record as unknown wakeup
    if (wakeupReasons.empty()) {
        wakeupReasons.push_back(kUnknownWakeup);
    }

    return wakeupReasons;
}

// reads the suspend overhead and suspend time
// Returns 0s if reading the sysfs node fails (unlikely)
static struct SuspendTime readSuspendTime(int fd) {
    std::string content;

    lseek(fd, 0, SEEK_SET);
    if (!ReadFdToString(fd, &content)) {
        LOG(ERROR) << "failed to read suspend time";
        return {0ns, 0ns};
    }

    double suspendOverhead, suspendTime;
    std::stringstream ss(content);
    if (!(ss >> suspendOverhead) || !(ss >> suspendTime)) {
        LOG(ERROR) << "failed to parse suspend time " << content;
        return {0ns, 0ns};
    }

    return {std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::duration<double>(suspendOverhead)),
            std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::duration<double>(suspendTime))};
}

SystemSuspend::SystemSuspend(unique_fd wakeupCountFd, unique_fd stateFd, unique_fd suspendStatsFd,
                             size_t maxStatsEntries, unique_fd kernelWakelockStatsFd,
                             unique_fd wakeupReasonsFd, unique_fd suspendTimeFd,
                             const SleepTimeConfig& sleepTimeConfig,
                             const sp<SuspendControlService>& controlService,
                             const sp<SuspendControlServiceInternal>& controlServiceInternal,
                             bool useSuspendCounter)
    : mSuspendCounter(0),
      mWakeupCountFd(std::move(wakeupCountFd)),
      mStateFd(std::move(stateFd)),
      mSuspendStatsFd(std::move(suspendStatsFd)),
      mSuspendTimeFd(std::move(suspendTimeFd)),
      kSleepTimeConfig(sleepTimeConfig),
      mSleepTime(sleepTimeConfig.baseSleepTime),
      mNumConsecutiveBadSuspends(0),
      mControlService(controlService),
      mControlServiceInternal(controlServiceInternal),
      mStatsList(maxStatsEntries, std::move(kernelWakelockStatsFd)),
      mWakeupList(maxStatsEntries),
      mUseSuspendCounter(useSuspendCounter),
      mWakeLockFd(-1),
      mWakeUnlockFd(-1),
      mWakeupReasonsFd(std::move(wakeupReasonsFd)) {
    mControlServiceInternal->setSuspendService(this);

    if (!mUseSuspendCounter) {
        mWakeLockFd.reset(TEMP_FAILURE_RETRY(open(kSysPowerWakeLock, O_CLOEXEC | O_RDWR)));
        if (mWakeLockFd < 0) {
            PLOG(ERROR) << "error opening " << kSysPowerWakeLock;
        }
    }

    mWakeUnlockFd.reset(TEMP_FAILURE_RETRY(open(kSysPowerWakeUnlock, O_CLOEXEC | O_RDWR)));
    if (mWakeUnlockFd < 0) {
        PLOG(ERROR) << "error opening " << kSysPowerWakeUnlock;
    }
}

bool SystemSuspend::enableAutosuspend(const sp<IBinder>& token) {
    auto tokensLock = std::lock_guard(mAutosuspendClientTokensLock);
    auto autosuspendLock = std::lock_guard(mAutosuspendLock);

    // Disable zygote kernel wakelock, since explicitly attempting to
    // enable autosuspend. This should be done even if autosuspend is
    // already enabled, since it could be the case that the framework
    // is restarting and connecting to the existing suspend service.
    if (!WriteStringToFd(kZygoteKernelWakelock, mWakeUnlockFd)) {
        PLOG(ERROR) << "error writing " << kZygoteKernelWakelock << " to " << kSysPowerWakeUnlock;
    }

    bool hasToken = std::find(mAutosuspendClientTokens.begin(), mAutosuspendClientTokens.end(),
                              token) != mAutosuspendClientTokens.end();

    if (!hasToken) {
        mAutosuspendClientTokens.push_back(token);
    }

    if (mAutosuspendEnabled) {
        LOG(ERROR) << "Autosuspend already started.";
        return false;
    }

    mAutosuspendEnabled = true;
    initAutosuspendLocked();
    return true;
}

void SystemSuspend::disableAutosuspendLocked() {
    mAutosuspendClientTokens.clear();
    if (mAutosuspendEnabled) {
        mAutosuspendEnabled = false;
        mAutosuspendCondVar.notify_all();
        LOG(INFO) << "automatic system suspend disabled";
    }
}

void SystemSuspend::disableAutosuspend() {
    auto tokensLock = std::lock_guard(mAutosuspendClientTokensLock);
    auto autosuspendLock = std::lock_guard(mAutosuspendLock);
    disableAutosuspendLocked();
}

void SystemSuspend::checkAutosuspendClientsLivenessLocked() {
    // Ping autosuspend client tokens, remove any dead tokens from the list.
    // mAutosuspendLock must not be held when calling this, as that could lead to a deadlock
    // if pingBinder() can't be processed by system_server because it's Binder thread pool is
    // exhausted and blocked on acquire/release wakelock calls.
    mAutosuspendClientTokens.erase(
        std::remove_if(mAutosuspendClientTokens.begin(), mAutosuspendClientTokens.end(),
                       [](const sp<IBinder>& token) { return token->pingBinder() != OK; }),
        mAutosuspendClientTokens.end());
}

bool SystemSuspend::hasAliveAutosuspendTokenLocked() {
    return !mAutosuspendClientTokens.empty();
}

SystemSuspend::~SystemSuspend(void) {
    auto tokensLock = std::lock_guard(mAutosuspendClientTokensLock);
    auto autosuspendLock = std::unique_lock(mAutosuspendLock);

    // signal autosuspend thread to shut down
    disableAutosuspendLocked();

    // wait for autosuspend thread to exit
    mAutosuspendCondVar.wait_for(autosuspendLock, 100ms, [this]() REQUIRES(mAutosuspendLock) {
        return !mAutosuspendThreadCreated;
    });
}

bool SystemSuspend::forceSuspend() {
#ifndef FUZZ_MODE_SUSPEND_SERVICE
    //  We are forcing the system to suspend. This particular call ignores all
    //  existing wakelocks (full or partial). It does not cancel the wakelocks
    //  or reset mSuspendCounter, it just ignores them.  When the system
    //  returns from suspend, the wakelocks and SuspendCounter will not have
    //  changed.
    auto autosuspendLock = std::unique_lock(mAutosuspendLock);
    bool success = WriteStringToFd(kSleepState, mStateFd);
    autosuspendLock.unlock();

    if (!success) {
        PLOG(VERBOSE) << "error writing to /sys/power/state for forceSuspend";
    }
    return success;
#else
    return false;
#endif  // !FUZZ_MODE_SUSPEND_SERVICE
}

void SystemSuspend::incSuspendCounter(const string& name) {
    auto l = std::lock_guard(mAutosuspendLock);
    if (mUseSuspendCounter) {
        mSuspendCounter++;
    } else {
        if (!WriteStringToFd(name, mWakeLockFd)) {
            PLOG(ERROR) << "error writing " << name << " to " << kSysPowerWakeLock;
        }
    }
}

void SystemSuspend::decSuspendCounter(const string& name) {
    auto l = std::lock_guard(mAutosuspendLock);
    if (mUseSuspendCounter) {
        if (--mSuspendCounter == 0) {
            mAutosuspendCondVar.notify_one();
        }
    } else {
        if (!WriteStringToFd(name, mWakeUnlockFd)) {
            PLOG(ERROR) << "error writing " << name << " to " << kSysPowerWakeUnlock;
        }
    }
}

unique_fd SystemSuspend::reopenFileUsingFd(const int fd, const int permission) {
    string filePath = android::base::StringPrintf("/proc/self/fd/%d", fd);

    unique_fd tempFd{TEMP_FAILURE_RETRY(open(filePath.c_str(), permission))};
    if (tempFd < 0) {
        PLOG(ERROR) << "SystemSuspend: Error opening file, using path: " << filePath;
        return unique_fd(-1);
    }
    return tempFd;
}

void SystemSuspend::initAutosuspendLocked() {
    if (mAutosuspendThreadCreated) {
        LOG(INFO) << "Autosuspend thread already started.";
        return;
    }

    std::thread autosuspendThread([this] {
        auto autosuspendLock = std::unique_lock(mAutosuspendLock);
        bool shouldSleep = true;

        while (true) {
            {
                base::ScopedLockAssertion autosuspendLocked(mAutosuspendLock);

                if (!mAutosuspendEnabled) {
                    mAutosuspendThreadCreated = false;
                    return;
                }
                // If we got here by a failed write to /sys/power/wakeup_count; don't sleep
                // since we didn't attempt to suspend on the last cycle of this loop.
                if (shouldSleep) {
                    mAutosuspendCondVar.wait_for(
                        autosuspendLock, mSleepTime,
                        [this]() REQUIRES(mAutosuspendLock) { return !mAutosuspendEnabled; });
                }

                if (!mAutosuspendEnabled) continue;
                autosuspendLock.unlock();
            }

            lseek(mWakeupCountFd, 0, SEEK_SET);
            string wakeupCount = readFd(mWakeupCountFd);

            {
                autosuspendLock.lock();
                base::ScopedLockAssertion autosuspendLocked(mAutosuspendLock);

                if (wakeupCount.empty()) {
                    PLOG(ERROR) << "error reading from /sys/power/wakeup_count";
                    continue;
                }

                shouldSleep = false;

                mAutosuspendCondVar.wait(autosuspendLock, [this]() REQUIRES(mAutosuspendLock) {
                    return mSuspendCounter == 0 || !mAutosuspendEnabled;
                });

                if (!mAutosuspendEnabled) continue;
                autosuspendLock.unlock();
            }

            bool success;
            {
                auto tokensLock = std::lock_guard(mAutosuspendClientTokensLock);
                checkAutosuspendClientsLivenessLocked();

                autosuspendLock.lock();
                base::ScopedLockAssertion autosuspendLocked(mAutosuspendLock);

                if (!hasAliveAutosuspendTokenLocked()) {
                    disableAutosuspendLocked();
                    continue;
                }

                // Check suspend counter hasn't increased while checking client liveness
                if (mSuspendCounter > 0) {
                    continue;
                }

                // The mutex is locked and *MUST* remain locked until we write to /sys/power/state.
                // Otherwise, a WakeLock might be acquired after we check mSuspendCounter and before
                // we write to /sys/power/state.

                if (!WriteStringToFd(wakeupCount, mWakeupCountFd)) {
                    PLOG(VERBOSE) << "error writing to /sys/power/wakeup_count";
                    continue;
                }
                success = WriteStringToFd(kSleepState, mStateFd);
                shouldSleep = true;

                autosuspendLock.unlock();
            }

            if (!success) {
                PLOG(VERBOSE) << "error writing to /sys/power/state";
            }

            struct SuspendTime suspendTime = readSuspendTime(mSuspendTimeFd);
            updateSleepTime(success, suspendTime);

            std::vector<std::string> wakeupReasons = readWakeupReasons(mWakeupReasonsFd);
            if (wakeupReasons == std::vector<std::string>({kUnknownWakeup})) {
                LOG(INFO) << "Unknown/empty wakeup reason. Re-opening wakeup_reason file.";

                mWakeupReasonsFd =
                    std::move(reopenFileUsingFd(mWakeupReasonsFd.get(), O_CLOEXEC | O_RDONLY));
            }
            mWakeupList.update(wakeupReasons);

            mControlService->notifyWakeup(success, wakeupReasons);

            logKernelWakeLockStats();

            // Take the lock before returning to the start of the loop
            autosuspendLock.lock();
        }
    });
    autosuspendThread.detach();
    mAutosuspendThreadCreated = true;
    LOG(INFO) << "automatic system suspend enabled";
}

void SystemSuspend::logKernelWakeLockStats() {
    [[clang::no_destroy]] static CachedProperty logStatsProp("suspend.debug.wakestats_log.enabled");
    std::string prop(logStatsProp.Get(NULL));

    if ((prop.compare("true") != 0) && (prop.compare("1") != 0)) return;

    std::stringstream klStats;
    klStats << "Kernel wakesource stats: ";
    std::vector<WakeLockInfo> wlStats;
    mStatsList.getWakeLockStats(&wlStats);

    for (const WakeLockInfo& wake : wlStats) {
        if ((wake.isKernelWakelock) && (wake.activeCount > 0)) {
            klStats << wake.name << "," << wake.totalTime << "," << wake.activeCount << ";";
        }
    }
    LOG(INFO) << klStats.rdbuf();
}

/**
 * Updates sleep time depending on the result of suspend attempt.
 * Time (in milliseconds) between suspend attempts is described the formula
 * t[n] = { B, 0 < n <= N
 *        { min(B * (S**(n - N)), M), n > N
 * where:
 *   n is the number of consecutive bad suspend attempts,
 *   B = kBaseSleepTime,
 *   N = kSuspendBackoffThreshold,
 *   S = kSleepTimeScaleFactor,
 *   M = kMaxSleepTime
 *
 * kFailedSuspendBackoffEnabled determines whether a failed suspend is counted as a bad suspend
 *
 * kShortSuspendBackoffEnabled determines whether a suspend whose duration
 * t < kShortSuspendThreshold is counted as a bad suspend
 */
void SystemSuspend::updateSleepTime(bool success, const struct SuspendTime& suspendTime) {
    std::scoped_lock lock(mSuspendInfoLock);
    mSuspendInfo.suspendAttemptCount++;
    mSuspendInfo.sleepTimeMillis +=
        std::chrono::round<std::chrono::milliseconds>(mSleepTime).count();

    bool shortSuspend = success && (suspendTime.suspendTime > 0ns) &&
                        (suspendTime.suspendTime < kSleepTimeConfig.shortSuspendThreshold);

    bool badSuspend = (kSleepTimeConfig.failedSuspendBackoffEnabled && !success) ||
                      (kSleepTimeConfig.shortSuspendBackoffEnabled && shortSuspend);

    auto suspendTimeMillis =
        std::chrono::round<std::chrono::milliseconds>(suspendTime.suspendTime).count();
    auto suspendOverheadMillis =
        std::chrono::round<std::chrono::milliseconds>(suspendTime.suspendOverhead).count();

    if (success) {
        mSuspendInfo.suspendOverheadTimeMillis += suspendOverheadMillis;
        mSuspendInfo.suspendTimeMillis += suspendTimeMillis;
    } else {
        mSuspendInfo.failedSuspendCount++;
        mSuspendInfo.failedSuspendOverheadTimeMillis += suspendOverheadMillis;
    }

    if (shortSuspend) {
        mSuspendInfo.shortSuspendCount++;
        mSuspendInfo.shortSuspendTimeMillis += suspendTimeMillis;
    }

    if (!badSuspend) {
        ATRACE_INSTANT_FOR_TRACK(ATRACE_TRACK_BACKOFF, "good");
        mNumConsecutiveBadSuspends = 0;
        mSleepTime = kSleepTimeConfig.baseSleepTime;
        return;
    }

    const char* backoffDecision = "defer";

    // Suspend attempt was bad (failed or short suspend)
    if (mNumConsecutiveBadSuspends >= kSleepTimeConfig.backoffThreshold) {
        if (mNumConsecutiveBadSuspends == kSleepTimeConfig.backoffThreshold) {
            mSuspendInfo.newBackoffCount++;
            backoffDecision = "new";
        } else {
            mSuspendInfo.backoffContinueCount++;
            backoffDecision = "continue";
        }

        mSleepTime = std::min(std::chrono::round<std::chrono::milliseconds>(
                                  mSleepTime * kSleepTimeConfig.sleepTimeScaleFactor),
                              kSleepTimeConfig.maxSleepTime);
    }

    mNumConsecutiveBadSuspends++;

    std::string msg =
        base::StringPrintf("bad %s %s %d %lld", backoffDecision, shortSuspend ? "short" : "failed",
                           mNumConsecutiveBadSuspends, mSleepTime.count());
    ATRACE_INSTANT_FOR_TRACK(ATRACE_TRACK_BACKOFF, msg.c_str());
}

void SystemSuspend::updateWakeLockStatOnAcquire(const std::string& name, int pid) {
    // Update the stats first so that the stat time is right after
    // suspend counter being incremented.
    mStatsList.updateOnAcquire(name, pid);
    mControlService->notifyWakelock(name, true);
}

void SystemSuspend::updateWakeLockStatOnRelease(const std::string& name, int pid) {
    // Update the stats first so that the stat time is right after
    // suspend counter being decremented.
    mStatsList.updateOnRelease(name, pid);
    mControlService->notifyWakelock(name, false);
}

const WakeLockEntryList& SystemSuspend::getStatsList() const {
    return mStatsList;
}

void SystemSuspend::updateStatsNow() {
    mStatsList.updateNow();
}

void SystemSuspend::getSuspendInfo(SuspendInfo* info) {
    std::scoped_lock lock(mSuspendInfoLock);

    *info = mSuspendInfo;
}

const WakeupList& SystemSuspend::getWakeupList() const {
    return mWakeupList;
}

/**
 * Returns suspend stats.
 */
Result<SuspendStats> SystemSuspend::getSuspendStats() {
    SuspendStats stats;
    std::unique_ptr<DIR, decltype(&closedir)> dp(fdopendir(dup(mSuspendStatsFd.get())), &closedir);
    if (!dp) {
        return stats;
    }

    // rewinddir, else subsequent calls will not get any suspend_stats
    rewinddir(dp.get());

    struct dirent* de;

    // Grab a wakelock before reading suspend stats, to ensure a consistent snapshot.
    const std::string suspendInstance = std::string() + ISystemSuspend::descriptor + "/default";
    auto suspendService = ISystemSuspend::fromBinder(
        ndk::SpAIBinder(AServiceManager_checkService(suspendInstance.c_str())));

    std::shared_ptr<IWakeLock> wl = nullptr;
    if (suspendService) {
        auto status =
            suspendService->acquireWakeLock(WakeLockType::PARTIAL, "suspend_stats_lock", &wl);
    }

    while ((de = readdir(dp.get()))) {
        std::string statName(de->d_name);
        if ((statName == ".") || (statName == "..")) {
            continue;
        }

        unique_fd statFd{TEMP_FAILURE_RETRY(
            openat(mSuspendStatsFd.get(), statName.c_str(), O_CLOEXEC | O_RDONLY))};
        if (statFd < 0) {
            return Error() << "Failed to open " << statName;
        }

        std::string valStr;
        if (!ReadFdToString(statFd.get(), &valStr)) {
            return Error() << "Failed to read " << statName;
        }

        // Trim newline
        valStr.erase(std::remove(valStr.begin(), valStr.end(), '\n'), valStr.end());

        if (statName == "last_failed_dev") {
            stats.lastFailedDev = valStr;
        } else if (statName == "last_failed_step") {
            stats.lastFailedStep = valStr;
        } else {
            int statVal = std::stoi(valStr);
            if (statName == "success") {
                stats.success = statVal;
            } else if (statName == "fail") {
                stats.fail = statVal;
            } else if (statName == "failed_freeze") {
                stats.failedFreeze = statVal;
            } else if (statName == "failed_prepare") {
                stats.failedPrepare = statVal;
            } else if (statName == "failed_suspend") {
                stats.failedSuspend = statVal;
            } else if (statName == "failed_suspend_late") {
                stats.failedSuspendLate = statVal;
            } else if (statName == "failed_suspend_noirq") {
                stats.failedSuspendNoirq = statVal;
            } else if (statName == "failed_resume") {
                stats.failedResume = statVal;
            } else if (statName == "failed_resume_early") {
                stats.failedResumeEarly = statVal;
            } else if (statName == "failed_resume_noirq") {
                stats.failedResumeNoirq = statVal;
            } else if (statName == "last_failed_errno") {
                stats.lastFailedErrno = statVal;
            }
        }
    }

    return stats;
}

std::chrono::milliseconds SystemSuspend::getSleepTime() const {
    return mSleepTime;
}

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android
