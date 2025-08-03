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

#include "external/puller_util.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include <vector>

#include "../metrics/metrics_test_helper.h"
#include "FieldValue.h"
#include "stats_event.h"
#include "tests/statsd_test_util.h"

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

using namespace testing;
using std::shared_ptr;
using std::vector;
/*
 * Test merge isolated and host uid
 */
namespace {
const int uidAtomTagId = 100;
const vector<int> additiveFields = {3};
const int nonUidAtomTagId = 200;
const int timestamp = 1234;
const int isolatedUid1 = 30;
const int isolatedUid2 = 40;
const int isolatedNonAdditiveData = 32;
const int isolatedAdditiveData = 31;
const int hostUid = 20;
const int hostNonAdditiveData = 22;
const int hostAdditiveData = 21;
const int attributionAtomTagId = 300;
const int hostUid2 = 2000;
const int isolatedUid3 = 3000;
const int isolatedUid4 = 4000;

sp<MockUidMap> makeMockUidMap() {
    return makeMockUidMapForHosts(
            {{hostUid, {isolatedUid1, isolatedUid2}}, {hostUid2, {isolatedUid3, isolatedUid4}}});
}

}  // anonymous namespace

TEST(PullerUtilTest, MergeNoDimension) {
    vector<shared_ptr<LogEvent>> data = {
            // 30->22->31
            makeUidLogEvent(uidAtomTagId, timestamp, isolatedUid1, hostNonAdditiveData,
                            isolatedAdditiveData),

            // 20->22->21
            makeUidLogEvent(uidAtomTagId, timestamp, hostUid, hostNonAdditiveData,
                            hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, additiveFields);

    ASSERT_EQ(1, (int)data.size());
    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(3, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData + hostAdditiveData, actualFieldValues->at(2).mValue.int_value);
}

TEST(PullerUtilTest, MergeWithDimension) {
    vector<shared_ptr<LogEvent>> data = {
            // 30->32->31
            makeUidLogEvent(uidAtomTagId, timestamp, isolatedUid1, isolatedNonAdditiveData,
                            isolatedAdditiveData),

            // 20->32->21
            makeUidLogEvent(uidAtomTagId, timestamp, hostUid, isolatedNonAdditiveData,
                            hostAdditiveData),

            // 20->22->21
            makeUidLogEvent(uidAtomTagId, timestamp, hostUid, hostNonAdditiveData,
                            hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, additiveFields);

    ASSERT_EQ(2, (int)data.size());

    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(3, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(2).mValue.int_value);

    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(3, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostAdditiveData + isolatedAdditiveData, actualFieldValues->at(2).mValue.int_value);
}

TEST(PullerUtilTest, NoMergeHostUidOnly) {
    vector<shared_ptr<LogEvent>> data = {
            // 20->32->31
            makeUidLogEvent(uidAtomTagId, timestamp, hostUid, isolatedNonAdditiveData,
                            isolatedAdditiveData),

            // 20->22->21
            makeUidLogEvent(uidAtomTagId, timestamp, hostUid, hostNonAdditiveData,
                            hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, additiveFields);

    ASSERT_EQ(2, (int)data.size());

    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(3, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(2).mValue.int_value);

    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(3, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData, actualFieldValues->at(2).mValue.int_value);
}

TEST(PullerUtilTest, IsolatedUidOnly) {
    vector<shared_ptr<LogEvent>> data = {
            // 30->32->31
            makeUidLogEvent(uidAtomTagId, timestamp, isolatedUid1, isolatedNonAdditiveData,
                            isolatedAdditiveData),

            // 30->22->21
            makeUidLogEvent(uidAtomTagId, timestamp, isolatedUid1, hostNonAdditiveData,
                            hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, additiveFields);

    ASSERT_EQ(2, (int)data.size());

    // 20->32->31
    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(3, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(2).mValue.int_value);

    // 20->22->21
    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(3, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData, actualFieldValues->at(2).mValue.int_value);
}

TEST(PullerUtilTest, MultipleIsolatedUidToOneHostUid) {
    vector<shared_ptr<LogEvent>> data = {
            // 30->32->31
            makeUidLogEvent(uidAtomTagId, timestamp, isolatedUid1, isolatedNonAdditiveData,
                            isolatedAdditiveData),

            // 40->32->21
            makeUidLogEvent(uidAtomTagId, timestamp, isolatedUid2, isolatedNonAdditiveData,
                            hostAdditiveData),

            // 20->32->21
            makeUidLogEvent(uidAtomTagId, timestamp, hostUid, isolatedNonAdditiveData,
                            hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, additiveFields);

    ASSERT_EQ(1, (int)data.size());

    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(3, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData + hostAdditiveData + hostAdditiveData,
              actualFieldValues->at(2).mValue.int_value);
}

TEST(PullerUtilTest, TwoIsolatedUidsOneAtom) {
    vector<shared_ptr<LogEvent>> data = {
            makeExtraUidsLogEvent(uidAtomTagId, timestamp, isolatedUid1, isolatedNonAdditiveData,
                                  isolatedAdditiveData, {isolatedUid3}),

            makeExtraUidsLogEvent(uidAtomTagId, timestamp, isolatedUid2, isolatedNonAdditiveData,
                                  hostAdditiveData, {isolatedUid4}),

            makeExtraUidsLogEvent(uidAtomTagId, timestamp, hostUid, isolatedNonAdditiveData,
                                  hostAdditiveData, {hostUid2}),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, additiveFields);

    ASSERT_EQ(1, (int)data.size());

    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(4, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData + hostAdditiveData + hostAdditiveData,
              actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(hostUid2, actualFieldValues->at(3).mValue.int_value);
}

TEST(PullerUtilTest, NoNeedToMerge) {
    vector<shared_ptr<LogEvent>> data = {
            // 32->31
            CreateTwoValueLogEvent(nonUidAtomTagId, timestamp, isolatedNonAdditiveData,
                                   isolatedAdditiveData),

            // 22->21
            CreateTwoValueLogEvent(nonUidAtomTagId, timestamp, hostNonAdditiveData,
                                   hostAdditiveData),

    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, nonUidAtomTagId, {} /*no additive fields*/);

    ASSERT_EQ(2, (int)data.size());

    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(2, actualFieldValues->size());
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData, actualFieldValues->at(1).mValue.int_value);

    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(2, actualFieldValues->size());
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(1).mValue.int_value);
}

TEST(PullerUtilTest, MergeNoDimensionAttributionChain) {
    vector<shared_ptr<LogEvent>> data = {
            // 30->tag1->400->tag2->22->31
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {isolatedUid1, 400},
                                    {"tag1", "tag2"}, hostNonAdditiveData, isolatedAdditiveData),

            // 20->tag1->400->tag2->22->21
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {hostUid, 400},
                                    {"tag1", "tag2"}, hostNonAdditiveData, hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, attributionAtomTagId, additiveFields);

    ASSERT_EQ(1, (int)data.size());
    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ("tag1", actualFieldValues->at(1).mValue.str_value);
    EXPECT_EQ(400, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ("tag2", actualFieldValues->at(3).mValue.str_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData + hostAdditiveData, actualFieldValues->at(5).mValue.int_value);
}

TEST(PullerUtilTest, MergeWithDimensionAttributionChain) {
    vector<shared_ptr<LogEvent>> data = {
            // 200->tag1->30->tag2->32->31
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {200, isolatedUid1},
                                    {"tag1", "tag2"}, isolatedNonAdditiveData,
                                    isolatedAdditiveData),

            // 200->tag1->20->tag2->32->21
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {200, hostUid},
                                    {"tag1", "tag2"}, isolatedNonAdditiveData, hostAdditiveData),

            // 200->tag1->20->tag2->22->21
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {200, hostUid},
                                    {"tag1", "tag2"}, hostNonAdditiveData, hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, attributionAtomTagId, additiveFields);

    ASSERT_EQ(2, (int)data.size());

    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(200, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ("tag1", actualFieldValues->at(1).mValue.str_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ("tag2", actualFieldValues->at(3).mValue.str_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(5).mValue.int_value);

    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(200, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ("tag1", actualFieldValues->at(1).mValue.str_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ("tag2", actualFieldValues->at(3).mValue.str_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(hostAdditiveData + isolatedAdditiveData, actualFieldValues->at(5).mValue.int_value);
}

TEST(PullerUtilTest, NoMergeHostUidOnlyAttributionChain) {
    vector<shared_ptr<LogEvent>> data = {
            // 20->tag1->400->tag2->32->31
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {hostUid, 400},
                                    {"tag1", "tag2"}, isolatedNonAdditiveData,
                                    isolatedAdditiveData),

            // 20->tag1->400->tag2->22->21
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {hostUid, 400},
                                    {"tag1", "tag2"}, hostNonAdditiveData, hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, attributionAtomTagId, additiveFields);

    ASSERT_EQ(2, (int)data.size());

    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ("tag1", actualFieldValues->at(1).mValue.str_value);
    EXPECT_EQ(400, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ("tag2", actualFieldValues->at(3).mValue.str_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(5).mValue.int_value);

    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ("tag1", actualFieldValues->at(1).mValue.str_value);
    EXPECT_EQ(400, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ("tag2", actualFieldValues->at(3).mValue.str_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData, actualFieldValues->at(5).mValue.int_value);
}

TEST(PullerUtilTest, IsolatedUidOnlyAttributionChain) {
    vector<shared_ptr<LogEvent>> data = {
            // 30->tag1->400->tag2->32->31
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {isolatedUid1, 400},
                                    {"tag1", "tag2"}, isolatedNonAdditiveData,
                                    isolatedAdditiveData),

            // 30->tag1->400->tag2->22->21
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {isolatedUid1, 400},
                                    {"tag1", "tag2"}, hostNonAdditiveData, hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, attributionAtomTagId, additiveFields);

    ASSERT_EQ(2, (int)data.size());

    // 20->tag1->400->tag2->32->31
    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ("tag1", actualFieldValues->at(1).mValue.str_value);
    EXPECT_EQ(400, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ("tag2", actualFieldValues->at(3).mValue.str_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(5).mValue.int_value);

    // 20->tag1->400->tag2->22->21
    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ("tag1", actualFieldValues->at(1).mValue.str_value);
    EXPECT_EQ(400, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ("tag2", actualFieldValues->at(3).mValue.str_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData, actualFieldValues->at(5).mValue.int_value);
}

TEST(PullerUtilTest, MultipleIsolatedUidToOneHostUidAttributionChain) {
    vector<shared_ptr<LogEvent>> data = {
            // 30->tag1->400->tag2->32->31
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {isolatedUid1, 400},
                                    {"tag1", "tag2"}, isolatedNonAdditiveData,
                                    isolatedAdditiveData),

            // 31->tag1->400->tag2->32->21
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {isolatedUid2, 400},
                                    {"tag1", "tag2"}, isolatedNonAdditiveData, hostAdditiveData),

            // 20->tag1->400->tag2->32->21
            makeAttributionLogEvent(attributionAtomTagId, timestamp, {hostUid, 400},
                                    {"tag1", "tag2"}, isolatedNonAdditiveData, hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, attributionAtomTagId, additiveFields);

    ASSERT_EQ(1, (int)data.size());

    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ("tag1", actualFieldValues->at(1).mValue.str_value);
    EXPECT_EQ(400, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ("tag2", actualFieldValues->at(3).mValue.str_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(isolatedAdditiveData + hostAdditiveData + hostAdditiveData,
              actualFieldValues->at(5).mValue.int_value);
}

// Test that repeated fields are treated as non-additive fields even when marked as additive.
TEST(PullerUtilTest, RepeatedAdditiveField) {
    vector<int> int32Array1 = {3, 6};
    vector<int> int32Array2 = {6, 9};

    vector<shared_ptr<LogEvent>> data = {
            // 30->22->{3,6}
            makeUidLogEvent(uidAtomTagId, timestamp, isolatedUid1, hostNonAdditiveData,
                            int32Array1),

            // 30->22->{6,9}
            makeUidLogEvent(uidAtomTagId, timestamp, isolatedUid1, hostNonAdditiveData,
                            int32Array2),

            // 20->22->{3,6}
            makeUidLogEvent(uidAtomTagId, timestamp, hostUid, hostNonAdditiveData, int32Array1),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, additiveFields);

    ASSERT_EQ(2, (int)data.size());
    // Events 1 and 3 are merged - non-additive fields, including the repeated additive field, are
    // equal.
    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(4, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(3, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(6, actualFieldValues->at(3).mValue.int_value);

    // Event 2 isn't merged - repeated additive field is not equal.
    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(4, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(6, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(9, actualFieldValues->at(3).mValue.int_value);
}

// Test that repeated uid events are sorted and merged correctly.
TEST(PullerUtilTest, RepeatedUidField) {
    vector<int> uidArray1 = {isolatedUid1, hostUid};
    vector<int> uidArray2 = {isolatedUid1, isolatedUid3};
    vector<int> uidArray3 = {isolatedUid1, hostUid, isolatedUid2};

    vector<shared_ptr<LogEvent>> data = {
            // {30, 20}->22->21
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray1, hostNonAdditiveData,
                                    hostAdditiveData),

            // {30, 3000}->22->21 (different uid, not merged)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray2, hostNonAdditiveData,
                                    hostAdditiveData),

            // {30, 20}->22->31 (different additive field, merged)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray1, hostNonAdditiveData,
                                    isolatedAdditiveData),

            // {30, 20}->32->21 (different non-additive field, not merged)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray1, isolatedNonAdditiveData,
                                    hostAdditiveData),

            // {30, 20, 40}->22->21 (different repeated uid length, not merged)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray3, hostNonAdditiveData,
                                    hostAdditiveData),

            // {30, 20}->22->21 (same as first event, merged)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray1, hostNonAdditiveData,
                                    hostAdditiveData),
    };

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, additiveFields);

    ASSERT_EQ(4, (int)data.size());
    // Events 1 and 3 and 6 are merged.
    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(4, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(hostAdditiveData + isolatedAdditiveData + hostAdditiveData,
              actualFieldValues->at(3).mValue.int_value);

    // Event 4 isn't merged - different non-additive data.
    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(4, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(isolatedNonAdditiveData, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(3).mValue.int_value);

    // Event 2 isn't merged - different uid.
    actualFieldValues = &data[2]->getValues();
    ASSERT_EQ(4, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostUid2, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(3).mValue.int_value);

    // Event 5 isn't merged - different repeated uid length.
    actualFieldValues = &data[3]->getValues();
    ASSERT_EQ(5, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(hostNonAdditiveData, actualFieldValues->at(3).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(4).mValue.int_value);
}

// Test that repeated uid events with multiple repeated non-additive fields are sorted and merged
// correctly.
TEST(PullerUtilTest, MultipleRepeatedFields) {
    vector<int> uidArray1 = {isolatedUid1, hostUid};
    vector<int> uidArray2 = {isolatedUid1, isolatedUid3};
    vector<int> uidArray3 = {isolatedUid1, hostUid, isolatedUid2};

    vector<int> nonAdditiveArray1 = {1, 2, 3};
    vector<int> nonAdditiveArray2 = {1, 5, 3};
    vector<int> nonAdditiveArray3 = {1, 2};

    const vector<int> secondAdditiveField = {2};

    vector<shared_ptr<LogEvent>> data = {
            // TODO: Once b/224880904 is fixed, can use different additive data without
            // having the sort order messed up.

            // Event 1 {30, 20}->21->{1, 2, 3} (merged with event 4)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray1, hostAdditiveData,
                                    nonAdditiveArray1),

            // Event 2 {30, 3000}->21->{1, 2, 3} (different uid, not merged)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray2, hostAdditiveData,
                                    nonAdditiveArray1),

            // Event 3 {30, 20, 40}->21->{1, 2} (different repeated fields with total length equal
            // to event 1, merged with event 6)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray3, hostAdditiveData,
                                    nonAdditiveArray3),

            // Event 4 {30, 20}->21->{1, 2, 3} (merged with event 1)
            // TODO: once sorting bug is fixed, can change this additive field
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray1, hostAdditiveData,
                                    nonAdditiveArray1),

            // Event 5 {30, 20}->21->{1, 5, 3} (different repeated field, not merged)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray1, hostAdditiveData,
                                    nonAdditiveArray2),

            // Event 6 {30, 20, 40}->22->{1, 2} (different repeated fields with total length equal
            // to event 1, merged with event 3)
            makeRepeatedUidLogEvent(uidAtomTagId, timestamp, uidArray3, isolatedAdditiveData,
                                    nonAdditiveArray3),
    };

    // Expected event ordering after the sort:
    // Event 3 {30, 20, 40}->21->{1, 2} (total size equal to event 1, merged with event 6)
    // Event 6 {30, 20, 40}->22->{1, 2} (total size equal to event 1, merged with event 3)
    // Event 1 {30, 20}->21->{1, 2, 3}
    // Event 4 {30, 20}->21->{1, 2, 3} (merged with event 1)
    // Event 5 {30, 20}->21->{1, 5, 3} (different repeated field, not merged)
    // Event 2 {30, 3000}->21->{1, 2, 3} (different uid, not merged)

    sp<MockUidMap> uidMap = makeMockUidMap();
    mapAndMergeIsolatedUidsToHostUid(data, uidMap, uidAtomTagId, secondAdditiveField);

    ASSERT_EQ(4, (int)data.size());

    // Events 3 and 6 are merged. Not merged with event 1 because different repeated uids and
    // fields, though length is same.
    const vector<FieldValue>* actualFieldValues = &data[0]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(hostAdditiveData + isolatedAdditiveData, actualFieldValues->at(3).mValue.int_value);
    EXPECT_EQ(1, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(2, actualFieldValues->at(5).mValue.int_value);

    // Events 1 and 4 are merged.
    actualFieldValues = &data[1]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostAdditiveData + hostAdditiveData, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(1, actualFieldValues->at(3).mValue.int_value);
    EXPECT_EQ(2, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(3, actualFieldValues->at(5).mValue.int_value);

    // Event 5 isn't merged - different repeated field.
    actualFieldValues = &data[2]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostUid, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(1, actualFieldValues->at(3).mValue.int_value);
    EXPECT_EQ(5, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(3, actualFieldValues->at(5).mValue.int_value);

    // Event 2 isn't merged - different uid.
    actualFieldValues = &data[3]->getValues();
    ASSERT_EQ(6, actualFieldValues->size());
    EXPECT_EQ(hostUid, actualFieldValues->at(0).mValue.int_value);
    EXPECT_EQ(hostUid2, actualFieldValues->at(1).mValue.int_value);
    EXPECT_EQ(hostAdditiveData, actualFieldValues->at(2).mValue.int_value);
    EXPECT_EQ(1, actualFieldValues->at(3).mValue.int_value);
    EXPECT_EQ(2, actualFieldValues->at(4).mValue.int_value);
    EXPECT_EQ(3, actualFieldValues->at(5).mValue.int_value);
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
