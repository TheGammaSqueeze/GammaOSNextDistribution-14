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

#include <AudioConfig.h>
#include <CallQuality.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;

const int32_t kDownlinkCallQualityLevel = 0;
const int32_t kUplinkCallQualityLevel = 0;
const int32_t kCallDuration = 30000;
const int32_t kNumRtpPacketsTransmitted = 30 * 50;
const int32_t kNumRtpPacketsReceived = 30 * 50;
const int32_t kNumRtpPacketsTransmittedLost = 1;
const int32_t kNumRtpPacketsNotReceived = 1;
const int32_t kAverageRelativeJitter = 1;
const int32_t kMaxRelativeJitter = 5;
const int32_t kAverageRoundTripTime = 100;
const int32_t kCodecType = AudioConfig::CODEC_AMR_WB;
const bool kRtpInactivityDetected = false;
const bool kRxSilenceDetected = false;
const bool kTxSilenceDetected = false;
const int32_t kNumVoiceFrames = 30 * 50;
const int32_t kNumNoDataFrames = 0;
const int32_t kNumDroppedRtpPackets = 0;
const int64_t kMinPlayoutDelayMillis = 100;
const int64_t kMaxPlayoutDelayMillis = 180;
const int32_t kNumRtpSidPacketsReceived = 10;
const int32_t kNumRtpDuplicatePackets = 1;

class CallQualityTest : public ::testing::Test
{
public:
    CallQuality quality1;
    CallQuality quality2;
    CallQuality quality3;

protected:
    virtual void SetUp() override
    {
        quality1.setDownlinkCallQualityLevel(kDownlinkCallQualityLevel);
        quality1.setUplinkCallQualityLevel(kUplinkCallQualityLevel);
        quality1.setCallDuration(kCallDuration);
        quality1.setNumRtpPacketsTransmitted(kNumRtpPacketsTransmitted);
        quality1.setNumRtpPacketsReceived(kNumRtpPacketsReceived);
        quality1.setNumRtpPacketsTransmittedLost(kNumRtpPacketsTransmittedLost);
        quality1.setNumRtpPacketsNotReceived(kNumRtpPacketsNotReceived);
        quality1.setAverageRelativeJitter(kAverageRelativeJitter);
        quality1.setMaxRelativeJitter(kMaxRelativeJitter);
        quality1.setAverageRoundTripTime(kAverageRoundTripTime);
        quality1.setCodecType(kCodecType);
        quality1.setRtpInactivityDetected(kRtpInactivityDetected);
        quality1.setRxSilenceDetected(kRxSilenceDetected);
        quality1.setTxSilenceDetected(kTxSilenceDetected);
        quality1.setNumVoiceFrames(kNumVoiceFrames);
        quality1.setNumNoDataFrames(kNumNoDataFrames);
        quality1.setNumDroppedRtpPackets(kNumDroppedRtpPackets);
        quality1.setMinPlayoutDelayMillis(kMinPlayoutDelayMillis);
        quality1.setMaxPlayoutDelayMillis(kMaxPlayoutDelayMillis);
        quality1.setNumRtpSidPacketsReceived(kNumRtpSidPacketsReceived);
        quality1.setNumRtpDuplicatePackets(kNumRtpDuplicatePackets);
    }

    virtual void TearDown() override {}
};

TEST_F(CallQualityTest, TestGetterSetter) {}

TEST_F(CallQualityTest, TestParcel)
{
    android::Parcel parcel;
    quality1.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    CallQuality testQuality;
    testQuality.readFromParcel(&parcel);
    EXPECT_EQ(testQuality, quality1);
}

TEST_F(CallQualityTest, TestAssign)
{
    CallQuality testQuality;
    testQuality = quality1;
    EXPECT_EQ(quality1, testQuality);
}

