// Copyright (C) 2020 The Android Open Source Project
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

#include <aidl/android/os/StatsDimensionsValueParcel.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android/binder_interface_utils.h>
#include <gtest/gtest.h>

#include <thread>

#include "src/StatsLogProcessor.h"
#include "src/StatsService.h"
#include "src/storage/StorageManager.h"
#include "src/subscriber/SubscriberReporter.h"
#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

using aidl::android::os::StatsDimensionsValueParcel;
using android::base::SetProperty;
using android::base::StringPrintf;
using ::ndk::SharedRefBase;
using namespace std;

// Tests that only run with the partial config update feature turned on.
namespace {
void ValidateSubsystemSleepDimension(const DimensionsValue& value, string name) {
    EXPECT_EQ(value.field(), util::SUBSYSTEM_SLEEP_STATE);
    ASSERT_EQ(value.value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0).field(), 1 /* subsystem name field */);
    EXPECT_EQ(value.value_tuple().dimensions_value(0).value_str(), name);
}

sp<StatsLogProcessor> CreateStatsLogProcessor(
        const int64_t timeBaseNs, const int64_t currentTimeNs, const StatsdConfig& config,
        const ConfigKey& key, const shared_ptr<MockLogEventFilter>& logEventFilter) {
    // call from StatsLogProcessor constructor
    Expectation initCall =
            EXPECT_CALL(*logEventFilter, setAtomIds(StatsLogProcessor::getDefaultAtomIdSet(), _))
                    .Times(1);
    EXPECT_CALL(*logEventFilter, setAtomIds(CreateAtomIdSetFromConfig(config), _))
            .Times(1)
            .After(initCall);
    return CreateStatsLogProcessor(timeBaseNs, currentTimeNs, config, key, nullptr, 0, new UidMap(),
                                   logEventFilter);
}

}  // Anonymous namespace.

// Setup for test fixture.
class ConfigUpdateE2eTest : public testing::Test {
protected:
    std::shared_ptr<MockLogEventFilter> mLogEventFilter;

    void SetUp() override {
        mLogEventFilter = std::make_shared<MockLogEventFilter>();
    }
};

TEST_F(ConfigUpdateE2eTest, TestEventMetric) {
    StatsdConfig config;

    AtomMatcher syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;
    AtomMatcher wakelockAcquireMatcher = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockAcquireMatcher;
    AtomMatcher screenOnMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnMatcher;
    AtomMatcher screenOffMatcher = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = screenOffMatcher;
    AtomMatcher batteryPluggedUsbMatcher = CreateBatteryStateUsbMatcher();
    *config.add_atom_matcher() = batteryPluggedUsbMatcher;
    AtomMatcher unpluggedMatcher = CreateBatteryStateNoneMatcher();
    *config.add_atom_matcher() = unpluggedMatcher;

    AtomMatcher* combinationMatcher = config.add_atom_matcher();
    combinationMatcher->set_id(StringToId("SyncOrWakelockMatcher"));
    combinationMatcher->mutable_combination()->set_operation(LogicalOperation::OR);
    addMatcherToMatcherCombination(syncStartMatcher, combinationMatcher);
    addMatcherToMatcherCombination(wakelockAcquireMatcher, combinationMatcher);

    Predicate screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;
    Predicate unpluggedPredicate = CreateDeviceUnpluggedPredicate();
    *config.add_predicate() = unpluggedPredicate;

    Predicate* combinationPredicate = config.add_predicate();
    combinationPredicate->set_id(StringToId("ScreenOnOrUnpluggedPred)"));
    combinationPredicate->mutable_combination()->set_operation(LogicalOperation::OR);
    addPredicateToPredicateCombination(screenOnPredicate, combinationPredicate);
    addPredicateToPredicateCombination(unpluggedPredicate, combinationPredicate);

    EventMetric eventPersist =
            createEventMetric("SyncOrWlWhileScreenOnOrUnplugged", combinationMatcher->id(),
                              combinationPredicate->id());
    EventMetric eventChange = createEventMetric(
            "WakelockWhileScreenOn", wakelockAcquireMatcher.id(), screenOnPredicate.id());
    EventMetric eventRemove = createEventMetric("Syncs", syncStartMatcher.id(), nullopt);

    *config.add_event_metric() = eventRemove;
    *config.add_event_metric() = eventPersist;
    *config.add_event_metric() = eventChange;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);

    int app1Uid = 123;
    vector<int> attributionUids1 = {app1Uid};
    vector<string> attributionTags1 = {"App1"};

    // Initialize log events before update.
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 5 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl1"));  // Not kept.
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 10 * NS_PER_SEC,
            android::view::DISPLAY_STATE_ON));  // Condition true for change.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 15 * NS_PER_SEC, attributionUids1,
                                          attributionTags1,
                                          "sync1"));  // Kept for persist & remove.
    events.push_back(CreateBatteryStateChangedEvent(
            bucketStartTimeNs + 20 * NS_PER_SEC,
            BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE));  // Condition true for preserve.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 25 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl2"));  // Kept by persist and change.
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 30 * NS_PER_SEC,
            android::view::DISPLAY_STATE_OFF));  // Condition false for change.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 35 * NS_PER_SEC, attributionUids1,
                                          attributionTags1,
                                          "sync2"));  // Kept for persist & remove.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 40 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl3"));  // Kept by persist.

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Do update. Add matchers/conditions in different order to force indices to change.
    StatsdConfig newConfig;

    *newConfig.add_atom_matcher() = screenOnMatcher;
    *newConfig.add_atom_matcher() = batteryPluggedUsbMatcher;
    *newConfig.add_atom_matcher() = syncStartMatcher;
    *newConfig.add_atom_matcher() = *combinationMatcher;
    *newConfig.add_atom_matcher() = wakelockAcquireMatcher;
    *newConfig.add_atom_matcher() = screenOffMatcher;
    *newConfig.add_atom_matcher() = unpluggedMatcher;
    *newConfig.add_predicate() = *combinationPredicate;
    *newConfig.add_predicate() = unpluggedPredicate;
    *newConfig.add_predicate() = screenOnPredicate;

    // Add metrics. Note that the condition of eventChange will go from false to true.
    eventChange.set_condition(unpluggedPredicate.id());
    *newConfig.add_event_metric() = eventChange;
    EventMetric eventNew = createEventMetric("ScreenOn", screenOnMatcher.id(), nullopt);
    *newConfig.add_event_metric() = eventNew;
    *newConfig.add_event_metric() = eventPersist;

    int64_t updateTimeNs = bucketStartTimeNs + 60 * NS_PER_SEC;
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // Send events after the update.
    events.clear();
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 65 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl4"));  // Kept by preserve & change.
    events.push_back(CreateBatteryStateChangedEvent(
            bucketStartTimeNs + 70 * NS_PER_SEC,
            BatteryPluggedStateEnum::BATTERY_PLUGGED_USB));  // All conditions are false.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 75 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl5"));  // Not kept.
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 80 * NS_PER_SEC,
            android::view::DISPLAY_STATE_ON));  // Condition true for preserve, event kept by new.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 85 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl6"));  // Kept by preserve.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 90 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync3"));  // Kept by preserve.

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }
    uint64_t dumpTimeNs = bucketStartTimeNs + 100 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(reports.reports_size(), 2);

    // Report from before update.
    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 3);
    // Event remove. Captured sync events. There were 2 syncs before the update.
    StatsLogReport eventRemoveBefore = report.metrics(0);
    EXPECT_EQ(eventRemoveBefore.metric_id(), eventRemove.id());
    EXPECT_TRUE(eventRemoveBefore.has_event_metrics());
    ASSERT_EQ(eventRemoveBefore.event_metrics().data_size(), 2);
    auto data = eventRemoveBefore.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 15 * NS_PER_SEC);
    EXPECT_EQ(data.atom().sync_state_changed().sync_name(), "sync1");
    data = eventRemoveBefore.event_metrics().data(1);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 35 * NS_PER_SEC);
    EXPECT_EQ(data.atom().sync_state_changed().sync_name(), "sync2");

    // Captured wakelocks & syncs while screen on or unplugged. There were 2 wakelocks and 2 syncs.
    StatsLogReport eventPersistBefore = report.metrics(1);
    EXPECT_EQ(eventPersistBefore.metric_id(), eventPersist.id());
    EXPECT_TRUE(eventPersistBefore.has_event_metrics());
    ASSERT_EQ(eventPersistBefore.event_metrics().data_size(), 3);
    data = eventPersistBefore.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 25 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl2");
    data = eventPersistBefore.event_metrics().data(1);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 35 * NS_PER_SEC);
    EXPECT_EQ(data.atom().sync_state_changed().sync_name(), "sync2");
    data = eventPersistBefore.event_metrics().data(2);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 40 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl3");

    // Captured wakelock events while screen on. There was 1 before the update.
    StatsLogReport eventChangeBefore = report.metrics(2);
    EXPECT_EQ(eventChangeBefore.metric_id(), eventChange.id());
    EXPECT_TRUE(eventChangeBefore.has_event_metrics());
    ASSERT_EQ(eventChangeBefore.event_metrics().data_size(), 1);
    data = eventChangeBefore.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 25 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl2");

    // Report from after update.
    report = reports.reports(1);
    ASSERT_EQ(report.metrics_size(), 3);
    // Captured wakelocks while unplugged. There was 1 after the update.
    StatsLogReport eventChangeAfter = report.metrics(0);
    EXPECT_EQ(eventChangeAfter.metric_id(), eventChange.id());
    EXPECT_TRUE(eventChangeAfter.has_event_metrics());
    ASSERT_EQ(eventChangeAfter.event_metrics().data_size(), 1);
    data = eventChangeAfter.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 65 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl4");

    // Captured screen on events. There was 1 after the update.
    StatsLogReport eventNewAfter = report.metrics(1);
    EXPECT_EQ(eventNewAfter.metric_id(), eventNew.id());
    EXPECT_TRUE(eventNewAfter.has_event_metrics());
    ASSERT_EQ(eventNewAfter.event_metrics().data_size(), 1);
    data = eventNewAfter.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 80 * NS_PER_SEC);
    EXPECT_EQ(data.atom().screen_state_changed().state(), android::view::DISPLAY_STATE_ON);

    // There were 2 wakelocks and 1 sync after the update while the condition was true.
    StatsLogReport eventPersistAfter = report.metrics(2);
    EXPECT_EQ(eventPersistAfter.metric_id(), eventPersist.id());
    EXPECT_TRUE(eventPersistAfter.has_event_metrics());
    ASSERT_EQ(eventPersistAfter.event_metrics().data_size(), 3);
    data = eventPersistAfter.event_metrics().data(0);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 65 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl4");
    data = eventPersistAfter.event_metrics().data(1);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 85 * NS_PER_SEC);
    EXPECT_EQ(data.atom().wakelock_state_changed().tag(), "wl6");
    data = eventPersistAfter.event_metrics().data(2);
    EXPECT_EQ(data.elapsed_timestamp_nanos(), bucketStartTimeNs + 90 * NS_PER_SEC);
    EXPECT_EQ(data.atom().sync_state_changed().sync_name(), "sync3");
}

TEST_F(ConfigUpdateE2eTest, TestCountMetric) {
    StatsdConfig config;

    AtomMatcher syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;
    AtomMatcher wakelockAcquireMatcher = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockAcquireMatcher;
    AtomMatcher wakelockReleaseMatcher = CreateReleaseWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockReleaseMatcher;
    AtomMatcher screenOnMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnMatcher;
    AtomMatcher screenOffMatcher = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = screenOffMatcher;

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    // The predicate is dimensioning by first attribution node by uid.
    *holdingWakelockPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *config.add_predicate() = holdingWakelockPredicate;

    Predicate screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;

    Predicate* combination = config.add_predicate();
    combination->set_id(StringToId("ScreenOnAndHoldingWL)"));
    combination->mutable_combination()->set_operation(LogicalOperation::AND);
    addPredicateToPredicateCombination(screenOnPredicate, combination);
    addPredicateToPredicateCombination(holdingWakelockPredicate, combination);

    State uidProcessState = CreateUidProcessState();
    *config.add_state() = uidProcessState;

    CountMetric countPersist =
            createCountMetric("CountSyncPerUidWhileScreenOnHoldingWLSliceProcessState",
                              syncStartMatcher.id(), combination->id(), {uidProcessState.id()});
    *countPersist.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    // Links between sync state atom and condition of uid is holding wakelock.
    MetricConditionLink* links = countPersist.add_links();
    links->set_condition(holdingWakelockPredicate.id());
    *links->mutable_fields_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    *links->mutable_fields_in_condition() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    MetricStateLink* stateLink = countPersist.add_state_link();
    stateLink->set_state_atom_id(util::UID_PROCESS_STATE_CHANGED);
    *stateLink->mutable_fields_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    *stateLink->mutable_fields_in_state() =
            CreateDimensions(util::UID_PROCESS_STATE_CHANGED, {1 /*uid*/});

    CountMetric countChange = createCountMetric("Count*WhileScreenOn", syncStartMatcher.id(),
                                                screenOnPredicate.id(), {});
    CountMetric countRemove = createCountMetric("CountSync", syncStartMatcher.id(), nullopt, {});
    *config.add_count_metric() = countRemove;
    *config.add_count_metric() = countPersist;
    *config.add_count_metric() = countChange;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);

    int app1Uid = 123, app2Uid = 456;
    vector<int> attributionUids1 = {app1Uid};
    vector<string> attributionTags1 = {"App1"};
    vector<int> attributionUids2 = {app2Uid};
    vector<string> attributionTags2 = {"App2"};

    // Initialize log events before update. Counts are for countPersist since others are simpler.
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 2 * NS_PER_SEC, app1Uid,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND));
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 3 * NS_PER_SEC, app2Uid,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 5 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // Not counted.
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 10 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_ON));
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 15 * NS_PER_SEC,
                                                attributionUids1, attributionTags1, "wl1"));
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 20 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // Counted. uid1 = 1.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 21 * NS_PER_SEC, attributionUids2,
                                          attributionTags2, "sync_name"));  // Not counted.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 25 * NS_PER_SEC,
                                                attributionUids2, attributionTags2, "wl2"));
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 30 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // Counted. uid1 = 2.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 31 * NS_PER_SEC, attributionUids2,
                                          attributionTags2, "sync_name"));  // Counted. uid2 = 1
    events.push_back(CreateReleaseWakelockEvent(bucketStartTimeNs + 35 * NS_PER_SEC,
                                                attributionUids1, attributionTags1, "wl1"));
    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Do update. Add matchers/conditions in different order to force indices to change.
    StatsdConfig newConfig;

    *newConfig.add_atom_matcher() = screenOnMatcher;
    *newConfig.add_atom_matcher() = screenOffMatcher;
    *newConfig.add_atom_matcher() = syncStartMatcher;
    *newConfig.add_atom_matcher() = wakelockAcquireMatcher;
    *newConfig.add_atom_matcher() = wakelockReleaseMatcher;
    *newConfig.add_predicate() = *combination;
    *newConfig.add_predicate() = holdingWakelockPredicate;
    *newConfig.add_predicate() = screenOnPredicate;
    *newConfig.add_state() = uidProcessState;

    countChange.set_what(screenOnMatcher.id());
    *newConfig.add_count_metric() = countChange;
    CountMetric countNew = createCountMetric("CountWlWhileScreenOn", wakelockAcquireMatcher.id(),
                                             screenOnPredicate.id(), {});
    *newConfig.add_count_metric() = countNew;
    *newConfig.add_count_metric() = countPersist;

    int64_t updateTimeNs = bucketStartTimeNs + 60 * NS_PER_SEC;
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // Send events after the update. Counts reset to 0 since this is a new bucket.
    events.clear();
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 65 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // Not counted.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 66 * NS_PER_SEC, attributionUids2,
                                          attributionTags2, "sync_name"));  // Counted. uid2 = 1.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 70 * NS_PER_SEC,
                                                attributionUids1, attributionTags1, "wl1"));
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 75 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_OFF));
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 80 * NS_PER_SEC, attributionUids2,
                                          attributionTags2, "sync_name"));  // Not counted.
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 85 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_ON));
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 90 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // Counted. uid1 = 1.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 11 * NS_PER_SEC, attributionUids2,
                                          attributionTags2, "sync_name"));  // Counted. uid2 = 2.
    // Flushes bucket.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + bucketSizeNs + NS_PER_SEC,
                                                attributionUids1, attributionTags1, "wl2"));
    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }
    uint64_t dumpTimeNs = bucketStartTimeNs + bucketSizeNs + 10 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 2);

    // Report from before update.
    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 3);
    // Count syncs. There were 5 syncs before the update.
    StatsLogReport countRemoveBefore = report.metrics(0);
    EXPECT_EQ(countRemoveBefore.metric_id(), countRemove.id());
    EXPECT_TRUE(countRemoveBefore.has_count_metrics());
    ASSERT_EQ(countRemoveBefore.count_metrics().data_size(), 1);
    auto data = countRemoveBefore.count_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 5);

    // Uid 1 had 2 syncs, uid 2 had 1 sync.
    StatsLogReport countPersistBefore = report.metrics(1);
    EXPECT_EQ(countPersistBefore.metric_id(), countPersist.id());
    EXPECT_TRUE(countPersistBefore.has_count_metrics());
    StatsLogReport::CountMetricDataWrapper countMetrics;
    sortMetricDataByDimensionsValue(countPersistBefore.count_metrics(), &countMetrics);
    ASSERT_EQ(countMetrics.data_size(), 2);
    data = countMetrics.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app1Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 2);

    data = countMetrics.data(1);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app2Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 1);

    // Counts syncs while screen on. There were 4 before the update.
    StatsLogReport countChangeBefore = report.metrics(2);
    EXPECT_EQ(countChangeBefore.metric_id(), countChange.id());
    EXPECT_TRUE(countChangeBefore.has_count_metrics());
    ASSERT_EQ(countChangeBefore.count_metrics().data_size(), 1);
    data = countChangeBefore.count_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateCountBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 4, 50 * NS_PER_SEC);

    // Report from after update.
    report = reports.reports(1);
    ASSERT_EQ(report.metrics_size(), 3);
    // Count screen on while screen is on. There was 1 after the update.
    StatsLogReport countChangeAfter = report.metrics(0);
    EXPECT_EQ(countChangeAfter.metric_id(), countChange.id());
    EXPECT_TRUE(countChangeAfter.has_count_metrics());
    ASSERT_EQ(countChangeAfter.count_metrics().data_size(), 1);
    data = countChangeAfter.count_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateCountBucket(data.bucket_info(0), updateTimeNs, bucketStartTimeNs + bucketSizeNs, 1,
                        530 * NS_PER_SEC);

    // Count wl acquires while screen on. There were 2, one in each bucket.
    StatsLogReport countNewAfter = report.metrics(1);
    EXPECT_EQ(countNewAfter.metric_id(), countNew.id());
    EXPECT_TRUE(countNewAfter.has_count_metrics());
    ASSERT_EQ(countNewAfter.count_metrics().data_size(), 1);
    data = countNewAfter.count_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 2);
    ValidateCountBucket(data.bucket_info(0), updateTimeNs, bucketStartTimeNs + bucketSizeNs, 1,
                        530 * NS_PER_SEC);
    ValidateCountBucket(data.bucket_info(1), bucketStartTimeNs + bucketSizeNs, dumpTimeNs, 1,
                        10 * NS_PER_SEC);

    // Uid 1 had 1 sync, uid 2 had 2 syncs.
    StatsLogReport countPersistAfter = report.metrics(2);
    EXPECT_EQ(countPersistAfter.metric_id(), countPersist.id());
    EXPECT_TRUE(countPersistAfter.has_count_metrics());
    countMetrics.Clear();
    sortMetricDataByDimensionsValue(countPersistAfter.count_metrics(), &countMetrics);
    ASSERT_EQ(countMetrics.data_size(), 2);
    data = countMetrics.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app1Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateCountBucket(data.bucket_info(0), updateTimeNs, bucketStartTimeNs + bucketSizeNs, 1);

    data = countMetrics.data(1);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app2Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateCountBucket(data.bucket_info(0), updateTimeNs, bucketStartTimeNs + bucketSizeNs, 2);
}

