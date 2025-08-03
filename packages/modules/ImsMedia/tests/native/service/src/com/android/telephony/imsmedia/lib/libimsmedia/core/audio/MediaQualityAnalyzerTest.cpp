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

#include <gtest/gtest.h>
#include <gmock/gmock.h>
#include <AudioConfig.h>
#include <RtpReceptionStats.h>
#include <ImsMediaAudioUtil.h>
#include <MediaQualityAnalyzer.h>
#include <MockBaseSessionCallback.h>
#include <ImsMediaTimer.h>

using namespace android::telephony::imsmedia;
using ::testing::_;

// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE;
const android::String8 kRemoteAddress("127.0.0.1");
const int32_t kRemotePort = 10000;
const int8_t kDscp = 0;
const int8_t kRxPayload = 96;
const int8_t kTxPayload = 96;
const int8_t kSamplingRate = 16;
// AnbrParam
const int32_t kAnbrMUplinkMode = 1;
const int32_t kAnbrMDownlinkMode = 2;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 1001;
const int32_t kIntervalSec = 3;
const int32_t kRtcpXrBlockTypes = RtcpConfig::FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK |
        RtcpConfig::FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK;

// AudioConfig
const int8_t kPTimeMillis = 20;
const int32_t kMaxPtimeMillis = 100;
const int8_t kcodecModeRequest = 15;
const bool kDtxEnabled = true;
const int32_t kCodecType = AudioConfig::CODEC_AMR_WB;
const int8_t kDtmfPayloadTypeNumber = 100;
const int8_t kDtmfsamplingRateKHz = 16;

// AmrParam
const int32_t kAmrMode = 8;
const bool kOctetAligned = false;
const int32_t kMaxRedundancyMillis = 240;

// EvsParam
const int32_t kEvsBandwidth = EvsParams::EVS_BAND_NONE;
const int32_t kEvsMode = 8;
const int8_t kChannelAwareMode = 3;
const bool kUseHeaderFullOnly = false;

// MediaQualityThreshold
const std::vector<int32_t> kRtpInactivityTimerMillis = {2000, 4000};
const int32_t kRtcpInactivityTimerMillis = 2000;
const int32_t kRtpHysteresisTimeInMillis = 2000;
const int32_t kRtpPacketLossDurationMillis = 3000;
const std::vector<int32_t> kRtpPacketLossRate = {3, 5};
const std::vector<int32_t> kRtpJitterMillis = {10, 20};

const uint32_t kTimerFactor = 100;
const uint32_t kTestingTimeInterval = 1000 / kTimerFactor;
const uint32_t kTimeWaitingMargin = 2;

class FakeMediaQualityCallback : public BaseSessionCallback
{
public:
    FakeMediaQualityCallback() {}
    virtual ~FakeMediaQualityCallback() {}

    virtual void SendEvent(int32_t type, uint64_t param1, uint64_t /*param2*/)
    {
        if (type == kAudioCallQualityChangedInd)
        {
            CallQuality* quality = reinterpret_cast<CallQuality*>(param1);

            if (quality != nullptr)
            {
                mCallQuality = *quality;
                delete quality;
            }
        }
        else if (type == kImsMediaEventMediaQualityStatus)
        {
            MediaQualityStatus* status = reinterpret_cast<MediaQualityStatus*>(param1);

            if (status != nullptr)
            {
                mStatus = *status;
                delete status;
            }
        }
        else if (type == kAudioNotifyRtpReceptionStats)
        {
            RtpReceptionStats* stats = reinterpret_cast<RtpReceptionStats*>(param1);

            if (stats != nullptr)
            {
                mStats = *stats;
                delete stats;
            }
        }
    }

    virtual void onEvent(int32_t /* type */, uint64_t /* param1 */, uint64_t /* param2 */) {}
    CallQuality getCallQuality() { return mCallQuality; }
    MediaQualityStatus getMediaQualityStatus() { return mStatus; }
    RtpReceptionStats getRtpReceptionStats() { return mStats; }

private:
    CallQuality mCallQuality;
    MediaQualityStatus mStatus;
    RtpReceptionStats mStats;
};

