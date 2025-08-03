/*
 * Copyright (C) 2023 The Android Open Source Project
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

#pragma once

#include <gtest/gtest_prod.h>

#include <atomic>
#include <mutex>
#include <unordered_map>
#include <unordered_set>

namespace android {
namespace os {
namespace statsd {

/**
 * Templating is for benchmarks only
 *
 * Based on benchmarks the more fast container to be used for atom ids filtering
 * is unordered_set<int>
 * #BM_LogEventFilterUnorderedSet                       391208 ns     390086 ns         1793
 * #BM_LogEventFilterUnorderedSet2Consumers            1293527 ns    1289326 ns          543
 * #BM_LogEventFilterSet                                613362 ns     611259 ns         1146
 * #BM_LogEventFilterSet2Consumers                     1859397 ns    1854193 ns          378
 *
 * See @LogEventFilter definition below
 */
template <typename T>
class LogEventFilterGeneric {
public:
    virtual ~LogEventFilterGeneric() = default;

    virtual void setFilteringEnabled(bool isEnabled) {
        mLogsFilteringEnabled = isEnabled;
    }

    bool getFilteringEnabled() const {
        return mLogsFilteringEnabled;
    }

    /**
     * @brief Tests atom id with list of interesting atoms
     *        If Logs filtering is disabled - assume all atoms in use
     *        Most of the time should be non-blocking call - only in case when setAtomIds() was
     *        called the call will be blocking due to atom list needs to be synced up
     * @param atomId
     * @return true if atom is used by any of consumer or filtering is disabled
     */
    virtual bool isAtomInUse(int atomId) const {
        if (!mLogsFilteringEnabled) {
            return true;
        }

        // check if there is an updated set of interesting atom ids
        if (mLocalSetUpdateCounter != mSetUpdateCounter.load(std::memory_order_relaxed)) {
            std::lock_guard<std::mutex> guard(mTagIdsMutex);
            mLocalSetUpdateCounter = mSetUpdateCounter.load(std::memory_order_relaxed);
            mLocalTagIds.swap(mTagIds);
        }
        return mLocalTagIds.find(atomId) != mLocalTagIds.end();
    }

    typedef const void* ConsumerId;

    typedef T AtomIdSet;
    /**
     * @brief Set the Atom Ids object
     *
     * @param tagIds set of atoms ids
     * @param consumer used to differentiate the consumers to form proper superset of ids
     */
    virtual void setAtomIds(AtomIdSet tagIds, ConsumerId consumer) {
        std::lock_guard lock(mTagIdsMutex);
        // update ids list from consumer
        if (tagIds.size() == 0) {
            mTagIdsPerConsumer.erase(consumer);
        } else {
            mTagIdsPerConsumer[consumer].swap(tagIds);
        }
        // populate the superset incorporating list of distinct atom ids from all consumers
        mTagIds.clear();
        for (const auto& [_, atomIds] : mTagIdsPerConsumer) {
            mTagIds.insert(atomIds.begin(), atomIds.end());
        }
        mSetUpdateCounter.fetch_add(1, std::memory_order_relaxed);
    }

private:
    std::atomic_bool mLogsFilteringEnabled = true;
    std::atomic_int mSetUpdateCounter;
    mutable int mLocalSetUpdateCounter;

    mutable std::mutex mTagIdsMutex;
    std::unordered_map<ConsumerId, AtomIdSet> mTagIdsPerConsumer;
    mutable AtomIdSet mTagIds;
    mutable AtomIdSet mLocalTagIds;

    friend class LogEventFilterTest;

    FRIEND_TEST(LogEventFilterTest, TestEmptyFilter);
    FRIEND_TEST(LogEventFilterTest, TestRemoveNonExistingEmptyFilter);
    FRIEND_TEST(LogEventFilterTest, TestEmptyFilterDisabled);
    FRIEND_TEST(LogEventFilterTest, TestNonEmptyFilterFullOverlap);
    FRIEND_TEST(LogEventFilterTest, TestNonEmptyFilterPartialOverlap);
    FRIEND_TEST(LogEventFilterTest, TestNonEmptyFilterDisabled);
    FRIEND_TEST(LogEventFilterTest, TestNonEmptyFilterDisabledPartialOverlap);
    FRIEND_TEST(LogEventFilterTest, TestMultipleConsumerOverlapIds);
    FRIEND_TEST(LogEventFilterTest, TestMultipleConsumerOverlapIdsRemoved);
    FRIEND_TEST(LogEventFilterTest, TestMultipleConsumerEmptyFilter);
};

typedef LogEventFilterGeneric<std::unordered_set<int>> LogEventFilter;

}  // namespace statsd
}  // namespace os
}  // namespace android
