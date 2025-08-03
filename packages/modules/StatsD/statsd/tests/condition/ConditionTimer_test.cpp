// Copyright (C) 2019 The Android Open Source Project
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

#include "src/condition/ConditionTimer.h"

#include <gtest/gtest.h>
#include <stdio.h>

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {

constexpr int64_t time_base = 10;
constexpr int64_t ct_start_time = 200;

static void assertConditionDurationInfo(const ConditionTimer::ConditionDurationInfo& info,
                                        ConditionTimer::ConditionDurationInfo expectedInfo) {
    // TODO(b/195646451): improve debuggability invoking macros directly in the test
    EXPECT_EQ(info, expectedInfo);
}

}  // namespace

TEST(ConditionTimerTest, TestTimer_Inital_False) {
    ConditionTimer timer(false, time_base);
    EXPECT_EQ(false, timer.mCondition);
    EXPECT_EQ(0, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time, ct_start_time),
                                {.mDurationNs = 0, .mCorrectionNs = 0});
    EXPECT_EQ(0, timer.mTimerNs);

    timer.onConditionChanged(true, ct_start_time + 5);
    EXPECT_EQ(ct_start_time + 5, timer.mLastConditionChangeTimestampNs);
    EXPECT_EQ(true, timer.mCondition);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time + 100, ct_start_time + 100),
                                {.mDurationNs = 95, .mCorrectionNs = 0});
    EXPECT_EQ(ct_start_time + 100, timer.mLastConditionChangeTimestampNs);
    EXPECT_EQ(true, timer.mCondition);
}

TEST(ConditionTimerTest, TestTimer_Inital_True) {
    ConditionTimer timer(true, time_base);
    EXPECT_EQ(true, timer.mCondition);
    EXPECT_EQ(0, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time, ct_start_time),
                                {.mDurationNs = ct_start_time - time_base, .mCorrectionNs = 0});
    EXPECT_EQ(true, timer.mCondition);
    EXPECT_EQ(0, timer.mTimerNs);
    EXPECT_EQ(ct_start_time, timer.mLastConditionChangeTimestampNs);

    timer.onConditionChanged(false, ct_start_time + 5);
    EXPECT_EQ(5, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time + 100, ct_start_time + 100),
                                {.mDurationNs = 5, .mCorrectionNs = 0});
    EXPECT_EQ(0, timer.mTimerNs);
}

TEST(ConditionTimerTest, TestTimer_Correction_DelayedChangeToFalse) {
    ConditionTimer timer(true, time_base);
    EXPECT_EQ(true, timer.mCondition);
    EXPECT_EQ(0, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time, ct_start_time),
                                {.mDurationNs = ct_start_time - time_base, .mCorrectionNs = 0});
    EXPECT_EQ(true, timer.mCondition);
    EXPECT_EQ(0, timer.mTimerNs);
    EXPECT_EQ(ct_start_time, timer.mLastConditionChangeTimestampNs);

    timer.onConditionChanged(false, ct_start_time + 7);
    EXPECT_EQ(7, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time + 7, ct_start_time + 5),
                                {.mDurationNs = 5, .mCorrectionNs = 2});
    EXPECT_EQ(2, timer.mTimerNs);
    EXPECT_EQ(2, timer.mCurrentBucketStartDelayNs);
}

TEST(ConditionTimerTest, TestTimer_Correction_DelayedChangeToTrue) {
    ConditionTimer timer(false, time_base);
    EXPECT_EQ(false, timer.mCondition);
    EXPECT_EQ(0, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time, ct_start_time),
                                {.mDurationNs = 0, .mCorrectionNs = 0});
    EXPECT_EQ(0, timer.mTimerNs);

    timer.onConditionChanged(true, ct_start_time + 7);
    EXPECT_EQ(ct_start_time + 7, timer.mLastConditionChangeTimestampNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time + 7, ct_start_time + 5),
                                {.mDurationNs = 0, .mCorrectionNs = 0});
    EXPECT_EQ(0, timer.mTimerNs);
    EXPECT_EQ(0, timer.mCurrentBucketStartDelayNs);
}

TEST(ConditionTimerTest, TestTimer_Correction_DelayedWithInitialFalse) {
    ConditionTimer timer(false, time_base);
    EXPECT_EQ(false, timer.mCondition);
    EXPECT_EQ(0, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time, ct_start_time),
                                {.mDurationNs = 0, .mCorrectionNs = 0});
    EXPECT_EQ(0, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time + 7, ct_start_time + 5),
                                {.mDurationNs = 0, .mCorrectionNs = 0});
    EXPECT_EQ(0, timer.mTimerNs);
    EXPECT_EQ(0, timer.mCurrentBucketStartDelayNs);
}

TEST(ConditionTimerTest, TestTimer_Correction_DelayedWithInitialTrue) {
    ConditionTimer timer(true, time_base);
    EXPECT_EQ(true, timer.mCondition);
    EXPECT_EQ(0, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time, ct_start_time),
                                {.mDurationNs = ct_start_time - time_base, .mCorrectionNs = 0});
    EXPECT_EQ(0, timer.mTimerNs);

    assertConditionDurationInfo(timer.newBucketStart(ct_start_time + 7, ct_start_time + 5),
                                {.mDurationNs = 5, .mCorrectionNs = 2});
    EXPECT_EQ(0, timer.mTimerNs);
    EXPECT_EQ(2, timer.mCurrentBucketStartDelayNs);
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