class MediaQualityAnalyzerTest : public ::testing::Test
{
public:
    MediaQualityAnalyzerTest() { mAnalyzer = nullptr; }
    virtual ~MediaQualityAnalyzerTest() {}

protected:
    MediaQualityAnalyzer* mAnalyzer;
    AudioConfig mConfig;
    RtcpConfig mRtcpConfig;
    AnbrMode anbr;
    AmrParams mAmrParam;
    EvsParams mEvsParam;
    FakeMediaQualityCallback mFakeCallback;
    MockBaseSessionCallback mCallback;
    ImsMediaCondition mCondition;

    virtual void SetUp() override
    {
        mCallback.SetDelegate(&mFakeCallback);
        mCallback.DelegateToFake();

        mAnalyzer = new MediaQualityAnalyzer();
        mRtcpConfig.setCanonicalName(kCanonicalName);
        mRtcpConfig.setTransmitPort(kTransmitPort);
        mRtcpConfig.setIntervalSec(kIntervalSec);
        mRtcpConfig.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

        mAmrParam.setAmrMode(kAmrMode);
        mAmrParam.setOctetAligned(kOctetAligned);
        mAmrParam.setMaxRedundancyMillis(kMaxRedundancyMillis);

        mEvsParam.setEvsBandwidth(kEvsBandwidth);
        mEvsParam.setEvsMode(kEvsMode);
        mEvsParam.setChannelAwareMode(kChannelAwareMode);
        mEvsParam.setUseHeaderFullOnly(kUseHeaderFullOnly);
        mEvsParam.setCodecModeRequest(kcodecModeRequest);

        anbr.setAnbrUplinkCodecMode(kAnbrMUplinkMode);
        anbr.setAnbrDownlinkCodecMode(kAnbrMDownlinkMode);

        mConfig.setMediaDirection(kMediaDirection);
        mConfig.setRemoteAddress(kRemoteAddress);
        mConfig.setRemotePort(kRemotePort);
        mConfig.setRtcpConfig(mRtcpConfig);
        mConfig.setDscp(kDscp);
        mConfig.setRxPayloadTypeNumber(kRxPayload);
        mConfig.setTxPayloadTypeNumber(kTxPayload);
        mConfig.setSamplingRateKHz(kSamplingRate);
        mConfig.setAnbrMode(anbr);
        mConfig.setPtimeMillis(kPTimeMillis);
        mConfig.setMaxPtimeMillis(kMaxPtimeMillis);
        mConfig.setDtxEnabled(kDtxEnabled);
        mConfig.setCodecType(kCodecType);
        mConfig.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        mConfig.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        mConfig.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
        mConfig.setAmrParams(mAmrParam);
        mConfig.setEvsParams(mEvsParam);

        mAnalyzer->setEventTimeFactor(kTimerFactor);  // speed up the event interval 100 times
        mAnalyzer->setCallback(&mCallback);
        mAnalyzer->setConfig(&mConfig);
        mCondition.reset();
    }

    virtual void TearDown() override
    {
        if (mAnalyzer != nullptr)
        {
            delete mAnalyzer;
        }
    }
};

TEST_F(MediaQualityAnalyzerTest, TestDeleteWithoutStop)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    mAnalyzer->start();
}

