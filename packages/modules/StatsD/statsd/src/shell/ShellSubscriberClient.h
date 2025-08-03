/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <aidl/android/os/IStatsSubscriptionCallback.h>
#include <aidl/android/os/StatsSubscriptionCallbackReason.h>
#include <android-base/file.h>
#include <android/util/ProtoOutputStream.h>
#include <private/android_filesystem_config.h>

#include <memory>

#include "external/StatsPullerManager.h"
#include "logd/LogEvent.h"
#include "packages/UidMap.h"
#include "socket/LogEventFilter.h"
#include "src/shell/shell_config.pb.h"
#include "src/statsd_config.pb.h"

using aidl::android::os::IStatsSubscriptionCallback;
using aidl::android::os::StatsSubscriptionCallbackReason;

namespace android {
namespace os {
namespace statsd {

// ShellSubscriberClient is not thread-safe. All calls must be
// guarded by the mutex in ShellSubscriber.h
class ShellSubscriberClient {
public:
    struct PullInfo {
        PullInfo(const SimpleAtomMatcher& matcher, int64_t startTimeMs, int64_t interval,
                 const std::vector<std::string>& packages, const std::vector<int32_t>& uids);

        const SimpleAtomMatcher mPullerMatcher;
        const int64_t mIntervalMs;
        int64_t mPrevPullElapsedRealtimeMs;
        const std::vector<std::string> mPullPackages;
        const std::vector<int32_t> mPullUids;
    };

    static std::unique_ptr<ShellSubscriberClient> create(int in, int out, int64_t timeoutSec,
                                                         int64_t startTimeSec,
                                                         const sp<UidMap>& uidMap,
                                                         const sp<StatsPullerManager>& pullerMgr);

    static std::unique_ptr<ShellSubscriberClient> create(
            const std::vector<uint8_t>& subscriptionConfig,
            const std::shared_ptr<IStatsSubscriptionCallback>& callback, int64_t startTimeSec,
            const sp<UidMap>& uidMap, const sp<StatsPullerManager>& pullerMgr);

    // Should only be called by the create() factory.
    explicit ShellSubscriberClient(int id, int out,
                                   const std::shared_ptr<IStatsSubscriptionCallback>& callback,
                                   const std::vector<SimpleAtomMatcher>& pushedMatchers,
                                   const std::vector<PullInfo>& pulledInfo, int64_t timeoutSec,
                                   int64_t startTimeSec, const sp<UidMap>& uidMap,
                                   const sp<StatsPullerManager>& pullerMgr);

    void onLogEvent(const LogEvent& event);

    int64_t pullAndSendHeartbeatsIfNeeded(int64_t nowSecs, int64_t nowMillis, int64_t nowNanos);

    // Should only be called when mCallback is not nullptr.
    void flush();

    // Should only be called when mCallback is not nullptr.
    void onUnsubscribe();

    bool isAlive() const {
        return mClientAlive;
    }

    bool hasCallback(const std::shared_ptr<IStatsSubscriptionCallback>& callback) const {
        return mCallback != nullptr && callback != nullptr &&
               callback->asBinder() == mCallback->asBinder();
    }

    static size_t getMaxSizeKb() {
        return kMaxSizeKb;
    }

    void addAllAtomIds(LogEventFilter::AtomIdSet& allAtomIds) const;

    // Minimum pull interval for callback subscriptions.
    static constexpr int64_t kMinCallbackPullIntervalMs = 60'000;  // 60 seconds.

    // Minimum sleep for the pull thread for callback subscriptions.
    static constexpr int64_t kMinCallbackSleepIntervalMs = 2000;  // 2 seconds.
private:
    int64_t pullIfNeeded(int64_t nowSecs, int64_t nowMillis, int64_t nowNanos);

    void writePulledAtomsLocked(const vector<std::shared_ptr<LogEvent>>& data,
                                const SimpleAtomMatcher& matcher);

    void attemptWriteToPipeLocked();

    void getUidsForPullAtom(vector<int32_t>* uids, const PullInfo& pullInfo);

    void flushProtoIfNeeded();

    bool writeEventToProtoIfMatched(const LogEvent& event, const SimpleAtomMatcher& matcher,
                                    const sp<UidMap>& uidMap);

    void clearCache();

    void triggerFdFlush();

    void triggerCallback(StatsSubscriptionCallbackReason reason);

    const int32_t DEFAULT_PULL_UID = AID_SYSTEM;

    // Unique ID for this subscription for  StatsdStats.
    const int mId;

    const sp<UidMap> mUidMap;

    const sp<StatsPullerManager> mPullerMgr;

    android::base::unique_fd mDupOut;

    const std::vector<SimpleAtomMatcher> mPushedMatchers;

    std::vector<PullInfo> mPulledInfo;

    std::shared_ptr<IStatsSubscriptionCallback> mCallback;

    const int64_t mTimeoutSec;

    const int64_t mStartTimeSec;

    bool mClientAlive = true;

    int64_t mLastWriteMs;

    // Stores Atom proto messages for events along with their respective timestamps.
    ProtoOutputStream mProtoOut;

    // Stores the total approximate encoded proto byte-size for cached Atom events in
    // mEventTimestampNs and mProtoOut.
    size_t mCacheSize;

    static constexpr int64_t kMsBetweenHeartbeats = 1000;

    // Cap the buffer size of configs to guard against bad allocations
    static constexpr size_t kMaxSizeKb = 50;

    static constexpr size_t kMaxCacheSizeBytes = 2 * 1024;  // 2 KB

    static constexpr int64_t kMsBetweenCallbacks = 70'000;  // 70 seconds.
};

}  // namespace statsd
}  // namespace os
}  // namespace android
