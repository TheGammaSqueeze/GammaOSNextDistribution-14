/**
 * Copyright (C) 2024 The Android Open Source Project
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

#include <RtpReceptionStats.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;

TEST(RtpReceptionStatsTest, TestDefaultValues)
{
    RtpReceptionStats stats;

    EXPECT_EQ(stats.getRtpTimestamp(), 0);
    EXPECT_EQ(stats.getRtcpSrTimestamp(), 0);
    EXPECT_EQ(stats.getRtcpSrNtpTimestamp(), 0);
    EXPECT_EQ(stats.getJitterBufferMs(), 0);
    EXPECT_EQ(stats.getRoundTripTimeMs(), 0);
}

TEST(RtpReceptionStatsTest, TestConstructors)
{
    RtpReceptionStats stats1(100, 200, 300, 400, 500);
    EXPECT_EQ(stats1.getRtpTimestamp(), 100);
    EXPECT_EQ(stats1.getRtcpSrTimestamp(), 200);
    EXPECT_EQ(stats1.getRtcpSrNtpTimestamp(), 300);
    EXPECT_EQ(stats1.getJitterBufferMs(), 400);
    EXPECT_EQ(stats1.getRoundTripTimeMs(), 500);

    RtpReceptionStats stats2(stats1);
    EXPECT_EQ(stats2.getRtpTimestamp(), 100);
    EXPECT_EQ(stats2.getRtcpSrTimestamp(), 200);
    EXPECT_EQ(stats2.getRtcpSrNtpTimestamp(), 300);
    EXPECT_EQ(stats2.getJitterBufferMs(), 400);
    EXPECT_EQ(stats2.getRoundTripTimeMs(), 500);
}

TEST(RtpReceptionStatsTest, TestOperators)
{
    RtpReceptionStats stats1(10, 20, 30, 40, 50);
    RtpReceptionStats stats2(60, 70, 80, 90, 100);

    EXPECT_EQ(stats1 == stats2, false);
    EXPECT_EQ(stats1 != stats2, true);

    stats2 = stats1;
    EXPECT_EQ(stats2.getRtpTimestamp(), 10);
    EXPECT_EQ(stats2.getRtcpSrTimestamp(), 20);
    EXPECT_EQ(stats2.getRtcpSrNtpTimestamp(), 30);
    EXPECT_EQ(stats2.getJitterBufferMs(), 40);
    EXPECT_EQ(stats2.getRoundTripTimeMs(), 50);

    EXPECT_EQ(stats1 == stats2, true);
    EXPECT_EQ(stats1 != stats2, false);
}

TEST(RtpReceptionStatsTest, TestGetterSetters)
{
    RtpReceptionStats stats(100, 200, 300, 400, 500);

    EXPECT_EQ(stats.getRtpTimestamp(), 100);
    EXPECT_EQ(stats.getRtcpSrTimestamp(), 200);
    EXPECT_EQ(stats.getRtcpSrNtpTimestamp(), 300);
    EXPECT_EQ(stats.getJitterBufferMs(), 400);
    EXPECT_EQ(stats.getRoundTripTimeMs(), 500);

    stats.setDefaultConfig();
    EXPECT_EQ(stats.getRtpTimestamp(), 0);
    EXPECT_EQ(stats.getRtcpSrTimestamp(), 0);
    EXPECT_EQ(stats.getRtcpSrNtpTimestamp(), 0);
    EXPECT_EQ(stats.getJitterBufferMs(), 0);
    EXPECT_EQ(stats.getRoundTripTimeMs(), 0);
}

TEST(RtpReceptionStatsTest, TestParcel)
{
    RtpReceptionStats stats1(100, 200, 300, 400, 500);
    android::Parcel parcel;
    EXPECT_EQ(stats1.writeToParcel(nullptr), android::BAD_VALUE);
    stats1.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    RtpReceptionStats stats2;
    EXPECT_EQ(stats2.readFromParcel(nullptr), android::BAD_VALUE);
    stats2.readFromParcel(&parcel);
    EXPECT_EQ(stats1, stats2);
}