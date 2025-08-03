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
import android.telephony.imsmedia.AmrParams;
import android.telephony.imsmedia.AnbrMode;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.EvsParams;
import android.telephony.imsmedia.RtcpConfig;
import android.telephony.imsmedia.RtpConfig;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;

@RunWith(AndroidJUnit4.class)
public class AudioConfigTest {
    // AmrParams
    private static final boolean OCTET_ALIGNED = true;
    private static final int MAX_REDUNDANCY_MILLIS = 1001;

    // EvsParams
    private static final byte CHANNEL_AWARE_MODE = 7;
    private static final boolean USE_HEADER_FULL_ONLY = true;

    // RtcpConfig
    private static final String CANONICAL_NAME = "name";
    private static final int RTCP_PORT = 3333;
    private static final int RTCP_INTERVAL = 66;

    // AnbrParam
    private static final int ANBR_UPLINK_MODE = 1;
    private static final int ANBR_DOWNLINK_MODE = 2;

    // AudioConfig
    private static final String REMOTE_RTP_ADDRESS = "122.22.22.22";
    private static final int REMOTE_RTP_PORT = 2222;
    private static final byte DSCP = 10;
    private static final byte RX_PAYLOAD = 112;
    private static final byte TX_PAYLOAD = 122;
    private static final byte SAMPLING_RATE = 98;
    private static final byte PTIME = 99;
    private static final int MAX_PTIME = 240;
    private static final boolean DTX_ENABLED = true;
    private static final byte DTMF_PAYLOAD = 126;
    private static final byte DTMF_SAMPLING_RATE = 127;

    private static final RtcpConfig rtcp = new RtcpConfig.Builder()
            .setCanonicalName(CANONICAL_NAME)
            .setTransmitPort(RTCP_PORT)
            .setIntervalSec(RTCP_INTERVAL)
            .setRtcpXrBlockTypes(RtcpConfig.FLAG_RTCPXR_DLRR_REPORT_BLOCK)
            .build();

    private static final EvsParams evs = new EvsParams.Builder()
            .setEvsbandwidth(EvsParams.EVS_SUPER_WIDE_BAND)
            .setEvsMode(EvsParams.EVS_MODE_7)
            .setChannelAwareMode(CHANNEL_AWARE_MODE)
            .setHeaderFullOnly(USE_HEADER_FULL_ONLY)
            .build();

    private static final AmrParams amr = new AmrParams.Builder()
            .setAmrMode(AmrParams.AMR_MODE_5)
            .setOctetAligned(OCTET_ALIGNED)
            .setMaxRedundancyMillis(MAX_REDUNDANCY_MILLIS)
            .build();

    private static final AnbrMode sAnbr = new AnbrMode.Builder()
            .setAnbrUplinkCodecMode(ANBR_UPLINK_MODE)
            .setAnbrDownlinkCodecMode(ANBR_DOWNLINK_MODE)
            .build();

    @Test
    public void testConstructorAndGetters() {
        AudioConfig config = createAudioConfig();

        assertThat(config.getMediaDirection()).isEqualTo(
                RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(config.getPtimeMillis()).isEqualTo(PTIME);
        assertThat(config.getMaxPtimeMillis()).isEqualTo(MAX_PTIME);
        assertThat(config.getDtxEnabled()).isEqualTo(DTX_ENABLED);
        assertThat(config.getCodecType()).isEqualTo(AudioConfig.CODEC_EVS);
        assertThat(config.getTxDtmfPayloadTypeNumber()).isEqualTo(DTMF_PAYLOAD);
        assertThat(config.getRxDtmfPayloadTypeNumber()).isEqualTo(DTMF_PAYLOAD);
        assertThat(config.getDtmfSamplingRateKHz()).isEqualTo(DTMF_SAMPLING_RATE);
        assertThat(config.getAmrParams()).isEqualTo(null);
        assertThat(config.getEvsParams()).isEqualTo(evs);
        assertThat(config.getAccessNetwork()).isEqualTo(AccessNetworkType.EUTRAN);
        assertThat(config.getRemoteRtpAddress()).isEqualTo(new InetSocketAddress(
                InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT));
        assertThat(config.getRtcpConfig()).isEqualTo(rtcp);
        assertThat(config.getRxPayloadTypeNumber()).isEqualTo(RX_PAYLOAD);
        assertThat(config.getTxPayloadTypeNumber()).isEqualTo(TX_PAYLOAD);
        assertThat(config.getSamplingRateKHz()).isEqualTo(SAMPLING_RATE);
        assertThat(config.getDscp()).isEqualTo(DSCP);
        assertThat(config.getAnbrMode()).isEqualTo(sAnbr);
    }

    @Test
    public void testParcel() {
        AudioConfig config = createAudioConfig();

        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AudioConfig parcelConfig = AudioConfig.CREATOR.createFromParcel(parcel);
        assertThat(config).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        AudioConfig config1 = createAudioConfig();

        AudioConfig config2 = createAudioConfig();

        assertThat(config1).isEqualTo(config2);
    }

    @Test
    public void testNotEqual() {
        AudioConfig config1 = createAudioConfig();

        AudioConfig config2 = new AudioConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(new InetSocketAddress(
                    InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT))
                .setRtcpConfig(rtcp)
                .setDscp(DSCP)
                .setRxPayloadTypeNumber(RX_PAYLOAD)
                .setTxPayloadTypeNumber(TX_PAYLOAD)
                .setSamplingRateKHz(SAMPLING_RATE)
                .setAnbrMode(sAnbr)
                .setPtimeMillis(PTIME)
                .setMaxPtimeMillis(MAX_PTIME)
                .setDtxEnabled(DTX_ENABLED)
                .setCodecType(AudioConfig.CODEC_EVS)
                .setTxDtmfPayloadTypeNumber(DTMF_PAYLOAD)
                .setRxDtmfPayloadTypeNumber(DTMF_PAYLOAD)
                .setDtmfSamplingRateKHz(DTMF_SAMPLING_RATE)
                .setAmrParams(amr)
                .setEvsParams(evs)
                .build();

        assertThat(config1).isNotEqualTo(config2);
    }

    static AudioConfig createAudioConfig() {
        return new AudioConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(new InetSocketAddress(
                    InetAddresses.parseNumericAddress(REMOTE_RTP_ADDRESS), REMOTE_RTP_PORT))
                .setRtcpConfig(rtcp)
                .setDscp(DSCP)
                .setRxPayloadTypeNumber(RX_PAYLOAD)
                .setTxPayloadTypeNumber(TX_PAYLOAD)
                .setSamplingRateKHz(SAMPLING_RATE)
                .setAnbrMode(sAnbr)
                .setPtimeMillis(PTIME)
                .setMaxPtimeMillis(MAX_PTIME)
                .setDtxEnabled(DTX_ENABLED)
                .setCodecType(AudioConfig.CODEC_EVS)
                .setTxDtmfPayloadTypeNumber(DTMF_PAYLOAD)
                .setRxDtmfPayloadTypeNumber(DTMF_PAYLOAD)
                .setDtmfSamplingRateKHz(DTMF_SAMPLING_RATE)
                .setEvsParams(evs)
                .build();
    }
}
