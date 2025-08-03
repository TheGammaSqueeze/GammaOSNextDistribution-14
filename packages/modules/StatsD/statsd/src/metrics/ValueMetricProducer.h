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

#pragma once

#include <gtest/gtest_prod.h>

#include <optional>

#include "FieldValue.h"
#include "HashableDimensionKey.h"
#include "MetricProducer.h"
#include "anomaly/AnomalyTracker.h"
#include "condition/ConditionTracker.h"
#include "external/PullDataReceiver.h"
#include "external/StatsPullerManager.h"
#include "matchers/EventMatcherWizard.h"
#include "src/statsd_config.pb.h"
#include "stats_log_util.h"
#include "stats_util.h"

namespace android {
namespace os {
namespace statsd {

template <typename AggregatedValue>
struct PastBucket {
    int64_t mBucketStartNs;
    int64_t mBucketEndNs;
    std::vector<int> aggIndex;
    std::vector<AggregatedValue> aggregates;
    std::vector<int> sampleSizes;

    /**
     * If the metric has no condition, then this field is just wasted.
     * When we tune statsd memory usage in the future, this is a candidate to optimize.
     */
    int64_t mConditionTrueNs;

    /**
     * The semantic is the value which needs to be applied to mConditionTrueNs for correction
     * to be performed prior normalization calculation on the user (read server) side. Applied only
     * to ValueMetrics with pulled atoms.
     */
    int64_t mConditionCorrectionNs;
};

// Aggregates values within buckets.
//
// There are different events that might complete a bucket
// - a condition change
// - an app upgrade
// - an alarm set to the end of the bucket
template <typename AggregatedValue, typename DimExtras>
class ValueMetricProducer : public MetricProducer, public virtual PullDataReceiver {
public:
    struct PullOptions {
        const int pullAtomId;
        const sp<StatsPullerManager>& pullerManager;
    };

    struct BucketOptions {
        const int64_t timeBaseNs;
        const int64_t startTimeNs;
        const int64_t bucketSizeNs;
        const int64_t minBucketSizeNs;
        const optional<int64_t> conditionCorrectionThresholdNs;
        const optional<bool> splitBucketForAppUpgrade;
    };

    struct WhatOptions {
        const bool containsAnyPositionInDimensionsInWhat;
        const bool shouldUseNestedDimensions;
        const int whatMatcherIndex;
        const sp<EventMatcherWizard>& matcherWizard;
        const FieldMatcher& dimensionsInWhat;
        const vector<Matcher>& fieldMatchers;
    };

    struct ConditionOptions {
        const int conditionIndex;
        const ConditionLinks& conditionLinks;
        const vector<ConditionState>& initialConditionCache;
        const sp<ConditionWizard>& conditionWizard;
    };

    struct StateOptions {
        const StateLinks& stateLinks;
        const vector<int>& slicedStateAtoms;
        const unordered_map<int, unordered_map<int, int64_t>>& stateGroupMap;
    };

    struct ActivationOptions {
        const std::unordered_map<int, std::shared_ptr<Activation>>& eventActivationMap;
        const std::unordered_map<int, std::vector<std::shared_ptr<Activation>>>&
                eventDeactivationMap;
    };

    struct GuardrailOptions {
        const size_t dimensionSoftLimit;
        const size_t dimensionHardLimit;
    };

    virtual ~ValueMetricProducer();

    // Process data pulled on bucket boundary.
    virtual void onDataPulled(const std::vector<std::shared_ptr<LogEvent>>& data,
                              PullResult pullResult, int64_t originalPullTimeNs) override {
    }

    // Determine if metric needs to pull
    virtual bool isPullNeeded() const override {
        return false;
    }

    // ValueMetric needs special logic if it's a pulled atom.
    void onStatsdInitCompleted(int64_t eventTimeNs) override;

    void onStateChanged(int64_t eventTimeNs, int32_t atomId, const HashableDimensionKey& primaryKey,
                        const FieldValue& oldState, const FieldValue& newState) override;

protected:
    ValueMetricProducer(int64_t metricId, const ConfigKey& key, uint64_t protoHash,
                        const PullOptions& pullOptions, const BucketOptions& bucketOptions,
                        const WhatOptions& whatOptions, const ConditionOptions& conditionOptions,
                        const StateOptions& stateOptions,
                        const ActivationOptions& activationOptions,
                        const GuardrailOptions& guardrailOptions);

