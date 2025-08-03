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
import android.car.hardware.property.ErrorState;

import org.junit.Test;

import java.util.List;

public class ErrorStateTest {

    @Test
    public void testToString() {
        assertThat(ErrorState.toString(ErrorState.OTHER_ERROR_STATE))
                .isEqualTo("OTHER_ERROR_STATE");
        assertThat(ErrorState.toString(ErrorState.NOT_AVAILABLE_DISABLED))
                .isEqualTo("NOT_AVAILABLE_DISABLED");
        assertThat(ErrorState.toString(ErrorState.NOT_AVAILABLE_SPEED_LOW))
                .isEqualTo("NOT_AVAILABLE_SPEED_LOW");
        assertThat(ErrorState.toString(ErrorState.NOT_AVAILABLE_SPEED_HIGH))
                .isEqualTo("NOT_AVAILABLE_SPEED_HIGH");
        assertThat(ErrorState.toString(ErrorState.NOT_AVAILABLE_POOR_VISIBILITY))
                .isEqualTo("NOT_AVAILABLE_POOR_VISIBILITY");
        assertThat(ErrorState.toString(ErrorState.NOT_AVAILABLE_SAFETY))
                .isEqualTo("NOT_AVAILABLE_SAFETY");
        assertThat(ErrorState.toString(3)).isEqualTo("0x3");
        assertThat(ErrorState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllErrorStatesAreMappedInToString() {
        List<Integer> errorStates = VehiclePropertyUtils.getIntegersFromDataEnums(ErrorState.class);
        for (Integer errorState : errorStates) {
            String errorStateString = ErrorState.toString(errorState);
            assertWithMessage("%s starts with 0x", errorStateString).that(
                    errorStateString.startsWith("0x")).isFalse();
        }
    }
}
