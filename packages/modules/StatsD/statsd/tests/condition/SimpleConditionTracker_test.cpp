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

#include "src/condition/SimpleConditionTracker.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include <numeric>
#include <vector>

#include "src/guardrail/StatsdStats.h"
#include "stats_event.h"
#include "tests/statsd_test_util.h"

using std::map;
using std::unordered_map;
using std::vector;

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {

const ConfigKey kConfigKey(0, 12345);

const int ATTRIBUTION_NODE_FIELD_ID = 1;
const int ATTRIBUTION_UID_FIELD_ID = 1;
const int TAG_ID = 1;
const uint64_t protoHash = 0x123456789;

SimplePredicate getWakeLockHeldCondition(bool countNesting,
                                         SimplePredicate_InitialValue initialValue,
                                         bool outputSlicedUid, Position position) {
    SimplePredicate simplePredicate;
    simplePredicate.set_start(StringToId("WAKE_LOCK_ACQUIRE"));
    simplePredicate.set_stop(StringToId("WAKE_LOCK_RELEASE"));
    simplePredicate.set_stop_all(StringToId("RELEASE_ALL"));
    if (outputSlicedUid) {
        simplePredicate.mutable_dimensions()->set_field(TAG_ID);
        simplePredicate.mutable_dimensions()->add_child()->set_field(ATTRIBUTION_NODE_FIELD_ID);
        simplePredicate.mutable_dimensions()->mutable_child(0)->set_position(position);
        simplePredicate.mutable_dimensions()->mutable_child(0)->add_child()->set_field(
            ATTRIBUTION_UID_FIELD_ID);
    }

    simplePredicate.set_count_nesting(countNesting);
    simplePredicate.set_initial_value(initialValue);
    return simplePredicate;
}

void makeWakeLockEvent(LogEvent* logEvent, const vector<int>& uids, const string& wl, int acquire) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, 1);
    AStatsEvent_overwriteTimestamp(statsEvent, 0);

    vector<std::string> tags(uids.size()); // vector of empty strings
    writeAttribution(statsEvent, uids, tags);

    AStatsEvent_writeString(statsEvent, wl.c_str());
    AStatsEvent_writeInt32(statsEvent, acquire);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

std::map<int64_t, HashableDimensionKey> getWakeLockQueryKey(
    const Position position,
    const std::vector<int> &uids, const string& conditionName) {
    std::map<int64_t, HashableDimensionKey> outputKeyMap;
    std::vector<int> uid_indexes;
    int pos[] = {1, 1, 1};
    int depth = 2;
    Field field(1, pos, depth);
    switch(position) {
        case Position::FIRST:
            uid_indexes.push_back(0);
            break;
        case Position::LAST:
            uid_indexes.push_back(uids.size() - 1);
            field.setField(0x02018001);
            break;
        case Position::ANY:
            uid_indexes.resize(uids.size());
            std::iota(uid_indexes.begin(), uid_indexes.end(), 0);
            field.setField(0x02010001);
            break;
        default:
            break;
    }

    for (const int idx : uid_indexes) {
        Value value((int32_t)uids[idx]);
        HashableDimensionKey dim;
        dim.addValue(FieldValue(field, value));
        outputKeyMap[StringToId(conditionName)] = dim;
    }
    return outputKeyMap;
}

class SimpleConditionTrackerTest : public testing::TestWithParam<SimplePredicate_InitialValue> {};

INSTANTIATE_TEST_SUITE_P(
        InitialValues, SimpleConditionTrackerTest,
        testing::Values(SimplePredicate_InitialValue_FALSE, SimplePredicate_InitialValue_UNKNOWN),
        [](const testing::TestParamInfo<SimpleConditionTrackerTest::ParamType>& info) {
            return SimplePredicate_InitialValue_Name(info.param);
        });
}  // anonymous namespace

