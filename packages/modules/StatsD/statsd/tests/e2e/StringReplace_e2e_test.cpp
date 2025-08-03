/*
 * Copyright (C) 2024, The Android Open Source Project
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
#include <gtest_matchers.h>

#include "src/StatsLogProcessor.h"
#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

namespace {

const int64_t metricId = 123456;
const int TEST_ATOM_REPORTED_STRING_FIELD_ID = 5;
const int SUBSYSTEM_SLEEP_STATE_SUBSYSTEM_NAME_FIELD_ID = 1;
const int SUBSYSTEM_SLEEP_STATE_SUBNAME_FIELD_ID = 2;
const int SUBSYSTEM_SLEEP_STATE_TIME_MILLIS_FIELD_ID = 4;
const int SCHEDULED_JOB_STATE_CHANGED_JOB_NAME_FIELD_ID = 2;
const int ACTIVITY_FOREGROUND_STATE_CHANGED_UID_FIELD_ID = 1;
const int ACTIVITY_FOREGROUND_STATE_CHANGED_PKG_NAME_FIELD_ID = 2;
const int WAKELOCK_STATE_CHANGED_TAG_FIELD_ID = 3;
const int ATTRIBUTION_CHAIN_FIELD_ID = 1;
const int ATTRIBUTION_TAG_FIELD_ID = 2;

std::unique_ptr<LogEvent> CreateTestAtomReportedEventStringDim(uint64_t timestampNs,
                                                               const string& stringField) {
    return CreateTestAtomReportedEventWithPrimitives(
            timestampNs, 0 /* intField */, 0l /* longField */, 0.0f /* floatField */, stringField,
            false /* boolField */, TestAtomReported::OFF /* enumField */);
}

StatsdConfig CreateStatsdConfig() {
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.
    config.set_hash_strings_in_metric_report(false);

    return config;
}

}  // namespace

