// Copyright (C) 2017 The Android Open Source Project
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

#include "src/metrics/NumericValueMetricProducer.h"

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
using std::make_shared;
using std::nullopt;
using std::optional;
using std::set;
using std::shared_ptr;
using std::unordered_map;
using std::vector;

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {

const ConfigKey kConfigKey(0, 12345);
const int tagId = 1;
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
double epsilon = 0.001;

static void assertPastBucketValuesSingleKey(
        const std::unordered_map<MetricDimensionKey, std::vector<PastBucket<Value>>>& mPastBuckets,
        const std::initializer_list<int>& expectedValuesList,
        const std::initializer_list<int64_t>& expectedDurationNsList,
        const std::initializer_list<int64_t>& expectedCorrectionNsList,
        const std::initializer_list<int64_t>& expectedStartTimeNsList,
        const std::initializer_list<int64_t>& expectedEndTimeNsList) {
    vector<int> expectedValues(expectedValuesList);
    vector<int64_t> expectedDurationNs(expectedDurationNsList);
    vector<int64_t> expectedCorrectionNs(expectedCorrectionNsList);
    vector<int64_t> expectedStartTimeNs(expectedStartTimeNsList);
    vector<int64_t> expectedEndTimeNs(expectedEndTimeNsList);

    ASSERT_EQ(expectedValues.size(), expectedDurationNs.size());
    ASSERT_EQ(expectedValues.size(), expectedStartTimeNs.size());
    ASSERT_EQ(expectedValues.size(), expectedEndTimeNs.size());
    ASSERT_EQ(expectedValues.size(), expectedCorrectionNs.size());

    if (expectedValues.size() == 0) {
        ASSERT_EQ(0, mPastBuckets.size());
        return;
    }

    ASSERT_EQ(1, mPastBuckets.size());
    ASSERT_EQ(expectedValues.size(), mPastBuckets.begin()->second.size());

    const vector<PastBucket<Value>>& buckets = mPastBuckets.begin()->second;
    for (int i = 0; i < expectedValues.size(); i++) {
        EXPECT_EQ(expectedValues[i], buckets[i].aggregates[0].long_value)
                << "Values differ at index " << i;
        EXPECT_EQ(expectedDurationNs[i], buckets[i].mConditionTrueNs)
                << "Condition duration value differ at index " << i;
        EXPECT_EQ(expectedStartTimeNs[i], buckets[i].mBucketStartNs)
                << "Start time differs at index " << i;
        EXPECT_EQ(expectedEndTimeNs[i], buckets[i].mBucketEndNs)
                << "End time differs at index " << i;
        EXPECT_EQ(expectedCorrectionNs[i], buckets[i].mConditionCorrectionNs)
                << "Condition correction differs at index " << i;
    }
}

}  // anonymous namespace

class NumericValueMetricProducerTestHelper {
public:
    static sp<NumericValueMetricProducer> createValueProducerNoConditions(
            sp<MockStatsPullerManager>& pullerManager, ValueMetric& metric,
            const int pullAtomId = tagId) {
        return createValueProducer(pullerManager, metric, pullAtomId);
    }

    static sp<NumericValueMetricProducer> createValueProducerWithCondition(
            sp<MockStatsPullerManager>& pullerManager, ValueMetric& metric,
            ConditionState conditionAfterFirstBucketPrepared, const int pullAtomId = tagId) {
        return createValueProducer(pullerManager, metric, pullAtomId,
                                   conditionAfterFirstBucketPrepared);
    }

    static sp<NumericValueMetricProducer> createValueProducerWithState(
            sp<MockStatsPullerManager>& pullerManager, ValueMetric& metric,
            vector<int32_t> slicedStateAtoms,
            unordered_map<int, unordered_map<int, int64_t>> stateGroupMap,
            const int pullAtomId = tagId) {
        return createValueProducer(pullerManager, metric, pullAtomId,
                                   /*conditionAfterFirstBucketPrepared=*/nullopt, slicedStateAtoms,
                                   stateGroupMap);
    }

    static sp<NumericValueMetricProducer> createValueProducerWithConditionAndState(
            sp<MockStatsPullerManager>& pullerManager, ValueMetric& metric,
            vector<int32_t> slicedStateAtoms,
            unordered_map<int, unordered_map<int, int64_t>> stateGroupMap,
            ConditionState conditionAfterFirstBucketPrepared, const int pullAtomId = tagId) {
        return createValueProducer(pullerManager, metric, pullAtomId,
                                   conditionAfterFirstBucketPrepared, slicedStateAtoms,
                                   stateGroupMap);
    }

    static sp<NumericValueMetricProducer> createValueProducerWithSampling(
            sp<MockStatsPullerManager>& pullerManager, ValueMetric& metric,
            const int pullAtomId = tagId) {
        sp<NumericValueMetricProducer> valueProducer = createValueProducer(
                pullerManager, metric, pullAtomId, /*conditionAfterFirstBucketPrepared=*/nullopt,
                /*slicedStateAtoms=*/{}, /*stateGroupMap=*/{}, bucketStartTimeNs, bucketStartTimeNs,
                /*eventMatcherWizard=*/nullptr);

        SamplingInfo samplingInfo;
        samplingInfo.shardCount = metric.dimensional_sampling_info().shard_count();
        translateFieldMatcher(metric.dimensional_sampling_info().sampled_what_field(),
                              &samplingInfo.sampledWhatFields);
        valueProducer->setSamplingInfo(samplingInfo);
        return valueProducer;
    }

    static sp<NumericValueMetricProducer> createValueProducerWithBucketParams(
            sp<MockStatsPullerManager>& pullerManager, ValueMetric& metric,
            const int64_t timeBaseNs, const int64_t startTimeNs, const int pullAtomId = tagId) {
        return createValueProducer(
                pullerManager, metric, pullAtomId, /*conditionAfterFirstBucketPrepared=*/nullopt,
                /*slicedStateAtoms=*/{}, /*stateGroupMap=*/{}, timeBaseNs, startTimeNs);
    }

    static sp<NumericValueMetricProducer> createValueProducerWithEventMatcherWizard(
            sp<MockStatsPullerManager>& pullerManager, ValueMetric& metric,
            const sp<EventMatcherWizard>& eventMatcherWizard, const int pullAtomId = tagId) {
        return createValueProducer(pullerManager, metric, pullAtomId,
                                   /*conditionAfterFirstBucketPrepared=*/nullopt,
                                   /*slicedStateAtoms=*/{}, /*stateGroupMap=*/{}, bucketStartTimeNs,
                                   bucketStartTimeNs, eventMatcherWizard);
    }

    static sp<NumericValueMetricProducer> createValueProducer(
            sp<MockStatsPullerManager>& pullerManager, ValueMetric& metric, const int pullAtomId,
            optional<ConditionState> conditionAfterFirstBucketPrepared = nullopt,
            vector<int32_t> slicedStateAtoms = {},
            unordered_map<int, unordered_map<int, int64_t>> stateGroupMap = {},
            const int64_t timeBaseNs = bucketStartTimeNs,
            const int64_t startTimeNs = bucketStartTimeNs,
            sp<EventMatcherWizard> eventMatcherWizard = nullptr) {
        if (eventMatcherWizard == nullptr) {
            eventMatcherWizard = createEventMatcherWizard(tagId, logEventMatcherIndex);
        }
        sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
        if (pullAtomId != -1) {
            EXPECT_CALL(*pullerManager, RegisterReceiver(tagId, kConfigKey, _, _, _))
                    .WillOnce(Return());
            EXPECT_CALL(*pullerManager, UnRegisterReceiver(tagId, kConfigKey, _))
                    .WillRepeatedly(Return());
        }
        const int64_t bucketSizeNs = MillisToNano(
                TimeUnitToBucketSizeInMillisGuardrailed(kConfigKey.GetUid(), metric.bucket()));
        const bool containsAnyPositionInDimensionsInWhat =
                HasPositionANY(metric.dimensions_in_what());
        const bool shouldUseNestedDimensions =
                ShouldUseNestedDimensions(metric.dimensions_in_what());

        vector<Matcher> fieldMatchers;
        translateFieldMatcher(metric.value_field(), &fieldMatchers);

        const auto [dimensionSoftLimit, dimensionHardLimit] =
                StatsdStats::getAtomDimensionKeySizeLimits(
                        tagId, StatsdStats::kDimensionKeySizeHardLimitMin);

        int conditionIndex = conditionAfterFirstBucketPrepared ? 0 : -1;
        vector<ConditionState> initialConditionCache;
        if (conditionAfterFirstBucketPrepared) {
            initialConditionCache.push_back(ConditionState::kUnknown);
        }

        // get the condition_correction_threshold_nanos value
        const optional<int64_t> conditionCorrectionThresholdNs =
                metric.has_condition_correction_threshold_nanos()
                        ? optional<int64_t>(metric.condition_correction_threshold_nanos())
                        : nullopt;

        sp<NumericValueMetricProducer> valueProducer = new NumericValueMetricProducer(
                kConfigKey, metric, protoHash, {pullAtomId, pullerManager},
                {timeBaseNs, startTimeNs, bucketSizeNs, metric.min_bucket_size_nanos(),
                 conditionCorrectionThresholdNs, metric.split_bucket_for_app_upgrade()},
                {containsAnyPositionInDimensionsInWhat, shouldUseNestedDimensions,
                 logEventMatcherIndex, eventMatcherWizard, metric.dimensions_in_what(),
                 fieldMatchers},
                {conditionIndex, metric.links(), initialConditionCache, wizard},
                {metric.state_link(), slicedStateAtoms, stateGroupMap},
                {/*eventActivationMap=*/{}, /*eventDeactivationMap=*/{}},
                {dimensionSoftLimit, dimensionHardLimit});

        valueProducer->prepareFirstBucket();
        if (conditionAfterFirstBucketPrepared) {
            valueProducer->mCondition = conditionAfterFirstBucketPrepared.value();
        }
        return valueProducer;
    }

    static ValueMetric createMetric() {
        ValueMetric metric;
        metric.set_id(metricId);
        metric.set_bucket(ONE_MINUTE);
        metric.mutable_value_field()->set_field(tagId);
        metric.mutable_value_field()->add_child()->set_field(2);
        metric.set_max_pull_delay_sec(INT_MAX);
        metric.set_split_bucket_for_app_upgrade(true);
        return metric;
    }

    static ValueMetric createMetricWithCondition() {
        ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
        metric.set_condition(StringToId("SCREEN_ON"));
        return metric;
    }

    static ValueMetric createMetricWithState(string state) {
        ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
        metric.add_slice_by_state(StringToId(state));
        return metric;
    }

    static ValueMetric createMetricWithConditionAndState(string state) {
        ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
        metric.set_condition(StringToId("SCREEN_ON"));
        metric.add_slice_by_state(StringToId(state));
        return metric;
    }

    static ValueMetric createMetricWithRepeatedValueField() {
        ValueMetric metric;
        metric.set_id(metricId);
        metric.set_bucket(ONE_MINUTE);
        metric.mutable_value_field()->set_field(tagId);
        FieldMatcher* valueChild = metric.mutable_value_field()->add_child();
        valueChild->set_field(3);
        valueChild->set_position(Position::FIRST);
        metric.set_max_pull_delay_sec(INT_MAX);
        metric.set_split_bucket_for_app_upgrade(true);
        metric.set_aggregation_type(ValueMetric_AggregationType_SUM);
        return metric;
    }
};

// Setup for parameterized tests.
class NumericValueMetricProducerTest_PartialBucket : public TestWithParam<BucketSplitEvent> {};

INSTANTIATE_TEST_SUITE_P(NumericValueMetricProducerTest_PartialBucket,
                         NumericValueMetricProducerTest_PartialBucket,
                         testing::Values(APP_UPGRADE, BOOT_COMPLETE));

/*
 * Tests that the first bucket works correctly
 */
TEST(NumericValueMetricProducerTest, TestCalcPreviousBucketEndTime) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    int64_t startTimeBase = 11;
    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    // statsd started long ago.
    // The metric starts in the middle of the bucket
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithBucketParams(
                    pullerManager, metric, startTimeBase, /*startTimeNs=*/22, /*pullAtomId=*/-1);

    EXPECT_EQ(startTimeBase, valueProducer->calcPreviousBucketEndTime(60 * NS_PER_SEC + 10));
    EXPECT_EQ(startTimeBase, valueProducer->calcPreviousBucketEndTime(60 * NS_PER_SEC + 10));
    EXPECT_EQ(60 * NS_PER_SEC + startTimeBase,
              valueProducer->calcPreviousBucketEndTime(2 * 60 * NS_PER_SEC));
    EXPECT_EQ(2 * 60 * NS_PER_SEC + startTimeBase,
              valueProducer->calcPreviousBucketEndTime(3 * 60 * NS_PER_SEC));
}

/*
 * Tests that the first bucket works correctly
 */
TEST(NumericValueMetricProducerTest, TestFirstBucket) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    // statsd started long ago.
    // The metric starts in the middle of the bucket
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithBucketParams(
                    pullerManager, metric, /*timeBaseNs=*/5,
                    /*startTimeNs=*/600 * NS_PER_SEC + NS_PER_SEC / 2, /*pullAtomId=*/-1);

    EXPECT_EQ(600500000000, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(10, valueProducer->mCurrentBucketNum);
    EXPECT_EQ(660000000005, valueProducer->getCurrentBucketEndTimeNs());
}

/*
 * Tests pulled atoms with no conditions
 */
TEST(NumericValueMetricProducerTest, TestPulledEventsNoCondition) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 3));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 11));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    // empty since bucket is flushed
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    // dimInfos holds the base
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];

    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(11, curBase.value().long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {8}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 1, 23));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    // empty since bucket is cleared
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    // dimInfos holds the base
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];

    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(23, curBase.value().long_value);
    assertPastBucketValuesSingleKey(
            valueProducer->mPastBuckets, {8, 12}, {bucketSizeNs, bucketSizeNs}, {0, 0},
            {bucketStartTimeNs, bucket2StartTimeNs}, {bucket2StartTimeNs, bucket3StartTimeNs});

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket4StartTimeNs + 1, 36));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);
    // empty since bucket is cleared
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    // dimInfos holds the base
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];

    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(36, curBase.value().long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {8, 12, 13},
                                    {bucketSizeNs, bucketSizeNs, bucketSizeNs}, {0, 0, 0},
                                    {bucketStartTimeNs, bucket2StartTimeNs, bucket3StartTimeNs},
                                    {bucket2StartTimeNs, bucket3StartTimeNs, bucket4StartTimeNs});
}

TEST_P(NumericValueMetricProducerTest_PartialBucket, TestPartialBucketCreated) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    int64_t partialBucketSplitTimeNs = bucket2StartTimeNs + 2;
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Initialize bucket.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, 1));
                return true;
            }))
            // Partial bucket.
            .WillOnce(Invoke([partialBucketSplitTimeNs](int tagId, const ConfigKey&,
                                                        const int64_t eventTimeNs,
                                                        vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, partialBucketSplitTimeNs);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, partialBucketSplitTimeNs + 8, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    // First bucket ends.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 10, 2));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // Partial buckets created in 2nd bucket.
    switch (GetParam()) {
        case APP_UPGRADE:
            valueProducer->notifyAppUpgrade(partialBucketSplitTimeNs);
            break;
        case BOOT_COMPLETE:
            valueProducer->onStatsdInitCompleted(partialBucketSplitTimeNs);
            break;
    }
    EXPECT_EQ(partialBucketSplitTimeNs, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(1, valueProducer->getCurrentBucketNum());

    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {1, 3},
                                    {bucketSizeNs, partialBucketSplitTimeNs - bucket2StartTimeNs},
                                    {0, 0}, {bucketStartTimeNs, bucket2StartTimeNs},
                                    {bucket2StartTimeNs, partialBucketSplitTimeNs});
}

/*
 * Tests pulled atoms with filtering
 */
TEST(NumericValueMetricProducerTest, TestPulledEventsWithFiltering) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    FieldValueMatcher fvm;
    fvm.set_field(1);
    fvm.set_eq_int(3);
    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex, {fvm});
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs, 3, 3));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithEventMatcherWizard(
                    pullerManager, metric, eventMatcherWizard);

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 1, 3, 11));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    // empty since bucket is cleared
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    // dimInfos holds the base
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];

    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(11, curBase.value().long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {8}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket3StartTimeNs + 1, 4, 23));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    // No new data seen, so data has been cleared.
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());

    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket4StartTimeNs + 1, 3, 36));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];

    // the base was reset
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(36, curBase.value().long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {8}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

/*
 * Tests pulled atoms with no conditions and take absolute value after reset
 */
TEST(NumericValueMetricProducerTest, TestPulledEventsTakeAbsoluteValueOnReset) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_use_absolute_value_on_reset(true);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Return(true));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 11));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    // empty since bucket is cleared
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    // dimInfos holds the base
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];

    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(11, curBase.value().long_value);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    allData.clear();
    // 10 is less than 11, so we reset and keep 10 as the value.
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 1, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    // empty since the bucket is flushed.
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(10, curBase.value().long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10}, {bucketSizeNs}, {0},
                                    {bucket2StartTimeNs}, {bucket3StartTimeNs});

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket4StartTimeNs + 1, 36));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(36, curBase.value().long_value);
    assertPastBucketValuesSingleKey(
            valueProducer->mPastBuckets, {10, 26}, {bucketSizeNs, bucketSizeNs}, {0, 0},
            {bucket2StartTimeNs, bucket3StartTimeNs}, {bucket3StartTimeNs, bucket4StartTimeNs});
}

/*
 * Tests pulled atoms with no conditions and take zero value after reset
 */
TEST(NumericValueMetricProducerTest, TestPulledEventsTakeZeroOnReset) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Return(false));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 11));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    // empty since bucket is cleared
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    // mDimInfos holds the base
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];

    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(11, curBase.value().long_value);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    allData.clear();
    // 10 is less than 11, so we reset. 10 only updates the base.
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 1, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(10, curBase.value().long_value);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket4StartTimeNs + 1, 36));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(36, curBase.value().long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {26}, {bucketSizeNs}, {0},
                                    {bucket3StartTimeNs}, {bucket4StartTimeNs});
}

/*
 * Test pulled event with non sliced condition.
 */
TEST(NumericValueMetricProducerTest, TestEventsWithNonSlicedCondition) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 8);  // First condition change.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 100));
                return true;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 1);  // Second condition change.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 130));
                return true;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket3StartTimeNs + 1);  // Third condition change.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 1, 180));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    // startUpdated:false sum:0 start:100
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(100, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 110));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10}, {bucketSizeNs - 8}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(110, curBase.value().long_value);

    valueProducer->onConditionChanged(false, bucket2StartTimeNs + 1);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10}, {bucketSizeNs - 8}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(20, curInterval.aggregate.long_value);
    EXPECT_EQ(false, curBase.has_value());

    valueProducer->onConditionChanged(true, bucket3StartTimeNs + 1);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10, 20}, {bucketSizeNs - 8, 1},
                                    {0, 0}, {bucketStartTimeNs, bucket2StartTimeNs},
                                    {bucket2StartTimeNs, bucket3StartTimeNs});
}

TEST_P(NumericValueMetricProducerTest_PartialBucket, TestPushedEvents) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());

    int64_t partialBucketSplitTimeNs = bucketStartTimeNs + 150;
    switch (GetParam()) {
        case APP_UPGRADE:
            valueProducer->notifyAppUpgrade(partialBucketSplitTimeNs);
            break;
        case BOOT_COMPLETE:
            valueProducer->onStatsdInitCompleted(partialBucketSplitTimeNs);
            break;
    }
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10},
                                    {partialBucketSplitTimeNs - bucketStartTimeNs}, {0},
                                    {bucketStartTimeNs}, {partialBucketSplitTimeNs});
    EXPECT_EQ(partialBucketSplitTimeNs, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(0, valueProducer->getCurrentBucketNum());

    // Event arrives after the bucket split.
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 59 * NS_PER_SEC, 20);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10},
                                    {partialBucketSplitTimeNs - bucketStartTimeNs}, {0},
                                    {bucketStartTimeNs}, {partialBucketSplitTimeNs});
    EXPECT_EQ(partialBucketSplitTimeNs, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(0, valueProducer->getCurrentBucketNum());

    // Next value should create a new bucket.
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event3, tagId, bucket2StartTimeNs + 5 * NS_PER_SEC, 10);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event3);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10, 20},
                                    {partialBucketSplitTimeNs - bucketStartTimeNs,
                                     bucket2StartTimeNs - partialBucketSplitTimeNs},
                                    {0, 5 * NS_PER_SEC},
                                    {bucketStartTimeNs, partialBucketSplitTimeNs},
                                    {partialBucketSplitTimeNs, bucket2StartTimeNs});
    EXPECT_EQ(bucketStartTimeNs + bucketSizeNs, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(1, valueProducer->getCurrentBucketNum());
}

