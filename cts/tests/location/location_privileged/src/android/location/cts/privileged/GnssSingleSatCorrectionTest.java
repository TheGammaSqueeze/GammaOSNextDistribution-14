/*
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

package android.location.cts.privileged;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.GnssExcessPathInfo;
import android.location.GnssReflectingPlane;
import android.location.GnssSingleSatCorrection;
import android.location.GnssStatus;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests fundamental functionality of {@link GnssSingleSatCorrection}. This includes writing and
 * reading from parcel, and verifying setters.
 */
@RunWith(AndroidJUnit4.class)
public class GnssSingleSatCorrectionTest {
    private static final double DELTA = 0.000001;

    @Test
    public void testGetValues() {
        GnssSingleSatCorrection gnssSingleSatCorrection = createTestSingleSatCorrection();
        verifyTestValues(gnssSingleSatCorrection);
    }

    @Test
    public void testWriteToParcel() {
        GnssSingleSatCorrection gnssSingleSatCorrection = createTestSingleSatCorrection();
        Parcel parcel = Parcel.obtain();
        gnssSingleSatCorrection.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssSingleSatCorrection newGnssSingleSatCorrection =
                GnssSingleSatCorrection.CREATOR.createFromParcel(parcel);
        verifyTestValues(newGnssSingleSatCorrection);
        assertEquals(newGnssSingleSatCorrection, gnssSingleSatCorrection);
        parcel.recycle();
    }

    @Test
    public void testWriteToParcelWithoutSomeOptionalFields() {
        GnssSingleSatCorrection object = new GnssSingleSatCorrection.Builder()
                .setConstellationType(GnssStatus.CONSTELLATION_GALILEO)
                .setSatelliteId(12)
                .setCarrierFrequencyHz(1575420000f)
                .setProbabilityLineOfSight(0.1f)
                .build();
        Parcel parcel = Parcel.obtain();
        object.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssSingleSatCorrection fromParcel =
                GnssSingleSatCorrection.CREATOR.createFromParcel(parcel);
        assertEquals(object, fromParcel);
        parcel.recycle();
    }

    @Test
    public void testClear() {
        GnssSingleSatCorrection.Builder builder = createTestSingleSatCorrectionBuilder();
        builder.clearProbabilityLineOfSight();
        builder.clearExcessPathLengthMeters();
        builder.clearExcessPathLengthUncertaintyMeters();
        builder.clearCombinedAttenuationDb();
        GnssSingleSatCorrection singleSatCorrection = builder.build();

        assertFalse(singleSatCorrection.hasValidSatelliteLineOfSight());
        assertFalse(singleSatCorrection.hasExcessPathLength());
        assertFalse(singleSatCorrection.hasExcessPathLengthUncertainty());
        assertFalse(singleSatCorrection.hasCombinedAttenuation());
    }

    private static GnssSingleSatCorrection.Builder createTestSingleSatCorrectionBuilder() {
        return new GnssSingleSatCorrection.Builder()
                        .setConstellationType(GnssStatus.CONSTELLATION_GALILEO)
                        .setSatelliteId(12)
                        .setCarrierFrequencyHz(1575420000f)
                        .setProbabilityLineOfSight(0.1f)
                        .setExcessPathLengthMeters(10.0f)
                        .setExcessPathLengthUncertaintyMeters(5.0f)
                        .setCombinedAttenuationDb(2.1f)
                        .setGnssExcessPathInfoList(List.of(GnssExcessPathInfoTest
                                .createTestGnssExcessPathInfo()));
    }

    static GnssSingleSatCorrection createTestSingleSatCorrection() {
        return createTestSingleSatCorrectionBuilder().build();
    }

    static GnssReflectingPlane createTestReflectingPlane() {
        GnssReflectingPlane.Builder reflectingPlane =
                new GnssReflectingPlane.Builder()
                        .setLatitudeDegrees(37.386052)
                        .setLongitudeDegrees(-122.083853)
                        .setAltitudeMeters(100.0)
                        .setAzimuthDegrees(123.0);
        return reflectingPlane.build();
    }

    private static void verifyTestValues(GnssSingleSatCorrection singleSatCorrection) {
        assertTrue(singleSatCorrection.hasValidSatelliteLineOfSight());
        assertTrue(singleSatCorrection.hasExcessPathLength());
        assertTrue(singleSatCorrection.hasExcessPathLengthUncertainty());
        assertTrue(singleSatCorrection.hasCombinedAttenuation());
        assertEquals(GnssStatus.CONSTELLATION_GALILEO,
                singleSatCorrection.getConstellationType());
        assertEquals(12, singleSatCorrection.getSatelliteId());
        assertEquals(1575420000f, singleSatCorrection.getCarrierFrequencyHz(), DELTA);
        assertEquals(0.1f, singleSatCorrection.getProbabilityLineOfSight(), DELTA);
        assertEquals(10.0f, singleSatCorrection.getExcessPathLengthMeters(), DELTA);
        assertEquals(5.0f, singleSatCorrection.getExcessPathLengthUncertaintyMeters(), DELTA);
        assertEquals(2.1f, singleSatCorrection.getCombinedAttenuationDb(), DELTA);
        List<GnssExcessPathInfo> gnssExcessPathInfos =
                singleSatCorrection.getGnssExcessPathInfoList();
        assertEquals(1, gnssExcessPathInfos.size());
        GnssExcessPathInfoTest.verifyTestValues(gnssExcessPathInfos.get(0));
    }
}
