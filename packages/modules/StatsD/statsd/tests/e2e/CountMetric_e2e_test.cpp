/*
 * Copyright (C) 2019, The Android Open Source Project
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

#include <gtest/gtest.h>

#include "src/StatsLogProcessor.h"
#include "src/state/StateManager.h"
#include "src/state/StateTracker.h"
#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

/**
 * Tests the initial condition and condition after the first log events for
 * count metrics with either a combination condition or simple condition.
 *
 * Metrics should be initialized with condition kUnknown (given that the
 * predicate is using the default InitialValue of UNKNOWN). The condition should
 * be updated to either kFalse or kTrue if a condition event is logged for all
 * children conditions.
 */
TEST(CountMetricE2eTest, TestInitialConditionChanges) {
    // Initialize config.
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.

    auto syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = CreateBatteryStateNoneMatcher();
    *config.add_atom_matcher() = CreateBatteryStateUsbMatcher();

    auto screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;

    auto deviceUnpluggedPredicate = CreateDeviceUnpluggedPredicate();
    *config.add_predicate() = deviceUnpluggedPredicate;

    auto screenOnOnBatteryPredicate = config.add_predicate();
    screenOnOnBatteryPredicate->set_id(StringToId("screenOnOnBatteryPredicate"));
    screenOnOnBatteryPredicate->mutable_combination()->set_operation(LogicalOperation::AND);
    addPredicateToPredicateCombination(screenOnPredicate, screenOnOnBatteryPredicate);
    addPredicateToPredicateCombination(deviceUnpluggedPredicate, screenOnOnBatteryPredicate);

    // CountSyncStartWhileScreenOnOnBattery (CombinationCondition)
    CountMetric* countMetric1 = config.add_count_metric();
    countMetric1->set_id(StringToId("CountSyncStartWhileScreenOnOnBattery"));
    countMetric1->set_what(syncStartMatcher.id());
    countMetric1->set_condition(screenOnOnBatteryPredicate->id());
    countMetric1->set_bucket(FIVE_MINUTES);

    // CountSyncStartWhileOnBattery (SimpleCondition)
    CountMetric* countMetric2 = config.add_count_metric();
    countMetric2->set_id(StringToId("CountSyncStartWhileOnBatterySliceScreen"));
    countMetric2->set_what(syncStartMatcher.id());
    countMetric2->set_condition(deviceUnpluggedPredicate.id());
    countMetric2->set_bucket(FIVE_MINUTES);

    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    auto processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    EXPECT_EQ(processor->mMetricsManagers.size(), 1u);
    sp<MetricsManager> metricsManager = processor->mMetricsManagers.begin()->second;
    EXPECT_TRUE(metricsManager->isConfigValid());
    EXPECT_EQ(2, metricsManager->mAllMetricProducers.size());

    sp<MetricProducer> metricProducer1 = metricsManager->mAllMetricProducers[0];
    sp<MetricProducer> metricProducer2 = metricsManager->mAllMetricProducers[1];

    EXPECT_EQ(ConditionState::kUnknown, metricProducer1->mCondition);
    EXPECT_EQ(ConditionState::kUnknown, metricProducer2->mCondition);

    auto screenOnEvent =
            CreateScreenStateChangedEvent(bucketStartTimeNs + 30, android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());
    EXPECT_EQ(ConditionState::kUnknown, metricProducer1->mCondition);
    EXPECT_EQ(ConditionState::kUnknown, metricProducer2->mCondition);

    auto pluggedUsbEvent = CreateBatteryStateChangedEvent(
            bucketStartTimeNs + 50, BatteryPluggedStateEnum::BATTERY_PLUGGED_USB);
    processor->OnLogEvent(pluggedUsbEvent.get());
    EXPECT_EQ(ConditionState::kFalse, metricProducer1->mCondition);
    EXPECT_EQ(ConditionState::kFalse, metricProducer2->mCondition);

    auto pluggedNoneEvent = CreateBatteryStateChangedEvent(
            bucketStartTimeNs + 70, BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE);
    processor->OnLogEvent(pluggedNoneEvent.get());
    EXPECT_EQ(ConditionState::kTrue, metricProducer1->mCondition);
    EXPECT_EQ(ConditionState::kTrue, metricProducer2->mCondition);
}

