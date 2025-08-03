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

#include <RtpPacket.h>
#include <gtest/gtest.h>

TEST(RtpPacketTest, TestConstructor)
{
    RtpPacket rtpPacket;

    // Check default value
    EXPECT_TRUE(rtpPacket.getExtHeader() == nullptr);

    EXPECT_TRUE(rtpPacket.getRtpPayload() == nullptr);
}

TEST(RtpPacketTest, TestGetSets)
{
    RtpPacket rtpPacket;

    uint8_t pRtpPayLoad[] = {0x67, 0x42, 0xc0, 0x0c, 0xda, 0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10,
            0x3c, 0x58, 0xba, 0x80};

    RtpBuffer* rtpPayloadBuffer = new RtpBuffer(sizeof(pRtpPayLoad), pRtpPayLoad);
    rtpPacket.setRtpPayload(rtpPayloadBuffer);
    RtpBuffer* pobjRtpBuffer = rtpPacket.getRtpPayload();
    ASSERT_TRUE(pobjRtpBuffer != nullptr);

    EXPECT_EQ(memcmp(pRtpPayLoad, pobjRtpBuffer->getBuffer(), sizeof(pRtpPayLoad)), 0);
    EXPECT_EQ(pobjRtpBuffer->getLength(), sizeof(pRtpPayLoad));

    uint8_t pRtpExtHdr[] = {0x41, 0x00, 0x00};

    RtpBuffer* rtpExtBuffer = new RtpBuffer(sizeof(pRtpExtHdr), pRtpExtHdr);
    rtpPacket.setExtHeader(rtpExtBuffer);
    RtpBuffer* pobjRtpExtHdr = rtpPacket.getExtHeader();

    ASSERT_TRUE(pobjRtpBuffer != nullptr);
    EXPECT_EQ(memcmp(pRtpExtHdr, pobjRtpExtHdr->getBuffer(), sizeof(pRtpExtHdr)), 0);
    EXPECT_EQ(pobjRtpExtHdr->getLength(), sizeof(pRtpExtHdr));
}

TEST(RtpPacketTest, TestDecodePacket)
{
    RtpPacket rtpPacket;

    /*
     * Real-Time Transport Protocol
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..0. .... = Padding: False
     * ...1 .... = Extension: True
     * .... 0000 = Contributing source identifiers count: 0
     * 1... .... = Marker: True
     * Payload type: DynamicRTP-Type-99 (99)
     * Sequence number: 42371
     * Timestamp: 57800
     * Synchronization Source identifier: 0x927dcd02 (2457718018)
     * Defined by profile: Unknown (0xbede)
     * Extension length: 1
     * Header extensions
     *     RFC 5285 Header Extension (One-Byte Header)
     *         Identifier: 4
     *         Length: 2
     *         Extension Data: (0x7842)
     */

    uint8_t pobjRtpPktBuf[] = {0x90, 0xe3, 0xa5, 0x83, 0x00, 0x00, 0xe1, 0xc8, 0x92, 0x7d, 0xcd,
            0x02, 0xbe, 0xde, 0x00, 0x01, 0x41, 0x78, 0x42, 0x00, 0x67, 0x42, 0xc0, 0x0c, 0xda,
            0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10, 0x3c, 0x58, 0xba, 0x80};

    RtpBuffer rtpBuffer(sizeof(pobjRtpPktBuf), pobjRtpPktBuf);
    eRtp_Bool eResult = rtpPacket.decodePacket(&rtpBuffer);

    EXPECT_EQ(eResult, eRTP_SUCCESS);

    // check Header extension
    RtpBuffer* pobjRtpExtHdr = rtpPacket.getExtHeader();
    ASSERT_TRUE(pobjRtpExtHdr != nullptr);

    uint8_t pRtpExtHdr[] = {0xbe, 0xde, 0x00, 0x01, 0x41, 0x78, 0x42, 0x00};

    EXPECT_EQ(memcmp(pRtpExtHdr, pobjRtpExtHdr->getBuffer(), sizeof(pRtpExtHdr)), 0);
    EXPECT_EQ(pobjRtpExtHdr->getLength(), sizeof(pRtpExtHdr));

    // check Payload
    RtpBuffer* pobjRtpBuffer = rtpPacket.getRtpPayload();
    uint8_t pRtpPayLoad[] = {0x67, 0x42, 0xc0, 0x0c, 0xda, 0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10,
            0x3c, 0x58, 0xba, 0x80};

    ASSERT_TRUE(pobjRtpBuffer != nullptr);
    EXPECT_EQ(memcmp(pRtpPayLoad, pobjRtpBuffer->getBuffer(), sizeof(pRtpPayLoad)), 0);
    EXPECT_EQ(pobjRtpBuffer->getLength(), sizeof(pRtpPayLoad));
}

