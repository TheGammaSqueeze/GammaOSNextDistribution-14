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

#include <unordered_map>

#include <android/util/ProtoOutputStream.h>
#include <gtest/gtest_prod.h>
#include "../condition/ConditionTracker.h"
#include "../external/PullDataReceiver.h"
#include "../external/StatsPullerManager.h"
#include "../matchers/matcher_util.h"
#include "../matchers/EventMatcherWizard.h"
#include "MetricProducer.h"
#include "src/statsd_config.pb.h"
#include "../stats_util.h"

namespace android {
namespace os {
namespace statsd {

struct GaugeAtom {
    GaugeAtom(const std::shared_ptr<vector<FieldValue>>& fields, int64_t elapsedTimeNs)
        : mFields(fields), mElapsedTimestampNs(elapsedTimeNs) {
    }
    std::shared_ptr<vector<FieldValue>> mFields;
    int64_t mElapsedTimestampNs;
};

struct GaugeBucket {
    int64_t mBucketStartNs;
    int64_t mBucketEndNs;
    std::vector<GaugeAtom> mGaugeAtoms;

    // Maps the field/value pairs of an atom to a list of timestamps used to deduplicate atoms.
    std::unordered_map<AtomDimensionKey, std::vector<int64_t>> mAggregatedAtoms;
};

typedef std::unordered_map<MetricDimensionKey, std::vector<GaugeAtom>>
    DimToGaugeAtomsMap;

// This gauge metric producer first register the puller to automatically pull the gauge at the
// beginning of each bucket. If the condition is met, insert it to the bucket info. Otherwise
// proactively pull the gauge when the condition is changed to be true. Therefore, the gauge metric
// producer always reports the gauge at the earliest time of the bucket when the condition is met.
class GaugeMetricProducer : public MetricProducer, public virtual PullDataReceiver {
public:
    GaugeMetricProducer(
            const ConfigKey& key, const GaugeMetric& gaugeMetric, int conditionIndex,
            const vector<ConditionState>& initialConditionCache,
            const sp<ConditionWizard>& conditionWizard, const uint64_t protoHash,
            const int whatMatcherIndex, const sp<EventMatcherWizard>& matcherWizard,
            const int pullTagId, int triggerAtomId, int atomId, const int64_t timeBaseNs,
            int64_t startTimeNs, const sp<StatsPullerManager>& pullerManager,
            const std::unordered_map<int, std::shared_ptr<Activation>>& eventActivationMap = {},
            const std::unordered_map<int, std::vector<std::shared_ptr<Activation>>>&
                    eventDeactivationMap = {},
            const size_t dimensionSoftLimit = StatsdStats::kDimensionKeySizeSoftLimit,
            const size_t dimensionHardLimit = StatsdStats::kDimensionKeySizeHardLimit);

    virtual ~GaugeMetricProducer();

    // Handles when the pulled data arrives.
    void onDataPulled(const std::vector<std::shared_ptr<LogEvent>>& data, PullResult pullResult,
                      int64_t originalPullTimeNs) override;

    // Determine if metric needs to pull
    bool isPullNeeded() const override {
        std::lock_guard<std::mutex> lock(mMutex);
        return mIsActive && (mCondition == ConditionState::kTrue);
    };

    // GaugeMetric needs to immediately trigger another pull when we create the partial bucket.
    void notifyAppUpgradeInternalLocked(int64_t eventTimeNs) override {
        flushLocked(eventTimeNs);
        if (mIsPulled && mSamplingType == GaugeMetric::RANDOM_ONE_SAMPLE && mIsActive) {
            pullAndMatchEventsLocked(eventTimeNs);
        }
    };

    // GaugeMetric needs to immediately trigger another pull when we create the partial bucket.
    void onStatsdInitCompleted(int64_t eventTimeNs) override {
        std::lock_guard<std::mutex> lock(mMutex);

        flushLocked(eventTimeNs);
        if (mIsPulled && mSamplingType == GaugeMetric::RANDOM_ONE_SAMPLE && mIsActive) {
            pullAndMatchEventsLocked(eventTimeNs);
        }
    };

    MetricType getMetricType() const override {
        return METRIC_TYPE_GAUGE;
    }

protected:
    void onMatchedLogEventInternalLocked(
            const size_t matcherIndex, const MetricDimensionKey& eventKey,
            const ConditionKey& conditionKey, bool condition, const LogEvent& event,
            const std::map<int, HashableDimensionKey>& statePrimaryKeys) override;

private:
    void onDumpReportLocked(const int64_t dumpTimeNs,
                            const bool include_current_partial_bucket,
                            const bool erase_data,
                            const DumpLatency dumpLatency,
                            std::set<string> *str_set,
                            android::util::ProtoOutputStream* protoOutput) override;
    void clearPastBucketsLocked(const int64_t dumpTimeNs) override;

