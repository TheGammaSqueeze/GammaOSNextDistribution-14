/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "socket/LogEventFilter.h"

#include <gtest/gtest.h>

#include <algorithm>

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {

constexpr int kAtomIdsCount = 100;  //  Filter size setup

LogEventFilter::AtomIdSet generateAtomIds(int rangeStart, int rangeEndInclusive) {
    LogEventFilter::AtomIdSet atomIds;
    for (int i = rangeStart; i <= rangeEndInclusive; ++i) {
        atomIds.insert(i);
    }
    return atomIds;
}

bool testGuaranteedUnusedAtomsNotInUse(const LogEventFilter& filter) {
    const auto sampleIds = generateAtomIds(10000, 11000);
    bool atLeastOneInUse = false;
    for (const auto& atomId : sampleIds) {
        atLeastOneInUse |= filter.isAtomInUse(atomId);
    }
    return !atLeastOneInUse;
}

}  // namespace

TEST(LogEventFilterTest, TestEmptyFilter) {
    LogEventFilter filter;
    const auto sampleIds = generateAtomIds(1, kAtomIdsCount);
    for (const auto& atomId : sampleIds) {
        EXPECT_FALSE(filter.isAtomInUse(atomId));
    }
}

TEST(LogEventFilterTest, TestRemoveNonExistingEmptyFilter) {
    LogEventFilter filter;
    EXPECT_FALSE(filter.isAtomInUse(1));
    LogEventFilter::AtomIdSet emptyAtomIdsSet;
    EXPECT_EQ(0, filter.mTagIdsPerConsumer.size());
    EXPECT_EQ(0, filter.mLocalTagIds.size());
    filter.setAtomIds(std::move(emptyAtomIdsSet), reinterpret_cast<LogEventFilter::ConsumerId>(0));
    EXPECT_FALSE(filter.isAtomInUse(1));
    EXPECT_EQ(0, filter.mLocalTagIds.size());
    EXPECT_EQ(0, filter.mTagIdsPerConsumer.size());
}

TEST(LogEventFilterTest, TestEmptyFilterDisabled) {
    LogEventFilter filter;
    filter.setFilteringEnabled(false);
    const auto sampleIds = generateAtomIds(1, kAtomIdsCount);
    for (const auto& atomId : sampleIds) {
        EXPECT_TRUE(filter.isAtomInUse(atomId));
    }
}

TEST(LogEventFilterTest, TestNonEmptyFilterFullOverlap) {
    LogEventFilter filter;
    auto filterIds = generateAtomIds(1, kAtomIdsCount);
    filter.setAtomIds(std::move(filterIds), reinterpret_cast<LogEventFilter::ConsumerId>(0));
    EXPECT_EQ(1, filter.mTagIdsPerConsumer.size());

    // inner copy updated only during fetch if required
    EXPECT_EQ(0, filter.mLocalTagIds.size());
    const auto sampleIds = generateAtomIds(1, kAtomIdsCount);
    for (const auto& atomId : sampleIds) {
        EXPECT_TRUE(filter.isAtomInUse(atomId));
    }
    EXPECT_EQ(kAtomIdsCount, filter.mLocalTagIds.size());
}

TEST(LogEventFilterTest, TestNonEmptyFilterPartialOverlap) {
    LogEventFilter filter;
    auto filterIds = generateAtomIds(1, kAtomIdsCount);
    filter.setAtomIds(std::move(filterIds), reinterpret_cast<LogEventFilter::ConsumerId>(0));
    // extra 100 atom ids should be filtered out
    const auto sampleIds = generateAtomIds(1, kAtomIdsCount + 100);
    for (const auto& atomId : sampleIds) {
        bool const atomInUse = atomId <= kAtomIdsCount;
        EXPECT_EQ(atomInUse, filter.isAtomInUse(atomId));
    }
}

TEST(LogEventFilterTest, TestNonEmptyFilterDisabledPartialOverlap) {
    LogEventFilter filter;
    auto filterIds = generateAtomIds(1, kAtomIdsCount);
    filter.setAtomIds(std::move(filterIds), reinterpret_cast<LogEventFilter::ConsumerId>(0));
    filter.setFilteringEnabled(false);
    // extra 100 atom ids should be in use due to filter is disabled
    const auto sampleIds = generateAtomIds(1, kAtomIdsCount + 100);
    for (const auto& atomId : sampleIds) {
        EXPECT_TRUE(filter.isAtomInUse(atomId));
    }
}

