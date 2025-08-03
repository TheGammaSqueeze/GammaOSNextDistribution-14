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

#include <android/util/ProtoOutputStream.h>
#include <gtest/gtest_prod.h>
#include <kll.h>

#include <optional>

#include "MetricProducer.h"
#include "ValueMetricProducer.h"
#include "condition/ConditionTimer.h"
#include "condition/ConditionTracker.h"
#include "matchers/EventMatcherWizard.h"
#include "src/statsd_config.pb.h"
#include "stats_log_util.h"

using dist_proc::aggregation::KllQuantile;

namespace android {
namespace os {
namespace statsd {

// Uses KllQuantile to aggregate values within buckets.
//
// There are different events that might complete a bucket
// - a condition change
// - an app upgrade
// - an alarm set to the end of the bucket
class KllMetricProducer : public ValueMetricProducer<std::unique_ptr<KllQuantile>, Empty> {
public:
    KllMetricProducer(const ConfigKey& key, const KllMetric& kllMetric, const uint64_t protoHash,
                      const PullOptions& pullOptions, const BucketOptions& bucketOptions,
                      const WhatOptions& whatOptions, const ConditionOptions& conditionOptions,
                      const StateOptions& stateOptions, const ActivationOptions& activationOptions,
                      const GuardrailOptions& guardrailOptions);

    inline MetricType getMetricType() const override {
        return METRIC_TYPE_KLL;
    }

protected:
private:
    inline optional<int64_t> getConditionIdForMetric(const StatsdConfig& config,
                                                     const int configIndex) const override {
        const KllMetric& metric = config.kll_metric(configIndex);
        return metric.has_condition() ? make_optional(metric.condition()) : nullopt;
    }

    inline int64_t getWhatAtomMatcherIdForMetric(const StatsdConfig& config,
                                                 const int configIndex) const override {
        return config.kll_metric(configIndex).what();
    }

    inline ConditionLinks getConditionLinksForMetric(const StatsdConfig& config,
                                                     const int configIndex) const override {
        return config.kll_metric(configIndex).links();
    }

    // Determine whether or not a LogEvent can be skipped.
    inline bool canSkipLogEventLocked(
            const MetricDimensionKey& eventKey, bool condition, int64_t eventTimeNs,
            const std::map<int, HashableDimensionKey>& statePrimaryKeys) const override {
        // Can only skip if the condition is false.
        // We assume metric is pushed since KllMetric doesn't support pulled metrics.
        return !condition;
    }

    DumpProtoFields getDumpProtoFields() const override;

    inline std::string aggregatedValueToString(
            const std::unique_ptr<KllQuantile>& aggregate) const override {
        return std::to_string(aggregate->num_values()) + " values";
    }

    inline bool multipleBucketsSkipped(const int64_t numBucketsForward) const override {
        // Always false because we assume KllMetric is pushed only for now.
        return false;
    }

    // The KllQuantile ptr ownership is transferred to newly created PastBuckets from Intervals.
    PastBucket<std::unique_ptr<KllQuantile>> buildPartialBucket(
            int64_t bucketEndTime, std::vector<Interval>& intervals) override;

    void writePastBucketAggregateToProto(const int aggIndex,
                                         const std::unique_ptr<KllQuantile>& kll,
                                         const int sampleSize,
                                         ProtoOutputStream* const protoOutput) const override;

    bool aggregateFields(const int64_t eventTimeNs, const MetricDimensionKey& eventKey,
                         const LogEvent& event, std::vector<Interval>& intervals,
                         Empty& empty) override;

    // Internal function to calculate the current used bytes.
    size_t byteSizeLocked() const override;

    FRIEND_TEST(KllMetricProducerTest, TestByteSize);
    FRIEND_TEST(KllMetricProducerTest, TestPushedEventsWithoutCondition);
    FRIEND_TEST(KllMetricProducerTest, TestPushedEventsWithCondition);
    FRIEND_TEST(KllMetricProducerTest, TestForcedBucketSplitWhenConditionUnknownSkipsBucket);

    FRIEND_TEST(KllMetricProducerTest_BucketDrop, TestInvalidBucketWhenConditionUnknown);
    FRIEND_TEST(KllMetricProducerTest_BucketDrop, TestBucketDropWhenBucketTooSmall);
    FRIEND_TEST(KllMetricProducerTest_BucketDrop, TestBucketDropWhenDataUnavailable);

    FRIEND_TEST(KllMetricProducerTest_PartialBucket, TestPushedEventsMultipleBuckets);

    FRIEND_TEST(ConfigUpdateTest, TestUpdateKllMetrics);

    FRIEND_TEST(MetricsManagerUtilDimLimitTest, TestDimLimit);

    FRIEND_TEST(ConfigUpdateDimLimitTest, TestDimLimit);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
