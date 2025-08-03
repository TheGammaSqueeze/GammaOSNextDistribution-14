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

#include <gtest/gtest.h>
#include <stdio.h>

#include "matchers/matcher_util.h"
#include "src/statsd_config.pb.h"
#include "stats_annotations.h"
#include "stats_event.h"
#include "stats_log_util.h"
#include "stats_util.h"
#include "statsd_test_util.h"

using namespace android::os::statsd;
using std::shared_ptr;
using std::unordered_map;
using std::vector;

const int32_t TAG_ID = 123;
const int32_t TAG_ID_2 = 28;  // hardcoded tag of atom with uid field
const int FIELD_ID_1 = 1;
const int FIELD_ID_2 = 2;
const int FIELD_ID_3 = 3;

const int ATTRIBUTION_UID_FIELD_ID = 1;
const int ATTRIBUTION_TAG_FIELD_ID = 2;


#ifdef __ANDROID__

namespace {

void makeIntLogEvent(LogEvent* logEvent, const int32_t atomId, const int64_t timestamp,
                     const int32_t value) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, timestamp);
    AStatsEvent_writeInt32(statsEvent, value);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

void makeFloatLogEvent(LogEvent* logEvent, const int32_t atomId, const int64_t timestamp,
                       const float floatValue) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, timestamp);
    AStatsEvent_writeFloat(statsEvent, floatValue);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

void makeStringLogEvent(LogEvent* logEvent, const int32_t atomId, const int64_t timestamp,
                        const string& name) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, timestamp);
    AStatsEvent_writeString(statsEvent, name.c_str());

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

void makeIntWithBoolAnnotationLogEvent(LogEvent* logEvent, const int32_t atomId,
                                       const int32_t field, const uint8_t annotationId,
                                       const bool annotationValue) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_writeInt32(statsEvent, field);
    AStatsEvent_addBoolAnnotation(statsEvent, annotationId, annotationValue);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

void makeAttributionLogEvent(LogEvent* logEvent, const int32_t atomId, const int64_t timestamp,
                             const vector<int>& attributionUids,
                             const vector<string>& attributionTags, const string& name) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, timestamp);

    writeAttribution(statsEvent, attributionUids, attributionTags);
    AStatsEvent_writeString(statsEvent, name.c_str());

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

void makeBoolLogEvent(LogEvent* logEvent, const int32_t atomId, const int64_t timestamp,
                      const bool bool1, const bool bool2) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_overwriteTimestamp(statsEvent, timestamp);

    AStatsEvent_writeBool(statsEvent, bool1);
    AStatsEvent_writeBool(statsEvent, bool2);

    parseStatsEventToLogEvent(statsEvent, logEvent);
}

void makeRepeatedIntLogEvent(LogEvent* logEvent, const int32_t atomId,
                             const vector<int>& intArray) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_writeInt32Array(statsEvent, intArray.data(), intArray.size());
    parseStatsEventToLogEvent(statsEvent, logEvent);
}

void makeRepeatedUidLogEvent(LogEvent* logEvent, const int32_t atomId,
                             const vector<int>& intArray) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_writeInt32Array(statsEvent, intArray.data(), intArray.size());
    AStatsEvent_addBoolAnnotation(statsEvent, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    parseStatsEventToLogEvent(statsEvent, logEvent);
}

void makeRepeatedStringLogEvent(LogEvent* logEvent, const int32_t atomId,
                                const vector<string>& stringArray) {
    vector<const char*> cStringArray(stringArray.size());
    for (int i = 0; i < cStringArray.size(); i++) {
        cStringArray[i] = stringArray[i].c_str();
    }

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomId);
    AStatsEvent_writeStringArray(statsEvent, cStringArray.data(), stringArray.size());
    parseStatsEventToLogEvent(statsEvent, logEvent);
}

}  // anonymous namespace

TEST(AtomMatcherTest, TestSimpleMatcher) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeIntLogEvent(&event, TAG_ID, 0, 11);

    // Test
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Wrong tag id.
    simpleMatcher->set_atom_id(TAG_ID + 1);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestAttributionMatcher) {
    sp<UidMap> uidMap = new UidMap();
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};

    // Set up the log event.
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value");

    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    // Match first node.
    auto attributionMatcher = simpleMatcher->add_field_value_matcher();
    attributionMatcher->set_field(FIELD_ID_1);
    attributionMatcher->set_position(Position::FIRST);
    attributionMatcher->mutable_matches_tuple()->add_field_value_matcher()->set_field(
            ATTRIBUTION_TAG_FIELD_ID);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "tag");

    auto fieldMatcher = simpleMatcher->add_field_value_matcher();
    fieldMatcher->set_field(FIELD_ID_2);
    fieldMatcher->set_eq_string("some value");

    // Tag not matched.
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "location3");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "location1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match last node.
    attributionMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "location3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match any node.
    attributionMatcher->set_position(Position::ANY);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "location1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "location2");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "location3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "location4");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Attribution match but primitive field not match.
    attributionMatcher->set_position(Position::ANY);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "location2");
    fieldMatcher->set_eq_string("wrong value");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldMatcher->set_eq_string("some value");

    // Uid match.
    attributionMatcher->set_position(Position::ANY);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_field(
            ATTRIBUTION_UID_FIELD_ID);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg0");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg0");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 2222, /*version*/ 2, "v2", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 1, "v1", "Pkg2");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 2, "v2", "PkG3");

    uidMap->updateMap(1, uidData);

    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "Pkg2");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg0");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    attributionMatcher->set_position(Position::FIRST);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg0");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "Pkg2");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    attributionMatcher->set_position(Position::LAST);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg0");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "Pkg2");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Uid + tag.
    attributionMatcher->set_position(Position::ANY);
    attributionMatcher->mutable_matches_tuple()->add_field_value_matcher()->set_field(
            ATTRIBUTION_TAG_FIELD_ID);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg0");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location2");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "Pkg2");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    attributionMatcher->set_position(Position::FIRST);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg0");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location2");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "Pkg2");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location3");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location3");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    attributionMatcher->set_position(Position::LAST);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg0");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "pkg1");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location2");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "Pkg2");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string(
            "PkG3");
    attributionMatcher->mutable_matches_tuple()->mutable_field_value_matcher(1)->set_eq_string(
            "location1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestUidFieldMatcher) {
    sp<UidMap> uidMap = new UidMap();

    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg0");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 2222, /*version*/ 2, "v2", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 1, "v1", "Pkg2");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 2, "v2", "PkG3");

    uidMap->updateMap(1, uidData);

    // Set up matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    simpleMatcher->add_field_value_matcher()->set_field(1);
    simpleMatcher->mutable_field_value_matcher(0)->set_eq_string("pkg0");

    // Make event without is_uid annotation.
    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeIntLogEvent(&event1, TAG_ID, 0, 1111);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    // Make event with is_uid annotation.
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeIntWithBoolAnnotationLogEvent(&event2, TAG_ID_2, 1111, ASTATSLOG_ANNOTATION_ID_IS_UID,
                                      true);

    // Event has is_uid annotation, so mapping from uid to package name occurs.
    simpleMatcher->set_atom_id(TAG_ID_2);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);

    // Event has is_uid annotation, but uid maps to different package name.
    simpleMatcher->mutable_field_value_matcher(0)->set_eq_string("Pkg2");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event2).matched);
}

