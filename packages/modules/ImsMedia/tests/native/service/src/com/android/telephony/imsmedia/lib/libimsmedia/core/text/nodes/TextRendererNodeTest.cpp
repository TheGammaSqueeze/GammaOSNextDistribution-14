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
#include <gmock/gmock.h>

#include <TextConfig.h>
#include <TextRendererNode.h>
#include <ImsMediaCondition.h>
#include <ImsMediaTrace.h>

using namespace android::telephony::imsmedia;
using namespace android;

const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_SEND_ONLY;
const String8 kRemoteAddress("127.0.0.1");
const int32_t kRemotePort = 10000;
const int8_t kDscp = 0;
const int8_t kRxPayload = 102;
const int8_t kTxPayload = 102;
const int8_t kSamplingRate = 16;

// RtcpConfig
const String8 kCanonicalName("name");
const int32_t kTransmitPort = 10001;
const int32_t kIntervalSec = 5;
const int32_t kRtcpXrBlockTypes = 0;

// TextConfig
const int32_t kCodecType = TextConfig::TEXT_T140_RED;
const int32_t kBitrate = 100;
const int8_t kRedundantPayload = 101;
const int8_t kRedundantLevel = 2;
const bool kKeepRedundantLevel = true;
const char* kBomString = {"\xef\xbb\xbf"};
const String8 strCharReplacement = String8("\xEf\xbf\xbd");

class TextRendererCallback : public BaseSessionCallback
{
public:
    TextRendererCallback() { mCountPacketLoss = 0; }
    virtual ~TextRendererCallback() {}

    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2)
    {
        (void)param2;
        ASSERT_TRUE(type != kImsMediaEventNotifyError);
        String8* text = reinterpret_cast<String8*>(param1);
        ASSERT_TRUE(text != nullptr);
        mReceivedText = *text;

        if (mReceivedText == strCharReplacement)
        {
            mCountPacketLoss++;
        }
        delete text;
    }

    int32_t getCountPacketLoss() { return mCountPacketLoss; }
    String8 getReceivedText() { return mReceivedText; }

private:
    int32_t mCountPacketLoss;
    String8 mReceivedText;
};

class TextRendererNodeTest : public ::testing::Test
{
public:
    TextRendererNodeTest()
    {
        mFakeCallback = nullptr;
        mNode = nullptr;
    }
    virtual ~TextRendererNodeTest() {}

protected:
    TextConfig mConfig;
    RtcpConfig mRtcp;
    TextRendererCallback* mFakeCallback;
    TextRendererNode* mNode;

    virtual void SetUp() override
    {
        mRtcp.setCanonicalName(kCanonicalName);
        mRtcp.setTransmitPort(kTransmitPort);
        mRtcp.setIntervalSec(kIntervalSec);
        mRtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

        mConfig.setMediaDirection(kMediaDirection);
        mConfig.setRemoteAddress(kRemoteAddress);
        mConfig.setRemotePort(kRemotePort);
        mConfig.setRtcpConfig(mRtcp);
        mConfig.setDscp(kDscp);
        mConfig.setRxPayloadTypeNumber(kRxPayload);
        mConfig.setTxPayloadTypeNumber(kTxPayload);
        mConfig.setSamplingRateKHz(kSamplingRate);
        mConfig.setCodecType(kCodecType);
        mConfig.setBitrate(kBitrate);
        mConfig.setRedundantPayload(kRedundantPayload);
        mConfig.setRedundantLevel(kRedundantLevel);
        mConfig.setKeepRedundantLevel(kKeepRedundantLevel);

        mFakeCallback = new TextRendererCallback();
        mNode = new TextRendererNode(mFakeCallback);
        mNode->SetMediaType(IMS_MEDIA_TEXT);
        mNode->SetConfig(&mConfig);
    }

    virtual void TearDown() override
    {
        delete mNode;
        delete mFakeCallback;
    }
};

TEST_F(TextRendererNodeTest, startFail)
{
    mConfig.setCodecType(TextConfig::TEXT_CODEC_NONE);
    mNode->SetConfig(&mConfig);
    EXPECT_EQ(mNode->Start(), RESULT_INVALID_PARAM);
}

TEST_F(TextRendererNodeTest, receiveNormalRttString)
{
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    String8 testString = String8("hello");

    std::unique_ptr<char> tempBuffer1(new char[strlen(kBomString)]);
    memcpy(tempBuffer1.get(), kBomString, strlen(kBomString));

    mNode->OnDataFromFrontNode(
            MEDIASUBTYPE_UNDEFINED, reinterpret_cast<uint8_t*>(tempBuffer1.get()), 3, 0, true, 0);

    std::unique_ptr<char> tempBuffer2(new char[testString.length()]);
    memcpy(tempBuffer2.get(), testString.c_str(), testString.length());

    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED,
            reinterpret_cast<uint8_t*>(tempBuffer2.get()), testString.length(), 1, false, 1);

    mNode->ProcessData();
    EXPECT_EQ(mFakeCallback->getReceivedText(), testString);

    mNode->Stop();
}

