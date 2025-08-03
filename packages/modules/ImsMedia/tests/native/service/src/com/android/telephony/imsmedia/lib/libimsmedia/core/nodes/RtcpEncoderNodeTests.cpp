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
#include <RtcpEncoderNode.h>
#include <android/log.h>

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
const android::String8 kPauseImagePath("data/user_de/0/com.android.telephony.imsmedia/test.jpg");
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
        mOnDataFromFrontNodeCalled = true;
        cond.notify_all();
    }

    ImsMediaResult Start() { return RESULT_SUCCESS; }
};

class SessionCallback : public BaseSessionCallback
{
public:
    bool mOnEventCalled = false;
    virtual ~SessionCallback() {}
    virtual void onEvent(int32_t, uint64_t, uint64_t)
    {
        mOnEventCalled = true;
        cond.notify_all();
    }
};

class RtcpEncoderNodeEx : public RtcpEncoderNode
{
public:
    bool mCallBaseClassMethod = false;
    bool mProcessTimerMethodCalled = false;

    RtcpEncoderNodeEx(BaseSessionCallback* callback = nullptr) :
            RtcpEncoderNode(callback)
    {
    }
    virtual ~RtcpEncoderNodeEx() {}

    void ProcessTimer()
    {
        mProcessTimerMethodCalled = true;
        cond.notify_all();

        if (mCallBaseClassMethod)
        {
            RtcpEncoderNode::ProcessTimer();
        }
    }
};

class RtcpEncoderNodeTests : public ::testing::Test
{
public:
    virtual ~RtcpEncoderNodeTests() {}

protected:
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
        videoConfig.setPauseImagePath(kPauseImagePath);
        videoConfig.setDeviceOrientationDegree(kDeviceOrientationDegree);
        videoConfig.setCvoValue(kCvoValue);
        videoConfig.setRtcpFbType(kRtcpFbTypes);
    }

    FakeNode* connectNodes(RtcpEncoderNode* pRtcpEncNode)
    {
        FakeNode* pFakeNode = new FakeNode();
        pRtcpEncNode->ConnectRearNode(pFakeNode);
        return pFakeNode;
    }
};

TEST_F(RtcpEncoderNodeTests, TestInitState)
{
    RtcpEncoderNode* pRtcpEncNode = new RtcpEncoderNode();
    EXPECT_EQ(pRtcpEncNode->GetNodeId(), kNodeIdRtcpEncoder);
    EXPECT_EQ(pRtcpEncNode->IsRunTime(), true);
    EXPECT_EQ(pRtcpEncNode->IsSourceNode(), true);
    delete pRtcpEncNode;
}

TEST_F(RtcpEncoderNodeTests, TestConfigChange)
{
    RtcpEncoderNode* pRtcpEncNode = new RtcpEncoderNode();
    VideoConfig videoConfig;
    setupVideoConfig(videoConfig);
    pRtcpEncNode->SetConfig(&videoConfig);
    EXPECT_EQ(pRtcpEncNode->IsSameConfig(&videoConfig), true);
    delete pRtcpEncNode;
}

TEST_F(RtcpEncoderNodeTests, TestStartStopSuccess)
{
    SessionCallback callback;
    RtcpEncoderNodeEx* pRtcpEncNode = new RtcpEncoderNodeEx(&callback);
    kRtcpXrBlockTypes = RtcpConfig::FLAG_RTCPXR_LOSS_RLE_REPORT_BLOCK;
    VideoConfig videoConfig;
    setupVideoConfig(videoConfig);
    pRtcpEncNode->SetConfig(&videoConfig);
    EXPECT_EQ(pRtcpEncNode->Start(), RESULT_SUCCESS);

    bool timeout = false;
    constexpr std::chrono::duration<int> waittime = std::chrono::seconds(RTCP_INTERVAL + 1);
    // Wait for RTCP timer expiry to confirm start success.
    {
        pRtcpEncNode->mCallBaseClassMethod = true;
        std::unique_lock<std::mutex> lock(timerMutex);
        if (cond.wait_for(lock, waittime) == std::cv_status::timeout)
            timeout = true;

        EXPECT_EQ(timeout, false);
        EXPECT_EQ(pRtcpEncNode->mProcessTimerMethodCalled, true);
    }

    // Check if SendEvent is called.
    {
        timeout = false;
        std::unique_lock<std::mutex> lock(timerMutex);
        if (cond.wait_for(lock, waittime) == std::cv_status::timeout)
            timeout = true;

        EXPECT_EQ(timeout, false);
        EXPECT_EQ(callback.mOnEventCalled, true);
    }

    // Call stop and make sure RTCP timer doesn't expire.
    pRtcpEncNode->Stop();
    {
        timeout = false;
        pRtcpEncNode->mProcessTimerMethodCalled = false;
        std::unique_lock<std::mutex> lock(timerMutex);
        if (cond.wait_for(lock, waittime) == std::cv_status::timeout)
            timeout = true;

        EXPECT_EQ(timeout, true);
        EXPECT_EQ(pRtcpEncNode->mProcessTimerMethodCalled, false);
    }
    delete pRtcpEncNode;
}

