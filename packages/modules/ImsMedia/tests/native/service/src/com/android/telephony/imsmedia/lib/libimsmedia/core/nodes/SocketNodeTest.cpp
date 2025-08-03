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
#include <MockBaseNode.h>
#include <ImsMediaNetworkUtil.h>
#include <ImsMediaCondition.h>
#include <SocketReaderNode.h>
#include <SocketWriterNode.h>
#include <thread>

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

using ::testing::_;
using ::testing::Eq;
using ::testing::Pointee;
using ::testing::Return;

class FakeSocketReader : public SocketReaderNode
{
public:
    virtual ~FakeSocketReader() {}
    void callCloseSocket() { CloseSocket(); }
};

class SocketNodeTest : public ::testing::Test
{
public:
    SocketNodeTest()
    {
        mReader = nullptr;
        mWriter = nullptr;
        mSocketRtpFd = -1;
    }
    virtual ~SocketNodeTest() {}

    void StopNode(BaseNode* node)
    {
        if (node != nullptr)
        {
            node->Stop();
            reinterpret_cast<FakeSocketReader*>(node)->callCloseSocket();
        }

        mCondition.signal();
    }

protected:
    AmrParams mAmr;
    EvsParams mEvs;
    AudioConfig mAudioConfig;
    RtcpConfig mRtcp;
    FakeSocketReader* mReader;
    SocketWriterNode* mWriter;
    MockBaseNode mMockNode;
    int mSocketRtpFd;
    ImsMediaCondition mCondition;

    virtual void SetUp() override
    {
        mRtcp.setCanonicalName(kCanonicalName);
        mRtcp.setTransmitPort(kTransmitPort);
        mRtcp.setIntervalSec(kIntervalSec);
        mRtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

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

        mSocketRtpFd = ImsMediaNetworkUtil::openSocket(kRemoteAddress, kRemotePort, AF_INET);
        EXPECT_NE(mSocketRtpFd, -1);
        RtpAddress testAddress(kRemoteAddress, kRemotePort);

        mReader = new FakeSocketReader();
        mReader->SetMediaType(IMS_MEDIA_AUDIO);
        mReader->SetProtocolType(kProtocolRtp);
        mReader->SetLocalFd(mSocketRtpFd);
        mReader->SetLocalAddress(testAddress);
        mReader->SetConfig(&mAudioConfig);
        mReader->Prepare();

        mWriter = new SocketWriterNode();
        mWriter->SetMediaType(IMS_MEDIA_AUDIO);
        mWriter->SetProtocolType(kProtocolRtp);
        mWriter->SetConfig(&mAudioConfig);
        mWriter->SetLocalFd(mSocketRtpFd);
        mWriter->SetLocalAddress(testAddress);

        mReader->ConnectRearNode(&mMockNode);

        ON_CALL(mMockNode, GetState).WillByDefault(Return(kNodeStateRunning));
    }

    virtual void TearDown() override
    {
        mReader->DisconnectNodes();

        if (mReader != nullptr)
        {
            delete mReader;
        }

        mWriter->DisconnectNodes();

        if (mWriter != nullptr)
        {
            delete mWriter;
        }

        if (mSocketRtpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(mSocketRtpFd);
        }
    }
};

TEST_F(SocketNodeTest, startNode)
{
    EXPECT_EQ(mReader->Start(), RESULT_SUCCESS);
    EXPECT_EQ(mWriter->Start(), RESULT_SUCCESS);

    mReader->Stop();
    EXPECT_EQ(mReader->IsSocketOpened(), true);

    mWriter->Stop();
}

