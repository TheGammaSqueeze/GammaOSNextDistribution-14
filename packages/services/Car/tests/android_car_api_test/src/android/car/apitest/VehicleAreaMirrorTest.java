/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.car.VehicleAreaMirror;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

@SmallTest
public final class VehicleAreaMirrorTest extends CarLessApiTestBase {

    @Test
    @ApiTest(apis = {"android.car.VehicleAreaMirror#MIRROR_DRIVER_CENTER",
            "android.car.VehicleAreaMirror#MIRROR_DRIVER_LEFT",
            "android.car.VehicleAreaMirror#MIRROR_DRIVER_RIGHT"})
    public void testMatchWithVehicleHal() {
        assertThat(VehicleAreaMirror.MIRROR_DRIVER_CENTER).isEqualTo(
                android.hardware.automotive.vehicle.VehicleAreaMirror.DRIVER_CENTER);
        assertThat(VehicleAreaMirror.MIRROR_DRIVER_LEFT)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaMirror.DRIVER_LEFT);
        assertThat(VehicleAreaMirror.MIRROR_DRIVER_RIGHT)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaMirror.DRIVER_RIGHT);
    }
}
