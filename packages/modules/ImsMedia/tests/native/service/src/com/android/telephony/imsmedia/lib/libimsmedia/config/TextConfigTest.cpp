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

#include <TextConfig.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;
// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_NO_FLOW;
const android::String8 kRemoteAddress("0.0.0.0");
const int32_t kRemotePort = 10000;
const int8_t kDscp = 0;
const int8_t kRxPayload = 100;
const int8_t kTxPayload = 100;
const int8_t kSamplingRate = 8;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 10001;
const int32_t kIntervalSec = 3;
const int32_t kRtcpXrBlockTypes = 0;

// TextConfig
const int32_t kCodecType = TextConfig::TEXT_T140_RED;
const int32_t kBitrate = 100;
const int8_t kRedundantPayload = 102;
const int8_t kRedundantLevel = 2;
const bool kKeepRedundantLevel = true;

class TextConfigTest : public ::testing::Test
{
public:
    RtcpConfig rtcp;
    TextConfig config1;
    TextConfig config2;
    TextConfig config3;

protected:
    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

        config1.setMediaDirection(kMediaDirection);
        config1.setRemoteAddress(kRemoteAddress);
        config1.setRemotePort(kRemotePort);
        config1.setRtcpConfig(rtcp);
        config1.setDscp(kDscp);
        config1.setRxPayloadTypeNumber(kRxPayload);
        config1.setTxPayloadTypeNumber(kTxPayload);
        config1.setSamplingRateKHz(kSamplingRate);
        config1.setCodecType(kCodecType);
        config1.setBitrate(kBitrate);
        config1.setRedundantPayload(kRedundantPayload);
        config1.setRedundantLevel(kRedundantLevel);
        config1.setKeepRedundantLevel(kKeepRedundantLevel);
    }

    virtual void TearDown() override {}
};

TEST_F(TextConfigTest, TestGetterSetter)
{
    EXPECT_EQ(config1.getCodecType(), kCodecType);
    EXPECT_EQ(config1.getBitrate(), kBitrate);
    EXPECT_EQ(config1.getRedundantPayload(), kRedundantPayload);
    EXPECT_EQ(config1.getRedundantLevel(), kRedundantLevel);
    EXPECT_EQ(config1.getKeepRedundantLevel(), kKeepRedundantLevel);
}

TEST_F(TextConfigTest, TestParcel)
{
    android::Parcel parcel;
    config1.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    TextConfig configTest;
    configTest.readFromParcel(&parcel);
    EXPECT_EQ(configTest, config1);
}

TEST_F(TextConfigTest, TestAssign)
{
    TextConfig testConfig;
    testConfig = config1;
    EXPECT_EQ(config1, testConfig);

    TextConfig* testConfig2 = new TextConfig(config1);
    EXPECT_EQ(config1, *testConfig2);
    delete testConfig2;
}

TEST_F(TextConfigTest, TestEqual)
{
    config2.setMediaDirection(kMediaDirection);
    config2.setRemoteAddress(kRemoteAddress);
    config2.setRemotePort(kRemotePort);
    config2.setRtcpConfig(rtcp);
    config2.setDscp(kDscp);
    config2.setRxPayloadTypeNumber(kRxPayload);
    config2.setTxPayloadTypeNumber(kTxPayload);
    config2.setSamplingRateKHz(kSamplingRate);
    config2.setCodecType(kCodecType);
    config2.setBitrate(kBitrate);
    config2.setRedundantPayload(kRedundantPayload);
    config2.setRedundantLevel(kRedundantLevel);
    config2.setKeepRedundantLevel(kKeepRedundantLevel);
    EXPECT_EQ(config2, config1);
}

TEST_F(TextConfigTest, TestNotEqual)
{
    config2.setMediaDirection(kMediaDirection);
    config2.setRemoteAddress(kRemoteAddress);
    config2.setRemotePort(kRemotePort);
    config2.setRtcpConfig(rtcp);
    config2.setDscp(kDscp);
    config2.setRxPayloadTypeNumber(kRxPayload);
    config2.setTxPayloadTypeNumber(kTxPayload);
    config2.setSamplingRateKHz(kSamplingRate);
    config2.setCodecType(kCodecType);
    config2.setBitrate(kBitrate);
    config2.setRedundantPayload(103);
    config2.setRedundantLevel(kRedundantLevel);
    config2.setKeepRedundantLevel(kKeepRedundantLevel);

    EXPECT_NE(config2, config1);
}
