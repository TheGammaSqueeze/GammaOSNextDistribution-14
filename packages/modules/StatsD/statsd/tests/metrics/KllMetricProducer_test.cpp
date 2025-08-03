// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "src/metrics/KllMetricProducer.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <math.h>
#include <stdio.h>

#include <vector>

#include "metrics_test_helper.h"
#include "src/FieldValue.h"
#include "src/matchers/SimpleAtomMatchingTracker.h"
#include "src/metrics/MetricProducer.h"
#include "src/stats_log_util.h"
#include "tests/statsd_test_util.h"

using namespace testing;
using android::sp;
using dist_proc::aggregation::KllQuantile;
using std::make_shared;
using std::optional;
using std::set;
using std::shared_ptr;
using std::unique_ptr;
using std::unordered_map;
using std::vector;

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {

const ConfigKey kConfigKey(0, 12345);
const int atomId = 1;
const int64_t metricId = 123;
const uint64_t protoHash = 0x1234567890;
const int logEventMatcherIndex = 0;
const int64_t bucketStartTimeNs = 10000000000;
const int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(ONE_MINUTE) * 1000000LL;
const int64_t bucket2StartTimeNs = bucketStartTimeNs + bucketSizeNs;
const int64_t bucket3StartTimeNs = bucketStartTimeNs + 2 * bucketSizeNs;
const int64_t bucket4StartTimeNs = bucketStartTimeNs + 3 * bucketSizeNs;
const int64_t bucket5StartTimeNs = bucketStartTimeNs + 4 * bucketSizeNs;
const int64_t bucket6StartTimeNs = bucketStartTimeNs + 5 * bucketSizeNs;

static void assertPastBucketsSingleKey(
        const std::unordered_map<MetricDimensionKey,
                                 std::vector<PastBucket<unique_ptr<KllQuantile>>>>& mPastBuckets,
        const std::initializer_list<int>& expectedKllCountsList,
        const std::initializer_list<int64_t>& expectedDurationNsList,
        const std::initializer_list<int64_t>& expectedStartTimeNsList,
        const std::initializer_list<int64_t>& expectedEndTimeNsList) {
    vector<int> expectedKllCounts(expectedKllCountsList);
    vector<int64_t> expectedDurationNs(expectedDurationNsList);
    vector<int64_t> expectedStartTimeNs(expectedStartTimeNsList);
    vector<int64_t> expectedEndTimeNs(expectedEndTimeNsList);

    ASSERT_EQ(expectedKllCounts.size(), expectedDurationNs.size());
    ASSERT_EQ(expectedKllCounts.size(), expectedStartTimeNs.size());
    ASSERT_EQ(expectedKllCounts.size(), expectedEndTimeNs.size());

    if (expectedKllCounts.size() == 0) {
        ASSERT_EQ(0, mPastBuckets.size());
        return;
    }

    ASSERT_EQ(1, mPastBuckets.size());
    const vector<PastBucket<unique_ptr<KllQuantile>>>& buckets = mPastBuckets.begin()->second;
    ASSERT_EQ(expectedKllCounts.size(), buckets.size());

    for (int i = 0; i < expectedKllCounts.size(); i++) {
        EXPECT_EQ(expectedKllCounts[i], buckets[i].aggregates[0]->num_values())
                << "Number of entries in KLL sketch differ at index " << i;
        EXPECT_EQ(expectedDurationNs[i], buckets[i].mConditionTrueNs)
                << "Condition duration value differ at index " << i;
        EXPECT_EQ(expectedStartTimeNs[i], buckets[i].mBucketStartNs)
                << "Start time differs at index " << i;
        EXPECT_EQ(expectedEndTimeNs[i], buckets[i].mBucketEndNs)
                << "End time differs at index " << i;
    }
}

}  // anonymous namespace

class KllMetricProducerTestHelper {
public:
    static sp<KllMetricProducer> createKllProducerNoConditions(const KllMetric& metric) {
        return createKllProducer(metric);
    }

    static sp<KllMetricProducer> createKllProducerWithCondition(
            const KllMetric& metric, const ConditionState& initialCondition) {
        return createKllProducer(metric, initialCondition);
    }

