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

#include <RtcpSdesPacket.h>
#include <RtcpHeader.h>
#include <gtest/gtest.h>

namespace android
{

class RtcpSdesPacketTest : public ::testing::Test
{
public:
    RtcpSdesPacket* testRtcpSdesPacket;
    RtcpHeader testRtcpHeader;

protected:
    virtual void SetUp() override
    {
        testRtcpSdesPacket = new RtcpSdesPacket();
        ASSERT_TRUE(testRtcpSdesPacket != nullptr);
    }

    virtual void TearDown() override
    {
        if (testRtcpSdesPacket)
        {
            delete testRtcpSdesPacket;
            testRtcpSdesPacket = nullptr;
        }
    }
};

/** Success Test scenario with Single SDES Item */
TEST_F(RtcpSdesPacketTest, decodeSdesPacketSingleSdesItem)
{
    RtpDt_UChar* pucSdesBuf = new RtpDt_UChar[60];

    tRTCP_SDES_ITEM* sdesItem = new tRTCP_SDES_ITEM();

    sdesItem->ucType = 1;
    sdesItem->ucLength = 18;

    RtpDt_UChar* pcBuffer = new RtpDt_UChar[sdesItem->ucLength];

    memcpy(pcBuffer, "sleepy@example.com", sdesItem->ucLength);

    sdesItem->pValue = pcBuffer;

    RtcpConfigInfo* mRtcpConfigInfo = new RtcpConfigInfo();
    mRtcpConfigInfo->setSdesItemCount(1);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 1);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(sdesItem, 1));

    /* pucSdesBuf injected with single ssrc number */

    /*
    * Real-time Transport Control Protocol (Sdes Packet)
    * [Common Header]
    * Sdes Item Type: 1 (0x01)
    *  Sdes Item Length: 18 (0x12)
    *      Sdes Item Payload: "sleepy@example.com" (0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40,
                               0x65, 0x78, 0x61, 0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D)
    *
    */
    memcpy(pucSdesBuf,
            (RtpDt_UChar[]){0x01, 0x12, 0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40, 0x65, 0x78, 0x61,
                    0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D},
            20);

    testRtcpHeader.setReceptionReportCount(1);

    testRtcpSdesPacket->setRtcpHdrInfo(testRtcpHeader);

    EXPECT_TRUE(testRtcpSdesPacket->decodeSdesPacket(pucSdesBuf, 20, mRtcpConfigInfo));

    std::list<RtcpChunk*> chunkItemList = testRtcpSdesPacket->getSdesChunkList();

    std::list<tRTCP_SDES_ITEM*> sdesItemList = chunkItemList.front()->getSdesItemList();

    tRTCP_SDES_ITEM* tmpSdesItem = sdesItemList.front();

    EXPECT_EQ(tmpSdesItem->ucType, 1);
    EXPECT_EQ(tmpSdesItem->ucLength, 18);

    delete[] pucSdesBuf;
    delete[] pcBuffer;
    delete sdesItem;
    delete mRtcpConfigInfo;
}

/** Success Test scenario with Multiple SDES items */
TEST_F(RtcpSdesPacketTest, decodeSdesPacketMultiSdesItem)
{
    RtpDt_UChar* pucSdesBuf = new RtpDt_UChar[60];
    RtcpConfigInfo* mRtcpConfigInfo = new RtcpConfigInfo();

    // First SDES Items

    tRTCP_SDES_ITEM* sdesItem = new tRTCP_SDES_ITEM();

    sdesItem->ucType = 1;
    sdesItem->ucLength = 18;

    RtpDt_UChar* pcBuffer = new RtpDt_UChar[sdesItem->ucLength];

    memcpy(pcBuffer, "sleepy@example.com", sdesItem->ucLength);

    sdesItem->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(1);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 1);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(sdesItem, 1));
    // Second SDES Items

    tRTCP_SDES_ITEM* sdesItemSec = new tRTCP_SDES_ITEM();

    sdesItemSec->ucType = 1;
    sdesItemSec->ucLength = 18;

    memcpy(pcBuffer, "google@example.com", sdesItemSec->ucLength);

    sdesItemSec->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(2);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 2);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(sdesItemSec, 1));

    /* pucSdesBuf injected with single ssrc number */
    memcpy(pucSdesBuf,
            (RtpDt_UChar[]){0x01, 0x12, 0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40, 0x65, 0x78, 0x61,
                    0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D, 0x01, 0x12, 0x67, 0x6f, 0x6f,
                    0x67, 0x6c, 0x65, 0x40, 0x65, 0x78, 0x61, 0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63,
                    0x6F, 0x6D},
            40);
    testRtcpHeader.setReceptionReportCount(2);
    testRtcpSdesPacket->setRtcpHdrInfo(testRtcpHeader);

    EXPECT_TRUE(testRtcpSdesPacket->decodeSdesPacket(pucSdesBuf, 40, mRtcpConfigInfo));

    std::list<RtcpChunk*> chunkItemList = testRtcpSdesPacket->getSdesChunkList();

    std::list<tRTCP_SDES_ITEM*> sdesItemList = chunkItemList.front()->getSdesItemList();

    tRTCP_SDES_ITEM* tmpSdesItem = sdesItemList.front();

    EXPECT_EQ(tmpSdesItem->ucType, 1);
    EXPECT_EQ(tmpSdesItem->ucLength, 18);

    sdesItemList = chunkItemList.front()->getSdesItemList();

    tmpSdesItem = sdesItemList.front();

    EXPECT_EQ(tmpSdesItem->ucType, 1);
    EXPECT_EQ(tmpSdesItem->ucLength, 18);

    delete[] pucSdesBuf;
    delete[] pcBuffer;
    delete sdesItem;
    delete mRtcpConfigInfo;
}

