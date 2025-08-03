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

#pragma once

#include <aidl/android/os/BnPendingIntentRef.h>
#include <aidl/android/os/BnPullAtomCallback.h>
#include <aidl/android/os/BnStatsQueryCallback.h>
#include <aidl/android/os/BnStatsSubscriptionCallback.h>
#include <aidl/android/os/IPullAtomCallback.h>
#include <aidl/android/os/IPullAtomResultReceiver.h>
#include <aidl/android/os/StatsSubscriptionCallbackReason.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "src/StatsLogProcessor.h"
#include "src/StatsService.h"
#include "src/flags/FlagProvider.h"
#include "src/hash.h"
#include "src/logd/LogEvent.h"
#include "src/matchers/EventMatcherWizard.h"
#include "src/packages/UidMap.h"
#include "src/stats_log.pb.h"
#include "src/stats_log_util.h"
#include "src/statsd_config.pb.h"
#include "stats_annotations.h"
#include "stats_event.h"
#include "statslog_statsdtest.h"

namespace android {
namespace os {
namespace statsd {

using namespace testing;
using ::aidl::android::os::BnPullAtomCallback;
using ::aidl::android::os::BnStatsQueryCallback;
using ::aidl::android::os::BnStatsSubscriptionCallback;
using ::aidl::android::os::IPullAtomCallback;
using ::aidl::android::os::IPullAtomResultReceiver;
using ::aidl::android::os::StatsSubscriptionCallbackReason;
using android::util::ProtoReader;
using google::protobuf::RepeatedPtrField;
using Status = ::ndk::ScopedAStatus;
using PackageInfoSnapshot = UidMapping_PackageInfoSnapshot;
using PackageInfo = UidMapping_PackageInfoSnapshot_PackageInfo;
using ::ndk::SharedRefBase;

// Wrapper for assertion helpers called from tests to keep track of source location of failures.
// Example usage:
//      static void myTestVerificationHelper(Foo foo) {
//          EXPECT_EQ(...);
//          ASSERT_EQ(...);
//      }
//
//      TEST_F(MyTest, TestFoo) {
//          ...
//          TRACE_CALL(myTestVerificationHelper, foo);
//          ...
//      }
//
#define TRACE_CALL(function, ...) \
    do {                          \
        SCOPED_TRACE("");         \
        (function)(__VA_ARGS__);  \
    } while (false)

const int SCREEN_STATE_ATOM_ID = util::SCREEN_STATE_CHANGED;
const int UID_PROCESS_STATE_ATOM_ID = util::UID_PROCESS_STATE_CHANGED;

enum BucketSplitEvent { APP_UPGRADE, BOOT_COMPLETE };

class MockUidMap : public UidMap {
public:
    MOCK_METHOD(int, getHostUidOrSelf, (int uid), (const));
    MOCK_METHOD(std::set<int32_t>, getAppUid, (const string& package), (const));
};

class BasicMockLogEventFilter : public LogEventFilter {
public:
    MOCK_METHOD(void, setFilteringEnabled, (bool isEnabled), (override));
    MOCK_METHOD(void, setAtomIds, (AtomIdSet tagIds, ConsumerId consumer), (override));
};

class MockPendingIntentRef : public aidl::android::os::BnPendingIntentRef {
public:
    MOCK_METHOD1(sendDataBroadcast, Status(int64_t lastReportTimeNs));
    MOCK_METHOD1(sendActiveConfigsChangedBroadcast, Status(const vector<int64_t>& configIds));
    MOCK_METHOD1(sendRestrictedMetricsChangedBroadcast, Status(const vector<int64_t>& metricIds));
    MOCK_METHOD6(sendSubscriberBroadcast,
                 Status(int64_t configUid, int64_t configId, int64_t subscriptionId,
                        int64_t subscriptionRuleId, const vector<string>& cookies,
                        const StatsDimensionsValueParcel& dimensionsValueParcel));
};

typedef StrictMock<BasicMockLogEventFilter> MockLogEventFilter;

class MockStatsQueryCallback : public BnStatsQueryCallback {
public:
    MOCK_METHOD4(sendResults,
                 Status(const vector<string>& queryData, const vector<string>& columnNames,
                        const vector<int32_t>& columnTypes, int32_t rowCount));
    MOCK_METHOD1(sendFailure, Status(const string& in_error));
};

class MockStatsSubscriptionCallback : public BnStatsSubscriptionCallback {
public:
    MOCK_METHOD(Status, onSubscriptionData,
                (StatsSubscriptionCallbackReason in_reason,
                 const std::vector<uint8_t>& in_subscriptionPayload),
                (override));
};

class StatsServiceConfigTest : public ::testing::Test {
protected:
    shared_ptr<StatsService> service;
    const int kConfigKey = 789130123;  // Randomly chosen
    const int kCallingUid = 10100;     // Randomly chosen

