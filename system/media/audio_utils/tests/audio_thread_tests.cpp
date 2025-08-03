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

#include <audio_utils/threads.h>
#include <gtest/gtest.h>

using namespace android;
using namespace android::audio_utils;

TEST(audio_thread_tests, conversion) {
    EXPECT_EQ(120, kDefaultPrio);

    EXPECT_EQ(kMaxRtPrio, nice_to_unified_priority(kMinNice));
    EXPECT_EQ(kMaxPrio - 1, nice_to_unified_priority(kMaxNice));

    EXPECT_EQ(kMinNice, unified_priority_to_nice(kMaxRtPrio));
    EXPECT_EQ(kMaxNice, unified_priority_to_nice(kMaxPrio - 1));

    EXPECT_EQ(kMaxRtPrio - 1, unified_priority_to_rtprio(0));
    EXPECT_EQ(kMinRtPrio, unified_priority_to_rtprio(98));

    EXPECT_EQ(0, rtprio_to_unified_priority(kMaxRtPrio - 1));
    EXPECT_EQ(98, rtprio_to_unified_priority(kMinRtPrio));

    EXPECT_FALSE(is_cfs_priority(kMaxRtPrio-1));
    EXPECT_TRUE(is_cfs_priority(kMaxRtPrio));  // note the bound is exclusive

    EXPECT_TRUE(is_realtime_priority(kMaxRtPrio-1));
    EXPECT_FALSE(is_realtime_priority(kMaxRtPrio));  // the bound is exclusive.
}

TEST(audio_thread_tests, priority) {
    const auto tid = gettid_wrapper();
    const int priority = get_thread_priority(tid);
    ASSERT_GE(priority, 0);

    constexpr int kPriority110 = 110;
    EXPECT_EQ(NO_ERROR, set_thread_priority(tid, kPriority110));
    EXPECT_EQ(kPriority110, get_thread_priority(tid));

    constexpr int kPriority130 = 130;
    EXPECT_EQ(NO_ERROR, set_thread_priority(tid, kPriority130));
    EXPECT_EQ(kPriority130, get_thread_priority(tid));

    // Requires privilege to go RT.
    // constexpr int kPriority98 = 98;
    // EXPECT_EQ(NO_ERROR, set_thread_priority(tid, kPriority98));
    // EXPECT_EQ(kPriority98, get_thread_priority(tid));

    EXPECT_EQ(NO_ERROR, set_thread_priority(tid, priority));
}
