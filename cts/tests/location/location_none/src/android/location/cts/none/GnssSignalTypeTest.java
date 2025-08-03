/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.location.cts.none;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.location.GnssSignalType;
import android.location.GnssStatus;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests fundamental functionality of {@link GnssSignalType} class. This includes writing
 * and reading from parcel, and verifying computed values and getters.
 */
@RunWith(AndroidJUnit4.class)
public class GnssSignalTypeTest {

    private static final double PRECISION = 0.0001;

    @Test
    public void testGetValues() {
        GnssSignalType signalType =
                GnssSignalType.create(GnssStatus.CONSTELLATION_GPS, 1575.42e6, "C");
        assertEquals(signalType.getConstellationType(), GnssStatus.CONSTELLATION_GPS);
        assertEquals(signalType.getCarrierFrequencyHz(), 1575.42e6, PRECISION);
        assertEquals(signalType.getCodeType(), "C");
    }

    @Test
    public void testDescribeContents() {
        GnssSignalType signalType =
                GnssSignalType.create(GnssStatus.CONSTELLATION_GPS, 1575.42e6, "C");
        assertEquals(signalType.describeContents(), 0);
    }

    @Test
    public void testWriteToParcel() {
        GnssSignalType signalType =
                GnssSignalType.create(GnssStatus.CONSTELLATION_GPS, 1575.42e6, "C");

        Parcel parcel = Parcel.obtain();
        signalType.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssSignalType fromParcel = GnssSignalType.CREATOR.createFromParcel(parcel);

        assertEquals(signalType, fromParcel);
    }

    @Test
    public void testEquals() {
        GnssSignalType signalType1 =
                GnssSignalType.create(GnssStatus.CONSTELLATION_GPS, 1575.42e6, "C");
        GnssSignalType signalType2 =
                GnssSignalType.create(GnssStatus.CONSTELLATION_GALILEO, 1575.42e6, "A");
        GnssSignalType signalType3 =
                GnssSignalType.create(GnssStatus.CONSTELLATION_GPS, 1575.42e6, "C");

        assertNotEquals(signalType1, signalType2);
        assertEquals(signalType1, signalType3);
    }
}