TEST(RtpPacketTest, TestDecodePacketWithWrongRtpVersion)
{
    RtpPacket rtpPacket;

    /*
     * Real-Time Transport Protocol
     * 01.. .... = Version: RFC 1889 Version (1)
     * ..0. .... = Padding: False
     * ...1 .... = Extension: True
     * .... 0000 = Contributing source identifiers count: 0
     * 1... .... = Marker: True
     * Payload type: DynamicRTP-Type-99 (99)
     * Sequence number: 42371
     * Timestamp: 57800
     * Synchronization Source identifier: 0x927dcd02 (2457718018)
     * Defined by profile: Unknown (0xbede)
     * Extension length: 1
     * Header extensions
     *     RFC 5285 Header Extension (One-Byte Header)
     *         Identifier: 4
     *         Length: 2
     *         Extension Data: (0x7842)
     */

    uint8_t pobjRtpPktBuf[] = {0x50, 0xe3, 0xa5, 0x83, 0x00, 0x00, 0xe1, 0xc8, 0x92, 0x7d, 0xcd,
            0x02, 0xbe, 0xde, 0x00, 0x01, 0x41, 0x78, 0x42, 0x00, 0x67, 0x42, 0xc0, 0x0c, 0xda,
            0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10, 0x3c, 0x58, 0xba, 0x80};

    RtpBuffer rtpBuffer(sizeof(pobjRtpPktBuf), pobjRtpPktBuf);
    eRtp_Bool eResult = rtpPacket.decodePacket(&rtpBuffer);

    // check for failure as Rtp version is wrong.
    EXPECT_EQ(eResult, eRTP_FAILURE);
}

TEST(RtpPacketTest, TestDecodePacketWithWrongExtLength)
{
    RtpPacket rtpPacket;

    /*
     * Real-Time Transport Protocol
     * 01.. .... = Version: RFC 1889 Version (1)
     * ..0. .... = Padding: False
     * ...1 .... = Extension: True
     * .... 0000 = Contributing source identifiers count: 0
     * 1... .... = Marker: True
     * Payload type: DynamicRTP-Type-99 (99)
     * Sequence number: 42371
     * Timestamp: 57800
     * Synchronization Source identifier: 0x927dcd02 (2457718018)
     * Defined by profile: Unknown (0xbede)
     * Extension length: 2
     * Header extensions
     *     RFC 5285 Header Extension (One-Byte Header)
     *         Identifier: 4
     *         Length: 2
     *         Extension Data: (0x7842)
     */

    uint8_t pobjRtpPktBuf[] = {0x50, 0xe3, 0xa5, 0x83, 0x00, 0x00, 0xe1, 0xc8, 0x92, 0x7d, 0xcd,
            0x02, 0xbe, 0xde, 0x00, 0x02, 0x41, 0x78, 0x42, 0x00};

    RtpBuffer rtpBuffer(sizeof(pobjRtpPktBuf), pobjRtpPktBuf);
    eRtp_Bool eResult = rtpPacket.decodePacket(&rtpBuffer);

    // check for failure as Extension length is wrong.
    EXPECT_EQ(eResult, eRTP_FAILURE);
}

