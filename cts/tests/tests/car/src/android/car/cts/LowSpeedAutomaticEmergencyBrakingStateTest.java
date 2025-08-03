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
import android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class LowSpeedAutomaticEmergencyBrakingStateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testToString() {
        assertThat(LowSpeedAutomaticEmergencyBrakingState.toString(
                LowSpeedAutomaticEmergencyBrakingState.OTHER))
                .isEqualTo("OTHER");
        assertThat(LowSpeedAutomaticEmergencyBrakingState.toString(
                LowSpeedAutomaticEmergencyBrakingState.ENABLED))
                .isEqualTo("ENABLED");
        assertThat(LowSpeedAutomaticEmergencyBrakingState.toString(
                LowSpeedAutomaticEmergencyBrakingState.ACTIVATED))
                .isEqualTo("ACTIVATED");
        assertThat(LowSpeedAutomaticEmergencyBrakingState.toString(
                LowSpeedAutomaticEmergencyBrakingState.USER_OVERRIDE))
                .isEqualTo("USER_OVERRIDE");
        assertThat(LowSpeedAutomaticEmergencyBrakingState.toString(4)).isEqualTo("0x4");
        assertThat(LowSpeedAutomaticEmergencyBrakingState.toString(12)).isEqualTo("0xc");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testAllLowSpeedAutomaticEmergencyBrakingStatesAreMappedInToString() {
        List<Integer> lowSpeedAutomaticEmergencyBrakingStates = VehiclePropertyUtils
                .getIntegersFromDataEnums(LowSpeedAutomaticEmergencyBrakingState.class);
        for (Integer lowSpeedAutomaticEmergencyBrakingState
                : lowSpeedAutomaticEmergencyBrakingStates) {
            String lowSpeedAutomaticEmergencyBrakingStateString =
                    LowSpeedAutomaticEmergencyBrakingState.toString(
                    lowSpeedAutomaticEmergencyBrakingState);
            assertWithMessage("%s starts with 0x", lowSpeedAutomaticEmergencyBrakingStateString)
                    .that(lowSpeedAutomaticEmergencyBrakingStateString.startsWith("0x")).isFalse();
        }
    }
}
