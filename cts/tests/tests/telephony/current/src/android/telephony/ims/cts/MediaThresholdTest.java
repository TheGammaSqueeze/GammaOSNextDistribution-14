/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.ims.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.ims.MediaThreshold;

import org.junit.Test;

public class MediaThresholdTest {

    private static final int[] THRESHOLD_PACKET_LOSS_RATE = {10, 20};
    private static final int[] THRESHOLD_JITTER_MILLIS = {100};
    private static final long[] THRESHOLD_INACTIVITY_TIME_MILLIS = {3000, 7000};
    private static final int[] THRESHOLD_PACKET_LOSS_RATE2 = {10, 30};
    private static final int[] THRESHOLD_JITTER_MILLIS2 = {110};
    private static final long[] THRESHOLD_INACTIVITY_TIME_MILLIS2 = {3700, 5700};
    private static final int[] THRESHOLD_PACKET_LOSS_RATE3 = {10, 110};
    private static final int[] THRESHOLD_JITTER_MILLIS3 = {-1, 100};
    private static final long[] THRESHOLD_INACTIVITY_TIME_MILLIS3 = {-1, 3000, 5000, 60500};

    @Test
    public void testBuilderAndGetters() {
        MediaThreshold status = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS)
                .build();

        assertArrayEquals(THRESHOLD_PACKET_LOSS_RATE, status.getThresholdsRtpPacketLossRate());
        assertArrayEquals(THRESHOLD_JITTER_MILLIS, status.getThresholdsRtpJitterMillis());
        assertArrayEquals(
                THRESHOLD_INACTIVITY_TIME_MILLIS, status.getThresholdsRtpInactivityTimeMillis());

        status = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE3)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS3)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS3)
                .build();

        assertArrayEquals(new int[]{10}, status.getThresholdsRtpPacketLossRate());
        assertArrayEquals(new int[]{100}, status.getThresholdsRtpJitterMillis());
        assertArrayEquals(new long[]{3000, 5000}, status.getThresholdsRtpInactivityTimeMillis());
    }

    @Test
    public void testEquals() {
        MediaThreshold status1 = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS)
                .build();
        MediaThreshold status2 = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS)
                .build();

        assertEquals(status1, status2);
    }

    @Test
    public void testNotEquals() {
        MediaThreshold status1 = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS)
                .build();
        MediaThreshold status2 = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE2)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS)
                .build();
        assertNotEquals(status1, status2);

        status2 = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS2)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS)
                .build();
        assertNotEquals(status1, status2);

        status2 = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS2)
                .build();
        assertNotEquals(status1, status2);
    }

    @Test
    public void testParcel() {
        MediaThreshold status = new MediaThreshold.Builder()
                .setThresholdsRtpPacketLossRate(THRESHOLD_PACKET_LOSS_RATE)
                .setThresholdsRtpJitterMillis(THRESHOLD_JITTER_MILLIS)
                .setThresholdsRtpInactivityTimeMillis(THRESHOLD_INACTIVITY_TIME_MILLIS)
                .build();

        Parcel thresholdParcel = Parcel.obtain();
        status.writeToParcel(thresholdParcel, 0);
        thresholdParcel.setDataPosition(0);

        MediaThreshold postParcel = MediaThreshold.CREATOR.createFromParcel(thresholdParcel);
        assertEquals(status, postParcel);
    }
}
