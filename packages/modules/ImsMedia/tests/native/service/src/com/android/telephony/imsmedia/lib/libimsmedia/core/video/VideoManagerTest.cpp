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
#include <media/NdkImageReader.h>
#include <VideoConfig.h>
#include <MockVideoManager.h>
#include <ImsMediaCondition.h>
#include <unordered_map>
#include <algorithm>

using namespace android::telephony::imsmedia;

using ::testing::Eq;
using ::testing::Pointee;
using ::testing::Return;

namespace
{
// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_NO_FLOW;
const android::String8 kRemoteAddress("0.0.0.0");
const int32_t kRemotePort = 1000;
const int32_t kMtu = 1500;
const int8_t kDscp = 0;
const int8_t kRxPayload = 102;
const int8_t kTxPayload = 102;
const int8_t kSamplingRate = 90;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 1001;
const int32_t kIntervalSec = 1500;
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
const int32_t kRtcpFbTypes = VideoConfig::RTP_FB_NONE;

int32_t kSessionId = 0;
static ImsMediaCondition gCondition;

class VideoManagerCallback
{
public:
    int32_t resSessionId;
    int32_t response;
    VideoConfig resConfig;
    ImsMediaResult result;
    int32_t inactivityType;
    int32_t inactivityDuration;
    int32_t peerResolutionWidth;
    int32_t peerResolutionHeight;

    void resetRespond()
    {
        resSessionId = -1;
        response = -1;
        result = RESULT_NOT_READY;
    }

    void onCallback(const int id, const int event, const ImsMediaResult res)
    {
        resSessionId = id;
        response = event;
        result = res;
    }

    void onCallbackConfig(
            const int id, const int event, const ImsMediaResult res, const VideoConfig& config)
    {
        resSessionId = id;
        response = event;
        resConfig = config;
        result = res;
    }

    void onCallbackInactivity(const int id, const int event, const int type, const int duration)
    {
        resSessionId = id;
        response = event;
        inactivityType = type;
        inactivityDuration = duration;
    }

    void onCallbackPeerDimensionChanged(
            const int id, const int event, const int width, const int height)
    {
        resSessionId = id;
        response = event;
        peerResolutionWidth = width;
        peerResolutionHeight = height;
    }
};

static std::unordered_map<int, VideoManagerCallback*> gMapCallback;

class VideoManagerTest : public ::testing::Test
{
public:
    MockVideoManager manager;
    VideoConfig config;
    RtcpConfig rtcp;
    int socketRtpFd;
    int socketRtcpFd;
    VideoManagerCallback callback;

    VideoManagerTest()
    {
        socketRtpFd = -1;
        socketRtcpFd = -1;
        callback.resetRespond();
        gCondition.reset();
    }
    ~VideoManagerTest() {}

protected:
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

        manager.setCallback(&VideoCallback);
        gMapCallback.insert(std::make_pair(kSessionId, &callback));
        const char testIp[] = "127.0.0.1";
        unsigned int testPortRtp = 50000;
        socketRtpFd = ImsMediaNetworkUtil::openSocket(testIp, testPortRtp, AF_INET);
        EXPECT_NE(socketRtpFd, -1);
        unsigned int testPortRtcp = 50001;
        socketRtcpFd = ImsMediaNetworkUtil::openSocket(testIp, testPortRtcp, AF_INET);
        EXPECT_NE(socketRtcpFd, -1);
        gCondition.reset();
    }

    virtual void TearDown() override
    {
        if (socketRtpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtpFd);
        }