TEST_F(MediaQualityAnalyzerTest, TestCodecType)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(8);
    mConfig.setCodecType(0);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->start();
    mAnalyzer->stop();
    EXPECT_EQ(CallQuality::AUDIO_QUALITY_NONE, mFakeCallback.getCallQuality().getCodecType());

    mConfig.setCodecType(AudioConfig::CODEC_AMR_WB);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->start();
    mAnalyzer->stop();
    EXPECT_EQ(CallQuality::AUDIO_QUALITY_AMR_WB, mFakeCallback.getCallQuality().getCodecType());

    mConfig.setCodecType(AudioConfig::CODEC_AMR);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->start();
    mAnalyzer->stop();
    EXPECT_EQ(CallQuality::AUDIO_QUALITY_AMR, mFakeCallback.getCallQuality().getCodecType());

    mConfig.setCodecType(AudioConfig::CODEC_EVS);
    mEvsParam.setEvsBandwidth(kEvsBandwidthNone);  // error
    mConfig.setEvsParams(mEvsParam);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->start();
    mAnalyzer->stop();
    EXPECT_EQ(CallQuality::AUDIO_QUALITY_NONE, mFakeCallback.getCallQuality().getCodecType());

    mConfig.setCodecType(AudioConfig::CODEC_EVS);
    mEvsParam.setEvsBandwidth(EvsParams::EVS_NARROW_BAND);
    mConfig.setEvsParams(mEvsParam);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->start();
    mAnalyzer->stop();
    EXPECT_EQ(CallQuality::AUDIO_QUALITY_EVS_NB, mFakeCallback.getCallQuality().getCodecType());

    mConfig.setCodecType(AudioConfig::CODEC_EVS);
    mEvsParam.setEvsBandwidth(EvsParams::EVS_WIDE_BAND);
    mConfig.setEvsParams(mEvsParam);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->start();
    mAnalyzer->stop();
    EXPECT_EQ(CallQuality::AUDIO_QUALITY_EVS_WB, mFakeCallback.getCallQuality().getCodecType());

    mConfig.setCodecType(AudioConfig::CODEC_EVS);
    mEvsParam.setEvsBandwidth(EvsParams::EVS_SUPER_WIDE_BAND);
    mConfig.setEvsParams(mEvsParam);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->start();
    mAnalyzer->stop();
    EXPECT_EQ(CallQuality::AUDIO_QUALITY_EVS_SWB, mFakeCallback.getCallQuality().getCodecType());

    mConfig.setCodecType(AudioConfig::CODEC_EVS);
    mEvsParam.setEvsBandwidth(EvsParams::EVS_FULL_BAND);
    mConfig.setEvsParams(mEvsParam);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->start();
    mAnalyzer->stop();
    EXPECT_EQ(CallQuality::AUDIO_QUALITY_EVS_FB, mFakeCallback.getCallQuality().getCodecType());
}

