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
#include <AudioRtpPayloadEncoderNode.h>
#include <AudioRtpPayloadDecoderNode.h>
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
const bool kDtxEnabled = true;
const int8_t kDtmfPayloadTypeNumber = 103;
const int8_t kDtmfsamplingRateKHz = 16;

// AmrParam
const int32_t kAmrMode = AmrParams::AMR_MODE_8;
const bool kOctetAligned = false;
const int32_t kMaxRedundancyMillis = 240;

// EvsParam
const int32_t kEvsBandwidth = EvsParams::EVS_SUPER_WIDE_BAND;
const int32_t kEvsMode = EvsParams::EVS_MODE_13;
const int8_t kChannelAwareMode = 2;

namespace
{
class FakeNode : public BaseNode
{
public:
    explicit FakeNode(BaseSessionCallback* callback = nullptr) :
            BaseNode(callback)
    {
        frameSize = 0;
        memset(dataFrame, 0, sizeof(dataFrame));
        subType = MEDIASUBTYPE_UNDEFINED;
    }
    virtual ~FakeNode() {}
    virtual ImsMediaResult Start() { return RESULT_SUCCESS; }
    virtual void Stop() {}
    virtual bool IsRunTime() { return true; }
    virtual bool IsSourceNode() { return false; }
    virtual void SetConfig(void* config) { (void)config; }
    virtual void OnDataFromFrontNode(ImsMediaSubType /*IMS_MEDIA_AUDIO*/, uint8_t* data,
            uint32_t size, uint32_t /*timestamp*/, bool /*mark*/, uint32_t /*seq*/,
            ImsMediaSubType dataType, uint32_t /*arrivalTime*/)
    {
        if (data != nullptr && size > 0)
        {
            memset(dataFrame, 0, sizeof(dataFrame));
            memcpy(dataFrame, data, size);
            frameSize = size;
            subType = dataType;
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

class AudioRtpPayloadNodeTest : public ::testing::Test
{
public:
    AudioRtpPayloadNodeTest()
    {
        encoder = nullptr;
        decoder = nullptr;
        fakeNode = nullptr;
    }
    virtual ~AudioRtpPayloadNodeTest() {}

protected:
    AmrParams amr;
    EvsParams evs;
    RtcpConfig rtcp;
    AudioConfig audioConfig;
    AudioRtpPayloadEncoderNode* encoder;
    AudioRtpPayloadDecoderNode* decoder;
    FakeNode* fakeNode;
    std::list<BaseNode*> nodes;

    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

        setupAudioConfig();
        setupNodes(&audioConfig);
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
    void setupNodes(AudioConfig* config)
    {
        encoder = new AudioRtpPayloadEncoderNode();
        encoder->SetMediaType(IMS_MEDIA_AUDIO);
        encoder->SetConfig(config);
        nodes.push_back(encoder);

        decoder = new AudioRtpPayloadDecoderNode();
        decoder->SetMediaType(IMS_MEDIA_AUDIO);
        decoder->SetConfig(config);
        nodes.push_back(decoder);
        encoder->ConnectRearNode(decoder);

        fakeNode = new FakeNode();
        fakeNode->SetMediaType(IMS_MEDIA_AUDIO);
        fakeNode->SetConfig(config);
        nodes.push_back(fakeNode);
        decoder->ConnectRearNode(fakeNode);
    }

    void setupAudioConfig()
    {
        amr.setAmrMode(kAmrMode);
        amr.setOctetAligned(kOctetAligned);
        amr.setMaxRedundancyMillis(kMaxRedundancyMillis);

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
        audioConfig.setCodecType(AudioConfig::CODEC_AMR_WB);
        audioConfig.setTxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        audioConfig.setRxDtmfPayloadTypeNumber(kDtmfPayloadTypeNumber);
        audioConfig.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
        audioConfig.setAmrParams(amr);
        audioConfig.setEvsParams(evs);
    }
};

TEST_F(AudioRtpPayloadNodeTest, startFail)
{
    audioConfig.setPtimeMillis(0);
    encoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_INVALID_PARAM);
}

TEST_F(AudioRtpPayloadNodeTest, startAndUpdate)
{
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(encoder->UpdateConfig(&audioConfig), RESULT_SUCCESS);
    EXPECT_EQ(decoder->UpdateConfig(&audioConfig), RESULT_SUCCESS);

    // update
    audioConfig.setCodecType(AudioConfig::CODEC_AMR);
    EXPECT_EQ(encoder->UpdateConfig(&audioConfig), RESULT_SUCCESS);
    EXPECT_EQ(decoder->UpdateConfig(&audioConfig), RESULT_SUCCESS);
}

TEST_F(AudioRtpPayloadNodeTest, testAmrBandwidthEfficientDataProcess)
{
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // AMR-WB mode 8 audio frame with toc field
    uint8_t testFrame[] = {0x44, 0xe6, 0x6e, 0x84, 0x8a, 0xa4, 0xda, 0xc8, 0xf2, 0x6c, 0xeb, 0x87,
            0xe4, 0x56, 0x0f, 0x49, 0x47, 0xfa, 0xdc, 0xa7, 0x9d, 0xbb, 0xcf, 0xda, 0xda, 0x67,
            0x80, 0xc2, 0x7f, 0x8d, 0x5b, 0xab, 0xd9, 0xbb, 0xd7, 0x1e, 0x60, 0x96, 0x5d, 0xdd,
            0x28, 0x65, 0x5f, 0x43, 0xf4, 0xb9, 0x0d, 0x7d, 0x05, 0x4e, 0x30, 0x50, 0xe1, 0x98,
            0x03, 0xed, 0xee, 0x8a, 0xa8, 0x34, 0x40};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_NORMAL);
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame(), testFrame, fakeNode->GetFrameSize()), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testAmrBandwidthEfficientSIDDataProcess)
{
    audioConfig.setCodecType(AudioConfig::CODEC_AMR);
    amr.setOctetAligned(false);
    audioConfig.setAmrParams(amr);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // AMR-WB mode 9 audio frame with toc field
    uint8_t testFrame[] = {0x40, 0xe6, 0x6e, 0x84, 0x8a, 0xa4};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_SID);
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    // Removing ToC byte from payload
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame() + 1, testFrame + 1, fakeNode->GetFrameSize() - 1), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testAmrWbBandwidthEfficientSIDDataProcess)
{
    audioConfig.setCodecType(AudioConfig::CODEC_AMR_WB);
    amr.setOctetAligned(false);
    audioConfig.setAmrParams(amr);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // AMR-WB mode 9 audio frame with toc field
    uint8_t testFrame[] = {0x48, 0xe6, 0x6e, 0x84, 0x8a, 0xa4};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_SID);
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    // Removing ToC byte from payload
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame() + 1, testFrame + 1, fakeNode->GetFrameSize() - 1), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testAmrOctetAlignedSIDDataProcess)
{
    audioConfig.setCodecType(AudioConfig::CODEC_AMR);
    amr.setOctetAligned(true);
    audioConfig.setAmrParams(amr);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // AMR-WB mode 9 audio frame with toc field
    uint8_t testFrame[] = {0x40, 0xe6, 0x6e, 0x84, 0x8a, 0xa4};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_SID);
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    // Removing ToC byte from payload
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame() + 1, testFrame + 1, fakeNode->GetFrameSize() - 1), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testAmrOctetAlignedDataProcess)
{
    amr.setOctetAligned(true);
    audioConfig.setAmrParams(amr);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // AMR-WB mode 8 audio frame with toc field
    uint8_t testFrame[] = {0x44, 0xe6, 0x6e, 0x84, 0x8a, 0xa4, 0xda, 0xc8, 0xf2, 0x6c, 0xeb, 0x87,
            0xe4, 0x56, 0x0f, 0x49, 0x47, 0xfa, 0xdc, 0xa7, 0x9d, 0xbb, 0xcf, 0xda, 0xda, 0x67,
            0x80, 0xc2, 0x7f, 0x8d, 0x5b, 0xab, 0xd9, 0xbb, 0xd7, 0x1e, 0x60, 0x96, 0x5d, 0xdd,
            0x28, 0x65, 0x5f, 0x43, 0xf4, 0xb9, 0x0d, 0x7d, 0x05, 0x4e, 0x30, 0x50, 0xe1, 0x98,
            0x03, 0xed, 0xee, 0x8a, 0xa8, 0x34, 0x40};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_NORMAL);
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame(), testFrame, fakeNode->GetFrameSize()), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testAmrWbOctetAlignedSIDDataProcess)
{
    audioConfig.setCodecType(AudioConfig::CODEC_AMR_WB);
    amr.setOctetAligned(true);
    audioConfig.setAmrParams(amr);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // AMR-WB mode 9 audio frame with toc field
    uint8_t testFrame[] = {0x48, 0xe6, 0x6e, 0x84, 0x8a, 0xa4};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_SID);
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    // Removing ToC byte from payload
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame() + 1, testFrame + 1, fakeNode->GetFrameSize() - 1), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testEvsCompactModeDataProcess)
{
    evs.setEvsBandwidth(kEvsBandwidth);
    evs.setEvsMode(kEvsMode);
    evs.setChannelAwareMode(kChannelAwareMode);
    evs.setUseHeaderFullOnly(false);
    evs.setCodecModeRequest(-1);

    audioConfig.setEvsParams(evs);
    audioConfig.setCodecType(AudioConfig::CODEC_EVS);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // EVS mode 13.2 kbps frame without toc field
    uint8_t testFrame[] = {0xce, 0x40, 0xf2, 0xb2, 0xa4, 0xce, 0x4f, 0xd9, 0xfa, 0xe9, 0x77, 0xdc,
            0x9b, 0xc0, 0xa8, 0x10, 0xc8, 0xc3, 0x0f, 0xc9, 0x52, 0xc1, 0xda, 0x45, 0x7e, 0x6c,
            0x55, 0x47, 0xff, 0xff, 0xff, 0xff, 0xe0};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_NORMAL);
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame(), testFrame, fakeNode->GetFrameSize()), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testEvsHeaderFullModeDataProcess)
{
    evs.setEvsBandwidth(kEvsBandwidth);
    evs.setEvsMode(kEvsMode);
    evs.setChannelAwareMode(kChannelAwareMode);
    evs.setUseHeaderFullOnly(true);
    evs.setCodecModeRequest(-1);

    audioConfig.setEvsParams(evs);
    audioConfig.setCodecType(AudioConfig::CODEC_EVS);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // EVS mode 13.2 kbps frame with toc field
    uint8_t testFrame[] = {0xce, 0x40, 0xf2, 0xb2, 0xa4, 0xce, 0x4f, 0xd9, 0xfa, 0xe9, 0x77, 0xdc,
            0x9b, 0xc0, 0xa8, 0x10, 0xc8, 0xc3, 0x0f, 0xc9, 0x52, 0xc1, 0xda, 0x45, 0x7e, 0x6c,
            0x55, 0x47, 0xff, 0xff, 0xff, 0xff, 0xe0};

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_NORMAL);
    // 0x04 is the ToC header for the 13.2kbps bitrate
    EXPECT_EQ(*(fakeNode->GetDataFrame()), 0x04);
    // Compairing one byte extra as PayloadEncoder will have added one Toc header as it's HeaderFull
    // mode test.
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame) + 1);
    // Removing Toc header from the comparison as testFrame does not have toc header included
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame() + 1, testFrame, fakeNode->GetFrameSize() - 1), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testEvsHeaderFullModeSIDDataProcess)
{
    evs.setEvsBandwidth(kEvsBandwidth);
    evs.setEvsMode(kEvsMode);
    evs.setChannelAwareMode(kChannelAwareMode);
    evs.setUseHeaderFullOnly(true);
    evs.setCodecModeRequest(-1);

    audioConfig.setEvsParams(evs);
    audioConfig.setCodecType(AudioConfig::CODEC_EVS);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // EVS mode 13.2 kbps frame with toc field
    uint8_t testFrame[] = {0xce, 0x40, 0xf2, 0xb2, 0xa4, 0xce};

    // ImsMediaSubType subtype;

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    // 0x04 is the ToC header for the SID packet
    EXPECT_EQ(*(fakeNode->GetDataFrame()), 0x0C);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_SID);
    // Compairing one byte extra as PayloadEncoder will have added one Toc header as it's HeaderFull
    // mode test.
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame) + 1);
    // Removing Toc header from the comparison as testFrame does not have toc header included
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame() + 1, testFrame, fakeNode->GetFrameSize() - 1), 0);
}

