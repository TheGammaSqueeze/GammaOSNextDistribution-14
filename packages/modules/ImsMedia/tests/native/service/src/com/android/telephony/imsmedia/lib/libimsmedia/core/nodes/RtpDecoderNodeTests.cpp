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
#include <AudioConfig.h>
#include <VideoConfig.h>
#include <TextConfig.h>
#include <RtpEncoderNode.h>
#include <RtpDecoderNode.h>
#include <string.h>

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
const int8_t kDtmfPayloadTypeNumber = 103;
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

class FakeRtpDecoderCallback : public BaseSessionCallback
{
public:
    FakeRtpDecoderCallback()
    {
        dtmfDigit = 0;
        dtmfDuration = 0;
        listExtensions = nullptr;
    }
    virtual ~FakeRtpDecoderCallback()
    {
        if (listExtensions != nullptr)
        {
            delete listExtensions;
        }
    }
    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2)
    {
        if (type == kAudioDtmfReceivedInd)
        {
            dtmfDigit = static_cast<uint8_t>(param1);
            dtmfDuration = static_cast<uint32_t>(param2);
        }
        else if (type == kImsMediaEventHeaderExtensionReceived)
        {
            if (listExtensions != nullptr)
            {
                delete listExtensions;
            }

            listExtensions = reinterpret_cast<std::list<RtpHeaderExtension>*>(param1);
        }
    }
    uint8_t GetDtmfDigit() { return dtmfDigit; }
    uint32_t GetDtmfDuration() { return dtmfDuration; }
    std::list<RtpHeaderExtension>* GetListExtension() { return listExtensions; }

private:
    uint8_t dtmfDigit;
    uint32_t dtmfDuration;
    std::list<RtpHeaderExtension>* listExtensions;
};

class FakeRtpDecoderNode : public BaseNode
{
public:
    explicit FakeRtpDecoderNode(BaseSessionCallback* callback = nullptr) :
            BaseNode(callback)
    {
        frameSize = 0;
        memset(dataFrame, 0, sizeof(dataFrame));
        subType = MEDIASUBTYPE_UNDEFINED;
    }
    virtual ~FakeRtpDecoderNode() {}
    virtual ImsMediaResult Start() { return RESULT_SUCCESS; }
    virtual void Stop() {}
    virtual bool IsRunTime() { return true; }
    virtual bool IsSourceNode() { return false; }
    virtual void SetConfig(void* config) { (void)config; }
    virtual void OnDataFromFrontNode(ImsMediaSubType type, uint8_t* data, uint32_t size,
            uint32_t /*timestamp*/, bool /*mark*/, uint32_t /*seq*/, ImsMediaSubType /*dataType*/,
            uint32_t /*arrivalTime*/)
    {
        if (subType == MEDIASUBTYPE_REFRESHED)
        {
            return;
        }

        if (data != nullptr && size > 0)
        {
            memset(dataFrame, 0, sizeof(dataFrame));
            memcpy(dataFrame, data, size);
            frameSize = size;
            subType = type;
        }
    }

    virtual kBaseNodeState GetState() { return kNodeStateRunning; }

    uint32_t GetFrameSize() { return frameSize; }
    uint8_t* GetDataFrame() { return dataFrame; }
    ImsMediaSubType GetSubType() { return subType; }

private:
    uint32_t frameSize;
    uint8_t dataFrame[DEFAULT_MTU];
    ImsMediaSubType subType;
};

class RtpDecoderNodeTest : public ::testing::Test
{
public:
    RtpDecoderNodeTest()
    {
        encoder = nullptr;
        decoder = nullptr;
        fakeNode = nullptr;
    }
    virtual ~RtpDecoderNodeTest() {}

protected:
    AmrParams amr;
    EvsParams evs;
    AudioConfig audioConfig;
    VideoConfig videoConfig;
    TextConfig textConfig;
    RtcpConfig rtcp;
    RtpEncoderNode* encoder;
    RtpDecoderNode* decoder;
    FakeRtpDecoderNode* fakeNode;
    FakeRtpDecoderCallback callback;
    std::list<BaseNode*> nodes;

    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);
    }

    virtual void TearDown() override
    {
        while (nodes.size() > 0)
        {
            BaseNode* node = nodes.front();
            node->Stop();
            delete node;
            nodes.pop_front();
        }
    }

