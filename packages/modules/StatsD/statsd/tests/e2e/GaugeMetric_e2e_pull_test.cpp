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

#include <android/binder_interface_utils.h>
#include <gtest/gtest.h>

#include <vector>

#include "src/StatsLogProcessor.h"
#include "src/stats_log_util.h"
#include "tests/statsd_test_util.h"

using ::ndk::SharedRefBase;

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

namespace {

const int64_t metricId = 123456;
const int32_t ATOM_TAG = util::SUBSYSTEM_SLEEP_STATE;

StatsdConfig CreateStatsdConfig(const GaugeMetric::SamplingType sampling_type,
                                bool useCondition = true) {
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.
    auto atomMatcher = CreateSimpleAtomMatcher("TestMatcher", ATOM_TAG);
    *config.add_atom_matcher() = atomMatcher;
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();

    auto screenIsOffPredicate = CreateScreenIsOffPredicate();
    *config.add_predicate() = screenIsOffPredicate;

    auto gaugeMetric = config.add_gauge_metric();
    gaugeMetric->set_id(metricId);
    gaugeMetric->set_what(atomMatcher.id());
    if (useCondition) {
        gaugeMetric->set_condition(screenIsOffPredicate.id());
    }
    gaugeMetric->set_sampling_type(sampling_type);
    gaugeMetric->mutable_gauge_fields_filter()->set_include_all(true);
    *gaugeMetric->mutable_dimensions_in_what() =
            CreateDimensions(ATOM_TAG, {1 /* subsystem name */});
    gaugeMetric->set_bucket(FIVE_MINUTES);
    gaugeMetric->set_max_pull_delay_sec(INT_MAX);
    config.set_hash_strings_in_metric_report(false);
    gaugeMetric->set_split_bucket_for_app_upgrade(true);
    gaugeMetric->set_min_bucket_size_nanos(1000);

    return config;
}

}  // namespaces

TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEvents) {
    auto config = CreateStatsdConfig(GaugeMetric::RANDOM_ONE_SAMPLE);
    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    ConfigKey cfgKey;
    auto processor =
            CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                    SharedRefBase::make<FakeSubsystemSleepCallback>(), ATOM_TAG);
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    EXPECT_TRUE(processor->mMetricsManagers.begin()->second->isConfigValid());
    processor->mPullerManager->ForceClearPullerCache();

    int startBucketNum = processor->mMetricsManagers.begin()
                                 ->second->mAllMetricProducers[0]
                                 ->getCurrentBucketNum();
    EXPECT_GT(startBucketNum, (int64_t)0);

    // When creating the config, the gauge metric producer should register the alarm at the
    // end of the current bucket.
    ASSERT_EQ((size_t)1, processor->mPullerManager->mReceivers.size());
    EXPECT_EQ(bucketSizeNs,
              processor->mPullerManager->mReceivers.begin()->second.front().intervalNs);
    int64_t& nextPullTimeNs =
            processor->mPullerManager->mReceivers.begin()->second.front().nextPullTimeNs;
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + bucketSizeNs, nextPullTimeNs);

    auto screenOffEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 55, android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    // Pulling alarm arrives on time and reset the sequential pulling alarm.
    processor->informPullAlarmFired(nextPullTimeNs + 1);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 2 * bucketSizeNs, nextPullTimeNs);

    auto screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 10,
                                                       android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 100,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    processor->informPullAlarmFired(nextPullTimeNs + 1);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 3 * bucketSizeNs, nextPullTimeNs);

    processor->informPullAlarmFired(nextPullTimeNs + 1);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 4 * bucketSizeNs, nextPullTimeNs);

    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 3 * bucketSizeNs + 2,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    processor->informPullAlarmFired(nextPullTimeNs + 3);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 5 * bucketSizeNs, nextPullTimeNs);

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 5 * bucketSizeNs + 1,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    processor->informPullAlarmFired(nextPullTimeNs + 2);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 6 * bucketSizeNs, nextPullTimeNs);

    processor->informPullAlarmFired(nextPullTimeNs + 2);

    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, configAddedTimeNs + 7 * bucketSizeNs + 10, false, true,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_GT((int)gaugeMetrics.data_size(), 1);

    auto data = gaugeMetrics.data(0);
    EXPECT_EQ(ATOM_TAG, data.dimensions_in_what().field());
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1 /* subsystem name field */,
              data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_FALSE(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str().empty());
    ASSERT_EQ(6, data.bucket_info_size());

    ASSERT_EQ(1, data.bucket_info(0).atom_size());
    ASSERT_EQ(1, data.bucket_info(0).elapsed_timestamp_nanos_size());
    EXPECT_EQ(configAddedTimeNs + 55, data.bucket_info(0).elapsed_timestamp_nanos(0));
    ASSERT_EQ(0, data.bucket_info(0).wall_clock_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 2 * bucketSizeNs, data.bucket_info(0).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs, data.bucket_info(0).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(0).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(1).atom_size());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs + 1, data.bucket_info(1).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs + 1, data.bucket_info(1).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs, data.bucket_info(1).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs, data.bucket_info(1).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(1).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(1).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(2).atom_size());
    ASSERT_EQ(1, data.bucket_info(2).elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs + 1, data.bucket_info(2).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs, data.bucket_info(2).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 5 * bucketSizeNs, data.bucket_info(2).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(2).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(2).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(3).atom_size());
    ASSERT_EQ(1, data.bucket_info(3).elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 5 * bucketSizeNs + 1, data.bucket_info(3).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 5 * bucketSizeNs, data.bucket_info(3).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 6 * bucketSizeNs, data.bucket_info(3).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(3).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(3).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(4).atom_size());
    ASSERT_EQ(1, data.bucket_info(4).elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 7 * bucketSizeNs + 1, data.bucket_info(4).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 7 * bucketSizeNs, data.bucket_info(4).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 8 * bucketSizeNs, data.bucket_info(4).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(4).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(4).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(5).atom_size());
    ASSERT_EQ(1, data.bucket_info(5).elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 8 * bucketSizeNs + 2, data.bucket_info(5).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 8 * bucketSizeNs, data.bucket_info(5).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 9 * bucketSizeNs, data.bucket_info(5).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(5).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(5).atom(0).subsystem_sleep_state().time_millis(), 0);
}

