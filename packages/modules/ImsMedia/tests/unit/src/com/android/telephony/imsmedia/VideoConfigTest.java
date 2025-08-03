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

package com.android.telephony.imsmedia;

import static com.google.common.truth.Truth.assertThat;

import android.net.InetAddresses;
import android.os.Parcel;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.imsmedia.RtcpConfig;
import android.telephony.imsmedia.RtpConfig;
import android.telephony.imsmedia.VideoConfig;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;

@RunWith(AndroidJUnit4.class)
public class VideoConfigTest {
    // RtcpConfig
    private static final String CANONICAL_NAME = "name";
    private static final int RTCP_PORT = 3333;
    private static final int RTCP_INTERVAL = 66;

    // VideoConfig
    private static final String REMOTE_RTP_ADDRESS = "122.22.22.22";
    private static final int REMOTE_RTP_PORT = 2222;
    private static final byte DSCP = 10;
    private static final int MAX_MTU_BYTES = 1524;
    private static final byte RX_PAYLOAD = 112;
    private static final byte TX_PAYLOAD = 122;
    private static final byte SAMPLING_RATE = 98;
    private static final int VIDEO_FRAMERATE = 15;
    private static final int VIDEO_BITRATE = 384;
    private static final int IDR_INTERVAL = 1;
    private static final int CAMERA_ID = 0;
    private static final int CAMERA_ZOOM = 10;
    private static final int RESOLUTION_WIDTH = 480;
    private static final int RESOLUTION_HEIGHT = 640;
    private static final String IMAGE_PATH =
            "data/user_de/0/com.android.telephony.imsmedia/test.jpg";
    private static final int CVO_VALUE = 1;
    private static final int DEVICE_ORIENTATION = 0;
    private static final int RTCP_FB_TYPES =
            VideoConfig.RTPFB_NACK | VideoConfig.RTPFB_TMMBR | VideoConfig.RTPFB_TMMBN;

    private static final RtcpConfig sRtcp = new RtcpConfig.Builder()
            .setCanonicalName(CANONICAL_NAME)
            .setTransmitPort(RTCP_PORT)
            .setIntervalSec(RTCP_INTERVAL)
            .setRtcpXrBlockTypes(RtcpConfig.FLAG_RTCPXR_DLRR_REPORT_BLOCK)
            .build();