TEST_F(TextRendererNodeTest, receiveChunkRttString)
{
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    std::list<String8> listStrings;
    listStrings.push_back(String8(kBomString));
    listStrings.push_back(String8("\xC2\xA9"));
    listStrings.push_back(String8("\xE2\x9C\x82"));
    listStrings.push_back(String8("\xF0\x9F\x9A\x80"));

    std::list<String8>::iterator iter;
    int32_t index = 0;

    for (iter = listStrings.begin(); iter != listStrings.end(); iter++, index++)
    {
        String8 text = *iter;
        std::unique_ptr<char> tempBuffer(new char[text.length()]);
        memcpy(tempBuffer.get(), text.c_str(), text.length());

        mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED,
                reinterpret_cast<uint8_t*>(tempBuffer.get()), text.length(), index, false, index);
        mNode->ProcessData();

        if (index > 0)  // except BOM
        {
            EXPECT_EQ(mFakeCallback->getReceivedText(), text);
        }
    }

    mNode->Stop();
}

TEST_F(TextRendererNodeTest, receiveRttBomAppended)
{
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    String8 testString1 = String8(kBomString);
    String8 testString2 = String8("hello");
    testString1.append(testString2);

    std::unique_ptr<char> tempBuffer(new char[testString1.length()]);
    memcpy(tempBuffer.get(), testString1.c_str(), testString1.length());

    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, reinterpret_cast<uint8_t*>(tempBuffer.get()),
            testString1.length(), 1, true, 1);
    mNode->ProcessData();

    EXPECT_EQ(mFakeCallback->getReceivedText(), testString2);

    mNode->Stop();
}

TEST_F(TextRendererNodeTest, receiveRttStringSeqOutOfOrder)
{
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    String8 testString1 = String8("hello");
    String8 testString2 = String8("world");

    std::unique_ptr<char> tempBuffer1(new char[testString1.length()]);
    memcpy(tempBuffer1.get(), testString1.c_str(), testString1.length());

    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED,
            reinterpret_cast<uint8_t*>(tempBuffer1.get()), testString1.length(), 1, false, 1);

    std::unique_ptr<char> tempBuffer2(new char[testString2.length()]);
    memcpy(tempBuffer2.get(), testString2.c_str(), testString2.length());

    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED,
            reinterpret_cast<uint8_t*>(tempBuffer2.get()), testString2.length(), 0, false, 0);

    mNode->ProcessData();
    // Check last frame sequence
    EXPECT_EQ(mFakeCallback->getReceivedText(), testString1);

    // Check ignore already played sequence
    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED,
            reinterpret_cast<uint8_t*>(tempBuffer2.get()), testString2.length(), 1, false, 1);

    mNode->ProcessData();
    EXPECT_EQ(mFakeCallback->getReceivedText(), testString1);

    mNode->Stop();
}

TEST_F(TextRendererNodeTest, receiveRttWithSeqRoundingWithLoss)
{
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    String8 testString1 = String8("hello");
    String8 testString2 = String8("world");
    const uint32_t seq1 = 0xffff;
    const uint32_t numLost = 3;
    const uint32_t seq2 = numLost;

    std::unique_ptr<char> tempBuffer1(new char[testString1.length()]);
    memcpy(tempBuffer1.get(), testString1.c_str(), testString1.length());

    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED,
            reinterpret_cast<uint8_t*>(tempBuffer1.get()), testString1.length(), seq1, true, seq1);
    mNode->ProcessData();
    EXPECT_EQ(mFakeCallback->getReceivedText(), testString1);

    std::unique_ptr<char> tempBuffer2(new char[testString2.length()]);
    memcpy(tempBuffer2.get(), testString2.c_str(), testString2.length());

    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED,
            reinterpret_cast<uint8_t*>(tempBuffer2.get()), testString2.length(), seq2, true, seq2);
    mNode->ProcessData();

    ImsMediaCondition condition;
    condition.wait_timeout(1100);  // wait more than 1 sec

    mNode->ProcessData();
    EXPECT_EQ(mFakeCallback->getCountPacketLoss(), numLost);
    EXPECT_EQ(mFakeCallback->getReceivedText(), testString2);

    mNode->Stop();
}

TEST_F(TextRendererNodeTest, receiveOversizeRtt)
{
    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);

    String8 testString1;

    for (int32_t i = 0; i < MAX_RTT_LEN; i++)
    {
        testString1.append("a");
    }

    // make oversize string
    const int numChunk = 4;
    String8 testString2;

    for (int32_t i = 0; i < numChunk; i++)
    {
        testString2.append(testString1);
    }

    std::unique_ptr<char> tempBuffer(new char[testString2.length()]);
    memcpy(tempBuffer.get(), testString2.c_str(), testString2.length());

    mNode->OnDataFromFrontNode(MEDIASUBTYPE_UNDEFINED, reinterpret_cast<uint8_t*>(tempBuffer.get()),
            testString2.length(), 0, true, 0);

    mNode->ProcessData();
    EXPECT_EQ(mFakeCallback->getReceivedText(), testString1);

    mNode->Stop();
}