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

#include "statsd_test_util.h"

#include <aggregator.pb.h>
#include <aidl/android/util/StatsEventParcel.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>

#include "matchers/SimpleAtomMatchingTracker.h"
#include "stats_event.h"
#include "stats_util.h"

using aidl::android::util::StatsEventParcel;
using android::base::SetProperty;
using android::base::StringPrintf;
using std::shared_ptr;
using zetasketch::android::AggregatorStateProto;

namespace android {
namespace os {
namespace statsd {

bool StatsServiceConfigTest::sendConfig(const StatsdConfig& config) {
    string str;
    config.SerializeToString(&str);
    std::vector<uint8_t> configAsVec(str.begin(), str.end());
    return service->addConfiguration(kConfigKey, configAsVec, kCallingUid).isOk();
}

ConfigMetricsReport StatsServiceConfigTest::getReports(sp<StatsLogProcessor> processor,
                                                       int64_t timestamp, bool include_current) {
    vector<uint8_t> output;
    ConfigKey configKey(kCallingUid, kConfigKey);
    processor->onDumpReport(configKey, timestamp, include_current /* include_current_bucket*/,
                            true /* erase_data */, ADB_DUMP, NO_TIME_CONSTRAINTS, &output);
    ConfigMetricsReportList reports;
    reports.ParseFromArray(output.data(), output.size());
    EXPECT_EQ(1, reports.reports_size());
    return reports.reports(0);
}

StatsLogReport outputStreamToProto(ProtoOutputStream* proto) {
    vector<uint8_t> bytes;
    bytes.resize(proto->size());
    size_t pos = 0;
    sp<ProtoReader> reader = proto->data();

    while (reader->readBuffer() != NULL) {
        size_t toRead = reader->currentToRead();
        std::memcpy(&((bytes)[pos]), reader->readBuffer(), toRead);
        pos += toRead;
        reader->move(toRead);
    }

    StatsLogReport report;
    report.ParseFromArray(bytes.data(), bytes.size());
    return report;
}

AtomMatcher CreateSimpleAtomMatcher(const string& name, int atomId) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(atomId);
    return atom_matcher;
}

AtomMatcher CreateTemperatureAtomMatcher() {
    return CreateSimpleAtomMatcher("TemperatureMatcher", util::TEMPERATURE);
}

AtomMatcher CreateScheduledJobStateChangedAtomMatcher(const string& name,
                                                      ScheduledJobStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::SCHEDULED_JOB_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(3);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateStartScheduledJobAtomMatcher() {
    return CreateScheduledJobStateChangedAtomMatcher("ScheduledJobStart",
                                                     ScheduledJobStateChanged::STARTED);
}

AtomMatcher CreateFinishScheduledJobAtomMatcher() {
    return CreateScheduledJobStateChangedAtomMatcher("ScheduledJobFinish",
                                                     ScheduledJobStateChanged::FINISHED);
}

AtomMatcher CreateScheduleScheduledJobAtomMatcher() {
    return CreateScheduledJobStateChangedAtomMatcher("ScheduledJobSchedule",
                                                     ScheduledJobStateChanged::SCHEDULED);
}

AtomMatcher CreateScreenBrightnessChangedAtomMatcher() {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId("ScreenBrightnessChanged"));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::SCREEN_BRIGHTNESS_CHANGED);
    return atom_matcher;
}

AtomMatcher CreateUidProcessStateChangedAtomMatcher() {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId("UidProcessStateChanged"));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::UID_PROCESS_STATE_CHANGED);
    return atom_matcher;
}

AtomMatcher CreateWakelockStateChangedAtomMatcher(const string& name,
                                                  WakelockStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::WAKELOCK_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(4);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateAcquireWakelockAtomMatcher() {
    return CreateWakelockStateChangedAtomMatcher("AcquireWakelock", WakelockStateChanged::ACQUIRE);
}

AtomMatcher CreateReleaseWakelockAtomMatcher() {
    return CreateWakelockStateChangedAtomMatcher("ReleaseWakelock", WakelockStateChanged::RELEASE);
}

AtomMatcher CreateBatterySaverModeStateChangedAtomMatcher(
    const string& name, BatterySaverModeStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::BATTERY_SAVER_MODE_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(1);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateBatterySaverModeStartAtomMatcher() {
    return CreateBatterySaverModeStateChangedAtomMatcher(
        "BatterySaverModeStart", BatterySaverModeStateChanged::ON);
}


AtomMatcher CreateBatterySaverModeStopAtomMatcher() {
    return CreateBatterySaverModeStateChangedAtomMatcher(
        "BatterySaverModeStop", BatterySaverModeStateChanged::OFF);
}

AtomMatcher CreateBatteryStateChangedAtomMatcher(const string& name,
                                                 BatteryPluggedStateEnum state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::PLUGGED_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(1);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateBatteryStateNoneMatcher() {
    return CreateBatteryStateChangedAtomMatcher("BatteryPluggedNone",
                                                BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE);
}

AtomMatcher CreateBatteryStateUsbMatcher() {
    return CreateBatteryStateChangedAtomMatcher("BatteryPluggedUsb",
                                                BatteryPluggedStateEnum::BATTERY_PLUGGED_USB);
}

AtomMatcher CreateScreenStateChangedAtomMatcher(
    const string& name, android::view::DisplayStateEnum state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::SCREEN_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(1);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateScreenTurnedOnAtomMatcher() {
    return CreateScreenStateChangedAtomMatcher("ScreenTurnedOn",
            android::view::DisplayStateEnum::DISPLAY_STATE_ON);
}

AtomMatcher CreateScreenTurnedOffAtomMatcher() {
    return CreateScreenStateChangedAtomMatcher("ScreenTurnedOff",
            ::android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
}

AtomMatcher CreateSyncStateChangedAtomMatcher(
    const string& name, SyncStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::SYNC_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(3);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateSyncStartAtomMatcher() {
    return CreateSyncStateChangedAtomMatcher("SyncStart", SyncStateChanged::ON);
}

AtomMatcher CreateSyncEndAtomMatcher() {
    return CreateSyncStateChangedAtomMatcher("SyncEnd", SyncStateChanged::OFF);
}

AtomMatcher CreateActivityForegroundStateChangedAtomMatcher(
    const string& name, ActivityForegroundStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::ACTIVITY_FOREGROUND_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(4);  // Activity field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateMoveToBackgroundAtomMatcher() {
    return CreateActivityForegroundStateChangedAtomMatcher(
        "Background", ActivityForegroundStateChanged::BACKGROUND);
}

AtomMatcher CreateMoveToForegroundAtomMatcher() {
    return CreateActivityForegroundStateChangedAtomMatcher(
        "Foreground", ActivityForegroundStateChanged::FOREGROUND);
}

AtomMatcher CreateProcessLifeCycleStateChangedAtomMatcher(
    const string& name, ProcessLifeCycleStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(util::PROCESS_LIFE_CYCLE_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(3);  // Process state field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateProcessCrashAtomMatcher() {
    return CreateProcessLifeCycleStateChangedAtomMatcher(
        "Crashed", ProcessLifeCycleStateChanged::CRASHED);
}

AtomMatcher CreateAppStartOccurredAtomMatcher() {
    return CreateSimpleAtomMatcher("AppStartOccurredMatcher", util::APP_START_OCCURRED);
}

AtomMatcher CreateTestAtomRepeatedStateAtomMatcher(const string& name,
                                                   TestAtomReported::State state,
                                                   Position position) {
    AtomMatcher atom_matcher = CreateSimpleAtomMatcher(name, util::TEST_ATOM_REPORTED);
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(14);  // Repeated enum field.
    field_value_matcher->set_eq_int(state);
    field_value_matcher->set_position(position);
    return atom_matcher;
}

AtomMatcher CreateTestAtomRepeatedStateFirstOffAtomMatcher() {
    return CreateTestAtomRepeatedStateAtomMatcher("TestFirstStateOff", TestAtomReported::OFF,
                                                  Position::FIRST);
}

AtomMatcher CreateTestAtomRepeatedStateFirstOnAtomMatcher() {
    return CreateTestAtomRepeatedStateAtomMatcher("TestFirstStateOn", TestAtomReported::ON,
                                                  Position::FIRST);
}

AtomMatcher CreateTestAtomRepeatedStateAnyOnAtomMatcher() {
    return CreateTestAtomRepeatedStateAtomMatcher("TestAnyStateOn", TestAtomReported::ON,
                                                  Position::ANY);
}

void addMatcherToMatcherCombination(const AtomMatcher& matcher, AtomMatcher* combinationMatcher) {
    combinationMatcher->mutable_combination()->add_matcher(matcher.id());
}

Predicate CreateScheduledJobPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("ScheduledJobRunningPredicate"));
    predicate.mutable_simple_predicate()->set_start(StringToId("ScheduledJobStart"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("ScheduledJobFinish"));
    return predicate;
}

Predicate CreateBatterySaverModePredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("BatterySaverIsOn"));
    predicate.mutable_simple_predicate()->set_start(StringToId("BatterySaverModeStart"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("BatterySaverModeStop"));
    return predicate;
}

Predicate CreateDeviceUnpluggedPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("DeviceUnplugged"));
    predicate.mutable_simple_predicate()->set_start(StringToId("BatteryPluggedNone"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("BatteryPluggedUsb"));
    return predicate;
}

Predicate CreateScreenIsOnPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("ScreenIsOn"));
    predicate.mutable_simple_predicate()->set_start(StringToId("ScreenTurnedOn"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("ScreenTurnedOff"));
    return predicate;
}

Predicate CreateScreenIsOffPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("ScreenIsOff"));
    predicate.mutable_simple_predicate()->set_start(StringToId("ScreenTurnedOff"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("ScreenTurnedOn"));
    return predicate;
}

Predicate CreateHoldingWakelockPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("HoldingWakelock"));
    predicate.mutable_simple_predicate()->set_start(StringToId("AcquireWakelock"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("ReleaseWakelock"));
    return predicate;
}

Predicate CreateIsSyncingPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("IsSyncing"));
    predicate.mutable_simple_predicate()->set_start(StringToId("SyncStart"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("SyncEnd"));
    return predicate;
}

Predicate CreateIsInBackgroundPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("IsInBackground"));
    predicate.mutable_simple_predicate()->set_start(StringToId("Background"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("Foreground"));
    return predicate;
}