TEST(SimpleConditionTrackerTest, TestNonSlicedInitialValueFalse) {
    SimplePredicate simplePredicate;
    simplePredicate.set_start(StringToId("SCREEN_TURNED_ON"));
    simplePredicate.set_stop(StringToId("SCREEN_TURNED_OFF"));
    simplePredicate.set_count_nesting(false);
    simplePredicate.set_initial_value(SimplePredicate_InitialValue_FALSE);

    unordered_map<int64_t, int> trackerNameIndexMap;
    trackerNameIndexMap[StringToId("SCREEN_TURNED_ON")] = 0;
    trackerNameIndexMap[StringToId("SCREEN_TURNED_OFF")] = 1;

    SimpleConditionTracker conditionTracker(kConfigKey, StringToId("SCREEN_IS_ON"), protoHash,
                                            0 /*tracker index*/, simplePredicate,
                                            trackerNameIndexMap);

    ConditionKey queryKey;
    vector<sp<ConditionTracker>> allPredicates;
    vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);

    // Check that initial condition is false.
    conditionTracker.isConditionMet(queryKey, allPredicates, false, conditionCache);
    EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);

    vector<MatchingState> matcherState;
    vector<uint8_t> changedCache(1, false);

    // Matched stop event.
    // Check that condition is still false.
    unique_ptr<LogEvent> screenOffEvent =
            CreateScreenStateChangedEvent(/*timestamp=*/50, android::view::DISPLAY_STATE_OFF);
    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);  // On matcher not matched
    matcherState.push_back(MatchingState::kMatched);     // Off matcher matched
    conditionCache[0] = ConditionState::kNotEvaluated;
    conditionTracker.evaluateCondition(*screenOffEvent, matcherState, allPredicates, conditionCache,
                                       changedCache);
    EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);
    EXPECT_FALSE(changedCache[0]);

    // Matched start event.
    // Check that condition has changed to true.
    unique_ptr<LogEvent> screenOnEvent =
            CreateScreenStateChangedEvent(/*timestamp=*/100, android::view::DISPLAY_STATE_ON);
    matcherState.clear();
    matcherState.push_back(MatchingState::kMatched);     // On matcher matched
    matcherState.push_back(MatchingState::kNotMatched);  // Off matcher not matched
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;
    conditionTracker.evaluateCondition(*screenOnEvent, matcherState, allPredicates, conditionCache,
                                       changedCache);
    EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);
    EXPECT_TRUE(changedCache[0]);
}

TEST(SimpleConditionTrackerTest, TestNonSlicedInitialValueUnknown) {
    SimplePredicate simplePredicate;
    simplePredicate.set_start(StringToId("SCREEN_TURNED_ON"));
    simplePredicate.set_stop(StringToId("SCREEN_TURNED_OFF"));
    simplePredicate.set_count_nesting(false);
    simplePredicate.set_initial_value(SimplePredicate_InitialValue_UNKNOWN);

    unordered_map<int64_t, int> trackerNameIndexMap;
    trackerNameIndexMap[StringToId("SCREEN_TURNED_ON")] = 0;
    trackerNameIndexMap[StringToId("SCREEN_TURNED_OFF")] = 1;

    SimpleConditionTracker conditionTracker(kConfigKey, StringToId("SCREEN_IS_ON"), protoHash,
                                            0 /*tracker index*/, simplePredicate,
                                            trackerNameIndexMap);

    ConditionKey queryKey;
    vector<sp<ConditionTracker>> allPredicates;
    vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);

    // Check that initial condition is unknown.
    conditionTracker.isConditionMet(queryKey, allPredicates, false, conditionCache);
    EXPECT_EQ(ConditionState::kUnknown, conditionCache[0]);

    vector<MatchingState> matcherState;
    vector<uint8_t> changedCache(1, false);

    // Matched stop event.
    // Check that condition is changed to false.
    unique_ptr<LogEvent> screenOffEvent =
            CreateScreenStateChangedEvent(/*timestamp=*/50, android::view::DISPLAY_STATE_OFF);
    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);  // On matcher not matched
    matcherState.push_back(MatchingState::kMatched);     // Off matcher matched
    conditionCache[0] = ConditionState::kNotEvaluated;
    conditionTracker.evaluateCondition(*screenOffEvent, matcherState, allPredicates, conditionCache,
                                       changedCache);
    EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);
    EXPECT_TRUE(changedCache[0]);

    // Matched start event.
    // Check that condition has changed to true.
    unique_ptr<LogEvent> screenOnEvent =
            CreateScreenStateChangedEvent(/*timestamp=*/100, android::view::DISPLAY_STATE_ON);
    matcherState.clear();
    matcherState.push_back(MatchingState::kMatched);     // On matcher matched
    matcherState.push_back(MatchingState::kNotMatched);  // Off matcher not matched
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;
    conditionTracker.evaluateCondition(*screenOnEvent, matcherState, allPredicates, conditionCache,
                                       changedCache);
    EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);
    EXPECT_TRUE(changedCache[0]);
}

