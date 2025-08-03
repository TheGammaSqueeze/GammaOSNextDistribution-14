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
import android.car.hardware.property.EvStoppingMode;

import org.junit.Test;

import java.util.List;

public class EvStoppingModeTest {

    @Test
    public void testToString() {
        assertThat(EvStoppingMode.toString(EvStoppingMode.STATE_OTHER))
                .isEqualTo("STATE_OTHER");
        assertThat(EvStoppingMode.toString(EvStoppingMode.STATE_CREEP))
                .isEqualTo("STATE_CREEP");
        assertThat(EvStoppingMode.toString(EvStoppingMode.STATE_ROLL))
                .isEqualTo("STATE_ROLL");
        assertThat(EvStoppingMode.toString(EvStoppingMode.STATE_HOLD))
                .isEqualTo("STATE_HOLD");
        assertThat(EvStoppingMode.toString(4)).isEqualTo("0x4");
        assertThat(EvStoppingMode.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllEvStoppingModesAreMappedInToString() {
        List<Integer> evStoppingModes = VehiclePropertyUtils.getIntegersFromDataEnums(
                EvStoppingMode.class);
        for (Integer evStoppingMode : evStoppingModes) {
            String evStoppingModeString = EvStoppingMode.toString(evStoppingMode);
            assertWithMessage("string for mode %s must not start with 0x",
                    evStoppingModeString).that(evStoppingModeString.startsWith("0x")).isFalse();
        }
    }
}