TEST_F(ConfigUpdateE2eTest, TestDurationMetric) {
    StatsdConfig config;

    AtomMatcher syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;
    AtomMatcher syncStopMatcher = CreateSyncEndAtomMatcher();
    *config.add_atom_matcher() = syncStopMatcher;
    AtomMatcher wakelockAcquireMatcher = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockAcquireMatcher;
    AtomMatcher wakelockReleaseMatcher = CreateReleaseWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockReleaseMatcher;
    AtomMatcher screenOnMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnMatcher;
    AtomMatcher screenOffMatcher = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = screenOffMatcher;

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    // The predicate is dimensioning by first attribution node by uid.
    *holdingWakelockPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *config.add_predicate() = holdingWakelockPredicate;

    Predicate screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;

    Predicate syncPredicate = CreateIsSyncingPredicate();
    // The predicate is dimensioning by first attribution node by uid.
    *syncPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    *config.add_predicate() = syncPredicate;

    State uidProcessState = CreateUidProcessState();
    *config.add_state() = uidProcessState;

    DurationMetric durationSumPersist =
            createDurationMetric("DurSyncPerUidWhileHoldingWLSliceProcessState", syncPredicate.id(),
                                 holdingWakelockPredicate.id(), {uidProcessState.id()});
    *durationSumPersist.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    // Links between sync state atom and condition of uid is holding wakelock.
    MetricConditionLink* links = durationSumPersist.add_links();
    links->set_condition(holdingWakelockPredicate.id());
    *links->mutable_fields_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    *links->mutable_fields_in_condition() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    MetricStateLink* stateLink = durationSumPersist.add_state_link();
    stateLink->set_state_atom_id(util::UID_PROCESS_STATE_CHANGED);
    *stateLink->mutable_fields_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    *stateLink->mutable_fields_in_state() =
            CreateDimensions(util::UID_PROCESS_STATE_CHANGED, {1 /*uid*/});

    DurationMetric durationMaxPersist =
            createDurationMetric("DurMaxSyncPerUidWhileHoldingWL", syncPredicate.id(),
                                 holdingWakelockPredicate.id(), {});
    durationMaxPersist.set_aggregation_type(DurationMetric::MAX_SPARSE);
    *durationMaxPersist.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    // Links between sync state atom and condition of uid is holding wakelock.
    links = durationMaxPersist.add_links();
    links->set_condition(holdingWakelockPredicate.id());
    *links->mutable_fields_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});
    *links->mutable_fields_in_condition() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});

    DurationMetric durationChange = createDurationMetric("Dur*WhileScreenOn", syncPredicate.id(),
                                                         screenOnPredicate.id(), {});
    DurationMetric durationRemove =
            createDurationMetric("DurScreenOn", screenOnPredicate.id(), nullopt, {});
    *config.add_duration_metric() = durationMaxPersist;
    *config.add_duration_metric() = durationRemove;
    *config.add_duration_metric() = durationSumPersist;
    *config.add_duration_metric() = durationChange;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);

    int app1Uid = 123, app2Uid = 456, app3Uid = 789;
    vector<int> attributionUids1 = {app1Uid};
    vector<string> attributionTags1 = {"App1"};
    vector<int> attributionUids2 = {app2Uid};
    vector<string> attributionTags2 = {"App2"};
    vector<int> attributionUids3 = {app3Uid};
    vector<string> attributionTags3 = {"App3"};

    // Initialize log events before update. Comments provided for durations of persisted metrics.
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 2 * NS_PER_SEC, app1Uid,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND));
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 3 * NS_PER_SEC, app2Uid,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND));
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 5 * NS_PER_SEC, android::view::DisplayStateEnum::DISPLAY_STATE_ON));
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 6 * NS_PER_SEC, app3Uid,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND));
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 10 * NS_PER_SEC, attributionUids1,
                                          attributionTags1, "sync_name"));  // uid1 paused.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 15 * NS_PER_SEC,
                                                attributionUids2, attributionTags2,
                                                "wl2"));  // uid2 cond true.
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 20 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_OFF));
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 25 * NS_PER_SEC, attributionUids2,
                                          attributionTags2, "sync_name"));  // Uid 2 start t=25.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 27 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl1"));  // Uid 1 start t=27.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 35 * NS_PER_SEC, attributionUids3,
                                          attributionTags3, "sync_name"));  // Uid 3 paused.
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 40 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_ON));
    events.push_back(CreateSyncEndEvent(bucketStartTimeNs + 45 * NS_PER_SEC, attributionUids2,
                                        attributionTags2,
                                        "sync_name"));  // Uid 2 stop. sum/max = 20.
    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Do update. Add matchers/conditions in different order to force indices to change.
    StatsdConfig newConfig;

    *newConfig.add_atom_matcher() = wakelockReleaseMatcher;
    *newConfig.add_atom_matcher() = syncStopMatcher;
    *newConfig.add_atom_matcher() = screenOnMatcher;
    *newConfig.add_atom_matcher() = screenOffMatcher;
    *newConfig.add_atom_matcher() = syncStartMatcher;
    *newConfig.add_atom_matcher() = wakelockAcquireMatcher;
    *newConfig.add_predicate() = syncPredicate;
    *newConfig.add_predicate() = screenOnPredicate;
    *newConfig.add_predicate() = holdingWakelockPredicate;
    *newConfig.add_state() = uidProcessState;

    durationChange.set_what(holdingWakelockPredicate.id());
    *newConfig.add_duration_metric() = durationChange;
    DurationMetric durationNew =
            createDurationMetric("DurationSync", syncPredicate.id(), nullopt, {});
    *newConfig.add_duration_metric() = durationNew;
    *newConfig.add_duration_metric() = durationMaxPersist;
    *newConfig.add_duration_metric() = durationSumPersist;

    // At update, only uid 1 is syncing & holding a wakelock, duration=33. Max is paused for uid3.
    // Before the update, only uid2 will report a duration for max, since others are started/paused.
    int64_t updateTimeNs = bucketStartTimeNs + 60 * NS_PER_SEC;
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // Send events after the update.
    events.clear();
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 65 * NS_PER_SEC,
                                                attributionUids3, attributionTags3,
                                                "wl3"));  // Uid3 start t=65.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 70 * NS_PER_SEC, attributionUids2,
                                          attributionTags2, "sync_name"));  // Uid2 start t=70.
    events.push_back(CreateReleaseWakelockEvent(bucketStartTimeNs + 75 * NS_PER_SEC,
                                                attributionUids1, attributionTags1,
                                                "wl1"));  // Uid1 Pause. Dur = 15.
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 81 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_OFF));
    events.push_back(CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 85 * NS_PER_SEC, app3Uid,
            android::app::ProcessStateEnum::
                    PROCESS_STATE_IMPORTANT_FOREGROUND));  // Uid3 Sum in BG: 20. FG starts. Max is
                                                           // unchanged.
    events.push_back(CreateSyncEndEvent(bucketStartTimeNs + 90 * NS_PER_SEC, attributionUids3,
                                        attributionTags3,
                                        "sync_name"));  // Uid3 stop. Sum in FG: 5. MAX: 25.

    // Flushes bucket. Sum: uid1=15, uid2=bucketSize - 70, uid3 = 20 in FG, 5 in BG. Max: uid3=25,
    // others don't report.
    events.push_back(CreateSyncEndEvent(bucketStartTimeNs + bucketSizeNs + NS_PER_SEC,
                                        attributionUids1, attributionTags1,
                                        "sync_name"));  // Uid1 stop. Max=15+33=48, Sum is 0.

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }
    uint64_t bucketEndTimeNs = bucketStartTimeNs + bucketSizeNs;
    uint64_t dumpTimeNs = bucketStartTimeNs + bucketSizeNs + 10 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    // In the partial bucket, Sum for uid2 = 10, Max for Uid1 = 48. Rest are unreported.
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 2);

    // Report from before update.
    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 4);
    // Max duration of syncs per uid while uid holding WL. Only uid2 should have data.
    StatsLogReport durationMaxPersistBefore = report.metrics(0);
    EXPECT_EQ(durationMaxPersistBefore.metric_id(), durationMaxPersist.id());
    EXPECT_TRUE(durationMaxPersistBefore.has_duration_metrics());
    StatsLogReport::DurationMetricDataWrapper durationMetrics;
    sortMetricDataByDimensionsValue(durationMaxPersistBefore.duration_metrics(), &durationMetrics);
    ASSERT_EQ(durationMetrics.data_size(), 1);
    auto data = durationMetrics.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app2Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 20 * NS_PER_SEC);

    // Screen on time. ON: 5, OFF: 20, ON: 40. Update: 60. Result: 35
    StatsLogReport durationRemoveBefore = report.metrics(1);
    EXPECT_EQ(durationRemoveBefore.metric_id(), durationRemove.id());
    EXPECT_TRUE(durationRemoveBefore.has_duration_metrics());
    durationMetrics.Clear();
    sortMetricDataByDimensionsValue(durationRemoveBefore.duration_metrics(), &durationMetrics);
    ASSERT_EQ(durationMetrics.data_size(), 1);
    data = durationMetrics.data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 35 * NS_PER_SEC);

    // Duration of syncs per uid while uid holding WL, slice screen. Uid1=33, uid2=20.
    StatsLogReport durationSumPersistBefore = report.metrics(2);
    EXPECT_EQ(durationSumPersistBefore.metric_id(), durationSumPersist.id());
    EXPECT_TRUE(durationSumPersistBefore.has_duration_metrics());
    durationMetrics.Clear();
    sortMetricDataByDimensionsValue(durationSumPersistBefore.duration_metrics(), &durationMetrics);
    ASSERT_EQ(durationMetrics.data_size(), 2);
    data = durationMetrics.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app1Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 33 * NS_PER_SEC);

    data = durationMetrics.data(1);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app2Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 20 * NS_PER_SEC);

    // Duration of syncs while screen on. Start: 10, pause: 20, start: 40 Update: 60. Total: 30.
    StatsLogReport durationChangeBefore = report.metrics(3);
    EXPECT_EQ(durationChangeBefore.metric_id(), durationChange.id());
    EXPECT_TRUE(durationChangeBefore.has_duration_metrics());
    durationMetrics.Clear();
    sortMetricDataByDimensionsValue(durationChangeBefore.duration_metrics(), &durationMetrics);
    ASSERT_EQ(durationMetrics.data_size(), 1);
    data = durationMetrics.data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), bucketStartTimeNs, updateTimeNs, 30 * NS_PER_SEC,
                           35000000000);

    // Report from after update.
    report = reports.reports(1);
    ASSERT_EQ(report.metrics_size(), 4);
    // Duration of WL while screen on. Update: 60, pause: 81. Total: 21.
    StatsLogReport durationChangeAfter = report.metrics(0);
    EXPECT_EQ(durationChangeAfter.metric_id(), durationChange.id());
    EXPECT_TRUE(durationChangeAfter.has_duration_metrics());
    durationMetrics.Clear();
    sortMetricDataByDimensionsValue(durationChangeAfter.duration_metrics(), &durationMetrics);
    ASSERT_EQ(durationMetrics.data_size(), 1);
    data = durationMetrics.data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), updateTimeNs, bucketEndTimeNs, 21 * NS_PER_SEC,
                           21000000000);

    // Duration of syncs. Always true since at least 1 uid is always syncing.
    StatsLogReport durationNewAfter = report.metrics(1);
    EXPECT_EQ(durationNewAfter.metric_id(), durationNew.id());
    EXPECT_TRUE(durationNewAfter.has_duration_metrics());
    durationMetrics.Clear();
    sortMetricDataByDimensionsValue(durationNewAfter.duration_metrics(), &durationMetrics);
    ASSERT_EQ(durationMetrics.data_size(), 1);
    data = durationMetrics.data(0);
    ASSERT_EQ(data.bucket_info_size(), 2);
    ValidateDurationBucket(data.bucket_info(0), updateTimeNs, bucketEndTimeNs,
                           bucketEndTimeNs - updateTimeNs);
    ValidateDurationBucket(data.bucket_info(1), bucketEndTimeNs, dumpTimeNs,
                           dumpTimeNs - bucketEndTimeNs);

    // Max duration of syncs per uid while uid holding WL.
    StatsLogReport durationMaxPersistAfter = report.metrics(2);
    EXPECT_EQ(durationMaxPersistAfter.metric_id(), durationMaxPersist.id());
    EXPECT_TRUE(durationMaxPersistAfter.has_duration_metrics());
    durationMetrics.Clear();
    sortMetricDataByDimensionsValue(durationMaxPersistAfter.duration_metrics(), &durationMetrics);
    ASSERT_EQ(durationMetrics.data_size(), 2);

    // Uid 1. Duration = 48 in the later bucket.
    data = durationMetrics.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app1Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), bucketEndTimeNs, dumpTimeNs, 48 * NS_PER_SEC);

    // Uid 3. Duration = 25.
    data = durationMetrics.data(1);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app3Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), updateTimeNs, bucketEndTimeNs, 25 * NS_PER_SEC);

    // Duration of syncs per uid while uid holding WL, slice screen.
    StatsLogReport durationSumPersistAfter = report.metrics(3);
    EXPECT_EQ(durationSumPersistAfter.metric_id(), durationSumPersist.id());
    EXPECT_TRUE(durationSumPersistAfter.has_duration_metrics());
    durationMetrics.Clear();
    sortMetricDataByDimensionsValue(durationSumPersistAfter.duration_metrics(), &durationMetrics);
    ASSERT_EQ(durationMetrics.data_size(), 4);

    // Uid 1 in BG. Duration = 15.
    data = durationMetrics.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app1Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), updateTimeNs, bucketEndTimeNs, 15 * NS_PER_SEC);

    // Uid 2 in FG. Duration = bucketSize - 70 in first bucket, 10 in second bucket.
    data = durationMetrics.data(1);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app2Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    ASSERT_EQ(data.bucket_info_size(), 2);
    ValidateDurationBucket(data.bucket_info(0), updateTimeNs, bucketEndTimeNs,
                           bucketSizeNs - 70 * NS_PER_SEC);
    ValidateDurationBucket(data.bucket_info(1), bucketEndTimeNs, dumpTimeNs, 10 * NS_PER_SEC);

    // Uid 3 in FG. Duration = 5.
    data = durationMetrics.data(2);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app3Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), updateTimeNs, bucketEndTimeNs, 5 * NS_PER_SEC);

    // Uid 3 in BG. Duration = 20.
    data = durationMetrics.data(3);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::SYNC_STATE_CHANGED, app3Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateDurationBucket(data.bucket_info(0), updateTimeNs, bucketEndTimeNs, 20 * NS_PER_SEC);
}

