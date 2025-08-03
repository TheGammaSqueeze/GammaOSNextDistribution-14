// Copyright (C) 2018 The Android Open Source Project
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

#include "src/shell/ShellSubscriber.h"

#include <aidl/android/os/StatsSubscriptionCallbackReason.h>
#include <gtest/gtest.h>
#include <stdio.h>
#include <unistd.h>

#include <optional>
#include <vector>

#include "frameworks/proto_logging/stats/atoms.pb.h"
#include "gtest_matchers.h"
#include "src/guardrail/StatsdStats.h"
#include "src/shell/shell_config.pb.h"
#include "src/shell/shell_data.pb.h"
#include "src/stats_log.pb.h"
#include "stats_event.h"
#include "statslog_statsdtest.h"
#include "tests/metrics/metrics_test_helper.h"
#include "tests/statsd_test_util.h"

using ::aidl::android::os::StatsSubscriptionCallbackReason;
using android::sp;
using android::os::statsd::TestAtomReported;
using android::os::statsd::TrainExperimentIds;
using android::os::statsd::util::BytesField;
using android::os::statsd::util::CPU_ACTIVE_TIME;
using android::os::statsd::util::PHONE_SIGNAL_STRENGTH_CHANGED;
using android::os::statsd::util::PLUGGED_STATE_CHANGED;
using android::os::statsd::util::SCREEN_STATE_CHANGED;
using android::os::statsd::util::TEST_ATOM_REPORTED;
using std::vector;
using testing::_;
using testing::A;
using testing::AtMost;
using testing::ByMove;
using testing::DoAll;
using testing::InSequence;
using testing::Invoke;
using testing::MockFunction;
using testing::NaggyMock;
using testing::NiceMock;
using testing::Return;
using testing::SaveArg;
using testing::SetArgPointee;
using testing::StrictMock;

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

namespace {

int kUid1 = 1000;
int kUid2 = 2000;

int kCpuTime1 = 100;
int kCpuTime2 = 200;

int64_t kCpuActiveTimeEventTimestampNs = 1111L;

// Number of clients running simultaneously

// Just a single client
const int kSingleClient = 1;
// One more client than allowed binder threads
const int kNumClients = 11;

// Utility to make an expected pulled atom shell data
ShellData getExpectedPulledData() {
    ShellData shellData;
    auto* atom1 = shellData.add_atom()->mutable_cpu_active_time();
    atom1->set_uid(kUid1);
    atom1->set_time_millis(kCpuTime1);
    shellData.add_elapsed_timestamp_nanos(kCpuActiveTimeEventTimestampNs);

    auto* atom2 = shellData.add_atom()->mutable_cpu_active_time();
    atom2->set_uid(kUid2);
    atom2->set_time_millis(kCpuTime2);
    shellData.add_elapsed_timestamp_nanos(kCpuActiveTimeEventTimestampNs);

    return shellData;
}

// Utility to make a pulled atom Shell Config
ShellSubscription getPulledConfig() {
    ShellSubscription config;
    auto* pull_config = config.add_pulled();
    pull_config->mutable_matcher()->set_atom_id(CPU_ACTIVE_TIME);
    pull_config->set_freq_millis(2000);
    return config;
}

// Utility to adjust CPU time for pulled events
shared_ptr<LogEvent> makeCpuActiveTimeAtom(int32_t uid, int64_t timeMillis) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, CPU_ACTIVE_TIME);
    AStatsEvent_overwriteTimestamp(statsEvent, kCpuActiveTimeEventTimestampNs);
    AStatsEvent_writeInt32(statsEvent, uid);
    AStatsEvent_writeInt64(statsEvent, timeMillis);

    std::shared_ptr<LogEvent> logEvent = std::make_shared<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());
    return logEvent;
}

