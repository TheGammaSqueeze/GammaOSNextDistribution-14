/*
 * Copyright 2021 The Android Open Source Project
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

#define LOG_TAG "powerhal-libperfmgr"
#define ATRACE_TAG (ATRACE_TAG_POWER | ATRACE_TAG_HAL)

#include "PowerSessionManager.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <log/log.h>
#include <perfmgr/HintManager.h>
#include <private/android_filesystem_config.h>
#include <processgroup/processgroup.h>
#include <sys/syscall.h>
#include <utils/Trace.h>

#include "AdpfTypes.h"

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

using ::android::base::StringPrintf;
using ::android::perfmgr::AdpfConfig;
using ::android::perfmgr::HintManager;

namespace {
/* there is no glibc or bionic wrapper */
struct sched_attr {
    __u32 size;
    __u32 sched_policy;
    __u64 sched_flags;
    __s32 sched_nice;
    __u32 sched_priority;
    __u64 sched_runtime;
    __u64 sched_deadline;
    __u64 sched_period;
    __u32 sched_util_min;
    __u32 sched_util_max;
};

static int set_uclamp_min(int tid, int min) {
    static constexpr int32_t kMinUclampValue = 0;
    static constexpr int32_t kMaxUclampValue = 1024;
    min = std::max(kMinUclampValue, min);
    min = std::min(min, kMaxUclampValue);

    sched_attr attr = {};
    attr.size = sizeof(attr);

    attr.sched_flags = (SCHED_FLAG_KEEP_ALL | SCHED_FLAG_UTIL_CLAMP_MIN);
    attr.sched_util_min = min;

    const int ret = syscall(__NR_sched_setattr, tid, attr, 0);
    if (ret) {
        ALOGW("sched_setattr failed for thread %d, err=%d", tid, errno);
        return errno;
    }
    return 0;
}
}  // namespace

void PowerSessionManager::updateHintMode(const std::string &mode, bool enabled) {
    if (enabled && mode.compare(0, 8, "REFRESH_") == 0) {
        if (mode.compare("REFRESH_120FPS") == 0) {
            mDisplayRefreshRate = 120;
        } else if (mode.compare("REFRESH_90FPS") == 0) {
            mDisplayRefreshRate = 90;
        } else if (mode.compare("REFRESH_60FPS") == 0) {
            mDisplayRefreshRate = 60;
        }
    }
    if (HintManager::GetInstance()->GetAdpfProfile()) {
        HintManager::GetInstance()->SetAdpfProfile(mode);
    }
}

void PowerSessionManager::updateHintBoost(const std::string &boost, int32_t durationMs) {
    ATRACE_CALL();
    ALOGV("PowerSessionManager::updateHintBoost: boost: %s, durationMs: %d", boost.c_str(),
          durationMs);
}

int PowerSessionManager::getDisplayRefreshRate() {
    return mDisplayRefreshRate;
}

void PowerSessionManager::addPowerSession(const std::string &idString,
                                          const std::shared_ptr<AppHintDesc> &sessionDescriptor,
                                          const std::vector<int32_t> &threadIds) {
    if (!sessionDescriptor) {
        ALOGE("sessionDescriptor is null. PowerSessionManager failed to add power session: %s",
              idString.c_str());
        return;
    }
    const auto timeNow = std::chrono::steady_clock::now();
    SessionValueEntry sve;
    sve.tgid = sessionDescriptor->tgid;
    sve.uid = sessionDescriptor->uid;
    sve.idString = idString;
    sve.isActive = sessionDescriptor->is_active;
    sve.isAppSession = sessionDescriptor->uid >= AID_APP_START;
    sve.lastUpdatedTime = timeNow;
    sve.votes = std::make_shared<Votes>();
    sve.votes->add(
            static_cast<std::underlying_type_t<AdpfHintType>>(AdpfHintType::ADPF_VOTE_DEFAULT),
            CpuVote(false, timeNow, sessionDescriptor->targetNs, kUclampMin, kUclampMax));

    bool addedRes = false;
    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        addedRes = mSessionTaskMap.add(sessionDescriptor->sessionId, sve, {});
    }
    if (!addedRes) {
        ALOGE("sessionTaskMap failed to add power session: %" PRId64, sessionDescriptor->sessionId);
    }

    setThreadsFromPowerSession(sessionDescriptor->sessionId, threadIds);
}

