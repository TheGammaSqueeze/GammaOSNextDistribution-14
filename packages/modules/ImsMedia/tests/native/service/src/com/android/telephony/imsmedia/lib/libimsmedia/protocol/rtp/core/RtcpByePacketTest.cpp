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

#include <RtcpByePacket.h>
#include <gtest/gtest.h>

#include <memory>

namespace android
{

class RtcpByePacketTest : public ::testing::Test
{
public:
    RtcpByePacket* rtcpByePacket;
    RtcpHeader rtcpHeader;

protected:
    virtual void SetUp() override
    {
        rtcpByePacket = new RtcpByePacket();
        ASSERT_TRUE(rtcpByePacket != nullptr);
    }

    virtual void TearDown() override
    {
        if (rtcpByePacket != nullptr)
        {
            delete rtcpByePacket;
            rtcpByePacket = nullptr;
        }
    }
};

TEST_F(RtcpByePacketTest, decodeByePacketWithSingleSsrc)
{
    /*
     * Real-time Transport Control Protocol (Goodbye)
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..0. .... = Padding: False
     * ...0 0011 = Reception report count: 3
     * Packet type: Goodbye (203)
     * Length: 2 (12 bytes)
     * Identifier : 0xb1c8cb01
     * SSRC : 0xb1c8cb02
     */

    uint8_t inputBuffer[] = {0xb1, 0xc8, 0xcb, 0x02};
    std::unique_ptr<RtpDt_UChar[]> byePacket(new RtpDt_UChar[sizeof(inputBuffer)]);
    memcpy(byePacket.get(), inputBuffer, sizeof(inputBuffer));

    rtcpHeader.setReceptionReportCount(2);
    rtcpByePacket->setRtcpHdrInfo(rtcpHeader);

    EXPECT_TRUE(rtcpByePacket->decodeByePacket(byePacket.get(), sizeof(inputBuffer)));

    std::list<RtpDt_UInt32*> ssrcList = rtcpByePacket->getSsrcList();
    EXPECT_EQ(ssrcList.size(), 1);
    EXPECT_EQ(*ssrcList.front(), reinterpret_cast<RtpDt_UInt32>(0xb1c8cb02));
}

TEST_F(RtcpByePacketTest, decodeByePacketWithMultipleSsrc)
{
    /*
     * Real-time Transport Control Protocol (Goodbye)
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..0. .... = Padding: False
     * ...0 0011 = Reception report count: 3
     * Packet type: Goodbye (203)
     * Length: 3 (16 bytes)
     * Identifier : 0xb1c8cb01
     * SSRC : 0xb1c8cb02
     * SSRC : 0xd2bd4e3e
     */

    uint8_t inputBuffer[] = {0xb1, 0xc8, 0xcb, 0x02, 0xd2, 0xbd, 0x4e, 0x3e};
    std::unique_ptr<RtpDt_UChar[]> byePacket(new RtpDt_UChar[sizeof(inputBuffer)]);
    memcpy(byePacket.get(), inputBuffer, sizeof(inputBuffer));

    rtcpHeader.setReceptionReportCount(3);
    rtcpByePacket->setRtcpHdrInfo(rtcpHeader);

    EXPECT_TRUE(rtcpByePacket->decodeByePacket(byePacket.get(), sizeof(inputBuffer)));

    std::list<RtpDt_UInt32*> ssrcList = rtcpByePacket->getSsrcList();
    EXPECT_EQ(ssrcList.size(), 2);
    EXPECT_EQ(*ssrcList.front(), reinterpret_cast<RtpDt_UInt32>(0xb1c8cb02));
    ssrcList.pop_front();
    EXPECT_EQ(*ssrcList.front(), reinterpret_cast<RtpDt_UInt32>(0xd2bd4e3e));
}

TEST_F(RtcpByePacketTest, decodeByePacketWithMultipleSsrcAndReason)
{
    /*
     * Real-time Transport Control Protocol (Goodbye)
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..1. .... = Padding: True
     * ...0 0011 = Reception report count: 3
     * Packet type: Goodbye (203)
     * Length: 8 (36 bytes)
     * Identifier : 0xb1c8cb01
     * SSRC : 0xb1c8cb02
     * SSRC : 0xd2bd4e3e
     * Length: 17
     * Reason for leaving: RTP loop detected
     * padding: 0x0002
     */

    uint8_t inputBuffer[] = {0xb1, 0xc8, 0xcb, 0x02, 0xd2, 0xbd, 0x4e, 0x3e, 0x11, 0x52, 0x54, 0x50,
            0x20, 0x6C, 0x6F, 0x6F, 0x70, 0x20, 0x64, 0x65, 0x74, 0x65, 0x63, 0x74, 0x65, 0x64,
            0x00, 0x02};
    std::unique_ptr<RtpDt_UChar[]> byePacket(new RtpDt_UChar[sizeof(inputBuffer)]);
    memcpy(byePacket.get(), inputBuffer, sizeof(inputBuffer));

    rtcpHeader.setReceptionReportCount(3);
    rtcpByePacket->setRtcpHdrInfo(rtcpHeader);

    EXPECT_TRUE(rtcpByePacket->decodeByePacket(byePacket.get(), sizeof(inputBuffer)));

    std::list<RtpDt_UInt32*> ssrcList = rtcpByePacket->getSsrcList();
    EXPECT_EQ(ssrcList.size(), 2);
    EXPECT_EQ(*ssrcList.front(), reinterpret_cast<RtpDt_UInt32>(0xb1c8cb02));
    ssrcList.pop_front();
    EXPECT_EQ(*ssrcList.front(), reinterpret_cast<RtpDt_UInt32>(0xd2bd4e3e));

    RtpBuffer* reasonBuf = rtcpByePacket->getReason();
    const char* reason = "RTP loop detected";
    EXPECT_EQ(reasonBuf->getLength(), strlen(reason));
    EXPECT_EQ(memcmp(reasonBuf->getBuffer(), reason, strlen(reason)), 0);
}

TEST_F(RtcpByePacketTest, formByePacketWithSsrc)
{
    /*
     * Real-time Transport Control Protocol (Goodbye)
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..0. .... = Padding: False
     * ...0 0011 = Reception report count: 3
     * Packet type: Goodbye (203)
     * Length: 3 (16 bytes)
     * Identifier : 0xb1c8cb01
     * SSRC : 0xb1c8cb02
     * SSRC : 0xd2bd4e3e
     */

    std::unique_ptr<RtpBuffer> byePacketBuffer(new RtpBuffer());
    std::unique_ptr<RtpDt_UChar[]> pcBuff(new RtpDt_UChar[RTP_DEF_MTU_SIZE]);

    byePacketBuffer->setBufferInfo(RTP_DEF_MTU_SIZE, pcBuff.release());
    byePacketBuffer->setLength(RTP_ZERO);

    rtcpHeader.setReceptionReportCount(3);
    rtcpByePacket->setRtcpHdrInfo(rtcpHeader);

    uint8_t pExpectedBuffer[] = {0xb1, 0xc8, 0xcb, 0x02, 0xd2, 0xbd, 0x4e, 0x3e};
    std::unique_ptr<RtpDt_UChar[]> byePacket(new RtpDt_UChar[sizeof(pExpectedBuffer)]);
    memcpy(byePacket.get(), pExpectedBuffer, sizeof(pExpectedBuffer));

    EXPECT_TRUE(rtcpByePacket->decodeByePacket(byePacket.get(), sizeof(pExpectedBuffer)));

    EXPECT_TRUE(rtcpByePacket->formByePacket(byePacketBuffer.get()));
    // Compare formed Rtcp Bye packet with expected Rtcp Bye packet
    EXPECT_EQ(memcmp((byePacketBuffer->getBuffer() + RTCP_FIXED_HDR_LEN), pExpectedBuffer,
                      sizeof(pExpectedBuffer)),
            0);
}

TEST_F(RtcpByePacketTest, formByePacketWithSsrcAndReason)
{
    /*
     * Real-time Transport Control Protocol (Goodbye)
     * 10.. .... = Version: RFC 1889 Version (2)
     * ..1. .... = Padding: True
     * ...0 0011 = Reception report count: 3
     * Packet type: Goodbye (203)
     * Length: 6 (28 bytes)
     * Identifier : 0xb1c8cb01
     * SSRC : 0xb1c8cb02
     * SSRC : 0xd2bd4e3e
     * Length: 8
     * Reason for leaving: teardown
     * padding: 0x000003
     */

    std::unique_ptr<RtpBuffer> byePacketBuffer(new RtpBuffer());
    std::unique_ptr<RtpDt_UChar[]> pcBuff(new RtpDt_UChar[RTP_DEF_MTU_SIZE]);

    byePacketBuffer->setBufferInfo(RTP_DEF_MTU_SIZE, pcBuff.release());
    byePacketBuffer->setLength(RTP_ZERO);

    rtcpHeader.setReceptionReportCount(3);
    rtcpByePacket->setRtcpHdrInfo(rtcpHeader);

    uint8_t pExpectedBuffer[] = {0xb1, 0xc8, 0xcb, 0x02, 0xd2, 0xbd, 0x4e, 0x3e, 0x08, 0x74, 0x65,
            0x61, 0x72, 0x64, 0x6F, 0x77, 0x6E, 0x00, 0x00, 0x03};
    std::unique_ptr<RtpDt_UChar[]> byePacket(new RtpDt_UChar[sizeof(pExpectedBuffer)]);
    memcpy(byePacket.get(), pExpectedBuffer, sizeof(pExpectedBuffer));

    EXPECT_TRUE(rtcpByePacket->decodeByePacket(byePacket.get(), sizeof(pExpectedBuffer)));

    EXPECT_TRUE(rtcpByePacket->formByePacket(byePacketBuffer.get()));
    // Compare formed Rtcp Bye packet with expected Rtcp Bye packet
    EXPECT_EQ(memcmp((byePacketBuffer->getBuffer() + RTCP_FIXED_HDR_LEN), pExpectedBuffer,
                      (sizeof(pExpectedBuffer) - 3)),
            0);
}

TEST_F(RtcpByePacketTest, CheckGetSets)
{
    rtcpByePacket->setRtcpHdrInfo(rtcpHeader);
    EXPECT_EQ(rtcpHeader, *(rtcpByePacket->getRtcpHdrInfo()));

    RtpBuffer* reasonBuf = new RtpBuffer();
    rtcpByePacket->setReason(reasonBuf);
    EXPECT_EQ(reasonBuf, rtcpByePacket->getReason());
}

}  // namespace android