// Utility to create pushed atom LogEvents
vector<std::shared_ptr<LogEvent>> getPushedEvents() {
    vector<std::shared_ptr<LogEvent>> pushedList;
    // Create the LogEvent from an AStatsEvent
    std::unique_ptr<LogEvent> logEvent1 = CreateScreenStateChangedEvent(
            1000 /*timestamp*/, ::android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    std::unique_ptr<LogEvent> logEvent2 = CreateScreenStateChangedEvent(
            2000 /*timestamp*/, ::android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    std::unique_ptr<LogEvent> logEvent3 = CreateBatteryStateChangedEvent(
            3000 /*timestamp*/, BatteryPluggedStateEnum::BATTERY_PLUGGED_USB);
    std::unique_ptr<LogEvent> logEvent4 = CreateBatteryStateChangedEvent(
            4000 /*timestamp*/, BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE);
    pushedList.push_back(std::move(logEvent1));
    pushedList.push_back(std::move(logEvent2));
    pushedList.push_back(std::move(logEvent3));
    pushedList.push_back(std::move(logEvent4));
    return pushedList;
}

// Utility to read & return ShellData proto, skipping heartbeats.
static ShellData readData(int fd) {
    ssize_t dataSize = 0;
    while (dataSize == 0) {
        read(fd, &dataSize, sizeof(dataSize));
    }
    // Read that much data in proto binary format.
    vector<uint8_t> dataBuffer(dataSize);
    EXPECT_EQ((int)dataSize, read(fd, dataBuffer.data(), dataSize));

    // Make sure the received bytes can be parsed to an atom.
    ShellData receivedAtom;
    EXPECT_TRUE(receivedAtom.ParseFromArray(dataBuffer.data(), dataSize) != 0);
    return receivedAtom;
}

void runShellTest(ShellSubscription config, sp<MockUidMap> uidMap,
                  sp<MockStatsPullerManager> pullerManager,
                  const vector<std::shared_ptr<LogEvent>>& pushedEvents,
                  const vector<ShellData>& expectedData, int numClients) {
    sp<ShellSubscriber> shellManager =
            new ShellSubscriber(uidMap, pullerManager, std::make_shared<LogEventFilter>());

    size_t bufferSize = config.ByteSize();
    vector<uint8_t> buffer(bufferSize);
    config.SerializeToArray(&buffer[0], bufferSize);

    int fds_configs[numClients][2];
    int fds_datas[numClients][2];
    for (int i = 0; i < numClients; i++) {
        // set up 2 pipes for read/write config and data
        ASSERT_EQ(0, pipe2(fds_configs[i], O_CLOEXEC));
        ASSERT_EQ(0, pipe2(fds_datas[i], O_CLOEXEC));

        // write the config to pipe, first write size of the config
        write(fds_configs[i][1], &bufferSize, sizeof(bufferSize));
        // then write config itself
        write(fds_configs[i][1], buffer.data(), bufferSize);
        close(fds_configs[i][1]);

        shellManager->startNewSubscription(fds_configs[i][0], fds_datas[i][1], /*timeoutSec=*/-1);
        close(fds_configs[i][0]);
        close(fds_datas[i][1]);
    }

    // send a log event that matches the config.
    for (const auto& event : pushedEvents) {
        shellManager->onLogEvent(*event);
    }

    for (int i = 0; i < numClients; i++) {
        vector<ShellData> actualData;
        for (int j = 1; j <= expectedData.size(); j++) {
            actualData.push_back(readData(fds_datas[i][0]));
        }

        EXPECT_THAT(expectedData, UnorderedPointwise(EqShellData(), actualData));
    }

    // Not closing fds_datas[i][0] because this causes writes within ShellSubscriberClient to hang
}

unique_ptr<LogEvent> createTestAtomReportedEvent(const uint64_t timestampNs,
                                                 const int32_t intFieldValue,
                                                 const vector<int64_t>& expIds) {
    TrainExperimentIds trainExpIds;
    *trainExpIds.mutable_experiment_id() = {expIds.begin(), expIds.end()};
    const vector<uint8_t> trainExpIdsBytes = protoToBytes(trainExpIds);
    return CreateTestAtomReportedEvent(
            timestampNs, /* attributionUids */ {1001},
            /* attributionTags */ {"app1"}, intFieldValue, /*longField */ 0LL,
            /* floatField */ 0.0f,
            /* stringField */ "abc", /* boolField */ false, TestAtomReported::OFF, trainExpIdsBytes,
            /* repeatedIntField */ {}, /* repeatedLongField */ {}, /* repeatedFloatField */ {},
            /* repeatedStringField */ {}, /* repeatedBoolField */ {},
            /* repeatedBoolFieldLength */ 0, /* repeatedEnumField */ {});
}

TestAtomReported createTestAtomReportedProto(const int32_t intFieldValue,
                                             const vector<int64_t>& expIds) {
    TestAtomReported t;
    auto* attributionNode = t.add_attribution_node();
    attributionNode->set_uid(1001);
    attributionNode->set_tag("app");  // String transformation removes trailing digits.
    t.set_int_field(intFieldValue);
    t.set_long_field(0);
    t.set_float_field(0.0f);
    t.set_string_field("abc");
    t.set_boolean_field(false);
    t.set_state(TestAtomReported_State_OFF);
    *t.mutable_bytes_field()->mutable_experiment_id() = {expIds.begin(), expIds.end()};
    return t;
}

class ShellSubscriberCallbackTest : public ::testing::Test {
protected:
    ShellSubscriberCallbackTest()
        : uidMap(new NaggyMock<MockUidMap>()),
          pullerManager(new StrictMock<MockStatsPullerManager>()),
          mockLogEventFilter(std::make_shared<MockLogEventFilter>()),
          shellSubscriber(uidMap, pullerManager, mockLogEventFilter),
          callback(SharedRefBase::make<StrictMock<MockStatsSubscriptionCallback>>()),
          reason(nullopt) {
    }

    void SetUp() override {
        // Save callback arguments when it is invoked.
        ON_CALL(*callback, onSubscriptionData(_, _))
                .WillByDefault(DoAll(SaveArg<0>(&reason), SaveArg<1>(&payload),
                                     [] { return Status::ok(); }));

        ShellSubscription config;
        config.add_pushed()->set_atom_id(TEST_ATOM_REPORTED);
        FieldValueMatcher* fvm = config.mutable_pushed(0)->add_field_value_matcher();
        fvm->set_field(1);  // attribution_chain
        fvm->set_position(Position::FIRST);
        fvm = fvm->mutable_matches_tuple()->add_field_value_matcher();
        fvm->set_field(2);  // tag field
        fvm->mutable_replace_string()->set_regex(
                R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
        fvm->mutable_replace_string()->set_replacement("");

        config.add_pushed()->set_atom_id(SCREEN_STATE_CHANGED);
        config.add_pushed()->set_atom_id(PHONE_SIGNAL_STRENGTH_CHANGED);
        configBytes = protoToBytes(config);

        StatsdStats::getInstance().reset();

        // Expect empty call from the shellSubscriber destructor
        // and may be one additional call during unsubscribe if subscription was active.
        // Putting this here because newer EXPECT_CALL takes precedence over an older one.
        LogEventFilter::AtomIdSet tagIds;
        EXPECT_CALL(*mockLogEventFilter, setAtomIds(tagIds, &shellSubscriber)).Times(AtMost(2));

        // Catch callback invocation from uninsteresting unsubscribe call.
        EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(AtMost(1));
    }

    void TearDown() override {
        shellSubscriber.unsubscribe(callback);
        StatsdStats::getInstance().reset();
    }

    sp<MockUidMap> uidMap;
    sp<MockStatsPullerManager> pullerManager;
    std::shared_ptr<MockLogEventFilter> mockLogEventFilter;
    ShellSubscriber shellSubscriber;
    std::shared_ptr<MockStatsSubscriptionCallback> callback;
    vector<uint8_t> configBytes;

    // Capture callback arguments.
    std::optional<StatsSubscriptionCallbackReason> reason;
    vector<uint8_t> payload;
};

class ShellSubscriberCallbackPulledTest : public ShellSubscriberCallbackTest {
protected:
    void SetUp() override {
        ShellSubscriberCallbackTest::SetUp();

        const vector<int32_t> uids{AID_SYSTEM};
        const vector<std::shared_ptr<LogEvent>> pulledData{
                makeCpuActiveTimeAtom(/*uid=*/kUid1, /*timeMillis=*/kCpuTime1),
                makeCpuActiveTimeAtom(/*uid=*/kUid2, /*timeMillis=*/kCpuTime2)};
        ON_CALL(*pullerManager, Pull(CPU_ACTIVE_TIME, uids, _, _))
                .WillByDefault(DoAll(SetArgPointee<3>(pulledData), Return(true)));

        configBytes = protoToBytes(getPulledConfig());

        // Used to call pullAndSendHeartbeatsIfNeeded directly without depending on sleep.
        shellSubscriberClient = std::move(ShellSubscriberClient::create(
                configBytes, callback, /* startTimeSec= */ 0, uidMap, pullerManager));
    }

    unique_ptr<ShellSubscriberClient> shellSubscriberClient;
};

LogEventFilter::AtomIdSet CreateAtomIdSetFromShellSubscriptionBytes(const vector<uint8_t>& bytes) {
    LogEventFilter::AtomIdSet result;

    ShellSubscription config;
    config.ParseFromArray(bytes.data(), bytes.size());

    for (int i = 0; i < config.pushed_size(); i++) {
        const auto& pushed = config.pushed(i);
        EXPECT_TRUE(pushed.has_atom_id());
        result.insert(pushed.atom_id());
    }

    return result;
}

}  // namespace

TEST_F(ShellSubscriberCallbackTest, testShellSubscriberDestructor) {
    LogEventFilter::AtomIdSet tagIds;
    EXPECT_CALL(*mockLogEventFilter, setAtomIds(tagIds, &shellSubscriber)).Times(1);
}

TEST_F(ShellSubscriberCallbackTest, testAddSubscription) {
    EXPECT_CALL(
            *mockLogEventFilter,
            setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes), &shellSubscriber))
            .Times(1);

    ASSERT_TRUE(shellSubscriber.startNewSubscription(configBytes, callback));

    auto subscriptionStats = getStatsdStatsReport().subscription_stats();

    EXPECT_EQ(subscriptionStats.pull_thread_wakeup_count(), 0);
    ASSERT_EQ(subscriptionStats.per_subscription_stats_size(), 1);
    auto perSubscriptionStats = subscriptionStats.per_subscription_stats(0);
    EXPECT_EQ(perSubscriptionStats.pushed_atom_count(), 3);
    EXPECT_FALSE(perSubscriptionStats.has_pulled_atom_count());
    EXPECT_GT(perSubscriptionStats.start_time_sec(), 0);
    EXPECT_EQ(perSubscriptionStats.end_time_sec(), 0);
    EXPECT_EQ(perSubscriptionStats.flush_count(), 0);
}