Predicate CreateTestAtomRepeatedStateFirstOffPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("TestFirstStateIsOff"));
    predicate.mutable_simple_predicate()->set_start(StringToId("TestFirstStateOff"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("TestFirstStateOn"));
    return predicate;
}

State CreateScreenState() {
    State state;
    state.set_id(StringToId("ScreenState"));
    state.set_atom_id(util::SCREEN_STATE_CHANGED);
    return state;
}

State CreateUidProcessState() {
    State state;
    state.set_id(StringToId("UidProcessState"));
    state.set_atom_id(util::UID_PROCESS_STATE_CHANGED);
    return state;
}

State CreateOverlayState() {
    State state;
    state.set_id(StringToId("OverlayState"));
    state.set_atom_id(util::OVERLAY_STATE_CHANGED);
    return state;
}

State CreateScreenStateWithOnOffMap(int64_t screenOnId, int64_t screenOffId) {
    State state;
    state.set_id(StringToId("ScreenStateOnOff"));
    state.set_atom_id(util::SCREEN_STATE_CHANGED);

    auto map = CreateScreenStateOnOffMap(screenOnId, screenOffId);
    *state.mutable_map() = map;

    return state;
}

State CreateScreenStateWithSimpleOnOffMap(int64_t screenOnId, int64_t screenOffId) {
    State state;
    state.set_id(StringToId("ScreenStateSimpleOnOff"));
    state.set_atom_id(util::SCREEN_STATE_CHANGED);

    auto map = CreateScreenStateSimpleOnOffMap(screenOnId, screenOffId);
    *state.mutable_map() = map;

    return state;
}

StateMap_StateGroup CreateScreenStateOnGroup(int64_t screenOnId) {
    StateMap_StateGroup group;
    group.set_group_id(screenOnId);
    group.add_value(android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    group.add_value(android::view::DisplayStateEnum::DISPLAY_STATE_VR);
    group.add_value(android::view::DisplayStateEnum::DISPLAY_STATE_ON_SUSPEND);
    return group;
}

StateMap_StateGroup CreateScreenStateOffGroup(int64_t screenOffId) {
    StateMap_StateGroup group;
    group.set_group_id(screenOffId);
    group.add_value(android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    group.add_value(android::view::DisplayStateEnum::DISPLAY_STATE_DOZE);
    group.add_value(android::view::DisplayStateEnum::DISPLAY_STATE_DOZE_SUSPEND);
    return group;
}

StateMap_StateGroup CreateScreenStateSimpleOnGroup(int64_t screenOnId) {
    StateMap_StateGroup group;
    group.set_group_id(screenOnId);
    group.add_value(android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    return group;
}

StateMap_StateGroup CreateScreenStateSimpleOffGroup(int64_t screenOffId) {
    StateMap_StateGroup group;
    group.set_group_id(screenOffId);
    group.add_value(android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    return group;
}

StateMap CreateScreenStateOnOffMap(int64_t screenOnId, int64_t screenOffId) {
    StateMap map;
    *map.add_group() = CreateScreenStateOnGroup(screenOnId);
    *map.add_group() = CreateScreenStateOffGroup(screenOffId);
    return map;
}

StateMap CreateScreenStateSimpleOnOffMap(int64_t screenOnId, int64_t screenOffId) {
    StateMap map;
    *map.add_group() = CreateScreenStateSimpleOnGroup(screenOnId);
    *map.add_group() = CreateScreenStateSimpleOffGroup(screenOffId);
    return map;
}

void addPredicateToPredicateCombination(const Predicate& predicate,
                                        Predicate* combinationPredicate) {
    combinationPredicate->mutable_combination()->add_predicate(predicate.id());
}

FieldMatcher CreateAttributionUidDimensions(const int atomId,
                                            const std::vector<Position>& positions) {
    FieldMatcher dimensions;
    dimensions.set_field(atomId);
    for (const auto position : positions) {
        auto child = dimensions.add_child();
        child->set_field(1);
        child->set_position(position);
        child->add_child()->set_field(1);
    }
    return dimensions;
}

FieldMatcher CreateAttributionUidAndTagDimensions(const int atomId,
                                                 const std::vector<Position>& positions) {
    FieldMatcher dimensions;
    dimensions.set_field(atomId);
    for (const auto position : positions) {
        auto child = dimensions.add_child();
        child->set_field(1);
        child->set_position(position);
        child->add_child()->set_field(1);
        child->add_child()->set_field(2);
    }
    return dimensions;
}

FieldMatcher CreateDimensions(const int atomId, const std::vector<int>& fields) {
    FieldMatcher dimensions;
    dimensions.set_field(atomId);
    for (const int field : fields) {
        dimensions.add_child()->set_field(field);
    }
    return dimensions;
}

FieldMatcher CreateRepeatedDimensions(const int atomId, const std::vector<int>& fields,
                                      const std::vector<Position>& positions) {
    FieldMatcher dimensions;
    if (fields.size() != positions.size()) {
        return dimensions;
    }

    dimensions.set_field(atomId);
    for (size_t i = 0; i < fields.size(); i++) {
        auto child = dimensions.add_child();
        child->set_field(fields[i]);
        child->set_position(positions[i]);
    }
    return dimensions;
}

FieldMatcher CreateAttributionUidAndOtherDimensions(const int atomId,
                                                    const std::vector<Position>& positions,
                                                    const std::vector<int>& fields) {
    FieldMatcher dimensions = CreateAttributionUidDimensions(atomId, positions);

    for (const int field : fields) {
        dimensions.add_child()->set_field(field);
    }
    return dimensions;
}

EventMetric createEventMetric(const string& name, const int64_t what,
                              const optional<int64_t>& condition) {
    EventMetric metric;
    metric.set_id(StringToId(name));
    metric.set_what(what);
    if (condition) {
        metric.set_condition(condition.value());
    }
    return metric;
}

CountMetric createCountMetric(const string& name, const int64_t what,
                              const optional<int64_t>& condition, const vector<int64_t>& states) {
    CountMetric metric;
    metric.set_id(StringToId(name));
    metric.set_what(what);
    metric.set_bucket(TEN_MINUTES);
    if (condition) {
        metric.set_condition(condition.value());
    }
    for (const int64_t state : states) {
        metric.add_slice_by_state(state);
    }
    return metric;
}

DurationMetric createDurationMetric(const string& name, const int64_t what,
                                    const optional<int64_t>& condition,
                                    const vector<int64_t>& states) {
    DurationMetric metric;
    metric.set_id(StringToId(name));
    metric.set_what(what);
    metric.set_bucket(TEN_MINUTES);
    if (condition) {
        metric.set_condition(condition.value());
    }
    for (const int64_t state : states) {
        metric.add_slice_by_state(state);
    }
    return metric;
}

GaugeMetric createGaugeMetric(const string& name, const int64_t what,
                              const GaugeMetric::SamplingType samplingType,
                              const optional<int64_t>& condition,
                              const optional<int64_t>& triggerEvent) {
    GaugeMetric metric;
    metric.set_id(StringToId(name));
    metric.set_what(what);
    metric.set_bucket(TEN_MINUTES);
    metric.set_sampling_type(samplingType);
    if (condition) {
        metric.set_condition(condition.value());
    }
    if (triggerEvent) {
        metric.set_trigger_event(triggerEvent.value());
    }
    metric.mutable_gauge_fields_filter()->set_include_all(true);
    return metric;
}

ValueMetric createValueMetric(const string& name, const AtomMatcher& what, const int valueField,
                              const optional<int64_t>& condition, const vector<int64_t>& states) {
    ValueMetric metric;
    metric.set_id(StringToId(name));
    metric.set_what(what.id());
    metric.set_bucket(TEN_MINUTES);
    metric.mutable_value_field()->set_field(what.simple_atom_matcher().atom_id());
    metric.mutable_value_field()->add_child()->set_field(valueField);
    if (condition) {
        metric.set_condition(condition.value());
    }
    for (const int64_t state : states) {
        metric.add_slice_by_state(state);
    }
    return metric;
}

KllMetric createKllMetric(const string& name, const AtomMatcher& what, const int kllField,
                          const optional<int64_t>& condition) {
    KllMetric metric;
    metric.set_id(StringToId(name));
    metric.set_what(what.id());
    metric.set_bucket(TEN_MINUTES);
    metric.mutable_kll_field()->set_field(what.simple_atom_matcher().atom_id());
    metric.mutable_kll_field()->add_child()->set_field(kllField);
    if (condition) {
        metric.set_condition(condition.value());
    }
    return metric;
}

Alert createAlert(const string& name, const int64_t metricId, const int buckets,
                  const int64_t triggerSum) {
    Alert alert;
    alert.set_id(StringToId(name));
    alert.set_metric_id(metricId);
    alert.set_num_buckets(buckets);
    alert.set_trigger_if_sum_gt(triggerSum);
    return alert;
}

Alarm createAlarm(const string& name, const int64_t offsetMillis, const int64_t periodMillis) {
    Alarm alarm;
    alarm.set_id(StringToId(name));
    alarm.set_offset_millis(offsetMillis);
    alarm.set_period_millis(periodMillis);
    return alarm;
}

Subscription createSubscription(const string& name, const Subscription_RuleType type,
                                const int64_t ruleId) {
    Subscription subscription;
    subscription.set_id(StringToId(name));
    subscription.set_rule_type(type);
    subscription.set_rule_id(ruleId);
    subscription.mutable_broadcast_subscriber_details();
    return subscription;
}

// START: get primary key functions
void getUidProcessKey(int uid, HashableDimensionKey* key) {
    int pos1[] = {1, 0, 0};
    Field field1(27 /* atom id */, pos1, 0 /* depth */);
    Value value1((int32_t)uid);

    key->addValue(FieldValue(field1, value1));
}

void getOverlayKey(int uid, string packageName, HashableDimensionKey* key) {
    int pos1[] = {1, 0, 0};
    int pos2[] = {2, 0, 0};

    Field field1(59 /* atom id */, pos1, 0 /* depth */);
    Field field2(59 /* atom id */, pos2, 0 /* depth */);

    Value value1((int32_t)uid);
    Value value2(packageName);

    key->addValue(FieldValue(field1, value1));
    key->addValue(FieldValue(field2, value2));
}

void getPartialWakelockKey(int uid, const std::string& tag, HashableDimensionKey* key) {
    int pos1[] = {1, 1, 1};
    int pos3[] = {2, 0, 0};
    int pos4[] = {3, 0, 0};

    Field field1(10 /* atom id */, pos1, 2 /* depth */);

    Field field3(10 /* atom id */, pos3, 0 /* depth */);
    Field field4(10 /* atom id */, pos4, 0 /* depth */);

    Value value1((int32_t)uid);
    Value value3((int32_t)1 /*partial*/);
    Value value4(tag);

    key->addValue(FieldValue(field1, value1));
    key->addValue(FieldValue(field3, value3));
    key->addValue(FieldValue(field4, value4));
}

void getPartialWakelockKey(int uid, HashableDimensionKey* key) {
    int pos1[] = {1, 1, 1};
    int pos3[] = {2, 0, 0};

    Field field1(10 /* atom id */, pos1, 2 /* depth */);
    Field field3(10 /* atom id */, pos3, 0 /* depth */);

    Value value1((int32_t)uid);
    Value value3((int32_t)1 /*partial*/);

    key->addValue(FieldValue(field1, value1));
    key->addValue(FieldValue(field3, value3));
}
// END: get primary key functions

void writeAttribution(AStatsEvent* statsEvent, const vector<int>& attributionUids,
                      const vector<string>& attributionTags) {
    vector<const char*> cTags(attributionTags.size());
    for (int i = 0; i < cTags.size(); i++) {
        cTags[i] = attributionTags[i].c_str();
    }

    AStatsEvent_writeAttributionChain(statsEvent,
                                      reinterpret_cast<const uint32_t*>(attributionUids.data()),
                                      cTags.data(), attributionUids.size());
}

void parseStatsEventToLogEvent(AStatsEvent* statsEvent, LogEvent* logEvent) {
    AStatsEvent_build(statsEvent);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    logEvent->parseBuffer(buf, size);

    AStatsEvent_release(statsEvent);
}

void CreateTwoValueLogEvent(LogEvent* logEvent, int atomId, int64_t eventTimeNs, int32_t value1,
                            int32_t value2) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);

    AStatsEvent_writeInt32(statsEvent, value1);
    AStatsEvent_writeInt32(statsEvent, value2);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

shared_ptr<LogEvent> CreateTwoValueLogEvent(int atomId, int64_t eventTimeNs, int32_t value1,
                                            int32_t value2) {
    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    CreateTwoValueLogEvent(logEvent.get(), atomId, eventTimeNs, value1, value2);
    return logEvent;
}

void CreateThreeValueLogEvent(LogEvent* logEvent, int atomId, int64_t eventTimeNs, int32_t value1,
                              int32_t value2, int32_t value3) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);

    AStatsEvent_writeInt32(statsEvent, value1);
    AStatsEvent_writeInt32(statsEvent, value2);
    AStatsEvent_writeInt32(statsEvent, value3);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

shared_ptr<LogEvent> CreateThreeValueLogEvent(int atomId, int64_t eventTimeNs, int32_t value1,
                                              int32_t value2, int32_t value3) {
    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    CreateThreeValueLogEvent(logEvent.get(), atomId, eventTimeNs, value1, value2, value3);
    return logEvent;
}

void CreateRepeatedValueLogEvent(LogEvent* logEvent, int atomId, int64_t eventTimeNs,
                                 int32_t value) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);

    AStatsEvent_writeInt32(statsEvent, value);
    AStatsEvent_writeInt32(statsEvent, value);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

shared_ptr<LogEvent> CreateRepeatedValueLogEvent(int atomId, int64_t eventTimeNs, int32_t value) {
    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    CreateRepeatedValueLogEvent(logEvent.get(), atomId, eventTimeNs, value);
    return logEvent;
}

void CreateNoValuesLogEvent(LogEvent* logEvent, int atomId, int64_t eventTimeNs) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

shared_ptr<LogEvent> CreateNoValuesLogEvent(int atomId, int64_t eventTimeNs) {
    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    CreateNoValuesLogEvent(logEvent.get(), atomId, eventTimeNs);
    return logEvent;
}

AStatsEvent* makeUidStatsEvent(int atomId, int64_t eventTimeNs, int uid, int data1, int data2) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);

    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    AStatsEvent_writeInt32(statsEvent, data1);
    AStatsEvent_writeInt32(statsEvent, data2);

    return statsEvent;
}

