/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.location.cts.privileged;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.location.SatellitePvt;
import android.location.SatellitePvt.ClockInfo;
import android.location.SatellitePvt.PositionEcef;
import android.location.SatellitePvt.VelocityEcef;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SatellitePvtTest {

    private static final double DELTA = 0.000001;
    private static final PositionEcef POSITION_ECEF_M = new PositionEcef(1.0, 2.0, 3.0, 4.0);
    private static final VelocityEcef VELOCITY_ECEF_MPS = new VelocityEcef(5.0, 6.0, 7.0, 8.0);
    private static final ClockInfo CLOCK_INFO = new ClockInfo(9.0, 10.0, 11.0);

    /**
     * Test for {@link SatellitePvt#writeToParcel}.
     *
     * <p> The test creates a {@link SatellitePvt} object, writes it to parcel then creates it
     * back from the parcel and checks if the same object is created.
     */
    @Test
    public void testWriteToParcel() {
        SatellitePvt satellitePvt1 = createTestSatellitePvt(POSITION_ECEF_M, VELOCITY_ECEF_MPS,
                CLOCK_INFO, /*ionoDelayMeters=*/ 12.0,
                /*tropoDelayMeters=*/ 13.0,
                /* timeOfClock= */ 1234,
                /* timeOfEphemeris= */ 2345,
                /* issueOfDataClock= */ 45,
                /* issueOfDataEphemeris= */ 234,
                /* ephemerisSource= */ 2);
        Parcel parcel = Parcel.obtain();
        satellitePvt1.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SatellitePvt satellitePvt2 = SatellitePvt.CREATOR.createFromParcel(parcel);
        verifyTestValues(satellitePvt1, satellitePvt2);
        parcel.recycle();
    }

    private static void verifyTestValues(SatellitePvt satPvt1, SatellitePvt satPvt2) {
        assertEquals(satPvt1.getPositionEcef().getXMeters(),
                     satPvt2.getPositionEcef().getXMeters(), DELTA);
        assertEquals(satPvt1.getPositionEcef().getYMeters(),
                     satPvt2.getPositionEcef().getYMeters(), DELTA);
        assertEquals(satPvt1.getPositionEcef().getZMeters(),
                     satPvt2.getPositionEcef().getZMeters(), DELTA);
        assertEquals(satPvt1.getPositionEcef().getUreMeters(),
                     satPvt2.getPositionEcef().getUreMeters(), DELTA);
        assertEquals(satPvt1.getVelocityEcef().getXMetersPerSecond(),
                     satPvt2.getVelocityEcef().getXMetersPerSecond(), DELTA);
        assertEquals(satPvt1.getVelocityEcef().getYMetersPerSecond(),
                     satPvt2.getVelocityEcef().getYMetersPerSecond(), DELTA);
        assertEquals(satPvt1.getVelocityEcef().getZMetersPerSecond(),
                     satPvt2.getVelocityEcef().getZMetersPerSecond(), DELTA);
        assertEquals(satPvt1.getVelocityEcef().getUreRateMetersPerSecond(),
                     satPvt2.getVelocityEcef().getUreRateMetersPerSecond(), DELTA);
        assertEquals(satPvt1.getClockInfo().getHardwareCodeBiasMeters(),
                     satPvt2.getClockInfo().getHardwareCodeBiasMeters(), DELTA);
        assertEquals(satPvt1.getClockInfo().getTimeCorrectionMeters(),
                     satPvt2.getClockInfo().getTimeCorrectionMeters(), DELTA);
        assertEquals(satPvt1.getClockInfo().getClockDriftMetersPerSecond(),
                     satPvt2.getClockInfo().getClockDriftMetersPerSecond(), DELTA);
        assertEquals(satPvt1.getIonoDelayMeters(), satPvt2.getIonoDelayMeters(), DELTA);
        assertEquals(satPvt1.getTropoDelayMeters(), satPvt2.getTropoDelayMeters(), DELTA);

        assertTrue(satPvt1.hasTimeOfClockSeconds());
        assertTrue(satPvt2.hasTimeOfClockSeconds());
        assertEquals(satPvt1.getTimeOfClockSeconds(), satPvt2.getTimeOfClockSeconds());
        assertTrue(satPvt1.hasTimeOfEphemerisSeconds());
        assertTrue(satPvt2.hasTimeOfEphemerisSeconds());
        assertEquals(satPvt1.getTimeOfEphemerisSeconds(), satPvt2.getTimeOfEphemerisSeconds());
        assertTrue(satPvt1.hasIssueOfDataClock());
        assertTrue(satPvt2.hasIssueOfDataClock());
        assertEquals(satPvt1.getIssueOfDataClock(), satPvt2.getIssueOfDataClock());
        assertTrue(satPvt1.hasIssueOfDataEphemeris());
        assertTrue(satPvt2.hasIssueOfDataEphemeris());
        assertEquals(satPvt1.getIssueOfDataEphemeris(), satPvt2.getIssueOfDataEphemeris());
        assertEquals(satPvt1.getEphemerisSource(), satPvt2.getEphemerisSource());
    }

    private static SatellitePvt createTestSatellitePvt(
            PositionEcef positionEcef, VelocityEcef velocityEcef, ClockInfo clockInfo,
            double ionoDelayMeters, double tropoDelayMeters, int timeOfClock, int timeOfEphemeris,
            int issueOfDataClock, int issueOfDataEphemeris, int ephemerisSource) {
        return new SatellitePvt.Builder()
                .setPositionEcef(positionEcef)
                .setVelocityEcef(velocityEcef)
                .setClockInfo(clockInfo)
                .setIonoDelayMeters(ionoDelayMeters)
                .setTropoDelayMeters(tropoDelayMeters)
                .setTimeOfClockSeconds(timeOfClock)
                .setTimeOfEphemerisSeconds(timeOfEphemeris)
                .setIssueOfDataClock(issueOfDataClock)
                .setIssueOfDataEphemeris(issueOfDataEphemeris)
                .setEphemerisSource(ephemerisSource)
                .build();
    }
}