TEST(CountMetricE2eTest, TestConditionTrueNanos) {
    // Initialize config.
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.

    AtomMatcher syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();

    AtomMatcher crashMatcher = CreateProcessCrashAtomMatcher();
    *config.add_atom_matcher() = crashMatcher;

    Predicate screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;

    CountMetric* countMetric = config.add_count_metric();
    int64_t metricId = StringToId("CountSyncStartWhileScreenOn");
    countMetric->set_id(metricId);
    countMetric->set_what(syncStartMatcher.id());
    countMetric->set_condition(screenOnPredicate.id());
    countMetric->set_bucket(FIVE_MINUTES);

    MetricActivation* metricActivation = config.add_metric_activation();
    metricActivation->set_metric_id(metricId);
    EventActivation* eventActivation = metricActivation->add_event_activation();
    eventActivation->set_atom_matcher_id(crashMatcher.id());
    eventActivation->set_ttl_seconds(360);  // 6 minutes.

    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    const uint64_t bucket2StartTimeNs = bucketStartTimeNs + bucketSizeNs;
    const uint64_t bucket3StartTimeNs = bucket2StartTimeNs + bucketSizeNs;

    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    std::vector<int> attributionUids1 = {123};
    std::vector<string> attributionTags1 = {"App1"};
    int64_t conditionStart1Ns = bucketStartTimeNs + 50 * NS_PER_SEC;
    int64_t activationStart1Ns = bucketStartTimeNs + 70 * NS_PER_SEC;
    int64_t conditionEnd1Ns = bucket2StartTimeNs + 35 * NS_PER_SEC;
    int64_t conditionStart2Ns = bucket2StartTimeNs + 90 * NS_PER_SEC;
    int64_t activationEnd1Ns = bucket2StartTimeNs + 140 * NS_PER_SEC;
    int64_t conditionEnd2Ns = bucket2StartTimeNs + 155 * NS_PER_SEC;
    int64_t activationStart2Ns = bucket2StartTimeNs + 200 * NS_PER_SEC;
    int64_t conditionStart3Ns = bucket2StartTimeNs + 240 * NS_PER_SEC;
    int64_t conditionEnd3Ns = bucket3StartTimeNs + 40 * NS_PER_SEC;
    int64_t activationEnd2Ns = bucket3StartTimeNs + 270 * NS_PER_SEC;

    std::vector<std::unique_ptr<LogEvent>> events;
    // Active false, condition becomes true.
    events.push_back(CreateScreenStateChangedEvent(
            conditionStart1Ns,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 1:00
    // Event not counted.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 60 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 1:10
    // Active becomes true, condition true.
    events.push_back(CreateAppCrashEvent(activationStart1Ns, 111));  // 1:20
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 75 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 1:25

    // Bucket 2 starts. 5:10
    events.push_back(CreateSyncStartEvent(bucket2StartTimeNs + 20 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 5:30
    // Active true, condition becomes false.
    events.push_back(CreateScreenStateChangedEvent(
            conditionEnd1Ns,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 5:45
    // Event not counted.
    events.push_back(CreateSyncStartEvent(bucket2StartTimeNs + 50 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 6:00
    // Active true, condition becomes true.
    events.push_back(CreateScreenStateChangedEvent(
            conditionStart2Ns,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 6:40
    events.push_back(CreateSyncStartEvent(bucket2StartTimeNs + 110 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 7:00
    // Active becomes false, condition true (activation expires).
    // Event not counted.
    events.push_back(CreateSyncStartEvent(activationEnd1Ns, attributionUids1, attributionTags1,
                                          "sync_name"));  // 7:30
    // Active false, condition becomes false.
    events.push_back(CreateScreenStateChangedEvent(
            conditionEnd2Ns,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 7:45
    // Event not counted.
    events.push_back(CreateSyncStartEvent(bucket2StartTimeNs + 160 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 7:50
    // Active becomes true, condition false.
    events.push_back(CreateAppCrashEvent(activationStart2Ns, 111));  // 8:30
    // Event not counted.
    events.push_back(CreateSyncStartEvent(bucket2StartTimeNs + 220 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 8:50
    // Active true, condition becomes true.
    events.push_back(CreateScreenStateChangedEvent(
            conditionStart3Ns,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 9:10
    events.push_back(CreateSyncStartEvent(bucket2StartTimeNs + 250 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 9:20

    // Bucket 3 starts. 10:10
    events.push_back(CreateSyncStartEvent(bucket3StartTimeNs + 10 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 10:20
    // Active true, condition becomes false.
    events.push_back(CreateScreenStateChangedEvent(
            conditionEnd3Ns,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 10:50
                                                                   // Event not counted.
    events.push_back(CreateSyncStartEvent(bucket3StartTimeNs + 70 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 11:20
    // Active becomes false, condition false (activation expires).
    // Event not counted.
    events.push_back(CreateSyncStartEvent(activationEnd2Ns, attributionUids1, attributionTags1,
                                          "sync_name"));  // 14:40

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    int64_t dumpReportTimeNs = bucket3StartTimeNs + 290 * NS_PER_SEC;  // 15:00
    processor->onDumpReport(cfgKey, dumpReportTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(1, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(4, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucket2StartTimeNs, 1,
                        bucket2StartTimeNs - activationStart1Ns);
    ValidateCountBucket(
            data.bucket_info(1), bucket2StartTimeNs, activationEnd1Ns, 2,
            (conditionEnd1Ns - bucket2StartTimeNs) + (activationEnd1Ns - conditionStart2Ns));
    ValidateCountBucket(data.bucket_info(2), activationEnd1Ns, bucket3StartTimeNs, 1,
                        bucket3StartTimeNs - conditionStart3Ns);
    ValidateCountBucket(data.bucket_info(3), bucket3StartTimeNs, activationEnd2Ns, 1,
                        conditionEnd3Ns - bucket3StartTimeNs);
}

/**
* Test a count metric that has one slice_by_state with no primary fields.
*
* Once the CountMetricProducer is initialized, it has one atom id in
* mSlicedStateAtoms and no entries in mStateGroupMap.

* One StateTracker tracks the state atom, and it has one listener which is the
* CountMetricProducer that was initialized.
*/
TEST(CountMetricE2eTest, TestSlicedState) {
    // Initialize config.
    StatsdConfig config;

    auto syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;

    auto state = CreateScreenState();
    *config.add_state() = state;

    // Create count metric that slices by screen state.
    int64_t metricId = 123456;
    auto countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(syncStartMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    countMetric->add_slice_by_state(state.id());

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    auto processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    // Check that CountMetricProducer was initialized correctly.
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    sp<MetricsManager> metricsManager = processor->mMetricsManagers.begin()->second;
    EXPECT_TRUE(metricsManager->isConfigValid());
    ASSERT_EQ(metricsManager->mAllMetricProducers.size(), 1);
    sp<MetricProducer> metricProducer = metricsManager->mAllMetricProducers[0];
    ASSERT_EQ(metricProducer->mSlicedStateAtoms.size(), 1);
    EXPECT_EQ(metricProducer->mSlicedStateAtoms.at(0), SCREEN_STATE_ATOM_ID);
    ASSERT_EQ(metricProducer->mStateGroupMap.size(), 0);

    // Check that StateTrackers were initialized correctly.
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(SCREEN_STATE_ATOM_ID));

    /*
               bucket #1                      bucket #2
    |     1     2     3     4     5     6     7     8     9     10 (minutes)
    |-----------------------------|-----------------------------|--
            x                x         x    x        x      x       (syncStartEvents)
          |                                       |                 (ScreenIsOnEvent)
                   |     |                                          (ScreenIsOffEvent)
                                                        |           (ScreenDozeEvent)
    */
    // Initialize log events - first bucket.
    std::vector<int> attributionUids1 = {123};
    std::vector<string> attributionTags1 = {"App1"};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 50 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 1:00
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 75 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 1:25
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 150 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 2:40
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 200 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 3:30
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 250 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 4:20

    // Initialize log events - second bucket.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 350 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 6:00
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 400 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 6:50
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 450 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 7:40
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 475 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 8:05
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 500 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_UNKNOWN));  // 8:30
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 520 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 8:50

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs * 2 + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(3, countMetrics.data_size());

    // For each CountMetricData, check StateValue info is correct and buckets
    // have correct counts.
    auto data = countMetrics.data(0);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_UNKNOWN,
              data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_OFF, data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());
    EXPECT_EQ(2, data.bucket_info(1).count());

    data = countMetrics.data(2);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::view::DisplayStateEnum::DISPLAY_STATE_ON, data.slice_by_state(0).value());
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());
    EXPECT_EQ(1, data.bucket_info(1).count());
}

/**
 * Test a count metric that has one slice_by_state with a mapping and no
 * primary fields.
 *
 * Once the CountMetricProducer is initialized, it has one atom id in
 * mSlicedStateAtoms and has one entry per state value in mStateGroupMap.
 *
 * One StateTracker tracks the state atom, and it has one listener which is the
 * CountMetricProducer that was initialized.
 */
TEST(CountMetricE2eTest, TestSlicedStateWithMap) {
    // Initialize config.
    StatsdConfig config;

    auto syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;

    int64_t screenOnId = 4444;
    int64_t screenOffId = 9876;
    auto state = CreateScreenStateWithOnOffMap(screenOnId, screenOffId);
    *config.add_state() = state;

    // Create count metric that slices by screen state with on/off map.
    int64_t metricId = 123456;
    auto countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(syncStartMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    countMetric->add_slice_by_state(state.id());

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    auto processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    // Check that StateTrackers were initialized correctly.
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(SCREEN_STATE_ATOM_ID));

    // Check that CountMetricProducer was initialized correctly.
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    sp<MetricsManager> metricsManager = processor->mMetricsManagers.begin()->second;
    EXPECT_TRUE(metricsManager->isConfigValid());
    ASSERT_EQ(metricsManager->mAllMetricProducers.size(), 1);
    sp<MetricProducer> metricProducer = metricsManager->mAllMetricProducers[0];
    ASSERT_EQ(metricProducer->mSlicedStateAtoms.size(), 1);
    EXPECT_EQ(metricProducer->mSlicedStateAtoms.at(0), SCREEN_STATE_ATOM_ID);
    ASSERT_EQ(metricProducer->mStateGroupMap.size(), 1);

    StateMap map = state.map();
    for (auto group : map.group()) {
        for (auto value : group.value()) {
            EXPECT_EQ(metricProducer->mStateGroupMap.at(SCREEN_STATE_ATOM_ID).at(value),
                      group.group_id());
        }
    }

    /*
               bucket #1                      bucket #2
    |     1     2     3     4     5     6     7     8     9     10 (minutes)
    |-----------------------------|-----------------------------|--
      x   x     x       x    x   x      x         x         x       (syncStartEvents)
     -----------------------------------------------------------SCREEN_OFF events
             |                  |                                   (ScreenStateOffEvent = 1)
       |                  |                                         (ScreenStateDozeEvent = 3)
                                                |                   (ScreenStateDozeSuspendEvent =
    4)
     -----------------------------------------------------------SCREEN_ON events
                   |                                       |        (ScreenStateOnEvent = 2)
                      |                                             (ScreenStateVrEvent = 5)
                                            |                       (ScreenStateOnSuspendEvent = 6)
    */
    // Initialize log events - first bucket.
    std::vector<int> attributionUids1 = {123};
    std::vector<string> attributionTags1 = {"App1"};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 20 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 0:30
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 30 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_DOZE));  // 0:40
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 60 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 1:10
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 90 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 1:40
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 120 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 2:10
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 150 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 2:40
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 180 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_VR));  // 3:10
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 200 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 3:30
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 210 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_DOZE));  // 3:40
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 250 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 4:20
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 280 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 4:50
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 285 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 4:55

    // Initialize log events - second bucket.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 360 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 6:10
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 390 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON_SUSPEND));  // 6:40
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 430 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_DOZE_SUSPEND));  // 7:20
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 440 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 7:30
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 540 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 9:10
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 570 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // 9:40

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs * 2 + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(3, countMetrics.data_size());

    // For each CountMetricData, check StateValue info is correct and buckets
    // have correct counts.
    auto data = countMetrics.data(0);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */, data.slice_by_state(0).value());
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOnId, data.slice_by_state(0).group_id());
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());
    EXPECT_EQ(1, data.bucket_info(1).count());

    data = countMetrics.data(2);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOffId, data.slice_by_state(0).group_id());
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(4, data.bucket_info(0).count());
    EXPECT_EQ(2, data.bucket_info(1).count());
}

/**
* Test a count metric that has one slice_by_state with a primary field.

* Once the CountMetricProducer is initialized, it should have one
* MetricStateLink stored. State querying using a non-empty primary key
* should also work as intended.
*/
TEST(CountMetricE2eTest, TestSlicedStateWithPrimaryFields) {
    // Initialize config.
    StatsdConfig config;

    auto appCrashMatcher =
            CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);
    *config.add_atom_matcher() = appCrashMatcher;

    auto state = CreateUidProcessState();
    *config.add_state() = state;

    // Create count metric that slices by uid process state.
    int64_t metricId = 123456;
    auto countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(appCrashMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    countMetric->add_slice_by_state(state.id());
    *countMetric->mutable_dimensions_in_what() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    MetricStateLink* stateLink = countMetric->add_state_link();
    stateLink->set_state_atom_id(UID_PROCESS_STATE_ATOM_ID);
    auto fieldsInWhat = stateLink->mutable_fields_in_what();
    *fieldsInWhat = CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    auto fieldsInState = stateLink->mutable_fields_in_state();
    *fieldsInState = CreateDimensions(UID_PROCESS_STATE_ATOM_ID, {1 /*uid*/});

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    auto processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    // Check that StateTrackers were initialized correctly.
    EXPECT_EQ(1, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(UID_PROCESS_STATE_ATOM_ID));

    // Check that CountMetricProducer was initialized correctly.
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    sp<MetricsManager> metricsManager = processor->mMetricsManagers.begin()->second;
    EXPECT_TRUE(metricsManager->isConfigValid());
    ASSERT_EQ(metricsManager->mAllMetricProducers.size(), 1);
    sp<MetricProducer> metricProducer = metricsManager->mAllMetricProducers[0];
    ASSERT_EQ(metricProducer->mSlicedStateAtoms.size(), 1);
    EXPECT_EQ(metricProducer->mSlicedStateAtoms.at(0), UID_PROCESS_STATE_ATOM_ID);
    ASSERT_EQ(metricProducer->mStateGroupMap.size(), 0);
    ASSERT_EQ(metricProducer->mMetric2StateLinks.size(), 1);

    /*
    NOTE: "1" or "2" represents the uid associated with the state/app crash event
               bucket #1               bucket #2
    |    1    2    3    4    5    6    7    8    9    10
    |------------------------|-------------------------|--
      1  1    1      1   1  2     1        1        2    (AppCrashEvents)
     -----------------------------------------------------PROCESS STATE events
           1               2                             (TopEvent = 1002)
                       1             1                   (ForegroundServiceEvent = 1003)
                                         2               (ImportantBackgroundEvent = 1006)
       1          1                               1      (ImportantForegroundEvent = 1005)

    Based on the diagram above, an AppCrashEvent querying for process state value would return:
    - StateTracker::kStateUnknown
    - Important foreground
    - Top
    - Important foreground
    - Foreground service
    - Top (both the app crash and state still have matching uid = 2)

    - Foreground service
    - Foreground service
    - Important background
    */
    // Initialize log events - first bucket.
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 20 * NS_PER_SEC, 1 /*uid*/));  // 0:30
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 30 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));  // 0:40
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 60 * NS_PER_SEC, 1 /*uid*/));  // 1:10
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 90 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_TOP));  // 1:40
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 120 * NS_PER_SEC, 1 /*uid*/));  // 2:10
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 150 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));  // 2:40
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 200 * NS_PER_SEC, 1 /*uid*/));  // 3:30
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 210 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_FOREGROUND_SERVICE));  // 3:40
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 250 * NS_PER_SEC, 1 /*uid*/));  // 4:20
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 280 * NS_PER_SEC, 2 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_TOP));  // 4:50
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 285 * NS_PER_SEC, 2 /*uid*/));  // 4:55

    // Initialize log events - second bucket.
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 360 * NS_PER_SEC, 1 /*uid*/));  // 6:10
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 390 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_FOREGROUND_SERVICE));  // 6:40
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 430 * NS_PER_SEC, 2 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND));  // 7:20
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 440 * NS_PER_SEC, 1 /*uid*/));  // 7:30
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 540 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));  // 9:10
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 570 * NS_PER_SEC, 2 /*uid*/));  // 9:40

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs * 2 + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(6, countMetrics.data_size());

    // For each CountMetricData, check StateValue info is correct and buckets
    // have correct counts.
    auto data = countMetrics.data(0);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(-1 /* StateTracker::kStateUnknown */, data.slice_by_state(0).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_TOP, data.slice_by_state(0).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(2);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_FOREGROUND_SERVICE, data.slice_by_state(0).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());
    EXPECT_EQ(2, data.bucket_info(1).count());

    data = countMetrics.data(3);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_IMPORTANT_FOREGROUND, data.slice_by_state(0).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(2, data.bucket_info(0).count());

    data = countMetrics.data(4);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_TOP, data.slice_by_state(0).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 2);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(5);
    ASSERT_EQ(1, data.slice_by_state_size());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_IMPORTANT_BACKGROUND, data.slice_by_state(0).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 2);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());
}

