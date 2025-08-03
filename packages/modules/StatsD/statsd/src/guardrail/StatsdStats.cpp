/*
 * Copyright 2017, The Android Open Source Project
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
#define STATSD_DEBUG false  // STOPSHIP if true
#include "Log.h"

#include "StatsdStats.h"

#include <android/util/ProtoOutputStream.h>

#include "../stats_log_util.h"
#include "shell/ShellSubscriber.h"
#include "statslog_statsd.h"
#include "storage/StorageManager.h"
#include "utils/ShardOffsetProvider.h"

namespace android {
namespace os {
namespace statsd {

using android::util::FIELD_COUNT_REPEATED;
using android::util::FIELD_TYPE_BOOL;
using android::util::FIELD_TYPE_ENUM;
using android::util::FIELD_TYPE_FLOAT;
using android::util::FIELD_TYPE_INT32;
using android::util::FIELD_TYPE_INT64;
using android::util::FIELD_TYPE_MESSAGE;
using android::util::FIELD_TYPE_STRING;
using android::util::FIELD_TYPE_UINT32;
using android::util::ProtoOutputStream;
using std::lock_guard;
using std::shared_ptr;
using std::string;
using std::to_string;
using std::vector;

const int FIELD_ID_BEGIN_TIME = 1;
const int FIELD_ID_END_TIME = 2;
const int FIELD_ID_CONFIG_STATS = 3;
const int FIELD_ID_ATOM_STATS = 7;
const int FIELD_ID_UIDMAP_STATS = 8;
const int FIELD_ID_ANOMALY_ALARM_STATS = 9;
const int FIELD_ID_PERIODIC_ALARM_STATS = 12;
const int FIELD_ID_SYSTEM_SERVER_RESTART = 15;
const int FIELD_ID_LOGGER_ERROR_STATS = 16;
const int FIELD_ID_OVERFLOW = 18;
const int FIELD_ID_ACTIVATION_BROADCAST_GUARDRAIL = 19;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS = 20;
const int FIELD_ID_SHARD_OFFSET = 21;
const int FIELD_ID_STATSD_STATS_ID = 22;
const int FIELD_ID_SUBSCRIPTION_STATS = 23;
const int FIELD_ID_SOCKET_LOSS_STATS = 24;
const int FIELD_ID_QUEUE_STATS = 25;

const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_CALLING_UID = 1;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_CONFIG_ID = 2;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_CONFIG_UID = 3;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_CONFIG_PACKAGE = 4;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_INVALID_QUERY_REASON = 5;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_QUERY_WALL_TIME_NS = 6;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_HAS_ERROR = 7;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_ERROR = 8;
const int FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_LATENCY_NS = 9;

const int FIELD_ID_ATOM_STATS_TAG = 1;
const int FIELD_ID_ATOM_STATS_COUNT = 2;
const int FIELD_ID_ATOM_STATS_ERROR_COUNT = 3;
const int FIELD_ID_ATOM_STATS_DROPS_COUNT = 4;
const int FIELD_ID_ATOM_STATS_SKIP_COUNT = 5;

const int FIELD_ID_ANOMALY_ALARMS_REGISTERED = 1;
const int FIELD_ID_PERIODIC_ALARMS_REGISTERED = 1;

const int FIELD_ID_LOG_LOSS_STATS_TIME = 1;
const int FIELD_ID_LOG_LOSS_STATS_COUNT = 2;
const int FIELD_ID_LOG_LOSS_STATS_ERROR = 3;
const int FIELD_ID_LOG_LOSS_STATS_TAG = 4;
const int FIELD_ID_LOG_LOSS_STATS_UID = 5;
const int FIELD_ID_LOG_LOSS_STATS_PID = 6;

const int FIELD_ID_OVERFLOW_COUNT = 1;
const int FIELD_ID_OVERFLOW_MAX_HISTORY = 2;
const int FIELD_ID_OVERFLOW_MIN_HISTORY = 3;

const int FIELD_ID_QUEUE_MAX_SIZE_OBSERVED = 1;
const int FIELD_ID_QUEUE_MAX_SIZE_OBSERVED_ELAPSED_NANOS = 2;

const int FIELD_ID_CONFIG_STATS_UID = 1;
const int FIELD_ID_CONFIG_STATS_ID = 2;
const int FIELD_ID_CONFIG_STATS_CREATION = 3;
const int FIELD_ID_CONFIG_STATS_RESET = 19;
const int FIELD_ID_CONFIG_STATS_DELETION = 4;
const int FIELD_ID_CONFIG_STATS_METRIC_COUNT = 5;
const int FIELD_ID_CONFIG_STATS_CONDITION_COUNT = 6;
const int FIELD_ID_CONFIG_STATS_MATCHER_COUNT = 7;
const int FIELD_ID_CONFIG_STATS_ALERT_COUNT = 8;
const int FIELD_ID_CONFIG_STATS_VALID = 9;
const int FIELD_ID_CONFIG_STATS_INVALID_CONFIG_REASON = 24;
const int FIELD_ID_CONFIG_STATS_BROADCAST = 10;
const int FIELD_ID_CONFIG_STATS_DATA_DROP_TIME = 11;
const int FIELD_ID_CONFIG_STATS_DATA_DROP_BYTES = 21;
const int FIELD_ID_CONFIG_STATS_DUMP_REPORT_TIME = 12;
const int FIELD_ID_CONFIG_STATS_DUMP_REPORT_BYTES = 20;
const int FIELD_ID_CONFIG_STATS_MATCHER_STATS = 13;
const int FIELD_ID_CONFIG_STATS_CONDITION_STATS = 14;
const int FIELD_ID_CONFIG_STATS_METRIC_STATS = 15;
const int FIELD_ID_CONFIG_STATS_ALERT_STATS = 16;
const int FIELD_ID_CONFIG_STATS_METRIC_DIMENSION_IN_CONDITION_STATS = 17;
const int FIELD_ID_CONFIG_STATS_ANNOTATION = 18;
const int FIELD_ID_CONFIG_STATS_ACTIVATION = 22;
const int FIELD_ID_CONFIG_STATS_DEACTIVATION = 23;
const int FIELD_ID_CONFIG_STATS_ANNOTATION_INT64 = 1;
const int FIELD_ID_CONFIG_STATS_ANNOTATION_INT32 = 2;
const int FIELD_ID_CONFIG_STATS_RESTRICTED_METRIC_STATS = 25;
const int FIELD_ID_CONFIG_STATS_DEVICE_INFO_TABLE_CREATION_FAILED = 26;
const int FIELD_ID_CONFIG_STATS_RESTRICTED_DB_CORRUPTED_COUNT = 27;
const int FIELD_ID_CONFIG_STATS_RESTRICTED_CONFIG_FLUSH_LATENCY = 28;
const int FIELD_ID_CONFIG_STATS_RESTRICTED_CONFIG_DB_SIZE_TIME_SEC = 29;
const int FIELD_ID_CONFIG_STATS_RESTRICTED_CONFIG_DB_SIZE_BYTES = 30;
const int FIELD_ID_CONFIG_STATS_DUMP_REPORT_NUMBER = 31;
const int FIELD_ID_DB_DELETION_STAT_FAILED = 32;
const int FIELD_ID_DB_DELETION_SIZE_EXCEEDED_LIMIT = 33;
const int FIELD_ID_DB_DELETION_CONFIG_INVALID = 34;
const int FIELD_ID_DB_DELETION_TOO_OLD = 35;
const int FIELD_ID_DB_DELETION_CONFIG_REMOVED = 36;
const int FIELD_ID_DB_DELETION_CONFIG_UPDATED = 37;

const int FIELD_ID_INVALID_CONFIG_REASON_ENUM = 1;
const int FIELD_ID_INVALID_CONFIG_REASON_METRIC_ID = 2;
const int FIELD_ID_INVALID_CONFIG_REASON_STATE_ID = 3;
const int FIELD_ID_INVALID_CONFIG_REASON_ALERT_ID = 4;
const int FIELD_ID_INVALID_CONFIG_REASON_ALARM_ID = 5;
const int FIELD_ID_INVALID_CONFIG_REASON_SUBSCRIPTION_ID = 6;
const int FIELD_ID_INVALID_CONFIG_REASON_MATCHER_ID = 7;
const int FIELD_ID_INVALID_CONFIG_REASON_CONDITION_ID = 8;

const int FIELD_ID_MATCHER_STATS_ID = 1;
const int FIELD_ID_MATCHER_STATS_COUNT = 2;
const int FIELD_ID_CONDITION_STATS_ID = 1;
const int FIELD_ID_CONDITION_STATS_COUNT = 2;
const int FIELD_ID_METRIC_STATS_ID = 1;
const int FIELD_ID_METRIC_STATS_COUNT = 2;
const int FIELD_ID_ALERT_STATS_ID = 1;
const int FIELD_ID_ALERT_STATS_COUNT = 2;

const int FIELD_ID_UID_MAP_CHANGES = 1;
const int FIELD_ID_UID_MAP_BYTES_USED = 2;
const int FIELD_ID_UID_MAP_DROPPED_CHANGES = 3;
const int FIELD_ID_UID_MAP_DELETED_APPS = 4;

const int FIELD_ID_ACTIVATION_BROADCAST_GUARDRAIL_UID = 1;
const int FIELD_ID_ACTIVATION_BROADCAST_GUARDRAIL_TIME = 2;

// SocketLossStats
const int FIELD_ID_SOCKET_LOSS_STATS_PER_UID = 1;
const int FIELD_ID_SOCKET_LOSS_STATS_OVERFLOW_COUNTERS = 2;

// for LossStatsOverflowCounters proto
const int FIELD_ID_SOCKET_LOSS_STATS_OVERFLOW_COUNTERS_UID = 1;
const int FIELD_ID_SOCKET_LOSS_STATS_OVERFLOW_COUNTERS_COUNT = 2;

// for LossStatsPerUid proto
const int FIELD_ID_SOCKET_LOSS_STATS_UID = 1;
const int FIELD_ID_SOCKET_LOSS_STATS_FIRST_TIMESTAMP_NANOS = 2;
const int FIELD_ID_SOCKET_LOSS_STATS_LAST_TIMESTAMP_NANOS = 3;
const int FIELD_ID_SOCKET_LOSS_ATOM_ID_LOSS_STATS = 4;

// for AtomIdLossStats proto
const int FIELD_ID_ATOM_ID_LOSS_STATS_ATOM_ID = 1;
const int FIELD_ID_ATOM_ID_LOSS_STATS_ERROR = 2;
const int FIELD_ID_ATOM_ID_LOSS_STATS_COUNT = 3;

// for RestrictedMetricStats proto
const int FIELD_ID_RESTRICTED_STATS_METRIC_ID = 1;
const int FIELD_ID_RESTRICTED_STATS_INSERT_ERROR = 2;
const int FIELD_ID_RESTRICTED_STATS_TABLE_CREATION_ERROR = 3;
const int FIELD_ID_RESTRICTED_STATS_TABLE_DELETION_ERROR = 4;
const int FIELD_ID_RESTRICTED_STATS_FLUSH_LATENCY = 5;
const int FIELD_ID_RESTRICTED_STATS_CATEGORY_CHANGED_COUNT = 6;

const int FIELD_ID_SUBSCRIPTION_STATS_PER_SUBSCRIPTION_STATS = 1;
const int FIELD_ID_SUBSCRIPTION_STATS_PULL_THREAD_WAKEUP_COUNT = 2;

const int FIELD_ID_PER_SUBSCRIPTION_STATS_ID = 1;
const int FIELD_ID_PER_SUBSCRIPTION_STATS_PUSHED_ATOM_COUNT = 2;
const int FIELD_ID_PER_SUBSCRIPTION_STATS_PULLED_ATOM_COUNT = 3;
const int FIELD_ID_PER_SUBSCRIPTION_STATS_START_TIME = 4;
const int FIELD_ID_PER_SUBSCRIPTION_STATS_END_TIME = 5;
const int FIELD_ID_PER_SUBSCRIPTION_STATS_FLUSH_COUNT = 6;

const std::map<int, std::pair<size_t, size_t>> StatsdStats::kAtomDimensionKeySizeLimitMap = {
        {util::BINDER_CALLS, {6000, 10000}},
        {util::LOOPER_STATS, {1500, 2500}},
        {util::CPU_TIME_PER_UID_FREQ, {6000, 10000}},
};

StatsdStats::StatsdStats() : mStatsdStatsId(rand()) {
    mPushedAtomStats.resize(kMaxPushedAtomId + 1);
    mStartTimeSec = getWallClockSec();
}

StatsdStats& StatsdStats::getInstance() {
    static StatsdStats statsInstance;
    return statsInstance;
}

void StatsdStats::addToIceBoxLocked(shared_ptr<ConfigStats>& stats) {
    // The size of mIceBox grows strictly by one at a time. It won't be > kMaxIceBoxSize.
    if (mIceBox.size() == kMaxIceBoxSize) {
        mIceBox.pop_front();
    }
    mIceBox.push_back(stats);
}

void StatsdStats::noteConfigReceived(
        const ConfigKey& key, int metricsCount, int conditionsCount, int matchersCount,
        int alertsCount, const std::list<std::pair<const int64_t, const int32_t>>& annotations,
        const optional<InvalidConfigReason>& reason) {
    lock_guard<std::mutex> lock(mLock);
    int32_t nowTimeSec = getWallClockSec();

    // If there is an existing config for the same key, icebox the old config.
    noteConfigRemovedInternalLocked(key);

    shared_ptr<ConfigStats> configStats = std::make_shared<ConfigStats>();
    configStats->uid = key.GetUid();
    configStats->id = key.GetId();
    configStats->creation_time_sec = nowTimeSec;
    configStats->metric_count = metricsCount;
    configStats->condition_count = conditionsCount;
    configStats->matcher_count = matchersCount;
    configStats->alert_count = alertsCount;
    configStats->is_valid = !reason.has_value();
    configStats->reason = reason;
    for (auto& v : annotations) {
        configStats->annotations.emplace_back(v);
    }

    if (!reason.has_value()) {
        mConfigStats[key] = configStats;
    } else {
        configStats->deletion_time_sec = nowTimeSec;
        addToIceBoxLocked(configStats);
    }
}

void StatsdStats::noteConfigRemovedInternalLocked(const ConfigKey& key) {
    auto it = mConfigStats.find(key);
    if (it != mConfigStats.end()) {
        int32_t nowTimeSec = getWallClockSec();
        it->second->deletion_time_sec = nowTimeSec;
        addToIceBoxLocked(it->second);
        mConfigStats.erase(it);
    }
}

void StatsdStats::noteConfigRemoved(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    noteConfigRemovedInternalLocked(key);
}

void StatsdStats::noteConfigResetInternalLocked(const ConfigKey& key) {
    auto it = mConfigStats.find(key);
    if (it != mConfigStats.end()) {
        it->second->reset_time_sec = getWallClockSec();
    }
}

void StatsdStats::noteConfigReset(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    noteConfigResetInternalLocked(key);
}

void StatsdStats::noteLogLost(int32_t wallClockTimeSec, int32_t count, int32_t lastError,
                              int32_t lastTag, int32_t uid, int32_t pid) {
    lock_guard<std::mutex> lock(mLock);
    if (mLogLossStats.size() == kMaxLoggerErrors) {
        mLogLossStats.pop_front();
    }
    mLogLossStats.emplace_back(wallClockTimeSec, count, lastError, lastTag, uid, pid);
}

void StatsdStats::noteBroadcastSent(const ConfigKey& key) {
    noteBroadcastSent(key, getWallClockSec());
}

void StatsdStats::noteBroadcastSent(const ConfigKey& key, int32_t timeSec) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    if (it->second->broadcast_sent_time_sec.size() == kMaxTimestampCount) {
        it->second->broadcast_sent_time_sec.pop_front();
    }
    it->second->broadcast_sent_time_sec.push_back(timeSec);
}

void StatsdStats::noteActiveStatusChanged(const ConfigKey& key, bool activated) {
    noteActiveStatusChanged(key, activated, getWallClockSec());
}

void StatsdStats::noteActiveStatusChanged(const ConfigKey& key, bool activated, int32_t timeSec) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    auto& vec = activated ? it->second->activation_time_sec
                          : it->second->deactivation_time_sec;
    if (vec.size() == kMaxTimestampCount) {
        vec.pop_front();
    }
    vec.push_back(timeSec);
}

void StatsdStats::noteActivationBroadcastGuardrailHit(const int uid) {
    noteActivationBroadcastGuardrailHit(uid, getWallClockSec());
}

void StatsdStats::noteActivationBroadcastGuardrailHit(const int uid, const int32_t timeSec) {
    lock_guard<std::mutex> lock(mLock);
    auto& guardrailTimes = mActivationBroadcastGuardrailStats[uid];
    if (guardrailTimes.size() == kMaxTimestampCount) {
        guardrailTimes.pop_front();
    }
    guardrailTimes.push_back(timeSec);
}

void StatsdStats::noteDataDropped(const ConfigKey& key, const size_t totalBytes) {
    noteDataDropped(key, totalBytes, getWallClockSec());
}

void StatsdStats::noteEventQueueOverflow(int64_t oldestEventTimestampNs, int32_t atomId,
                                         bool isSkipped) {
    lock_guard<std::mutex> lock(mLock);

    mOverflowCount++;

    int64_t history = getElapsedRealtimeNs() - oldestEventTimestampNs;

    if (history > mMaxQueueHistoryNs) {
        mMaxQueueHistoryNs = history;
    }

    if (history < mMinQueueHistoryNs) {
        mMinQueueHistoryNs = history;
    }

    noteAtomLoggedLocked(atomId, isSkipped);
    noteAtomDroppedLocked(atomId);
}

void StatsdStats::noteEventQueueSize(int32_t size, int64_t eventTimestampNs) {
    lock_guard<std::mutex> lock(mLock);

    if (mEventQueueMaxSizeObserved < size) {
        mEventQueueMaxSizeObserved = size;
        mEventQueueMaxSizeObservedElapsedNanos = eventTimestampNs;
    }
}

void StatsdStats::noteAtomDroppedLocked(int32_t atomId) {
    constexpr int kMaxPushedAtomDroppedStatsSize = kMaxPushedAtomId + kMaxNonPlatformPushedAtoms;
    if (mPushedAtomDropsStats.size() < kMaxPushedAtomDroppedStatsSize ||
        mPushedAtomDropsStats.find(atomId) != mPushedAtomDropsStats.end()) {
        mPushedAtomDropsStats[atomId]++;
    }
}

void StatsdStats::noteAtomSocketLoss(const SocketLossInfo& lossInfo) {
    ALOGW("SocketLossEvent detected: %lld (firstLossTsNanos), %lld (lastLossTsNanos)",
          (long long)lossInfo.firstLossTsNanos, (long long)lossInfo.lastLossTsNanos);
    lock_guard<std::mutex> lock(mLock);

    if (mSocketLossStats.size() == kMaxSocketLossStatsSize) {
        // erase the oldest record
        mSocketLossStats.pop_front();
    }
    mSocketLossStats.emplace_back(lossInfo.uid, lossInfo.firstLossTsNanos,
                                  lossInfo.lastLossTsNanos);
    for (size_t i = 0; i < lossInfo.atomIds.size(); i++) {
        ALOGW("For uid %d atom %d was lost %d times with error %d", lossInfo.uid,
              lossInfo.atomIds[i], lossInfo.counts[i], lossInfo.errors[i]);
        mSocketLossStats.back().mLossCountPerErrorAtomId.emplace_back(
                lossInfo.atomIds[i], lossInfo.errors[i], lossInfo.counts[i]);
    }

    if (lossInfo.overflowCounter > 0) {
        auto overflowPerUid = mSocketLossStatsOverflowCounters.find(lossInfo.uid);
        if (overflowPerUid != mSocketLossStatsOverflowCounters.end()) {
            overflowPerUid->second += lossInfo.overflowCounter;
        } else if (mSocketLossStatsOverflowCounters.size() < kMaxSocketLossStatsSize) {
            mSocketLossStatsOverflowCounters[lossInfo.uid] = lossInfo.overflowCounter;
        }
    }
}

void StatsdStats::noteDataDropped(const ConfigKey& key, const size_t totalBytes, int32_t timeSec) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    if (it->second->data_drop_time_sec.size() == kMaxTimestampCount) {
        it->second->data_drop_time_sec.pop_front();
        it->second->data_drop_bytes.pop_front();
    }
    it->second->data_drop_time_sec.push_back(timeSec);
    it->second->data_drop_bytes.push_back(totalBytes);
}

void StatsdStats::noteMetricsReportSent(const ConfigKey& key, const size_t numBytes,
                                        const int32_t reportNumber) {
    noteMetricsReportSent(key, numBytes, getWallClockSec(), reportNumber);
}

void StatsdStats::noteMetricsReportSent(const ConfigKey& key, const size_t numBytes,
                                        int32_t timeSec, const int32_t reportNumber) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }

    if (it->second->dump_report_stats.size() == kMaxTimestampCount) {
        it->second->dump_report_stats.pop_front();
    }
    it->second->dump_report_stats.emplace_back(timeSec, numBytes, reportNumber);
}

void StatsdStats::noteDeviceInfoTableCreationFailed(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    it->second->device_info_table_creation_failed = true;
}

void StatsdStats::noteDbCorrupted(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    it->second->db_corrupted_count++;
}

void StatsdStats::noteDbSizeExceeded(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    it->second->db_deletion_size_exceeded_limit++;
}

void StatsdStats::noteDbStatFailed(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    it->second->db_deletion_stat_failed++;
}

void StatsdStats::noteDbConfigInvalid(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    it->second->db_deletion_config_invalid++;
}

void StatsdStats::noteDbTooOld(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    it->second->db_deletion_too_old++;
}

void StatsdStats::noteDbDeletionConfigRemoved(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    it->second->db_deletion_config_removed++;
}

void StatsdStats::noteDbDeletionConfigUpdated(const ConfigKey& key) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(key);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", key.ToString().c_str());
        return;
    }
    it->second->db_deletion_config_updated++;
}

void StatsdStats::noteUidMapDropped(int deltas) {
    lock_guard<std::mutex> lock(mLock);
    mUidMapStats.dropped_changes += mUidMapStats.dropped_changes + deltas;
}

void StatsdStats::noteUidMapAppDeletionDropped() {
    lock_guard<std::mutex> lock(mLock);
    mUidMapStats.deleted_apps++;
}

void StatsdStats::setUidMapChanges(int changes) {
    lock_guard<std::mutex> lock(mLock);
    mUidMapStats.changes = changes;
}

void StatsdStats::setCurrentUidMapMemory(int bytes) {
    lock_guard<std::mutex> lock(mLock);
    mUidMapStats.bytes_used = bytes;
}

void StatsdStats::noteConditionDimensionSize(const ConfigKey& key, const int64_t id, int size) {
    lock_guard<std::mutex> lock(mLock);
    // if name doesn't exist before, it will create the key with count 0.
    auto statsIt = mConfigStats.find(key);
    if (statsIt == mConfigStats.end()) {
        return;
    }

    auto& conditionSizeMap = statsIt->second->condition_stats;
    if (size > conditionSizeMap[id]) {
        conditionSizeMap[id] = size;
    }
}

void StatsdStats::noteMetricDimensionSize(const ConfigKey& key, const int64_t id, int size) {
    lock_guard<std::mutex> lock(mLock);
    // if name doesn't exist before, it will create the key with count 0.
    auto statsIt = mConfigStats.find(key);
    if (statsIt == mConfigStats.end()) {
        return;
    }
    auto& metricsDimensionMap = statsIt->second->metric_stats;
    if (size > metricsDimensionMap[id]) {
        metricsDimensionMap[id] = size;
    }
}

void StatsdStats::noteMetricDimensionInConditionSize(const ConfigKey& key, const int64_t id,
                                                     int size) {
    lock_guard<std::mutex> lock(mLock);
    // if name doesn't exist before, it will create the key with count 0.
    auto statsIt = mConfigStats.find(key);
    if (statsIt == mConfigStats.end()) {
        return;
    }
    auto& metricsDimensionMap = statsIt->second->metric_dimension_in_condition_stats;
    if (size > metricsDimensionMap[id]) {
        metricsDimensionMap[id] = size;
    }
}

void StatsdStats::noteMatcherMatched(const ConfigKey& key, const int64_t id) {
    lock_guard<std::mutex> lock(mLock);

    auto statsIt = mConfigStats.find(key);
    if (statsIt == mConfigStats.end()) {
        return;
    }
    statsIt->second->matcher_stats[id]++;
}

void StatsdStats::noteAnomalyDeclared(const ConfigKey& key, const int64_t id) {
    lock_guard<std::mutex> lock(mLock);
    auto statsIt = mConfigStats.find(key);
    if (statsIt == mConfigStats.end()) {
        return;
    }
    statsIt->second->alert_stats[id]++;
}

void StatsdStats::noteRegisteredAnomalyAlarmChanged() {
    lock_guard<std::mutex> lock(mLock);
    mAnomalyAlarmRegisteredStats++;
}

void StatsdStats::noteRegisteredPeriodicAlarmChanged() {
    lock_guard<std::mutex> lock(mLock);
    mPeriodicAlarmRegisteredStats++;
}

void StatsdStats::updateMinPullIntervalSec(int pullAtomId, long intervalSec) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[pullAtomId].minPullIntervalSec =
            std::min(mPulledAtomStats[pullAtomId].minPullIntervalSec, intervalSec);
}

void StatsdStats::notePull(int pullAtomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[pullAtomId].totalPull++;
}

void StatsdStats::notePullFromCache(int pullAtomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[pullAtomId].totalPullFromCache++;
}

void StatsdStats::notePullTime(int pullAtomId, int64_t pullTimeNs) {
    lock_guard<std::mutex> lock(mLock);
    auto& pullStats = mPulledAtomStats[pullAtomId];
    pullStats.maxPullTimeNs = std::max(pullStats.maxPullTimeNs, pullTimeNs);
    pullStats.avgPullTimeNs = (pullStats.avgPullTimeNs * pullStats.numPullTime + pullTimeNs) /
                              (pullStats.numPullTime + 1);
    pullStats.numPullTime += 1;
}

void StatsdStats::notePullDelay(int pullAtomId, int64_t pullDelayNs) {
    lock_guard<std::mutex> lock(mLock);
    auto& pullStats = mPulledAtomStats[pullAtomId];
    pullStats.maxPullDelayNs = std::max(pullStats.maxPullDelayNs, pullDelayNs);
    pullStats.avgPullDelayNs =
        (pullStats.avgPullDelayNs * pullStats.numPullDelay + pullDelayNs) /
            (pullStats.numPullDelay + 1);
    pullStats.numPullDelay += 1;
}

void StatsdStats::notePullDataError(int pullAtomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[pullAtomId].dataError++;
}

void StatsdStats::notePullTimeout(int pullAtomId,
                                  int64_t pullUptimeMillis,
                                  int64_t pullElapsedMillis) {
    lock_guard<std::mutex> lock(mLock);
    PulledAtomStats& pulledAtomStats = mPulledAtomStats[pullAtomId];
    pulledAtomStats.pullTimeout++;

    if (pulledAtomStats.pullTimeoutMetadata.size() == kMaxTimestampCount) {
        pulledAtomStats.pullTimeoutMetadata.pop_front();
    }

    pulledAtomStats.pullTimeoutMetadata.emplace_back(pullUptimeMillis, pullElapsedMillis);
}

void StatsdStats::notePullExceedMaxDelay(int pullAtomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[pullAtomId].pullExceedMaxDelay++;
}

void StatsdStats::noteAtomLogged(int atomId, int32_t /*timeSec*/, bool isSkipped) {
    lock_guard<std::mutex> lock(mLock);

    noteAtomLoggedLocked(atomId, isSkipped);
}