TEST_F(ShellSubscriberCallbackTest, testAddSubscriptionExceedMax) {
    const size_t maxSubs = ShellSubscriber::getMaxSubscriptions();
    // setAtomIds called with tagIds during subscribe and unsubscribe until the 2nd last
    // subscription is removed
    EXPECT_CALL(
            *mockLogEventFilter,
            setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes), &shellSubscriber))
            .Times(maxSubs * 2 - 1);

    // setAtomIds called with empty tagIds during last unsubscribe.
    LogEventFilter::AtomIdSet tagIds;
    EXPECT_CALL(*mockLogEventFilter, setAtomIds(tagIds, &shellSubscriber))
            .Times(1)
            .RetiresOnSaturation();

    vector<uint8_t> results(maxSubs, false);

    std::shared_ptr<MockStatsSubscriptionCallback> callbacks[maxSubs];
    for (int i = 0; i < maxSubs; i++) {
        callbacks[i] = SharedRefBase::make<NiceMock<MockStatsSubscriptionCallback>>();
        ON_CALL(*(callbacks[i]), onSubscriptionData(_, _))
                .WillByDefault(Return(ByMove(Status::ok())));
        results[i] = shellSubscriber.startNewSubscription(configBytes, callbacks[i]);
    }

    // First maxSubs subscriptions should succeed.
    EXPECT_THAT(results, Each(IsTrue()));

    // Subsequent startNewSubscription should fail.
    EXPECT_FALSE(shellSubscriber.startNewSubscription(configBytes, callback));

    for (int i = 0; i < maxSubs; i++) {
        shellSubscriber.unsubscribe(callbacks[i]);
    }
}

