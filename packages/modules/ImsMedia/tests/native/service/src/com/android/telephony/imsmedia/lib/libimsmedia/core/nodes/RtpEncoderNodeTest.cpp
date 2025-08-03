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
#include <AudioConfig.h>
#include <VideoConfig.h>
#include <TextConfig.h>
#include <RtpEncoderNode.h>

using namespace android::telephony::imsmedia;
using namespace android;

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
const int32_t kIntervalSec = 5;
const int32_t kRtcpXrBlockTypes = 0;

// AudioConfig
const int8_t kPTimeMillis = 20;
const int32_t kMaxPtimeMillis = 100;
const int8_t kcodecModeRequest = 15;
const bool kDtxEnabled = true;
const int8_t kDtmfPayloadTypeNumber = 100;
const int8_t kDtmfsamplingRateKHz = 16;

// AmrParam
const int32_t kAmrMode = AmrParams::AMR_MODE_6;
const bool kOctetAligned = false;
const int32_t kMaxRedundancyMillis = 240;

// EvsParam
const int32_t kEvsBandwidth = EvsParams::EVS_BAND_NONE;
const int32_t kEvsMode = 8;
const int8_t kChannelAwareMode = 3;
const bool kUseHeaderFullOnly = false;

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

// TextConfig
const int8_t kRedundantPayload = 102;
const int8_t kRedundantLevel = 2;
const bool kKeepRedundantLevel = true;

const int32_t kRtpHeaderSize = 12;
const int32_t kRtpHeaderSizeWithExtension = 20;

class FakeRtpEncoderCallback : public BaseSessionCallback
{
public:
    FakeRtpEncoderCallback() {}
    virtual ~FakeRtpEncoderCallback() {}

    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2)
    {
        (void)type;
        (void)param1;
        (void)param2;
    }
};

class FakeRtpEncoderNode : public BaseNode
{
public:
    explicit FakeRtpEncoderNode(BaseSessionCallback* callback = nullptr) :
            BaseNode(callback)
    {
        mFrameSize = 0;
    }
    virtual ~FakeRtpEncoderNode() {}
    virtual ImsMediaResult Start() { return RESULT_SUCCESS; }
    virtual void Stop() {}
    virtual bool IsRunTime() { return true; }
    virtual bool IsSourceNode() { return false; }
    virtual void SetConfig(void* config) { (void)config; }
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* data, uint32_t size,
            uint32_t timestamp, bool mark, uint32_t seq, ImsMediaSubType dataType,
            uint32_t arrivalTime)
    {
        (void)subtype;
        (void)data;
        (void)timestamp;
        (void)mark;
        (void)seq;
        (void)dataType;
        (void)arrivalTime;
        mFrameSize = size;
    }

    virtual kBaseNodeState GetState() { return kNodeStateRunning; }

    uint32_t GetFrameSize() { return mFrameSize; }

private:
    uint32_t mFrameSize;
};

class RtpEncoderNodeTest : public ::testing::Test
{
public:
    RtpEncoderNodeTest()
    {
        mNode = nullptr;
        mFakeNode = nullptr;
    }
    virtual ~RtpEncoderNodeTest() {}

protected:
    AmrParams mAmr;
    EvsParams mEvs;
    AudioConfig mAudioConfig;
    VideoConfig mVideoConfig;
    TextConfig mTextConfig;
    RtcpConfig mRtcp;
    RtpEncoderNode* mNode;
    FakeRtpEncoderNode* mFakeNode;
    FakeRtpEncoderCallback mCallback;
    std::list<BaseNode*> mNodes;

    virtual void SetUp() override
    {
        mRtcp.setCanonicalName(kCanonicalName);
        mRtcp.setTransmitPort(kTransmitPort);
        mRtcp.setIntervalSec(kIntervalSec);
        mRtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);
    }

    virtual void TearDown() override
    {
        while (mNodes.size() > 0)
        {
            BaseNode* node = mNodes.front();
            node->Stop();
            delete node;
            mNodes.pop_front();
        }
    }