TEST(StringReplaceE2eTest, TestPushedDimension) {
    StatsdConfig config = CreateStatsdConfig();

    *config.add_atom_matcher() =
            CreateSimpleAtomMatcher("TestAtomMatcher", util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = config.mutable_atom_matcher(0)
                                     ->mutable_simple_atom_matcher()
                                     ->add_field_value_matcher();
    fvm->set_field(TEST_ATOM_REPORTED_STRING_FIELD_ID);
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    CountMetric* countMetric = config.add_count_metric();
    *countMetric = createCountMetric("TestCountMetric", config.atom_matcher(0).id() /* what */,
                                     nullopt /* condition */, {} /* states */);
    countMetric->mutable_dimensions_in_what()->set_field(util::TEST_ATOM_REPORTED);
    countMetric->mutable_dimensions_in_what()->add_child()->set_field(
            TEST_ATOM_REPORTED_STRING_FIELD_ID);

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.count_metric(0).bucket()) * 1000000LL;
    const int uid = 12345;
    const int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);

    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(
            bucketStartTimeNs, bucketStartTimeNs, config, cfgKey, nullptr, 0, new UidMap());

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 20 * NS_PER_SEC,
                                                          "dimA" /* stringField */));  // 0:30
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 40 * NS_PER_SEC,
                                                          "dimA123" /* stringField */));  // 0:50
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 60 * NS_PER_SEC,
                                                          "dimA123B" /* stringField */));  // 1:10
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 80 * NS_PER_SEC,
                                                          "dimC0" /* stringField */));  // 1:20
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 90 * NS_PER_SEC,
                                                          "dimC00000" /* stringField */));  // 1:30
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 100 * NS_PER_SEC,
                                                          "dimC" /* stringField */));  // 1:40

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
    ASSERT_EQ(3, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    DimensionsValue dimValue = data.dimensions_in_what();
    EXPECT_EQ(dimValue.field(), util::TEST_ATOM_REPORTED);
    ASSERT_EQ(dimValue.value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(dimValue.value_tuple().dimensions_value(0).field(),
              TEST_ATOM_REPORTED_STRING_FIELD_ID);
    EXPECT_EQ(dimValue.value_tuple().dimensions_value(0).value_str(), "dimA");
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(2, data.bucket_info(0).count());

    data = countMetrics.data(1);
    dimValue = data.dimensions_in_what();
    EXPECT_EQ(dimValue.field(), util::TEST_ATOM_REPORTED);
    ASSERT_EQ(dimValue.value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(dimValue.value_tuple().dimensions_value(0).field(),
              TEST_ATOM_REPORTED_STRING_FIELD_ID);
    EXPECT_EQ(dimValue.value_tuple().dimensions_value(0).value_str(), "dimA123B");
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(1, data.bucket_info(0).count());

    data = countMetrics.data(2);
    dimValue = data.dimensions_in_what();
    EXPECT_EQ(dimValue.field(), util::TEST_ATOM_REPORTED);
    ASSERT_EQ(dimValue.value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(dimValue.value_tuple().dimensions_value(0).field(),
              TEST_ATOM_REPORTED_STRING_FIELD_ID);
    EXPECT_EQ(dimValue.value_tuple().dimensions_value(0).value_str(), "dimC");
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(3, data.bucket_info(0).count());
}

TEST(StringReplaceE2eTest, TestPushedWhat) {
    StatsdConfig config = CreateStatsdConfig();

    *config.add_atom_matcher() =
            CreateSimpleAtomMatcher("TestAtomMatcher", util::TEST_ATOM_REPORTED);

    FieldValueMatcher* fvm = config.mutable_atom_matcher(0)
                                     ->mutable_simple_atom_matcher()
                                     ->add_field_value_matcher();
    fvm->set_field(TEST_ATOM_REPORTED_STRING_FIELD_ID);
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    *config.add_gauge_metric() = createGaugeMetric(
            "TestAtomGaugeMetric", config.atom_matcher(0).id() /* what */,
            GaugeMetric::FIRST_N_SAMPLES, nullopt /* condition */, nullopt /* triggerEvent */);

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000LL;
    const int uid = 12345;
    const int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);

    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(
            bucketStartTimeNs, bucketStartTimeNs, config, cfgKey, nullptr, 0, new UidMap());

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 20 * NS_PER_SEC,
                                                          "dimA" /* stringField */));  // 0:30
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 40 * NS_PER_SEC,
                                                          "dimA123" /* stringField */));  // 0:50
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 60 * NS_PER_SEC,
                                                          "dimA123B" /* stringField */));  // 1:10
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 80 * NS_PER_SEC,
                                                          "dimC0" /* stringField */));  // 1:20
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 90 * NS_PER_SEC,
                                                          "dimC00000" /* stringField */));  // 1:30
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 100 * NS_PER_SEC,
                                                          "dimC" /* stringField */));  // 1:40

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
    backfillAggregatedAtoms(&reports);

    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    EXPECT_TRUE(reports.reports(0).metrics(0).has_gauge_metrics());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 1);

    auto data = gaugeMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());

    ASSERT_EQ(6, data.bucket_info(0).atom_size());
    EXPECT_EQ(data.bucket_info(0).atom(0).test_atom_reported().string_field(), "dimA");
    EXPECT_EQ(data.bucket_info(0).atom(1).test_atom_reported().string_field(), "dimA");
    EXPECT_EQ(data.bucket_info(0).atom(2).test_atom_reported().string_field(), "dimA123B");
    EXPECT_EQ(data.bucket_info(0).atom(3).test_atom_reported().string_field(), "dimC");
    EXPECT_EQ(data.bucket_info(0).atom(4).test_atom_reported().string_field(), "dimC");
    EXPECT_EQ(data.bucket_info(0).atom(5).test_atom_reported().string_field(), "dimC");
}