AStatsEvent* makeUidStatsEvent(int atomId, int64_t eventTimeNs, int uid, int data1,
                               const vector<int>& data2) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);
    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    AStatsEvent_writeInt32(statsEvent, data1);
    AStatsEvent_writeInt32Array(statsEvent, data2.data(), data2.size());

    return statsEvent;
}

shared_ptr<LogEvent> makeUidLogEvent(int atomId, int64_t eventTimeNs, int uid, int data1,
                                     int data2) {
    AStatsEvent* statsEvent = makeUidStatsEvent(atomId, eventTimeNs, uid, data1, data2);

    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

shared_ptr<LogEvent> makeUidLogEvent(int atomId, int64_t eventTimeNs, int uid, int data1,
                                     const vector<int>& data2) {
    AStatsEvent* statsEvent = makeUidStatsEvent(atomId, eventTimeNs, uid, data1, data2);

    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

shared_ptr<LogEvent> makeExtraUidsLogEvent(int atomId, int64_t eventTimeNs, int uid1, int data1,
                                           int data2, const vector<int>& extraUids) {
    AStatsEvent* statsEvent = makeUidStatsEvent(atomId, eventTimeNs, uid1, data1, data2);
    for (const int extraUid : extraUids) {
        AStatsEvent_writeInt32(statsEvent, extraUid);
        AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    }

    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

shared_ptr<LogEvent> makeRepeatedUidLogEvent(int atomId, int64_t eventTimeNs,
                                             const vector<int>& uids) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);
    AStatsEvent_writeInt32Array(statsEvent, uids.data(), uids.size());
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);

    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());

    return logEvent;
}

shared_ptr<LogEvent> makeRepeatedUidLogEvent(int atomId, int64_t eventTimeNs,
                                             const vector<int>& uids, int data1, int data2) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);
    AStatsEvent_writeInt32Array(statsEvent, uids.data(), uids.size());
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    AStatsEvent_writeInt32(statsEvent, data1);
    AStatsEvent_writeInt32(statsEvent, data2);

    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());

    return logEvent;
}

shared_ptr<LogEvent> makeRepeatedUidLogEvent(int atomId, int64_t eventTimeNs,
                                             const vector<int>& uids, int data1,
                                             const vector<int>& data2) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);
    AStatsEvent_writeInt32Array(statsEvent, uids.data(), uids.size());
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    AStatsEvent_writeInt32(statsEvent, data1);
    AStatsEvent_writeInt32Array(statsEvent, data2.data(), data2.size());

    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());

    return logEvent;
}

shared_ptr<LogEvent> makeAttributionLogEvent(int atomId, int64_t eventTimeNs,
                                             const vector<int>& uids, const vector<string>& tags,
                                             int data1, int data2) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, eventTimeNs);

    writeAttribution(statsEvent, uids, tags);
    AStatsEvent_writeInt32(statsEvent, data1);
    AStatsEvent_writeInt32(statsEvent, data2);

    shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

sp<MockUidMap> makeMockUidMapForHosts(const map<int, vector<int>>& hostUidToIsolatedUidsMap) {
    sp<MockUidMap> uidMap = new NaggyMock<MockUidMap>();
    EXPECT_CALL(*uidMap, getHostUidOrSelf(_)).WillRepeatedly(ReturnArg<0>());
    for (const auto& [hostUid, isolatedUids] : hostUidToIsolatedUidsMap) {
        for (const int isolatedUid : isolatedUids) {
            EXPECT_CALL(*uidMap, getHostUidOrSelf(isolatedUid)).WillRepeatedly(Return(hostUid));
        }
    }

    return uidMap;
}

sp<MockUidMap> makeMockUidMapForPackage(const string& pkg, const set<int32_t>& uids) {
    sp<MockUidMap> uidMap = new StrictMock<MockUidMap>();
    EXPECT_CALL(*uidMap, getAppUid(_)).Times(AnyNumber());
    EXPECT_CALL(*uidMap, getAppUid(pkg)).WillRepeatedly(Return(uids));

    return uidMap;
}

std::unique_ptr<LogEvent> CreateScreenStateChangedEvent(uint64_t timestampNs,
                                                        const android::view::DisplayStateEnum state,
                                                        int loggerUid) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::SCREEN_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    AStatsEvent_writeInt32(statsEvent, state);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, false);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(loggerUid, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateBatterySaverOnEvent(uint64_t timestampNs) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::BATTERY_SAVER_MODE_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    AStatsEvent_writeInt32(statsEvent, BatterySaverModeStateChanged::ON);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, false);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateBatterySaverOffEvent(uint64_t timestampNs) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::BATTERY_SAVER_MODE_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    AStatsEvent_writeInt32(statsEvent, BatterySaverModeStateChanged::OFF);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, false);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateBatteryStateChangedEvent(const uint64_t timestampNs,
                                                         const BatteryPluggedStateEnum state,
                                                         int32_t uid) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::PLUGGED_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    AStatsEvent_writeInt32(statsEvent, state);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, false);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/uid, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateMalformedBatteryStateChangedEvent(const uint64_t timestampNs) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::PLUGGED_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    AStatsEvent_writeString(statsEvent, "bad_state");
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, false);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateScreenBrightnessChangedEvent(uint64_t timestampNs, int level) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::SCREEN_BRIGHTNESS_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    AStatsEvent_writeInt32(statsEvent, level);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateScheduledJobStateChangedEvent(
        const vector<int>& attributionUids, const vector<string>& attributionTags,
        const string& jobName, const ScheduledJobStateChanged::State state, uint64_t timestampNs) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::SCHEDULED_JOB_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    writeAttribution(statsEvent, attributionUids, attributionTags);
    AStatsEvent_writeString(statsEvent, jobName.c_str());
    AStatsEvent_writeInt32(statsEvent, state);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateStartScheduledJobEvent(uint64_t timestampNs,
                                                       const vector<int>& attributionUids,
                                                       const vector<string>& attributionTags,
                                                       const string& jobName) {
    return CreateScheduledJobStateChangedEvent(attributionUids, attributionTags, jobName,
                                               ScheduledJobStateChanged::STARTED, timestampNs);
}