TEST_P(NumericValueMetricProducerTest_PartialBucket, TestPulledValue) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    int64_t partialBucketSplitTimeNs = bucket2StartTimeNs + 150;
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Return(true))
            .WillOnce(Invoke([partialBucketSplitTimeNs](int tagId, const ConfigKey&,
                                                        const int64_t eventTimeNs,
                                                        vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, partialBucketSplitTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, partialBucketSplitTimeNs, 120));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 100));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());

    switch (GetParam()) {
        case APP_UPGRADE:
            valueProducer->notifyAppUpgrade(partialBucketSplitTimeNs);
            break;
        case BOOT_COMPLETE:
            valueProducer->onStatsdInitCompleted(partialBucketSplitTimeNs);
            break;
    }
    EXPECT_EQ(partialBucketSplitTimeNs, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(1, valueProducer->getCurrentBucketNum());
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20}, {150}, {0},
                                    {bucket2StartTimeNs}, {partialBucketSplitTimeNs});

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 1, 150));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    EXPECT_EQ(bucket3StartTimeNs, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(2, valueProducer->getCurrentBucketNum());
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20, 30},
                                    {150, bucketSizeNs - 150}, {0, 0},
                                    {bucket2StartTimeNs, partialBucketSplitTimeNs},
                                    {partialBucketSplitTimeNs, bucket3StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestPulledWithAppUpgradeDisabled) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_split_bucket_for_app_upgrade(false);

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Return(true));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 100));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    valueProducer->notifyAppUpgrade(bucket2StartTimeNs + 150);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets[DEFAULT_METRIC_DIMENSION_KEY].size());
    EXPECT_EQ(bucket2StartTimeNs, valueProducer->mCurrentBucketStartTimeNs);
}

TEST_P(NumericValueMetricProducerTest_PartialBucket, TestPulledValueWhileConditionFalse) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 1);  // Condition change to true time.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, 100));
                return true;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs,
                          bucket2StartTimeNs - 100);  // Condition change to false time.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs - 100, 120));
                return true;
            }));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 1);

    valueProducer->onConditionChanged(false, bucket2StartTimeNs - 100);
    EXPECT_FALSE(valueProducer->mCondition);

    int64_t partialBucketSplitTimeNs = bucket2StartTimeNs - 50;
    switch (GetParam()) {
        case APP_UPGRADE:
            valueProducer->notifyAppUpgrade(partialBucketSplitTimeNs);
            break;
        case BOOT_COMPLETE:
            valueProducer->onStatsdInitCompleted(partialBucketSplitTimeNs);
            break;
    }
    // Expect one full buckets already done and starting a partial bucket.
    EXPECT_EQ(partialBucketSplitTimeNs, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(0, valueProducer->getCurrentBucketNum());
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20},
                                    {(bucket2StartTimeNs - 100) - (bucketStartTimeNs + 1)}, {0},
                                    {bucketStartTimeNs}, {partialBucketSplitTimeNs});
    EXPECT_FALSE(valueProducer->mCondition);
}

TEST(NumericValueMetricProducerTest, TestPushedEventsWithoutCondition) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 20, 20);

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(10, curInterval.aggregate.long_value);
    EXPECT_TRUE(curInterval.hasValue());

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(30, curInterval.aggregate.long_value);

    // Check dump report.
    ProtoOutputStream output;
    valueProducer->onDumpReport(bucket2StartTimeNs + 10000, false /* include recent buckets */,
                                true, FAST /* dumpLatency */, nullptr, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());

    auto data = report.value_metrics().data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(30, data.bucket_info(0).values(0).value_long());
    EXPECT_EQ(bucketStartTimeNs, data.bucket_info(0).start_bucket_elapsed_nanos());
    EXPECT_EQ(bucket2StartTimeNs, data.bucket_info(0).end_bucket_elapsed_nanos());
    EXPECT_FALSE(data.has_dimensions_in_what());

    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
}

TEST(NumericValueMetricProducerTest, TestPushedEventsWithCondition) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    // has 1 slice
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());

    valueProducer->onConditionChangedLocked(true, bucketStartTimeNs + 15);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 20, 20);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(20, curInterval.aggregate.long_value);

    LogEvent event3(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event3, tagId, bucketStartTimeNs + 30, 30);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event3);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(50, curInterval.aggregate.long_value);

    valueProducer->onConditionChangedLocked(false, bucketStartTimeNs + 35);

    LogEvent event4(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event4, tagId, bucketStartTimeNs + 40, 40);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event4);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(50, curInterval.aggregate.long_value);

    // Check dump report.
    ProtoOutputStream output;
    valueProducer->onDumpReport(bucket2StartTimeNs + 10000, false /* include recent buckets */,
                                true, FAST /* dumpLatency */, nullptr, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());

    auto data = report.value_metrics().data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(50, data.bucket_info(0).values(0).value_long());
    EXPECT_EQ(20, data.bucket_info(0).condition_true_nanos());
    EXPECT_EQ(bucketStartTimeNs, data.bucket_info(0).start_bucket_elapsed_nanos());
    EXPECT_EQ(bucket2StartTimeNs, data.bucket_info(0).end_bucket_elapsed_nanos());
    EXPECT_FALSE(data.has_dimensions_in_what());

    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
}

TEST(NumericValueMetricProducerTest, TestAnomalyDetection) {
    sp<AlarmMonitor> alarmMonitor;
    Alert alert;
    alert.set_id(101);
    alert.set_metric_id(metricId);
    alert.set_trigger_if_sum_gt(130);
    alert.set_num_buckets(2);
    const int32_t refPeriodSec = 3;
    alert.set_refractory_period_secs(refPeriodSec);

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    sp<AnomalyTracker> anomalyTracker =
            valueProducer->addAnomalyTracker(alert, alarmMonitor, UPDATE_NEW, bucketStartTimeNs);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 1 * NS_PER_SEC, 10);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 2 + NS_PER_SEC, 20);

    LogEvent event3(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event3, tagId,
                                bucketStartTimeNs + 2 * bucketSizeNs + 1 * NS_PER_SEC, 130);

    LogEvent event4(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event4, tagId,
                                bucketStartTimeNs + 3 * bucketSizeNs + 1 * NS_PER_SEC, 1);

    LogEvent event5(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event5, tagId,
                                bucketStartTimeNs + 3 * bucketSizeNs + 2 * NS_PER_SEC, 150);

    LogEvent event6(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event6, tagId,
                                bucketStartTimeNs + 3 * bucketSizeNs + 10 * NS_PER_SEC, 160);

    // Two events in bucket #0.
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);
    // Value sum == 30 <= 130.
    EXPECT_EQ(anomalyTracker->getRefractoryPeriodEndsSec(DEFAULT_METRIC_DIMENSION_KEY), 0U);

    // One event in bucket #2. No alarm as bucket #0 is trashed out.
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event3);
    // Value sum == 130 <= 130.
    EXPECT_EQ(anomalyTracker->getRefractoryPeriodEndsSec(DEFAULT_METRIC_DIMENSION_KEY), 0U);

    // Three events in bucket #3.
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event4);
    // Anomaly at event 4 since Value sum == 131 > 130!
    EXPECT_EQ(anomalyTracker->getRefractoryPeriodEndsSec(DEFAULT_METRIC_DIMENSION_KEY),
              std::ceil(1.0 * event4.GetElapsedTimestampNs() / NS_PER_SEC + refPeriodSec));
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event5);
    // Event 5 is within 3 sec refractory period. Thus last alarm timestamp is still event4.
    EXPECT_EQ(anomalyTracker->getRefractoryPeriodEndsSec(DEFAULT_METRIC_DIMENSION_KEY),
              std::ceil(1.0 * event4.GetElapsedTimestampNs() / NS_PER_SEC + refPeriodSec));

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event6);
    // Anomaly at event 6 since Value sum == 160 > 130 and after refractory period.
    EXPECT_EQ(anomalyTracker->getRefractoryPeriodEndsSec(DEFAULT_METRIC_DIMENSION_KEY),
              std::ceil(1.0 * event6.GetElapsedTimestampNs() / NS_PER_SEC + refPeriodSec));
}

TEST(NumericValueMetricProducerTest, TestAnomalyDetectionMultipleBucketsSkipped) {
    sp<AlarmMonitor> alarmMonitor;
    Alert alert;
    alert.set_id(101);
    alert.set_metric_id(metricId);
    alert.set_trigger_if_sum_gt(100);
    alert.set_num_buckets(1);
    const int32_t refPeriodSec = 3;
    alert.set_refractory_period_secs(refPeriodSec);

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 1);  // Condition change to true time.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, 0));
                return true;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs,
                          bucket3StartTimeNs + 100);  // Condition changed to false time.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 100, 120));
                return true;
            }));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);
    sp<AnomalyTracker> anomalyTracker =
            valueProducer->addAnomalyTracker(alert, alarmMonitor, UPDATE_NEW, bucketStartTimeNs);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 1);

    // multiple buckets should be skipped here.
    valueProducer->onConditionChanged(false, bucket3StartTimeNs + 100);

    // No alert is fired when multiple buckets are skipped.
    EXPECT_EQ(anomalyTracker->getRefractoryPeriodEndsSec(DEFAULT_METRIC_DIMENSION_KEY), 0U);
}

// Test value metric no condition, the pull on bucket boundary come in time and too late
TEST(NumericValueMetricProducerTest, TestBucketBoundaryNoCondition) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Return(true));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    // pull 1
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 11));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    // empty since bucket is finished
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];

    // startUpdated:true sum:0 start:11
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(11, curBase.value().long_value);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    // pull 2 at correct time
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 1, 23));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    // empty since bucket is finished
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    // tartUpdated:false sum:12
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(23, curBase.value().long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {12}, {bucketSizeNs}, {0},
                                    {bucket2StartTimeNs}, {bucket3StartTimeNs});

    // pull 3 come late.
    // The previous bucket gets closed with error. (Has start value 23, no ending)
    // Another bucket gets closed with error. (No start, but ending with 36)
    // The new bucket is back to normal.
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket6StartTimeNs + 1, 36));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket6StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    // startUpdated:false sum:12
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(36, curBase.value().long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {12}, {bucketSizeNs}, {0},
                                    {bucket2StartTimeNs}, {bucket3StartTimeNs});
    // The 1st bucket is dropped because of no data
    // The 3rd bucket is dropped due to multiple buckets being skipped.
    ASSERT_EQ(2, valueProducer->mSkippedBuckets.size());

    EXPECT_EQ(bucketStartTimeNs, valueProducer->mSkippedBuckets[0].bucketStartTimeNs);
    EXPECT_EQ(bucket2StartTimeNs, valueProducer->mSkippedBuckets[0].bucketEndTimeNs);
    ASSERT_EQ(1, valueProducer->mSkippedBuckets[0].dropEvents.size());
    EXPECT_EQ(NO_DATA, valueProducer->mSkippedBuckets[0].dropEvents[0].reason);
    EXPECT_EQ(bucket2StartTimeNs, valueProducer->mSkippedBuckets[0].dropEvents[0].dropTimeNs);

    EXPECT_EQ(bucket3StartTimeNs, valueProducer->mSkippedBuckets[1].bucketStartTimeNs);
    EXPECT_EQ(bucket6StartTimeNs, valueProducer->mSkippedBuckets[1].bucketEndTimeNs);
    ASSERT_EQ(1, valueProducer->mSkippedBuckets[1].dropEvents.size());
    EXPECT_EQ(MULTIPLE_BUCKETS_SKIPPED, valueProducer->mSkippedBuckets[1].dropEvents[0].reason);
    EXPECT_EQ(bucket6StartTimeNs, valueProducer->mSkippedBuckets[1].dropEvents[0].dropTimeNs);
}

/*
 * Test pulled event with non sliced condition. The pull on boundary come late because the alarm
 * was delivered late.
 */
TEST(NumericValueMetricProducerTest, TestBucketBoundaryWithCondition) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // condition becomes true
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 8);  // First condition change.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 100));
                return true;
            }))
            // condition becomes false
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 1);  // Second condition change.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 120));
                return true;
            }));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(100, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    // pull on bucket boundary come late, condition change happens before it
    valueProducer->onConditionChanged(false, bucket2StartTimeNs + 1);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20}, {bucketSizeNs - 8}, {1},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
    EXPECT_EQ(false, curBase.has_value());

    // Now the alarm is delivered.
    // since the condition turned to off before this pull finish, it has no effect
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 30, 110));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20}, {bucketSizeNs - 8}, {1},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(false, curBase.has_value());
}

/*
 * Test pulled event with non sliced condition. The pull on boundary come late, after the condition
 * change to false, and then true again. This is due to alarm delivered late.
 */
TEST(NumericValueMetricProducerTest, TestBucketBoundaryWithCondition2) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // condition becomes true
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 8);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 100));
                return true;
            }))
            // condition becomes false
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 1);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 120));
                return true;
            }))
            // condition becomes true again
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 25);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 25, 130));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    // startUpdated:false sum:0 start:100
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(100, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    // pull on bucket boundary come late, condition change happens before it
    valueProducer->onConditionChanged(false, bucket2StartTimeNs + 1);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20}, {bucketSizeNs - 8}, {1},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(false, curBase.has_value());

    // condition changed to true again, before the pull alarm is delivered
    valueProducer->onConditionChanged(true, bucket2StartTimeNs + 25);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20}, {bucketSizeNs - 8}, {1},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(130, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);

    // Now the alarm is delivered, but it is considered late, the data will be used
    // for the new bucket since it was just pulled.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 50, 140));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 50);

    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(140, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(10, curInterval.aggregate.long_value);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20}, {bucketSizeNs - 8}, {1},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 160));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    assertPastBucketValuesSingleKey(
            valueProducer->mPastBuckets, {20, 30}, {bucketSizeNs - 8, bucketSizeNs - 24}, {1, -1},
            {bucketStartTimeNs, bucket2StartTimeNs}, {bucket2StartTimeNs, bucket3StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestPushedAggregateMin) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_aggregation_type(ValueMetric::MIN);

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 20, 20);

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(10, curInterval.aggregate.long_value);
    EXPECT_TRUE(curInterval.hasValue());

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(10, curInterval.aggregate.long_value);

    valueProducer->flushIfNeededLocked(bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestPushedAggregateMax) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_aggregation_type(ValueMetric::MAX);

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(10, curInterval.aggregate.long_value);
    EXPECT_TRUE(curInterval.hasValue());

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 20, 20);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(20, curInterval.aggregate.long_value);

    valueProducer->flushIfNeededLocked(bucket2StartTimeNs);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestPushedAggregateAvg) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_aggregation_type(ValueMetric::AVG);

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 20, 15);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval curInterval;
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(1, curInterval.sampleSize);
    EXPECT_EQ(10, curInterval.aggregate.long_value);

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(25, curInterval.aggregate.long_value);
    EXPECT_EQ(2, curInterval.sampleSize);

    valueProducer->flushIfNeededLocked(bucket2StartTimeNs);
    ASSERT_EQ(1UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(1UL, valueProducer->mPastBuckets.begin()->second.size());

    EXPECT_TRUE(
            std::abs(valueProducer->mPastBuckets.begin()->second.back().aggregates[0].double_value -
                     12.5) < epsilon);
    EXPECT_EQ(2, valueProducer->mPastBuckets.begin()->second.back().sampleSizes[0]);
}

TEST(NumericValueMetricProducerTest, TestPushedAggregateSum) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_aggregation_type(ValueMetric::SUM);

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 20, 15);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(10, curInterval.aggregate.long_value);
    EXPECT_TRUE(curInterval.hasValue());

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(25, curInterval.aggregate.long_value);

    valueProducer->flushIfNeededLocked(bucket2StartTimeNs);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {25}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestSkipZeroDiffOutput) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_aggregation_type(ValueMetric::MIN);
    metric.set_use_diff(true);

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(10, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 15, 15);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(15, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(5, curInterval.aggregate.long_value);

    // no change in data.
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event3, tagId, bucket2StartTimeNs + 10, 15);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event3);

    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(15, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(0, curInterval.aggregate.long_value);

    LogEvent event4(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event4, tagId, bucket2StartTimeNs + 15, 15);
    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event4);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(15, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(0, curInterval.aggregate.long_value);

    valueProducer->flushIfNeededLocked(bucket3StartTimeNs);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {10},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestSkipZeroDiffOutputMultiValue) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.mutable_value_field()->add_child()->set_field(3);
    metric.set_aggregation_type(ValueMetric::MIN);
    metric.set_use_diff(true);

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    CreateThreeValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 1, 10, 20);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    CreateThreeValueLogEvent(&event2, tagId, bucketStartTimeNs + 15, 1, 15, 22);

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event1);
    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(10, curBase.value().long_value);
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[1];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(20, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event2);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(15, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(5, curInterval.aggregate.long_value);
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[1];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[1];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(22, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(2, curInterval.aggregate.long_value);

    // no change in first value field
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    CreateThreeValueLogEvent(&event3, tagId, bucket2StartTimeNs + 10, 1, 15, 25);

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event3);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(15, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(0, curInterval.aggregate.long_value);
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[1];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[1];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(25, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(3, curInterval.aggregate.long_value);

    LogEvent event4(/*uid=*/0, /*pid=*/0);
    CreateThreeValueLogEvent(&event4, tagId, bucket2StartTimeNs + 15, 1, 15, 29);

    valueProducer->onMatchedLogEvent(1 /*log matcher index*/, event4);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(15, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(0, curInterval.aggregate.long_value);
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[1];
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[1];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(29, curBase.value().long_value);
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(3, curInterval.aggregate.long_value);

    valueProducer->flushIfNeededLocked(bucket3StartTimeNs);

    ASSERT_EQ(1UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(2UL, valueProducer->mPastBuckets.begin()->second.size());
    ASSERT_EQ(2UL, valueProducer->mPastBuckets.begin()->second[0].aggregates.size());
    ASSERT_EQ(1UL, valueProducer->mPastBuckets.begin()->second[1].aggregates.size());

    EXPECT_EQ(bucketSizeNs, valueProducer->mPastBuckets.begin()->second[0].mConditionTrueNs);
    EXPECT_EQ(5, valueProducer->mPastBuckets.begin()->second[0].aggregates[0].long_value);
    EXPECT_EQ(0, valueProducer->mPastBuckets.begin()->second[0].aggIndex[0]);
    EXPECT_EQ(2, valueProducer->mPastBuckets.begin()->second[0].aggregates[1].long_value);
    EXPECT_EQ(1, valueProducer->mPastBuckets.begin()->second[0].aggIndex[1]);

    EXPECT_EQ(bucketSizeNs, valueProducer->mPastBuckets.begin()->second[1].mConditionTrueNs);
    EXPECT_EQ(3, valueProducer->mPastBuckets.begin()->second[1].aggregates[0].long_value);
    EXPECT_EQ(1, valueProducer->mPastBuckets.begin()->second[1].aggIndex[0]);
}

/*
 * Tests zero default base.
 */
TEST(NumericValueMetricProducerTest, TestUseZeroDefaultBase) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.mutable_dimensions_in_what()->set_field(tagId);
    metric.mutable_dimensions_in_what()->add_child()->set_field(1);
    metric.set_use_zero_default_base(true);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs, 1, 3));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    auto iter = valueProducer->mCurrentSlicedBucket.begin();
    auto& interval1 = iter->second.intervals[0];
    auto iterBase = valueProducer->mDimInfos.begin();
    auto& base1 = iterBase->second.dimExtras[0];
    EXPECT_EQ(1, iter->first.getDimensionKeyInWhat().getValues()[0].mValue.int_value);
    EXPECT_EQ(true, base1.has_value());
    EXPECT_EQ(3, base1.value().long_value);
    EXPECT_EQ(0, interval1.sampleSize);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());
    vector<shared_ptr<LogEvent>> allData;

    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 1, 2, 4));
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 1, 1, 11));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    EXPECT_EQ(true, base1.has_value());
    EXPECT_EQ(11, base1.value().long_value);

    auto itBase = valueProducer->mDimInfos.begin();
    for (; itBase != valueProducer->mDimInfos.end(); itBase++) {
        if (itBase != iterBase) {
            break;
        }
    }
    EXPECT_TRUE(itBase != iterBase);
    auto& base2 = itBase->second.dimExtras[0];
    EXPECT_EQ(true, base2.has_value());
    EXPECT_EQ(4, base2.value().long_value);

    ASSERT_EQ(2UL, valueProducer->mPastBuckets.size());
    auto iterator = valueProducer->mPastBuckets.begin();
    EXPECT_EQ(bucketSizeNs, iterator->second[0].mConditionTrueNs);
    EXPECT_EQ(8, iterator->second[0].aggregates[0].long_value);
    iterator++;
    EXPECT_EQ(bucketSizeNs, iterator->second[0].mConditionTrueNs);
    EXPECT_EQ(4, iterator->second[0].aggregates[0].long_value);
}

