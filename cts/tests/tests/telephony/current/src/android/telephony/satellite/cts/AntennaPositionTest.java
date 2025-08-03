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

package android.telephony.satellite.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.satellite.AntennaDirection;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.SatelliteManager;

import org.junit.Test;

public class AntennaPositionTest {
    private static final AntennaDirection ANTENNA_DIRECTION =
            new AntennaDirection(1, 1, 1);
    private static final int SUGGESTED_HOLD_POSITION =
            SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT;

    @Test
    public void testConstructorsAndGetters() {
        AntennaPosition antennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SUGGESTED_HOLD_POSITION);

        assertThat(antennaPosition.getAntennaDirection()).isEqualTo(ANTENNA_DIRECTION);
        assertThat(antennaPosition.getSuggestedHoldPosition()).isEqualTo(SUGGESTED_HOLD_POSITION);
    }

    @Test
    public void testEquals() {
        AntennaPosition antennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SUGGESTED_HOLD_POSITION);
        AntennaPosition equalsAntennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SUGGESTED_HOLD_POSITION);

        assertThat(antennaPosition).isEqualTo(equalsAntennaPosition);
    }

    @Test
    public void testNotEquals() {
        AntennaPosition antennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SUGGESTED_HOLD_POSITION);
        AntennaPosition notEqualsAntennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT);
        assertThat(antennaPosition).isNotEqualTo(notEqualsAntennaPosition);
    }

    @Test
    public void testParcel() {
        AntennaPosition antennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SUGGESTED_HOLD_POSITION);

        Parcel parcel = Parcel.obtain();
        antennaPosition.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AntennaPosition fromParcel = AntennaPosition.CREATOR.createFromParcel(parcel);
        assertThat(antennaPosition).isEqualTo(fromParcel);
    }

    @Test
    public void testGetAntennaDirection() {
        AntennaPosition antennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SUGGESTED_HOLD_POSITION);

        assertThat(antennaPosition.getAntennaDirection()).isEqualTo(ANTENNA_DIRECTION);
    }

    @Test
    public void testGetSuggestedHoldPosition() {
        AntennaPosition antennaPosition = new AntennaPosition(ANTENNA_DIRECTION,
                SUGGESTED_HOLD_POSITION);

        assertThat(antennaPosition.getSuggestedHoldPosition()).isEqualTo(SUGGESTED_HOLD_POSITION);
    }
}
