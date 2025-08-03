/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef METRIC_PRODUCER_H
#define METRIC_PRODUCER_H

#include <src/active_config_list.pb.h>
#include <utils/RefBase.h>

#include <unordered_map>

#include "HashableDimensionKey.h"
#include "anomaly/AnomalyTracker.h"
#include "condition/ConditionTimer.h"
#include "condition/ConditionWizard.h"
#include "config/ConfigKey.h"
#include "guardrail/StatsdStats.h"
#include "matchers/EventMatcherWizard.h"
#include "matchers/matcher_util.h"
#include "packages/PackageInfoListener.h"
#include "src/statsd_metadata.pb.h"  // MetricMetadata
#include "state/StateListener.h"
#include "state/StateManager.h"
#include "utils/DbUtils.h"
#include "utils/ShardOffsetProvider.h"

namespace android {
namespace os {
namespace statsd {

// If the metric has no activation requirement, it will be active once the metric producer is
// created.
// If the metric needs to be activated by atoms, the metric producer will start
// with kNotActive state, turn to kActive or kActiveOnBoot when the activation event arrives, become
// kNotActive when it reaches the duration limit (timebomb). If the activation event arrives again
// before or after it expires, the event producer will be re-activated and ttl will be reset.
enum ActivationState {
    kNotActive = 0,
    kActive = 1,
    kActiveOnBoot = 2,
};

enum DumpLatency {
    // In some cases, we only have a short time range to do the dump, e.g. statsd is being killed.
    // We might be able to return all the data in this mode. For instance, pull metrics might need
    // to be pulled when the current bucket is requested.
    FAST = 1,
    // In other cases, it is fine for a dump to take more than a few milliseconds, e.g. config
    // updates.
    NO_TIME_CONSTRAINTS = 2
};

enum MetricType {
    METRIC_TYPE_EVENT = 1,
    METRIC_TYPE_COUNT = 2,
    METRIC_TYPE_DURATION = 3,
    METRIC_TYPE_GAUGE = 4,
    METRIC_TYPE_VALUE = 5,
    METRIC_TYPE_KLL = 6,
};

struct Activation {
    Activation(const ActivationType& activationType, int64_t ttlNs)
        : ttl_ns(ttlNs),
          start_ns(0),
          state(ActivationState::kNotActive),
          activationType(activationType) {
    }

    const int64_t ttl_ns;
    int64_t start_ns;
    ActivationState state;
    const ActivationType activationType;
};

struct DropEvent {
    // Reason for dropping the bucket and/or marking the bucket invalid.
    BucketDropReason reason;
    // The timestamp of the drop event.
    int64_t dropTimeNs;
};

struct SkippedBucket {
    // Start time of the dropped bucket.
    int64_t bucketStartTimeNs;
    // End time of the dropped bucket.
    int64_t bucketEndTimeNs;
    // List of events that invalidated this bucket.
    std::vector<DropEvent> dropEvents;

    void reset() {
        bucketStartTimeNs = 0;
        bucketEndTimeNs = 0;
        dropEvents.clear();
    }
};

struct SamplingInfo {
    // Matchers for sampled fields. Currently only one sampled dimension is supported.
    std::vector<Matcher> sampledWhatFields;

    int shardCount = 0;
};

template <class T>
optional<bool> getAppUpgradeBucketSplit(const T& metric) {
    return metric.has_split_bucket_for_app_upgrade()
                   ? std::make_optional<bool>(metric.split_bucket_for_app_upgrade())
                   : std::nullopt;
}

// A MetricProducer is responsible for compute one single metric, creating stats log report, and
// writing the report to dropbox. MetricProducers should respond to package changes as required in
// PackageInfoListener, but if none of the metrics are slicing by package name, then the update can
// be a no-op.
class MetricProducer : public virtual RefBase, public virtual StateListener {
public:
    MetricProducer(int64_t metricId, const ConfigKey& key, int64_t timeBaseNs,
                   const int conditionIndex, const vector<ConditionState>& initialConditionCache,
                   const sp<ConditionWizard>& wizard, const uint64_t protoHash,
                   const std::unordered_map<int, std::shared_ptr<Activation>>& eventActivationMap,
                   const std::unordered_map<int, std::vector<std::shared_ptr<Activation>>>&
                           eventDeactivationMap,
                   const vector<int>& slicedStateAtoms,
                   const unordered_map<int, unordered_map<int, int64_t>>& stateGroupMap,
                   const optional<bool> splitBucketForAppUpgrade);