    void SetUp() override {
        service = createStatsService();
        // Removing config file from data/misc/stats-service and data/misc/stats-data if present
        ConfigKey configKey(kCallingUid, kConfigKey);
        service->removeConfiguration(kConfigKey, kCallingUid);
        service->mProcessor->onDumpReport(configKey, getElapsedRealtimeNs(),
                                          false /* include_current_bucket*/, true /* erase_data */,
                                          ADB_DUMP, NO_TIME_CONSTRAINTS, nullptr);
    }

    void TearDown() override {
        // Cleaning up data/misc/stats-service and data/misc/stats-data
        ConfigKey configKey(kCallingUid, kConfigKey);
        service->removeConfiguration(kConfigKey, kCallingUid);
        service->mProcessor->onDumpReport(configKey, getElapsedRealtimeNs(),
                                          false /* include_current_bucket*/, true /* erase_data */,
                                          ADB_DUMP, NO_TIME_CONSTRAINTS, nullptr);
    }

    virtual shared_ptr<StatsService> createStatsService() {
        return SharedRefBase::make<StatsService>(new UidMap(), /* queue */ nullptr,
                                                 std::make_shared<LogEventFilter>());
    }

    bool sendConfig(const StatsdConfig& config);

    ConfigMetricsReport getReports(sp<StatsLogProcessor> processor, int64_t timestamp,
                                   bool include_current = false);
};

static void assertConditionTimer(const ConditionTimer& conditionTimer, bool condition,
                                 int64_t timerNs, int64_t lastConditionTrueTimestampNs,
                                 int64_t currentBucketStartDelayNs = 0) {
    EXPECT_EQ(condition, conditionTimer.mCondition);
    EXPECT_EQ(timerNs, conditionTimer.mTimerNs);
    EXPECT_EQ(lastConditionTrueTimestampNs, conditionTimer.mLastConditionChangeTimestampNs);
    EXPECT_EQ(currentBucketStartDelayNs, conditionTimer.mCurrentBucketStartDelayNs);
}

// Converts a ProtoOutputStream to a StatsLogReport proto.
StatsLogReport outputStreamToProto(ProtoOutputStream* proto);

// Create AtomMatcher proto to simply match a specific atom type.
AtomMatcher CreateSimpleAtomMatcher(const string& name, int atomId);

// Create AtomMatcher proto for temperature atom.
AtomMatcher CreateTemperatureAtomMatcher();

// Create AtomMatcher proto for scheduled job state changed.
AtomMatcher CreateScheduledJobStateChangedAtomMatcher();

// Create AtomMatcher proto for starting a scheduled job.
AtomMatcher CreateStartScheduledJobAtomMatcher();

// Create AtomMatcher proto for a scheduled job is done.
AtomMatcher CreateFinishScheduledJobAtomMatcher();

// Create AtomMatcher proto for cancelling a scheduled job.
AtomMatcher CreateScheduleScheduledJobAtomMatcher();

// Create AtomMatcher proto for screen brightness state changed.
AtomMatcher CreateScreenBrightnessChangedAtomMatcher();

// Create AtomMatcher proto for starting battery save mode.
AtomMatcher CreateBatterySaverModeStartAtomMatcher();

// Create AtomMatcher proto for stopping battery save mode.
AtomMatcher CreateBatterySaverModeStopAtomMatcher();

// Create AtomMatcher proto for battery state none mode.
AtomMatcher CreateBatteryStateNoneMatcher();

// Create AtomMatcher proto for battery state usb mode.
AtomMatcher CreateBatteryStateUsbMatcher();

// Create AtomMatcher proto for process state changed.
AtomMatcher CreateUidProcessStateChangedAtomMatcher();

// Create AtomMatcher proto for acquiring wakelock.
AtomMatcher CreateAcquireWakelockAtomMatcher();

// Create AtomMatcher proto for releasing wakelock.
AtomMatcher CreateReleaseWakelockAtomMatcher() ;

// Create AtomMatcher proto for screen turned on.
AtomMatcher CreateScreenTurnedOnAtomMatcher();

// Create AtomMatcher proto for screen turned off.
AtomMatcher CreateScreenTurnedOffAtomMatcher();

// Create AtomMatcher proto for app sync turned on.
AtomMatcher CreateSyncStartAtomMatcher();

// Create AtomMatcher proto for app sync turned off.
AtomMatcher CreateSyncEndAtomMatcher();

// Create AtomMatcher proto for app sync moves to background.
AtomMatcher CreateMoveToBackgroundAtomMatcher();

// Create AtomMatcher proto for app sync moves to foreground.
AtomMatcher CreateMoveToForegroundAtomMatcher();

// Create AtomMatcher proto for process crashes
AtomMatcher CreateProcessCrashAtomMatcher();

// Create AtomMatcher proto for app launches.
AtomMatcher CreateAppStartOccurredAtomMatcher();

// Create AtomMatcher proto for test atom repeated state.
AtomMatcher CreateTestAtomRepeatedStateAtomMatcher(const string& name,
                                                   TestAtomReported::State state,
                                                   Position position);

// Create AtomMatcher proto for test atom repeated state is off, first position.
AtomMatcher CreateTestAtomRepeatedStateFirstOffAtomMatcher();

// Create AtomMatcher proto for test atom repeated state is on, first position.
AtomMatcher CreateTestAtomRepeatedStateFirstOnAtomMatcher();

// Create AtomMatcher proto for test atom repeated state is on, any position.
AtomMatcher CreateTestAtomRepeatedStateAnyOnAtomMatcher();

// Add an AtomMatcher to a combination AtomMatcher.
void addMatcherToMatcherCombination(const AtomMatcher& matcher, AtomMatcher* combinationMatcher);

// Create Predicate proto for screen is on.
Predicate CreateScreenIsOnPredicate();

// Create Predicate proto for screen is off.
Predicate CreateScreenIsOffPredicate();

// Create Predicate proto for a running scheduled job.
Predicate CreateScheduledJobPredicate();

// Create Predicate proto for battery saver mode.
Predicate CreateBatterySaverModePredicate();

// Create Predicate proto for device unplogged mode.
Predicate CreateDeviceUnpluggedPredicate();

// Create Predicate proto for holding wakelock.
Predicate CreateHoldingWakelockPredicate();

// Create a Predicate proto for app syncing.
Predicate CreateIsSyncingPredicate();

// Create a Predicate proto for app is in background.
Predicate CreateIsInBackgroundPredicate();

// Create a Predicate proto for test atom repeated state field is off.
Predicate CreateTestAtomRepeatedStateFirstOffPredicate();

// Create State proto for screen state atom.
State CreateScreenState();

// Create State proto for uid process state atom.
State CreateUidProcessState();

// Create State proto for overlay state atom.
State CreateOverlayState();

// Create State proto for screen state atom with on/off map.
State CreateScreenStateWithOnOffMap(int64_t screenOnId, int64_t screenOffId);

// Create State proto for screen state atom with simple on/off map.
State CreateScreenStateWithSimpleOnOffMap(int64_t screenOnId, int64_t screenOffId);

// Create StateGroup proto for ScreenState ON group
StateMap_StateGroup CreateScreenStateOnGroup(int64_t screenOnId);

// Create StateGroup proto for ScreenState OFF group
StateMap_StateGroup CreateScreenStateOffGroup(int64_t screenOffId);

// Create StateGroup proto for simple ScreenState ON group
StateMap_StateGroup CreateScreenStateSimpleOnGroup(int64_t screenOnId);

// Create StateGroup proto for simple ScreenState OFF group
StateMap_StateGroup CreateScreenStateSimpleOffGroup(int64_t screenOffId);

// Create StateMap proto for ScreenState ON/OFF map
StateMap CreateScreenStateOnOffMap(int64_t screenOnId, int64_t screenOffId);

// Create StateMap proto for simple ScreenState ON/OFF map
StateMap CreateScreenStateSimpleOnOffMap(int64_t screenOnId, int64_t screenOffId);

// Add a predicate to the predicate combination.
void addPredicateToPredicateCombination(const Predicate& predicate, Predicate* combination);

// Create dimensions from primitive fields.
FieldMatcher CreateDimensions(const int atomId, const std::vector<int>& fields);

// Create dimensions from repeated primitive fields.
FieldMatcher CreateRepeatedDimensions(const int atomId, const std::vector<int>& fields,
                                      const std::vector<Position>& positions);

// Create dimensions by attribution uid and tag.
FieldMatcher CreateAttributionUidAndTagDimensions(const int atomId,
                                                  const std::vector<Position>& positions);

// Create dimensions by attribution uid only.
FieldMatcher CreateAttributionUidDimensions(const int atomId,
                                            const std::vector<Position>& positions);

FieldMatcher CreateAttributionUidAndOtherDimensions(const int atomId,
                                                    const std::vector<Position>& positions,
                                                    const std::vector<int>& fields);

EventMetric createEventMetric(const string& name, int64_t what, const optional<int64_t>& condition);

CountMetric createCountMetric(const string& name, int64_t what, const optional<int64_t>& condition,
                              const vector<int64_t>& states);

DurationMetric createDurationMetric(const string& name, int64_t what,
                                    const optional<int64_t>& condition,
                                    const vector<int64_t>& states);

GaugeMetric createGaugeMetric(const string& name, int64_t what,
                              const GaugeMetric::SamplingType samplingType,
                              const optional<int64_t>& condition,
                              const optional<int64_t>& triggerEvent);

ValueMetric createValueMetric(const string& name, const AtomMatcher& what, int valueField,
                              const optional<int64_t>& condition, const vector<int64_t>& states);

KllMetric createKllMetric(const string& name, const AtomMatcher& what, int kllField,
                          const optional<int64_t>& condition);

Alert createAlert(const string& name, int64_t metricId, int buckets, const int64_t triggerSum);

Alarm createAlarm(const string& name, int64_t offsetMillis, int64_t periodMillis);

Subscription createSubscription(const string& name, const Subscription_RuleType type,
                                const int64_t ruleId);

// START: get primary key functions
// These functions take in atom field information and create FieldValues which are stored in the
// given HashableDimensionKey.
void getUidProcessKey(int uid, HashableDimensionKey* key);

void getOverlayKey(int uid, string packageName, HashableDimensionKey* key);

void getPartialWakelockKey(int uid, const std::string& tag, HashableDimensionKey* key);

void getPartialWakelockKey(int uid, HashableDimensionKey* key);
// END: get primary key functions

void writeAttribution(AStatsEvent* statsEvent, const vector<int>& attributionUids,
                      const vector<string>& attributionTags);

// Builds statsEvent to get buffer that is parsed into logEvent then releases statsEvent.
void parseStatsEventToLogEvent(AStatsEvent* statsEvent, LogEvent* logEvent);

shared_ptr<LogEvent> CreateTwoValueLogEvent(int atomId, int64_t eventTimeNs, int32_t value1,
                                            int32_t value2);

void CreateTwoValueLogEvent(LogEvent* logEvent, int atomId, int64_t eventTimeNs, int32_t value1,
                            int32_t value2);

shared_ptr<LogEvent> CreateThreeValueLogEvent(int atomId, int64_t eventTimeNs, int32_t value1,
                                              int32_t value2, int32_t value3);

void CreateThreeValueLogEvent(LogEvent* logEvent, int atomId, int64_t eventTimeNs, int32_t value1,
                              int32_t value2, int32_t value3);

// The repeated value log event helpers create a log event with two int fields, both
// set to the same value. This is useful for testing metrics that are only interested
// in the value of the second field but still need the first field to be populated.
std::shared_ptr<LogEvent> CreateRepeatedValueLogEvent(int atomId, int64_t eventTimeNs,
                                                      int32_t value);

void CreateRepeatedValueLogEvent(LogEvent* logEvent, int atomId, int64_t eventTimeNs,
                                 int32_t value);

std::shared_ptr<LogEvent> CreateNoValuesLogEvent(int atomId, int64_t eventTimeNs);

void CreateNoValuesLogEvent(LogEvent* logEvent, int atomId, int64_t eventTimeNs);

AStatsEvent* makeUidStatsEvent(int atomId, int64_t eventTimeNs, int uid, int data1, int data2);

AStatsEvent* makeUidStatsEvent(int atomId, int64_t eventTimeNs, int uid, int data1,
                               const vector<int>& data2);

std::shared_ptr<LogEvent> makeUidLogEvent(int atomId, int64_t eventTimeNs, int uid, int data1,
                                          int data2);

std::shared_ptr<LogEvent> makeUidLogEvent(int atomId, int64_t eventTimeNs, int uid, int data1,
                                          const vector<int>& data2);

shared_ptr<LogEvent> makeExtraUidsLogEvent(int atomId, int64_t eventTimeNs, int uid1, int data1,
                                           int data2, const std::vector<int>& extraUids);

std::shared_ptr<LogEvent> makeRepeatedUidLogEvent(int atomId, int64_t eventTimeNs,
                                                  const std::vector<int>& uids);

shared_ptr<LogEvent> makeRepeatedUidLogEvent(int atomId, int64_t eventTimeNs,
                                             const vector<int>& uids, int data1, int data2);

shared_ptr<LogEvent> makeRepeatedUidLogEvent(int atomId, int64_t eventTimeNs,
                                             const vector<int>& uids, int data1,
                                             const vector<int>& data2);

std::shared_ptr<LogEvent> makeAttributionLogEvent(int atomId, int64_t eventTimeNs,
                                                  const vector<int>& uids,
                                                  const vector<string>& tags, int data1, int data2);

sp<MockUidMap> makeMockUidMapForHosts(const map<int, vector<int>>& hostUidToIsolatedUidsMap);

sp<MockUidMap> makeMockUidMapForPackage(const string& pkg, const set<int32_t>& uids);

// Create log event for screen state changed.
std::unique_ptr<LogEvent> CreateScreenStateChangedEvent(uint64_t timestampNs,
                                                        const android::view::DisplayStateEnum state,
                                                        int loggerUid = 0);

// Create log event for screen brightness state changed.
std::unique_ptr<LogEvent> CreateScreenBrightnessChangedEvent(uint64_t timestampNs, int level);

// Create log event when scheduled job starts.
std::unique_ptr<LogEvent> CreateStartScheduledJobEvent(uint64_t timestampNs,
                                                       const vector<int>& attributionUids,
                                                       const vector<string>& attributionTags,
                                                       const string& jobName);

// Create log event when scheduled job finishes.
std::unique_ptr<LogEvent> CreateFinishScheduledJobEvent(uint64_t timestampNs,
                                                        const vector<int>& attributionUids,
                                                        const vector<string>& attributionTags,
                                                        const string& jobName);

// Create log event when scheduled job schedules.
std::unique_ptr<LogEvent> CreateScheduleScheduledJobEvent(uint64_t timestampNs,
                                                          const vector<int>& attributionUids,
                                                          const vector<string>& attributionTags,
                                                          const string& jobName);

// Create log event when battery saver starts.
std::unique_ptr<LogEvent> CreateBatterySaverOnEvent(uint64_t timestampNs);
// Create log event when battery saver stops.
std::unique_ptr<LogEvent> CreateBatterySaverOffEvent(uint64_t timestampNs);

// Create log event when battery state changes.
std::unique_ptr<LogEvent> CreateBatteryStateChangedEvent(const uint64_t timestampNs,
                                                         const BatteryPluggedStateEnum state,
                                                         int32_t uid = AID_ROOT);

// Create malformed log event for battery state change.
std::unique_ptr<LogEvent> CreateMalformedBatteryStateChangedEvent(const uint64_t timestampNs);

std::unique_ptr<LogEvent> CreateActivityForegroundStateChangedEvent(
        uint64_t timestampNs, const int uid, const string& pkgName, const string& className,
        const ActivityForegroundStateChanged::State state);

// Create log event for app moving to background.
std::unique_ptr<LogEvent> CreateMoveToBackgroundEvent(uint64_t timestampNs, int uid);

// Create log event for app moving to foreground.
std::unique_ptr<LogEvent> CreateMoveToForegroundEvent(uint64_t timestampNs, int uid);

// Create log event when the app sync starts.
std::unique_ptr<LogEvent> CreateSyncStartEvent(uint64_t timestampNs, const vector<int>& uids,
                                               const vector<string>& tags, const string& name);

// Create log event when the app sync ends.
std::unique_ptr<LogEvent> CreateSyncEndEvent(uint64_t timestampNs, const vector<int>& uids,
                                             const vector<string>& tags, const string& name);

// Create log event when the app sync ends.
std::unique_ptr<LogEvent> CreateAppCrashEvent(uint64_t timestampNs, int uid);

// Create log event for an app crash.
std::unique_ptr<LogEvent> CreateAppCrashOccurredEvent(uint64_t timestampNs, int uid);

// Create log event for acquiring wakelock.
std::unique_ptr<LogEvent> CreateAcquireWakelockEvent(uint64_t timestampNs, const vector<int>& uids,
                                                     const vector<string>& tags,
                                                     const string& wakelockName);

// Create log event for releasing wakelock.
std::unique_ptr<LogEvent> CreateReleaseWakelockEvent(uint64_t timestampNs, const vector<int>& uids,
                                                     const vector<string>& tags,
                                                     const string& wakelockName);

// Create log event for releasing wakelock.
std::unique_ptr<LogEvent> CreateIsolatedUidChangedEvent(uint64_t timestampNs, int hostUid,
                                                        int isolatedUid, bool is_create);

// Create log event for uid process state change.
std::unique_ptr<LogEvent> CreateUidProcessStateChangedEvent(
        uint64_t timestampNs, int uid, const android::app::ProcessStateEnum state);

std::unique_ptr<LogEvent> CreateBleScanStateChangedEvent(uint64_t timestampNs,
                                                         const vector<int>& attributionUids,
                                                         const vector<string>& attributionTags,
                                                         const BleScanStateChanged::State state,
                                                         const bool filtered, const bool firstMatch,
                                                         const bool opportunistic);

std::unique_ptr<LogEvent> CreateOverlayStateChangedEvent(int64_t timestampNs, const int32_t uid,
                                                         const string& packageName,
                                                         const bool usingAlertWindow,
                                                         const OverlayStateChanged::State state);

std::unique_ptr<LogEvent> CreateAppStartOccurredEvent(
        uint64_t timestampNs, int uid, const string& pkg_name,
        AppStartOccurred::TransitionType type, const string& activity_name,
        const string& calling_pkg_name, const bool is_instant_app, int64_t activity_start_msec);

std::unique_ptr<LogEvent> CreateBleScanResultReceivedEvent(uint64_t timestampNs,
                                                           const vector<int>& attributionUids,
                                                           const vector<string>& attributionTags,
                                                           const int numResults);

std::unique_ptr<LogEvent> CreateTestAtomReportedEventVariableRepeatedFields(
        uint64_t timestampNs, const vector<int>& repeatedIntField,
        const vector<int64_t>& repeatedLongField, const vector<float>& repeatedFloatField,
        const vector<string>& repeatedStringField, const bool* repeatedBoolField,
        const size_t repeatedBoolFieldLength, const vector<int>& repeatedEnumField);

std::unique_ptr<LogEvent> CreateTestAtomReportedEventWithPrimitives(
        uint64_t timestampNs, int intField, long longField, float floatField,
        const string& stringField, bool boolField, TestAtomReported::State enumField);

std::unique_ptr<LogEvent> CreateRestrictedLogEvent(int atomTag, int64_t timestampNs = 0);
std::unique_ptr<LogEvent> CreateNonRestrictedLogEvent(int atomTag, int64_t timestampNs = 0);

std::unique_ptr<LogEvent> CreatePhoneSignalStrengthChangedEvent(
        int64_t timestampNs, ::telephony::SignalStrengthEnum state);

std::unique_ptr<LogEvent> CreateTestAtomReportedEvent(
        uint64_t timestampNs, const vector<int>& attributionUids,
        const vector<string>& attributionTags, int intField, const long longField,
        const float floatField, const string& stringField, const bool boolField,
        const TestAtomReported::State enumField, const vector<uint8_t>& bytesField,
        const vector<int>& repeatedIntField, const vector<int64_t>& repeatedLongField,
        const vector<float>& repeatedFloatField, const vector<string>& repeatedStringField,
        const bool* repeatedBoolField, const size_t repeatedBoolFieldLength,
        const vector<int>& repeatedEnumField);

void createStatsEvent(AStatsEvent* statsEvent, uint8_t typeId, uint32_t atomId);

void fillStatsEventWithSampleValue(AStatsEvent* statsEvent, uint8_t typeId);

// Create a statsd log event processor upon the start time in seconds, config and key.
sp<StatsLogProcessor> CreateStatsLogProcessor(
        const int64_t timeBaseNs, int64_t currentTimeNs, const StatsdConfig& config,
        const ConfigKey& key, const shared_ptr<IPullAtomCallback>& puller = nullptr,
        const int32_t atomTag = 0 /*for puller only*/, const sp<UidMap> = new UidMap(),
        const shared_ptr<LogEventFilter>& logEventFilter = std::make_shared<LogEventFilter>());

LogEventFilter::AtomIdSet CreateAtomIdSetDefault();
LogEventFilter::AtomIdSet CreateAtomIdSetFromConfig(const StatsdConfig& config);

// Util function to sort the log events by timestamp.
void sortLogEventsByTimestamp(std::vector<std::unique_ptr<LogEvent>> *events);

int64_t StringToId(const string& str);

sp<EventMatcherWizard> createEventMatcherWizard(
        int tagId, int matcherIndex, const std::vector<FieldValueMatcher>& fieldValueMatchers = {});

StatsDimensionsValueParcel CreateAttributionUidDimensionsValueParcel(const int atomId,
                                                                     const int uid);

void ValidateUidDimension(const DimensionsValue& value, int atomId, int uid);
void ValidateWakelockAttributionUidAndTagDimension(const DimensionsValue& value, int atomId,
                                                   const int uid, const string& tag);
void ValidateUidDimension(const DimensionsValue& value, int node_idx, int atomId, int uid);
void ValidateAttributionUidDimension(const DimensionsValue& value, int atomId, int uid);
void ValidateAttributionUidAndTagDimension(
    const DimensionsValue& value, int atomId, int uid, const std::string& tag);
void ValidateAttributionUidAndTagDimension(
    const DimensionsValue& value, int node_idx, int atomId, int uid, const std::string& tag);
void ValidateStateValue(const google::protobuf::RepeatedPtrField<StateValue>& stateValues,
                        int atomId, int64_t value);

void ValidateCountBucket(const CountBucketInfo& countBucket, int64_t startTimeNs, int64_t endTimeNs,
                         int64_t count, int64_t conditionTrueNs = 0);
void ValidateDurationBucket(const DurationBucketInfo& bucket, int64_t startTimeNs,
                            int64_t endTimeNs, int64_t durationNs, int64_t conditionTrueNs = 0);
void ValidateGaugeBucketTimes(const GaugeBucketInfo& gaugeBucket, int64_t startTimeNs,
                              int64_t endTimeNs, vector<int64_t> eventTimesNs);
void ValidateValueBucket(const ValueBucketInfo& bucket, int64_t startTimeNs, int64_t endTimeNs,
                         const vector<int64_t>& values, int64_t conditionTrueNs,
                         int64_t conditionCorrectionNs);
void ValidateKllBucket(const KllBucketInfo& bucket, int64_t startTimeNs, int64_t endTimeNs,
                       const std::vector<int64_t> sketchSizes, int64_t conditionTrueNs);

struct DimensionsPair {
    DimensionsPair(DimensionsValue m1, google::protobuf::RepeatedPtrField<StateValue> m2)
        : dimInWhat(m1), stateValues(m2){};