TEST(CountMetricE2eTest, TestMultipleSlicedStates) {
    // Initialize config.
    StatsdConfig config;

    auto appCrashMatcher =
            CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);
    *config.add_atom_matcher() = appCrashMatcher;

    int64_t screenOnId = 4444;
    int64_t screenOffId = 9876;
    auto state1 = CreateScreenStateWithOnOffMap(screenOnId, screenOffId);
    *config.add_state() = state1;
    auto state2 = CreateUidProcessState();
    *config.add_state() = state2;

    // Create count metric that slices by screen state with on/off map and
    // slices by uid process state.
    int64_t metricId = 123456;
    auto countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(appCrashMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    countMetric->add_slice_by_state(state1.id());
    countMetric->add_slice_by_state(state2.id());
    *countMetric->mutable_dimensions_in_what() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    MetricStateLink* stateLink = countMetric->add_state_link();
    stateLink->set_state_atom_id(UID_PROCESS_STATE_ATOM_ID);
    auto fieldsInWhat = stateLink->mutable_fields_in_what();
    *fieldsInWhat = CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    auto fieldsInState = stateLink->mutable_fields_in_state();
    *fieldsInState = CreateDimensions(UID_PROCESS_STATE_ATOM_ID, {1 /*uid*/});

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    auto processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    // Check that StateTrackers were properly initialized.
    EXPECT_EQ(2, StateManager::getInstance().getStateTrackersCount());
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(SCREEN_STATE_ATOM_ID));
    EXPECT_EQ(1, StateManager::getInstance().getListenersCount(UID_PROCESS_STATE_ATOM_ID));

    // Check that CountMetricProducer was initialized correctly.
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    sp<MetricsManager> metricsManager = processor->mMetricsManagers.begin()->second;
    EXPECT_TRUE(metricsManager->isConfigValid());
    ASSERT_EQ(metricsManager->mAllMetricProducers.size(), 1);
    sp<MetricProducer> metricProducer = metricsManager->mAllMetricProducers[0];
    ASSERT_EQ(metricProducer->mSlicedStateAtoms.size(), 2);
    EXPECT_EQ(metricProducer->mSlicedStateAtoms.at(0), SCREEN_STATE_ATOM_ID);
    EXPECT_EQ(metricProducer->mSlicedStateAtoms.at(1), UID_PROCESS_STATE_ATOM_ID);
    ASSERT_EQ(metricProducer->mStateGroupMap.size(), 1);
    ASSERT_EQ(metricProducer->mMetric2StateLinks.size(), 1);

    StateMap map = state1.map();
    for (auto group : map.group()) {
        for (auto value : group.value()) {
            EXPECT_EQ(metricProducer->mStateGroupMap.at(SCREEN_STATE_ATOM_ID).at(value),
                      group.group_id());
        }
    }

    /*
                 bucket #1                      bucket #2
      |    1    2    3    4    5    6    7    8    9    10 (minutes)
      |------------------------|------------------------|--
        1  1    1     1    1  2     1        1         2   (AppCrashEvents)
       ---------------------------------------------------SCREEN_OFF events
             |                              |              (ScreenOffEvent = 1)
         |              |                                  (ScreenDozeEvent = 3)
       ---------------------------------------------------SCREEN_ON events
                   |                              |        (ScreenOnEvent = 2)
                                        |                  (ScreenOnSuspendEvent = 6)
       ---------------------------------------------------PROCESS STATE events
             1               2                             (TopEvent = 1002)
                                      1                    (ForegroundServiceEvent = 1003)
                                            2              (ImportantBackgroundEvent = 1006)
       1          1                                   1    (ImportantForegroundEvent = 1005)

       Based on the diagram above, Screen State / Process State pairs for each
       AppCrashEvent are:
       - StateTracker::kStateUnknown / important foreground
       - off / important foreground
       - off / Top
       - on / important foreground
       - off / important foreground
       - off / top

       - off / important foreground
       - off / foreground service
       - on / important background

      */
    // Initialize log events - first bucket.
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 5 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));  // 0:15
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 20 * NS_PER_SEC, 1 /*uid*/));  // 0:30
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 30 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_DOZE));  // 0:40
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 60 * NS_PER_SEC, 1 /*uid*/));  // 1:10
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 90 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_TOP));  // 1:40
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 90 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 1:40
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 120 * NS_PER_SEC, 1 /*uid*/));  // 2:10
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 150 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));  // 2:40
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 160 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 2:50
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 200 * NS_PER_SEC, 1 /*uid*/));  // 3:30
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 210 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_DOZE));  // 3:40
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 250 * NS_PER_SEC, 1 /*uid*/));  // 4:20
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 280 * NS_PER_SEC, 2 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_TOP));  // 4:50
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 285 * NS_PER_SEC, 2 /*uid*/));  // 4:55

    // Initialize log events - second bucket.
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 360 * NS_PER_SEC, 1 /*uid*/));  // 6:10
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 380 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_FOREGROUND_SERVICE));  // 6:30
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 390 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON_SUSPEND));  // 6:40
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 420 * NS_PER_SEC, 2 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND));  // 7:10
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 440 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // 7:30
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 450 * NS_PER_SEC, 1 /*uid*/));  // 7:40
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 520 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // 8:50
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 540 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));  // 9:10
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 570 * NS_PER_SEC, 2 /*uid*/));  // 9:40

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs * 2 + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(7, countMetrics.data_size());

    // For each CountMetricData, check StateValue info is correct and buckets
    // have correct counts.
    auto data = countMetrics.data(0);
    ASSERT_EQ(2, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_value());
    EXPECT_EQ(-1, data.slice_by_state(0).value());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(1).atom_id());
    EXPECT_TRUE(data.slice_by_state(1).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_IMPORTANT_FOREGROUND, data.slice_by_state(1).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(1);
    ASSERT_EQ(2, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOnId, data.slice_by_state(0).group_id());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(1).atom_id());
    EXPECT_TRUE(data.slice_by_state(1).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_IMPORTANT_FOREGROUND, data.slice_by_state(1).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(2);
    ASSERT_EQ(2, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOffId, data.slice_by_state(0).group_id());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(1).atom_id());
    EXPECT_TRUE(data.slice_by_state(1).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_TOP, data.slice_by_state(1).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(3);
    ASSERT_EQ(2, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOffId, data.slice_by_state(0).group_id());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(1).atom_id());
    EXPECT_TRUE(data.slice_by_state(1).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_FOREGROUND_SERVICE, data.slice_by_state(1).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(4);
    ASSERT_EQ(2, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOffId, data.slice_by_state(0).group_id());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(1).atom_id());
    EXPECT_TRUE(data.slice_by_state(1).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_IMPORTANT_FOREGROUND, data.slice_by_state(1).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 1);
    ASSERT_EQ(2, data.bucket_info_size());
    EXPECT_EQ(2, data.bucket_info(0).count());
    EXPECT_EQ(1, data.bucket_info(1).count());

    data = countMetrics.data(5);
    ASSERT_EQ(2, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOnId, data.slice_by_state(0).group_id());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(1).atom_id());
    EXPECT_TRUE(data.slice_by_state(1).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_IMPORTANT_BACKGROUND, data.slice_by_state(1).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 2);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(6);
    ASSERT_EQ(2, data.slice_by_state_size());
    EXPECT_EQ(SCREEN_STATE_ATOM_ID, data.slice_by_state(0).atom_id());
    EXPECT_TRUE(data.slice_by_state(0).has_group_id());
    EXPECT_EQ(screenOffId, data.slice_by_state(0).group_id());
    EXPECT_EQ(UID_PROCESS_STATE_ATOM_ID, data.slice_by_state(1).atom_id());
    EXPECT_TRUE(data.slice_by_state(1).has_value());
    EXPECT_EQ(android::app::PROCESS_STATE_TOP, data.slice_by_state(1).value());
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, 2);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());
}

