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
#include <AudioConfig.h>
#include <AudioSession.h>

using namespace android::telephony::imsmedia;

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
const int32_t kIntervalSec = 5;
const int32_t kRtcpXrBlockTypes = RtcpConfig::FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK |
        RtcpConfig::FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK;

// AudioConfig
const int8_t kPTimeMillis = 20;
const int32_t kMaxPtimeMillis = 100;
const bool kDtxEnabled = true;
const int32_t kCodecType = AudioConfig::CODEC_AMR_WB;
const int8_t kDtmfTxPayloadTypeNumber = 100;
const int8_t kDtmfRxPayloadTypeNumber = 101;
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
const int8_t kcodecModeRequest = 15;

class AudioSessionTest : public ::testing::Test
{
public:
    AudioSession* session;
    AudioConfig config;
    RtcpConfig rtcp;
    AnbrMode anbr;
    AmrParams amr;
    EvsParams evs;
    int socketRtpFd;
    int socketRtcpFd;

    AudioSessionTest()
    {
        session = nullptr;
        socketRtpFd = -1;
        socketRtcpFd = -1;
    }
    ~AudioSessionTest() {}

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

        config.setMediaDirection(kMediaDirection);
        config.setRemoteAddress(kRemoteAddress);
        config.setRemotePort(kRemotePort);
        config.setRtcpConfig(rtcp);
        config.setDscp(kDscp);
        config.setRxPayloadTypeNumber(kRxPayload);
        config.setTxPayloadTypeNumber(kTxPayload);
        config.setSamplingRateKHz(kSamplingRate);
        config.setAnbrMode(anbr);
        config.setPtimeMillis(kPTimeMillis);
        config.setMaxPtimeMillis(kMaxPtimeMillis);
        config.setDtxEnabled(kDtxEnabled);
        config.setCodecType(kCodecType);
        config.setTxDtmfPayloadTypeNumber(kDtmfTxPayloadTypeNumber);
        config.setRxDtmfPayloadTypeNumber(kDtmfRxPayloadTypeNumber);
        config.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
        config.setAmrParams(amr);
        config.setEvsParams(evs);

        session = new AudioSession();
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

TEST_F(AudioSessionTest, testLocalEndpoint)
{
    EXPECT_EQ(session->getState(), kSessionStateOpened);
    EXPECT_EQ(session->getLocalRtpFd(), -1);
    EXPECT_EQ(session->getLocalRtcpFd(), -1);

    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->getLocalRtpFd(), socketRtpFd);
    EXPECT_EQ(session->getLocalRtcpFd(), socketRtcpFd);
}

TEST_F(AudioSessionTest, testStartGraphFail)
{
    EXPECT_EQ(session->startGraph(nullptr), RESULT_INVALID_PARAM);
    EXPECT_EQ(session->getState(), kSessionStateOpened);

    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setRemoteAddress(android::String8(""));
    EXPECT_EQ(session->startGraph(&config), RESULT_INVALID_PARAM);
    EXPECT_EQ(session->getState(), kSessionStateOpened);
}

TEST_F(AudioSessionTest, testStartGraphAndUpdate)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    EXPECT_TRUE(session->IsGraphAlreadyExist(&config));

    // normal update
    config.setTxPayloadTypeNumber(120);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    // create one more graph
    config.setRemotePort(20000);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 2);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 2);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 2);

    config.setRemotePort(30000);
    EXPECT_FALSE(session->IsGraphAlreadyExist(&config));
}

TEST_F(AudioSessionTest, testStartGraphSendOnly)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_ONLY);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSending);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
}

TEST_F(AudioSessionTest, testStartGraphReceiveOnly)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateReceiving);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
}

TEST_F(AudioSessionTest, testStartGraphInactive)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
}

TEST_F(AudioSessionTest, testStartAndHoldResumeWithSameRemoteAddress)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);

    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
}

TEST_F(AudioSessionTest, testStartAndHoldResumeWithDifferentRemoteAddress)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    config.setRemotePort(20000);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(session->addGraph(&config, false), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);

    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    config.setRemotePort(30000);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE);
    EXPECT_EQ(session->addGraph(&config, false), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
}

TEST_F(AudioSessionTest, testAddGraphWithoutStartGraph)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(session->addGraph(&config, false), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);

    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    config.setRemotePort(20000);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_ONLY);
    EXPECT_EQ(session->addGraph(&config, false), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSending);

    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    config.setRemotePort(30000);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY);
    EXPECT_EQ(session->addGraph(&config, true), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateReceiving);

    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 2);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 2);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 2);
}

TEST_F(AudioSessionTest, testStartAddDeleteConfirmGraph)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    config.setRemotePort(20000);
    EXPECT_EQ(session->addGraph(&config, true), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 2);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 2);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 2);

    EXPECT_EQ(session->confirmGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
}

TEST_F(AudioSessionTest, testStartAndAddWithRtcpOff)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);

    config.setRemotePort(20000);
    EXPECT_EQ(session->addGraph(&config, false), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
}

TEST_F(AudioSessionTest, testDeactivateActiveSession)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    EXPECT_EQ(session->deactivate(), true);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
}

TEST_F(AudioSessionTest, testDeactivateSendonlySession)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_ONLY);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSending);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    EXPECT_EQ(session->deactivate(), true);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
}

TEST_F(AudioSessionTest, testDeactivateReceiveonlySession)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateReceiving);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    EXPECT_EQ(session->deactivate(), true);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
}

TEST_F(AudioSessionTest, testDeactivateAndResumeSession)
{
    session->setLocalEndPoint(socketRtpFd, socketRtcpFd);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    EXPECT_EQ(session->deactivate(), true);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateSuspended);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE);
    EXPECT_EQ(session->startGraph(&config), RESULT_SUCCESS);
    EXPECT_EQ(session->getState(), kSessionStateActive);
    EXPECT_EQ(session->getGraphSize(kStreamRtpTx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtpRx), 1);
    EXPECT_EQ(session->getGraphSize(kStreamRtcp), 1);
}
