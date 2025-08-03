/**
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

#include <gtest/gtest.h>
#include <ImsMediaNetworkUtil.h>
#include <ImsMediaCondition.h>
#include <TextConfig.h>
#include <TextSession.h>

using namespace android::telephony::imsmedia;

// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE;
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

class TextSessionTest : public ::testing::Test
{
public:
    TextSessionTest()
    {
        session = nullptr;
        socketRtpFd = -1;
        socketRtcpFd = -1;
    }
    virtual ~TextSessionTest() {}

protected:
    TextSession* session;
    TextConfig config;
    RtcpConfig rtcp;
    int socketRtpFd;
    int socketRtcpFd;

    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

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

        session = new TextSession();
        const char testIp[] = "127.0.0.1";
        unsigned int testPortRtp = 30000;
        socketRtpFd = ImsMediaNetworkUtil::openSocket(testIp, testPortRtp, AF_INET);
        EXPECT_NE(socketRtpFd, -1);
        unsigned int testPortRtcp = 30001;
        socketRtcpFd = ImsMediaNetworkUtil::openSocket(testIp, testPortRtcp, AF_INET);
        EXPECT_NE(socketRtcpFd, -1);
    }

    virtual void TearDown() override
    {
        if (session != nullptr)
        {
            delete session;
        }

        if (socketRtpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtpFd);
        }

        if (socketRtcpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtcpFd);
        }
    }
};

TEST_F(TextSessionTest, testLocalEndpoint)
{
    EXPECT_EQ(session->getState(), kSessionStateOpened);
    EXPECT_EQ(session->getLocalRtpFd(), -1);
    EXPECT_EQ(session->getLocalRtcpFd(), -1);

    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->getLocalRtpFd(), socketRtpFd);
    EXPECT_EQ(session->getLocalRtcpFd(), socketRtcpFd);
}

TEST_F(TextSessionTest, testStartGraphFail)
{
    EXPECT_EQ(session->startGraph(nullptr), RESULT_INVALID_PARAM);
    EXPECT_EQ(session->getState(), kSessionStateOpened);

    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setRemoteAddress(android::String8(""));
    EXPECT_EQ(session->startGraph(&config), RESULT_INVALID_PARAM);
    EXPECT_EQ(session->getState(), kSessionStateOpened);
}

TEST_F(TextSessionTest, testStartGraphAndUpdate)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    // normal update
    config.setTxPayloadTypeNumber(120);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    // create one more graph
    config.setRemotePort(20000);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
}

TEST_F(TextSessionTest, testStartGraphSendOnly)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_ONLY);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
}

TEST_F(TextSessionTest, testStartGraphReceiveOnly)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
}

TEST_F(TextSessionTest, testStartGraphInactive)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
}

TEST_F(TextSessionTest, testStartAndHoldResume)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
}

TEST_F(TextSessionTest, testDeactivateActiveSession)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    EXPECT_EQ(session->deactivate(), true);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
}

TEST_F(TextSessionTest, testDeactivateSendonlySession)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_ONLY);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    EXPECT_EQ(session->deactivate(), true);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
}

TEST_F(TextSessionTest, testDeactivateReceiveonlySession)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    EXPECT_EQ(session->deactivate(), true);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
}

TEST_F(TextSessionTest, testDeactivateAndResumeSession)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    EXPECT_EQ(session->deactivate(), true);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
}
