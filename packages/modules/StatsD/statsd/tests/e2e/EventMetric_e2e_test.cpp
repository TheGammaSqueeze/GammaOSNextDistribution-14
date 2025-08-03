// Copyright (C) 2019 The Android Open Source Project
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

#include <gtest/gtest.h>

#include <vector>

#include "flags/FlagProvider.h"
#include "src/StatsLogProcessor.h"
#include "src/state/StateTracker.h"
#include "src/stats_log_util.h"
#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

// Setup for test fixture.
class EventMetricE2eTest : public ::testing::Test {
    void SetUp() override {
        FlagProvider::getInstance().overrideFuncs(&isAtLeastSFuncTrue);
    }

    void TearDown() override {
        FlagProvider::getInstance().resetOverrides();
    }
};

TEST_F(EventMetricE2eTest, TestEventMetricDataAggregated) {
    StatsdConfig config;

    AtomMatcher wakelockAcquireMatcher = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockAcquireMatcher;

    EventMetric wakelockEventMetric =
            createEventMetric("EventWakelockStateChanged", wakelockAcquireMatcher.id(), nullopt);
    *config.add_event_metric() = wakelockEventMetric;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key);

    // Initialize log events before update.
    std::vector<std::unique_ptr<LogEvent>> events;

    int app1Uid = 123;
    vector<int> attributionUids = {app1Uid};
    std::vector<string> attributionTags = {"App1"};

    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 10 * NS_PER_SEC,
                                                attributionUids, attributionTags, "wl1"));
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 20 * NS_PER_SEC,
                                                attributionUids, attributionTags, "wl1"));
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 30 * NS_PER_SEC,
                                                attributionUids, attributionTags, "wl2"));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = bucketStartTimeNs + 100 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(reports.reports_size(), 1);

    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 1);
    StatsLogReport wakelockEventMetricReport = report.metrics(0);
    EXPECT_EQ(wakelockEventMetricReport.metric_id(), wakelockEventMetric.id());
    EXPECT_TRUE(wakelockEventMetricReport.has_event_metrics());
    ASSERT_EQ(wakelockEventMetricReport.event_metrics().data_size(), 3);
    auto data = wakelockEventMetricReport.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 10 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl1");
    data = wakelockEventMetricReport.event_metrics().data(1);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 20 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl1");
    data = wakelockEventMetricReport.event_metrics().data(2);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 30 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl2");
}

TEST_F(EventMetricE2eTest, TestRepeatedFieldsAndEmptyArrays) {
    StatsdConfig config;

    AtomMatcher testAtomReportedAtomMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedAtomMatcher;

    EventMetric testAtomReportedEventMetric =
            createEventMetric("EventTestAtomReported", testAtomReportedAtomMatcher.id(), nullopt);
    *config.add_event_metric() = testAtomReportedEventMetric;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key);

    // Initialize log events before update.
    std::vector<std::unique_ptr<LogEvent>> events;

    vector<int> intArray = {3, 6};
    vector<int64_t> longArray = {1000L, 10002L};
    vector<float> floatArray = {0.3f, 0.09f};
    vector<string> stringArray = {"str1", "str2"};
    int boolArrayLength = 2;
    bool boolArray[boolArrayLength];
    boolArray[0] = 1;
    boolArray[1] = 0;
    vector<uint8_t> boolArrayVector = {1, 0};
    vector<int> enumArray = {TestAtomReported::ON, TestAtomReported::OFF};

    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 10 * NS_PER_SEC, intArray, longArray, floatArray, stringArray,
            boolArray, boolArrayLength, enumArray));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, {}));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 30 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArray));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = bucketStartTimeNs + 100 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(reports.reports_size(), 1);

    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 1);
    StatsLogReport testAtomEventMetricReport = report.metrics(0);
    EXPECT_EQ(testAtomEventMetricReport.metric_id(), testAtomReportedEventMetric.id());
    EXPECT_TRUE(testAtomEventMetricReport.has_event_metrics());
    ASSERT_EQ(testAtomEventMetricReport.event_metrics().data_size(), 3);

    EventMetricData data = testAtomEventMetricReport.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 10 * NS_PER_SEC);
    TestAtomReported atom = data.atom().test_atom_reported();
    EXPECT_THAT(atom.repeated_int_field(), ElementsAreArray(intArray));
    EXPECT_THAT(atom.repeated_long_field(), ElementsAreArray(longArray));
    EXPECT_THAT(atom.repeated_float_field(), ElementsAreArray(floatArray));
    EXPECT_THAT(atom.repeated_string_field(), ElementsAreArray(stringArray));
    EXPECT_THAT(atom.repeated_boolean_field(), ElementsAreArray(boolArrayVector));
    EXPECT_THAT(atom.repeated_enum_field(), ElementsAreArray(enumArray));

    data = testAtomEventMetricReport.event_metrics().data(1);
    atom = data.atom().test_atom_reported();
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 20 * NS_PER_SEC);
    EXPECT_EQ(atom.repeated_int_field_size(), 0);
    EXPECT_EQ(atom.repeated_long_field_size(), 0);
    EXPECT_EQ(atom.repeated_float_field_size(), 0);
    EXPECT_EQ(atom.repeated_string_field_size(), 0);
    EXPECT_EQ(atom.repeated_boolean_field_size(), 0);
    EXPECT_EQ(atom.repeated_enum_field_size(), 0);

    data = testAtomEventMetricReport.event_metrics().data(2);
    atom = data.atom().test_atom_reported();
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 30 * NS_PER_SEC);
    EXPECT_EQ(atom.repeated_int_field_size(), 0);
    EXPECT_EQ(atom.repeated_long_field_size(), 0);
    EXPECT_EQ(atom.repeated_float_field_size(), 0);
    EXPECT_EQ(atom.repeated_string_field_size(), 0);
    EXPECT_EQ(atom.repeated_boolean_field_size(), 0);
    EXPECT_THAT(atom.repeated_enum_field(), ElementsAreArray(enumArray));
}

