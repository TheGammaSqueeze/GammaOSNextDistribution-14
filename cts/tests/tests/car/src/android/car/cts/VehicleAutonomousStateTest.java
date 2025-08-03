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

import static android.car.feature.Flags.FLAG_ANDROID_VIC_VEHICLE_PROPERTIES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.hardware.property.VehicleAutonomousState;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class VehicleAutonomousStateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testToString() {
        assertThat(VehicleAutonomousState.toString(
                VehicleAutonomousState.LEVEL_0))
                .isEqualTo("LEVEL_0");
        assertThat(VehicleAutonomousState.toString(
                VehicleAutonomousState.LEVEL_1))
                .isEqualTo("LEVEL_1");
        assertThat(VehicleAutonomousState.toString(
                VehicleAutonomousState.LEVEL_2))
                .isEqualTo("LEVEL_2");
        assertThat(VehicleAutonomousState.toString(
                VehicleAutonomousState.LEVEL_3))
                .isEqualTo("LEVEL_3");
        assertThat(VehicleAutonomousState.toString(
                VehicleAutonomousState.LEVEL_4))
                .isEqualTo("LEVEL_4");
        assertThat(VehicleAutonomousState.toString(
                VehicleAutonomousState.LEVEL_5))
                .isEqualTo("LEVEL_5");
        assertThat(VehicleAutonomousState.toString(10)).isEqualTo("0xa");
        assertThat(VehicleAutonomousState.toString(12)).isEqualTo("0xc");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testAllVehicleAutonomousStatesAreMappedInToString() {
        List<Integer> vehicleAutonomousStates =
                VehiclePropertyUtils.getIntegersFromDataEnums(VehicleAutonomousState.class);
        for (Integer vehicleAutonomousState : vehicleAutonomousStates) {
            String vehicleAutonomousStateString = VehicleAutonomousState.toString(
                    vehicleAutonomousState);
            assertWithMessage("%s starts with 0x", vehicleAutonomousStateString).that(
                    vehicleAutonomousStateString.startsWith("0x")).isFalse();
        }
    }
}