TEST_F(ShellSubscriberCallbackTest, testPushedEventsAreCached) {
    MockFunction<void(string checkPointName)> check;
    {
        // Use InSequence and MockFunction to make onSubscriptionData called 0 times work as
        // expected by only limiting the scope to until the end of the test body and ignore the
        // onSubscriptionData EXPECT_CALL in the tearDown().
        InSequence s;

        // Expect callback to not be invoked until test body has finished executing.
        EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(0));
        EXPECT_CALL(*mockLogEventFilter,
                    setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes),
                               &shellSubscriber))
                .Times(1);
        EXPECT_CALL(check, Call(""));
    }
    shellSubscriber.startNewSubscription(configBytes, callback);

    // Log an event that does NOT invoke the callack.
    shellSubscriber.onLogEvent(*CreateScreenStateChangedEvent(
            1000 /*timestamp*/, ::android::view::DisplayStateEnum::DISPLAY_STATE_ON));

    check.Call("");
}

TEST_F(ShellSubscriberCallbackTest, testOverflowCacheIsFlushed) {
    // Expect callback to be invoked once.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(1)).RetiresOnSaturation();
    EXPECT_CALL(
            *mockLogEventFilter,
            setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes), &shellSubscriber))
            .Times(1);
    shellSubscriber.startNewSubscription(configBytes, callback);

    shellSubscriber.onLogEvent(*CreateScreenStateChangedEvent(
            1000 /*timestamp*/, ::android::view::DisplayStateEnum::DISPLAY_STATE_ON));

    // Inflate size of TestAtomReported through the MODE_BYTES field.
    const vector<int64_t> expIds = vector<int64_t>(200, INT64_MAX);

    // This event should trigger cache overflow flush.
    shellSubscriber.onLogEvent(*createTestAtomReportedEvent(/*timestampNs=*/1100,
                                                            /*intFieldValue=*/1, expIds));

    EXPECT_THAT(reason, Eq(StatsSubscriptionCallbackReason::STATSD_INITIATED));

    // Get ShellData proto from the bytes payload of the callback.
    ShellData actualShellData;
    ASSERT_TRUE(actualShellData.ParseFromArray(payload.data(), payload.size()));

    ShellData expectedShellData;
    expectedShellData.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    *expectedShellData.add_atom()->mutable_test_atom_reported() =
            createTestAtomReportedProto(/* intFieldValue=*/1, expIds);
    expectedShellData.add_elapsed_timestamp_nanos(1000);
    expectedShellData.add_elapsed_timestamp_nanos(1100);

    EXPECT_THAT(actualShellData, EqShellData(expectedShellData));

    auto subscriptionStats = getStatsdStatsReport().subscription_stats();
    ASSERT_EQ(subscriptionStats.per_subscription_stats_size(), 1);
    auto perSubscriptionStats = subscriptionStats.per_subscription_stats(0);
    EXPECT_EQ(perSubscriptionStats.flush_count(), 1);
}

TEST_F(ShellSubscriberCallbackTest, testFlushTrigger) {
    // Expect callback to be invoked once.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(1)).RetiresOnSaturation();
    EXPECT_CALL(
            *mockLogEventFilter,
            setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes), &shellSubscriber))
            .Times(1);
    shellSubscriber.startNewSubscription(configBytes, callback);

    shellSubscriber.onLogEvent(*CreateScreenStateChangedEvent(
            1000 /*timestamp*/, ::android::view::DisplayStateEnum::DISPLAY_STATE_ON));

    shellSubscriber.flushSubscription(callback);

    EXPECT_THAT(reason, Eq(StatsSubscriptionCallbackReason::FLUSH_REQUESTED));

    // Get ShellData proto from the bytes payload of the callback.
    ShellData actualShellData;
    ASSERT_TRUE(actualShellData.ParseFromArray(payload.data(), payload.size()));

    ShellData expectedShellData;
    expectedShellData.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    expectedShellData.add_elapsed_timestamp_nanos(1000);

    EXPECT_THAT(actualShellData, EqShellData(expectedShellData));

    auto subscriptionStats = getStatsdStatsReport().subscription_stats();
    ASSERT_EQ(subscriptionStats.per_subscription_stats_size(), 1);
    auto perSubscriptionStats = subscriptionStats.per_subscription_stats(0);
    EXPECT_EQ(perSubscriptionStats.flush_count(), 1);
}

TEST_F(ShellSubscriberCallbackTest, testFlushTriggerEmptyCache) {
    // Expect callback to be invoked once.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(1)).RetiresOnSaturation();
    EXPECT_CALL(
            *mockLogEventFilter,
            setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes), &shellSubscriber))
            .Times(1);
    shellSubscriber.startNewSubscription(configBytes, callback);

    shellSubscriber.flushSubscription(callback);

    EXPECT_THAT(reason, Eq(StatsSubscriptionCallbackReason::FLUSH_REQUESTED));

    // Get ShellData proto from the bytes payload of the callback.
    ShellData actualShellData;
    ASSERT_TRUE(actualShellData.ParseFromArray(payload.data(), payload.size()));

    ShellData expectedShellData;

    EXPECT_THAT(actualShellData, EqShellData(expectedShellData));

    auto subscriptionStats = getStatsdStatsReport().subscription_stats();
    ASSERT_EQ(subscriptionStats.per_subscription_stats_size(), 1);
    auto perSubscriptionStats = subscriptionStats.per_subscription_stats(0);
    ASSERT_EQ(perSubscriptionStats.flush_count(), 1);
}

