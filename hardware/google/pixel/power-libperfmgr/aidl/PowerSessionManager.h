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

#pragma once

#include <android-base/properties.h>
#include <perfmgr/HintManager.h>
#include <utils/Looper.h>

#include <mutex>
#include <optional>
#include <unordered_set>

#include "BackgroundWorker.h"
#include "PowerHintSession.h"
#include "SessionTaskMap.h"

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

using ::android::Looper;
using ::android::Message;
using ::android::Thread;
using ::android::perfmgr::HintManager;

constexpr char kPowerHalAdpfDisableTopAppBoost[] = "vendor.powerhal.adpf.disable.hint";

class PowerSessionManager : public ::android::RefBase {
  public:
    // Update the current hint info
    void updateHintMode(const std::string &mode, bool enabled);
    void updateHintBoost(const std::string &boost, int32_t durationMs);
    int getDisplayRefreshRate();
    // Add and remove power hint session
    void addPowerSession(const std::string &idString,
                         const std::shared_ptr<AppHintDesc> &sessionDescriptor,
                         const std::vector<int32_t> &threadIds);
    void removePowerSession(int64_t sessionId);
    // Replace current threads in session with threadIds
    void setThreadsFromPowerSession(int64_t sessionId, const std::vector<int32_t> &threadIds);
    // Pause and resume power hint session
    void pause(int64_t sessionId);
    void resume(int64_t sessionId);

    void updateUniversalBoostMode();
    void dumpToFd(int fd);

    void updateTargetWorkDuration(int64_t sessionId, AdpfHintType voteId,
                                  std::chrono::nanoseconds durationNs);

    // Set vote for power hint session
    void voteSet(int64_t sessionId, AdpfHintType voteId, int uclampMin, int uclampMax,
                 std::chrono::steady_clock::time_point startTime,
                 std::chrono::nanoseconds durationNs);

    void disableBoosts(int64_t sessionId);

    // Singleton
    static sp<PowerSessionManager> getInstance() {
        static sp<PowerSessionManager> instance = new PowerSessionManager();
        return instance;
    }

  private:
    std::optional<bool> isAnyAppSessionActive();
    void disableSystemTopAppBoost();
    void enableSystemTopAppBoost();
    const std::string kDisableBoostHintName;

    int mDisplayRefreshRate;

    // Rewrite specific
    mutable std::mutex mSessionTaskMapMutex;
    SessionTaskMap mSessionTaskMap;
    std::shared_ptr<PriorityQueueWorkerPool> mPriorityQueueWorkerPool;

    // Session timeout
    struct EventSessionTimeout {
        std::chrono::steady_clock::time_point timeStamp;
        int64_t sessionId{0};
        int voteId{0};
    };
    void handleEvent(const EventSessionTimeout &e);
    TemplatePriorityQueueWorker<EventSessionTimeout> mEventSessionTimeoutWorker;

    // Calculate uclamp range
    void applyUclamp(int64_t sessionId, std::chrono::steady_clock::time_point timePoint);
    // Force a session active or in-active, helper for other methods
    void forceSessionActive(int64_t sessionId, bool isActive);

    // Singleton
    PowerSessionManager()
        : kDisableBoostHintName(::android::base::GetProperty(kPowerHalAdpfDisableTopAppBoost,
                                                             "ADPF_DISABLE_TA_BOOST")),
          mDisplayRefreshRate(60),
          mPriorityQueueWorkerPool(new PriorityQueueWorkerPool(1, "adpf_handler")),
          mEventSessionTimeoutWorker([&](auto e) { handleEvent(e); }, mPriorityQueueWorkerPool) {}
    PowerSessionManager(PowerSessionManager const &) = delete;
    void operator=(PowerSessionManager const &) = delete;
};

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
