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
import android.car.hardware.property.EvRegenerativeBrakingState;

import org.junit.Test;

import java.util.List;

public final class EvRegenerativeBrakingStateTest extends AbstractCarLessTestCase {

    @Test
    public void testToString() {
        assertThat(EvRegenerativeBrakingState.toString(
                EvRegenerativeBrakingState.STATE_UNKNOWN)).isEqualTo("STATE_UNKNOWN");
        assertThat(EvRegenerativeBrakingState.toString(
                EvRegenerativeBrakingState.STATE_DISABLED)).isEqualTo(
                "STATE_DISABLED");
        assertThat(EvRegenerativeBrakingState.toString(
                EvRegenerativeBrakingState.STATE_PARTIALLY_ENABLED)).isEqualTo(
                "STATE_PARTIALLY_ENABLED");
        assertThat(EvRegenerativeBrakingState.toString(
                EvRegenerativeBrakingState.STATE_FULLY_ENABLED)).isEqualTo(
                "STATE_FULLY_ENABLED");
    }

    @Test
    public void testAllEvRegenerativeBrakingStatesAreMappedInToString() {
        List<Integer> evRegenerativeBrakingStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                EvRegenerativeBrakingState.class);
        for (Integer evRegenerativeBrakingState : evRegenerativeBrakingStates) {
            String evRegenerativeBrakingStateString = EvRegenerativeBrakingState.toString(
                    evRegenerativeBrakingState);
            assertWithMessage("%s starts with 0x", evRegenerativeBrakingStateString).that(
                    evRegenerativeBrakingStateString.startsWith("0x")).isFalse();
        }
    }
}
