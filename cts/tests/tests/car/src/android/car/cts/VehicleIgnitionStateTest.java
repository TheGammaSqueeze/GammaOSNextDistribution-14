/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.car.VehicleIgnitionState;
import android.car.cts.utils.VehiclePropertyUtils;

import org.junit.Test;

import java.util.List;

public final class VehicleIgnitionStateTest extends AbstractCarLessTestCase {

    @Test
    public void testToString() {
        assertThat(VehicleIgnitionState.toString(VehicleIgnitionState.UNDEFINED))
                .isEqualTo("UNDEFINED");
        assertThat(VehicleIgnitionState.toString(VehicleIgnitionState.LOCK)).isEqualTo("LOCK");
        assertThat(VehicleIgnitionState.toString(VehicleIgnitionState.OFF)).isEqualTo("OFF");
        assertThat(VehicleIgnitionState.toString(VehicleIgnitionState.ACC)).isEqualTo("ACC");
        assertThat(VehicleIgnitionState.toString(VehicleIgnitionState.ON)).isEqualTo("ON");
        assertThat(VehicleIgnitionState.toString(VehicleIgnitionState.START)).isEqualTo("START");
        int unknownIgnitionState = 255;
        assertThat(VehicleIgnitionState.toString(unknownIgnitionState)).isEqualTo("0xff");
    }

    @Test
    public void testAllIgnitionStatesAreMappedInToString() {
        List<Integer> ignitionStates =
                VehiclePropertyUtils.getIntegersFromDataEnums(VehicleIgnitionState.class);
        for (Integer ignitionState : ignitionStates) {
            String ignitionStateString = VehicleIgnitionState.toString(ignitionState);
            assertWithMessage("%s starts with 0x", ignitionStateString)
                    .that(ignitionStateString.startsWith("0x")).isFalse();
        }
    }
}

