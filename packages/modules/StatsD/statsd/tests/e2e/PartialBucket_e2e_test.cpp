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

#include <android/binder_ibinder.h>
#include <android/binder_interface_utils.h>
#include <gtest/gtest.h>

#include <vector>

#include "src/StatsLogProcessor.h"
#include "src/StatsService.h"
#include "src/packages/UidMap.h"
#include "src/stats_log_util.h"
#include "tests/statsd_test_util.h"

using ::ndk::SharedRefBase;
using std::shared_ptr;

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__
namespace {
const string kApp1 = "app1.sharing.1";

StatsdConfig MakeCountMetricConfig(const std::optional<bool> splitBucket) {
    StatsdConfig config;

    auto appCrashMatcher = CreateProcessCrashAtomMatcher();
    *config.add_atom_matcher() = appCrashMatcher;
    auto countMetric = config.add_count_metric();
    countMetric->set_id(StringToId("AppCrashes"));
    countMetric->set_what(appCrashMatcher.id());
    countMetric->set_bucket(FIVE_MINUTES);
    if (splitBucket.has_value()) {
        countMetric->set_split_bucket_for_app_upgrade(splitBucket.value());
    }
    return config;
}

StatsdConfig MakeValueMetricConfig(int64_t minTime) {
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.

    auto pulledAtomMatcher =
            CreateSimpleAtomMatcher("TestMatcher", util::SUBSYSTEM_SLEEP_STATE);
    *config.add_atom_matcher() = pulledAtomMatcher;
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();

    auto valueMetric = config.add_value_metric();
    valueMetric->set_id(123456);
    valueMetric->set_what(pulledAtomMatcher.id());
    *valueMetric->mutable_value_field() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {4 /* time sleeping field */});
    *valueMetric->mutable_dimensions_in_what() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {1 /* subsystem name */});
    valueMetric->set_bucket(FIVE_MINUTES);
    valueMetric->set_min_bucket_size_nanos(minTime);
    valueMetric->set_use_absolute_value_on_reset(true);
    valueMetric->set_skip_zero_diff_output(false);
    valueMetric->set_split_bucket_for_app_upgrade(true);
    return config;
}

StatsdConfig MakeGaugeMetricConfig(int64_t minTime) {
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.

    auto pulledAtomMatcher =
                CreateSimpleAtomMatcher("TestMatcher", util::SUBSYSTEM_SLEEP_STATE);
    *config.add_atom_matcher() = pulledAtomMatcher;
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();

    auto gaugeMetric = config.add_gauge_metric();
    gaugeMetric->set_id(123456);
    gaugeMetric->set_what(pulledAtomMatcher.id());
    gaugeMetric->mutable_gauge_fields_filter()->set_include_all(true);
    *gaugeMetric->mutable_dimensions_in_what() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {1 /* subsystem name */});
    gaugeMetric->set_bucket(FIVE_MINUTES);
    gaugeMetric->set_min_bucket_size_nanos(minTime);
    gaugeMetric->set_split_bucket_for_app_upgrade(true);
    return config;
}
}  // anonymous namespace

// Setup for test fixture.
class PartialBucketE2eTest : public StatsServiceConfigTest {};

TEST_F(PartialBucketE2eTest, TestCountMetricWithoutSplit) {
    sendConfig(MakeCountMetricConfig({true}));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 1, 100).get());
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 2, 100).get());

    ConfigMetricsReport report = getReports(service->mProcessor, start + 3);
    // Expect no metrics since the bucket has not finished yet.
    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(0, report.metrics(0).count_metrics().data_size());
}

TEST_F(PartialBucketE2eTest, TestCountMetricNoSplitOnNewApp) {
    sendConfig(MakeCountMetricConfig({true}));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    // Force the uidmap to update at timestamp 2.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 1, 100).get());
    // This is a new installation, so there shouldn't be a split (should be same as the without
    // split case).
    service->mUidMap->updateApp(start + 2, kApp1, 1, 2, "v2", "", /* certificateHash */ {});
    // Goes into the second bucket.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 3, 100).get());

    ConfigMetricsReport report = getReports(service->mProcessor, start + 4);
    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(0, report.metrics(0).count_metrics().data_size());
}