TEST_F(ConfigUpdateE2eTest, TestGaugeMetric) {
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.

    AtomMatcher appStartMatcher = CreateSimpleAtomMatcher("AppStart", util::APP_START_OCCURRED);
    *config.add_atom_matcher() = appStartMatcher;
    AtomMatcher backgroundMatcher = CreateMoveToBackgroundAtomMatcher();
    *config.add_atom_matcher() = backgroundMatcher;
    AtomMatcher foregroundMatcher = CreateMoveToForegroundAtomMatcher();
    *config.add_atom_matcher() = foregroundMatcher;
    AtomMatcher screenOnMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnMatcher;
    AtomMatcher screenOffMatcher = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = screenOffMatcher;
    AtomMatcher subsystemSleepMatcher =
            CreateSimpleAtomMatcher("SubsystemSleep", util::SUBSYSTEM_SLEEP_STATE);
    *config.add_atom_matcher() = subsystemSleepMatcher;

    Predicate isInBackgroundPredicate = CreateIsInBackgroundPredicate();
    *isInBackgroundPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateDimensions(util::ACTIVITY_FOREGROUND_STATE_CHANGED, {1 /*uid field*/});
    *config.add_predicate() = isInBackgroundPredicate;

    Predicate screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;

    GaugeMetric gaugePullPersist =
            createGaugeMetric("SubsystemSleepWhileScreenOn", subsystemSleepMatcher.id(),
                              GaugeMetric::RANDOM_ONE_SAMPLE, screenOnPredicate.id(), {});
    *gaugePullPersist.mutable_dimensions_in_what() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {1 /* subsystem name */});

    GaugeMetric gaugePushPersist =
            createGaugeMetric("AppStartWhileInBg", appStartMatcher.id(),
                              GaugeMetric::FIRST_N_SAMPLES, isInBackgroundPredicate.id(), nullopt);
    *gaugePushPersist.mutable_dimensions_in_what() =
            CreateDimensions(util::APP_START_OCCURRED, {1 /*uid field*/});
    // Links between sync state atom and condition of uid is holding wakelock.
    MetricConditionLink* links = gaugePushPersist.add_links();
    links->set_condition(isInBackgroundPredicate.id());
    *links->mutable_fields_in_what() =
            CreateDimensions(util::APP_START_OCCURRED, {1 /*uid field*/});
    *links->mutable_fields_in_condition() =
            CreateDimensions(util::ACTIVITY_FOREGROUND_STATE_CHANGED, {1 /*uid field*/});

    GaugeMetric gaugeChange = createGaugeMetric("GaugeScrOn", screenOnMatcher.id(),
                                                GaugeMetric::RANDOM_ONE_SAMPLE, nullopt, nullopt);
    GaugeMetric gaugeRemove =
            createGaugeMetric("GaugeSubsysTriggerScr", subsystemSleepMatcher.id(),
                              GaugeMetric::FIRST_N_SAMPLES, nullopt, screenOnMatcher.id());
    *gaugeRemove.mutable_dimensions_in_what() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {1 /* subsystem name */});
    *config.add_gauge_metric() = gaugeRemove;
    *config.add_gauge_metric() = gaugePullPersist;
    *config.add_gauge_metric() = gaugeChange;
    *config.add_gauge_metric() = gaugePushPersist;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = getElapsedRealtimeNs();  // 0:10
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
    // call from StatsLogProcessor constructor
    Expectation initCall =
            EXPECT_CALL(*mLogEventFilter, setAtomIds(StatsLogProcessor::getDefaultAtomIdSet(), _))
                    .Times(1);
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(config), _))
            .Times(1)
            .After(initCall);
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key,
                                    SharedRefBase::make<FakeSubsystemSleepCallback>(),
                                    util::SUBSYSTEM_SLEEP_STATE, new UidMap(), mLogEventFilter);

    int app1Uid = 123, app2Uid = 456;

    // Initialize log events before update.
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateMoveToBackgroundEvent(bucketStartTimeNs + 5 * NS_PER_SEC, app1Uid));
    events.push_back(CreateAppStartOccurredEvent(bucketStartTimeNs + 10 * NS_PER_SEC, app1Uid,
                                                 "app1", AppStartOccurred::WARM, "", "", true,
                                                 /*start_msec*/ 101));  // Kept by gaugePushPersist.
    events.push_back(
            CreateAppStartOccurredEvent(bucketStartTimeNs + 15 * NS_PER_SEC, app2Uid, "app2",
                                        AppStartOccurred::WARM, "", "", true,
                                        /*start_msec*/ 201));  // Not kept by gaugePushPersist.
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 20 * NS_PER_SEC,
            android::view::DISPLAY_STATE_ON));  // Pulls gaugePullPersist and gaugeRemove.
    events.push_back(CreateMoveToBackgroundEvent(bucketStartTimeNs + 25 * NS_PER_SEC, app2Uid));
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 30 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_OFF));
    events.push_back(CreateAppStartOccurredEvent(bucketStartTimeNs + 35 * NS_PER_SEC, app1Uid,
                                                 "app1", AppStartOccurred::WARM, "", "", true,
                                                 /*start_msec*/ 102));  // Kept by gaugePushPersist.
    events.push_back(CreateAppStartOccurredEvent(bucketStartTimeNs + 40 * NS_PER_SEC, app2Uid,
                                                 "app2", AppStartOccurred::WARM, "", "", true,
                                                 /*start_msec*/ 202));  // Kept by gaugePushPersist.
    events.push_back(CreateScreenStateChangedEvent(
            bucketStartTimeNs + 45 * NS_PER_SEC,
            android::view::DisplayStateEnum::DISPLAY_STATE_ON));  // Pulls gaugeRemove only.
    events.push_back(CreateMoveToForegroundEvent(bucketStartTimeNs + 50 * NS_PER_SEC, app1Uid));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->mPullerManager->ForceClearPullerCache();
        processor->OnLogEvent(event.get());
    }
    processor->mPullerManager->ForceClearPullerCache();

    // Do the update. Add matchers/conditions in different order to force indices to change.
    StatsdConfig newConfig;
    newConfig.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.

    *newConfig.add_atom_matcher() = screenOffMatcher;
    *newConfig.add_atom_matcher() = foregroundMatcher;
    *newConfig.add_atom_matcher() = appStartMatcher;
    *newConfig.add_atom_matcher() = subsystemSleepMatcher;
    *newConfig.add_atom_matcher() = backgroundMatcher;
    *newConfig.add_atom_matcher() = screenOnMatcher;

    *newConfig.add_predicate() = isInBackgroundPredicate;
    *newConfig.add_predicate() = screenOnPredicate;

    gaugeChange.set_sampling_type(GaugeMetric::FIRST_N_SAMPLES);
    *newConfig.add_gauge_metric() = gaugeChange;
    GaugeMetric gaugeNew = createGaugeMetric("GaugeSubsys", subsystemSleepMatcher.id(),
                                             GaugeMetric::RANDOM_ONE_SAMPLE, {}, {});
    *gaugeNew.mutable_dimensions_in_what() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {1 /* subsystem name */});
    *newConfig.add_gauge_metric() = gaugeNew;
    *newConfig.add_gauge_metric() = gaugePushPersist;
    *newConfig.add_gauge_metric() = gaugePullPersist;

    int64_t updateTimeNs = bucketStartTimeNs + 60 * NS_PER_SEC;
    // Update pulls gaugePullPersist and gaugeNew.
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), _)).Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // Verify puller manager is properly set.
    sp<StatsPullerManager> pullerManager = processor->mPullerManager;
    EXPECT_EQ(pullerManager->mNextPullTimeNs, bucketStartTimeNs + bucketSizeNs);
    ASSERT_EQ(pullerManager->mReceivers.size(), 1);
    ASSERT_EQ(pullerManager->mReceivers.begin()->second.size(), 2);

    // Send events after the update. Counts reset to 0 since this is a new bucket.
    events.clear();
    events.push_back(
            CreateAppStartOccurredEvent(bucketStartTimeNs + 65 * NS_PER_SEC, app1Uid, "app1",
                                        AppStartOccurred::WARM, "", "", true,
                                        /*start_msec*/ 103));  // Not kept by gaugePushPersist.
    events.push_back(CreateAppStartOccurredEvent(bucketStartTimeNs + 70 * NS_PER_SEC, app2Uid,
                                                 "app2", AppStartOccurred::WARM, "", "", true,
                                                 /*start_msec*/ 203));  // Kept by gaugePushPersist.
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 75 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_OFF));
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 80 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_ON));
    events.push_back(CreateMoveToBackgroundEvent(bucketStartTimeNs + 85 * NS_PER_SEC, app1Uid));
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 90 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_OFF));
    events.push_back(CreateAppStartOccurredEvent(bucketStartTimeNs + 95 * NS_PER_SEC, app1Uid,
                                                 "app1", AppStartOccurred::WARM, "", "", true,
                                                 /*start_msec*/ 104));  // Kept by gaugePushPersist.
    events.push_back(CreateAppStartOccurredEvent(bucketStartTimeNs + 100 * NS_PER_SEC, app2Uid,
                                                 "app2", AppStartOccurred::WARM, "", "", true,
                                                 /*start_msec*/ 204));  // Kept by gaugePushPersist.
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 105 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_ON));
    events.push_back(
            CreateScreenStateChangedEvent(bucketStartTimeNs + 110 * NS_PER_SEC,
                                          android::view::DisplayStateEnum::DISPLAY_STATE_OFF));
    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->mPullerManager->ForceClearPullerCache();
        processor->OnLogEvent(event.get());
    }
    processor->mPullerManager->ForceClearPullerCache();
    // Pulling alarm arrive, triggering a bucket split. Only gaugeNew keeps the data since the
    // condition is false for gaugeNew.
    processor->informPullAlarmFired(bucketStartTimeNs + bucketSizeNs);

    uint64_t dumpTimeNs = bucketStartTimeNs + bucketSizeNs + 10 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    backfillAggregatedAtoms(&reports);
    ASSERT_EQ(reports.reports_size(), 2);

    int64_t roundedBucketStartNs = MillisToNano(NanoToMillis(bucketStartTimeNs));
    int64_t roundedUpdateTimeNs = MillisToNano(NanoToMillis(updateTimeNs));
    int64_t roundedBucketEndNs = MillisToNano(NanoToMillis(bucketStartTimeNs + bucketSizeNs));
    int64_t roundedDumpTimeNs = MillisToNano(NanoToMillis(dumpTimeNs));

    // Report from before update.
    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 4);
    // Gauge subsystem sleep state trigger screen on. 2 pulls occurred.
    StatsLogReport gaugeRemoveBefore = report.metrics(0);
    EXPECT_EQ(gaugeRemoveBefore.metric_id(), gaugeRemove.id());
    EXPECT_TRUE(gaugeRemoveBefore.has_gauge_metrics());
    StatsLogReport::GaugeMetricDataWrapper gaugeMetrics;
    sortMetricDataByDimensionsValue(gaugeRemoveBefore.gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 2);
    auto data = gaugeMetrics.data(0);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_1");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs,
                             {(int64_t)(bucketStartTimeNs + 20 * NS_PER_SEC),
                              (int64_t)(bucketStartTimeNs + 45 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 2);
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 101);
    EXPECT_EQ(data.bucket_info(0).atom(1).subsystem_sleep_state().time_millis(), 401);

    data = gaugeMetrics.data(1);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_2");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs,
                             {(int64_t)(bucketStartTimeNs + 20 * NS_PER_SEC),
                              (int64_t)(bucketStartTimeNs + 45 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 2);
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 102);
    EXPECT_EQ(data.bucket_info(0).atom(1).subsystem_sleep_state().time_millis(), 402);

    // Gauge subsystem sleep state when screen is on. One pull when the screen turned on
    StatsLogReport gaugePullPersistBefore = report.metrics(1);
    EXPECT_EQ(gaugePullPersistBefore.metric_id(), gaugePullPersist.id());
    EXPECT_TRUE(gaugePullPersistBefore.has_gauge_metrics());
    gaugeMetrics.Clear();
    sortMetricDataByDimensionsValue(gaugePullPersistBefore.gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 2);
    data = gaugeMetrics.data(0);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_1");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs,
                             {(int64_t)(bucketStartTimeNs + 20 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 101);

    data = gaugeMetrics.data(1);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_2");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs,
                             {(int64_t)(bucketStartTimeNs + 20 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 102);

    // Gauge screen on events, one per bucket.
    StatsLogReport gaugeChangeBefore = report.metrics(2);
    EXPECT_EQ(gaugeChangeBefore.metric_id(), gaugeChange.id());
    EXPECT_TRUE(gaugeChangeBefore.has_gauge_metrics());
    gaugeMetrics.Clear();
    sortMetricDataByDimensionsValue(gaugeChangeBefore.gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 1);
    data = gaugeMetrics.data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs,
                             {(int64_t)(bucketStartTimeNs + 20 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).screen_state_changed().state(),
              android::view::DISPLAY_STATE_ON);

    // Gauge app start while app is in the background. App 1 started twice, app 2 started once.
    StatsLogReport gaugePushPersistBefore = report.metrics(3);
    EXPECT_EQ(gaugePushPersistBefore.metric_id(), gaugePushPersist.id());
    EXPECT_TRUE(gaugePushPersistBefore.has_gauge_metrics());
    gaugeMetrics.Clear();
    sortMetricDataByDimensionsValue(gaugePushPersistBefore.gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 2);
    data = gaugeMetrics.data(0);
    ValidateUidDimension(data.dimensions_in_what(), util::APP_START_OCCURRED, app1Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs,
                             {(int64_t)(bucketStartTimeNs + 10 * NS_PER_SEC),
                              (int64_t)(bucketStartTimeNs + 35 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 2);
    EXPECT_EQ(data.bucket_info(0).atom(0).app_start_occurred().pkg_name(), "app1");
    EXPECT_EQ(data.bucket_info(0).atom(0).app_start_occurred().activity_start_millis(), 101);
    EXPECT_EQ(data.bucket_info(0).atom(1).app_start_occurred().pkg_name(), "app1");
    EXPECT_EQ(data.bucket_info(0).atom(1).app_start_occurred().activity_start_millis(), 102);

    data = gaugeMetrics.data(1);
    ValidateUidDimension(data.dimensions_in_what(), util::APP_START_OCCURRED, app2Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs,
                             {(int64_t)(bucketStartTimeNs + 40 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).app_start_occurred().pkg_name(), "app2");
    EXPECT_EQ(data.bucket_info(0).atom(0).app_start_occurred().activity_start_millis(), 202);

    // Report from after update.
    report = reports.reports(1);
    ASSERT_EQ(report.metrics_size(), 4);
    // Gauge screen on events FIRST_N_SAMPLES. There were 2.
    StatsLogReport gaugeChangeAfter = report.metrics(0);
    EXPECT_EQ(gaugeChangeAfter.metric_id(), gaugeChange.id());
    EXPECT_TRUE(gaugeChangeAfter.has_gauge_metrics());
    gaugeMetrics.Clear();
    sortMetricDataByDimensionsValue(gaugeChangeAfter.gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 1);
    data = gaugeMetrics.data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs,
                             {(int64_t)(bucketStartTimeNs + 80 * NS_PER_SEC),
                              (int64_t)(bucketStartTimeNs + 105 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 2);
    EXPECT_EQ(data.bucket_info(0).atom(0).screen_state_changed().state(),
              android::view::DISPLAY_STATE_ON);
    EXPECT_EQ(data.bucket_info(0).atom(1).screen_state_changed().state(),
              android::view::DISPLAY_STATE_ON);

    // Gauge subsystem sleep state, random one sample, no condition.
    // Pulled at update time and after the normal bucket end.
    StatsLogReport gaugeNewAfter = report.metrics(1);
    EXPECT_EQ(gaugeNewAfter.metric_id(), gaugeNew.id());
    EXPECT_TRUE(gaugeNewAfter.has_gauge_metrics());
    gaugeMetrics.Clear();
    sortMetricDataByDimensionsValue(gaugeNewAfter.gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 2);
    data = gaugeMetrics.data(0);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_1");
    ASSERT_EQ(data.bucket_info_size(), 2);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs,
                             {updateTimeNs});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 901);
    ValidateGaugeBucketTimes(data.bucket_info(1), roundedBucketEndNs, roundedDumpTimeNs,
                             {(int64_t)(bucketStartTimeNs + bucketSizeNs)});
    ASSERT_EQ(data.bucket_info(1).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(1).atom(0).subsystem_sleep_state().time_millis(), 1601);

    data = gaugeMetrics.data(1);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_2");
    ASSERT_EQ(data.bucket_info_size(), 2);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs,
                             {updateTimeNs});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 902);
    ValidateGaugeBucketTimes(data.bucket_info(1), roundedBucketEndNs, roundedDumpTimeNs,
                             {(int64_t)(bucketStartTimeNs + bucketSizeNs)});
    ASSERT_EQ(data.bucket_info(1).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(1).atom(0).subsystem_sleep_state().time_millis(), 1602);

    // Gauge app start while app is in the background. App 1 started once, app 2 started twice.
    StatsLogReport gaugePushPersistAfter = report.metrics(2);
    EXPECT_EQ(gaugePushPersistAfter.metric_id(), gaugePushPersist.id());
    EXPECT_TRUE(gaugePushPersistAfter.has_gauge_metrics());
    gaugeMetrics.Clear();
    sortMetricDataByDimensionsValue(gaugePushPersistAfter.gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 2);
    data = gaugeMetrics.data(0);
    ValidateUidDimension(data.dimensions_in_what(), util::APP_START_OCCURRED, app1Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs,
                             {(int64_t)(bucketStartTimeNs + 95 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).app_start_occurred().pkg_name(), "app1");
    EXPECT_EQ(data.bucket_info(0).atom(0).app_start_occurred().activity_start_millis(), 104);

    data = gaugeMetrics.data(1);
    ValidateUidDimension(data.dimensions_in_what(), util::APP_START_OCCURRED, app2Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs,
                             {(int64_t)(bucketStartTimeNs + 70 * NS_PER_SEC),
                              (int64_t)(bucketStartTimeNs + 100 * NS_PER_SEC)});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 2);
    EXPECT_EQ(data.bucket_info(0).atom(0).app_start_occurred().pkg_name(), "app2");
    EXPECT_EQ(data.bucket_info(0).atom(0).app_start_occurred().activity_start_millis(), 203);
    EXPECT_EQ(data.bucket_info(0).atom(1).app_start_occurred().pkg_name(), "app2");
    EXPECT_EQ(data.bucket_info(0).atom(1).app_start_occurred().activity_start_millis(), 204);

    // Gauge subsystem sleep state when screen is on. One pull at update since screen is on then.
    StatsLogReport gaugePullPersistAfter = report.metrics(3);
    EXPECT_EQ(gaugePullPersistAfter.metric_id(), gaugePullPersist.id());
    EXPECT_TRUE(gaugePullPersistAfter.has_gauge_metrics());
    gaugeMetrics.Clear();
    sortMetricDataByDimensionsValue(gaugePullPersistAfter.gauge_metrics(), &gaugeMetrics);
    ASSERT_EQ(gaugeMetrics.data_size(), 2);
    data = gaugeMetrics.data(0);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_1");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs,
                             {updateTimeNs});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 901);

    data = gaugeMetrics.data(1);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_2");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateGaugeBucketTimes(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs,
                             {updateTimeNs});
    ASSERT_EQ(data.bucket_info(0).atom_size(), 1);
    EXPECT_EQ(data.bucket_info(0).atom(0).subsystem_sleep_state().time_millis(), 902);
}

