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
import android.car.hardware.property.EvChargeState;

import org.junit.Test;

import java.util.List;

public final class EvChargeStateTest extends AbstractCarLessTestCase {

    @Test
    public void testToString() {
        assertThat(EvChargeState.toString(EvChargeState.STATE_UNKNOWN)).isEqualTo("STATE_UNKNOWN");
        assertThat(EvChargeState.toString(EvChargeState.STATE_CHARGING)).isEqualTo(
                "STATE_CHARGING");
        assertThat(EvChargeState.toString(EvChargeState.STATE_FULLY_CHARGED)).isEqualTo(
                "STATE_FULLY_CHARGED");
        assertThat(EvChargeState.toString(EvChargeState.STATE_NOT_CHARGING)).isEqualTo(
                "STATE_NOT_CHARGING");
        assertThat(EvChargeState.toString(EvChargeState.STATE_ERROR)).isEqualTo("STATE_ERROR");
    }

    @Test
    public void testAllEvChargeStatesAreMappedInToString() {
        List<Integer> evChargeStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                EvChargeState.class);
        for (Integer evChargeState : evChargeStates) {
            String evChargeStateString = EvChargeState.toString(evChargeState);
            assertWithMessage("%s starts with 0x", evChargeStateString).that(
                    evChargeStateString.startsWith("0x")).isFalse();
        }
    }
}
