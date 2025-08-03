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

import android.os.Parcel;
import android.telephony.imsmedia.MediaQualityThreshold;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class MediaQualityThresholdTest {
    private static final int[] RTP_TIMEOUT = { 10000, 20000 };
    private static final int RTCP_TIMEOUT = 15000;
    private static final int RTP_HYSTERESIS_TIME = 3000;
    private static final int RTP_PACKET_LOSS_DURATION = 3000;
    private static final int[] PACKET_LOSS_RATE = { 1, 3 };
    private static final int[] JITTER_THRESHOLD = { 100, 200 };
    private static final boolean NOTIFY_STATUS = false;
    private static final int VIDEO_BITRATE_BPS = 100000;

    @Test
    public void testConstructorAndGetters() {
        MediaQualityThreshold threshold = createMediaQualityThreshold();
        assertThat(Arrays.equals(threshold.getRtpInactivityTimerMillis(), RTP_TIMEOUT)).isTrue();
        assertThat(threshold.getRtcpInactivityTimerMillis()).isEqualTo(RTCP_TIMEOUT);
        assertThat(threshold.getRtpHysteresisTimeInMillis()).isEqualTo(RTP_HYSTERESIS_TIME);
        assertThat(threshold.getRtpPacketLossDurationMillis()).isEqualTo(RTP_PACKET_LOSS_DURATION);
        assertThat(Arrays.equals(threshold.getRtpPacketLossRate(), PACKET_LOSS_RATE)).isTrue();
        assertThat(Arrays.equals(threshold.getRtpJitterMillis(), JITTER_THRESHOLD)).isTrue();
        assertThat(threshold.getNotifyCurrentStatus()).isEqualTo(NOTIFY_STATUS);
        assertThat(threshold.getVideoBitrateBps()).isEqualTo(VIDEO_BITRATE_BPS);
    }

    @Test
    public void testParcel() {
        MediaQualityThreshold threshold = createMediaQualityThreshold();

        Parcel parcel = Parcel.obtain();
        threshold.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        MediaQualityThreshold parcelConfig = MediaQualityThreshold.CREATOR.createFromParcel(parcel);
        assertThat(threshold).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        MediaQualityThreshold threshold1 = createMediaQualityThreshold();
        MediaQualityThreshold threshold2 = createMediaQualityThreshold();

        assertThat(threshold1).isEqualTo(threshold2);
    }

    @Test
    public void testNotEqual() {
        MediaQualityThreshold threshold1 = createMediaQualityThreshold();

        MediaQualityThreshold threshold2 = new MediaQualityThreshold.Builder()
                .setRtpInactivityTimerMillis(RTP_TIMEOUT)
                .setRtcpInactivityTimerMillis(RTCP_TIMEOUT)
                .setRtpHysteresisTimeInMillis(RTP_HYSTERESIS_TIME + 1)
                .setRtpPacketLossDurationMillis(RTP_PACKET_LOSS_DURATION)
                .setRtpPacketLossRate(PACKET_LOSS_RATE)
                .setRtpJitterMillis(JITTER_THRESHOLD)
                .setNotifyCurrentStatus(NOTIFY_STATUS)
                .setVideoBitrateBps(VIDEO_BITRATE_BPS)
                .build();

        assertThat(threshold1).isNotEqualTo(threshold2);

        MediaQualityThreshold threshold3 = new MediaQualityThreshold.Builder()
                .setRtpInactivityTimerMillis(RTP_TIMEOUT)
                .setRtcpInactivityTimerMillis(RTCP_TIMEOUT)
                .setRtpHysteresisTimeInMillis(RTP_HYSTERESIS_TIME)
                .setRtpPacketLossDurationMillis(RTP_PACKET_LOSS_DURATION + 100)
                .setRtpPacketLossRate(PACKET_LOSS_RATE)
                .setRtpJitterMillis(JITTER_THRESHOLD)
                .setNotifyCurrentStatus(NOTIFY_STATUS)
                .setVideoBitrateBps(VIDEO_BITRATE_BPS)
                .build();

        assertThat(threshold1).isNotEqualTo(threshold3);

        MediaQualityThreshold threshold4 = new MediaQualityThreshold.Builder()
                .setRtpInactivityTimerMillis(RTP_TIMEOUT)
                .setRtcpInactivityTimerMillis(RTCP_TIMEOUT + 1)
                .setRtpHysteresisTimeInMillis(RTP_HYSTERESIS_TIME)
                .setRtpPacketLossDurationMillis(RTP_PACKET_LOSS_DURATION)
                .setRtpPacketLossRate(PACKET_LOSS_RATE)
                .setRtpJitterMillis(JITTER_THRESHOLD)
                .setNotifyCurrentStatus(NOTIFY_STATUS)
                .setVideoBitrateBps(VIDEO_BITRATE_BPS)
                .build();

        assertThat(threshold1).isNotEqualTo(threshold4);

        int[] testRtpTimeout = { 10, 25 };
        MediaQualityThreshold threshold5 = new MediaQualityThreshold.Builder()
                .setRtpInactivityTimerMillis(testRtpTimeout)
                .setRtcpInactivityTimerMillis(RTCP_TIMEOUT)
                .setRtpHysteresisTimeInMillis(RTP_HYSTERESIS_TIME)
                .setRtpPacketLossDurationMillis(RTP_PACKET_LOSS_DURATION)
                .setRtpPacketLossRate(PACKET_LOSS_RATE)
                .setRtpJitterMillis(JITTER_THRESHOLD)
                .setNotifyCurrentStatus(NOTIFY_STATUS)
                .setVideoBitrateBps(VIDEO_BITRATE_BPS)
                .build();

        assertThat(threshold1).isNotEqualTo(threshold5);
    }

    static MediaQualityThreshold createMediaQualityThreshold() {
        return new MediaQualityThreshold.Builder()
                .setRtpInactivityTimerMillis(RTP_TIMEOUT)
                .setRtcpInactivityTimerMillis(RTCP_TIMEOUT)
                .setRtpHysteresisTimeInMillis(RTP_HYSTERESIS_TIME)
                .setRtpPacketLossDurationMillis(RTP_PACKET_LOSS_DURATION)
                .setRtpPacketLossRate(PACKET_LOSS_RATE)
                .setRtpJitterMillis(JITTER_THRESHOLD)
                .setNotifyCurrentStatus(NOTIFY_STATUS)
                .setVideoBitrateBps(VIDEO_BITRATE_BPS)
                .build();
    }

    static MediaQualityThreshold createMediaQualityThresholdForHal() {
        return new MediaQualityThreshold.Builder()
                .setRtpInactivityTimerMillis(RTP_TIMEOUT)
                .setRtcpInactivityTimerMillis(RTCP_TIMEOUT)
                .setRtpHysteresisTimeInMillis(RTP_HYSTERESIS_TIME)
                .setRtpPacketLossDurationMillis(RTP_PACKET_LOSS_DURATION)
                .setRtpPacketLossRate(PACKET_LOSS_RATE)
                .setRtpJitterMillis(JITTER_THRESHOLD)
                .setNotifyCurrentStatus(NOTIFY_STATUS)
                .setVideoBitrateBps(0)
                .build();
    }
}