TEST_F(PartialBucketE2eTest, TestCountMetricSplitOnUpgrade) {
    sendConfig(MakeCountMetricConfig({true}));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.
    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1, /*version*/ 1, "v1", kApp1);
    service->mUidMap->updateMap(start, uidData);

    // Force the uidmap to update at timestamp 2.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 1, 100).get());
    service->mUidMap->updateApp(start + 2, kApp1, 1, 2, "v2", "", /* certificateHash */ {});
    // Goes into the second bucket.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 3, 100).get());

    ConfigMetricsReport report = getReports(service->mProcessor, start + 4);
    backfillStartEndTimestamp(&report);

    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data(0).bucket_info_size());
    EXPECT_TRUE(report.metrics(0)
                        .count_metrics()
                        .data(0)
                        .bucket_info(0)
                        .has_start_bucket_elapsed_nanos());
    EXPECT_TRUE(report.metrics(0)
                        .count_metrics()
                        .data(0)
                        .bucket_info(0)
                        .has_end_bucket_elapsed_nanos());
    EXPECT_EQ(1, report.metrics(0).count_metrics().data(0).bucket_info(0).count());
}

TEST_F(PartialBucketE2eTest, TestCountMetricSplitOnRemoval) {
    sendConfig(MakeCountMetricConfig({true}));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.
    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1, /*version*/ 1, "v1", kApp1);
    service->mUidMap->updateMap(start, uidData);

    // Force the uidmap to update at timestamp 2.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 1, 100).get());
    service->mUidMap->removeApp(start + 2, kApp1, 1);
    // Goes into the second bucket.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 3, 100).get());

    ConfigMetricsReport report = getReports(service->mProcessor, start + 4);
    backfillStartEndTimestamp(&report);

    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data(0).bucket_info_size());
    EXPECT_TRUE(report.metrics(0)
                        .count_metrics()
                        .data(0)
                        .bucket_info(0)
                        .has_start_bucket_elapsed_nanos());
    EXPECT_TRUE(report.metrics(0)
                        .count_metrics()
                        .data(0)
                        .bucket_info(0)
                        .has_end_bucket_elapsed_nanos());
    EXPECT_EQ(1, report.metrics(0).count_metrics().data(0).bucket_info(0).count());
}

TEST_F(PartialBucketE2eTest, TestCountMetricSplitOnBoot) {
    sendConfig(MakeCountMetricConfig(std::nullopt));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    // Goes into the first bucket
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + NS_PER_SEC, 100).get());
    int64_t bootCompleteTimeNs = start + 2 * NS_PER_SEC;
    service->mProcessor->onStatsdInitCompleted(bootCompleteTimeNs);
    // Goes into the second bucket.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 3 * NS_PER_SEC, 100).get());

    ConfigMetricsReport report = getReports(service->mProcessor, start + 4 * NS_PER_SEC);
    backfillStartEndTimestamp(&report);

    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data(0).bucket_info_size());
    EXPECT_TRUE(report.metrics(0)
                        .count_metrics()
                        .data(0)
                        .bucket_info(0)
                        .has_start_bucket_elapsed_nanos());
    EXPECT_EQ(MillisToNano(NanoToMillis(bootCompleteTimeNs)),
              report.metrics(0).count_metrics().data(0).bucket_info(0).end_bucket_elapsed_nanos());
    EXPECT_EQ(1, report.metrics(0).count_metrics().data(0).bucket_info(0).count());
}

