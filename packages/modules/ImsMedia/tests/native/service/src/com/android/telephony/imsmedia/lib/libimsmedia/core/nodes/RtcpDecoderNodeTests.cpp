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
#include <condition_variable>
#include <mutex>
#include <RtcpConfig.h>
#include <AudioConfig.h>
#include <VideoConfig.h>
#include <TextConfig.h>
#include <RtcpDecoderNode.h>
#include <ImsMediaVideoUtil.h>
#include <ImsMediaTrace.h>

using namespace android::telephony::imsmedia;
using namespace android;

namespace
{
// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE;
const String8 kRemoteAddress("127.0.0.1");
const int32_t kRemotePort = 10000;
const int8_t kDscp = 0;
const int8_t kRxPayload = 96;
const int8_t kTxPayload = 96;
const int8_t kSamplingRate = 16;

// RtcpConfig
const String8 kCanonicalName("name");
const int32_t kTransmitPort = 10001;
const int32_t RTCP_INTERVAL = 1;
const int32_t kIntervalSec = RTCP_INTERVAL;
int32_t kRtcpXrBlockTypes = 0;

// VideoConfig
const int32_t kVideoMode = VideoConfig::VIDEO_MODE_PREVIEW;
const int32_t kMtu = 1500;
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
const int32_t kDeviceOrientationDegree = 0;
const int32_t kCvoValue = 1;
const int32_t kRtcpFbTypes = VideoConfig::RTP_FB_NONE;

static std::condition_variable cond;
static std::mutex timerMutex;

class FakeNode : public BaseNode
{
public:
    bool mOnDataFromFrontNodeCalled = false;
    virtual ~FakeNode() {}
    void Stop() {}
    bool IsRunTime() { return true; }
    bool IsSourceNode() { return false; }
    virtual kBaseNodeState GetState() { return kNodeStateRunning; }
    void SetConfig(void* config) { (void)config; }
    void OnDataFromFrontNode(ImsMediaSubType, uint8_t*, uint32_t, uint32_t, bool, uint32_t,
            ImsMediaSubType, uint32_t)
    {
        IMLOGI0("FakeNode::OnDataFromFrontNode");
        mOnDataFromFrontNodeCalled = true;
        cond.notify_all();
    }

    ImsMediaResult Start() { return RESULT_SUCCESS; }
};

class SessionCallback : public BaseSessionCallback
{
public:
    bool mOnEventCalled = false;
    int32_t mType;
    uint64_t mParam1, mParam2;

    virtual ~SessionCallback() {}
    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2)
    {
        IMLOGI0("SessionCallback::onEvent");
        mOnEventCalled = true;
        mType = type;
        mParam1 = param1;
        mParam2 = param2;
        cond.notify_all();
    }
};

class RtcpDecoderNodeEx : public RtcpDecoderNode
{
public:
    bool mCallBaseClassMethod = false;
    bool mOnRtcpIndCalled = false;

    RtcpDecoderNodeEx(BaseSessionCallback* callback = nullptr) :
            RtcpDecoderNode(callback)
    {
    }
    virtual ~RtcpDecoderNodeEx() {}
};

class RtcpDecoderNodeTests : public ::testing::Test
{
public:
    virtual ~RtcpDecoderNodeTests() {}

protected:
    RtcpDecoderNodeEx* pRtcpDecNode;
    VideoConfig videoConfig;
    FakeNode* pFakeRearNode;
    SessionCallback* pCallback;

    virtual void SetUp() override
    {
        pCallback = new SessionCallback();
        pRtcpDecNode = new RtcpDecoderNodeEx(pCallback);
        pRtcpDecNode->SetMediaType(IMS_MEDIA_VIDEO);
        setupVideoConfig(videoConfig);
        pRtcpDecNode->SetConfig(&videoConfig);
        pFakeRearNode = connectNodes(pRtcpDecNode);
    }

    virtual void TearDown() override
    {
        delete pRtcpDecNode;
        delete pFakeRearNode;
        delete pCallback;
    }

