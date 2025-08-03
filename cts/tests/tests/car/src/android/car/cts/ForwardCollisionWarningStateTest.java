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
import android.car.hardware.property.ForwardCollisionWarningState;

import org.junit.Test;

import java.util.List;

public class ForwardCollisionWarningStateTest {

    @Test
    public void testToString() {
        assertThat(ForwardCollisionWarningState.toString(ForwardCollisionWarningState.OTHER))
                .isEqualTo("OTHER");
        assertThat(ForwardCollisionWarningState.toString(ForwardCollisionWarningState.NO_WARNING))
                .isEqualTo("NO_WARNING");
        assertThat(ForwardCollisionWarningState.toString(ForwardCollisionWarningState.WARNING))
                .isEqualTo("WARNING");
        assertThat(ForwardCollisionWarningState.toString(3)).isEqualTo("0x3");
        assertThat(ForwardCollisionWarningState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllForwardCollisionWarningStatesAreMappedInToString() {
        List<Integer> forwardCollisionWarningStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                ForwardCollisionWarningState.class);
        for (Integer forwardCollisionWarningState : forwardCollisionWarningStates) {
            String forwardCollisionWarningStateString = ForwardCollisionWarningState.toString(
                    forwardCollisionWarningState);
            assertWithMessage("%s starts with 0x", forwardCollisionWarningStateString).that(
                    forwardCollisionWarningStateString.startsWith("0x")).isFalse();
        }
    }
}
