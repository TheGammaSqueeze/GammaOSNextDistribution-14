/**
 * Copyright (C) 2023 The Android Open Source Project
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

#include <gtest/gtest.h>
#include <ImsMediaBitWriter.h>
#include <string.h>

class ImsMediaBitWriterTest : public ::testing::Test
{
public:
protected:
    virtual void SetUp() override {}

    virtual void TearDown() override {}
};

TEST_F(ImsMediaBitWriterTest, SetBufferAndWriteBitTest)
{
    uint8_t testBuffer[] = {1, 2, 4, 8, 16, 32, 64, 128};
    uint8_t dstBuffer[8] = {0};

    ImsMediaBitWriter writer;

    EXPECT_EQ(writer.Write(0, 24), false);
    writer.SetBuffer(dstBuffer, sizeof(dstBuffer));
    EXPECT_EQ(writer.Write(0, 32), false);

    for (int32_t i = 0; i < sizeof(testBuffer); i++)
    {
        EXPECT_EQ(writer.Write(testBuffer[i], 8), true);
    }

    EXPECT_EQ(writer.Write(0, 8), false);
    EXPECT_EQ(memcmp(dstBuffer, testBuffer, sizeof(testBuffer)), 0);
}

TEST_F(ImsMediaBitWriterTest, SetBufferAndWriteByteTest)
{
    uint8_t testBuffer[] = {1, 2, 4, 8, 16, 32, 64, 128};
    uint8_t dstBuffer[8] = {0};

    ImsMediaBitWriter writer;
    writer.SetBuffer(dstBuffer, sizeof(dstBuffer));

    for (int32_t i = 0; i < sizeof(testBuffer); i++)
    {
        EXPECT_EQ(writer.WriteByteBuffer(testBuffer + i, 8), true);
    }

    EXPECT_EQ(memcmp(dstBuffer, testBuffer, sizeof(testBuffer)), 0);
}

TEST_F(ImsMediaBitWriterTest, SetBufferAndSeekToWriteTest)
{
    uint8_t testBuffer[] = {1, 2, 4, 8, 16, 32, 64, 128};
    uint8_t dstBuffer[8] = {1, 2, 4, 8};

    ImsMediaBitWriter writer;
    writer.SetBuffer(dstBuffer, sizeof(dstBuffer));
    writer.Seek(32);
    writer.WriteByteBuffer(testBuffer + 4, 32);

    EXPECT_EQ(memcmp(dstBuffer, testBuffer, sizeof(testBuffer)), 0);
}