TEST_F(ConfigUpdateE2eTest, TestValueMetric) {
    StatsdConfig config;
    config.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.

    AtomMatcher brightnessMatcher = CreateScreenBrightnessChangedAtomMatcher();
    *config.add_atom_matcher() = brightnessMatcher;
    AtomMatcher screenOnMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnMatcher;
    AtomMatcher screenOffMatcher = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = screenOffMatcher;
    AtomMatcher batteryPluggedUsbMatcher = CreateBatteryStateUsbMatcher();
    *config.add_atom_matcher() = batteryPluggedUsbMatcher;
    AtomMatcher unpluggedMatcher = CreateBatteryStateNoneMatcher();
    *config.add_atom_matcher() = unpluggedMatcher;
    AtomMatcher subsystemSleepMatcher =
            CreateSimpleAtomMatcher("SubsystemSleep", util::SUBSYSTEM_SLEEP_STATE);
    *config.add_atom_matcher() = subsystemSleepMatcher;

    Predicate screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;
    Predicate unpluggedPredicate = CreateDeviceUnpluggedPredicate();
    *config.add_predicate() = unpluggedPredicate;

    State screenState = CreateScreenState();
    *config.add_state() = screenState;

    ValueMetric valuePullPersist =
            createValueMetric("SubsystemSleepWhileUnpluggedSliceScreen", subsystemSleepMatcher, 4,
                              unpluggedPredicate.id(), {screenState.id()});
    *valuePullPersist.mutable_dimensions_in_what() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {1 /* subsystem name */});

    ValueMetric valuePushPersist = createValueMetric(
            "MinScreenBrightnessWhileScreenOn", brightnessMatcher, 1, screenOnPredicate.id(), {});
    valuePushPersist.set_aggregation_type(ValueMetric::MIN);

    ValueMetric valueChange =
            createValueMetric("SubsystemSleep", subsystemSleepMatcher, 4, nullopt, {});
    *valueChange.mutable_dimensions_in_what() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {1 /* subsystem name */});

    ValueMetric valueRemove =
            createValueMetric("AvgScreenBrightness", brightnessMatcher, 1, nullopt, {});
    valueRemove.set_aggregation_type(ValueMetric::AVG);

    *config.add_value_metric() = valuePullPersist;
    *config.add_value_metric() = valueRemove;
    *config.add_value_metric() = valuePushPersist;
    *config.add_value_metric() = valueChange;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = getElapsedRealtimeNs();
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
    // call from StatsLogProcessor constructor
    Expectation initCall =
            EXPECT_CALL(*mLogEventFilter, setAtomIds(StatsLogProcessor::getDefaultAtomIdSet(), _))
                    .Times(1);
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(config), _))
            .Times(1)
            .After(initCall);
    // Config creation triggers pull #1.
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key,
                                    SharedRefBase::make<FakeSubsystemSleepCallback>(),
                                    util::SUBSYSTEM_SLEEP_STATE, new UidMap(), mLogEventFilter);

    // Initialize log events before update.
    // ValuePushPersist and ValuePullPersist will skip the bucket due to condition unknown.
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 5 * NS_PER_SEC, 5));
    events.push_back(CreateScreenStateChangedEvent(bucketStartTimeNs + 10 * NS_PER_SEC,
                                                   android::view::DISPLAY_STATE_ON));
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 15 * NS_PER_SEC, 15));
    events.push_back(CreateBatteryStateChangedEvent(
            bucketStartTimeNs + 20 * NS_PER_SEC,
            BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE));  // Pull #2.
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 25 * NS_PER_SEC, 40));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->mPullerManager->ForceClearPullerCache();
        processor->OnLogEvent(event.get());
    }
    processor->mPullerManager->ForceClearPullerCache();

    // Do the update. Add matchers/conditions in different order to force indices to change.
    StatsdConfig newConfig;
    newConfig.add_default_pull_packages("AID_ROOT");  // Fake puller is registered with root.

    *newConfig.add_atom_matcher() = screenOffMatcher;
    *newConfig.add_atom_matcher() = unpluggedMatcher;
    *newConfig.add_atom_matcher() = batteryPluggedUsbMatcher;
    *newConfig.add_atom_matcher() = subsystemSleepMatcher;
    *newConfig.add_atom_matcher() = brightnessMatcher;
    *newConfig.add_atom_matcher() = screenOnMatcher;

    *newConfig.add_predicate() = unpluggedPredicate;
    *newConfig.add_predicate() = screenOnPredicate;

    *config.add_state() = screenState;

    valueChange.set_condition(screenOnPredicate.id());
    *newConfig.add_value_metric() = valueChange;
    ValueMetric valueNew = createValueMetric("MaxScrBrightness", brightnessMatcher, 1, nullopt, {});
    valueNew.set_aggregation_type(ValueMetric::MAX);
    *newConfig.add_value_metric() = valueNew;
    *newConfig.add_value_metric() = valuePushPersist;
    *newConfig.add_value_metric() = valuePullPersist;

    int64_t updateTimeNs = bucketStartTimeNs + 30 * NS_PER_SEC;
    // Update pulls valuePullPersist and valueNew. Pull #3.
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), _)).Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // Verify puller manager is properly set.
    sp<StatsPullerManager> pullerManager = processor->mPullerManager;
    EXPECT_EQ(pullerManager->mNextPullTimeNs, bucketStartTimeNs + bucketSizeNs);
    ASSERT_EQ(pullerManager->mReceivers.size(), 1);
    ASSERT_EQ(pullerManager->mReceivers.begin()->second.size(), 2);

    // Send events after the update. Values reset since this is a new bucket.
    events.clear();
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 35 * NS_PER_SEC, 30));
    events.push_back(CreateScreenStateChangedEvent(bucketStartTimeNs + 40 * NS_PER_SEC,
                                                   android::view::DISPLAY_STATE_OFF));  // Pull #4.
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 45 * NS_PER_SEC, 20));
    events.push_back(CreateBatteryStateChangedEvent(
            bucketStartTimeNs + 50 * NS_PER_SEC,
            BatteryPluggedStateEnum::BATTERY_PLUGGED_USB));  // Pull #5.
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 55 * NS_PER_SEC, 25));
    events.push_back(CreateScreenStateChangedEvent(bucketStartTimeNs + 60 * NS_PER_SEC,
                                                   android::view::DISPLAY_STATE_ON));  // Pull #6.
    events.push_back(CreateBatteryStateChangedEvent(
            bucketStartTimeNs + 65 * NS_PER_SEC,
            BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE));  // Pull #7.
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 70 * NS_PER_SEC, 40));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->mPullerManager->ForceClearPullerCache();
        processor->OnLogEvent(event.get());
    }
    processor->mPullerManager->ForceClearPullerCache();

    // Pulling alarm arrive, triggering a bucket split.
    // Both valuePullPersist and valueChange use the value since both conditions are true. Pull #8.
    processor->informPullAlarmFired(bucketStartTimeNs + bucketSizeNs);
    processor->OnLogEvent(CreateScreenBrightnessChangedEvent(
                                  bucketStartTimeNs + bucketSizeNs + 5 * NS_PER_SEC, 50)
                                  .get());

    uint64_t dumpTimeNs = bucketStartTimeNs + bucketSizeNs + 10 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 2);

    int64_t roundedBucketStartNs = MillisToNano(NanoToMillis(bucketStartTimeNs));
    int64_t roundedUpdateTimeNs = MillisToNano(NanoToMillis(updateTimeNs));
    int64_t roundedBucketEndNs = MillisToNano(NanoToMillis(bucketStartTimeNs + bucketSizeNs));
    int64_t roundedDumpTimeNs = MillisToNano(NanoToMillis(dumpTimeNs));

    // Report from before update.
    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 4);
    // Pull subsystem sleep while unplugged slice screen. Bucket skipped due to condition unknown.
    StatsLogReport valuePullPersistBefore = report.metrics(0);
    EXPECT_EQ(valuePullPersistBefore.metric_id(), valuePullPersist.id());
    EXPECT_TRUE(valuePullPersistBefore.has_value_metrics());
    ASSERT_EQ(valuePullPersistBefore.value_metrics().data_size(), 0);
    ASSERT_EQ(valuePullPersistBefore.value_metrics().skipped_size(), 1);
    StatsLogReport::SkippedBuckets skipBucket = valuePullPersistBefore.value_metrics().skipped(0);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), roundedBucketStartNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), roundedUpdateTimeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::CONDITION_UNKNOWN);

    // Average screen brightness. Values were 5, 15, 40. Avg: 20.
    StatsLogReport valueRemoveBefore = report.metrics(1);
    EXPECT_EQ(valueRemoveBefore.metric_id(), valueRemove.id());
    EXPECT_TRUE(valueRemoveBefore.has_value_metrics());
    StatsLogReport::ValueMetricDataWrapper valueMetrics;
    sortMetricDataByDimensionsValue(valueRemoveBefore.value_metrics(), &valueMetrics);
    ASSERT_EQ(valueMetrics.data_size(), 1);
    ValueMetricData data = valueMetrics.data(0);
    EXPECT_FALSE(data.has_dimensions_in_what());
    EXPECT_EQ(data.slice_by_state_size(), 0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    EXPECT_EQ(3, data.bucket_info(0).values(0).sample_size());
    ValidateValueBucket(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs, {20}, 0, 0);

    // Min screen brightness while screen on. Bucket skipped due to condition unknown.
    StatsLogReport valuePushPersistBefore = report.metrics(2);
    EXPECT_EQ(valuePushPersistBefore.metric_id(), valuePushPersist.id());
    EXPECT_TRUE(valuePushPersistBefore.has_value_metrics());
    ASSERT_EQ(valuePushPersistBefore.value_metrics().data_size(), 0);
    ASSERT_EQ(valuePushPersistBefore.value_metrics().skipped_size(), 1);
    skipBucket = valuePushPersistBefore.value_metrics().skipped(0);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), roundedBucketStartNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), roundedUpdateTimeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::CONDITION_UNKNOWN);

    // Pull Subsystem sleep state. Value is Pull #3 (900) - Pull#1 (100).
    StatsLogReport valueChangeBefore = report.metrics(3);
    EXPECT_EQ(valueChangeBefore.metric_id(), valueChange.id());
    EXPECT_TRUE(valueChangeBefore.has_value_metrics());
    valueMetrics.Clear();
    sortMetricDataByDimensionsValue(valueChangeBefore.value_metrics(), &valueMetrics);
    ASSERT_EQ(valueMetrics.data_size(), 2);
    data = valueMetrics.data(0);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_1");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateValueBucket(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs, {800}, 0,
                        0);
    data = valueMetrics.data(1);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_2");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateValueBucket(data.bucket_info(0), roundedBucketStartNs, roundedUpdateTimeNs, {800}, 0,
                        0);

    // Report from after update.
    report = reports.reports(1);
    ASSERT_EQ(report.metrics_size(), 4);
    // Pull subsystem sleep while screen on.
    // Pull#4 (1600) - pull#3 (900) + pull#8 (6400) - pull#6 (3600)
    StatsLogReport valueChangeAfter = report.metrics(0);
    EXPECT_EQ(valueChangeAfter.metric_id(), valueChange.id());
    EXPECT_TRUE(valueChangeAfter.has_value_metrics());
    valueMetrics.Clear();
    sortMetricDataByDimensionsValue(valueChangeAfter.value_metrics(), &valueMetrics);
    int64_t conditionTrueNs = bucketSizeNs - 60 * NS_PER_SEC + 10 * NS_PER_SEC;
    ASSERT_EQ(valueMetrics.data_size(), 2);
    data = valueMetrics.data(0);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_1");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateValueBucket(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs, {3500},
                        conditionTrueNs, 0);
    ASSERT_EQ(valueMetrics.data_size(), 2);
    data = valueMetrics.data(1);
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_2");
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateValueBucket(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs, {3500},
                        conditionTrueNs, 0);

    ASSERT_EQ(valueChangeAfter.value_metrics().skipped_size(), 1);
    skipBucket = valueChangeAfter.value_metrics().skipped(0);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), roundedBucketEndNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), roundedDumpTimeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::DUMP_REPORT_REQUESTED);

    // Max screen brightness, no condition. Val is 40 in first bucket, 50 in second.
    StatsLogReport valueNewAfter = report.metrics(1);
    EXPECT_EQ(valueNewAfter.metric_id(), valueNew.id());
    EXPECT_TRUE(valueNewAfter.has_value_metrics());
    valueMetrics.Clear();
    sortMetricDataByDimensionsValue(valueNewAfter.value_metrics(), &valueMetrics);
    ASSERT_EQ(valueMetrics.data_size(), 1);
    data = valueMetrics.data(0);
    ASSERT_EQ(data.bucket_info_size(), 2);
    ValidateValueBucket(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs, {40}, 0, 0);
    ValidateValueBucket(data.bucket_info(1), roundedBucketEndNs, roundedDumpTimeNs, {50}, 0, 0);

    // Min screen brightness when screen on. Val is 30 in first bucket, 50 in second.
    StatsLogReport valuePushPersistAfter = report.metrics(2);
    EXPECT_EQ(valuePushPersistAfter.metric_id(), valuePushPersist.id());
    EXPECT_TRUE(valuePushPersistAfter.has_value_metrics());
    valueMetrics.Clear();
    sortMetricDataByDimensionsValue(valuePushPersistAfter.value_metrics(), &valueMetrics);
    ASSERT_EQ(valueMetrics.data_size(), 1);
    data = valueMetrics.data(0);
    ASSERT_EQ(data.bucket_info_size(), 2);
    conditionTrueNs = bucketSizeNs - 60 * NS_PER_SEC + 10 * NS_PER_SEC;
    ValidateValueBucket(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs, {30},
                        conditionTrueNs, 0);
    ValidateValueBucket(data.bucket_info(1), roundedBucketEndNs, roundedDumpTimeNs, {50},
                        10 * NS_PER_SEC, 0);

    // TODO(b/179725160): fix assertions.
    // Subsystem sleep state while unplugged slice screen.
    StatsLogReport valuePullPersistAfter = report.metrics(3);
    EXPECT_EQ(valuePullPersistAfter.metric_id(), valuePullPersist.id());
    EXPECT_TRUE(valuePullPersistAfter.has_value_metrics());
    valueMetrics.Clear();
    sortMetricDataByDimensionsValue(valuePullPersistAfter.value_metrics(), &valueMetrics);
    ASSERT_EQ(valueMetrics.data_size(), 4);
    // Name 1, screen OFF. Pull#5 (2500) - pull#4 (1600).
    data = valueMetrics.data(0);
    conditionTrueNs = 10 * NS_PER_SEC;
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_1");
    ValidateStateValue(data.slice_by_state(), util::SCREEN_STATE_CHANGED,
                       android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateValueBucket(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs, {900}, -1, 0);
    // Name 1, screen ON. Pull#4 (1600) - pull#3 (900) + pull#8 (6400) - pull#7 (4900).
    data = valueMetrics.data(1);
    conditionTrueNs = 10 * NS_PER_SEC + bucketSizeNs - 65 * NS_PER_SEC;
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_1");
    ValidateStateValue(data.slice_by_state(), util::SCREEN_STATE_CHANGED,
                       android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateValueBucket(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs, {2200}, -1,
                        0);
    // Name 2, screen OFF. Pull#5 (2500) - pull#4 (1600).
    data = valueMetrics.data(2);
    conditionTrueNs = 10 * NS_PER_SEC;
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_2");
    ValidateStateValue(data.slice_by_state(), util::SCREEN_STATE_CHANGED,
                       android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateValueBucket(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs, {900}, -1, 0);
    // Name 2, screen ON. Pull#4 (1600) - pull#3 (900) + pull#8 (6400) - pull#7 (4900).
    data = valueMetrics.data(3);
    conditionTrueNs = 10 * NS_PER_SEC + bucketSizeNs - 65 * NS_PER_SEC;
    ValidateSubsystemSleepDimension(data.dimensions_in_what(), "subsystem_name_2");
    ValidateStateValue(data.slice_by_state(), util::SCREEN_STATE_CHANGED,
                       android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    ASSERT_EQ(data.bucket_info_size(), 1);
    ValidateValueBucket(data.bucket_info(0), roundedUpdateTimeNs, roundedBucketEndNs, {2200}, -1,
                        0);

    ASSERT_EQ(valuePullPersistAfter.value_metrics().skipped_size(), 1);
    skipBucket = valuePullPersistAfter.value_metrics().skipped(0);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), roundedBucketEndNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), roundedDumpTimeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::DUMP_REPORT_REQUESTED);
}