TEST(SimpleConditionTrackerTest, TestNonSlicedCondition) {
    SimplePredicate simplePredicate;
    simplePredicate.set_start(StringToId("SCREEN_TURNED_ON"));
    simplePredicate.set_stop(StringToId("SCREEN_TURNED_OFF"));
    simplePredicate.set_count_nesting(false);
    simplePredicate.set_initial_value(SimplePredicate_InitialValue_UNKNOWN);

    unordered_map<int64_t, int> trackerNameIndexMap;
    trackerNameIndexMap[StringToId("SCREEN_TURNED_ON")] = 0;
    trackerNameIndexMap[StringToId("SCREEN_TURNED_OFF")] = 1;

    SimpleConditionTracker conditionTracker(kConfigKey, StringToId("SCREEN_IS_ON"), protoHash,
                                            0 /*tracker index*/, simplePredicate,
                                            trackerNameIndexMap);
    EXPECT_FALSE(conditionTracker.isSliced());

    // This event is not accessed in this test besides dimensions which is why this is okay.
    // This is technically an invalid LogEvent because we do not call parseBuffer.
    LogEvent event(/*uid=*/0, /*pid=*/0);

    vector<MatchingState> matcherState;
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kNotMatched);

    vector<sp<ConditionTracker>> allPredicates;
    vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);
    vector<uint8_t> changedCache(1, false);

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);
    // not matched start or stop. condition doesn't change
    EXPECT_EQ(ConditionState::kUnknown, conditionCache[0]);
    EXPECT_FALSE(changedCache[0]);

    // prepare a case for match start.
    matcherState.clear();
    matcherState.push_back(MatchingState::kMatched);
    matcherState.push_back(MatchingState::kNotMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);
    // now condition should change to true.
    EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);
    EXPECT_TRUE(changedCache[0]);

    // match nothing.
    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kNotMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);
    EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);
    EXPECT_FALSE(changedCache[0]);

    // the case for match stop.
    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);

    // condition changes to false.
    EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);
    EXPECT_TRUE(changedCache[0]);

    // match stop again.
    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);
    // condition should still be false. not changed.
    EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);
    EXPECT_FALSE(changedCache[0]);
}