public:
    void setupNodes(ImsMediaType type, RtpConfig* config)
    {
        mNode = new RtpEncoderNode(&mCallback);
        mNode->SetMediaType(type);
        mNode->SetConfig(config);
        mNodes.push_back(mNode);

        mFakeNode = new FakeRtpEncoderNode(&mCallback);
        mFakeNode->SetMediaType(type);
        mFakeNode->SetConfig(config);
        mNodes.push_back(mFakeNode);
        mNode->ConnectRearNode(mFakeNode);
    }

    void setupAudioConfig()
    {
        mAmr.setAmrMode(kAmrMode);
        mAmr.setOctetAligned(kOctetAligned);
        mAmr.setMaxRedundancyMillis(kMaxRedundancyMillis);

        mEvs.setEvsBandwidth(kEvsBandwidth);
        mEvs.setEvsMode(kEvsMode);
        mEvs.setChannelAwareMode(kChannelAwareMode);
        mEvs.setUseHeaderFullOnly(kUseHeaderFullOnly);
        mEvs.setCodecModeRequest(kcodecModeRequest);

        mAudioConfig.setMediaDirection(kMediaDirection);
        mAudioConfig.setRemoteAddress(kRemoteAddress);
        mAudioConfig.setRemotePort(kRemotePort);
        mAudioConfig.setRtcpConfig(mRtcp);
        mAudioConfig.setDscp(kDscp);
        mAudioConfig.setRxPayloadTypeNumber(kRxPayload);
        mAudioConfig.setTxPayloadTypeNumber(kTxPayload);
        mAudioConfig.setSamplingRateKHz(kSamplingRate);
        mAudioConfig.setPtimeMillis(kPTimeMillis);
        mAudioConfig.setMaxPtimeMillis(kMaxPtimeMillis);
        mAudioConfig.setDtxEnabled(kDtxEnabled);
        mAudioConfig.setCodecType(AudioConfig::CODEC_AMR);
        mAudioConfig.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        mAudioConfig.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        mAudioConfig.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
        mAudioConfig.setAmrParams(mAmr);
        mAudioConfig.setEvsParams(mEvs);

        setupNodes(IMS_MEDIA_AUDIO, &mAudioConfig);
    }

    void setupVideoConfig()
    {
        mVideoConfig.setMediaDirection(kMediaDirection);
        mVideoConfig.setRemoteAddress(kRemoteAddress);
        mVideoConfig.setRemotePort(kRemotePort);
        mVideoConfig.setRtcpConfig(mRtcp);
        mVideoConfig.setMaxMtuBytes(kMtu);
        mVideoConfig.setDscp(kDscp);
        mVideoConfig.setRxPayloadTypeNumber(kRxPayload);
        mVideoConfig.setTxPayloadTypeNumber(kTxPayload);
        mVideoConfig.setSamplingRateKHz(kSamplingRate);
        mVideoConfig.setVideoMode(kVideoMode);
        mVideoConfig.setCodecType(VideoConfig::CODEC_AVC);
        mVideoConfig.setFramerate(kFramerate);
        mVideoConfig.setBitrate(kBitrate);
        mVideoConfig.setCodecProfile(kCodecProfile);
        mVideoConfig.setCodecLevel(kCodecLevel);
        mVideoConfig.setIntraFrameInterval(kIntraFrameIntervalSec);
        mVideoConfig.setPacketizationMode(kPacketizationMode);
        mVideoConfig.setCameraId(kCameraId);
        mVideoConfig.setCameraZoom(kCameraZoom);
        mVideoConfig.setResolutionWidth(kResolutionWidth);
        mVideoConfig.setResolutionHeight(kResolutionHeight);
        mVideoConfig.setPauseImagePath(kPauseImagePath);
        mVideoConfig.setDeviceOrientationDegree(kDeviceOrientationDegree);
        mVideoConfig.setCvoValue(kCvoValue);
        mVideoConfig.setRtcpFbType(kRtcpFbTypes);

        setupNodes(IMS_MEDIA_VIDEO, &mVideoConfig);
    }

    void setupTextConfig()
    {
        mTextConfig.setMediaDirection(kMediaDirection);
        mTextConfig.setRemoteAddress(kRemoteAddress);
        mTextConfig.setRemotePort(kRemotePort);
        mTextConfig.setRtcpConfig(mRtcp);
        mTextConfig.setDscp(kDscp);
        mTextConfig.setRxPayloadTypeNumber(kRxPayload);
        mTextConfig.setTxPayloadTypeNumber(kTxPayload);
        mTextConfig.setSamplingRateKHz(kSamplingRate);
        mTextConfig.setCodecType(TextConfig::TEXT_T140_RED);
        mTextConfig.setBitrate(kBitrate);
        mTextConfig.setRedundantPayload(kRedundantPayload);
        mTextConfig.setRedundantLevel(kRedundantLevel);
        mTextConfig.setKeepRedundantLevel(kKeepRedundantLevel);

        setupNodes(IMS_MEDIA_TEXT, &mTextConfig);
    }
};