/*
 * Tests using zero default base with failed pull.
 */
TEST(NumericValueMetricProducerTest, TestUseZeroDefaultBaseWithPullFailures) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.mutable_dimensions_in_what()->set_field(tagId);
    metric.mutable_dimensions_in_what()->add_child()->set_field(1);
    metric.set_use_zero_default_base(true);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs, 1, 3));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    const auto& it = valueProducer->mCurrentSlicedBucket.begin();
    NumericValueMetricProducer::Interval& interval1 = it->second.intervals[0];
    optional<Value>& base1 =
            valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat())->second.dimExtras[0];
    EXPECT_EQ(1, it->first.getDimensionKeyInWhat().getValues()[0].mValue.int_value);
    EXPECT_EQ(true, base1.has_value());
    EXPECT_EQ(3, base1.value().long_value);
    EXPECT_EQ(0, interval1.sampleSize);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());
    vector<shared_ptr<LogEvent>> allData;

    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 1, 2, 4));
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 1, 1, 11));

    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    EXPECT_EQ(true, base1.has_value());
    EXPECT_EQ(11, base1.value().long_value);

    auto itBase2 = valueProducer->mDimInfos.begin();
    for (; itBase2 != valueProducer->mDimInfos.end(); itBase2++) {
        if (itBase2->second.dimExtras[0] != base1) {
            break;
        }
    }
    optional<Value>& base2 = itBase2->second.dimExtras[0];
    EXPECT_TRUE(base2 != base1);
    EXPECT_EQ(2, itBase2->first.getValues()[0].mValue.int_value);
    EXPECT_EQ(true, base2.has_value());
    EXPECT_EQ(4, base2.value().long_value);
    ASSERT_EQ(2UL, valueProducer->mPastBuckets.size());

    // next pull somehow did not happen, skip to end of bucket 3
    // This pull is incomplete since it's missing dimension 1. Will cause mDimInfos to be trimmed
    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket4StartTimeNs + 1, 2, 5));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);

    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    EXPECT_EQ(2, valueProducer->mDimInfos.begin()->first.getValues()[0].mValue.int_value);
    optional<Value>& base3 = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, base3.has_value());
    EXPECT_EQ(5, base3.value().long_value);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);
    ASSERT_EQ(2UL, valueProducer->mPastBuckets.size());

    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket5StartTimeNs + 1, 2, 13));
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket5StartTimeNs + 1, 1, 5));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket5StartTimeNs);

    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    optional<Value>& base4 = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    optional<Value>& base5 = std::next(valueProducer->mDimInfos.begin())->second.dimExtras[0];

    EXPECT_EQ(true, base4.has_value());
    EXPECT_EQ(5, base4.value().long_value);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);
    EXPECT_EQ(true, base5.has_value());
    EXPECT_EQ(13, base5.value().long_value);

    ASSERT_EQ(2UL, valueProducer->mPastBuckets.size());
}

/*
 * Tests trim unused dimension key if no new data is seen in an entire bucket.
 */
TEST(NumericValueMetricProducerTest, TestTrimUnusedDimensionKey) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.mutable_dimensions_in_what()->set_field(tagId);
    metric.mutable_dimensions_in_what()->add_child()->set_field(1);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs, 1, 3));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    auto iter = valueProducer->mCurrentSlicedBucket.begin();
    auto& interval1 = iter->second.intervals[0];
    auto iterBase = valueProducer->mDimInfos.begin();
    auto& base1 = iterBase->second.dimExtras[0];
    EXPECT_EQ(1, iter->first.getDimensionKeyInWhat().getValues()[0].mValue.int_value);
    EXPECT_EQ(true, base1.has_value());
    EXPECT_EQ(3, base1.value().long_value);
    EXPECT_EQ(0, interval1.sampleSize);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 1, 2, 4));
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 1, 1, 11));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    EXPECT_EQ(true, base1.has_value());
    EXPECT_EQ(11, base1.value().long_value);
    EXPECT_FALSE(iterBase->second.seenNewData);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {8}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    auto itBase = valueProducer->mDimInfos.begin();
    for (; itBase != valueProducer->mDimInfos.end(); itBase++) {
        if (itBase != iterBase) {
            break;
        }
    }
    EXPECT_TRUE(itBase != iterBase);
    auto base2 = itBase->second.dimExtras[0];
    EXPECT_EQ(2, itBase->first.getValues()[0].mValue.int_value);
    EXPECT_EQ(true, base2.has_value());
    EXPECT_EQ(4, base2.value().long_value);
    EXPECT_FALSE(itBase->second.seenNewData);

    // next pull somehow did not happen, skip to end of bucket 3
    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket4StartTimeNs + 1, 2, 5));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);
    // Only one dimension left. One was trimmed.
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    base2 = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(2, valueProducer->mDimInfos.begin()->first.getValues()[0].mValue.int_value);
    EXPECT_EQ(true, base2.has_value());
    EXPECT_EQ(5, base2.value().long_value);
    EXPECT_FALSE(valueProducer->mDimInfos.begin()->second.seenNewData);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {8}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket5StartTimeNs + 1, 2, 14));
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket5StartTimeNs + 1, 1, 14));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket5StartTimeNs);

    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket6StartTimeNs + 1, 1, 19));
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket6StartTimeNs + 1, 2, 20));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket6StartTimeNs);

    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    ASSERT_EQ(2UL, valueProducer->mPastBuckets.size());
    // Dimension = 2
    auto iterator = valueProducer->mPastBuckets.begin();
    ASSERT_EQ(1, iterator->first.getDimensionKeyInWhat().getValues().size());
    EXPECT_EQ(2, iterator->first.getDimensionKeyInWhat().getValues()[0].mValue.int_value);
    ASSERT_EQ(2, iterator->second.size());
    EXPECT_EQ(bucket4StartTimeNs, iterator->second[0].mBucketStartNs);
    EXPECT_EQ(bucket5StartTimeNs, iterator->second[0].mBucketEndNs);
    EXPECT_EQ(9, iterator->second[0].aggregates[0].long_value);
    EXPECT_EQ(bucketSizeNs, iterator->second[0].mConditionTrueNs);
    EXPECT_EQ(bucket5StartTimeNs, iterator->second[1].mBucketStartNs);
    EXPECT_EQ(bucket6StartTimeNs, iterator->second[1].mBucketEndNs);
    EXPECT_EQ(6, iterator->second[1].aggregates[0].long_value);
    EXPECT_EQ(bucketSizeNs, iterator->second[1].mConditionTrueNs);
    iterator++;
    // Dimension = 1
    ASSERT_EQ(1, iterator->first.getDimensionKeyInWhat().getValues().size());
    EXPECT_EQ(1, iterator->first.getDimensionKeyInWhat().getValues()[0].mValue.int_value);
    ASSERT_EQ(2, iterator->second.size());
    EXPECT_EQ(bucketStartTimeNs, iterator->second[0].mBucketStartNs);
    EXPECT_EQ(bucket2StartTimeNs, iterator->second[0].mBucketEndNs);
    EXPECT_EQ(8, iterator->second[0].aggregates[0].long_value);
    EXPECT_EQ(bucketSizeNs, iterator->second[0].mConditionTrueNs);
    EXPECT_EQ(bucket5StartTimeNs, iterator->second[1].mBucketStartNs);
    EXPECT_EQ(bucket6StartTimeNs, iterator->second[1].mBucketEndNs);
    EXPECT_EQ(5, iterator->second[1].aggregates[0].long_value);
    EXPECT_EQ(bucketSizeNs, iterator->second[1].mConditionTrueNs);
}

TEST(NumericValueMetricProducerTest, TestResetBaseOnPullFailAfterConditionChange_EndOfBucket) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    // Used by onConditionChanged.
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 8, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 100));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);
    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    NumericValueMetricProducer::Interval& curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value>& curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(100, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);

    vector<shared_ptr<LogEvent>> allData;
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_FAIL, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    EXPECT_EQ(false, curBase.has_value());
    EXPECT_EQ(false, valueProducer->mHasGlobalBase);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(1UL, valueProducer->mSkippedBuckets.size());
}

TEST(NumericValueMetricProducerTest, TestResetBaseOnPullFailAfterConditionChange) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 8);  // Condition change to true.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 100));
                return true;
            }))
            .WillOnce(Return(false));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval& curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value>& curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(100, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    valueProducer->onConditionChanged(false, bucketStartTimeNs + 20);

    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    EXPECT_EQ(0, curInterval.sampleSize);
    EXPECT_EQ(false, curBase.has_value());
    EXPECT_EQ(false, valueProducer->mHasGlobalBase);
}

TEST(NumericValueMetricProducerTest, TestResetBaseOnPullFailBeforeConditionChange) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 50));
                return false;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 1);  // Condition change to false.
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 100));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());

    valueProducer->onConditionChanged(false, bucketStartTimeNs + 1);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval& curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(false, curBase.has_value());
    EXPECT_EQ(0, curInterval.sampleSize);
    EXPECT_EQ(false, valueProducer->mHasGlobalBase);
}

TEST(NumericValueMetricProducerTest, TestResetBaseOnPullDelayExceeded) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_condition(StringToId("SCREEN_ON"));
    metric.set_max_pull_delay_sec(0);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 1, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, 120));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Max delay is set to 0 so pull will exceed max delay.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 1);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
}

TEST(NumericValueMetricProducerTest, TestResetBaseOnPullTooLate) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducer(
                    pullerManager, metric, tagId, ConditionState::kFalse,
                    /*slicedStateAtoms=*/{},
                    /*stateGroupMap=*/{}, bucket2StartTimeNs, bucket2StartTimeNs,
                    eventMatcherWizard);

    // Event should be skipped since it is from previous bucket.
    // Pull should not be called.
    valueProducer->onConditionChanged(true, bucketStartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
}

TEST(NumericValueMetricProducerTest, TestBaseSetOnConditionChange) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 1, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, 100));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 1);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval& curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(100, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);
}

/*
 * Tests that a bucket is marked invalid when a condition change pull fails.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenOneConditionFailed) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First onConditionChanged
            .WillOnce(Return(false))
            // Second onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 3);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 130));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kTrue);

    // Bucket start.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, 110));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucketStartTimeNs);

    // This will fail and should invalidate the whole bucket since we do not have all the data
    // needed to compute the metric value when the screen was on.
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 2);
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 3);

    // Bucket end.
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 140));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    valueProducer->flushIfNeededLocked(bucket2StartTimeNs + 1);

    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());
    // Contains base from last pull which was successful.
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(140, curBase.value().long_value);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 10, false /* include partial bucket */, true,
                                FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 2), dropEvent.drop_time_millis());
}

/*
 * Tests that a bucket is marked invalid when the guardrail is hit.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenGuardRailHit) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.mutable_dimensions_in_what()->set_field(tagId);
    metric.mutable_dimensions_in_what()->add_child()->set_field(1);
    metric.set_condition(StringToId("SCREEN_ON"));

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 2, _))
            // First onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                for (int i = 0; i < 2000; i++) {
                    data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, i));
                }
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    ASSERT_EQ(false, StatsdStats::getInstance().hasHitDimensionGuardrail(metricId));
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 2);
    EXPECT_EQ(true, valueProducer->mCurrentBucketIsSkipped);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mSkippedBuckets.size());
    ASSERT_EQ(true, StatsdStats::getInstance().hasHitDimensionGuardrail(metricId));

    // Bucket 2 start.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 1, 1, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // First bucket added to mSkippedBuckets after flush.
    ASSERT_EQ(1UL, valueProducer->mSkippedBuckets.size());

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 10000, false /* include recent buckets */,
                                true, FAST /* dumpLatency */, &strSet, &output);
    ASSERT_EQ(true, StatsdStats::getInstance().hasHitDimensionGuardrail(metricId));

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.dimension_guardrail_hit());
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::DIMENSION_GUARDRAIL_REACHED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 2), dropEvent.drop_time_millis());
}

/*
 * Tests that a bucket is marked invalid when the bucket's initial pull fails.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenInitialPullFailed) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 2);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 120));
                return true;
            }))
            // Second onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 3);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 130));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kTrue);

    // Bucket start.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, 110));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_FAIL, bucketStartTimeNs);

    valueProducer->onConditionChanged(false, bucketStartTimeNs + 2);
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 3);

    // Bucket end.
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 140));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    valueProducer->flushIfNeededLocked(bucket2StartTimeNs + 1);

    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());
    // Contains base from last pull which was successful.
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(140, curBase.value().long_value);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 10000, false /* include recent buckets */,
                                true, FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 2), dropEvent.drop_time_millis());
}

/*
 * Tests that a bucket is marked invalid when the bucket's final pull fails
 * (i.e. failed pull on bucket boundary).
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenLastPullFailed) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 2);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 120));
                return true;
            }))
            // Second onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 3);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 8, 130));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kTrue);

    // Bucket start.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, 110));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucketStartTimeNs);

    valueProducer->onConditionChanged(false, bucketStartTimeNs + 2);
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 3);

    // Bucket end.
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 140));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_FAIL, bucket2StartTimeNs);

    valueProducer->flushIfNeededLocked(bucket2StartTimeNs + 1);

    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());
    // Last pull failed so base has been reset.
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(false, curBase.has_value());
    EXPECT_EQ(false, valueProducer->mHasGlobalBase);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 10000, false /* include recent buckets */,
                                true, FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs), dropEvent.drop_time_millis());
}

TEST(NumericValueMetricProducerTest, TestEmptyDataResetsBase_onDataPulled) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            // Start bucket.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 3));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    // Bucket 2 start.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 110));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    EXPECT_EQ(valueProducer->mDimInfos.begin()->second.seenNewData, false);
    ASSERT_EQ(1UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(0UL, valueProducer->mSkippedBuckets.size());

    // Bucket 3 empty.
    allData.clear();
    allData.push_back(CreateNoValuesLogEvent(tagId, bucket3StartTimeNs + 1));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    // Data has been trimmed.
    ASSERT_EQ(1UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(1UL, valueProducer->mSkippedBuckets.size());
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());

    // Bucket 4 start.
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket4StartTimeNs + 1, 150));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);
    ASSERT_EQ(1UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(2UL, valueProducer->mSkippedBuckets.size());
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    // Bucket 5 start.
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket5StartTimeNs + 1, 170));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket5StartTimeNs);
    assertPastBucketValuesSingleKey(
            valueProducer->mPastBuckets, {107, 20}, {bucketSizeNs, bucketSizeNs}, {0, 0},
            {bucketStartTimeNs, bucket4StartTimeNs}, {bucket2StartTimeNs, bucket5StartTimeNs});
    ASSERT_EQ(2UL, valueProducer->mSkippedBuckets.size());
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
}

TEST(NumericValueMetricProducerTest, TestEmptyDataResetsBase_onConditionChanged) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 3));
                return true;
            }))
            // Empty pull when change to false
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 20);
                data->clear();
                return true;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 30);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30, 10));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 10);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval& curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(0, curInterval.sampleSize);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);

    // Empty pull.
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 20);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(0, curInterval.sampleSize);
    EXPECT_EQ(false, valueProducer->mHasGlobalBase);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 30);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(0, curInterval.sampleSize);
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(10, curBase.value().long_value);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);

    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 120));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(120, curBase.value().long_value);
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {110}, {bucketSizeNs - 20}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestEmptyDataResetsBase_onBucketBoundary) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 1));
                return true;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 11);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 2));
                return true;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 12);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 10);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 11);
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 12);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval& curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(true, valueProducer->mHasGlobalBase);

    // End of bucket
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());

    ASSERT_EQ(1UL, valueProducer->mPastBuckets.size());
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {1}, {bucketSizeNs - 12 + 1}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestPartialResetOnBucketBoundaries) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.mutable_dimensions_in_what()->set_field(tagId);
    metric.mutable_dimensions_in_what()->add_child()->set_field(1);
    metric.set_condition(StringToId("SCREEN_ON"));

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 10, _))
            // First onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 1));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 10);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    // End of bucket
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 2));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // Key 1 should be removed from mDimInfos since in not present in the most pull.
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    auto baseInfoIter = valueProducer->mDimInfos.begin();
    EXPECT_EQ(true, baseInfoIter->second.dimExtras[0].has_value());
    EXPECT_EQ(2, baseInfoIter->second.dimExtras[0].value().long_value);

    EXPECT_EQ(true, valueProducer->mHasGlobalBase);
}

TEST_P(NumericValueMetricProducerTest_PartialBucket, TestFullBucketResetWhenLastBucketInvalid) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    int64_t partialBucketSplitTimeNs = bucketStartTimeNs + bucketSizeNs / 2;
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Initialization.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 1));
                return true;
            }))
            // notifyAppUpgrade.
            .WillOnce(Invoke([partialBucketSplitTimeNs](int tagId, const ConfigKey&,
                                                        const int64_t eventTimeNs,
                                                        vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, partialBucketSplitTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, partialBucketSplitTimeNs, 10));
                return true;
            }));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    sp<AlarmMonitor> alarmMonitor;
    Alert alert;
    alert.set_id(101);
    alert.set_metric_id(metricId);
    alert.set_trigger_if_sum_gt(100);
    alert.set_num_buckets(1);
    alert.set_refractory_period_secs(3);
    sp<AnomalyTracker> anomalyTracker =
            valueProducer->addAnomalyTracker(alert, alarmMonitor, UPDATE_NEW, bucketStartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentFullBucket.size());

    switch (GetParam()) {
        case APP_UPGRADE:
            valueProducer->notifyAppUpgrade(partialBucketSplitTimeNs);
            break;
        case BOOT_COMPLETE:
            valueProducer->onStatsdInitCompleted(partialBucketSplitTimeNs);
            break;
    }
    EXPECT_EQ(partialBucketSplitTimeNs, valueProducer->mCurrentBucketStartTimeNs);
    EXPECT_EQ(0, valueProducer->getCurrentBucketNum());
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {9},
                                    {partialBucketSplitTimeNs - bucketStartTimeNs}, {0},
                                    {bucketStartTimeNs}, {partialBucketSplitTimeNs});
    ASSERT_EQ(1UL, valueProducer->mCurrentFullBucket.size());

    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 1, 4));
    // Pull fails and arrives late.
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_FAIL, bucket3StartTimeNs + 1);
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {9},
                                    {partialBucketSplitTimeNs - bucketStartTimeNs}, {0},
                                    {bucketStartTimeNs}, {partialBucketSplitTimeNs});
    ASSERT_EQ(1, valueProducer->mSkippedBuckets.size());
    ASSERT_EQ(2, valueProducer->mSkippedBuckets[0].dropEvents.size());
    EXPECT_EQ(PULL_FAILED, valueProducer->mSkippedBuckets[0].dropEvents[0].reason);
    EXPECT_EQ(MULTIPLE_BUCKETS_SKIPPED, valueProducer->mSkippedBuckets[0].dropEvents[1].reason);
    EXPECT_EQ(partialBucketSplitTimeNs, valueProducer->mSkippedBuckets[0].bucketStartTimeNs);
    EXPECT_EQ(bucket3StartTimeNs, valueProducer->mSkippedBuckets[0].bucketEndTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentFullBucket.size());
}

TEST(NumericValueMetricProducerTest, TestBucketBoundariesOnConditionChange) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Second onConditionChanged.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 10, 5));
                return true;
            }))
            // Third onConditionChanged.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket3StartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs + 10, 7));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kUnknown);

    valueProducer->onConditionChanged(false, bucketStartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());

    // End of first bucket
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 4));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 1);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());

    valueProducer->onConditionChanged(true, bucket2StartTimeNs + 10);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    auto curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    auto curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(true, curBase.has_value());
    EXPECT_EQ(5, curBase.value().long_value);
    EXPECT_EQ(0, curInterval.sampleSize);

    valueProducer->onConditionChanged(false, bucket3StartTimeNs + 10);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    // Bucket should have been completed.
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {2}, {bucketSizeNs - 10}, {10},
                                    {bucket2StartTimeNs}, {bucket3StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestLateOnDataPulledWithoutDiff) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_use_diff(false);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucketStartTimeNs + 30);

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 20));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // Bucket should have been completed.
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {30}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestLateOnDataPulledWithDiff) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            // Initialization.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 1));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucketStartTimeNs + 30);

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 20));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // Bucket should have been completed.
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {19}, {bucketSizeNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST_P(NumericValueMetricProducerTest_PartialBucket, TestBucketBoundariesOnPartialBucket) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    int64_t partialBucketSplitTimeNs = bucket2StartTimeNs + 2;
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Initialization.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 1));
                return true;
            }))
            // notifyAppUpgrade.
            .WillOnce(Invoke([partialBucketSplitTimeNs](int tagId, const ConfigKey&,
                                                        const int64_t eventTimeNs,
                                                        vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, partialBucketSplitTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, partialBucketSplitTimeNs, 10));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    switch (GetParam()) {
        case APP_UPGRADE:
            valueProducer->notifyAppUpgrade(partialBucketSplitTimeNs);
            break;
        case BOOT_COMPLETE:
            valueProducer->onStatsdInitCompleted(partialBucketSplitTimeNs);
            break;
    }

    // Bucket should have been completed.
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {9}, {bucketSizeNs}, {2},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
}

