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

#include <RtcpReportBlock.h>
#include <gtest/gtest.h>

TEST(RtcpReportBlockTest, TestGetSetMethods)
{
    RtcpReportBlock objRtcpReportBlock;

    objRtcpReportBlock.setSsrc(0x86d4e6e9);
    EXPECT_EQ(objRtcpReportBlock.getSsrc(), 0x86d4e6e9);

    objRtcpReportBlock.setFracLost(0xFF);
    EXPECT_EQ(objRtcpReportBlock.getFracLost(), 0xFF);

    objRtcpReportBlock.setCumNumPktLost(0xAABBCCDD);
    EXPECT_EQ(objRtcpReportBlock.getCumNumPktLost(), 0xAABBCCDD);

    objRtcpReportBlock.setExtHighSeqRcv(0x11223344);
    EXPECT_EQ(objRtcpReportBlock.getExtHighSeqRcv(), 0x11223344);

    objRtcpReportBlock.setJitter(0x01020304);
    EXPECT_EQ(objRtcpReportBlock.getJitter(), 0x01020304);

    objRtcpReportBlock.setLastSR(0x86d4e600);
    EXPECT_EQ(objRtcpReportBlock.getLastSR(), 0x86d4e600);

    objRtcpReportBlock.setDelayLastSR(0x86d4e601);
    EXPECT_EQ(objRtcpReportBlock.getDelayLastSR(), 0x86d4e601);
}

TEST(RtcpReportBlockTest, TestDecodeReportBlock)
{
    uint8_t bufReportBlock[] = {0x01, 0x02, 0x03, 0x04, 0x10, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x86, 0xd4, 0xe6, 0xe9, 0x00, 0x00, 0x00, 0x01};
    RtcpReportBlock objRtcpReportBlock;
    objRtcpReportBlock.decodeReportBlock(reinterpret_cast<RtpDt_UChar*>(bufReportBlock));

    EXPECT_EQ(objRtcpReportBlock.getSsrc(), 0x01020304);
    EXPECT_EQ((int)objRtcpReportBlock.getFracLost(), 0x10);
    EXPECT_EQ((int)objRtcpReportBlock.getCumNumPktLost(), 0x000020);
    EXPECT_EQ(objRtcpReportBlock.getExtHighSeqRcv(), 0);
    EXPECT_EQ(objRtcpReportBlock.getJitter(), 0);
    EXPECT_EQ(objRtcpReportBlock.getLastSR(), 0x86d4e6e9);
    EXPECT_EQ(objRtcpReportBlock.getDelayLastSR(), 0x00000001);
}

TEST(RtcpReportBlockTest, TestFormReportBlock)
{
    RtcpReportBlock objRtcpReportBlock;
    objRtcpReportBlock.setSsrc(0x86d4e6e9);
    objRtcpReportBlock.setFracLost(0xFF);
    objRtcpReportBlock.setCumNumPktLost(0xAABBCC);
    objRtcpReportBlock.setExtHighSeqRcv(0x11223344);
    objRtcpReportBlock.setJitter(0x01020304);
    objRtcpReportBlock.setLastSR(0x86d4e600);
    objRtcpReportBlock.setDelayLastSR(0x86d4e601);

    RtpBuffer objRtcpPktBuf(64, nullptr);
    objRtcpPktBuf.setLength(0);
    eRtp_Bool res = objRtcpReportBlock.formReportBlock(&objRtcpPktBuf);
    EXPECT_EQ(res, eRTP_SUCCESS);

    uint8_t bufReportBlock[] = {0x86, 0xd4, 0xe6, 0xe9, 0xFF, 0xAA, 0xBB, 0xCC, 0x11, 0x22, 0x33,
            0x44, 0x01, 0x02, 0x03, 0x04, 0x86, 0xd4, 0xe6, 0x00, 0x86, 0xd4, 0xe6, 0x01};

    EXPECT_EQ(memcmp(bufReportBlock, objRtcpPktBuf.getBuffer(), sizeof(bufReportBlock)), 0);
}