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
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;
// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_NO_FLOW;
const android::String8 kRemoteAddress("0.0.0.0");
const int32_t kRemotePort = 1000;
const int8_t kDscp = 0;
const int8_t kRxPayload = 96;
const int8_t kTxPayload = 96;
const int8_t kSamplingRate = 8;
// AnbrParam
const int32_t kAnbrMUplinkMode = 1;
const int32_t kAnbrMDownlinkMode = 2;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 1001;
const int32_t kIntervalSec = 1500;
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

class AudioConfigTest : public ::testing::Test
{
public:
    RtcpConfig rtcp;
    AnbrMode anbr;
    AmrParams amr;
    EvsParams evs;
    AudioConfig config1;
    AudioConfig config2;
    AudioConfig config3;

protected:
    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

        amr.setAmrMode(kAmrMode);
        amr.setOctetAligned(kOctetAligned);
        amr.setMaxRedundancyMillis(kMaxRedundancyMillis);

        evs.setEvsBandwidth(kEvsBandwidth);
        evs.setEvsMode(kEvsMode);
        evs.setChannelAwareMode(kChannelAwareMode);
        evs.setUseHeaderFullOnly(kUseHeaderFullOnly);
        evs.setCodecModeRequest(kcodecModeRequest);

        anbr.setAnbrUplinkCodecMode(kAnbrMUplinkMode);
        anbr.setAnbrDownlinkCodecMode(kAnbrMDownlinkMode);

        config1.setMediaDirection(kMediaDirection);
        config1.setRemoteAddress(kRemoteAddress);
        config1.setRemotePort(kRemotePort);
        config1.setRtcpConfig(rtcp);
        config1.setDscp(kDscp);
        config1.setRxPayloadTypeNumber(kRxPayload);
        config1.setTxPayloadTypeNumber(kTxPayload);
        config1.setSamplingRateKHz(kSamplingRate);
        config1.setAnbrMode(anbr);
        config1.setPtimeMillis(kPTimeMillis);
        config1.setMaxPtimeMillis(kMaxPtimeMillis);
        config1.setDtxEnabled(kDtxEnabled);
        config1.setCodecType(kCodecType);
        config1.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        config1.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        config1.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
        config1.setAmrParams(amr);
        config1.setEvsParams(evs);
    }

    virtual void TearDown() override {}
};

TEST_F(AudioConfigTest, TestGetterSetter)
{
    EXPECT_EQ(config1.getPtimeMillis(), kPTimeMillis);
    EXPECT_EQ(config1.getMaxPtimeMillis(), kMaxPtimeMillis);
    EXPECT_EQ(config1.getDtxEnabled(), kDtxEnabled);
    EXPECT_EQ(config1.getCodecType(), kCodecType);
    EXPECT_EQ(config1.getTxDtmfPayloadTypeNumber(), kDtmfPayloadTypeNumber);
    EXPECT_EQ(config1.getRxDtmfPayloadTypeNumber(), kDtmfPayloadTypeNumber);
    EXPECT_EQ(config1.getDtmfsamplingRateKHz(), kDtmfsamplingRateKHz);
    EXPECT_EQ(config1.getAmrParams(), amr);
    EXPECT_EQ(config1.getEvsParams(), evs);
}

TEST_F(AudioConfigTest, TestParcel)
{
    android::Parcel parcel;
    config1.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    AudioConfig configTest;
    configTest.readFromParcel(&parcel);
    EXPECT_EQ(configTest, config1);
}

TEST_F(AudioConfigTest, TestAssign)
{
    AudioConfig testConfig;
    testConfig = config1;
    EXPECT_EQ(config1, testConfig);

    AudioConfig* testConfig2 = new AudioConfig(config1);
    EXPECT_EQ(config1, *testConfig2);
    delete testConfig2;
}

TEST_F(AudioConfigTest, TestEqual)
{
    config2.setMediaDirection(kMediaDirection);
    config2.setRemoteAddress(kRemoteAddress);
    config2.setRemotePort(kRemotePort);
    config2.setRtcpConfig(rtcp);
    config2.setDscp(kDscp);
    config2.setRxPayloadTypeNumber(kRxPayload);
    config2.setTxPayloadTypeNumber(kTxPayload);
    config2.setSamplingRateKHz(kSamplingRate);
    config2.setAnbrMode(anbr);
    config2.setPtimeMillis(kPTimeMillis);
    config2.setMaxPtimeMillis(kMaxPtimeMillis);
    config2.setDtxEnabled(kDtxEnabled);
    config2.setCodecType(kCodecType);
    config2.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    config2.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    config2.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
    config2.setAmrParams(amr);
    config2.setEvsParams(evs);
    EXPECT_EQ(config2, config1);
}