TEST_F(ConfigUpdateE2eTest, TestKllMetric) {
    StatsdConfig config;

    AtomMatcher brightnessMatcher = CreateScreenBrightnessChangedAtomMatcher();
    *config.add_atom_matcher() = brightnessMatcher;
    AtomMatcher screenOnMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnMatcher;
    AtomMatcher screenOffMatcher = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = screenOffMatcher;
    AtomMatcher batteryPluggedUsbMatcher = CreateBatteryStateUsbMatcher();
    *config.add_atom_matcher() = batteryPluggedUsbMatcher;
    AtomMatcher unpluggedMatcher = CreateBatteryStateNoneMatcher();
    *config.add_atom_matcher() = unpluggedMatcher;

    Predicate screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;
    Predicate unpluggedPredicate = CreateDeviceUnpluggedPredicate();
    *config.add_predicate() = unpluggedPredicate;

    KllMetric kllPersist = createKllMetric("ScreenBrightnessWhileScreenOn", brightnessMatcher, 1,
                                           screenOnPredicate.id());

    KllMetric kllChange = createKllMetric("ScreenBrightness", brightnessMatcher, 1, nullopt);

    KllMetric kllRemove = createKllMetric("ScreenBrightnessWhileUnplugged", brightnessMatcher, 1,
                                          unpluggedPredicate.id());

    *config.add_kll_metric() = kllRemove;
    *config.add_kll_metric() = kllPersist;
    *config.add_kll_metric() = kllChange;

    ConfigKey key(123, 987);
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);

    // Initialize log events before update.
    // kllPersist and kllRemove will skip the bucket due to condition unknown.
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 5 * NS_PER_SEC, 5));
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 15 * NS_PER_SEC, 15));
    events.push_back(CreateBatteryStateChangedEvent(bucketStartTimeNs + 20 * NS_PER_SEC,
                                                    BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE));
    events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 25 * NS_PER_SEC, 40));
    events.push_back(CreateScreenStateChangedEvent(bucketStartTimeNs + 27 * NS_PER_SEC,
                                                   android::view::DISPLAY_STATE_ON));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Do the update. Add matchers/conditions in different order to force indices to change.
    StatsdConfig newConfig;

    *newConfig.add_atom_matcher() = screenOffMatcher;
    *newConfig.add_atom_matcher() = unpluggedMatcher;
    *newConfig.add_atom_matcher() = batteryPluggedUsbMatcher;
    *newConfig.add_atom_matcher() = brightnessMatcher;
    *newConfig.add_atom_matcher() = screenOnMatcher;
    AtomMatcher batterySaverStartMatcher = CreateBatterySaverModeStartAtomMatcher();
    *newConfig.add_atom_matcher() = batterySaverStartMatcher;
    AtomMatcher batterySaverStopMatcher = CreateBatterySaverModeStopAtomMatcher();
    *newConfig.add_atom_matcher() = batterySaverStopMatcher;

    *newConfig.add_predicate() = screenOnPredicate;
    Predicate batterySaverPredicate = CreateBatterySaverModePredicate();
    *newConfig.add_predicate() = batterySaverPredicate;
    Predicate screenOffPredicate = CreateScreenIsOffPredicate();
    *newConfig.add_predicate() = screenOffPredicate;

    kllChange.set_condition(batterySaverPredicate.id());
    *newConfig.add_kll_metric() = kllChange;
    KllMetric kllNew = createKllMetric("ScreenBrightnessWhileScreenOff", brightnessMatcher, 1,
                                       screenOffPredicate.id());
    *newConfig.add_kll_metric() = kllNew;
    *newConfig.add_kll_metric() = kllPersist;

    int64_t updateTimeNs = bucketStartTimeNs + 30 * NS_PER_SEC;
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // Send events after the update. This is a new bucket.
    events.clear();
    events.push_back(CreateScreenBrightnessChangedEvent(updateTimeNs + 5 * NS_PER_SEC, 30));
    events.push_back(CreateScreenStateChangedEvent(updateTimeNs + 10 * NS_PER_SEC,
                                                   android::view::DISPLAY_STATE_OFF));
    events.push_back(CreateScreenBrightnessChangedEvent(updateTimeNs + 15 * NS_PER_SEC, 20));
    events.push_back(CreateBatteryStateChangedEvent(updateTimeNs + 20 * NS_PER_SEC,
                                                    BatteryPluggedStateEnum::BATTERY_PLUGGED_USB));
    events.push_back(CreateScreenBrightnessChangedEvent(updateTimeNs + 25 * NS_PER_SEC, 25));
    events.push_back(CreateScreenStateChangedEvent(updateTimeNs + 30 * NS_PER_SEC,
                                                   android::view::DISPLAY_STATE_ON));
    events.push_back(CreateBatteryStateChangedEvent(updateTimeNs + 35 * NS_PER_SEC,
                                                    BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE));
    events.push_back(CreateScreenBrightnessChangedEvent(updateTimeNs + 40 * NS_PER_SEC, 40));

    // End current bucket and start new bucket.
    events.push_back(CreateScreenBrightnessChangedEvent(
            bucketStartTimeNs + bucketSizeNs + 5 * NS_PER_SEC, 50));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = bucketStartTimeNs + bucketSizeNs + 10 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 2);

    // Report from before update.
    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 3);

    // kllRemove: Screen brightness while unplugged. Bucket skipped due to condition unknown.
    StatsLogReport kllRemoveBefore = report.metrics(0);
    EXPECT_EQ(kllRemoveBefore.metric_id(), kllRemove.id());
    EXPECT_TRUE(kllRemoveBefore.has_kll_metrics());
    ASSERT_EQ(kllRemoveBefore.kll_metrics().data_size(), 0);
    ASSERT_EQ(kllRemoveBefore.kll_metrics().skipped_size(), 1);
    StatsLogReport::SkippedBuckets skipBucket = kllRemoveBefore.kll_metrics().skipped(0);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), bucketStartTimeNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), updateTimeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::CONDITION_UNKNOWN);

    // kllPersist: Screen brightness while screen on.
    StatsLogReport kllPersistBefore = report.metrics(1);
    EXPECT_EQ(kllPersistBefore.metric_id(), kllPersist.id());
    EXPECT_TRUE(kllPersistBefore.has_kll_metrics());
    EXPECT_EQ(kllPersistBefore.kll_metrics().data_size(), 0);
    ASSERT_EQ(kllPersistBefore.kll_metrics().skipped_size(), 1);
    skipBucket = kllPersistBefore.kll_metrics().skipped(0);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), bucketStartTimeNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), updateTimeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::CONDITION_UNKNOWN);

    // kllChange: Screen brightness.
    StatsLogReport kllChangeBefore = report.metrics(2);
    EXPECT_EQ(kllChangeBefore.metric_id(), kllChange.id());
    EXPECT_TRUE(kllChangeBefore.has_kll_metrics());
    ASSERT_EQ(kllChangeBefore.kll_metrics().data_size(), 1);
    KllMetricData data = kllChangeBefore.kll_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    TRACE_CALL(ValidateKllBucket, data.bucket_info(0), bucketStartTimeNs, updateTimeNs,
               /*sketchSizes=*/{3}, /*conditionTrueNs=*/0);
    EXPECT_EQ(kllChangeBefore.kll_metrics().skipped_size(), 0);

    // Report from after update.
    report = reports.reports(1);
    ASSERT_EQ(report.metrics_size(), 3);

    // kllChange: Screen brightness while on battery saver.
    StatsLogReport kllChangeAfter = report.metrics(0);
    EXPECT_EQ(kllChangeAfter.metric_id(), kllChange.id());
    EXPECT_TRUE(kllChangeAfter.has_kll_metrics());
    EXPECT_EQ(kllChangeAfter.kll_metrics().data_size(), 0);
    ASSERT_EQ(kllChangeAfter.kll_metrics().skipped_size(), 2);
    skipBucket = kllChangeAfter.kll_metrics().skipped(0);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), updateTimeNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), bucketStartTimeNs + bucketSizeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::CONDITION_UNKNOWN);
    skipBucket = kllChangeAfter.kll_metrics().skipped(1);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), bucketStartTimeNs + bucketSizeNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), dumpTimeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::CONDITION_UNKNOWN);

    // kllNew: Screen brightness while screen off.
    StatsLogReport kllNewAfter = report.metrics(1);
    EXPECT_EQ(kllNewAfter.metric_id(), kllNew.id());
    EXPECT_TRUE(kllNewAfter.has_kll_metrics());
    EXPECT_EQ(kllNewAfter.kll_metrics().data_size(), 0);
    ASSERT_EQ(kllNewAfter.kll_metrics().skipped_size(), 2);
    skipBucket = kllNewAfter.kll_metrics().skipped(0);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), updateTimeNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), bucketStartTimeNs + bucketSizeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::CONDITION_UNKNOWN);
    skipBucket = kllNewAfter.kll_metrics().skipped(1);
    EXPECT_EQ(skipBucket.start_bucket_elapsed_nanos(), bucketStartTimeNs + bucketSizeNs);
    EXPECT_EQ(skipBucket.end_bucket_elapsed_nanos(), dumpTimeNs);
    ASSERT_EQ(skipBucket.drop_event_size(), 1);
    EXPECT_EQ(skipBucket.drop_event(0).drop_reason(), BucketDropReason::NO_DATA);

    // kllPersist: Screen brightness while screen on.
    StatsLogReport kllPersistAfter = report.metrics(2);
    EXPECT_EQ(kllPersistAfter.metric_id(), kllPersist.id());
    EXPECT_TRUE(kllPersistAfter.has_kll_metrics());
    ASSERT_EQ(kllPersistAfter.kll_metrics().data_size(), 1);
    data = kllPersistAfter.kll_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 2);
    int64_t conditionTrueNs =
            bucketStartTimeNs + 40 * NS_PER_SEC - updateTimeNs + bucketSizeNs - 60 * NS_PER_SEC;
    TRACE_CALL(ValidateKllBucket, data.bucket_info(0), updateTimeNs,
               bucketStartTimeNs + bucketSizeNs,
               /*sketchSizes=*/{2}, conditionTrueNs);
    conditionTrueNs = dumpTimeNs - bucketStartTimeNs - bucketSizeNs;
    TRACE_CALL(ValidateKllBucket, data.bucket_info(1), bucketStartTimeNs + bucketSizeNs, dumpTimeNs,
               /*sketchSizes=*/{1}, conditionTrueNs);
    EXPECT_EQ(kllPersistAfter.kll_metrics().skipped_size(), 0);
}

TEST_F(ConfigUpdateE2eTest, TestMetricActivation) {
    ALOGE("Start ConfigUpdateE2eTest#TestMetricActivation");
    StatsdConfig config;

    string immediateTag = "immediate", bootTag = "boot", childTag = "child";

    AtomMatcher syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;

    AtomMatcher immediateMatcher =
            CreateSimpleAtomMatcher("immediateMatcher", util::WAKELOCK_STATE_CHANGED);
    FieldValueMatcher* fvm =
            immediateMatcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(3);  // Tag.
    fvm->set_eq_string(immediateTag);
    *config.add_atom_matcher() = immediateMatcher;

    AtomMatcher bootMatcher = CreateSimpleAtomMatcher("bootMatcher", util::WAKELOCK_STATE_CHANGED);
    fvm = bootMatcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(3);  // Tag.
    fvm->set_eq_string(bootTag);
    *config.add_atom_matcher() = bootMatcher;

    AtomMatcher childMatcher =
            CreateSimpleAtomMatcher("childMatcher", util::WAKELOCK_STATE_CHANGED);
    fvm = childMatcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(3);  // Tag.
    fvm->set_eq_string(childTag);
    *config.add_atom_matcher() = childMatcher;

    AtomMatcher acquireMatcher = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = acquireMatcher;

    AtomMatcher combinationMatcher;
    combinationMatcher.set_id(StringToId("combination"));
    AtomMatcher_Combination* combination = combinationMatcher.mutable_combination();
    combination->set_operation(LogicalOperation::OR);
    combination->add_matcher(acquireMatcher.id());
    combination->add_matcher(childMatcher.id());
    *config.add_atom_matcher() = combinationMatcher;

    CountMetric immediateMetric =
            createCountMetric("ImmediateMetric", syncStartMatcher.id(), nullopt, {});
    CountMetric bootMetric = createCountMetric("BootMetric", syncStartMatcher.id(), nullopt, {});
    CountMetric combinationMetric =
            createCountMetric("CombinationMetric", syncStartMatcher.id(), nullopt, {});
    *config.add_count_metric() = immediateMetric;
    *config.add_count_metric() = bootMetric;
    *config.add_count_metric() = combinationMetric;

    MetricActivation immediateMetricActivation;
    immediateMetricActivation.set_metric_id(immediateMetric.id());
    auto eventActivation = immediateMetricActivation.add_event_activation();
    eventActivation->set_activation_type(ActivationType::ACTIVATE_IMMEDIATELY);
    eventActivation->set_atom_matcher_id(immediateMatcher.id());
    eventActivation->set_ttl_seconds(60);  // One minute.
    *config.add_metric_activation() = immediateMetricActivation;

    MetricActivation bootMetricActivation;
    bootMetricActivation.set_metric_id(bootMetric.id());
    eventActivation = bootMetricActivation.add_event_activation();
    eventActivation->set_activation_type(ActivationType::ACTIVATE_ON_BOOT);
    eventActivation->set_atom_matcher_id(bootMatcher.id());
    eventActivation->set_ttl_seconds(60);  // One minute.
    *config.add_metric_activation() = bootMetricActivation;

    MetricActivation combinationMetricActivation;
    combinationMetricActivation.set_metric_id(combinationMetric.id());
    eventActivation = combinationMetricActivation.add_event_activation();
    eventActivation->set_activation_type(ActivationType::ACTIVATE_IMMEDIATELY);
    eventActivation->set_atom_matcher_id(combinationMatcher.id());
    eventActivation->set_ttl_seconds(60);  // One minute.
    *config.add_metric_activation() = combinationMetricActivation;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);
    int uid1 = 55555;

    // Initialize log events before update.
    // Counts provided in order of immediate, boot, and combination metric.
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 5 * NS_PER_SEC, {uid1}, {""},
                                          ""));  // Count: 0, 0, 0.
    events.push_back(CreateReleaseWakelockEvent(bucketStartTimeNs + 10 * NS_PER_SEC, {uid1}, {""},
                                                immediateTag));  // Activate immediate metric.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 15 * NS_PER_SEC, {uid1}, {""},
                                          ""));  // Count: 1, 0, 0.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 20 * NS_PER_SEC, {uid1}, {""},
                                                "foo"));  // Activate combination metric.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 25 * NS_PER_SEC, {uid1}, {""},
                                          ""));  // Count: 2, 0, 1.
    events.push_back(CreateReleaseWakelockEvent(bucketStartTimeNs + 30 * NS_PER_SEC, {uid1}, {""},
                                                bootTag));  // Boot metric pending activation.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 35 * NS_PER_SEC, {uid1}, {""},
                                          ""));  // Count: 3, 0, 2.
    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Do update. Add matchers/conditions in different order to force indices to change.
    StatsdConfig newConfig;
    newConfig.set_hash_strings_in_metric_report(false);  // Modify metadata for fun.

    // Change combination matcher, will mean combination metric isn't active after update.
    combinationMatcher.mutable_combination()->set_operation(LogicalOperation::AND);
    *newConfig.add_atom_matcher() = acquireMatcher;
    *newConfig.add_atom_matcher() = bootMatcher;
    *newConfig.add_atom_matcher() = combinationMatcher;
    *newConfig.add_atom_matcher() = childMatcher;
    *newConfig.add_atom_matcher() = syncStartMatcher;
    *newConfig.add_atom_matcher() = immediateMatcher;

    *newConfig.add_count_metric() = bootMetric;
    *newConfig.add_count_metric() = combinationMetric;
    *newConfig.add_count_metric() = immediateMetric;

    *newConfig.add_metric_activation() = bootMetricActivation;
    *newConfig.add_metric_activation() = combinationMetricActivation;
    *newConfig.add_metric_activation() = immediateMetricActivation;

    int64_t updateTimeNs = bucketStartTimeNs + 40 * NS_PER_SEC;
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // The reboot will write to disk again, so sleep for 1 second to avoid this.
    // TODO(b/178887128): clean this up.
    std::this_thread::sleep_for(1000ms);
    // Send event after the update. Counts reset to 0 since this is a new bucket.
    processor->OnLogEvent(
            CreateSyncStartEvent(bucketStartTimeNs + 45 * NS_PER_SEC, {uid1}, {""}, "")
                    .get());  // Count: 1, 0, 0.

    // Fake a reboot. Code is from StatsService::informDeviceShutdown.
    int64_t shutDownTimeNs = bucketStartTimeNs + 50 * NS_PER_SEC;
    processor->WriteDataToDisk(DEVICE_SHUTDOWN, FAST, shutDownTimeNs, getWallClockNs());
    processor->SaveActiveConfigsToDisk(shutDownTimeNs);
    processor->SaveMetadataToDisk(getWallClockNs(), shutDownTimeNs);

    // On boot, use StartUp. However, skip config manager for simplicity.
    int64_t bootTimeNs = bucketStartTimeNs + 55 * NS_PER_SEC;
    processor = CreateStatsLogProcessor(bootTimeNs, bootTimeNs, newConfig, key, mLogEventFilter);
    processor->LoadActiveConfigsFromDisk();
    processor->LoadMetadataFromDisk(getWallClockNs(), bootTimeNs);

    // Send events after boot. Counts reset to 0 since this is a new bucket. Boot metric now active.
    events.clear();
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 60 * NS_PER_SEC, {uid1}, {""},
                                          ""));  // Count: 1, 1, 0.
    int64_t deactivationTimeNs = bucketStartTimeNs + 76 * NS_PER_SEC;
    events.push_back(CreateScreenStateChangedEvent(
            deactivationTimeNs,
            android::view::DisplayStateEnum::DISPLAY_STATE_OFF));  // TTLs immediate metric.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 80 * NS_PER_SEC, {uid1}, {""},
                                          ""));  // Count: 1, 2, 0.
    events.push_back(CreateAcquireWakelockEvent(bucketStartTimeNs + 85 * NS_PER_SEC, {uid1}, {""},
                                                childTag));  // Activate combination metric.
    events.push_back(CreateSyncStartEvent(bucketStartTimeNs + 90 * NS_PER_SEC, {uid1}, {""},
                                          ""));  // Count: 1, 3, 1.

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }
    uint64_t dumpTimeNs = bucketStartTimeNs + 90 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    sortReportsByElapsedTime(&reports);
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 3);

    // Report from before update.
    ConfigMetricsReport report = reports.reports(0);
    EXPECT_EQ(report.last_report_elapsed_nanos(), bucketStartTimeNs);
    EXPECT_EQ(report.current_report_elapsed_nanos(), updateTimeNs);
    ASSERT_EQ(report.metrics_size(), 3);
    // Immediate metric. Count = 3.
    StatsLogReport metricReport = report.metrics(0);
    EXPECT_EQ(metricReport.metric_id(), immediateMetric.id());
    EXPECT_TRUE(metricReport.is_active());
    EXPECT_TRUE(metricReport.has_count_metrics());

    // Boot metric. Count = 0.
    metricReport = report.metrics(1);
    EXPECT_EQ(metricReport.metric_id(), bootMetric.id());
    EXPECT_FALSE(metricReport.is_active());
    EXPECT_FALSE(metricReport.has_count_metrics());

    // Combination metric. Count = 2.
    metricReport = report.metrics(2);
    EXPECT_EQ(metricReport.metric_id(), combinationMetric.id());
    EXPECT_TRUE(metricReport.is_active());
    EXPECT_TRUE(metricReport.has_count_metrics());

    // Report from after update, before boot.
    report = reports.reports(1);
    EXPECT_EQ(report.last_report_elapsed_nanos(), updateTimeNs);
    EXPECT_EQ(report.current_report_elapsed_nanos(), shutDownTimeNs);
    ASSERT_EQ(report.metrics_size(), 3);
    // Boot metric. Count = 0.
    metricReport = report.metrics(0);
    EXPECT_EQ(metricReport.metric_id(), bootMetric.id());
    EXPECT_FALSE(metricReport.is_active());
    EXPECT_FALSE(metricReport.has_count_metrics());

    // Combination metric. Count = 0.
    metricReport = report.metrics(1);
    EXPECT_EQ(metricReport.metric_id(), combinationMetric.id());
    EXPECT_FALSE(metricReport.is_active());
    EXPECT_FALSE(metricReport.has_count_metrics());

    // Immediate metric. Count = 1.
    metricReport = report.metrics(2);
    EXPECT_EQ(metricReport.metric_id(), immediateMetric.id());
    EXPECT_TRUE(metricReport.is_active());
    EXPECT_TRUE(metricReport.has_count_metrics());

    // Report from after reboot.
    report = reports.reports(2);
    EXPECT_EQ(report.last_report_elapsed_nanos(), bootTimeNs);
    EXPECT_EQ(report.current_report_elapsed_nanos(), dumpTimeNs);
    ASSERT_EQ(report.metrics_size(), 3);
    // Boot metric. Count = 3.
    metricReport = report.metrics(0);
    EXPECT_EQ(metricReport.metric_id(), bootMetric.id());
    EXPECT_TRUE(metricReport.is_active());
    EXPECT_TRUE(metricReport.has_count_metrics());

    // Combination metric. Count = 1.
    metricReport = report.metrics(1);
    EXPECT_EQ(metricReport.metric_id(), combinationMetric.id());
    EXPECT_TRUE(metricReport.is_active());
    EXPECT_TRUE(metricReport.has_count_metrics());

    // Immediate metric. Count = 1.
    metricReport = report.metrics(2);
    EXPECT_EQ(metricReport.metric_id(), immediateMetric.id());
    EXPECT_FALSE(metricReport.is_active());
    EXPECT_TRUE(metricReport.has_count_metrics());
    ALOGE("End ConfigUpdateE2eTest#TestMetricActivation");
}

