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
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests fundamental functionality of {@link GnssExcessPathInfo}. This includes writing and reading
 * from parcel, and verifying setters.
 */
@RunWith(AndroidJUnit4.class)
public class GnssExcessPathInfoTest {

    private static final double DELTA = 0.000001;

    @Test
    public void testGetValues() {
        GnssExcessPathInfo GnssExcessPathInfo = createTestGnssExcessPathInfo();
        verifyTestValues(GnssExcessPathInfo);
    }

    @Test
    public void testWriteToParcel() {
        GnssExcessPathInfo gnssExcessPathInfo = createTestGnssExcessPathInfo();
        Parcel parcel = Parcel.obtain();
        gnssExcessPathInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssExcessPathInfo newGnssExcessPathInfo =
                GnssExcessPathInfo.CREATOR.createFromParcel(parcel);
        verifyTestValues(newGnssExcessPathInfo);
        parcel.recycle();
    }

    @Test
    public void testClear() {
        GnssExcessPathInfo.Builder builder = createTestGnssExcessPathInfoBuilder();
        builder.clearExcessPathLengthMeters();
        builder.clearExcessPathLengthUncertaintyMeters();
        builder.setReflectingPlane(null);
        builder.clearAttenuationDb();
        GnssExcessPathInfo gnssExcessPathInfo = builder.build();

        assertFalse(gnssExcessPathInfo.hasExcessPathLength());
        assertFalse(gnssExcessPathInfo.hasExcessPathLengthUncertainty());
        assertFalse(gnssExcessPathInfo.hasReflectingPlane());
        assertFalse(gnssExcessPathInfo.hasAttenuation());
    }

    private static GnssExcessPathInfo.Builder createTestGnssExcessPathInfoBuilder() {
        return new GnssExcessPathInfo.Builder()
                .setExcessPathLengthMeters(10.5f)
                .setExcessPathLengthUncertaintyMeters(5.2f)
                .setReflectingPlane(
                        GnssSingleSatCorrectionTest.createTestReflectingPlane())
                .setAttenuationDb(2.9f);
    }

    static GnssExcessPathInfo createTestGnssExcessPathInfo() {
        return createTestGnssExcessPathInfoBuilder().build();
    }

    static void verifyTestValues(GnssExcessPathInfo gnssExcessPathInfo) {
        assertTrue(gnssExcessPathInfo.hasExcessPathLength());
        assertTrue(gnssExcessPathInfo.hasExcessPathLengthUncertainty());
        assertTrue(gnssExcessPathInfo.hasReflectingPlane());
        assertTrue(gnssExcessPathInfo.hasAttenuation());

        assertEquals(10.5f, gnssExcessPathInfo.getExcessPathLengthMeters(), DELTA);
        assertEquals(5.2f, gnssExcessPathInfo.getExcessPathLengthUncertaintyMeters(), DELTA);
        assertEquals(GnssSingleSatCorrectionTest.createTestReflectingPlane(),
                gnssExcessPathInfo.getReflectingPlane());
        assertEquals(2.9f, gnssExcessPathInfo.getAttenuationDb(), DELTA);
    }
}