void StatsdStats::noteAtomLoggedLocked(int atomId, bool isSkipped) {
    if (atomId >= 0 && atomId <= kMaxPushedAtomId) {
        mPushedAtomStats[atomId].logCount++;
        mPushedAtomStats[atomId].skipCount += isSkipped;
    } else {
        if (atomId < 0) {
            android_errorWriteLog(0x534e4554, "187957589");
        }
        if (mNonPlatformPushedAtomStats.size() < kMaxNonPlatformPushedAtoms ||
            mNonPlatformPushedAtomStats.find(atomId) != mNonPlatformPushedAtomStats.end()) {
            mNonPlatformPushedAtomStats[atomId].logCount++;
            mNonPlatformPushedAtomStats[atomId].skipCount += isSkipped;
        }
    }
}

void StatsdStats::noteSystemServerRestart(int32_t timeSec) {
    lock_guard<std::mutex> lock(mLock);

    if (mSystemServerRestartSec.size() == kMaxSystemServerRestarts) {
        mSystemServerRestartSec.pop_front();
    }
    mSystemServerRestartSec.push_back(timeSec);
}

void StatsdStats::notePullFailed(int atomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[atomId].pullFailed++;
}

void StatsdStats::notePullUidProviderNotFound(int atomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[atomId].pullUidProviderNotFound++;
}

