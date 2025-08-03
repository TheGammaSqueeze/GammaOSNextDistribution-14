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

#include "src/anomaly/AlarmTracker.h"

#include <gtest/gtest.h>
#include <log/log_time.h>
#include <stdio.h>

#include <vector>

#include "src/subscriber/SubscriberReporter.h"
#include "tests/statsd_test_util.h"

using namespace testing;
using android::sp;
using std::set;
using std::shared_ptr;
using std::unordered_map;
using std::vector;

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {
const int kConfigUid = 0;
const int kConfigId = 12345;
const ConfigKey kConfigKey(kConfigUid, kConfigId);
}  // anonymous namespace

TEST(AlarmTrackerTest, TestTriggerTimestamp) {
    sp<AlarmMonitor> subscriberAlarmMonitor =
        new AlarmMonitor(100,
                         [](const shared_ptr<IStatsCompanionService>&, int64_t){},
                         [](const shared_ptr<IStatsCompanionService>&){});
    Alarm alarm;
    alarm.set_offset_millis(15 * MS_PER_SEC);
    alarm.set_period_millis(60 * 60 * MS_PER_SEC);  // 1hr
    int64_t startMillis = 100000000 * MS_PER_SEC;
    int64_t nextAlarmTime = startMillis / MS_PER_SEC + 15;
    AlarmTracker tracker(startMillis, startMillis, alarm, kConfigKey, subscriberAlarmMonitor);

    EXPECT_EQ(tracker.mAlarmSec, nextAlarmTime);

    uint64_t currentTimeSec = startMillis / MS_PER_SEC + 10;
    std::unordered_set<sp<const InternalAlarm>, SpHash<InternalAlarm>> firedAlarmSet =
        subscriberAlarmMonitor->popSoonerThan(static_cast<uint32_t>(currentTimeSec));
    EXPECT_TRUE(firedAlarmSet.empty());
    tracker.informAlarmsFired(currentTimeSec * NS_PER_SEC, firedAlarmSet);
    EXPECT_EQ(tracker.mAlarmSec, nextAlarmTime);
    EXPECT_EQ(tracker.getAlarmTimestampSec(), nextAlarmTime);

    currentTimeSec = startMillis / MS_PER_SEC + 7000;
    nextAlarmTime = startMillis / MS_PER_SEC + 15 + 2 * 60 * 60;
    firedAlarmSet = subscriberAlarmMonitor->popSoonerThan(static_cast<uint32_t>(currentTimeSec));
    ASSERT_EQ(firedAlarmSet.size(), 1u);
    tracker.informAlarmsFired(currentTimeSec * NS_PER_SEC, firedAlarmSet);
    EXPECT_TRUE(firedAlarmSet.empty());
    EXPECT_EQ(tracker.mAlarmSec, nextAlarmTime);
    EXPECT_EQ(tracker.getAlarmTimestampSec(), nextAlarmTime);

    // Alarm fires exactly on time.
    currentTimeSec = startMillis / MS_PER_SEC + 15 + 2 * 60 * 60;
    nextAlarmTime = startMillis / MS_PER_SEC + 15 + 3 * 60 * 60;
    firedAlarmSet = subscriberAlarmMonitor->popSoonerThan(static_cast<uint32_t>(currentTimeSec));
    ASSERT_EQ(firedAlarmSet.size(), 1u);
    tracker.informAlarmsFired(currentTimeSec * NS_PER_SEC, firedAlarmSet);
    EXPECT_TRUE(firedAlarmSet.empty());
    EXPECT_EQ(tracker.mAlarmSec, nextAlarmTime);
    EXPECT_EQ(tracker.getAlarmTimestampSec(), nextAlarmTime);

    // Alarm fires exactly 1 period late.
    currentTimeSec = startMillis / MS_PER_SEC + 15 + 4 * 60 * 60;
    nextAlarmTime = startMillis / MS_PER_SEC + 15 + 5 * 60 * 60;
    firedAlarmSet = subscriberAlarmMonitor->popSoonerThan(static_cast<uint32_t>(currentTimeSec));
    ASSERT_EQ(firedAlarmSet.size(), 1u);
    tracker.informAlarmsFired(currentTimeSec * NS_PER_SEC, firedAlarmSet);
    EXPECT_TRUE(firedAlarmSet.empty());
    EXPECT_EQ(tracker.mAlarmSec, nextAlarmTime);
    EXPECT_EQ(tracker.getAlarmTimestampSec(), nextAlarmTime);
}

