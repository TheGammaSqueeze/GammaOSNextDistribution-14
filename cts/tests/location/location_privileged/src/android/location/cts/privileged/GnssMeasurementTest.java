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

package android.location.cts.privileged;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.CorrelationVector;
import android.location.GnssMeasurement;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GnssMeasurementTest {

    private static final Collection<CorrelationVector> TEST_CORRELATION_VECTORS =
            createTestCorrelationVectors();

    @Test
    public void testDescribeContents() {
        GnssMeasurement measurement = new GnssMeasurement();
        assertEquals(0, measurement.describeContents());
    }

    @Test
    public void testReset() {
        GnssMeasurement measurement = new GnssMeasurement();
        measurement.reset();
    }

    @Test
    public void testWriteToParcel() {
        GnssMeasurement measurement = new GnssMeasurement();
        setTestValues(measurement);
        Parcel parcel = Parcel.obtain();
        measurement.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssMeasurement newMeasurement = GnssMeasurement.CREATOR.createFromParcel(parcel);
        verifyTestValues(newMeasurement);
        parcel.recycle();
    }

    @Test
    public void testSet() {
        GnssMeasurement measurement = new GnssMeasurement();
        setTestValues(measurement);
        GnssMeasurement newMeasurement = new GnssMeasurement();
        newMeasurement.set(measurement);
        verifyTestValues(newMeasurement);
    }

    @Test
    public void testSetReset() {
        GnssMeasurement measurement = new GnssMeasurement();
        setTestValues(measurement);

        assertTrue(measurement.hasCorrelationVectors());
        measurement.resetCorrelationVectors();
        assertFalse(measurement.hasCorrelationVectors());
    }

    private static void setTestValues(GnssMeasurement measurement) {
        measurement.setCorrelationVectors(TEST_CORRELATION_VECTORS);
    }

    private static void verifyTestValues(GnssMeasurement measurement) {
        Collection<CorrelationVector> correlationVectors = measurement.getCorrelationVectors();
        assertArrayEquals(
                TEST_CORRELATION_VECTORS.toArray(
                        new CorrelationVector[TEST_CORRELATION_VECTORS.size()]),
                correlationVectors.toArray(new CorrelationVector[correlationVectors.size()]));
    }

    private static Collection<CorrelationVector> createTestCorrelationVectors() {
        Collection<CorrelationVector> correlationVectors = new ArrayList<>();
        correlationVectors.add(
                new CorrelationVector.Builder()
                        .setSamplingWidthMeters(30d)
                        .setSamplingStartMeters(10d)
                        .setFrequencyOffsetMetersPerSecond(10d)
                        .setMagnitude(new int[] {0, 5000, 10000, 5000, 0, 0, 3000, 0})
                        .build());
        correlationVectors.add(
                new CorrelationVector.Builder()
                        .setSamplingWidthMeters(30d)
                        .setSamplingStartMeters(-20d)
                        .setFrequencyOffsetMetersPerSecond(20d)
                        .setMagnitude(new int[] {0, 3000, 5000, 3000, 0, 0, 1000, 0})
                        .build());
        return correlationVectors;
    }
}