// Create log event when scheduled job finishes.
std::unique_ptr<LogEvent> CreateFinishScheduledJobEvent(uint64_t timestampNs,
                                                        const vector<int>& attributionUids,
                                                        const vector<string>& attributionTags,
                                                        const string& jobName) {
    return CreateScheduledJobStateChangedEvent(attributionUids, attributionTags, jobName,
                                               ScheduledJobStateChanged::FINISHED, timestampNs);
}

// Create log event when scheduled job is scheduled.
std::unique_ptr<LogEvent> CreateScheduleScheduledJobEvent(uint64_t timestampNs,
                                                          const vector<int>& attributionUids,
                                                          const vector<string>& attributionTags,
                                                          const string& jobName) {
    return CreateScheduledJobStateChangedEvent(attributionUids, attributionTags, jobName,
                                               ScheduledJobStateChanged::SCHEDULED, timestampNs);
}

std::unique_ptr<LogEvent> CreateTestAtomReportedEventVariableRepeatedFields(
        uint64_t timestampNs, const vector<int>& repeatedIntField,
        const vector<int64_t>& repeatedLongField, const vector<float>& repeatedFloatField,
        const vector<string>& repeatedStringField, const bool* repeatedBoolField,
        const size_t repeatedBoolFieldLength, const vector<int>& repeatedEnumField) {
    return CreateTestAtomReportedEvent(timestampNs, {1001, 1002}, {"app1", "app2"}, 5, 1000l, 21.9f,
                                       "string", 1, TestAtomReported::ON, {8, 1, 8, 2, 8, 3},
                                       repeatedIntField, repeatedLongField, repeatedFloatField,
                                       repeatedStringField, repeatedBoolField,
                                       repeatedBoolFieldLength, repeatedEnumField);
}

std::unique_ptr<LogEvent> CreateTestAtomReportedEventWithPrimitives(
        uint64_t timestampNs, int intField, long longField, float floatField,
        const string& stringField, bool boolField, TestAtomReported::State enumField) {
    return CreateTestAtomReportedEvent(
            timestampNs, /* attributionUids */ {1001},
            /* attributionTags */ {"app1"}, intField, longField, floatField, stringField, boolField,
            enumField, /* bytesField */ {},
            /* repeatedIntField */ {}, /* repeatedLongField */ {}, /* repeatedFloatField */ {},
            /* repeatedStringField */ {}, /* repeatedBoolField */ {},
            /* repeatedBoolFieldLength */ 0, /* repeatedEnumField */ {});
}

std::unique_ptr<LogEvent> CreateTestAtomReportedEvent(
        uint64_t timestampNs, const vector<int>& attributionUids,
        const vector<string>& attributionTags, const int intField, const long longField,
        const float floatField, const string& stringField, const bool boolField,
        const TestAtomReported::State enumField, const vector<uint8_t>& bytesField,
        const vector<int>& repeatedIntField, const vector<int64_t>& repeatedLongField,
        const vector<float>& repeatedFloatField, const vector<string>& repeatedStringField,
        const bool* repeatedBoolField, const size_t repeatedBoolFieldLength,
        const vector<int>& repeatedEnumField) {
    vector<const char*> cRepeatedStringField(repeatedStringField.size());
    for (int i = 0; i < cRepeatedStringField.size(); i++) {
        cRepeatedStringField[i] = repeatedStringField[i].c_str();
    }

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::TEST_ATOM_REPORTED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    writeAttribution(statsEvent, attributionUids, attributionTags);
    AStatsEvent_writeInt32(statsEvent, intField);
    AStatsEvent_writeInt64(statsEvent, longField);
    AStatsEvent_writeFloat(statsEvent, floatField);
    AStatsEvent_writeString(statsEvent, stringField.c_str());
    AStatsEvent_writeBool(statsEvent, boolField);
    AStatsEvent_writeInt32(statsEvent, enumField);
    AStatsEvent_writeByteArray(statsEvent, bytesField.data(), bytesField.size());
    AStatsEvent_writeInt32Array(statsEvent, repeatedIntField.data(), repeatedIntField.size());
    AStatsEvent_writeInt64Array(statsEvent, repeatedLongField.data(), repeatedLongField.size());
    AStatsEvent_writeFloatArray(statsEvent, repeatedFloatField.data(), repeatedFloatField.size());
    AStatsEvent_writeStringArray(statsEvent, cRepeatedStringField.data(),
                                 repeatedStringField.size());
    AStatsEvent_writeBoolArray(statsEvent, repeatedBoolField, repeatedBoolFieldLength);
    AStatsEvent_writeInt32Array(statsEvent, repeatedEnumField.data(), repeatedEnumField.size());

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateWakelockStateChangedEvent(uint64_t timestampNs,
                                                          const vector<int>& attributionUids,
                                                          const vector<string>& attributionTags,
                                                          const string& wakelockName,
                                                          const WakelockStateChanged::State state) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::WAKELOCK_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    writeAttribution(statsEvent, attributionUids, attributionTags);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID,
                                  true);
    AStatsEvent_writeInt32(statsEvent, android::os::WakeLockLevelEnum::PARTIAL_WAKE_LOCK);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD, true);
    AStatsEvent_writeString(statsEvent, wakelockName.c_str());
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD, true);
    AStatsEvent_writeInt32(statsEvent, state);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, true);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateAcquireWakelockEvent(uint64_t timestampNs,
                                                     const vector<int>& attributionUids,
                                                     const vector<string>& attributionTags,
                                                     const string& wakelockName) {
    return CreateWakelockStateChangedEvent(timestampNs, attributionUids, attributionTags,
                                           wakelockName, WakelockStateChanged::ACQUIRE);
}

std::unique_ptr<LogEvent> CreateReleaseWakelockEvent(uint64_t timestampNs,
                                                     const vector<int>& attributionUids,
                                                     const vector<string>& attributionTags,
                                                     const string& wakelockName) {
    return CreateWakelockStateChangedEvent(timestampNs, attributionUids, attributionTags,
                                           wakelockName, WakelockStateChanged::RELEASE);
}

std::unique_ptr<LogEvent> CreateActivityForegroundStateChangedEvent(
        uint64_t timestampNs, const int uid, const string& pkgName, const string& className,
        const ActivityForegroundStateChanged::State state) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::ACTIVITY_FOREGROUND_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_writeString(statsEvent, pkgName.c_str());
    AStatsEvent_writeString(statsEvent, className.c_str());
    AStatsEvent_writeInt32(statsEvent, state);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateMoveToBackgroundEvent(uint64_t timestampNs, const int uid) {
    return CreateActivityForegroundStateChangedEvent(timestampNs, uid, "pkg_name", "class_name",
                                                     ActivityForegroundStateChanged::BACKGROUND);
}

std::unique_ptr<LogEvent> CreateMoveToForegroundEvent(uint64_t timestampNs, const int uid) {
    return CreateActivityForegroundStateChangedEvent(timestampNs, uid, "pkg_name", "class_name",
                                                     ActivityForegroundStateChanged::FOREGROUND);
}

std::unique_ptr<LogEvent> CreateSyncStateChangedEvent(uint64_t timestampNs,
                                                      const vector<int>& attributionUids,
                                                      const vector<string>& attributionTags,
                                                      const string& name,
                                                      const SyncStateChanged::State state) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::SYNC_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    writeAttribution(statsEvent, attributionUids, attributionTags);
    AStatsEvent_writeString(statsEvent, name.c_str());
    AStatsEvent_writeInt32(statsEvent, state);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateSyncStartEvent(uint64_t timestampNs,
                                               const vector<int>& attributionUids,
                                               const vector<string>& attributionTags,
                                               const string& name) {
    return CreateSyncStateChangedEvent(timestampNs, attributionUids, attributionTags, name,
                                       SyncStateChanged::ON);
}

std::unique_ptr<LogEvent> CreateSyncEndEvent(uint64_t timestampNs,
                                             const vector<int>& attributionUids,
                                             const vector<string>& attributionTags,
                                             const string& name) {
    return CreateSyncStateChangedEvent(timestampNs, attributionUids, attributionTags, name,
                                       SyncStateChanged::OFF);
}

std::unique_ptr<LogEvent> CreateProcessLifeCycleStateChangedEvent(
        uint64_t timestampNs, const int uid, const ProcessLifeCycleStateChanged::State state) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::PROCESS_LIFE_CYCLE_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_writeString(statsEvent, "");
    AStatsEvent_writeInt32(statsEvent, state);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateAppCrashEvent(uint64_t timestampNs, const int uid) {
    return CreateProcessLifeCycleStateChangedEvent(timestampNs, uid,
                                                   ProcessLifeCycleStateChanged::CRASHED);
}

