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
import static org.junit.Assert.fail;

import android.os.Parcel;
import android.uwb.AngleMeasurement;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test of {@link AngleMeasurement}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AngleMeasurementTest {
    @Test
    public void testConstructs() {
        double radians = 0.1234;
        double errorRadians = 0.5678;
        double confidence = 0.5;

        AngleMeasurement measurement = new AngleMeasurement(radians, errorRadians, confidence);
        assertEquals(measurement.getRadians(), radians, 0);
        assertEquals(measurement.getErrorRadians(), errorRadians, 0);
        assertEquals(measurement.getConfidenceLevel(), confidence, 0);
    }

    @Test
    public void testInvalidRadians() {
        double radians = Math.PI + 0.01;
        double errorRadians = 0.5678;
        double confidence = 0.5;

        constructExpectFailure(radians, errorRadians, confidence);
        constructExpectFailure(-radians, errorRadians, confidence);
    }

    @Test
    public void testInvalidErrorRadians() {
        double radians = 0.1234;
        double confidence = 0.5;

        constructExpectFailure(radians, -0.01, confidence);
        constructExpectFailure(-radians, Math.PI + 0.01, confidence);
    }

    @Test
    public void testInvalidConfidence() {
        double radians = 0.1234;
        double errorRadians = 0.5678;

        constructExpectFailure(radians, errorRadians, -0.01);
        constructExpectFailure(radians, errorRadians, 1.01);
    }

    private void constructExpectFailure(double radians, double errorRadians, double confidence) {
        try {
            new AngleMeasurement(radians, errorRadians, confidence);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testParcel() {
        Parcel parcel = Parcel.obtain();
        AngleMeasurement measurement = UwbTestUtils.getAngleMeasurement();
        measurement.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AngleMeasurement fromParcel = AngleMeasurement.CREATOR.createFromParcel(parcel);
        assertEquals(measurement, fromParcel);
    }
}