TEST(LogEventFilterTest, TestMultipleConsumerOverlapIdsRemoved) {
    LogEventFilter filter;
    auto filterIds1 = generateAtomIds(1, kAtomIdsCount);
    // half of filterIds1 atom ids overlaps with filterIds2
    auto filterIds2 = generateAtomIds(kAtomIdsCount / 2, kAtomIdsCount * 2);
    filter.setAtomIds(std::move(filterIds1), reinterpret_cast<LogEventFilter::ConsumerId>(0));
    filter.setAtomIds(std::move(filterIds2), reinterpret_cast<LogEventFilter::ConsumerId>(1));
    // inner copy updated only during fetch if required
    EXPECT_EQ(0, filter.mLocalTagIds.size());
    const auto sampleIds = generateAtomIds(1, kAtomIdsCount * 2);
    for (const auto& atomId : sampleIds) {
        EXPECT_TRUE(filter.isAtomInUse(atomId));
    }
    EXPECT_EQ(kAtomIdsCount * 2, filter.mLocalTagIds.size());
    EXPECT_TRUE(testGuaranteedUnusedAtomsNotInUse(filter));

    // set empty filter for second consumer
    LogEventFilter::AtomIdSet emptyAtomIdsSet;
    filter.setAtomIds(std::move(emptyAtomIdsSet), reinterpret_cast<LogEventFilter::ConsumerId>(1));
    EXPECT_EQ(kAtomIdsCount * 2, filter.mLocalTagIds.size());
    for (const auto& atomId : sampleIds) {
        bool const atomInUse = atomId <= kAtomIdsCount;
        EXPECT_EQ(atomInUse, filter.isAtomInUse(atomId));
    }
    EXPECT_EQ(kAtomIdsCount, filter.mLocalTagIds.size());
    EXPECT_TRUE(testGuaranteedUnusedAtomsNotInUse(filter));
}

TEST(LogEventFilterTest, TestMultipleConsumerEmptyFilter) {
    LogEventFilter filter;
    auto filterIds1 = generateAtomIds(1, kAtomIdsCount);
    auto filterIds2 = generateAtomIds(kAtomIdsCount + 1, kAtomIdsCount * 2);
    filter.setAtomIds(std::move(filterIds1), reinterpret_cast<LogEventFilter::ConsumerId>(0));
    filter.setAtomIds(std::move(filterIds2), reinterpret_cast<LogEventFilter::ConsumerId>(1));
    EXPECT_EQ(2, filter.mTagIdsPerConsumer.size());
    // inner copy updated only during fetch if required
    EXPECT_EQ(0, filter.mLocalTagIds.size());
    const auto sampleIds = generateAtomIds(1, kAtomIdsCount * 2);
    for (const auto& atomId : sampleIds) {
        EXPECT_TRUE(filter.isAtomInUse(atomId));
    }
    EXPECT_EQ(kAtomIdsCount * 2, filter.mLocalTagIds.size());
    EXPECT_TRUE(testGuaranteedUnusedAtomsNotInUse(filter));

    // set empty filter for first consumer
    LogEventFilter::AtomIdSet emptyAtomIdsSet;
    filter.setAtomIds(emptyAtomIdsSet, reinterpret_cast<LogEventFilter::ConsumerId>(0));
    EXPECT_EQ(1, filter.mTagIdsPerConsumer.size());
    EXPECT_EQ(kAtomIdsCount * 2, filter.mLocalTagIds.size());
    for (const auto& atomId : sampleIds) {
        bool const atomInUse = atomId > kAtomIdsCount;
        EXPECT_EQ(atomInUse, filter.isAtomInUse(atomId));
    }
    EXPECT_EQ(kAtomIdsCount, filter.mLocalTagIds.size());
    EXPECT_TRUE(testGuaranteedUnusedAtomsNotInUse(filter));

    // set empty filter for second consumer
    filter.setAtomIds(emptyAtomIdsSet, reinterpret_cast<LogEventFilter::ConsumerId>(1));
    EXPECT_EQ(0, filter.mTagIdsPerConsumer.size());
    EXPECT_EQ(kAtomIdsCount, filter.mLocalTagIds.size());
    for (const auto& atomId : sampleIds) {
        EXPECT_FALSE(filter.isAtomInUse(atomId));
    }
    EXPECT_EQ(0, filter.mLocalTagIds.size());
    EXPECT_TRUE(testGuaranteedUnusedAtomsNotInUse(filter));
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