TEST(NumericValueMetricProducerTest, TestDataIsNotUpdatedWhenNoConditionChanged) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First on condition changed.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 8);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 1));
                return true;
            }))
            // Second on condition changed.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 3));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 10);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 12);

    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    auto curInterval = valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    auto curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(2, curInterval.aggregate.long_value);

    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 1);

    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {2}, {2}, {0}, {bucketStartTimeNs},
                                    {bucket2StartTimeNs});
}

// TODO: b/145705635 fix or delete this test
TEST(NumericValueMetricProducerTest, TestBucketInvalidIfGlobalBaseIsNotSet) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First condition change.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 1));
                return true;
            }))
            // 2nd condition change.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 8);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 1));
                return true;
            }))
            // 3rd condition change.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 1));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);
    valueProducer->onConditionChanged(true, bucket2StartTimeNs + 10);

    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 3, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_FAIL, bucketStartTimeNs + 3);

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 20));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_FAIL, bucket2StartTimeNs);

    valueProducer->onConditionChanged(false, bucket2StartTimeNs + 8);
    valueProducer->onConditionChanged(true, bucket2StartTimeNs + 10);

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 30));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // There was not global base available so all buckets are invalid.
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {}, {}, {}, {}, {});
}

TEST(NumericValueMetricProducerTest, TestFastDumpWithoutCurrentBucket) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            // Initial pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs, tagId, 1, 1));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 1, tagId, 2, 2));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket4StartTimeNs, false /* include recent buckets */, true, FAST,
                                &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    // Previous bucket is part of the report, and the current bucket is not skipped.
    ASSERT_EQ(1, report.value_metrics().data_size());
    EXPECT_EQ(0, report.value_metrics().data(0).bucket_info(0).bucket_num());
    ASSERT_EQ(0, report.value_metrics().skipped_size());
}

TEST(NumericValueMetricProducerTest, TestPullNeededNoTimeConstraints) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Initial pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs, tagId, 1, 1));
                return true;
            }))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10);
                data->clear();
                data->push_back(
                        CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 10, tagId, 3, 3));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucketStartTimeNs + 10, true /* include recent buckets */, true,
                                NO_TIME_CONSTRAINTS, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().data(0).bucket_info_size());
    EXPECT_EQ(2, report.value_metrics().data(0).bucket_info(0).values(0).value_long());
}

TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_withoutCondition) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_use_diff(false);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 30, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 30);

    // Bucket should have been completed.
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {10}, {bucketSizeNs}, {30},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
    ASSERT_EQ(0, valueProducer->mCurrentSlicedBucket.size());
    // TODO: mDimInfos is not needed for non-diffed data, but an entry is still created.
    ASSERT_EQ(1, valueProducer->mDimInfos.size());
}

TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_withMultipleConditionChanges) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();
    metric.set_use_diff(false);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // condition becomes true
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 8);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30, 10));
                return true;
            }))
            // condition becomes false
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 50, 20));
                return true;
            }));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 50);
    // has one slice
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    NumericValueMetricProducer::Interval curInterval =
            valueProducer->mCurrentSlicedBucket.begin()->second.intervals[0];
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(false, curBase.has_value());
    EXPECT_TRUE(curInterval.hasValue());
    EXPECT_EQ(20, curInterval.aggregate.long_value);

    // Now the alarm is delivered. Condition is off though.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 30, 110));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {20}, {50 - 8}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(false, curBase.has_value());
}

TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_bucketBoundaryTrue) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();
    metric.set_use_diff(false);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 8, _))
            // condition becomes true
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30, 10));
                return true;
            }));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);

    // Now the alarm is delivered. Condition is on.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 30, 30));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {30}, {bucketSizeNs - 8}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    optional<Value> curBase = valueProducer->mDimInfos.begin()->second.dimExtras[0];
    EXPECT_EQ(false, curBase.has_value());
}

TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_bucketBoundaryFalse) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();
    metric.set_use_diff(false);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Now the alarm is delivered. Condition is off though.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 30, 30));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // Condition was always false.
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {}, {}, {}, {}, {});
}

TEST(NumericValueMetricProducerTest, TestPulledData_noDiff_withFailure) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();
    metric.set_use_diff(false);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // condition becomes true
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 8);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30, 10));
                return true;
            }))
            .WillOnce(Return(false));
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 8);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 50);
    // First event is skipped because the metric is not diffed, so no entry is created in the map
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());

    // Now the alarm is delivered. Condition is off though.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 30, 30));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());

    // No buckets, we had a failure.
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {}, {}, {}, {}, {});
}

/*
 * Test that DUMP_REPORT_REQUESTED dump reason is logged.
 *
 * For the bucket to be marked invalid during a dump report requested,
 * three things must be true:
 * - we want to include the current partial bucket
 * - we need a pull (metric is pulled and condition is true)
 * - the dump latency must be FAST
 */

TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenDumpReportRequested) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 20, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 20, 10));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Condition change event.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 20);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucketStartTimeNs + 40, true /* include recent buckets */, true,
                                FAST /* dumpLatency */, &strSet, &output);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 40),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::DUMP_REPORT_REQUESTED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 40), dropEvent.drop_time_millis());
}

/*
 * Test that EVENT_IN_WRONG_BUCKET dump reason is logged for a late condition
 * change event (i.e. the condition change occurs in the wrong bucket).
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenConditionEventWrongBucket) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 50, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 50, 10));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Condition change event.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 50);

    // Bucket boundary pull.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 15));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 1);

    // Late condition change event.
    valueProducer->onConditionChanged(false, bucket2StartTimeNs - 100);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 100, true /* include recent buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs + 100),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::EVENT_IN_WRONG_BUCKET, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs - 100), dropEvent.drop_time_millis());
}

/*
 * Test that EVENT_IN_WRONG_BUCKET dump reason is logged for a late accumulate
 * event (i.e. the accumulate events call occurs in the wrong bucket).
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenAccumulateEventWrongBucket) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 50, 10));
                return true;
            }))
            // Dump report requested.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 100);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 100, 15));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Condition change event.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 50);

    // Bucket boundary pull.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 15));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 1);

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs - 100, 20));

    // Late accumulateEvents event.
    valueProducer->accumulateEvents(allData, bucket2StartTimeNs - 100, bucket2StartTimeNs - 100);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 100, true /* include recent buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs + 100),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::EVENT_IN_WRONG_BUCKET, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs - 100), dropEvent.drop_time_millis());
}

/*
 * Test that CONDITION_UNKNOWN dump reason is logged due to an unknown condition
 * when a metric is initialized.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenConditionUnknown) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 50, 10));
                return true;
            }))
            // Dump report requested.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10000);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 100, 15));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kUnknown);

    // Condition change event.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 50);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 10000;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include recent buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that PULL_FAILED dump reason is logged due to a pull failure in
 * #pullAndMatchEventsLocked.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenPullFailed) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 50, 10));
                return true;
            }))
            // Dump report requested, pull fails.
            .WillOnce(Return(false));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Condition change event.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 50);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 10000;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include recent buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that MULTIPLE_BUCKETS_SKIPPED dump reason is logged when a log event
 * skips over more than one bucket.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestInvalidBucketWhenMultipleBucketsSkipped) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 10, 10));
                return true;
            }))
            // Dump report requested.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket4StartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket4StartTimeNs + 1000, 15));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Condition change event.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 10);

    // Condition change event that skips forward by three buckets.
    valueProducer->onConditionChanged(false, bucket4StartTimeNs + 10);
    // Ensure data structures are appropriately trimmed when multiple buckets are skipped.
    ASSERT_EQ(valueProducer->mCurrentSlicedBucket.size(), 0);
    ASSERT_EQ(valueProducer->mDimInfos.size(), 1);

    int64_t dumpTimeNs = bucket4StartTimeNs + 1000;

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(dumpTimeNs, true /* include current buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(2, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucket4StartTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::MULTIPLE_BUCKETS_SKIPPED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucket4StartTimeNs + 10), dropEvent.drop_time_millis());

    // This bucket is skipped because a dumpReport with include current buckets is called.
    // This creates a new bucket from bucket4StartTimeNs to dumpTimeNs in which we have no data
    // since the condition is false for the entire bucket interval.
    EXPECT_EQ(NanoToMillis(bucket4StartTimeNs),
              report.value_metrics().skipped(1).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpTimeNs),
              report.value_metrics().skipped(1).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(1).drop_event_size());

    dropEvent = report.value_metrics().skipped(1).drop_event(0);
    EXPECT_EQ(BucketDropReason::NO_DATA, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that BUCKET_TOO_SMALL dump reason is logged when a flushed bucket size
 * is smaller than the "min_bucket_size_nanos" specified in the metric config.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestBucketDropWhenBucketTooSmall) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();
    metric.set_min_bucket_size_nanos(10000000000);  // 10 seconds

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 10, 10));
                return true;
            }))
            // Dump report requested.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 9000000);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 9000000, 15));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Condition change event.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 10);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 9000000;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include recent buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::BUCKET_TOO_SMALL, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that NO_DATA dump reason is logged when a flushed bucket contains no data.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestBucketDropWhenDataUnavailable) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 10000000000;  // 10 seconds
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include current bucket */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::NO_DATA, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that all buckets are dropped due to condition unknown until the first onConditionChanged.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestConditionUnknownMultipleBuckets) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucket2StartTimeNs + 10 * NS_PER_SEC, 10));
                return true;
            }))
            // Dump report requested.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 15 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucket2StartTimeNs + 15 * NS_PER_SEC, 15));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kUnknown);

    // Bucket should be dropped because of condition unknown.
    int64_t appUpgradeTimeNs = bucketStartTimeNs + 5 * NS_PER_SEC;
    valueProducer->notifyAppUpgrade(appUpgradeTimeNs);

    // Bucket also dropped due to condition unknown
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1, 3));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // This bucket is also dropped due to condition unknown.
    int64_t conditionChangeTimeNs = bucket2StartTimeNs + 10 * NS_PER_SEC;
    valueProducer->onConditionChanged(true, conditionChangeTimeNs);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucket2StartTimeNs + 15 * NS_PER_SEC;  // 15 seconds
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include current bucket */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(3, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(appUpgradeTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(appUpgradeTimeNs), dropEvent.drop_time_millis());

    EXPECT_EQ(NanoToMillis(appUpgradeTimeNs),
              report.value_metrics().skipped(1).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(1).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(1).drop_event_size());

    dropEvent = report.value_metrics().skipped(1).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs), dropEvent.drop_time_millis());

    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(2).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.value_metrics().skipped(2).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(2).drop_event_size());

    dropEvent = report.value_metrics().skipped(2).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(conditionChangeTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that a skipped bucket is logged when a forced bucket split occurs when the previous bucket
 * was not flushed in time.
 */
TEST(NumericValueMetricProducerTest_BucketDrop,
     TestBucketDropWhenForceBucketSplitBeforeBucketFlush) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 10, 10));
                return true;
            }))
            // App Update.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 1000);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + 1000, 15));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    // Condition changed event
    int64_t conditionChangeTimeNs = bucketStartTimeNs + 10;
    valueProducer->onConditionChanged(true, conditionChangeTimeNs);

    // App update event.
    int64_t appUpdateTimeNs = bucket2StartTimeNs + 1000;
    valueProducer->notifyAppUpgrade(appUpdateTimeNs);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucket2StartTimeNs + 10000000000;  // 10 seconds
    valueProducer->onDumpReport(dumpReportTimeNs, false /* include current buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    ASSERT_EQ(1, report.value_metrics().data(0).bucket_info_size());
    auto data = report.value_metrics().data(0);
    ASSERT_EQ(0, data.bucket_info(0).bucket_num());
    EXPECT_EQ(5, data.bucket_info(0).values(0).value_long());

    EXPECT_EQ(NanoToMillis(bucket2StartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(appUpdateTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::NO_DATA, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(appUpdateTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test multiple bucket drop events in the same bucket.
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestMultipleBucketDropEvents) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs + 10, _))
            // Condition change to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 10, 10));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kUnknown);

    // Condition change event.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 10);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 1000;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include recent buckets */, true,
                                FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(2, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 10), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(1);
    EXPECT_EQ(BucketDropReason::DUMP_REPORT_REQUESTED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs), dropEvent.drop_time_millis());
}

/*
 * Test that the number of logged bucket drop events is capped at the maximum.
 * The maximum is currently 10 and is set in MetricProducer::maxDropEventsReached().
 */
TEST(NumericValueMetricProducerTest_BucketDrop, TestMaxBucketDropEvents) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First condition change event.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10);
                for (int i = 0; i < 2000; i++) {
                    data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 1, i));
                }
                return true;
            }))
            .WillOnce(Return(false))
            .WillOnce(Return(false))
            .WillOnce(Return(false))
            .WillOnce(Return(false))
            .WillOnce(Return(false))
            .WillOnce(Return(false))
            .WillOnce(Return(false))
            .WillOnce(Return(false))
            .WillOnce(Return(false))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 220);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 220, 10));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kUnknown);

    // First condition change event causes guardrail to be reached.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 10);

    // 2-10 condition change events result in failed pulls.
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 30);
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 50);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 70);
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 90);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 100);
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 150);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 170);
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 190);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 200);

    // Condition change event 11
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 220);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 1000;
    // Because we already have 10 dump events in the current bucket,
    // this case should not be added to the list of dump events.
    valueProducer->onDumpReport(bucketStartTimeNs + 1000, true /* include recent buckets */, true,
                                FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(dumpReportTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(10, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 10), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(1);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 30), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(2);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 50), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(3);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 70), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(4);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 90), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(5);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 100), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(6);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 150), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(7);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 170), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(8);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 190), dropEvent.drop_time_millis());

    dropEvent = report.value_metrics().skipped(0).drop_event(9);
    EXPECT_EQ(BucketDropReason::PULL_FAILED, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(bucketStartTimeNs + 200), dropEvent.drop_time_millis());
}

/*
 * Test metric with a simple sliced state
 * - Increasing values
 * - Using diff
 * - Second field is value field
 */
TEST(NumericValueMetricProducerTest, TestSlicedState) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric =
            NumericValueMetricProducerTestHelper::createMetricWithState("SCREEN_STATE");
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // NumericValueMetricProducer initialized.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 3));
                return true;
            }))
            // Screen state change to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 5 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 5 * NS_PER_SEC, 5));
                return true;
            }))
            // Screen state change to OFF.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 10 * NS_PER_SEC, 9));
                return true;
            }))
            // Screen state change to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 15 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucketStartTimeNs + 15 * NS_PER_SEC, 21));
                return true;
            }))
            // Dump report requested.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucketStartTimeNs + 50 * NS_PER_SEC, 30));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithState(
                    pullerManager, metric, {util::SCREEN_STATE_CHANGED}, {});
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(SCREEN_STATE_ATOM_ID, valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(SCREEN_STATE_ATOM_ID));

    // Bucket status after metric initialized.
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {
    auto it = valueProducer->mCurrentSlicedBucket.begin();
    auto itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(3, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, kStateUnknown}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_EQ(0, it->second.intervals[0].sampleSize);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs);

    // Bucket status after screen state change kStateUnknown->ON.
    auto screenEvent = CreateScreenStateChangedEvent(
            bucketStartTimeNs + 5 * NS_PER_SEC, android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(5, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_EQ(0, it->second.intervals.size());
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 5 * NS_PER_SEC);
    // Value for dimension, state key {{}, kStateUnknown}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(2, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 5 * NS_PER_SEC);

    // Bucket status after screen state change ON->OFF.
    screenEvent = CreateScreenStateChangedEvent(bucketStartTimeNs + 10 * NS_PER_SEC,
                                                android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(9, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_OFF,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, OFF}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_OFF,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_EQ(0, it->second.intervals.size());
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 10 * NS_PER_SEC);
    // Value for dimension, state key {{}, ON}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(4, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 10 * NS_PER_SEC);
    // Value for dimension, state key {{}, kStateUnknown}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(2, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 5 * NS_PER_SEC);

    // Bucket status after screen state change OFF->ON.
    screenEvent = CreateScreenStateChangedEvent(bucketStartTimeNs + 15 * NS_PER_SEC,
                                                android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(21, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, OFF}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_OFF,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(12, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 15 * NS_PER_SEC);
    // Value for dimension, state key {{}, ON}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(4, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, true, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 15 * NS_PER_SEC);
    // Value for dimension, state key {{}, kStateUnknown}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(2, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 5 * NS_PER_SEC);

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucketStartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);

    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(30, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_EQ(it->second.intervals[0].sampleSize, 0);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 50 * NS_PER_SEC);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(3, report.value_metrics().data_size());

    // {{}, kStateUnknown}
    auto data = report.value_metrics().data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(2, data.bucket_info(0).values(0).value_long());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */, data.slice_by_state(0).value());
    EXPECT_EQ(5 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());

    // {{}, ON}
    data = report.value_metrics().data(1);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(13, data.bucket_info(0).values(0).value_long());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON, data.slice_by_state(0).value());
    EXPECT_EQ(40 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());

    // {{}, OFF}
    data = report.value_metrics().data(2);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(12, data.bucket_info(0).values(0).value_long());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_OFF, data.slice_by_state(0).value());
    EXPECT_EQ(5 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());
}

/*
 * Test metric with sliced state with map
 * - Increasing values
 * - Using diff
 * - Second field is value field
 */
