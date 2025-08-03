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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import android.car.VehicleGear;
import android.car.cts.utils.VehiclePropertyUtils;

import org.junit.Test;

import java.util.List;

public class VehicleGearTest extends AbstractCarLessTestCase {

    private static final String TAG = VehicleGearTest.class.getSimpleName();

    /**
     * Test for {@link VehicleGear#toString()}
     */
    @Test
    public void testToString() {
        assertThat(VehicleGear.toString(VehicleGear.GEAR_UNKNOWN)).isEqualTo("GEAR_UNKNOWN");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_NEUTRAL)).isEqualTo("GEAR_NEUTRAL");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_REVERSE)).isEqualTo("GEAR_REVERSE");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_PARK)).isEqualTo("GEAR_PARK");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_DRIVE)).isEqualTo("GEAR_DRIVE");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_FIRST)).isEqualTo("GEAR_FIRST");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_SECOND)).isEqualTo("GEAR_SECOND");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_THIRD)).isEqualTo("GEAR_THIRD");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_FOURTH)).isEqualTo("GEAR_FOURTH");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_FIFTH)).isEqualTo("GEAR_FIFTH");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_SIXTH)).isEqualTo("GEAR_SIXTH");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_SEVENTH)).isEqualTo("GEAR_SEVENTH");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_EIGHTH)).isEqualTo("GEAR_EIGHTH");
        assertThat(VehicleGear.toString(VehicleGear.GEAR_NINTH)).isEqualTo("GEAR_NINTH");
        assertThat(VehicleGear.toString(3)).isEqualTo("0x3");
        assertThat(VehicleGear.toString(12)).isEqualTo("0xc");

    }

    /**
     * Test if all vehicle gears have a mapped string value.
     */
    @Test
    public void testAllGearsAreMappedInToString() {
        List<Integer> gears = VehiclePropertyUtils.getIntegersFromDataEnums(VehicleGear.class);
        for (int gear : gears) {
            String gearString = VehicleGear.toString(gear);
            assertThat(gearString.startsWith("0x")).isFalse();
        }
    }
}