std::unique_ptr<LogEvent> CreateAppCrashOccurredEvent(uint64_t timestampNs, const int uid) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::APP_CRASH_OCCURRED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_writeString(statsEvent, "eventType");
    AStatsEvent_writeString(statsEvent, "processName");

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateIsolatedUidChangedEvent(uint64_t timestampNs, int hostUid,
                                                        int isolatedUid, bool is_create) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::ISOLATED_UID_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    AStatsEvent_writeInt32(statsEvent, hostUid);
    AStatsEvent_writeInt32(statsEvent, isolatedUid);
    AStatsEvent_writeInt32(statsEvent, is_create);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateUidProcessStateChangedEvent(
        uint64_t timestampNs, int uid, const android::app::ProcessStateEnum state) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::UID_PROCESS_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD, true);
    AStatsEvent_writeInt32(statsEvent, state);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, false);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateBleScanStateChangedEvent(uint64_t timestampNs,
                                                         const vector<int>& attributionUids,
                                                         const vector<string>& attributionTags,
                                                         const BleScanStateChanged::State state,
                                                         const bool filtered, const bool firstMatch,
                                                         const bool opportunistic) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::BLE_SCAN_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    writeAttribution(statsEvent, attributionUids, attributionTags);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID,
                                  true);
    AStatsEvent_writeInt32(statsEvent, state);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, true);
    if (state == util::BLE_SCAN_STATE_CHANGED__STATE__RESET) {
        AStatsEvent_addInt32Annotation(statsEvent, ASTATSLOG_ANNOTATION_ID_TRIGGER_STATE_RESET,
                                       util::BLE_SCAN_STATE_CHANGED__STATE__OFF);
    }
    AStatsEvent_writeBool(statsEvent, filtered);  // filtered
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD, true);
    AStatsEvent_writeBool(statsEvent, firstMatch);  // first match
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD, true);
    AStatsEvent_writeBool(statsEvent, opportunistic);  // opportunistic
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD, true);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateOverlayStateChangedEvent(int64_t timestampNs, const int32_t uid,
                                                         const string& packageName,
                                                         const bool usingAlertWindow,
                                                         const OverlayStateChanged::State state) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::OVERLAY_STATE_CHANGED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD, true);
    AStatsEvent_writeString(statsEvent, packageName.c_str());
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD, true);
    AStatsEvent_writeBool(statsEvent, usingAlertWindow);
    AStatsEvent_writeInt32(statsEvent, state);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE, true);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_STATE_NESTED, false);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateAppStartOccurredEvent(
        uint64_t timestampNs, const int uid, const string& pkgName,
        AppStartOccurred::TransitionType type, const string& activityName,
        const string& callingPkgName, const bool isInstantApp, int64_t activityStartMs) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::APP_START_OCCURRED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_writeString(statsEvent, pkgName.c_str());
    AStatsEvent_writeInt32(statsEvent, type);
    AStatsEvent_writeString(statsEvent, activityName.c_str());
    AStatsEvent_writeString(statsEvent, callingPkgName.c_str());
    AStatsEvent_writeInt32(statsEvent, isInstantApp);
    AStatsEvent_writeInt32(statsEvent, activityStartMs);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateBleScanResultReceivedEvent(uint64_t timestampNs,
                                                           const vector<int>& attributionUids,
                                                           const vector<string>& attributionTags,
                                                           const int numResults) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::BLE_SCAN_RESULT_RECEIVED);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);

    writeAttribution(statsEvent, attributionUids, attributionTags);
    AStatsEvent_writeInt32(statsEvent, numResults);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateRestrictedLogEvent(int atomTag, int64_t timestampNs) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomTag);
    AStatsEvent_addInt32Annotation(statsEvent, ASTATSLOG_ANNOTATION_ID_RESTRICTION_CATEGORY,
                                   ASTATSLOG_RESTRICTION_CATEGORY_DIAGNOSTIC);
    AStatsEvent_writeInt32(statsEvent, 10);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreateNonRestrictedLogEvent(int atomTag, int64_t timestampNs) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomTag);
    AStatsEvent_writeInt32(statsEvent, 10);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

std::unique_ptr<LogEvent> CreatePhoneSignalStrengthChangedEvent(
        int64_t timestampNs, ::telephony::SignalStrengthEnum state) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, util::PHONE_SIGNAL_STRENGTH_CHANGED);
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_TRUNCATE_TIMESTAMP, true);
    AStatsEvent_overwriteTimestamp(statsEvent, timestampNs);
    AStatsEvent_writeInt32(statsEvent, state);

    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

sp<StatsLogProcessor> CreateStatsLogProcessor(const int64_t timeBaseNs, const int64_t currentTimeNs,
                                              const StatsdConfig& config, const ConfigKey& key,
                                              const shared_ptr<IPullAtomCallback>& puller,
                                              const int32_t atomTag, const sp<UidMap> uidMap,
                                              const shared_ptr<LogEventFilter>& logEventFilter) {
    sp<StatsPullerManager> pullerManager = new StatsPullerManager();
    if (puller != nullptr) {
        pullerManager->RegisterPullAtomCallback(/*uid=*/0, atomTag, NS_PER_SEC, NS_PER_SEC * 10, {},
                                                puller);
    }
    sp<AlarmMonitor> anomalyAlarmMonitor =
        new AlarmMonitor(1,
                         [](const shared_ptr<IStatsCompanionService>&, int64_t){},
                         [](const shared_ptr<IStatsCompanionService>&){});
    sp<AlarmMonitor> periodicAlarmMonitor =
        new AlarmMonitor(1,
                         [](const shared_ptr<IStatsCompanionService>&, int64_t){},
                         [](const shared_ptr<IStatsCompanionService>&){});
    sp<StatsLogProcessor> processor = new StatsLogProcessor(
            uidMap, pullerManager, anomalyAlarmMonitor, periodicAlarmMonitor, timeBaseNs,
            [](const ConfigKey&) { return true; },
            [](const int&, const vector<int64_t>&) { return true; },
            [](const ConfigKey&, const string&, const vector<int64_t>&) {}, logEventFilter);

    processor->OnConfigUpdated(currentTimeNs, key, config);
    return processor;
}

LogEventFilter::AtomIdSet CreateAtomIdSetDefault() {
    LogEventFilter::AtomIdSet resultList(std::move(StatsLogProcessor::getDefaultAtomIdSet()));
    StateManager::getInstance().addAllAtomIds(resultList);
    return resultList;
}

LogEventFilter::AtomIdSet CreateAtomIdSetFromConfig(const StatsdConfig& config) {
    LogEventFilter::AtomIdSet resultList(std::move(StatsLogProcessor::getDefaultAtomIdSet()));

    // Parse the config for atom ids. A combination atom matcher is a combination of (in the end)
    // simple atom matchers. So by adding all the atoms from the simple atom matchers
    // function adds all of the atoms.
    for (int i = 0; i < config.atom_matcher_size(); i++) {
        const AtomMatcher& matcher = config.atom_matcher(i);
        if (matcher.has_simple_atom_matcher()) {
            EXPECT_TRUE(matcher.simple_atom_matcher().has_atom_id());
            resultList.insert(matcher.simple_atom_matcher().atom_id());
        }
    }

    for (int i = 0; i < config.state_size(); i++) {
        const State& state = config.state(i);
        EXPECT_TRUE(state.has_atom_id());
        resultList.insert(state.atom_id());
    }

    return resultList;
}

void sortLogEventsByTimestamp(std::vector<std::unique_ptr<LogEvent>> *events) {
  std::sort(events->begin(), events->end(),
            [](const std::unique_ptr<LogEvent>& a, const std::unique_ptr<LogEvent>& b) {
              return a->GetElapsedTimestampNs() < b->GetElapsedTimestampNs();
            });
}

int64_t StringToId(const string& str) {
    return static_cast<int64_t>(std::hash<std::string>()(str));
}

sp<EventMatcherWizard> createEventMatcherWizard(
        int tagId, int matcherIndex, const vector<FieldValueMatcher>& fieldValueMatchers) {
    sp<UidMap> uidMap = new UidMap();
    SimpleAtomMatcher atomMatcher;
    atomMatcher.set_atom_id(tagId);
    for (const FieldValueMatcher& fvm : fieldValueMatchers) {
        *atomMatcher.add_field_value_matcher() = fvm;
    }
    uint64_t matcherHash = 0x12345678;
    int64_t matcherId = 678;
    return new EventMatcherWizard(
            {new SimpleAtomMatchingTracker(matcherId, matcherHash, atomMatcher, uidMap)});
}

StatsDimensionsValueParcel CreateAttributionUidDimensionsValueParcel(const int atomId,
                                                                     const int uid) {
    StatsDimensionsValueParcel root;
    root.field = atomId;
    root.valueType = STATS_DIMENSIONS_VALUE_TUPLE_TYPE;
    StatsDimensionsValueParcel attrNode;
    attrNode.field = 1;
    attrNode.valueType = STATS_DIMENSIONS_VALUE_TUPLE_TYPE;
    StatsDimensionsValueParcel posInAttrChain;
    posInAttrChain.field = 1;
    posInAttrChain.valueType = STATS_DIMENSIONS_VALUE_TUPLE_TYPE;
    StatsDimensionsValueParcel uidNode;
    uidNode.field = 1;
    uidNode.valueType = STATS_DIMENSIONS_VALUE_INT_TYPE;
    uidNode.intValue = uid;
    posInAttrChain.tupleValue.push_back(uidNode);
    attrNode.tupleValue.push_back(posInAttrChain);
    root.tupleValue.push_back(attrNode);
    return root;
}

void ValidateUidDimension(const DimensionsValue& value, int atomId, int uid) {
    EXPECT_EQ(value.field(), atomId);
    ASSERT_EQ(value.value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0).value_int(), uid);
}

void ValidateWakelockAttributionUidAndTagDimension(const DimensionsValue& value, const int atomId,
                                                   const int uid, const string& tag) {
    EXPECT_EQ(value.field(), atomId);
    ASSERT_EQ(value.value_tuple().dimensions_value_size(), 2);
    // Attribution field.
    EXPECT_EQ(value.value_tuple().dimensions_value(0).field(), 1);
    // Uid field.
    ASSERT_EQ(value.value_tuple().dimensions_value(0).value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0).value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0).value_tuple().dimensions_value(0).value_int(),
              uid);
    // Tag field.
    EXPECT_EQ(value.value_tuple().dimensions_value(1).field(), 3);
    EXPECT_EQ(value.value_tuple().dimensions_value(1).value_str(), tag);
}

void ValidateAttributionUidDimension(const DimensionsValue& value, int atomId, int uid) {
    EXPECT_EQ(value.field(), atomId);
    ASSERT_EQ(value.value_tuple().dimensions_value_size(), 1);
    // Attribution field.
    EXPECT_EQ(value.value_tuple().dimensions_value(0).field(), 1);
    // Uid only.
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).value_int(), uid);
}

void ValidateUidDimension(const DimensionsValue& value, int node_idx, int atomId, int uid) {
    EXPECT_EQ(value.field(), atomId);
    ASSERT_GT(value.value_tuple().dimensions_value_size(), node_idx);
    // Attribution field.
    EXPECT_EQ(value.value_tuple().dimensions_value(node_idx).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(node_idx)
        .value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(node_idx)
        .value_tuple().dimensions_value(0).value_int(), uid);
}

void ValidateAttributionUidAndTagDimension(
    const DimensionsValue& value, int node_idx, int atomId, int uid, const std::string& tag) {
    EXPECT_EQ(value.field(), atomId);
    ASSERT_GT(value.value_tuple().dimensions_value_size(), node_idx);
    // Attribution field.
    EXPECT_EQ(1, value.value_tuple().dimensions_value(node_idx).field());
    // Uid only.
    EXPECT_EQ(2, value.value_tuple().dimensions_value(node_idx)
        .value_tuple().dimensions_value_size());
    EXPECT_EQ(1, value.value_tuple().dimensions_value(node_idx)
        .value_tuple().dimensions_value(0).field());
    EXPECT_EQ(uid, value.value_tuple().dimensions_value(node_idx)
        .value_tuple().dimensions_value(0).value_int());
    EXPECT_EQ(2, value.value_tuple().dimensions_value(node_idx)
        .value_tuple().dimensions_value(1).field());
    EXPECT_EQ(tag, value.value_tuple().dimensions_value(node_idx)
        .value_tuple().dimensions_value(1).value_str());
}