TEST_F(PartialBucketE2eTest, TestCountMetricNoSplitOnUpgradeWhenDisabled) {
    StatsdConfig config = MakeCountMetricConfig({false});
    sendConfig(config);
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.
    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1, /*version*/ 1, "v1", kApp1);
    service->mUidMap->updateMap(start, uidData);

    // Force the uidmap to update at timestamp 2.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 1, 100).get());
    service->mUidMap->updateApp(start + 2, kApp1, 1, 2, "v2", "", /* certificateHash */ {});
    // Still goes into the first bucket.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 3, 100).get());

    ConfigMetricsReport report =
            getReports(service->mProcessor, start + 4, /*include_current=*/true);
    backfillStartEndTimestamp(&report);

    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data(0).bucket_info_size());
    const CountBucketInfo& bucketInfo = report.metrics(0).count_metrics().data(0).bucket_info(0);
    EXPECT_EQ(bucketInfo.end_bucket_elapsed_nanos(), MillisToNano(NanoToMillis(start + 4)));
    EXPECT_EQ(bucketInfo.count(), 2);
}

TEST_F(PartialBucketE2eTest, TestValueMetricWithoutMinPartialBucket) {
    service->mPullerManager->RegisterPullAtomCallback(
            /*uid=*/0, util::SUBSYSTEM_SLEEP_STATE, NS_PER_SEC, NS_PER_SEC * 10, {},
            SharedRefBase::make<FakeSubsystemSleepCallback>());
    // Partial buckets don't occur when app is first installed.
    service->mUidMap->updateApp(1, kApp1, 1, 1, "v1", "", /* certificateHash */ {});
    sendConfig(MakeValueMetricConfig(0));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    service->mProcessor->informPullAlarmFired(5 * 60 * NS_PER_SEC + start);
    int64_t appUpgradeTimeNs = 5 * 60 * NS_PER_SEC + start + 2 * NS_PER_SEC;
    service->mUidMap->updateApp(appUpgradeTimeNs, kApp1, 1, 2, "v2", "", /* certificateHash */ {});

    ConfigMetricsReport report =
            getReports(service->mProcessor, 5 * 60 * NS_PER_SEC + start + 100 * NS_PER_SEC);
    backfillStartEndTimestamp(&report);

    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(0, report.metrics(0).value_metrics().skipped_size());

    // The fake subsystem state sleep puller returns two atoms.
    ASSERT_EQ(2, report.metrics(0).value_metrics().data_size());
    ASSERT_EQ(2, report.metrics(0).value_metrics().data(0).bucket_info_size());
    EXPECT_EQ(MillisToNano(NanoToMillis(appUpgradeTimeNs)),
              report.metrics(0).value_metrics().data(0).bucket_info(1).end_bucket_elapsed_nanos());
}

TEST_F(PartialBucketE2eTest, TestValueMetricWithMinPartialBucket) {
    service->mPullerManager->RegisterPullAtomCallback(
            /*uid=*/0, util::SUBSYSTEM_SLEEP_STATE, NS_PER_SEC, NS_PER_SEC * 10, {},
            SharedRefBase::make<FakeSubsystemSleepCallback>());
    // Partial buckets don't occur when app is first installed.
    service->mUidMap->updateApp(1, kApp1, 1, 1, "v1", "", /* certificateHash */ {});
    sendConfig(MakeValueMetricConfig(60 * NS_PER_SEC /* One minute */));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    const int64_t endSkipped = 5 * 60 * NS_PER_SEC + start + 2 * NS_PER_SEC;
    service->mProcessor->informPullAlarmFired(5 * 60 * NS_PER_SEC + start);
    service->mUidMap->updateApp(endSkipped, kApp1, 1, 2, "v2", "", /* certificateHash */ {});

    ConfigMetricsReport report =
            getReports(service->mProcessor, 5 * 60 * NS_PER_SEC + start + 100 * NS_PER_SEC);
    backfillStartEndTimestamp(&report);

    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(1, report.metrics(0).value_metrics().skipped_size());
    EXPECT_TRUE(report.metrics(0).value_metrics().skipped(0).has_start_bucket_elapsed_nanos());
    // Can't test the start time since it will be based on the actual time when the pulling occurs.
    EXPECT_EQ(MillisToNano(NanoToMillis(endSkipped)),
              report.metrics(0).value_metrics().skipped(0).end_bucket_elapsed_nanos());

    ASSERT_EQ(2, report.metrics(0).value_metrics().data_size());
    ASSERT_EQ(1, report.metrics(0).value_metrics().data(0).bucket_info_size());
}

