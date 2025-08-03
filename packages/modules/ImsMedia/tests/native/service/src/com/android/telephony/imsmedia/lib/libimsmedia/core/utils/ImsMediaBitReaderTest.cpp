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
#include <ImsMediaBitReader.h>
#include <string.h>

class ImsMediaBitReaderTest : public ::testing::Test
{
public:
protected:
    virtual void SetUp() override {}

    virtual void TearDown() override {}
};

TEST_F(ImsMediaBitReaderTest, SetBufferAndReadBitTest)
{
    uint8_t testBuffer[] = {1, 2, 4, 8, 16, 32, 64, 128};

    ImsMediaBitReader reader;
    EXPECT_EQ(reader.Read(24), 0);
    reader.SetBuffer(testBuffer, sizeof(testBuffer));
    EXPECT_EQ(reader.Read(32), 0);

    for (int32_t i = 0; i < sizeof(testBuffer); i++)
    {
        EXPECT_EQ(reader.Read(8), testBuffer[i]);
    }

    EXPECT_EQ(reader.Read(8), 0);
}

TEST_F(ImsMediaBitReaderTest, SetBufferAndReadByteTest)
{
    uint8_t testBuffer[] = {1, 2, 4, 8, 16, 32, 64, 128};

    ImsMediaBitReader reader;
    reader.SetBuffer(testBuffer, sizeof(testBuffer));

    uint8_t dstBuffer[8] = {0};

    for (int32_t i = 0; i < sizeof(testBuffer); i++)
    {
        reader.ReadByteBuffer(dstBuffer + i, 8);
    }

    EXPECT_EQ(memcmp(dstBuffer, testBuffer, sizeof(testBuffer)), 0);
}

TEST_F(ImsMediaBitReaderTest, SetBufferAndReadUEModeTest)
{
    uint8_t testBuffer[] = {0xDA};  // 11011010

    ImsMediaBitReader reader;
    reader.SetBuffer(testBuffer, sizeof(testBuffer));

    EXPECT_EQ(reader.ReadByUEMode(), 0);
    EXPECT_EQ(reader.ReadByUEMode(), 0);
    EXPECT_EQ(reader.ReadByUEMode(), 2);
    EXPECT_EQ(reader.ReadByUEMode(), 1);
}
