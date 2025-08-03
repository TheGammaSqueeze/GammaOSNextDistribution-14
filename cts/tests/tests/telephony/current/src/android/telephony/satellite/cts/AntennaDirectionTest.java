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

import org.junit.Test;

public class AntennaDirectionTest {
    private static final float X = 10.5f;
    private static final float Y = 11.2f;
    private static final float Z = 13.5f;

    @Test
    public void testConstructorsAndGetters() {
        AntennaDirection antennaDirection = new AntennaDirection(X, Y, Z);

        assertThat(antennaDirection.getX()).isEqualTo(X);
        assertThat(antennaDirection.getY()).isEqualTo(Y);
        assertThat(antennaDirection.getZ()).isEqualTo(Z);
    }

    @Test
    public void testEquals() {
        AntennaDirection antennaDirection = new AntennaDirection(X, Y, Z);
        AntennaDirection equalsAntennaDirection = new AntennaDirection(X, Y, Z);
        assertThat(antennaDirection).isEqualTo(equalsAntennaDirection);
    }

    @Test
    public void testNotEquals() {
        AntennaDirection antennaDirection = new AntennaDirection(X, Y, Z);
        AntennaDirection notEqualsAntennaDirection = new AntennaDirection(10.05f, 11.02f, 13.05f);
        assertThat(antennaDirection).isNotEqualTo(notEqualsAntennaDirection);
    }

    @Test
    public void testParcel() {
        AntennaDirection antennaDirection = new AntennaDirection(X, Y, Z);

        Parcel parcel = Parcel.obtain();
        antennaDirection.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AntennaDirection fromParcel = AntennaDirection.CREATOR.createFromParcel(parcel);
        assertThat(antennaDirection).isEqualTo(fromParcel);
    }

    @Test
    public void testGetX() {
        AntennaDirection antennaDirection = new AntennaDirection(X, Y, Z);
        assertThat(antennaDirection.getX()).isEqualTo(X);
    }

    @Test
    public void testGetY() {
        AntennaDirection antennaDirection = new AntennaDirection(X, Y, Z);
        assertThat(antennaDirection.getY()).isEqualTo(Y);
    }

    @Test
    public void testGetZ() {
        AntennaDirection antennaDirection = new AntennaDirection(X, Y, Z);
        assertThat(antennaDirection.getZ()).isEqualTo(Z);
    }
}
