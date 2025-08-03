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

#include <RtcpAppPacket.h>
#include <gtest/gtest.h>

#include <memory>

namespace android
{

class RtcpAppPacketTest : public ::testing::Test
{
public:
    RtcpAppPacket* testRtcpAppPacket;

protected:
    virtual void SetUp() override
    {
        testRtcpAppPacket = new RtcpAppPacket();
        ASSERT_TRUE(testRtcpAppPacket != nullptr);
    }

    virtual void TearDown() override
    {
        if (testRtcpAppPacket)
        {
            delete testRtcpAppPacket;
            testRtcpAppPacket = nullptr;
        }
    }
};

/** Successful Test scenario */
TEST_F(RtcpAppPacketTest, decodeAppPacketSuccess)
{
    std::unique_ptr<RtpDt_UChar[]> pucAppBuf(new RtpDt_UChar[14]);
    ASSERT_TRUE(pucAppBuf != nullptr);

    memcpy(pucAppBuf.get(),
            (RtpDt_UChar[]){0x80, 0xCC, 0x00, 0x07, 0x19, 0x6D, 0x27, 0xC5, 0x2B, 0x67, 0x01}, 13);
    EXPECT_TRUE(testRtcpAppPacket->decodeAppPacket(pucAppBuf.get(), 13));
}

/** App Packet with Exact 12 Byte, Without Application dependent data */
TEST_F(RtcpAppPacketTest, decodeAppPacketBoundaryLength)
{
    std::unique_ptr<RtpDt_UChar[]> pucAppBuf(new RtpDt_UChar[14]);
    ASSERT_TRUE(pucAppBuf != nullptr);

    memcpy(pucAppBuf.get(),
            (RtpDt_UChar[]){0x80, 0xCC, 0x00, 0x07, 0x19, 0x6D, 0x27, 0xC5, 0x2B, 0x67}, 12);
    EXPECT_TRUE(testRtcpAppPacket->decodeAppPacket(pucAppBuf.get(), 12));
}

/**  App Packet with less than expected Length, Without Application dependent data */
TEST_F(RtcpAppPacketTest, decodeAppPacketUnderBoundaryLength)
{
    std::unique_ptr<RtpDt_UChar[]> pucAppBuf(new RtpDt_UChar[14]);
    ASSERT_TRUE(pucAppBuf != nullptr);

    memcpy(pucAppBuf.get(), (RtpDt_UChar[]){0x80, 0xCC, 0x00, 0x07, 0x19, 0x6D, 0x27, 0xC5, 0x2B},
            11);
    EXPECT_TRUE(testRtcpAppPacket->decodeAppPacket(pucAppBuf.get(), 11));
}

/** Successful Test scenario */
TEST_F(RtcpAppPacketTest, formAppPacketSuccessTest)
{
    std::unique_ptr<RtpBuffer> testBuf(new RtpBuffer());
    ASSERT_TRUE(testBuf != nullptr);

    std::unique_ptr<RtpDt_UChar[]> pcBuff(new RtpDt_UChar[RTP_DEF_MTU_SIZE]);

    ASSERT_TRUE(pcBuff != nullptr);

    testBuf->setBufferInfo(RTP_DEF_MTU_SIZE, pcBuff.release());
    testBuf->setLength(RTP_ZERO);

    EXPECT_EQ(RTP_ZERO, testBuf->getLength());

    RtpBuffer* testBufPtr = testBuf.release();
    testRtcpAppPacket->setAppData(testBufPtr);

    RtpDt_UInt32 uiName = 1111;
    testRtcpAppPacket->setName(uiName);

    // funny test here: output to testBufPtr, and it is already in testRtcpAppPacket
    EXPECT_TRUE(testRtcpAppPacket->formAppPacket(testBufPtr));
}

/** With m_pAppData condition true */
TEST_F(RtcpAppPacketTest, formAppPacketBufferOverflowTest)
{
    std::unique_ptr<RtpBuffer> testBuf(new RtpBuffer());
    ASSERT_TRUE(testBuf != nullptr);

    std::unique_ptr<RtpDt_UChar[]> pcBuff(new RtpDt_UChar[RTP_DEF_MTU_SIZE]);

    ASSERT_TRUE(pcBuff != nullptr);

    testBuf->setBufferInfo(RTP_DEF_MTU_SIZE, pcBuff.release());
    testBuf->setLength(RTP_ZERO);

    EXPECT_EQ(RTP_ZERO, testBuf->getLength());

    RtpDt_UInt32 uiName = 11111111;
    testRtcpAppPacket->setName(uiName);

    std::unique_ptr<RtpBuffer> testBufAppData(new RtpBuffer());
    ASSERT_TRUE(testBufAppData != nullptr);

    std::unique_ptr<RtpDt_UChar[]> appBuffNew(new RtpDt_UChar[10]);
    ASSERT_TRUE(appBuffNew != nullptr);

    memcpy(appBuffNew.get(),
            (RtpDt_UChar[]){0x01, 0x07, 0x08, 0x09, 0x01, 0x02, 0x03, 0x04, 0xAA, 0xBB, 0x40, 0x20},
            10);

    testBufAppData->setBufferInfo(10, appBuffNew.release());
    testBufAppData->setLength(10);

    testRtcpAppPacket->setAppData(testBufAppData.release());
    EXPECT_TRUE(testRtcpAppPacket->formAppPacket(testBuf.get()));
    // auto delete of testBuf
}

}  // namespace android
