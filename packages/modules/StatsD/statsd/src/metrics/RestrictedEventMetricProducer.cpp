/*
 * Copyright 2023, The Android Open Source Project
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
#define STATSD_DEBUG true
#include "Log.h"

#include "RestrictedEventMetricProducer.h"

#include "stats_annotations.h"
#include "stats_log_util.h"
#include "utils/DbUtils.h"

using std::lock_guard;
using std::vector;

namespace android {
namespace os {
namespace statsd {

#define NS_PER_DAY (24 * 3600 * NS_PER_SEC)

RestrictedEventMetricProducer::RestrictedEventMetricProducer(
        const ConfigKey& key, const EventMetric& metric, const int conditionIndex,
        const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
        const uint64_t protoHash, const int64_t startTimeNs,
        const unordered_map<int, shared_ptr<Activation>>& eventActivationMap,
        const unordered_map<int, vector<shared_ptr<Activation>>>& eventDeactivationMap,
        const vector<int>& slicedStateAtoms,
        const unordered_map<int, unordered_map<int, int64_t>>& stateGroupMap)
    : EventMetricProducer(key, metric, conditionIndex, initialConditionCache, wizard, protoHash,
                          startTimeNs, eventActivationMap, eventDeactivationMap, slicedStateAtoms,
                          stateGroupMap),
      mRestrictedDataCategory(CATEGORY_UNKNOWN) {
}

void RestrictedEventMetricProducer::onMatchedLogEventInternalLocked(
        const size_t matcherIndex, const MetricDimensionKey& eventKey,
        const ConditionKey& conditionKey, bool condition, const LogEvent& event,
        const std::map<int, HashableDimensionKey>& statePrimaryKeys) {
    if (!condition) {
        return;
    }
    if (mRestrictedDataCategory != CATEGORY_UNKNOWN &&
        mRestrictedDataCategory != event.getRestrictionCategory()) {
        StatsdStats::getInstance().noteRestrictedMetricCategoryChanged(mConfigKey, mMetricId);
        deleteMetricTable();
        mLogEvents.clear();
        mTotalSize = 0;
    }
    mRestrictedDataCategory = event.getRestrictionCategory();
    mLogEvents.push_back(event);
    mTotalSize += getSize(event.getValues()) + sizeof(event);
}

void RestrictedEventMetricProducer::onDumpReportLocked(
        const int64_t dumpTimeNs, const bool include_current_partial_bucket, const bool erase_data,
        const DumpLatency dumpLatency, std::set<string>* str_set,
        android::util::ProtoOutputStream* protoOutput) {
    VLOG("Unexpected call to onDumpReportLocked() in RestrictedEventMetricProducer");
}

void RestrictedEventMetricProducer::onMetricRemove() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mIsMetricTableCreated) {
        return;
    }
    deleteMetricTable();
}

void RestrictedEventMetricProducer::enforceRestrictedDataTtl(sqlite3* db,
                                                             const int64_t wallClockNs) {
    int32_t ttlInDays = RestrictedPolicyManager::getInstance().getRestrictedCategoryTtl(
            mRestrictedDataCategory);
    int64_t ttlTime = wallClockNs - ttlInDays * NS_PER_DAY;
    dbutils::flushTtl(db, mMetricId, ttlTime);
}

void RestrictedEventMetricProducer::clearPastBucketsLocked(const int64_t dumpTimeNs) {
    VLOG("Unexpected call to clearPastBucketsLocked in RestrictedEventMetricProducer");
}

void RestrictedEventMetricProducer::dropDataLocked(const int64_t dropTimeNs) {
    mLogEvents.clear();
    mTotalSize = 0;
    StatsdStats::getInstance().noteBucketDropped(mMetricId);
}

void RestrictedEventMetricProducer::flushRestrictedData() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mLogEvents.empty()) {
        return;
    }
    int64_t flushStartNs = getElapsedRealtimeNs();
    if (!mIsMetricTableCreated) {
        if (!dbutils::isEventCompatible(mConfigKey, mMetricId, mLogEvents[0])) {
            // Delete old data if schema changes
            // TODO(b/268150038): report error to statsdstats
            ALOGD("Detected schema change for metric %lld", (long long)mMetricId);
            deleteMetricTable();
        }
        // TODO(b/271481944): add retry.
        if (!dbutils::createTableIfNeeded(mConfigKey, mMetricId, mLogEvents[0])) {
            ALOGE("Failed to create table for metric %lld", (long long)mMetricId);
            StatsdStats::getInstance().noteRestrictedMetricTableCreationError(mConfigKey,
                                                                              mMetricId);
            return;
        }
        mIsMetricTableCreated = true;
    }
    string err;
    if (!dbutils::insert(mConfigKey, mMetricId, mLogEvents, err)) {
        ALOGE("Failed to insert logEvent to table for metric %lld. err=%s", (long long)mMetricId,
              err.c_str());
        StatsdStats::getInstance().noteRestrictedMetricInsertError(mConfigKey, mMetricId);
    } else {
        StatsdStats::getInstance().noteRestrictedMetricFlushLatency(
                mConfigKey, mMetricId, getElapsedRealtimeNs() - flushStartNs);
    }
    mLogEvents.clear();
    mTotalSize = 0;
}

bool RestrictedEventMetricProducer::writeMetricMetadataToProto(
        metadata::MetricMetadata* metricMetadata) {
    metricMetadata->set_metric_id(mMetricId);
    metricMetadata->set_restricted_category(mRestrictedDataCategory);
    return true;
}

void RestrictedEventMetricProducer::loadMetricMetadataFromProto(
        const metadata::MetricMetadata& metricMetadata) {
    mRestrictedDataCategory =
            static_cast<StatsdRestrictionCategory>(metricMetadata.restricted_category());
}

void RestrictedEventMetricProducer::deleteMetricTable() {
    if (!dbutils::deleteTable(mConfigKey, mMetricId)) {
        StatsdStats::getInstance().noteRestrictedMetricTableDeletionError(mConfigKey, mMetricId);
        VLOG("Failed to delete table for metric %lld", (long long)mMetricId);
    }
    mIsMetricTableCreated = false;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