TEST(NumericValueMetricProducerTest, TestSlicedStateWithMap) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric =
            NumericValueMetricProducerTestHelper::createMetricWithState("SCREEN_STATE_ONOFF");
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // NumericValueMetricProducer initialized.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 3));
                return true;
            }))
            // Screen state change to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 5 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 5 * NS_PER_SEC, 5));
                return true;
            }))
            // Screen state change to VR has no pull because it is in the same
            // state group as ON.

            // Screen state change to ON has no pull because it is in the same
            // state group as VR.

            // Screen state change to OFF.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 15 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucketStartTimeNs + 15 * NS_PER_SEC, 21));
                return true;
            }))
            // Dump report requested.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucketStartTimeNs + 50 * NS_PER_SEC, 30));
                return true;
            }));

    const StateMap& stateMap =
            CreateScreenStateOnOffMap(/*screen on id=*/321, /*screen off id=*/123);
    const StateMap_StateGroup screenOnGroup = stateMap.group(0);
    const StateMap_StateGroup screenOffGroup = stateMap.group(1);

    unordered_map<int, unordered_map<int, int64_t>> stateGroupMap;
    for (auto group : stateMap.group()) {
        for (auto value : group.value()) {
            stateGroupMap[SCREEN_STATE_ATOM_ID][value] = group.group_id();
        }
    }

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithState(
                    pullerManager, metric, {util::SCREEN_STATE_CHANGED}, stateGroupMap);

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(SCREEN_STATE_ATOM_ID, valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(SCREEN_STATE_ATOM_ID));

    // Bucket status after metric initialized.
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    auto it = valueProducer->mCurrentSlicedBucket.begin();
    auto itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(3, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, {kStateUnknown}}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_EQ(0, it->second.intervals[0].sampleSize);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs);

    // Bucket status after screen state change kStateUnknown->ON.
    auto screenEvent = CreateScreenStateChangedEvent(
            bucketStartTimeNs + 5 * NS_PER_SEC, android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(5, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(screenOnGroup.group_id(),
              itBase->second.currentState.getValues()[0].mValue.long_value);
    // Value for dimension, state key {{}, ON GROUP}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(screenOnGroup.group_id(),
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 5 * NS_PER_SEC);
    // Value for dimension, state key {{}, kStateUnknown}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(2, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 5 * NS_PER_SEC);

    // Bucket status after screen state change ON->VR.
    // Both ON and VR are in the same state group, so the base should not change.
    screenEvent = CreateScreenStateChangedEvent(bucketStartTimeNs + 10 * NS_PER_SEC,
                                                android::view::DisplayStateEnum::DISPLAY_STATE_VR);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(5, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(screenOnGroup.group_id(),
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, ON GROUP}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(screenOnGroup.group_id(),
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 5 * NS_PER_SEC);
    // Value for dimension, state key {{}, kStateUnknown}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(2, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 5 * NS_PER_SEC);

    // Bucket status after screen state change VR->ON.
    // Both ON and VR are in the same state group, so the base should not change.
    screenEvent = CreateScreenStateChangedEvent(bucketStartTimeNs + 12 * NS_PER_SEC,
                                                android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(5, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(screenOnGroup.group_id(),
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, ON GROUP}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(screenOnGroup.group_id(),
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 5 * NS_PER_SEC);
    // Value for dimension, state key {{}, kStateUnknown}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(2, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 5 * NS_PER_SEC);

    // Bucket status after screen state change VR->OFF.
    screenEvent = CreateScreenStateChangedEvent(bucketStartTimeNs + 15 * NS_PER_SEC,
                                                android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(21, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(screenOffGroup.group_id(),
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, OFF GROUP}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(screenOffGroup.group_id(),
              it->first.getStateValuesKey().getValues()[0].mValue.long_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 15 * NS_PER_SEC);
    // Value for dimension, state key {{}, ON GROUP}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(screenOnGroup.group_id(),
              it->first.getStateValuesKey().getValues()[0].mValue.long_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(16, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucketStartTimeNs + 15 * NS_PER_SEC);
    // Value for dimension, state key {{}, kStateUnknown}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(2, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 5 * NS_PER_SEC,
                         bucketStartTimeNs + 5 * NS_PER_SEC);

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucketStartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);

    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(30, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(screenOffGroup.group_id(),
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, OFF GROUP}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(screenOffGroup.group_id(),
              it->first.getStateValuesKey().getValues()[0].mValue.long_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 50 * NS_PER_SEC);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(3, report.value_metrics().data_size());

    // {{}, kStateUnknown}
    auto data = report.value_metrics().data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(2, report.value_metrics().data(0).bucket_info(0).values(0).value_long());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(-1 /*StateTracker::kStateUnknown*/, data.slice_by_state(0).value());
    EXPECT_EQ(5 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());

    // {{}, ON GROUP}
    data = report.value_metrics().data(1);
    ASSERT_EQ(1, report.value_metrics().data(1).bucket_info_size());
    EXPECT_EQ(16, report.value_metrics().data(1).bucket_info(0).values(0).value_long());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOnGroup.group_id(), data.slice_by_state(0).group_id());
    EXPECT_EQ(10 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());

    // {{}, OFF GROUP}
    data = report.value_metrics().data(2);
    ASSERT_EQ(1, report.value_metrics().data(2).bucket_info_size());
    EXPECT_EQ(9, report.value_metrics().data(2).bucket_info(0).values(0).value_long());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOffGroup.group_id(), data.slice_by_state(0).group_id());
    EXPECT_EQ(35 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());
}

/*
 * Test metric that slices by state with a primary field and has dimensions
 * - Increasing values
 * - Using diff
 * - Second field is value field
 */
TEST(NumericValueMetricProducerTest, TestSlicedStateWithPrimaryField_WithDimensions) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric =
            NumericValueMetricProducerTestHelper::createMetricWithState("UID_PROCESS_STATE");
    metric.mutable_dimensions_in_what()->set_field(tagId);
    metric.mutable_dimensions_in_what()->add_child()->set_field(1);
    metric.set_condition_correction_threshold_nanos(0);

    MetricStateLink* stateLink = metric.add_state_link();
    stateLink->set_state_atom_id(UID_PROCESS_STATE_ATOM_ID);
    auto fieldsInWhat = stateLink->mutable_fields_in_what();
    *fieldsInWhat = CreateDimensions(tagId, {1 /* uid */});
    auto fieldsInState = stateLink->mutable_fields_in_state();
    *fieldsInState = CreateDimensions(UID_PROCESS_STATE_ATOM_ID, {1 /* uid */});

    /*
    NOTE: "1" denotes uid 1 and "2" denotes uid 2.
                    bucket # 1                            bucket # 2
    10     20     30     40     50     60     70     80     90    100    110    120 (seconds)
    |------------------------------------------|---------------------------------|--

                                                                                    (kStateUnknown)
    1
    |-------------|
          20

    2
    |----------------------------|
                 40

                                                                                    (FOREGROUND)
                  1                                                       1
                  |----------------------------|-------------|            |------|
                               40                     20                     10


                                                                                    (BACKGROUND)
                                                             1
                                                             |------------|
                                                                   20
                                 2
                                 |-------------|---------------------------------|
                                       20                      50
    */
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // NumericValueMetricProducer initialized.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs, 1 /*uid*/, 3));
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs, 2 /*uid*/, 7));
                return true;
            }))
            // Uid 1 process state change from kStateUnknown -> Foreground
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 20 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC,
                                                       1 /*uid*/, 6));
                // This event should be skipped.
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC,
                                                       2 /*uid*/, 8));
                return true;
            }))
            // Uid 2 process state change from kStateUnknown -> Background
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 40 * NS_PER_SEC);
                data->clear();
                // This event should be skipped.
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs + 40 * NS_PER_SEC,
                                                       1 /*uid*/, 12));
                data->push_back(CreateTwoValueLogEvent(tagId, bucketStartTimeNs + 40 * NS_PER_SEC,
                                                       2 /*uid*/, 9));
                return true;
            }))
            // Uid 1 process state change from Foreground -> Background
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 20 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 20 * NS_PER_SEC,
                                                       1 /*uid*/, 13));
                // This event should be skipped.
                data->push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 20 * NS_PER_SEC,
                                                       2 /*uid*/, 11));
                return true;
            }))
            // Uid 1 process state change from Background -> Foreground
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 40 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 40 * NS_PER_SEC,
                                                       1 /*uid*/, 17));

                // This event should be skipped.
                data->push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 40 * NS_PER_SEC,
                                                       2 /*uid */, 15));
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 50 * NS_PER_SEC,
                                                       1 /*uid*/, 21));
                data->push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs + 50 * NS_PER_SEC,
                                                       2 /*uid*/, 20));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithState(
                    pullerManager, metric, {UID_PROCESS_STATE_ATOM_ID}, {});

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(UID_PROCESS_STATE_ATOM_ID, valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(UID_PROCESS_STATE_ATOM_ID));

    // Bucket status after metric initialized.
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    // Bucket status after uid 1 process state change kStateUnknown -> Foreground.
    auto uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucketStartTimeNs + 20 * NS_PER_SEC, 1 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_FOREGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    // Bucket status after uid 2 process state change kStateUnknown -> Background.
    uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucketStartTimeNs + 40 * NS_PER_SEC, 2 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_BACKGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(4UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    // Pull at end of first bucket.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs, 1 /*uid*/, 10));
    allData.push_back(CreateTwoValueLogEvent(tagId, bucket2StartTimeNs, 2 /*uid*/, 15));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 1);

    // Ensure the MetricDimensionKeys for the current state are kept.
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    auto it = valueProducer->mCurrentSlicedBucket.begin();  // dimension, state key {2, BACKGROUND}
    EXPECT_EQ(1, it->first.getDimensionKeyInWhat().getValues().size());
    EXPECT_EQ(2, it->first.getDimensionKeyInWhat().getValues()[0].mValue.int_value);
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(android::app::PROCESS_STATE_IMPORTANT_BACKGROUND,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    it++;  // dimension, state key {1, FOREGROUND}
    EXPECT_EQ(1, it->first.getDimensionKeyInWhat().getValues().size());
    EXPECT_EQ(1, it->first.getDimensionKeyInWhat().getValues()[0].mValue.int_value);
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(android::app::PROCESS_STATE_IMPORTANT_FOREGROUND,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);

    // Bucket status after uid 1 process state change from Foreground -> Background.
    uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucket2StartTimeNs + 20 * NS_PER_SEC, 1 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_BACKGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    // Bucket status after uid 1 process state change Background->Foreground.
    uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucket2StartTimeNs + 40 * NS_PER_SEC, 1 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_FOREGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucket2StartTimeNs + 50 * NS_PER_SEC;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include recent buckets */, true,
                                NO_TIME_CONSTRAINTS, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    StatsLogReport::ValueMetricDataWrapper valueMetrics;
    sortMetricDataByDimensionsValue(report.value_metrics(), &valueMetrics);
    ASSERT_EQ(5, valueMetrics.data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());

    // {uid 1, kStateUnknown}
    ValueMetricData data = valueMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateUidDimension(data.dimensions_in_what(), tagId, 1);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       -1 /*StateTracker::kStateUnknown*/);
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {3},
                        20 * NS_PER_SEC, 0);

    // {uid 1, FOREGROUND}
    data = valueMetrics.data(1);
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateUidDimension(data.dimensions_in_what(), tagId, 1);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {4},
                        40 * NS_PER_SEC, 1);
    ValidateValueBucket(data.bucket_info(1), bucket2StartTimeNs, dumpReportTimeNs, {7},
                        30 * NS_PER_SEC, -1);

    // {uid 1, BACKGROUND}
    data = valueMetrics.data(2);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateUidDimension(data.dimensions_in_what(), tagId, 1);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    ValidateValueBucket(data.bucket_info(0), bucket2StartTimeNs, dumpReportTimeNs, {4},
                        20 * NS_PER_SEC, -1);

    // {uid 2, kStateUnknown}
    data = valueMetrics.data(3);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateUidDimension(data.dimensions_in_what(), tagId, 2);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       -1 /*StateTracker::kStateUnknown*/);
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {2},
                        40 * NS_PER_SEC, -1);

    // {uid 2, BACKGROUND}
    data = valueMetrics.data(4);
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateUidDimension(data.dimensions_in_what(), tagId, 2);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {6},
                        20 * NS_PER_SEC, 1);
    ValidateValueBucket(data.bucket_info(1), bucket2StartTimeNs, dumpReportTimeNs, {5},
                        50 * NS_PER_SEC, -1);
}

/*
 * Test slicing condition_true_nanos by state for metric that slices by state when data is not
 * present in pulled data during a state change.
 */
TEST(NumericValueMetricProducerTest, TestSlicedStateWithMissingDataInStateChange) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric =
            NumericValueMetricProducerTestHelper::createMetricWithState("BATTERY_SAVER_MODE_STATE");
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    /*
    NOTE: "-" means that the data was not present in the pulled data.

                             bucket # 1
    10         20         30         40         50         60   (seconds)
    |-------------------------------------------------------|--
    x                                                           (kStateUnknown)
    |-----------|
         10

                x                               x               (ON)
                |---------------------|         |-----------|
                           20                        10

                                      -                         (OFF)
    */
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // NumericValueMetricProducer initialized.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 3));
                return true;
            }))
            // Battery saver mode state changed to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 10 * NS_PER_SEC, 5));
                return true;
            }))
            // Battery saver mode state changed to OFF but data for dimension key {} is not present
            // in the pulled data.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 30 * NS_PER_SEC);
                data->clear();
                return true;
            }))
            // Battery saver mode state changed to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 40 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 40 * NS_PER_SEC, 7));
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucketStartTimeNs + 50 * NS_PER_SEC, 15));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithState(
                    pullerManager, metric, {util::BATTERY_SAVER_MODE_STATE_CHANGED}, {});
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(util::BATTERY_SAVER_MODE_STATE_CHANGED,
                                                 valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(
                         util::BATTERY_SAVER_MODE_STATE_CHANGED));

    // Bucket status after metric initialized.
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    auto it = valueProducer->mCurrentSlicedBucket.begin();
    auto itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, kStateUnknown}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs);

    // Bucket status after battery saver mode ON event.
    unique_ptr<LogEvent> batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucketStartTimeNs + 10 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);

    // Base for dimension key {}

    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 10 * NS_PER_SEC);

    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucketStartTimeNs + 10 * NS_PER_SEC);

    // Bucket status after battery saver mode OFF event which is not present
    // in the pulled data.
    unique_ptr<LogEvent> batterySaverOffEvent =
            CreateBatterySaverOffEvent(/*timestamp=*/bucketStartTimeNs + 30 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOffEvent);

    // Base for dimension key {} is cleared.
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    // Value for key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 20 * NS_PER_SEC,
                         bucketStartTimeNs + 30 * NS_PER_SEC);

    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucketStartTimeNs + 10 * NS_PER_SEC);

    // Bucket status after battery saver mode ON event.
    batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucketStartTimeNs + 40 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);

    // Base for dimension key {}
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 20 * NS_PER_SEC,
                         bucketStartTimeNs + 40 * NS_PER_SEC);

    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucketStartTimeNs + 10 * NS_PER_SEC);

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucketStartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(2, report.value_metrics().data_size());

    // {{}, kStateUnknown}
    ValueMetricData data = report.value_metrics().data(0);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/, data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + 50 * NS_PER_SEC,
                        {2}, 10 * NS_PER_SEC, -1);

    // {{}, ON}
    data = report.value_metrics().data(1);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_EQ(BatterySaverModeStateChanged::ON, data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + 50 * NS_PER_SEC,
                        {8}, 30 * NS_PER_SEC, -1);
}

/*
 * Test for metric that slices by state when data is not present in pulled data
 * during an event and then a flush occurs for the current bucket. With the new
 * condition timer behavior, a "new" MetricDimensionKey is inserted into
 * `mCurrentSlicedBucket` before intervals are closed/added to that new
 * MetricDimensionKey.
 */
TEST(NumericValueMetricProducerTest, TestSlicedStateWithMissingDataThenFlushBucket) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric =
            NumericValueMetricProducerTestHelper::createMetricWithState("BATTERY_SAVER_MODE_STATE");
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    /*
    NOTE: "-" means that the data was not present in the pulled data.

                             bucket # 1
    10         20         30         40         50         60   (seconds)
    |-------------------------------------------------------|--
    -                                                           (kStateUnknown)

                -                                               (ON)
    */
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // NumericValueMetricProducer initialized  but data for dimension key {} is not present
            // in the pulled data..
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                return true;
            }))
            // Battery saver mode state changed to ON but data for dimension key {} is not present
            // in the pulled data.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucketStartTimeNs + 50 * NS_PER_SEC, 15));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithState(
                    pullerManager, metric, {util::BATTERY_SAVER_MODE_STATE_CHANGED}, {});
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(util::BATTERY_SAVER_MODE_STATE_CHANGED,
                                                 valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(
                         util::BATTERY_SAVER_MODE_STATE_CHANGED));

    // Bucket status after metric initialized.
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());

    // Bucket status after battery saver mode ON event which is not present
    // in the pulled data.
    unique_ptr<LogEvent> batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucketStartTimeNs + 10 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);

    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucketStartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
}

TEST(NumericValueMetricProducerTest, TestSlicedStateWithNoPullOnBucketBoundary) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric =
            NumericValueMetricProducerTestHelper::createMetricWithState("BATTERY_SAVER_MODE_STATE");
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    /*
                 bucket # 1                         bucket # 2
    10    20    30    40    50    60    70    80   90   100   110   120  (seconds)
    |------------------------------------|---------------------------|--
    x                                                                    (kStateUnknown)
    |-----|
      10
          x                                              x               (ON)
          |-----|                                        |-----------|
             10                                               20
                x                                                        (OFF)
                |------------------------|
                          40
    */
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // NumericValueMetricProducer initialized.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs, 3));
                return true;
            }))
            // Battery saver mode state changed to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 10 * NS_PER_SEC, 5));
                return true;
            }))
            // Battery saver mode state changed to OFF.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 20 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC, 7));
                return true;
            }))
            // Battery saver mode state changed to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 30 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucket2StartTimeNs + 30 * NS_PER_SEC, 10));
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucket2StartTimeNs + 50 * NS_PER_SEC, 15));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithState(
                    pullerManager, metric, {util::BATTERY_SAVER_MODE_STATE_CHANGED}, {});
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(util::BATTERY_SAVER_MODE_STATE_CHANGED,
                                                 valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(
                         util::BATTERY_SAVER_MODE_STATE_CHANGED));

    // Bucket status after metric initialized.
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    auto it = valueProducer->mCurrentSlicedBucket.begin();
    auto itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for dimension, state key {{}, kStateUnknown}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs);

    // Bucket status after battery saver mode ON event.
    unique_ptr<LogEvent> batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucketStartTimeNs + 10 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);

    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 10 * NS_PER_SEC);

    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucketStartTimeNs + 10 * NS_PER_SEC);

    // Bucket status after battery saver mode OFF event.
    unique_ptr<LogEvent> batterySaverOffEvent =
            CreateBatterySaverOffEvent(/*timestamp=*/bucketStartTimeNs + 20 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOffEvent);

    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, OFF}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 20 * NS_PER_SEC);

    // Value for key {{}, ON}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucketStartTimeNs + 20 * NS_PER_SEC);

    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucketStartTimeNs + 10 * NS_PER_SEC);

    // Bucket status after battery saver mode ON event.
    batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucket2StartTimeNs + 30 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);

    // Bucket split. all MetricDimensionKeys other than the current state key are trimmed.
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucket2StartTimeNs + 30 * NS_PER_SEC);

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(3, report.value_metrics().data_size());

    // {{}, kStateUnknown}
    ValueMetricData data = report.value_metrics().data(0);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/, data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {2},
                        10 * NS_PER_SEC, -1);

    // {{}, ON}
    data = report.value_metrics().data(1);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_EQ(BatterySaverModeStateChanged::ON, data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {2},
                        10 * NS_PER_SEC, -1);
    ValidateValueBucket(data.bucket_info(1), bucket2StartTimeNs,
                        bucket2StartTimeNs + 50 * NS_PER_SEC, {5}, 20 * NS_PER_SEC, -1);

    // {{}, OFF}
    data = report.value_metrics().data(2);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF, data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {3},
                        40 * NS_PER_SEC, -1);
}

/*
 * Test slicing condition_true_nanos by state for metric that slices by state when data is not
 * present in pulled data during a condition change.
 */
TEST(NumericValueMetricProducerTest, TestSlicedStateWithDataMissingInConditionChange) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithConditionAndState(
            "BATTERY_SAVER_MODE_STATE");
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    /*
    NOTE: "-" means that the data was not present in the pulled data.

                             bucket # 1
    10         20         30         40         50         60   (seconds)
    |-------------------------------------------------------|--

    T                                 F         T               (Condition)
               x                                       x        (ON)
               |----------------------|         -      |----|
                         20                               5
                                           x                    (OFF)
    */
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Battery saver mode state changed to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 10 * NS_PER_SEC, 3));
                return true;
            }))
            // Condition changed to false.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 30 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30 * NS_PER_SEC, 5));
                return true;
            }))
            // Condition changed to true but data for dimension key {} is not present in the
            // pulled data.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 40 * NS_PER_SEC);
                data->clear();
                return true;
            }))
            // Battery saver mode state changed to ON.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 45 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucketStartTimeNs + 45 * NS_PER_SEC, 14));
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucketStartTimeNs + 50 * NS_PER_SEC, 20));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithConditionAndState(
                    pullerManager, metric, {util::BATTERY_SAVER_MODE_STATE_CHANGED}, {},
                    ConditionState::kTrue);
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(util::BATTERY_SAVER_MODE_STATE_CHANGED,
                                                 valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(
                         util::BATTERY_SAVER_MODE_STATE_CHANGED));

    // Bucket status after battery saver mode ON event.
    unique_ptr<LogEvent> batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucketStartTimeNs + 10 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);
    // Base for dimension key {}
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    auto it = valueProducer->mCurrentSlicedBucket.begin();
    auto itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 10 * NS_PER_SEC);

    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 0, 0);

    // Bucket status after condition change to false.
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 30 * NS_PER_SEC);
    // Base for dimension key {}
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 20 * NS_PER_SEC,
                         bucketStartTimeNs + 30 * NS_PER_SEC);

    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 0, 0);

    unique_ptr<LogEvent> batterySaverOffEvent =
            CreateBatterySaverOffEvent(/*timestamp=*/bucketStartTimeNs + 35 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOffEvent);
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());

    // Bucket status after condition change to true.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 40 * NS_PER_SEC);
    // Base for dimension key {}. The pull returned no data, so mDimInfos is trimmed.
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    // Value for key {{}, ON}
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 20 * NS_PER_SEC,
                         bucketStartTimeNs + 30 * NS_PER_SEC);

    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, false, 0, 0);

    batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucketStartTimeNs + 45 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    itBase = valueProducer->mDimInfos.find(it->first.getDimensionKeyInWhat());
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucketStartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());

    // {{}, ON}
    ValueMetricData data = report.value_metrics().data(0);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_EQ(BatterySaverModeStateChanged::ON, data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + 50 * NS_PER_SEC,
                        {2 + 6}, 25 * NS_PER_SEC, -1);
}

/*
 * Test slicing condition_true_nanos by state for metric that slices by state with a primary field,
 * condition, and has multiple dimensions.
 */
