/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "ValueMetricProducer.h"

namespace android {
namespace os {
namespace statsd {

// TODO(b/185796344): don't use Value from FieldValue.
using ValueBases = std::vector<std::optional<Value>>;
class NumericValueMetricProducer : public ValueMetricProducer<Value, ValueBases> {
public:
    NumericValueMetricProducer(const ConfigKey& key, const ValueMetric& valueMetric,
                               const uint64_t protoHash, const PullOptions& pullOptions,
                               const BucketOptions& bucketOptions, const WhatOptions& whatOptions,
                               const ConditionOptions& conditionOptions,
                               const StateOptions& stateOptions,
                               const ActivationOptions& activationOptions,
                               const GuardrailOptions& guardrailOptions);

    // Process data pulled on bucket boundary.
    void onDataPulled(const std::vector<std::shared_ptr<LogEvent>>& allData, PullResult pullResult,
                      int64_t originalPullTimeNs) override;

    // Determine if metric needs to pull
    bool isPullNeeded() const override {
        std::lock_guard<std::mutex> lock(mMutex);
        return mIsActive && (mCondition == ConditionState::kTrue);
    }

    inline MetricType getMetricType() const override {
        return METRIC_TYPE_VALUE;
    }

protected:
private:
    void prepareFirstBucketLocked() override;

    inline optional<int64_t> getConditionIdForMetric(const StatsdConfig& config,
                                                     const int configIndex) const override {
        const ValueMetric& metric = config.value_metric(configIndex);
        return metric.has_condition() ? make_optional(metric.condition()) : nullopt;
    }

    inline int64_t getWhatAtomMatcherIdForMetric(const StatsdConfig& config,
                                                 const int configIndex) const override {
        return config.value_metric(configIndex).what();
    }

    inline ConditionLinks getConditionLinksForMetric(const StatsdConfig& config,
                                                     const int configIndex) const override {
        return config.value_metric(configIndex).links();
    }

    void onActiveStateChangedInternalLocked(const int64_t eventTimeNs,
                                            const bool isActive) override;

    // Only called when mIsActive and the event is NOT too late.
    void onConditionChangedInternalLocked(const ConditionState oldCondition,
                                          const ConditionState newCondition,
                                          const int64_t eventTimeNs) override;

    inline std::string aggregatedValueToString(const Value& value) const override {
        return value.toString();
    }

    // Mark the data as invalid.
    void invalidateCurrentBucket(const int64_t dropTimeNs, const BucketDropReason reason) override;

    // Reset diff base and mHasGlobalBase
    void resetBase();

    // Calculate previous bucket end time based on current time.
    int64_t calcPreviousBucketEndTime(const int64_t currentTimeNs);

    inline bool multipleBucketsSkipped(const int64_t numBucketsForward) const override {
        return numBucketsForward > 1 && (isPulled() || mUseDiff);
    }

    // Process events retrieved from a pull.
    void accumulateEvents(const std::vector<std::shared_ptr<LogEvent>>& allData,
                          int64_t originalPullTimeNs, int64_t eventElapsedTimeNs);

    void closeCurrentBucket(const int64_t eventTimeNs,
                            const int64_t nextBucketStartTimeNs) override;

    PastBucket<Value> buildPartialBucket(int64_t bucketEndTime,
                                         std::vector<Interval>& intervals) override;

    bool valuePassesThreshold(const Interval& interval) const;

    Value getFinalValue(const Interval& interval) const;

    void initNextSlicedBucket(int64_t nextBucketStartTimeNs) override;

    void appendToFullBucket(const bool isFullBucketReached);

    bool hitFullBucketGuardRailLocked(const MetricDimensionKey& newKey);

    inline bool canSkipLogEventLocked(
            const MetricDimensionKey& eventKey, const bool condition, int64_t eventTimeNs,
            const map<int, HashableDimensionKey>& statePrimaryKeys) const override {
        // For pushed metrics, can only skip if condition is false.
        // For pulled metrics, can only skip if metric is not diffed and condition is false or
        // unknown.
        return (!isPulled() && !condition) ||
               (isPulled() && !mUseDiff && mCondition != ConditionState::kTrue);
    }