void StatsdStats::notePullerNotFound(int atomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[atomId].pullerNotFound++;
}

void StatsdStats::notePullBinderCallFailed(int atomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[atomId].binderCallFailCount++;
}

void StatsdStats::noteEmptyData(int atomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[atomId].emptyData++;
}

void StatsdStats::notePullerCallbackRegistrationChanged(int atomId, bool registered) {
    lock_guard<std::mutex> lock(mLock);
    if (registered) {
        mPulledAtomStats[atomId].registeredCount++;
    } else {
        mPulledAtomStats[atomId].unregisteredCount++;
    }
}

void StatsdStats::noteHardDimensionLimitReached(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).hardDimensionLimitReached++;
}

void StatsdStats::noteLateLogEventSkipped(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).lateLogEventSkipped++;
}

void StatsdStats::noteSkippedForwardBuckets(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).skippedForwardBuckets++;
}

void StatsdStats::noteBadValueType(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).badValueType++;
}

void StatsdStats::noteBucketDropped(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).bucketDropped++;
}

void StatsdStats::noteBucketUnknownCondition(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).bucketUnknownCondition++;
}

void StatsdStats::noteConditionChangeInNextBucket(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).conditionChangeInNextBucket++;
}

void StatsdStats::noteInvalidatedBucket(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).invalidatedBucket++;
}

void StatsdStats::noteBucketCount(int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    getAtomMetricStats(metricId).bucketCount++;
}

void StatsdStats::noteBucketBoundaryDelayNs(int64_t metricId, int64_t timeDelayNs) {
    lock_guard<std::mutex> lock(mLock);
    AtomMetricStats& metricStats = getAtomMetricStats(metricId);
    metricStats.maxBucketBoundaryDelayNs =
            std::max(metricStats.maxBucketBoundaryDelayNs, timeDelayNs);
    metricStats.minBucketBoundaryDelayNs =
            std::min(metricStats.minBucketBoundaryDelayNs, timeDelayNs);
}

void StatsdStats::noteAtomError(int atomTag, bool pull) {
    lock_guard<std::mutex> lock(mLock);
    if (pull) {
        mPulledAtomStats[atomTag].atomErrorCount++;
        return;
    }

    bool present = (mPushedAtomErrorStats.find(atomTag) != mPushedAtomErrorStats.end());
    bool full = (mPushedAtomErrorStats.size() >= (size_t)kMaxPushedAtomErrorStatsSize);
    if (!full || present) {
        mPushedAtomErrorStats[atomTag]++;
    }
}

bool StatsdStats::hasHitDimensionGuardrail(int64_t metricId) const {
    lock_guard<std::mutex> lock(mLock);
    auto atomMetricStatsIter = mAtomMetricStats.find(metricId);
    if (atomMetricStatsIter != mAtomMetricStats.end()) {
        return atomMetricStatsIter->second.hardDimensionLimitReached > 0;
    }
    return false;
}

void StatsdStats::noteQueryRestrictedMetricSucceed(const int64_t configId,
                                                   const string& configPackage,
                                                   const std::optional<int32_t> configUid,
                                                   const int32_t callingUid,
                                                   const int64_t latencyNs) {
    lock_guard<std::mutex> lock(mLock);

    if (mRestrictedMetricQueryStats.size() == kMaxRestrictedMetricQueryCount) {
        mRestrictedMetricQueryStats.pop_front();
    }
    mRestrictedMetricQueryStats.emplace_back(RestrictedMetricQueryStats(
            callingUid, configId, configPackage, configUid, getWallClockNs(),
            /*invalidQueryReason=*/std::nullopt, /*error=*/"", latencyNs));
}

void StatsdStats::noteQueryRestrictedMetricFailed(const int64_t configId,
                                                  const string& configPackage,
                                                  const std::optional<int32_t> configUid,
                                                  const int32_t callingUid,
                                                  const InvalidQueryReason reason) {
    lock_guard<std::mutex> lock(mLock);
    noteQueryRestrictedMetricFailedLocked(configId, configPackage, configUid, callingUid, reason,
                                          /*error=*/"");
}