TEST_F(EventMetricE2eTest, TestMatchRepeatedFieldPositionFirst) {
    StatsdConfig config;

    AtomMatcher testAtomReportedStateFirstOnAtomMatcher =
            CreateTestAtomRepeatedStateFirstOnAtomMatcher();
    *config.add_atom_matcher() = testAtomReportedStateFirstOnAtomMatcher;

    EventMetric testAtomReportedEventMetric = createEventMetric(
            "EventTestAtomReported", testAtomReportedStateFirstOnAtomMatcher.id(), nullopt);
    *config.add_event_metric() = testAtomReportedEventMetric;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key);

    // Initialize log events before update.
    std::vector<std::unique_ptr<LogEvent>> events;

    vector<int> enumArrayNoMatch = {TestAtomReported::OFF, TestAtomReported::ON};
    vector<int> enumArrayMatch = {TestAtomReported::ON, TestAtomReported::OFF};

    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 10 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayNoMatch));
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 20 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, enumArrayMatch));
    // No matching is done on an empty array.
    events.push_back(CreateTestAtomReportedEventVariableRepeatedFields(
            bucketStartTimeNs + 30 * NS_PER_SEC, {}, {}, {}, {}, {}, 0, {}));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = bucketStartTimeNs + 100 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(reports.reports_size(), 1);

    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 1);
    StatsLogReport testAtomEventMetricReport = report.metrics(0);
    EXPECT_EQ(testAtomEventMetricReport.metric_id(), testAtomReportedEventMetric.id());
    EXPECT_TRUE(testAtomEventMetricReport.has_event_metrics());
    ASSERT_EQ(testAtomEventMetricReport.event_metrics().data_size(), 1);

    EventMetricData data = testAtomEventMetricReport.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 20 * NS_PER_SEC);
    TestAtomReported atom = data.atom().test_atom_reported();
    ASSERT_EQ(atom.repeated_int_field_size(), 0);
    ASSERT_EQ(atom.repeated_long_field_size(), 0);
    ASSERT_EQ(atom.repeated_float_field_size(), 0);
    ASSERT_EQ(atom.repeated_string_field_size(), 0);
    ASSERT_EQ(atom.repeated_boolean_field_size(), 0);
    EXPECT_THAT(atom.repeated_enum_field(), ElementsAreArray(enumArrayMatch));
}

TEST_F(EventMetricE2eTest, TestDumpReportIncrementsReportNumber) {
    StatsdConfig config;

    AtomMatcher testAtomReportedStateFirstOnAtomMatcher =
            CreateTestAtomRepeatedStateFirstOnAtomMatcher();
    *config.add_atom_matcher() = testAtomReportedStateFirstOnAtomMatcher;

    EventMetric testAtomReportedEventMetric = createEventMetric(
            "EventTestAtomReported", testAtomReportedStateFirstOnAtomMatcher.id(), nullopt);
    *config.add_event_metric() = testAtomReportedEventMetric;

    ConfigKey key(123, 987);
    uint64_t configUpdateTime = 10000000000;  // 0:10
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(configUpdateTime, configUpdateTime, config, key);

    uint64_t dumpTimeNs = configUpdateTime + 100 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    ASSERT_EQ(reports.reports_size(), 1);

    EXPECT_EQ(reports.report_number(), 1);
    EXPECT_EQ(reports.statsd_stats_id(), StatsdStats::getInstance().getStatsdStatsId());

    buffer.clear();
    processor->onDumpReport(key, dumpTimeNs + 100, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    ASSERT_EQ(reports.reports_size(), 1);

    EXPECT_EQ(reports.report_number(), 2);
    EXPECT_EQ(reports.statsd_stats_id(), StatsdStats::getInstance().getStatsdStatsId());
}

TEST_F(EventMetricE2eTest, TestEventMetricSampling) {
    // Set srand seed to make rand deterministic for testing.
    srand(0);

    StatsdConfig config;

    AtomMatcher batterySaverOnMatcher = CreateBatterySaverModeStartAtomMatcher();
    *config.add_atom_matcher() = batterySaverOnMatcher;

    EventMetric batterySaverOnEventMetric =
            createEventMetric("EventBatterySaverOn", batterySaverOnMatcher.id(), nullopt);
    batterySaverOnEventMetric.set_sampling_percentage(50);
    *config.add_event_metric() = batterySaverOnEventMetric;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key);

    // Initialize log events before update.
    std::vector<std::unique_ptr<LogEvent>> events;

    for (int i = 0; i < 100; i++) {
        events.push_back(CreateBatterySaverOnEvent(bucketStartTimeNs + (10 + 10 * i) * NS_PER_SEC));
    }

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = bucketStartTimeNs + 2000 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(reports.reports_size(), 1);

    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 1);
    StatsLogReport metricReport = report.metrics(0);
    EXPECT_EQ(metricReport.metric_id(), batterySaverOnEventMetric.id());
    EXPECT_TRUE(metricReport.has_event_metrics());
    ASSERT_EQ(metricReport.event_metrics().data_size(), 46);
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