TEST_F(ConfigUpdateE2eTest, TestAnomalyCountMetric) {
    StatsdConfig config;

    AtomMatcher syncStartMatcher = CreateSyncStartAtomMatcher();
    *config.add_atom_matcher() = syncStartMatcher;
    AtomMatcher wakelockAcquireMatcher = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockAcquireMatcher;

    CountMetric countWakelock =
            createCountMetric("CountWakelock", wakelockAcquireMatcher.id(), nullopt, {});
    *countWakelock.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});

    CountMetric countSync = createCountMetric("CountSync", syncStartMatcher.id(), nullopt, {});
    *countSync.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::SYNC_STATE_CHANGED, {Position::FIRST});

    *config.add_count_metric() = countWakelock;
    *config.add_count_metric() = countSync;

    Alert alertPreserve =
            createAlert("AlertPreserve", countWakelock.id(), /*buckets=*/2, /*triggerSumGt=*/1);
    alertPreserve.set_refractory_period_secs(20);
    Alert alertReplace = createAlert("AlertReplace", countSync.id(), 1, 1);
    alertReplace.set_refractory_period_secs(1);
    Alert alertRemove = createAlert("AlertRemove", countWakelock.id(), 1, 0);
    alertRemove.set_refractory_period_secs(1);
    *config.add_alert() = alertReplace;
    *config.add_alert() = alertPreserve;
    *config.add_alert() = alertRemove;

    int preserveSubId = 1, replaceSubId = 2, removeSubId = 3;
    Subscription preserveSub = createSubscription("S1", Subscription::ALERT, alertPreserve.id());
    preserveSub.mutable_broadcast_subscriber_details()->set_subscriber_id(preserveSubId);
    Subscription replaceSub = createSubscription("S2", Subscription::ALERT, alertReplace.id());
    replaceSub.mutable_broadcast_subscriber_details()->set_subscriber_id(replaceSubId);
    Subscription removeSub = createSubscription("S3", Subscription::ALERT, alertRemove.id());
    removeSub.mutable_broadcast_subscriber_details()->set_subscriber_id(removeSubId);
    *config.add_subscription() = preserveSub;
    *config.add_subscription() = removeSub;
    *config.add_subscription() = replaceSub;

    int app1Uid = 123, app2Uid = 456;
    vector<int> attributionUids1 = {app1Uid};
    vector<string> attributionTags1 = {"App1"};
    vector<int> attributionUids2 = {app2Uid};
    vector<string> attributionTags2 = {"App2"};
    int64_t configUid = 123, configId = 987;
    ConfigKey key(configUid, configId);

    int alertPreserveCount = 0, alertRemoveCount = 0;
    StatsDimensionsValueParcel alertPreserveDims;
    StatsDimensionsValueParcel alertRemoveDims;

    // The binder calls here will happen synchronously because they are in-process.
    shared_ptr<MockPendingIntentRef> preserveBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*preserveBroadcast, sendSubscriberBroadcast(configUid, configId, preserveSub.id(),
                                                            alertPreserve.id(), _, _))
            .Times(2)
            .WillRepeatedly(
                    Invoke([&alertPreserveCount, &alertPreserveDims](
                                   int64_t, int64_t, int64_t, int64_t, const vector<string>&,
                                   const StatsDimensionsValueParcel& dimensionsValueParcel) {
                        alertPreserveCount++;
                        alertPreserveDims = dimensionsValueParcel;
                        return Status::ok();
                    }));

    shared_ptr<MockPendingIntentRef> replaceBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*replaceBroadcast, sendSubscriberBroadcast(configUid, configId, replaceSub.id(),
                                                           alertReplace.id(), _, _))
            .Times(0);

    shared_ptr<MockPendingIntentRef> removeBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*removeBroadcast, sendSubscriberBroadcast(configUid, configId, removeSub.id(),
                                                          alertRemove.id(), _, _))
            .Times(3)
            .WillRepeatedly(
                    Invoke([&alertRemoveCount, &alertRemoveDims](
                                   int64_t, int64_t, int64_t, int64_t, const vector<string>&,
                                   const StatsDimensionsValueParcel& dimensionsValueParcel) {
                        alertRemoveCount++;
                        alertRemoveDims = dimensionsValueParcel;
                        return Status::ok();
                    }));

    SubscriberReporter::getInstance().setBroadcastSubscriber(key, preserveSubId, preserveBroadcast);
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, replaceSubId, replaceBroadcast);
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, removeSubId, removeBroadcast);

    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    uint64_t bucket2StartTimeNs = bucketStartTimeNs + bucketSizeNs;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);

    StatsDimensionsValueParcel wlUid1 =
            CreateAttributionUidDimensionsValueParcel(util::WAKELOCK_STATE_CHANGED, app1Uid);
    StatsDimensionsValueParcel wlUid2 =
            CreateAttributionUidDimensionsValueParcel(util::WAKELOCK_STATE_CHANGED, app2Uid);

    processor->OnLogEvent(CreateAcquireWakelockEvent(bucketStartTimeNs + 15 * NS_PER_SEC,
                                                     attributionUids1, attributionTags1, "wl1")
                                  .get());
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 1);
    EXPECT_EQ(alertRemoveDims, wlUid1);

    processor->OnLogEvent(CreateAcquireWakelockEvent(bucketStartTimeNs + 20 * NS_PER_SEC,
                                                     attributionUids2, attributionTags2, "wl2")
                                  .get());
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 2);
    EXPECT_EQ(alertRemoveDims, wlUid2);

    processor->OnLogEvent(CreateSyncStartEvent(bucket2StartTimeNs + 5 * NS_PER_SEC,
                                               attributionUids1, attributionTags1, "sync1")
                                  .get());
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 2);

    // AlertPreserve enters 30 sec refractory period for uid2.
    processor->OnLogEvent(CreateAcquireWakelockEvent(bucket2StartTimeNs + 10 * NS_PER_SEC,
                                                     attributionUids2, attributionTags2, "wl2")
                                  .get());
    EXPECT_EQ(alertPreserveCount, 1);
    EXPECT_EQ(alertPreserveDims, wlUid2);
    EXPECT_EQ(alertRemoveCount, 3);
    EXPECT_EQ(alertRemoveDims, wlUid2);

    // Do config update.
    StatsdConfig newConfig;
    *newConfig.add_atom_matcher() = wakelockAcquireMatcher;
    *newConfig.add_atom_matcher() = syncStartMatcher;

    // Clear dims of sync metric, will result in alertReplace getting replaced.
    countSync.clear_dimensions_in_what();
    *newConfig.add_count_metric() = countSync;
    *newConfig.add_count_metric() = countWakelock;

    // New alert on existing metric. Should get current full bucket, but not history of 1st bucket.
    Alert alertNew = createAlert("AlertNew", countWakelock.id(), /*buckets=*/1, /*triggerSumGt=*/1);
    *newConfig.add_alert() = alertPreserve;
    *newConfig.add_alert() = alertNew;
    *newConfig.add_alert() = alertReplace;

    int newSubId = 4;
    Subscription newSub = createSubscription("S4", Subscription::ALERT, alertNew.id());
    newSub.mutable_broadcast_subscriber_details()->set_subscriber_id(newSubId);
    *newConfig.add_subscription() = newSub;
    *newConfig.add_subscription() = replaceSub;
    *newConfig.add_subscription() = preserveSub;

    int alertNewCount = 0;
    StatsDimensionsValueParcel alertNewDims;
    shared_ptr<MockPendingIntentRef> newBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*newBroadcast,
                sendSubscriberBroadcast(configUid, configId, newSub.id(), alertNew.id(), _, _))
            .Times(1)
            .WillRepeatedly(
                    Invoke([&alertNewCount, &alertNewDims](
                                   int64_t, int64_t, int64_t, int64_t, const vector<string>&,
                                   const StatsDimensionsValueParcel& dimensionsValueParcel) {
                        alertNewCount++;
                        alertNewDims = dimensionsValueParcel;
                        return Status::ok();
                    }));
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, newSubId, newBroadcast);

    int64_t updateTimeNs = bucket2StartTimeNs + 15 * NS_PER_SEC;
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // Within refractory of AlertPreserve, but AlertNew should fire since the full bucket has 2.
    processor->OnLogEvent(CreateAcquireWakelockEvent(bucket2StartTimeNs + 20 * NS_PER_SEC,
                                                     attributionUids2, attributionTags2, "wl2")
                                  .get());
    EXPECT_EQ(alertPreserveCount, 1);
    EXPECT_EQ(alertNewCount, 1);
    EXPECT_EQ(alertNewDims, wlUid2);

    // Wakelock for uid1 fired in first bucket, alert preserve should keep the history and fire.
    processor->OnLogEvent(CreateAcquireWakelockEvent(bucket2StartTimeNs + 25 * NS_PER_SEC,
                                                     attributionUids1, attributionTags1, "wl1")
                                  .get());
    EXPECT_EQ(alertPreserveCount, 2);
    EXPECT_EQ(alertPreserveDims, wlUid1);
    EXPECT_EQ(alertNewCount, 1);

    processor->OnLogEvent(CreateSyncStartEvent(bucket2StartTimeNs + 30 * NS_PER_SEC,
                                               attributionUids1, attributionTags1, "sync1")
                                  .get());
    EXPECT_EQ(alertPreserveCount, 2);
    EXPECT_EQ(alertNewCount, 1);
    EXPECT_EQ(alertRemoveCount, 3);

    // Clear data so it doesn't stay on disk.
    vector<uint8_t> buffer;
    processor->onDumpReport(key, bucket2StartTimeNs + 100 * NS_PER_SEC, true, true, ADB_DUMP, FAST,
                            &buffer);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, preserveSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, replaceSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, removeSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, newSubId);
}

