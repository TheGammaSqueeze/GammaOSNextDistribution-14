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

import android.car.hardware.property.VehicleHalStatusCode;

import org.junit.Test;

public class VehicleHalStatusCodeTest {
    @Test
    public void testToString() {
        assertThat(VehicleHalStatusCode.toString(VehicleHalStatusCode.STATUS_OK))
                .isEqualTo("STATUS_OK");
        assertThat(VehicleHalStatusCode.toString(VehicleHalStatusCode.STATUS_TRY_AGAIN))
                .isEqualTo("STATUS_TRY_AGAIN");
        assertThat(VehicleHalStatusCode.toString(VehicleHalStatusCode.STATUS_INVALID_ARG))
                .isEqualTo("STATUS_INVALID_ARG");
        assertThat(VehicleHalStatusCode.toString(VehicleHalStatusCode.STATUS_NOT_AVAILABLE))
                .isEqualTo("STATUS_NOT_AVAILABLE");
        assertThat(VehicleHalStatusCode.toString(VehicleHalStatusCode.STATUS_ACCESS_DENIED))
                .isEqualTo("STATUS_ACCESS_DENIED");
        assertThat(VehicleHalStatusCode.toString(VehicleHalStatusCode.STATUS_INTERNAL_ERROR))
                .isEqualTo("STATUS_INTERNAL_ERROR");
        assertThat(VehicleHalStatusCode.toString(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED))
                .isEqualTo("STATUS_NOT_AVAILABLE_DISABLED");
        assertThat(VehicleHalStatusCode.toString(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW))
                .isEqualTo("STATUS_NOT_AVAILABLE_SPEED_LOW");
        assertThat(VehicleHalStatusCode.toString(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH))
                .isEqualTo("STATUS_NOT_AVAILABLE_SPEED_HIGH");
        assertThat(VehicleHalStatusCode.toString(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_POOR_VISIBILITY))
                .isEqualTo("STATUS_NOT_AVAILABLE_POOR_VISIBILITY");
        assertThat(VehicleHalStatusCode.toString(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY))
                .isEqualTo("STATUS_NOT_AVAILABLE_SAFETY");
    }
}
