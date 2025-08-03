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

#include <RtpStackUtil.h>
#include <gtest/gtest.h>

TEST(RtpStackUtilTest, TestgetSequenceNumber)
{
    uint8_t pobjRtpPktBuf[] = {0x90, 0xe3, 0xa5, 0x83, 0x00, 0x00, 0xe1, 0xc8, 0x92, 0x7d, 0xcd,
            0x02, 0xbe, 0xde, 0x00, 0x01, 0x41, 0x78, 0x42, 0x00, 0x67, 0x42, 0xc0, 0x0c, 0xda,
            0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10, 0x3c, 0x58, 0xba, 0x80};

    EXPECT_EQ(RtpStackUtil::getSequenceNumber(pobjRtpPktBuf), 0xa583);
    EXPECT_EQ(RtpStackUtil::getSequenceNumber(nullptr), 0);
}

TEST(RtpStackUtilTest, TestgetRtpSsrc)
{
    uint8_t pobjRtpPktBuf[] = {0x90, 0xe3, 0xa5, 0x83, 0x00, 0x00, 0xe1, 0xc8, 0x92, 0x7d, 0xcd,
            0x02, 0xbe, 0xde, 0x00, 0x01, 0x41, 0x78, 0x42, 0x00, 0x67, 0x42, 0xc0, 0x0c, 0xda,
            0x0f, 0x0a, 0x69, 0xa8, 0x10, 0x10, 0x10, 0x3c, 0x58, 0xba, 0x80};

    EXPECT_EQ(RtpStackUtil::getRtpSsrc(pobjRtpPktBuf), 0x927dcd02);
    EXPECT_EQ(RtpStackUtil::getRtpSsrc(nullptr), 0);
}

TEST(RtpStackUtilTest, TestgetRtcpSsrc)
{
    uint8_t pobjRtcpPktBuf[] = {0xFF, 0xFF, 0xFF, 0xFF, 0x59, 0x09, 0x41, 0x02};

    EXPECT_EQ(RtpStackUtil::getRtcpSsrc(pobjRtcpPktBuf), 0x59094102);
    EXPECT_EQ(RtpStackUtil::getRtcpSsrc(nullptr), 0);
}

TEST(RtpStackUtilTest, TestgenerateNewSsrc)
{
    RtpDt_UInt32 uiSsrc1 = RtpStackUtil::generateNewSsrc(RTP_CONF_SSRC_SEED);
    usleep(100);
    RtpDt_UInt32 uiSsrc2 = RtpStackUtil::generateNewSsrc(RTP_CONF_SSRC_SEED);

    EXPECT_NE(uiSsrc1, uiSsrc2);
}

TEST(RtpStackUtilTest, TestgetMidFourOctets)
{
    /**
     * High32Bits: E6 87 A1 95
     * Low32Bits : CB AF 60 20
     * MidFourOctets: A1 95 CB AF
     */

    tRTP_NTP_TIME stNtpTimestamp;
    stNtpTimestamp.m_uiNtpHigh32Bits = 3867648405;
    stNtpTimestamp.m_uiNtpLow32Bits = 3417268256;

    EXPECT_EQ(RtpStackUtil::getMidFourOctets(&stNtpTimestamp), 0xa195cbaf);
    EXPECT_EQ(RtpStackUtil::getMidFourOctets(nullptr), 0);
}

TEST(RtpStackUtilTest, TestcalcRtpTs)
{
    RtpDt_UInt32 uiPrevRtpTimestamp = 57800;
    tRTP_NTP_TIME stPrevNtpTimestamp;
    stPrevNtpTimestamp.m_uiNtpHigh32Bits = 3867661587;
    stPrevNtpTimestamp.m_uiNtpLow32Bits = 1798971300;

    tRTP_NTP_TIME stCurNtpTimestamp;
    stCurNtpTimestamp.m_uiNtpHigh32Bits = 3867661587;
    stCurNtpTimestamp.m_uiNtpLow32Bits = 1803741934;

    RtpDt_UInt32 rtpTs = RtpStackUtil::calcRtpTimestamp(
            uiPrevRtpTimestamp, &stCurNtpTimestamp, &stPrevNtpTimestamp, 16000);

    EXPECT_EQ(rtpTs, 57817);
}

TEST(RtpStackUtilTest, TestcalcRtpTsWithNoPrevTs)
{
    RtpDt_UInt32 uiPrevRtpTimestamp = 57800;
    tRTP_NTP_TIME stPrevNtpTimestamp;
    stPrevNtpTimestamp.m_uiNtpHigh32Bits = 0;
    stPrevNtpTimestamp.m_uiNtpLow32Bits = 0;

    tRTP_NTP_TIME stCurNtpTimestamp;
    stCurNtpTimestamp.m_uiNtpHigh32Bits = 3867661587;
    stCurNtpTimestamp.m_uiNtpLow32Bits = 1803741934;

    RtpDt_UInt32 rtpTs = RtpStackUtil::calcRtpTimestamp(
            uiPrevRtpTimestamp, &stCurNtpTimestamp, &stPrevNtpTimestamp, 8000);
    EXPECT_EQ(rtpTs, uiPrevRtpTimestamp);

    rtpTs = RtpStackUtil::calcRtpTimestamp(uiPrevRtpTimestamp, nullptr, &stPrevNtpTimestamp, 16000);
    EXPECT_EQ(rtpTs, 0);

    rtpTs = RtpStackUtil::calcRtpTimestamp(uiPrevRtpTimestamp, &stCurNtpTimestamp, nullptr, 8000);
    EXPECT_EQ(rtpTs, 0);

    rtpTs = RtpStackUtil::calcRtpTimestamp(uiPrevRtpTimestamp, nullptr, nullptr, 16000);
    EXPECT_EQ(rtpTs, 0);
}