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
import android.car.hardware.property.AutomaticEmergencyBrakingState;

import org.junit.Test;

import java.util.List;

public class AutomaticEmergencyBrakingStateTest {

    @Test
    public void testToString() {
        assertThat(AutomaticEmergencyBrakingState.toString(AutomaticEmergencyBrakingState.OTHER))
                .isEqualTo("OTHER");
        assertThat(AutomaticEmergencyBrakingState.toString(AutomaticEmergencyBrakingState.ENABLED))
                .isEqualTo("ENABLED");
        assertThat(AutomaticEmergencyBrakingState.toString(
                AutomaticEmergencyBrakingState.ACTIVATED))
                .isEqualTo("ACTIVATED");
        assertThat(AutomaticEmergencyBrakingState.toString(
                AutomaticEmergencyBrakingState.USER_OVERRIDE))
                .isEqualTo("USER_OVERRIDE");
        assertThat(AutomaticEmergencyBrakingState.toString(4)).isEqualTo("0x4");
        assertThat(AutomaticEmergencyBrakingState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllAutomaticEmergencyBrakingStatesAreMappedInToString() {
        List<Integer> automaticEmergencyBrakingStates =
                VehiclePropertyUtils.getIntegersFromDataEnums(AutomaticEmergencyBrakingState.class);
        for (Integer automaticEmergencyBrakingState : automaticEmergencyBrakingStates) {
            String automaticEmergencyBrakingString = AutomaticEmergencyBrakingState.toString(
                    automaticEmergencyBrakingState);
            assertWithMessage("%s starts with 0x", automaticEmergencyBrakingString).that(
                    automaticEmergencyBrakingString.startsWith("0x")).isFalse();
        }
    }
}
