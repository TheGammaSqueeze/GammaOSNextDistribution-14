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

#include <RtcpSrPacket.h>
#include <gtest/gtest.h>

class RtcpSrPacketTest : public ::testing::Test
{
public:
protected:
    virtual void SetUp() override {}

    virtual void TearDown() override {}
};

TEST_F(RtcpSrPacketTest, TestGetSetMethods)
{
    RtcpSrPacket objRtcpSrPacket;

    RtcpHeader objRtcpHeader;
    RtpDt_UChar pRtcpBuff[] = {0x81, 0xc8, 0x00, 0x06, 0x59, 0x09, 0x41, 0x02};
    objRtcpHeader.decodeRtcpHeader(pRtcpBuff, sizeof(pRtcpBuff));
    objRtcpSrPacket.setRtcpHdrInfo(objRtcpHeader);
    RtcpHeader* pRet = objRtcpSrPacket.getRtcpHdrInfo();
    ASSERT_TRUE(pRet != nullptr);
    EXPECT_EQ(*pRet, objRtcpHeader);

    objRtcpSrPacket.setRtpTimestamp(0xAAAAAAAA);
    EXPECT_EQ(objRtcpSrPacket.getRtpTimestamp(), 0xAAAAAAAA);

    objRtcpSrPacket.setSendPktCount(0xAAAAAAAA);
    EXPECT_EQ(objRtcpSrPacket.getSendPktCount(), 0xAAAAAAAA);

    objRtcpSrPacket.setSendOctetCount(0xAAAAAAAA);
    EXPECT_EQ(objRtcpSrPacket.getSendOctetCount(), 0xAAAAAAAA);
}

TEST_F(RtcpSrPacketTest, TestDecodeSrPacketWithZeroReports)
{
    /*
     * Real-time Transport Control Protocol (Sender Report)
     * [Common Header]
     * Timestamp, MSW: 3865027889 (0xe65fa531)
     * Timestamp, LSW: 1402021058 (0x539124c2)
     * [MSW and LSW as NTP timestamp: Jun 24, 2022 02:51:29.326433465 UTC]
     * RTP timestamp: 262533
     * Sender's packet count: 65
     * Sender's octet count: 51283
     */
    RtcpSrPacket objRtcpSrPacket;
    uint8_t bufSrSdesPacket[] = {0xe6, 0x5f, 0xa5, 0x31, 0x53, 0x91, 0x24, 0xc2, 0x00, 0x04, 0x01,
            0x85, 0x00, 0x00, 0x00, 0x41, 0x00, 0x00, 0xc8, 0x53, 0x81, 0xca, 0x00, 0x0a};
    eRTP_STATUS_CODE res =
            objRtcpSrPacket.decodeSrPacket(reinterpret_cast<RtpDt_UChar*>(bufSrSdesPacket), 24, 0);
    EXPECT_EQ(res, RTP_SUCCESS);

    tRTP_NTP_TIME* ntpTime = objRtcpSrPacket.getNtpTime();
    ASSERT_TRUE(ntpTime != nullptr);

    EXPECT_EQ(ntpTime->m_uiNtpHigh32Bits, 0xe65fa531);
    EXPECT_EQ(ntpTime->m_uiNtpLow32Bits, 0x539124c2);
    EXPECT_EQ(objRtcpSrPacket.getRtpTimestamp(), 0x00040185);
    EXPECT_EQ(objRtcpSrPacket.getSendPktCount(), 65);
    EXPECT_EQ(objRtcpSrPacket.getSendOctetCount(), 0x0000c853);
}

