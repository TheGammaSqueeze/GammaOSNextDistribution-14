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

package com.android.telephony.imsmedia;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.imsmedia.MediaQualityStatus;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaQualityStatusTest {
    private static final int RTP_INACTIVITY_TIME = 10000;
    private static final int RTCP_INACTIVITY_TIME = 15000;
    private static final int RTP_PACKET_LOSS_RATE = 1;
    private static final int RTP_JITTER = 100;

    static MediaQualityStatus createMediaQualityStatus() {
        return new MediaQualityStatus.Builder()
                .setRtpInactivityTimeMillis(RTP_INACTIVITY_TIME)
                .setRtcpInactivityTimeMillis(RTCP_INACTIVITY_TIME)
                .setRtpPacketLossRate(RTP_PACKET_LOSS_RATE)
                .setRtpJitterMillis(RTP_JITTER)
                .build();
    }

    static android.hardware.radio.ims.media.MediaQualityStatus createHalMediaQualityStatus() {
        final android.hardware.radio.ims.media.MediaQualityStatus status =
                new android.hardware.radio.ims.media.MediaQualityStatus();
        status.rtpInactivityTimeMillis = RTP_INACTIVITY_TIME;
        status.rtcpInactivityTimeMillis = RTCP_INACTIVITY_TIME;
        status.rtpPacketLossRate = RTP_PACKET_LOSS_RATE;
        status.rtpJitterMillis = RTP_JITTER;
        return status;
    }

    @Test
    public void testConstructorAndGetters() {
        MediaQualityStatus status = createMediaQualityStatus();
        assertThat(status.getRtpInactivityTimeMillis()).isEqualTo(RTP_INACTIVITY_TIME);
        assertThat(status.getRtcpInactivityTimeMillis()).isEqualTo(RTCP_INACTIVITY_TIME);
        assertThat(status.getRtpPacketLossRate()).isEqualTo(RTP_PACKET_LOSS_RATE);
        assertThat(status.getRtpJitterMillis()).isEqualTo(RTP_JITTER);
    }

    @Test
    public void testParcel() {
        MediaQualityStatus status = createMediaQualityStatus();

        Parcel parcel = Parcel.obtain();
        status.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        MediaQualityStatus parcelConfig = MediaQualityStatus.CREATOR.createFromParcel(parcel);
        assertThat(status).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        MediaQualityStatus status1 = createMediaQualityStatus();
        MediaQualityStatus status2 = createMediaQualityStatus();
        assertThat(status1).isEqualTo(status2);
    }

    @Test
    public void testNotEqual() {
        MediaQualityStatus status1 = createMediaQualityStatus();
        MediaQualityStatus status2 = new MediaQualityStatus.Builder()
                .setRtpInactivityTimeMillis(RTP_INACTIVITY_TIME + 1)
                .setRtcpInactivityTimeMillis(RTCP_INACTIVITY_TIME)
                .setRtpPacketLossRate(RTP_PACKET_LOSS_RATE)
                .setRtpJitterMillis(RTP_JITTER)
                .build();

        assertThat(status1).isNotEqualTo(status2);

        MediaQualityStatus status3 = new MediaQualityStatus.Builder()
                .setRtpInactivityTimeMillis(RTP_INACTIVITY_TIME)
                .setRtcpInactivityTimeMillis(RTCP_INACTIVITY_TIME + 1)
                .setRtpPacketLossRate(RTP_PACKET_LOSS_RATE)
                .setRtpJitterMillis(RTP_JITTER)
                .build();

        assertThat(status1).isNotEqualTo(status3);

        MediaQualityStatus status4 = new MediaQualityStatus.Builder()
                .setRtpInactivityTimeMillis(RTP_INACTIVITY_TIME)
                .setRtcpInactivityTimeMillis(RTCP_INACTIVITY_TIME)
                .setRtpPacketLossRate(RTP_PACKET_LOSS_RATE + 1)
                .setRtpJitterMillis(RTP_JITTER)
                .build();

        assertThat(status1).isNotEqualTo(status4);

        MediaQualityStatus status5 = new MediaQualityStatus.Builder()
                .setRtpInactivityTimeMillis(RTP_INACTIVITY_TIME)
                .setRtcpInactivityTimeMillis(RTCP_INACTIVITY_TIME)
                .setRtpPacketLossRate(RTP_PACKET_LOSS_RATE)
                .setRtpJitterMillis(RTP_JITTER + 1)
                .build();

        assertThat(status1).isNotEqualTo(status5);
    }
}