TEST(GaugeMetricE2ePulledTest, TestFirstNSamplesPulledNoTrigger) {
    StatsdConfig config = CreateStatsdConfig(GaugeMetric::FIRST_N_SAMPLES);
    auto gaugeMetric = config.mutable_gauge_metric(0);
    gaugeMetric->set_max_num_gauge_atoms_per_bucket(3);
    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    ConfigKey cfgKey;
    auto processor =
            CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                    SharedRefBase::make<FakeSubsystemSleepCallback>(), ATOM_TAG);
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    EXPECT_TRUE(processor->mMetricsManagers.begin()->second->isConfigValid());
    processor->mPullerManager->ForceClearPullerCache();

    // When creating the config, the gauge metric producer should register the alarm at the
    // end of the current bucket.
    ASSERT_EQ((size_t)1, processor->mPullerManager->mReceivers.size());
    EXPECT_EQ(bucketSizeNs,
              processor->mPullerManager->mReceivers.begin()->second.front().intervalNs);
    int64_t& nextPullTimeNs =
            processor->mPullerManager->mReceivers.begin()->second.front().nextPullTimeNs;

    auto screenOffEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 55, android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    auto screenOnEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 100, android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 150,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    screenOnEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 200, android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 250,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    screenOnEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 300, android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    // Not logged. max_num_gauge_atoms_per_bucket already hit.
    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 325,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    // Pulling alarm arrives on time and reset the sequential pulling alarm.
    processor->informPullAlarmFired(nextPullTimeNs + 1);

    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 10,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 100,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    processor->informPullAlarmFired(nextPullTimeNs + 2);

    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + (3 * bucketSizeNs) + 15,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    processor->informPullAlarmFired(nextPullTimeNs + 4);

    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, configAddedTimeNs + (4 * bucketSizeNs) + 10, false, true,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_GT((int)gaugeMetrics.data_size(), 1);

    auto data = gaugeMetrics.data(1);
    EXPECT_EQ(ATOM_TAG, data.dimensions_in_what().field());
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1 /* subsystem name field */,
              data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_FALSE(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str().empty());
    ASSERT_EQ(3, data.bucket_info_size());

    ASSERT_EQ(3, data.bucket_info(0).atom_size());
    ASSERT_EQ(3, data.bucket_info(0).elapsed_timestamp_nanos_size());
    ValidateGaugeBucketTimes(data.bucket_info(0),
                             /*startTimeNs=*/configAddedTimeNs,
                             /*endTimeNs=*/configAddedTimeNs + bucketSizeNs,
                             /*eventTimesNs=*/
                             {(int64_t)(configAddedTimeNs + 55), (int64_t)(configAddedTimeNs + 150),
                              (int64_t)(configAddedTimeNs + 250)});

    ASSERT_EQ(2, data.bucket_info(1).atom_size());
    ASSERT_EQ(2, data.bucket_info(1).elapsed_timestamp_nanos_size());
    ValidateGaugeBucketTimes(data.bucket_info(1),
                             /*startTimeNs=*/configAddedTimeNs + bucketSizeNs,
                             /*endTimeNs=*/configAddedTimeNs + (2 * bucketSizeNs),
                             /*eventTimesNs=*/
                             {(int64_t)(configAddedTimeNs + bucketSizeNs + 1),
                              (int64_t)(configAddedTimeNs + bucketSizeNs + 100)});

    ASSERT_EQ(1, data.bucket_info(2).atom_size());
    ASSERT_EQ(1, data.bucket_info(2).elapsed_timestamp_nanos_size());
    ValidateGaugeBucketTimes(
            data.bucket_info(2), /*startTimeNs=*/configAddedTimeNs + (2 * bucketSizeNs),
            /*endTimeNs=*/configAddedTimeNs + (3 * bucketSizeNs),
            /*eventTimesNs=*/{(int64_t)(configAddedTimeNs + (2 * bucketSizeNs) + 2)});
}