        if (socketRtcpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtcpFd);
        }

        gMapCallback.erase(kSessionId);
    }

    void openSession(const int32_t sessionId)
    {
        callback.resetRespond();
        android::Parcel parcel;
        parcel.writeInt32(kVideoOpenSession);
        parcel.writeInt32(socketRtpFd);
        parcel.writeInt32(socketRtcpFd);
        parcel.setDataPosition(0);
        gCondition.reset();
        manager.sendMessage(sessionId, parcel);
        EXPECT_TRUE(!gCondition.wait_timeout(1000));
        EXPECT_EQ(callback.resSessionId, sessionId);
        EXPECT_EQ(callback.response, kVideoOpenSessionSuccess);
    }

    void closeSession(const int32_t sessionId)
    {
        callback.resetRespond();
        android::Parcel parcel;
        parcel.writeInt32(kVideoCloseSession);
        parcel.setDataPosition(0);
        gCondition.reset();
        manager.sendMessage(sessionId, parcel);
        EXPECT_TRUE(!gCondition.wait_timeout(1000));
        EXPECT_EQ(callback.resSessionId, sessionId);
        EXPECT_EQ(callback.response, kVideoSessionClosed);
    }

    void testEventResponse(const int32_t sessionId, const int32_t event, VideoConfig* config,
            const int32_t response, const int32_t result)
    {
        callback.resetRespond();
        android::Parcel parcel;
        parcel.writeInt32(event);

        if (config != nullptr)
        {
            config->writeToParcel(&parcel);
        }

        parcel.setDataPosition(0);
        gCondition.reset();
        manager.sendMessage(sessionId, parcel);
        EXPECT_TRUE(!gCondition.wait_timeout(1000));
        EXPECT_EQ(callback.resSessionId, sessionId);
        EXPECT_EQ(callback.response, response);

        if (callback.response >= kVideoOpenSessionFailure &&
                callback.response <= kVideoModifySessionResponse)
        {
            EXPECT_EQ(result, result);

            if (config != nullptr && callback.response == kVideoModifySessionResponse)
            {
                EXPECT_EQ(callback.resConfig, *config);
            }
        }
    }

    static int32_t VideoCallback(int sessionId, const android::Parcel& parcel)
    {
        parcel.setDataPosition(0);

        int response = parcel.readInt32();
        ImsMediaResult result = RESULT_INVALID_PARAM;

        auto callback = gMapCallback.find(sessionId);

        if (callback != gMapCallback.end())
        {
            if (response >= kVideoOpenSessionFailure && response <= kVideoModifySessionResponse)
            {
                result = static_cast<ImsMediaResult>(parcel.readInt32());
            }

            switch (response)
            {
                case kVideoModifySessionResponse:
                {
                    VideoConfig resConfig;
                    resConfig.readFromParcel(&parcel);
                    (callback->second)->onCallbackConfig(sessionId, response, result, resConfig);
                }
                break;
                case kVideoPeerDimensionChanged:
                    (callback->second)
                            ->onCallbackPeerDimensionChanged(
                                    sessionId, response, parcel.readInt32(), parcel.readInt32());
                    break;
                case kVideoMediaInactivityInd:
                    (callback->second)
                            ->onCallbackInactivity(
                                    sessionId, response, parcel.readInt32(), parcel.readInt32());
                    break;
                case kVideoFirstMediaPacketInd:
                default:
                    (callback->second)->onCallback(sessionId, response, result);
                    break;
            }
        }

        gCondition.signal();
        return 0;
    }
};

TEST_F(VideoManagerTest, testOpenCloseSession)
{
    EXPECT_EQ(manager.getState(kSessionId), kSessionStateClosed);
    openSession(kSessionId);
    closeSession(kSessionId);
}

TEST_F(VideoManagerTest, testModifySession)
{
    testEventResponse(kSessionId, kVideoModifySession, nullptr, kVideoModifySessionResponse,
            RESULT_INVALID_PARAM);

    openSession(kSessionId);

    testEventResponse(kSessionId, kVideoModifySession, nullptr, kVideoModifySessionResponse,
            RESULT_INVALID_PARAM);

    testEventResponse(
            kSessionId, kVideoModifySession, &config, kVideoModifySessionResponse, RESULT_SUCCESS);

    closeSession(kSessionId);
}

TEST_F(VideoManagerTest, testSetPreviewSurface)
{
    openSession(kSessionId);

    AImageReader* previewReader;
    AImageReader_new(
            kResolutionWidth, kResolutionHeight, AIMAGE_FORMAT_YUV_420_888, 1, &previewReader);

    ANativeWindow* previewSurface;
    AImageReader_getWindow(previewReader, &previewSurface);

    EXPECT_CALL(manager, setPreviewSurfaceToSession(kSessionId, Eq(previewSurface)))
            .Times(1)
            .WillOnce(Return(RESULT_SUCCESS));

    manager.setPreviewSurface(kSessionId, previewSurface);

    gCondition.wait_timeout(20);
    closeSession(kSessionId);
}