void PowerSessionManager::removePowerSession(int64_t sessionId) {
    // To remove a session we also need to undo the effects the session
    // has on currently enabled votes which means setting vote to inactive
    // and then forceing a uclamp update to occur
    forceSessionActive(sessionId, false);

    std::vector<pid_t> addedThreads;
    std::vector<pid_t> removedThreads;

    {
        // Wait till end to remove session because it needs to be around for apply U clamp
        // to work above since applying the uclamp needs a valid session id
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        mSessionTaskMap.replace(sessionId, {}, &addedThreads, &removedThreads);
        mSessionTaskMap.remove(sessionId);
    }

    for (auto tid : removedThreads) {
        if (!SetTaskProfiles(tid, {"NoResetUclampGrp"})) {
            ALOGE("Failed to set NoResetUclampGrp task profile for tid:%d", tid);
        }
    }
}

void PowerSessionManager::setThreadsFromPowerSession(int64_t sessionId,
                                                     const std::vector<int32_t> &threadIds) {
    std::vector<pid_t> addedThreads;
    std::vector<pid_t> removedThreads;
    forceSessionActive(sessionId, false);
    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        mSessionTaskMap.replace(sessionId, threadIds, &addedThreads, &removedThreads);
    }
    for (auto tid : addedThreads) {
        if (!SetTaskProfiles(tid, {"ResetUclampGrp"})) {
            ALOGE("Failed to set ResetUclampGrp task profile for tid:%d", tid);
        }
    }
    for (auto tid : removedThreads) {
        if (!SetTaskProfiles(tid, {"NoResetUclampGrp"})) {
            ALOGE("Failed to set NoResetUclampGrp task profile for tid:%d", tid);
        }
    }
    forceSessionActive(sessionId, true);
}

std::optional<bool> PowerSessionManager::isAnyAppSessionActive() {
    bool isAnyAppSessionActive = false;
    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        isAnyAppSessionActive =
                mSessionTaskMap.isAnyAppSessionActive(std::chrono::steady_clock::now());
    }
    return isAnyAppSessionActive;
}

void PowerSessionManager::updateUniversalBoostMode() {
    const auto active = isAnyAppSessionActive();
    if (!active.has_value()) {
        return;
    }
    if (active.value()) {
        disableSystemTopAppBoost();
    } else {
        enableSystemTopAppBoost();
    }
}

void PowerSessionManager::dumpToFd(int fd) {
    std::ostringstream dump_buf;
    dump_buf << "========== Begin PowerSessionManager ADPF list ==========\n";
    std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
    mSessionTaskMap.forEachSessionValTasks(
            [&](auto /* sessionId */, const auto &sessionVal, const auto &tasks) {
                sessionVal.dump(dump_buf);
                dump_buf << " Tid:Ref[";

                size_t tasksLen = tasks.size();
                for (auto taskId : tasks) {
                    dump_buf << taskId << ":";
                    const auto &sessionIds = mSessionTaskMap.getSessionIds(taskId);
                    if (!sessionIds.empty()) {
                        dump_buf << sessionIds.size();
                    }
                    if (tasksLen > 0) {
                        dump_buf << ", ";
                        --tasksLen;
                    }
                }
                dump_buf << "]\n";
            });
    dump_buf << "========== End PowerSessionManager ADPF list ==========\n";
    if (!::android::base::WriteStringToFd(dump_buf.str(), fd)) {
        ALOGE("Failed to dump one of session list to fd:%d", fd);
    }
}

void PowerSessionManager::pause(int64_t sessionId) {
    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        auto sessValPtr = mSessionTaskMap.findSession(sessionId);
        if (nullptr == sessValPtr) {
            ALOGW("Pause failed, session is null %" PRId64, sessionId);
            return;
        }

        if (!sessValPtr->isActive) {
            ALOGW("Sess(%" PRId64 "), cannot pause, already inActive", sessionId);
            return;
        }
        sessValPtr->isActive = false;
    }
    applyUclamp(sessionId, std::chrono::steady_clock::now());
    updateUniversalBoostMode();
}

