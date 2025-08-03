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
import android.car.hardware.property.DriverDrowsinessAttentionState;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class DriverDrowsinessAttentionStateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testToString() {
        assertThat(
                DriverDrowsinessAttentionState.toString(DriverDrowsinessAttentionState.OTHER))
                .isEqualTo("OTHER");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_1_EXTREMELY_ALERT))
                .isEqualTo("KSS_RATING_1_EXTREMELY_ALERT");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_2_VERY_ALERT))
                .isEqualTo("KSS_RATING_2_VERY_ALERT");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_3_ALERT))
                .isEqualTo("KSS_RATING_3_ALERT");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_4_RATHER_ALERT))
                .isEqualTo("KSS_RATING_4_RATHER_ALERT");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_5_NEITHER_ALERT_NOR_SLEEPY))
                .isEqualTo("KSS_RATING_5_NEITHER_ALERT_NOR_SLEEPY");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_6_SOME_SLEEPINESS))
                .isEqualTo("KSS_RATING_6_SOME_SLEEPINESS");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_7_SLEEPY_NO_EFFORT))
                .isEqualTo("KSS_RATING_7_SLEEPY_NO_EFFORT");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_8_SLEEPY_SOME_EFFORT))
                .isEqualTo("KSS_RATING_8_SLEEPY_SOME_EFFORT");
        assertThat(
                DriverDrowsinessAttentionState.toString(
                        DriverDrowsinessAttentionState.KSS_RATING_9_VERY_SLEEPY))
                .isEqualTo("KSS_RATING_9_VERY_SLEEPY");
        assertThat(DriverDrowsinessAttentionState.toString(12)).isEqualTo("0xc");
        assertThat(DriverDrowsinessAttentionState.toString(21)).isEqualTo("0x15");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testAllDriverDrowsinessAttentionStatesAreMappedInToString() {
        List<Integer> driverDrowsinessAttentionStates =
                VehiclePropertyUtils.getIntegersFromDataEnums(DriverDrowsinessAttentionState.class);
        for (Integer driverDrowsinessAttentionState : driverDrowsinessAttentionStates) {
            String driverDrowsinessAttentionStateString = DriverDrowsinessAttentionState.toString(
                    driverDrowsinessAttentionState);
            assertWithMessage("%s starts with 0x", driverDrowsinessAttentionStateString).that(
                    driverDrowsinessAttentionStateString.startsWith("0x")).isFalse();
        }
    }
}
