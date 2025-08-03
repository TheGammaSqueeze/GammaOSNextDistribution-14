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

package com.android.telephony.imsmedia.tests;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.imsmedia.RtpReceptionStats;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RtpReceptionStatsTest {
    private static final int INVALID_NUMBER = -1;
    private static final int RTP_TIMESTAMP = 100;
    private static final int RTCPSR_TIMESTAMP = 4390;
    private static final long RTCPSR_NTP_TIMESTAMP = Long.MAX_VALUE;
    private static final int JITTER_BUFFER_MS = 60;
    private static final int ROUND_TRIP_TIME_MS = 100;

    @Test
    public void testConstructorAndGetters() {
        RtpReceptionStats stats = createRtpReceptionStats();
        assertThat(stats.getRtpTimestamp()).isEqualTo(RTP_TIMESTAMP);
        assertThat(stats.getRtcpSrTimestamp()).isEqualTo(RTCPSR_TIMESTAMP);
        assertThat(stats.getRtcpSrNtpTimestamp()).isEqualTo(RTCPSR_NTP_TIMESTAMP);
        assertThat(stats.getJitterBufferMs()).isEqualTo(JITTER_BUFFER_MS);
        assertThat(stats.getRoundTripTimeMs()).isEqualTo(ROUND_TRIP_TIME_MS);
    }

    @Test
    public void testParcel() {
        RtpReceptionStats stats = createRtpReceptionStats();

        Parcel parcel = Parcel.obtain();
        stats.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        RtpReceptionStats parcelParams = RtpReceptionStats.CREATOR.createFromParcel(parcel);
        assertThat(stats).isEqualTo(parcelParams);
    }

    @Test
    public void testEqual() {
        RtpReceptionStats stats1 = createRtpReceptionStats();
        RtpReceptionStats stats2 = createRtpReceptionStats();

        assertThat(stats1).isEqualTo(stats2);
    }

    @Test
    public void testNotEqual() {
        RtpReceptionStats stats1 = createRtpReceptionStats();

        RtpReceptionStats stats2 = new RtpReceptionStats.Builder()
                .setRtpTimestamp(500)
                .setRtcpSrTimestamp(RTCPSR_TIMESTAMP)
                .setRtcpSrNtpTimestamp(RTCPSR_NTP_TIMESTAMP)
                .setJitterBufferMs(JITTER_BUFFER_MS)
                .setRoundTripTimeMs(ROUND_TRIP_TIME_MS)
                .build();

        assertThat(stats1).isNotEqualTo(stats2);

        RtpReceptionStats stats3 = new RtpReceptionStats.Builder()
                .setRtpTimestamp(RTP_TIMESTAMP)
                .setRtcpSrTimestamp(6)
                .setRtcpSrNtpTimestamp(RTCPSR_NTP_TIMESTAMP)
                .setJitterBufferMs(JITTER_BUFFER_MS)
                .setRoundTripTimeMs(ROUND_TRIP_TIME_MS)
                .build();

        assertThat(stats1).isNotEqualTo(stats3);

        RtpReceptionStats stats4 = new RtpReceptionStats.Builder()
                .setRtpTimestamp(RTP_TIMESTAMP)
                .setRtcpSrTimestamp(RTCPSR_TIMESTAMP)
                .setRtcpSrNtpTimestamp(10)
                .setJitterBufferMs(JITTER_BUFFER_MS)
                .setRoundTripTimeMs(ROUND_TRIP_TIME_MS)
                .build();

        assertThat(stats1).isNotEqualTo(stats4);

        RtpReceptionStats stats5 = new RtpReceptionStats.Builder()
                .setRtpTimestamp(RTP_TIMESTAMP)
                .setRtcpSrTimestamp(RTCPSR_TIMESTAMP)
                .setRtcpSrNtpTimestamp(RTCPSR_NTP_TIMESTAMP)
                .setJitterBufferMs(120)
                .setRoundTripTimeMs(ROUND_TRIP_TIME_MS)
                .build();

        assertThat(stats1).isNotEqualTo(stats5);

        RtpReceptionStats stats6 = new RtpReceptionStats.Builder()
                .setRtpTimestamp(RTP_TIMESTAMP)
                .setRtcpSrTimestamp(RTCPSR_TIMESTAMP)
                .setRtcpSrNtpTimestamp(RTCPSR_NTP_TIMESTAMP)
                .setJitterBufferMs(JITTER_BUFFER_MS)
                .setRoundTripTimeMs(200)
                .build();

        assertThat(stats1).isNotEqualTo(stats6);
    }

    /**
     * create the RtpReceptionStats instance
     * @return Returns the instance
     */
    public static RtpReceptionStats createRtpReceptionStats() {
        return new RtpReceptionStats.Builder()
                .setRtpTimestamp(RTP_TIMESTAMP)
                .setRtcpSrTimestamp(RTCPSR_TIMESTAMP)
                .setRtcpSrNtpTimestamp(RTCPSR_NTP_TIMESTAMP)
                .setJitterBufferMs(JITTER_BUFFER_MS)
                .setRoundTripTimeMs(ROUND_TRIP_TIME_MS)
                .build();
    }
}