void PowerSessionManager::resume(int64_t sessionId) {
    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        auto sessValPtr = mSessionTaskMap.findSession(sessionId);
        if (nullptr == sessValPtr) {
            ALOGW("Resume failed, session is null %" PRId64, sessionId);
            return;
        }

        if (sessValPtr->isActive) {
            ALOGW("Sess(%" PRId64 "), cannot resume, already active", sessionId);
            return;
        }
        sessValPtr->isActive = true;
    }
    applyUclamp(sessionId, std::chrono::steady_clock::now());
    updateUniversalBoostMode();
}

void PowerSessionManager::updateTargetWorkDuration(int64_t sessionId, AdpfHintType voteId,
                                                   std::chrono::nanoseconds durationNs) {
    int voteIdInt = static_cast<std::underlying_type_t<AdpfHintType>>(voteId);
    std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
    auto sessValPtr = mSessionTaskMap.findSession(sessionId);
    if (nullptr == sessValPtr) {
        ALOGE("Failed to updateTargetWorkDuration, session val is null id: %" PRId64, sessionId);
        return;
    }

    sessValPtr->votes->updateDuration(voteIdInt, durationNs);
    // Note, for now we are not recalculating and applying uclamp because
    // that maintains behavior from before.  In the future we may want to
    // revisit that decision.
}

void PowerSessionManager::voteSet(int64_t sessionId, AdpfHintType voteId, int uclampMin,
                                  int uclampMax, std::chrono::steady_clock::time_point startTime,
                                  std::chrono::nanoseconds durationNs) {
    const int voteIdInt = static_cast<std::underlying_type_t<AdpfHintType>>(voteId);
    const auto timeoutDeadline = startTime + durationNs;
    bool scheduleTimeout = false;

    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        auto sessValPtr = mSessionTaskMap.findSession(sessionId);
        if (nullptr == sessValPtr) {
            // Because of the async nature of some events an event for a session
            // that has been removed is a possibility so this is a verbose log
            // instead of a warning or error
            return;
        }

        if (!sessValPtr->votes->voteIsActive(voteIdInt)) {
            scheduleTimeout = true;
        }
        if (timeoutDeadline < sessValPtr->votes->voteTimeout(voteIdInt)) {
            scheduleTimeout = true;
        }
        sessValPtr->votes->add(voteIdInt,
                               CpuVote(true, startTime, durationNs, uclampMin, uclampMax));
        sessValPtr->lastUpdatedTime = startTime;
    }

    applyUclamp(sessionId, startTime);  // std::chrono::steady_clock::now());

    if (scheduleTimeout) {
        // Sent event to handle stale-vote/timeout in the future
        EventSessionTimeout eTimeout;
        eTimeout.timeStamp = startTime;  // eSet.timeStamp;
        eTimeout.sessionId = sessionId;
        eTimeout.voteId = voteIdInt;
        mEventSessionTimeoutWorker.schedule(eTimeout, timeoutDeadline);
    }
}

void PowerSessionManager::disableBoosts(int64_t sessionId) {
    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        auto sessValPtr = mSessionTaskMap.findSession(sessionId);
        if (nullptr == sessValPtr) {
            // Because of the async nature of some events an event for a session
            // that has been removed is a possibility so this is a verbose log
            // instead of a warning or error
            return;
        }

        // sessValPtr->disableBoosts();
        for (auto vid :
             {AdpfHintType::ADPF_CPU_LOAD_UP, AdpfHintType::ADPF_CPU_LOAD_RESET,
              AdpfHintType::ADPF_CPU_LOAD_RESUME, AdpfHintType::ADPF_VOTE_POWER_EFFICIENCY,
              AdpfHintType::ADPF_GPU_LOAD_UP, AdpfHintType::ADPF_GPU_LOAD_RESET}) {
            auto vint = static_cast<std::underlying_type_t<AdpfHintType>>(vid);
            sessValPtr->votes->setUseVote(vint, false);
        }
    }
}

void PowerSessionManager::enableSystemTopAppBoost() {
    if (HintManager::GetInstance()->IsHintSupported(kDisableBoostHintName)) {
        ALOGV("PowerSessionManager::enableSystemTopAppBoost!!");
        HintManager::GetInstance()->EndHint(kDisableBoostHintName);
    }
}

