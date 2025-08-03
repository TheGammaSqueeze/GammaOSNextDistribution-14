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
#include <ImsMediaNetworkUtil.h>
#include <TextConfig.h>
#include <MediaQualityThreshold.h>
#include <TextStreamGraphRtpRx.h>

using namespace android::telephony::imsmedia;

// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY;
const android::String8 kRemoteAddress("127.0.0.1");
const int32_t kRemotePort = 10000;
const int8_t kDscp = 0;
const int8_t kRxPayload = 96;
const int8_t kTxPayload = 96;
const int8_t kSamplingRate = 16;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 1001;
const int32_t kIntervalSec = 5;
const int32_t kRtcpXrBlockTypes = RtcpConfig::FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK |
        RtcpConfig::FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK;

// TextConfig
const int32_t kCodecType = TextConfig::TEXT_T140_RED;
const int32_t kBitrate = 100;
const int8_t kRedundantPayload = 102;
const int8_t kRedundantLevel = 2;
const bool kKeepRedundantLevel = true;

// inactivity timer
const std::vector<int32_t> kRtpInactivityTimerMillis = {10000};

class TextStreamGraphRtpRxTest : public ::testing::Test
{
public:
    TextStreamGraphRtpRxTest()
    {
        graph = nullptr;
        socketRtpFd = -1;
    }
    virtual ~TextStreamGraphRtpRxTest() {}

protected:
    TextStreamGraphRtpRx* graph;
    TextConfig config;
    RtcpConfig rtcp;
    MediaQualityThreshold threshold;
    int socketRtpFd;

    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);
        threshold.setRtpInactivityTimerMillis(kRtpInactivityTimerMillis);
        config.setMediaDirection(kMediaDirection);
        config.setRemoteAddress(kRemoteAddress);
        config.setRemotePort(kRemotePort);
        config.setRtcpConfig(rtcp);
        config.setDscp(kDscp);
        config.setRxPayloadTypeNumber(kRxPayload);
        config.setTxPayloadTypeNumber(kTxPayload);
        config.setSamplingRateKHz(kSamplingRate);
        config.setCodecType(kCodecType);
        config.setBitrate(kBitrate);
        config.setRedundantPayload(kRedundantPayload);
        config.setRedundantLevel(kRedundantLevel);
        config.setKeepRedundantLevel(kKeepRedundantLevel);

        const char testIp[] = "127.0.0.1";
        unsigned int testPort = 30000;
        socketRtpFd = ImsMediaNetworkUtil::openSocket(testIp, testPort, AF_INET);
        EXPECT_NE(socketRtpFd, -1);

        graph = new TextStreamGraphRtpRx(nullptr, socketRtpFd);
    }

    virtual void TearDown() override
    {
        if (graph != nullptr)
        {
            delete graph;
        }

        if (socketRtpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtpFd);
        }
    }
};

TEST_F(TextStreamGraphRtpRxTest, TestGraphError)
{
    EXPECT_EQ(graph->create(nullptr), RESULT_INVALID_PARAM);
    EXPECT_EQ(graph->getState(), kStreamStateIdle);
}

TEST_F(TextStreamGraphRtpRxTest, TestGraphSetMediaThresholdFail)
{
    EXPECT_EQ(graph->setMediaQualityThreshold(&threshold), false);
}

TEST_F(TextStreamGraphRtpRxTest, TestRtpRxStreamDirectionUpdate)
{
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->setMediaQualityThreshold(&threshold), true);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(graph->update(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE);
    EXPECT_EQ(graph->update(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_NO_FLOW);
    EXPECT_EQ(graph->update(&config), RESULT_SUCCESS);

    EXPECT_EQ(graph->stop(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);
}

TEST_F(TextStreamGraphRtpRxTest, TestRtpRxStreamCodecUpdate)
{
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE);
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    config.setCodecType(TextConfig::TEXT_T140);
    EXPECT_EQ(graph->update(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    EXPECT_EQ(graph->stop(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);
}