    void setupRtcpConfig(RtcpConfig& rtcpConfig)
    {
        rtcpConfig.setCanonicalName(kCanonicalName);
        rtcpConfig.setTransmitPort(kTransmitPort);
        rtcpConfig.setIntervalSec(kIntervalSec);
        rtcpConfig.setRtcpXrBlockTypes(kRtcpXrBlockTypes);
    }

    // using video codec because RTCP has feedback implementation for video media type.
    void setupVideoConfig(VideoConfig& videoConfig)
    {
        videoConfig.setMediaDirection(kMediaDirection);
        videoConfig.setRemoteAddress(kRemoteAddress);
        videoConfig.setRemotePort(kRemotePort);
        RtcpConfig rtcpConfig;
        setupRtcpConfig(rtcpConfig);
        videoConfig.setRtcpConfig(rtcpConfig);
        videoConfig.setMaxMtuBytes(kMtu);
        videoConfig.setDscp(kDscp);
        videoConfig.setRxPayloadTypeNumber(kRxPayload);
        videoConfig.setTxPayloadTypeNumber(kTxPayload);
        videoConfig.setSamplingRateKHz(kSamplingRate);
        videoConfig.setVideoMode(kVideoMode);
        videoConfig.setCodecType(VideoConfig::CODEC_AVC);
        videoConfig.setFramerate(kFramerate);
        videoConfig.setBitrate(kBitrate);
        videoConfig.setCodecProfile(kCodecProfile);
        videoConfig.setCodecLevel(kCodecLevel);
        videoConfig.setIntraFrameInterval(kIntraFrameIntervalSec);
        videoConfig.setPacketizationMode(kPacketizationMode);
        videoConfig.setCameraId(kCameraId);
        videoConfig.setCameraZoom(kCameraZoom);
        videoConfig.setResolutionWidth(kResolutionWidth);
        videoConfig.setResolutionHeight(kResolutionHeight);
        videoConfig.setDeviceOrientationDegree(kDeviceOrientationDegree);
        videoConfig.setCvoValue(kCvoValue);
        videoConfig.setRtcpFbType(kRtcpFbTypes);
    }

    FakeNode* connectNodes(RtcpDecoderNode* pRtcpDecNode)
    {
        FakeNode* pFakeNode = new FakeNode();
        pRtcpDecNode->ConnectRearNode(pFakeNode);
        return pFakeNode;
    }
};

TEST_F(RtcpDecoderNodeTests, TestInitState)
{
    EXPECT_EQ(pRtcpDecNode->GetNodeId(), kNodeIdRtcpDecoder);
    EXPECT_EQ(pRtcpDecNode->IsRunTime(), true);
    EXPECT_EQ(pRtcpDecNode->IsSourceNode(), false);
}

TEST_F(RtcpDecoderNodeTests, TestConfigChange)
{
    VideoConfig videoConfig;
    setupVideoConfig(videoConfig);
    EXPECT_EQ(pRtcpDecNode->IsSameConfig(&videoConfig), true);
}

TEST_F(RtcpDecoderNodeTests, TestStartStopSuccess)
{
    EXPECT_EQ(pRtcpDecNode->Start(), RESULT_SUCCESS);
    EXPECT_EQ(pRtcpDecNode->GetState(), kNodeStateRunning);

    pRtcpDecNode->Stop();
    EXPECT_EQ(pRtcpDecNode->GetState(), kNodeStateStopped);
}

TEST_F(RtcpDecoderNodeTests, TestOnRtcpSrInd)
{
    pRtcpDecNode->SetMediaType(IMS_MEDIA_AUDIO);
    tNotifyReceiveRtcpSrInd payload;
    memset(&payload, 0x00, sizeof(payload));
    pRtcpDecNode->OnRtcpInd(RTPSVC_RECEIVE_RTCP_SR_IND, &payload);
    EXPECT_EQ(pCallback->mOnEventCalled, true);
    EXPECT_EQ(pCallback->mType, kCollectPacketInfo);
    EXPECT_EQ(pCallback->mParam1, kStreamRtcp);
}