TEST(CountMetricE2eTest, TestUploadThreshold) {
    // Initialize config.
    StatsdConfig config;

    auto appCrashMatcher = CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);
    *config.add_atom_matcher() = appCrashMatcher;

    int64_t thresholdCount = 2;
    UploadThreshold threshold;
    threshold.set_gt_int(thresholdCount);

    int64_t metricId = 123456;
    CountMetric countMetric = createCountMetric("COUNT", appCrashMatcher.id(), nullopt, {});
    *countMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    *countMetric.mutable_threshold() = threshold;
    *config.add_count_metric() = countMetric;

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    auto processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    int appUid1 = 1;
    int appUid2 = 2;
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 20 * NS_PER_SEC, appUid1));  // 0:30
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 40 * NS_PER_SEC, appUid2));  // 0:50
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 60 * NS_PER_SEC, appUid1));  // 1:10
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 65 * NS_PER_SEC, appUid1));  // 1:15

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(1, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);

    // Uid 1 reports a count greater than the threshold.
    // Uid 2 is dropped because the count was less than the threshold.
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, appUid1);
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        3);
}

TEST(CountMetricE2eTest, TestRepeatedFieldsAndEmptyArrays) {
    StatsdConfig config;

    AtomMatcher testAtomReportedAtomMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedAtomMatcher;

    int64_t metricId = 123456;
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(testAtomReportedAtomMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);

    // Initialize StatsLogProcessor.
    ConfigKey cfgKey(123, 987);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    vector<int> intArray = {3, 6};
    vector<int64_t> longArray = {1000L, 10002L};
    vector<float> floatArray = {0.3f, 0.09f};
    vector<string> stringArray = {"str1", "str2"};
    int boolArrayLength = 2;
    bool boolArray[boolArrayLength];
    boolArray[0] = 1;
    boolArray[1] = 0;
    vector<int> enumArray = {TestAtomReported::ON, TestAtomReported::OFF};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 10 * NS_PER_SEC, intArray, longArray, floatArray, stringArray,
            boolArray, boolArrayLength, enumArray));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, {}));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs * 2 + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(1, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);
}