    void onMatchedLogEventInternalLocked(
            const size_t matcherIndex, const MetricDimensionKey& eventKey,
            const ConditionKey& conditionKey, bool condition, const LogEvent& event,
            const std::map<int, HashableDimensionKey>& statePrimaryKeys) override;

    // Determine whether or not a LogEvent can be skipped.
    virtual inline bool canSkipLogEventLocked(
            const MetricDimensionKey& eventKey, bool condition, int64_t eventTimeNs,
            const std::map<int, HashableDimensionKey>& statePrimaryKeys) const = 0;

    void notifyAppUpgradeInternalLocked(const int64_t eventTimeNs) override;

    void onDumpReportLocked(const int64_t dumpTimeNs, const bool includeCurrentPartialBucket,
                            const bool eraseData, const DumpLatency dumpLatency,
                            std::set<string>* strSet,
                            android::util::ProtoOutputStream* protoOutput) override;

    struct DumpProtoFields {
        const int metricTypeFieldId;
        const int bucketNumFieldId;
        const int startBucketMsFieldId;
        const int endBucketMsFieldId;
        const int conditionTrueNsFieldId;
        const optional<int> conditionCorrectionNsFieldId;
    };

    virtual DumpProtoFields getDumpProtoFields() const = 0;

    void clearPastBucketsLocked(const int64_t dumpTimeNs) override;

    // ValueMetricProducer internal interface to handle active state change.
    void onActiveStateChangedLocked(const int64_t eventTimeNs, const bool isActive) override;

    virtual void onActiveStateChangedInternalLocked(const int64_t eventTimeNs,
                                                    const bool isActive) {
    }

    // ValueMetricProducer internal interface to handle condition change.
    void onConditionChangedLocked(const bool condition, int64_t eventTimeNs) override;

    // Only called when mIsActive, the event is NOT too late, and after pulling.
    virtual void onConditionChangedInternalLocked(const ConditionState oldCondition,
                                                  const ConditionState newCondition,
                                                  const int64_t eventTimeNs) {
    }

    // Internal interface to handle sliced condition change.
    void onSlicedConditionMayChangeLocked(bool overallCondition, int64_t eventTime) override;

    void dumpStatesLocked(int out, bool verbose) const override;

    virtual std::string aggregatedValueToString(const AggregatedValue& aggregate) const = 0;

    // For pulled metrics, this method should only be called if a pull has been done. Else we will
    // not have complete data for the bucket.
    void flushIfNeededLocked(int64_t eventTime) override;

    // For pulled metrics, this method should only be called if a pulled has been done. Else we will
    // not have complete data for the bucket.
    void flushCurrentBucketLocked(int64_t eventTimeNs, int64_t nextBucketStartTimeNs) override;

    void dropDataLocked(const int64_t dropTimeNs) override;

    // Calculate how many buckets are present between the current bucket and eventTimeNs.
    int64_t calcBucketsForwardCount(const int64_t eventTimeNs) const;

    // Mark the data as invalid.
    virtual void invalidateCurrentBucket(const int64_t dropTimeNs, const BucketDropReason reason);

    // Skips the current bucket without notifying StatsdStats of the skipped bucket.
    // This should only be called from #flushCurrentBucketLocked. Otherwise, a future event that
    // causes the bucket to be invalidated will not notify StatsdStats.
    void skipCurrentBucket(const int64_t dropTimeNs, const BucketDropReason reason);

    optional<InvalidConfigReason> onConfigUpdatedLocked(
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
            std::vector<int>& metricsWithActivation) override;

    virtual optional<int64_t> getConditionIdForMetric(const StatsdConfig& config,
                                                      const int configIndex) const = 0;

    virtual int64_t getWhatAtomMatcherIdForMetric(const StatsdConfig& config,
                                                  const int configIndex) const = 0;

    virtual ConditionLinks getConditionLinksForMetric(const StatsdConfig& config,
                                                      const int configIndex) const = 0;

    int mWhatMatcherIndex;

    sp<EventMatcherWizard> mEventMatcherWizard;

    const sp<StatsPullerManager> mPullerManager;

    // Value fields for matching.
    const std::vector<Matcher> mFieldMatchers;

    // Value fields for matching.
    std::set<HashableDimensionKey> mMatchedMetricDimensionKeys;

    // Holds the atom id, primary key pair from a state change.
    // Only used for pulled metrics.
    // TODO(b/185796114): can be passed as function arguments instead.
    pair<int32_t, HashableDimensionKey> mStateChangePrimaryKey;