TEST_F(MediaQualityAnalyzerTest, TestCollectTxPackets)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    mAnalyzer->start();

    const int32_t kMaxStoredPacketSize = 500;
    const int32_t kNumPackets = kMaxStoredPacketSize + 10;

    for (int32_t i = 0; i < kNumPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpTx, reinterpret_cast<uint64_t>(packet));
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), kMaxStoredPacketSize);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);
    mAnalyzer->stop();

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsTransmitted(), kNumPackets);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectRxPacketsSid)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    mAnalyzer->start();

    const int32_t kMaxStoredPacketSize = 500;
    const int32_t kNumPackets = kMaxStoredPacketSize + 10;

    for (int32_t i = 0; i < kNumPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();
        packet->rtpDataType = kRtpDataTypeSid;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), kMaxStoredPacketSize);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);
    mAnalyzer->stop();

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), kNumPackets);
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpSidPacketsReceived(), kNumPackets);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectOptionalInfoAudioPlayingStatus)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    mAnalyzer->start();

    const int32_t kNumVoice = 10;
    const int32_t kNumNoData = 10;

    for (int32_t i = 0; i < kNumVoice; i++)
    {
        mAnalyzer->SendEvent(kRequestAudioPlayingStatus, kAudioTypeVoice, 0);
    }

    for (int32_t i = 0; i < kNumNoData; i++)
    {
        mAnalyzer->SendEvent(kRequestAudioPlayingStatus, kAudioTypeNoData, 0);
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumVoiceFrames(), kNumVoice);
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumNoDataFrames(), kNumNoData);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectOptionalInfoRoundTripDelay)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    mAnalyzer->start();

    const int32_t kNumTotal = 10;
    uint32_t averageRoundTripDelay = 0;
    uint32_t sumRoundTripDelay = 0;

    for (int32_t i = 0; i < kNumTotal; i++)
    {
        uint32_t roundTripDelay = i * 10;
        mAnalyzer->SendEvent(kRequestRoundTripTimeDelayUpdate, roundTripDelay, 0);
        sumRoundTripDelay += roundTripDelay;
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    averageRoundTripDelay = sumRoundTripDelay / kNumTotal;

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getAverageRoundTripTime(), averageRoundTripDelay);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectOptionalInfoLoss)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    mAnalyzer->start();

    const int32_t kNumLostPacket = 510;
    const int32_t kNumPackets = 2;

    RtpPacket* packet1 = new RtpPacket();
    packet1->seqNum = 0;
    mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet1));

    RtpPacket* packet2 = new RtpPacket();
    packet2->seqNum = kNumLostPacket + 1;
    mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet2));

    for (int32_t i = 1; i <= kNumLostPacket; i++)
    {
        SessionCallbackParameter* param = new SessionCallbackParameter(kReportPacketLossGap, i, 1);
        mAnalyzer->SendEvent(kCollectOptionalInfo, reinterpret_cast<uint64_t>(param), 0);
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), kNumPackets);
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsNotReceived(), kNumLostPacket);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectRxStatusError)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(3);
    mAnalyzer->start();
    mAnalyzer->SendEvent(kCollectRxRtpStatus, 0);

    const int32_t kNumPackets = 10;

    for (int32_t i = 0; i < kNumPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();
        packet->seqNum = i;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));

        SessionCallbackParameter* param = new SessionCallbackParameter(100 + i, 0, 0);
        mAnalyzer->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param));
    }

    mCondition.wait_timeout(5 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), kNumPackets);
    EXPECT_EQ(mFakeCallback.getCallQuality().getRtpInactivityDetected(), true);
    EXPECT_EQ(mFakeCallback.getCallQuality().getDownlinkCallQualityLevel(),
            CallQuality::kCallQualityBad);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectRxStatusDefault)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(3);
    mAnalyzer->start();
    mAnalyzer->SendEvent(kCollectRxRtpStatus, 0);

    const int32_t kNumPackets = 10;

    for (int32_t i = 0; i < kNumPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();
        packet->seqNum = i;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));

        SessionCallbackParameter* param = new SessionCallbackParameter(i, kRtpStatusNotDefined, 0);
        mAnalyzer->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param));
    }

    mCondition.wait_timeout(5 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), kNumPackets);
    EXPECT_EQ(mFakeCallback.getCallQuality().getRtpInactivityDetected(), true);
    EXPECT_EQ(mFakeCallback.getCallQuality().getDownlinkCallQualityLevel(),
            CallQuality::kCallQualityBad);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectRxStatusNormalAfterInactivity)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(3);
    mAnalyzer->start();

    const int32_t kNumPackets = 10;

    for (int32_t i = 0; i < kNumPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();
        packet->seqNum = i;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));
    }

    // wait for 4 cycles of the timer interval
    mCondition.wait_timeout(4 * kTestingTimeInterval + kTimeWaitingMargin);

    // Check the CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), kNumPackets);
    EXPECT_EQ(mFakeCallback.getCallQuality().getRtpInactivityDetected(), true);
    EXPECT_EQ(mFakeCallback.getCallQuality().getDownlinkCallQualityLevel(),
            CallQuality::kCallQualityExcellent);

    for (int32_t i = 0; i < kNumPackets; i++)
    {
        SessionCallbackParameter* param = new SessionCallbackParameter(i, kRtpStatusNormal, 0);
        mAnalyzer->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param));
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    // Check the CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), kNumPackets);
    EXPECT_EQ(mFakeCallback.getCallQuality().getRtpInactivityDetected(), false);
    EXPECT_EQ(mFakeCallback.getCallQuality().getDownlinkCallQualityLevel(),
            CallQuality::kCallQualityExcellent);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectRxStatusDiscarded)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    mAnalyzer->start();

    const int32_t kNumPackets = 10;

    for (int32_t i = 0; i < kNumPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();
        packet->seqNum = i;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));

        SessionCallbackParameter* param = new SessionCallbackParameter(
                i, i % 2 == 0 ? kRtpStatusLate : kRtpStatusDiscarded, 0);
        mAnalyzer->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param));
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), kNumPackets);
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumDroppedRtpPackets(), kNumPackets);
}