public:
    void setupNodes(ImsMediaType type, RtpConfig* config)
    {
        encoder = new RtpEncoderNode(&callback);
        encoder->SetMediaType(type);
        encoder->SetConfig(config);
        encoder->SetLocalAddress(RtpAddress(kRemoteAddress, kRemotePort));
        nodes.push_back(encoder);

        decoder = new RtpDecoderNode(&callback);
        decoder->SetMediaType(type);
        decoder->SetConfig(config);
        encoder->SetLocalAddress(RtpAddress(kRemoteAddress, kRemotePort));
        nodes.push_back(decoder);
        encoder->ConnectRearNode(decoder);

        fakeNode = new FakeRtpDecoderNode(&callback);
        fakeNode->SetMediaType(type);
        fakeNode->SetConfig(config);
        nodes.push_back(fakeNode);
        decoder->ConnectRearNode(fakeNode);
    }

    void setupAudioConfig()
    {
        amr.setAmrMode(kAmrMode);
        amr.setOctetAligned(kOctetAligned);
        amr.setMaxRedundancyMillis(kMaxRedundancyMillis);

        evs.setEvsBandwidth(kEvsBandwidth);
        evs.setEvsMode(kEvsMode);
        evs.setChannelAwareMode(kChannelAwareMode);
        evs.setUseHeaderFullOnly(kUseHeaderFullOnly);
        evs.setCodecModeRequest(kcodecModeRequest);

        audioConfig.setMediaDirection(kMediaDirection);
        audioConfig.setRemoteAddress(kRemoteAddress);
        audioConfig.setRemotePort(kRemotePort);
        audioConfig.setRtcpConfig(rtcp);
        audioConfig.setDscp(kDscp);
        audioConfig.setRxPayloadTypeNumber(kRxPayload);
        audioConfig.setTxPayloadTypeNumber(kTxPayload);
        audioConfig.setSamplingRateKHz(kSamplingRate);
        audioConfig.setPtimeMillis(kPTimeMillis);
        audioConfig.setMaxPtimeMillis(kMaxPtimeMillis);
        audioConfig.setDtxEnabled(kDtxEnabled);
        audioConfig.setCodecType(AudioConfig::CODEC_AMR);
        audioConfig.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        audioConfig.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        audioConfig.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
        audioConfig.setAmrParams(amr);
        audioConfig.setEvsParams(evs);

        setupNodes(IMS_MEDIA_AUDIO, &audioConfig);
    }

    void setupVideoConfig()
    {
        videoConfig.setMediaDirection(kMediaDirection);
        videoConfig.setRemoteAddress(kRemoteAddress);
        videoConfig.setRemotePort(kRemotePort);
        videoConfig.setRtcpConfig(rtcp);
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

        setupNodes(IMS_MEDIA_VIDEO, &videoConfig);
    }

    void setupTextConfig()
    {
        textConfig.setMediaDirection(kMediaDirection);
        textConfig.setRemoteAddress(kRemoteAddress);
        textConfig.setRemotePort(kRemotePort);
        textConfig.setRtcpConfig(rtcp);
        textConfig.setDscp(kDscp);
        textConfig.setRxPayloadTypeNumber(kRxPayload);
        textConfig.setTxPayloadTypeNumber(kTxPayload);
        textConfig.setSamplingRateKHz(kSamplingRate);
        textConfig.setCodecType(TextConfig::TEXT_T140_RED);
        textConfig.setBitrate(kBitrate);
        textConfig.setRedundantPayload(kRedundantPayload);
        textConfig.setRedundantLevel(kRedundantLevel);
        textConfig.setKeepRedundantLevel(kKeepRedundantLevel);

        setupNodes(IMS_MEDIA_TEXT, &textConfig);
    }
};

TEST_F(RtpDecoderNodeTest, startFail)
{
    setupAudioConfig();
    audioConfig.setRxPayloadTypeNumber(0);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(decoder->Start(), RESULT_INVALID_PARAM);

    audioConfig.setTxPayloadTypeNumber(0);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(decoder->Start(), RESULT_INVALID_PARAM);
}

TEST_F(RtpDecoderNodeTest, startAudioAndUpdate)
{
    setupAudioConfig();
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(decoder->UpdateConfig(&audioConfig), RESULT_SUCCESS);

    // update
    audioConfig.setTxDtmfPayloadTypeNumber(102);
    EXPECT_EQ(decoder->UpdateConfig(&audioConfig), RESULT_SUCCESS);
}

TEST_F(RtpDecoderNodeTest, testAudioDataProcess)
{
    setupAudioConfig();
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // AMR mode 6 payload frame
    uint8_t testFrame[] = {0x1c, 0x51, 0x06, 0x40, 0x32, 0xba, 0x8e, 0xc1, 0x25, 0x42, 0x2f, 0xc7,
            0xaf, 0x6e, 0xe0, 0xbb, 0xb2, 0x91, 0x09, 0xa5, 0xa6, 0x08, 0x18, 0x6f, 0x08, 0x1c,
            0x1c, 0x44, 0xd8, 0xe0, 0x48, 0x8c, 0x7c, 0xf8, 0x4c, 0x22, 0xd0};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0);
    encoder->ProcessData();
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    EXPECT_EQ(strncmp(reinterpret_cast<const char*>(fakeNode->GetDataFrame()),
                      reinterpret_cast<const char*>(testFrame), fakeNode->GetFrameSize()),
            0);
}

