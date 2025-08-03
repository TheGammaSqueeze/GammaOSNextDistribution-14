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
import android.car.hardware.property.CrossTrafficMonitoringWarningState;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class CrossTrafficMonitoringWarningStateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testToString() {
        assertThat(CrossTrafficMonitoringWarningState.toString(
                CrossTrafficMonitoringWarningState.OTHER))
                .isEqualTo("OTHER");
        assertThat(CrossTrafficMonitoringWarningState.toString(
                CrossTrafficMonitoringWarningState.NO_WARNING))
                .isEqualTo("NO_WARNING");
        assertThat(CrossTrafficMonitoringWarningState.toString(
                CrossTrafficMonitoringWarningState.WARNING_FRONT_LEFT))
                .isEqualTo("WARNING_FRONT_LEFT");
        assertThat(CrossTrafficMonitoringWarningState.toString(
                CrossTrafficMonitoringWarningState.WARNING_FRONT_RIGHT))
                .isEqualTo("WARNING_FRONT_RIGHT");
        assertThat(CrossTrafficMonitoringWarningState.toString(
                CrossTrafficMonitoringWarningState.WARNING_FRONT_BOTH))
                .isEqualTo("WARNING_FRONT_BOTH");
        assertThat(CrossTrafficMonitoringWarningState.toString(
                CrossTrafficMonitoringWarningState.WARNING_REAR_LEFT))
                .isEqualTo("WARNING_REAR_LEFT");
        assertThat(CrossTrafficMonitoringWarningState.toString(
                CrossTrafficMonitoringWarningState.WARNING_REAR_RIGHT))
                .isEqualTo("WARNING_REAR_RIGHT");
        assertThat(CrossTrafficMonitoringWarningState.toString(
                CrossTrafficMonitoringWarningState.WARNING_REAR_BOTH))
                .isEqualTo("WARNING_REAR_BOTH");
        assertThat(CrossTrafficMonitoringWarningState.toString(8)).isEqualTo("0x8");
        assertThat(CrossTrafficMonitoringWarningState.toString(12)).isEqualTo("0xc");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testAllCrossTrafficMonitoringWarningStatesAreMappedInToString() {
        List<Integer> crossTrafficMonitoringWarningStates = VehiclePropertyUtils
                .getIntegersFromDataEnums(CrossTrafficMonitoringWarningState.class);
        for (Integer crossTrafficMonitoringWarningState : crossTrafficMonitoringWarningStates) {
            String crossTrafficMonitoringWarningStateString =
                    CrossTrafficMonitoringWarningState.toString(crossTrafficMonitoringWarningState);
            assertWithMessage("%s starts with 0x", crossTrafficMonitoringWarningStateString).that(
                    crossTrafficMonitoringWarningStateString.startsWith("0x")).isFalse();
        }
    }
}
