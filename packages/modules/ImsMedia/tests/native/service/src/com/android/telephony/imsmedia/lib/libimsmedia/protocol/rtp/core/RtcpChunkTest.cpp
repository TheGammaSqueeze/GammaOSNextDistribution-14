/*
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

#include <RtcpChunk.h>
#include <gtest/gtest.h>

namespace android
{

class RtcpChunkTest : public ::testing::Test
{
public:
    RtcpChunk* testRtcpChunk;
    RtpDt_UInt16 chunkLen = RTP_ZERO;

protected:
    virtual void SetUp() override
    {
        testRtcpChunk = new RtcpChunk();
        ASSERT_TRUE(testRtcpChunk != nullptr);
    }

    virtual void TearDown() override
    {
        if (testRtcpChunk)
        {
            delete testRtcpChunk;
            testRtcpChunk = nullptr;
        }
    }
};

/** Success Test scenario with Single SDES Item */
TEST_F(RtcpChunkTest, decodeChunkItem)
{
    RtpDt_UChar* pucChunkBuf = new RtpDt_UChar[60];

    tRTCP_SDES_ITEM* chunkItem = new tRTCP_SDES_ITEM();

    chunkItem->ucType = 1;
    chunkItem->ucLength = 18;

    RtpDt_UChar* pcBuffer = new RtpDt_UChar[chunkItem->ucLength];

    memcpy(pcBuffer, "sleepy@example.com", chunkItem->ucLength);

    chunkItem->pValue = pcBuffer;

    RtcpConfigInfo* mRtcpConfigInfo = new RtcpConfigInfo();
    mRtcpConfigInfo->setSdesItemCount(1);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 1);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(chunkItem, 1));

    /* pucChunkBuf injected with single ssrc number */

    /*
    * Real-time Transport Control Protocol (Sdes Packet)
    * [Common Header]
    * Sdes Item Type: 1 (0x01)
    *  Sdes Item Length: 18 (0x12)
    *      Sdes Item Payload: "sleepy@example.com" (0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40,
                               0x65, 0x78, 0x61, 0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D)
    */
    memcpy(pucChunkBuf,
            (RtpDt_UChar[]){0x01, 0x12, 0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40, 0x65, 0x78, 0x61,
                    0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D},
            20);

    EXPECT_TRUE(testRtcpChunk->decodeRtcpChunk(pucChunkBuf, chunkLen, mRtcpConfigInfo));

    std::list<tRTCP_SDES_ITEM*> sdesItemList = testRtcpChunk->getSdesItemList();

    tRTCP_SDES_ITEM* sdesItem = sdesItemList.front();

    EXPECT_EQ(sdesItem->ucType, 1);
    EXPECT_EQ(sdesItem->ucLength, 18);
    // EXPECT_STREQ(sdesItem->pValue,"sleepy@example.com");

    delete[] pucChunkBuf;
    delete[] pcBuffer;
    delete chunkItem;
    delete mRtcpConfigInfo;
}

/** Success Test scenario with Multiple SDES items */
TEST_F(RtcpChunkTest, decodeMultichunkItem)
{
    RtpDt_UChar* pucChunkBuf = new RtpDt_UChar[60];
    RtcpConfigInfo* mRtcpConfigInfo = new RtcpConfigInfo();

    // First SDES Items

    tRTCP_SDES_ITEM* chunkItem = new tRTCP_SDES_ITEM();

    chunkItem->ucType = 1;
    chunkItem->ucLength = 18;

    RtpDt_UChar* pcBuffer = new RtpDt_UChar[chunkItem->ucLength];

    memcpy(pcBuffer, "sleepy@example.com", chunkItem->ucLength);

    chunkItem->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(1);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 1);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(chunkItem, 1));

    // Second SDES Items

    tRTCP_SDES_ITEM* chunkItemSec = new tRTCP_SDES_ITEM();

    chunkItemSec->ucType = 1;
    chunkItemSec->ucLength = 18;

    memcpy(pcBuffer, "google@example.com", chunkItemSec->ucLength);

    chunkItemSec->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(2);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 2);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(chunkItem, 1));

    /* pucChunkBuf injected with multiple ssrc number */
    memcpy(pucChunkBuf,
            (RtpDt_UChar[]){0x01, 0x12, 0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40, 0x65, 0x78, 0x61,
                    0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D, 0x01, 0x12, 0x67, 0x6f, 0x6f,
                    0x67, 0x6c, 0x65, 0x40, 0x65, 0x78, 0x61, 0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63,
                    0x6F, 0x6D},
            40);

    EXPECT_TRUE(testRtcpChunk->decodeRtcpChunk(pucChunkBuf, chunkLen, mRtcpConfigInfo));

    std::list<tRTCP_SDES_ITEM*> sdesItemList = testRtcpChunk->getSdesItemList();

    tRTCP_SDES_ITEM* sdesItem = sdesItemList.front();

    EXPECT_EQ(sdesItem->ucType, 1);
    EXPECT_EQ(sdesItem->ucLength, 18);

    delete[] pucChunkBuf;
    delete[] pcBuffer;
    delete chunkItem;
    delete mRtcpConfigInfo;
    delete chunkItemSec;
}

