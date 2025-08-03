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

package android.location.cts.none;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.location.GnssAutomaticGainControl;
import android.location.GnssStatus;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests fundamental functionality of {@link GnssAutomaticGainControl} class. This includes writing
 * and reading from parcel, and verifying computed values and getters.
 */
@RunWith(AndroidJUnit4.class)
public class GnssAutomaticGainControlTest {
    private static final float DELTA = 1e-3f;

    @Test
    public void testGetValues() {
        GnssAutomaticGainControl agc = new GnssAutomaticGainControl.Builder().setCarrierFrequencyHz(
                1575420000).setConstellationType(GnssStatus.CONSTELLATION_GPS).setLevelDb(
                3.5).build();
        assertEquals(GnssStatus.CONSTELLATION_GPS, agc.getConstellationType());
        assertEquals(1575420000, agc.getCarrierFrequencyHz());
        assertEquals(3.5, agc.getLevelDb(), DELTA);
    }

    @Test
    public void testDescribeContents() {
        GnssAutomaticGainControl agc = new GnssAutomaticGainControl.Builder().build();
        assertEquals(agc.describeContents(), 0);
    }

    @Test
    public void testWriteToParcel() {
        GnssAutomaticGainControl agc = new GnssAutomaticGainControl.Builder().setCarrierFrequencyHz(
                1575420000).setConstellationType(GnssStatus.CONSTELLATION_GPS).setLevelDb(
                3.5).build();

        Parcel parcel = Parcel.obtain();
        agc.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssAutomaticGainControl fromParcel = GnssAutomaticGainControl.CREATOR.createFromParcel(
                parcel);

        assertEquals(agc, fromParcel);
    }

    @Test
    public void testEquals() {
        GnssAutomaticGainControl agc1 =
                new GnssAutomaticGainControl.Builder().setCarrierFrequencyHz(
                        1575420000).setConstellationType(GnssStatus.CONSTELLATION_GPS).setLevelDb(
                        3.5).build();
        GnssAutomaticGainControl agc2 = new GnssAutomaticGainControl.Builder(agc1).build();
        GnssAutomaticGainControl agc3 =
                new GnssAutomaticGainControl.Builder().setCarrierFrequencyHz(
                        1602000000).setConstellationType(
                        GnssStatus.CONSTELLATION_GLONASS).setLevelDb(-2.8).build();

        assertEquals(agc1, agc2);
        assertNotEquals(agc1, agc3);
    }
}
