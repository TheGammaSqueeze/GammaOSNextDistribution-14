/**
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

#include <RtcpConfig.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;

const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 1000;
const int32_t kIntervalSec = 1500;
const int32_t kRtcpXrBlockTypes = RtcpConfig::FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK |
        RtcpConfig::FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK;

TEST(RtcpConfigTest, TestGetterSetter)
{
    RtcpConfig* rtcp = new RtcpConfig();
    rtcp->setCanonicalName(kCanonicalName);
    rtcp->setTransmitPort(kTransmitPort);
    rtcp->setIntervalSec(kIntervalSec);
    rtcp->setRtcpXrBlockTypes(kRtcpXrBlockTypes);
    EXPECT_EQ(rtcp->getCanonicalName(), kCanonicalName);
    EXPECT_EQ(rtcp->getTransmitPort(), kTransmitPort);
    EXPECT_EQ(rtcp->getIntervalSec(), kIntervalSec);
    EXPECT_EQ(rtcp->getRtcpXrBlockTypes(), kRtcpXrBlockTypes);
    delete rtcp;
}

TEST(RtcpConfigTest, TestParcel)
{
    RtcpConfig* rtcp = new RtcpConfig();
    rtcp->setCanonicalName(kCanonicalName);
    rtcp->setTransmitPort(kTransmitPort);
    rtcp->setIntervalSec(kIntervalSec);
    rtcp->setRtcpXrBlockTypes(kRtcpXrBlockTypes);

    android::Parcel parcel;
    rtcp->writeToParcel(&parcel);
    parcel.setDataPosition(0);

    RtcpConfig* rtcp2 = new RtcpConfig();
    rtcp2->readFromParcel(&parcel);
    EXPECT_EQ(*rtcp2, *rtcp);

    delete rtcp;
    delete rtcp2;
}

TEST(RtcpConfigTest, TestAssign)
{
    RtcpConfig config;
    config.setCanonicalName(kCanonicalName);
    config.setTransmitPort(kTransmitPort);
    config.setIntervalSec(kIntervalSec);
    config.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

    RtcpConfig config2;
    config2 = config;
    EXPECT_EQ(config, config2);
}

TEST(RtcpConfigTest, TestEqual)
{
    RtcpConfig* rtcp = new RtcpConfig();
    rtcp->setCanonicalName(kCanonicalName);
    rtcp->setTransmitPort(kTransmitPort);
    rtcp->setIntervalSec(kIntervalSec);
    rtcp->setRtcpXrBlockTypes(kRtcpXrBlockTypes);

    RtcpConfig* rtcp2 = new RtcpConfig();
    rtcp2->setCanonicalName(kCanonicalName);
    rtcp2->setTransmitPort(kTransmitPort);
    rtcp2->setIntervalSec(kIntervalSec);
    rtcp2->setRtcpXrBlockTypes(kRtcpXrBlockTypes);
    EXPECT_EQ(*rtcp, *rtcp2);
    delete rtcp;
    delete rtcp2;
}

TEST(RtcpConfigTest, TestNotEqual)
{
    RtcpConfig* rtcp = new RtcpConfig();
    rtcp->setCanonicalName(kCanonicalName);
    rtcp->setTransmitPort(kTransmitPort);
    rtcp->setIntervalSec(kIntervalSec);
    rtcp->setRtcpXrBlockTypes(kRtcpXrBlockTypes);

    RtcpConfig* rtcp2 = new RtcpConfig();
    android::String8 name("name2");
    rtcp2->setCanonicalName(name);
    rtcp2->setTransmitPort(kTransmitPort);
    rtcp2->setIntervalSec(kIntervalSec);
    rtcp2->setRtcpXrBlockTypes(kRtcpXrBlockTypes);

    RtcpConfig* rtcp3 = new RtcpConfig();
    rtcp3->setCanonicalName(kCanonicalName);
    rtcp3->setTransmitPort(9999);
    rtcp3->setIntervalSec(kIntervalSec);
    rtcp3->setRtcpXrBlockTypes(kRtcpXrBlockTypes);

    EXPECT_NE(*rtcp, *rtcp2);
    EXPECT_NE(*rtcp, *rtcp3);

    delete rtcp;
    delete rtcp2;
    delete rtcp3;
}