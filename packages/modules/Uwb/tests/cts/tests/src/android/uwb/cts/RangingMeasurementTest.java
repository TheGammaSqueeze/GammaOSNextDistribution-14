/*
 * Copyright 2021 The Android Open Source Project
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

package android.uwb.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.RangingMeasurement;
import android.uwb.UwbAddress;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.dltdoa.DlTDoAMeasurement;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test of {@link RangingMeasurement}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RangingMeasurementTest {
    private static final int TEST_RSSI_DBM = -80;
    private static final int INVALID_RSSI_DBM = -129;

    @Test
    public void testBuilder() {
        int status = RangingMeasurement.RANGING_STATUS_SUCCESS;
        UwbAddress address = UwbTestUtils.getUwbAddress(false);
        long time = SystemClock.elapsedRealtimeNanos();
        AngleOfArrivalMeasurement angleMeasurement = UwbTestUtils.getAngleOfArrivalMeasurement();
        AngleOfArrivalMeasurement destinationAngleMeasurement =
                UwbTestUtils.getAngleOfArrivalMeasurement();
        DistanceMeasurement distanceMeasurement = UwbTestUtils.getDistanceMeasurement();
        int los = RangingMeasurement.NLOS;
        int measurementFocus = RangingMeasurement.MEASUREMENT_FOCUS_RANGE;

        int messageType = 0x02;
        int messageControl = 0x513;
        int blockIndex = 4;
        int roundIndex = 6;
        int nLoS = 40;
        long txTimestamp = 40_000L;
        long rxTimestamp = 50_000L;
        int anchorCfo = 433;
        int cfo = 0x56;
        long initiatorReplyTime = 100;
        long responderReplyTime = 200;
        int initiatorResponderTof = 400;
        byte[] anchorLocation = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        byte[] activeRangingRounds = new byte[]{0x01, 0x02};

        PersistableBundle dlTDoAMeasurement = new DlTDoAMeasurement.Builder()
                .setMessageType(messageType)
                .setMessageControl(messageControl)
                .setBlockIndex(blockIndex)
                .setRoundIndex(roundIndex)
                .setNLoS(nLoS)
                .setTxTimestamp(txTimestamp)
                .setRxTimestamp(rxTimestamp)
                .setAnchorCfo(anchorCfo)
                .setCfo(cfo)
                .setInitiatorReplyTime(initiatorReplyTime)
                .setResponderReplyTime(responderReplyTime)
                .setInitiatorResponderTof(initiatorResponderTof)
                .setAnchorLocation(anchorLocation)
                .setActiveRangingRounds(activeRangingRounds)
                .build()
                .toBundle();

        RangingMeasurement.Builder builder = new RangingMeasurement.Builder();

        builder.setStatus(status);
        tryBuild(builder, false);

        builder.setElapsedRealtimeNanos(time);
        tryBuild(builder, false);

        builder.setAngleOfArrivalMeasurement(angleMeasurement);
        tryBuild(builder, false);

        builder.setDestinationAngleOfArrivalMeasurement(destinationAngleMeasurement);
        tryBuild(builder, false);

        builder.setDistanceMeasurement(distanceMeasurement);
        tryBuild(builder, false);

        builder.setRssiDbm(TEST_RSSI_DBM);
        tryBuild(builder, false);

        builder.setRemoteDeviceAddress(address);
        tryBuild(builder, true);

        builder.setLineOfSight(los);
        tryBuild(builder, true);

        builder.setMeasurementFocus(measurementFocus);
        tryBuild(builder, true);

        builder.setRangingMeasurementMetadata(dlTDoAMeasurement);
        RangingMeasurement measurement = tryBuild(builder, true);

        assertEquals(status, measurement.getStatus());
        assertEquals(address, measurement.getRemoteDeviceAddress());
        assertEquals(time, measurement.getElapsedRealtimeNanos());
        assertEquals(angleMeasurement, measurement.getAngleOfArrivalMeasurement());
        assertEquals(destinationAngleMeasurement,
                measurement.getDestinationAngleOfArrivalMeasurement());
        assertEquals(distanceMeasurement, measurement.getDistanceMeasurement());
        assertEquals(los, measurement.getLineOfSight());
        assertEquals(measurementFocus, measurement.getMeasurementFocus());
        assertEquals(TEST_RSSI_DBM, measurement.getRssiDbm());
        assertEquals(dlTDoAMeasurement, measurement.getRangingMeasurementMetadata());
    }

    @Test
    public void testInvalidRssi() {
        RangingMeasurement.Builder builder = new RangingMeasurement.Builder();
        try {
            builder.setRssiDbm(INVALID_RSSI_DBM);
            fail("Expected RangingMeasurement.Builder.setRssiDbm() to fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid"));
        }
    }

    private RangingMeasurement tryBuild(RangingMeasurement.Builder builder,
            boolean expectSuccess) {
        RangingMeasurement measurement = null;
        try {
            measurement = builder.build();
            if (!expectSuccess) {
                fail("Expected RangingMeasurement.Builder.build() to fail");
            }
        } catch (IllegalStateException e) {
            if (expectSuccess) {
                fail("Expected DistanceMeasurement.Builder.build() to succeed");
            }
        }
        return measurement;
    }

    @Test
    public void testParcel() {
        Parcel parcel = Parcel.obtain();
        RangingMeasurement measurement = UwbTestUtils.getRangingMeasurement();
        measurement.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RangingMeasurement fromParcel = RangingMeasurement.CREATOR.createFromParcel(parcel);
        assertEquals(measurement, fromParcel);
    }
}