    bool aggregateFields(const int64_t eventTimeNs, const MetricDimensionKey& eventKey,
                         const LogEvent& event, std::vector<Interval>& intervals,
                         ValueBases& bases) override;

    void pullAndMatchEventsLocked(const int64_t timestampNs) override;

    DumpProtoFields getDumpProtoFields() const override;

    void writePastBucketAggregateToProto(const int aggIndex, const Value& value,
                                         const int sampleSize,
                                         ProtoOutputStream* const protoOutput) const override;

    // Internal function to calculate the current used bytes.
    size_t byteSizeLocked() const override;

    void combineValueFields(pair<LogEvent, vector<int>>& eventValues, const LogEvent& newEvent,
                            const vector<int>& newValueIndices) const;

    const bool mUseAbsoluteValueOnReset;

    const ValueMetric::AggregationType mAggregationType;

    const bool mIncludeSampleSize;

    const bool mUseDiff;

    const ValueMetric::ValueDirection mValueDirection;

    const bool mSkipZeroDiffOutput;

    // If true, use a zero value as base to compute the diff.
    // This is used for new keys which are present in the new data but was not
    // present in the base data.
    // The default base will only be used if we have a global base.
    const bool mUseZeroDefaultBase;

    // For pulled metrics, this is always set to true whenever a pull succeeds.
    // It is set to false when a pull fails, or upon condition change to false.
    // This is used to decide if we have the right base data to compute the
    // diff against.
    bool mHasGlobalBase;

    const int64_t mMaxPullDelayNs;

    // For anomaly detection.
    std::unordered_map<MetricDimensionKey, int64_t> mCurrentFullBucket;

