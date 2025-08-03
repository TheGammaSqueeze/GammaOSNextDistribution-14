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
import android.car.hardware.property.VehicleOilLevel;

import org.junit.Test;

import java.util.List;

public class VehicleOilLevelTest {

    @Test
    public void testToString() {
        assertThat(VehicleOilLevel.toString(VehicleOilLevel.LEVEL_CRITICALLY_LOW))
                .isEqualTo("LEVEL_CRITICALLY_LOW");
        assertThat(VehicleOilLevel.toString(VehicleOilLevel.LEVEL_LOW))
                .isEqualTo("LEVEL_LOW");
        assertThat(VehicleOilLevel.toString(VehicleOilLevel.LEVEL_NORMAL))
                .isEqualTo("LEVEL_NORMAL");
        assertThat(VehicleOilLevel.toString(VehicleOilLevel.LEVEL_HIGH))
                .isEqualTo("LEVEL_HIGH");
        assertThat(VehicleOilLevel.toString(VehicleOilLevel.LEVEL_ERROR))
                .isEqualTo("LEVEL_ERROR");
        assertThat(VehicleOilLevel.toString(5)).isEqualTo("0x5");
        assertThat(VehicleOilLevel.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllVehicleOilLevelsAreMappedInToString() {
        List<Integer> vehicleOilLevels = VehiclePropertyUtils.getIntegersFromDataEnums(
                VehicleOilLevel.class);
        for (Integer vehicleOilLevel : vehicleOilLevels) {
            String vehicleOilLevelString = VehicleOilLevel.toString(vehicleOilLevel);
            assertWithMessage("%s starts with 0x", vehicleOilLevelString).that(
                    vehicleOilLevelString.startsWith("0x")).isFalse();
        }
    }
}
