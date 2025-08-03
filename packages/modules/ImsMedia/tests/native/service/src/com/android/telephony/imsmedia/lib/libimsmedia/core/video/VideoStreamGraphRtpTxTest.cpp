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
#include <media/NdkImageReader.h>
#include <MockBaseSessionCallback.h>
#include <VideoConfig.h>
#include <VideoStreamGraphRtpTx.h>

using namespace android::telephony::imsmedia;

// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_SEND_ONLY;
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
const int32_t kResolutionWidth = 640;
const int32_t kResolutionHeight = 480;
const android::String8 kPauseImagePath("data/user_de/0/com.android.telephony.imsmedia/test.jpg");
const int32_t kDeviceOrientationDegree = 0;
const int32_t kCvoValue = 1;
const int32_t kRtcpFbTypes = VideoConfig::RTP_FB_NONE;

class VideoStreamGraphRtpTxTest : public ::testing::Test
{
public:
    VideoStreamGraphRtpTxTest()
    {
        graph = nullptr;
        previewReader = nullptr;
        previewSurface = nullptr;
        socketRtpFd = -1;
    }
    virtual ~VideoStreamGraphRtpTxTest() {}

protected:
    VideoStreamGraphRtpTx* graph;
    VideoConfig config;
    RtcpConfig rtcp;
    AImageReader* previewReader;
    ANativeWindow* previewSurface;
    int socketRtpFd;
    MockBaseSessionCallback mCallback;

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
        socketRtpFd = ImsMediaNetworkUtil::openSocket(testIp, testPort, AF_INET);

        EXPECT_NE(socketRtpFd, -1);

        graph = new VideoStreamGraphRtpTx(&mCallback, socketRtpFd);

        EXPECT_EQ(AImageReader_new(kResolutionWidth, kResolutionHeight, AIMAGE_FORMAT_YUV_420_888,
                          1, &previewReader),
                AMEDIA_OK);
        AImageReader_getWindow(previewReader, &previewSurface);

        /*
         * TODO: Below line will skip all test under this class, need to remove to include it in
         * atest
         */
        GTEST_SKIP();
    }

    virtual void TearDown() override
    {
        if (socketRtpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtpFd);
        }

        if (previewReader != nullptr)
        {
            AImageReader_delete(previewReader);
        }

        if (graph != nullptr)
        {
            delete graph;
        }
    }
};

TEST_F(VideoStreamGraphRtpTxTest, TestGraphError)
{
    EXPECT_EQ(graph->create(nullptr), RESULT_INVALID_PARAM);
    EXPECT_EQ(graph->getState(), kStreamStateIdle);
}

TEST_F(VideoStreamGraphRtpTxTest, TestRtpTxPreviewStreamUpdateFail)
{
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateWaitSurface);

    graph->setSurface(previewSurface);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    EXPECT_EQ(graph->update(nullptr), RESULT_INVALID_PARAM);

    EXPECT_EQ(graph->stop(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);
}

TEST_F(VideoStreamGraphRtpTxTest, TestRtpTxStreamRecordingModeAndCvo)
{
    config.setVideoMode(VideoConfig::VIDEO_MODE_RECORDING);
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateWaitSurface);

    graph->setSurface(previewSurface);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    EXPECT_EQ(graph->OnEvent(kRequestVideoCvoUpdate, 0, 0), true);

    EXPECT_EQ(graph->stop(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);
}

TEST_F(VideoStreamGraphRtpTxTest, TestRtpTxStreamVideoModeUpdate)
{
    config.setVideoMode(VideoConfig::VIDEO_MODE_PAUSE_IMAGE);
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    config.setVideoMode(VideoConfig::VIDEO_MODE_RECORDING);
    EXPECT_EQ(graph->update(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateWaitSurface);

    graph->setSurface(previewSurface);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    EXPECT_EQ(graph->stop(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);
}

TEST_F(VideoStreamGraphRtpTxTest, TestRtpTxStreamDirectionUpdate)
{
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateWaitSurface);

    graph->setSurface(previewSurface);
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

TEST_F(VideoStreamGraphRtpTxTest, TestRtpTxStreamInternalEvent)
{
    config.setVideoMode(VideoConfig::VIDEO_MODE_RECORDING);
    EXPECT_EQ(graph->create(&config), RESULT_SUCCESS);
    EXPECT_EQ(graph->start(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateWaitSurface);

    graph->setSurface(previewSurface);
    EXPECT_EQ(graph->getState(), kStreamStateRunning);

    EXPECT_EQ(graph->OnEvent(kRequestVideoCvoUpdate, 1, 90), true);

    EXPECT_EQ(graph->OnEvent(kRequestVideoBitrateChange, 512000, 0), true);

    EXPECT_EQ(graph->OnEvent(kRequestVideoIdrFrame, 0, 0), true);

    EXPECT_EQ(graph->stop(), RESULT_SUCCESS);
    EXPECT_EQ(graph->getState(), kStreamStateCreated);
}