TEST(NumericValueMetricProducerTest, TestSlicedStateWithMultipleDimensions) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithConditionAndState(
            "UID_PROCESS_STATE");
    metric.mutable_dimensions_in_what()->set_field(tagId);
    metric.mutable_dimensions_in_what()->add_child()->set_field(1);
    metric.mutable_dimensions_in_what()->add_child()->set_field(3);

    MetricStateLink* stateLink = metric.add_state_link();
    stateLink->set_state_atom_id(UID_PROCESS_STATE_ATOM_ID);
    auto fieldsInWhat = stateLink->mutable_fields_in_what();
    *fieldsInWhat = CreateDimensions(tagId, {1 /* uid */});
    auto fieldsInState = stateLink->mutable_fields_in_state();
    *fieldsInState = CreateDimensions(UID_PROCESS_STATE_ATOM_ID, {1 /* uid */});

    /*
                    bucket # 1                            bucket # 2
    10     20     30     40     50     60     70     80     90    100    110    120 (seconds)
    |------------------------------------------|---------------------------------|--

    T                           F   T                                               (Condition)
                                                                                    (FOREGROUND)
           x                                                                        {1, 14}
           |------|
              10

           x                                                                        {1, 16}
           |------|
              10
                                                                   x                {2, 8}
                                                                   |-------------|
                                                                         20

                                                                                    (BACKGROUND)
                  x                                                                 {1, 14}
                  |-------------|   |----------|---------------------------------|
                        20              15                     50

                  x                                                                 {1, 16}
                  |-------------|   |----------|---------------------------------|
                        20              15                     50

                     x                                                              {2, 8}
                     |----------|   |----------|-------------------|
                         15             15              30
    */
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Uid 1 process state change from kStateUnknown -> Foreground
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 10 * NS_PER_SEC,
                                                         1 /*uid*/, 3, 14 /*tag*/));
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 10 * NS_PER_SEC,
                                                         1 /*uid*/, 3, 16 /*tag*/));

                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 10 * NS_PER_SEC,
                                                         2 /*uid*/, 5, 8 /*tag*/));
                return true;
            }))
            // Uid 1 process state change from Foreground -> Background
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 20 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC,
                                                         1 /*uid*/, 5, 14 /*tag*/));
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC,
                                                         1 /*uid*/, 5, 16 /*tag*/));

                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC,
                                                         2 /*uid*/, 7, 8 /*tag*/));

                return true;
            }))
            // Uid 2 process state change from kStateUnknown -> Background
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 25 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 25 * NS_PER_SEC,
                                                         2 /*uid*/, 9, 8 /*tag*/));

                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 25 * NS_PER_SEC,
                                                         1 /*uid*/, 9, 14 /* tag */));

                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 25 * NS_PER_SEC,
                                                         1 /*uid*/, 9, 16 /* tag */));

                return true;
            }))
            // Condition changed to false.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 40 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 40 * NS_PER_SEC,
                                                         1 /*uid*/, 11, 14 /* tag */));
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 40 * NS_PER_SEC,
                                                         1 /*uid*/, 11, 16 /* tag */));
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 40 * NS_PER_SEC,
                                                         2 /*uid*/, 11, 8 /*tag*/));

                return true;
            }))
            // Condition changed to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 45 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 45 * NS_PER_SEC,
                                                         1 /*uid*/, 13, 14 /* tag */));
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 45 * NS_PER_SEC,
                                                         1 /*uid*/, 13, 16 /* tag */));
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 45 * NS_PER_SEC,
                                                         2 /*uid*/, 13, 8 /*tag*/));
                return true;
            }))
            // Uid 2 process state change from Background -> Foreground
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 30 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 30 * NS_PER_SEC, 2 /*uid*/, 18, 8 /*tag*/));

                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 30 * NS_PER_SEC, 1 /*uid*/, 18, 14 /* tag */));
                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 30 * NS_PER_SEC, 1 /*uid*/, 18, 16 /* tag */));

                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 50 * NS_PER_SEC, 1 /*uid*/, 21, 14 /* tag */));
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 50 * NS_PER_SEC, 1 /*uid*/, 21, 16 /* tag */));
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 50 * NS_PER_SEC, 2 /*uid*/, 21, 8 /*tag*/));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithConditionAndState(
                    pullerManager, metric, {UID_PROCESS_STATE_ATOM_ID}, {}, ConditionState::kTrue);
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(UID_PROCESS_STATE_ATOM_ID, valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(UID_PROCESS_STATE_ATOM_ID));

    // Condition is true.
    auto uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucketStartTimeNs + 10 * NS_PER_SEC, 1 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_FOREGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(4UL, valueProducer->mCurrentSlicedBucket.size());

    uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucketStartTimeNs + 20 * NS_PER_SEC, 1 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_BACKGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(6UL, valueProducer->mCurrentSlicedBucket.size());

    uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucketStartTimeNs + 25 * NS_PER_SEC, 2 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_BACKGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(8UL, valueProducer->mCurrentSlicedBucket.size());

    valueProducer->onConditionChanged(false, bucketStartTimeNs + 40 * NS_PER_SEC);
    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(8UL, valueProducer->mCurrentSlicedBucket.size());

    valueProducer->onConditionChanged(true, bucketStartTimeNs + 45 * NS_PER_SEC);
    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(8UL, valueProducer->mCurrentSlicedBucket.size());

    // Pull at end of first bucket.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(
            CreateThreeValueLogEvent(tagId, bucket2StartTimeNs, 1 /*uid*/, 13, 14 /* tag */));
    allData.push_back(
            CreateThreeValueLogEvent(tagId, bucket2StartTimeNs, 1 /*uid*/, 13, 16 /* tag */));
    allData.push_back(
            CreateThreeValueLogEvent(tagId, bucket2StartTimeNs, 2 /*uid*/, 13, 8 /*tag*/));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 1);

    // Buckets flushed. MetricDimensionKeys not corresponding to the current state are removed.
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(5UL, valueProducer->mPastBuckets.size());

    uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucket2StartTimeNs + 30 * NS_PER_SEC, 2 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_FOREGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(4UL, valueProducer->mCurrentSlicedBucket.size());

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);
    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    StatsLogReport::ValueMetricDataWrapper valueMetrics;
    sortMetricDataByDimensionsValue(report.value_metrics(), &valueMetrics);
    ASSERT_EQ(6, valueMetrics.data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());

    // {{uid 1, tag 14}, FOREGROUND}.
    ValueMetricData data = valueMetrics.data(0);
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_EQ(android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND,
              data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(10 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());

    // {{uid 1, tag 16}, BACKGROUND}.
    data = valueMetrics.data(1);
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_EQ(android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND,
              data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(35 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());
    EXPECT_EQ(50 * NS_PER_SEC, data.bucket_info(1).condition_true_nanos());

    // {{uid 1, tag 16}, FOREGROUND}.
    data = valueMetrics.data(2);
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_EQ(android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND,
              data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(10 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());

    // {{uid 1, tag 14}, BACKGROUND}.
    data = valueMetrics.data(3);
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_EQ(android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND,
              data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(35 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());
    EXPECT_EQ(50 * NS_PER_SEC, data.bucket_info(1).condition_true_nanos());

    // {{uid 2, tag 8}, FOREGROUND}.
    data = valueMetrics.data(4);
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_EQ(android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND,
              data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(20 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());

    // {{uid 2, tag 8}, BACKGROUND}.
    data = valueMetrics.data(5);
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_EQ(android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND,
              data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(30 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());
    EXPECT_EQ(30 * NS_PER_SEC, data.bucket_info(1).condition_true_nanos());
}

TEST(NumericValueMetricProducerTest, TestSlicedStateWithCondition) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithConditionAndState(
            "BATTERY_SAVER_MODE_STATE");
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition changed to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 20 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC, 3));
                return true;
            }))
            // Battery saver mode state changed to OFF.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 30 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30 * NS_PER_SEC, 5));
                return true;
            }))
            // Condition changed to false.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucket2StartTimeNs + 10 * NS_PER_SEC, 15));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithConditionAndState(
                    pullerManager, metric, {util::BATTERY_SAVER_MODE_STATE_CHANGED}, {},
                    ConditionState::kFalse);
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(util::BATTERY_SAVER_MODE_STATE_CHANGED,
                                                 valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(
                         util::BATTERY_SAVER_MODE_STATE_CHANGED));

    // Bucket status after battery saver mode ON event.
    // Condition is false so we do nothing.
    unique_ptr<LogEvent> batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucketStartTimeNs + 10 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);
    EXPECT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    EXPECT_EQ(0UL, valueProducer->mDimInfos.size());

    // Bucket status after condition change to true.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 20 * NS_PER_SEC);
    // Base for dimension key {}
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    std::unordered_map<HashableDimensionKey,
                       NumericValueMetricProducer::DimensionsInWhatInfo>::iterator itBase =
            valueProducer->mDimInfos.find(DEFAULT_DIMENSION_KEY);
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(3, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, ON}
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    std::unordered_map<MetricDimensionKey, NumericValueMetricProducer::CurrentBucket>::iterator it =
            valueProducer->mCurrentSlicedBucket.begin();
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 20 * NS_PER_SEC);
    // Value for key {{}, -1}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(-1 /*StateTracker::kUnknown*/,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_EQ(0, it->second.intervals[0].sampleSize);
    assertConditionTimer(it->second.conditionTimer, false, 0, 0);

    // Bucket status after battery saver mode OFF event.
    unique_ptr<LogEvent> batterySaverOffEvent =
            CreateBatterySaverOffEvent(/*timestamp=*/bucketStartTimeNs + 30 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOffEvent);
    // Base for dimension key {}
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    itBase = valueProducer->mDimInfos.find(DEFAULT_DIMENSION_KEY);
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(5, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, OFF}
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 30 * NS_PER_SEC);
    // Value for key {{}, ON}
    it++;
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(2, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucketStartTimeNs + 30 * NS_PER_SEC);
    // Value for key {{}, -1}
    it++;
    assertConditionTimer(it->second.conditionTimer, false, 0, 0);

    // Pull at end of first bucket.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 11));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    ASSERT_EQ(2UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // Base for dimension key {}
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    itBase = valueProducer->mDimInfos.find(DEFAULT_DIMENSION_KEY);
    EXPECT_TRUE(itBase->second.dimExtras[0].has_value());
    EXPECT_EQ(11, itBase->second.dimExtras[0].value().long_value);
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, OFF}
    it = valueProducer->mCurrentSlicedBucket.begin();
    assertConditionTimer(it->second.conditionTimer, true, 0, bucket2StartTimeNs);

    // Bucket 2 status after condition change to false.
    valueProducer->onConditionChanged(false, bucket2StartTimeNs + 10 * NS_PER_SEC);
    // Base for dimension key {}
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    itBase = valueProducer->mDimInfos.find(DEFAULT_DIMENSION_KEY);
    EXPECT_FALSE(itBase->second.dimExtras[0].has_value());
    EXPECT_TRUE(itBase->second.hasCurrentState);
    ASSERT_EQ(1, itBase->second.currentState.getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF,
              itBase->second.currentState.getValues()[0].mValue.int_value);
    // Value for key {{}, OFF}
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    it = valueProducer->mCurrentSlicedBucket.begin();
    EXPECT_EQ(0, it->first.getDimensionKeyInWhat().getValues().size());
    ASSERT_EQ(1, it->first.getStateValuesKey().getValues().size());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    EXPECT_GT(it->second.intervals[0].sampleSize, 0);
    EXPECT_EQ(4, it->second.intervals[0].aggregate.long_value);
    assertConditionTimer(it->second.conditionTimer, false, 10 * NS_PER_SEC,
                         bucket2StartTimeNs + 10 * NS_PER_SEC);

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(2, report.value_metrics().data_size());

    ValueMetricData data = report.value_metrics().data(0);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(BatterySaverModeStateChanged::ON, data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(2, data.bucket_info(0).values(0).value_long());
    EXPECT_EQ(10 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());

    data = report.value_metrics().data(1);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF, data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(6, data.bucket_info(0).values(0).value_long());
    EXPECT_EQ(4, data.bucket_info(1).values(0).value_long());
    EXPECT_EQ(30 * NS_PER_SEC, data.bucket_info(0).condition_true_nanos());
    EXPECT_EQ(10 * NS_PER_SEC, data.bucket_info(1).condition_true_nanos());
}

TEST(NumericValueMetricProducerTest, TestSlicedStateWithConditionFalseMultipleBuckets) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithConditionAndState(
            "BATTERY_SAVER_MODE_STATE");
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Condition changed to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 20 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC, 3));
                return true;
            }))
            // Battery saver mode state changed to OFF.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 30 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 30 * NS_PER_SEC, 5));
                return true;
            }))
            // Condition changed to false.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 40 * NS_PER_SEC);
                data->clear();
                data->push_back(
                        CreateRepeatedValueLogEvent(tagId, bucketStartTimeNs + 40 * NS_PER_SEC, 9));
                return true;
            }))
            // Condition changed to true.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket3StartTimeNs + 10 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucket3StartTimeNs + 10 * NS_PER_SEC, 35));
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket3StartTimeNs + 30 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(
                        tagId, bucket3StartTimeNs + 30 * NS_PER_SEC, 53));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithConditionAndState(
                    pullerManager, metric, {util::BATTERY_SAVER_MODE_STATE_CHANGED}, {},
                    ConditionState::kFalse);
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(util::BATTERY_SAVER_MODE_STATE_CHANGED,
                                                 valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(
                         util::BATTERY_SAVER_MODE_STATE_CHANGED));

    // Bucket status after battery saver mode ON event.
    // Condition is false so we do nothing.
    unique_ptr<LogEvent> batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucketStartTimeNs + 10 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);
    EXPECT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    EXPECT_EQ(0UL, valueProducer->mDimInfos.size());

    // Bucket status after condition change to true.
    valueProducer->onConditionChanged(true, bucketStartTimeNs + 20 * NS_PER_SEC);
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());

    // Bucket status after battery saver mode OFF event.
    unique_ptr<LogEvent> batterySaverOffEvent =
            CreateBatterySaverOffEvent(/*timestamp=*/bucketStartTimeNs + 30 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOffEvent);
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());

    // Bucket status after condition change to false.
    valueProducer->onConditionChanged(false, bucketStartTimeNs + 40 * NS_PER_SEC);
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());

    // Pull at end of first bucket.
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs, 11));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(2UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    // Battery saver mode ON event. Nothing change since the condition is false.
    batterySaverOnEvent =
            CreateBatterySaverOnEvent(/*timestamp=*/bucket2StartTimeNs + 30 * NS_PER_SEC);
    StateManager::getInstance().onLogEvent(*batterySaverOnEvent);

    // Pull at end of second bucket. Since no new data is seen, mDimInfos will be cleared.
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 15));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);
    ASSERT_EQ(2UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(0UL, valueProducer->mDimInfos.size());

    // Bucket2 status after condition change to true.
    valueProducer->onConditionChanged(true, bucket3StartTimeNs + 10 * NS_PER_SEC);
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());
    // This currently keys into the old state key, which is unknown since mDimInfos was cleared.
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket3StartTimeNs + 30 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(2, report.value_metrics().data_size());

    ValueMetricData data = report.value_metrics().data(0);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(BatterySaverModeStateChanged::ON, data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {2},
                        10 * NS_PER_SEC, -1);
    ValidateValueBucket(data.bucket_info(1), bucket3StartTimeNs,
                        bucket3StartTimeNs + 30 * NS_PER_SEC, {18}, 20 * NS_PER_SEC, -1);

    data = report.value_metrics().data(1);
    EXPECT_EQ(util::BATTERY_SAVER_MODE_STATE_CHANGED, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(BatterySaverModeStateChanged::OFF, data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {4},
                        10 * NS_PER_SEC, -1);
}

/*
 * Test slicing by state for metric that slices by state with a primary field,
 * has multiple dimensions, and a pull that returns incomplete data.
 */
TEST(NumericValueMetricProducerTest, TestSlicedStateWithMultipleDimensionsMissingDataInPull) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithConditionAndState(
            "UID_PROCESS_STATE");
    metric.mutable_dimensions_in_what()->set_field(tagId);
    metric.mutable_dimensions_in_what()->add_child()->set_field(1);
    metric.mutable_dimensions_in_what()->add_child()->set_field(3);

    MetricStateLink* stateLink = metric.add_state_link();
    stateLink->set_state_atom_id(UID_PROCESS_STATE_ATOM_ID);
    auto fieldsInWhat = stateLink->mutable_fields_in_what();
    *fieldsInWhat = CreateDimensions(tagId, {1 /* uid */});
    auto fieldsInState = stateLink->mutable_fields_in_state();
    *fieldsInState = CreateDimensions(UID_PROCESS_STATE_ATOM_ID, {1 /* uid */});
    /*
                    bucket # 1                            bucket # 2
    10     20     30     40     50     60     70     80     90    100    110    120 (seconds)
    |------------------------------------------|---------------------------------|--
                                                                                    (kUnknown)
    x                                                                               {1, 14}
    |-------------|
          20
    x             -                                                                 {1, 16}
    |-------------|
          20
    x                                                                               {2, 8}
    |-----------------|
            25
                                                                                    {FOREGROUND}
                                                                   x                {2, 8}
                                                                   |-------------|
                                                                         20
                                                                                    (BACKGROUND)
                  x                                                                 {1, 14}
                  |----------------------------|---------------------------------|
                               40                              50
                  -                                                                 {1, 16}
                                               |---------------------------------|
                                                               50
                     x                         -                                    {2, 8}
                     |-------------------------|
                                45
    */
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Initial Pull
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs, 1 /*uid*/, 1,
                                                         14 /*tag*/));
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs, 1 /*uid*/, 1,
                                                         16 /*tag*/));
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs, 2 /*uid*/, 1,
                                                         8 /*tag*/));
                return true;
            }))
            // Uid 1 process state change from kStateUnknown -> Background. Tag 16 is missing.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 20 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC,
                                                         1 /*uid*/, 5, 14 /*tag*/));
                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 20 * NS_PER_SEC,
                                                         2 /*uid*/, 7, 8 /*tag*/));
                return true;
            }))
            // Uid 2 process state change from kStateUnknown -> Background
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucketStartTimeNs + 25 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 25 * NS_PER_SEC,
                                                         2 /*uid*/, 8, 8 /*tag*/));
                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 25 * NS_PER_SEC,
                                                         1 /*uid*/, 8, 14 /* tag */));
                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 25 * NS_PER_SEC,
                                                         1 /*uid*/, 8, 16 /* tag */));
                return true;
            }))
            // Uid 2 process state change from Background -> Foreground
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 30 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 30 * NS_PER_SEC, 2 /*uid*/, 18, 8 /*tag*/));
                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 30 * NS_PER_SEC, 1 /*uid*/, 18, 14 /* tag */));
                // This event should be skipped.
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 30 * NS_PER_SEC, 1 /*uid*/, 18, 16 /* tag */));
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                EXPECT_EQ(eventTimeNs, bucket2StartTimeNs + 50 * NS_PER_SEC);
                data->clear();
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 50 * NS_PER_SEC, 1 /*uid*/, 22, 14 /* tag */));
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 50 * NS_PER_SEC, 1 /*uid*/, 22, 16 /* tag */));
                data->push_back(CreateThreeValueLogEvent(
                        tagId, bucket2StartTimeNs + 50 * NS_PER_SEC, 2 /*uid*/, 22, 8 /*tag*/));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithState(
                    pullerManager, metric, {UID_PROCESS_STATE_ATOM_ID}, {});
    EXPECT_EQ(1, valueProducer->mSlicedStateAtoms.size());

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(UID_PROCESS_STATE_ATOM_ID, valueProducer);
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(UID_PROCESS_STATE_ATOM_ID));

    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());

    // Tag 16 is missing and gets trimmed from mDimInfos
    auto uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucketStartTimeNs + 20 * NS_PER_SEC, 1 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_BACKGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(4UL, valueProducer->mCurrentSlicedBucket.size());

    uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucketStartTimeNs + 25 * NS_PER_SEC, 2 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_BACKGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(5UL, valueProducer->mCurrentSlicedBucket.size());

    // Pull at end of first bucket. Uid 2 is missing and gets trimmed from mDimInfos
    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(
            CreateThreeValueLogEvent(tagId, bucket2StartTimeNs, 1 /*uid*/, 13, 14 /* tag */));
    allData.push_back(
            CreateThreeValueLogEvent(tagId, bucket2StartTimeNs, 1 /*uid*/, 13, 16 /* tag */));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs + 1);

    // Buckets flushed. MetricDimensionKeys not corresponding to the current state are removed.
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());
    // {1, 16, kUnknown}, {2, 8, BACKGROUND} aren't present since the pulls were missing the dims.
    ASSERT_EQ(3UL, valueProducer->mPastBuckets.size());

    uidProcessEvent =
            CreateUidProcessStateChangedEvent(bucket2StartTimeNs + 30 * NS_PER_SEC, 2 /* uid */,
                                              android::app::PROCESS_STATE_IMPORTANT_FOREGROUND);
    StateManager::getInstance().onLogEvent(*uidProcessEvent);
    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(4UL, valueProducer->mCurrentSlicedBucket.size());

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket2StartTimeNs + 50 * NS_PER_SEC,
                                true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                &strSet, &output);
    ASSERT_EQ(3UL, valueProducer->mDimInfos.size());
    ASSERT_EQ(3UL, valueProducer->mCurrentSlicedBucket.size());

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    StatsLogReport::ValueMetricDataWrapper valueMetrics;
    sortMetricDataByDimensionsValue(report.value_metrics(), &valueMetrics);

    // {1, 16, kUnknown}, {2, 8, BACKGROUND} aren't present since the pulls were missing the dims.
    ASSERT_EQ(5, valueMetrics.data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());

    // {{uid 1, tag 14}, kStateUnknown}.
    ValueMetricData data = valueMetrics.data(0);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       -1 /*StateTracker::kStateUnknown*/);
    EXPECT_EQ(data.dimensions_in_what().field(), tagId);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 3);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(), 14);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {4},
                        20 * NS_PER_SEC, -1);

    // {{uid 1, tag 14}, BACKGROUND}.
    data = valueMetrics.data(1);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    EXPECT_EQ(data.dimensions_in_what().field(), tagId);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 3);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(), 14);
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {8},
                        40 * NS_PER_SEC, -1);
    ValidateValueBucket(data.bucket_info(1), bucket2StartTimeNs,
                        bucket2StartTimeNs + 50 * NS_PER_SEC, {9}, 50 * NS_PER_SEC, -1);

    // {{uid 1, tag 16}, BACKGROUND}.
    data = valueMetrics.data(2);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    EXPECT_EQ(data.dimensions_in_what().field(), tagId);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 3);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(), 16);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucket2StartTimeNs,
                        bucket2StartTimeNs + 50 * NS_PER_SEC, {9}, 50 * NS_PER_SEC, -1);

    // {{uid 2, tag 8}, kStateUnknown}.
    data = valueMetrics.data(3);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       -1 /*StateTracker::kStateUnknown*/);
    EXPECT_EQ(data.dimensions_in_what().field(), tagId);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 3);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(), 8);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {7},
                        25 * NS_PER_SEC, -1);

    // {{uid 2, tag 8}, FOREGROUND}.
    data = valueMetrics.data(4);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    EXPECT_EQ(data.dimensions_in_what().field(), tagId);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 3);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(), 8);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucket2StartTimeNs,
                        bucket2StartTimeNs + 50 * NS_PER_SEC, {4}, 20 * NS_PER_SEC, -1);
}

