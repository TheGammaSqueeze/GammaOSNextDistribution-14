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

#include <VideoConfig.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;
// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_NO_FLOW;
const android::String8 kRemoteAddress("0.0.0.0");
const int32_t kRemotePort = 1000;
const int32_t kMtu = 1500;
const int8_t kDscp = 0;
const int8_t kRxPayload = 102;
const int8_t kTxPayload = 102;
const int8_t kSamplingRate = 90;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 1001;
const int32_t kIntervalSec = 1500;
const int32_t kRtcpXrBlockTypes = RtcpConfig::FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK |
        RtcpConfig::FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK;

// VideoConfig
const int32_t kVideoMode = VideoConfig::VIDEO_MODE_PREVIEW;
const int32_t kCodecType = VideoConfig::CODEC_AVC;
const int32_t kFramerate = DEFAULT_FRAMERATE;
const int32_t kBitrate = DEFAULT_BITRATE;
const int32_t kCodecProfile = VideoConfig::AVC_PROFILE_BASELINE;
const int32_t kCodecLevel = VideoConfig::AVC_LEVEL_12;
const int32_t kIntraFrameIntervalSec = 1;
const int32_t kPacketizationMode = VideoConfig::MODE_NON_INTERLEAVED;
const int32_t kCameraId = 0;
const int32_t kCameraZoom = 10;
const int32_t kResolutionWidth = DEFAULT_RESOLUTION_WIDTH;
const int32_t kResolutionHeight = DEFAULT_RESOLUTION_HEIGHT;
const android::String8 kPauseImagePath("data/user_de/0/com.android.telephony.imsmedia/test.jpg");
const int32_t kDeviceOrientationDegree = 0;
const int32_t kCvoValue = 1;
const int32_t kRtcpFbTypes = VideoConfig::RTP_FB_NONE;

// for encoder
const char* kMimeType = "video/avc";

class VideoConfigTest : public ::testing::Test
{
public:
    RtcpConfig rtcp;
    VideoConfig config1;
    VideoConfig config2;
    VideoConfig config3;

protected:
    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);
        config1.setMediaDirection(kMediaDirection);
        config1.setRemoteAddress(kRemoteAddress);
        config1.setRemotePort(kRemotePort);
        config1.setRtcpConfig(rtcp);
        config1.setMaxMtuBytes(kMtu);
        config1.setDscp(kDscp);
        config1.setRxPayloadTypeNumber(kRxPayload);
        config1.setTxPayloadTypeNumber(kTxPayload);
        config1.setSamplingRateKHz(kSamplingRate);
        config1.setVideoMode(kVideoMode);
        config1.setCodecType(kCodecType);
        config1.setFramerate(kFramerate);
        config1.setBitrate(kBitrate);
        config1.setCodecProfile(kCodecProfile);
        config1.setCodecLevel(kCodecLevel);
        config1.setIntraFrameInterval(kIntraFrameIntervalSec);
        config1.setPacketizationMode(kPacketizationMode);
        config1.setCameraId(kCameraId);
        config1.setCameraZoom(kCameraZoom);
        config1.setResolutionWidth(kResolutionWidth);
        config1.setResolutionHeight(kResolutionHeight);
        config1.setPauseImagePath(kPauseImagePath);
        config1.setDeviceOrientationDegree(kDeviceOrientationDegree);
        config1.setCvoValue(kCvoValue);
        config1.setRtcpFbType(kRtcpFbTypes);
    }

    virtual void TearDown() override {}
};

TEST_F(VideoConfigTest, TestGetterSetter)
{
    EXPECT_EQ(config1.getMediaDirection(), kMediaDirection);
    EXPECT_EQ(config1.getRemoteAddress(), kRemoteAddress);
    EXPECT_EQ(config1.getRemotePort(), kRemotePort);
    EXPECT_EQ(config1.getRtcpConfig(), rtcp);
    EXPECT_EQ(config1.getMaxMtuBytes(), kMtu);
    EXPECT_EQ(config1.getDscp(), kDscp);
    EXPECT_EQ(config1.getRxPayloadTypeNumber(), kRxPayload);
    EXPECT_EQ(config1.getTxPayloadTypeNumber(), kTxPayload);
    EXPECT_EQ(config1.getSamplingRateKHz(), kSamplingRate);
    EXPECT_EQ(config1.getVideoMode(), kVideoMode);
    EXPECT_EQ(config1.getCodecType(), kCodecType);
    EXPECT_EQ(config1.getFramerate(), kFramerate);
    EXPECT_EQ(config1.getBitrate(), kBitrate);
    EXPECT_EQ(config1.getCodecProfile(), kCodecProfile);
    EXPECT_EQ(config1.getCodecLevel(), kCodecLevel);
    EXPECT_EQ(config1.getIntraFrameInterval(), kIntraFrameIntervalSec);
    EXPECT_EQ(config1.getPacketizationMode(), kPacketizationMode);
    EXPECT_EQ(config1.getCameraId(), kCameraId);
    EXPECT_EQ(config1.getCameraZoom(), kCameraZoom);
    EXPECT_EQ(config1.getResolutionWidth(), kResolutionWidth);
    EXPECT_EQ(config1.getResolutionHeight(), kResolutionHeight);
    EXPECT_EQ(config1.getPauseImagePath(), kPauseImagePath);
    EXPECT_EQ(config1.getDeviceOrientationDegree(), kDeviceOrientationDegree);
    EXPECT_EQ(config1.getCvoValue(), kCvoValue);
    EXPECT_EQ(config1.getRtcpFbType(), kRtcpFbTypes);
}