TEST(SimpleConditionTrackerTest, TestNonSlicedConditionNestCounting) {
    std::vector<sp<ConditionTracker>> allConditions;
    SimplePredicate simplePredicate;
    simplePredicate.set_start(StringToId("SCREEN_TURNED_ON"));
    simplePredicate.set_stop(StringToId("SCREEN_TURNED_OFF"));
    simplePredicate.set_count_nesting(true);

    unordered_map<int64_t, int> trackerNameIndexMap;
    trackerNameIndexMap[StringToId("SCREEN_TURNED_ON")] = 0;
    trackerNameIndexMap[StringToId("SCREEN_TURNED_OFF")] = 1;

    SimpleConditionTracker conditionTracker(kConfigKey, StringToId("SCREEN_IS_ON"), protoHash,
                                            0 /*condition tracker index*/, simplePredicate,
                                            trackerNameIndexMap);
    EXPECT_FALSE(conditionTracker.isSliced());

    // This event is not accessed in this test besides dimensions which is why this is okay.
    // This is technically an invalid LogEvent because we do not call parseBuffer.
    LogEvent event(/*uid=*/0, /*pid=*/0);

    // one matched start
    vector<MatchingState> matcherState;
    matcherState.push_back(MatchingState::kMatched);
    matcherState.push_back(MatchingState::kNotMatched);
    vector<sp<ConditionTracker>> allPredicates;
    vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);
    vector<uint8_t> changedCache(1, false);

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);

    EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);
    EXPECT_TRUE(changedCache[0]);

    // prepare for another matched start.
    matcherState.clear();
    matcherState.push_back(MatchingState::kMatched);
    matcherState.push_back(MatchingState::kNotMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);

    EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);
    EXPECT_FALSE(changedCache[0]);

    // ONE MATCHED STOP
    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);
    // result should still be true
    EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);
    EXPECT_FALSE(changedCache[0]);

    // ANOTHER MATCHED STOP
    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;

    conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                       changedCache);
    EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);
    EXPECT_TRUE(changedCache[0]);
}

TEST_P(SimpleConditionTrackerTest, TestSlicedCondition) {
    std::vector<sp<ConditionTracker>> allConditions;
    for (Position position : {Position::FIRST, Position::LAST}) {
        SimplePredicate simplePredicate =
                getWakeLockHeldCondition(true /*nesting*/, GetParam() /*initialValue*/,
                                         true /*output slice by uid*/, position);
        string conditionName = "WL_HELD_BY_UID2";

        unordered_map<int64_t, int> trackerNameIndexMap;
        trackerNameIndexMap[StringToId("WAKE_LOCK_ACQUIRE")] = 0;
        trackerNameIndexMap[StringToId("WAKE_LOCK_RELEASE")] = 1;
        trackerNameIndexMap[StringToId("RELEASE_ALL")] = 2;

        SimpleConditionTracker conditionTracker(kConfigKey, StringToId(conditionName), protoHash,
                                                0 /*condition tracker index*/, simplePredicate,
                                                trackerNameIndexMap);

        std::vector<int> uids = {111, 222, 333};

        LogEvent event1(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event1, uids, "wl1", /*acquire=*/1);

        // one matched start
        vector<MatchingState> matcherState;
        matcherState.push_back(MatchingState::kMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        vector<sp<ConditionTracker>> allPredicates;
        vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);
        vector<uint8_t> changedCache(1, false);

        conditionTracker.evaluateCondition(event1, matcherState, allPredicates, conditionCache,
                                           changedCache);

        ASSERT_EQ(1UL, conditionTracker.mSlicedConditionState.size());
        EXPECT_TRUE(changedCache[0]);
        ASSERT_EQ(conditionTracker.getChangedToTrueDimensions(allConditions)->size(), 1u);
        EXPECT_TRUE(conditionTracker.getChangedToFalseDimensions(allConditions)->empty());

        // Now test query
        const auto queryKey = getWakeLockQueryKey(position, uids, conditionName);
        conditionCache[0] = ConditionState::kNotEvaluated;

        conditionTracker.isConditionMet(queryKey, allPredicates, false, conditionCache);
        EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);

        // another wake lock acquired by this uid
        LogEvent event2(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event2, uids, "wl2", /*acquire=*/1);
        matcherState.clear();
        matcherState.push_back(MatchingState::kMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        conditionCache[0] = ConditionState::kNotEvaluated;
        changedCache[0] = false;
        conditionTracker.evaluateCondition(event2, matcherState, allPredicates, conditionCache,
                                           changedCache);
        EXPECT_FALSE(changedCache[0]);
        ASSERT_EQ(1UL, conditionTracker.mSlicedConditionState.size());
        EXPECT_TRUE(conditionTracker.getChangedToTrueDimensions(allConditions)->empty());
        EXPECT_TRUE(conditionTracker.getChangedToFalseDimensions(allConditions)->empty());

        // wake lock 1 release
        LogEvent event3(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event3, uids, "wl1", /*acquire=*/0);
        matcherState.clear();
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kMatched);
        conditionCache[0] = ConditionState::kNotEvaluated;
        changedCache[0] = false;
        conditionTracker.evaluateCondition(event3, matcherState, allPredicates, conditionCache,
                                           changedCache);
        // nothing changes, because wake lock 2 is still held for this uid
        EXPECT_FALSE(changedCache[0]);
        ASSERT_EQ(1UL, conditionTracker.mSlicedConditionState.size());
        EXPECT_TRUE(conditionTracker.getChangedToTrueDimensions(allConditions)->empty());
        EXPECT_TRUE(conditionTracker.getChangedToFalseDimensions(allConditions)->empty());

        LogEvent event4(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event4, uids, "wl2", /*acquire=*/0);
        matcherState.clear();
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kMatched);
        conditionCache[0] = ConditionState::kNotEvaluated;
        changedCache[0] = false;
        conditionTracker.evaluateCondition(event4, matcherState, allPredicates, conditionCache,
                                           changedCache);

        ASSERT_EQ(conditionTracker.mSlicedConditionState.size(),
                  GetParam() == SimplePredicate_InitialValue_FALSE ? 0 : 1);
        EXPECT_TRUE(changedCache[0]);
        ASSERT_EQ(conditionTracker.getChangedToFalseDimensions(allConditions)->size(), 1u);
        EXPECT_TRUE(conditionTracker.getChangedToTrueDimensions(allConditions)->empty());

        // query again
        conditionCache[0] = ConditionState::kNotEvaluated;
        conditionTracker.isConditionMet(queryKey, allPredicates, false, conditionCache);
        EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);
    }
}

