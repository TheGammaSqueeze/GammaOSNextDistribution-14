/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// #define LOG_NDEBUG 0
#define LOG_TAG "audio_utils_mel_aggregator_tests"

#include <audio_utils/MelAggregator.h>

#include <gtest/gtest.h>
#include <gmock/gmock.h>

namespace android::audio_utils {
namespace {

constexpr int32_t kTestPortId = 1;
constexpr float kFloatError = 0.1f;
constexpr float kMelFloatError = 0.0001f;

/** Value used for CSD calculation. 3 MELs with this value will cause a change of 1% in CSD. */
constexpr float kCustomMelDbA = 107.f;

using ::testing::ElementsAre;
using ::testing::Pointwise;
using ::testing::FloatNear;

TEST(MelAggregatorTest, ResetAggregator) {
    MelAggregator aggregator{100};

    aggregator.aggregateAndAddNewMelRecord(MelRecord(1, {10.f, 10.f}, 0));
    aggregator.reset(1.f, {CsdRecord(1, 1, 1.f, 1.f)});

    EXPECT_EQ(aggregator.getCachedMelRecordsSize(), size_t{0});
    EXPECT_EQ(aggregator.getCsd(), 1.f);
    EXPECT_EQ(aggregator.getCsdRecordsSize(), size_t{1});
}

TEST(MelAggregatorTest, AggregateValuesFromDifferentStreams) {
    MelAggregator aggregator{/* csdWindowSeconds */ 100};

    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {10.f, 10.f},
                                                     /* timestamp */0));
    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {10.f, 10.f},
                                                     /* timestamp */0));

    ASSERT_EQ(aggregator.getCachedMelRecordsSize(), size_t{1});
    aggregator.foreachCachedMel([](const MelRecord &record) {
        EXPECT_EQ(record.portId, kTestPortId);
        EXPECT_THAT(record.mels, Pointwise(FloatNear(kFloatError), {13.f, 13.f}));
    });
}

TEST(MelAggregatorTest, AggregateWithOlderValues) {
    MelAggregator aggregator{/* csdWindowSeconds */ 100};

    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {1.f, 1.f},
                                                     /* timestamp */1));
    // second mel array contains values that are older than the first entry
    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {2.f, 2.f, 2.f},
                                                     /* timestamp */0));

    ASSERT_EQ(aggregator.getCachedMelRecordsSize(), size_t{1});
    aggregator.foreachCachedMel([](const MelRecord &record) {
        EXPECT_EQ(record.portId, kTestPortId);
        EXPECT_THAT(record.mels, Pointwise(FloatNear(kFloatError), {2.f, 4.5f, 4.5f}));
    });
}

TEST(MelAggregatorTest, AggregateWithNewerValues) {
    MelAggregator aggregator{/* csdWindowSeconds */ 100};

    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {1.f, 1.f},
                                                     /* timestamp */1));
    // second mel array contains values that are older than the first entry
    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {2.f, 2.f},
                                                     /* timestamp */2));

    ASSERT_EQ(aggregator.getCachedMelRecordsSize(), size_t{1});
    aggregator.foreachCachedMel([](const MelRecord &record) {
        EXPECT_EQ(record.portId, kTestPortId);
        EXPECT_THAT(record.mels, Pointwise(FloatNear(kFloatError), {1.f, 4.5f, 2.f}));
    });
}

TEST(MelAggregatorTest, AggregateWithNonOverlappingValues) {
    MelAggregator aggregator{/* csdWindowSeconds */ 100};

    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {1.f, 1.f},
                                                     /* timestamp */0));
    // second mel array contains values that are older than the first entry
    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {1.f, 1.f},
                                                     /* timestamp */2));

    ASSERT_EQ(aggregator.getCachedMelRecordsSize(), size_t{2});
    aggregator.foreachCachedMel([](const MelRecord &record) {
        EXPECT_EQ(record.portId, kTestPortId);
        EXPECT_THAT(record.mels, Pointwise(FloatNear(kFloatError), {1.f, 1.f}));
    });
}

TEST(MelAggregatorTest, CheckMelIntervalSplit) {
    MelAggregator aggregator{/* csdWindowSeconds */ 100};

    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {3.f, 3.f}, /* timestamp */1));
    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId, {3.f, 3.f, 3.f, 3.f},
                                                     /* timestamp */0));

    ASSERT_EQ(aggregator.getCachedMelRecordsSize(), size_t{1});

    aggregator.foreachCachedMel([](const MelRecord &record) {
        EXPECT_EQ(record.portId, kTestPortId);
        EXPECT_THAT(record.mels, Pointwise(FloatNear(kFloatError), {3.f, 6.f, 6.f, 3.f}));
    });
}

TEST(MelAggregatorTest, CsdRollingWindowDiscardsOldElements) {
    MelAggregator aggregator{/* csdWindowSeconds */ 3};

    aggregator.aggregateAndAddNewMelRecord(MelRecord(kTestPortId,
                                                     std::vector<float>(3, kCustomMelDbA),
                                                     /* timestamp */0));
    float csdValue = aggregator.getCsd();
    auto records = aggregator.aggregateAndAddNewMelRecord(
        MelRecord(kTestPortId, std::vector<float>(3, kCustomMelDbA), /* timestamp */3));

    EXPECT_EQ(records.size(), size_t{2});  // new record and record to remove
    EXPECT_TRUE(records[0].value * records[1].value < 0.f);
    EXPECT_EQ(csdValue, aggregator.getCsd());
    EXPECT_EQ(aggregator.getCsdRecordsSize(), size_t{1});
}

TEST(MelAggregatorTest, CsdReaches100PercWith107dB) {
    MelAggregator aggregator{/* csdWindowSeconds */ 300};

    // 287s of 107dB should produce at least 100% CSD
    auto records = aggregator.aggregateAndAddNewMelRecord(
        MelRecord(kTestPortId, std::vector<float>(288, kCustomMelDbA), /* timestamp */0));

    // each record should have a CSD value between 1% and 2%
    EXPECT_GE(records.size(), size_t{50});
    EXPECT_GE(aggregator.getCsd(), 1.f);
}

TEST(MelAggregatorTest, CsdReaches100PercWith80dB) {
    constexpr int64_t seconds40h = 40*3600;
    MelAggregator aggregator{seconds40h};

    // 40h of 80dB should produce (near) exactly 100% CSD
    auto records = aggregator.aggregateAndAddNewMelRecord(
        MelRecord(kTestPortId,
                  std::vector<float>(seconds40h, 80.0f),
            /* timestamp */0));

    // each record should have a CSD value between 1% and 2%
    EXPECT_GE(records.size(), size_t{50});
    EXPECT_NEAR(aggregator.getCsd(), 1.f, kMelFloatError);
}

}  // namespace
}  // namespace android