    static sp<KllMetricProducer> createKllProducer(
            const KllMetric& metric, optional<ConditionState> initialCondition = nullopt,
            vector<int32_t> slicedStateAtoms = {},
            unordered_map<int, unordered_map<int, int64_t>> stateGroupMap = {},
            const int64_t timeBaseNs = bucketStartTimeNs,
            const int64_t startTimeNs = bucketStartTimeNs) {
        sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
        const int64_t bucketSizeNs = MillisToNano(
                TimeUnitToBucketSizeInMillisGuardrailed(kConfigKey.GetUid(), metric.bucket()));
        const bool containsAnyPositionInDimensionsInWhat =
                HasPositionANY(metric.dimensions_in_what());
        const bool shouldUseNestedDimensions =
                ShouldUseNestedDimensions(metric.dimensions_in_what());

        vector<Matcher> fieldMatchers;
        translateFieldMatcher(metric.kll_field(), &fieldMatchers);

        const auto [dimensionSoftLimit, dimensionHardLimit] =
                StatsdStats::getAtomDimensionKeySizeLimits(
                        atomId, StatsdStats::kDimensionKeySizeHardLimitMin);

        int conditionIndex = initialCondition ? 0 : -1;
        vector<ConditionState> initialConditionCache;
        if (initialCondition) {
            initialConditionCache.push_back(initialCondition.value());
        }

        return new KllMetricProducer(
                kConfigKey, metric, protoHash, {/*pullAtomId=*/-1, /*pullerManager=*/nullptr},
                {timeBaseNs, startTimeNs, bucketSizeNs, metric.min_bucket_size_nanos(),
                 /*conditionCorrectionThresholdNs=*/nullopt, metric.split_bucket_for_app_upgrade()},
                {containsAnyPositionInDimensionsInWhat, shouldUseNestedDimensions,
                 logEventMatcherIndex,
                 /*eventMatcherWizard=*/nullptr, metric.dimensions_in_what(), fieldMatchers},
                {conditionIndex, metric.links(), initialConditionCache, wizard},
                {metric.state_link(), slicedStateAtoms, stateGroupMap},
                {/*eventActivationMap=*/{}, /*eventDeactivationMap=*/{}},
                {dimensionSoftLimit, dimensionHardLimit});
    }

    static KllMetric createMetric() {
        KllMetric metric;
        metric.set_id(metricId);
        metric.set_bucket(ONE_MINUTE);
        metric.mutable_kll_field()->set_field(atomId);
        metric.mutable_kll_field()->add_child()->set_field(2);
        metric.set_split_bucket_for_app_upgrade(true);
        return metric;
    }

    static KllMetric createMetricWithCondition() {
        KllMetric metric = KllMetricProducerTestHelper::createMetric();
        metric.set_condition(StringToId("SCREEN_ON"));
        return metric;
    }
};

// Setup for parameterized tests.
class KllMetricProducerTest_PartialBucket : public TestWithParam<BucketSplitEvent> {};

INSTANTIATE_TEST_SUITE_P(KllMetricProducerTest_PartialBucket, KllMetricProducerTest_PartialBucket,
                         testing::Values(APP_UPGRADE, BOOT_COMPLETE));