TEST(SimpleConditionTrackerTest, TestSlicedWithNoOutputDim) {
    std::vector<sp<ConditionTracker>> allConditions;

    SimplePredicate simplePredicate =
            getWakeLockHeldCondition(true /*nesting*/, SimplePredicate_InitialValue_FALSE,
                                     false /*slice output by uid*/, Position::ANY /* position */);
    string conditionName = "WL_HELD";

    unordered_map<int64_t, int> trackerNameIndexMap;
    trackerNameIndexMap[StringToId("WAKE_LOCK_ACQUIRE")] = 0;
    trackerNameIndexMap[StringToId("WAKE_LOCK_RELEASE")] = 1;
    trackerNameIndexMap[StringToId("RELEASE_ALL")] = 2;

    SimpleConditionTracker conditionTracker(kConfigKey, StringToId(conditionName), protoHash,
                                            0 /*condition tracker index*/, simplePredicate,
                                            trackerNameIndexMap);

    EXPECT_FALSE(conditionTracker.isSliced());

    std::vector<int> uids1 = {111, 1111, 11111};
    string uid1_wl1 = "wl1_1";
    std::vector<int> uids2 = {222, 2222, 22222};
    string uid2_wl1 = "wl2_1";

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeWakeLockEvent(&event1, uids1, uid1_wl1, /*acquire=*/1);

    // one matched start for uid1
    vector<MatchingState> matcherState;
    matcherState.push_back(MatchingState::kMatched);
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kNotMatched);
    vector<sp<ConditionTracker>> allPredicates;
    vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);
    vector<uint8_t> changedCache(1, false);

    conditionTracker.evaluateCondition(event1, matcherState, allPredicates, conditionCache,
                                       changedCache);

    ASSERT_EQ(1UL, conditionTracker.mSlicedConditionState.size());
    EXPECT_TRUE(changedCache[0]);

    // Now test query
    ConditionKey queryKey;
    conditionCache[0] = ConditionState::kNotEvaluated;

    conditionTracker.isConditionMet(queryKey, allPredicates, true, conditionCache);
    EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);

    // another wake lock acquired by a different uid
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeWakeLockEvent(&event2, uids2, uid2_wl1, /*acquire=*/1);

    matcherState.clear();
    matcherState.push_back(MatchingState::kMatched);
    matcherState.push_back(MatchingState::kNotMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;
    conditionTracker.evaluateCondition(event2, matcherState, allPredicates, conditionCache,
                                       changedCache);
    EXPECT_FALSE(changedCache[0]);

    // uid1 wake lock 1 release
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    makeWakeLockEvent(&event3, uids1, uid1_wl1,
                      /*release=*/0);  // now release it.

    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;
    conditionTracker.evaluateCondition(event3, matcherState, allPredicates, conditionCache,
                                       changedCache);
    // nothing changes, because uid2 is still holding wl.
    EXPECT_FALSE(changedCache[0]);

    LogEvent event4(/*uid=*/0, /*pid=*/0);
    makeWakeLockEvent(&event4, uids2, uid2_wl1,
                      /*acquire=*/0);  // now release it.
    matcherState.clear();
    matcherState.push_back(MatchingState::kNotMatched);
    matcherState.push_back(MatchingState::kMatched);
    conditionCache[0] = ConditionState::kNotEvaluated;
    changedCache[0] = false;
    conditionTracker.evaluateCondition(event4, matcherState, allPredicates, conditionCache,
                                       changedCache);
    ASSERT_EQ(0UL, conditionTracker.mSlicedConditionState.size());
    EXPECT_TRUE(changedCache[0]);

    // query again
    conditionCache[0] = ConditionState::kNotEvaluated;
    conditionTracker.isConditionMet(queryKey, allPredicates, true, conditionCache);
    EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);
}

