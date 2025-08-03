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

import android.car.FuelType;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

@SmallTest
public final class FuelTypeTest extends CarLessApiTestBase {

    @Test
    @ApiTest(apis = {"android.car.FuelType#UNKNOWN", "android.car.FuelType#UNLEADED",
            "android.car.FuelType#LEADED", "android.car.FuelType#DIESEL_1",
            "android.car.FuelType#DIESEL_2", "android.car.FuelType#BIODIESEL",
            "android.car.FuelType#E85", "android.car.FuelType#LPG", "android.car.FuelType#CNG",
            "android.car.FuelType#LNG", "android.car.FuelType#ELECTRIC",
            "android.car.FuelType#HYDROGEN", "android.car.FuelType#OTHER"})
    public void testMatchWithVehicleHal() {
        assertThat(FuelType.UNKNOWN)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_UNKNOWN);

        assertThat(FuelType.UNLEADED)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_UNLEADED);

        assertThat(FuelType.LEADED)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_LEADED);

        assertThat(FuelType.DIESEL_1)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_DIESEL_1);

        assertThat(FuelType.DIESEL_2)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_DIESEL_2);

        assertThat(FuelType.BIODIESEL)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_BIODIESEL);

        assertThat(FuelType.E85)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_E85);

        assertThat(FuelType.LPG)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_LPG);

        assertThat(FuelType.CNG)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_CNG);

        assertThat(FuelType.LNG)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_LNG);

        assertThat(FuelType.ELECTRIC)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_ELECTRIC);

        assertThat(FuelType.HYDROGEN)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_HYDROGEN);

        assertThat(FuelType.OTHER)
                .isEqualTo(android.hardware.automotive.vehicle.FuelType.FUEL_TYPE_OTHER);
    }
}