void StatsdStats::noteQueryRestrictedMetricFailed(
        const int64_t configId, const string& configPackage, const std::optional<int32_t> configUid,
        const int32_t callingUid, const InvalidQueryReason reason, const string& error) {
    lock_guard<std::mutex> lock(mLock);
    noteQueryRestrictedMetricFailedLocked(configId, configPackage, configUid, callingUid, reason,
                                          error);
}

void StatsdStats::noteQueryRestrictedMetricFailedLocked(
        const int64_t configId, const string& configPackage, const std::optional<int32_t> configUid,
        const int32_t callingUid, const InvalidQueryReason reason, const string& error) {
    if (mRestrictedMetricQueryStats.size() == kMaxRestrictedMetricQueryCount) {
        mRestrictedMetricQueryStats.pop_front();
    }
    mRestrictedMetricQueryStats.emplace_back(RestrictedMetricQueryStats(
            callingUid, configId, configPackage, configUid, getWallClockNs(), reason, error,
            /*queryLatencyNs=*/std::nullopt));
}

void StatsdStats::noteRestrictedMetricInsertError(const ConfigKey& configKey,
                                                  const int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(configKey);
    if (it != mConfigStats.end()) {
        it->second->restricted_metric_stats[metricId].insertError++;
    }
}

void StatsdStats::noteRestrictedMetricTableCreationError(const ConfigKey& configKey,
                                                         const int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(configKey);
    if (it != mConfigStats.end()) {
        it->second->restricted_metric_stats[metricId].tableCreationError++;
    }
}

void StatsdStats::noteRestrictedMetricTableDeletionError(const ConfigKey& configKey,
                                                         const int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(configKey);
    if (it != mConfigStats.end()) {
        it->second->restricted_metric_stats[metricId].tableDeletionError++;
    }
}

void StatsdStats::noteRestrictedMetricFlushLatency(const ConfigKey& configKey,
                                                   const int64_t metricId,
                                                   const int64_t flushLatencyNs) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(configKey);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", configKey.ToString().c_str());
        return;
    }
    auto& restrictedMetricStats = it->second->restricted_metric_stats[metricId];
    if (restrictedMetricStats.flushLatencyNs.size() == kMaxRestrictedMetricFlushLatencyCount) {
        restrictedMetricStats.flushLatencyNs.pop_front();
    }
    restrictedMetricStats.flushLatencyNs.push_back(flushLatencyNs);
}

void StatsdStats::noteRestrictedConfigFlushLatency(const ConfigKey& configKey,
                                                   const int64_t totalFlushLatencyNs) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(configKey);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", configKey.ToString().c_str());
        return;
    }
    std::list<int64_t>& totalFlushLatencies = it->second->total_flush_latency_ns;
    if (totalFlushLatencies.size() == kMaxRestrictedConfigFlushLatencyCount) {
        totalFlushLatencies.pop_front();
    }
    totalFlushLatencies.push_back(totalFlushLatencyNs);
}

void StatsdStats::noteRestrictedConfigDbSize(const ConfigKey& configKey,
                                             const int64_t elapsedTimeNs, const int64_t dbSize) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(configKey);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", configKey.ToString().c_str());
        return;
    }
    std::list<int64_t>& totalDbSizeTimestamps = it->second->total_db_size_timestamps;
    std::list<int64_t>& totaDbSizes = it->second->total_db_sizes;
    if (totalDbSizeTimestamps.size() == kMaxRestrictedConfigDbSizeCount) {
        totalDbSizeTimestamps.pop_front();
        totaDbSizes.pop_front();
    }
    totalDbSizeTimestamps.push_back(elapsedTimeNs);
    totaDbSizes.push_back(dbSize);
}

void StatsdStats::noteRestrictedMetricCategoryChanged(const ConfigKey& configKey,
                                                      const int64_t metricId) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mConfigStats.find(configKey);
    if (it == mConfigStats.end()) {
        ALOGE("Config key %s not found!", configKey.ToString().c_str());
        return;
    }
    it->second->restricted_metric_stats[metricId].categoryChangedCount++;
}

void StatsdStats::noteSubscriptionStarted(int subId, int32_t pushedAtomCount,
                                          int32_t pulledAtomCount) {
    lock_guard<std::mutex> lock(mLock);

    // If we're already keeping track of max # subscriptions, remove the earliest added
    // SubscriptionStats for which the corresponding subscription has ended.
    if (mSubscriptionStats.size() >= ShellSubscriber::getMaxSubscriptions()) {
        for (auto it = mSubscriptionStats.begin();;) {
            if (it == mSubscriptionStats.end()) {
                // Didn't find any ended subscriptions; don't track new subscription.
                // We should not really enter this block since ShellSubscriber will refuse more than
                // ShellSubscriber::kMaxSubscriptions active subscriptions to be added. So for
                // (kMaxSubscriptions + 1)th subscription being added, ShellSubscriber should reject
                // it and noteSubscriptionStarted should not be called for it.
                return;
            } else if (it->second.end_time_sec > 0) {
                // Remove the first ended subscription.
                mSubscriptionStats.erase(it);
                break;
            } else {
                it++;
            }
        }
    }

    const int32_t nowTimeSec = getWallClockSec();

    SubscriptionStats& subscriptionStats = mSubscriptionStats[subId];
    subscriptionStats.pushed_atom_count = pushedAtomCount;
    subscriptionStats.pulled_atom_count = pulledAtomCount;
    subscriptionStats.start_time_sec = nowTimeSec;
}

void StatsdStats::noteSubscriptionEnded(int subId) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mSubscriptionStats.find(subId);
    if (it == mSubscriptionStats.end()) {
        // We should not enter here since noteSubscriptionStarted should be called first and that
        // should successfully add an entry in mSubscriptionStats. See the comment in
        // noteSubscriptionStarted.
        return;
    }
    const int32_t nowTimeSec = getWallClockSec();
    it->second.end_time_sec = nowTimeSec;
}

void StatsdStats::noteSubscriptionFlushed(int subId) {
    lock_guard<std::mutex> lock(mLock);
    auto it = mSubscriptionStats.find(subId);
    if (it == mSubscriptionStats.end()) {
        // We should not enter here since noteSubscriptionStarted should be called first and that
        // should successfully add an entry in mSubscriptionStats. See the comment in
        // noteSubscriptionStarted.
        return;
    }
    it->second.flush_count++;
}

void StatsdStats::noteSubscriptionAtomPulled(int atomId) {
    lock_guard<std::mutex> lock(mLock);
    mPulledAtomStats[atomId].subscriptionPullCount++;
}

void StatsdStats::noteSubscriptionPullThreadWakeup() {
    lock_guard<std::mutex> lock(mLock);
    mSubscriptionPullThreadWakeupCount++;
}

StatsdStats::AtomMetricStats& StatsdStats::getAtomMetricStats(int64_t metricId) {
    auto atomMetricStatsIter = mAtomMetricStats.find(metricId);
    if (atomMetricStatsIter != mAtomMetricStats.end()) {
        return atomMetricStatsIter->second;
    }
    auto emplaceResult = mAtomMetricStats.emplace(metricId, AtomMetricStats());
    return emplaceResult.first->second;
}

void StatsdStats::reset() {
    lock_guard<std::mutex> lock(mLock);
    resetInternalLocked();
}

void StatsdStats::resetInternalLocked() {
    // Reset the historical data, but keep the active ConfigStats
    mStartTimeSec = getWallClockSec();
    mIceBox.clear();
    std::fill(mPushedAtomStats.begin(), mPushedAtomStats.end(), PushedAtomStats());
    mNonPlatformPushedAtomStats.clear();
    mAnomalyAlarmRegisteredStats = 0;
    mPeriodicAlarmRegisteredStats = 0;
    mSystemServerRestartSec.clear();
    mLogLossStats.clear();
    mOverflowCount = 0;
    mMinQueueHistoryNs = kInt64Max;
    mMaxQueueHistoryNs = 0;
    mEventQueueMaxSizeObserved = 0;
    mEventQueueMaxSizeObservedElapsedNanos = 0;
    for (auto& config : mConfigStats) {
        config.second->broadcast_sent_time_sec.clear();
        config.second->activation_time_sec.clear();
        config.second->deactivation_time_sec.clear();
        config.second->data_drop_time_sec.clear();
        config.second->data_drop_bytes.clear();
        config.second->dump_report_stats.clear();
        config.second->annotations.clear();
        config.second->matcher_stats.clear();
        config.second->condition_stats.clear();
        config.second->metric_stats.clear();
        config.second->metric_dimension_in_condition_stats.clear();
        config.second->alert_stats.clear();
        config.second->restricted_metric_stats.clear();
        config.second->db_corrupted_count = 0;
        config.second->total_flush_latency_ns.clear();
        config.second->total_db_size_timestamps.clear();
        config.second->total_db_sizes.clear();
        config.second->db_deletion_size_exceeded_limit = 0;
        config.second->db_deletion_stat_failed = 0;
        config.second->db_deletion_config_invalid = 0;
        config.second->db_deletion_too_old = 0;
        config.second->db_deletion_config_removed = 0;
        config.second->db_deletion_config_updated = 0;
    }
    for (auto& pullStats : mPulledAtomStats) {
        pullStats.second.totalPull = 0;
        pullStats.second.totalPullFromCache = 0;
        pullStats.second.minPullIntervalSec = LONG_MAX;
        pullStats.second.avgPullTimeNs = 0;
        pullStats.second.maxPullTimeNs = 0;
        pullStats.second.numPullTime = 0;
        pullStats.second.avgPullDelayNs = 0;
        pullStats.second.maxPullDelayNs = 0;
        pullStats.second.numPullDelay = 0;
        pullStats.second.dataError = 0;
        pullStats.second.pullTimeout = 0;
        pullStats.second.pullExceedMaxDelay = 0;
        pullStats.second.pullFailed = 0;
        pullStats.second.pullUidProviderNotFound = 0;
        pullStats.second.pullerNotFound = 0;
        pullStats.second.registeredCount = 0;
        pullStats.second.unregisteredCount = 0;
        pullStats.second.atomErrorCount = 0;
        pullStats.second.binderCallFailCount = 0;
        pullStats.second.pullTimeoutMetadata.clear();
        pullStats.second.subscriptionPullCount = 0;
    }
    mAtomMetricStats.clear();
    mActivationBroadcastGuardrailStats.clear();
    mPushedAtomErrorStats.clear();
    mSocketLossStats.clear();
    mSocketLossStatsOverflowCounters.clear();
    mPushedAtomDropsStats.clear();
    mRestrictedMetricQueryStats.clear();
    mSubscriptionPullThreadWakeupCount = 0;

    for (auto it = mSubscriptionStats.begin(); it != mSubscriptionStats.end();) {
        if (it->second.end_time_sec > 0) {
            // Remove finished subscriptions
            it = mSubscriptionStats.erase(it);
        } else {
            // Reset dynamic properties of active subscriptions.
            it->second.flush_count = 0;
            ++it;
        }
    }
}

