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
#define STATSD_DEBUG false  // STOPSHIP if true
#include "Log.h"

#include "ShellSubscriberClient.h"

#include "FieldValue.h"
#include "guardrail/StatsdStats.h"
#include "matchers/matcher_util.h"
#include "stats_log_util.h"

using android::base::unique_fd;
using Status = ::ndk::ScopedAStatus;

namespace android {
namespace os {
namespace statsd {

const static int FIELD_ID_SHELL_DATA__ATOM = 1;
const static int FIELD_ID_SHELL_DATA__ELAPSED_TIMESTAMP_NANOS = 2;

// Store next subscription ID for StatsdStats.
// Not thread-safe; should only be accessed while holding ShellSubscriber::mMutex lock.
static int nextSubId = 0;

struct ReadConfigResult {
    vector<SimpleAtomMatcher> pushedMatchers;
    vector<ShellSubscriberClient::PullInfo> pullInfo;
};

// Read and parse single config. There should only one config in the input.
static optional<ReadConfigResult> readConfig(const vector<uint8_t>& configBytes,
                                             int64_t startTimeMs, int64_t minPullIntervalMs) {
    // Parse the config.
    ShellSubscription config;
    if (!config.ParseFromArray(configBytes.data(), configBytes.size())) {
        ALOGE("ShellSubscriberClient: failed to parse the config");
        return nullopt;
    }

    ReadConfigResult result;

    result.pushedMatchers.assign(config.pushed().begin(), config.pushed().end());

    vector<ShellSubscriberClient::PullInfo> pullInfo;
    for (const auto& pulled : config.pulled()) {
        vector<string> packages;
        vector<int32_t> uids;
        for (const string& pkg : pulled.packages()) {
            auto it = UidMap::sAidToUidMapping.find(pkg);
            if (it != UidMap::sAidToUidMapping.end()) {
                uids.push_back(it->second);
            } else {
                packages.push_back(pkg);
            }
        }

        const int64_t pullIntervalMs = max(pulled.freq_millis(), minPullIntervalMs);
        result.pullInfo.emplace_back(pulled.matcher(), startTimeMs, pullIntervalMs, packages, uids);
        ALOGD("ShellSubscriberClient: adding matcher for pulled atom %d",
              pulled.matcher().atom_id());
    }

    return result;
}

ShellSubscriberClient::PullInfo::PullInfo(const SimpleAtomMatcher& matcher, int64_t startTimeMs,
                                          int64_t intervalMs,
                                          const std::vector<std::string>& packages,
                                          const std::vector<int32_t>& uids)
    : mPullerMatcher(matcher),
      mIntervalMs(intervalMs),
      mPrevPullElapsedRealtimeMs(startTimeMs),
      mPullPackages(packages),
      mPullUids(uids) {
}

ShellSubscriberClient::ShellSubscriberClient(
        int id, int out, const std::shared_ptr<IStatsSubscriptionCallback>& callback,
        const std::vector<SimpleAtomMatcher>& pushedMatchers,
        const std::vector<PullInfo>& pulledInfo, int64_t timeoutSec, int64_t startTimeSec,
        const sp<UidMap>& uidMap, const sp<StatsPullerManager>& pullerMgr)
    : mId(id),
      mUidMap(uidMap),
      mPullerMgr(pullerMgr),
      mDupOut(fcntl(out, F_DUPFD_CLOEXEC, 0)),
      mPushedMatchers(pushedMatchers),
      mPulledInfo(pulledInfo),
      mCallback(callback),
      mTimeoutSec(timeoutSec),
      mStartTimeSec(startTimeSec),
      mLastWriteMs(startTimeSec * 1000),
      mCacheSize(0){};

unique_ptr<ShellSubscriberClient> ShellSubscriberClient::create(
        int in, int out, int64_t timeoutSec, int64_t startTimeSec, const sp<UidMap>& uidMap,
        const sp<StatsPullerManager>& pullerMgr) {
    // Read the size of the config.
    size_t bufferSize;
    if (!android::base::ReadFully(in, &bufferSize, sizeof(bufferSize))) {
        return nullptr;
    }

    // Check bufferSize
    if (bufferSize > (kMaxSizeKb * 1024)) {
        ALOGE("ShellSubscriberClient: received config (%zu bytes) is larger than the max size (%zu "
              "bytes)",
              bufferSize, (kMaxSizeKb * 1024));
        return nullptr;
    }

    // Read the config.
    vector<uint8_t> buffer(bufferSize);
    if (!android::base::ReadFully(in, buffer.data(), bufferSize)) {
        ALOGE("ShellSubscriberClient: failed to read the config from file descriptor");
        return nullptr;
    }

    const optional<ReadConfigResult> readConfigResult =
            readConfig(buffer, startTimeSec * 1000, /* minPullIntervalMs */ 0);
    if (!readConfigResult.has_value()) {
        return nullptr;
    }

    return make_unique<ShellSubscriberClient>(
            nextSubId++, out, /*callback=*/nullptr, readConfigResult->pushedMatchers,
            readConfigResult->pullInfo, timeoutSec, startTimeSec, uidMap, pullerMgr);
}

unique_ptr<ShellSubscriberClient> ShellSubscriberClient::create(
        const vector<uint8_t>& subscriptionConfig,
        const shared_ptr<IStatsSubscriptionCallback>& callback, int64_t startTimeSec,
        const sp<UidMap>& uidMap, const sp<StatsPullerManager>& pullerMgr) {
    if (callback == nullptr) {
        ALOGE("ShellSubscriberClient: received nullptr callback");
        return nullptr;
    }

    if (subscriptionConfig.size() > (kMaxSizeKb * 1024)) {
        ALOGE("ShellSubscriberClient: received config (%zu bytes) is larger than the max size (%zu "
              "bytes)",
              subscriptionConfig.size(), (kMaxSizeKb * 1024));
        return nullptr;
    }

    const optional<ReadConfigResult> readConfigResult =
            readConfig(subscriptionConfig, startTimeSec * 1000,
                       ShellSubscriberClient::kMinCallbackPullIntervalMs);
    if (!readConfigResult.has_value()) {
        return nullptr;
    }

    const int id = nextSubId++;

    StatsdStats::getInstance().noteSubscriptionStarted(id, readConfigResult->pushedMatchers.size(),
                                                       readConfigResult->pullInfo.size());
    return make_unique<ShellSubscriberClient>(
            id, /*out=*/-1, callback, readConfigResult->pushedMatchers, readConfigResult->pullInfo,
            /*timeoutSec=*/-1, startTimeSec, uidMap, pullerMgr);
}

bool ShellSubscriberClient::writeEventToProtoIfMatched(const LogEvent& event,
                                                       const SimpleAtomMatcher& matcher,
                                                       const sp<UidMap>& uidMap) {
    auto [matched, transformedEvent] = matchesSimple(mUidMap, matcher, event);
    if (!matched) {
        return false;
    }
    const LogEvent& eventRef = transformedEvent == nullptr ? event : *transformedEvent;

    // Cache atom event in mProtoOut.
    uint64_t atomToken = mProtoOut.start(util::FIELD_TYPE_MESSAGE | util::FIELD_COUNT_REPEATED |
                                         FIELD_ID_SHELL_DATA__ATOM);
    eventRef.ToProto(mProtoOut);
    mProtoOut.end(atomToken);

    const int64_t timestampNs = truncateTimestampIfNecessary(eventRef);
    mProtoOut.write(util::FIELD_TYPE_INT64 | util::FIELD_COUNT_REPEATED |
                            FIELD_ID_SHELL_DATA__ELAPSED_TIMESTAMP_NANOS,
                    static_cast<long long>(timestampNs));

    // Update byte size of cached data.
    mCacheSize += getSize(eventRef.getValues()) + sizeof(timestampNs);

    return true;
}

// Called by ShellSubscriber when a pushed event occurs
void ShellSubscriberClient::onLogEvent(const LogEvent& event) {
    for (const auto& matcher : mPushedMatchers) {
        if (writeEventToProtoIfMatched(event, matcher, mUidMap)) {
            flushProtoIfNeeded();
            break;
        }
    }
}

void ShellSubscriberClient::flushProtoIfNeeded() {
    if (mCallback == nullptr) {  // Using file descriptor.
        triggerFdFlush();
    } else if (mCacheSize >= kMaxCacheSizeBytes) {  // Using callback.
        // Flush data if cache is full.
        triggerCallback(StatsSubscriptionCallbackReason::STATSD_INITIATED);
    }
}

int64_t ShellSubscriberClient::pullIfNeeded(int64_t nowSecs, int64_t nowMillis, int64_t nowNanos) {
    int64_t sleepTimeMs = 24 * 60 * 60 * 1000;  // 24 hours.
    for (PullInfo& pullInfo : mPulledInfo) {
        if (pullInfo.mPrevPullElapsedRealtimeMs + pullInfo.mIntervalMs <= nowMillis) {
            vector<int32_t> uids;
            getUidsForPullAtom(&uids, pullInfo);

            vector<shared_ptr<LogEvent>> data;
            mPullerMgr->Pull(pullInfo.mPullerMatcher.atom_id(), uids, nowNanos, &data);
            VLOG("ShellSubscriberClient: pulled %zu atoms with id %d", data.size(),
                 pullInfo.mPullerMatcher.atom_id());
            if (mCallback != nullptr) {  // Callback subscription
                StatsdStats::getInstance().noteSubscriptionAtomPulled(
                        pullInfo.mPullerMatcher.atom_id());
            }

            writePulledAtomsLocked(data, pullInfo.mPullerMatcher);
            pullInfo.mPrevPullElapsedRealtimeMs = nowMillis;
        }

        // Determine how long to sleep before doing more work.
        const int64_t nextPullTimeMs = pullInfo.mPrevPullElapsedRealtimeMs + pullInfo.mIntervalMs;

        const int64_t timeBeforePullMs =
                nextPullTimeMs - nowMillis;  // guaranteed to be non-negative
        sleepTimeMs = min(sleepTimeMs, timeBeforePullMs);
    }
    return sleepTimeMs;
}

// The pullAndHeartbeat threads sleep for the minimum time
// among all clients' input
int64_t ShellSubscriberClient::pullAndSendHeartbeatsIfNeeded(int64_t nowSecs, int64_t nowMillis,
                                                             int64_t nowNanos) {
    int64_t sleepTimeMs;
    if (mCallback == nullptr) {  // File descriptor subscription
        if ((nowSecs - mStartTimeSec >= mTimeoutSec) && (mTimeoutSec > 0)) {
            mClientAlive = false;
            return kMsBetweenHeartbeats;
        }

        sleepTimeMs = min(kMsBetweenHeartbeats, pullIfNeeded(nowSecs, nowMillis, nowNanos));

        // Send a heartbeat consisting of data size of 0, if
        // the user hasn't recently received data from statsd. When it receives the data size of 0,
        // the user will not expect any atoms and recheck whether the subscription should end.
        if (nowMillis - mLastWriteMs >= kMsBetweenHeartbeats) {
            triggerFdFlush();
            if (!mClientAlive) return kMsBetweenHeartbeats;
        }

        int64_t timeBeforeHeartbeat = mLastWriteMs + kMsBetweenHeartbeats - nowMillis;
        sleepTimeMs = min(sleepTimeMs, timeBeforeHeartbeat);
    } else {  // Callback subscription.
        sleepTimeMs = min(kMsBetweenCallbacks, pullIfNeeded(nowSecs, nowMillis, nowNanos));

        if (mCacheSize > 0 && nowMillis - mLastWriteMs >= kMsBetweenCallbacks) {
            // Flush data if cache has kept data for longer than kMsBetweenCallbacks.
            triggerCallback(StatsSubscriptionCallbackReason::STATSD_INITIATED);
        }

        // Cache should be flushed kMsBetweenCallbacks after mLastWrite.
        const int64_t timeToCallbackMs = mLastWriteMs + kMsBetweenCallbacks - nowMillis;

        // For callback subscriptions, ensure minimum sleep time is at least
        // kMinCallbackSleepIntervalMs. Even if there is less than kMinCallbackSleepIntervalMs left
        // before next pull time, sleep for at least kMinCallbackSleepIntervalMs. This has the
        // effect of multiple pulled atoms that have a pull within kMinCallbackSleepIntervalMs from
        // now to have their pulls batched together, mitigating frequent wakeups of the puller
        // thread.
        sleepTimeMs = max(kMinCallbackSleepIntervalMs, min(sleepTimeMs, timeToCallbackMs));
    }
    return sleepTimeMs;
}

void ShellSubscriberClient::writePulledAtomsLocked(const vector<shared_ptr<LogEvent>>& data,
                                                   const SimpleAtomMatcher& matcher) {
    bool hasData = false;
    for (const shared_ptr<LogEvent>& event : data) {
        if (writeEventToProtoIfMatched(*event, matcher, mUidMap)) {
            hasData = true;
        }
    }

    if (hasData) {
        flushProtoIfNeeded();
    }
}

// Tries to write the atom encoded in mProtoOut to the pipe. If the write fails
// because the read end of the pipe has closed, change the client status so
// the manager knows the subscription is no longer active
void ShellSubscriberClient::attemptWriteToPipeLocked() {
    const size_t dataSize = mProtoOut.size();
    // First, write the payload size.
    if (!android::base::WriteFully(mDupOut, &dataSize, sizeof(dataSize))) {
        mClientAlive = false;
        return;
    }
    // Then, write the payload if this is not just a heartbeat.
    if (dataSize > 0 && !mProtoOut.flush(mDupOut.get())) {
        mClientAlive = false;
        return;
    }
    mLastWriteMs = getElapsedRealtimeMillis();
}

void ShellSubscriberClient::getUidsForPullAtom(vector<int32_t>* uids, const PullInfo& pullInfo) {
    uids->insert(uids->end(), pullInfo.mPullUids.begin(), pullInfo.mPullUids.end());
    // This is slow. Consider storing the uids per app and listening to uidmap updates.
    for (const string& pkg : pullInfo.mPullPackages) {
        set<int32_t> uidsForPkg = mUidMap->getAppUid(pkg);
        uids->insert(uids->end(), uidsForPkg.begin(), uidsForPkg.end());
    }
    uids->push_back(DEFAULT_PULL_UID);
}

void ShellSubscriberClient::clearCache() {
    mProtoOut.clear();
    mCacheSize = 0;
}

void ShellSubscriberClient::triggerFdFlush() {
    attemptWriteToPipeLocked();
    clearCache();
}

void ShellSubscriberClient::triggerCallback(StatsSubscriptionCallbackReason reason) {
    // Invoke Binder callback with cached event data.
    vector<uint8_t> payloadBytes;
    mProtoOut.serializeToVector(&payloadBytes);
    StatsdStats::getInstance().noteSubscriptionFlushed(mId);
    const Status status = mCallback->onSubscriptionData(reason, payloadBytes);
    if (status.getStatus() == STATUS_DEAD_OBJECT &&
        status.getExceptionCode() == EX_TRANSACTION_FAILED) {
        mClientAlive = false;
        return;
    }

    mLastWriteMs = getElapsedRealtimeMillis();
    clearCache();
}

void ShellSubscriberClient::flush() {
    triggerCallback(StatsSubscriptionCallbackReason::FLUSH_REQUESTED);
}

void ShellSubscriberClient::onUnsubscribe() {
    StatsdStats::getInstance().noteSubscriptionEnded(mId);
    if (mClientAlive) {
        triggerCallback(StatsSubscriptionCallbackReason::SUBSCRIPTION_ENDED);
    }
}

void ShellSubscriberClient::addAllAtomIds(LogEventFilter::AtomIdSet& allAtomIds) const {
    for (const auto& matcher : mPushedMatchers) {
        allAtomIds.insert(matcher.atom_id());
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