    virtual ~MetricProducer(){};

    ConditionState initialCondition(const int conditionIndex,
                                    const vector<ConditionState>& initialConditionCache) const {
        return conditionIndex >= 0 ? initialConditionCache[conditionIndex] : ConditionState::kTrue;
    }

    // Update appropriate state on config updates. Primarily, all indices need to be updated.
    // This metric and all of its dependencies are guaranteed to be preserved across the update.
    // This function also updates several maps used by metricsManager.
    // This function clears all anomaly trackers. All anomaly trackers need to be added again.
    optional<InvalidConfigReason> onConfigUpdated(
            const StatsdConfig& config, int configIndex, int metricIndex,
            const std::vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
            const std::unordered_map<int64_t, int>& oldAtomMatchingTrackerMap,
            const std::unordered_map<int64_t, int>& newAtomMatchingTrackerMap,
            const sp<EventMatcherWizard>& matcherWizard,
            const std::vector<sp<ConditionTracker>>& allConditionTrackers,
            const std::unordered_map<int64_t, int>& conditionTrackerMap,
            const sp<ConditionWizard>& wizard,
            const std::unordered_map<int64_t, int>& metricToActivationMap,
            std::unordered_map<int, std::vector<int>>& trackerToMetricMap,
            std::unordered_map<int, std::vector<int>>& conditionToMetricMap,
            std::unordered_map<int, std::vector<int>>& activationAtomTrackerToMetricMap,
            std::unordered_map<int, std::vector<int>>& deactivationAtomTrackerToMetricMap,
            std::vector<int>& metricsWithActivation) {
        std::lock_guard<std::mutex> lock(mMutex);
        return onConfigUpdatedLocked(config, configIndex, metricIndex, allAtomMatchingTrackers,
                                     oldAtomMatchingTrackerMap, newAtomMatchingTrackerMap,
                                     matcherWizard, allConditionTrackers, conditionTrackerMap,
                                     wizard, metricToActivationMap, trackerToMetricMap,
                                     conditionToMetricMap, activationAtomTrackerToMetricMap,
                                     deactivationAtomTrackerToMetricMap, metricsWithActivation);
    };

    /**
     * Force a partial bucket split on app upgrade
     */
    void notifyAppUpgrade(int64_t eventTimeNs) {
        std::lock_guard<std::mutex> lock(mMutex);
        const bool splitBucket =
                mSplitBucketForAppUpgrade ? mSplitBucketForAppUpgrade.value() : false;
        if (!splitBucket) {
            return;
        }
        notifyAppUpgradeInternalLocked(eventTimeNs);
    };

    void notifyAppRemoved(int64_t eventTimeNs) {
        // Force buckets to split on removal also.
        notifyAppUpgrade(eventTimeNs);
    };

    /**
     * Force a partial bucket split on boot complete.
     */
    virtual void onStatsdInitCompleted(int64_t eventTimeNs) {
        std::lock_guard<std::mutex> lock(mMutex);
        flushLocked(eventTimeNs);
    }
    // Consume the parsed stats log entry that already matched the "what" of the metric.
    void onMatchedLogEvent(const size_t matcherIndex, const LogEvent& event) {
        std::lock_guard<std::mutex> lock(mMutex);
        onMatchedLogEventLocked(matcherIndex, event);
    }

    void onConditionChanged(const bool condition, int64_t eventTime) {
        std::lock_guard<std::mutex> lock(mMutex);
        onConditionChangedLocked(condition, eventTime);
    }

    void onSlicedConditionMayChange(bool overallCondition, int64_t eventTime) {
        std::lock_guard<std::mutex> lock(mMutex);
        onSlicedConditionMayChangeLocked(overallCondition, eventTime);
    }

    bool isConditionSliced() const {
        std::lock_guard<std::mutex> lock(mMutex);
        return mConditionSliced;
    };

    void onStateChanged(const int64_t eventTimeNs, const int32_t atomId,
                        const HashableDimensionKey& primaryKey, const FieldValue& oldState,
                        const FieldValue& newState){};

    // Output the metrics data to [protoOutput]. All metrics reports end with the same timestamp.
    // This method clears all the past buckets.
    void onDumpReport(const int64_t dumpTimeNs,
                      const bool include_current_partial_bucket,
                      const bool erase_data,
                      const DumpLatency dumpLatency,
                      std::set<string> *str_set,
                      android::util::ProtoOutputStream* protoOutput) {
        std::lock_guard<std::mutex> lock(mMutex);
        return onDumpReportLocked(dumpTimeNs, include_current_partial_bucket, erase_data,
                dumpLatency, str_set, protoOutput);
    }