TEST_F(AudioConfigTest, TestNotEqual)
{
    config2.setMediaDirection(kMediaDirection);
    config2.setRemoteAddress(kRemoteAddress);
    config2.setRemotePort(2000);
    config2.setRtcpConfig(rtcp);
    config2.setDscp(kDscp);
    config2.setRxPayloadTypeNumber(kRxPayload);
    config2.setTxPayloadTypeNumber(kTxPayload);
    config2.setSamplingRateKHz(kSamplingRate);
    config2.setAnbrMode(anbr);
    config2.setPtimeMillis(kPTimeMillis);
    config2.setMaxPtimeMillis(kMaxPtimeMillis);
    config2.setDtxEnabled(kDtxEnabled);
    config2.setCodecType(kCodecType);
    config2.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    config2.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    config2.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
    config2.setAmrParams(amr);
    config2.setEvsParams(evs);

    config3.setMediaDirection(kMediaDirection);
    config3.setRemoteAddress(kRemoteAddress);
    config3.setRemotePort(kRemotePort);
    config3.setRtcpConfig(rtcp);
    config3.setDscp(kDscp);
    config3.setRxPayloadTypeNumber(kRxPayload);
    config3.setTxPayloadTypeNumber(kTxPayload);
    config3.setSamplingRateKHz(kSamplingRate);
    config3.setAnbrMode(anbr);
    config3.setPtimeMillis(kPTimeMillis);
    config3.setMaxPtimeMillis(kMaxPtimeMillis);
    config3.setDtxEnabled(false);
    config3.setCodecType(kCodecType);
    config3.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    config3.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    config3.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
    config3.setAmrParams(amr);
    config3.setEvsParams(evs);

    EXPECT_NE(config2, config1);
    EXPECT_NE(config3, config1);
}

TEST_F(AudioConfigTest, TestParcelWithoutRtcp)
{
    android::Parcel parcel;
    AudioConfig configWrite;

    configWrite.setMediaDirection(kMediaDirection);
    configWrite.setRemoteAddress(kRemoteAddress);
    configWrite.setRemotePort(kRemotePort);
    configWrite.setDscp(kDscp);
    configWrite.setRxPayloadTypeNumber(kRxPayload);
    configWrite.setTxPayloadTypeNumber(kTxPayload);
    configWrite.setSamplingRateKHz(kSamplingRate);
    configWrite.setAnbrMode(anbr);
    configWrite.setPtimeMillis(kPTimeMillis);
    configWrite.setMaxPtimeMillis(kMaxPtimeMillis);
    configWrite.setDtxEnabled(kDtxEnabled);
    configWrite.setCodecType(kCodecType);
    configWrite.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    configWrite.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    configWrite.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
    configWrite.setAmrParams(amr);
    configWrite.setEvsParams(evs);
    configWrite.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    AudioConfig configRead;
    configRead.readFromParcel(&parcel);

    EXPECT_EQ(configRead, configWrite);
    EXPECT_TRUE(configRead.getRtcpConfig().getCanonicalName() == "");
    EXPECT_EQ(configRead.getRtcpConfig().getTransmitPort(), 0);
    EXPECT_EQ(configRead.getRtcpConfig().getIntervalSec(), 0);
    EXPECT_EQ(configRead.getRtcpConfig().getRtcpXrBlockTypes(), RtcpConfig::FLAG_RTCPXR_NONE);
}

TEST_F(AudioConfigTest, TestParcelWithoutAmrParams)
{
    android::Parcel parcel;
    AudioConfig configWrite;

    configWrite.setMediaDirection(kMediaDirection);
    configWrite.setRemoteAddress(kRemoteAddress);
    configWrite.setRemotePort(kRemotePort);
    configWrite.setRtcpConfig(rtcp);
    configWrite.setDscp(kDscp);
    configWrite.setRxPayloadTypeNumber(kRxPayload);
    configWrite.setTxPayloadTypeNumber(kTxPayload);
    configWrite.setSamplingRateKHz(kSamplingRate);
    configWrite.setAnbrMode(anbr);
    configWrite.setPtimeMillis(kPTimeMillis);
    configWrite.setMaxPtimeMillis(kMaxPtimeMillis);
    configWrite.setDtxEnabled(kDtxEnabled);
    configWrite.setCodecType(kCodecType);
    configWrite.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    configWrite.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    configWrite.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
    configWrite.setEvsParams(evs);
    configWrite.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    AudioConfig configRead;
    configRead.readFromParcel(&parcel);

    EXPECT_EQ(configRead, configWrite);
    EXPECT_EQ(configRead.getAmrParams().getAmrMode(), 0);
    EXPECT_EQ(configRead.getAmrParams().getOctetAligned(), false);
    EXPECT_EQ(configRead.getAmrParams().getMaxRedundancyMillis(), 0);
}

TEST_F(AudioConfigTest, TestParcelWithoutEvsParams)
{
    android::Parcel parcel;
    AudioConfig configWrite;

    configWrite.setMediaDirection(kMediaDirection);
    configWrite.setRemoteAddress(kRemoteAddress);
    configWrite.setRemotePort(kRemotePort);
    configWrite.setRtcpConfig(rtcp);
    configWrite.setDscp(kDscp);
    configWrite.setRxPayloadTypeNumber(kRxPayload);
    configWrite.setTxPayloadTypeNumber(kTxPayload);
    configWrite.setSamplingRateKHz(kSamplingRate);
    configWrite.setAnbrMode(anbr);
    configWrite.setPtimeMillis(kPTimeMillis);
    configWrite.setMaxPtimeMillis(kMaxPtimeMillis);
    configWrite.setDtxEnabled(kDtxEnabled);
    configWrite.setCodecType(kCodecType);
    configWrite.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    configWrite.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
    configWrite.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
    configWrite.setAmrParams(amr);
    configWrite.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    AudioConfig configRead;
    configRead.readFromParcel(&parcel);

    EXPECT_EQ(configRead, configWrite);
    EXPECT_EQ(configRead.getEvsParams().getEvsBandwidth(), EvsParams::EVS_BAND_NONE);
    EXPECT_EQ(configRead.getEvsParams().getEvsMode(), 0);
    EXPECT_EQ(configRead.getEvsParams().getChannelAwareMode(), 0);
    EXPECT_EQ(configRead.getEvsParams().getUseHeaderFullOnly(), false);
    EXPECT_EQ(configRead.getEvsParams().getCodecModeRequest(), 0);
}