TEST_F(RtcpSrPacketTest, TestDecodeSrPacketWithOneReport)
{
    /*
     * Real-time Transport Control Protocol (Sender Report)
     * [Common Header]
     *       Timestamp, MSW: 3314714324 (0xc59286d4)
     *       Timestamp, LSW: 3874060501 (0xe6e978d5)
     *       [MSW and LSW as NTP timestamp: Jan 14, 2005 17:58:44.902000000 UTC]
     *       RTP timestamp: 320
     *       Sender's packet count: 2
     *       Sender's octet count: 320
     *       Source 1
     *           Identifier: 0xd2bd4e3e (3535621694)
     *           SSRC contents
     *               Fraction lost: 0 / 256
     *               Cumulative number of packets lost: 0
     *           Extended highest sequence number received: 131074
     *               Sequence number cycles count: 2
     *               Highest sequence number received: 2
     *           Interarrival jitter: 0
     *           Last SR timestamp: 2262099689 (0x86d4e6e9)
     *           Delay since last SR timestamp: 1 (0 milliseconds)
     */
    uint8_t bufSrSdesPacket[] = {0xc5, 0x92, 0x86, 0xd4, 0xe6, 0xe9, 0x78, 0xd5, 0x00, 0x00, 0x01,
            0x40, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x01, 0x40, 0xd2, 0xbd, 0x4e, 0x3e, 0x10,
            0x00, 0x00, 0x20, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x86, 0xd4, 0xe6,
            0xe9, 0x00, 0x00, 0x00, 0x01, 0x81, 0xc9, 0x00, 0x07, 0xd2, 0xbd, 0x4e, 0x3e, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x86, 0xd4, 0xe6, 0xe9, 0x00, 0x00, 0x00, 0x01};

    RtcpSrPacket objRtcpSrPacket;
    eRTP_STATUS_CODE res =
            objRtcpSrPacket.decodeSrPacket(reinterpret_cast<RtpDt_UChar*>(bufSrSdesPacket), 76, 0);
    EXPECT_EQ(res, RTP_SUCCESS);

    tRTP_NTP_TIME* ntpTime = objRtcpSrPacket.getNtpTime();
    ASSERT_TRUE(ntpTime != nullptr);

    EXPECT_EQ(ntpTime->m_uiNtpHigh32Bits, 3314714324);
    EXPECT_EQ(ntpTime->m_uiNtpLow32Bits, 3874060501);
    EXPECT_EQ(objRtcpSrPacket.getRtpTimestamp(), 320);
    EXPECT_EQ(objRtcpSrPacket.getSendPktCount(), 2);
    EXPECT_EQ(objRtcpSrPacket.getSendOctetCount(), 320);

    RtcpRrPacket* pRRInfo = objRtcpSrPacket.getRrPktInfo();
    ASSERT_TRUE(pRRInfo != nullptr);

    std::list<RtcpReportBlock*> reports = pRRInfo->getReportBlockList();
    ASSERT_TRUE(reports.size() != 0);

    RtcpReportBlock* report = reports.front();
    ASSERT_TRUE(report != nullptr);

    EXPECT_EQ(report->getSsrc(), 0xd2bd4e3e);
    EXPECT_EQ((int)report->getFracLost(), 0x10);
    EXPECT_EQ((int)report->getCumNumPktLost(), 0x000020);
    EXPECT_EQ(report->getExtHighSeqRcv(), 131074);
    EXPECT_EQ(report->getJitter(), 0);
    EXPECT_EQ(report->getLastSR(), 2262099689);
    EXPECT_EQ(report->getDelayLastSR(), 1);
}

TEST_F(RtcpSrPacketTest, TestDecodeSrPacketWithShorterInputBuffer)
{
    RtcpSrPacket objRtcpSrPacket;
    uint8_t bufSrSdesPacket[] = {0xe6, 0x5f, 0xa5, 0x31, 0x53, 0x91, 0x24, 0xc2, 0x00, 0x04, 0x01,
            0x85, 0x00, 0x00, 0x00, 0x41};
    eRTP_STATUS_CODE res =
            objRtcpSrPacket.decodeSrPacket(reinterpret_cast<RtpDt_UChar*>(bufSrSdesPacket), 16, 0);
    EXPECT_EQ(res, RTP_FAILURE);
}

// TODO: Write test cases for formSRPacket