TEST_F(RtcpDecoderNodeTests, TestOnRtcpRrInd)
{
    pRtcpDecNode->SetMediaType(IMS_MEDIA_AUDIO);
    tNotifyReceiveRtcpRrInd payload;
    memset(&payload, 0x00, sizeof(payload));
    pRtcpDecNode->OnRtcpInd(RTPSVC_RECEIVE_RTCP_RR_IND, &payload);
    EXPECT_EQ(pCallback->mOnEventCalled, true);
    EXPECT_EQ(pCallback->mType, kCollectPacketInfo);
    EXPECT_EQ(pCallback->mParam1, kStreamRtcp);
}

TEST_F(RtcpDecoderNodeTests, TestOnRtcpFbInd)
{
    pRtcpDecNode->SetMediaType(IMS_MEDIA_AUDIO);
    tRtpSvcIndSt_ReceiveRtcpFeedbackInd payload;
    memset(&payload, 0x00, sizeof(payload));
    payload.wFmt = kRtpFbTmmbr;
    uint8_t fbMsgData[64];
    payload.pMsg = fbMsgData;
    pRtcpDecNode->OnRtcpInd(RTPSVC_RECEIVE_RTCP_FB_IND, &payload);
    EXPECT_EQ(pCallback->mOnEventCalled, true);
    EXPECT_EQ(pCallback->mType, kRequestVideoSendTmmbn);
}

TEST_F(RtcpDecoderNodeTests, TestOnNumReceivedPacket)
{
    pRtcpDecNode->SetMediaType(IMS_MEDIA_AUDIO);
    pRtcpDecNode->SetInactivityTimerSec(1);
    pRtcpDecNode->OnNumReceivedPacket(0, 0);
    EXPECT_EQ(pCallback->mOnEventCalled, true);
    EXPECT_EQ(pCallback->mType, kImsMediaEventMediaInactivity);
    EXPECT_EQ(pCallback->mParam1, kProtocolRtcp);
    EXPECT_EQ(pCallback->mParam2, 1);

    pCallback->mOnEventCalled = false;
    pRtcpDecNode->OnNumReceivedPacket(1, 0);
    EXPECT_EQ(pCallback->mOnEventCalled, false);

    pCallback->mOnEventCalled = false;
    pRtcpDecNode->OnNumReceivedPacket(0, 1);
    EXPECT_EQ(pCallback->mOnEventCalled, false);

    pCallback->mOnEventCalled = false;
    pRtcpDecNode->OnNumReceivedPacket(1, 1);
    EXPECT_EQ(pCallback->mOnEventCalled, false);
}

TEST_F(RtcpDecoderNodeTests, TestOnEvent)
{
    pRtcpDecNode->OnEvent(kRequestRoundTripTimeDelayUpdate, 100);
    EXPECT_EQ(pCallback->mOnEventCalled, true);
    EXPECT_EQ(pCallback->mType, kRequestRoundTripTimeDelayUpdate);
}

TEST_F(RtcpDecoderNodeTests, TestReceiveTmmbr)
{
    pRtcpDecNode->SetMediaType(IMS_MEDIA_AUDIO);
    tRtpSvcIndSt_ReceiveRtcpFeedbackInd payload;
    memset(&payload, 0x00, sizeof(payload));
    uint8_t fbMsgData[64];
    payload.pMsg = fbMsgData;
    pRtcpDecNode->ReceiveTmmbr(&payload);
    EXPECT_EQ(pCallback->mOnEventCalled, true);
    EXPECT_EQ(pCallback->mType, kRequestVideoSendTmmbn);
}

TEST_F(RtcpDecoderNodeTests, TestRequestIdrFrame)
{
    pRtcpDecNode->RequestIdrFrame();
    EXPECT_EQ(pCallback->mOnEventCalled, true);
    EXPECT_EQ(pCallback->mType, kRequestVideoIdrFrame);
}
}  // namespace