TEST_F(AudioRtpPayloadNodeTest, testEvsCompactModeSIDDataProcess)
{
    evs.setEvsBandwidth(kEvsBandwidth);
    evs.setEvsMode(kEvsMode);
    evs.setChannelAwareMode(kChannelAwareMode);
    evs.setUseHeaderFullOnly(false);
    evs.setCodecModeRequest(-1);

    audioConfig.setEvsParams(evs);
    audioConfig.setCodecType(AudioConfig::CODEC_EVS);
    encoder->SetConfig(&audioConfig);
    decoder->SetConfig(&audioConfig);
    EXPECT_EQ(encoder->Start(), RESULT_SUCCESS);
    EXPECT_EQ(decoder->Start(), RESULT_SUCCESS);

    // EVS mode 13.2 kbps frame with toc field
    uint8_t testFrame[] = {0xce, 0x40, 0xf2, 0xb2, 0xa4, 0xce};

    // ImsMediaSubType subtype;

    encoder->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, testFrame, sizeof(testFrame), 0, false, 0,
            MEDIASUBTYPE_UNDEFINED);

    EXPECT_EQ(fakeNode->GetSubType(), MEDIASUBTYPE_AUDIO_SID);
    // Compairing one byte extra as PayloadEncoder will have added one Toc header as it's HeaderFull
    // mode test.
    EXPECT_EQ(fakeNode->GetFrameSize(), sizeof(testFrame));
    // Removing Toc header from the comparison as testFrame does not have toc header included
    EXPECT_EQ(memcmp(fakeNode->GetDataFrame(), testFrame, fakeNode->GetFrameSize()), 0);
}

}  // namespace