TEST(AtomMatcherTest, TestRepeatedUidFieldMatcher) {
    sp<UidMap> uidMap = new UidMap();

    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg0");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 2222, /*version*/ 2, "v2", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 1, "v1", "Pkg2");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 2, "v2", "PkG3");

    uidMap->updateMap(1, uidData);

    // Set up matcher.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);

    // No is_uid annotation, no mapping from uid to package name.
    vector<int> intArray = {1111, 3333, 2222};
    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeRepeatedIntLogEvent(&event1, TAG_ID, intArray);

    fieldValueMatcher->set_position(Position::FIRST);
    fieldValueMatcher->set_eq_string("pkg0");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    fieldValueMatcher->set_position(Position::LAST);
    fieldValueMatcher->set_eq_string("pkg1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    fieldValueMatcher->set_position(Position::ANY);
    fieldValueMatcher->set_eq_string("Pkg2");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    // is_uid annotation, mapping from uid to package name.
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeRepeatedUidLogEvent(&event2, TAG_ID, intArray);

    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event2).matched);
    fieldValueMatcher->set_eq_string("pkg0");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);

    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event2).matched);
    fieldValueMatcher->set_eq_string("pkg1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);

    fieldValueMatcher->set_position(Position::ANY);
    fieldValueMatcher->set_eq_string("pkg");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event2).matched);
    fieldValueMatcher->set_eq_string("Pkg2");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);
}

TEST(AtomMatcherTest, TestNeqAnyStringMatcher_SingleString) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    StringListMatcher* neqStringList = fieldValueMatcher->mutable_neq_any_string();
    neqStringList->add_str_value("some value");
    neqStringList->add_str_value("another value");

    // First string matched.
    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event1, TAG_ID, 0, "some value");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    // Second string matched.
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event2, TAG_ID, 0, "another value");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event2).matched);

    // No strings matched.
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event3, TAG_ID, 0, "foo");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event3).matched);
}