TEST(AlarmTrackerTest, TestProbabilityOfInforming) {
    // Initiating StatsdStats at the start of this test, so it doesn't call rand() during the test
    StatsdStats::getInstance();
    srand(/*commonly used seed=*/0);
    sp<AlarmMonitor> subscriberAlarmMonitor = new AlarmMonitor(
            100, [](const shared_ptr<IStatsCompanionService>&, int64_t) {},
            [](const shared_ptr<IStatsCompanionService>&) {});
    int broadcastSubRandId = 1, broadcastSubAlwaysId = 2, broadcastSubNeverId = 3;

    int64_t startMillis = 100000000 * MS_PER_SEC;
    uint64_t currentTimeSec = startMillis / MS_PER_SEC + 15 + 60 * 60;

    // Alarm with probability of informing set to 0.5
    Alarm alarmRand = createAlarm("alarmRand", /*offsetMillis=*/15 * MS_PER_SEC,
                                  /*periodMillis=*/60 * 60 * MS_PER_SEC);
    alarmRand.set_probability_of_informing(0.5);
    AlarmTracker trackerRand(startMillis, startMillis, alarmRand, kConfigKey,
                             subscriberAlarmMonitor);
    Subscription subRand = createSubscription("subRand", /*rule_type=*/Subscription::ALARM,
                                              /*rule_id=*/alarmRand.id());
    subRand.mutable_broadcast_subscriber_details()->set_subscriber_id(broadcastSubRandId);
    trackerRand.addSubscription(subRand);

    // Alarm with probability of informing set to 1.1 (always; set by default)
    Alarm alarmAlways = createAlarm("alarmAlways", /*offsetMillis=*/15 * MS_PER_SEC,
                                    /*periodMillis=*/60 * 60 * MS_PER_SEC);
    AlarmTracker trackerAlways(startMillis, startMillis, alarmAlways, kConfigKey,
                               subscriberAlarmMonitor);
    Subscription subAlways = createSubscription("subAlways", /*rule_type=*/Subscription::ALARM,
                                                /*rule_id=*/alarmAlways.id());
    subAlways.mutable_broadcast_subscriber_details()->set_subscriber_id(broadcastSubAlwaysId);
    trackerAlways.addSubscription(subAlways);

    // Alarm with probability of informing set to -0.1 (never)
    Alarm alarmNever = createAlarm("alarmNever", /*offsetMillis=*/15 * MS_PER_SEC,
                                   /*periodMillis=*/60 * 60 * MS_PER_SEC);
    alarmNever.set_probability_of_informing(-0.1);
    AlarmTracker trackerNever(startMillis, startMillis, alarmNever, kConfigKey,
                              subscriberAlarmMonitor);
    Subscription subNever = createSubscription("subNever", /*rule_type=*/Subscription::ALARM,
                                               /*rule_id=*/alarmNever.id());
    subNever.mutable_broadcast_subscriber_details()->set_subscriber_id(broadcastSubNeverId);
    trackerNever.addSubscription(subNever);

    std::unordered_set<sp<const InternalAlarm>, SpHash<InternalAlarm>> firedAlarmSet =
            subscriberAlarmMonitor->popSoonerThan(static_cast<uint32_t>(currentTimeSec));
    ASSERT_EQ(firedAlarmSet.size(), 3u);

    int alarmRandCount = 0, alarmAlwaysCount = 0;
    // The binder calls here will happen synchronously because they are in-process.
    shared_ptr<MockPendingIntentRef> randBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*randBroadcast,
                sendSubscriberBroadcast(kConfigUid, kConfigId, subRand.id(), alarmRand.id(), _, _))
            .Times(3)
            .WillRepeatedly([&alarmRandCount] {
                alarmRandCount++;
                return Status::ok();
            });

    shared_ptr<MockPendingIntentRef> alwaysBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*alwaysBroadcast, sendSubscriberBroadcast(kConfigUid, kConfigId, subAlways.id(),
                                                          alarmAlways.id(), _, _))
            .Times(10)
            .WillRepeatedly([&alarmAlwaysCount] {
                alarmAlwaysCount++;
                return Status::ok();
            });

    shared_ptr<MockPendingIntentRef> neverBroadcast =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*neverBroadcast, sendSubscriberBroadcast(kConfigUid, kConfigId, subNever.id(),
                                                         alarmNever.id(), _, _))
            .Times(0);

    SubscriberReporter::getInstance().setBroadcastSubscriber(kConfigKey, broadcastSubRandId,
                                                             randBroadcast);
    SubscriberReporter::getInstance().setBroadcastSubscriber(kConfigKey, broadcastSubAlwaysId,
                                                             alwaysBroadcast);
    SubscriberReporter::getInstance().setBroadcastSubscriber(kConfigKey, broadcastSubNeverId,
                                                             neverBroadcast);
    // Trying to inform the subscription 10x.
    // Deterministic sequence for trackerRand:
    // 0.96, 0.95, 0.95, 0.94, 0.43, 0.92, 0.92, 0.41, 0.39, 0.88
    for (size_t i = 0; i < 10; i++) {
        trackerRand.informAlarmsFired(currentTimeSec * NS_PER_SEC, firedAlarmSet);
        if (i <= 3) {
            EXPECT_EQ(alarmRandCount, 0);
        } else if (i >= 4 && i <= 6) {
            EXPECT_EQ(alarmRandCount, 1);
        } else if (i == 7) {
            EXPECT_EQ(alarmRandCount, 2);
        } else {
            EXPECT_EQ(alarmRandCount, 3);
        }
        trackerAlways.informAlarmsFired(currentTimeSec * NS_PER_SEC, firedAlarmSet);
        EXPECT_EQ(alarmAlwaysCount, i + 1);
        trackerNever.informAlarmsFired(currentTimeSec * NS_PER_SEC, firedAlarmSet);

        currentTimeSec = startMillis / MS_PER_SEC + 15 + (i + 2) * 60 * 60;
        firedAlarmSet =
                subscriberAlarmMonitor->popSoonerThan(static_cast<uint32_t>(currentTimeSec));
    }
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(kConfigKey, broadcastSubRandId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(kConfigKey, broadcastSubAlwaysId);
    SubscriberReporter::getInstance().unsetBroadcastSubscriber(kConfigKey, broadcastSubNeverId);
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
