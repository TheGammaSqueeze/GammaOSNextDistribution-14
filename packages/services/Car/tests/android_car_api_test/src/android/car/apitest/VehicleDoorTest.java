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

import android.car.VehicleAreaDoor;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

@SmallTest
public final class VehicleDoorTest extends CarLessApiTestBase {

    @Test
    @ApiTest(apis = {"android.car.VehicleAreaDoor#DOOR_HOOD",
            "android.car.VehicleAreaDoor#DOOR_REAR", "android.car.VehicleAreaDoor#DOOR_ROW_1_LEFT",
            "android.car.VehicleAreaDoor#DOOR_ROW_1_RIGHT",
            "android.car.VehicleAreaDoor#DOOR_ROW_2_LEFT",
            "android.car.VehicleAreaDoor#DOOR_ROW_2_RIGHT",
            "android.car.VehicleAreaDoor#DOOR_ROW_3_LEFT",
            "android.car.VehicleAreaDoor#DOOR_ROW_3_RIGHT"})
    public void testMatchWithVehicleHal() {
        assertThat(VehicleAreaDoor.DOOR_HOOD)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaDoor.HOOD);
        assertThat(VehicleAreaDoor.DOOR_REAR)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaDoor.REAR);
        assertThat(VehicleAreaDoor.DOOR_ROW_1_LEFT)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaDoor.ROW_1_LEFT);
        assertThat(VehicleAreaDoor.DOOR_ROW_1_RIGHT)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaDoor.ROW_1_RIGHT);
        assertThat(VehicleAreaDoor.DOOR_ROW_2_LEFT)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaDoor.ROW_2_LEFT);
        assertThat(VehicleAreaDoor.DOOR_ROW_2_RIGHT)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaDoor.ROW_2_RIGHT);
        assertThat(VehicleAreaDoor.DOOR_ROW_3_LEFT)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaDoor.ROW_3_LEFT);
        assertThat(VehicleAreaDoor.DOOR_ROW_3_RIGHT)
                .isEqualTo(android.hardware.automotive.vehicle.VehicleAreaDoor.ROW_3_RIGHT);
    }
}