TEST_F(ConfigUpdateE2eTest, TestAnomalyDurationMetric) {
    StatsdConfig config;

    AtomMatcher wakelockAcquireMatcher = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockAcquireMatcher;
    AtomMatcher wakelockReleaseMatcher = CreateReleaseWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockReleaseMatcher;
    AtomMatcher screenOnMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnMatcher;
    AtomMatcher screenOffMatcher = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = screenOffMatcher;

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    *holdingWakelockPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *config.add_predicate() = holdingWakelockPredicate;
    Predicate screenOnPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnPredicate;

    DurationMetric durationWakelock =
            createDurationMetric("DurWakelock", holdingWakelockPredicate.id(), nullopt, {});
    *durationWakelock.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    DurationMetric durationScreen =
            createDurationMetric("DurScreen", screenOnPredicate.id(), nullopt, {});
    *config.add_duration_metric() = durationScreen;
    *config.add_duration_metric() = durationWakelock;

    Alert alertPreserve = createAlert("AlertPreserve", durationWakelock.id(), /*buckets=*/2,
                                      /*triggerSumGt=*/30 * NS_PER_SEC);
    alertPreserve.set_refractory_period_secs(300);
    Alert alertReplace = createAlert("AlertReplace", durationScreen.id(), 2, 30 * NS_PER_SEC);
    alertReplace.set_refractory_period_secs(1);
    Alert alertRemove = createAlert("AlertRemove", durationWakelock.id(), 5, 10 * NS_PER_SEC);
    alertRemove.set_refractory_period_secs(1);
    *config.add_alert() = alertReplace;
    *config.add_alert() = alertPreserve;
    *config.add_alert() = alertRemove;

    int preserveSubId = 1, replaceSubId = 2, removeSubId = 3;
    Subscription preserveSub = createSubscription("S1", Subscription::ALERT, alertPreserve.id());
    preserveSub.mutable_broadcast_subscriber_details()->set_subscriber_id(preserveSubId);
    Subscription replaceSub = createSubscription("S2", Subscription::ALERT, alertReplace.id());
    replaceSub.mutable_broadcast_subscriber_details()->set_subscriber_id(replaceSubId);
    Subscription removeSub = createSubscription("S3", Subscription::ALERT, alertRemove.id());
    removeSub.mutable_broadcast_subscriber_details()->set_subscriber_id(removeSubId);
    *config.add_subscription() = preserveSub;
    *config.add_subscription() = removeSub;
    *config.add_subscription() = replaceSub;

    int app1Uid = 123, app2Uid = 456, app3Uid = 789, app4Uid = 111;
    vector<int> attributionUids1 = {app1Uid}, attributionUids2 = {app2Uid},
                attributionUids3 = {app3Uid}, attributionUids4 = {app4Uid};
    vector<string> attributionTags1 = {"App1"}, attributionTags2 = {"App2"},
                   attributionTags3 = {"App3"}, attributionTags4 = {"App4"};

    int64_t configUid = 123, configId = 987;
    ConfigKey key(configUid, configId);

    int alertPreserveCount = 0, alertRemoveCount = 0;
    StatsDimensionsValueParcel alertPreserveDims;
    StatsDimensionsValueParcel alertRemoveDims;

    // The binder calls here will happen synchronously because they are in-process.
    shared_ptr<MockPendingIntentRef> preserveBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*preserveBroadcast, sendSubscriberBroadcast(configUid, configId, preserveSub.id(),
                                                            alertPreserve.id(), _, _))
            .Times(4)
            .WillRepeatedly(
                    Invoke([&alertPreserveCount, &alertPreserveDims](
                                   int64_t, int64_t, int64_t, int64_t, const vector<string>&,
                                   const StatsDimensionsValueParcel& dimensionsValueParcel) {
                        alertPreserveCount++;
                        alertPreserveDims = dimensionsValueParcel;
                        return Status::ok();
                    }));

    shared_ptr<MockPendingIntentRef> replaceBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*replaceBroadcast, sendSubscriberBroadcast(configUid, configId, replaceSub.id(),
                                                           alertReplace.id(), _, _))
            .Times(0);

    shared_ptr<MockPendingIntentRef> removeBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*removeBroadcast, sendSubscriberBroadcast(configUid, configId, removeSub.id(),
                                                          alertRemove.id(), _, _))
            .Times(6)
            .WillRepeatedly(
                    Invoke([&alertRemoveCount, &alertRemoveDims](
                                   int64_t, int64_t, int64_t, int64_t, const vector<string>&,
                                   const StatsDimensionsValueParcel& dimensionsValueParcel) {
                        alertRemoveCount++;
                        alertRemoveDims = dimensionsValueParcel;
                        return Status::ok();
                    }));

    SubscriberReporter::getInstance().setBroadcastSubscriber(key, preserveSubId, preserveBroadcast);
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, replaceSubId, replaceBroadcast);
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, removeSubId, removeBroadcast);

    const sp<UidMap> uidMap = new UidMap();
    // call from StatsLogProcessor constructor
    Expectation initCall =
            EXPECT_CALL(*mLogEventFilter, setAtomIds(StatsLogProcessor::getDefaultAtomIdSet(), _))
                    .Times(1);
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(config), _))
            .Times(1)
            .After(initCall);
    const shared_ptr<StatsService> service =
            SharedRefBase::make<StatsService>(uidMap, /* queue */ nullptr, mLogEventFilter);
    sp<StatsLogProcessor> processor = service->mProcessor;
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
    int64_t bucketStartTimeNs = processor->mTimeBaseNs;
    int64_t roundedBucketStartTimeNs = bucketStartTimeNs / NS_PER_SEC * NS_PER_SEC;
    uint64_t bucket2StartTimeNs = bucketStartTimeNs + bucketSizeNs;
    processor->OnConfigUpdated(bucketStartTimeNs, key, config);

    StatsDimensionsValueParcel wlUid1 =
            CreateAttributionUidDimensionsValueParcel(util::WAKELOCK_STATE_CHANGED, app1Uid);
    StatsDimensionsValueParcel wlUid2 =
            CreateAttributionUidDimensionsValueParcel(util::WAKELOCK_STATE_CHANGED, app2Uid);
    StatsDimensionsValueParcel wlUid3 =
            CreateAttributionUidDimensionsValueParcel(util::WAKELOCK_STATE_CHANGED, app3Uid);
    StatsDimensionsValueParcel wlUid4 =
            CreateAttributionUidDimensionsValueParcel(util::WAKELOCK_STATE_CHANGED, app4Uid);

    int64_t eventTimeNs = bucketStartTimeNs + 15 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateAcquireWakelockEvent(eventTimeNs, attributionUids1, attributionTags1, "wl1")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 0);

    eventTimeNs = bucketStartTimeNs + 20 * NS_PER_SEC;
    processor->OnLogEvent(CreateScreenStateChangedEvent(
                                  eventTimeNs, android::view::DisplayStateEnum::DISPLAY_STATE_ON)
                                  .get(),
                          eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 0);

    // Uid 1 accumulates 15 seconds in bucket #1.
    eventTimeNs = bucketStartTimeNs + 30 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateReleaseWakelockEvent(eventTimeNs, attributionUids1, attributionTags1, "wl1")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 1);
    EXPECT_EQ(alertRemoveDims, wlUid1);

    // 20 seconds accumulated in bucket #1.
    eventTimeNs = bucketStartTimeNs + 40 * NS_PER_SEC;
    processor->OnLogEvent(CreateScreenStateChangedEvent(
                                  eventTimeNs, android::view::DisplayStateEnum::DISPLAY_STATE_OFF)
                                  .get(),
                          eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 1);

    eventTimeNs = bucket2StartTimeNs + 2 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateAcquireWakelockEvent(eventTimeNs, attributionUids4, attributionTags4, "wl4")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 1);

    eventTimeNs = bucket2StartTimeNs + 5 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateAcquireWakelockEvent(eventTimeNs, attributionUids2, attributionTags2, "wl2")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 1);

    // Alarm for alert remove for uid 4.
    eventTimeNs = bucket2StartTimeNs + 13 * NS_PER_SEC;
    processor->OnLogEvent(CreateBatteryStateChangedEvent(
                                  eventTimeNs, BatteryPluggedStateEnum::BATTERY_PLUGGED_USB)
                                  .get(),
                          eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 2);
    EXPECT_EQ(alertRemoveDims, wlUid4);

    // Uid3 will be pending at the update.
    // Also acts as the alarm for alert remove for uid 2.
    eventTimeNs = bucket2StartTimeNs + 30 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateAcquireWakelockEvent(eventTimeNs, attributionUids3, attributionTags3, "wl3")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 0);
    EXPECT_EQ(alertRemoveCount, 3);
    EXPECT_EQ(alertRemoveDims, wlUid2);

    // Alarm for alert preserve for uid 4, enters 5 min refractory period.
    eventTimeNs = bucket2StartTimeNs + 33 * NS_PER_SEC;
    processor->OnLogEvent(CreateBatteryStateChangedEvent(
                                  eventTimeNs, BatteryPluggedStateEnum::BATTERY_PLUGGED_USB)
                                  .get(),
                          eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 1);
    EXPECT_EQ(alertPreserveDims, wlUid4);
    EXPECT_EQ(alertRemoveCount, 3);

    // Uid 2 accumulates 32 seconds in partial bucket before the update. Alert preserve fires.
    // Preserve enters 5 min refractory for uid 2.
    // Alert remove fires again for uid 2 since the refractory has expired.
    eventTimeNs = bucket2StartTimeNs + 37 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateReleaseWakelockEvent(eventTimeNs, attributionUids2, attributionTags2, "wl2")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 2);
    EXPECT_EQ(alertPreserveDims, wlUid2);
    EXPECT_EQ(alertRemoveCount, 4);
    EXPECT_EQ(alertRemoveDims, wlUid2);

    // Alarm for alert remove for uid 3.
    eventTimeNs = bucket2StartTimeNs + 41 * NS_PER_SEC;
    processor->OnLogEvent(CreateBatteryStateChangedEvent(
                                  eventTimeNs, BatteryPluggedStateEnum::BATTERY_PLUGGED_USB)
                                  .get(),
                          eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 2);
    EXPECT_EQ(alertRemoveCount, 5);
    EXPECT_EQ(alertRemoveDims, wlUid3);

    // Release wl for uid 4, has accumulated 41 seconds in partial bucket before update.
    // Acts as alarm for uid3 of alert remove.
    eventTimeNs = bucket2StartTimeNs + 43 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateReleaseWakelockEvent(eventTimeNs, attributionUids4, attributionTags4, "wl4")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 2);
    EXPECT_EQ(alertRemoveCount, 6);
    EXPECT_EQ(alertRemoveDims, wlUid4);

    // Starts the timer for screen on.
    eventTimeNs = bucket2StartTimeNs + 46 * NS_PER_SEC;
    processor->OnLogEvent(CreateScreenStateChangedEvent(
                                  eventTimeNs, android::view::DisplayStateEnum::DISPLAY_STATE_ON)
                                  .get(),
                          eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 2);
    EXPECT_EQ(alertRemoveCount, 6);

    // Do config update.
    StatsdConfig newConfig;
    *newConfig.add_atom_matcher() = wakelockAcquireMatcher;
    *newConfig.add_atom_matcher() = screenOffMatcher;
    *newConfig.add_atom_matcher() = wakelockReleaseMatcher;
    *newConfig.add_atom_matcher() = screenOnMatcher;
    *newConfig.add_predicate() = screenOnPredicate;
    *newConfig.add_predicate() = holdingWakelockPredicate;
    *newConfig.add_duration_metric() = durationWakelock;
    *newConfig.add_duration_metric() = durationScreen;

    alertReplace.set_refractory_period_secs(2);  // Causes replacement.
    // New alert on existing metric. Should get current full bucket, but not history of 1st bucket.
    Alert alertNew = createAlert("AlertNew", durationWakelock.id(), /*buckets=*/2,
                                 /*triggerSumGt=*/40 * NS_PER_SEC);
    *newConfig.add_alert() = alertPreserve;
    *newConfig.add_alert() = alertNew;
    *newConfig.add_alert() = alertReplace;

    int newSubId = 4;
    Subscription newSub = createSubscription("S4", Subscription::ALERT, alertNew.id());
    newSub.mutable_broadcast_subscriber_details()->set_subscriber_id(newSubId);
    *newConfig.add_subscription() = newSub;
    *newConfig.add_subscription() = replaceSub;
    *newConfig.add_subscription() = preserveSub;

    int alertNewCount = 0;
    StatsDimensionsValueParcel alertNewDims;
    shared_ptr<MockPendingIntentRef> newBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*newBroadcast,
                sendSubscriberBroadcast(configUid, configId, newSub.id(), alertNew.id(), _, _))
            .Times(3)
            .WillRepeatedly(
                    Invoke([&alertNewCount, &alertNewDims](
                                   int64_t, int64_t, int64_t, int64_t, const vector<string>&,
                                   const StatsDimensionsValueParcel& dimensionsValueParcel) {
                        alertNewCount++;
                        alertNewDims = dimensionsValueParcel;
                        return Status::ok();
                    }));
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, newSubId, newBroadcast);

    int64_t updateTimeNs = bucket2StartTimeNs + 50 * NS_PER_SEC;
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), _)).Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, newConfig);

    // Alert preserve will set alarm after the refractory period, but alert new will set it for
    // 8 seconds after this event.
    // Alert new should fire for uid 4, since it has already accumulated 41s and should fire on the
    // first event after the update.
    eventTimeNs = bucket2StartTimeNs + 55 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateAcquireWakelockEvent(eventTimeNs, attributionUids2, attributionTags2, "wl2")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 2);
    EXPECT_EQ(alertNewCount, 1);
    EXPECT_EQ(alertNewDims, wlUid4);

    eventTimeNs = bucket2StartTimeNs + 60 * NS_PER_SEC;
    // Alert replace doesn't fire because it has lost history.
    processor->OnLogEvent(CreateScreenStateChangedEvent(
                                  eventTimeNs, android::view::DisplayStateEnum::DISPLAY_STATE_OFF)
                                  .get(),
                          eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 2);
    EXPECT_EQ(alertNewCount, 1);

    // Alert preserve has 15 seconds from 1st bucket, so alert should fire at bucket2Start + 80.
    // Serves as alarm for alert new for uid2.
    // Also serves as alarm for alert preserve for uid 3, which began at bucket2Start + 30.
    eventTimeNs = bucket2StartTimeNs + 65 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateAcquireWakelockEvent(eventTimeNs, attributionUids1, attributionTags1, "wl1")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 3);
    EXPECT_EQ(alertPreserveDims, wlUid3);
    EXPECT_EQ(alertNewCount, 2);
    EXPECT_EQ(alertNewDims, wlUid2);

    // Release wakelock for uid1, causing alert preserve to fire for uid1.
    // Also serves as alarm for alert new for uid3.
    eventTimeNs = bucket2StartTimeNs + 81 * NS_PER_SEC;
    processor->OnLogEvent(
            CreateReleaseWakelockEvent(eventTimeNs, attributionUids1, attributionTags1, "wl1")
                    .get(),
            eventTimeNs);
    EXPECT_EQ(alertPreserveCount, 4);
    EXPECT_EQ(alertPreserveDims, wlUid1);
    EXPECT_EQ(alertNewCount, 3);
    EXPECT_EQ(alertNewDims, wlUid3);

    // Clear data so it doesn't stay on disk.
    vector<uint8_t> buffer;
    processor->onDumpReport(key, bucket2StartTimeNs + 100 * NS_PER_SEC, true, true, ADB_DUMP, FAST,
                            &buffer);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, preserveSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, replaceSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, removeSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, newSubId);
}