TEST_F(RtpEncoderNodeTest, startFail)
{
    setupAudioConfig();
    mAudioConfig.setRxPayloadTypeNumber(0);
    mNode->SetConfig(&mAudioConfig);
    EXPECT_EQ(mNode->Start(), RESULT_INVALID_PARAM);

    mAudioConfig.setTxPayloadTypeNumber(0);
    mNode->SetConfig(&mAudioConfig);
    EXPECT_EQ(mNode->Start(), RESULT_INVALID_PARAM);
}

TEST_F(RtpEncoderNodeTest, startAudioAndUpdate)
{
    setupAudioConfig();
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(mNode->UpdateConfig(&mAudioConfig), RESULT_SUCCESS);

    // update
    mAudioConfig.setTxDtmfPayloadTypeNumber(102);
    EXPECT_EQ(mNode->UpdateConfig(&mAudioConfig), RESULT_SUCCESS);
}

TEST_F(RtpEncoderNodeTest, testAudioDataProcess)
{
    setupAudioConfig();
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    // AMR mode 6 payload frame
    uint8_t testFrame[] = {0x1c, 0x51, 0x06, 0x40, 0x32, 0xba, 0x8e, 0xc1, 0x25, 0x42, 0x2f, 0xc7,
            0xaf, 0x6e, 0xe0, 0xbb, 0xb2, 0x91, 0x09, 0xa5, 0xa6, 0x08, 0x18, 0x6f, 0x08, 0x1c,
            0x1c, 0x44, 0xd8, 0xe0, 0x48, 0x8c, 0x7c, 0xf8, 0x4c, 0x22, 0xd0};

    EXPECT_EQ(mFakeNode->GetFrameSize(), 0);
    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0);
    EXPECT_EQ(mFakeNode->GetFrameSize(), sizeof(testFrame) + kRtpHeaderSize);
}

TEST_F(RtpEncoderNodeTest, startVideoAndUpdate)
{
    setupVideoConfig();
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(mNode->UpdateConfig(&mVideoConfig), RESULT_SUCCESS);

    // update
    mVideoConfig.setTxPayloadTypeNumber(99);
    EXPECT_EQ(mNode->UpdateConfig(&mVideoConfig), RESULT_SUCCESS);
}

TEST_F(RtpEncoderNodeTest, testVideoDataProcess)
{
    setupVideoConfig();
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    // H.264 payload of sps frame
    uint8_t testFrame[] = {0x67, 0x42, 0xc0, 0x0c, 0xda, 0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10,
            0x3c, 0x58, 0xba, 0x80};

    EXPECT_EQ(mFakeNode->GetFrameSize(), 0);
    mNode->OnDataFromFrontNode(MEDIASUBTYPE_RTPPAYLOAD, testFrame, sizeof(testFrame), 0, true, 0);

    EXPECT_TRUE(mNode->SetCvoExtension(0, 0));
    EXPECT_EQ(mFakeNode->GetFrameSize(), sizeof(testFrame) + kRtpHeaderSize);

    mNode->OnDataFromFrontNode(
            MEDIASUBTYPE_VIDEO_IDR_FRAME, testFrame, sizeof(testFrame), 0, true, 0);
    EXPECT_EQ(mFakeNode->GetFrameSize(), sizeof(testFrame) + kRtpHeaderSizeWithExtension);
}

TEST_F(RtpEncoderNodeTest, startTextAndUpdate)
{
    setupTextConfig();
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(mNode->UpdateConfig(&mTextConfig), RESULT_SUCCESS);

    // update
    mTextConfig.setTxPayloadTypeNumber(99);
    EXPECT_EQ(mNode->UpdateConfig(&mTextConfig), RESULT_SUCCESS);
}

TEST_F(RtpEncoderNodeTest, testTextDataProcess)
{
    setupTextConfig();
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    // RED payload
    uint8_t testFrame[] = {0xef, 0x00, 0x00, 0x00, 0xef, 0x00, 0x00, 0x00, 0x6f, 0x74};

    EXPECT_EQ(mFakeNode->GetFrameSize(), 0);
    mNode->OnDataFromFrontNode(
            MEDIASUBTYPE_BITSTREAM_T140_RED, testFrame, sizeof(testFrame), 0, true, 0);
    EXPECT_EQ(mFakeNode->GetFrameSize(), sizeof(testFrame) + kRtpHeaderSize);
}