    virtual optional<InvalidConfigReason> onConfigUpdatedLocked(
            const StatsdConfig& config, int configIndex, int metricIndex,
            const std::vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
            const std::unordered_map<int64_t, int>& oldAtomMatchingTrackerMap,
            const std::unordered_map<int64_t, int>& newAtomMatchingTrackerMap,
            const sp<EventMatcherWizard>& matcherWizard,
            const std::vector<sp<ConditionTracker>>& allConditionTrackers,
            const std::unordered_map<int64_t, int>& conditionTrackerMap,
            const sp<ConditionWizard>& wizard,
            const std::unordered_map<int64_t, int>& metricToActivationMap,
            std::unordered_map<int, std::vector<int>>& trackerToMetricMap,
            std::unordered_map<int, std::vector<int>>& conditionToMetricMap,
            std::unordered_map<int, std::vector<int>>& activationAtomTrackerToMetricMap,
            std::unordered_map<int, std::vector<int>>& deactivationAtomTrackerToMetricMap,
            std::vector<int>& metricsWithActivation);

    void clearPastBuckets(const int64_t dumpTimeNs) {
        std::lock_guard<std::mutex> lock(mMutex);
        return clearPastBucketsLocked(dumpTimeNs);
    }

    void prepareFirstBucket() {
        std::lock_guard<std::mutex> lock(mMutex);
        prepareFirstBucketLocked();
    }

    // Returns the memory in bytes currently used to store this metric's data. Does not change
    // state.
    size_t byteSize() const {
        std::lock_guard<std::mutex> lock(mMutex);
        return byteSizeLocked();
    }

    void dumpStates(int out, bool verbose) const {
        std::lock_guard<std::mutex> lock(mMutex);
        dumpStatesLocked(out, verbose);
    }

    // Let MetricProducer drop in-memory data to save memory.
    // We still need to keep future data valid and anomaly tracking work, which means we will
    // have to flush old data, informing anomaly trackers then safely drop old data.
    // We still keep current bucket data for future metrics' validity.
    void dropData(const int64_t dropTimeNs) {
        std::lock_guard<std::mutex> lock(mMutex);
        dropDataLocked(dropTimeNs);
    }

    void loadActiveMetric(const ActiveMetric& activeMetric, int64_t currentTimeNs) {
        std::lock_guard<std::mutex> lock(mMutex);
        loadActiveMetricLocked(activeMetric, currentTimeNs);
    }

    void activate(int activationTrackerIndex, int64_t elapsedTimestampNs) {
        std::lock_guard<std::mutex> lock(mMutex);
        activateLocked(activationTrackerIndex, elapsedTimestampNs);
    }

    void cancelEventActivation(int deactivationTrackerIndex) {
        std::lock_guard<std::mutex> lock(mMutex);
        cancelEventActivationLocked(deactivationTrackerIndex);
    }

    bool isActive() const {
        std::lock_guard<std::mutex> lock(mMutex);
        return isActiveLocked();
    }

    void flushIfExpire(int64_t elapsedTimestampNs);

    void writeActiveMetricToProtoOutputStream(
            int64_t currentTimeNs, const DumpReportReason reason, ProtoOutputStream* proto);

    virtual void enforceRestrictedDataTtl(sqlite3* db, int64_t wallClockNs){};

    virtual bool writeMetricMetadataToProto(metadata::MetricMetadata* metricMetadata) {
        return false;
    }

    virtual void loadMetricMetadataFromProto(const metadata::MetricMetadata& metricMetadata){};

    /* Called when the metric is to about to be removed from config. */
    virtual void onMetricRemove() {
    }

    virtual void flushRestrictedData() {
    }

    // Start: getters/setters
    inline int64_t getMetricId() const {
        return mMetricId;
    }

    inline uint64_t getProtoHash() const {
        return mProtoHash;
    }

    virtual MetricType getMetricType() const = 0;

    // For test only.
    inline int64_t getCurrentBucketNum() const {
        return mCurrentBucketNum;
    }

    int64_t getBucketSizeInNs() const {
        std::lock_guard<std::mutex> lock(mMutex);
        return mBucketSizeNs;
    }

    inline const std::vector<int> getSlicedStateAtoms() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mSlicedStateAtoms;
    }