/*
 * Test bucket splits when condition is unknown.
 */
TEST(NumericValueMetricProducerTest, TestForcedBucketSplitWhenConditionUnknownSkipsBucket) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kUnknown);

    // App update event.
    int64_t appUpdateTimeNs = bucketStartTimeNs + 1000;
    valueProducer->notifyAppUpgrade(appUpdateTimeNs);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 10000000000;  // 10 seconds
    valueProducer->onDumpReport(dumpReportTimeNs, false /* include current buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(0, report.value_metrics().data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    EXPECT_EQ(NanoToMillis(bucketStartTimeNs),
              report.value_metrics().skipped(0).start_bucket_elapsed_millis());
    EXPECT_EQ(NanoToMillis(appUpdateTimeNs),
              report.value_metrics().skipped(0).end_bucket_elapsed_millis());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());

    auto dropEvent = report.value_metrics().skipped(0).drop_event(0);
    EXPECT_EQ(BucketDropReason::CONDITION_UNKNOWN, dropEvent.drop_reason());
    EXPECT_EQ(NanoToMillis(appUpdateTimeNs), dropEvent.drop_time_millis());
}

TEST(NumericValueMetricProducerTest, TestUploadThreshold) {
    // Create metric with upload threshold and two value fields.
    int64_t thresholdValue = 15;
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.mutable_value_field()->add_child()->set_field(3);
    metric.mutable_threshold()->set_gt_int(thresholdValue);
    *metric.mutable_dimensions_in_what() = CreateDimensions(tagId, {1 /*uid*/});

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First bucket pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(
                        CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 1, 1 /*uid*/, 5, 5));
                data->push_back(
                        CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 1, 2 /*uid*/, 5, 5));
                return true;
            }))
            // Dump report.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 10000000000,
                                                         1 /*uid*/, 22, 21));
                data->push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 10000000000,
                                                         2 /*uid*/, 30, 10));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    // Bucket 2 start.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 1, 1 /*uid*/, 21, 21));
    allData.push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 1, 2 /*uid*/, 20, 5));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucket2StartTimeNs + 10000000000;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include current buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    StatsLogReport::ValueMetricDataWrapper valueMetrics;
    sortMetricDataByDimensionsValue(report.value_metrics(), &valueMetrics);
    ASSERT_EQ(1, valueMetrics.data_size());
    ASSERT_EQ(1, report.value_metrics().skipped_size());

    // Check data keyed to uid 1.
    ValueMetricData data = valueMetrics.data(0);
    ValidateUidDimension(data.dimensions_in_what(), tagId, 1);
    ASSERT_EQ(1, data.bucket_info_size());
    // First bucket.
    // Values pass threshold.
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {16, 16}, -1,
                        0);
    // Second bucket is dropped because values do not pass threshold.

    // Check data keyed to uid 2.
    // First bucket and second bucket are dropped because values do not pass threshold.

    // Check that second bucket has NO_DATA drop reason.
    EXPECT_EQ(bucket2StartTimeNs, report.value_metrics().skipped(0).start_bucket_elapsed_nanos());
    EXPECT_EQ(dumpReportTimeNs, report.value_metrics().skipped(0).end_bucket_elapsed_nanos());
    ASSERT_EQ(1, report.value_metrics().skipped(0).drop_event_size());
    EXPECT_EQ(BucketDropReason::NO_DATA,
              report.value_metrics().skipped(0).drop_event(0).drop_reason());
}

/**
 * Tests pulled atoms with conditions and delayed pull on the bucket boundary in respect to
 * late alarm and condition is true during the pull
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestAlarmLatePullWhileConditionTrue) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;  // 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // Pull on the initial onConditionChanged
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs);

    vector<shared_ptr<LogEvent>> allData;

    // first delayed pull on the bucket #1 edge
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + pullDelayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // second pull on the bucket #2 boundary on time
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 15));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);

    // the second pull did close the second bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5, 5},
                                    {bucketSizeNs, bucketSizeNs}, {pullDelayNs, -pullDelayNs},
                                    {bucketStartTimeNs, bucket2StartTimeNs},
                                    {bucket2StartTimeNs, bucket3StartTimeNs});
}

/**
 * Tests pulled atoms with conditions and delayed pull on the bucket boundary in respect to
 * late alarm and condition is false during the pull
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestAlarmLatePullWhileConditionFalse) {
    const int64_t delayNs = NS_PER_SEC;              // 1 sec
    const int64_t conditionDurationNs = NS_PER_SEC;  // 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    int increasedValue = 5;
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .Times(4)
            .WillRepeatedly(Invoke([&increasedValue](int tagId, const ConfigKey&,
                                                     const int64_t eventTimeNs,
                                                     vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, increasedValue));
                increasedValue += 5;
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs);
    valueProducer->onConditionChanged(false, bucketStartTimeNs + conditionDurationNs);

    vector<shared_ptr<LogEvent>> allData;
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + delayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + delayNs);

    // first delayed pull on the bucket #1 edge
    // the delayed pull did close the first bucket with condition duration == conditionDurationNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {conditionDurationNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    valueProducer->onConditionChanged(true, bucket2StartTimeNs + 2 * delayNs);

    valueProducer->onConditionChanged(false,
                                      bucket2StartTimeNs + 2 * delayNs + conditionDurationNs);

    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);

    // second pull on the bucket #2 edge is on time
    assertPastBucketValuesSingleKey(
            valueProducer->mPastBuckets, {5, 5}, {conditionDurationNs, conditionDurationNs}, {0, 0},
            {bucketStartTimeNs, bucket2StartTimeNs}, {bucket2StartTimeNs, bucket3StartTimeNs});
}

/**
 * Tests pulled atoms with conditions and delayed pull on the bucket boundary in respect to
 * onConditionChanged true to false
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestLatePullOnConditionChangeFalse) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;          // 1 sec
    const int64_t arbitraryIntervalNs = 5 * NS_PER_SEC;  // 5 sec interval
    const int64_t conditionDurationNs = 1 * NS_PER_SEC;  // 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    int increasedValue = 5;
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .Times(4)
            .WillRepeatedly(Invoke([&increasedValue](int tagId, const ConfigKey&,
                                                     const int64_t eventTimeNs,
                                                     vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, increasedValue));
                increasedValue += 5;
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs);

    // will force delayed pull & bucket close
    valueProducer->onConditionChanged(false, bucket2StartTimeNs + pullDelayNs);

    // first delayed pull on the bucket #1 edge
    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // here arbitraryIntervalNs just an arbitrary interval after the delayed pull &
    // before the sequence of condition change events
    valueProducer->onConditionChanged(true, bucket2StartTimeNs + pullDelayNs + arbitraryIntervalNs);

    valueProducer->onConditionChanged(
            false, bucket2StartTimeNs + pullDelayNs + arbitraryIntervalNs + conditionDurationNs);

    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 30));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);

    // second pull on the bucket #2 edge is on time
    // the pull did close the second bucket with condition where
    // duration == conditionDurationNs + carryover from first bucket due to delayed pull
    assertPastBucketValuesSingleKey(
            valueProducer->mPastBuckets, {5, 5}, {bucketSizeNs, pullDelayNs + conditionDurationNs},
            {pullDelayNs, -pullDelayNs}, {bucketStartTimeNs, bucket2StartTimeNs},
            {bucket2StartTimeNs, bucket3StartTimeNs});
}

/**
 * Tests pulled atoms with conditions and delayed pull on the bucket boundary in respect to
 * onConditionChanged false to true
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestLatePullOnConditionChangeTrue) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;                 // 1 sec
    const int64_t conditionSwitchIntervalNs = 10 * NS_PER_SEC;  // 10 sec
    const int64_t conditionDurationNs = 1 * NS_PER_SEC;         // 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    int increasedValue = 5;
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .Times(5)
            .WillRepeatedly(Invoke([&increasedValue](int tagId, const ConfigKey&,
                                                     const int64_t eventTimeNs,
                                                     vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, increasedValue));
                increasedValue += 5;
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs);

    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());

    valueProducer->onConditionChanged(false, bucketStartTimeNs + conditionDurationNs);

    // will force delayed pull & bucket close
    valueProducer->onConditionChanged(true, bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {conditionDurationNs}, {0},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    valueProducer->onConditionChanged(false,
                                      bucket2StartTimeNs + pullDelayNs + conditionDurationNs);

    // will force delayed pull & bucket close
    valueProducer->onConditionChanged(true, bucket3StartTimeNs + pullDelayNs);

    // the delayed pull did close the second bucket with condition duration == conditionDurationNs
    assertPastBucketValuesSingleKey(
            valueProducer->mPastBuckets, {5, 5}, {conditionDurationNs, conditionDurationNs}, {0, 0},
            {bucketStartTimeNs, bucket2StartTimeNs}, {bucket2StartTimeNs, bucket3StartTimeNs});
}

/**
 * Tests pulled atoms with conditions and delayed pull on the bucket boundary in respect to
 * late alarms. Condition is true during the pull
 * With a following events in the middle of the bucket
 * 1) onConditionChanged true to false
 * 2) onConditionChanged false to true
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestAlarmLatePullWithConditionChanged) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;                             // 1 sec
    const int64_t conditionSwitchIntervalNs = 10 * NS_PER_SEC;              // 10 sec
    const int64_t bucket2DelayNs = 5 * NS_PER_SEC;                          // 1 sec
    const int64_t bucket1LatePullNs = bucket2StartTimeNs + pullDelayNs;     // 71 sec
    const int64_t bucket2LatePullNs = bucket3StartTimeNs + bucket2DelayNs;  // 145 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithCondition();

    int increasedValue = 5;
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .Times(5)
            .WillRepeatedly(Invoke([&increasedValue](int tagId, const ConfigKey&,
                                                     const int64_t eventTimeNs,
                                                     vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, increasedValue));
                increasedValue += 5;
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithCondition(
                    pullerManager, metric, ConditionState::kFalse);

    valueProducer->onConditionChanged(true, bucketStartTimeNs);

    // will force delayed pull & bucket #1 close
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket1LatePullNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket1LatePullNs);

    // first delayed pull on the bucket #1 edge
    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    valueProducer->onConditionChanged(false, bucket1LatePullNs + conditionSwitchIntervalNs);

    valueProducer->onConditionChanged(true, bucket1LatePullNs + 2 * conditionSwitchIntervalNs);

    // will force delayed pull & bucket #2 close
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2LatePullNs, 25));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2LatePullNs);

    // second delayed pull on the bucket #2 edge
    // the pull did close the second bucket with condition true
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5, 10},
                                    {bucketSizeNs, bucketSizeNs - conditionSwitchIntervalNs},
                                    {pullDelayNs, -pullDelayNs + bucket2DelayNs},
                                    {bucketStartTimeNs, bucket2StartTimeNs},
                                    {bucket2StartTimeNs, bucket3StartTimeNs});

    valueProducer->onConditionChanged(false, bucket2LatePullNs + conditionSwitchIntervalNs);

    valueProducer->onConditionChanged(true, bucket2LatePullNs + 3 * conditionSwitchIntervalNs);

    // will force pull on time & bucket #3 close
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket4StartTimeNs, 40));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);

    // the pull did close the third bucket with condition true
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5, 10, 15},
                                    {bucketSizeNs, bucketSizeNs - conditionSwitchIntervalNs,
                                     bucketSizeNs - 2 * conditionSwitchIntervalNs},
                                    {pullDelayNs, -pullDelayNs + bucket2DelayNs, -bucket2DelayNs},
                                    {bucketStartTimeNs, bucket2StartTimeNs, bucket3StartTimeNs},
                                    {bucket2StartTimeNs, bucket3StartTimeNs, bucket4StartTimeNs});
}

/**
 * Tests pulled atoms with no conditions and delayed pull on the bucket boundary
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestAlarmLatePullNoCondition) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;  // 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;

    // first delayed pull on the bucket #1 edge
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + pullDelayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // second pull on the bucket #2 boundary on time
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 15));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);

    // the second pull did close the second bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5, 5},
                                    {bucketSizeNs, bucketSizeNs}, {pullDelayNs, -pullDelayNs},
                                    {bucketStartTimeNs, bucket2StartTimeNs},
                                    {bucket2StartTimeNs, bucket3StartTimeNs});

    // third pull on the bucket #3 boundary on time
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket4StartTimeNs, 20));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);

    // the third pull did close the third bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5, 5, 5},
                                    {bucketSizeNs, bucketSizeNs, bucketSizeNs},
                                    {pullDelayNs, -pullDelayNs, 0},
                                    {bucketStartTimeNs, bucket2StartTimeNs, bucket3StartTimeNs},
                                    {bucket2StartTimeNs, bucket3StartTimeNs, bucket4StartTimeNs});
}

/**
 * Tests pulled atoms with no conditions and delayed pull on the bucket boundary
 * The skipped bucket is introduced prior delayed pull
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestAlarmLatePullNoConditionWithSkipped) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;  // 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, bucketStartTimeNs, _))
            .WillOnce(Return(true));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    vector<shared_ptr<LogEvent>> allData;

    // first delayed pull on the bucket #1 edge with delay
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + pullDelayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket which is skipped
    // skipped due to bucket does not contains any value
    ASSERT_EQ(0UL, valueProducer->mPastBuckets.size());
    ASSERT_EQ(1UL, valueProducer->mSkippedBuckets.size());

    // second pull on the bucket #2 boundary on time
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 15));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);

    // the second pull did close the second bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs},
                                    {-pullDelayNs}, {bucket2StartTimeNs}, {bucket3StartTimeNs});

    // third pull on the bucket #3 boundary on time
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket4StartTimeNs, 20));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket4StartTimeNs);

    // the third pull did close the third bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(
            valueProducer->mPastBuckets, {5, 5}, {bucketSizeNs, bucketSizeNs}, {-pullDelayNs, 0},
            {bucket2StartTimeNs, bucket3StartTimeNs}, {bucket3StartTimeNs, bucket4StartTimeNs});
}

/**
 * Tests pulled atoms with no conditions and delayed pull on the bucket boundary
 * The threshold is not defined - correction upload should be skipped
 * Metric population scenario mimics the
 * NumericValueMetricProducerTest_ConditionCorrection.TestAlarmLatePullNoCondition test
 * to extent of a single bucket with correction value due to pull delay
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestThresholdNotDefinedNoUpload) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;  // 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    ASSERT_FALSE(metric.has_condition_correction_threshold_nanos());

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ASSERT_FALSE(valueProducer->mConditionCorrectionThresholdNs.has_value());

    vector<shared_ptr<LogEvent>> allData;

    // first delayed pull on the bucket #1 edge
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + pullDelayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    // and the condition correction == pull delay
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // generate dump report and validate correction value in the reported buckets
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket3StartTimeNs, false /* include partial bucket */, true,
                                FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);

    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());
    ASSERT_EQ(1, report.value_metrics().data(0).bucket_info_size());
    EXPECT_FALSE(report.value_metrics().data(0).bucket_info(0).has_condition_correction_nanos());
}

/**
 * Tests pulled atoms with no conditions and delayed pull on the bucket boundary
 * The threshold set to zero - correction should be performed
 * Metric population scenario mimics the
 * NumericValueMetricProducerTest_ConditionCorrection.TestAlarmLatePullNoCondition test
 * to extent of a single bucket with correction value due to pull delay
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestThresholdDefinedZero) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;  // 1 sec
    const int64_t correctionThresholdNs = 0;     // 0 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_condition_correction_threshold_nanos(correctionThresholdNs);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ASSERT_EQ(correctionThresholdNs, valueProducer->mConditionCorrectionThresholdNs);

    vector<shared_ptr<LogEvent>> allData;

    // first delayed pull on the bucket #1 edge
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + pullDelayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // generate dump report and validate correction value in the reported buckets
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket3StartTimeNs, false /* include partial bucket */, true,
                                FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);

    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());
    ASSERT_EQ(1, report.value_metrics().data(0).bucket_info_size());
    EXPECT_EQ(pullDelayNs,
              report.value_metrics().data(0).bucket_info(0).condition_correction_nanos());
}

/**
 * Tests pulled atoms with no conditions and delayed pull on the bucket boundary
 * The threshold is equal to the pullDelayNs - correction should be performed
 * Metric population scenario mimics the
 * NumericValueMetricProducerTest_ConditionCorrection.TestAlarmLatePullNoCondition test
 * to extent of a 2 bucket with correction value due to pull delay
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestThresholdUploadPassWhenEqual) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;         // 1 sec
    const int64_t correctionThresholdNs = pullDelayNs;  // 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_condition_correction_threshold_nanos(pullDelayNs);
    ASSERT_EQ(pullDelayNs, metric.condition_correction_threshold_nanos());

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ASSERT_EQ(correctionThresholdNs, valueProducer->mConditionCorrectionThresholdNs);

    vector<shared_ptr<LogEvent>> allData;

    // first delayed pull on the bucket #1 edge
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + pullDelayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // second pull on the bucket #2 boundary on time
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket3StartTimeNs, 15));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket3StartTimeNs);

    // the second pull did close the second bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5, 5},
                                    {bucketSizeNs, bucketSizeNs}, {pullDelayNs, -pullDelayNs},
                                    {bucketStartTimeNs, bucket2StartTimeNs},
                                    {bucket2StartTimeNs, bucket3StartTimeNs});

    // generate dump report and validate correction value in the reported buckets
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket3StartTimeNs, false /* include partial bucket */, true,
                                FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);

    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());
    ASSERT_EQ(2, report.value_metrics().data(0).bucket_info_size());
    EXPECT_EQ(pullDelayNs,
              report.value_metrics().data(0).bucket_info(0).condition_correction_nanos());
    EXPECT_EQ(-pullDelayNs,
              report.value_metrics().data(0).bucket_info(1).condition_correction_nanos());
}