/** Failure Test scenario with Invalid SDES items type */
TEST_F(RtcpSdesPacketTest, decodeSdesPacketDiffSdesType)
{
    RtpDt_UChar* pucSdesBuf = new RtpDt_UChar[60];
    RtcpConfigInfo* mRtcpConfigInfo = new RtcpConfigInfo();

    // First SDES Items

    tRTCP_SDES_ITEM* sdesItem = new tRTCP_SDES_ITEM();

    sdesItem->ucType = 2;
    sdesItem->ucLength = 18;

    RtpDt_UChar* pcBuffer = new RtpDt_UChar[sdesItem->ucLength];

    memcpy(pcBuffer, "sleepy@example.com", sdesItem->ucLength);

    sdesItem->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(1);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 1);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(sdesItem, 1));

    // Second SDES Items

    tRTCP_SDES_ITEM* sdesItemSec = new tRTCP_SDES_ITEM();

    sdesItemSec->ucType = 2;
    sdesItemSec->ucLength = 18;

    memcpy(pcBuffer, "google@example.com", sdesItemSec->ucLength);

    sdesItemSec->pValue = pcBuffer;

    mRtcpConfigInfo->setSdesItemCount(2);
    EXPECT_EQ(mRtcpConfigInfo->getSdesItemCount(), 2);
    EXPECT_TRUE(mRtcpConfigInfo->addRtcpSdesItem(sdesItemSec, 1));

    /* pucSdesBuf injected with single ssrc number */
    memcpy(pucSdesBuf,
            (RtpDt_UChar[]){0x02, 0x12, 0x73, 0x6C, 0x65, 0x65, 0x70, 0x79, 0x40, 0x65, 0x78, 0x61,
                    0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D, 0x02, 0x12, 0x67, 0x6f, 0x6f,
                    0x67, 0x6c, 0x65, 0x40, 0x65, 0x78, 0x61, 0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63,
                    0x6F, 0x6D},
            40);

    testRtcpHeader.setReceptionReportCount(2);
    testRtcpSdesPacket->setRtcpHdrInfo(testRtcpHeader);

    EXPECT_NE(testRtcpSdesPacket->decodeSdesPacket(pucSdesBuf, 40, mRtcpConfigInfo), RTP_SUCCESS);

    std::list<RtcpChunk*> chunkItemList = testRtcpSdesPacket->getSdesChunkList();

    std::list<tRTCP_SDES_ITEM*> sdesItemList = chunkItemList.front()->getSdesItemList();

    tRTCP_SDES_ITEM* tmpSdesItem = sdesItemList.front();

    EXPECT_EQ(tmpSdesItem->ucType, 2);
    EXPECT_EQ(tmpSdesItem->ucLength, 18);

    sdesItemList = chunkItemList.front()->getSdesItemList();

    tmpSdesItem = sdesItemList.front();

    EXPECT_EQ(tmpSdesItem->ucType, 2);
    EXPECT_EQ(tmpSdesItem->ucLength, 18);

    delete[] pucSdesBuf;
    delete[] pcBuffer;
    delete sdesItem;
    delete mRtcpConfigInfo;
}

// TODO: Take formRtcpSdesPacket function later on.
}  // namespace android