TEST_F(ShellSubscriberCallbackTest, testUnsubscribe) {
    // Expect callback to be invoked once.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(1));
    Expectation newSubcriptionEvent =
            EXPECT_CALL(*mockLogEventFilter,
                        setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes),
                                   &shellSubscriber))
                    .Times(1);

    // setAtomIds called with no atoms on unsubscribe.
    LogEventFilter::AtomIdSet idSetEmpty;
    EXPECT_CALL(*mockLogEventFilter, setAtomIds(idSetEmpty, &shellSubscriber))
            .Times(1)
            .After(newSubcriptionEvent)
            .RetiresOnSaturation();

    shellSubscriber.startNewSubscription(configBytes, callback);

    shellSubscriber.onLogEvent(*CreateScreenStateChangedEvent(
            1000 /*timestamp*/, ::android::view::DisplayStateEnum::DISPLAY_STATE_ON));

    shellSubscriber.unsubscribe(callback);

    EXPECT_THAT(reason, Eq(StatsSubscriptionCallbackReason::SUBSCRIPTION_ENDED));

    // Get ShellData proto from the bytes payload of the callback.
    ShellData actualShellData;
    ASSERT_TRUE(actualShellData.ParseFromArray(payload.data(), payload.size()));

    ShellData expectedShellData;
    expectedShellData.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    expectedShellData.add_elapsed_timestamp_nanos(1000);

    EXPECT_THAT(actualShellData, EqShellData(expectedShellData));

    auto subscriptionStats = getStatsdStatsReport().subscription_stats();
    ASSERT_THAT(subscriptionStats.per_subscription_stats_size(), Eq(1));
    EXPECT_THAT(subscriptionStats.per_subscription_stats(0).end_time_sec(), Gt(0));

    // This event is ignored as the subscription has ended.
    shellSubscriber.onLogEvent(*CreateScreenStateChangedEvent(
            1000 /*timestamp*/, ::android::view::DisplayStateEnum::DISPLAY_STATE_ON));

    // This should be a no-op as we've already unsubscribed.
    shellSubscriber.unsubscribe(callback);

    auto subscriptionStats2 = getStatsdStatsReport().subscription_stats();
    ASSERT_THAT(subscriptionStats2.per_subscription_stats_size(), Eq(1));
    EXPECT_THAT(subscriptionStats2.per_subscription_stats(0).end_time_sec(),
                Eq(subscriptionStats.per_subscription_stats(0).end_time_sec()));
}

TEST_F(ShellSubscriberCallbackTest, testUnsubscribeEmptyCache) {
    // Expect callback to be invoked once.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(1)).RetiresOnSaturation();
    Expectation newSubcriptionEvent =
            EXPECT_CALL(*mockLogEventFilter,
                        setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes),
                                   &shellSubscriber))
                    .Times(1);
    LogEventFilter::AtomIdSet idSetEmpty;
    EXPECT_CALL(*mockLogEventFilter, setAtomIds(idSetEmpty, &shellSubscriber))
            .Times(1)
            .After(newSubcriptionEvent)
            .RetiresOnSaturation();

    shellSubscriber.startNewSubscription(configBytes, callback);

    shellSubscriber.unsubscribe(callback);

    EXPECT_THAT(reason, Eq(StatsSubscriptionCallbackReason::SUBSCRIPTION_ENDED));

    // Get ShellData proto from the bytes payload of the callback.
    ShellData actualShellData;
    ASSERT_TRUE(actualShellData.ParseFromArray(payload.data(), payload.size()));

    ShellData expectedShellData;

    EXPECT_THAT(actualShellData, EqShellData(expectedShellData));
}

TEST_F(ShellSubscriberCallbackTest, testTruncateTimestampAtom) {
    // Expect callback to be invoked once.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(1)).RetiresOnSaturation();
    EXPECT_CALL(
            *mockLogEventFilter,
            setAtomIds(CreateAtomIdSetFromShellSubscriptionBytes(configBytes), &shellSubscriber))
            .Times(1);
    shellSubscriber.startNewSubscription(configBytes, callback);

    shellSubscriber.onLogEvent(*CreatePhoneSignalStrengthChangedEvent(
            NS_PER_SEC * 5 * 60 + 1000 /*timestamp*/,
            ::android::telephony::SignalStrengthEnum::SIGNAL_STRENGTH_GOOD));

    shellSubscriber.flushSubscription(callback);

    // Get ShellData proto from the bytes payload of the callback.
    ShellData actualShellData;
    ASSERT_TRUE(actualShellData.ParseFromArray(payload.data(), payload.size()));

    ShellData expectedShellData;
    expectedShellData.add_atom()->mutable_phone_signal_strength_changed()->set_signal_strength(
            ::android::telephony::SignalStrengthEnum::SIGNAL_STRENGTH_GOOD);
    expectedShellData.add_elapsed_timestamp_nanos(NS_PER_SEC * 5 * 60);

    EXPECT_THAT(actualShellData, EqShellData(expectedShellData));
}