TEST_F(CallQualityTest, TestEqual)
{
    quality2.setDownlinkCallQualityLevel(kDownlinkCallQualityLevel);
    quality2.setUplinkCallQualityLevel(kUplinkCallQualityLevel);
    quality2.setCallDuration(kCallDuration);
    quality2.setNumRtpPacketsTransmitted(kNumRtpPacketsTransmitted);
    quality2.setNumRtpPacketsReceived(kNumRtpPacketsReceived);
    quality2.setNumRtpPacketsTransmittedLost(kNumRtpPacketsTransmittedLost);
    quality2.setNumRtpPacketsNotReceived(kNumRtpPacketsNotReceived);
    quality2.setAverageRelativeJitter(kAverageRelativeJitter);
    quality2.setMaxRelativeJitter(kMaxRelativeJitter);
    quality2.setAverageRoundTripTime(kAverageRoundTripTime);
    quality2.setCodecType(kCodecType);
    quality2.setRtpInactivityDetected(kRtpInactivityDetected);
    quality2.setRxSilenceDetected(kRxSilenceDetected);
    quality2.setTxSilenceDetected(kTxSilenceDetected);
    quality2.setNumVoiceFrames(kNumVoiceFrames);
    quality2.setNumNoDataFrames(kNumNoDataFrames);
    quality2.setNumDroppedRtpPackets(kNumDroppedRtpPackets);
    quality2.setMinPlayoutDelayMillis(kMinPlayoutDelayMillis);
    quality2.setMaxPlayoutDelayMillis(kMaxPlayoutDelayMillis);
    quality2.setNumRtpSidPacketsReceived(kNumRtpSidPacketsReceived);
    quality2.setNumRtpDuplicatePackets(kNumRtpDuplicatePackets);
    EXPECT_EQ(quality2, quality1);
}

TEST_F(CallQualityTest, TestNotEqual)
{
    quality2.setDownlinkCallQualityLevel(kDownlinkCallQualityLevel);
    quality2.setUplinkCallQualityLevel(kUplinkCallQualityLevel);
    quality2.setCallDuration(kCallDuration);
    quality2.setNumRtpPacketsTransmitted(kNumRtpPacketsTransmitted);
    quality2.setNumRtpPacketsReceived(kNumRtpPacketsReceived);
    quality2.setNumRtpPacketsTransmittedLost(kNumRtpPacketsTransmittedLost);
    quality2.setNumRtpPacketsNotReceived(kNumRtpPacketsNotReceived);
    quality2.setAverageRelativeJitter(5);
    quality2.setMaxRelativeJitter(kMaxRelativeJitter);
    quality2.setAverageRoundTripTime(kAverageRoundTripTime);
    quality2.setCodecType(kCodecType);
    quality2.setRtpInactivityDetected(kRtpInactivityDetected);
    quality2.setRxSilenceDetected(kRxSilenceDetected);
    quality2.setTxSilenceDetected(kTxSilenceDetected);
    quality2.setNumVoiceFrames(kNumVoiceFrames);
    quality2.setNumNoDataFrames(kNumNoDataFrames);
    quality2.setNumDroppedRtpPackets(kNumDroppedRtpPackets);
    quality2.setMinPlayoutDelayMillis(kMinPlayoutDelayMillis);
    quality2.setMaxPlayoutDelayMillis(kMaxPlayoutDelayMillis);
    quality2.setNumRtpSidPacketsReceived(kNumRtpSidPacketsReceived);
    quality2.setNumRtpDuplicatePackets(kNumRtpDuplicatePackets);
    EXPECT_NE(quality2, quality1);

    quality3.setDownlinkCallQualityLevel(kDownlinkCallQualityLevel);
    quality3.setUplinkCallQualityLevel(kUplinkCallQualityLevel);
    quality3.setCallDuration(kCallDuration);
    quality3.setNumRtpPacketsTransmitted(kNumRtpPacketsTransmitted);
    quality3.setNumRtpPacketsReceived(kNumRtpPacketsReceived);
    quality3.setNumRtpPacketsTransmittedLost(kNumRtpPacketsTransmittedLost);
    quality3.setNumRtpPacketsNotReceived(kNumRtpPacketsNotReceived);
    quality3.setAverageRelativeJitter(kAverageRelativeJitter);
    quality3.setMaxRelativeJitter(kMaxRelativeJitter);
    quality3.setAverageRoundTripTime(kAverageRoundTripTime);
    quality3.setCodecType(AudioConfig::CODEC_AMR);
    quality3.setRtpInactivityDetected(kRtpInactivityDetected);
    quality3.setRxSilenceDetected(kRxSilenceDetected);
    quality3.setTxSilenceDetected(kTxSilenceDetected);
    quality3.setNumVoiceFrames(kNumVoiceFrames);
    quality3.setNumNoDataFrames(kNumNoDataFrames);
    quality3.setNumDroppedRtpPackets(kNumDroppedRtpPackets);
    quality3.setMinPlayoutDelayMillis(kMinPlayoutDelayMillis);
    quality3.setMaxPlayoutDelayMillis(kMaxPlayoutDelayMillis);
    quality3.setNumRtpSidPacketsReceived(kNumRtpSidPacketsReceived);
    quality3.setNumRtpDuplicatePackets(kNumRtpDuplicatePackets);
    EXPECT_NE(quality3, quality1);
}