TEST(AtomMatcherTest, TestNeqAnyStringMatcher_AttributionUids) {
    sp<UidMap> uidMap = new UidMap();

    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg0");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 2222, /*version*/ 2, "v2", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 1, "v1", "Pkg2");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 2, "v2", "PkG3");

    uidMap->updateMap(1, uidData);

    std::vector<int> attributionUids = {1111, 2222, 3333, 1066};
    std::vector<string> attributionTags = {"location1", "location2", "location3", "location3"};

    // Set up the event
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value");

    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    // Match first node.
    auto attributionMatcher = simpleMatcher->add_field_value_matcher();
    attributionMatcher->set_field(FIELD_ID_1);
    attributionMatcher->set_position(Position::FIRST);
    attributionMatcher->mutable_matches_tuple()->add_field_value_matcher()->set_field(
            ATTRIBUTION_UID_FIELD_ID);
    auto neqStringList = attributionMatcher->mutable_matches_tuple()
                                 ->mutable_field_value_matcher(0)
                                 ->mutable_neq_any_string();
    neqStringList->add_str_value("Pkg2");
    neqStringList->add_str_value("PkG3");

    auto fieldMatcher = simpleMatcher->add_field_value_matcher();
    fieldMatcher->set_field(FIELD_ID_2);
    fieldMatcher->set_eq_string("some value");

    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    neqStringList->Clear();
    neqStringList->add_str_value("pkg1");
    neqStringList->add_str_value("PkG3");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    attributionMatcher->set_position(Position::ANY);
    neqStringList->Clear();
    neqStringList->add_str_value("maps.com");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    neqStringList->Clear();
    neqStringList->add_str_value("PkG3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    attributionMatcher->set_position(Position::LAST);
    neqStringList->Clear();
    neqStringList->add_str_value("AID_STATSD");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestEqAnyStringMatcher) {
    sp<UidMap> uidMap = new UidMap();

    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg0");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 2222, /*version*/ 2, "v2", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 1, "v1", "Pkg2");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 2, "v2", "PkG3");

    uidMap->updateMap(1, uidData);

    std::vector<int> attributionUids = {1067, 2222, 3333, 1066};
    std::vector<string> attributionTags = {"location1", "location2", "location3", "location3"};

    // Set up the event
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value");

    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    // Match first node.
    auto attributionMatcher = simpleMatcher->add_field_value_matcher();
    attributionMatcher->set_field(FIELD_ID_1);
    attributionMatcher->set_position(Position::FIRST);
    attributionMatcher->mutable_matches_tuple()->add_field_value_matcher()->set_field(
            ATTRIBUTION_UID_FIELD_ID);
    auto eqStringList = attributionMatcher->mutable_matches_tuple()
                                ->mutable_field_value_matcher(0)
                                ->mutable_eq_any_string();
    eqStringList->add_str_value("AID_ROOT");
    eqStringList->add_str_value("AID_INCIDENTD");

    auto fieldMatcher = simpleMatcher->add_field_value_matcher();
    fieldMatcher->set_field(FIELD_ID_2);
    fieldMatcher->set_eq_string("some value");

    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    attributionMatcher->set_position(Position::ANY);
    eqStringList->Clear();
    eqStringList->add_str_value("AID_STATSD");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    eqStringList->Clear();
    eqStringList->add_str_value("pkg1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    auto normalStringField = fieldMatcher->mutable_eq_any_string();
    normalStringField->add_str_value("some value123");
    normalStringField->add_str_value("some value");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    normalStringField->Clear();
    normalStringField->add_str_value("AID_STATSD");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    eqStringList->Clear();
    eqStringList->add_str_value("maps.com");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestBoolMatcher) {
    sp<UidMap> uidMap = new UidMap();
    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    auto keyValue1 = simpleMatcher->add_field_value_matcher();
    keyValue1->set_field(FIELD_ID_1);
    auto keyValue2 = simpleMatcher->add_field_value_matcher();
    keyValue2->set_field(FIELD_ID_2);

    // Set up the event
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeBoolLogEvent(&event, TAG_ID, 0, true, false);

    // Test
    keyValue1->set_eq_bool(true);
    keyValue2->set_eq_bool(false);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    keyValue1->set_eq_bool(false);
    keyValue2->set_eq_bool(false);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    keyValue1->set_eq_bool(false);
    keyValue2->set_eq_bool(true);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    keyValue1->set_eq_bool(true);
    keyValue2->set_eq_bool(true);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestStringMatcher) {
    sp<UidMap> uidMap = new UidMap();
    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    auto keyValue = simpleMatcher->add_field_value_matcher();
    keyValue->set_field(FIELD_ID_1);
    keyValue->set_eq_string("some value");

    // Set up the event
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event, TAG_ID, 0, "some value");

    // Test
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestIntMatcher_EmptyRepeatedField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeRepeatedIntLogEvent(&event, TAG_ID, {});

    // Set up the matcher.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);

    // Match first int.
    fieldValueMatcher->set_position(Position::FIRST);
    fieldValueMatcher->set_eq_int(9);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match last int.
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match any int.
    fieldValueMatcher->set_position(Position::ANY);
    fieldValueMatcher->set_eq_int(13);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestIntMatcher_RepeatedIntField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    LogEvent event(/*uid=*/0, /*pid=*/0);
    vector<int> intArray = {21, 9};
    makeRepeatedIntLogEvent(&event, TAG_ID, intArray);

    // Set up the matcher.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    // Match first int.
    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    fieldValueMatcher->set_position(Position::FIRST);
    fieldValueMatcher->set_eq_int(9);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_int(21);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match last int.
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_int(9);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match any int.
    fieldValueMatcher->set_position(Position::ANY);
    fieldValueMatcher->set_eq_int(13);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_int(21);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_int(9);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestLtIntMatcher_RepeatedIntField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    LogEvent event(/*uid=*/0, /*pid=*/0);
    vector<int> intArray = {21, 9};
    makeRepeatedIntLogEvent(&event, TAG_ID, intArray);

    // Set up the matcher.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    // Match first int.
    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    fieldValueMatcher->set_position(Position::FIRST);
    fieldValueMatcher->set_lt_int(9);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_lt_int(21);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_lt_int(23);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match last int.
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_lt_int(9);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_lt_int(8);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match any int.
    fieldValueMatcher->set_position(Position::ANY);
    fieldValueMatcher->set_lt_int(21);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_lt_int(8);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_lt_int(23);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestStringMatcher_RepeatedStringField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    LogEvent event(/*uid=*/0, /*pid=*/0);
    vector<string> strArray = {"str1", "str2", "str3"};
    makeRepeatedStringLogEvent(&event, TAG_ID, strArray);

    // Set up the matcher.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    // Match first int.
    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    fieldValueMatcher->set_position(Position::FIRST);
    fieldValueMatcher->set_eq_string("str2");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_string("str1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match last int.
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_string("str3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match any int.
    fieldValueMatcher->set_position(Position::ANY);
    fieldValueMatcher->set_eq_string("str4");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_string("str1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_string("str2");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    fieldValueMatcher->set_eq_string("str3");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestEqAnyStringMatcher_RepeatedStringField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    LogEvent event(/*uid=*/0, /*pid=*/0);
    vector<string> strArray = {"str1", "str2", "str3"};
    makeRepeatedStringLogEvent(&event, TAG_ID, strArray);

    // Set up the matcher.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    StringListMatcher* eqStringList = fieldValueMatcher->mutable_eq_any_string();

    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    eqStringList->add_str_value("str4");
    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    eqStringList->add_str_value("str2");
    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    eqStringList->add_str_value("str3");
    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    eqStringList->add_str_value("str1");
    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestNeqAnyStringMatcher_RepeatedStringField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    LogEvent event(/*uid=*/0, /*pid=*/0);
    vector<string> strArray = {"str1", "str2", "str3"};
    makeRepeatedStringLogEvent(&event, TAG_ID, strArray);

    // Set up the matcher.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    StringListMatcher* neqStringList = fieldValueMatcher->mutable_neq_any_string();

    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    neqStringList->add_str_value("str4");
    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    neqStringList->add_str_value("str2");
    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    neqStringList->add_str_value("str3");
    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    neqStringList->add_str_value("str1");
    fieldValueMatcher->set_position(Position::FIRST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::LAST);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    fieldValueMatcher->set_position(Position::ANY);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestMultiFieldsMatcher) {
    sp<UidMap> uidMap = new UidMap();
    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    auto keyValue1 = simpleMatcher->add_field_value_matcher();
    keyValue1->set_field(FIELD_ID_1);
    auto keyValue2 = simpleMatcher->add_field_value_matcher();
    keyValue2->set_field(FIELD_ID_2);

    // Set up the event
    LogEvent event(/*uid=*/0, /*pid=*/0);
    CreateTwoValueLogEvent(&event, TAG_ID, 0, 2, 3);

    // Test
    keyValue1->set_eq_int(2);
    keyValue2->set_eq_int(3);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    keyValue1->set_eq_int(2);
    keyValue2->set_eq_int(4);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    keyValue1->set_eq_int(4);
    keyValue2->set_eq_int(3);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestIntComparisonMatcher) {
    sp<UidMap> uidMap = new UidMap();
    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();

    simpleMatcher->set_atom_id(TAG_ID);
    auto keyValue = simpleMatcher->add_field_value_matcher();
    keyValue->set_field(FIELD_ID_1);

    // Set up the event
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeIntLogEvent(&event, TAG_ID, 0, 11);

    // Test

    // eq_int
    keyValue->set_eq_int(10);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_eq_int(11);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_eq_int(12);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // lt_int
    keyValue->set_lt_int(10);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_lt_int(11);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_lt_int(12);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // lte_int
    keyValue->set_lte_int(10);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_lte_int(11);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_lte_int(12);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // gt_int
    keyValue->set_gt_int(10);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_gt_int(11);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_gt_int(12);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // gte_int
    keyValue->set_gte_int(10);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_gte_int(11);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);
    keyValue->set_gte_int(12);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestFloatComparisonMatcher) {
    sp<UidMap> uidMap = new UidMap();
    // Set up the matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    auto keyValue = simpleMatcher->add_field_value_matcher();
    keyValue->set_field(FIELD_ID_1);

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeFloatLogEvent(&event1, TAG_ID, 0, 10.1f);
    keyValue->set_lt_float(10.0);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeFloatLogEvent(&event2, TAG_ID, 0, 9.9f);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);

    LogEvent event3(/*uid=*/0, /*pid=*/0);
    makeFloatLogEvent(&event3, TAG_ID, 0, 10.1f);
    keyValue->set_gt_float(10.0);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event3).matched);

    LogEvent event4(/*uid=*/0, /*pid=*/0);
    makeFloatLogEvent(&event4, TAG_ID, 0, 9.9f);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event4).matched);
}