TEST(GaugeMetricE2ePulledTest, TestConditionChangeToTrueSamplePulledEvents) {
    auto config = CreateStatsdConfig(GaugeMetric::CONDITION_CHANGE_TO_TRUE);
    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    ConfigKey cfgKey;
    auto processor =
            CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                    SharedRefBase::make<FakeSubsystemSleepCallback>(), ATOM_TAG);
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    EXPECT_TRUE(processor->mMetricsManagers.begin()->second->isConfigValid());
    processor->mPullerManager->ForceClearPullerCache();

    int startBucketNum = processor->mMetricsManagers.begin()
                                 ->second->mAllMetricProducers[0]
                                 ->getCurrentBucketNum();
    EXPECT_GT(startBucketNum, (int64_t)0);

    auto screenOffEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 55, android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    auto screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 10,
                                                       android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 100,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 3 * bucketSizeNs + 2,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 5 * bucketSizeNs + 1,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());
    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 5 * bucketSizeNs + 3,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());
    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 5 * bucketSizeNs + 10,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, configAddedTimeNs + 8 * bucketSizeNs + 10, false, true,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_GT((int)gaugeMetrics.data_size(), 1);

    auto data = gaugeMetrics.data(0);
    EXPECT_EQ(ATOM_TAG, data.dimensions_in_what().field());
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1 /* subsystem name field */,
              data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_FALSE(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str().empty());
    ASSERT_EQ(3, data.bucket_info_size());

    ASSERT_EQ(1, data.bucket_info(0).atom_size());
    ASSERT_EQ(1, data.bucket_info(0).elapsed_timestamp_nanos_size());
    EXPECT_EQ(configAddedTimeNs + 55, data.bucket_info(0).elapsed_timestamp_nanos(0));
    ASSERT_EQ(0, data.bucket_info(0).wall_clock_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 2 * bucketSizeNs, data.bucket_info(0).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs, data.bucket_info(0).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(0).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(1).atom_size());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs + 100, data.bucket_info(1).elapsed_timestamp_nanos(0));
    EXPECT_EQ(configAddedTimeNs + 55, data.bucket_info(0).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs, data.bucket_info(1).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs, data.bucket_info(1).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(1).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(1).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(2, data.bucket_info(2).atom_size());
    ASSERT_EQ(2, data.bucket_info(2).elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 7 * bucketSizeNs + 1, data.bucket_info(2).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 7 * bucketSizeNs + 10, data.bucket_info(2).elapsed_timestamp_nanos(1));
    EXPECT_EQ(baseTimeNs + 7 * bucketSizeNs, data.bucket_info(2).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 8 * bucketSizeNs, data.bucket_info(2).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(2).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(2).atom(0).subsystem_sleep_state().time_millis(), 0);
    EXPECT_TRUE(data.bucket_info(2).atom(1).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(2).atom(1).subsystem_sleep_state().time_millis(), 0);
}

TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEvent_LateAlarm) {
    auto config = CreateStatsdConfig(GaugeMetric::RANDOM_ONE_SAMPLE);
    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    ConfigKey cfgKey;
    auto processor =
            CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                    SharedRefBase::make<FakeSubsystemSleepCallback>(), ATOM_TAG);
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    EXPECT_TRUE(processor->mMetricsManagers.begin()->second->isConfigValid());
    processor->mPullerManager->ForceClearPullerCache();

    int startBucketNum = processor->mMetricsManagers.begin()
                                 ->second->mAllMetricProducers[0]
                                 ->getCurrentBucketNum();
    EXPECT_GT(startBucketNum, (int64_t)0);

    // When creating the config, the gauge metric producer should register the alarm at the
    // end of the current bucket.
    ASSERT_EQ((size_t)1, processor->mPullerManager->mReceivers.size());
    EXPECT_EQ(bucketSizeNs,
              processor->mPullerManager->mReceivers.begin()->second.front().intervalNs);
    int64_t& nextPullTimeNs =
            processor->mPullerManager->mReceivers.begin()->second.front().nextPullTimeNs;
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + bucketSizeNs, nextPullTimeNs);

    auto screenOffEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 55, android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    auto screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 10,
                                                       android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    // Pulling alarm arrives one bucket size late.
    processor->informPullAlarmFired(nextPullTimeNs + bucketSizeNs);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 3 * bucketSizeNs, nextPullTimeNs);

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 3 * bucketSizeNs + 11,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    // Pulling alarm arrives more than one bucket size late.
    processor->informPullAlarmFired(nextPullTimeNs + bucketSizeNs + 12);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 5 * bucketSizeNs, nextPullTimeNs);

    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, configAddedTimeNs + 7 * bucketSizeNs + 10, false, true,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_GT((int)gaugeMetrics.data_size(), 1);

    auto data = gaugeMetrics.data(0);
    EXPECT_EQ(ATOM_TAG, data.dimensions_in_what().field());
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1 /* subsystem name field */,
              data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_FALSE(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str().empty());
    ASSERT_EQ(3, data.bucket_info_size());

    ASSERT_EQ(1, data.bucket_info(0).atom_size());
    ASSERT_EQ(1, data.bucket_info(0).elapsed_timestamp_nanos_size());
    EXPECT_EQ(configAddedTimeNs + 55, data.bucket_info(0).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 2 * bucketSizeNs, data.bucket_info(0).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs, data.bucket_info(0).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(0).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(1).atom_size());
    EXPECT_EQ(configAddedTimeNs + 3 * bucketSizeNs + 11,
              data.bucket_info(1).elapsed_timestamp_nanos(0));
    EXPECT_EQ(configAddedTimeNs + 55, data.bucket_info(0).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 5 * bucketSizeNs, data.bucket_info(1).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 6 * bucketSizeNs, data.bucket_info(1).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(1).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(1).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(2).atom_size());
    ASSERT_EQ(1, data.bucket_info(2).elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 6 * bucketSizeNs + 12, data.bucket_info(2).elapsed_timestamp_nanos(0));
    EXPECT_EQ(baseTimeNs + 6 * bucketSizeNs, data.bucket_info(2).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 7 * bucketSizeNs, data.bucket_info(2).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(2).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(2).atom(0).subsystem_sleep_state().time_millis(), 0);
}

TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEventsWithActivation) {
    auto config = CreateStatsdConfig(GaugeMetric::RANDOM_ONE_SAMPLE, /*useCondition=*/false);

    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    auto batterySaverStartMatcher = CreateBatterySaverModeStartAtomMatcher();
    *config.add_atom_matcher() = batterySaverStartMatcher;
    const int64_t ttlNs = 2 * bucketSizeNs;  // Two buckets.
    auto metric_activation = config.add_metric_activation();
    metric_activation->set_metric_id(metricId);
    metric_activation->set_activation_type(ACTIVATE_IMMEDIATELY);
    auto event_activation = metric_activation->add_event_activation();
    event_activation->set_atom_matcher_id(batterySaverStartMatcher.id());
    event_activation->set_ttl_seconds(ttlNs / 1000000000);

    StatsdStats::getInstance().reset();

    ConfigKey cfgKey;
    auto processor =
            CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                    SharedRefBase::make<FakeSubsystemSleepCallback>(), ATOM_TAG);
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    EXPECT_TRUE(processor->mMetricsManagers.begin()->second->isConfigValid());
    processor->mPullerManager->ForceClearPullerCache();

    const int startBucketNum = processor->mMetricsManagers.begin()
                                       ->second->mAllMetricProducers[0]
                                       ->getCurrentBucketNum();
    EXPECT_EQ(startBucketNum, 2);
    EXPECT_FALSE(processor->mMetricsManagers.begin()->second->mAllMetricProducers[0]->isActive());

    // When creating the config, the gauge metric producer should register the alarm at the
    // end of the current bucket.
    ASSERT_EQ((size_t)1, processor->mPullerManager->mReceivers.size());
    EXPECT_EQ(bucketSizeNs,
              processor->mPullerManager->mReceivers.begin()->second.front().intervalNs);
    int64_t& nextPullTimeNs =
            processor->mPullerManager->mReceivers.begin()->second.front().nextPullTimeNs;
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + bucketSizeNs, nextPullTimeNs);

    // Check no pull occurred on metric initialization when it's not active.
    const int64_t metricInitTimeNs = configAddedTimeNs + 1;  // 10 mins + 1 ns.
    processor->onStatsdInitCompleted(metricInitTimeNs);
    StatsdStatsReport_PulledAtomStats pulledAtomStats =
            getPulledAtomStats(util::SUBSYSTEM_SLEEP_STATE);
    EXPECT_EQ(pulledAtomStats.atom_id(), ATOM_TAG);
    EXPECT_EQ(pulledAtomStats.total_pull(), 0);

    // Check no pull occurred on app upgrade when metric is not active.
    const int64_t appUpgradeTimeNs = metricInitTimeNs + 1;  // 10 mins + 2 ns.
    processor->notifyAppUpgrade(appUpgradeTimeNs, "appName", 1000 /* uid */, 2 /* version */);
    pulledAtomStats = getPulledAtomStats(util::SUBSYSTEM_SLEEP_STATE);
    EXPECT_EQ(pulledAtomStats.atom_id(), ATOM_TAG);
    EXPECT_EQ(pulledAtomStats.total_pull(), 0);

    // Check skipped bucket is not added when metric is not active.
    int64_t dumpReportTimeNs = appUpgradeTimeNs + 1;  // 10 mins + 3 ns.
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, dumpReportTimeNs, true /* include_current_partial_bucket */,
                            true /* erase_data */, ADB_DUMP, NO_TIME_CONSTRAINTS, &buffer);
    ConfigMetricsReportList reports;
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics =
            reports.reports(0).metrics(0).gauge_metrics();
    EXPECT_EQ(gaugeMetrics.skipped_size(), 0);

    // Pulling alarm arrives on time and reset the sequential pulling alarm.
    // Event should not be kept.
    processor->informPullAlarmFired(nextPullTimeNs + 1);  // 15 mins + 1 ns.
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 2 * bucketSizeNs, nextPullTimeNs);
    EXPECT_FALSE(processor->mMetricsManagers.begin()->second->mAllMetricProducers[0]->isActive());

    // Activate the metric. A pull occurs upon activation. The event is kept. 1 total
    // 15 mins + 2 ms
    const int64_t activationNs = configAddedTimeNs + bucketSizeNs + (2 * 1000 * 1000);  // 2 millis.
    auto batterySaverOnEvent = CreateBatterySaverOnEvent(activationNs);
    processor->OnLogEvent(batterySaverOnEvent.get());  // 15 mins + 2 ms.
    EXPECT_TRUE(processor->mMetricsManagers.begin()->second->mAllMetricProducers[0]->isActive());

    // This event should be kept. 2 total.
    processor->informPullAlarmFired(nextPullTimeNs + 1);  // 20 mins + 1 ns.
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 3 * bucketSizeNs, nextPullTimeNs);

    // This event should be kept. 3 total.
    processor->informPullAlarmFired(nextPullTimeNs + 2);  // 25 mins + 2 ns.
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 4 * bucketSizeNs, nextPullTimeNs);

    // Create random event to deactivate metric.
    // A pull should not occur here. 3 total.
    // 25 mins + 2 ms + 1 ns.
    const int64_t deactivationNs = activationNs + ttlNs + 1;
    auto deactivationEvent = CreateScreenBrightnessChangedEvent(deactivationNs, 50);
    processor->OnLogEvent(deactivationEvent.get());
    EXPECT_FALSE(processor->mMetricsManagers.begin()->second->mAllMetricProducers[0]->isActive());

    // Event should not be kept. 3 total.
    // 30 mins + 3 ns.
    processor->informPullAlarmFired(nextPullTimeNs + 3);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 5 * bucketSizeNs, nextPullTimeNs);

    // Event should not be kept. 3 total.
    // 35 mins + 2 ns.
    processor->informPullAlarmFired(nextPullTimeNs + 2);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 6 * bucketSizeNs, nextPullTimeNs);

    buffer.clear();
    // 40 mins + 10 ns.
    processor->onDumpReport(cfgKey, configAddedTimeNs + 6 * bucketSizeNs + 10,
                            false /* include_current_partial_bucket */, true /* erase_data */,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    gaugeMetrics = StatsLogReport::GaugeMetricDataWrapper();
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_GT((int)gaugeMetrics.data_size(), 0);

    auto data = gaugeMetrics.data(0);
    EXPECT_EQ(ATOM_TAG, data.dimensions_in_what().field());
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1 /* subsystem name field */,
              data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_FALSE(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str().empty());
    ASSERT_EQ(3, data.bucket_info_size());

    auto bucketInfo = data.bucket_info(0);
    ASSERT_EQ(1, bucketInfo.atom_size());
    ASSERT_EQ(1, bucketInfo.elapsed_timestamp_nanos_size());
    EXPECT_EQ(activationNs, bucketInfo.elapsed_timestamp_nanos(0));
    ASSERT_EQ(0, bucketInfo.wall_clock_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs, bucketInfo.start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs, bucketInfo.end_bucket_elapsed_nanos());
    EXPECT_TRUE(bucketInfo.atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(bucketInfo.atom(0).subsystem_sleep_state().time_millis(), 0);

    bucketInfo = data.bucket_info(1);
    ASSERT_EQ(1, bucketInfo.atom_size());
    ASSERT_EQ(1, bucketInfo.elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs + 1, bucketInfo.elapsed_timestamp_nanos(0));
    ASSERT_EQ(0, bucketInfo.wall_clock_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs, bucketInfo.start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 5 * bucketSizeNs, bucketInfo.end_bucket_elapsed_nanos());
    EXPECT_TRUE(bucketInfo.atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(bucketInfo.atom(0).subsystem_sleep_state().time_millis(), 0);

    bucketInfo = data.bucket_info(2);
    ASSERT_EQ(1, bucketInfo.atom_size());
    ASSERT_EQ(1, bucketInfo.elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 5 * bucketSizeNs + 2, bucketInfo.elapsed_timestamp_nanos(0));
    ASSERT_EQ(0, bucketInfo.wall_clock_timestamp_nanos_size());
    EXPECT_EQ(MillisToNano(NanoToMillis(baseTimeNs + 5 * bucketSizeNs)),
              bucketInfo.start_bucket_elapsed_nanos());
    EXPECT_EQ(MillisToNano(NanoToMillis(deactivationNs)), bucketInfo.end_bucket_elapsed_nanos());
    EXPECT_TRUE(bucketInfo.atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(bucketInfo.atom(0).subsystem_sleep_state().time_millis(), 0);

    // Check skipped bucket is not added after deactivation.
    dumpReportTimeNs = configAddedTimeNs + 8 * bucketSizeNs + 10;
    buffer.clear();
    processor->onDumpReport(cfgKey, dumpReportTimeNs, true /* include_current_partial_bucket */,
                            true /* erase_data */, ADB_DUMP, NO_TIME_CONSTRAINTS, &buffer);
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    gaugeMetrics = reports.reports(0).metrics(0).gauge_metrics();
    EXPECT_EQ(gaugeMetrics.skipped_size(), 0);
}

TEST(GaugeMetricE2ePulledTest, TestFirstNSamplesPulledNoTriggerWithActivation) {
    StatsdConfig config = CreateStatsdConfig(GaugeMetric::FIRST_N_SAMPLES);
    auto gaugeMetric = config.mutable_gauge_metric(0);
    gaugeMetric->set_max_num_gauge_atoms_per_bucket(2);
    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    auto batterySaverStartMatcher = CreateBatterySaverModeStartAtomMatcher();
    *config.add_atom_matcher() = batterySaverStartMatcher;
    const int64_t ttlNs = 2 * bucketSizeNs;  // Two buckets.
    auto metric_activation = config.add_metric_activation();
    metric_activation->set_metric_id(metricId);
    metric_activation->set_activation_type(ACTIVATE_IMMEDIATELY);
    auto event_activation = metric_activation->add_event_activation();
    event_activation->set_atom_matcher_id(batterySaverStartMatcher.id());
    event_activation->set_ttl_seconds(ttlNs / NS_PER_SEC);

    StatsdStats::getInstance().reset();

    ConfigKey cfgKey;
    auto processor =
            CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                    SharedRefBase::make<FakeSubsystemSleepCallback>(), ATOM_TAG);
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    processor->mPullerManager->ForceClearPullerCache();

    EXPECT_FALSE(processor->mMetricsManagers.begin()->second->mAllMetricProducers[0]->isActive());

    // When creating the config, the gauge metric producer should register the alarm at the
    // end of the current bucket.
    ASSERT_EQ((size_t)1, processor->mPullerManager->mReceivers.size());
    EXPECT_EQ(bucketSizeNs,
              processor->mPullerManager->mReceivers.begin()->second.front().intervalNs);
    int64_t& nextPullTimeNs =
            processor->mPullerManager->mReceivers.begin()->second.front().nextPullTimeNs;

    // Condition true but Active false
    auto screenOffEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 55, android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    auto screenOnEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 100, android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    // Pulling alarm arrives on time and reset the sequential pulling alarm.
    // Event should not be kept.
    processor->informPullAlarmFired(nextPullTimeNs + 1);  // 15 mins + 1 ns.
    EXPECT_FALSE(processor->mMetricsManagers.begin()->second->mAllMetricProducers[0]->isActive());

    // Activate the metric. A pull occurs upon activation. The event is not kept. 0 total
    // 15 mins + 1000 ns.
    const int64_t activationNs = configAddedTimeNs + bucketSizeNs + 1000;
    auto batterySaverOnEvent = CreateBatterySaverOnEvent(activationNs);
    processor->OnLogEvent(batterySaverOnEvent.get());  // 15 mins + 1000 ns.
    EXPECT_TRUE(processor->mMetricsManagers.begin()->second->mAllMetricProducers[0]->isActive());

    // A pull occurs upon condition change. The event is kept. 1 total. 1 in bucket
    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 150,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 200,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    // A pull occurs upon condition change. The event is kept. 1 total. 2 in bucket
    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 250,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 300,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    // A pull occurs upon condition change. The event is not kept due to
    // max_num_gauge_atoms_per_bucket. 1 total. 2 total in bucket
    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 325,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + bucketSizeNs + 375,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());
    // Condition false but Active true

    // This event should not be kept. 1 total.
    processor->informPullAlarmFired(nextPullTimeNs + 1);  // 20 mins + 1 ns.

    // This event should not be kept. 1 total.
    processor->informPullAlarmFired(nextPullTimeNs + 2);  // 25 mins + 2 ns.

    // A pull occurs upon condition change. The event is kept. 2 total. 1 in bucket
    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 3 * bucketSizeNs + 50,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());
    // Condition true but Active true

    // Create random event to deactivate metric.
    // A pull should not occur here. 2 total. 1 in bucket.
    // 25 mins + 1000 ns + 1 ns.
    const int64_t deactivationNs = activationNs + ttlNs + 1;
    auto deactivationEvent = CreateScreenBrightnessChangedEvent(deactivationNs, 50);
    processor->OnLogEvent(deactivationEvent.get());
    EXPECT_FALSE(processor->mMetricsManagers.begin()->second->mAllMetricProducers[0]->isActive());
    // Condition true but Active false

    screenOnEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 3 * bucketSizeNs + 50,
                                                  android::view::DISPLAY_STATE_ON);
    processor->OnLogEvent(screenOnEvent.get());

    screenOffEvent = CreateScreenStateChangedEvent(configAddedTimeNs + 3 * bucketSizeNs + 100,
                                                   android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    vector<uint8_t> buffer;
    // 30 mins + 10 ns.
    processor->onDumpReport(cfgKey, configAddedTimeNs + 4 * bucketSizeNs + 10,
                            false /* include_current_partial_bucket */, true /* erase_data */,
                            ADB_DUMP, FAST, &buffer);
    ConfigMetricsReportList reports;
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics = StatsLogReport::GaugeMetricDataWrapper();
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_GT((int)gaugeMetrics.data_size(), 0);

    auto data = gaugeMetrics.data(0);
    EXPECT_EQ(ATOM_TAG, data.dimensions_in_what().field());
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1 /* subsystem name field */,
              data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_FALSE(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str().empty());
    ASSERT_EQ(2, data.bucket_info_size());

    ASSERT_EQ(2, data.bucket_info(0).atom_size());
    ASSERT_EQ(2, data.bucket_info(0).elapsed_timestamp_nanos_size());
    ValidateGaugeBucketTimes(data.bucket_info(0),
                             /*startTimeNs=*/configAddedTimeNs + bucketSizeNs,
                             /*endTimeNs=*/configAddedTimeNs + (2 * bucketSizeNs),
                             /*eventTimesNs=*/
                             {(int64_t)(configAddedTimeNs + bucketSizeNs + 150),
                              (int64_t)(configAddedTimeNs + bucketSizeNs + 250)});

    ASSERT_EQ(1, data.bucket_info(1).atom_size());
    ASSERT_EQ(1, data.bucket_info(1).elapsed_timestamp_nanos_size());
    ValidateGaugeBucketTimes(data.bucket_info(1),
                             /*startTimeNs=*/
                             MillisToNano(NanoToMillis(configAddedTimeNs + (3 * bucketSizeNs))),
                             /*endTimeNs=*/MillisToNano(NanoToMillis(deactivationNs)),
                             /*eventTimesNs=*/
                             {(int64_t)(configAddedTimeNs + (3 * bucketSizeNs) + 50)});
}

TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEventsNoCondition) {
    auto config = CreateStatsdConfig(GaugeMetric::RANDOM_ONE_SAMPLE, /*useCondition=*/false);

    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs =
        TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    ConfigKey cfgKey;
    auto processor = CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                             SharedRefBase::make<FakeSubsystemSleepCallback>(),
                                             ATOM_TAG);
    ASSERT_EQ(processor->mMetricsManagers.size(), 1u);
    EXPECT_TRUE(processor->mMetricsManagers.begin()->second->isConfigValid());
    processor->mPullerManager->ForceClearPullerCache();

    int startBucketNum = processor->mMetricsManagers.begin()->second->
            mAllMetricProducers[0]->getCurrentBucketNum();
    EXPECT_GT(startBucketNum, (int64_t)0);

    // When creating the config, the gauge metric producer should register the alarm at the
    // end of the current bucket.
    ASSERT_EQ((size_t)1, processor->mPullerManager->mReceivers.size());
    EXPECT_EQ(bucketSizeNs,
              processor->mPullerManager->mReceivers.begin()->second.front().intervalNs);
    int64_t& nextPullTimeNs =
            processor->mPullerManager->mReceivers.begin()->second.front().nextPullTimeNs;
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + bucketSizeNs, nextPullTimeNs);

    // Pulling alarm arrives on time and reset the sequential pulling alarm.
    processor->informPullAlarmFired(nextPullTimeNs + 1);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 2 * bucketSizeNs, nextPullTimeNs);

    processor->informPullAlarmFired(nextPullTimeNs + 4);
    EXPECT_EQ(baseTimeNs + startBucketNum * bucketSizeNs + 3 * bucketSizeNs,
              nextPullTimeNs);

    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, configAddedTimeNs + 7 * bucketSizeNs + 10, false, true,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(buffer.size() > 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(
            reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_GT((int)gaugeMetrics.data_size(), 0);

    auto data = gaugeMetrics.data(0);
    EXPECT_EQ(ATOM_TAG, data.dimensions_in_what().field());
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(1 /* subsystem name field */,
              data.dimensions_in_what().value_tuple().dimensions_value(0).field());
    EXPECT_FALSE(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str().empty());
    ASSERT_EQ(3, data.bucket_info_size());

    ASSERT_EQ(1, data.bucket_info(0).atom_size());
    ASSERT_EQ(1, data.bucket_info(0).elapsed_timestamp_nanos_size());
    EXPECT_EQ(configAddedTimeNs, data.bucket_info(0).elapsed_timestamp_nanos(0));
    ASSERT_EQ(0, data.bucket_info(0).wall_clock_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 2 * bucketSizeNs, data.bucket_info(0).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs, data.bucket_info(0).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(0).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(1).atom_size());
    ASSERT_EQ(1, data.bucket_info(1).elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs + 1, data.bucket_info(1).elapsed_timestamp_nanos(0));
    ASSERT_EQ(0, data.bucket_info(1).wall_clock_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 3 * bucketSizeNs, data.bucket_info(1).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs, data.bucket_info(1).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(1).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(1).atom(0).subsystem_sleep_state().time_millis(), 0);

    ASSERT_EQ(1, data.bucket_info(2).atom_size());
    ASSERT_EQ(1, data.bucket_info(2).elapsed_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs + 4, data.bucket_info(2).elapsed_timestamp_nanos(0));
    ASSERT_EQ(0, data.bucket_info(2).wall_clock_timestamp_nanos_size());
    EXPECT_EQ(baseTimeNs + 4 * bucketSizeNs, data.bucket_info(2).start_bucket_elapsed_nanos());
    EXPECT_EQ(baseTimeNs + 5 * bucketSizeNs, data.bucket_info(2).end_bucket_elapsed_nanos());
    EXPECT_TRUE(data.bucket_info(2).atom(0).subsystem_sleep_state().subsystem_name().empty());
    EXPECT_GT(data.bucket_info(2).atom(0).subsystem_sleep_state().time_millis(), 0);
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
