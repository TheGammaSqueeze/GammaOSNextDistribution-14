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

#include <MediaQualityStatus.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;

const int32_t kRtpInactivityTimeMillis = 10000;
const int32_t kRtcpInactivityTimeMillis = 10000;
const int32_t kRtpPacketLossRate = 1;
const int32_t kRtpJitterMillis = 100;

class MediaQualityStatusTest : public ::testing::Test
{
public:
    MediaQualityStatus status;

protected:
    virtual void SetUp() override
    {
        status.setRtpInactivityTimeMillis(kRtpInactivityTimeMillis);
        status.setRtcpInactivityTimeMillis(kRtcpInactivityTimeMillis);
        status.setRtpPacketLossRate(kRtpPacketLossRate);
        status.setRtpJitterMillis(kRtpJitterMillis);
    }

    virtual void TearDown() override {}
};

TEST_F(MediaQualityStatusTest, TestGetterSetter)
{
    EXPECT_EQ(status.getRtpInactivityTimeMillis(), kRtpInactivityTimeMillis);
    EXPECT_EQ(status.getRtcpInactivityTimeMillis(), kRtcpInactivityTimeMillis);
    EXPECT_EQ(status.getRtpPacketLossRate(), kRtpPacketLossRate);
    EXPECT_EQ(status.getRtpJitterMillis(), kRtpJitterMillis);
}

TEST_F(MediaQualityStatusTest, TestParcel)
{
    android::Parcel parcel;
    status.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    MediaQualityStatus testThreshold;
    testThreshold.readFromParcel(&parcel);
    EXPECT_EQ(testThreshold, status);
}

TEST_F(MediaQualityStatusTest, TestAssign)
{
    MediaQualityStatus status2;
    status2 = status;
    EXPECT_EQ(status, status2);
}

TEST_F(MediaQualityStatusTest, TestEqual)
{
    MediaQualityStatus status2;
    status2.setRtpInactivityTimeMillis(kRtpInactivityTimeMillis);
    status2.setRtcpInactivityTimeMillis(kRtcpInactivityTimeMillis);
    status2.setRtpPacketLossRate(kRtpPacketLossRate);
    status2.setRtpJitterMillis(kRtpJitterMillis);
    EXPECT_EQ(status, status2);
}

TEST_F(MediaQualityStatusTest, TestNotEqual)
{
    MediaQualityStatus status2;
    status2.setRtpInactivityTimeMillis(15000);
    status2.setRtcpInactivityTimeMillis(kRtcpInactivityTimeMillis);
    status2.setRtpPacketLossRate(kRtpPacketLossRate);
    status2.setRtpJitterMillis(kRtpJitterMillis);

    MediaQualityStatus status3;
    status3.setRtpInactivityTimeMillis(kRtpInactivityTimeMillis);
    status3.setRtcpInactivityTimeMillis(kRtcpInactivityTimeMillis);
    status3.setRtpPacketLossRate(3);
    status3.setRtpJitterMillis(kRtpJitterMillis);

    EXPECT_NE(status, status2);
    EXPECT_NE(status, status3);
}