// Helper for the composite matchers.
void addSimpleMatcher(SimpleAtomMatcher* simpleMatcher, int tag, int key, int val) {
    simpleMatcher->set_atom_id(tag);
    auto keyValue = simpleMatcher->add_field_value_matcher();
    keyValue->set_field(key);
    keyValue->set_eq_int(val);
}

TEST(AtomMatcherTest, TestAndMatcher) {
    // Set up the matcher
    LogicalOperation operation = LogicalOperation::AND;

    vector<int> children;
    children.push_back(0);
    children.push_back(1);
    children.push_back(2);

    vector<MatchingState> matcherResults;
    matcherResults.push_back(MatchingState::kMatched);
    matcherResults.push_back(MatchingState::kNotMatched);
    matcherResults.push_back(MatchingState::kMatched);

    EXPECT_FALSE(combinationMatch(children, operation, matcherResults));

    matcherResults.clear();
    matcherResults.push_back(MatchingState::kMatched);
    matcherResults.push_back(MatchingState::kMatched);
    matcherResults.push_back(MatchingState::kMatched);

    EXPECT_TRUE(combinationMatch(children, operation, matcherResults));
}

TEST(AtomMatcherTest, TestOrMatcher) {
    // Set up the matcher
    LogicalOperation operation = LogicalOperation::OR;

    vector<int> children;
    children.push_back(0);
    children.push_back(1);
    children.push_back(2);

    vector<MatchingState> matcherResults;
    matcherResults.push_back(MatchingState::kMatched);
    matcherResults.push_back(MatchingState::kNotMatched);
    matcherResults.push_back(MatchingState::kMatched);

    EXPECT_TRUE(combinationMatch(children, operation, matcherResults));

    matcherResults.clear();
    matcherResults.push_back(MatchingState::kNotMatched);
    matcherResults.push_back(MatchingState::kNotMatched);
    matcherResults.push_back(MatchingState::kNotMatched);

    EXPECT_FALSE(combinationMatch(children, operation, matcherResults));
}

TEST(AtomMatcherTest, TestNotMatcher) {
    // Set up the matcher
    LogicalOperation operation = LogicalOperation::NOT;

    vector<int> children;
    children.push_back(0);

    vector<MatchingState> matcherResults;
    matcherResults.push_back(MatchingState::kMatched);

    EXPECT_FALSE(combinationMatch(children, operation, matcherResults));

    matcherResults.clear();
    matcherResults.push_back(MatchingState::kNotMatched);
    EXPECT_TRUE(combinationMatch(children, operation, matcherResults));
}

TEST(AtomMatcherTest, TestNandMatcher) {
    // Set up the matcher
    LogicalOperation operation = LogicalOperation::NAND;

    vector<int> children;
    children.push_back(0);
    children.push_back(1);

    vector<MatchingState> matcherResults;
    matcherResults.push_back(MatchingState::kMatched);
    matcherResults.push_back(MatchingState::kNotMatched);

    EXPECT_TRUE(combinationMatch(children, operation, matcherResults));

    matcherResults.clear();
    matcherResults.push_back(MatchingState::kNotMatched);
    matcherResults.push_back(MatchingState::kNotMatched);
    EXPECT_TRUE(combinationMatch(children, operation, matcherResults));

    matcherResults.clear();
    matcherResults.push_back(MatchingState::kMatched);
    matcherResults.push_back(MatchingState::kMatched);
    EXPECT_FALSE(combinationMatch(children, operation, matcherResults));
}

