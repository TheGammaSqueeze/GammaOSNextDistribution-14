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

package com.android.car.internal.property;

import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import android.car.VehicleAreaType;
import android.car.hardware.CarPropertyConfig;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.os.Parcel;

import org.junit.Test;

import java.util.List;

public final class CarPropertyConfigListUnitTest extends AbstractExtendedMockitoTestCase {

    @Test
    public void testSerializeThenDeserializeNullConfigList() {
        Parcel parcel = Parcel.obtain();
        CarPropertyConfigList carPropertyConfigList = new CarPropertyConfigList(null);

        carPropertyConfigList.serializeNullPayload(parcel);
        carPropertyConfigList.deserialize(parcel);

        expectWithMessage("Car property config list null config list")
                .that(carPropertyConfigList.getConfigs())
                .isEmpty();
    }

    @Test
    public void testSerializeNullPayloadThenDeserialize() {
        Parcel parcel = Parcel.obtain();
        CarPropertyConfigList carPropertyConfigList = new CarPropertyConfigList(null);

        carPropertyConfigList.serialize(parcel, /* flags= */ 0);
        carPropertyConfigList.deserialize(parcel);

        expectWithMessage("Car property config list null payload")
                .that(carPropertyConfigList.getConfigs())
                .isEmpty();
    }

    @Test
    public void testSerializeThenDeserializeWithValidResults() {
        Parcel parcel = Parcel.obtain();
        List<CarPropertyConfig> configs = List.of(
                CarPropertyConfig.newBuilder(Float.class, HVAC_TEMPERATURE_SET,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL).build());
        CarPropertyConfigList carPropertyConfigList = new CarPropertyConfigList(configs);

        carPropertyConfigList.serialize(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        carPropertyConfigList.deserialize(parcel);

        expectWithMessage("Car property config list property id")
                .that(carPropertyConfigList.getConfigs().get(0).getPropertyId())
                .isEqualTo(configs.get(0).getPropertyId());
        expectWithMessage("Car property config list area type")
                .that(carPropertyConfigList.getConfigs().get(0).getAreaType())
                .isEqualTo(configs.get(0).getAreaType());
    }

    @Test
    public void testNewArray() {
        CarPropertyConfigList[] array = CarPropertyConfigList.CREATOR.newArray(56);

        expectWithMessage("Creator new array").that(array).isNotNull();
        expectWithMessage("Creator new array").that(array).hasLength(56);
    }

    @Test
    public void testCreateFromParcel() {
        CarPropertyConfig carPropertyConfig = CarPropertyConfig.newBuilder(
                Integer.class, 123, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL).build();
        CarPropertyConfigList carPropertyConfigList = new CarPropertyConfigList(
                List.of(carPropertyConfig));
        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        carPropertyConfigList.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        CarPropertyConfig carPropertyConfigCreateFromParcel =
                CarPropertyConfigList.CREATOR.createFromParcel(parcel).getConfigs().get(0);

        expectWithMessage("Create from parcel propertyId")
                .that(carPropertyConfigCreateFromParcel.getPropertyId())
                .isEqualTo(123);
        expectWithMessage("Create from parcel area type")
                .that(carPropertyConfigCreateFromParcel.getAreaType())
                .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
    }
}