TEST_F(ShellSubscriberCallbackPulledTest, testPullIfNeededBeforeInterval) {
    // Pull should not happen
    EXPECT_CALL(*pullerManager, Pull(_, A<const vector<int32_t>&>(), _, _)).Times(Exactly(0));

    // Expect callback to not be invoked.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(0));

    shellSubscriberClient->pullAndSendHeartbeatsIfNeeded(/* nowSecs= */ 0, /* nowMillis= */ 0,
                                                         /* nowNanos= */ 0);
}

TEST_F(ShellSubscriberCallbackPulledTest, testPullAtInterval) {
    // Pull should happen once. The data is cached.
    EXPECT_CALL(*pullerManager, Pull(_, A<const vector<int32_t>&>(), _, _)).Times(Exactly(1));

    // Expect callback to not be invoked.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(0));

    // This pull should NOT trigger a cache flush.
    shellSubscriberClient->pullAndSendHeartbeatsIfNeeded(/* nowSecs= */ 61, /* nowMillis= */ 61'000,
                                                         /* nowNanos= */ 61'000'000'000);
}

TEST_F(ShellSubscriberCallbackPulledTest, testCachedPullIsFlushed) {
    // Pull should happen once. The data is cached.
    EXPECT_CALL(*pullerManager, Pull(_, A<const vector<int32_t>&>(), _, _)).Times(Exactly(1));

    // This pull should NOT trigger a cache flush.
    shellSubscriberClient->pullAndSendHeartbeatsIfNeeded(/* nowSecs= */ 61, /* nowMillis= */ 61'000,
                                                         /* nowNanos= */ 61'000'000'000);

    // Expect callback to be invoked once flush is requested.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(1));

    // This should flush out data cached from the pull.
    shellSubscriberClient->flush();

    EXPECT_THAT(reason, Eq(StatsSubscriptionCallbackReason::FLUSH_REQUESTED));

    // Get ShellData proto from the bytes payload of the callback.
    ShellData actualShellData;
    ASSERT_TRUE(actualShellData.ParseFromArray(payload.data(), payload.size()));

    EXPECT_THAT(actualShellData, EqShellData(getExpectedPulledData()));
}

TEST_F(ShellSubscriberCallbackPulledTest, testPullAtCacheTimeout) {
    // Pull should happen once. The data is flushed.
    EXPECT_CALL(*pullerManager, Pull(_, A<const vector<int32_t>&>(), _, _)).Times(Exactly(1));

    // Expect callback to be invoked.
    EXPECT_CALL(*callback, onSubscriptionData(_, _)).Times(Exactly(1));

    // This pull should trigger a cache flush.
    shellSubscriberClient->pullAndSendHeartbeatsIfNeeded(/* nowSecs= */ 70, /* nowMillis= */ 70'000,
                                                         /* nowNanos= */ 70'000'000'000);

    EXPECT_THAT(reason, Eq(StatsSubscriptionCallbackReason::STATSD_INITIATED));

    // Get ShellData proto from the bytes payload of the callback.
    ShellData actualShellData;
    ASSERT_TRUE(actualShellData.ParseFromArray(payload.data(), payload.size()));

    EXPECT_THAT(actualShellData, EqShellData(getExpectedPulledData()));
}

TEST_F(ShellSubscriberCallbackPulledTest, testPullFrequencyTooShort) {
    // Pull should NOT happen.
    EXPECT_CALL(*pullerManager, Pull(_, A<const vector<int32_t>&>(), _, _)).Times(Exactly(0));

    // This should not trigger a pull even though the timestamp passed in matches the pull interval
    // specified in the config.
    const int64_t sleepTimeMs =
            shellSubscriberClient->pullAndSendHeartbeatsIfNeeded(2, 2000, 2'000'000'000);
}

TEST_F(ShellSubscriberCallbackPulledTest, testMinSleep) {
    // Pull should NOT happen.
    EXPECT_CALL(*pullerManager, Pull(_, A<const vector<int32_t>&>(), _, _)).Times(Exactly(0));

    const int64_t sleepTimeMs =
            shellSubscriberClient->pullAndSendHeartbeatsIfNeeded(59, 59'000, 59'000'000'000);

    // Even though there is only 1000 ms left until the next pull, the sleep time returned is
    // kMinCallbackSleepIntervalMs.
    EXPECT_THAT(sleepTimeMs, Eq(ShellSubscriberClient::kMinCallbackSleepIntervalMs));
}

TEST(ShellSubscriberTest, testPushedSubscription) {
    sp<MockUidMap> uidMap = new NaggyMock<MockUidMap>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    vector<std::shared_ptr<LogEvent>> pushedList = getPushedEvents();

    // create a simple config to get screen events
    ShellSubscription config;
    config.add_pushed()->set_atom_id(SCREEN_STATE_CHANGED);

    // this is the expected screen event atom.
    vector<ShellData> expectedData;
    ShellData shellData1;
    shellData1.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    shellData1.add_elapsed_timestamp_nanos(pushedList[0]->GetElapsedTimestampNs());
    ShellData shellData2;
    shellData2.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    shellData2.add_elapsed_timestamp_nanos(pushedList[1]->GetElapsedTimestampNs());
    expectedData.push_back(shellData1);
    expectedData.push_back(shellData2);

    // Test with single client
    TRACE_CALL(runShellTest, config, uidMap, pullerManager, pushedList, expectedData,
               kSingleClient);

    // Test with multiple client
    TRACE_CALL(runShellTest, config, uidMap, pullerManager, pushedList, expectedData, kNumClients);
}

TEST(ShellSubscriberTest, testPulledSubscription) {
    sp<MockUidMap> uidMap = new NaggyMock<MockUidMap>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    const vector<int32_t> uids = {AID_SYSTEM};
    EXPECT_CALL(*pullerManager, Pull(CPU_ACTIVE_TIME, uids, _, _))
            .WillRepeatedly(Invoke([](int tagId, const vector<int32_t>&, const int64_t,
                                      vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(makeCpuActiveTimeAtom(/*uid=*/kUid1, /*timeMillis=*/kCpuTime1));
                data->push_back(makeCpuActiveTimeAtom(/*uid=*/kUid2, /*timeMillis=*/kCpuTime2));
                return true;
            }));

    // Test with single client
    TRACE_CALL(runShellTest, getPulledConfig(), uidMap, pullerManager, /*pushedEvents=*/{},
               {getExpectedPulledData()}, kSingleClient);

    // Test with multiple clients.
    TRACE_CALL(runShellTest, getPulledConfig(), uidMap, pullerManager, {},
               {getExpectedPulledData()}, kNumClients);
}

TEST(ShellSubscriberTest, testBothSubscriptions) {
    sp<MockUidMap> uidMap = new NaggyMock<MockUidMap>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    const vector<int32_t> uids = {AID_SYSTEM};
    EXPECT_CALL(*pullerManager, Pull(CPU_ACTIVE_TIME, uids, _, _))
            .WillRepeatedly(Invoke([](int tagId, const vector<int32_t>&, const int64_t,
                                      vector<std::shared_ptr<LogEvent>>* data) {
                data->clear();
                data->push_back(makeCpuActiveTimeAtom(/*uid=*/kUid1, /*timeMillis=*/kCpuTime1));
                data->push_back(makeCpuActiveTimeAtom(/*uid=*/kUid2, /*timeMillis=*/kCpuTime2));
                return true;
            }));

    vector<std::shared_ptr<LogEvent>> pushedList = getPushedEvents();

    ShellSubscription config = getPulledConfig();
    config.add_pushed()->set_atom_id(SCREEN_STATE_CHANGED);

    vector<ShellData> expectedData;
    ShellData shellData1;
    shellData1.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    shellData1.add_elapsed_timestamp_nanos(pushedList[0]->GetElapsedTimestampNs());
    ShellData shellData2;
    shellData2.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    shellData2.add_elapsed_timestamp_nanos(pushedList[1]->GetElapsedTimestampNs());
    expectedData.push_back(getExpectedPulledData());
    expectedData.push_back(shellData1);
    expectedData.push_back(shellData2);

    // Test with single client
    TRACE_CALL(runShellTest, config, uidMap, pullerManager, pushedList, expectedData,
               kSingleClient);

    // Test with multiple client
    TRACE_CALL(runShellTest, config, uidMap, pullerManager, pushedList, expectedData, kNumClients);
}

TEST(ShellSubscriberTest, testMaxSizeGuard) {
    sp<MockUidMap> uidMap = new NaggyMock<MockUidMap>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    sp<ShellSubscriber> shellManager =
            new ShellSubscriber(uidMap, pullerManager, std::make_shared<LogEventFilter>());

    // set up 2 pipes for read/write config and data
    int fds_config[2];
    ASSERT_EQ(0, pipe2(fds_config, O_CLOEXEC));

    int fds_data[2];
    ASSERT_EQ(0, pipe2(fds_data, O_CLOEXEC));

    // write invalid size of the config
    size_t invalidBufferSize = (shellManager->getMaxSizeKb() * 1024) + 1;
    write(fds_config[1], &invalidBufferSize, sizeof(invalidBufferSize));
    close(fds_config[1]);
    close(fds_data[0]);

    EXPECT_FALSE(shellManager->startNewSubscription(fds_config[0], fds_data[1], /*timeoutSec=*/-1));
    close(fds_config[0]);
    close(fds_data[1]);
}

TEST(ShellSubscriberTest, testMaxSubscriptionsGuard) {
    sp<MockUidMap> uidMap = new NaggyMock<MockUidMap>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    sp<ShellSubscriber> shellManager =
            new ShellSubscriber(uidMap, pullerManager, std::make_shared<LogEventFilter>());

    // create a simple config to get screen events
    ShellSubscription config;
    config.add_pushed()->set_atom_id(SCREEN_STATE_CHANGED);

    size_t bufferSize = config.ByteSize();
    vector<uint8_t> buffer(bufferSize);
    config.SerializeToArray(&buffer[0], bufferSize);

    size_t maxSubs = shellManager->getMaxSubscriptions();
    int fds_configs[maxSubs + 1][2];
    int fds_datas[maxSubs + 1][2];
    for (int i = 0; i < maxSubs; i++) {
        // set up 2 pipes for read/write config and data
        ASSERT_EQ(0, pipe2(fds_configs[i], O_CLOEXEC));
        ASSERT_EQ(0, pipe2(fds_datas[i], O_CLOEXEC));

        // write the config to pipe, first write size of the config
        write(fds_configs[i][1], &bufferSize, sizeof(bufferSize));
        // then write config itself
        write(fds_configs[i][1], buffer.data(), bufferSize);
        close(fds_configs[i][1]);

        EXPECT_TRUE(shellManager->startNewSubscription(fds_configs[i][0], fds_datas[i][1],
                                                       /*timeoutSec=*/-1));
        close(fds_configs[i][0]);
        close(fds_datas[i][1]);
    }
    ASSERT_EQ(0, pipe2(fds_configs[maxSubs], O_CLOEXEC));
    ASSERT_EQ(0, pipe2(fds_datas[maxSubs], O_CLOEXEC));

    // write the config to pipe, first write size of the config
    write(fds_configs[maxSubs][1], &bufferSize, sizeof(bufferSize));
    // then write config itself
    write(fds_configs[maxSubs][1], buffer.data(), bufferSize);
    close(fds_configs[maxSubs][1]);

    EXPECT_FALSE(shellManager->startNewSubscription(fds_configs[maxSubs][0], fds_datas[maxSubs][1],
                                                    /*timeoutSec=*/-1));
    close(fds_configs[maxSubs][0]);
    close(fds_datas[maxSubs][1]);

    // Not closing fds_datas[i][0] because this causes writes within ShellSubscriberClient to hang
}

TEST(ShellSubscriberTest, testDifferentConfigs) {
    sp<MockUidMap> uidMap = new NaggyMock<MockUidMap>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();
    sp<ShellSubscriber> shellManager =
            new ShellSubscriber(uidMap, pullerManager, std::make_shared<LogEventFilter>());

    // number of different configs
    int numConfigs = 2;

    // create a simple config to get screen events
    ShellSubscription configs[numConfigs];
    configs[0].add_pushed()->set_atom_id(SCREEN_STATE_CHANGED);
    configs[1].add_pushed()->set_atom_id(PLUGGED_STATE_CHANGED);

    vector<vector<uint8_t>> configBuffers;
    for (int i = 0; i < numConfigs; i++) {
        size_t bufferSize = configs[i].ByteSize();
        vector<uint8_t> buffer(bufferSize);
        configs[i].SerializeToArray(&buffer[0], bufferSize);
        configBuffers.push_back(buffer);
    }

    int fds_configs[numConfigs][2];
    int fds_datas[numConfigs][2];
    for (int i = 0; i < numConfigs; i++) {
        // set up 2 pipes for read/write config and data
        ASSERT_EQ(0, pipe2(fds_configs[i], O_CLOEXEC));
        ASSERT_EQ(0, pipe2(fds_datas[i], O_CLOEXEC));

        size_t configSize = configBuffers[i].size();
        // write the config to pipe, first write size of the config
        write(fds_configs[i][1], &configSize, sizeof(configSize));
        // then write config itself
        write(fds_configs[i][1], configBuffers[i].data(), configSize);
        close(fds_configs[i][1]);

        EXPECT_TRUE(shellManager->startNewSubscription(fds_configs[i][0], fds_datas[i][1],
                                                       /*timeoutSec=*/-1));
        close(fds_configs[i][0]);
        close(fds_datas[i][1]);
    }

    // send a log event that matches the config.
    vector<std::shared_ptr<LogEvent>> pushedList = getPushedEvents();
    for (const auto& event : pushedList) {
        shellManager->onLogEvent(*event);
    }

    // Validate Config 1
    ShellData actual1 = readData(fds_datas[0][0]);
    ShellData expected1;
    expected1.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_ON);
    expected1.add_elapsed_timestamp_nanos(pushedList[0]->GetElapsedTimestampNs());
    EXPECT_THAT(expected1, EqShellData(actual1));

    ShellData actual2 = readData(fds_datas[0][0]);
    ShellData expected2;
    expected2.add_atom()->mutable_screen_state_changed()->set_state(
            ::android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
    expected2.add_elapsed_timestamp_nanos(pushedList[1]->GetElapsedTimestampNs());
    EXPECT_THAT(expected2, EqShellData(actual2));

    // Validate Config 2, repeating the process
    ShellData actual3 = readData(fds_datas[1][0]);
    ShellData expected3;
    expected3.add_atom()->mutable_plugged_state_changed()->set_state(
            BatteryPluggedStateEnum::BATTERY_PLUGGED_USB);
    expected3.add_elapsed_timestamp_nanos(pushedList[2]->GetElapsedTimestampNs());
    EXPECT_THAT(expected3, EqShellData(actual3));

    ShellData actual4 = readData(fds_datas[1][0]);
    ShellData expected4;
    expected4.add_atom()->mutable_plugged_state_changed()->set_state(
            BatteryPluggedStateEnum::BATTERY_PLUGGED_NONE);
    expected4.add_elapsed_timestamp_nanos(pushedList[3]->GetElapsedTimestampNs());
    EXPECT_THAT(expected4, EqShellData(actual4));

    // Not closing fds_datas[i][0] because this causes writes within ShellSubscriberClient to hang
}

TEST(ShellSubscriberTest, testPushedSubscriptionRestrictedEvent) {
    sp<MockUidMap> uidMap = new NaggyMock<MockUidMap>();
    sp<MockStatsPullerManager> pullerManager = new StrictMock<MockStatsPullerManager>();

    std::vector<shared_ptr<LogEvent>> pushedList;
    pushedList.push_back(CreateRestrictedLogEvent(/*atomTag=*/10, /*timestamp=*/1000));

    // create a simple config to get screen events
    ShellSubscription config;
    config.add_pushed()->set_atom_id(10);

    // expect empty data
    vector<ShellData> expectedData;

    // Test with single client
    TRACE_CALL(runShellTest, config, uidMap, pullerManager, pushedList, expectedData,
               kSingleClient);

    // Test with multiple client
    TRACE_CALL(runShellTest, config, uidMap, pullerManager, pushedList, expectedData, kNumClients);
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
