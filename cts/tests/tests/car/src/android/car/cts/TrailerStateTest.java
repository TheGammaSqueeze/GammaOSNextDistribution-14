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
import android.car.hardware.property.TrailerState;

import org.junit.Test;

import java.util.List;

public class TrailerStateTest {

    @Test
    public void testToString() {
        assertThat(TrailerState.toString(TrailerState.STATE_UNKNOWN))
                .isEqualTo("STATE_UNKNOWN");
        assertThat(TrailerState.toString(TrailerState.STATE_NOT_PRESENT))
                .isEqualTo("STATE_NOT_PRESENT");
        assertThat(TrailerState.toString(TrailerState.STATE_PRESENT))
                .isEqualTo("STATE_PRESENT");
        assertThat(TrailerState.toString(TrailerState.STATE_ERROR))
                .isEqualTo("STATE_ERROR");
        assertThat(TrailerState.toString(4)).isEqualTo("0x4");
        assertThat(TrailerState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllTrailerStatesAreMappedInToString() {
        List<Integer> trailerStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                TrailerState.class);
        for (Integer trailerState : trailerStates) {
            String trailerStateString = TrailerState.toString(trailerState);
            assertWithMessage("%s starts with 0x", trailerStateString).that(
                    trailerStateString.startsWith("0x")).isFalse();
        }
    }
}
