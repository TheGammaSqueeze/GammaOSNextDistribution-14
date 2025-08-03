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
#include <TextConfig.h>
#include <TextSourceNode.h>
#include <ImsMediaCondition.h>
#include <string.h>

using namespace android::telephony::imsmedia;
using namespace android;

// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_SEND_ONLY;
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

// TextConfig
const int32_t kCodecType = TextConfig::TEXT_T140_RED;
const int32_t kBitrate = 100;
const int8_t kRedundantPayload = 102;
const int8_t kRedundantLevel = 2;
const bool kKeepRedundantLevel = true;
const int kTextInterval = 300;
const uint8_t kBom[] = {0xEF, 0xBB, 0xBF};

class FakeTextNode : public BaseNode
{
public:
    FakeTextNode()
    {
        mEmptyFlag = false;
        memset(mData, 0, sizeof(mData));
    }
    virtual ~FakeTextNode() {}
    virtual ImsMediaResult Start() { return RESULT_SUCCESS; }
    virtual void Stop() {}
    virtual bool IsRunTime() { return true; }
    virtual bool IsSourceNode() { return false; }
    virtual void SetConfig(void* config) { (void)config; }
    virtual void OnDataFromFrontNode(ImsMediaSubType /*subtype*/, uint8_t* data, uint32_t size,
            uint32_t /*timestamp*/, bool /*mark*/, uint32_t /*seq*/, ImsMediaSubType /*dataType*/,
            uint32_t /*arrivalTime*/)
    {
        if (size != 0 && size <= MAX_RTT_LEN)
        {
            memset(mData, 0, sizeof(mData));
            memcpy(mData, data, size);
            mEmptyFlag = false;
        }
        else if (data == nullptr)
        {
            mEmptyFlag = true;
        }
    }

    virtual kBaseNodeState GetState() { return kNodeStateRunning; }

    uint8_t* getData() { return mData; }
    bool getEmptyFlag() { return mEmptyFlag; }

private:
    uint8_t mData[MAX_RTT_LEN + 1];
    bool mEmptyFlag;
};

class TextSourceNodeTest : public ::testing::Test
{
public:
    TextSourceNodeTest()
    {
        mNode = NULL;
        mFakeNode = NULL;
    }
    virtual ~TextSourceNodeTest() {}

protected:
    TextConfig mConfig;
    RtcpConfig mRtcp;
    ImsMediaCondition mCondition;
    TextSourceNode* mNode;
    FakeTextNode* mFakeNode;
    std::list<BaseNode*> mNodes;

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

        mNode = new TextSourceNode();
        mNode->SetMediaType(IMS_MEDIA_TEXT);
        mNode->SetConfig(&mConfig);
        mNodes.push_back(mNode);

        mFakeNode = new FakeTextNode();
        mFakeNode->SetMediaType(IMS_MEDIA_TEXT);
        mFakeNode->SetConfig(&mConfig);
        mNodes.push_back(mFakeNode);
        mNode->ConnectRearNode(mFakeNode);
        mCondition.reset();
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
};

TEST_F(TextSourceNodeTest, startFail)
{
    mConfig.setCodecType(TextConfig::TEXT_CODEC_NONE);
    mNode->SetConfig(&mConfig);
    EXPECT_EQ(mNode->Start(), RESULT_INVALID_PARAM);
}

TEST_F(TextSourceNodeTest, sendRttTestChunkSizeOne)
{
    String8 testText1 = String8("a");

    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);
    EXPECT_FALSE(mFakeNode->getEmptyFlag());
    mNode->SendRtt(&testText1);

    mNode->ProcessData();
    // expect BOM
    EXPECT_EQ(memcmp(mFakeNode->getData(), kBom, sizeof(kBom)), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    EXPECT_EQ(memcmp(mFakeNode->getData(), testText1.c_str(), testText1.length()), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    // expect empty flag set
    EXPECT_TRUE(mFakeNode->getEmptyFlag());
}

TEST_F(TextSourceNodeTest, sendRttTestChunkSizeTwo)
{
    String8 testText2 = String8("\xC2\xA9");

    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);
    EXPECT_FALSE(mFakeNode->getEmptyFlag());
    mNode->SendRtt(&testText2);

    mNode->ProcessData();
    // expect BOM
    EXPECT_EQ(memcmp(mFakeNode->getData(), kBom, sizeof(kBom)), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    EXPECT_EQ(memcmp(mFakeNode->getData(), testText2.c_str(), testText2.length()), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    // expect empty flag set
    EXPECT_TRUE(mFakeNode->getEmptyFlag());
}

TEST_F(TextSourceNodeTest, sendRttTestChunkSizeThree)
{
    String8 testText3 = String8("\xE2\x9C\x82");

    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);
    EXPECT_FALSE(mFakeNode->getEmptyFlag());
    mNode->SendRtt(&testText3);

    mNode->ProcessData();
    // expect BOM
    EXPECT_EQ(memcmp(mFakeNode->getData(), kBom, sizeof(kBom)), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    EXPECT_EQ(memcmp(mFakeNode->getData(), testText3.c_str(), testText3.length()), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    // expect empty flag set
    EXPECT_TRUE(mFakeNode->getEmptyFlag());
}

TEST_F(TextSourceNodeTest, sendRttTestChunkSizeFour)
{
    String8 testText4 = String8("\xF0\x9F\x9A\x80");

    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);
    EXPECT_FALSE(mFakeNode->getEmptyFlag());
    mNode->SendRtt(&testText4);

    mNode->ProcessData();
    // expect BOM
    EXPECT_EQ(memcmp(mFakeNode->getData(), kBom, sizeof(kBom)), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    EXPECT_EQ(memcmp(mFakeNode->getData(), testText4.c_str(), testText4.length()), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    // expect empty flag set
    EXPECT_TRUE(mFakeNode->getEmptyFlag());
}

TEST_F(TextSourceNodeTest, sendRttTextOverMaxCharacter)
{
    String8 testText = String8("aaaaaaaaaabbbbbbbbbb");
    String8 confirmText1 = String8("aaaaaaaaaa");
    String8 confirmText2 = String8("bbbbbbbbbb");

    EXPECT_EQ(mNode->Start(), RESULT_SUCCESS);
    EXPECT_FALSE(mFakeNode->getEmptyFlag());
    mNode->SendRtt(&testText);

    mNode->ProcessData();
    // expect BOM
    EXPECT_EQ(memcmp(mFakeNode->getData(), kBom, sizeof(kBom)), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    EXPECT_EQ(memcmp(mFakeNode->getData(), confirmText1.c_str(), confirmText1.length()), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    EXPECT_EQ(memcmp(mFakeNode->getData(), confirmText2.c_str(), confirmText2.length()), 0);

    mCondition.wait_timeout(kTextInterval);
    mNode->ProcessData();
    // expect empty flag set
    EXPECT_TRUE(mFakeNode->getEmptyFlag());
}