TEST(AtomMatcherTest, TestNorMatcher) {
    // Set up the matcher
    LogicalOperation operation = LogicalOperation::NOR;

    vector<int> children;
    children.push_back(0);
    children.push_back(1);

    vector<MatchingState> matcherResults;
    matcherResults.push_back(MatchingState::kMatched);
    matcherResults.push_back(MatchingState::kNotMatched);

    EXPECT_FALSE(combinationMatch(children, operation, matcherResults));

    matcherResults.clear();
    matcherResults.push_back(MatchingState::kNotMatched);
    matcherResults.push_back(MatchingState::kNotMatched);
    EXPECT_TRUE(combinationMatch(children, operation, matcherResults));

    matcherResults.clear();
    matcherResults.push_back(MatchingState::kMatched);
    matcherResults.push_back(MatchingState::kMatched);
    EXPECT_FALSE(combinationMatch(children, operation, matcherResults));
}
//
TEST(AtomMatcherTest, TestUidFieldMatcherWithWildcardString) {
    sp<UidMap> uidMap = new UidMap();
    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "package0");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1111, /*version*/ 1, "v1", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 2222, /*version*/ 2, "v2", "pkg1");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 1, "v1", "package2");
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 3333, /*version*/ 2, "v2", "package3");

    uidMap->updateMap(1, uidData);

    // Set up matcher
    AtomMatcher matcher;
    auto simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    simpleMatcher->add_field_value_matcher()->set_field(1);
    simpleMatcher->mutable_field_value_matcher(0)->set_eq_wildcard_string("pkg*");

    // Event without is_uid annotation.
    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeIntLogEvent(&event1, TAG_ID, 0, 1111);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    // Event where mapping from uid to package name occurs.
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeIntWithBoolAnnotationLogEvent(&event2, TAG_ID, 1111, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);

    // Event where uid maps to package names that don't fit wildcard pattern.
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    makeIntWithBoolAnnotationLogEvent(&event3, TAG_ID, 3333, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event3).matched);

    // Update matcher to match one AID
    simpleMatcher->mutable_field_value_matcher(0)->set_eq_wildcard_string(
            "AID_SYSTEM");  // uid 1000

    // Event where mapping from uid to aid doesn't fit wildcard pattern.
    LogEvent event4(/*uid=*/0, /*pid=*/0);
    makeIntWithBoolAnnotationLogEvent(&event4, TAG_ID, 1005, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event4).matched);

    // Event where mapping from uid to aid does fit wildcard pattern.
    LogEvent event5(/*uid=*/0, /*pid=*/0);
    makeIntWithBoolAnnotationLogEvent(&event5, TAG_ID, 1000, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event5).matched);

    // Update matcher to match multiple AIDs
    simpleMatcher->mutable_field_value_matcher(0)->set_eq_wildcard_string("AID_SDCARD_*");

    // Event where mapping from uid to aid doesn't fit wildcard pattern.
    LogEvent event6(/*uid=*/0, /*pid=*/0);
    makeIntWithBoolAnnotationLogEvent(&event6, TAG_ID, 1036, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event6).matched);

    // Event where mapping from uid to aid does fit wildcard pattern.
    LogEvent event7(/*uid=*/0, /*pid=*/0);
    makeIntWithBoolAnnotationLogEvent(&event7, TAG_ID, 1034, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event7).matched);

    LogEvent event8(/*uid=*/0, /*pid=*/0);
    makeIntWithBoolAnnotationLogEvent(&event8, TAG_ID, 1035, ASTATSLOG_ANNOTATION_ID_IS_UID, true);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event8).matched);
}

TEST(AtomMatcherTest, TestWildcardStringMatcher) {
    sp<UidMap> uidMap = new UidMap();
    // Set up the matcher
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    // Matches any string that begins with "test.string:test_" and ends with number between 0 and 9
    // inclusive
    fieldValueMatcher->set_eq_wildcard_string("test.string:test_[0-9]");

    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event1, TAG_ID, 0, "test.string:test_0");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event2, TAG_ID, 0, "test.string:test_19");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event2)
                         .matched);  // extra character at end of string

    LogEvent event3(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event3, TAG_ID, 0, "extra.test.string:test_1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher,
                               event3)
                         .matched);  // extra characters at beginning of string

    LogEvent event4(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event4, TAG_ID, 0, "test.string:test_");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher,
                               event4)
                         .matched);  // missing character from 0-9 at end of string

    LogEvent event5(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event5, TAG_ID, 0, "est.string:test_1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event5)
                         .matched);  // missing 't' at beginning of string

    LogEvent event6(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event6, TAG_ID, 0, "test.string:test_1extra");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event6)
                         .matched);  // extra characters at end of string

    // Matches any string that contains "test.string:test_" + any extra characters before or after
    fieldValueMatcher->set_eq_wildcard_string("*test.string:test_*");

    LogEvent event7(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event7, TAG_ID, 0, "test.string:test_");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event7).matched);

    LogEvent event8(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event8, TAG_ID, 0, "extra.test.string:test_");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event8).matched);

    LogEvent event9(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event9, TAG_ID, 0, "test.string:test_extra");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event9).matched);

    LogEvent event10(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event10, TAG_ID, 0, "est.string:test_");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event10).matched);

    LogEvent event11(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event11, TAG_ID, 0, "test.string:test");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event11).matched);
}

TEST(AtomMatcherTest, TestEqAnyWildcardStringMatcher) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    StringListMatcher* eqWildcardStrList = fieldValueMatcher->mutable_eq_any_wildcard_string();
    eqWildcardStrList->add_str_value("first_string_*");
    eqWildcardStrList->add_str_value("second_string_*");

    // First wildcard pattern matched.
    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event1, TAG_ID, 0, "first_string_1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    // Second wildcard pattern matched.
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event2, TAG_ID, 0, "second_string_1");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);

    // No wildcard patterns matched.
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    makeStringLogEvent(&event3, TAG_ID, 0, "third_string_1");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event3).matched);
}

TEST(AtomMatcherTest, TestNeqAnyWildcardStringMatcher) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location_1", "location_2", "location"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value");

    // Set up the matcher. Match first tag.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    FieldValueMatcher* attributionMatcher = simpleMatcher->add_field_value_matcher();
    attributionMatcher->set_field(FIELD_ID_1);
    attributionMatcher->set_position(Position::FIRST);
    FieldValueMatcher* attributionTagMatcher =
            attributionMatcher->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagMatcher->set_field(ATTRIBUTION_TAG_FIELD_ID);
    StringListMatcher* neqWildcardStrList =
            attributionTagMatcher->mutable_neq_any_wildcard_string();

    // First tag is not matched. neq string list {"tag"}
    neqWildcardStrList->add_str_value("tag");
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // First tag is matched. neq string list {"tag", "location_*"}
    neqWildcardStrList->add_str_value("location_*");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match last tag.
    attributionMatcher->set_position(Position::LAST);

    // Last tag is not matched. neq string list {"tag", "location_*"}
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Last tag is matched. neq string list {"tag", "location_*", "location*"}
    neqWildcardStrList->add_str_value("location*");
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match any tag.
    attributionMatcher->set_position(Position::ANY);

    // All tags are matched. neq string list {"tag", "location_*", "location*"}
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Set up another log event.
    std::vector<string> attributionTags2 = {"location_1", "location", "string"};
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event2, TAG_ID, 0, attributionUids, attributionTags2, "some value");

    // Tag "string" is not matched. neq string list {"tag", "location_*", "location*"}
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);
}

