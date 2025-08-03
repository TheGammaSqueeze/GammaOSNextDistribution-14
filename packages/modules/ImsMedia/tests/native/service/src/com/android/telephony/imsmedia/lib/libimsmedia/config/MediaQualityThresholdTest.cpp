/**
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

#include <MediaQualityThreshold.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;

const std::vector<int32_t> kRtpInactivityTimerMillis = {10000, 20000};
const int32_t kRtcpInactivityTimerMillis = 20000;
const int32_t kRtpHysteresisTimeInMillis = 3000;
const int32_t kRtpPacketLossDurationMillis = 5000;
const std::vector<int32_t> kRtpPacketLossRate = {3, 5};
const std::vector<int32_t> kRtpJitterMillis = {100, 200};
const bool kNotifyCurrentStatus = false;
const int32_t kVideoBitrateBps = 100000;

class MediaQualityThresholdTest : public ::testing::Test
{
public:
    MediaQualityThreshold threshold;

protected:
    virtual void SetUp() override
    {
        threshold.setRtpInactivityTimerMillis(kRtpInactivityTimerMillis);
        threshold.setRtcpInactivityTimerMillis(kRtcpInactivityTimerMillis);
        threshold.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
        threshold.setRtpPacketLossDurationMillis(kRtpPacketLossDurationMillis);
        threshold.setRtpPacketLossRate(kRtpPacketLossRate);
        threshold.setRtpJitterMillis(kRtpJitterMillis);
        threshold.setNotifyCurrentStatus(kNotifyCurrentStatus);
        threshold.setVideoBitrateBps(kVideoBitrateBps);
    }

    virtual void TearDown() override {}
};

TEST_F(MediaQualityThresholdTest, TestGetterSetter)
{
    EXPECT_EQ(threshold.getRtpInactivityTimerMillis(), kRtpInactivityTimerMillis);
    EXPECT_EQ(threshold.getRtcpInactivityTimerMillis(), kRtcpInactivityTimerMillis);
    EXPECT_EQ(threshold.getRtpHysteresisTimeInMillis(), kRtpHysteresisTimeInMillis);
    EXPECT_EQ(threshold.getRtpPacketLossDurationMillis(), kRtpPacketLossDurationMillis);
    EXPECT_EQ(threshold.getRtpPacketLossRate(), kRtpPacketLossRate);
    EXPECT_EQ(threshold.getRtpJitterMillis(), kRtpJitterMillis);
    EXPECT_EQ(threshold.getNotifyCurrentStatus(), kNotifyCurrentStatus);
    EXPECT_EQ(threshold.getVideoBitrateBps(), kVideoBitrateBps);
}

TEST_F(MediaQualityThresholdTest, TestParcel)
{
    android::Parcel parcel;
    threshold.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    MediaQualityThreshold testThreshold;
    testThreshold.readFromParcel(&parcel);
    EXPECT_EQ(testThreshold, threshold);
}

TEST_F(MediaQualityThresholdTest, TestAssign)
{
    MediaQualityThreshold threshold2;
    threshold2 = threshold;
    EXPECT_EQ(threshold, threshold2);
}

TEST_F(MediaQualityThresholdTest, TestEqual)
{
    MediaQualityThreshold threshold2;
    threshold2.setRtpInactivityTimerMillis(kRtpInactivityTimerMillis);
    threshold2.setRtcpInactivityTimerMillis(kRtcpInactivityTimerMillis);
    threshold2.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
    threshold2.setRtpPacketLossDurationMillis(kRtpPacketLossDurationMillis);
    threshold2.setRtpPacketLossRate(kRtpPacketLossRate);
    threshold2.setRtpJitterMillis(kRtpJitterMillis);
    threshold2.setNotifyCurrentStatus(kNotifyCurrentStatus);
    threshold2.setVideoBitrateBps(kVideoBitrateBps);
    EXPECT_EQ(threshold, threshold2);
}

TEST_F(MediaQualityThresholdTest, TestNotEqual)
{
    MediaQualityThreshold threshold2;
    threshold2.setRtpInactivityTimerMillis(std::vector<int32_t>{3000, 5000});
    threshold2.setRtcpInactivityTimerMillis(kRtcpInactivityTimerMillis);
    threshold2.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
    threshold2.setRtpPacketLossDurationMillis(kRtpPacketLossDurationMillis);
    threshold2.setRtpPacketLossRate(kRtpPacketLossRate);
    threshold2.setRtpJitterMillis(kRtpJitterMillis);
    threshold2.setNotifyCurrentStatus(kNotifyCurrentStatus);
    threshold2.setVideoBitrateBps(kVideoBitrateBps);

    MediaQualityThreshold threshold3;
    threshold3.setRtpInactivityTimerMillis(kRtpInactivityTimerMillis);
    threshold3.setRtcpInactivityTimerMillis(kRtcpInactivityTimerMillis);
    threshold3.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
    threshold3.setRtpPacketLossDurationMillis(kRtpPacketLossDurationMillis);
    threshold3.setRtpPacketLossRate(std::vector<int32_t>{5, 10});
    threshold3.setRtpJitterMillis(kRtpJitterMillis);
    threshold3.setNotifyCurrentStatus(kNotifyCurrentStatus);
    threshold3.setVideoBitrateBps(kVideoBitrateBps);

    EXPECT_NE(threshold, threshold2);
    EXPECT_NE(threshold, threshold3);
}