TEST_F(MediaQualityAnalyzerTest, TestCollectRxStatusDuplicated)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    mAnalyzer->start();

    const int32_t kNumPackets = 10;

    for (int32_t i = 0; i < kNumPackets; i++)
    {
        RtpPacket* packet1 = new RtpPacket();
        packet1->seqNum = i;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet1));

        SessionCallbackParameter* param1 = new SessionCallbackParameter(i, kRtpStatusNormal, 0);
        mAnalyzer->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param1));

        RtpPacket* packet2 = new RtpPacket();
        packet2->seqNum = i;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet2));

        SessionCallbackParameter* param2 = new SessionCallbackParameter(i, kRtpStatusDuplicated, 0);
        mAnalyzer->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param2));
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), kNumPackets * 2);
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpDuplicatePackets(), kNumPackets);
}

TEST_F(MediaQualityAnalyzerTest, TestRtpInactivityNotRunning)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(2);
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(0);
    MediaQualityThreshold threshold;
    threshold.setRtpInactivityTimerMillis(std::vector<int32_t>{0});
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    threshold.setRtpInactivityTimerMillis(std::vector<int32_t>{2000});
    mConfig.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();
}

TEST_F(MediaQualityAnalyzerTest, TestRtpInactivityNoUpdateByDirection)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(2);
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(1);
    MediaQualityThreshold threshold;
    threshold.setRtpInactivityTimerMillis(std::vector<int32_t>{4000});
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);

    mConfig.setMediaDirection(RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY);

    if (!mAnalyzer->isSameConfig(&mConfig))
    {
        mAnalyzer->stop();
        mAnalyzer->start();
    }

    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();
    MediaQualityStatus quality = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(quality.getRtpInactivityTimeMillis(), 4000);
}

TEST_F(MediaQualityAnalyzerTest, TestRtpInactivityUpdateByDirection)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(2);
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(1);
    MediaQualityThreshold threshold;
    threshold.setRtpInactivityTimerMillis(std::vector<int32_t>{2000});
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);

    mConfig.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);

    if (!mAnalyzer->isSameConfig(&mConfig))
    {
        mAnalyzer->stop();
        mAnalyzer->start();
    }

    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();
}

TEST_F(MediaQualityAnalyzerTest, TestRtpInactivityUpdate)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(3);
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(3);
    MediaQualityThreshold threshold;
    threshold.setRtpInactivityTimerMillis(kRtpInactivityTimerMillis);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);

    // Check MediaQualityStatus value
    MediaQualityStatus quality1 = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(quality1.getRtpInactivityTimeMillis(), 2000);

    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);

    // Check MediaQualityStatus value
    MediaQualityStatus quality2 = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(quality2.getRtpInactivityTimeMillis(), 4000);

    RtpPacket* packet = new RtpPacket();
    packet->seqNum = 0;
    mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));

    mCondition.wait_timeout(3 * kTestingTimeInterval + kTimeWaitingMargin);

    MediaQualityStatus quality3 = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(quality3.getRtpInactivityTimeMillis(), 2000);
    mAnalyzer->stop();
}

TEST_F(MediaQualityAnalyzerTest, TestRtcpInactivityNotRunning)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(3);
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(0);
    MediaQualityThreshold threshold;
    threshold.setRtcpInactivityTimerMillis(0);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    threshold.setRtcpInactivityTimerMillis(2000);
    mConfig.setMediaDirection(RtpConfig::MEDIA_DIRECTION_NO_FLOW);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    threshold.setRtcpInactivityTimerMillis(2000);
    mRtcpConfig.setIntervalSec(0);
    mConfig.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    mConfig.setRtcpConfig(mRtcpConfig);
    mAnalyzer->setConfig(&mConfig);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();
}

TEST_F(MediaQualityAnalyzerTest, TestRtcpInactivity)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(3);
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(3);
    MediaQualityThreshold threshold;
    threshold.setRtcpInactivityTimerMillis(kRtcpInactivityTimerMillis);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);

    // Check MediaQualityStatus value
    MediaQualityStatus quality1 = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(quality1.getRtcpInactivityTimeMillis(), 2000);

    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);

    // Check MediaQualityStatus value
    MediaQualityStatus quality2 = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(quality2.getRtcpInactivityTimeMillis(), 2000);

    mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtcp);
    mCondition.wait_timeout(3 * kTestingTimeInterval + kTimeWaitingMargin);

    MediaQualityStatus quality3 = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(quality3.getRtcpInactivityTimeMillis(), 2000);
    mAnalyzer->stop();
}