    FRIEND_TEST(NumericValueMetricProducerTest, TestAnomalyDetection);
    FRIEND_TEST(NumericValueMetricProducerTest, TestBaseSetOnConditionChange);
    FRIEND_TEST(NumericValueMetricProducerTest, TestBucketBoundariesOnConditionChange);
    FRIEND_TEST(NumericValueMetricProducerTest, TestBucketBoundaryNoCondition);
    FRIEND_TEST(NumericValueMetricProducerTest, TestBucketBoundaryWithCondition);
    FRIEND_TEST(NumericValueMetricProducerTest, TestBucketBoundaryWithCondition2);
    FRIEND_TEST(NumericValueMetricProducerTest, TestBucketInvalidIfGlobalBaseIsNotSet);
    FRIEND_TEST(NumericValueMetricProducerTest, TestCalcPreviousBucketEndTime);
    FRIEND_TEST(NumericValueMetricProducerTest, TestDataIsNotUpdatedWhenNoConditionChanged);
    FRIEND_TEST(NumericValueMetricProducerTest, TestEmptyDataResetsBase_onBucketBoundary);
    FRIEND_TEST(NumericValueMetricProducerTest, TestEmptyDataResetsBase_onConditionChanged);
    FRIEND_TEST(NumericValueMetricProducerTest, TestEmptyDataResetsBase_onDataPulled);
    FRIEND_TEST(NumericValueMetricProducerTest, TestEventsWithNonSlicedCondition);
    FRIEND_TEST(NumericValueMetricProducerTest, TestFirstBucket);
    FRIEND_TEST(NumericValueMetricProducerTest, TestLateOnDataPulledWithDiff);
    FRIEND_TEST(NumericValueMetricProducerTest, TestLateOnDataPulledWithoutDiff);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPartialResetOnBucketBoundaries);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_bucketBoundaryFalse);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_bucketBoundaryTrue);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_withFailure);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_withMultipleConditionChanges);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_withoutCondition);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledEventsNoCondition);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledEventsTakeAbsoluteValueOnReset);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledEventsTakeZeroOnReset);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledEventsWithFiltering);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPulledWithAppUpgradeDisabled);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPushedAggregateAvg);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPushedAggregateMax);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPushedAggregateMin);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPushedAggregateSum);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPushedEventsWithCondition);
    FRIEND_TEST(NumericValueMetricProducerTest, TestPushedEventsWithoutCondition);
    FRIEND_TEST(NumericValueMetricProducerTest, TestResetBaseOnPullDelayExceeded);
    FRIEND_TEST(NumericValueMetricProducerTest, TestResetBaseOnPullFailAfterConditionChange);
    FRIEND_TEST(NumericValueMetricProducerTest,
                TestResetBaseOnPullFailAfterConditionChange_EndOfBucket);
    FRIEND_TEST(NumericValueMetricProducerTest, TestResetBaseOnPullFailBeforeConditionChange);
    FRIEND_TEST(NumericValueMetricProducerTest, TestResetBaseOnPullTooLate);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSampleSize);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSkipZeroDiffOutput);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSkipZeroDiffOutputMultiValue);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedState);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithMap);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithPrimaryField_WithDimensions);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithCondition);
    FRIEND_TEST(NumericValueMetricProducerTest, TestTrimUnusedDimensionKey);
    FRIEND_TEST(NumericValueMetricProducerTest, TestUseZeroDefaultBase);
    FRIEND_TEST(NumericValueMetricProducerTest, TestUseZeroDefaultBaseWithPullFailures);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithMultipleDimensions);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithMissingDataInStateChange);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithDataMissingInConditionChange);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithMissingDataThenFlushBucket);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithNoPullOnBucketBoundary);
    FRIEND_TEST(NumericValueMetricProducerTest, TestSlicedStateWithConditionFalseMultipleBuckets);
    FRIEND_TEST(NumericValueMetricProducerTest,
                TestSlicedStateWithMultipleDimensionsMissingDataInPull);
    FRIEND_TEST(NumericValueMetricProducerTest, TestUploadThreshold);

    FRIEND_TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenOneConditionFailed);
    FRIEND_TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenInitialPullFailed);
    FRIEND_TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenLastPullFailed);
    FRIEND_TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenGuardRailHit);
    FRIEND_TEST(NumericValueMetricProducerTest_BucketDrop,
                TestInvalidBucketWhenDumpReportRequested);
    FRIEND_TEST(NumericValueMetricProducerTest_BucketDrop,
                TestInvalidBucketWhenAccumulateEventWrongBucket);
    FRIEND_TEST(NumericValueMetricProducerTest_BucketDrop,
                TestInvalidBucketWhenMultipleBucketsSkipped);

    FRIEND_TEST(NumericValueMetricProducerTest_PartialBucket, TestBucketBoundariesOnPartialBucket);
    FRIEND_TEST(NumericValueMetricProducerTest_PartialBucket,
                TestFullBucketResetWhenLastBucketInvalid);
    FRIEND_TEST(NumericValueMetricProducerTest_PartialBucket, TestPartialBucketCreated);
    FRIEND_TEST(NumericValueMetricProducerTest_PartialBucket, TestPushedEvents);
    FRIEND_TEST(NumericValueMetricProducerTest_PartialBucket, TestPulledValue);
    FRIEND_TEST(NumericValueMetricProducerTest_PartialBucket, TestPulledValueWhileConditionFalse);

    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestAlarmLatePullWhileConditionTrue);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestAlarmLatePullWithConditionChanged);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestAlarmLatePullWhileConditionFalse);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestLatePullOnConditionChangeFalse);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestLatePullOnConditionChangeTrue);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection, TestAlarmLatePullNoCondition);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestAlarmLatePullNoConditionWithSkipped);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestThresholdNotDefinedNoUpload);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection, TestThresholdDefinedZero);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestThresholdUploadPassWhenEqual);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection,
                TestThresholdUploadPassWhenGreater);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection, TestThresholdUploadSkip);
    FRIEND_TEST(NumericValueMetricProducerTest_ConditionCorrection, TestLateStateChangeSlicedAtoms);

    FRIEND_TEST(NumericValueMetricProducerTest, TestSubsetDimensions);

    FRIEND_TEST(ConfigUpdateTest, TestUpdateValueMetrics);

    FRIEND_TEST(MetricsManagerUtilDimLimitTest, TestDimLimit);

    FRIEND_TEST(ConfigUpdateDimLimitTest, TestDimLimit);

    friend class NumericValueMetricProducerTestHelper;
};

}  // namespace statsd
}  // namespace os
}  // namespace android