void PowerSessionManager::disableSystemTopAppBoost() {
    if (HintManager::GetInstance()->IsHintSupported(kDisableBoostHintName)) {
        ALOGV("PowerSessionManager::disableSystemTopAppBoost!!");
        HintManager::GetInstance()->DoHint(kDisableBoostHintName);
    }
}

void PowerSessionManager::handleEvent(const EventSessionTimeout &eventTimeout) {
    bool recalcUclamp = false;
    const auto tNow = std::chrono::steady_clock::now();
    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        auto sessValPtr = mSessionTaskMap.findSession(eventTimeout.sessionId);
        if (nullptr == sessValPtr) {
            // It is ok for session timeouts to fire after a session has been
            // removed
            return;
        }

        // To minimize the number of events pushed into the queue, we are using
        // the following logic to make use of a single timeout event which will
        // requeue itself if the timeout has been changed since it was added to
        // the work queue.  Requeue Logic:
        // if vote active and vote timeout <= sched time
        //    then deactivate vote and recalc uclamp (near end of function)
        // if vote active and vote timeout > sched time
        //    then requeue timeout event for new deadline (which is vote timeout)
        const bool voteIsActive = sessValPtr->votes->voteIsActive(eventTimeout.voteId);
        const auto voteTimeout = sessValPtr->votes->voteTimeout(eventTimeout.voteId);

        if (voteIsActive) {
            if (voteTimeout <= tNow) {
                sessValPtr->votes->setUseVote(eventTimeout.voteId, false);
                recalcUclamp = true;
            } else {
                // Can unlock sooner than we do
                auto eventTimeout2 = eventTimeout;
                mEventSessionTimeoutWorker.schedule(eventTimeout2, voteTimeout);
            }
        }
    }

    if (!recalcUclamp) {
        return;
    }

    // It is important to use the correct time here, time now is more reasonable
    // than trying to use the event's timestamp which will be slightly off given
    // the background priority queue introduces latency
    applyUclamp(eventTimeout.sessionId, tNow);
    updateUniversalBoostMode();
}

void PowerSessionManager::applyUclamp(int64_t sessionId,
                                      std::chrono::steady_clock::time_point timePoint) {
    const bool uclampMinOn = HintManager::GetInstance()->GetAdpfProfile()->mUclampMinOn;

    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        auto sessValPtr = mSessionTaskMap.findSession(sessionId);
        if (nullptr == sessValPtr) {
            return;
        }

        if (!uclampMinOn) {
            ALOGV("PowerSessionManager::set_uclamp_min: skip");
        } else {
            auto &threadList = mSessionTaskMap.getTaskIds(sessionId);
            auto tidIter = threadList.begin();
            while (tidIter != threadList.end()) {
                UclampRange uclampRange;
                mSessionTaskMap.getTaskVoteRange(*tidIter, timePoint, &uclampRange.uclampMin,
                                                 &uclampRange.uclampMax);
                int stat = set_uclamp_min(*tidIter, uclampRange.uclampMin);
                if (stat == ESRCH) {
                    ALOGV("Removing dead thread %d from hint session %s.", *tidIter,
                          sessValPtr->idString.c_str());
                    if (mSessionTaskMap.removeDeadTaskSessionMap(sessionId, *tidIter)) {
                        ALOGV("Removed dead thread-session map.");
                    }
                    tidIter = threadList.erase(tidIter);
                } else {
                    tidIter++;
                }
            }
        }

        sessValPtr->lastUpdatedTime = timePoint;
    }
}

void PowerSessionManager::forceSessionActive(int64_t sessionId, bool isActive) {
    {
        std::lock_guard<std::mutex> lock(mSessionTaskMapMutex);
        auto sessValPtr = mSessionTaskMap.findSession(sessionId);
        if (nullptr == sessValPtr) {
            return;
        }
        sessValPtr->isActive = isActive;
    }

    // As currently written, call needs to occur synchronously so as to ensure
    // that the SessionId remains valid and mapped to the proper threads/tasks
    // which enables apply u clamp to work correctly
    applyUclamp(sessionId, std::chrono::steady_clock::now());
    updateUniversalBoostMode();
}

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
