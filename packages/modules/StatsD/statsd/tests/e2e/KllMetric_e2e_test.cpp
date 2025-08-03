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

#include <gtest/gtest.h>

#include "src/StatsLogProcessor.h"
#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

using namespace std;

namespace {

unique_ptr<LogEvent> CreateTestAtomReportedEvent(const uint64_t timestampNs, const long longField,
                                                 const string& stringField) {
    return CreateTestAtomReportedEvent(
            timestampNs, /* attributionUids */ {1001},
            /* attributionTags */ {"app1"}, /* intField */ 0, longField, /* floatField */ 0.0f,
            stringField, /* boolField */ false, TestAtomReported::OFF, /* bytesField */ {},
            /* repeatedIntField */ {}, /* repeatedLongField */ {}, /* repeatedFloatField */ {},
            /* repeatedStringField */ {}, /* repeatedBoolField */ {},
            /* repeatedBoolFieldLength */ 0, /* repeatedEnumField */ {});
}

}  // anonymous namespace.

class KllMetricE2eTest : public ::testing::Test {
protected:
    void SetUp() override {
        key = ConfigKey(123, 987);
        bucketStartTimeNs = getElapsedRealtimeNs();
        bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
        whatMatcher = CreateScreenBrightnessChangedAtomMatcher();
        metric = createKllMetric("ScreenBrightness", whatMatcher, /*valueField=*/1,
                                 /*condition=*/nullopt);

        *config.add_atom_matcher() = whatMatcher;
        *config.add_kll_metric() = metric;

        events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 5 * NS_PER_SEC, 5));
        events.push_back(
                CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 15 * NS_PER_SEC, 15));
        events.push_back(
                CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 25 * NS_PER_SEC, 40));
    }

    ConfigKey key;
    uint64_t bucketStartTimeNs;
    uint64_t bucketSizeNs;
    AtomMatcher whatMatcher;
    KllMetric metric;
    StatsdConfig config;
    vector<unique_ptr<LogEvent>> events;
};

TEST_F(KllMetricE2eTest, TestSimpleMetric) {
    const sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key);

    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = bucketStartTimeNs + bucketSizeNs;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, /*include_current_bucket*/ false, true, ADB_DUMP, FAST,
                            &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 1);

    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 1);
    StatsLogReport metricReport = report.metrics(0);
    EXPECT_EQ(metricReport.metric_id(), metric.id());
    EXPECT_TRUE(metricReport.has_kll_metrics());
    ASSERT_EQ(metricReport.kll_metrics().data_size(), 1);
    KllMetricData data = metricReport.kll_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    KllBucketInfo bucket = data.bucket_info(0);
    EXPECT_EQ(bucket.start_bucket_elapsed_nanos(), bucketStartTimeNs);
    EXPECT_EQ(bucket.end_bucket_elapsed_nanos(), bucketStartTimeNs + bucketSizeNs);
    EXPECT_EQ(bucket.sketches_size(), 1);
    EXPECT_EQ(metricReport.kll_metrics().skipped_size(), 0);
}

TEST_F(KllMetricE2eTest, TestMetricWithDimensions) {
    whatMatcher = CreateSimpleAtomMatcher("TestAtomReported", util::TEST_ATOM_REPORTED);
    metric = createKllMetric("TestAtomMetric", whatMatcher, /* kllField */ 3,
                             /* condition */ nullopt);

    *metric.mutable_dimensions_in_what() =
            CreateDimensions(util::TEST_ATOM_REPORTED, {5 /* string_field */});

    config.clear_atom_matcher();
    *config.add_atom_matcher() = whatMatcher;

    config.clear_kll_metric();
    *config.add_kll_metric() = metric;

    events.clear();
    events.push_back(CreateTestAtomReportedEvent(bucketStartTimeNs + 5 * NS_PER_SEC, 5l, "dim_1"));
    events.push_back(CreateTestAtomReportedEvent(bucketStartTimeNs + 15 * NS_PER_SEC, 6l, "dim_2"));
    events.push_back(CreateTestAtomReportedEvent(bucketStartTimeNs + 25 * NS_PER_SEC, 7l, "dim_1"));

    const sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key);

    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = bucketStartTimeNs + bucketSizeNs;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, /*include_current_bucket*/ false, true, ADB_DUMP, FAST,
                            &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 1);

    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 1);
    StatsLogReport metricReport = report.metrics(0);
    EXPECT_EQ(metricReport.metric_id(), metric.id());
    EXPECT_TRUE(metricReport.has_kll_metrics());
    ASSERT_EQ(metricReport.kll_metrics().data_size(), 2);

    KllMetricData data = metricReport.kll_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    KllBucketInfo bucket = data.bucket_info(0);
    EXPECT_EQ(bucket.start_bucket_elapsed_nanos(), bucketStartTimeNs);
    EXPECT_EQ(bucket.end_bucket_elapsed_nanos(), bucketStartTimeNs + bucketSizeNs);
    EXPECT_EQ(bucket.sketches_size(), 1);
    EXPECT_EQ(metricReport.kll_metrics().skipped_size(), 0);
    EXPECT_EQ(data.dimensions_in_what().field(), util::TEST_ATOM_REPORTED);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 5);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str(), "dim_1");

    data = metricReport.kll_metrics().data(1);
    ASSERT_EQ(data.bucket_info_size(), 1);
    bucket = data.bucket_info(0);
    EXPECT_EQ(bucket.start_bucket_elapsed_nanos(), bucketStartTimeNs);
    EXPECT_EQ(bucket.end_bucket_elapsed_nanos(), bucketStartTimeNs + bucketSizeNs);
    EXPECT_EQ(bucket.sketches_size(), 1);
    EXPECT_EQ(metricReport.kll_metrics().skipped_size(), 0);
    EXPECT_EQ(data.dimensions_in_what().field(), util::TEST_ATOM_REPORTED);
    ASSERT_EQ(data.dimensions_in_what().value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).field(), 5);
    EXPECT_EQ(data.dimensions_in_what().value_tuple().dimensions_value(0).value_str(), "dim_2");
}

