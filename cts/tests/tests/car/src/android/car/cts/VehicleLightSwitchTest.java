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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.hardware.property.VehicleLightSwitch;

import org.junit.Test;

import java.util.List;

public final class VehicleLightSwitchTest {

    @Test
    public void testToString() {
        assertThat(VehicleLightSwitch.toString(VehicleLightSwitch.STATE_OFF))
                .isEqualTo("STATE_OFF");
        assertThat(VehicleLightSwitch.toString(VehicleLightSwitch.STATE_ON))
                .isEqualTo("STATE_ON");
        assertThat(VehicleLightSwitch.toString(VehicleLightSwitch.STATE_DAYTIME_RUNNING))
                .isEqualTo("STATE_DAYTIME_RUNNING");
        assertThat(VehicleLightSwitch.toString(VehicleLightSwitch.STATE_AUTOMATIC))
                .isEqualTo("STATE_AUTOMATIC");
        assertThat(VehicleLightSwitch.toString(3)).isEqualTo("0x3");
        assertThat(VehicleLightSwitch.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllVehicleLightSwitchesAreMappedInToString() {
        List<Integer> vehicleLightSwitches = VehiclePropertyUtils.getIntegersFromDataEnums(
                VehicleLightSwitch.class);
        for (Integer vehicleLightSwitch : vehicleLightSwitches) {
            String vehicleLightSwitchString = VehicleLightSwitch.toString(vehicleLightSwitch);
            assertWithMessage("%s starts with 0x", vehicleLightSwitchString).that(
                    vehicleLightSwitchString.startsWith("0x")).isFalse();
        }
    }
}