TEST(AtomMatcherTest, TestEqAnyIntMatcher) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);

    FieldValueMatcher* fieldValueMatcher = simpleMatcher->add_field_value_matcher();
    fieldValueMatcher->set_field(FIELD_ID_1);
    IntListMatcher* eqIntList = fieldValueMatcher->mutable_eq_any_int();
    eqIntList->add_int_value(3);
    eqIntList->add_int_value(5);

    // First int matched.
    LogEvent event1(/*uid=*/0, /*pid=*/0);
    makeIntLogEvent(&event1, TAG_ID, 0, 3);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event1).matched);

    // Second int matched.
    LogEvent event2(/*uid=*/0, /*pid=*/0);
    makeIntLogEvent(&event2, TAG_ID, 0, 5);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event2).matched);

    // No ints matched.
    LogEvent event3(/*uid=*/0, /*pid=*/0);
    makeIntLogEvent(&event3, TAG_ID, 0, 4);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event3).matched);
}

TEST(AtomMatcherTest, TestNeqAnyIntMatcher) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value");

    // Set up the matcher. Match first uid.
    AtomMatcher matcher;
    SimpleAtomMatcher* simpleMatcher = matcher.mutable_simple_atom_matcher();
    simpleMatcher->set_atom_id(TAG_ID);
    FieldValueMatcher* attributionMatcher = simpleMatcher->add_field_value_matcher();
    attributionMatcher->set_field(FIELD_ID_1);
    attributionMatcher->set_position(Position::FIRST);
    FieldValueMatcher* attributionUidMatcher =
            attributionMatcher->mutable_matches_tuple()->add_field_value_matcher();
    attributionUidMatcher->set_field(ATTRIBUTION_UID_FIELD_ID);
    IntListMatcher* neqIntList = attributionUidMatcher->mutable_neq_any_int();

    // First uid is not matched. neq int list {4444}
    neqIntList->add_int_value(4444);
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // First uid is matched. neq int list {4444, 1111}
    neqIntList->add_int_value(1111);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match last uid.
    attributionMatcher->set_position(Position::LAST);

    // Last uid is not matched. neq int list {4444, 1111}
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Last uid is matched. neq int list {4444, 1111, 3333}
    neqIntList->add_int_value(3333);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // Match any uid.
    attributionMatcher->set_position(Position::ANY);

    // Uid 2222 is not matched. neq int list {4444, 1111, 3333}
    EXPECT_TRUE(matchesSimple(uidMap, *simpleMatcher, event).matched);

    // All uids are matched. neq int list {4444, 1111, 3333, 2222}
    neqIntList->add_int_value(2222);
    EXPECT_FALSE(matchesSimple(uidMap, *simpleMatcher, event).matched);
}

TEST(AtomMatcherTest, TestStringReplaceRoot) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value123");

    // Set up the matcher. Replace second field.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(FIELD_ID_2);
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    const auto [hasMatched, transformedEvent] =
            matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
    EXPECT_TRUE(hasMatched);
    ASSERT_NE(transformedEvent, nullptr);

    const vector<FieldValue>& fieldValues = transformedEvent->getValues();
    ASSERT_EQ(fieldValues.size(), 7);
    EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
    EXPECT_EQ(fieldValues[1].mValue.str_value, "location1");
    EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
    EXPECT_EQ(fieldValues[3].mValue.str_value, "location2");
    EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
    EXPECT_EQ(fieldValues[5].mValue.str_value, "location3");
    EXPECT_EQ(fieldValues[6].mValue.str_value, "some value");
}

TEST(AtomMatcherTest, TestStringReplaceAttributionTagFirst) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value123");

    // Set up the matcher. Replace first attribution tag.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::FIRST);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    const auto [hasMatched, transformedEvent] =
            matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
    EXPECT_TRUE(hasMatched);
    ASSERT_NE(transformedEvent, nullptr);
    const vector<FieldValue>& fieldValues = transformedEvent->getValues();
    ASSERT_EQ(fieldValues.size(), 7);
    EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
    EXPECT_EQ(fieldValues[1].mValue.str_value, "location");
    EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
    EXPECT_EQ(fieldValues[3].mValue.str_value, "location2");
    EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
    EXPECT_EQ(fieldValues[5].mValue.str_value, "location3");
    EXPECT_EQ(fieldValues[6].mValue.str_value, "some value123");
}

TEST(AtomMatcherTest, TestStringReplaceAttributionTagLast) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value123");

    // Set up the matcher. Replace last attribution tag.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::LAST);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    const auto [hasMatched, transformedEvent] =
            matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
    EXPECT_TRUE(hasMatched);
    ASSERT_NE(transformedEvent, nullptr);

    const vector<FieldValue>& fieldValues = transformedEvent->getValues();
    ASSERT_EQ(fieldValues.size(), 7);
    EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
    EXPECT_EQ(fieldValues[1].mValue.str_value, "location1");
    EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
    EXPECT_EQ(fieldValues[3].mValue.str_value, "location2");
    EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
    EXPECT_EQ(fieldValues[5].mValue.str_value, "location");
    EXPECT_EQ(fieldValues[6].mValue.str_value, "some value123");
}

TEST(AtomMatcherTest, TestStringReplaceAttributionTagAll) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value123");

    // Set up the matcher. Replace all attribution tags.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::ALL);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    const auto [hasMatched, transformedEvent] =
            matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
    EXPECT_TRUE(hasMatched);
    ASSERT_NE(transformedEvent, nullptr);

    const vector<FieldValue>& fieldValues = transformedEvent->getValues();
    ASSERT_EQ(fieldValues.size(), 7);
    EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
    EXPECT_EQ(fieldValues[1].mValue.str_value, "location");
    EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
    EXPECT_EQ(fieldValues[3].mValue.str_value, "location");
    EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
    EXPECT_EQ(fieldValues[5].mValue.str_value, "location");
    EXPECT_EQ(fieldValues[6].mValue.str_value, "some value123");
}