void ValidateAttributionUidAndTagDimension(
    const DimensionsValue& value, int atomId, int uid, const std::string& tag) {
    EXPECT_EQ(value.field(), atomId);
    ASSERT_EQ(1, value.value_tuple().dimensions_value_size());
    // Attribution field.
    EXPECT_EQ(1, value.value_tuple().dimensions_value(0).field());
    // Uid only.
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).value_int(), uid);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(1).field(), 2);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(1).value_str(), tag);
}

void ValidateStateValue(const google::protobuf::RepeatedPtrField<StateValue>& stateValues,
                        int atomId, int64_t value) {
    ASSERT_EQ(stateValues.size(), 1);
    ASSERT_EQ(stateValues[0].atom_id(), atomId);
    switch (stateValues[0].contents_case()) {
        case StateValue::ContentsCase::kValue:
            EXPECT_EQ(stateValues[0].value(), (int32_t)value);
            break;
        case StateValue::ContentsCase::kGroupId:
            EXPECT_EQ(stateValues[0].group_id(), value);
            break;
        default:
            FAIL() << "State value should have either a value or a group id";
    }
}

void ValidateCountBucket(const CountBucketInfo& countBucket, int64_t startTimeNs, int64_t endTimeNs,
                         int64_t count, int64_t conditionTrueNs) {
    EXPECT_EQ(countBucket.start_bucket_elapsed_nanos(), startTimeNs);
    EXPECT_EQ(countBucket.end_bucket_elapsed_nanos(), endTimeNs);
    EXPECT_EQ(countBucket.count(), count);
    EXPECT_EQ(countBucket.condition_true_nanos(), conditionTrueNs);
}

void ValidateDurationBucket(const DurationBucketInfo& bucket, int64_t startTimeNs,
                            int64_t endTimeNs, int64_t durationNs, int64_t conditionTrueNs) {
    EXPECT_EQ(bucket.start_bucket_elapsed_nanos(), startTimeNs);
    EXPECT_EQ(bucket.end_bucket_elapsed_nanos(), endTimeNs);
    EXPECT_EQ(bucket.duration_nanos(), durationNs);
    EXPECT_EQ(bucket.condition_true_nanos(), conditionTrueNs);
}

void ValidateGaugeBucketTimes(const GaugeBucketInfo& gaugeBucket, int64_t startTimeNs,
                              int64_t endTimeNs, vector<int64_t> eventTimesNs) {
    EXPECT_EQ(gaugeBucket.start_bucket_elapsed_nanos(), startTimeNs);
    EXPECT_EQ(gaugeBucket.end_bucket_elapsed_nanos(), endTimeNs);
    EXPECT_EQ(gaugeBucket.elapsed_timestamp_nanos_size(), eventTimesNs.size());
    for (int i = 0; i < eventTimesNs.size(); i++) {
        EXPECT_EQ(gaugeBucket.elapsed_timestamp_nanos(i), eventTimesNs[i]);
    }
}

void ValidateValueBucket(const ValueBucketInfo& bucket, int64_t startTimeNs, int64_t endTimeNs,
                         const vector<int64_t>& values, int64_t conditionTrueNs,
                         int64_t conditionCorrectionNs) {
    EXPECT_EQ(bucket.start_bucket_elapsed_nanos(), startTimeNs);
    EXPECT_EQ(bucket.end_bucket_elapsed_nanos(), endTimeNs);
    ASSERT_EQ(bucket.values_size(), values.size());
    for (int i = 0; i < values.size(); ++i) {
        if (bucket.values(i).has_value_double()) {
            EXPECT_EQ((int64_t)bucket.values(i).value_double(), values[i]);
        } else {
            EXPECT_EQ(bucket.values(i).value_long(), values[i]);
        }
    }
    if (conditionTrueNs > 0) {
        EXPECT_EQ(bucket.condition_true_nanos(), conditionTrueNs);
        if (conditionCorrectionNs > 0) {
            EXPECT_EQ(bucket.condition_correction_nanos(), conditionCorrectionNs);
        }
    }
}

void ValidateKllBucket(const KllBucketInfo& bucket, int64_t startTimeNs, int64_t endTimeNs,
                       const vector<int64_t> sketchSizes, int64_t conditionTrueNs) {
    EXPECT_EQ(bucket.start_bucket_elapsed_nanos(), startTimeNs);
    EXPECT_EQ(bucket.end_bucket_elapsed_nanos(), endTimeNs);
    ASSERT_EQ(bucket.sketches_size(), sketchSizes.size());
    for (int i = 0; i < sketchSizes.size(); ++i) {
        AggregatorStateProto aggProto;
        EXPECT_TRUE(aggProto.ParseFromString(bucket.sketches(i).kll_sketch()));
        EXPECT_EQ(aggProto.num_values(), sketchSizes[i]);
    }
    if (conditionTrueNs > 0) {
        EXPECT_EQ(bucket.condition_true_nanos(), conditionTrueNs);
    }
}

bool EqualsTo(const DimensionsValue& s1, const DimensionsValue& s2) {
    if (s1.field() != s2.field()) {
        return false;
    }
    if (s1.value_case() != s2.value_case()) {
        return false;
    }
    switch (s1.value_case()) {
        case DimensionsValue::ValueCase::kValueStr:
            return (s1.value_str() == s2.value_str());
        case DimensionsValue::ValueCase::kValueInt:
            return s1.value_int() == s2.value_int();
        case DimensionsValue::ValueCase::kValueLong:
            return s1.value_long() == s2.value_long();
        case DimensionsValue::ValueCase::kValueBool:
            return s1.value_bool() == s2.value_bool();
        case DimensionsValue::ValueCase::kValueFloat:
            return s1.value_float() == s2.value_float();
        case DimensionsValue::ValueCase::kValueTuple: {
            if (s1.value_tuple().dimensions_value_size() !=
                s2.value_tuple().dimensions_value_size()) {
                return false;
            }
            bool allMatched = true;
            for (int i = 0; allMatched && i < s1.value_tuple().dimensions_value_size(); ++i) {
                allMatched &= EqualsTo(s1.value_tuple().dimensions_value(i),
                                       s2.value_tuple().dimensions_value(i));
            }
            return allMatched;
        }
        case DimensionsValue::ValueCase::VALUE_NOT_SET:
        default:
            return true;
    }
}

bool LessThan(const google::protobuf::RepeatedPtrField<StateValue>& s1,
              const google::protobuf::RepeatedPtrField<StateValue>& s2) {
    if (s1.size() != s2.size()) {
        return s1.size() < s2.size();
    }
    for (int i = 0; i < s1.size(); i++) {
        const StateValue& state1 = s1[i];
        const StateValue& state2 = s2[i];
        if (state1.atom_id() != state2.atom_id()) {
            return state1.atom_id() < state2.atom_id();
        }
        if (state1.value() != state2.value()) {
            return state1.value() < state2.value();
        }
        if (state1.group_id() != state2.group_id()) {
            return state1.group_id() < state2.group_id();
        }
    }
    return false;
}

bool LessThan(const DimensionsValue& s1, const DimensionsValue& s2) {
    if (s1.field() != s2.field()) {
        return s1.field() < s2.field();
    }
    if (s1.value_case() != s2.value_case()) {
        return s1.value_case() < s2.value_case();
    }
    switch (s1.value_case()) {
        case DimensionsValue::ValueCase::kValueStr:
            return s1.value_str() < s2.value_str();
        case DimensionsValue::ValueCase::kValueInt:
            return s1.value_int() < s2.value_int();
        case DimensionsValue::ValueCase::kValueLong:
            return s1.value_long() < s2.value_long();
        case DimensionsValue::ValueCase::kValueBool:
            return (int)s1.value_bool() < (int)s2.value_bool();
        case DimensionsValue::ValueCase::kValueFloat:
            return s1.value_float() < s2.value_float();
        case DimensionsValue::ValueCase::kValueTuple: {
            if (s1.value_tuple().dimensions_value_size() !=
                s2.value_tuple().dimensions_value_size()) {
                return s1.value_tuple().dimensions_value_size() <
                       s2.value_tuple().dimensions_value_size();
            }
            for (int i = 0; i < s1.value_tuple().dimensions_value_size(); ++i) {
                if (EqualsTo(s1.value_tuple().dimensions_value(i),
                             s2.value_tuple().dimensions_value(i))) {
                    continue;
                } else {
                    return LessThan(s1.value_tuple().dimensions_value(i),
                                    s2.value_tuple().dimensions_value(i));
                }
            }
            return false;
        }
        case DimensionsValue::ValueCase::VALUE_NOT_SET:
        default:
            return false;
    }
}

bool LessThan(const DimensionsPair& s1, const DimensionsPair& s2) {
    if (LessThan(s1.dimInWhat, s2.dimInWhat)) {
        return true;
    } else if (LessThan(s2.dimInWhat, s1.dimInWhat)) {
        return false;
    }

    return LessThan(s1.stateValues, s2.stateValues);
}

void backfillStringInDimension(const std::map<uint64_t, string>& str_map,
                               DimensionsValue* dimension) {
    if (dimension->has_value_str_hash()) {
        auto it = str_map.find((uint64_t)(dimension->value_str_hash()));
        if (it != str_map.end()) {
            dimension->clear_value_str_hash();
            dimension->set_value_str(it->second);
        } else {
            ALOGE("Can not find the string hash: %llu",
                (unsigned long long)dimension->value_str_hash());
        }
    } else if (dimension->has_value_tuple()) {
        auto value_tuple = dimension->mutable_value_tuple();
        for (int i = 0; i < value_tuple->dimensions_value_size(); ++i) {
            backfillStringInDimension(str_map, value_tuple->mutable_dimensions_value(i));
        }
    }
}