TEST_P(SimpleConditionTrackerTest, TestStopAll) {
    std::vector<sp<ConditionTracker>> allConditions;
    for (Position position : {Position::FIRST, Position::LAST}) {
        SimplePredicate simplePredicate =
                getWakeLockHeldCondition(true /*nesting*/, GetParam() /*initialValue*/,
                                         true /*output slice by uid*/, position);
        string conditionName = "WL_HELD_BY_UID3";

        unordered_map<int64_t, int> trackerNameIndexMap;
        trackerNameIndexMap[StringToId("WAKE_LOCK_ACQUIRE")] = 0;
        trackerNameIndexMap[StringToId("WAKE_LOCK_RELEASE")] = 1;
        trackerNameIndexMap[StringToId("RELEASE_ALL")] = 2;

        SimpleConditionTracker conditionTracker(kConfigKey, StringToId(conditionName), protoHash,
                                                0 /*condition tracker index*/, simplePredicate,
                                                trackerNameIndexMap);

        std::vector<int> uids1 = {111, 1111, 11111};
        std::vector<int> uids2 = {222, 2222, 22222};

        LogEvent event1(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event1, uids1, "wl1", /*acquire=*/1);

        // one matched start
        vector<MatchingState> matcherState;
        matcherState.push_back(MatchingState::kMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        vector<sp<ConditionTracker>> allPredicates;
        vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);
        vector<uint8_t> changedCache(1, false);

        conditionTracker.evaluateCondition(event1, matcherState, allPredicates, conditionCache,
                                           changedCache);
        ASSERT_EQ(1UL, conditionTracker.mSlicedConditionState.size());
        EXPECT_TRUE(changedCache[0]);
        ASSERT_EQ(1UL, conditionTracker.getChangedToTrueDimensions(allConditions)->size());
        EXPECT_TRUE(conditionTracker.getChangedToFalseDimensions(allConditions)->empty());

        // Now test query
        const auto queryKey = getWakeLockQueryKey(position, uids1, conditionName);
        conditionCache[0] = ConditionState::kNotEvaluated;

        conditionTracker.isConditionMet(queryKey, allPredicates, false, conditionCache);
        EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);

        // another wake lock acquired by uid2
        LogEvent event2(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event2, uids2, "wl2", /*acquire=*/1);

        matcherState.clear();
        matcherState.push_back(MatchingState::kMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        conditionCache[0] = ConditionState::kNotEvaluated;
        changedCache[0] = false;
        conditionTracker.evaluateCondition(event2, matcherState, allPredicates, conditionCache,
                                           changedCache);
        ASSERT_EQ(2UL, conditionTracker.mSlicedConditionState.size());

        EXPECT_TRUE(changedCache[0]);
        ASSERT_EQ(1UL, conditionTracker.getChangedToTrueDimensions(allConditions)->size());
        EXPECT_TRUE(conditionTracker.getChangedToFalseDimensions(allConditions)->empty());

        // TEST QUERY
        const auto queryKey2 = getWakeLockQueryKey(position, uids2, conditionName);
        conditionCache[0] = ConditionState::kNotEvaluated;
        conditionTracker.isConditionMet(queryKey, allPredicates, false, conditionCache);

        EXPECT_EQ(ConditionState::kTrue, conditionCache[0]);

        // stop all event
        LogEvent event3(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event3, uids2, "wl2", /*acquire=*/1);

        matcherState.clear();
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kMatched);

        conditionCache[0] = ConditionState::kNotEvaluated;
        changedCache[0] = false;
        conditionTracker.evaluateCondition(event3, matcherState, allPredicates, conditionCache,
                                           changedCache);
        EXPECT_TRUE(changedCache[0]);
        ASSERT_EQ(0UL, conditionTracker.mSlicedConditionState.size());
        ASSERT_EQ(2UL, conditionTracker.getChangedToFalseDimensions(allConditions)->size());
        EXPECT_TRUE(conditionTracker.getChangedToTrueDimensions(allConditions)->empty());

        // TEST QUERY
        const auto queryKey3 = getWakeLockQueryKey(position, uids1, conditionName);
        conditionCache[0] = ConditionState::kNotEvaluated;
        conditionTracker.isConditionMet(queryKey, allPredicates, false, conditionCache);
        EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);

        // TEST QUERY
        const auto queryKey4 = getWakeLockQueryKey(position, uids2, conditionName);
        conditionCache[0] = ConditionState::kNotEvaluated;
        conditionTracker.isConditionMet(queryKey, allPredicates, false, conditionCache);
        EXPECT_EQ(ConditionState::kFalse, conditionCache[0]);
    }
}

