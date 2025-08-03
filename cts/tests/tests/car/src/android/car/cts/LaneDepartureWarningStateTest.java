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
import android.car.hardware.property.LaneDepartureWarningState;

import org.junit.Test;

import java.util.List;

public class LaneDepartureWarningStateTest {

    @Test
    public void testToString() {
        assertThat(LaneDepartureWarningState.toString(LaneDepartureWarningState.OTHER))
                .isEqualTo("OTHER");
        assertThat(LaneDepartureWarningState.toString(LaneDepartureWarningState.NO_WARNING))
                .isEqualTo("NO_WARNING");
        assertThat(LaneDepartureWarningState.toString(LaneDepartureWarningState.WARNING_LEFT))
                .isEqualTo("WARNING_LEFT");
        assertThat(LaneDepartureWarningState.toString(LaneDepartureWarningState.WARNING_RIGHT))
                .isEqualTo("WARNING_RIGHT");
        assertThat(LaneDepartureWarningState.toString(4)).isEqualTo("0x4");
        assertThat(LaneDepartureWarningState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllLaneDepartureWarningStatesAreMappedInToString() {
        List<Integer> laneDepartureWarningStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                LaneDepartureWarningState.class);
        for (Integer laneDepartureWarningState : laneDepartureWarningStates) {
            String laneDepartureWarningStateString = LaneDepartureWarningState.toString(
                    laneDepartureWarningState);
            assertWithMessage("%s starts with 0x", laneDepartureWarningStateString).that(
                    laneDepartureWarningStateString.startsWith("0x")).isFalse();
        }
    }
}
