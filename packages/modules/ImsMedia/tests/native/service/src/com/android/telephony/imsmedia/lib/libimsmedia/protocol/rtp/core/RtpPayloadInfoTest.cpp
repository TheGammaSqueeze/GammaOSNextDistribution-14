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

#include <RtpPayloadInfo.h>
#include <gtest/gtest.h>

TEST(RtpPayloadInfoTest, TestDefaultConstructor)
{
    RtpPayloadInfo rtpPayloadInfo;

    // Check default value
    EXPECT_EQ(rtpPayloadInfo.getSamplingRate(), RTP_ZERO);
    for (RtpDt_UInt32 i = 0; i < RTP_MAX_PAYLOAD_TYPE; i++)
    {
        EXPECT_EQ(rtpPayloadInfo.getPayloadType(i), RTP_ZERO);
    }
}

TEST(RtpPayloadInfoTest, TestConstructor)
{
    RtpDt_UInt32 payloadType[RTP_MAX_PAYLOAD_TYPE] = {99, 127, 101};
    RtpPayloadInfo rtpPayloadInfo(payloadType, 16, RTP_MAX_PAYLOAD_TYPE);

    for (RtpDt_UInt32 i = 0; i < RTP_MAX_PAYLOAD_TYPE; i++)
    {
        EXPECT_EQ(rtpPayloadInfo.getPayloadType(i), payloadType[i]);
    }

    EXPECT_EQ(rtpPayloadInfo.getSamplingRate(), 16);
}

TEST(RtpPayloadInfoTest, TestSetRtpPayloadInfo)
{
    RtpDt_UInt32 payloadType[RTP_MAX_PAYLOAD_TYPE] = {98, 116};
    RtpPayloadInfo rtpPayloadInfoSource(payloadType, 8, RTP_TWO);
    RtpPayloadInfo rtpPayloadInfoDest;
    rtpPayloadInfoDest.setRtpPayloadInfo(&rtpPayloadInfoSource);

    for (RtpDt_UInt32 i = 0; i < RTP_TWO; i++)
    {
        EXPECT_EQ(rtpPayloadInfoDest.getPayloadType(i), payloadType[i]);
    }

    EXPECT_EQ(rtpPayloadInfoDest.getSamplingRate(), 8);
}