    // Internal interface to handle condition change.
    void onConditionChangedLocked(const bool conditionMet, int64_t eventTime) override;

    // Internal interface to handle active state change.
    void onActiveStateChangedLocked(const int64_t eventTimeNs, const bool isActive) override;

    // Internal interface to handle sliced condition change.
    void onSlicedConditionMayChangeLocked(bool overallCondition, int64_t eventTime) override;

    // Internal function to calculate the current used bytes.
    size_t byteSizeLocked() const override;

    void dumpStatesLocked(int out, bool verbose) const override;

    void dropDataLocked(const int64_t dropTimeNs) override;

    // Util function to flush the old packet.
    void flushIfNeededLocked(int64_t eventTime) override;

    void flushCurrentBucketLocked(int64_t eventTimeNs, int64_t nextBucketStartTimeNs) override;

    void prepareFirstBucketLocked() override;

    // Only call if mCondition == ConditionState::kTrue && metric is active.
    void pullAndMatchEventsLocked(const int64_t timestampNs);

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

    inline bool isRandomNSamples() const {
        return (mTriggerAtomId == -1 && mSamplingType == GaugeMetric::FIRST_N_SAMPLES) ||
               mSamplingType == GaugeMetric::RANDOM_ONE_SAMPLE;
    }

    int mWhatMatcherIndex;

    sp<EventMatcherWizard> mEventMatcherWizard;

    sp<StatsPullerManager> mPullerManager;
    // tagId for pulled data. -1 if this is not pulled
    const int mPullTagId;

    // tagId for atoms that trigger the pulling, if any
    const int mTriggerAtomId;

    // tagId for output atom
    const int mAtomId;

    // if this is pulled metric
    const bool mIsPulled;

    // Save the past buckets and we can clear when the StatsLogReport is dumped.
    std::unordered_map<MetricDimensionKey, std::vector<GaugeBucket>> mPastBuckets;

    // The current partial bucket.
    std::shared_ptr<DimToGaugeAtomsMap> mCurrentSlicedBucket;

    // The current full bucket for anomaly detection. This is updated to the latest value seen for
    // this slice (ie, for partial buckets, we use the last partial bucket in this full bucket).
    std::shared_ptr<DimToValMap> mCurrentSlicedBucketForAnomaly;

    const int64_t mMinBucketSizeNs;

    // Translate Atom based bucket to single numeric value bucket for anomaly and updates the map
    // for each slice with the latest value.
    void updateCurrentSlicedBucketForAnomaly();

    // Allowlist of fields to report. Empty means all are reported.
    std::vector<Matcher> mFieldMatchers;

    GaugeMetric::SamplingType mSamplingType;

    const int64_t mMaxPullDelayNs;

    // apply an allowlist on the original input
    std::shared_ptr<vector<FieldValue>> getGaugeFields(const LogEvent& event);

    // Util function to check whether the specified dimension hits the guardrail.
    bool hitGuardRailLocked(const MetricDimensionKey& newKey);

    static const size_t kBucketSize = sizeof(GaugeBucket{});

    const size_t mDimensionSoftLimit;

    const size_t mDimensionHardLimit;

    const size_t mGaugeAtomsPerDimensionLimit;

    // Tracks if the dimension guardrail has been hit in the current report.
    bool mDimensionGuardrailHit;

    const int mSamplingPercentage;

    FRIEND_TEST(GaugeMetricProducerTest, TestPulledEventsWithCondition);
    FRIEND_TEST(GaugeMetricProducerTest, TestPulledEventsWithSlicedCondition);
    FRIEND_TEST(GaugeMetricProducerTest, TestPulledEventsNoCondition);
    FRIEND_TEST(GaugeMetricProducerTest, TestPulledWithAppUpgradeDisabled);
    FRIEND_TEST(GaugeMetricProducerTest, TestPulledEventsAnomalyDetection);
    FRIEND_TEST(GaugeMetricProducerTest, TestFirstBucket);
    FRIEND_TEST(GaugeMetricProducerTest, TestPullOnTrigger);
    FRIEND_TEST(GaugeMetricProducerTest, TestPullNWithoutTrigger);
    FRIEND_TEST(GaugeMetricProducerTest, TestRemoveDimensionInOutput);
    FRIEND_TEST(GaugeMetricProducerTest, TestPullDimensionalSampling);

    FRIEND_TEST(GaugeMetricProducerTest_PartialBucket, TestPushedEvents);
    FRIEND_TEST(GaugeMetricProducerTest_PartialBucket, TestPulled);

    FRIEND_TEST(ConfigUpdateTest, TestUpdateGaugeMetrics);

    FRIEND_TEST(MetricsManagerUtilDimLimitTest, TestDimLimit);

    FRIEND_TEST(ConfigUpdateDimLimitTest, TestDimLimit);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