string buildTimeString(int64_t timeSec) {
    time_t t = timeSec;
    struct tm* tm = localtime(&t);
    char timeBuffer[80];
    strftime(timeBuffer, sizeof(timeBuffer), "%Y-%m-%d %I:%M%p", tm);
    return string(timeBuffer);
}

int StatsdStats::getPushedAtomErrorsLocked(int atomId) const {
    const auto& it = mPushedAtomErrorStats.find(atomId);
    if (it != mPushedAtomErrorStats.end()) {
        return it->second;
    } else {
        return 0;
    }
}

int StatsdStats::getPushedAtomDropsLocked(int atomId) const {
    const auto& it = mPushedAtomDropsStats.find(atomId);
    if (it != mPushedAtomDropsStats.end()) {
        return it->second;
    } else {
        return 0;
    }
}

bool StatsdStats::hasRestrictedConfigErrors(const std::shared_ptr<ConfigStats>& configStats) const {
    return configStats->device_info_table_creation_failed || configStats->db_corrupted_count ||
           configStats->db_deletion_size_exceeded_limit || configStats->db_deletion_stat_failed ||
           configStats->db_deletion_config_invalid || configStats->db_deletion_too_old ||
           configStats->db_deletion_config_removed || configStats->db_deletion_config_updated;
}

bool StatsdStats::hasEventQueueOverflow() const {
    lock_guard<std::mutex> lock(mLock);
    return mOverflowCount != 0;
}

bool StatsdStats::hasSocketLoss() const {
    lock_guard<std::mutex> lock(mLock);
    return !mLogLossStats.empty();
}

void StatsdStats::dumpStats(int out) const {
    lock_guard<std::mutex> lock(mLock);
    time_t t = mStartTimeSec;
    struct tm* tm = localtime(&t);
    char timeBuffer[80];
    strftime(timeBuffer, sizeof(timeBuffer), "%Y-%m-%d %I:%M%p\n", tm);
    dprintf(out, "Stats collection start second: %s\n", timeBuffer);
    dprintf(out, "%lu Config in icebox: \n", (unsigned long)mIceBox.size());
    for (const auto& configStats : mIceBox) {
        dprintf(out,
                "Config {%d_%lld}: creation=%d, deletion=%d, reset=%d, #metric=%d, #condition=%d, "
                "#matcher=%d, #alert=%d, valid=%d",
                configStats->uid, (long long)configStats->id, configStats->creation_time_sec,
                configStats->deletion_time_sec, configStats->reset_time_sec,
                configStats->metric_count, configStats->condition_count, configStats->matcher_count,
                configStats->alert_count, configStats->is_valid);
        if (hasRestrictedConfigErrors(configStats)) {
            dprintf(out,
                    ", device_info_table_creation_failed=%d, db_corrupted_count=%d, "
                    "db_size_exceeded=%d, db_stat_failed=%d, "
                    "db_config_invalid=%d, db_too_old=%d, db_deletion_config_removed=%d, "
                    "db_deletion_config_updated=%d",
                    configStats->device_info_table_creation_failed, configStats->db_corrupted_count,
                    configStats->db_deletion_size_exceeded_limit,
                    configStats->db_deletion_stat_failed, configStats->db_deletion_config_invalid,
                    configStats->db_deletion_too_old, configStats->db_deletion_config_removed,
                    configStats->db_deletion_config_updated);
        }
        dprintf(out, "\n");
        if (!configStats->is_valid) {
            dprintf(out, "\tinvalid config reason: %s\n",
                    InvalidConfigReasonEnum_Name(configStats->reason->reason).c_str());
        }

        for (const auto& broadcastTime : configStats->broadcast_sent_time_sec) {
            dprintf(out, "\tbroadcast time: %d\n", broadcastTime);
        }

        for (const int& activationTime : configStats->activation_time_sec) {
            dprintf(out, "\tactivation time: %d\n", activationTime);
        }

        for (const int& deactivationTime : configStats->deactivation_time_sec) {
            dprintf(out, "\tdeactivation time: %d\n", deactivationTime);
        }

        auto dropTimePtr = configStats->data_drop_time_sec.begin();
        auto dropBytesPtr = configStats->data_drop_bytes.begin();
        for (int i = 0; i < (int)configStats->data_drop_time_sec.size();
             i++, dropTimePtr++, dropBytesPtr++) {
            dprintf(out, "\tdata drop time: %d with size %lld", *dropTimePtr,
                    (long long)*dropBytesPtr);
        }

        for (const auto& stats : configStats->restricted_metric_stats) {
            dprintf(out, "Restricted MetricId %lld: ", (long long)stats.first);
            dprintf(out, "Insert error %lld, ", (long long)stats.second.insertError);
            dprintf(out, "Table creation error %lld, ", (long long)stats.second.tableCreationError);
            dprintf(out, "Table deletion error %lld ", (long long)stats.second.tableDeletionError);
            dprintf(out, "Category changed count %lld\n ",
                    (long long)stats.second.categoryChangedCount);
            string flushLatencies = "Flush Latencies: ";
            for (const int64_t latencyNs : stats.second.flushLatencyNs) {
                flushLatencies.append(to_string(latencyNs).append(","));
            }
            flushLatencies.pop_back();
            flushLatencies.push_back('\n');
            dprintf(out, "%s", flushLatencies.c_str());
        }

        for (const int64_t flushLatency : configStats->total_flush_latency_ns) {
            dprintf(out, "\tflush latency time ns: %lld\n", (long long)flushLatency);
        }

        for (const int64_t dbSize : configStats->total_db_sizes) {
            dprintf(out, "\tdb size: %lld\n", (long long)dbSize);
        }
    }
    dprintf(out, "%lu Active Configs\n", (unsigned long)mConfigStats.size());
    for (auto& pair : mConfigStats) {
        auto& configStats = pair.second;
        dprintf(out,
                "Config {%d-%lld}: creation=%d, deletion=%d, #metric=%d, #condition=%d, "
                "#matcher=%d, #alert=%d, valid=%d",
                configStats->uid, (long long)configStats->id, configStats->creation_time_sec,
                configStats->deletion_time_sec, configStats->metric_count,
                configStats->condition_count, configStats->matcher_count, configStats->alert_count,
                configStats->is_valid);
        if (hasRestrictedConfigErrors(configStats)) {
            dprintf(out,
                    ", device_info_table_creation_failed=%d, db_corrupted_count=%d, "
                    "db_size_exceeded=%d, db_stat_failed=%d, "
                    "db_config_invalid=%d, db_too_old=%d, db_deletion_config_removed=%d, "
                    "db_deletion_config_updated=%d",
                    configStats->device_info_table_creation_failed, configStats->db_corrupted_count,
                    configStats->db_deletion_size_exceeded_limit,
                    configStats->db_deletion_stat_failed, configStats->db_deletion_config_invalid,
                    configStats->db_deletion_too_old, configStats->db_deletion_config_removed,
                    configStats->db_deletion_config_updated);
        }
        dprintf(out, "\n");
        if (!configStats->is_valid) {
            dprintf(out, "\tinvalid config reason: %s\n",
                    InvalidConfigReasonEnum_Name(configStats->reason->reason).c_str());
        }

        for (const auto& annotation : configStats->annotations) {
            dprintf(out, "\tannotation: %lld, %d\n", (long long)annotation.first,
                    annotation.second);
        }

        for (const auto& broadcastTime : configStats->broadcast_sent_time_sec) {
            dprintf(out, "\tbroadcast time: %s(%lld)\n", buildTimeString(broadcastTime).c_str(),
                    (long long)broadcastTime);
        }

        for (const int& activationTime : configStats->activation_time_sec) {
            dprintf(out, "\tactivation time: %d\n", activationTime);
        }

        for (const int& deactivationTime : configStats->deactivation_time_sec) {
            dprintf(out, "\tdeactivation time: %d\n", deactivationTime);
        }

        auto dropTimePtr = configStats->data_drop_time_sec.begin();
        auto dropBytesPtr = configStats->data_drop_bytes.begin();
        for (int i = 0; i < (int)configStats->data_drop_time_sec.size();
             i++, dropTimePtr++, dropBytesPtr++) {
            dprintf(out, "\tdata drop time: %s(%lld) with %lld bytes\n",
                    buildTimeString(*dropTimePtr).c_str(), (long long)*dropTimePtr,
                    (long long)*dropBytesPtr);
        }

        for (const auto& dump : configStats->dump_report_stats) {
            dprintf(out, "\tdump report time: %s(%lld) bytes: %d reportNumber: %d\n",
                    buildTimeString(dump.mDumpReportTimeSec).c_str(),
                    (long long)dump.mDumpReportTimeSec, dump.mDumpReportSizeBytes,
                    dump.mDumpReportNumber);
        }

        for (const auto& stats : pair.second->matcher_stats) {
            dprintf(out, "matcher %lld matched %d times\n", (long long)stats.first, stats.second);
        }

        for (const auto& stats : pair.second->condition_stats) {
            dprintf(out, "condition %lld max output tuple size %d\n", (long long)stats.first,
                    stats.second);
        }

        for (const auto& stats : pair.second->condition_stats) {
            dprintf(out, "metrics %lld max output tuple size %d\n", (long long)stats.first,
                    stats.second);
        }

        for (const auto& stats : pair.second->alert_stats) {
            dprintf(out, "alert %lld declared %d times\n", (long long)stats.first, stats.second);
        }

        for (const auto& stats : configStats->restricted_metric_stats) {
            dprintf(out, "Restricted MetricId %lld: ", (long long)stats.first);
            dprintf(out, "Insert error %lld, ", (long long)stats.second.insertError);
            dprintf(out, "Table creation error %lld, ", (long long)stats.second.tableCreationError);
            dprintf(out, "Table deletion error %lld ", (long long)stats.second.tableDeletionError);
            dprintf(out, "Category changed count %lld\n ",
                    (long long)stats.second.categoryChangedCount);
            string flushLatencies = "Flush Latencies: ";
            for (const int64_t latencyNs : stats.second.flushLatencyNs) {
                flushLatencies.append(to_string(latencyNs).append(","));
            }
            flushLatencies.pop_back();
            flushLatencies.push_back('\n');
            dprintf(out, "%s", flushLatencies.c_str());
        }

        for (const int64_t flushLatency : configStats->total_flush_latency_ns) {
            dprintf(out, "flush latency time ns: %lld\n", (long long)flushLatency);
        }

        for (const int64_t dbSize : configStats->total_db_sizes) {
            dprintf(out, "\tdb size: %lld\n", (long long)dbSize);
        }
    }
    dprintf(out, "********Disk Usage stats***********\n");
    StorageManager::printStats(out);
    dprintf(out, "********Pushed Atom stats***********\n");
    const size_t atomCounts = mPushedAtomStats.size();
    for (size_t i = 2; i < atomCounts; i++) {
        if (mPushedAtomStats[i].logCount > 0) {
            dprintf(out,
                    "Atom %zu->(total count)%d, (error count)%d, (drop count)%d, (skip count)%d\n",
                    i, mPushedAtomStats[i].logCount, getPushedAtomErrorsLocked((int)i),
                    getPushedAtomDropsLocked((int)i), mPushedAtomStats[i].skipCount);
        }
    }
    for (const auto& pair : mNonPlatformPushedAtomStats) {
        dprintf(out, "Atom %d->(total count)%d, (error count)%d, (drop count)%d, (skip count)%d\n",
                pair.first, pair.second.logCount, getPushedAtomErrorsLocked(pair.first),
                getPushedAtomDropsLocked((int)pair.first), pair.second.skipCount);
    }

    dprintf(out, "********Pulled Atom stats***********\n");
    for (const auto& pair : mPulledAtomStats) {
        dprintf(out,
                "Atom %d->(total pull)%ld, (pull from cache)%ld, "
                "(pull failed)%ld, (min pull interval)%ld \n"
                "  (average pull time nanos)%lld, (max pull time nanos)%lld, (average pull delay "
                "nanos)%lld, "
                "  (max pull delay nanos)%lld, (data error)%ld\n"
                "  (pull timeout)%ld, (pull exceed max delay)%ld"
                "  (no uid provider count)%ld, (no puller found count)%ld\n"
                "  (registered count) %ld, (unregistered count) %ld"
                "  (atom error count) %d, (subscription pull count) %d, (binder call failed) %ld\n",
                (int)pair.first, (long)pair.second.totalPull, (long)pair.second.totalPullFromCache,
                (long)pair.second.pullFailed, (long)pair.second.minPullIntervalSec,
                (long long)pair.second.avgPullTimeNs, (long long)pair.second.maxPullTimeNs,
                (long long)pair.second.avgPullDelayNs, (long long)pair.second.maxPullDelayNs,
                pair.second.dataError, pair.second.pullTimeout, pair.second.pullExceedMaxDelay,
                pair.second.pullUidProviderNotFound, pair.second.pullerNotFound,
                pair.second.registeredCount, pair.second.unregisteredCount,
                pair.second.atomErrorCount, pair.second.subscriptionPullCount,
                pair.second.binderCallFailCount);
        if (pair.second.pullTimeoutMetadata.size() > 0) {
            string uptimeMillis = "(pull timeout system uptime millis) ";
            string pullTimeoutMillis = "(pull timeout elapsed time millis) ";
            for (const auto& stats : pair.second.pullTimeoutMetadata) {
                uptimeMillis.append(to_string(stats.pullTimeoutUptimeMillis)).append(",");
                pullTimeoutMillis.append(to_string(stats.pullTimeoutElapsedMillis)).append(",");
            }
            uptimeMillis.pop_back();
            uptimeMillis.push_back('\n');
            pullTimeoutMillis.pop_back();
            pullTimeoutMillis.push_back('\n');
            dprintf(out, "%s", uptimeMillis.c_str());
            dprintf(out, "%s", pullTimeoutMillis.c_str());
        }
    }

    if (mAnomalyAlarmRegisteredStats > 0) {
        dprintf(out, "********AnomalyAlarmStats stats***********\n");
        dprintf(out, "Anomaly alarm registrations: %d\n", mAnomalyAlarmRegisteredStats);
    }

    if (mPeriodicAlarmRegisteredStats > 0) {
        dprintf(out, "********SubscriberAlarmStats stats***********\n");
        dprintf(out, "Subscriber alarm registrations: %d\n", mPeriodicAlarmRegisteredStats);
    }

    dprintf(out, "UID map stats: bytes=%d, changes=%d, deleted=%d, changes lost=%d\n",
            mUidMapStats.bytes_used, mUidMapStats.changes, mUidMapStats.deleted_apps,
            mUidMapStats.dropped_changes);

    for (const auto& restart : mSystemServerRestartSec) {
        dprintf(out, "System server restarts at %s(%lld)\n", buildTimeString(restart).c_str(),
                (long long)restart);
    }

    for (const auto& loss : mLogLossStats) {
        dprintf(out,
                "Log loss: %lld (wall clock sec) - %d (count), %d (last error), %d (last tag), %d "
                "(uid), %d (pid)\n",
                (long long)loss.mWallClockSec, loss.mCount, loss.mLastError, loss.mLastTag,
                loss.mUid, loss.mPid);
    }

    if (mSocketLossStats.size()) {
        dprintf(out, "********SocketLossStats stats***********\n");
        for (const auto& loss : mSocketLossStats) {
            dprintf(out, "Socket loss: %d (uid), first loss at %lld, last loss at %lld\n",
                    loss.mUid, (long long)loss.mFirstLossTsNanos, (long long)loss.mLastLossTsNanos);
            for (const auto& counterInfo : loss.mLossCountPerErrorAtomId) {
                dprintf(out, "\t\t %d (atomId) %d (error), %d (count)\n", counterInfo.mAtomId,
                        counterInfo.mError, counterInfo.mCount);
            }
        }
    }

    if (mSocketLossStatsOverflowCounters.size()) {
        dprintf(out, "********mSocketLossStatsOverflowCounters stats***********\n");
        for (const auto& overflow : mSocketLossStatsOverflowCounters) {
            dprintf(out, "Socket loss overflow for %d uid is %d times\n", overflow.first,
                    overflow.second);
        }
    }

    dprintf(out, "********EventQueueOverflow stats***********\n");
    dprintf(out, "Event queue overflow: %d; MaxHistoryNs: %lld; MinHistoryNs: %lld\n",
            mOverflowCount, (long long)mMaxQueueHistoryNs, (long long)mMinQueueHistoryNs);
    dprintf(out, "Event queue max size: %d; Observed at : %lld\n", mEventQueueMaxSizeObserved,
            (long long)mEventQueueMaxSizeObservedElapsedNanos);

    if (mActivationBroadcastGuardrailStats.size() > 0) {
        dprintf(out, "********mActivationBroadcastGuardrail stats***********\n");
        for (const auto& pair: mActivationBroadcastGuardrailStats) {
            dprintf(out, "Uid %d: Times: ", pair.first);
            for (const auto& guardrailHitTime : pair.second) {
                dprintf(out, "%d ", guardrailHitTime);
            }
        }
        dprintf(out, "\n");
    }

    dprintf(out, "********Atom Subscription stats***********\n");
    dprintf(out, "Pull thread wakeup count: %d\n", mSubscriptionPullThreadWakeupCount);
    for (const auto& [id, subStats] : mSubscriptionStats) {
        dprintf(out,
                "Subscription %d: pushed_atom_count=%d, pulled_atom_count=%d, flush_count=%d\n", id,
                subStats.pushed_atom_count, subStats.pulled_atom_count, subStats.flush_count);
        dprintf(out, "\n");
    }

    if (mRestrictedMetricQueryStats.size() > 0) {
        dprintf(out, "********Restricted Metric Query stats***********\n");
        for (const auto& stat : mRestrictedMetricQueryStats) {
            if (stat.mHasError) {
                dprintf(out,
                        "Query with error type: %d - %lld (query time ns), "
                        "%d (calling uid), %lld (config id), %s (config package), %s (error)\n",
                        stat.mInvalidQueryReason.value(), (long long)stat.mQueryWallTimeNs,
                        stat.mCallingUid, (long long)stat.mConfigId, stat.mConfigPackage.c_str(),
                        stat.mError.c_str());
            } else {
                dprintf(out,
                        "Query succeed - %lld (query time ns), %d (calling uid), "
                        "%lld (config id), %s (config package), %d (config uid), "
                        "%lld (queryLatencyNs)\n",
                        (long long)stat.mQueryWallTimeNs, stat.mCallingUid,
                        (long long)stat.mConfigId, stat.mConfigPackage.c_str(),
                        stat.mConfigUid.value(), (long long)stat.mQueryLatencyNs.value());
            }
        }
    }
    dprintf(out, "********Statsd Stats Id***********\n");
    dprintf(out, "Statsd Stats Id %d\n", mStatsdStatsId);
    dprintf(out, "********Shard Offset Provider stats***********\n");
    dprintf(out, "Shard Offset: %u\n", ShardOffsetProvider::getInstance().getShardOffset());
}