TEST(StringReplaceE2eTest, TestPulledDimension) {
    StatsdConfig config = CreateStatsdConfig();

    *config.add_atom_matcher() =
            CreateSimpleAtomMatcher("SubsystemMatcher", util::SUBSYSTEM_SLEEP_STATE);
    FieldValueMatcher* fvm = config.mutable_atom_matcher(0)
                                     ->mutable_simple_atom_matcher()
                                     ->add_field_value_matcher();
    fvm->set_field(SUBSYSTEM_SLEEP_STATE_SUBSYSTEM_NAME_FIELD_ID);
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    *config.add_gauge_metric() = createGaugeMetric(
            "SubsystemGaugeMetric", config.atom_matcher(0).id() /* what */,
            GaugeMetric::RANDOM_ONE_SAMPLE, nullopt /* condition */, nullopt /* triggerEvent */);
    *config.mutable_gauge_metric(0)->mutable_dimensions_in_what() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {1 /* subsystem name */});

    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    ConfigKey cfgKey;
    auto processor = CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                             SharedRefBase::make<FakeSubsystemSleepCallback>(),
                                             util::SUBSYSTEM_SLEEP_STATE);
    processor->mPullerManager->ForceClearPullerCache();

    // Pulling alarm arrives on time and reset the sequential pulling alarm.
    processor->informPullAlarmFired(baseTimeNs + 2 * bucketSizeNs + 1);

    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, configAddedTimeNs + 3 * bucketSizeNs + 10, false, true,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 1);

    auto data = gaugeMetrics.data(0);
    EXPECT_EQ(util::SUBSYSTEM_SLEEP_STATE, data.dimensions_in_what().field());
    ASSERT_EQ(1, data.dimensions_in_what().value_tuple().dimensions_value_size());
    EXPECT_EQ(SUBSYSTEM_SLEEP_STATE_SUBSYSTEM_NAME_FIELD_ID,
              data.dimensions_in_what().value_tuple().dimensions_value(0).field());

    // Trailing numbers are trimmed from the dimension: subsystem_name_# --> subsystem_name_
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str(),
              "subsystem_name_");
}

TEST(StringReplaceE2eTest, TestPulledWhat) {
    StatsdConfig config = CreateStatsdConfig();

    *config.add_atom_matcher() =
            CreateSimpleAtomMatcher("SubsystemMatcher", util::SUBSYSTEM_SLEEP_STATE);
    FieldValueMatcher* fvm = config.mutable_atom_matcher(0)
                                     ->mutable_simple_atom_matcher()
                                     ->add_field_value_matcher();
    fvm->set_field(SUBSYSTEM_SLEEP_STATE_SUBNAME_FIELD_ID);
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"(foo)");
    stringReplacer->set_replacement("bar");

    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();

    *config.add_predicate() = CreateScreenIsOffPredicate();

    *config.add_gauge_metric() =
            createGaugeMetric("SubsystemGaugeMetric", config.atom_matcher(0).id() /* what */,
                              GaugeMetric::RANDOM_ONE_SAMPLE,
                              config.predicate(0).id() /* condition */, nullopt /* triggerEvent */);

    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;

    ConfigKey cfgKey;
    auto processor = CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                             SharedRefBase::make<FakeSubsystemSleepCallback>(),
                                             util::SUBSYSTEM_SLEEP_STATE);
    processor->mPullerManager->ForceClearPullerCache();

    auto screenOffEvent =
            CreateScreenStateChangedEvent(configAddedTimeNs + 55, android::view::DISPLAY_STATE_OFF);
    processor->OnLogEvent(screenOffEvent.get());

    // Pulling alarm arrives on time and reset the sequential pulling alarm.
    processor->informPullAlarmFired(baseTimeNs + 2 * bucketSizeNs + 1);

    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, configAddedTimeNs + 3 * bucketSizeNs + 10, false, true,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(1, reports.reports(0).metrics_size());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 1);

    auto data = gaugeMetrics.data(0);
    ASSERT_EQ(2, data.bucket_info_size());

    ASSERT_EQ(1, data.bucket_info(0).atom_size());
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().subname(),
              "subsystem_subname bar");

    ASSERT_EQ(1, data.bucket_info(1).atom_size());
    EXPECT_EQ(data.bucket_info(1).atom(0).subsystem_sleep_state().subname(),
              "subsystem_subname bar");
}

