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
import android.telephony.imsmedia.RtpContextParams;
import android.telephony.imsmedia.TextConfig;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;

@RunWith(AndroidJUnit4.class)
public class TextConfigTest {
    // RtcpConfig
    private static final String CANONICAL_NAME = "name";
    private static final int RTCP_PORT = 30001;
    private static final int RTCP_INTERVAL = 3;
    // TextConfig
    private static final String REMOTE_RTP_ADDRESS = "122.22.22.22";
    private static final int REMOTE_RTP_PORT = 30000;
    private static final byte DSCP = 10;
    private static final byte RX_PAYLOAD = 100;
    private static final byte TX_PAYLOAD = 100;
    private static final byte SAMPLING_RATE = 10;
    private static final int CODEC_TYPE = TextConfig.TEXT_T140_RED;
    private static final int BITRATE = 300;
    private static final byte REDUNDANT_PAYLOAD = 101;
    private static final byte REDUNDANT_LEVEL = 3;
    private static final boolean KEEP_REDUNDANT_LEVEL = true;
    private static final long SSRC = 3711990914L;
    private static final long TIMESTAMP = 3277637148L;
    private static final int SEQUENCE_NUMBER = 5231;

    private static final RtcpConfig sRtcp = new RtcpConfig.Builder()
            .setCanonicalName(CANONICAL_NAME)
            .setTransmitPort(RTCP_PORT)
            .setIntervalSec(RTCP_INTERVAL)
            .setRtcpXrBlockTypes(RtcpConfig.FLAG_RTCPXR_DLRR_REPORT_BLOCK)
            .build();

    private static final RtpContextParams sRtpContextParams = new RtpContextParams.Builder()
            .setSsrc(SSRC)
            .setTimestamp(TIMESTAMP)
            .setSequenceNumber(SEQUENCE_NUMBER)
            .build();

    @Test
    public void testConstructorAndGetters() {
        TextConfig config = createTextConfig();
        assertThat(config.getMediaDirection()).isEqualTo(
                RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(config.getAccessNetwork()).isEqualTo(AccessNetworkType.EUTRAN);
        assertThat(config.getRemoteRtpAddress()).isEqualTo(new InetSocketAddress(
                InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT));
        assertThat(config.getRtcpConfig()).isEqualTo(sRtcp);
        assertThat(config.getDscp()).isEqualTo(DSCP);
        assertThat(config.getRxPayloadTypeNumber()).isEqualTo(RX_PAYLOAD);
        assertThat(config.getTxPayloadTypeNumber()).isEqualTo(TX_PAYLOAD);
        assertThat(config.getSamplingRateKHz()).isEqualTo(SAMPLING_RATE);
        assertThat(config.getRtpContextParams()).isEqualTo(sRtpContextParams);
        assertThat(config.getCodecType()).isEqualTo(CODEC_TYPE);
        assertThat(config.getBitrate()).isEqualTo(BITRATE);
        assertThat(config.getRedundantPayload()).isEqualTo(REDUNDANT_PAYLOAD);
        assertThat(config.getRedundantLevel()).isEqualTo(REDUNDANT_LEVEL);
        assertThat(config.getKeepRedundantLevel()).isEqualTo(KEEP_REDUNDANT_LEVEL);
    }

    @Test
    public void testParcel() {
        TextConfig config = createTextConfig();

        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        TextConfig parcelConfig = TextConfig.CREATOR.createFromParcel(parcel);
        assertThat(config).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        TextConfig config1 = createTextConfig();
        TextConfig config2 = createTextConfig();
        assertThat(config1).isEqualTo(config2);
    }

    @Test
    public void testNotEqual() {
        TextConfig config1 = createTextConfig();

        TextConfig config2 = new TextConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(new InetSocketAddress(
                        InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT))
                .setRtcpConfig(sRtcp)
                .setDscp(DSCP)
                .setRxPayloadTypeNumber(RX_PAYLOAD)
                .setTxPayloadTypeNumber(TX_PAYLOAD)
                .setSamplingRateKHz(SAMPLING_RATE)
                .setRtpContextParams(sRtpContextParams)
                .setCodecType(TextConfig.TEXT_T140)
                .setBitrate(BITRATE)
                .setRedundantPayload(REDUNDANT_PAYLOAD)
                .setRedundantLevel(REDUNDANT_LEVEL)
                .setKeepRedundantLevel(KEEP_REDUNDANT_LEVEL)
                .build();

        assertThat(config1).isNotEqualTo(config2);

        TextConfig config3 = new TextConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(new InetSocketAddress(
                        InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT))
                .setRtcpConfig(sRtcp)
                .setDscp(DSCP)
                .setRxPayloadTypeNumber(RX_PAYLOAD)
                .setTxPayloadTypeNumber(TX_PAYLOAD)
                .setSamplingRateKHz(SAMPLING_RATE)
                .setCodecType(CODEC_TYPE)
                .setBitrate(BITRATE)
                .setRedundantPayload(REDUNDANT_PAYLOAD)
                .setRedundantLevel(REDUNDANT_LEVEL)
                .setKeepRedundantLevel(KEEP_REDUNDANT_LEVEL)
                .build();

        assertThat(config1).isNotEqualTo(config3);
    }

    static TextConfig createTextConfig() {
        return new TextConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(new InetSocketAddress(
                        InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT))
                .setRtcpConfig(sRtcp)
                .setDscp(DSCP)
                .setRxPayloadTypeNumber(RX_PAYLOAD)
                .setTxPayloadTypeNumber(TX_PAYLOAD)
                .setSamplingRateKHz(SAMPLING_RATE)
                .setRtpContextParams(sRtpContextParams)
                .setCodecType(CODEC_TYPE)
                .setBitrate(BITRATE)
                .setRedundantPayload(REDUNDANT_PAYLOAD)
                .setRedundantLevel(REDUNDANT_LEVEL)
                .setKeepRedundantLevel(KEEP_REDUNDANT_LEVEL)
                .build();
    }
}
