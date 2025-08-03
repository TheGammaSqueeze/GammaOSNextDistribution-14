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
import android.car.hardware.property.LowSpeedCollisionWarningState;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class LowSpeedCollisionWarningStateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testToString() {
        assertThat(LowSpeedCollisionWarningState.toString(
                LowSpeedCollisionWarningState.OTHER))
                .isEqualTo("OTHER");
        assertThat(LowSpeedCollisionWarningState.toString(
                LowSpeedCollisionWarningState.NO_WARNING))
                .isEqualTo("NO_WARNING");
        assertThat(LowSpeedCollisionWarningState.toString(
                LowSpeedCollisionWarningState.WARNING))
                .isEqualTo("WARNING");
        assertThat(LowSpeedCollisionWarningState.toString(3)).isEqualTo("0x3");
        assertThat(LowSpeedCollisionWarningState.toString(12)).isEqualTo("0xc");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testAllLowSpeedCollisionWarningStatesAreMappedInToString() {
        List<Integer> lowSpeedCollisionWarningStates =
                VehiclePropertyUtils.getIntegersFromDataEnums(LowSpeedCollisionWarningState.class);
        for (Integer lowSpeedCollisionWarningState : lowSpeedCollisionWarningStates) {
            String lowSpeedCollisionWarningStateString = LowSpeedCollisionWarningState.toString(
                    lowSpeedCollisionWarningState);
            assertWithMessage("%s starts with 0x", lowSpeedCollisionWarningStateString).that(
                    lowSpeedCollisionWarningStateString.startsWith("0x")).isFalse();
        }
    }
}
