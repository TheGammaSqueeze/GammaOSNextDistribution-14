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
import android.car.hardware.property.EmergencyLaneKeepAssistState;

import org.junit.Test;

import java.util.List;

public class EmergencyLaneKeepAssistStateTest {

    @Test
    public void testToString() {
        assertThat(EmergencyLaneKeepAssistState.toString(EmergencyLaneKeepAssistState.OTHER))
                .isEqualTo("OTHER");
        assertThat(EmergencyLaneKeepAssistState.toString(EmergencyLaneKeepAssistState.ENABLED))
                .isEqualTo("ENABLED");
        assertThat(EmergencyLaneKeepAssistState.toString(EmergencyLaneKeepAssistState.WARNING_LEFT))
                .isEqualTo("WARNING_LEFT");
        assertThat(EmergencyLaneKeepAssistState.toString(
                EmergencyLaneKeepAssistState.WARNING_RIGHT))
                .isEqualTo("WARNING_RIGHT");
        assertThat(EmergencyLaneKeepAssistState.toString(
                EmergencyLaneKeepAssistState.ACTIVATED_STEER_LEFT))
                .isEqualTo("ACTIVATED_STEER_LEFT");
        assertThat(EmergencyLaneKeepAssistState.toString(
                EmergencyLaneKeepAssistState.ACTIVATED_STEER_RIGHT))
                .isEqualTo("ACTIVATED_STEER_RIGHT");
        assertThat(EmergencyLaneKeepAssistState.toString(
                EmergencyLaneKeepAssistState.USER_OVERRIDE))
                .isEqualTo("USER_OVERRIDE");
        assertThat(EmergencyLaneKeepAssistState.toString(7))
                .isEqualTo("0x7");
        assertThat(EmergencyLaneKeepAssistState.toString(12))
                .isEqualTo("0xc");
    }

    @Test
    public void testAllEmergencyLaneKeepAssistStatesAreMappedInToString() {
        List<Integer> emergencyLaneKeepAssistStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                EmergencyLaneKeepAssistState.class);
        for (Integer emergencyLaneKeepAssistState : emergencyLaneKeepAssistStates) {
            String emergencyLaneKeepAssistStateString =
                    EmergencyLaneKeepAssistState.toString(emergencyLaneKeepAssistState);
            assertWithMessage("string for mode %s must not start with 0x",
                    emergencyLaneKeepAssistStateString).that(
                    emergencyLaneKeepAssistStateString.startsWith("0x")).isFalse();
        }
    }
}
