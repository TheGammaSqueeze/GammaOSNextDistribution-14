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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.AccessNetworkConstants;
import android.telephony.ims.MediaQualityStatus;

import org.junit.Test;

public class MediaQualityStatusTest {

    private static final String TEST_IMS_CALL_SESSION_ID = "test";
    private static final int TEST_MEDIA_SESSION_TYPE = MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO;
    private static final int TEST_TRANSPORT_TYPE = AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
    private static final int THRESHOLD_PACKET_LOSS_RATE = 10;
    private static final int THRESHOLD_JITTER_MILLIS = 100;
    private static final long THRESHOLD_INACTIVITY_TIME_MILLIS = 7000;

    @Test
    public void testBuilderAndGetters() {
        MediaQualityStatus status = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);

        assertEquals(TEST_IMS_CALL_SESSION_ID, status.getCallSessionId());
        assertEquals(TEST_MEDIA_SESSION_TYPE, status.getMediaSessionType());
        assertEquals(TEST_TRANSPORT_TYPE, status.getTransportType());
        assertEquals(THRESHOLD_PACKET_LOSS_RATE, status.getRtpPacketLossRate());
        assertEquals(THRESHOLD_JITTER_MILLIS, status.getRtpJitterMillis());
        assertEquals(THRESHOLD_INACTIVITY_TIME_MILLIS, status.getRtpInactivityMillis());
    }

    @Test
    public void testEquals() {
        MediaQualityStatus status1 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);
        MediaQualityStatus status2 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);
        assertEquals(status1, status2);
    }

    @Test
    public void testNotEquals() {
        MediaQualityStatus status1 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);
        MediaQualityStatus status2 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID + "TEST",
                TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE,
                THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);
        assertNotEquals(status1, status2);

        status2 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE + 1,
                TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);
        assertNotEquals(status1, status2);

        status2 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE + 1,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);
        assertNotEquals(status1, status2);

        status2 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE + 10, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);
        assertNotEquals(status1, status2);

        status2 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS + 10,
                THRESHOLD_INACTIVITY_TIME_MILLIS);
        assertNotEquals(status1, status2);

        status2 = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS + 500);
        assertNotEquals(status1, status2);
    }

    @Test
    public void testParcel() {
        MediaQualityStatus status = new MediaQualityStatus(
                TEST_IMS_CALL_SESSION_ID, TEST_MEDIA_SESSION_TYPE, TEST_TRANSPORT_TYPE,
                THRESHOLD_PACKET_LOSS_RATE, THRESHOLD_JITTER_MILLIS,
                THRESHOLD_INACTIVITY_TIME_MILLIS);

        Parcel mediaQualityStatusParcel = Parcel.obtain();
        status.writeToParcel(mediaQualityStatusParcel, 0);
        mediaQualityStatusParcel.setDataPosition(0);

        MediaQualityStatus postParcel =
                MediaQualityStatus.CREATOR.createFromParcel(mediaQualityStatusParcel);
        assertEquals(status, postParcel);
    }
}