TEST(CountMetricE2eTest, TestMatchRepeatedFieldPositionAny) {
    StatsdConfig config;

    AtomMatcher testAtomReportedStateAnyOnAtomMatcher =
            CreateTestAtomRepeatedStateAnyOnAtomMatcher();
    *config.add_atom_matcher() = testAtomReportedStateAnyOnAtomMatcher;

    int64_t metricId = 123456;
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(testAtomReportedStateAnyOnAtomMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);

    // Initialize StatsLogProcessor.
    ConfigKey cfgKey(123, 987);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    vector<int> enumArrayOnFirst = {TestAtomReported::ON, TestAtomReported::OFF};
    vector<int> enumArrayOnLast = {TestAtomReported::OFF, TestAtomReported::ON};
    vector<int> enumArrayNoOn = {TestAtomReported::OFF, TestAtomReported::OFF};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOnFirst));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 40 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayNoOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 60 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOnLast));
    // No matching is done on empty array.
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 80 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, {}));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs * 2 + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(1, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);
}

TEST(CountMetricE2eTest, TestRepeatedFieldDimension_PositionFirst) {
    StatsdConfig config;

    AtomMatcher testAtomReportedAtomMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedAtomMatcher;

    int64_t metricId = 123456;
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(testAtomReportedAtomMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    *countMetric->mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {14 /*repeated_enum_field*/}, {Position::FIRST});

    // Initialize StatsLogProcessor.
    ConfigKey cfgKey(2000, 921);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    vector<int> enumArrayOnOff = {TestAtomReported::ON, TestAtomReported::OFF};
    vector<int> enumArrayOnOn = {TestAtomReported::ON, TestAtomReported::ON};
    vector<int> enumArrayOffOn = {TestAtomReported::OFF, TestAtomReported::ON};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOnOff));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 40 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOffOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 60 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOnOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 80 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, {}));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(3, countMetrics.data_size());

    // Empty dimensions case.
    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 0);

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::OFF);

    data = countMetrics.data(2);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::ON);
}