void addConfigStatsToProto(const ConfigStats& configStats, ProtoOutputStream* proto) {
    uint64_t token =
            proto->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED | FIELD_ID_CONFIG_STATS);
    proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_UID, configStats.uid);
    proto->write(FIELD_TYPE_INT64 | FIELD_ID_CONFIG_STATS_ID, (long long)configStats.id);
    proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_CREATION, configStats.creation_time_sec);
    if (configStats.reset_time_sec != 0) {
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_RESET, configStats.reset_time_sec);
    }
    if (configStats.deletion_time_sec != 0) {
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_DELETION,
                     configStats.deletion_time_sec);
    }
    proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_METRIC_COUNT, configStats.metric_count);
    proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_CONDITION_COUNT,
                 configStats.condition_count);
    proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_MATCHER_COUNT, configStats.matcher_count);
    proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_ALERT_COUNT, configStats.alert_count);
    proto->write(FIELD_TYPE_BOOL | FIELD_ID_CONFIG_STATS_VALID, configStats.is_valid);

    if (!configStats.is_valid) {
        uint64_t tmpToken =
                proto->start(FIELD_TYPE_MESSAGE | FIELD_ID_CONFIG_STATS_INVALID_CONFIG_REASON);
        proto->write(FIELD_TYPE_ENUM | FIELD_ID_INVALID_CONFIG_REASON_ENUM,
                     configStats.reason->reason);
        if (configStats.reason->metricId.has_value()) {
            proto->write(FIELD_TYPE_INT64 | FIELD_ID_INVALID_CONFIG_REASON_METRIC_ID,
                         configStats.reason->metricId.value());
        }
        if (configStats.reason->stateId.has_value()) {
            proto->write(FIELD_TYPE_INT64 | FIELD_ID_INVALID_CONFIG_REASON_STATE_ID,
                         configStats.reason->stateId.value());
        }
        if (configStats.reason->alertId.has_value()) {
            proto->write(FIELD_TYPE_INT64 | FIELD_ID_INVALID_CONFIG_REASON_ALERT_ID,
                         configStats.reason->alertId.value());
        }
        if (configStats.reason->alarmId.has_value()) {
            proto->write(FIELD_TYPE_INT64 | FIELD_ID_INVALID_CONFIG_REASON_ALARM_ID,
                         configStats.reason->alarmId.value());
        }
        if (configStats.reason->subscriptionId.has_value()) {
            proto->write(FIELD_TYPE_INT64 | FIELD_ID_INVALID_CONFIG_REASON_SUBSCRIPTION_ID,
                         configStats.reason->subscriptionId.value());
        }
        for (const auto& matcherId : configStats.reason->matcherIds) {
            proto->write(FIELD_TYPE_INT64 | FIELD_COUNT_REPEATED |
                                 FIELD_ID_INVALID_CONFIG_REASON_MATCHER_ID,
                         matcherId);
        }
        for (const auto& conditionId : configStats.reason->conditionIds) {
            proto->write(FIELD_TYPE_INT64 | FIELD_COUNT_REPEATED |
                                 FIELD_ID_INVALID_CONFIG_REASON_CONDITION_ID,
                         conditionId);
        }
        proto->end(tmpToken);
    }

    for (const auto& broadcast : configStats.broadcast_sent_time_sec) {
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_BROADCAST | FIELD_COUNT_REPEATED,
                     broadcast);
    }

    for (const auto& activation : configStats.activation_time_sec) {
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_ACTIVATION | FIELD_COUNT_REPEATED,
                     activation);
    }

    for (const auto& deactivation : configStats.deactivation_time_sec) {
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_DEACTIVATION | FIELD_COUNT_REPEATED,
                     deactivation);
    }

    for (const auto& drop_time : configStats.data_drop_time_sec) {
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_DATA_DROP_TIME | FIELD_COUNT_REPEATED,
                     drop_time);
    }

    for (const auto& drop_bytes : configStats.data_drop_bytes) {
        proto->write(
                FIELD_TYPE_INT64 | FIELD_ID_CONFIG_STATS_DATA_DROP_BYTES | FIELD_COUNT_REPEATED,
                (long long)drop_bytes);
    }

    for (const auto& dump : configStats.dump_report_stats) {
        proto->write(
                FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_DUMP_REPORT_TIME | FIELD_COUNT_REPEATED,
                dump.mDumpReportTimeSec);
    }

    for (const auto& dump : configStats.dump_report_stats) {
        proto->write(
                FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_DUMP_REPORT_BYTES | FIELD_COUNT_REPEATED,
                (long long)dump.mDumpReportSizeBytes);
    }

    for (const auto& dump : configStats.dump_report_stats) {
        proto->write(
                FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_DUMP_REPORT_NUMBER | FIELD_COUNT_REPEATED,
                dump.mDumpReportNumber);
    }

    for (const auto& annotation : configStats.annotations) {
        uint64_t token = proto->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                      FIELD_ID_CONFIG_STATS_ANNOTATION);
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_CONFIG_STATS_ANNOTATION_INT64,
                     (long long)annotation.first);
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_ANNOTATION_INT32, annotation.second);
        proto->end(token);
    }

    for (const auto& pair : configStats.matcher_stats) {
        uint64_t tmpToken = proto->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                          FIELD_ID_CONFIG_STATS_MATCHER_STATS);
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_MATCHER_STATS_ID, (long long)pair.first);
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_MATCHER_STATS_COUNT, pair.second);
        proto->end(tmpToken);
    }

    for (const auto& pair : configStats.condition_stats) {
        uint64_t tmpToken = proto->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                          FIELD_ID_CONFIG_STATS_CONDITION_STATS);
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_CONDITION_STATS_ID, (long long)pair.first);
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_CONDITION_STATS_COUNT, pair.second);
        proto->end(tmpToken);
    }

    for (const auto& pair : configStats.metric_stats) {
        uint64_t tmpToken = proto->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                          FIELD_ID_CONFIG_STATS_METRIC_STATS);
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_METRIC_STATS_ID, (long long)pair.first);
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_METRIC_STATS_COUNT, pair.second);
        proto->end(tmpToken);
    }
    for (const auto& pair : configStats.metric_dimension_in_condition_stats) {
        uint64_t tmpToken = proto->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                         FIELD_ID_CONFIG_STATS_METRIC_DIMENSION_IN_CONDITION_STATS);
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_METRIC_STATS_ID, (long long)pair.first);
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_METRIC_STATS_COUNT, pair.second);
        proto->end(tmpToken);
    }

    for (const auto& pair : configStats.alert_stats) {
        uint64_t tmpToken = proto->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                          FIELD_ID_CONFIG_STATS_ALERT_STATS);
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_ALERT_STATS_ID, (long long)pair.first);
        proto->write(FIELD_TYPE_INT32 | FIELD_ID_ALERT_STATS_COUNT, pair.second);
        proto->end(tmpToken);
    }

    for (const auto& pair : configStats.restricted_metric_stats) {
        uint64_t token =
                proto->start(FIELD_TYPE_MESSAGE | FIELD_ID_CONFIG_STATS_RESTRICTED_METRIC_STATS |
                             FIELD_COUNT_REPEATED);

        proto->write(FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_STATS_METRIC_ID, (long long)pair.first);
        writeNonZeroStatToStream(FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_STATS_INSERT_ERROR,
                                 (long long)pair.second.insertError, proto);
        writeNonZeroStatToStream(FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_STATS_TABLE_CREATION_ERROR,
                                 (long long)pair.second.tableCreationError, proto);
        writeNonZeroStatToStream(FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_STATS_TABLE_DELETION_ERROR,
                                 (long long)pair.second.tableDeletionError, proto);
        for (const int64_t flushLatencyNs : pair.second.flushLatencyNs) {
            proto->write(FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_STATS_FLUSH_LATENCY |
                                 FIELD_COUNT_REPEATED,
                         flushLatencyNs);
        }
        writeNonZeroStatToStream(
                FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_STATS_CATEGORY_CHANGED_COUNT,
                (long long)pair.second.categoryChangedCount, proto);
        proto->end(token);
    }
    writeNonZeroStatToStream(
            FIELD_TYPE_BOOL | FIELD_ID_CONFIG_STATS_DEVICE_INFO_TABLE_CREATION_FAILED,
            configStats.device_info_table_creation_failed, proto);
    writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_CONFIG_STATS_RESTRICTED_DB_CORRUPTED_COUNT,
                             configStats.db_corrupted_count, proto);
    writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_DB_DELETION_STAT_FAILED,
                             configStats.db_deletion_size_exceeded_limit, proto);
    writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_DB_DELETION_SIZE_EXCEEDED_LIMIT,
                             configStats.db_deletion_size_exceeded_limit, proto);
    writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_DB_DELETION_CONFIG_INVALID,
                             configStats.db_deletion_config_invalid, proto);
    writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_DB_DELETION_TOO_OLD,
                             configStats.db_deletion_too_old, proto);
    writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_DB_DELETION_CONFIG_REMOVED,
                             configStats.db_deletion_config_removed, proto);
    writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_DB_DELETION_CONFIG_UPDATED,
                             configStats.db_deletion_config_updated, proto);
    for (int64_t latency : configStats.total_flush_latency_ns) {
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_CONFIG_STATS_RESTRICTED_CONFIG_FLUSH_LATENCY |
                             FIELD_COUNT_REPEATED,
                     latency);
    }
    for (int64_t dbSizeTimestamp : configStats.total_db_size_timestamps) {
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_CONFIG_STATS_RESTRICTED_CONFIG_DB_SIZE_TIME_SEC |
                             FIELD_COUNT_REPEATED,
                     dbSizeTimestamp);
    }
    for (int64_t dbSize : configStats.total_db_sizes) {
        proto->write(FIELD_TYPE_INT64 | FIELD_ID_CONFIG_STATS_RESTRICTED_CONFIG_DB_SIZE_BYTES |
                             FIELD_COUNT_REPEATED,
                     dbSize);
    }
    proto->end(token);
}