TEST_F(RtpDecoderNodeTest, testAudioDtmfDataProcess)
{
    setupAudioConfig();
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // dtmf rtp
    uint8_t dtmfFrame[] = {0x80, 0xe7, 0x7b, 0xaa, 0x00, 0x00, 0xc2, 0x5a, 0x6f, 0x88, 0xd8, 0x02,
            0x01, 0x0a, 0x00, 0xa0};

    decoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, dtmfFrame, sizeof(dtmfFrame), 0, true, 0,
            MEDIASUBTYPE_UNDEFINED, 0);
    EXPECT_EQ(callback.GetDtmfDigit(), 0);
    EXPECT_EQ(callback.GetDtmfDuration(), 0);

    uint8_t dtmfFrame2[] = {0x80, 0x67, 0x7b, 0xb3, 0x00, 0x00, 0xc2, 0x5a, 0x6f, 0x88, 0xd8, 0x02,
            0x01, 0x8a, 0x06, 0x40};

    decoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, dtmfFrame2, sizeof(dtmfFrame2), 0, false,
            0, MEDIASUBTYPE_UNDEFINED, 0);
    EXPECT_EQ(callback.GetDtmfDigit(), 0x01);
    EXPECT_EQ(callback.GetDtmfDuration(), 100);
}

TEST_F(RtpDecoderNodeTest, testAudioRtpExtension)
{
    setupAudioConfig();
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // AMR mode 6 payload frame
    uint8_t testFrame[] = {0x1c, 0x51, 0x06, 0x40, 0x32, 0xba, 0x8e, 0xc1, 0x25, 0x42, 0x2f, 0xc7,
            0xaf, 0x6e, 0xe0, 0xbb, 0xb2, 0x91, 0x09, 0xa5, 0xa6, 0x08, 0x18, 0x6f, 0x08, 0x1c,
            0x1c, 0x44, 0xd8, 0xe0, 0x48, 0x8c, 0x7c, 0xf8, 0x4c, 0x22, 0xd0};

    const uint8_t testExtension1[] = {0xFF, 0xF2};
    const uint8_t testExtension2[] = {0xFF, 0xF2};

    std::list<RtpHeaderExtension> listExtension;

    RtpHeaderExtension extension1;
    extension1.setLocalIdentifier(1);
    extension1.setExtensionData(testExtension1, 2);
    listExtension.push_back(extension1);

    RtpHeaderExtension extension2;
    extension2.setLocalIdentifier(2);
    extension2.setExtensionData(testExtension2, 2);
    listExtension.push_back(extension2);

    encoder->SetRtpHeaderExtension(&listExtension);
    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0);
    encoder->ProcessData();

    std::list<RtpHeaderExtension>* receivedExtension = callback.GetListExtension();
    ASSERT_TRUE(receivedExtension != nullptr);

    for (auto& extension : *receivedExtension)
    {
        EXPECT_EQ(extension, listExtension.front());
        listExtension.pop_front();
    }
}

TEST_F(RtpDecoderNodeTest, startVideoAndUpdate)
{
    setupVideoConfig();
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(decoder->UpdateConfig(&videoConfig), RESULT_SUCCESS);

    // update
    videoConfig.setTxPayloadTypeNumber(99);
    EXPECT_EQ(decoder->UpdateConfig(&videoConfig), RESULT_SUCCESS);
}

TEST_F(RtpDecoderNodeTest, testVideoDataProcess)
{
    setupVideoConfig();
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // H.264 payload of sps frame
    uint8_t testFrame[] = {0x67, 0x42, 0xc0, 0x0c, 0xda, 0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10,
            0x3c, 0x58, 0xba, 0x80};

    EXPECT_TRUE(encoder->SetCvoExtension(0, 90));  // rear camera and 90 degree

    encoder->OnDataFromFrontNode(
            MEDIASUBTYPE_VIDEO_IDR_FRAME, testFrame, sizeof(testFrame), 0, true, 0);
    encoder->ProcessData();
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    EXPECT_EQ(strncmp(reinterpret_cast<const char*>(fakeNode->GetDataFrame()),
                      reinterpret_cast<const char*>(testFrame), fakeNode->GetFrameSize()),
            0);
    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_ROT270);
}

TEST_F(RtpDecoderNodeTest, startTextAndUpdate)
{
    setupTextConfig();
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(decoder->UpdateConfig(&textConfig), RESULT_SUCCESS);

    // update
    textConfig.setTxPayloadTypeNumber(99);
    EXPECT_EQ(decoder->UpdateConfig(&textConfig), RESULT_SUCCESS);
}

TEST_F(RtpDecoderNodeTest, testTextDataProcess)
{
    setupTextConfig();
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // RED payload
    uint8_t testFrame[] = {0xef, 0x00, 0x00, 0x00, 0xef, 0x00, 0x00, 0x00, 0x6f, 0x74};

    encoder->OnDataFromFrontNode(
            MEDIASUBTYPE_BITSTREAM_T140_RED, testFrame, sizeof(testFrame), 0, true, 0);
    encoder->ProcessData();
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    EXPECT_EQ(strncmp(reinterpret_cast<const char*>(fakeNode->GetDataFrame()),
                      reinterpret_cast<const char*>(testFrame), fakeNode->GetFrameSize()),
            0);
}