TEST(StringReplaceE2eTest, TestCondition) {
    StatsdConfig config = CreateStatsdConfig();

    AtomMatcher matcher = CreateStartScheduledJobAtomMatcher();
    FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(SCHEDULED_JOB_STATE_CHANGED_JOB_NAME_FIELD_ID);
    fvm->set_eq_string("foo");
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"(com.google.)");
    stringReplacer->set_replacement("");
    *config.add_atom_matcher() = matcher;
    matcher = CreateFinishScheduledJobAtomMatcher();
    fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(SCHEDULED_JOB_STATE_CHANGED_JOB_NAME_FIELD_ID);
    fvm->set_eq_string("foo");
    stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"(com.google.)");
    stringReplacer->set_replacement("");
    *config.add_atom_matcher() = matcher;

    Predicate predicate = CreateScheduledJobPredicate();
    *config.add_predicate() = predicate;

    matcher = CreateSimpleAtomMatcher("TestAtomMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = matcher;

    CountMetric* countMetric = config.add_count_metric();
    *countMetric = createCountMetric("TestCountMetric", matcher.id() /* what */,
                                     predicate.id() /* condition */, {} /* states */);

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(countMetric->bucket()) * 1000000LL;
    const int uid = 12345;
    const int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);

    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(
            bucketStartTimeNs, bucketStartTimeNs, config, cfgKey, nullptr, 0, new UidMap());

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateStartScheduledJobEvent(bucketStartTimeNs + 20 * NS_PER_SEC,
                                                  {1001} /* uids */, {"App1"},
                                                  "com.google.job1"));  // 0:30
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 30 * NS_PER_SEC,
                                                          "str" /* stringField */));  // 0:40
    events.push_back(CreateStartScheduledJobEvent(bucketStartTimeNs + 40 * NS_PER_SEC,
                                                  {1002} /* uids */, {"App1"},
                                                  "com.google.foo"));  // 0:50
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 50 * NS_PER_SEC,
                                                          "str" /* stringField */));  // 1:00
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 60 * NS_PER_SEC,
                                                          "str" /* stringField */));  // 1:10
    events.push_back(CreateFinishScheduledJobEvent(bucketStartTimeNs + 70 * NS_PER_SEC,
                                                   {1001} /* uids */, {"App1"},
                                                   "com.google.job1"));  // 1:20
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 80 * NS_PER_SEC,
                                                          "str" /* stringField */));  // 1:30
    events.push_back(CreateFinishScheduledJobEvent(bucketStartTimeNs + 90 * NS_PER_SEC,
                                                   {1002} /* uids */, {"App1"},
                                                   "com.google.foo"));  // 1:40
    events.push_back(CreateTestAtomReportedEventStringDim(bucketStartTimeNs + 100 * NS_PER_SEC,
                                                          "str" /* stringField */));  // 1:50

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
    ASSERT_TRUE(reports.reports(0).metrics(0).has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).count_metrics(), &countMetrics);
    ASSERT_EQ(1, countMetrics.data_size());

    CountMetricData data = countMetrics.data(0);
    ASSERT_EQ(1, data.bucket_info_size());
    EXPECT_EQ(3, data.bucket_info(0).count());
}