TEST_F(KllMetricE2eTest, TestInitWithKllFieldPositionALL) {
    // Create config.
    StatsdConfig config;

    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedMatcher;

    // Create kll metric.
    int64_t metricId = 123456;
    KllMetric* kllMetric = config.add_kll_metric();
    kllMetric->set_id(metricId);
    kllMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    kllMetric->set_what(testAtomReportedMatcher.id());
    *kllMetric->mutable_kll_field() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::ALL});

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    // Config initialization fails.
    ASSERT_EQ(0, processor->mMetricsManagers.size());
}

TEST_F(KllMetricE2eTest, TestDimensionalSampling) {
    ShardOffsetProvider::getInstance().setShardOffset(5);

    // Create config.
    StatsdConfig config;

    AtomMatcher bleScanResultReceivedMatcher = CreateSimpleAtomMatcher(
            "BleScanResultReceivedAtomMatcher", util::BLE_SCAN_RESULT_RECEIVED);
    *config.add_atom_matcher() = bleScanResultReceivedMatcher;

    // Create kll metric.
    KllMetric sampledKllMetric =
            createKllMetric("KllSampledBleScanResultsPerUid", bleScanResultReceivedMatcher,
                            /*num_results=*/2, nullopt);
    *sampledKllMetric.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::BLE_SCAN_RESULT_RECEIVED, {Position::FIRST});
    *sampledKllMetric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateAttributionUidDimensions(util::BLE_SCAN_RESULT_RECEIVED, {Position::FIRST});
    sampledKllMetric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_kll_metric() = sampledKllMetric;

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);

    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(
            bucketStartTimeNs, bucketStartTimeNs, config, cfgKey, nullptr, 0, new UidMap());

    int appUid1 = 1001;  // odd hash value
    int appUid2 = 1002;  // even hash value
    int appUid3 = 1003;  // odd hash value
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateBleScanResultReceivedEvent(bucketStartTimeNs + 20 * NS_PER_SEC,
                                                      {appUid1}, {"tag1"}, 10));
    events.push_back(CreateBleScanResultReceivedEvent(bucketStartTimeNs + 40 * NS_PER_SEC,
                                                      {appUid2}, {"tag2"}, 10));
    events.push_back(CreateBleScanResultReceivedEvent(bucketStartTimeNs + 60 * NS_PER_SEC,
                                                      {appUid3}, {"tag3"}, 10));

    events.push_back(CreateBleScanResultReceivedEvent(bucketStartTimeNs + 120 * NS_PER_SEC,
                                                      {appUid1}, {"tag1"}, 11));
    events.push_back(CreateBleScanResultReceivedEvent(bucketStartTimeNs + 140 * NS_PER_SEC,
                                                      {appUid2}, {"tag2"}, 12));
    events.push_back(CreateBleScanResultReceivedEvent(bucketStartTimeNs + 160 * NS_PER_SEC,
                                                      {appUid3}, {"tag3"}, 13));

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

    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 1);
    StatsLogReport metricReport = report.metrics(0);
    EXPECT_EQ(metricReport.metric_id(), sampledKllMetric.id());
    EXPECT_TRUE(metricReport.has_kll_metrics());
    StatsLogReport::KllMetricDataWrapper kllMetrics;
    sortMetricDataByDimensionsValue(metricReport.kll_metrics(), &kllMetrics);
    ASSERT_EQ(kllMetrics.data_size(), 2);
    EXPECT_EQ(kllMetrics.skipped_size(), 0);

    // Only Uid 1 and 3 are logged. (odd hash value) + (offset of 5) % (shard count of 2) = 0
    KllMetricData data = kllMetrics.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::BLE_SCAN_RESULT_RECEIVED,
                                    appUid1);
    ValidateKllBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs, {2},
                      0);

    data = kllMetrics.data(1);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::BLE_SCAN_RESULT_RECEIVED,
                                    appUid3);
    ValidateKllBucket(data.bucket_info(0), bucketStartTimeNs, bucketStartTimeNs + bucketSizeNs, {2},
                      0);
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
