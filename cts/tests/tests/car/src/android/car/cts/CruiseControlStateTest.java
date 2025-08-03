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
import android.car.hardware.property.CruiseControlState;

import org.junit.Test;

import java.util.List;

public class CruiseControlStateTest {

    @Test
    public void testToString() {
        assertThat(CruiseControlState.toString(CruiseControlState.OTHER))
                .isEqualTo("OTHER");
        assertThat(CruiseControlState.toString(CruiseControlState.ENABLED))
                .isEqualTo("ENABLED");
        assertThat(CruiseControlState.toString(CruiseControlState.ACTIVATED))
                .isEqualTo("ACTIVATED");
        assertThat(CruiseControlState.toString(CruiseControlState.USER_OVERRIDE))
                .isEqualTo("USER_OVERRIDE");
        assertThat(CruiseControlState.toString(CruiseControlState.SUSPENDED))
                .isEqualTo("SUSPENDED");
        assertThat(CruiseControlState.toString(CruiseControlState.FORCED_DEACTIVATION_WARNING))
                .isEqualTo("FORCED_DEACTIVATION_WARNING");
        assertThat(CruiseControlState.toString(6))
                .isEqualTo("0x6");
        assertThat(CruiseControlState.toString(12))
                .isEqualTo("0xc");
    }

    @Test
    public void testAllCruiseControlStatesAreMappedInToString() {
        List<Integer> cruiseControlStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                CruiseControlState.class);
        for (Integer cruiseControlState : cruiseControlStates) {
            String cruiseControlStateString =
                    CruiseControlState.toString(cruiseControlState);
            assertWithMessage("string for mode %s must not start with 0x",
                    cruiseControlStateString).that(
                    cruiseControlStateString.startsWith("0x")).isFalse();
        }
    }
}