TEST_F(ConfigUpdateE2eTest, TestAlarms) {
    StatsdConfig config;
    Alarm alarmPreserve = createAlarm("AlarmPreserve", /*offset*/ 5 * MS_PER_SEC,
                                      /*period*/ TimeUnitToBucketSizeInMillis(ONE_MINUTE));
    Alarm alarmReplace = createAlarm("AlarmReplace", /*offset*/ 1,
                                     /*period*/ TimeUnitToBucketSizeInMillis(FIVE_MINUTES));
    Alarm alarmRemove = createAlarm("AlarmRemove", /*offset*/ 1,
                                    /*period*/ TimeUnitToBucketSizeInMillis(ONE_MINUTE));
    *config.add_alarm() = alarmReplace;
    *config.add_alarm() = alarmPreserve;
    *config.add_alarm() = alarmRemove;

    int preserveSubId = 1, replaceSubId = 2, removeSubId = 3;
    Subscription preserveSub = createSubscription("S1", Subscription::ALARM, alarmPreserve.id());
    preserveSub.mutable_broadcast_subscriber_details()->set_subscriber_id(preserveSubId);
    Subscription replaceSub = createSubscription("S2", Subscription::ALARM, alarmReplace.id());
    replaceSub.mutable_broadcast_subscriber_details()->set_subscriber_id(replaceSubId);
    Subscription removeSub = createSubscription("S3", Subscription::ALARM, alarmRemove.id());
    removeSub.mutable_broadcast_subscriber_details()->set_subscriber_id(removeSubId);
    *config.add_subscription() = preserveSub;
    *config.add_subscription() = removeSub;
    *config.add_subscription() = replaceSub;

    int64_t configUid = 123, configId = 987;
    ConfigKey key(configUid, configId);

    int alarmPreserveCount = 0, alarmReplaceCount = 0, alarmRemoveCount = 0;

    // The binder calls here will happen synchronously because they are in-process.
    shared_ptr<MockPendingIntentRef> preserveBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*preserveBroadcast, sendSubscriberBroadcast(configUid, configId, preserveSub.id(),
                                                            alarmPreserve.id(), _, _))
            .Times(4)
            .WillRepeatedly([&alarmPreserveCount](int64_t, int64_t, int64_t, int64_t,
                                                  const vector<string>&,
                                                  const StatsDimensionsValueParcel&) {
                alarmPreserveCount++;
                return Status::ok();
            });

    shared_ptr<MockPendingIntentRef> replaceBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*replaceBroadcast, sendSubscriberBroadcast(configUid, configId, replaceSub.id(),
                                                           alarmReplace.id(), _, _))
            .Times(2)
            .WillRepeatedly([&alarmReplaceCount](int64_t, int64_t, int64_t, int64_t,
                                                 const vector<string>&,
                                                 const StatsDimensionsValueParcel&) {
                alarmReplaceCount++;
                return Status::ok();
            });

    shared_ptr<MockPendingIntentRef> removeBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*removeBroadcast, sendSubscriberBroadcast(configUid, configId, removeSub.id(),
                                                          alarmRemove.id(), _, _))
            .Times(1)
            .WillRepeatedly([&alarmRemoveCount](int64_t, int64_t, int64_t, int64_t,
                                                const vector<string>&,
                                                const StatsDimensionsValueParcel&) {
                alarmRemoveCount++;
                return Status::ok();
            });

    SubscriberReporter::getInstance().setBroadcastSubscriber(key, preserveSubId, preserveBroadcast);
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, replaceSubId, replaceBroadcast);
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, removeSubId, removeBroadcast);

    int64_t startTimeSec = 10;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(
            startTimeSec * NS_PER_SEC, startTimeSec * NS_PER_SEC, config, key, mLogEventFilter);

    sp<AlarmMonitor> alarmMonitor = processor->getPeriodicAlarmMonitor();
    // First alarm is for alarm preserve's offset of 5 seconds.
    EXPECT_EQ(alarmMonitor->getRegisteredAlarmTimeSec(), startTimeSec + 5);

    // Alarm fired at 5. AlarmPreserve should fire.
    int32_t alarmFiredTimestampSec = startTimeSec + 5;
    auto alarmSet = alarmMonitor->popSoonerThan(static_cast<uint32_t>(alarmFiredTimestampSec));
    processor->onPeriodicAlarmFired(alarmFiredTimestampSec * NS_PER_SEC, alarmSet);
    EXPECT_EQ(alarmPreserveCount, 1);
    EXPECT_EQ(alarmReplaceCount, 0);
    EXPECT_EQ(alarmRemoveCount, 0);
    EXPECT_EQ(alarmMonitor->getRegisteredAlarmTimeSec(), startTimeSec + 60);

    // Alarm fired at 75. AlarmPreserve and AlarmRemove should fire.
    alarmFiredTimestampSec = startTimeSec + 75;
    alarmSet = alarmMonitor->popSoonerThan(static_cast<uint32_t>(alarmFiredTimestampSec));
    processor->onPeriodicAlarmFired(alarmFiredTimestampSec * NS_PER_SEC, alarmSet);
    EXPECT_EQ(alarmPreserveCount, 2);
    EXPECT_EQ(alarmReplaceCount, 0);
    EXPECT_EQ(alarmRemoveCount, 1);
    EXPECT_EQ(alarmMonitor->getRegisteredAlarmTimeSec(), startTimeSec + 120);

    // Do config update.
    StatsdConfig newConfig;

    // Change alarm replace's definition.
    alarmReplace.set_period_millis(TimeUnitToBucketSizeInMillis(ONE_MINUTE));
    Alarm alarmNew = createAlarm("AlarmNew", /*offset*/ 1,
                                 /*period*/ TimeUnitToBucketSizeInMillis(FIVE_MINUTES));
    *newConfig.add_alarm() = alarmNew;
    *newConfig.add_alarm() = alarmPreserve;
    *newConfig.add_alarm() = alarmReplace;

    int newSubId = 4;
    Subscription newSub = createSubscription("S4", Subscription::ALARM, alarmNew.id());
    newSub.mutable_broadcast_subscriber_details()->set_subscriber_id(newSubId);
    *newConfig.add_subscription() = newSub;
    *newConfig.add_subscription() = replaceSub;
    *newConfig.add_subscription() = preserveSub;

    int alarmNewCount = 0;
    shared_ptr<MockPendingIntentRef> newBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*newBroadcast,
                sendSubscriberBroadcast(configUid, configId, newSub.id(), alarmNew.id(), _, _))
            .Times(1)
            .WillRepeatedly([&alarmNewCount](int64_t, int64_t, int64_t, int64_t,
                                             const vector<string>&,
                                             const StatsDimensionsValueParcel&) {
                alarmNewCount++;
                return Status::ok();
            });
    SubscriberReporter::getInstance().setBroadcastSubscriber(key, newSubId, newBroadcast);
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(newConfig), _)).Times(1);
    processor->OnConfigUpdated((startTimeSec + 90) * NS_PER_SEC, key, newConfig);
    // After the update, the alarm time should remain unchanged since alarm replace now fires every
    // minute with no offset.
    EXPECT_EQ(alarmMonitor->getRegisteredAlarmTimeSec(), startTimeSec + 120);

    // Alarm fired at 120. AlermReplace should fire.
    alarmFiredTimestampSec = startTimeSec + 120;
    alarmSet = alarmMonitor->popSoonerThan(static_cast<uint32_t>(alarmFiredTimestampSec));
    processor->onPeriodicAlarmFired(alarmFiredTimestampSec * NS_PER_SEC, alarmSet);
    EXPECT_EQ(alarmPreserveCount, 2);
    EXPECT_EQ(alarmReplaceCount, 1);
    EXPECT_EQ(alarmNewCount, 0);
    EXPECT_EQ(alarmMonitor->getRegisteredAlarmTimeSec(), startTimeSec + 125);

    // Alarm fired at 130. AlarmPreserve should fire.
    alarmFiredTimestampSec = startTimeSec + 130;
    alarmSet = alarmMonitor->popSoonerThan(static_cast<uint32_t>(alarmFiredTimestampSec));
    processor->onPeriodicAlarmFired(alarmFiredTimestampSec * NS_PER_SEC, alarmSet);
    EXPECT_EQ(alarmPreserveCount, 3);
    EXPECT_EQ(alarmReplaceCount, 1);
    EXPECT_EQ(alarmNewCount, 0);
    EXPECT_EQ(alarmMonitor->getRegisteredAlarmTimeSec(), startTimeSec + 180);

    // Alarm fired late at 310. All alerms should fire.
    alarmFiredTimestampSec = startTimeSec + 310;
    alarmSet = alarmMonitor->popSoonerThan(static_cast<uint32_t>(alarmFiredTimestampSec));
    processor->onPeriodicAlarmFired(alarmFiredTimestampSec * NS_PER_SEC, alarmSet);
    EXPECT_EQ(alarmPreserveCount, 4);
    EXPECT_EQ(alarmReplaceCount, 2);
    EXPECT_EQ(alarmNewCount, 1);
    EXPECT_EQ(alarmMonitor->getRegisteredAlarmTimeSec(), startTimeSec + 360);

    // Clear subscribers
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, preserveSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, replaceSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, removeSubId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(key, newSubId);
}

TEST_F(ConfigUpdateE2eTest, TestNewDurationExistingWhat) {
    StatsdConfig config;
    *config.add_atom_matcher() = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = CreateReleaseWakelockAtomMatcher();

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    *config.add_predicate() = holdingWakelockPredicate;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(FIVE_MINUTES) * 1000000LL;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);

    int app1Uid = 123;
    vector<int> attributionUids1 = {app1Uid};
    vector<string> attributionTags1 = {"App1"};
    // Create a wakelock acquire, causing the condition to be true.
    unique_ptr<LogEvent> event = CreateAcquireWakelockEvent(bucketStartTimeNs + 10 * NS_PER_SEC,
                                                            attributionUids1, attributionTags1,
                                                            "wl1");  // 0:10
    processor->OnLogEvent(event.get());

    // Add metric.
    DurationMetric* durationMetric = config.add_duration_metric();
    durationMetric->set_id(StringToId("WakelockDuration"));
    durationMetric->set_what(holdingWakelockPredicate.id());
    durationMetric->set_aggregation_type(DurationMetric::SUM);
    durationMetric->set_bucket(FIVE_MINUTES);

    uint64_t updateTimeNs = bucketStartTimeNs + 60 * NS_PER_SEC;  // 1:00
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(config), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, config);

    event = CreateReleaseWakelockEvent(bucketStartTimeNs + 80 * NS_PER_SEC, attributionUids1,
                                       attributionTags1,
                                       "wl1");  // 1:20
    processor->OnLogEvent(event.get());
    uint64_t dumpTimeNs = bucketStartTimeNs + 90 * NS_PER_SEC;  // 1:30
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 1);
    ASSERT_EQ(reports.reports(0).metrics_size(), 1);
    EXPECT_TRUE(reports.reports(0).metrics(0).has_duration_metrics());

    StatsLogReport::DurationMetricDataWrapper metricData;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).duration_metrics(), &metricData);
    ASSERT_EQ(metricData.data_size(), 1);
    DurationMetricData data = metricData.data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);

    DurationBucketInfo bucketInfo = data.bucket_info(0);
    EXPECT_EQ(bucketInfo.start_bucket_elapsed_nanos(), updateTimeNs);
    EXPECT_EQ(bucketInfo.end_bucket_elapsed_nanos(), dumpTimeNs);
    EXPECT_EQ(bucketInfo.duration_nanos(), 20 * NS_PER_SEC);
}

TEST_F(ConfigUpdateE2eTest, TestNewDurationExistingWhatSlicedCondition) {
    StatsdConfig config;
    *config.add_atom_matcher() = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = CreateReleaseWakelockAtomMatcher();
    *config.add_atom_matcher() = CreateMoveToBackgroundAtomMatcher();
    *config.add_atom_matcher() = CreateMoveToForegroundAtomMatcher();

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    // The predicate is dimensioning by first attribution node by uid.
    *holdingWakelockPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *config.add_predicate() = holdingWakelockPredicate;

    Predicate isInBackgroundPredicate = CreateIsInBackgroundPredicate();
    *isInBackgroundPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateDimensions(util::ACTIVITY_FOREGROUND_STATE_CHANGED, {1 /*uid*/});
    *config.add_predicate() = isInBackgroundPredicate;

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(FIVE_MINUTES) * 1000000LL;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);

    int app1Uid = 123, app2Uid = 456;
    vector<int> attributionUids1 = {app1Uid};
    vector<string> attributionTags1 = {"App1"};
    vector<int> attributionUids2 = {app2Uid};
    vector<string> attributionTags2 = {"App2"};
    unique_ptr<LogEvent> event = CreateAcquireWakelockEvent(bucketStartTimeNs + 10 * NS_PER_SEC,
                                                            attributionUids1, attributionTags1,
                                                            "wl1");  // 0:10
    processor->OnLogEvent(event.get());
    event = CreateMoveToBackgroundEvent(bucketStartTimeNs + 22 * NS_PER_SEC, app1Uid);  // 0:22
    processor->OnLogEvent(event.get());
    event = CreateAcquireWakelockEvent(bucketStartTimeNs + 35 * NS_PER_SEC, attributionUids2,
                                       attributionTags2,
                                       "wl1");  // 0:35
    processor->OnLogEvent(event.get());

    // Add metric.
    DurationMetric* durationMetric = config.add_duration_metric();
    durationMetric->set_id(StringToId("WakelockDuration"));
    durationMetric->set_what(holdingWakelockPredicate.id());
    durationMetric->set_condition(isInBackgroundPredicate.id());
    durationMetric->set_aggregation_type(DurationMetric::SUM);
    // The metric is dimensioning by first attribution node and only by uid.
    *durationMetric->mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    durationMetric->set_bucket(FIVE_MINUTES);
    // Links between wakelock state atom and condition of app is in background.
    auto links = durationMetric->add_links();
    links->set_condition(isInBackgroundPredicate.id());
    *links->mutable_fields_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *links->mutable_fields_in_condition() =
            CreateDimensions(util::ACTIVITY_FOREGROUND_STATE_CHANGED, {1 /*uid*/});

    uint64_t updateTimeNs = bucketStartTimeNs + 60 * NS_PER_SEC;  // 1:00
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(config), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, config);

    event = CreateMoveToBackgroundEvent(bucketStartTimeNs + 73 * NS_PER_SEC, app2Uid);  // 1:13
    processor->OnLogEvent(event.get());
    event = CreateReleaseWakelockEvent(bucketStartTimeNs + 84 * NS_PER_SEC, attributionUids1,
                                       attributionTags1, "wl1");  // 1:24
    processor->OnLogEvent(event.get());

    uint64_t dumpTimeNs = bucketStartTimeNs + 90 * NS_PER_SEC;  //  1:30
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 1);
    ASSERT_EQ(reports.reports(0).metrics_size(), 1);
    EXPECT_TRUE(reports.reports(0).metrics(0).has_duration_metrics());

    StatsLogReport::DurationMetricDataWrapper metricData;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).duration_metrics(), &metricData);
    ASSERT_EQ(metricData.data_size(), 2);

    DurationMetricData data = metricData.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::WAKELOCK_STATE_CHANGED,
                                    app1Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    DurationBucketInfo bucketInfo = data.bucket_info(0);
    EXPECT_EQ(bucketInfo.duration_nanos(), 24 * NS_PER_SEC);

    data = metricData.data(1);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::WAKELOCK_STATE_CHANGED,
                                    app2Uid);
    ASSERT_EQ(data.bucket_info_size(), 1);
    bucketInfo = data.bucket_info(0);
    EXPECT_EQ(bucketInfo.duration_nanos(), 17 * NS_PER_SEC);
}

TEST_F(ConfigUpdateE2eTest, TestNewDurationExistingWhatSlicedState) {
    StatsdConfig config;
    *config.add_atom_matcher() = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = CreateReleaseWakelockAtomMatcher();

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    // The predicate is dimensioning by first attribution node by uid.
    *holdingWakelockPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *config.add_predicate() = holdingWakelockPredicate;

    auto uidProcessState = CreateUidProcessState();
    *config.add_state() = uidProcessState;

    // Count metric. We don't care about this one. Only use it so the StateTracker gets persisted.
    CountMetric* countMetric = config.add_count_metric();
    countMetric->set_id(StringToId("Tmp"));
    countMetric->set_what(config.atom_matcher(0).id());
    countMetric->add_slice_by_state(uidProcessState.id());
    // The metric is dimensioning by first attribution node and only by uid.
    *countMetric->mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    countMetric->set_bucket(FIVE_MINUTES);
    auto stateLink = countMetric->add_state_link();
    stateLink->set_state_atom_id(util::UID_PROCESS_STATE_CHANGED);
    *stateLink->mutable_fields_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *stateLink->mutable_fields_in_state() =
            CreateDimensions(util::UID_PROCESS_STATE_CHANGED, {1 /*uid*/});
    config.add_no_report_metric(countMetric->id());

    ConfigKey key(123, 987);
    uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    uint64_t bucketSizeNs = TimeUnitToBucketSizeInMillis(FIVE_MINUTES) * 1000000LL;
    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs,
                                                              config, key, mLogEventFilter);

    int app1Uid = 123, app2Uid = 456;
    vector<int> attributionUids1 = {app1Uid};
    vector<string> attributionTags1 = {"App1"};
    vector<int> attributionUids2 = {app2Uid};
    vector<string> attributionTags2 = {"App2"};
    unique_ptr<LogEvent> event = CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 10 * NS_PER_SEC, app1Uid,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);  // 0:10
    processor->OnLogEvent(event.get());
    event = CreateAcquireWakelockEvent(bucketStartTimeNs + 22 * NS_PER_SEC, attributionUids1,
                                       attributionTags1,
                                       "wl1");  // 0:22
    processor->OnLogEvent(event.get());
    event = CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 30 * NS_PER_SEC, app2Uid,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);  // 0:30
    processor->OnLogEvent(event.get());

    // Add metric.
    DurationMetric* durationMetric = config.add_duration_metric();
    durationMetric->set_id(StringToId("WakelockDuration"));
    durationMetric->set_what(holdingWakelockPredicate.id());
    durationMetric->add_slice_by_state(uidProcessState.id());
    durationMetric->set_aggregation_type(DurationMetric::SUM);
    // The metric is dimensioning by first attribution node and only by uid.
    *durationMetric->mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    durationMetric->set_bucket(FIVE_MINUTES);
    // Links between wakelock state atom and condition of app is in background.
    stateLink = durationMetric->add_state_link();
    stateLink->set_state_atom_id(util::UID_PROCESS_STATE_CHANGED);
    *stateLink->mutable_fields_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *stateLink->mutable_fields_in_state() =
            CreateDimensions(util::UID_PROCESS_STATE_CHANGED, {1 /*uid*/});

    uint64_t updateTimeNs = bucketStartTimeNs + 60 * NS_PER_SEC;  // 1:00
    EXPECT_CALL(*mLogEventFilter, setAtomIds(CreateAtomIdSetFromConfig(config), processor.get()))
            .Times(1);
    processor->OnConfigUpdated(updateTimeNs, key, config);

    event = CreateAcquireWakelockEvent(bucketStartTimeNs + 72 * NS_PER_SEC, attributionUids2,
                                       attributionTags2,
                                       "wl1");  // 1:13
    processor->OnLogEvent(event.get());
    event = CreateUidProcessStateChangedEvent(
            bucketStartTimeNs + 75 * NS_PER_SEC, app1Uid,
            android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);  // 1:15
    processor->OnLogEvent(event.get());
    event = CreateReleaseWakelockEvent(bucketStartTimeNs + 84 * NS_PER_SEC, attributionUids1,
                                       attributionTags1, "wl1");  // 1:24
    processor->OnLogEvent(event.get());

    uint64_t dumpTimeNs = bucketStartTimeNs + 90 * NS_PER_SEC;  //  1:30
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 1);
    ASSERT_EQ(reports.reports(0).metrics_size(), 1);
    EXPECT_TRUE(reports.reports(0).metrics(0).has_duration_metrics());

    StatsLogReport::DurationMetricDataWrapper metricData;
    sortMetricDataByDimensionsValue(reports.reports(0).metrics(0).duration_metrics(), &metricData);
    ASSERT_EQ(metricData.data_size(), 3);

    DurationMetricData data = metricData.data(0);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::WAKELOCK_STATE_CHANGED,
                                    app1Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    DurationBucketInfo bucketInfo = data.bucket_info(0);
    EXPECT_EQ(bucketInfo.duration_nanos(), 15 * NS_PER_SEC);

    data = metricData.data(1);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::WAKELOCK_STATE_CHANGED,
                                    app1Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_BACKGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    bucketInfo = data.bucket_info(0);
    EXPECT_EQ(bucketInfo.duration_nanos(), 9 * NS_PER_SEC);

    data = metricData.data(2);
    ValidateAttributionUidDimension(data.dimensions_in_what(), util::WAKELOCK_STATE_CHANGED,
                                    app2Uid);
    ValidateStateValue(data.slice_by_state(), util::UID_PROCESS_STATE_CHANGED,
                       android::app::ProcessStateEnum::PROCESS_STATE_IMPORTANT_FOREGROUND);
    ASSERT_EQ(data.bucket_info_size(), 1);
    bucketInfo = data.bucket_info(0);
    EXPECT_EQ(bucketInfo.duration_nanos(), 18 * NS_PER_SEC);
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