TEST(SimpleConditionTrackerTest, TestGuardrailNotHitWhenDefaultFalse) {
    std::vector<sp<ConditionTracker>> allConditions;
    SimplePredicate simplePredicate =
            getWakeLockHeldCondition(true /*nesting*/, SimplePredicate_InitialValue_FALSE,
                                     true /*output slice by uid*/, Position::FIRST);
    string conditionName = "WL_HELD_BY_UID";

    unordered_map<int64_t, int> trackerNameIndexMap;
    trackerNameIndexMap[StringToId("WAKE_LOCK_ACQUIRE")] = 0;
    trackerNameIndexMap[StringToId("WAKE_LOCK_RELEASE")] = 1;
    trackerNameIndexMap[StringToId("RELEASE_ALL")] = 2;

    SimpleConditionTracker conditionTracker(kConfigKey, StringToId(conditionName), protoHash,
                                            0 /*condition tracker index*/, simplePredicate,
                                            trackerNameIndexMap);
    for (int i = 0; i < StatsdStats::kDimensionKeySizeHardLimit + 1; i++) {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event, /*uids=*/{i}, "wl", /*acquire=*/1);

        // acquire, followed by release.
        vector<MatchingState> matcherState;
        matcherState.push_back(MatchingState::kMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        vector<sp<ConditionTracker>> allPredicates;
        vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);
        vector<uint8_t> changedCache(1, false);

        conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                           changedCache);

        ASSERT_EQ(1UL, conditionTracker.mSlicedConditionState.size());

        LogEvent event2(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event2, /*uids=*/{i}, "wl", /*acquire=*/0);
        matcherState.clear();
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kMatched);
        conditionCache[0] = ConditionState::kNotEvaluated;
        changedCache[0] = false;
        conditionTracker.evaluateCondition(event2, matcherState, allPredicates, conditionCache,
                                           changedCache);
        // wakelock is now released, key is cleared from map since the default value is false.
        ASSERT_EQ(0UL, conditionTracker.mSlicedConditionState.size());
    }
}

