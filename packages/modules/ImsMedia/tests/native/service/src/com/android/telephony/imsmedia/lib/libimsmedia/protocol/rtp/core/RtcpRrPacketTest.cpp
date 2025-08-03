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

#include <RtcpRrPacket.h>
#include <gtest/gtest.h>

class RtcpRrPacketTest : public ::testing::Test
{
public:
    /*
     * Source 1
     *     Identifier: 0x01020304 (0)
     *     SSRC contents
     *         Fraction lost: 0x10 / 256
     *         Cumulative number of packets lost: 0x000020
     *     Extended highest sequence number received: 0
     *         Sequence number cycles count: 0
     *         Highest sequence number received: 0x4525
     *     Interarrival jitter: 0
     *     Last SR timestamp: 2262099689 (0x86d4e6e9)
     *     Delay since last SR timestamp: 1 (0 milliseconds)
     */
    const static uint8_t bBufLen = 24;
    uint8_t bufRrWithOneReport[bBufLen] = {0x01, 0x02, 0x03, 0x04, 0x10, 0x00, 0x00, 0x20, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x86, 0xd4, 0xe6, 0xe9, 0x00, 0x00, 0x00,
            0x01};

protected:
    virtual void SetUp() override {}

    virtual void TearDown() override {}
};

TEST_F(RtcpRrPacketTest, TestGetSetMethods)
{
    RtcpRrPacket objRtcpRrPacket;

    RtcpHeader objRtcpHeader;
    RtpDt_UChar pRtcpBuff[] = {0x81, 0xc8, 0x00, 0x06, 0x59, 0x09, 0x41, 0x02};
    objRtcpHeader.decodeRtcpHeader(pRtcpBuff, sizeof(pRtcpBuff));
    objRtcpRrPacket.setRtcpHdrInfo(objRtcpHeader);
    RtcpHeader* pRet = objRtcpRrPacket.getRtcpHdrInfo();
    ASSERT_TRUE(pRet != nullptr);
    EXPECT_EQ(*pRet, objRtcpHeader);

    uint8_t hdrExtBuf[] = {0xe6, 0x5f, 0xa5, 0x31, 0x53, 0x91, 0x24, 0xc2, 0x00, 0x04, 0x01};

    RtpBuffer rtpbuffer(11, hdrExtBuf);
    objRtcpRrPacket.setExtHdrInfo(&rtpbuffer);
    RtpBuffer* pExtBuf = objRtcpRrPacket.getExtHdrInfo();
    ASSERT_TRUE(pExtBuf != nullptr);
    EXPECT_EQ(memcmp(rtpbuffer.getBuffer(), pExtBuf->getBuffer(), 11), 0);
    EXPECT_EQ(pExtBuf->getLength(), 11);

    RtpBuffer* pRtpbufferEmpty = new RtpBuffer(0, nullptr);
    objRtcpRrPacket.setExtHdrInfo(pRtpbufferEmpty);
}

TEST_F(RtcpRrPacketTest, TestDecodeRrPacket)
{
    RtcpRrPacket objRtcpRrPacket;
    RtpDt_UInt16 len = bBufLen;
    eRTP_STATUS_CODE res = objRtcpRrPacket.decodeRrPacket(bufRrWithOneReport, len, 0);
    EXPECT_EQ(res, RTP_SUCCESS);

    std::list<RtcpReportBlock*> reports = objRtcpRrPacket.getReportBlockList();
    ASSERT_TRUE(reports.size() != 0);

    RtcpReportBlock* report = reports.front();
    ASSERT_TRUE(report != nullptr);

    EXPECT_EQ(report->getSsrc(), 0x01020304);
    EXPECT_EQ((int)report->getFracLost(), 0x10);
    EXPECT_EQ((int)report->getCumNumPktLost(), 0x000020);
    EXPECT_EQ(report->getExtHighSeqRcv(), 0);
    EXPECT_EQ(report->getJitter(), 0);
    EXPECT_EQ(report->getLastSR(), 0x86d4e6e9);
    EXPECT_EQ(report->getDelayLastSR(), 0x00000001);
}

TEST_F(RtcpRrPacketTest, TestFormRrPacket)
{
    RtcpRrPacket objRtcpRrPacket;
    RtpBuffer objRtcpPktBuf(bBufLen, nullptr);
    objRtcpPktBuf.setLength(0);
    RtcpReportBlock* pobjRtcpReportBlock = new RtcpReportBlock();
    pobjRtcpReportBlock->setSsrc(0x01020304);
    pobjRtcpReportBlock->setFracLost(0x10);
    pobjRtcpReportBlock->setCumNumPktLost(0x000020);
    pobjRtcpReportBlock->setExtHighSeqRcv(0);
    pobjRtcpReportBlock->setJitter(0);
    pobjRtcpReportBlock->setLastSR(0x86d4e6e9);
    pobjRtcpReportBlock->setDelayLastSR(0x00000001);
    objRtcpRrPacket.getReportBlockList().push_back(pobjRtcpReportBlock);

    eRTP_STATUS_CODE res = objRtcpRrPacket.formRrPacket(&objRtcpPktBuf, eRTP_FALSE);
    EXPECT_EQ(res, RTP_SUCCESS);
    EXPECT_EQ(memcmp(objRtcpPktBuf.getBuffer(), RtcpRrPacketTest::bufRrWithOneReport, bBufLen), 0);
}

// TODO: Add test case for RTCP extension headers.