TEST_F(PartialBucketE2eTest, TestValueMetricOnBootWithoutMinPartialBucket) {
    // Initial pull will fail since puller is not registered.
    sendConfig(MakeValueMetricConfig(0));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    service->mPullerManager->RegisterPullAtomCallback(
            /*uid=*/0, util::SUBSYSTEM_SLEEP_STATE, NS_PER_SEC, NS_PER_SEC * 10, {},
            SharedRefBase::make<FakeSubsystemSleepCallback>());

    int64_t bootCompleteTimeNs = start + NS_PER_SEC;
    service->mProcessor->onStatsdInitCompleted(bootCompleteTimeNs);

    service->mProcessor->informPullAlarmFired(5 * 60 * NS_PER_SEC + start);

    ConfigMetricsReport report = getReports(service->mProcessor, 5 * 60 * NS_PER_SEC + start + 100);
    backfillStartEndTimestamp(&report);

    // First bucket is dropped due to the initial pull failing
    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(1, report.metrics(0).value_metrics().skipped_size());
    EXPECT_EQ(MillisToNano(NanoToMillis(bootCompleteTimeNs)),
              report.metrics(0).value_metrics().skipped(0).end_bucket_elapsed_nanos());

    // The fake subsystem state sleep puller returns two atoms.
    ASSERT_EQ(2, report.metrics(0).value_metrics().data_size());
    ASSERT_EQ(1, report.metrics(0).value_metrics().data(0).bucket_info_size());
    EXPECT_EQ(
            MillisToNano(NanoToMillis(bootCompleteTimeNs)),
            report.metrics(0).value_metrics().data(0).bucket_info(0).start_bucket_elapsed_nanos());
}

TEST_F(PartialBucketE2eTest, TestGaugeMetricWithoutMinPartialBucket) {
    service->mPullerManager->RegisterPullAtomCallback(
            /*uid=*/0, util::SUBSYSTEM_SLEEP_STATE, NS_PER_SEC, NS_PER_SEC * 10, {},
            SharedRefBase::make<FakeSubsystemSleepCallback>());
    // Partial buckets don't occur when app is first installed.
    service->mUidMap->updateApp(1, kApp1, 1, 1, "v1", "", /* certificateHash */ {});
    sendConfig(MakeGaugeMetricConfig(0));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    service->mProcessor->informPullAlarmFired(5 * 60 * NS_PER_SEC + start);
    service->mUidMap->updateApp(5 * 60 * NS_PER_SEC + start + 2, kApp1, 1, 2, "v2", "",
                                /* certificateHash */ {});

    ConfigMetricsReport report = getReports(service->mProcessor, 5 * 60 * NS_PER_SEC + start + 100);
    backfillStartEndTimestamp(&report);
    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(0, report.metrics(0).gauge_metrics().skipped_size());
    // The fake subsystem state sleep puller returns two atoms.
    ASSERT_EQ(2, report.metrics(0).gauge_metrics().data_size());
    ASSERT_EQ(2, report.metrics(0).gauge_metrics().data(0).bucket_info_size());
}