    inline bool isValid() const {
        return mValid;
    }

    /* Adds an AnomalyTracker and returns it. */
    virtual sp<AnomalyTracker> addAnomalyTracker(const Alert& alert,
                                                 const sp<AlarmMonitor>& anomalyAlarmMonitor,
                                                 const UpdateStatus& updateStatus,
                                                 const int64_t updateTimeNs) {
        std::lock_guard<std::mutex> lock(mMutex);
        sp<AnomalyTracker> anomalyTracker = new AnomalyTracker(alert, mConfigKey);
        mAnomalyTrackers.push_back(anomalyTracker);
        return anomalyTracker;
    }

    /* Adds an AnomalyTracker that has already been created */
    virtual void addAnomalyTracker(sp<AnomalyTracker>& anomalyTracker, int64_t updateTimeNs) {
        std::lock_guard<std::mutex> lock(mMutex);
        mAnomalyTrackers.push_back(anomalyTracker);
    }

    void setSamplingInfo(SamplingInfo samplingInfo) {
        std::lock_guard<std::mutex> lock(mMutex);
        mSampledWhatFields.swap(samplingInfo.sampledWhatFields);
        mShardCount = samplingInfo.shardCount;
    }
    // End: getters/setters
protected:
    /**
     * Flushes the current bucket if the eventTime is after the current bucket's end time.
     */
    virtual void flushIfNeededLocked(int64_t eventTime){};

    /**
     * For metrics that aggregate (ie, every metric producer except for EventMetricProducer),
     * we need to be able to flush the current buckets on demand (ie, end the current bucket and
     * start new bucket). If this function is called when eventTimeNs is greater than the current
     * bucket's end timestamp, than we flush up to the end of the latest full bucket; otherwise,
     * we assume that we want to flush a partial bucket. The bucket start timestamp and bucket
     * number are not changed by this function. This method should only be called by
     * flushIfNeededLocked or flushLocked or the app upgrade handler; the caller MUST update the
     * bucket timestamp and bucket number as needed.
     */
    virtual void flushCurrentBucketLocked(int64_t eventTimeNs, int64_t nextBucketStartTimeNs){};

    /**
     * Flushes all the data including the current partial bucket.
     */
    void flushLocked(int64_t eventTimeNs) {
        flushIfNeededLocked(eventTimeNs);
        flushCurrentBucketLocked(eventTimeNs, eventTimeNs);
    };

    virtual void notifyAppUpgradeInternalLocked(const int64_t eventTimeNs) {
        flushLocked(eventTimeNs);
    }

    /*
     * Individual metrics can implement their own business logic here. All pre-processing is done.
     *
     * [matcherIndex]: the index of the matcher which matched this event. This is interesting to
     *                 DurationMetric, because it has start/stop/stop_all 3 matchers.
     * [eventKey]: the extracted dimension key for the final output. if the metric doesn't have
     *             dimensions, it will be DEFAULT_DIMENSION_KEY
     * [conditionKey]: the keys of conditions which should be used to query the condition for this
     *                 target event (from MetricConditionLink). This is passed to individual metrics
     *                 because DurationMetric needs it to be cached.
     * [condition]: whether condition is met. If condition is sliced, this is the result coming from
     *              query with ConditionWizard; If condition is not sliced, this is the
     *              nonSlicedCondition.
     * [event]: the log event, just in case the metric needs its data, e.g., EventMetric.
     */
    virtual void onMatchedLogEventInternalLocked(
            const size_t matcherIndex, const MetricDimensionKey& eventKey,
            const ConditionKey& conditionKey, bool condition, const LogEvent& event,
            const map<int, HashableDimensionKey>& statePrimaryKeys) = 0;

    // Consume the parsed stats log entry that already matched the "what" of the metric.
    virtual void onMatchedLogEventLocked(const size_t matcherIndex, const LogEvent& event);
    virtual void onConditionChangedLocked(const bool condition, int64_t eventTime) = 0;
    virtual void onSlicedConditionMayChangeLocked(bool overallCondition,
                                                  const int64_t eventTime) = 0;
    virtual void onDumpReportLocked(const int64_t dumpTimeNs,
                                    const bool include_current_partial_bucket,
                                    const bool erase_data,
                                    const DumpLatency dumpLatency,
                                    std::set<string> *str_set,
                                    android::util::ProtoOutputStream* protoOutput) = 0;
    virtual void clearPastBucketsLocked(const int64_t dumpTimeNs) = 0;
    virtual void prepareFirstBucketLocked(){};
    virtual size_t byteSizeLocked() const = 0;
    virtual void dumpStatesLocked(int out, bool verbose) const = 0;
    virtual void dropDataLocked(const int64_t dropTimeNs) = 0;
    void loadActiveMetricLocked(const ActiveMetric& activeMetric, int64_t currentTimeNs);
    void activateLocked(int activationTrackerIndex, int64_t elapsedTimestampNs);
    void cancelEventActivationLocked(int deactivationTrackerIndex);