TEST_P(KllMetricProducerTest_PartialBucket, TestPushedEventsMultipleBuckets) {
    const KllMetric& metric = KllMetricProducerTestHelper::createMetric();
    sp<KllMetricProducer> kllProducer =
            KllMetricProducerTestHelper::createKllProducerNoConditions(metric);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, atomId, bucketStartTimeNs + 10, 10);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    ASSERT_EQ(1UL, kllProducer->mCurrentSlicedBucket.size());

    const int64_t partialBucketSplitTimeNs = bucketStartTimeNs + 150;
    switch (GetParam()) {
        case APP_UPGRADE:
            kllProducer->notifyAppUpgrade(partialBucketSplitTimeNs);
            break;
        case BOOT_COMPLETE:
            kllProducer->onStatsdInitCompleted(partialBucketSplitTimeNs);
            break;
    }
    TRACE_CALL(assertPastBucketsSingleKey, kllProducer->mPastBuckets, {1},
               {partialBucketSplitTimeNs - bucketStartTimeNs}, {bucketStartTimeNs},
               {partialBucketSplitTimeNs});
    EXPECT_EQ(partialBucketSplitTimeNs, kllProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(0, kllProducer->getCurrentBucketNum());

    // Event arrives after the bucket split.
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, atomId, bucketStartTimeNs + 59 * NS_PER_SEC, 20);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    TRACE_CALL(assertPastBucketsSingleKey, kllProducer->mPastBuckets, {1},
               {partialBucketSplitTimeNs - bucketStartTimeNs}, {bucketStartTimeNs},
               {partialBucketSplitTimeNs});
    EXPECT_EQ(partialBucketSplitTimeNs, kllProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(0, kllProducer->getCurrentBucketNum());

    // Next value should create a new bucket.
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event3, atomId, bucket2StartTimeNs + 5 * NS_PER_SEC, 10);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event3);
    TRACE_CALL(assertPastBucketsSingleKey, kllProducer->mPastBuckets, {1, 1},
               {partialBucketSplitTimeNs - bucketStartTimeNs,
                bucket2StartTimeNs - partialBucketSplitTimeNs},
               {bucketStartTimeNs, partialBucketSplitTimeNs},
               {partialBucketSplitTimeNs, bucket2StartTimeNs});
    EXPECT_EQ(bucketStartTimeNs + bucketSizeNs, kllProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(1, kllProducer->getCurrentBucketNum());
}

TEST(KllMetricProducerTest, TestPushedEventsWithoutCondition) {
    const KllMetric& metric = KllMetricProducerTestHelper::createMetric();
    sp<KllMetricProducer> kllProducer =
            KllMetricProducerTestHelper::createKllProducerNoConditions(metric);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, atomId, bucketStartTimeNs + 10, 10);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, atomId, bucketStartTimeNs + 20, 20);

    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    // has one slice
    ASSERT_EQ(1UL, kllProducer->mCurrentSlicedBucket.size());
    const KllMetricProducer::Interval& curInterval0 =
            kllProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(1, curInterval0.aggregate->num_values());
    EXPECT_GT(curInterval0.sampleSize, 0);

    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, kllProducer->mCurrentSlicedBucket.size());
    EXPECT_EQ(2, curInterval0.aggregate->num_values());

    kllProducer->flushIfNeededLocked(bucket2StartTimeNs);
    TRACE_CALL(assertPastBucketsSingleKey, kllProducer->mPastBuckets, {2}, {bucketSizeNs},
               {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(KllMetricProducerTest, TestPushedEventsWithCondition) {
    const KllMetric& metric = KllMetricProducerTestHelper::createMetric();
    sp<KllMetricProducer> kllProducer = KllMetricProducerTestHelper::createKllProducerWithCondition(
            metric, ConditionState::kFalse);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, atomId, bucketStartTimeNs + 10, 10);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    // Has 0 slices.
    ASSERT_EQ(0UL, kllProducer->mCurrentSlicedBucket.size());

    kllProducer->onConditionChangedLocked(true, bucketStartTimeNs + 15);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, atomId, bucketStartTimeNs + 20, 20);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, kllProducer->mCurrentSlicedBucket.size());
    const KllMetricProducer::Interval& curInterval0 =
            kllProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(1, curInterval0.aggregate->num_values());

    LogEvent event3(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event3, atomId, bucketStartTimeNs + 30, 30);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event3);

    // has one slice
    ASSERT_EQ(1UL, kllProducer->mCurrentSlicedBucket.size());
    EXPECT_EQ(2, curInterval0.aggregate->num_values());

    kllProducer->onConditionChangedLocked(false, bucketStartTimeNs + 35);

    LogEvent event4(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event4, atomId, bucketStartTimeNs + 40, 40);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event4);

    // has one slice
    ASSERT_EQ(1UL, kllProducer->mCurrentSlicedBucket.size());
    EXPECT_EQ(2, curInterval0.aggregate->num_values());

    kllProducer->flushIfNeededLocked(bucket2StartTimeNs);
    TRACE_CALL(assertPastBucketsSingleKey, kllProducer->mPastBuckets, {2}, {20},
               {bucketStartTimeNs}, {bucket2StartTimeNs});
}

/*
 * Test that CONDITION_UNKNOWN dump reason is logged due to an unknown condition
 * when a metric is initialized.
 */