    // Atom Id for pulled data. -1 if this is not pulled.
    const int mPullAtomId;

    // Tracks the value information of one value field.
    struct Interval {
        // Index in multi value aggregation.
        int aggIndex;

        // Current aggregation, depending on the aggregation type.
        AggregatedValue aggregate;

        // Number of samples collected.
        int sampleSize = 0;

        inline bool hasValue() const {
            return sampleSize > 0;
        }
    };

    // Internal state of an ongoing aggregation bucket.
    struct CurrentBucket {
        // If the `MetricDimensionKey` state key is the current state key, then
        // the condition timer will be updated later (e.g. condition/state/active
        // state change) with the correct condition and time.
        CurrentBucket() : intervals(), conditionTimer(ConditionTimer(false, 0)) {
        }
        // Value information for each value field of the metric.
        std::vector<Interval> intervals;
        // Tracks how long the condition is true.
        ConditionTimer conditionTimer;
    };

    // Tracks the internal state in the ongoing aggregation bucket for each DimensionsInWhat
    // key and StateValuesKey pair.
    std::unordered_map<MetricDimensionKey, CurrentBucket> mCurrentSlicedBucket;

    // State key and any extra information for a specific DimensionsInWhat key.
    struct DimensionsInWhatInfo {
        DimensionsInWhatInfo(const HashableDimensionKey& stateKey)
            : dimExtras(), currentState(stateKey), hasCurrentState(false) {
        }

        DimExtras dimExtras;

        // Whether new data is seen in the bucket.
        // TODO, this could be per base in the dim extras.
        bool seenNewData = false;

        // Last seen state value(s).
        HashableDimensionKey currentState;
        // Whether this dimensions in what key has a current state key.
        bool hasCurrentState;
    };

    // Tracks current state key and other information for each DimensionsInWhat key.
    std::unordered_map<HashableDimensionKey, DimensionsInWhatInfo> mDimInfos;

    // Save the past buckets and we can clear when the StatsLogReport is dumped.
    std::unordered_map<MetricDimensionKey, std::vector<PastBucket<AggregatedValue>>> mPastBuckets;

    const int64_t mMinBucketSizeNs;

    // Util function to check whether the specified dimension hits the guardrail.
    bool hitGuardRailLocked(const MetricDimensionKey& newKey) const;

    bool hasReachedGuardRailLimit() const;

    virtual void pullAndMatchEventsLocked(const int64_t timestampNs) {
    }

    virtual bool multipleBucketsSkipped(const int64_t numBucketsForward) const = 0;

    virtual PastBucket<AggregatedValue> buildPartialBucket(int64_t bucketEndTime,
                                                           std::vector<Interval>& intervals) = 0;

    virtual void closeCurrentBucket(const int64_t eventTimeNs, int64_t nextBucketStartTimeNs);

    virtual void initNextSlicedBucket(int64_t nextBucketStartTimeNs);

    // Updates the condition timers in the current sliced bucket when there is a
    // condition change or an active state change.
    void updateCurrentSlicedBucketConditionTimers(bool newCondition, int64_t eventTimeNs);

    virtual void writePastBucketAggregateToProto(const int aggIndex,
                                                 const AggregatedValue& aggregate,
                                                 const int sampleSize,
                                                 ProtoOutputStream* const protoOutput) const = 0;

    static const size_t kBucketSize = sizeof(PastBucket<AggregatedValue>{});

    const size_t mDimensionSoftLimit;

    const size_t mDimensionHardLimit;

    // This is to track whether or not the bucket is skipped for any of the reasons listed in
    // BucketDropReason, many of which make the bucket potentially invalid.
    bool mCurrentBucketIsSkipped;

    /** Stores condition correction threshold from the ValueMetric configuration */
    optional<int64_t> mConditionCorrectionThresholdNs;

    inline bool isEventLateLocked(const int64_t eventTimeNs) const {
        return eventTimeNs < mCurrentBucketStartTimeNs;
    }

    // Returns true if any of the intervals have seen new data.
    // This should return true unless there is an error parsing the value fields from the event.
    virtual bool aggregateFields(const int64_t eventTimeNs, const MetricDimensionKey& eventKey,
                                 const LogEvent& event, std::vector<Interval>& intervals,
                                 DimExtras& dimExtras) = 0;

    // If this is a pulled metric
    inline bool isPulled() const {
        return mPullAtomId != -1;
    }

private:
};  // ValueMetricProducer

}  // namespace statsd
}  // namespace os
}  // namespace android