void backfillStringInReport(ConfigMetricsReport *config_report) {
    std::map<uint64_t, string> str_map;
    for (const auto& str : config_report->strings()) {
        uint64_t hash = Hash64(str);
        if (str_map.find(hash) != str_map.end()) {
            ALOGE("String hash conflicts: %s %s", str.c_str(), str_map[hash].c_str());
        }
        str_map[hash] = str;
    }
    for (int i = 0; i < config_report->metrics_size(); ++i) {
        auto metric_report = config_report->mutable_metrics(i);
        if (metric_report->has_count_metrics()) {
            backfillStringInDimension(str_map, metric_report->mutable_count_metrics());
        } else if (metric_report->has_duration_metrics()) {
            backfillStringInDimension(str_map, metric_report->mutable_duration_metrics());
        } else if (metric_report->has_gauge_metrics()) {
            backfillStringInDimension(str_map, metric_report->mutable_gauge_metrics());
        } else if (metric_report->has_value_metrics()) {
            backfillStringInDimension(str_map, metric_report->mutable_value_metrics());
        } else if (metric_report->has_kll_metrics()) {
            backfillStringInDimension(str_map, metric_report->mutable_kll_metrics());
        }
    }
    // Backfill the package names.
    for (int i = 0 ; i < config_report->uid_map().snapshots_size(); ++i) {
        auto snapshot = config_report->mutable_uid_map()->mutable_snapshots(i);
        for (int j = 0 ; j < snapshot->package_info_size(); ++j) {
            auto package_info = snapshot->mutable_package_info(j);
            if (package_info->has_name_hash()) {
                auto it = str_map.find((uint64_t)(package_info->name_hash()));
                if (it != str_map.end()) {
                    package_info->clear_name_hash();
                    package_info->set_name(it->second);
                } else {
                    ALOGE("Can not find the string package name hash: %llu",
                        (unsigned long long)package_info->name_hash());
                }

            }
        }
    }
    // Backfill the app name in app changes.
    for (int i = 0 ; i < config_report->uid_map().changes_size(); ++i) {
        auto change = config_report->mutable_uid_map()->mutable_changes(i);
        if (change->has_app_hash()) {
            auto it = str_map.find((uint64_t)(change->app_hash()));
            if (it != str_map.end()) {
                change->clear_app_hash();
                change->set_app(it->second);
            } else {
                ALOGE("Can not find the string change app name hash: %llu",
                    (unsigned long long)change->app_hash());
            }
        }
    }
}

void backfillStringInReport(ConfigMetricsReportList *config_report_list) {
    for (int i = 0; i < config_report_list->reports_size(); ++i) {
        backfillStringInReport(config_report_list->mutable_reports(i));
    }
}

bool backfillDimensionPath(const DimensionsValue& path,
                           const google::protobuf::RepeatedPtrField<DimensionsValue>& leafValues,
                           int* leafIndex, DimensionsValue* dimension) {
    dimension->set_field(path.field());
    if (path.has_value_tuple()) {
        for (int i = 0; i < path.value_tuple().dimensions_value_size(); ++i) {
            if (!backfillDimensionPath(path.value_tuple().dimensions_value(i), leafValues,
                                       leafIndex,
                                       dimension->mutable_value_tuple()->add_dimensions_value())) {
                return false;
            }
        }
    } else {
        if (*leafIndex < 0 || *leafIndex >= leafValues.size()) {
            return false;
        }
        dimension->MergeFrom(leafValues.Get(*leafIndex));
        (*leafIndex)++;
    }
    return true;
}

bool backfillDimensionPath(const DimensionsValue& path,
                           const google::protobuf::RepeatedPtrField<DimensionsValue>& leafValues,
                           DimensionsValue* dimension) {
    int leafIndex = 0;
    return backfillDimensionPath(path, leafValues, &leafIndex, dimension);
}

void backfillDimensionPath(StatsLogReport* report) {
    if (report->has_dimensions_path_in_what()) {
        auto whatPath = report->dimensions_path_in_what();
        if (report->has_count_metrics()) {
            backfillDimensionPath(whatPath, report->mutable_count_metrics());
        } else if (report->has_duration_metrics()) {
            backfillDimensionPath(whatPath, report->mutable_duration_metrics());
        } else if (report->has_gauge_metrics()) {
            backfillDimensionPath(whatPath, report->mutable_gauge_metrics());
        } else if (report->has_value_metrics()) {
            backfillDimensionPath(whatPath, report->mutable_value_metrics());
        } else if (report->has_kll_metrics()) {
            backfillDimensionPath(whatPath, report->mutable_kll_metrics());
        }
        report->clear_dimensions_path_in_what();
    }
}

void backfillDimensionPath(ConfigMetricsReport* config_report) {
    for (int i = 0; i < config_report->metrics_size(); ++i) {
        backfillDimensionPath(config_report->mutable_metrics(i));
    }
}

void backfillDimensionPath(ConfigMetricsReportList* config_report_list) {
    for (int i = 0; i < config_report_list->reports_size(); ++i) {
        backfillDimensionPath(config_report_list->mutable_reports(i));
    }
}

void backfillStartEndTimestamp(StatsLogReport *report) {
    const int64_t timeBaseNs = report->time_base_elapsed_nano_seconds();
    const int64_t bucketSizeNs = report->bucket_size_nano_seconds();
    if (report->has_count_metrics()) {
        backfillStartEndTimestampForMetrics(
            timeBaseNs, bucketSizeNs, report->mutable_count_metrics());
    } else if (report->has_duration_metrics()) {
        backfillStartEndTimestampForMetrics(
            timeBaseNs, bucketSizeNs, report->mutable_duration_metrics());
    } else if (report->has_gauge_metrics()) {
        backfillStartEndTimestampForMetrics(
            timeBaseNs, bucketSizeNs, report->mutable_gauge_metrics());
        if (report->gauge_metrics().skipped_size() > 0) {
            backfillStartEndTimestampForSkippedBuckets(
                timeBaseNs, report->mutable_gauge_metrics());
        }
    } else if (report->has_value_metrics()) {
        backfillStartEndTimestampForMetrics(
            timeBaseNs, bucketSizeNs, report->mutable_value_metrics());
        if (report->value_metrics().skipped_size() > 0) {
            backfillStartEndTimestampForSkippedBuckets(
                timeBaseNs, report->mutable_value_metrics());
        }
    } else if (report->has_kll_metrics()) {
        backfillStartEndTimestampForMetrics(timeBaseNs, bucketSizeNs,
                                            report->mutable_kll_metrics());
        if (report->kll_metrics().skipped_size() > 0) {
            backfillStartEndTimestampForSkippedBuckets(timeBaseNs, report->mutable_kll_metrics());
        }
    }
}

void backfillStartEndTimestamp(ConfigMetricsReport *config_report) {
    for (int j = 0; j < config_report->metrics_size(); ++j) {
        backfillStartEndTimestamp(config_report->mutable_metrics(j));
    }
}

void backfillStartEndTimestamp(ConfigMetricsReportList *config_report_list) {
    for (int i = 0; i < config_report_list->reports_size(); ++i) {
        backfillStartEndTimestamp(config_report_list->mutable_reports(i));
    }
}

void backfillAggregatedAtoms(ConfigMetricsReportList* config_report_list) {
    for (int i = 0; i < config_report_list->reports_size(); ++i) {
        backfillAggregatedAtoms(config_report_list->mutable_reports(i));
    }
}

void backfillAggregatedAtoms(ConfigMetricsReport* config_report) {
    for (int i = 0; i < config_report->metrics_size(); ++i) {
        backfillAggregatedAtoms(config_report->mutable_metrics(i));
    }
}

void backfillAggregatedAtoms(StatsLogReport* report) {
    if (report->has_event_metrics()) {
        backfillAggregatedAtomsInEventMetric(report->mutable_event_metrics());
    }
    if (report->has_gauge_metrics()) {
        backfillAggregatedAtomsInGaugeMetric(report->mutable_gauge_metrics());
    }
}

void backfillAggregatedAtomsInEventMetric(StatsLogReport::EventMetricDataWrapper* wrapper) {
    std::vector<EventMetricData> metricData;
    for (int i = 0; i < wrapper->data_size(); ++i) {
        AggregatedAtomInfo* atomInfo = wrapper->mutable_data(i)->mutable_aggregated_atom_info();
        for (int j = 0; j < atomInfo->elapsed_timestamp_nanos_size(); j++) {
            EventMetricData data;
            *(data.mutable_atom()) = atomInfo->atom();
            data.set_elapsed_timestamp_nanos(atomInfo->elapsed_timestamp_nanos(j));
            metricData.push_back(data);
        }
    }

    if (metricData.size() == 0) {
        return;
    }

    sort(metricData.begin(), metricData.end(),
         [](const EventMetricData& lhs, const EventMetricData& rhs) {
             return lhs.elapsed_timestamp_nanos() < rhs.elapsed_timestamp_nanos();
         });

    wrapper->clear_data();
    for (int i = 0; i < metricData.size(); ++i) {
        *(wrapper->add_data()) = metricData[i];
    }
}

void backfillAggregatedAtomsInGaugeMetric(StatsLogReport::GaugeMetricDataWrapper* wrapper) {
    for (int i = 0; i < wrapper->data_size(); ++i) {
        for (int j = 0; j < wrapper->data(i).bucket_info_size(); ++j) {
            GaugeBucketInfo* bucketInfo = wrapper->mutable_data(i)->mutable_bucket_info(j);
            vector<pair<Atom, int64_t>> atomData = unnestGaugeAtomData(*bucketInfo);

            if (atomData.size() == 0) {
                return;
            }

            bucketInfo->clear_aggregated_atom_info();
            ASSERT_EQ(bucketInfo->atom_size(), 0);
            ASSERT_EQ(bucketInfo->elapsed_timestamp_nanos_size(), 0);

            for (int k = 0; k < atomData.size(); ++k) {
                *(bucketInfo->add_atom()) = atomData[k].first;
                bucketInfo->add_elapsed_timestamp_nanos(atomData[k].second);
            }
        }
    }
}

vector<pair<Atom, int64_t>> unnestGaugeAtomData(const GaugeBucketInfo& bucketInfo) {
    vector<pair<Atom, int64_t>> atomData;
    for (int k = 0; k < bucketInfo.aggregated_atom_info_size(); ++k) {
        const AggregatedAtomInfo& atomInfo = bucketInfo.aggregated_atom_info(k);
        for (int l = 0; l < atomInfo.elapsed_timestamp_nanos_size(); ++l) {
            atomData.push_back(make_pair(atomInfo.atom(), atomInfo.elapsed_timestamp_nanos(l)));
        }
    }

    sort(atomData.begin(), atomData.end(),
         [](const pair<Atom, int64_t>& lhs, const pair<Atom, int64_t>& rhs) {
             return lhs.second < rhs.second;
         });

    return atomData;
}

