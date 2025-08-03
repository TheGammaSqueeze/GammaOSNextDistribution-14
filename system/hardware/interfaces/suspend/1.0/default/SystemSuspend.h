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

#ifndef ANDROID_SYSTEM_SYSTEM_SUSPEND_V1_0_H
#define ANDROID_SYSTEM_SYSTEM_SUSPEND_V1_0_H

#include <android-base/result.h>
#include <android-base/thread_annotations.h>
#include <android-base/unique_fd.h>
#include <android/system/suspend/internal/SuspendInfo.h>
#include <utils/RefBase.h>

#include <atomic>
#include <condition_variable>
#include <mutex>
#include <string>

#include "SuspendControlService.h"
#include "WakeLockEntryList.h"
#include "WakeupList.h"

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

using ::android::base::Result;
using ::android::base::unique_fd;
using ::android::system::suspend::internal::SuspendInfo;

using namespace std::chrono_literals;

class SystemSuspend;

struct SuspendStats {
    int success = 0;
    int fail = 0;
    int failedFreeze = 0;
    int failedPrepare = 0;
    int failedSuspend = 0;
    int failedSuspendLate = 0;
    int failedSuspendNoirq = 0;
    int failedResume = 0;
    int failedResumeEarly = 0;
    int failedResumeNoirq = 0;
    std::string lastFailedDev;
    int lastFailedErrno = 0;
    std::string lastFailedStep;
};

struct SleepTimeConfig {
    std::chrono::milliseconds baseSleepTime;
    std::chrono::milliseconds maxSleepTime;
    double sleepTimeScaleFactor;
    uint32_t backoffThreshold;
    std::chrono::milliseconds shortSuspendThreshold;
    bool failedSuspendBackoffEnabled;
    bool shortSuspendBackoffEnabled;
};

std::string readFd(int fd);

class SystemSuspend : public RefBase {
   public:
    SystemSuspend(unique_fd wakeupCountFd, unique_fd stateFd, unique_fd suspendStatsFd,
                  size_t maxStatsEntries, unique_fd kernelWakelockStatsFd,
                  unique_fd wakeupReasonsFd, unique_fd suspendTimeFd,
                  const SleepTimeConfig& sleepTimeConfig,
                  const sp<SuspendControlService>& controlService,
                  const sp<SuspendControlServiceInternal>& controlServiceInternal,
                  bool useSuspendCounter = true);
    void incSuspendCounter(const std::string& name);
    void decSuspendCounter(const std::string& name);
    bool enableAutosuspend(const sp<IBinder>& token);
    void disableAutosuspend();
    bool forceSuspend();

    const WakeupList& getWakeupList() const;
    const WakeLockEntryList& getStatsList() const;
    void updateWakeLockStatOnAcquire(const std::string& name, int pid);
    void updateWakeLockStatOnRelease(const std::string& name, int pid);
    void updateStatsNow();
    Result<SuspendStats> getSuspendStats();
    void getSuspendInfo(SuspendInfo* info);
    std::chrono::milliseconds getSleepTime() const;
    unique_fd reopenFileUsingFd(const int fd, int permission);

   private:
    ~SystemSuspend(void) override;

    std::mutex mAutosuspendClientTokensLock;
    std::mutex mAutosuspendLock ACQUIRED_AFTER(mAutosuspendClientTokensLock);
    std::mutex mSuspendInfoLock;

    void initAutosuspendLocked()
        EXCLUSIVE_LOCKS_REQUIRED(mAutosuspendClientTokensLock, mAutosuspendLock);
    void disableAutosuspendLocked()
        EXCLUSIVE_LOCKS_REQUIRED(mAutosuspendClientTokensLock, mAutosuspendLock);
    void checkAutosuspendClientsLivenessLocked()
        EXCLUSIVE_LOCKS_REQUIRED(mAutosuspendClientTokensLock);
    bool hasAliveAutosuspendTokenLocked() EXCLUSIVE_LOCKS_REQUIRED(mAutosuspendClientTokensLock);
    void logKernelWakeLockStats();

    std::condition_variable mAutosuspendCondVar GUARDED_BY(mAutosuspendLock);
    uint32_t mSuspendCounter GUARDED_BY(mAutosuspendLock);

    std::vector<sp<IBinder>> mAutosuspendClientTokens GUARDED_BY(mAutosuspendClientTokensLock);
    std::atomic<bool> mAutosuspendEnabled GUARDED_BY(mAutosuspendLock){false};
    std::atomic<bool> mAutosuspendThreadCreated GUARDED_BY(mAutosuspendLock){false};

    unique_fd mWakeupCountFd;
    unique_fd mStateFd;

    unique_fd mSuspendStatsFd;
    unique_fd mSuspendTimeFd;

    SuspendInfo mSuspendInfo GUARDED_BY(mSuspendInfoLock);

    const SleepTimeConfig kSleepTimeConfig;

    // Amount of thread sleep time between consecutive iterations of the suspend loop
    std::chrono::milliseconds mSleepTime;
    int32_t mNumConsecutiveBadSuspends GUARDED_BY(mSuspendInfoLock);

    // Updates thread sleep time and suspend stats depending on the result of suspend attempt
    void updateSleepTime(bool success, const struct SuspendTime& suspendTime);

    sp<SuspendControlService> mControlService;
    sp<SuspendControlServiceInternal> mControlServiceInternal;

    WakeLockEntryList mStatsList;
    WakeupList mWakeupList;

    // If true, use mSuspendCounter to keep track of native wake locks. Otherwise, rely on
    // /sys/power/wake_lock interface to block suspend.
    // TODO(b/128923994): remove dependency on /sys/power/wake_lock interface.
    bool mUseSuspendCounter;
    unique_fd mWakeLockFd;
    unique_fd mWakeUnlockFd;
    unique_fd mWakeupReasonsFd;
};

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android

#endif  // ANDROID_SYSTEM_SYSTEM_SUSPEND_V1_0_H