    bool evaluateActiveStateLocked(int64_t elapsedTimestampNs);

    virtual void onActiveStateChangedLocked(const int64_t eventTimeNs, const bool isActive) {
        if (!isActive) {
            flushLocked(eventTimeNs);
        }
    }

    inline bool isActiveLocked() const {
        return mIsActive;
    }

    // Convenience to compute the current bucket's end time, which is always aligned with the
    // start time of the metric.
    int64_t getCurrentBucketEndTimeNs() const {
        return mTimeBaseNs + (mCurrentBucketNum + 1) * mBucketSizeNs;
    }

    int64_t getBucketNumFromEndTimeNs(const int64_t endNs) {
        return (endNs - mTimeBaseNs) / mBucketSizeNs - 1;
    }

    // Query StateManager for original state value using the queryKey.
    // The field and value are output.
    void queryStateValue(int32_t atomId, const HashableDimensionKey& queryKey, FieldValue* value);

    // If a state map exists for the given atom, replace the original state
    // value with the group id mapped to the value.
    // If no state map exists, keep the original state value.
    void mapStateValue(int32_t atomId, FieldValue* value);

    // Returns a HashableDimensionKey with unknown state value for each state
    // atom.
    HashableDimensionKey getUnknownStateKey();

    DropEvent buildDropEvent(const int64_t dropTimeNs, const BucketDropReason reason) const;

    // Returns true if the number of drop events in the current bucket has
    // exceeded the maximum number allowed, which is currently capped at 10.
    bool maxDropEventsReached() const;

    bool passesSampleCheckLocked(const vector<FieldValue>& values) const;

    const int64_t mMetricId;

    // Hash of the Metric's proto bytes from StatsdConfig, including any activations.
    // Used to determine if the definition of this metric has changed across a config update.
    const uint64_t mProtoHash;

    const ConfigKey mConfigKey;

    bool mValid;

    // The time when this metric producer was first created. The end time for the current bucket
    // can be computed from this based on mCurrentBucketNum.
    int64_t mTimeBaseNs;

    // Start time may not be aligned with the start of statsd if there is an app upgrade in the
    // middle of a bucket.
    int64_t mCurrentBucketStartTimeNs;

    // Used by anomaly detector to track which bucket we are in. This is not sent with the produced
    // report.
    int64_t mCurrentBucketNum;

    int64_t mBucketSizeNs;

    ConditionState mCondition;

    ConditionTimer mConditionTimer;

    int mConditionTrackerIndex;

    // TODO(b/185770739): use !mMetric2ConditionLinks.empty()
    bool mConditionSliced;

    sp<ConditionWizard> mWizard;

    bool mContainANYPositionInDimensionsInWhat;

    // Metrics slicing by primitive repeated field and/or position ALL need to use nested
    // dimensions.
    bool mShouldUseNestedDimensions;

    vector<Matcher> mDimensionsInWhat;  // The dimensions_in_what defined in statsd_config

    // True iff the metric to condition links cover all dimension fields in the condition tracker.
    // This field is always false for combinational condition trackers.
    bool mHasLinksToAllConditionDimensionsInTracker;

    std::vector<Metric2Condition> mMetric2ConditionLinks;

    std::vector<sp<AnomalyTracker>> mAnomalyTrackers;

    mutable std::mutex mMutex;

    // When the metric producer has multiple activations, these activations are ORed to determine
    // whether the metric producer is ready to generate metrics.
    std::unordered_map<int, std::shared_ptr<Activation>> mEventActivationMap;

    // Maps index of atom matcher for deactivation to a list of Activation structs.
    std::unordered_map<int, std::vector<std::shared_ptr<Activation>>> mEventDeactivationMap;

    bool mIsActive;

    // The slice_by_state atom ids defined in statsd_config.
    const std::vector<int32_t> mSlicedStateAtoms;

    // Maps atom ids and state values to group_ids (<atom_id, <value, group_id>>).
    const std::unordered_map<int32_t, std::unordered_map<int, int64_t>> mStateGroupMap;