void StatsdStats::dumpStats(std::vector<uint8_t>* output, bool reset) {
    lock_guard<std::mutex> lock(mLock);

    ProtoOutputStream proto;
    proto.write(FIELD_TYPE_INT32 | FIELD_ID_BEGIN_TIME, mStartTimeSec);
    proto.write(FIELD_TYPE_INT32 | FIELD_ID_END_TIME, (int32_t)getWallClockSec());

    for (const auto& configStats : mIceBox) {
        addConfigStatsToProto(*configStats, &proto);
    }

    for (auto& pair : mConfigStats) {
        addConfigStatsToProto(*(pair.second), &proto);
    }

    const size_t atomCounts = mPushedAtomStats.size();
    for (size_t i = 2; i < atomCounts; i++) {
        if (mPushedAtomStats[i].logCount > 0) {
            uint64_t token =
                    proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_ATOM_STATS | FIELD_COUNT_REPEATED);
            proto.write(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_TAG, (int32_t)i);
            proto.write(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_COUNT, mPushedAtomStats[i].logCount);
            const int errors = getPushedAtomErrorsLocked(i);
            writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_ERROR_COUNT, errors,
                                     &proto);
            const int drops = getPushedAtomDropsLocked(i);
            writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_DROPS_COUNT, drops,
                                     &proto);
            writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_SKIP_COUNT,
                                     mPushedAtomStats[i].skipCount, &proto);
            proto.end(token);
        }
    }

    for (const auto& pair : mNonPlatformPushedAtomStats) {
        uint64_t token =
                proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_ATOM_STATS | FIELD_COUNT_REPEATED);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_TAG, pair.first);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_COUNT, pair.second.logCount);
        const int errors = getPushedAtomErrorsLocked(pair.first);
        writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_ERROR_COUNT, errors,
                                 &proto);
        const int drops = getPushedAtomDropsLocked(pair.first);
        writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_DROPS_COUNT, drops, &proto);
        writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_ATOM_STATS_SKIP_COUNT,
                                 pair.second.skipCount, &proto);
        proto.end(token);
    }

    for (const auto& pair : mPulledAtomStats) {
        writePullerStatsToStream(pair, &proto);
    }

    for (const auto& pair : mAtomMetricStats) {
        writeAtomMetricStatsToStream(pair, &proto);
    }

    if (mAnomalyAlarmRegisteredStats > 0) {
        uint64_t token = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_ANOMALY_ALARM_STATS);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_ANOMALY_ALARMS_REGISTERED,
                    mAnomalyAlarmRegisteredStats);
        proto.end(token);
    }

    if (mPeriodicAlarmRegisteredStats > 0) {
        uint64_t token = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_PERIODIC_ALARM_STATS);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_PERIODIC_ALARMS_REGISTERED,
                    mPeriodicAlarmRegisteredStats);
        proto.end(token);
    }

    uint64_t uidMapToken = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_UIDMAP_STATS);
    proto.write(FIELD_TYPE_INT32 | FIELD_ID_UID_MAP_CHANGES, mUidMapStats.changes);
    proto.write(FIELD_TYPE_INT32 | FIELD_ID_UID_MAP_BYTES_USED, mUidMapStats.bytes_used);
    proto.write(FIELD_TYPE_INT32 | FIELD_ID_UID_MAP_DROPPED_CHANGES, mUidMapStats.dropped_changes);
    proto.write(FIELD_TYPE_INT32 | FIELD_ID_UID_MAP_DELETED_APPS, mUidMapStats.deleted_apps);
    proto.end(uidMapToken);

    for (const auto& error : mLogLossStats) {
        uint64_t token = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_LOGGER_ERROR_STATS |
                                      FIELD_COUNT_REPEATED);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_LOG_LOSS_STATS_TIME, error.mWallClockSec);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_LOG_LOSS_STATS_COUNT, error.mCount);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_LOG_LOSS_STATS_ERROR, error.mLastError);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_LOG_LOSS_STATS_TAG, error.mLastTag);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_LOG_LOSS_STATS_UID, error.mUid);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_LOG_LOSS_STATS_PID, error.mPid);
        proto.end(token);
    }

    if (mOverflowCount > 0) {
        uint64_t token = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_OVERFLOW);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_OVERFLOW_COUNT, (int32_t)mOverflowCount);
        proto.write(FIELD_TYPE_INT64 | FIELD_ID_OVERFLOW_MAX_HISTORY,
                    (long long)mMaxQueueHistoryNs);
        proto.write(FIELD_TYPE_INT64 | FIELD_ID_OVERFLOW_MIN_HISTORY,
                    (long long)mMinQueueHistoryNs);
        proto.end(token);
    }

    uint64_t queueStatsToken = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_QUEUE_STATS);
    proto.write(FIELD_TYPE_INT32 | FIELD_ID_QUEUE_MAX_SIZE_OBSERVED,
                (int32_t)mEventQueueMaxSizeObserved);
    proto.write(FIELD_TYPE_INT64 | FIELD_ID_QUEUE_MAX_SIZE_OBSERVED_ELAPSED_NANOS,
                (long long)mEventQueueMaxSizeObservedElapsedNanos);
    proto.end(queueStatsToken);

    for (const auto& restart : mSystemServerRestartSec) {
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_SYSTEM_SERVER_RESTART | FIELD_COUNT_REPEATED,
                    restart);
    }

    for (const auto& pair: mActivationBroadcastGuardrailStats) {
        uint64_t token = proto.start(FIELD_TYPE_MESSAGE |
                                     FIELD_ID_ACTIVATION_BROADCAST_GUARDRAIL |
                                     FIELD_COUNT_REPEATED);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_ACTIVATION_BROADCAST_GUARDRAIL_UID,
                    (int32_t) pair.first);
        for (const auto& guardrailHitTime : pair.second) {
            proto.write(FIELD_TYPE_INT32 | FIELD_ID_ACTIVATION_BROADCAST_GUARDRAIL_TIME |
                            FIELD_COUNT_REPEATED,
                        guardrailHitTime);
        }
        proto.end(token);
    }

    for (const auto& stat : mRestrictedMetricQueryStats) {
        uint64_t token = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS |
                                     FIELD_COUNT_REPEATED);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_CALLING_UID,
                    stat.mCallingUid);
        proto.write(FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_CONFIG_ID,
                    stat.mConfigId);
        proto.write(FIELD_TYPE_STRING | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_CONFIG_PACKAGE,
                    stat.mConfigPackage);
        if (stat.mConfigUid.has_value()) {
            proto.write(FIELD_TYPE_INT32 | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_CONFIG_UID,
                        stat.mConfigUid.value());
        }
        if (stat.mInvalidQueryReason.has_value()) {
            proto.write(
                    FIELD_TYPE_ENUM | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_INVALID_QUERY_REASON,
                    stat.mInvalidQueryReason.value());
        }
        proto.write(FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_QUERY_WALL_TIME_NS,
                    stat.mQueryWallTimeNs);
        proto.write(FIELD_TYPE_BOOL | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_HAS_ERROR,
                    stat.mHasError);
        if (stat.mHasError && !stat.mError.empty()) {
            proto.write(FIELD_TYPE_STRING | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_ERROR,
                        stat.mError);
        }
        if (stat.mQueryLatencyNs.has_value()) {
            proto.write(FIELD_TYPE_INT64 | FIELD_ID_RESTRICTED_METRIC_QUERY_STATS_LATENCY_NS,
                        stat.mQueryLatencyNs.value());
        }
        proto.end(token);
    }

    proto.write(FIELD_TYPE_UINT32 | FIELD_ID_SHARD_OFFSET,
                static_cast<long>(ShardOffsetProvider::getInstance().getShardOffset()));

    proto.write(FIELD_TYPE_INT32 | FIELD_ID_STATSD_STATS_ID, mStatsdStatsId);

    // Write subscription stats
    const uint64_t token = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_SUBSCRIPTION_STATS);
    for (const auto& [id, subStats] : mSubscriptionStats) {
        const uint64_t token = proto.start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                           FIELD_ID_SUBSCRIPTION_STATS_PER_SUBSCRIPTION_STATS);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_PER_SUBSCRIPTION_STATS_ID, id);
        writeNonZeroStatToStream(
                FIELD_TYPE_INT32 | FIELD_ID_PER_SUBSCRIPTION_STATS_PUSHED_ATOM_COUNT,
                subStats.pushed_atom_count, &proto);
        writeNonZeroStatToStream(
                FIELD_TYPE_INT32 | FIELD_ID_PER_SUBSCRIPTION_STATS_PULLED_ATOM_COUNT,
                subStats.pulled_atom_count, &proto);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_PER_SUBSCRIPTION_STATS_START_TIME,
                    subStats.start_time_sec);
        writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_PER_SUBSCRIPTION_STATS_END_TIME,
                                 subStats.end_time_sec, &proto);
        writeNonZeroStatToStream(FIELD_TYPE_INT32 | FIELD_ID_PER_SUBSCRIPTION_STATS_FLUSH_COUNT,
                                 subStats.flush_count, &proto);
        proto.end(token);
    }
    writeNonZeroStatToStream(
            FIELD_TYPE_INT32 | FIELD_ID_SUBSCRIPTION_STATS_PULL_THREAD_WAKEUP_COUNT,
            mSubscriptionPullThreadWakeupCount, &proto);
    proto.end(token);

    // libstatssocket specific stats

    const uint64_t socketLossStatsToken =
            proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_SOCKET_LOSS_STATS);

    // socket loss stats info per uid/error/atom id counter
    for (const auto& perUidLossInfo : mSocketLossStats) {
        uint64_t token = proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_SOCKET_LOSS_STATS_PER_UID |
                                     FIELD_COUNT_REPEATED);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_SOCKET_LOSS_STATS_UID, perUidLossInfo.mUid);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_SOCKET_LOSS_STATS_FIRST_TIMESTAMP_NANOS,
                    perUidLossInfo.mFirstLossTsNanos);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_SOCKET_LOSS_STATS_LAST_TIMESTAMP_NANOS,
                    perUidLossInfo.mLastLossTsNanos);
        for (const auto& counterInfo : perUidLossInfo.mLossCountPerErrorAtomId) {
            uint64_t token =
                    proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_SOCKET_LOSS_ATOM_ID_LOSS_STATS |
                                FIELD_COUNT_REPEATED);
            proto.write(FIELD_TYPE_INT32 | FIELD_ID_ATOM_ID_LOSS_STATS_ATOM_ID,
                        counterInfo.mAtomId);
            proto.write(FIELD_TYPE_INT32 | FIELD_ID_ATOM_ID_LOSS_STATS_ERROR, counterInfo.mError);
            proto.write(FIELD_TYPE_INT32 | FIELD_ID_ATOM_ID_LOSS_STATS_COUNT, counterInfo.mCount);
            proto.end(token);
        }
        proto.end(token);
    }

    // socket loss stats overflow counters
    for (const auto& overflowInfo : mSocketLossStatsOverflowCounters) {
        uint64_t token =
                proto.start(FIELD_TYPE_MESSAGE | FIELD_ID_SOCKET_LOSS_STATS_OVERFLOW_COUNTERS |
                            FIELD_COUNT_REPEATED);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_SOCKET_LOSS_STATS_OVERFLOW_COUNTERS_UID,
                    overflowInfo.first);
        proto.write(FIELD_TYPE_INT32 | FIELD_ID_SOCKET_LOSS_STATS_OVERFLOW_COUNTERS_COUNT,
                    overflowInfo.second);
        proto.end(token);
    }

    proto.end(socketLossStatsToken);

    output->clear();
    proto.serializeToVector(output);

    if (reset) {
        resetInternalLocked();
    }

    VLOG("reset=%d, returned proto size %lu", reset, (unsigned long)output->size());
}