TEST(KllMetricProducerTest_BucketDrop, TestInvalidBucketWhenConditionUnknown) {
    const KllMetric& metric = KllMetricProducerTestHelper::createMetricWithCondition();
    sp<KllMetricProducer> kllProducer = KllMetricProducerTestHelper::createKllProducerWithCondition(
            metric, ConditionState::kUnknown);

    // Condition change event.
    kllProducer->onConditionChanged(true, bucketStartTimeNs + 50);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 10000;
    kllProducer->onDumpReport(dumpReportTimeNs, true /* include recent buckets */, true,
                              NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_kll_metrics());
    ASSERT_EQ(0, report.kll_metrics().data_size());
    ASSERT_EQ(1, report.kll_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.kll_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.kll_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.kll_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.kll_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that BUCKET_TOO_SMALL dump reason is logged when a flushed bucket size
 * is smaller than the "min_bucket_size_nanos" specified in the metric config.
 */
TEST(KllMetricProducerTest_BucketDrop, TestBucketDropWhenBucketTooSmall) {
    KllMetric metric = KllMetricProducerTestHelper::createMetric();
    metric.set_min_bucket_size_nanos(10 * NS_PER_SEC);  // 10 seconds

    sp<KllMetricProducer> kllProducer =
            KllMetricProducerTestHelper::createKllProducerNoConditions(metric);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, atomId, bucketStartTimeNs + 10, 10);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 9000000;
    kllProducer->onDumpReport(dumpReportTimeNs, true /* include recent buckets */, true,
                              NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_kll_metrics());
    ASSERT_EQ(0, report.kll_metrics().data_size());
    ASSERT_EQ(1, report.kll_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.kll_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.kll_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.kll_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.kll_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::BUCKET_TOO_SMALL, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that NO_DATA dump reason is logged when a flushed bucket contains no data.
 */
TEST(KllMetricProducerTest_BucketDrop, TestBucketDropWhenDataUnavailable) {
    const KllMetric& metric = KllMetricProducerTestHelper::createMetricWithCondition();

    sp<KllMetricProducer> kllProducer = KllMetricProducerTestHelper::createKllProducerWithCondition(
            metric, ConditionState::kFalse);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 10000000000;  // 10 seconds
    kllProducer->onDumpReport(dumpReportTimeNs, true /* include current bucket */, true,
                              NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_kll_metrics());
    ASSERT_EQ(0, report.kll_metrics().data_size());
    ASSERT_EQ(1, report.kll_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.kll_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.kll_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.kll_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.kll_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::NO_DATA, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test bucket splits when condition is unknown.
 */
TEST(KllMetricProducerTest, TestForcedBucketSplitWhenConditionUnknownSkipsBucket) {
    const KllMetric& metric = KllMetricProducerTestHelper::createMetricWithCondition();

    sp<KllMetricProducer> kllProducer = KllMetricProducerTestHelper::createKllProducerWithCondition(
            metric, ConditionState::kUnknown);

    // App update event.
    int64_t appUpdateTimeNs = bucketStartTimeNs + 1000;
    kllProducer->notifyAppUpgrade(appUpdateTimeNs);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 10000000000;  // 10 seconds
    kllProducer->onDumpReport(dumpReportTimeNs, false /* include current buckets */, true,
                              NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_kll_metrics());
    ASSERT_EQ(0, report.kll_metrics().data_size());
    ASSERT_EQ(1, report.kll_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.kll_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(appUpdateTimeNs),
              report.kll_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.kll_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.kll_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(appUpdateTimeNs), dropEvent.drop_time_millis());
}

TEST(KllMetricProducerTest, TestByteSize) {
    const KllMetric& metric = KllMetricProducerTestHelper::createMetric();
    sp<KllMetricProducer> kllProducer =
            KllMetricProducerTestHelper::createKllProducerNoConditions(metric);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, atomId, bucketStartTimeNs + 10, 10);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, atomId, bucketStartTimeNs + 20, 20);

    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    kllProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);
    kllProducer->flushIfNeededLocked(bucket2StartTimeNs);

    const size_t expectedSize = kllProducer->kBucketSize + 4 /* one int aggIndex entry */ +
                                16 /* two int64_t entries in KllQuantile object */;

    EXPECT_EQ(expectedSize, kllProducer->byteSize());
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