TEST(RtpPacketTest, TestDecodePacketWithPadding)
{
    RtpPacket rtpPacket;

    /*
     * Real-Time Transport Protocol
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..1. .... = Padding: True
     * ...1 .... = Extension: True
     * .... 0000 = Contributing source identifiers count: 0
     * 1... .... = Marker: True
     * Payload type: DynamicRTP-Type-99 (99)
     * Sequence number: 42371
     * Timestamp: 57800
     * Synchronization Source identifier: 0x927dcd02 (2457718018)
     * Padding: 0x01000000 (16777216)
     * Defined by profile: Unknown (0xbede)
     * Extension length: 1
     * Header extensions
     *     RFC 5285 Header Extension (One-Byte Header)
     *         Identifier: 4
     *         Length: 2
     *         Extension Data: (0x7842)
     */

    uint8_t pobjRtpPktBuf[] = {0xB0, 0xe3, 0xa5, 0x83, 0x00, 0x00, 0xe1, 0xc8, 0x92, 0x7d, 0xcd,
            0x02, 0xbe, 0xde, 0x00, 0x01, 0x41, 0x78, 0x42, 0x00, 0x67, 0x42, 0xc0, 0x0c, 0xda,
            0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10, 0x3c, 0x58, 0xba, 0x80, 0x00, 0x02};

    RtpBuffer rtpBuffer(sizeof(pobjRtpPktBuf), pobjRtpPktBuf);
    eRtp_Bool eResult = rtpPacket.decodePacket(&rtpBuffer);

    // check for padding.
    EXPECT_EQ(eResult, eRTP_SUCCESS);
    EXPECT_TRUE(rtpPacket.getRtpHeader()->getPadding() != RTP_ZERO);

    // check Payload
    RtpBuffer* pobjRtpBuffer = rtpPacket.getRtpPayload();
    uint8_t pRtpPayLoad[] = {0x67, 0x42, 0xc0, 0x0c, 0xda, 0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10,
            0x3c, 0x58, 0xba, 0x80};

    ASSERT_TRUE(pobjRtpBuffer != nullptr);
    EXPECT_EQ(memcmp(pRtpPayLoad, pobjRtpBuffer->getBuffer(), sizeof(pRtpPayLoad)), 0);
    EXPECT_EQ(pobjRtpBuffer->getLength(), sizeof(pRtpPayLoad));
}

TEST(RtpPacketTest, TestFormPacketwithoutExtension)
{
    RtpPacket rtpPacket;
    RtpHeader* pobjRtpHdr = rtpPacket.getRtpHeader();
    ASSERT_TRUE(pobjRtpHdr != nullptr);

    // set Rtp Headers
    pobjRtpHdr->setVersion(RTP_TWO);
    pobjRtpHdr->setExtension(RTP_ZERO);
    pobjRtpHdr->setCsrcCount(RTP_ZERO);
    pobjRtpHdr->setPayloadType(127);
    pobjRtpHdr->setSequenceNumber(45125);
    pobjRtpHdr->setRtpTimestamp(79466);
    pobjRtpHdr->setRtpSsrc(2932706306);

    // set RtpPayload
    uint8_t pRtpPayLoad[] = {0x67, 0x42, 0xc0, 0x0c, 0xda, 0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10,
            0x3c, 0x58, 0xba, 0x80};

    RtpBuffer* pobjRtpPayloadBuf = new RtpBuffer();
    RtpDt_UChar* pucRtpPayLoadBuffer = new RtpDt_UChar[sizeof(pRtpPayLoad)];
    memcpy(pucRtpPayLoadBuffer, pRtpPayLoad, sizeof(pRtpPayLoad));
    pobjRtpPayloadBuf->setBufferInfo(sizeof(pRtpPayLoad), pucRtpPayLoadBuffer);
    rtpPacket.setRtpPayload(pobjRtpPayloadBuf);

    const RtpDt_UInt32 uiRtpLength = RTP_FIXED_HDR_LEN + sizeof(pRtpPayLoad);

    // define expected Rtp packet.
    uint8_t pExpectedBuffer[uiRtpLength] = {0x80, 0x7f, 0xb0, 0x45, 0x00, 0x01, 0x36, 0x6a, 0xae,
            0xcd, 0x8c, 0x02, 0x67, 0x42, 0xc0, 0x0c, 0xda, 0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10,
            0x10, 0x3c, 0x58, 0xba, 0x80};

    // form Rtp packet
    uint8_t puiRtpBuffer[uiRtpLength] = {0};
    RtpBuffer rtpPacketbuf(uiRtpLength, puiRtpBuffer);
    eRtp_Bool eResult = rtpPacket.formPacket(&rtpPacketbuf);
    EXPECT_EQ(eResult, eRTP_TRUE);

    // Compare formed Rtp packet with expected Rtp packet
    EXPECT_EQ(memcmp(rtpPacketbuf.getBuffer(), pExpectedBuffer, uiRtpLength), 0);
}

