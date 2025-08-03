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

#include <RtpBuffer.h>
#include <gtest/gtest.h>

namespace android
{

class RtpBufferTest : public ::testing::Test
{
public:
    RtpBuffer* testBuf;

protected:
    virtual void SetUp() override
    {
        testBuf = new RtpBuffer();
        ASSERT_TRUE(testBuf != nullptr);
    }

    virtual void TearDown() override
    {
        if (testBuf)
        {
            delete testBuf;
            testBuf = nullptr;
        }
    }
};

TEST_F(RtpBufferTest, InitTest)
{
    EXPECT_EQ(0, testBuf->getLength());
    ASSERT_TRUE(testBuf->getBuffer() == nullptr);
}

TEST_F(RtpBufferTest, SetLengthTest)
{
    testBuf->setLength(10);
    EXPECT_EQ(10, testBuf->getLength());
}

TEST_F(RtpBufferTest, SetBufferInfoTest)
{
    RtpDt_UChar* pBuf = new RtpDt_UChar[7];
    uint8_t expectedBuf[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
    memcpy(pBuf, expectedBuf, sizeof(expectedBuf));
    testBuf->setBufferInfo(sizeof(expectedBuf), pBuf);
    EXPECT_EQ(7, testBuf->getLength());
    EXPECT_EQ(0, memcmp(testBuf->getBuffer(), pBuf, 7));
}

}  // namespace android