    DimensionsValue dimInWhat;
    google::protobuf::RepeatedPtrField<StateValue> stateValues;
};

bool LessThan(const StateValue& s1, const StateValue& s2);
bool LessThan(const DimensionsValue& s1, const DimensionsValue& s2);
bool LessThan(const DimensionsPair& s1, const DimensionsPair& s2);

void backfillStartEndTimestamp(StatsLogReport* report);
void backfillStartEndTimestamp(ConfigMetricsReport *config_report);
void backfillStartEndTimestamp(ConfigMetricsReportList *config_report_list);

void backfillStringInReport(ConfigMetricsReportList *config_report_list);
void backfillStringInDimension(const std::map<uint64_t, string>& str_map,
                               DimensionsValue* dimension);

void backfillAggregatedAtoms(ConfigMetricsReportList* config_report_list);
void backfillAggregatedAtoms(ConfigMetricsReport* config_report);
void backfillAggregatedAtoms(StatsLogReport* report);
void backfillAggregatedAtomsInEventMetric(StatsLogReport::EventMetricDataWrapper* wrapper);
void backfillAggregatedAtomsInGaugeMetric(StatsLogReport::GaugeMetricDataWrapper* wrapper);

vector<pair<Atom, int64_t>> unnestGaugeAtomData(const GaugeBucketInfo& bucketInfo);

template <typename T>
void backfillStringInDimension(const std::map<uint64_t, string>& str_map,
                               T* metrics) {
    for (int i = 0; i < metrics->data_size(); ++i) {
        auto data = metrics->mutable_data(i);
        if (data->has_dimensions_in_what()) {
            backfillStringInDimension(str_map, data->mutable_dimensions_in_what());
        }
    }
}

void backfillDimensionPath(StatsLogReport* report);
void backfillDimensionPath(ConfigMetricsReport* config_report);
void backfillDimensionPath(ConfigMetricsReportList* config_report_list);

bool backfillDimensionPath(const DimensionsValue& path,
                           const google::protobuf::RepeatedPtrField<DimensionsValue>& leafValues,
                           DimensionsValue* dimension);

void sortReportsByElapsedTime(ConfigMetricsReportList* configReportList);

class FakeSubsystemSleepCallback : public BnPullAtomCallback {
public:
    // Track the number of pulls.
    int pullNum = 1;
    Status onPullAtom(int atomTag,
                      const shared_ptr<IPullAtomResultReceiver>& resultReceiver) override;
};

template <typename T>
void backfillDimensionPath(const DimensionsValue& whatPath, T* metricData) {
    for (int i = 0; i < metricData->data_size(); ++i) {
        auto data = metricData->mutable_data(i);
        if (data->dimension_leaf_values_in_what_size() > 0) {
            backfillDimensionPath(whatPath, data->dimension_leaf_values_in_what(),
                                  data->mutable_dimensions_in_what());
            data->clear_dimension_leaf_values_in_what();
        }
    }
}

struct DimensionCompare {
    bool operator()(const DimensionsPair& s1, const DimensionsPair& s2) const {
        return LessThan(s1, s2);
    }
};

template <typename T>
void sortMetricDataByDimensionsValue(const T& metricData, T* sortedMetricData) {
    std::map<DimensionsPair, int, DimensionCompare> dimensionIndexMap;
    for (int i = 0; i < metricData.data_size(); ++i) {
        dimensionIndexMap.insert(
                std::make_pair(DimensionsPair(metricData.data(i).dimensions_in_what(),
                                              metricData.data(i).slice_by_state()),
                               i));
    }
    for (const auto& itr : dimensionIndexMap) {
        *sortedMetricData->add_data() = metricData.data(itr.second);
    }
}

template <typename T>
void sortMetricDataByFirstDimensionLeafValue(const T& metricData, T* sortedMetricData) {
    std::map<DimensionsPair, int, DimensionCompare> dimensionIndexMap;
    for (int i = 0; i < metricData.data_size(); ++i) {
        dimensionIndexMap.insert(
                std::make_pair(DimensionsPair(metricData.data(i).dimension_leaf_values_in_what()[0],
                                              metricData.data(i).slice_by_state()),
                               i));
    }
    for (const auto& itr : dimensionIndexMap) {
        *sortedMetricData->add_data() = metricData.data(itr.second);
    }
}

template <typename T>
void backfillStartEndTimestampForFullBucket(const int64_t timeBaseNs, int64_t bucketSizeNs,
                                            T* bucket) {
    bucket->set_start_bucket_elapsed_nanos(timeBaseNs + bucketSizeNs * bucket->bucket_num());
    bucket->set_end_bucket_elapsed_nanos(
        timeBaseNs + bucketSizeNs * bucket->bucket_num() + bucketSizeNs);
    bucket->clear_bucket_num();
}

template <typename T>
void backfillStartEndTimestampForPartialBucket(const int64_t timeBaseNs, T* bucket) {
    if (bucket->has_start_bucket_elapsed_millis()) {
        bucket->set_start_bucket_elapsed_nanos(
            MillisToNano(bucket->start_bucket_elapsed_millis()));
        bucket->clear_start_bucket_elapsed_millis();
    }
    if (bucket->has_end_bucket_elapsed_millis()) {
        bucket->set_end_bucket_elapsed_nanos(
            MillisToNano(bucket->end_bucket_elapsed_millis()));
        bucket->clear_end_bucket_elapsed_millis();
    }
}

template <typename T>
void backfillStartEndTimestampForMetrics(const int64_t timeBaseNs, int64_t bucketSizeNs,
                                         T* metrics) {
    for (int i = 0; i < metrics->data_size(); ++i) {
        auto data = metrics->mutable_data(i);
        for (int j = 0; j < data->bucket_info_size(); ++j) {
            auto bucket = data->mutable_bucket_info(j);
            if (bucket->has_bucket_num()) {
                backfillStartEndTimestampForFullBucket(timeBaseNs, bucketSizeNs, bucket);
            } else {
                backfillStartEndTimestampForPartialBucket(timeBaseNs, bucket);
            }
        }
    }
}

template <typename T>
void backfillStartEndTimestampForSkippedBuckets(const int64_t timeBaseNs, T* metrics) {
    for (int i = 0; i < metrics->skipped_size(); ++i) {
        backfillStartEndTimestampForPartialBucket(timeBaseNs, metrics->mutable_skipped(i));
    }
}

template <typename P>
void outputStreamToProto(ProtoOutputStream* outputStream, P* proto) {
    vector<uint8_t> bytes;
    outputStream->serializeToVector(&bytes);
    proto->ParseFromArray(bytes.data(), bytes.size());
}

inline bool isAtLeastSFuncTrue() {
    return true;
}

inline bool isAtLeastSFuncFalse() {
    return false;
}

inline std::string getServerFlagFuncTrue(const std::string& flagNamespace,
                                         const std::string& flagName,
                                         const std::string& defaultValue) {
    return FLAG_TRUE;
}

inline std::string getServerFlagFuncFalse(const std::string& flagNamespace,
                                          const std::string& flagName,
                                          const std::string& defaultValue) {
    return FLAG_FALSE;
}

void writeFlag(const std::string& flagName, const std::string& flagValue);

void writeBootFlag(const std::string& flagName, const std::string& flagValue);

PackageInfoSnapshot getPackageInfoSnapshot(const sp<UidMap> uidMap);

ApplicationInfo createApplicationInfo(int32_t uid, int64_t version, const string& versionString,
                                      const string& package);

PackageInfo buildPackageInfo(const std::string& name, const int32_t uid, int64_t version,
                             const std::string& versionString,
                             const std::optional<std::string> installer,
                             const std::vector<uint8_t>& certHash, const bool deleted,
                             const bool hashStrings, const optional<uint32_t> installerIndex);

std::vector<PackageInfo> buildPackageInfos(
        const std::vector<string>& names, const std::vector<int32_t>& uids,
        const std::vector<int64_t>& versions, const std::vector<std::string>& versionStrings,
        const std::vector<std::string>& installers,
        const std::vector<std::vector<uint8_t>>& certHashes, const std::vector<uint8_t>& deleted,
        const std::vector<uint32_t>& installerIndices, const bool hashStrings);

template <typename T>
std::vector<T> concatenate(const vector<T>& a, const vector<T>& b) {
    vector<T> result(a);
    result.insert(result.end(), b.begin(), b.end());
    return result;
}

StatsdStatsReport getStatsdStatsReport(bool resetStats = false);

StatsdStatsReport getStatsdStatsReport(StatsdStats& stats, bool resetStats = false);

StatsdStatsReport_PulledAtomStats getPulledAtomStats(int atom_id);

template <typename P>
std::vector<uint8_t> protoToBytes(const P& proto) {
    const size_t byteSize = proto.ByteSizeLong();
    vector<uint8_t> bytes(byteSize);
    proto.SerializeToArray(bytes.data(), byteSize);
    return bytes;
}

StatsdConfig buildGoodConfig(int configId);

StatsdConfig buildGoodConfig(int configId, int alertId);

}  // namespace statsd
}  // namespace os
}  // namespace android