void sortReportsByElapsedTime(ConfigMetricsReportList* configReportList) {
    RepeatedPtrField<ConfigMetricsReport>* reports = configReportList->mutable_reports();
    sort(reports->pointer_begin(), reports->pointer_end(),
         [](const ConfigMetricsReport* lhs, const ConfigMetricsReport* rhs) {
             return lhs->current_report_elapsed_nanos() < rhs->current_report_elapsed_nanos();
         });
}

Status FakeSubsystemSleepCallback::onPullAtom(int atomTag,
        const shared_ptr<IPullAtomResultReceiver>& resultReceiver) {
    // Convert stats_events into StatsEventParcels.
    std::vector<StatsEventParcel> parcels;
    for (int i = 1; i < 3; i++) {
        AStatsEvent* event = AStatsEvent_obtain();
        AStatsEvent_setAtomId(event, atomTag);
        std::string subsystemName = "subsystem_name_";
        subsystemName = subsystemName + std::to_string(i);
        AStatsEvent_writeString(event, subsystemName.c_str());
        AStatsEvent_writeString(event, "subsystem_subname foo");
        AStatsEvent_writeInt64(event, /*count= */ i);
        AStatsEvent_writeInt64(event, /*time_millis= */ pullNum * pullNum * 100 + i);
        AStatsEvent_build(event);
        size_t size;
        uint8_t* buffer = AStatsEvent_getBuffer(event, &size);

        StatsEventParcel p;
        // vector.assign() creates a copy, but this is inevitable unless
        // stats_event.h/c uses a vector as opposed to a buffer.
        p.buffer.assign(buffer, buffer + size);
        parcels.push_back(std::move(p));
        AStatsEvent_release(event);
    }
    pullNum++;
    resultReceiver->pullFinished(atomTag, /*success=*/true, parcels);
    return Status::ok();
}

void writeFlag(const string& flagName, const string& flagValue) {
    SetProperty(StringPrintf("persist.device_config.%s.%s", STATSD_NATIVE_NAMESPACE.c_str(),
                             flagName.c_str()),
                flagValue);
}

void writeBootFlag(const string& flagName, const string& flagValue) {
    SetProperty(StringPrintf("persist.device_config.%s.%s", STATSD_NATIVE_BOOT_NAMESPACE.c_str(),
                             flagName.c_str()),
                flagValue);
}

PackageInfoSnapshot getPackageInfoSnapshot(const sp<UidMap> uidMap) {
    ProtoOutputStream protoOutputStream;
    uidMap->writeUidMapSnapshot(/* timestamp */ 1, /* includeVersionStrings */ true,
                                /* includeInstaller */ true, /* certificateHashSize */ UINT8_MAX,
                                /* interestingUids */ {},
                                /* installerIndices */ nullptr, /* str_set */ nullptr,
                                &protoOutputStream);

    PackageInfoSnapshot packageInfoSnapshot;
    outputStreamToProto(&protoOutputStream, &packageInfoSnapshot);
    return packageInfoSnapshot;
}

PackageInfo buildPackageInfo(const string& name, const int32_t uid, const int64_t version,
                             const string& versionString, const optional<string> installer,
                             const vector<uint8_t>& certHash, const bool deleted,
                             const bool hashStrings, const optional<uint32_t> installerIndex) {
    PackageInfo packageInfo;
    packageInfo.set_version(version);
    packageInfo.set_uid(uid);
    packageInfo.set_deleted(deleted);
    if (!certHash.empty()) {
        packageInfo.set_truncated_certificate_hash(certHash.data(), certHash.size());
    }
    if (hashStrings) {
        packageInfo.set_name_hash(Hash64(name));
        packageInfo.set_version_string_hash(Hash64(versionString));
    } else {
        packageInfo.set_name(name);
        packageInfo.set_version_string(versionString);
    }
    if (installer) {
        if (installerIndex) {
            packageInfo.set_installer_index(*installerIndex);
        } else if (hashStrings) {
            packageInfo.set_installer_hash(Hash64(*installer));
        } else {
            packageInfo.set_installer(*installer);
        }
    }
    return packageInfo;
}

vector<PackageInfo> buildPackageInfos(
        const vector<string>& names, const vector<int32_t>& uids, const vector<int64_t>& versions,
        const vector<string>& versionStrings, const vector<string>& installers,
        const vector<vector<uint8_t>>& certHashes, const vector<uint8_t>& deleted,
        const vector<uint32_t>& installerIndices, const bool hashStrings) {
    vector<PackageInfo> packageInfos;
    for (int i = 0; i < uids.size(); i++) {
        const optional<uint32_t> installerIndex =
                installerIndices.empty() ? nullopt : optional<uint32_t>(installerIndices[i]);
        const optional<string> installer =
                installers.empty() ? nullopt : optional<string>(installers[i]);
        vector<uint8_t> certHash;
        if (!certHashes.empty()) {
            certHash = certHashes[i];
        }
        packageInfos.emplace_back(buildPackageInfo(names[i], uids[i], versions[i],
                                                   versionStrings[i], installer, certHash,
                                                   deleted[i], hashStrings, installerIndex));
    }
    return packageInfos;
}

ApplicationInfo createApplicationInfo(const int32_t uid, const int64_t version,
                                      const string& versionString, const string& package) {
    ApplicationInfo info;
    info.set_uid(uid);
    info.set_version(version);
    info.set_version_string(versionString);
    info.set_package_name(package);
    return info;
}

StatsdStatsReport_PulledAtomStats getPulledAtomStats(int32_t atom_id) {
    StatsdStatsReport statsReport = getStatsdStatsReport();
    StatsdStatsReport_PulledAtomStats pulledAtomStats;
    for (size_t i = 0; i < statsReport.pulled_atom_stats_size(); i++) {
        if (statsReport.pulled_atom_stats(i).atom_id() == atom_id) {
            return statsReport.pulled_atom_stats(i);
        }
    }
    return pulledAtomStats;
}

void createStatsEvent(AStatsEvent* statsEvent, uint8_t typeId, uint32_t atomId) {
    AStatsEvent_setAtomId(statsEvent, atomId);
    fillStatsEventWithSampleValue(statsEvent, typeId);
}

void fillStatsEventWithSampleValue(AStatsEvent* statsEvent, uint8_t typeId) {
    int int32Array[2] = {3, 6};
    uint32_t uids[] = {1001, 1002};
    const char* tags[] = {"tag1", "tag2"};

    switch (typeId) {
        case INT32_TYPE:
            AStatsEvent_writeInt32(statsEvent, 10);
            break;
        case INT64_TYPE:
            AStatsEvent_writeInt64(statsEvent, 1000L);
            break;
        case STRING_TYPE:
            AStatsEvent_writeString(statsEvent, "test");
            break;
        case LIST_TYPE:
            AStatsEvent_writeInt32Array(statsEvent, int32Array, 2);
            break;
        case FLOAT_TYPE:
            AStatsEvent_writeFloat(statsEvent, 1.3f);
            break;
        case BOOL_TYPE:
            AStatsEvent_writeBool(statsEvent, 1);
            break;
        case BYTE_ARRAY_TYPE:
            AStatsEvent_writeByteArray(statsEvent, (uint8_t*)"test", strlen("test"));
            break;
        case ATTRIBUTION_CHAIN_TYPE:
            AStatsEvent_writeAttributionChain(statsEvent, uids, tags, 2);
            break;
        default:
            break;
    }
}

StatsdStatsReport getStatsdStatsReport(bool resetStats) {
    StatsdStats& stats = StatsdStats::getInstance();
    return getStatsdStatsReport(stats, resetStats);
}

StatsdStatsReport getStatsdStatsReport(StatsdStats& stats, bool resetStats) {
    vector<uint8_t> statsBuffer;
    stats.dumpStats(&statsBuffer, resetStats);
    StatsdStatsReport statsReport;
    EXPECT_TRUE(statsReport.ParseFromArray(statsBuffer.data(), statsBuffer.size()));
    return statsReport;
}

StatsdConfig buildGoodConfig(int configId) {
    StatsdConfig config;
    config.set_id(configId);

    AtomMatcher screenOnMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnMatcher;
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();

    AtomMatcher* eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("SCREEN_ON_OR_OFF"));
    AtomMatcher_Combination* combination = eventMatcher->mutable_combination();
    combination->set_operation(LogicalOperation::OR);
    combination->add_matcher(screenOnMatcher.id());
    combination->add_matcher(StringToId("ScreenTurnedOff"));

    CountMetric* countMetric = config.add_count_metric();
    *countMetric = createCountMetric("Count", screenOnMatcher.id() /* what */,
                                     nullopt /* condition */, {} /* states */);
    countMetric->mutable_dimensions_in_what()->set_field(SCREEN_STATE_ATOM_ID);
    countMetric->mutable_dimensions_in_what()->add_child()->set_field(1);

    config.add_no_report_metric(StringToId("Count"));

    *config.add_predicate() = CreateScreenIsOnPredicate();
    *config.add_duration_metric() =
            createDurationMetric("Duration", StringToId("ScreenIsOn") /* what */,
                                 nullopt /* condition */, {} /* states */);

    *config.add_gauge_metric() = createGaugeMetric(
            "Gauge", screenOnMatcher.id() /* what */, GaugeMetric_SamplingType_FIRST_N_SAMPLES,
            nullopt /* condition */, nullopt /* triggerEvent */);

    *config.add_value_metric() =
            createValueMetric("Value", screenOnMatcher /* what */, 2 /* valueField */,
                              nullopt /* condition */, {} /* states */);

    *config.add_kll_metric() = createKllMetric("Kll", screenOnMatcher /* what */, 2 /* kllField */,
                                               nullopt /* condition */);

    return config;
}

StatsdConfig buildGoodConfig(int configId, int alertId) {
    StatsdConfig config = buildGoodConfig(configId);
    auto alert = config.add_alert();
    alert->set_id(alertId);
    alert->set_metric_id(StringToId("Count"));
    alert->set_num_buckets(10);
    alert->set_refractory_period_secs(100);
    alert->set_trigger_if_sum_gt(100);

    return config;
}
}  // namespace statsd
}  // namespace os
}  // namespace android
