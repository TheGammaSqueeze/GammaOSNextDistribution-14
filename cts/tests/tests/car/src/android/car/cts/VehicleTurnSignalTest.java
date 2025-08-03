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
import android.car.hardware.property.VehicleTurnSignal;

import org.junit.Test;

import java.util.List;

public final class VehicleTurnSignalTest {

    @Test
    public void testToString() {
        assertThat(VehicleTurnSignal.toString(VehicleTurnSignal.STATE_NONE))
                .isEqualTo("STATE_NONE");
        assertThat(VehicleTurnSignal.toString(VehicleTurnSignal.STATE_RIGHT))
                .isEqualTo("STATE_RIGHT");
        assertThat(VehicleTurnSignal.toString(VehicleTurnSignal.STATE_LEFT))
                .isEqualTo("STATE_LEFT");
        assertThat(VehicleTurnSignal.toString(3)).isEqualTo("0x3");
        assertThat(VehicleTurnSignal.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllVehicleTurnSignalsAreMappedInToString() {
        List<Integer> vehicleTurnSignals = VehiclePropertyUtils.getIntegersFromDataEnums(
                VehicleTurnSignal.class);
        for (Integer vehicleTurnSignal : vehicleTurnSignals) {
            String vehicleTurnSignalString = VehicleTurnSignal.toString(vehicleTurnSignal);
            assertWithMessage("%s starts with 0x", vehicleTurnSignalString).that(
                    vehicleTurnSignalString.startsWith("0x")).isFalse();
        }
    }
}
