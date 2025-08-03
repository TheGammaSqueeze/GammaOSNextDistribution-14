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

#include <RtpHeader.h>
#include <gtest/gtest.h>

TEST(RtpHeaderTest, TestConstructor)
{
    RtpHeader rtpHeader;

    // Check default value
    EXPECT_EQ(rtpHeader.getVersion(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getPadding(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getExtension(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getCsrcCount(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getCsrcList().size(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getMarker(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getPayloadType(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getSequenceNumber(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getRtpTimestamp(), RTP_ZERO);
    EXPECT_EQ(rtpHeader.getRtpSsrc(), RTP_ZERO);
}

TEST(RtpHeaderTest, TestGetSets)
{
    RtpHeader rtpHeader;

    rtpHeader.setVersion(RTP_TWO);
    EXPECT_EQ(rtpHeader.getVersion(), RTP_TWO);

    rtpHeader.setPadding();
    EXPECT_EQ(rtpHeader.getPadding(), RTP_ONE);

    rtpHeader.setExtension(RTP_ONE);
    EXPECT_EQ(rtpHeader.getExtension(), RTP_ONE);

    rtpHeader.setCsrcCount(RTP_ZERO);
    EXPECT_EQ(rtpHeader.getCsrcCount(), RTP_ZERO);

    rtpHeader.setMarker();
    EXPECT_EQ(rtpHeader.getMarker(), RTP_ONE);

    rtpHeader.setPayloadType(104);
    EXPECT_EQ(rtpHeader.getPayloadType(), 104);

    rtpHeader.setSequenceNumber(11046);
    EXPECT_EQ(rtpHeader.getSequenceNumber(), 11046);

    rtpHeader.setRtpTimestamp(36338);
    EXPECT_EQ(rtpHeader.getRtpTimestamp(), 36338);

    rtpHeader.setRtpSsrc(1525054722);
    EXPECT_EQ(rtpHeader.getRtpSsrc(), 1525054722);
}

TEST(RtpHeaderTest, TestDecodeRtpHeaderWithoutCsrc)
{
    RtpHeader rtpHeader;
    RtpDt_UInt32 uiRtpBufPos = RTP_ZERO;

    /*
     * Real-Time Transport Protocol
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..0. .... = Padding: False
     * ...0 .... = Extension: False
     * .... 0000 = Contributing source identifiers count: 0
     * 1... .... = Marker: True
     * Payload type: DynamicRTP-Type-104 (104)
     * Sequence number: 1
     * Timestamp: 125760
     * Synchronization Source identifier: 0xce442f88 (3460575112)
     */
    uint8_t pRtpHeaderBuffer[] = {
            0x80, 0xe8, 0x00, 0x01, 0x00, 0x01, 0xeb, 0x40, 0xce, 0x44, 0x2f, 0x88};

    RtpBuffer rtpBuffer(sizeof(pRtpHeaderBuffer) / sizeof(pRtpHeaderBuffer[0]), pRtpHeaderBuffer);
    eRtp_Bool eResult = rtpHeader.decodeHeader(&rtpBuffer, uiRtpBufPos);

    EXPECT_EQ(eResult, eRTP_SUCCESS);

    EXPECT_EQ(rtpHeader.getVersion(), RTP_TWO);

    EXPECT_EQ(rtpHeader.getPadding(), RTP_ZERO);

    EXPECT_EQ(rtpHeader.getExtension(), RTP_ZERO);

    EXPECT_EQ(rtpHeader.getCsrcCount(), RTP_ZERO);

    EXPECT_EQ(rtpHeader.getCsrcList().size(), RTP_ZERO);

    EXPECT_EQ(rtpHeader.getMarker(), RTP_ONE);

    EXPECT_EQ(rtpHeader.getPayloadType(), 104);

    EXPECT_EQ(rtpHeader.getSequenceNumber(), 1);

    EXPECT_EQ(rtpHeader.getRtpTimestamp(), 125760);

    EXPECT_EQ(rtpHeader.getRtpSsrc(), 3460575112);
}

TEST(RtpHeaderTest, TestDecodeRtpHeaderWithCsrc)
{
    RtpHeader rtpHeader;
    RtpDt_UInt32 uiRtpBufPos = RTP_ZERO;

    /*
     * Real-Time Transport Protocol
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..1. .... = Padding: True
     * ...1 .... = Extension: True
     * .... 0010 = Contributing source identifiers count: 2
     * 0... .... = Marker: False
     * Payload type: DynamicRTP-Type-116 (116)
     * Sequence number: 7
     * Timestamp: 14760
     * Synchronization Source identifier: 0x0934f0ba (154464442)
     * Contributing Source identifier: 0x5ae67d02 (1525054722)
     * Contributing Source identifier: 0xce442f88 (3460575112)
     */
    uint8_t pRtpHeaderBuffer[] = {0xB2, 0x74, 0x00, 0x07, 0x00, 0x00, 0x39, 0xa8, 0x09, 0x34, 0xf0,
            0xba, 0x5a, 0xe6, 0x7d, 0x02, 0xce, 0x44, 0x2f, 0x88};

    RtpBuffer rtpBuffer(sizeof(pRtpHeaderBuffer) / sizeof(pRtpHeaderBuffer[0]), pRtpHeaderBuffer);
    eRtp_Bool eResult = rtpHeader.decodeHeader(&rtpBuffer, uiRtpBufPos);

    EXPECT_EQ(eResult, eRTP_SUCCESS);

    EXPECT_EQ(rtpHeader.getVersion(), RTP_TWO);

    EXPECT_EQ(rtpHeader.getPadding(), RTP_ONE);

    EXPECT_EQ(rtpHeader.getExtension(), RTP_ONE);

    EXPECT_EQ(rtpHeader.getCsrcCount(), RTP_TWO);

    EXPECT_EQ(rtpHeader.getMarker(), RTP_ZERO);

    EXPECT_EQ(rtpHeader.getPayloadType(), 116);

    EXPECT_EQ(rtpHeader.getSequenceNumber(), 7);

    EXPECT_EQ(rtpHeader.getRtpTimestamp(), 14760);

    EXPECT_EQ(rtpHeader.getRtpSsrc(), 154464442);

    EXPECT_EQ(rtpHeader.getCsrcList().size(), RTP_TWO);

    // csrc list
    std::list<RtpDt_UInt32> uiCsrcList = rtpHeader.getCsrcList();
    EXPECT_EQ(uiCsrcList.front(), 1525054722);
    uiCsrcList.pop_front();
    EXPECT_EQ(uiCsrcList.front(), 3460575112);
    uiCsrcList.clear();
}

TEST(RtpHeaderTest, TestDecodeInvalidRtpHeader)
{
    RtpHeader rtpHeader;
    RtpDt_UInt32 uiRtpBufPos = RTP_ZERO;

    // Rtp Header buffer with less than Fixed Header length.
    uint8_t pRtpHeaderBuffer[] = {0xB2, 0x74, 0x00, 0x07, 0x00, 0x00, 0x39, 0xa8, 0x09, 0x34};

    RtpBuffer rtpBuffer(sizeof(pRtpHeaderBuffer) / sizeof(pRtpHeaderBuffer[0]), pRtpHeaderBuffer);
    eRtp_Bool eResult = rtpHeader.decodeHeader(&rtpBuffer, uiRtpBufPos);

    EXPECT_EQ(eResult, eRTP_FALSE);
}

TEST(RtpHeaderTest, TestFormHeader)
{
    RtpHeader rtpHeader;

    /*
     * Real-Time Transport Protocol
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..0. .... = Padding: False
     * ...0 .... = Extension: False
     * .... 0000 = Contributing source identifiers count: 0
     * 0... .... = Marker: False
     * Payload type: DynamicRTP-Type-127 (127)
     * Sequence number: 45125
     * Timestamp: 79466
     * Synchronization Source identifier: 0xaecd8c02 (2932706306)
     */

    // set Rtp headers
    rtpHeader.setVersion(RTP_TWO);
    rtpHeader.setExtension(RTP_ZERO);
    rtpHeader.setCsrcCount(RTP_ZERO);
    rtpHeader.setPayloadType(127);
    rtpHeader.setSequenceNumber(45125);
    rtpHeader.setRtpTimestamp(79466);
    rtpHeader.setRtpSsrc(2932706306);

    // define expected Rtp packet output.
    uint8_t pExpectedBuffer[] = {
            0x80, 0x7f, 0xb0, 0x45, 0x00, 0x01, 0x36, 0x6a, 0xae, 0xcd, 0x8c, 0x02};

    // form Rtp Header
    uint8_t puiRtpBuffer[RTP_FIXED_HDR_LEN] = {0};
    RtpBuffer rtpPacket(RTP_FIXED_HDR_LEN, puiRtpBuffer);
    eRtp_Bool eResult = rtpHeader.formHeader(&rtpPacket);
    EXPECT_EQ(eResult, eRTP_TRUE);

    // Compare formed Rtp buffer with expected buffer
    EXPECT_EQ(memcmp(rtpPacket.getBuffer(), pExpectedBuffer, RTP_FIXED_HDR_LEN), 0);
}

// TODO : csrc list boundary test case to be added.
// TODO : Sequence number and timestamp boundary test case to be added.