TEST(CountMetricE2eTest, TestRepeatedFieldDimension_PositionLast) {
    StatsdConfig config;

    AtomMatcher testAtomReportedAtomMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedAtomMatcher;

    int64_t metricId = 123456;
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(testAtomReportedAtomMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    *countMetric->mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {14 /*repeated_enum_field*/}, {Position::LAST});

    // Initialize StatsLogProcessor.
    ConfigKey cfgKey(2000, 921);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    vector<int> enumArrayOnOff = {TestAtomReported::ON, TestAtomReported::OFF};
    vector<int> enumArrayOffOff = {TestAtomReported::OFF, TestAtomReported::OFF};
    vector<int> enumArrayOffOn = {TestAtomReported::OFF, TestAtomReported::ON};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOnOff));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 40 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOffOff));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 60 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOffOn));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(2, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::OFF);

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::ON);
}

TEST(CountMetricE2eTest, TestRepeatedFieldDimension_PositionAll) {
    StatsdConfig config;

    AtomMatcher testAtomReportedAtomMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedAtomMatcher;

    int64_t metricId = 123456;
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(testAtomReportedAtomMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    *countMetric->mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {14 /*repeated_enum_field*/}, {Position::ALL});

    // Initialize StatsLogProcessor.
    ConfigKey cfgKey(2000, 921);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    vector<int> enumArrayOnOff = {TestAtomReported::ON, TestAtomReported::OFF};
    vector<int> enumArrayOnOn = {TestAtomReported::ON, TestAtomReported::ON};
    vector<int> enumArrayOffOn = {TestAtomReported::OFF, TestAtomReported::ON};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOnOff));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 40 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOffOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 60 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOnOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 40 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOffOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOnOff));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 40 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOffOn));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    // Don't need to backfill dimension path because dimensions with position ALL are not encoded
    // with the path format.
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(3, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        3);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::OFF);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(),
              TestAtomReported::ON);

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::ON);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(),
              TestAtomReported::OFF);

    data = countMetrics.data(2);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::ON);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(),
              TestAtomReported::ON);
}