TEST_F(MediaQualityAnalyzerTest, TestCallQualityInactivity)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(2);
    mAnalyzer->start();
    mCondition.wait_timeout(4 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();

    // Check CallQuality value
    EXPECT_TRUE(mFakeCallback.getCallQuality().getRtpInactivityDetected());
}

TEST_F(MediaQualityAnalyzerTest, TestCallQualityLevelChanged)
{
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(2);
    mAnalyzer->start();

    const int32_t numPackets = 10;
    const int32_t jitter = 10;

    for (int32_t i = 0; i < numPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();

        if (i == 5)  // make 10% loss rate
        {
            continue;
        }

        packet->seqNum = i;
        packet->jitter = jitter;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));

        SessionCallbackParameter* param = new SessionCallbackParameter(
                i, kRtpStatusNormal, ImsMediaTimer::GetTimeInMilliSeconds());
        mAnalyzer->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param));
    }

    SessionCallbackParameter* param = new SessionCallbackParameter(kReportPacketLossGap, 5, 1);
    mAnalyzer->SendEvent(kCollectOptionalInfo, reinterpret_cast<uint64_t>(param), 0);

    mCondition.wait_timeout(5 * kTestingTimeInterval + kTimeWaitingMargin);

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), numPackets - 1);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 1);
    mAnalyzer->stop();

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    // Check CallQuality value
    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), numPackets - 1);
    EXPECT_EQ(mFakeCallback.getCallQuality().getDownlinkCallQualityLevel(),
            CallQuality::kCallQualityBad);
}

TEST_F(MediaQualityAnalyzerTest, TestJitterInd)
{
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(1);
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    MediaQualityThreshold threshold;
    threshold.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
    threshold.setRtpJitterMillis(kRtpJitterMillis);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();

    const int32_t numPackets = 20;
    const int32_t jitter = 20;
    const uint32_t ssrc = 10000;

    for (int32_t i = 0; i < numPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();
        packet->seqNum = i;
        packet->jitter = jitter;
        packet->ssrc = ssrc;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), numPackets);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    mAnalyzer->stop();

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsReceived(), numPackets);
    EXPECT_EQ(mFakeCallback.getCallQuality().getAverageRelativeJitter(), jitter);

    MediaQualityStatus status = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(status.getRtpJitterMillis(), jitter);
}

TEST_F(MediaQualityAnalyzerTest, TestSsrcChange)
{
    mAnalyzer->start();

    const int32_t numPackets = 20;
    const int32_t jitter = 20;
    const uint32_t ssrc1 = 10000;
    const uint32_t ssrc2 = 20000;

    for (int32_t i = 0; i < numPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();
        packet->seqNum = i;
        packet->jitter = jitter;
        packet->ssrc = ssrc1;

        if (i >= 5)
        {
            packet->ssrc = ssrc2;
        }

        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));
    }

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), numPackets);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    mAnalyzer->stop();

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);
}

TEST_F(MediaQualityAnalyzerTest, TestNoPacketLossInDuration)
{
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(0);
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    MediaQualityThreshold threshold;
    threshold.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
    threshold.setRtpPacketLossDurationMillis(kRtpPacketLossDurationMillis);
    threshold.setRtpPacketLossRate(kRtpPacketLossRate);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();

    const int32_t numPackets = 10;

    for (int32_t i = 0; i < numPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();

        if (i == 5 || i == 6)  // make 20% loss rate
        {
            continue;
        }

        packet->seqNum = i;
        packet->jitter = 10;
        packet->arrival = ImsMediaTimer::GetTimeInMilliSeconds() - kRtpPacketLossDurationMillis +
                kTimeWaitingMargin;
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));
    }

    SessionCallbackParameter* param = new SessionCallbackParameter(kReportPacketLossGap, 5, 2);
    mAnalyzer->SendEvent(kCollectOptionalInfo, reinterpret_cast<uint64_t>(param), 0);

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), numPackets - 2);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 2);

    mAnalyzer->stop();

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsNotReceived(), 2);

    MediaQualityStatus status = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(status.getRtpPacketLossRate(), 0);
}