TEST(SimpleConditionTrackerTest, TestGuardrailHitWhenDefaultUnknown) {
    std::vector<sp<ConditionTracker>> allConditions;
    SimplePredicate simplePredicate =
            getWakeLockHeldCondition(true /*nesting*/, SimplePredicate_InitialValue_UNKNOWN,
                                     true /*output slice by uid*/, Position::FIRST);
    string conditionName = "WL_HELD_BY_UID";

    unordered_map<int64_t, int> trackerNameIndexMap;
    trackerNameIndexMap[StringToId("WAKE_LOCK_ACQUIRE")] = 0;
    trackerNameIndexMap[StringToId("WAKE_LOCK_RELEASE")] = 1;
    trackerNameIndexMap[StringToId("RELEASE_ALL")] = 2;

    SimpleConditionTracker conditionTracker(kConfigKey, StringToId(conditionName), protoHash,
                                            0 /*condition tracker index*/, simplePredicate,
                                            trackerNameIndexMap);
    int i;
    for (i = 0; i < StatsdStats::kDimensionKeySizeHardLimit; i++) {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event, /*uids=*/{i}, "wl", /*acquire=*/1);

        // acquire, followed by release.
        vector<MatchingState> matcherState;
        matcherState.push_back(MatchingState::kMatched);
        matcherState.push_back(MatchingState::kNotMatched);
        vector<sp<ConditionTracker>> allPredicates;
        vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);
        vector<uint8_t> changedCache(1, false);

        conditionTracker.evaluateCondition(event, matcherState, allPredicates, conditionCache,
                                           changedCache);

        ASSERT_EQ(i + 1, conditionTracker.mSlicedConditionState.size());

        LogEvent event2(/*uid=*/0, /*pid=*/0);
        makeWakeLockEvent(&event2, /*uids=*/{i}, "wl", /*acquire=*/0);
        matcherState.clear();
        matcherState.push_back(MatchingState::kNotMatched);
        matcherState.push_back(MatchingState::kMatched);
        conditionCache[0] = ConditionState::kNotEvaluated;
        changedCache[0] = false;
        conditionTracker.evaluateCondition(event2, matcherState, allPredicates, conditionCache,
                                           changedCache);
        // wakelock is now released, key is not cleared from map since the default value is unknown.
        ASSERT_EQ(i + 1, conditionTracker.mSlicedConditionState.size());
    }

    ASSERT_EQ(StatsdStats::kDimensionKeySizeHardLimit,
              conditionTracker.mSlicedConditionState.size());
    // one more acquire after the guardrail is hit.
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    makeWakeLockEvent(&event3, /*uids=*/{i}, "wl", /*acquire=*/1);
    vector<MatchingState> matcherState;
    matcherState.push_back(MatchingState::kMatched);
    matcherState.push_back(MatchingState::kNotMatched);
    vector<sp<ConditionTracker>> allPredicates;
    vector<ConditionState> conditionCache(1, ConditionState::kNotEvaluated);
    vector<uint8_t> changedCache(1, false);

    conditionTracker.evaluateCondition(event3, matcherState, allPredicates, conditionCache,
                                       changedCache);

    ASSERT_EQ(StatsdStats::kDimensionKeySizeHardLimit,
              conditionTracker.mSlicedConditionState.size());
    EXPECT_EQ(conditionCache[0], ConditionState::kUnknown);
}
}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
