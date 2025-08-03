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
#include <VideoConfig.h>
#include <MediaQualityThreshold.h>
#include <VideoStreamGraphRtcp.h>
#include <ImsMediaVideoUtil.h>

using namespace android::telephony::imsmedia;

// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_INACTIVE;
const android::String8 kRemoteAddress("127.0.0.1");
const int32_t kRemotePort = 10000;
const int32_t kMtu = 1300;
const int8_t kDscp = 0;
const int8_t kRxPayload = 102;
const int8_t kTxPayload = 102;
const int8_t kSamplingRate = 90;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 10001;
const int32_t kIntervalSec = 5;
const int32_t kRtcpXrBlockTypes = RtcpConfig::FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK |
        RtcpConfig::FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK;

// VideoConfig
const int32_t kVideoMode = VideoConfig::VIDEO_MODE_PREVIEW;
const int32_t kCodecType = VideoConfig::CODEC_AVC;
const int32_t kFramerate = DEFAULT_FRAMERATE;
const int32_t kBitrate = DEFAULT_BITRATE;
const int32_t kCodecProfile = VideoConfig::AVC_PROFILE_BASELINE;
const int32_t kCodecLevel = VideoConfig::AVC_LEVEL_12;
const int32_t kIntraFrameIntervalSec = 1;
const int32_t kPacketizationMode = VideoConfig::MODE_NON_INTERLEAVED;
const int32_t kCameraId = 0;
const int32_t kCameraZoom = 10;
const int32_t kResolutionWidth = DEFAULT_RESOLUTION_WIDTH;
const int32_t kResolutionHeight = DEFAULT_RESOLUTION_HEIGHT;
const android::String8 kPauseImagePath("data/user_de/0/com.android.telephony.imsmedia/test.jpg");
const int32_t kDeviceOrientationDegree = 0;
const int32_t kCvoValue = 1;
const int32_t kRtcpFbTypes = VideoConfig::RTP_FB_NACK | VideoConfig::RTP_FB_TMMBR |
        VideoConfig::RTP_FB_TMMBN | VideoConfig::PSFB_PLI | VideoConfig::PSFB_FIR;

class VideoStreamGraphRtcpTest : public ::testing::Test
{
public:
    VideoStreamGraphRtcpTest()
    {
        graph = nullptr;
        socketRtcpFd = -1;
    }
    virtual ~VideoStreamGraphRtcpTest() {}

protected:
    VideoStreamGraphRtcp* graph;
    VideoConfig config;
    RtcpConfig rtcp;
    MediaQualityThreshold threshold;
    int socketRtcpFd;

    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);
        threshold.setRtcpInactivityTimerMillis(10000);
        config.setMediaDirection(kMediaDirection);
        config.setRemoteAddress(kRemoteAddress);
        config.setRemotePort(kRemotePort);
        config.setRtcpConfig(rtcp);
        config.setMaxMtuBytes(kMtu);
        config.setDscp(kDscp);
        config.setRxPayloadTypeNumber(kRxPayload);
        config.setTxPayloadTypeNumber(kTxPayload);
        config.setSamplingRateKHz(kSamplingRate);
        config.setVideoMode(kVideoMode);
        config.setCodecType(kCodecType);
        config.setFramerate(kFramerate);
        config.setBitrate(kBitrate);
        config.setCodecProfile(kCodecProfile);
        config.setCodecLevel(kCodecLevel);
        config.setIntraFrameInterval(kIntraFrameIntervalSec);
        config.setPacketizationMode(kPacketizationMode);
        config.setCameraId(kCameraId);
        config.setCameraZoom(kCameraZoom);
        config.setResolutionWidth(kResolutionWidth);
        config.setResolutionHeight(kResolutionHeight);
        config.setPauseImagePath(kPauseImagePath);
        config.setDeviceOrientationDegree(kDeviceOrientationDegree);
        config.setCvoValue(kCvoValue);
        config.setRtcpFbType(kRtcpFbTypes);

        const char testIp[] = "127.0.0.1";
        unsigned int testPort = 30000;
        socketRtcpFd = ImsMediaNetworkUtil::openSocket(testIp, testPort, AF_INET);
        EXPECT_NE(socketRtcpFd, -1);

        graph = new VideoStreamGraphRtcp(nullptr, socketRtcpFd);

        /*
         * TODO: Below line will skip all test under this class, need to remove
         * to include it in atets
         */

        GTEST_SKIP();
    }

    virtual void TearDown() override
    {
        if (graph != nullptr)
        {
            delete graph;
        }

        if (socketRtcpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtcpFd);
        }
    }
};

TEST_F(VideoStreamGraphRtcpTest, TestGraphError)
{
    EXPECT_EQ(graph->create(nullptr), RESULT_INVALID_PARAM);
    EXPECT_EQ(graph->getState(), kStreamStateIdle);
}

TEST_F(VideoStreamGraphRtcpTest, TestGraphSetMediaThresholdFail)
{
    EXPECT_EQ(graph->setMediaQualityThreshold(&threshold), false);
}

TEST_F(VideoStreamGraphRtcpTest, TestRtcpStreamAndUpdate)
{
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->setMediaQualityThreshold(&threshold), true);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    EXPECT_EQ(graph->update(nullptr), RESULT_INVALID_PARAM);

    rtcp.setIntervalSec(5);
    config.setRtcpConfig(rtcp);
    EXPECT_EQ(graph->update(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_NO_FLOW);
    EXPECT_EQ(graph->update(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);

    config.setMediaDirection(RtpConfig::MEDIA_DIRECTION_INACTIVE);
    EXPECT_EQ(graph->update(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    EXPECT_EQ(graph->stop(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);
}

TEST_F(VideoStreamGraphRtcpTest, TestRtcpStreamInternalEvent)
{
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->setMediaQualityThreshold(&threshold), true);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    InternalRequestEventParam* nackEvent =
            new InternalRequestEventParam(kRequestVideoSendNack, NackParams(0, 0, 0, true));
    EXPECT_EQ(
            graph->OnEvent(kRequestVideoSendNack, reinterpret_cast<uint64_t>(nackEvent), 0), true);

    InternalRequestEventParam* pliEvent =
            new InternalRequestEventParam(kRequestVideoSendPictureLost, kPsfbPli);
    EXPECT_EQ(graph->OnEvent(kRequestVideoSendPictureLost, reinterpret_cast<uint64_t>(pliEvent), 0),
            true);

    InternalRequestEventParam* firEvent =
            new InternalRequestEventParam(kRequestVideoSendPictureLost, kPsfbFir);
    EXPECT_EQ(graph->OnEvent(kRequestVideoSendPictureLost, reinterpret_cast<uint64_t>(firEvent), 0),
            true);

    InternalRequestEventParam* tmmbrEvent =
            new InternalRequestEventParam(kRtpFbTmmbr, TmmbrParams(100000, 0, 0, 0));
    EXPECT_EQ(graph->OnEvent(kRequestVideoSendTmmbr, reinterpret_cast<uint64_t>(tmmbrEvent), 0),
            true);

    InternalRequestEventParam* tmmbn =
            new InternalRequestEventParam(kRtpFbTmmbn, TmmbrParams(100000, 0, 0, 0));
    EXPECT_EQ(graph->OnEvent(kRequestVideoSendTmmbn, reinterpret_cast<uint64_t>(tmmbn), 0), true);

    EXPECT_EQ(graph->stop(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);
}
