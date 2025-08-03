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

#include <RtcpHeader.h>
#include <gtest/gtest.h>

class RtcpHeaderTest : public ::testing::Test
{
public:
protected:
    virtual void SetUp() override {}

    virtual void TearDown() override {}
};

TEST_F(RtcpHeaderTest, TestVersion)
{
    RtcpHeader rtcpHeader;

    // Check default value
    EXPECT_EQ(rtcpHeader.getVersion(), 0);

    rtcpHeader.setVersion(100);
    EXPECT_EQ(rtcpHeader.getVersion(), 0);

    rtcpHeader.setVersion(-1);
    EXPECT_EQ(rtcpHeader.getVersion(), 0);

    rtcpHeader.setVersion(2);
    EXPECT_EQ(rtcpHeader.getVersion(), 2);
}

TEST_F(RtcpHeaderTest, TestPaddingFlag)
{
    RtcpHeader rtcpHeader;

    // Check default value
    EXPECT_EQ(rtcpHeader.getPadding(), eRTP_FALSE);

    // Check true value
    rtcpHeader.setPadding(eRTP_TRUE);
    EXPECT_EQ(rtcpHeader.getPadding(), eRTP_TRUE);

    // Check false value
    rtcpHeader.setPadding(eRTP_FALSE);
    EXPECT_EQ(rtcpHeader.getPadding(), eRTP_FALSE);

    // Check default arg value
    rtcpHeader.setPadding();
    EXPECT_EQ(rtcpHeader.getPadding(), eRTP_TRUE);
}

TEST_F(RtcpHeaderTest, TestReceptionReportCount)
{
    RtcpHeader rtcpHeader;

    // Check default value
    EXPECT_EQ(rtcpHeader.getReceptionReportCount(), 0);

    // Negative case: value more than max allowed.
    eRtp_Bool res = rtcpHeader.setReceptionReportCount(MAX_RECEPTION_REPORT_COUNT + 1);
    EXPECT_EQ(res, eRTP_FALSE);
    EXPECT_EQ(rtcpHeader.getReceptionReportCount(), 0);

    // Positive case: value within limits.
    res = rtcpHeader.setReceptionReportCount(MAX_RECEPTION_REPORT_COUNT);
    EXPECT_EQ(res, eRTP_TRUE);
    EXPECT_EQ(rtcpHeader.getReceptionReportCount(), MAX_RECEPTION_REPORT_COUNT);
}

TEST_F(RtcpHeaderTest, TestPacketType)
{
    RtcpHeader rtcpHeader;

    // Check default value
    EXPECT_EQ(rtcpHeader.getPacketType(), 0);

    rtcpHeader.setPacketType(202);
    EXPECT_EQ(rtcpHeader.getPacketType(), 202);
}

TEST_F(RtcpHeaderTest, TestPacketLength)
{
    RtcpHeader rtcpHeader;

    // Check default value
    EXPECT_EQ(rtcpHeader.getLength(), 0);

    rtcpHeader.setLength(202);
    EXPECT_EQ(rtcpHeader.getLength(), 202);
}

TEST_F(RtcpHeaderTest, TestSSRC)
{
    RtcpHeader rtcpHeader;

    // Check default value
    EXPECT_EQ(rtcpHeader.getSsrc(), 0);

    rtcpHeader.setSsrc(202);
    EXPECT_EQ(rtcpHeader.getSsrc(), 202);
}

TEST_F(RtcpHeaderTest, TestDecodeRtcpHeader)
{
    RtcpHeader rtcpHeader;

    RtpDt_UChar pRTCPBuff[] = {0x81, 0xc8, 0x00, 0x06, 0x59, 0x09, 0x41, 0x02};

    rtcpHeader.decodeRtcpHeader(pRTCPBuff, 8);
    EXPECT_EQ(rtcpHeader.getVersion(), 2);
    EXPECT_EQ(rtcpHeader.getPadding(), eRTP_FALSE);
    EXPECT_EQ(rtcpHeader.getReceptionReportCount(), 1);
    EXPECT_EQ(rtcpHeader.getPacketType(), 200);
    EXPECT_EQ(rtcpHeader.getLength(), 6 * RTP_WORD_SIZE);
    EXPECT_EQ(rtcpHeader.getSsrc(), 0x59094102);

    RtpDt_UChar pRTCPBuff2[] = {0xFF, 0xFF, 0xFF, 0xFF, 0x59, 0x09, 0x41, 0x02};

    rtcpHeader.decodeRtcpHeader(pRTCPBuff2, 8);
    EXPECT_EQ(rtcpHeader.getVersion(), MAX_RTP_VERSION);
    EXPECT_EQ(rtcpHeader.getPadding(), eRTP_TRUE);
    EXPECT_EQ(rtcpHeader.getReceptionReportCount(), MAX_RECEPTION_REPORT_COUNT);
    EXPECT_EQ(rtcpHeader.getPacketType(), 0xFF);
    EXPECT_EQ(rtcpHeader.getLength(), 0xFFFF * RTP_WORD_SIZE);
    EXPECT_EQ(rtcpHeader.getSsrc(), 0x59094102);
}

TEST_F(RtcpHeaderTest, TestFormRtcpHeader)
{
    RtcpHeader rtcpHeader;

    rtcpHeader.setVersion(2);
    rtcpHeader.setPadding();
    rtcpHeader.setReceptionReportCount(5);
    rtcpHeader.setPacketType(200);
    rtcpHeader.setLength(28);
    rtcpHeader.setSsrc(0xFFFFFFFF);
    RtpBuffer rtpBuffer(16, nullptr);
    rtpBuffer.setLength(0);
    rtcpHeader.formRtcpHeader(&rtpBuffer);

    RtpDt_UChar bExpectedRtcpBuff[] = {0xA5, 0xc8, 0x00, 0x06, 0xFF, 0xFF, 0xFF, 0xFF};

    EXPECT_EQ(rtpBuffer.getLength(), sizeof(bExpectedRtcpBuff));
    EXPECT_EQ(memcmp(rtpBuffer.getBuffer(), bExpectedRtcpBuff, sizeof(bExpectedRtcpBuff)), 0);
}