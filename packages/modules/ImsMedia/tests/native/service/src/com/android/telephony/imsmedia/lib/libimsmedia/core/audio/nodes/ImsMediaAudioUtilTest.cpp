/**
 * Copyright (C) 2024 The Android Open Source Project
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
#include <ImsMediaAudioUtil.h>

class ImsMediaAudioUtilTest : public ::testing::Test
{
protected:
    virtual void SetUp() override {}

    virtual void TearDown() override {}
};

TEST_F(ImsMediaAudioUtilTest, ConvertCodecTypeTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::ConvertCodecType(AudioConfig::CODEC_AMR), kAudioCodecAmr);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertCodecType(AudioConfig::CODEC_AMR_WB), kAudioCodecAmrWb);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertCodecType(AudioConfig::CODEC_EVS), kAudioCodecEvs);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertCodecType(AudioConfig::CODEC_PCMA), kAudioCodecPcma);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertCodecType(AudioConfig::CODEC_PCMU), kAudioCodecPcmu);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertCodecType(12), kAudioCodecAmr);
}

TEST_F(ImsMediaAudioUtilTest, ConvertAmrModeToBitLenTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrModeToBitLen(kImsAudioAmrMode475), 95);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrModeToBitLen(kImsAudioAmrModeSID), 39);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrModeToBitLen(kImsAudioAmrModeNoData), 0);
}

TEST_F(ImsMediaAudioUtilTest, ConvertLenToAmrModeTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::ConvertLenToAmrMode(0), kImsAudioAmrModeNoData);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertLenToAmrMode(5), kImsAudioAmrModeSID);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertLenToAmrMode(12), kImsAudioAmrMode475);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertLenToAmrMode(30), kImsAudioAmrModeNoData);
}

TEST_F(ImsMediaAudioUtilTest, ConvertAmrWbModeToBitLenTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(kImsAudioAmrWbModeNoData), 0);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(kImsAudioAmrWbModeSID), 40);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(kImsAudioAmrWbModeNoData), 0);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(kImsAudioAmrWbMode660), 132);
}

TEST_F(ImsMediaAudioUtilTest, ConvertLenToAmrWbModeTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::ConvertLenToAmrWbMode(0), kImsAudioAmrWbModeNoData);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertLenToAmrWbMode(5), kImsAudioAmrWbModeSID);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertLenToAmrWbMode(17), kImsAudioAmrWbMode660);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertLenToAmrWbMode(30), kImsAudioAmrWbModeNoData);
}

TEST_F(ImsMediaAudioUtilTest, ConvertAmrModeToBitrateTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrModeToBitrate(kImsAudioAmrMode475), 4750);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrModeToBitrate(kImsAudioAmrMode1020), 10200);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrModeToBitrate(kImsAudioAmrMode1220), 12200);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrModeToBitrate(kImsAudioAmrModeSID), 12200);
}

TEST_F(ImsMediaAudioUtilTest, ConvertAmrWbModeToBitrateTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrWbModeToBitrate(kImsAudioAmrWbMode660), 6600);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrWbModeToBitrate(kImsAudioAmrWbMode2305), 23050);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrWbModeToBitrate(kImsAudioAmrWbMode2385), 23850);
    EXPECT_EQ(ImsMediaAudioUtil::ConvertAmrWbModeToBitrate(kImsAudioAmrWbModeSID), 23850);
}

TEST_F(ImsMediaAudioUtilTest, GetMaximumAmrModeTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmrWb, 255), kImsAudioAmrWbMode2305);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmrWb, 511), kImsAudioAmrWbMode2385);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmrWb, 15), kImsAudioAmrWbMode1425);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmrWb, 1), kImsAudioAmrWbMode660);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmrWb, 0), kImsAudioAmrWbMode2385);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmr, 255), kImsAudioAmrMode1220);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmr, 511), kImsAudioAmrMode1220);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmr, 15), kImsAudioAmrMode670);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmr, 1), kImsAudioAmrMode475);
    EXPECT_EQ(ImsMediaAudioUtil::GetMaximumAmrMode(kAudioCodecAmr, 0), kImsAudioAmrMode1220);
}

TEST_F(ImsMediaAudioUtilTest, GetAmrFrametypeTest)
{
    EXPECT_EQ(ImsMediaAudioUtil::GetAmrFrameType(kAudioCodecAmr, kImsAudioAmrMode590, 15),
            MEDIASUBTYPE_AUDIO_NORMAL);
    EXPECT_EQ(ImsMediaAudioUtil::GetAmrFrameType(kAudioCodecAmr, kImsAudioAmrModeNoData, 0),
            MEDIASUBTYPE_AUDIO_NODATA);
    EXPECT_EQ(ImsMediaAudioUtil::GetAmrFrameType(kAudioCodecAmr, kImsAudioAmrMode1220, 0),
            MEDIASUBTYPE_AUDIO_NODATA);
    EXPECT_EQ(ImsMediaAudioUtil::GetAmrFrameType(kAudioCodecAmr, kImsAudioAmrModeSID, 7),
            MEDIASUBTYPE_AUDIO_SID);
    EXPECT_EQ(ImsMediaAudioUtil::GetAmrFrameType(kAudioCodecAmrWb, kImsAudioAmrWbMode660, 31),
            MEDIASUBTYPE_AUDIO_NORMAL);
    EXPECT_EQ(ImsMediaAudioUtil::GetAmrFrameType(kAudioCodecAmrWb, kImsAudioAmrWbModeNoData, 0),
            MEDIASUBTYPE_AUDIO_NODATA);
    EXPECT_EQ(ImsMediaAudioUtil::GetAmrFrameType(kAudioCodecAmrWb, kImsAudioAmrWbMode1265, 0),
            MEDIASUBTYPE_AUDIO_NODATA);
    EXPECT_EQ(ImsMediaAudioUtil::GetAmrFrameType(kAudioCodecAmrWb, kImsAudioAmrWbModeSID, 7),
            MEDIASUBTYPE_AUDIO_SID);
}