std::pair<size_t, size_t> StatsdStats::getAtomDimensionKeySizeLimits(int atomId,
                                                                     size_t defaultHardLimit) {
    return kAtomDimensionKeySizeLimitMap.find(atomId) != kAtomDimensionKeySizeLimitMap.end()
                   ? kAtomDimensionKeySizeLimitMap.at(atomId)
                   : std::pair<size_t, size_t>(kDimensionKeySizeSoftLimit, defaultHardLimit);
}

InvalidConfigReason createInvalidConfigReasonWithMatcher(const InvalidConfigReasonEnum reason,
                                                         const int64_t matcherId) {
    InvalidConfigReason invalidConfigReason(reason);
    invalidConfigReason.matcherIds.push_back(matcherId);
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithMatcher(const InvalidConfigReasonEnum reason,
                                                         const int64_t metricId,
                                                         const int64_t matcherId) {
    InvalidConfigReason invalidConfigReason(reason, metricId);
    invalidConfigReason.matcherIds.push_back(matcherId);
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithPredicate(const InvalidConfigReasonEnum reason,
                                                           const int64_t conditionId) {
    InvalidConfigReason invalidConfigReason(reason);
    invalidConfigReason.conditionIds.push_back(conditionId);
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithPredicate(const InvalidConfigReasonEnum reason,
                                                           const int64_t metricId,
                                                           const int64_t conditionId) {
    InvalidConfigReason invalidConfigReason(reason, metricId);
    invalidConfigReason.conditionIds.push_back(conditionId);
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithState(const InvalidConfigReasonEnum reason,
                                                       const int64_t metricId,
                                                       const int64_t stateId) {
    InvalidConfigReason invalidConfigReason(reason, metricId);
    invalidConfigReason.stateId = stateId;
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithAlert(const InvalidConfigReasonEnum reason,
                                                       const int64_t alertId) {
    InvalidConfigReason invalidConfigReason(reason);
    invalidConfigReason.alertId = alertId;
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithAlert(const InvalidConfigReasonEnum reason,
                                                       const int64_t metricId,
                                                       const int64_t alertId) {
    InvalidConfigReason invalidConfigReason(reason, metricId);
    invalidConfigReason.alertId = alertId;
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithAlarm(const InvalidConfigReasonEnum reason,
                                                       const int64_t alarmId) {
    InvalidConfigReason invalidConfigReason(reason);
    invalidConfigReason.alarmId = alarmId;
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithSubscription(const InvalidConfigReasonEnum reason,
                                                              const int64_t subscriptionId) {
    InvalidConfigReason invalidConfigReason(reason);
    invalidConfigReason.subscriptionId = subscriptionId;
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithSubscriptionAndAlarm(
        const InvalidConfigReasonEnum reason, const int64_t subscriptionId, const int64_t alarmId) {
    InvalidConfigReason invalidConfigReason(reason);
    invalidConfigReason.subscriptionId = subscriptionId;
    invalidConfigReason.alarmId = alarmId;
    return invalidConfigReason;
}

InvalidConfigReason createInvalidConfigReasonWithSubscriptionAndAlert(
        const InvalidConfigReasonEnum reason, const int64_t subscriptionId, const int64_t alertId) {
    InvalidConfigReason invalidConfigReason(reason);
    invalidConfigReason.subscriptionId = subscriptionId;
    invalidConfigReason.alertId = alertId;
    return invalidConfigReason;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