TEST(AtomMatcherTest, TestStringReplaceNestedAllWithMultipleNestedStringFields) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value123");

    // Manually change uid fields to string fields, as there is no direct way to create a
    // LogEvent with multiple nested string fields.
    (*event.getMutableValues())[0].mValue = android::os::statsd::Value("abc1");
    (*event.getMutableValues())[2].mValue = android::os::statsd::Value("xyz2");
    (*event.getMutableValues())[4].mValue = android::os::statsd::Value("abc3");

    // Set up the matcher. Replace all attribution tags.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::ALL);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    const auto [hasMatched, transformedEvent] =
            matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
    EXPECT_TRUE(hasMatched);
    ASSERT_NE(transformedEvent, nullptr);

    const vector<FieldValue>& fieldValues = transformedEvent->getValues();
    ASSERT_EQ(fieldValues.size(), 7);
    EXPECT_EQ(fieldValues[0].mValue.str_value, "abc1");
    EXPECT_EQ(fieldValues[1].mValue.str_value, "location");
    EXPECT_EQ(fieldValues[2].mValue.str_value, "xyz2");
    EXPECT_EQ(fieldValues[3].mValue.str_value, "location");
    EXPECT_EQ(fieldValues[4].mValue.str_value, "abc3");
    EXPECT_EQ(fieldValues[5].mValue.str_value, "location");
    EXPECT_EQ(fieldValues[6].mValue.str_value, "some value123");
}

TEST(AtomMatcherTest, TestStringReplaceRootOnMatchedField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher. Replace second field and match on replaced field.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(FIELD_ID_2);
    fvm->set_eq_string("bar");
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags,
                                "some value123");

        EXPECT_FALSE(matchesSimple(uidMap, matcher.simple_atom_matcher(), event).matched);
    }

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "bar123");
        const auto [hasMatched, transformedEvent] =
                matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
        EXPECT_TRUE(hasMatched);
        ASSERT_NE(transformedEvent, nullptr);
        const vector<FieldValue>& fieldValues = transformedEvent->getValues();
        ASSERT_EQ(fieldValues.size(), 7);
        EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
        EXPECT_EQ(fieldValues[1].mValue.str_value, "location1");
        EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
        EXPECT_EQ(fieldValues[3].mValue.str_value, "location2");
        EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
        EXPECT_EQ(fieldValues[5].mValue.str_value, "location3");
        EXPECT_EQ(fieldValues[6].mValue.str_value, "bar");
    }
}

TEST(AtomMatcherTest, TestStringReplaceAttributionTagFirstOnMatchedField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher. Replace first attribution tag and match on that tag.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::FIRST);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    attributionTagFvm->set_eq_string("bar");
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags,
                                "some value123");

        EXPECT_FALSE(matchesSimple(uidMap, matcher.simple_atom_matcher(), event).matched);
    }

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        attributionTags = {"bar1", "bar2", "bar3"};
        makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "bar123");
        const auto [hasMatched, transformedEvent] =
                matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
        EXPECT_TRUE(hasMatched);
        ASSERT_NE(transformedEvent, nullptr);
        const vector<FieldValue>& fieldValues = transformedEvent->getValues();
        ASSERT_EQ(fieldValues.size(), 7);
        EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
        EXPECT_EQ(fieldValues[1].mValue.str_value, "bar");
        EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
        EXPECT_EQ(fieldValues[3].mValue.str_value, "bar2");
        EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
        EXPECT_EQ(fieldValues[5].mValue.str_value, "bar3");
        EXPECT_EQ(fieldValues[6].mValue.str_value, "bar123");
    }
}

TEST(AtomMatcherTest, TestStringReplaceAttributionTagLastOnMatchedField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher. Replace last attribution tag and match on that tag.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::LAST);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    attributionTagFvm->set_eq_string("bar");
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags,
                                "some value123");

        EXPECT_FALSE(matchesSimple(uidMap, matcher.simple_atom_matcher(), event).matched);
    }

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        attributionTags = {"bar1", "bar2", "bar3"};
        makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "bar123");
        const auto [hasMatched, transformedEvent] =
                matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
        EXPECT_TRUE(hasMatched);
        ASSERT_NE(transformedEvent, nullptr);
        const vector<FieldValue>& fieldValues = transformedEvent->getValues();
        ASSERT_EQ(fieldValues.size(), 7);
        EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
        EXPECT_EQ(fieldValues[1].mValue.str_value, "bar1");
        EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
        EXPECT_EQ(fieldValues[3].mValue.str_value, "bar2");
        EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
        EXPECT_EQ(fieldValues[5].mValue.str_value, "bar");
        EXPECT_EQ(fieldValues[6].mValue.str_value, "bar123");
    }
}

TEST(AtomMatcherTest, TestStringReplaceAttributionTagAnyOnMatchedField) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher. Replace all attribution tags but match on any tag.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::ANY);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    attributionTagFvm->set_eq_string("bar");
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags,
                                "some value123");

        EXPECT_FALSE(matchesSimple(uidMap, matcher.simple_atom_matcher(), event).matched);
    }

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        attributionTags = {"foo1", "bar2", "foo3"};
        makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "bar123");
        const auto [hasMatched, transformedEvent] =
                matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
        EXPECT_TRUE(hasMatched);
        ASSERT_NE(transformedEvent, nullptr);
        const vector<FieldValue>& fieldValues = transformedEvent->getValues();
        ASSERT_EQ(fieldValues.size(), 7);
        EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
        EXPECT_EQ(fieldValues[1].mValue.str_value, "foo");
        EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
        EXPECT_EQ(fieldValues[3].mValue.str_value, "bar");
        EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
        EXPECT_EQ(fieldValues[5].mValue.str_value, "foo");
        EXPECT_EQ(fieldValues[6].mValue.str_value, "bar123");
    }
}