TEST_F(VideoConfigTest, TestParcel)
{
    android::Parcel parcel;
    status_t err = config1.writeToParcel(&parcel);
    EXPECT_EQ(err, NO_ERROR);
    parcel.setDataPosition(0);

    VideoConfig configTest;
    err = configTest.readFromParcel(&parcel);
    EXPECT_EQ(err, NO_ERROR);
    EXPECT_EQ(configTest, config1);
}

TEST_F(VideoConfigTest, TestAssign)
{
    VideoConfig testConfig;
    testConfig = config1;
    EXPECT_EQ(config1, testConfig);

    VideoConfig* testConfig2 = new VideoConfig(config1);
    EXPECT_EQ(config1, *testConfig2);
    delete testConfig2;
}

TEST_F(VideoConfigTest, TestEqual)
{
    config2.setMediaDirection(kMediaDirection);
    config2.setRemoteAddress(kRemoteAddress);
    config2.setRemotePort(kRemotePort);
    config2.setRtcpConfig(rtcp);
    config2.setMaxMtuBytes(kMtu);
    config2.setDscp(kDscp);
    config2.setRxPayloadTypeNumber(kRxPayload);
    config2.setTxPayloadTypeNumber(kTxPayload);
    config2.setSamplingRateKHz(kSamplingRate);
    config2.setVideoMode(kVideoMode);
    config2.setCodecType(kCodecType);
    config2.setFramerate(kFramerate);
    config2.setBitrate(kBitrate);
    config2.setCodecProfile(kCodecProfile);
    config2.setCodecLevel(kCodecLevel);
    config2.setIntraFrameInterval(kIntraFrameIntervalSec);
    config2.setPacketizationMode(kPacketizationMode);
    config2.setCameraId(kCameraId);
    config2.setCameraZoom(kCameraZoom);
    config2.setResolutionWidth(kResolutionWidth);
    config2.setResolutionHeight(kResolutionHeight);
    config2.setPauseImagePath(kPauseImagePath);
    config2.setDeviceOrientationDegree(kDeviceOrientationDegree);
    config2.setCvoValue(kCvoValue);
    config2.setRtcpFbType(kRtcpFbTypes);
    EXPECT_EQ(config2, config1);
}

TEST_F(VideoConfigTest, TestNotEqual)
{
    config2.setMediaDirection(kMediaDirection);
    config2.setRemoteAddress(kRemoteAddress);
    config2.setRemotePort(2000);
    config2.setRtcpConfig(rtcp);
    config2.setMaxMtuBytes(kMtu);
    config2.setDscp(kDscp);
    config2.setRxPayloadTypeNumber(kRxPayload);
    config2.setTxPayloadTypeNumber(kTxPayload);
    config2.setSamplingRateKHz(kSamplingRate);
    config2.setVideoMode(kVideoMode);
    config2.setCodecType(kCodecType);
    config2.setFramerate(kFramerate);
    config2.setBitrate(kBitrate);
    config2.setCodecProfile(kCodecProfile);
    config2.setCodecLevel(kCodecLevel);
    config2.setIntraFrameInterval(kIntraFrameIntervalSec);
    config2.setPacketizationMode(kPacketizationMode);
    config2.setCameraId(kCameraId);
    config2.setCameraZoom(kCameraZoom);
    config2.setResolutionWidth(kResolutionWidth);
    config2.setResolutionHeight(kResolutionHeight);
    config2.setPauseImagePath(kPauseImagePath);
    config2.setDeviceOrientationDegree(kDeviceOrientationDegree);
    config2.setCvoValue(kCvoValue);
    config2.setRtcpFbType(kRtcpFbTypes);

    config3.setMediaDirection(kMediaDirection);
    config3.setRemoteAddress(kRemoteAddress);
    config3.setRemotePort(kRemotePort);
    config3.setRtcpConfig(rtcp);
    config3.setMaxMtuBytes(kMtu);
    config3.setDscp(kDscp);
    config3.setRxPayloadTypeNumber(kRxPayload);
    config3.setTxPayloadTypeNumber(kTxPayload);
    config3.setSamplingRateKHz(kSamplingRate);
    config3.setVideoMode(kVideoMode);
    config3.setCodecType(kCodecType);
    config3.setFramerate(20);
    config3.setBitrate(kBitrate);
    config3.setCodecProfile(kCodecProfile);
    config3.setCodecLevel(kCodecLevel);
    config3.setIntraFrameInterval(kIntraFrameIntervalSec);
    config3.setPacketizationMode(kPacketizationMode);
    config3.setCameraId(kCameraId);
    config3.setCameraZoom(kCameraZoom);
    config3.setResolutionWidth(kResolutionWidth);
    config3.setResolutionHeight(kResolutionHeight);
    config3.setPauseImagePath(kPauseImagePath);
    config3.setDeviceOrientationDegree(kDeviceOrientationDegree);
    config3.setCvoValue(kCvoValue);
    config3.setRtcpFbType(kRtcpFbTypes);

    EXPECT_NE(config2, config1);
    EXPECT_NE(config3, config1);
}