TEST(CountMetricE2eTest, TestMultipleRepeatedFieldDimensions_PositionFirst) {
    StatsdConfig config;

    AtomMatcher testAtomReportedAtomMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedAtomMatcher;

    int64_t metricId = 123456;
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(testAtomReportedAtomMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    *countMetric->mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/, 14 /*repeated_enum_field*/},
            {Position::FIRST, Position::FIRST});

    // Initialize StatsLogProcessor.
    ConfigKey cfgKey(2000, 921);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    vector<int> intArrayThree = {3, 6, 9};
    vector<int> intArraySix = {6, 9};
    vector<int> enumArrayOn = {TestAtomReported::ON, TestAtomReported::OFF};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, intArrayThree, {}, {}, {}, {}, 0, enumArrayOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 40 * NS_PER_SEC, intArraySix, {}, {}, {}, {}, 0, enumArrayOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 60 * NS_PER_SEC, intArrayThree, {}, {}, {}, {}, 0, enumArrayOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 80 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayOn));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 100 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, {}));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 120 * NS_PER_SEC, intArraySix, {}, {}, {}, {}, 0, {}));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(5, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 0);

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 6);

    data = countMetrics.data(2);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::ON);

    data = countMetrics.data(3);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 3);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(),
              TestAtomReported::ON);

    data = countMetrics.data(4);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 6);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(),
              TestAtomReported::ON);
}

TEST(CountMetricE2eTest, TestMultipleRepeatedFieldDimensions_PositionAll) {
    StatsdConfig config;

    AtomMatcher testAtomReportedAtomMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedAtomMatcher;

    int64_t metricId = 123456;
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(testAtomReportedAtomMatcher.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    *countMetric->mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/, 14 /*repeated_enum_field*/},
            {Position::ALL, Position::ALL});

    // Initialize StatsLogProcessor.
    ConfigKey cfgKey(2000, 921);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    vector<int> intArray1 = {3, 6};
    vector<int> intArray2 = {6, 9};
    vector<int> enumArray = {TestAtomReported::ON, TestAtomReported::OFF};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, intArray1, {}, {}, {}, {}, 0, enumArray));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 40 * NS_PER_SEC, intArray2, {}, {}, {}, {}, 0, enumArray));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 80 * NS_PER_SEC, intArray1, {}, {}, {}, {}, 0, enumArray));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 100 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArray));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 120 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, {}));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 140 * NS_PER_SEC, intArray2, {}, {}, {}, {}, 0, {}));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(5, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 0);

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 6);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(), 9);

    data = countMetrics.data(2);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(),
              TestAtomReported::ON);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(),
              TestAtomReported::OFF);

    data = countMetrics.data(3);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 4);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 3);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(), 6);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(2).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(2).value_int(),
              TestAtomReported::ON);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(3).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(3).value_int(),
              TestAtomReported::OFF);

    data = countMetrics.data(4);
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    EXPECT_EQ(util::TEST_ATOM_REPORTED, data.dimensions_in_what().field());
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 4);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_int(), 6);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).field(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(1).value_int(), 9);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(2).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(2).value_int(),
              TestAtomReported::ON);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(3).field(), 14);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(3).value_int(),
              TestAtomReported::OFF);
}