TEST_F(VideoManagerTest, testSetDisplaySurface)
{
    openSession(kSessionId);

    AImageReader* displayReader;
    AImageReader_new(
            kResolutionWidth, kResolutionHeight, AIMAGE_FORMAT_YUV_420_888, 1, &displayReader);

    ANativeWindow* displaySurface;
    AImageReader_getWindow(displayReader, &displaySurface);

    EXPECT_CALL(manager, setDisplaySurfaceToSession(kSessionId, Eq(displaySurface)))
            .Times(1)
            .WillOnce(Return(RESULT_SUCCESS));

    manager.setDisplaySurface(kSessionId, displaySurface);

    gCondition.wait_timeout(20);
    closeSession(kSessionId);
}

TEST_F(VideoManagerTest, testSetMediaQualityThreshold)
{
    openSession(kSessionId);

    const std::vector<int32_t> kRtpInactivityTimerMillis = {10000, 20000};
    const int32_t kRtcpInactivityTimerMillis = 20000;
    const int32_t kRtpHysteresisTimeInMillis = 3000;
    const int32_t kRtpPacketLossDurationMillis = 5000;
    const std::vector<int32_t> kRtpPacketLossRate = {3, 5};
    const std::vector<int32_t> kRtpJitterMillis = {100, 200};
    const bool kNotifyCurrentStatus = false;

    MediaQualityThreshold threshold;
    threshold.setRtpInactivityTimerMillis(kRtpInactivityTimerMillis);
    threshold.setRtcpInactivityTimerMillis(kRtcpInactivityTimerMillis);
    threshold.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
    threshold.setRtpPacketLossDurationMillis(kRtpPacketLossDurationMillis);
    threshold.setRtpPacketLossRate(kRtpPacketLossRate);
    threshold.setRtpJitterMillis(kRtpJitterMillis);
    threshold.setNotifyCurrentStatus(kNotifyCurrentStatus);

    android::Parcel parcel;
    parcel.writeInt32(kVideoSetMediaQualityThreshold);
    threshold.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    EXPECT_CALL(manager, setMediaQualityThreshold(kSessionId, Pointee(Eq(threshold))))
            .Times(1)
            .WillOnce(Return());

    manager.sendMessage(kSessionId, parcel);

    closeSession(kSessionId);
}

TEST_F(VideoManagerTest, testFirstMediaPacketInd)
{
    ImsMediaEventHandler::SendEvent(
            "VIDEO_RESPONSE_EVENT", kVideoFirstMediaPacketInd, kSessionId, 0, 0);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kVideoFirstMediaPacketInd);
}

TEST_F(VideoManagerTest, testPeerDimensionChangedInd)
{
    ImsMediaEventHandler::SendEvent("VIDEO_RESPONSE_EVENT", kVideoPeerDimensionChanged, kSessionId,
            kResolutionWidth, kResolutionHeight);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kVideoPeerDimensionChanged);
    EXPECT_EQ(callback.peerResolutionWidth, kResolutionWidth);
    EXPECT_EQ(callback.peerResolutionHeight, kResolutionHeight);
}

TEST_F(VideoManagerTest, testMediaInactivityInd)
{
    const int32_t kInactivityType = kProtocolRtp;

    ImsMediaEventHandler::SendEvent(
            "VIDEO_RESPONSE_EVENT", kVideoMediaInactivityInd, kSessionId, kInactivityType, 0);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kVideoMediaInactivityInd);
    EXPECT_EQ(callback.inactivityType, kInactivityType);
}

TEST_F(VideoManagerTest, testInternalEvent)
{
    const uint64_t kParamA = 1;
    const uint64_t kParamB = 2;

    for (int32_t request = kRequestVideoCvoUpdate; request <= kRequestRoundTripTimeDelayUpdate;
            request++)
    {
        EXPECT_CALL(manager, SendInternalEvent(request, kSessionId, Eq(kParamA), Eq(kParamB)))
                .Times(1)
                .WillOnce(Return());

        ImsMediaEventHandler::SendEvent(
                "VIDEO_REQUEST_EVENT", request, kSessionId, kParamA, kParamB);
        gCondition.wait_timeout(20);
    }
}

}  // namespace