/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car.apitest;

import static com.google.common.truth.Truth.assertThat;

import android.car.PortLocationType;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

@SmallTest
public final class PortLocationTypeTest extends CarLessApiTestBase {

    @Test
    @ApiTest(apis = {"android.car.PortLocationType#UNKNOWN",
            "android.car.PortLocationType#FRONT_LEFT", "android.car.PortLocationType#FRONT_RIGHT",
            "android.car.PortLocationType#REAR_RIGHT", "android.car.PortLocationType#REAR_LEFT",
            "android.car.PortLocationType#FRONT", "android.car.PortLocationType#REAR"})
    public void testMatchWithVehicleHal() {
        assertThat(PortLocationType.UNKNOWN)
                .isEqualTo(android.hardware.automotive.vehicle.PortLocationType.UNKNOWN);

        assertThat(PortLocationType.FRONT_LEFT)
                .isEqualTo(android.hardware.automotive.vehicle.PortLocationType.FRONT_LEFT);

        assertThat(PortLocationType.FRONT_RIGHT)
                .isEqualTo(android.hardware.automotive.vehicle.PortLocationType.FRONT_RIGHT);

        assertThat(PortLocationType.REAR_RIGHT)
                .isEqualTo(android.hardware.automotive.vehicle.PortLocationType.REAR_RIGHT);

        assertThat(PortLocationType.REAR_LEFT)
                .isEqualTo(android.hardware.automotive.vehicle.PortLocationType.REAR_LEFT);

        assertThat(PortLocationType.FRONT)
                .isEqualTo(android.hardware.automotive.vehicle.PortLocationType.FRONT);

        assertThat(PortLocationType.REAR)
                .isEqualTo(android.hardware.automotive.vehicle.PortLocationType.REAR);

    }
}