TEST(RtpPacketTest, TestFormPacketwithExtension)
{
    RtpPacket rtpPacket;
    RtpHeader* pobjRtpHdr = rtpPacket.getRtpHeader();
    ASSERT_TRUE(pobjRtpHdr != nullptr);

    /*
     * Real-Time Transport Protocol
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..0. .... = Padding: False
     * ...1 .... = Extension: True
     * .... 0000 = Contributing source identifiers count: 0
     * 1... .... = Marker: True
     * Payload type: DynamicRTP-Type-99 (99)
     * Sequence number: 42371
     * Timestamp: 57800
     * Synchronization Source identifier: 0x927dcd02 (2457718018)
     * Defined by profile: Unknown (0xbede)
     * Extension length: 1
     * Header extensions
     *     RFC 5285 Header Extension (One-Byte Header)
     *         Identifier: 4
     *         Length: 2
     *         Extension Data: (0x7842)
     */

    // set Rtp Headers
    pobjRtpHdr->setVersion(RTP_TWO);
    pobjRtpHdr->setExtension(RTP_ONE);
    pobjRtpHdr->setMarker();
    pobjRtpHdr->setCsrcCount(RTP_ZERO);
    pobjRtpHdr->setPayloadType(99);
    pobjRtpHdr->setSequenceNumber(42371);
    pobjRtpHdr->setRtpTimestamp(57800);
    pobjRtpHdr->setRtpSsrc(2457718018);

    // set RtpExtension
    uint8_t pRtpExtension[] = {0xbe, 0xde, 0x00, 0x01, 0x41, 0x78, 0x42, 0x00};

    RtpBuffer* pobjpRtpExtensionBuf = new RtpBuffer();
    RtpDt_UChar* pucRtpExtensionBuffer = new RtpDt_UChar[sizeof(pRtpExtension)];
    memcpy(pucRtpExtensionBuffer, pRtpExtension, sizeof(pRtpExtension));
    pobjpRtpExtensionBuf->setBufferInfo(sizeof(pRtpExtension), pucRtpExtensionBuffer);
    rtpPacket.setExtHeader(pobjpRtpExtensionBuf);

    // set RtpPayload
    uint8_t pRtpPayLoad[] = {0x67, 0x42, 0xc0, 0x0c, 0xda, 0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10,
            0x3c, 0x58, 0xba, 0x80};

    RtpBuffer* pobjRtpPayloadBuf = new RtpBuffer();
    RtpDt_UChar* pucRtpPayLoadBuffer = new RtpDt_UChar[sizeof(pRtpPayLoad)];
    memcpy(pucRtpPayLoadBuffer, pRtpPayLoad, sizeof(pRtpPayLoad));
    pobjRtpPayloadBuf->setBufferInfo(sizeof(pRtpPayLoad), pucRtpPayLoadBuffer);
    rtpPacket.setRtpPayload(pobjRtpPayloadBuf);

    const RtpDt_UInt32 uiRtpLength =
            RTP_FIXED_HDR_LEN + sizeof(pRtpExtension) + sizeof(pRtpPayLoad);

    // define expected Rtp packet output.
    uint8_t pExpectedBuffer[] = {0x90, 0xe3, 0xa5, 0x83, 0x00, 0x00, 0xe1, 0xc8, 0x92, 0x7d, 0xcd,
            0x02, 0xbe, 0xde, 0x00, 0x01, 0x41, 0x78, 0x42, 0x00, 0x67, 0x42, 0xc0, 0x0c, 0xda,
            0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10, 0x3c, 0x58, 0xba, 0x80};

    // form Rtp packet
    uint8_t puiRtpBuffer[uiRtpLength] = {0};
    RtpBuffer rtpPacketbuf(uiRtpLength, puiRtpBuffer);
    eRtp_Bool eResult = rtpPacket.formPacket(&rtpPacketbuf);
    EXPECT_EQ(eResult, eRTP_TRUE);

    // Compare formed Rtp packet with expected Rtp packet
    EXPECT_EQ(memcmp(rtpPacketbuf.getBuffer(), pExpectedBuffer, uiRtpLength), 0);
}