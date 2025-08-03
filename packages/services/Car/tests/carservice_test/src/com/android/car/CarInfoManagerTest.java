/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import android.car.Car;
import android.car.CarInfoManager;
import android.car.PortLocationType;
import android.car.VehicleAreaSeat;
import android.hardware.automotive.vehicle.EvConnectorType;
import android.hardware.automotive.vehicle.FuelType;
import android.hardware.automotive.vehicle.VehicleProperty;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.hal.test.AidlVehiclePropValueBuilder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarInfoManagerTest extends MockedCarTestBase {
    private static final String MAKE_NAME = "ANDROID";
    private static final String MODEL_NAME = "TEST";
    private static final String DEFAULT_STRING_VALUE = "";
    private static final int DEFAULT_INTEGER_VALUE = 0;
    private static final float DEFAULT_FLOAT_VALUE = 0f;
    private static final int MODEL_YEAR = 2020;
    private static final String MODEL_YEAR_STRING = "2020";
    private static final float FAKE_CAPACITY = 2.0f;
    private static final List<Integer> FUEL_TYPES =
            Arrays.asList(FuelType.FUEL_TYPE_CNG, FuelType.FUEL_TYPE_BIODIESEL);
    private static final List<Integer> EV_CONNECTOR_TYPES =
            Arrays.asList(android.car.EvConnectorType.GBT, android.car.EvConnectorType.GBT_DC);
    private CarInfoManager mCarInfoManager;
    @Rule
    public TestName mTestName = new TestName();

    @Override
    protected void configureMockedHal() {
        // test if the sensor is unimplemented in cars.
        if (mTestName.getMethodName().endsWith("unimplemented")) {
            return;
        }
        addAidlStaticProperty(VehicleProperty.INFO_MAKE,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_MAKE)
                        .setStringValue(MAKE_NAME)
                        .build());
        addAidlStaticProperty(VehicleProperty.INFO_MODEL_YEAR,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_MODEL_YEAR)
                        .addIntValues(MODEL_YEAR).build());
        addAidlStaticProperty(VehicleProperty.INFO_FUEL_CAPACITY,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_FUEL_CAPACITY)
                        .addFloatValues(FAKE_CAPACITY).build());
        addAidlStaticProperty(VehicleProperty.INFO_EV_BATTERY_CAPACITY,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_EV_BATTERY_CAPACITY)
                        .addFloatValues(FAKE_CAPACITY).build());
        addAidlStaticProperty(VehicleProperty.INFO_MODEL,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_MODEL)
                        .setStringValue(MODEL_NAME).build());
        addAidlStaticProperty(VehicleProperty.INFO_FUEL_TYPE,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_FUEL_TYPE)
                        .addIntValues(FuelType.FUEL_TYPE_CNG)
                        .addIntValues(FuelType.FUEL_TYPE_BIODIESEL)
                        .build());
        addAidlStaticProperty(VehicleProperty.INFO_EV_CONNECTOR_TYPE,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_EV_CONNECTOR_TYPE)
                        .addIntValues(EvConnectorType.GBT_AC)
                        .addIntValues(EvConnectorType.GBT_DC)
                        .build());
        addAidlStaticProperty(VehicleProperty.INFO_EV_PORT_LOCATION,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_EV_PORT_LOCATION)
                        .addIntValues(PortLocationType.FRONT).build());
        addAidlStaticProperty(VehicleProperty.INFO_FUEL_DOOR_LOCATION,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_FUEL_DOOR_LOCATION)
                        .addIntValues(PortLocationType.FRONT_LEFT).build());
        addAidlStaticProperty(VehicleProperty.INFO_DRIVER_SEAT,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_FUEL_DOOR_LOCATION)
                        .addIntValues(VehicleAreaSeat.SEAT_ROW_1_LEFT).build());
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mCarInfoManager = (CarInfoManager) getCar().getCarManager(Car.INFO_SERVICE);
    }

    @Test
    public void testVehicleId() throws Exception {
        assertThat(mCarInfoManager.getVehicleId()).isNotNull();
    }

    @Test
    public void testVehicleId_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getVehicleId()).isEqualTo(DEFAULT_STRING_VALUE);
    }

    @Test
    public void testManufacturer() throws Exception {
        assertThat(mCarInfoManager.getManufacturer()).isEqualTo(MAKE_NAME);
    }

    @Test
    public void testManufacturer_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getManufacturer()).isEqualTo(DEFAULT_STRING_VALUE);
    }

    @Test
    public void testGetModel() throws Exception {
        assertThat(mCarInfoManager.getModel()).isEqualTo(MODEL_NAME);
    }

    @Test
    public void testGetModel_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getModel()).isEqualTo(DEFAULT_STRING_VALUE);
    }

    @Test
    public void testGetFuelType() throws Exception {
        assertThat(mCarInfoManager.getFuelTypes()).asList()
                .containsAtLeastElementsIn(FUEL_TYPES).inOrder();
    }

    @Test
    public void testGetFuelType_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getFuelTypes()).isEmpty();
    }

    @Test
    public void testGetEvConnectorTypes() throws Exception {
        assertThat(mCarInfoManager.getEvConnectorTypes()).asList()
                .containsAtLeastElementsIn(EV_CONNECTOR_TYPES).inOrder();
    }

    @Test
    public void testGetEvConnectorTypes_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getEvConnectorTypes()).isEmpty();
    }

    @Test
    public void testGetModelYear() throws Exception {
        assertThat(mCarInfoManager.getModelYear()).isEqualTo(MODEL_YEAR_STRING);
        assertThat(mCarInfoManager.getModelYearInInteger()).isEqualTo(MODEL_YEAR);
    }

    @Test
    public void testGetModelYear_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getModelYear()).isEqualTo(DEFAULT_STRING_VALUE);
        assertThat(mCarInfoManager.getModelYearInInteger()).isEqualTo(DEFAULT_INTEGER_VALUE);
    }

    @Test
    public void testGetPortDoorLocation() throws Exception {
        assertThat(mCarInfoManager.getEvPortLocation()).isEqualTo(PortLocationType.FRONT);
        assertThat(mCarInfoManager.getFuelDoorLocation()).isEqualTo(PortLocationType.FRONT_LEFT);
    }

    @Test
    public void testGetPortDoorLocation_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getEvPortLocation()).isEqualTo(PortLocationType.UNKNOWN);
        assertThat(mCarInfoManager.getFuelDoorLocation()).isEqualTo(PortLocationType.UNKNOWN);
    }

    @Test
    public void testGetCapacity() throws Exception {
        assertThat(mCarInfoManager.getEvBatteryCapacity()).isEqualTo(FAKE_CAPACITY);
        assertThat(mCarInfoManager.getFuelCapacity()).isEqualTo(FAKE_CAPACITY);
    }

    @Test
    public void testGetCapacity_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getEvBatteryCapacity()).isEqualTo(DEFAULT_FLOAT_VALUE);
        assertThat(mCarInfoManager.getFuelCapacity()).isEqualTo(DEFAULT_FLOAT_VALUE);
    }

    @Test
    public void testGetDriverSeat() throws Exception {
        assertThat(mCarInfoManager.getDriverSeat()).isEqualTo(VehicleAreaSeat.SEAT_ROW_1_LEFT);
    }

    @Test
    public void testGetDriverSeat_unimplemented() throws Exception {
        assertThat(mCarInfoManager.getDriverSeat()).isEqualTo(VehicleAreaSeat.SEAT_UNKNOWN);
    }
}
