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

#include <RtcpXrPacket.h>
#include <gtest/gtest.h>

TEST(RtcpXrPacketTest, TestGetSetMethods)
{
    RtcpXrPacket objRtcpXrPacket;

    RtcpHeader objRtcpHeader;
    RtpDt_UChar pRtcpBuff[] = {0x81, 0xc8, 0x00, 0x06, 0x59, 0x09, 0x41, 0x02};
    objRtcpHeader.decodeRtcpHeader(pRtcpBuff, sizeof(pRtcpBuff));
    objRtcpXrPacket.setRtcpHdrInfo(objRtcpHeader);
    RtcpHeader* pRet = objRtcpXrPacket.getRtcpHdrInfo();
    ASSERT_TRUE(pRet != nullptr);
    EXPECT_EQ(*pRet, objRtcpHeader);

    uint8_t testReport[] = {0xe6, 0x5f, 0xa5, 0x31, 0x53, 0x91, 0x24, 0xc2, 0x00, 0x04, 0x01};

    RtpBuffer* pTestReportBuf = new RtpBuffer(sizeof(testReport), testReport);
    objRtcpXrPacket.setReportBlk(pTestReportBuf);
    RtpBuffer* pRetReportBuf = objRtcpXrPacket.getReportBlk();
    ASSERT_TRUE(pRetReportBuf != nullptr);
    EXPECT_EQ(
            memcmp(pTestReportBuf->getBuffer(), pRetReportBuf->getBuffer(), sizeof(testReport)), 0);
    EXPECT_EQ(pRetReportBuf->getLength(), sizeof(testReport));
}

TEST(RtcpXrPacketTest, TestDecodeXrPacket)
{
    RtcpXrPacket objRtcpXrPacket;
    uint8_t bufXrPacket[] = {0xe6, 0x5f, 0xa5, 0x31, 0x53, 0x91, 0x24, 0xc2, 0x00, 0x04, 0x01, 0x85,
            0x00, 0x00, 0x00, 0x41, 0x00, 0x00, 0xc8, 0x53, 0x81, 0xca, 0x00, 0x0a};
    eRTP_STATUS_CODE res =
            objRtcpXrPacket.decodeRtcpXrPacket(reinterpret_cast<RtpDt_UChar*>(bufXrPacket), 24, 0);

    EXPECT_EQ(res, RTP_SUCCESS);
}

TEST(RtcpXrPacketTest, TestFormXrPacket)
{
    RtpBuffer objRtcpPktBuf(64, nullptr);
    objRtcpPktBuf.setLength(0);
    RtcpXrPacket objRtcpXrPacket;

    RtcpHeader header;
    header.setVersion(2);
    header.setPadding(eRTP_FALSE);
    header.setReceptionReportCount(1);
    header.setPacketType(RTCP_XR);
    header.setSsrc(0x01020304);
    objRtcpXrPacket.setRtcpHdrInfo(header);

    uint8_t testReport[] = {0xe6, 0x5f, 0xa5, 0x31};
    RtpBuffer* pTestReportBuf = new RtpBuffer(sizeof(testReport), testReport);
    objRtcpXrPacket.setReportBlk(pTestReportBuf);

    eRTP_STATUS_CODE res = objRtcpXrPacket.formRtcpXrPacket(&objRtcpPktBuf);
    EXPECT_EQ(res, RTP_SUCCESS);

    RtpDt_UChar* buf = objRtcpPktBuf.getBuffer();
    ASSERT_TRUE(buf != nullptr);
    RtpDt_UChar expectedBuf[] = {
            0X81, 0XCF, 0X00, 0X02, 0X01, 0X02, 0X03, 0X04, 0XE6, 0X5F, 0XA5, 0X31};

    EXPECT_EQ(memcmp(buf, expectedBuf, sizeof(expectedBuf)), 0);
}