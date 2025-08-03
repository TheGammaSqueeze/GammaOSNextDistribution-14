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

#ifndef COUNT_METRIC_PRODUCER_H
#define COUNT_METRIC_PRODUCER_H

#include <android/util/ProtoOutputStream.h>
#include <gtest/gtest_prod.h>

#include <unordered_map>

#include "MetricProducer.h"
#include "anomaly/AnomalyTracker.h"
#include "condition/ConditionTimer.h"
#include "condition/ConditionTracker.h"
#include "matchers/matcher_util.h"
#include "src/statsd_config.pb.h"
#include "stats_util.h"

namespace android {
namespace os {
namespace statsd {

struct CountBucket {
    int64_t mBucketStartNs;
    int64_t mBucketEndNs;
    int64_t mCount;
    int64_t mConditionTrueNs;
};

class CountMetricProducer : public MetricProducer {
public:
    CountMetricProducer(
            const ConfigKey& key, const CountMetric& countMetric, int conditionIndex,
            const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
            const uint64_t protoHash, int64_t timeBaseNs, int64_t startTimeNs,
            const std::unordered_map<int, std::shared_ptr<Activation>>& eventActivationMap = {},
            const std::unordered_map<int, std::vector<std::shared_ptr<Activation>>>&
                    eventDeactivationMap = {},
            const vector<int>& slicedStateAtoms = {},
            const unordered_map<int, unordered_map<int, int64_t>>& stateGroupMap = {});

    virtual ~CountMetricProducer();

    void onStateChanged(const int64_t eventTimeNs, const int32_t atomId,
                        const HashableDimensionKey& primaryKey, const FieldValue& oldState,
                        const FieldValue& newState) override;

    MetricType getMetricType() const override {
        return METRIC_TYPE_COUNT;
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

    // Internal interface to handle sliced condition change.
    void onSlicedConditionMayChangeLocked(bool overallCondition, int64_t eventTime) override;

    // Internal function to calculate the current used bytes.
    size_t byteSizeLocked() const override;

    void dumpStatesLocked(int out, bool verbose) const override;

    void dropDataLocked(const int64_t dropTimeNs) override;

    // Util function to flush the old packet.
    void flushIfNeededLocked(int64_t newEventTime) override;

    void flushCurrentBucketLocked(int64_t eventTimeNs, int64_t nextBucketStartTimeNs) override;

    void onActiveStateChangedLocked(const int64_t eventTimeNs, const bool isActive) override;

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

    std::unordered_map<MetricDimensionKey, std::vector<CountBucket>> mPastBuckets;

    // The current bucket (may be a partial bucket).
    std::shared_ptr<DimToValMap> mCurrentSlicedCounter = std::make_shared<DimToValMap>();

    // The sum of previous partial buckets in the current full bucket (excluding the current
    // partial bucket). This is only updated while flushing the current bucket.
    std::shared_ptr<DimToValMap> mCurrentFullCounters = std::make_shared<DimToValMap>();

    static const size_t kBucketSize = sizeof(CountBucket{});

    bool hitGuardRailLocked(const MetricDimensionKey& newKey);

    bool countPassesThreshold(int64_t count);

    // Tracks if the dimension guardrail has been hit in the current report.
    bool mDimensionGuardrailHit;

    const size_t mDimensionHardLimit;

    FRIEND_TEST(CountMetricProducerTest, TestNonDimensionalEvents);
    FRIEND_TEST(CountMetricProducerTest, TestEventsWithNonSlicedCondition);
    FRIEND_TEST(CountMetricProducerTest, TestEventsWithSlicedCondition);
    FRIEND_TEST(CountMetricProducerTest, TestAnomalyDetectionUnSliced);
    FRIEND_TEST(CountMetricProducerTest, TestFirstBucket);
    FRIEND_TEST(CountMetricProducerTest, TestOneWeekTimeUnit);
    FRIEND_TEST(CountMetricProducerTest, TestSplitOnAppUpgradeDisabled);

    FRIEND_TEST(CountMetricProducerTest_PartialBucket, TestSplitInCurrentBucket);
    FRIEND_TEST(CountMetricProducerTest_PartialBucket, TestSplitInNextBucket);

    FRIEND_TEST(MetricsManagerUtilDimLimitTest, TestDimLimit);

    FRIEND_TEST(ConfigUpdateDimLimitTest, TestDimLimit);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
#endif  // COUNT_METRIC_PRODUCER_H