TEST(CountMetricE2eTest, TestConditionSlicedByRepeatedUidWithUidDimension) {
    StatsdConfig config;

    AtomMatcher uidProcessStateChangedAtomMatcher = CreateUidProcessStateChangedAtomMatcher();
    AtomMatcher repeatedStateFirstOffAtomMatcher = CreateTestAtomRepeatedStateFirstOffAtomMatcher();
    AtomMatcher repeatedStateFirstOnAtomMatcher = CreateTestAtomRepeatedStateFirstOnAtomMatcher();
    *config.add_atom_matcher() = uidProcessStateChangedAtomMatcher;
    *config.add_atom_matcher() = repeatedStateFirstOffAtomMatcher;
    *config.add_atom_matcher() = repeatedStateFirstOnAtomMatcher;

    Predicate testAtomRepeatedStateFirstOffPerUidPredicate =
            CreateTestAtomRepeatedStateFirstOffPredicate();
    FieldMatcher* dimensions =
            testAtomRepeatedStateFirstOffPerUidPredicate.mutable_simple_predicate()
                    ->mutable_dimensions();
    *dimensions = CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /* repeated uid*/},
                                           {Position::FIRST});
    *config.add_predicate() = testAtomRepeatedStateFirstOffPerUidPredicate;

    int64_t metricId = 123456;
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(metricId);
    countMetric->set_what(uidProcessStateChangedAtomMatcher.id());
    countMetric->set_condition(testAtomRepeatedStateFirstOffPerUidPredicate.id());
    countMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    *countMetric->mutable_dimensions_in_what() =
            CreateDimensions(util::UID_PROCESS_STATE_CHANGED, {1 /*uid*/});
    MetricConditionLink* links = countMetric->add_links();
    links->set_condition(testAtomRepeatedStateFirstOffPerUidPredicate.id());
    *links->mutable_fields_in_what() =
            CreateDimensions(util::UID_PROCESS_STATE_CHANGED, {1 /* uid*/});
    *links->mutable_fields_in_condition() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /* repeated uid*/}, {Position::FIRST});

    // Initialize StatsLogProcessor.
    ConfigKey cfgKey(2000, 921);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    const uint64_t bucket2StartTimeNs = bucketStartTimeNs + bucketSizeNs;
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    vector<int> intArray1 = {1, 2};
    vector<int> intArray2 = {2, 1};
    vector<int> enumArrayOn = {TestAtomReported::ON, TestAtomReported::OFF};
    vector<int> enumArrayOff = {TestAtomReported::OFF, TestAtomReported::ON};

    std::vector<std::unique_ptr<LogEvent>> events;
    // Set condition to true for uid 1.
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, intArray1, {}, {}, {}, {}, 0, enumArrayOff));

    // Uid 1 process state changed.
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 40 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));
    // Uid 2 process state changed. Should not be counted.
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 60 * NS_PER_SEC, 2 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));

    // Set condition to true for uid 2.
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 80 * NS_PER_SEC, intArray2, {}, {}, {}, {}, 0, enumArrayOff));
    // Uid 1 process state changed.
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 100 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));
    // Uid 2 process state changed.
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 120 * NS_PER_SEC, 2 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));

    // Bucket 2
    // Set condition to false for uid 1.
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucket2StartTimeNs + 20 * NS_PER_SEC, intArray1, {}, {}, {}, {}, 0, enumArrayOn));
    // Uid 1 process state changed. Should not be counted.
    events.push_back(CreateUidProcessStateChangedEvent(
            bucket2StartTimeNs + 40 * NS_PER_SEC, 1 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));
    // Uid 2 process state changed.
    events.push_back(CreateUidProcessStateChangedEvent(
            bucket2StartTimeNs + 60 * NS_PER_SEC, 2 /*uid*/,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucket2StartTimeNs + bucketSizeNs + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(2, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value(0).value_int());
    ASSERT_EQ(1, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);

    data = countMetrics.data(1);
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_EQ(2, data.dimensions_in_what().value_tuple().dimensions_value(0).value_int());
    ASSERT_EQ(2, data.bucket_info_size());
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        1);
    ValidateCountBucket(data.bucket_info(1), bucket2StartTimeNs, bucket2StartTimeNs + bucketSizeNs,
                        1);
}

TEST(CountMetricE2eTest, TestDimensionalSampling) {
    ShardOffsetProvider::getInstance().setShardOffset(5);

    // Initialize config.
    StatsdConfig config;

    AtomMatcher appCrashMatcher =
            CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);
    *config.add_atom_matcher() = appCrashMatcher;

    CountMetric sampledCountMetric =
            createCountMetric("CountSampledAppCrashesPerUid", appCrashMatcher.id(), nullopt, {});
    *sampledCountMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    *sampledCountMetric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    sampledCountMetric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = sampledCountMetric;

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);

    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(
            bucketStartTimeNs, bucketStartTimeNs, config, cfgKey, nullptr, 0, new UidMap());

    int appUid1 = 1001;  // odd hash value
    int appUid2 = 1002;  // even hash value
    int appUid3 = 1003;  // odd hash value
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 20 * NS_PER_SEC, appUid1));  // 0:30
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 40 * NS_PER_SEC, appUid2));  // 0:50
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 60 * NS_PER_SEC, appUid3));  // 1:10
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 80 * NS_PER_SEC, appUid1));  // 1:20
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 90 * NS_PER_SEC, appUid2));  // 1:30
    events.push_back(
            CreateAppCrashOccurredEvent(bucketStartTimeNs + 100 * NS_PER_SEC, appUid3));  // 1:40

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Check dump report.
    vector<uint8_t> buffer;
    ConfigMetricsReportList reports;
    processor->onDumpReport(cfgKey, bucketStartTimeNs + bucketSizeNs + 1, false, true, ADB_DUMP,
                            FAST, &buffer);
    ASSERT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(2, countMetrics.data_size());

    // Only Uid 1 and 3 are logged. (odd hash value) + (offset of 5) % (shard count of 2) = 0
    CountMetricData data = countMetrics.data(0);
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, appUid1);
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);

    data = countMetrics.data(1);
    ValidateUidDimension(data.dimensions_in_what(), util::APP_CRASH_OCCURRED, appUid3);
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs,
                        2);
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
