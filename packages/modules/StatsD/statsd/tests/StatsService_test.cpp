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

#include "StatsService.h"

#include <android/binder_interface_utils.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include "config/ConfigKey.h"
#include "packages/UidMap.h"
#include "src/statsd_config.pb.h"
#include "tests/statsd_test_util.h"

using namespace android;
using namespace testing;

namespace android {
namespace os {
namespace statsd {

using android::util::ProtoOutputStream;
using ::ndk::SharedRefBase;

#ifdef __ANDROID__

namespace {

const int64_t metricId = 123456;
const int32_t ATOM_TAG = util::SUBSYSTEM_SLEEP_STATE;

StatsdConfig CreateStatsdConfig(const GaugeMetric::SamplingType samplingType) {
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.
    auto atomMatcher = CreateSimpleAtomMatcher("TestMatcher", ATOM_TAG);
    *config.add_atom_matcher() = atomMatcher;
    *config.add_gauge_metric() =
            createGaugeMetric("GAUGE1", atomMatcher.id(), samplingType, nullopt, nullopt);
    config.set_hash_strings_in_metric_report(false);
    return config;
}

class FakeSubsystemSleepCallbackWithTiming : public FakeSubsystemSleepCallback {
public:
    Status onPullAtom(int atomTag,
                      const shared_ptr<IPullAtomResultReceiver>& resultReceiver) override {
        mPullTimeNs = getElapsedRealtimeNs();
        return FakeSubsystemSleepCallback::onPullAtom(atomTag, resultReceiver);
    }
    int64_t mPullTimeNs = 0;
};

}  // namespace

TEST(StatsServiceTest, TestAddConfig_simple) {
    const sp<UidMap> uidMap = new UidMap();
    shared_ptr<StatsService> service = SharedRefBase::make<StatsService>(
            uidMap, /* queue */ nullptr, std::make_shared<LogEventFilter>());
    const int kConfigKey = 12345;
    const int kCallingUid = 123;
    StatsdConfig config;
    config.set_id(kConfigKey);
    string serialized = config.SerializeAsString();

    EXPECT_TRUE(service->addConfigurationChecked(kCallingUid, kConfigKey,
                                                 {serialized.begin(), serialized.end()}));
    service->removeConfiguration(kConfigKey, kCallingUid);
    ConfigKey configKey(kCallingUid, kConfigKey);
    service->mProcessor->onDumpReport(configKey, getElapsedRealtimeNs(),
                                      false /* include_current_bucket*/, true /* erase_data */,
                                      ADB_DUMP, NO_TIME_CONSTRAINTS, nullptr);
}

TEST(StatsServiceTest, TestAddConfig_empty) {
    const sp<UidMap> uidMap = new UidMap();
    shared_ptr<StatsService> service = SharedRefBase::make<StatsService>(
            uidMap, /* queue */ nullptr, std::make_shared<LogEventFilter>());
    string serialized = "";
    const int kConfigKey = 12345;
    const int kCallingUid = 123;
    EXPECT_TRUE(service->addConfigurationChecked(kCallingUid, kConfigKey,
                                                 {serialized.begin(), serialized.end()}));
    service->removeConfiguration(kConfigKey, kCallingUid);
    ConfigKey configKey(kCallingUid, kConfigKey);
    service->mProcessor->onDumpReport(configKey, getElapsedRealtimeNs(),
                                      false /* include_current_bucket*/, true /* erase_data */,
                                      ADB_DUMP, NO_TIME_CONSTRAINTS, nullptr);
}

TEST(StatsServiceTest, TestAddConfig_invalid) {
    const sp<UidMap> uidMap = new UidMap();
    shared_ptr<StatsService> service = SharedRefBase::make<StatsService>(
            uidMap, /* queue */ nullptr, std::make_shared<LogEventFilter>());
    string serialized = "Invalid config!";

    EXPECT_FALSE(
            service->addConfigurationChecked(123, 12345, {serialized.begin(), serialized.end()}));
}

TEST(StatsServiceTest, TestGetUidFromArgs) {
    Vector<String8> args;
    args.push(String8("-1"));
    args.push(String8("0"));
    args.push(String8("1"));
    args.push(String8("a1"));
    args.push(String8(""));

    int32_t uid;

    const sp<UidMap> uidMap = new UidMap();
    shared_ptr<StatsService> service = SharedRefBase::make<StatsService>(
            uidMap, /* queue */ nullptr, std::make_shared<LogEventFilter>());
    service->mEngBuild = true;

    // "-1"
    EXPECT_FALSE(service->getUidFromArgs(args, 0, uid));

    // "0"
    EXPECT_TRUE(service->getUidFromArgs(args, 1, uid));
    EXPECT_EQ(0, uid);

    // "1"
    EXPECT_TRUE(service->getUidFromArgs(args, 2, uid));
    EXPECT_EQ(1, uid);

    // "a1"
    EXPECT_FALSE(service->getUidFromArgs(args, 3, uid));

    // ""
    EXPECT_FALSE(service->getUidFromArgs(args, 4, uid));

    // For a non-userdebug, uid "1" cannot be impersonated.
    service->mEngBuild = false;
    EXPECT_FALSE(service->getUidFromArgs(args, 2, uid));
}

class StatsServiceStatsdInitTest : public StatsServiceConfigTest,
                                   public testing::WithParamInterface<bool> {
public:
    StatsServiceStatsdInitTest() : kInitDelaySec(GetParam() ? 0 : 3) {
    }

    static std::string ToString(testing::TestParamInfo<bool> info) {
        return info.param ? "NoDelay" : "WithDelay";
    }

protected:
    const int kInitDelaySec = 0;

    shared_ptr<StatsService> createStatsService() override {
        return SharedRefBase::make<StatsService>(new UidMap(), /*queue=*/nullptr,
                                                 std::make_shared<LogEventFilter>(),
                                                 /*initEventDelaySecs=*/kInitDelaySec);
    }
};

INSTANTIATE_TEST_SUITE_P(StatsServiceStatsdInitTest, StatsServiceStatsdInitTest, testing::Bool(),
                         StatsServiceStatsdInitTest::ToString);

TEST_P(StatsServiceStatsdInitTest, StatsServiceStatsdInitTest) {
    // used for error threshold tolerance due to sleep() is involved
    const int64_t ERROR_THRESHOLD_NS = 25 * 1000000;  // 25 ms

    auto pullAtomCallback = SharedRefBase::make<FakeSubsystemSleepCallbackWithTiming>();

    // TODO: evaluate to use service->registerNativePullAtomCallback() API
    service->mPullerManager->RegisterPullAtomCallback(/*uid=*/0, ATOM_TAG, NS_PER_SEC,
                                                      NS_PER_SEC * 10, {}, pullAtomCallback);

    StatsdConfig config = CreateStatsdConfig(GaugeMetric::RANDOM_ONE_SAMPLE);
    config.set_id(kConfigKey);
    const int64_t createConfigTimeNs = getElapsedRealtimeNs();
    ASSERT_TRUE(sendConfig(config));
    ASSERT_EQ(2, pullAtomCallback->pullNum);

    service->mProcessor->mPullerManager->ForceClearPullerCache();

    const int64_t initCompletedTimeNs = getElapsedRealtimeNs();
    service->onStatsdInitCompleted();
    ASSERT_EQ(3, pullAtomCallback->pullNum);

    // Checking pull with or without delay according to the flag value
    const int64_t lastPullNs = pullAtomCallback->mPullTimeNs;

    if (GetParam()) {
        // when flag is defined - should be small delay between init & pull
        // expect delay smaller than 1 second
        EXPECT_GE(lastPullNs, initCompletedTimeNs);
        EXPECT_LE(lastPullNs, initCompletedTimeNs + ERROR_THRESHOLD_NS);
    } else {
        // when flag is not defined - big delay is expected (kInitDelaySec)
        EXPECT_GE(lastPullNs, initCompletedTimeNs + kInitDelaySec * NS_PER_SEC);
        EXPECT_LE(lastPullNs,
                  initCompletedTimeNs + kInitDelaySec * NS_PER_SEC + ERROR_THRESHOLD_NS);
    }

    const int64_t bucketSizeNs =
            TimeUnitToBucketSizeInMillis(config.gauge_metric(0).bucket()) * 1000000;
    const int64_t dumpReportTsNanos = createConfigTimeNs + bucketSizeNs + NS_PER_SEC;

    vector<uint8_t> output;
    ConfigKey configKey(kCallingUid, kConfigKey);
    service->mProcessor->onDumpReport(configKey, dumpReportTsNanos,
                                      /*include_current_bucket=*/false, /*erase_data=*/true,
                                      ADB_DUMP, FAST, &output);
    ConfigMetricsReportList reports;
    reports.ParseFromArray(output.data(), output.size());
    ASSERT_EQ(1, reports.reports_size());

    backfillDimensionPath(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics =
            reports.reports(0).metrics(0).gauge_metrics();
    ASSERT_EQ(gaugeMetrics.skipped_size(), 0);
    ASSERT_GT((int)gaugeMetrics.data_size(), 0);
    const auto data = gaugeMetrics.data(0);
    ASSERT_EQ(2, data.bucket_info_size());

    const auto bucketInfo0 = data.bucket_info(0);
    const auto bucketInfo1 = data.bucket_info(1);

    EXPECT_GE(NanoToMillis(bucketInfo0.start_bucket_elapsed_nanos()),
              NanoToMillis(createConfigTimeNs));
    EXPECT_LE(NanoToMillis(bucketInfo0.start_bucket_elapsed_nanos()),
              NanoToMillis(createConfigTimeNs + ERROR_THRESHOLD_NS));

    EXPECT_EQ(NanoToMillis(bucketInfo0.end_bucket_elapsed_nanos()),
              NanoToMillis(bucketInfo1.start_bucket_elapsed_nanos()));

    ASSERT_EQ(1, bucketInfo1.atom_size());
    ASSERT_GT(bucketInfo1.atom(0).subsystem_sleep_state().time_millis(), 0);

    EXPECT_GE(NanoToMillis(bucketInfo1.start_bucket_elapsed_nanos()),
              NanoToMillis(initCompletedTimeNs + kInitDelaySec * NS_PER_SEC));
    EXPECT_LE(NanoToMillis(bucketInfo1.start_bucket_elapsed_nanos()),
              NanoToMillis(initCompletedTimeNs + kInitDelaySec * NS_PER_SEC + ERROR_THRESHOLD_NS));

    // this check confirms that bucket end is not affected by the StatsService init delay
    EXPECT_EQ(NanoToMillis(bucketInfo1.end_bucket_elapsed_nanos()),
              NanoToMillis(service->mProcessor->mTimeBaseNs + bucketSizeNs));
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