    // MetricStateLinks defined in statsd_config that link fields in the state
    // atom to fields in the "what" atom.
    std::vector<Metric2State> mMetric2StateLinks;

    optional<UploadThreshold> mUploadThreshold;

    const optional<bool> mSplitBucketForAppUpgrade;

    SkippedBucket mCurrentSkippedBucket;
    // Buckets that were invalidated and had their data dropped.
    std::vector<SkippedBucket> mSkippedBuckets;

    // If hard dimension guardrail is hit, do not spam logcat. This is a per bucket tracker.
    mutable bool mHasHitGuardrail;

    // Matchers for sampled fields. Currently only one sampled dimension is supported.
    std::vector<Matcher> mSampledWhatFields;

    int mShardCount;

    FRIEND_TEST(CountMetricE2eTest, TestSlicedState);
    FRIEND_TEST(CountMetricE2eTest, TestSlicedStateWithMap);
    FRIEND_TEST(CountMetricE2eTest, TestMultipleSlicedStates);
    FRIEND_TEST(CountMetricE2eTest, TestSlicedStateWithPrimaryFields);
    FRIEND_TEST(CountMetricE2eTest, TestInitialConditionChanges);

    FRIEND_TEST(DurationMetricE2eTest, TestOneBucket);
    FRIEND_TEST(DurationMetricE2eTest, TestTwoBuckets);
    FRIEND_TEST(DurationMetricE2eTest, TestWithActivation);
    FRIEND_TEST(DurationMetricE2eTest, TestWithCondition);
    FRIEND_TEST(DurationMetricE2eTest, TestWithSlicedCondition);
    FRIEND_TEST(DurationMetricE2eTest, TestWithActivationAndSlicedCondition);
    FRIEND_TEST(DurationMetricE2eTest, TestWithSlicedState);
    FRIEND_TEST(DurationMetricE2eTest, TestWithConditionAndSlicedState);
    FRIEND_TEST(DurationMetricE2eTest, TestWithSlicedStateMapped);
    FRIEND_TEST(DurationMetricE2eTest, TestSlicedStatePrimaryFieldsNotSubsetDimInWhat);
    FRIEND_TEST(DurationMetricE2eTest, TestWithSlicedStatePrimaryFieldsSubset);
    FRIEND_TEST(DurationMetricE2eTest, TestUploadThreshold);

    FRIEND_TEST(MetricActivationE2eTest, TestCountMetric);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetricWithOneDeactivation);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetricWithTwoDeactivations);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetricWithSameDeactivation);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetricWithTwoMetricsTwoDeactivations);

    FRIEND_TEST(StatsLogProcessorTest, TestActiveConfigMetricDiskWriteRead);
    FRIEND_TEST(StatsLogProcessorTest, TestActivationOnBoot);
    FRIEND_TEST(StatsLogProcessorTest, TestActivationOnBootMultipleActivations);
    FRIEND_TEST(StatsLogProcessorTest,
            TestActivationOnBootMultipleActivationsDifferentActivationTypes);
    FRIEND_TEST(StatsLogProcessorTest, TestActivationsPersistAcrossSystemServerRestart);

    FRIEND_TEST(ValueMetricE2eTest, TestInitWithSlicedState);
    FRIEND_TEST(ValueMetricE2eTest, TestInitWithSlicedState_WithDimensions);
    FRIEND_TEST(ValueMetricE2eTest, TestInitWithSlicedState_WithIncorrectDimensions);
    FRIEND_TEST(ValueMetricE2eTest, TestInitialConditionChanges);

    FRIEND_TEST(MetricsManagerUtilTest, TestInitialConditions);
    FRIEND_TEST(MetricsManagerUtilTest, TestSampledMetrics);

    FRIEND_TEST(ConfigUpdateTest, TestUpdateMetricActivations);
    FRIEND_TEST(ConfigUpdateTest, TestUpdateCountMetrics);
    FRIEND_TEST(ConfigUpdateTest, TestUpdateEventMetrics);
    FRIEND_TEST(ConfigUpdateTest, TestUpdateGaugeMetrics);
    FRIEND_TEST(ConfigUpdateTest, TestUpdateDurationMetrics);
    FRIEND_TEST(ConfigUpdateTest, TestUpdateMetricsMultipleTypes);
    FRIEND_TEST(ConfigUpdateTest, TestUpdateAlerts);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
#endif  // METRIC_PRODUCER_H