TEST(AtomMatcherTest, TestStringReplaceAttributionTagAnyAndRootOnMatchedFields) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher. Replace all attribution tags but match on any tag.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::ANY);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    attributionTagFvm->set_eq_string("bar");
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");
    FieldValueMatcher* rootFvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    rootFvm->set_field(FIELD_ID_2);
    rootFvm->set_eq_string("blah");
    stringReplacer = rootFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, {1111, 2222, 3333} /* uids */,
                                {"location1", "location2", "location3"} /* tags */,
                                "some value123" /* name */);

        EXPECT_FALSE(matchesSimple(uidMap, matcher.simple_atom_matcher(), event).matched);
    }

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, {1111, 2222, 3333} /* uids */,
                                {"foo1", "bar2", "foo3"} /* tags */, "bar123" /* name */);
        EXPECT_FALSE(matchesSimple(uidMap, matcher.simple_atom_matcher(), event).matched);
    }

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, {1111, 2222, 3333} /* uids */,
                                {"foo1", "bar2", "foo3"} /* tags */, "blah123" /* name */);
        const auto [hasMatched, transformedEvent] =
                matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
        EXPECT_TRUE(hasMatched);
        ASSERT_NE(transformedEvent, nullptr);
        const vector<FieldValue>& fieldValues = transformedEvent->getValues();
        ASSERT_EQ(fieldValues.size(), 7);
        EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
        EXPECT_EQ(fieldValues[1].mValue.str_value, "foo");
        EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
        EXPECT_EQ(fieldValues[3].mValue.str_value, "bar");
        EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
        EXPECT_EQ(fieldValues[5].mValue.str_value, "foo");
        EXPECT_EQ(fieldValues[6].mValue.str_value, "blah");
    }
}

TEST(AtomMatcherTest, TestStringReplaceAttributionTagAnyWithAttributionUidValueMatcher) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the matcher. Replace all attribution tags but match on any uid and tag.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* attributionFvm =
            matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    attributionFvm->set_field(FIELD_ID_1);
    attributionFvm->set_position(Position::ANY);
    FieldValueMatcher* attributionUidFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionUidFvm->set_field(ATTRIBUTION_UID_FIELD_ID);
    attributionUidFvm->set_eq_int(2222);
    FieldValueMatcher* attributionTagFvm =
            attributionFvm->mutable_matches_tuple()->add_field_value_matcher();
    attributionTagFvm->set_field(ATTRIBUTION_TAG_FIELD_ID);
    attributionTagFvm->set_eq_string("bar");
    StringReplacer* stringReplacer = attributionTagFvm->mutable_replace_string();
    stringReplacer->set_regex(R"([0-9]+$)");  // match trailing digits, example "42" in "foo42".
    stringReplacer->set_replacement("");

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, {1111, 2222, 3333} /* uids */,
                                {"location1", "location2", "location3"} /* tags */,
                                "some value123" /* name */);

        EXPECT_FALSE(matchesSimple(uidMap, matcher.simple_atom_matcher(), event).matched);
    }

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, {1111, 3223, 3333} /* uids */,
                                {"foo1", "bar2", "foo3"} /* tags */, "bar123" /* name */);
        EXPECT_FALSE(matchesSimple(uidMap, matcher.simple_atom_matcher(), event).matched);
    }

    {
        LogEvent event(/*uid=*/0, /*pid=*/0);
        makeAttributionLogEvent(&event, TAG_ID, 0, {1111, 2222, 3333} /* uids */,
                                {"foo1", "bar2", "foo3"} /* tags */, "bar123" /* name */);
        const auto [hasMatched, transformedEvent] =
                matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
        EXPECT_TRUE(hasMatched);
        ASSERT_NE(transformedEvent, nullptr);
        const vector<FieldValue>& fieldValues = transformedEvent->getValues();
        ASSERT_EQ(fieldValues.size(), 7);
        EXPECT_EQ(fieldValues[0].mValue.int_value, 1111);
        EXPECT_EQ(fieldValues[1].mValue.str_value, "foo");
        EXPECT_EQ(fieldValues[2].mValue.int_value, 2222);
        EXPECT_EQ(fieldValues[3].mValue.str_value, "bar");
        EXPECT_EQ(fieldValues[4].mValue.int_value, 3333);
        EXPECT_EQ(fieldValues[5].mValue.str_value, "foo");
        EXPECT_EQ(fieldValues[6].mValue.str_value, "bar123");
    }
}

TEST(AtomMatcherTest, TestStringReplaceBadRegex) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value123");

    // Set up the matcher. Replace second field.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(FIELD_ID_2);
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(
            R"(*[0-9]+$)");  // bad regex: asterisk not preceded by any expression.
    stringReplacer->set_replacement("");

    const auto [hasMatched, transformedEvent] =
            matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
    EXPECT_TRUE(hasMatched);
    ASSERT_EQ(transformedEvent, nullptr);
}

TEST(AtomMatcherTest, TestStringReplaceRegexWithSubgroup) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value123");

    // Set up the matcher. Replace second field.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(FIELD_ID_2);
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"(([a-z]+)[0-9]+$)");  // "([a-z]+)" is a subgroup.
    stringReplacer->set_replacement("");

    const auto [hasMatched, transformedEvent] =
            matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
    EXPECT_TRUE(hasMatched);
    ASSERT_EQ(transformedEvent, nullptr);
}

TEST(AtomMatcherTest, TestStringReplaceNoop) {
    sp<UidMap> uidMap = new UidMap();

    // Set up the log event.
    std::vector<int> attributionUids = {1111, 2222, 3333};
    std::vector<string> attributionTags = {"location1", "location2", "location3"};
    LogEvent event(/*uid=*/0, /*pid=*/0);
    makeAttributionLogEvent(&event, TAG_ID, 0, attributionUids, attributionTags, "some value123");

    // Set up the matcher. Replace second field.
    AtomMatcher matcher = CreateSimpleAtomMatcher("matcher", TAG_ID);
    FieldValueMatcher* fvm = matcher.mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(FIELD_ID_2);
    StringReplacer* stringReplacer = fvm->mutable_replace_string();
    stringReplacer->set_regex(R"(this_pattern_should_not_match)");
    stringReplacer->set_replacement("");

    const auto [hasMatched, transformedEvent] =
            matchesSimple(uidMap, matcher.simple_atom_matcher(), event);
    EXPECT_TRUE(hasMatched);
    ASSERT_EQ(transformedEvent, nullptr);
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
