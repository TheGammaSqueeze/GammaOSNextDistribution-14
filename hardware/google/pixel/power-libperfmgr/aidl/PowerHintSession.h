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

#include <aidl/android/hardware/power/BnPowerHintSession.h>
#include <aidl/android/hardware/power/SessionHint.h>
#include <aidl/android/hardware/power/SessionMode.h>
#include <aidl/android/hardware/power/SessionTag.h>
#include <aidl/android/hardware/power/WorkDuration.h>
#include <utils/Looper.h>
#include <utils/Thread.h>

#include <array>
#include <unordered_map>

#include "AppDescriptorTrace.h"

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

using aidl::android::hardware::power::BnPowerHintSession;
using aidl::android::hardware::power::SessionConfig;
using aidl::android::hardware::power::SessionHint;
using aidl::android::hardware::power::SessionMode;
using aidl::android::hardware::power::SessionTag;
using aidl::android::hardware::power::WorkDuration;
using ::android::Message;
using ::android::MessageHandler;
using ::android::sp;
using std::chrono::milliseconds;
using std::chrono::nanoseconds;
using std::chrono::steady_clock;
using std::chrono::time_point;

class PowerSessionManager;

// The App Hint Descriptor struct manages information necessary
// to calculate the next uclamp min value from the PID function
// and is separate so that it can be used as a pointer for
// easily passing to the pid function
struct AppHintDesc {
    AppHintDesc(int64_t sessionId, int32_t tgid, int32_t uid, const std::vector<int32_t> &threadIds,
                SessionTag tag, std::chrono::nanoseconds pTargetNs);

    std::string toString() const;
    int64_t sessionId{0};
    const int32_t tgid;
    const int32_t uid;
    nanoseconds targetNs;
    std::vector<int32_t> thread_ids;
    SessionTag tag;
    int pidControlVariable;
    // status
    std::atomic<bool> is_active;
    // pid
    uint64_t update_count;
    int64_t integral_error;
    int64_t previous_error;
};

// The Power Hint Session is responsible for providing an
// interface for creating, updating, and closing power hints
// for a Session. Each sesion that is mapped to multiple
// threads (or task ids).
class PowerHintSession : public BnPowerHintSession {
  public:
    explicit PowerHintSession(int32_t tgid, int32_t uid, const std::vector<int32_t> &threadIds,
                              int64_t durationNanos, SessionTag tag);
    ~PowerHintSession();
    ndk::ScopedAStatus close() override;
    ndk::ScopedAStatus pause() override;
    ndk::ScopedAStatus resume() override;
    ndk::ScopedAStatus updateTargetWorkDuration(int64_t targetDurationNanos) override;
    ndk::ScopedAStatus reportActualWorkDuration(
            const std::vector<WorkDuration> &actualDurations) override;
    ndk::ScopedAStatus sendHint(SessionHint hint) override;
    ndk::ScopedAStatus setMode(SessionMode mode, bool enabled) override;
    ndk::ScopedAStatus setThreads(const std::vector<int32_t> &threadIds) override;
    ndk::ScopedAStatus getSessionConfig(SessionConfig *_aidl_return) override;

    bool isActive();
    bool isTimeout();
    // Is hint session for a user application
    bool isAppSession();
    void dumpToStream(std::ostream &stream);
    SessionTag getSessionTag() const;

  private:
    void tryToSendPowerHint(std::string hint);
    void updatePidControlVariable(int pidControlVariable, bool updateVote = true);
    int64_t convertWorkDurationToBoostByPid(const std::vector<WorkDuration> &actualDurations);
    // Data
    sp<PowerSessionManager> mPSManager;
    int64_t mSessionId = 0;
    std::string mIdString;
    std::shared_ptr<AppHintDesc> mDescriptor;
    // Trace strings
    AppDescriptorTrace mAppDescriptorTrace;
    std::atomic<time_point<steady_clock>> mLastUpdatedTime;
    std::atomic<bool> mSessionClosed = false;
    // Are cpu load change related hints are supported
    std::unordered_map<std::string, std::optional<bool>> mSupportedHints;
    // Last session hint sent, used for logging
    int mLastHintSent = -1;
    // Use the value of the last enum in enum_range +1 as array size
    std::array<bool, enum_size<SessionMode>()> mModes{};
    // Tag labeling what kind of session this is
    SessionTag mTag;
};

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