TEST_F(MediaQualityAnalyzerTest, TestPacketLossInd)
{
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(1);
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    MediaQualityThreshold threshold;
    threshold.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
    threshold.setRtpPacketLossDurationMillis(kRtpPacketLossDurationMillis);
    threshold.setRtpPacketLossRate(kRtpPacketLossRate);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();

    const int32_t numPackets = 10;

    for (int32_t i = 0; i < numPackets; i++)
    {
        RtpPacket* packet = new RtpPacket();

        if (i == 5 || i == 6)  // make 20% loss rate
        {
            continue;
        }

        packet->seqNum = i;
        packet->jitter = 10;
        packet->arrival = ImsMediaTimer::GetTimeInMilliSeconds();
        mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));
    }

    SessionCallbackParameter* param = new SessionCallbackParameter(kReportPacketLossGap, 5, 2);
    mAnalyzer->SendEvent(kCollectOptionalInfo, reinterpret_cast<uint64_t>(param), 0);

    mCondition.wait_timeout(kTestingTimeInterval + kTimeWaitingMargin);

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), numPackets - 2);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 2);

    mAnalyzer->stop();

    EXPECT_EQ(mAnalyzer->getTxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getRxPacketSize(), 0);
    EXPECT_EQ(mAnalyzer->getLostPacketSize(), 0);

    EXPECT_EQ(mFakeCallback.getCallQuality().getNumRtpPacketsNotReceived(), 2);

    MediaQualityStatus status = mFakeCallback.getMediaQualityStatus();
    EXPECT_EQ(status.getRtpPacketLossRate(), 20);
}

TEST_F(MediaQualityAnalyzerTest, TestNotifyMediaQualityStatus)
{
    EXPECT_CALL(mCallback, onEvent(kImsMediaEventMediaQualityStatus, _, _)).Times(1);
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    MediaQualityThreshold threshold;
    threshold.setNotifyCurrentStatus(true);
    mAnalyzer->setMediaQualityThreshold(threshold);
    mAnalyzer->start();
    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);
    mAnalyzer->stop();
}

TEST_F(MediaQualityAnalyzerTest, TestNotifyRtpReceptionStats)
{
    const uint32_t kNtpH = 0x12341234;
    const uint32_t kNtpL = 0x56785678;
    const uint32_t kRtcpSrTimestamp = 0x12345678;
    const uint32_t kRtpTimestamp = 0x12345678;
    const uint32_t kSsrc = 0x1234;
    const uint32_t kJitter = 100;
    const uint32_t kSeq = 200;
    const uint32_t kTimeArrival = 100;
    const uint32_t kTimePlayed = 200;
    const uint32_t kRoundTripTime = 100;

    mAnalyzer->setNotifyRtpReceptionStatsInterval(2000);
    EXPECT_CALL(mCallback, onEvent(kAudioCallQualityChangedInd, _, _)).Times(1);
    EXPECT_CALL(mCallback, onEvent(kAudioNotifyRtpReceptionStats, _, _)).Times(1);

    mAnalyzer->start();

    RtcpSr* rtcpSr = new RtcpSr(kNtpH, kNtpL, kRtcpSrTimestamp, 1, 1,
            RtcpRecvReport(kSsrc, 0, 0, 65535, 100, 1234, 100));
    mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtcp, reinterpret_cast<uint64_t>(rtcpSr));

    RtpPacket* packet =
            new RtpPacket(kSsrc, kSeq, 0, kJitter, kRtpTimestamp, kTimeArrival, kRtpDataTypeNormal);
    mAnalyzer->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));

    SessionCallbackParameter* param =
            new SessionCallbackParameter(kSeq, kRtpStatusNormal, kTimePlayed);
    mAnalyzer->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param));
    mAnalyzer->SendEvent(kRequestRoundTripTimeDelayUpdate, kRoundTripTime, 0);

    uint64_t ntp = static_cast<uint64_t>(kNtpH) << 32 | kNtpL;

    mCondition.wait_timeout(2 * kTestingTimeInterval + kTimeWaitingMargin);

    RtpReceptionStats stats1(
            kRtpTimestamp, kRtcpSrTimestamp, ntp, kTimePlayed - kTimeArrival, kRoundTripTime);

    EXPECT_EQ(stats1, mFakeCallback.getRtpReceptionStats());
    mAnalyzer->stop();
}