/** Failure Test scenario with Invalid SDES item type */
TEST_F(RtcpChunkTest, decodeSdesChunkWithInvalidType)
{
    RtpDt_UChar* pucChunkBuf = new RtpDt_UChar[60];
    RtcpConfigInfo* mRtcpConfigInfo = new RtcpConfigInfo();

    // First SDES Items

    tRTCP_SDES_ITEM* chunkItem = new tRTCP_SDES_ITEM();

    chunkItem->ucType = 2;
    chunkItem->ucLength = 18;

    RtpDt_UChar* pcBuffer = new RtpDt_UChar[chunkItem->ucLength];

    memcpy(pcBuffer, "sleepy@example.com", chunkItem->ucLength);

    chunkItem->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(1);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 1);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(chunkItem, 1));

    // Second SDES Items

    tRTCP_SDES_ITEM* chunkItemSec = new tRTCP_SDES_ITEM();

    chunkItemSec->ucType = 2;
    chunkItemSec->ucLength = 18;

    memcpy(pcBuffer, "google@example.com", chunkItemSec->ucLength);

    chunkItemSec->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(2);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 2);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(chunkItem, 1));

    /* pucChunkBuf injected with multiple ssrc number */
    memcpy(pucChunkBuf,
            (RtpDt_UChar[]){0x02, 0x12, 0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40, 0x65, 0x78, 0x61,
                    0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D, 0x02, 0x12, 0x67, 0x6f, 0x6f,
                    0x67, 0x6c, 0x65, 0x40, 0x65, 0x78, 0x61, 0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63,
                    0x6F, 0x6D},
            40);

    chunkLen = 1;

    EXPECT_NE(testRtcpChunk->decodeRtcpChunk(pucChunkBuf, chunkLen, mRtcpConfigInfo), RTP_SUCCESS);

    std::list<tRTCP_SDES_ITEM*> sdesItemList = testRtcpChunk->getSdesItemList();

    tRTCP_SDES_ITEM* sdesItem = sdesItemList.front();

    EXPECT_EQ(sdesItem->ucType, 2);
    EXPECT_EQ(sdesItem->ucLength, 18);

    delete[] pucChunkBuf;
    delete[] pcBuffer;
    delete chunkItem;
    delete mRtcpConfigInfo;
    delete chunkItemSec;
}

/** Failure Test scenario with Invalid SDES item type */
TEST_F(RtcpChunkTest, decodeSdesChunkWithInvalidLength)
{
    RtpDt_UChar* pucChunkBuf = new RtpDt_UChar[60];
    RtcpConfigInfo* mRtcpConfigInfo = new RtcpConfigInfo();

    // First SDES Items

    tRTCP_SDES_ITEM* chunkItem = new tRTCP_SDES_ITEM();

    chunkItem->ucType = 1;
    chunkItem->ucLength = 18;

    RtpDt_UChar* pcBuffer = new RtpDt_UChar[chunkItem->ucLength];

    memcpy(pcBuffer, "sleepy@example.com", chunkItem->ucLength);

    chunkItem->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(1);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 1);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(chunkItem, 1));

    /* pucChunkBuf injected with multiple ssrc number */
    memcpy(pucChunkBuf,
            (RtpDt_UChar[]){0x01, 0x12, 0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40, 0x65, 0x78, 0x61,
                    0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D},
            20);

    chunkLen = 1;

    EXPECT_EQ(testRtcpChunk->decodeRtcpChunk(pucChunkBuf, chunkLen, mRtcpConfigInfo), RTP_SUCCESS);

    std::list<tRTCP_SDES_ITEM*> sdesItemList = testRtcpChunk->getSdesItemList();

    tRTCP_SDES_ITEM* sdesItem = sdesItemList.front();

    EXPECT_EQ(sdesItem->ucType, 1);
    EXPECT_EQ(sdesItem->ucLength, 18);

    delete[] pucChunkBuf;
    delete[] pcBuffer;
    delete chunkItem;
    delete mRtcpConfigInfo;
}

// TODO: add testcase for decodeRtcpChunk to verify payload length, there is a
// fix needed in RtcpChunk.cpp to verify Sdes Payload Length.
// TODO: Take formRtcpChunk function later on.
}  // namespace android