/**
 * Tests pulled atoms with no conditions and delayed pull on the bucket boundary
 * The threshold is smaller thant pullDelayNs - correction should be performed
 * Metric population scenario mimics the
 * NumericValueMetricProducerTest_ConditionCorrection.TestAlarmLatePullNoCondition test
 * to extent of a single bucket with correction value due to pull delay
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestThresholdUploadPassWhenGreater) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;            // 1 sec
    const int64_t correctionThresholdNs = NS_PER_SEC - 1;  // less than 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_condition_correction_threshold_nanos(correctionThresholdNs);
    ASSERT_EQ(correctionThresholdNs, metric.condition_correction_threshold_nanos());

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ASSERT_EQ(correctionThresholdNs, valueProducer->mConditionCorrectionThresholdNs);

    vector<shared_ptr<LogEvent>> allData;

    // first delayed pull on the bucket #1 edge
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + pullDelayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // generate dump report and validate correction value in the reported buckets
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket3StartTimeNs, false /* include partial bucket */, true,
                                FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);

    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());
    ASSERT_EQ(1, report.value_metrics().data(0).bucket_info_size());
    EXPECT_EQ(pullDelayNs,
              report.value_metrics().data(0).bucket_info(0).condition_correction_nanos());
}

/**
 * Tests pulled atoms with no conditions and delayed pull on the bucket boundary
 * The threshold is greater than pullDelayNs - correction upload should be skipped
 * Metric population scenario mimics the
 * NumericValueMetricProducerTest_ConditionCorrection.TestAlarmLatePullNoCondition test
 * to extent of a single bucket with correction value due to pull delay
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestThresholdUploadSkip) {
    const int64_t pullDelayNs = 1 * NS_PER_SEC;            // 1 sec
    const int64_t correctionThresholdNs = NS_PER_SEC + 1;  // greater than 1 sec

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    metric.set_condition_correction_threshold_nanos(correctionThresholdNs);
    ASSERT_EQ(correctionThresholdNs, metric.condition_correction_threshold_nanos());

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, 5));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    ASSERT_EQ(correctionThresholdNs, valueProducer->mConditionCorrectionThresholdNs);

    vector<shared_ptr<LogEvent>> allData;

    // first delayed pull on the bucket #1 edge
    allData.clear();
    allData.push_back(CreateRepeatedValueLogEvent(tagId, bucket2StartTimeNs + pullDelayNs, 10));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS,
                                bucket2StartTimeNs + pullDelayNs);

    // the delayed pull did close the first bucket with condition duration == bucketSizeNs
    assertPastBucketValuesSingleKey(valueProducer->mPastBuckets, {5}, {bucketSizeNs}, {pullDelayNs},
                                    {bucketStartTimeNs}, {bucket2StartTimeNs});

    // generate dump report and validate correction value in the reported buckets
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket3StartTimeNs, false /* include partial bucket */, true,
                                FAST /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);

    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(1, report.value_metrics().data_size());
    ASSERT_EQ(0, report.value_metrics().skipped_size());
    ASSERT_EQ(1, report.value_metrics().data(0).bucket_info_size());
    EXPECT_FALSE(report.value_metrics().data(0).bucket_info(0).has_condition_correction_nanos());
}

/**
 * Tests pulled atoms with no conditions and delayed pull on the bucket boundary
 * for the atoms sliced by state. Delayed pull occures due to delayed onStateChange event
 * First bucket ends with delayed OFF -> ON transition, correction is applied only to OFF state
 * Second and third buckets pulled ontime
 */
TEST(NumericValueMetricProducerTest_ConditionCorrection, TestLateStateChangeSlicedAtoms) {
    // Set up NumericValueMetricProducer.
    ValueMetric metric =
            NumericValueMetricProducerTestHelper::createMetricWithState("SCREEN_STATE");
    metric.set_condition_correction_threshold_nanos(0);
    int increasedValue = 1;
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            .Times(5)
            .WillRepeatedly(Invoke([&increasedValue](int tagId, const ConfigKey&,
                                                     const int64_t eventTimeNs,
                                                     vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateRepeatedValueLogEvent(tagId, eventTimeNs, increasedValue++));
                return true;
            }));

    StateManager::getInstance().clear();
    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithState(
                    pullerManager, metric, {util::SCREEN_STATE_CHANGED}, {});

    // Set up StateManager and check that StateTrackers are initialized.
    StateManager::getInstance().registerListener(SCREEN_STATE_ATOM_ID, valueProducer);

    // Bucket status after screen state change kStateUnknown->OFF
    auto screenEvent = CreateScreenStateChangedEvent(
            bucketStartTimeNs + 5 * NS_PER_SEC, android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(2UL, valueProducer->mCurrentSlicedBucket.size());

    // Value for dimension, state key {{}, OFF}
    auto it = valueProducer->mCurrentSlicedBucket.begin();
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_OFF,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucketStartTimeNs + 5 * NS_PER_SEC);

    // Bucket status after screen state change OFF->ON, forces bucket flush and new bucket start
    // with 10 seconds delay
    screenEvent = CreateScreenStateChangedEvent(bucket2StartTimeNs + 10 * NS_PER_SEC,
                                                android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    StateManager::getInstance().onLogEvent(*screenEvent);
    // Bucket flush will trim all MetricDimensionKeys besides the current state key.
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    // mCurrentSlicedBucket represents second bucket
    // Value for dimension, state key {{}, ON}
    it = valueProducer->mCurrentSlicedBucket.begin();
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucket2StartTimeNs + 10 * NS_PER_SEC);

    // Bucket status after screen state change ON->OFF, forces bucket flush and new bucket start
    screenEvent = CreateScreenStateChangedEvent(bucket3StartTimeNs,
                                                android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    // mCurrentSlicedBucket represents third bucket
    // Value for dimension, state key {{}, OFF}
    it = valueProducer->mCurrentSlicedBucket.begin();
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_OFF,
              it->first.getStateValuesKey().getValues()[0].mValue.int_value);
    assertConditionTimer(it->second.conditionTimer, true, 0, bucket3StartTimeNs, 0);

    // Bucket status after screen state change OFF->ON, forces bucket flush and new bucket start
    screenEvent = CreateScreenStateChangedEvent(bucket4StartTimeNs,
                                                android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    StateManager::getInstance().onLogEvent(*screenEvent);
    ASSERT_EQ(1UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(1UL, valueProducer->mDimInfos.size());

    // Start dump report and check output.
    ProtoOutputStream output;
    std::set<string> strSet;
    valueProducer->onDumpReport(bucket4StartTimeNs + 10, false /* do not include partial buckets */,
                                true, NO_TIME_CONSTRAINTS, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    ASSERT_EQ(3, report.value_metrics().data_size());

    // {{}, ON} - delayed start finish on time - no correction
    auto data = report.value_metrics().data(0);
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON, data.slice_by_state(0).value());
    ValidateValueBucket(data.bucket_info(0), bucket2StartTimeNs, bucket3StartTimeNs, {1},
                        50 * NS_PER_SEC, 0);

    // {{}, Unknown}
    data = report.value_metrics().data(1);
    EXPECT_EQ(-1, data.slice_by_state(0).value());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {1},
                        5 * NS_PER_SEC, 0);

    // {{}, OFF}
    data = report.value_metrics().data(2);
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_OFF, data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {1},
                        55 * NS_PER_SEC, 10 * NS_PER_SEC);
    ValidateValueBucket(data.bucket_info(1), bucket3StartTimeNs, bucket4StartTimeNs, {1},
                        60 * NS_PER_SEC, 0);
}

TEST(NumericValueMetricProducerTest, TestSubsetDimensions) {
    // Create metric with subset of dimensions.
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();
    *metric.mutable_dimensions_in_what() = CreateDimensions(tagId, {1 /*uid*/});

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First and third fields are dimension fields. Second field is the value field.
            // First bucket pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(
                        CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 1, 1 /*uid*/, 5, 5));
                data->push_back(
                        CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 1, 1 /*uid*/, 5, 7));
                data->push_back(
                        CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 1, 2 /*uid*/, 6, 5));
                data->push_back(
                        CreateThreeValueLogEvent(tagId, bucketStartTimeNs + 1, 2 /*uid*/, 6, 7));
                return true;
            }))
            // Dump report.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 10000000000,
                                                         1 /*uid*/, 13, 5));
                data->push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 10000000000,
                                                         1 /*uid*/, 15, 7));
                data->push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 10000000000,
                                                         2 /*uid*/, 21, 5));
                data->push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 10000000000,
                                                         2 /*uid*/, 22, 7));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    // Bucket 2 start.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 1, 1 /*uid*/, 10, 5));
    allData.push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 1, 1 /*uid*/, 11, 7));
    allData.push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 1, 2 /*uid*/, 8, 5));
    allData.push_back(CreateThreeValueLogEvent(tagId, bucket2StartTimeNs + 1, 2 /*uid*/, 9, 7));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucket2StartTimeNs + 10000000000;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include current buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);
    ASSERT_EQ(0UL, valueProducer->mCurrentSlicedBucket.size());
    ASSERT_EQ(2UL, valueProducer->mDimInfos.size());

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    StatsLogReport::ValueMetricDataWrapper valueMetrics;
    sortMetricDataByDimensionsValue(report.value_metrics(), &valueMetrics);
    ASSERT_EQ(2, valueMetrics.data_size());
    EXPECT_EQ(0, report.value_metrics().skipped_size());

    // Check data keyed to uid 1.
    ValueMetricData data = valueMetrics.data(0);
    ValidateUidDimension(data.dimensions_in_what(), tagId, 1);
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {11}, -1, 0);
    ValidateValueBucket(data.bucket_info(1), bucket2StartTimeNs, dumpReportTimeNs, {7}, -1, 0);

    // Check data keyed to uid 2.
    data = valueMetrics.data(1);
    ValidateUidDimension(data.dimensions_in_what(), tagId, 2);
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {5}, -1, 0);
    ValidateValueBucket(data.bucket_info(1), bucket2StartTimeNs, dumpReportTimeNs, {26}, -1, 0);
}

TEST(NumericValueMetricProducerTest, TestRepeatedValueFieldAndDimensions) {
    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetricWithRepeatedValueField();
    metric.mutable_dimensions_in_what()->set_field(tagId);
    FieldMatcher* valueChild = metric.mutable_dimensions_in_what()->add_child();
    valueChild->set_field(1);
    valueChild->set_position(Position::FIRST);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First field is a dimension field (repeated, position FIRST).
            // Third field is the value field (repeated, position FIRST).
            // NumericValueMetricProducer initialized.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(
                        makeRepeatedUidLogEvent(tagId, bucketStartTimeNs + 1, {1, 10}, 5, {2, 3}));
                data->push_back(
                        makeRepeatedUidLogEvent(tagId, bucketStartTimeNs + 1, {2, 10}, 5, {3, 4}));
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(makeRepeatedUidLogEvent(tagId, bucket2StartTimeNs + 10000000000,
                                                        {1, 10}, 5, {10, 3}));
                data->push_back(makeRepeatedUidLogEvent(tagId, bucket2StartTimeNs + 10000000000,
                                                        {2, 10}, 5, {14, 4}));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(pullerManager,
                                                                                  metric);

    // Bucket 2 start.
    vector<shared_ptr<LogEvent>> allData;
    allData.clear();
    allData.push_back(makeRepeatedUidLogEvent(tagId, bucket2StartTimeNs + 1, {1, 10}, 5, {5, 7}));
    allData.push_back(makeRepeatedUidLogEvent(tagId, bucket2StartTimeNs + 1, {2, 10}, 5, {7, 5}));
    valueProducer->onDataPulled(allData, PullResult::PULL_RESULT_SUCCESS, bucket2StartTimeNs);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucket2StartTimeNs + 10000000000;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include current buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    StatsLogReport::ValueMetricDataWrapper valueMetrics;
    sortMetricDataByDimensionsValue(report.value_metrics(), &valueMetrics);
    ASSERT_EQ(2, valueMetrics.data_size());
    EXPECT_EQ(0, report.value_metrics().skipped_size());

    // Check data keyed to uid 1.
    ValueMetricData data = valueMetrics.data(0);
    ValidateUidDimension(data.dimensions_in_what(), tagId, 1);
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {3}, -1,
                        0);  // Summed diffs of 2, 5
    ValidateValueBucket(data.bucket_info(1), bucket2StartTimeNs, dumpReportTimeNs, {5}, -1,
                        0);  // Summed diffs of 5, 10

    // Check data keyed to uid 2.
    data = valueMetrics.data(1);
    ValidateUidDimension(data.dimensions_in_what(), tagId, 2);
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, {4}, -1,
                        0);  // Summed diffs of 3, 7
    ValidateValueBucket(data.bucket_info(1), bucket2StartTimeNs, dumpReportTimeNs, {7}, -1,
                        0);  // Summed diffs of 7, 14
}

TEST(NumericValueMetricProducerTest, TestSampleSize) {
    sp<EventMatcherWizard> eventMatcherWizard =
            createEventMatcherWizard(tagId, logEventMatcherIndex);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    ValueMetric metric = NumericValueMetricProducerTestHelper::createMetric();

    /*Sample size is added automatically with ValueMetric::AVG*/
    metric.set_aggregation_type(ValueMetric::AVG);
    sp<NumericValueMetricProducer> valueProducerAvg =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    /*Sample size is not added automatically with non-ValueMetric::AVG aggregation types*/
    metric.set_aggregation_type(ValueMetric::SUM);
    sp<NumericValueMetricProducer> valueProducerSum =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    /*Sample size is added when include_sample_size bool is set to true*/
    metric.set_include_sample_size(true);
    sp<NumericValueMetricProducer> valueProducerSumWithSampleSize =
            NumericValueMetricProducerTestHelper::createValueProducerNoConditions(
                    pullerManager, metric, /*pullAtomId=*/-1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(&event1, tagId, bucketStartTimeNs + 10, 10);
    CreateRepeatedValueLogEvent(&event2, tagId, bucketStartTimeNs + 20, 15);
    CreateRepeatedValueLogEvent(&event3, tagId, bucketStartTimeNs + 20, 20);
    valueProducerAvg->onMatchedLogEvent(1 /*log matcher index*/, event1);
    valueProducerAvg->onMatchedLogEvent(1 /*log matcher index*/, event2);
    valueProducerSum->onMatchedLogEvent(1 /*log matcher index*/, event1);
    valueProducerSum->onMatchedLogEvent(1 /*log matcher index*/, event2);
    valueProducerSum->onMatchedLogEvent(1 /*log matcher index*/, event3);
    valueProducerSumWithSampleSize->onMatchedLogEvent(1 /*log matcher index*/, event1);
    valueProducerSumWithSampleSize->onMatchedLogEvent(1 /*log matcher index*/, event2);
    valueProducerSumWithSampleSize->onMatchedLogEvent(1 /*log matcher index*/, event3);

    NumericValueMetricProducer::Interval curInterval;
    ASSERT_EQ(1UL, valueProducerAvg->mCurrentSlicedBucket.size());
    curInterval = valueProducerAvg->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(2, curInterval.sampleSize);
    ASSERT_EQ(1UL, valueProducerSum->mCurrentSlicedBucket.size());
    curInterval = valueProducerSum->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(3, curInterval.sampleSize);
    ASSERT_EQ(1UL, valueProducerSumWithSampleSize->mCurrentSlicedBucket.size());
    curInterval = valueProducerSumWithSampleSize->mCurrentSlicedBucket.begin()->second.intervals[0];
    EXPECT_EQ(3, curInterval.sampleSize);

    valueProducerAvg->flushIfNeededLocked(bucket2StartTimeNs);
    valueProducerSum->flushIfNeededLocked(bucket2StartTimeNs);
    valueProducerSumWithSampleSize->flushIfNeededLocked(bucket2StartTimeNs);

    // Start dump report and check output.
    ProtoOutputStream outputAvg;
    std::set<string> strSetAvg;
    valueProducerAvg->onDumpReport(bucket2StartTimeNs + 50 * NS_PER_SEC,
                                   true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                   &strSetAvg, &outputAvg);

    StatsLogReport reportAvg = outputStreamToProto(&outputAvg);
    ASSERT_EQ(1, reportAvg.value_metrics().data_size());

    ValueMetricData data = reportAvg.value_metrics().data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ASSERT_EQ(1, data.bucket_info(0).values_size());
    EXPECT_EQ(2, data.bucket_info(0).values(0).sample_size());
    EXPECT_TRUE(std::abs(data.bucket_info(0).values(0).value_double() - 12.5) < epsilon);

    // Start dump report and check output.
    ProtoOutputStream outputSum;
    std::set<string> strSetSum;
    valueProducerSum->onDumpReport(bucket2StartTimeNs + 50 * NS_PER_SEC,
                                   true /* include recent buckets */, true, NO_TIME_CONSTRAINTS,
                                   &strSetSum, &outputSum);

    StatsLogReport reportSum = outputStreamToProto(&outputSum);
    ASSERT_EQ(1, reportSum.value_metrics().data_size());

    data = reportSum.value_metrics().data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ASSERT_EQ(1, data.bucket_info(0).values_size());
    EXPECT_EQ(45, data.bucket_info(0).values(0).value_long());
    EXPECT_FALSE(data.bucket_info(0).values(0).has_sample_size());

    // Start dump report and check output.
    ProtoOutputStream outputSumWithSampleSize;
    std::set<string> strSetSumWithSampleSize;
    valueProducerSumWithSampleSize->onDumpReport(
            bucket2StartTimeNs + 50 * NS_PER_SEC, true /* include recent buckets */, true,
            NO_TIME_CONSTRAINTS, &strSetSumWithSampleSize, &outputSumWithSampleSize);

    StatsLogReport reportSumWithSampleSize = outputStreamToProto(&outputSumWithSampleSize);
    ASSERT_EQ(1, reportSumWithSampleSize.value_metrics().data_size());

    data = reportSumWithSampleSize.value_metrics().data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ASSERT_EQ(1, data.bucket_info(0).values_size());
    EXPECT_EQ(3, data.bucket_info(0).values(0).sample_size());
    EXPECT_EQ(45, data.bucket_info(0).values(0).value_long());
}

TEST(NumericValueMetricProducerTest, TestDimensionalSampling) {
    ShardOffsetProvider::getInstance().setShardOffset(5);

    int shardCount = 2;
    ValueMetric sampledValueMetric = NumericValueMetricProducerTestHelper::createMetric();
    *sampledValueMetric.mutable_dimensions_in_what() = CreateDimensions(tagId, {1 /*uid*/});
    *sampledValueMetric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(tagId, {1 /*uid*/});
    sampledValueMetric.mutable_dimensional_sampling_info()->set_shard_count(shardCount);

    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    EXPECT_CALL(*pullerManager, Pull(tagId, kConfigKey, _, _))
            // First field is a dimension field and sampled what field.
            // Second field is the value field.
            // NumericValueMetricProducer initialized.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(makeUidLogEvent(tagId, bucketStartTimeNs + 1, 1001, 5, 10));
                data->push_back(makeUidLogEvent(tagId, bucketStartTimeNs + 1, 1002, 10, 10));
                data->push_back(makeUidLogEvent(tagId, bucketStartTimeNs + 1, 1003, 15, 10));
                return true;
            }))
            // Dump report pull.
            .WillOnce(Invoke([](int tagId, const ConfigKey&, const int64_t eventTimeNs,
                                vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(
                        makeUidLogEvent(tagId, bucketStartTimeNs + 10000000000, 1001, 6, 10));
                data->push_back(
                        makeUidLogEvent(tagId, bucketStartTimeNs + 10000000000, 1002, 12, 10));
                data->push_back(
                        makeUidLogEvent(tagId, bucketStartTimeNs + 10000000000, 1003, 18, 10));
                return true;
            }));

    sp<NumericValueMetricProducer> valueProducer =
            NumericValueMetricProducerTestHelper::createValueProducerWithSampling(
                    pullerManager, sampledValueMetric);

    // Check dump report.
    ProtoOutputStream output;
    std::set<string> strSet;
    int64_t dumpReportTimeNs = bucketStartTimeNs + 10000000000;
    valueProducer->onDumpReport(dumpReportTimeNs, true /* include current buckets */, true,
                                NO_TIME_CONSTRAINTS /* dumpLatency */, &strSet, &output);

    StatsLogReport report = outputStreamToProto(&output);
    backfillDimensionPath(&report);
    backfillStartEndTimestamp(&report);
    EXPECT_TRUE(report.has_value_metrics());
    StatsLogReport::ValueMetricDataWrapper valueMetrics;
    sortMetricDataByDimensionsValue(report.value_metrics(), &valueMetrics);
    ASSERT_EQ(2, valueMetrics.data_size());
    EXPECT_EQ(0, report.value_metrics().skipped_size());

    // Only Uid 1, 3, 4 are logged. (odd hash value) + (offset of 5) % (shard count of 2) = 0
    ValueMetricData data = valueMetrics.data(0);
    ValidateUidDimension(data.dimensions_in_what(), tagId, 1001);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + 10000000000,
                        {1}, -1,
                        0);  // Diff of 5 and 6

    data = valueMetrics.data(1);
    ValidateUidDimension(data.dimensions_in_what(), tagId, 1003);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateValueBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + 10000000000,
                        {3}, -1,
                        0);  // Diff of 15 and 18
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