TEST(StringReplaceE2eTest, TestDurationMetric) {
    StatsdConfig config = CreateStatsdConfig();

    AtomMatcher matcher = CreateAcquireWakelockAtomMatcher();
    FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(ATTRIBUTION_CHAIN_FIELD_ID);
    fvm->set_position(Position::FIRST);
    fvm->mutable_matches_tuple()->add_field_value_matcher()->set_field(ATTRIBUTION_TAG_FIELD_ID);
    StringReplacer* stringReplacer =
            fvm->mutable_matches_tuple()->mutable_field_value_matcher(0)->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+)");
    stringReplacer->set_replacement("#");
    *config.add_atom_matcher() = matcher;

    matcher = CreateReleaseWakelockAtomMatcher();
    fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(ATTRIBUTION_CHAIN_FIELD_ID);
    fvm->set_position(Position::FIRST);
    fvm->mutable_matches_tuple()->add_field_value_matcher()->set_field(ATTRIBUTION_TAG_FIELD_ID);
    stringReplacer =
            fvm->mutable_matches_tuple()->mutable_field_value_matcher(0)->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+)");
    stringReplacer->set_replacement("#");
    *config.add_atom_matcher() = matcher;

    matcher = CreateMoveToBackgroundAtomMatcher();
    fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(ACTIVITY_FOREGROUND_STATE_CHANGED_PKG_NAME_FIELD_ID);
    stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+)");
    stringReplacer->set_replacement("#");
    *config.add_atom_matcher() = matcher;

    matcher = CreateMoveToForegroundAtomMatcher();
    fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(ACTIVITY_FOREGROUND_STATE_CHANGED_PKG_NAME_FIELD_ID);
    stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+)");
    stringReplacer->set_replacement("#");
    *config.add_atom_matcher() = matcher;

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    // The predicate is dimensioning by first attribution node by uid and tag.
    *holdingWakelockPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateAttributionUidAndTagDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *config.add_predicate() = holdingWakelockPredicate;

    Predicate isInBackgroundPredicate = CreateIsInBackgroundPredicate();
    *isInBackgroundPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateDimensions(util::ACTIVITY_FOREGROUND_STATE_CHANGED,
                             {ACTIVITY_FOREGROUND_STATE_CHANGED_UID_FIELD_ID,
                              ACTIVITY_FOREGROUND_STATE_CHANGED_PKG_NAME_FIELD_ID});
    *config.add_predicate() = isInBackgroundPredicate;

    DurationMetric durationMetric =
            createDurationMetric("WakelockDuration", holdingWakelockPredicate.id() /* what */,
                                 isInBackgroundPredicate.id() /* condition */, {} /* states */);
    *durationMetric.mutable_dimensions_in_what() =
            CreateAttributionUidAndTagDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    durationMetric.set_bucket(FIVE_MINUTES);
    *config.add_duration_metric() = durationMetric;

    // Links between wakelock state atom and condition of app is in background.
    MetricConditionLink* link = durationMetric.add_links();
    link->set_condition(isInBackgroundPredicate.id());
    *link->mutable_fields_in_what() =
            CreateAttributionUidAndTagDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *link->mutable_fields_in_condition() =
            CreateDimensions(util::ACTIVITY_FOREGROUND_STATE_CHANGED,
                             {ACTIVITY_FOREGROUND_STATE_CHANGED_UID_FIELD_ID,
                              ACTIVITY_FOREGROUND_STATE_CHANGED_PKG_NAME_FIELD_ID});

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    const uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(durationMetric.bucket()) * 1000000LL;
    const int uid = 12345;
    const int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);

    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(
            bucketStartTimeNs, bucketStartTimeNs, config, cfgKey, nullptr, 0, new UidMap());

    int appUid = 123;
    std::vector<int> attributionUids1 = {appUid};
    std::vector<string> attributionTags1 = {"App1"};
    std::vector<string> attributionTags2 = {"App2"};

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 10 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl1"));  // 0:10
    events.push_back(CreateActivityForegroundStateChangedEvent(
            bucketStartTimeNs + 22 * NS_PER_SEC, appUid, "App1", "class_name",
            ActivityForegroundStateChanged::BACKGROUND));  // 0:22
    events.push_back(CreateReleaseWakelockEvent(bucketStartTimeNs + 60 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl1"));  // 1:00
    events.push_back(CreateActivityForegroundStateChangedEvent(
            bucketStartTimeNs + (3 * 60 + 15) * NS_PER_SEC, appUid, "App1", "class_name",
            ActivityForegroundStateChanged::FOREGROUND));  // 3:15
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + (3 * 60 + 20) * NS_PER_SEC,
                                                attributionUids1, attributionTags2,
                                                "wl2"));  // 3:20
    events.push_back(CreateActivityForegroundStateChangedEvent(
            bucketStartTimeNs + (3 * 60 + 30) * NS_PER_SEC, appUid, "App1", "class_name",
            ActivityForegroundStateChanged::BACKGROUND));  // 3:30
    events.push_back(CreateActivityForegroundStateChangedEvent(
            bucketStartTimeNs + (3 * 60 + 40) * NS_PER_SEC, appUid, "App2", "class_name",
            ActivityForegroundStateChanged::FOREGROUND));  // 3:40
    events.push_back(CreateReleaseWakelockEvent(bucketStartTimeNs + (3 * 60 + 50) * NS_PER_SEC,
                                                attributionUids1, attributionTags2,
                                                "wl2"));  // 3:50

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

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
    ASSERT_TRUE(reports.reports(0).metrics(0).has_duration_metrics());
    StatsLogReport::DurationMetricDataWrapper durationMetrics;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).duration_metrics(),
                                    &durationMetrics);
    ASSERT_EQ(1, durationMetrics.data_size());

    DurationMetricData data = durationMetrics.data(0);
    // Validate dimension value.
    ValidateAttributionUidAndTagDimension(data.dimensions_in_what(), util::WAKELOCK_STATE_CHANGED,
                                          appUid, "App#");
    // Validate bucket info.
    ASSERT_EQ(1, data.bucket_info_size());

    auto bucketInfo = data.bucket_info(0);
    EXPECT_EQ(bucketStartTimeNs, bucketInfo.start_bucket_elapsed_nanos());
    EXPECT_EQ(bucketStartTimeNs + bucketSizeNs, bucketInfo.end_bucket_elapsed_nanos());
    EXPECT_EQ(48 * NS_PER_SEC, bucketInfo.duration_nanos());
}

