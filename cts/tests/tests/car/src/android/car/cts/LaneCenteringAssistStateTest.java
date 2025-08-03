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
import android.car.hardware.property.LaneCenteringAssistState;

import org.junit.Test;

import java.util.List;

public class LaneCenteringAssistStateTest {

    @Test
    public void testToString() {
        assertThat(LaneCenteringAssistState.toString(LaneCenteringAssistState.OTHER))
                .isEqualTo("OTHER");
        assertThat(LaneCenteringAssistState.toString(LaneCenteringAssistState.ENABLED))
                .isEqualTo("ENABLED");
        assertThat(LaneCenteringAssistState.toString(LaneCenteringAssistState.ACTIVATION_REQUESTED))
                .isEqualTo("ACTIVATION_REQUESTED");
        assertThat(LaneCenteringAssistState.toString(LaneCenteringAssistState.ACTIVATED))
                .isEqualTo("ACTIVATED");
        assertThat(LaneCenteringAssistState.toString(LaneCenteringAssistState.USER_OVERRIDE))
                .isEqualTo("USER_OVERRIDE");
        assertThat(LaneCenteringAssistState.toString(
                LaneCenteringAssistState.FORCED_DEACTIVATION_WARNING))
                .isEqualTo("FORCED_DEACTIVATION_WARNING");
        assertThat(LaneCenteringAssistState.toString(6)).isEqualTo("0x6");
        assertThat(LaneCenteringAssistState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllLaneCenteringAssistStatesAreMappedInToString() {
        List<Integer> laneCenteringAssistStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                LaneCenteringAssistState.class);
        for (Integer laneCenteringAssistState : laneCenteringAssistStates) {
            String laneCenteringAssistStateString = LaneCenteringAssistState.toString(
                    laneCenteringAssistState);
            assertWithMessage("%s starts with 0x", laneCenteringAssistStateString).that(
                    laneCenteringAssistStateString.startsWith("0x")).isFalse();
        }
    }
}
