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

#include <RtcpFbPacket.h>
#include <gtest/gtest.h>

TEST(RtcpFbPacketTest, TestGetSetMethods)
{
    RtcpFbPacket objRtcpFbPacket;

    RtcpHeader objRtcpHeader;
    RtpDt_UChar pRtcpBuff[] = {0x81, 0xc8, 0x00, 0x06, 0x59, 0x09, 0x41, 0x02};
    objRtcpHeader.decodeRtcpHeader(pRtcpBuff, sizeof(pRtcpBuff));
    objRtcpFbPacket.setRtcpHdrInfo(objRtcpHeader);
    RtcpHeader* pRet = objRtcpFbPacket.getRtcpHdrInfo();
    ASSERT_TRUE(pRet != nullptr);
    EXPECT_EQ(*pRet, objRtcpHeader);

    objRtcpFbPacket.setSsrc(0xAAAAAAAA);
    EXPECT_EQ(objRtcpFbPacket.getSsrc(), 0xAAAAAAAA);

    objRtcpFbPacket.setMediaSsrc(0xAAAAAAAA);
    EXPECT_EQ(objRtcpFbPacket.getMediaSsrc(), 0xAAAAAAAA);

    uint8_t testFci[] = {0xe6, 0x5f, 0xa5, 0x31};
    objRtcpFbPacket.setFCI(new RtpBuffer(sizeof(testFci), testFci));
    RtpBuffer* pRtpBuf = objRtcpFbPacket.getFCI();

    ASSERT_TRUE(pRtpBuf != nullptr);
    EXPECT_EQ(memcmp(pRtpBuf->getBuffer(), testFci, sizeof(testFci)), 0);

    objRtcpFbPacket.setPayloadType(RTCP_RTPFB);
    EXPECT_EQ(objRtcpFbPacket.getPayloadType(), RTCP_RTPFB);
}

TEST(RtcpFbPacketTest, TestDecodeFbPacket)
{
    /*
     * Media SSRC : 0xb1c8cb03 (2982726402)
     * 8bytes of test data: TMMBR***
     */
    uint8_t bufPacket[] = {0xb1, 0xc8, 0xcb, 0x03, 0x54, 0x4d, 0x4d, 0x42, 0x52, 0x2a, 0x2a, 0x2a};

    RtcpFbPacket objRtcpFbPacket;
    eRTP_STATUS_CODE res = objRtcpFbPacket.decodeRtcpFbPacket(
            reinterpret_cast<RtpDt_UChar*>(bufPacket), sizeof(bufPacket));
    EXPECT_EQ(res, RTP_SUCCESS);
    EXPECT_EQ(objRtcpFbPacket.getMediaSsrc(), 0xb1c8cb03);
    RtpBuffer* pRtpBuf = objRtcpFbPacket.getFCI();
    ASSERT_TRUE(pRtpBuf != nullptr);
    EXPECT_EQ(memcmp(pRtpBuf->getBuffer(), (bufPacket + RTP_WORD_SIZE),
                      (sizeof(bufPacket) - RTP_WORD_SIZE)),
            0);
}

TEST(RtcpFbPacketTest, TestEncodeRtcpRTPFB)
{
    RtpBuffer objRtcpPktBuf(64, nullptr);
    objRtcpPktBuf.setLength(0);
    RtcpFbPacket objRtcpFbPacket;

    RtcpHeader header;
    header.setVersion(2);
    header.setPadding(eRTP_FALSE);
    header.setReceptionReportCount(1);
    header.setPacketType(RTCP_RTPFB);
    header.setSsrc(0x01020304);
    objRtcpFbPacket.setRtcpHdrInfo(header);
    objRtcpFbPacket.setMediaSsrc(0xAAAAAAAA);
    objRtcpFbPacket.setPayloadType(RTCP_RTPFB);

    uint8_t testFci[] = {0xe6, 0x5f, 0xa5, 0x31};
    objRtcpFbPacket.setFCI(new RtpBuffer(sizeof(testFci), testFci));

    eRTP_STATUS_CODE res = objRtcpFbPacket.formRtcpFbPacket(&objRtcpPktBuf);
    EXPECT_EQ(res, RTP_SUCCESS);

    RtpDt_UChar* buf = objRtcpPktBuf.getBuffer();
    RtpDt_UChar expectedBuf[] = {0x81, 0xcd, 0x00, 0x03, 0x01, 0x02, 0x03, 0x04, 0xaa, 0xaa, 0xaa,
            0xaa, 0xe6, 0x5f, 0xa5, 0x31};

    EXPECT_EQ(memcmp(buf, expectedBuf, sizeof(expectedBuf)), 0);
}