    @Test
    public void testConstructorAndGetters() {
        VideoConfig config = createVideoConfig();
        assertThat(config.getMediaDirection()).isEqualTo(
                RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(config.getAccessNetwork()).isEqualTo(AccessNetworkType.EUTRAN);
        assertThat(config.getRemoteRtpAddress()).isEqualTo(new InetSocketAddress(
                InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT));
        assertThat(config.getRtcpConfig()).isEqualTo(sRtcp);
        assertThat(config.getDscp()).isEqualTo(DSCP);
        assertThat(config.getMaxMtuBytes()).isEqualTo(MAX_MTU_BYTES);
        assertThat(config.getRxPayloadTypeNumber()).isEqualTo(RX_PAYLOAD);
        assertThat(config.getTxPayloadTypeNumber()).isEqualTo(TX_PAYLOAD);
        assertThat(config.getSamplingRateKHz()).isEqualTo(SAMPLING_RATE);
        assertThat(config.getVideoMode()).isEqualTo(VideoConfig.VIDEO_MODE_RECORDING);
        assertThat(config.getCodecType()).isEqualTo(VideoConfig.VIDEO_CODEC_AVC);
        assertThat(config.getFramerate()).isEqualTo(VIDEO_FRAMERATE);
        assertThat(config.getBitrate()).isEqualTo(VIDEO_BITRATE);
        assertThat(config.getCodecProfile()).isEqualTo(VideoConfig.AVC_PROFILE_BASELINE);
        assertThat(config.getCodecLevel()).isEqualTo(VideoConfig.AVC_LEVEL_12);
        assertThat(config.getIntraFrameIntervalSec()).isEqualTo(IDR_INTERVAL);
        assertThat(config.getPacketizationMode()).isEqualTo(VideoConfig.MODE_NON_INTERLEAVED);
        assertThat(config.getCameraId()).isEqualTo(CAMERA_ID);
        assertThat(config.getCameraZoom()).isEqualTo(CAMERA_ZOOM);
        assertThat(config.getResolutionWidth()).isEqualTo(RESOLUTION_WIDTH);
        assertThat(config.getResolutionHeight()).isEqualTo(RESOLUTION_HEIGHT);
        assertThat(config.getPauseImagePath()).isEqualTo(IMAGE_PATH);
        assertThat(config.getDeviceOrientationDegree()).isEqualTo(DEVICE_ORIENTATION);
        assertThat(config.getCvoValue()).isEqualTo(CVO_VALUE);
        assertThat(config.getRtcpFbTypes()).isEqualTo(RTCP_FB_TYPES);
    }

    @Test
    public void testParcel() {
        VideoConfig config = createVideoConfig();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        VideoConfig parcelConfig = VideoConfig.CREATOR.createFromParcel(parcel);
        assertThat(config).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        VideoConfig config1 = createVideoConfig();
        VideoConfig config2 = createVideoConfig();
        assertThat(config1).isEqualTo(config2);
    }

    @Test
    public void testNotEqual() {
        VideoConfig config1 = createVideoConfig();

        VideoConfig config2 = new VideoConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(new InetSocketAddress(
                    InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT))
                .setRtcpConfig(sRtcp)
                .setMaxMtuBytes(MAX_MTU_BYTES)
                .setDscp(DSCP)
                .setRxPayloadTypeNumber(RX_PAYLOAD)
                .setTxPayloadTypeNumber(TX_PAYLOAD)
                .setSamplingRateKHz(SAMPLING_RATE)
                .setCodecType(VideoConfig.VIDEO_MODE_RECORDING)
                .setCodecType(VideoConfig.VIDEO_CODEC_HEVC)
                .setFramerate(VIDEO_FRAMERATE)
                .setBitrate(VIDEO_BITRATE)
                .setCodecProfile(VideoConfig.AVC_PROFILE_BASELINE)
                .setCodecLevel(VideoConfig.AVC_LEVEL_12)
                .setIntraFrameIntervalSec(IDR_INTERVAL)
                .setPacketizationMode(VideoConfig.MODE_NON_INTERLEAVED)
                .setCameraId(CAMERA_ID)
                .setCameraZoom(CAMERA_ZOOM)
                .setResolutionWidth(RESOLUTION_WIDTH)
                .setResolutionHeight(RESOLUTION_HEIGHT)
                .setPauseImagePath(IMAGE_PATH)
                .setDeviceOrientationDegree(DEVICE_ORIENTATION)
                .setCvoValue(CVO_VALUE)
                .setRtcpFbTypes(RTCP_FB_TYPES)
                .build();

        assertThat(config1).isNotEqualTo(config2);
    }

    static VideoConfig createVideoConfig() {
        return new VideoConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(new InetSocketAddress(
                    InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT))
                .setRtcpConfig(sRtcp)
                .setMaxMtuBytes(MAX_MTU_BYTES)
                .setDscp(DSCP)
                .setRxPayloadTypeNumber(RX_PAYLOAD)
                .setTxPayloadTypeNumber(TX_PAYLOAD)
                .setSamplingRateKHz(SAMPLING_RATE)
                .setVideoMode(VideoConfig.VIDEO_MODE_RECORDING)
                .setCodecType(VideoConfig.VIDEO_CODEC_AVC)
                .setFramerate(VIDEO_FRAMERATE)
                .setBitrate(VIDEO_BITRATE)
                .setCodecProfile(VideoConfig.AVC_PROFILE_BASELINE)
                .setCodecLevel(VideoConfig.AVC_LEVEL_12)
                .setIntraFrameIntervalSec(IDR_INTERVAL)
                .setPacketizationMode(VideoConfig.MODE_NON_INTERLEAVED)
                .setCameraId(CAMERA_ID)
                .setCameraZoom(CAMERA_ZOOM)
                .setResolutionWidth(RESOLUTION_WIDTH)
                .setResolutionHeight(RESOLUTION_HEIGHT)
                .setPauseImagePath(IMAGE_PATH)
                .setDeviceOrientationDegree(DEVICE_ORIENTATION)
                .setCvoValue(CVO_VALUE)
                .setRtcpFbTypes(RTCP_FB_TYPES)
                .build();
    }
}