TEST_F(RtcpEncoderNodeTests, TestOnRtcpPacket)
{
    RtcpEncoderNode* pRtcpEncNode = new RtcpEncoderNode();
    FakeNode* pRearNode = connectNodes(pRtcpEncNode);

    unsigned char data[10];
    pRtcpEncNode->OnRtcpPacket(data, 10);
    EXPECT_EQ(pRearNode->mOnDataFromFrontNodeCalled, true);
    delete pRtcpEncNode;
    delete pRearNode;
}

TEST_F(RtcpEncoderNodeTests, TestSendNack)
{
    RtcpEncoderNode* pRtcpEncNode = new RtcpEncoderNode();
    pRtcpEncNode->SetMediaType(IMS_MEDIA_VIDEO);

    bool bRet = pRtcpEncNode->SendNack(nullptr);
    EXPECT_EQ(bRet, false);

    NackParams param;
    param.PID = 0;
    param.BLP = 0;
    param.nSecNackCnt = 0;
    param.bNackReport = true;

    bRet = pRtcpEncNode->SendNack(&param);
    EXPECT_EQ(bRet, false);

    VideoConfig videoConfig;
    setupVideoConfig(videoConfig);
    videoConfig.setRtcpFbType(VideoConfig::RTP_FB_NACK);
    pRtcpEncNode->SetConfig(&videoConfig);
    EXPECT_EQ(pRtcpEncNode->Start(), RESULT_SUCCESS);
    bRet = pRtcpEncNode->SendNack(&param);
    EXPECT_EQ(bRet, true);
    pRtcpEncNode->Stop();
    delete pRtcpEncNode;
}

TEST_F(RtcpEncoderNodeTests, TestSendPictureLost)
{
    RtcpEncoderNode* pRtcpEncNode = new RtcpEncoderNode();
    pRtcpEncNode->SetMediaType(IMS_MEDIA_VIDEO);
    VideoConfig videoConfig;
    setupVideoConfig(videoConfig);
    videoConfig.setRtcpFbType(VideoConfig::PSFB_PLI);
    pRtcpEncNode->SetConfig(&videoConfig);
    EXPECT_EQ(pRtcpEncNode->Start(), RESULT_SUCCESS);

    bool bRet = pRtcpEncNode->SendPictureLost(kPsfbPli);
    EXPECT_EQ(bRet, true);

    videoConfig.setRtcpFbType(VideoConfig::PSFB_FIR);
    pRtcpEncNode->SetConfig(&videoConfig);
    bRet = pRtcpEncNode->SendPictureLost(kPsfbFir);
    EXPECT_EQ(bRet, true);
    pRtcpEncNode->Stop();
    delete pRtcpEncNode;
}

TEST_F(RtcpEncoderNodeTests, TestSendTmmbrn)
{
    RtcpEncoderNode* pRtcpEncNode = new RtcpEncoderNode();
    pRtcpEncNode->SetMediaType(IMS_MEDIA_VIDEO);
    VideoConfig videoConfig;
    setupVideoConfig(videoConfig);
    videoConfig.setRtcpFbType(VideoConfig::RTP_FB_TMMBR);
    pRtcpEncNode->SetConfig(&videoConfig);
    EXPECT_EQ(pRtcpEncNode->Start(), RESULT_SUCCESS);

    TmmbrParams tmmbr;
    tmmbr.ssrc = 0x1111;
    tmmbr.exp = 0x2222;
    tmmbr.mantissa = 0x3333;
    tmmbr.overhead = 0x4444;
    bool bRet = pRtcpEncNode->SendTmmbrn(kRtpFbTmmbr, &tmmbr);
    EXPECT_EQ(bRet, true);

    videoConfig.setRtcpFbType(VideoConfig::RTP_FB_TMMBR);
    pRtcpEncNode->SetConfig(&videoConfig);
    bRet = pRtcpEncNode->SendTmmbrn(kRtpFbTmmbr, &tmmbr);
    EXPECT_EQ(bRet, true);
    pRtcpEncNode->Stop();
    delete pRtcpEncNode;
}

TEST_F(RtcpEncoderNodeTests, SendRtcpXr)
{
    RtcpEncoderNode* pRtcpEncNode = new RtcpEncoderNode();
    pRtcpEncNode->SetMediaType(IMS_MEDIA_VIDEO);
    EXPECT_EQ(pRtcpEncNode->Start(), RESULT_SUCCESS);

    bool bRet = pRtcpEncNode->SendRtcpXr(nullptr, 0);
    EXPECT_EQ(bRet, false);

    uint8_t* pDummyRtcpXrPacket = new uint8_t[10];
    bRet = pRtcpEncNode->SendRtcpXr(pDummyRtcpXrPacket, 10);
    EXPECT_EQ(bRet, true);
    pRtcpEncNode->Stop();
    delete pRtcpEncNode;
}
}  // namespace