TEST_F(SocketNodeTest, startAndUpdate)
{
    EXPECT_EQ(mReader->Start(), RESULT_SUCCESS);
    EXPECT_EQ(mWriter->Start(), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(mReader->IsSameConfig(&mAudioConfig), true);
    EXPECT_EQ(mReader->UpdateConfig(&mAudioConfig), RESULT_SUCCESS);

    // no update
    EXPECT_EQ(mWriter->IsSameConfig(&mAudioConfig), true);
    EXPECT_EQ(mWriter->UpdateConfig(&mAudioConfig), RESULT_SUCCESS);

    // update remote address
    mAudioConfig.setRemotePort(20000);
    EXPECT_EQ(mReader->IsSameConfig(&mAudioConfig), false);
    EXPECT_EQ(mReader->UpdateConfig(&mAudioConfig), RESULT_SUCCESS);

    // update remote address
    EXPECT_EQ(mWriter->IsSameConfig(&mAudioConfig), false);
    EXPECT_EQ(mWriter->UpdateConfig(&mAudioConfig), RESULT_SUCCESS);

    mReader->Stop();
    mWriter->Stop();
}

TEST_F(SocketNodeTest, testDataProcess)
{
    EXPECT_EQ(mReader->Start(), RESULT_SUCCESS);
    EXPECT_EQ(mWriter->Start(), RESULT_SUCCESS);

    uint8_t testPacket[] = {0x80, 0x68, 0x00, 0x0b, 0xbc, 0xbc, 0xe8, 0xa4, 0x00, 0x04, 0x11, 0x68,
            0xf4, 0xfa, 0xfe, 0x67, 0x58, 0x84, 0x80};

    EXPECT_CALL(mMockNode,
            OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, Pointee(Eq(*testPacket)),
                    sizeof(testPacket), 0, false, 0, _, _));

    mWriter->OnDataFromFrontNode(
            MEDIASUBTYPE_UNDEFINED, testPacket, sizeof(testPacket), 0, false, 0);
    mCondition.wait_timeout(20);
    mReader->ProcessData();

    mReader->Stop();
    mWriter->Stop();
}

TEST_F(SocketNodeTest, testIncomingPacketAfterStop)
{
    EXPECT_EQ(mReader->Start(), RESULT_SUCCESS);
    EXPECT_EQ(mWriter->Start(), RESULT_SUCCESS);

    mReader->Stop();
    EXPECT_EQ(mReader->GetState(), kNodeStateStopped);

    uint8_t testPacket[] = {0x80, 0x68, 0x00, 0x0b, 0xbc, 0xbc, 0xe8, 0xa4, 0x00, 0x04, 0x11, 0x68,
            0xf4, 0xfa, 0xfe, 0x67, 0x58, 0x84, 0x80};

    ON_CALL(mMockNode, GetState).WillByDefault(Return(kNodeStateStopped));
    EXPECT_CALL(mMockNode,
            OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, Pointee(Eq(*testPacket)),
                    sizeof(testPacket), 0, false, 0, _, _))
            .Times(0);

    mWriter->OnDataFromFrontNode(
            MEDIASUBTYPE_UNDEFINED, testPacket, sizeof(testPacket), 0, false, 0);
    mCondition.wait_timeout(20);
    EXPECT_EQ(mReader->Start(), RESULT_SUCCESS);
    EXPECT_EQ(mReader->GetState(), kNodeStateRunning);
    mWriter->Stop();
    mReader->Stop();
}

TEST_F(SocketNodeTest, testIncomingPacketWhenClosing)
{
    EXPECT_EQ(mWriter->Start(), RESULT_SUCCESS);

    uint8_t testPacket[] = {0x80, 0x68, 0x00, 0x0b, 0xbc, 0xbc, 0xe8, 0xa4, 0x00, 0x04, 0x11, 0x68,
            0xf4, 0xfa, 0xfe, 0x67, 0x58, 0x84, 0x80};

    EXPECT_EQ(mReader->Start(), RESULT_SUCCESS);
    std::thread t1(&SocketWriterNode::OnDataFromFrontNode, mWriter, MEDIASUBTYPE_UNDEFINED,
            testPacket, sizeof(testPacket), 0, false, 0, MEDIASUBTYPE_UNDEFINED, 0);

    std::thread t2(&SocketNodeTest::StopNode, this, mReader);
    mCondition.wait_timeout(500);
    EXPECT_EQ(mReader->GetState(), kNodeStateStopped);

    t1.join();
    t2.join();
}