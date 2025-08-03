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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.hardware.property.LocationCharacterization;

import org.junit.Test;

import java.util.List;

public class LocationCharacterizationTest {

    @Test
    public void testToString() {
        assertThat(LocationCharacterization.toString(LocationCharacterization.PRIOR_LOCATIONS))
                .isEqualTo("PRIOR_LOCATIONS");
        assertThat(LocationCharacterization.toString(LocationCharacterization.GYROSCOPE_FUSION))
                .isEqualTo("GYROSCOPE_FUSION");
        assertThat(LocationCharacterization.toString(LocationCharacterization.ACCELEROMETER_FUSION))
                .isEqualTo("ACCELEROMETER_FUSION");
        assertThat(LocationCharacterization.toString(LocationCharacterization.COMPASS_FUSION))
                .isEqualTo("COMPASS_FUSION");
        assertThat(LocationCharacterization.toString(LocationCharacterization.WHEEL_SPEED_FUSION))
                .isEqualTo("WHEEL_SPEED_FUSION");
        assertThat(LocationCharacterization.toString(
                LocationCharacterization.STEERING_ANGLE_FUSION))
                .isEqualTo("STEERING_ANGLE_FUSION");
        assertThat(LocationCharacterization.toString(LocationCharacterization.CAR_SPEED_FUSION))
                .isEqualTo("CAR_SPEED_FUSION");
        assertThat(LocationCharacterization.toString(LocationCharacterization.DEAD_RECKONED))
                .isEqualTo("DEAD_RECKONED");
        assertThat(LocationCharacterization.toString(LocationCharacterization.RAW_GNSS_ONLY))
                .isEqualTo("RAW_GNSS_ONLY");
        assertThat(LocationCharacterization.toString(0)).isEqualTo("0x0");
        assertThat(LocationCharacterization.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllLocationCharacterizationsAreMappedInToString() {
        List<Integer> locationCharacterizations =
                VehiclePropertyUtils.getIntegersFromDataEnums(LocationCharacterization.class);
        for (Integer locationCharacterization : locationCharacterizations) {
            String automaticEmergencyBrakingString = LocationCharacterization.toString(
                    locationCharacterization);
            assertWithMessage("%s starts with 0x", automaticEmergencyBrakingString).that(
                    automaticEmergencyBrakingString.startsWith("0x")).isFalse();
        }
    }

    @Test
    public void testNoLocationCharacterizationsOverlap() {
        List<Integer> locationCharacterizations =
                VehiclePropertyUtils.getIntegersFromDataEnums(LocationCharacterization.class);
        Integer sumOfBitFlags = 0;
        for (Integer locationCharacterization : locationCharacterizations) {
            assertWithMessage("LocationCharacterization values must not bit-wise overlap. "
                    + "Found value: " + locationCharacterization + ", whose bit values already "
                    + "exist in enum elsewhere.")
                    .that(sumOfBitFlags & locationCharacterization)
                    .isEqualTo(0);
            sumOfBitFlags += locationCharacterization;
        }
    }
}