TEST(StringReplaceE2eTest, TestMultipleMatchersForAtom) {
    StatsdConfig config = CreateStatsdConfig();

    {
        AtomMatcher matcher = CreateSimpleAtomMatcher("Matcher1", util::SUBSYSTEM_SLEEP_STATE);
        FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
        fvm->set_field(SUBSYSTEM_SLEEP_STATE_SUBSYSTEM_NAME_FIELD_ID);
        fvm->set_eq_string("subsystem_name_1");
        fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
        fvm->set_field(SUBSYSTEM_SLEEP_STATE_SUBNAME_FIELD_ID);
        fvm->set_eq_string("subsystem_subname bar");
        StringReplacer* stringReplacer = fvm->mutable_replace_string();
        stringReplacer->set_regex(R"(foo)");
        stringReplacer->set_replacement("bar");
        *config.add_atom_matcher() = matcher;

        *config.add_value_metric() =
                createValueMetric("Value1", matcher, SUBSYSTEM_SLEEP_STATE_TIME_MILLIS_FIELD_ID,
                                  nullopt /* condition */, {} /* states */);
    }
    {
        AtomMatcher matcher = CreateSimpleAtomMatcher("Matcher2", util::SUBSYSTEM_SLEEP_STATE);
        FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
        fvm->set_field(SUBSYSTEM_SLEEP_STATE_SUBSYSTEM_NAME_FIELD_ID);
        fvm->set_eq_string("subsystem_name_2");
        fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
        fvm->set_field(SUBSYSTEM_SLEEP_STATE_SUBNAME_FIELD_ID);
        fvm->set_eq_string("subsystem_subname foo");
        *config.add_atom_matcher() = matcher;

        *config.add_value_metric() =
                createValueMetric("Value2", matcher, SUBSYSTEM_SLEEP_STATE_TIME_MILLIS_FIELD_ID,
                                  nullopt /* condition */, {} /* states */);
    }

    int64_t baseTimeNs = getElapsedRealtimeNs();
    int64_t configAddedTimeNs = 10 * 60 * NS_PER_SEC + baseTimeNs;
    int64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(config.value_metric(0).bucket()) * 1000000;

    ConfigKey cfgKey;
    auto processor = CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, cfgKey,
                                             SharedRefBase::make<FakeSubsystemSleepCallback>(),
                                             util::SUBSYSTEM_SLEEP_STATE);
    processor->mPullerManager->ForceClearPullerCache();

    // Pulling alarm arrives on time and reset the sequential pulling alarm.
    processor->informPullAlarmFired(baseTimeNs + 2 * bucketSizeNs + 1);

    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(cfgKey, configAddedTimeNs + 3 * bucketSizeNs + 10, false, true,
                            ADB_DUMP, FAST, &buffer);
    EXPECT_GT(buffer.size(), 0);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(1, reports.reports_size());
    ASSERT_EQ(2, reports.reports(0).metrics_size());

    {
        StatsLogReport::ValueMetricDataWrapper valueMetrics;
        sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).value_metrics(),
                                        &valueMetrics);
        ASSERT_EQ(valueMetrics.data_size(), 1);

        ValueMetricData data = valueMetrics.data(0);
        ASSERT_EQ(data.bucket_info_size(), 1);
        ASSERT_EQ(data.bucket_info(0).values_size(), 1);
    }
    {
        StatsLogReport::ValueMetricDataWrapper valueMetrics;
        sortMetricDataByDimensionsValue(reports.reports(0).metrics(1).value_metrics(),
                                        &valueMetrics);
        ASSERT_EQ(valueMetrics.data_size(), 1);

        ValueMetricData data = valueMetrics.data(0);
        ASSERT_EQ(data.bucket_info_size(), 1);
        ASSERT_EQ(data.bucket_info(0).values_size(), 1);
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