TEST_F(PartialBucketE2eTest, TestGaugeMetricWithMinPartialBucket) {
    // Partial buckets don't occur when app is first installed.
    service->mUidMap->updateApp(1, kApp1, 1, 1, "v1", "", /* certificateHash */ {});
    service->mPullerManager->RegisterPullAtomCallback(
            /*uid=*/0, util::SUBSYSTEM_SLEEP_STATE, NS_PER_SEC, NS_PER_SEC * 10, {},
            SharedRefBase::make<FakeSubsystemSleepCallback>());
    sendConfig(MakeGaugeMetricConfig(60 * NS_PER_SEC /* One minute */));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    const int64_t endSkipped = 5 * 60 * NS_PER_SEC + start + 2;
    service->mProcessor->informPullAlarmFired(5 * 60 * NS_PER_SEC + start);
    service->mUidMap->updateApp(endSkipped, kApp1, 1, 2, "v2", "", /* certificateHash */ {});

    ConfigMetricsReport report =
            getReports(service->mProcessor, 5 * 60 * NS_PER_SEC + start + 100 * NS_PER_SEC);
    backfillStartEndTimestamp(&report);
    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(1, report.metrics(0).gauge_metrics().skipped_size());
    // Can't test the start time since it will be based on the actual time when the pulling occurs.
    EXPECT_TRUE(report.metrics(0).gauge_metrics().skipped(0).has_start_bucket_elapsed_nanos());
    EXPECT_EQ(MillisToNano(NanoToMillis(endSkipped)),
              report.metrics(0).gauge_metrics().skipped(0).end_bucket_elapsed_nanos());
    ASSERT_EQ(2, report.metrics(0).gauge_metrics().data_size());
    ASSERT_EQ(1, report.metrics(0).gauge_metrics().data(0).bucket_info_size());
}

TEST_F(PartialBucketE2eTest, TestGaugeMetricOnBootWithoutMinPartialBucket) {
    // Initial pull will fail since puller hasn't been registered.
    sendConfig(MakeGaugeMetricConfig(0));
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.

    service->mPullerManager->RegisterPullAtomCallback(
            /*uid=*/0, util::SUBSYSTEM_SLEEP_STATE, NS_PER_SEC, NS_PER_SEC * 10, {},
            SharedRefBase::make<FakeSubsystemSleepCallback>());

    int64_t bootCompleteTimeNs = start + NS_PER_SEC;
    service->mProcessor->onStatsdInitCompleted(bootCompleteTimeNs);

    service->mProcessor->informPullAlarmFired(5 * 60 * NS_PER_SEC + start);

    ConfigMetricsReport report = getReports(service->mProcessor, 5 * 60 * NS_PER_SEC + start + 100);
    backfillStartEndTimestamp(&report);

    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(0, report.metrics(0).gauge_metrics().skipped_size());
    // The fake subsystem state sleep puller returns two atoms.
    ASSERT_EQ(2, report.metrics(0).gauge_metrics().data_size());
    // No data in the first bucket, so nothing is reported
    ASSERT_EQ(1, report.metrics(0).gauge_metrics().data(0).bucket_info_size());
    EXPECT_EQ(
            MillisToNano(NanoToMillis(bootCompleteTimeNs)),
            report.metrics(0).gauge_metrics().data(0).bucket_info(0).start_bucket_elapsed_nanos());
}

TEST_F(PartialBucketE2eTest, TestCountMetricNoSplitByDefault) {
    StatsdConfig config = MakeCountMetricConfig({nullopt});  // Do not set the value in the metric.
    sendConfig(config);
    int64_t start = getElapsedRealtimeNs();  // This is the start-time the metrics producers are
                                             // initialized with.
    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1, /*version*/ 1, "v1", kApp1);
    service->mUidMap->updateMap(start, uidData);

    // Force the uidmap to update at timestamp 2.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 1, 100).get());
    service->mUidMap->updateApp(start + 2, kApp1, 1, 2, "v2", "", /* certificateHash */ {});
    // Still goes into the first bucket.
    service->mProcessor->OnLogEvent(CreateAppCrashEvent(start + 3, 100).get());

    ConfigMetricsReport report =
            getReports(service->mProcessor, start + 4, /*include_current=*/true);
    backfillStartEndTimestamp(&report);

    ASSERT_EQ(1, report.metrics_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data_size());
    ASSERT_EQ(1, report.metrics(0).count_metrics().data(0).bucket_info_size());
    const CountBucketInfo& bucketInfo = report.metrics(0).count_metrics().data(0).bucket_info(0);
    EXPECT_EQ(bucketInfo.end_bucket_elapsed_nanos(), MillisToNano(NanoToMillis(start + 4)));
    EXPECT_EQ(bucketInfo.count(), 2);
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
