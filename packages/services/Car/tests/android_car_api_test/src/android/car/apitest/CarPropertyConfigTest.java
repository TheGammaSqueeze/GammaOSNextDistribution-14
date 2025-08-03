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

import static org.junit.Assert.assertThrows;

import android.car.VehicleAreaType;
import android.car.VehicleAreaWindow;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.AreaIdConfig;

import androidx.test.filters.MediumTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link CarPropertyConfig}
 */
@MediumTest
public final class CarPropertyConfigTest extends CarPropertyTestBase {

    @Test
    public void testCarPropertyConfigBuilder() {
        createFloatPropertyConfig();
    }

    private CarPropertyConfig<Float> createFloatPropertyConfig() {
        CarPropertyConfig<Float> config = CarPropertyConfig
                .newBuilder(Float.class, FLOAT_PROPERTY_ID, CAR_AREA_TYPE)
                .addArea(WINDOW_DRIVER)
                .addAreaConfig(WINDOW_PASSENGER, 10f, 20f)
                .build();

        assertThat(config.getPropertyId()).isEqualTo(FLOAT_PROPERTY_ID);
        assertThat(config.getAreaType()).isEqualTo(CAR_AREA_TYPE);
        assertThat(config.getPropertyType()).isEqualTo(Float.class);
        assertThat(config.getAreaCount()).isEqualTo(2);

        // We didn't assign any restrictions to WINDOW_DRIVER area.
        assertThat(config.getMinValue(WINDOW_DRIVER)).isNull();
        assertThat(config.getMaxValue(WINDOW_DRIVER)).isNull();

        assertThat(config.getMinValue(WINDOW_PASSENGER)).isEqualTo(10f);
        assertThat(config.getMaxValue(WINDOW_PASSENGER)).isEqualTo(20f);

        return config;
    }

    @Test
    public void testWriteReadFloat() {
        CarPropertyConfig<Float> config = createFloatPropertyConfig();

        writeToParcel(config);
        CarPropertyConfig<Float> configRead = readFromParcel();

        assertThat(configRead.getPropertyId()).isEqualTo(FLOAT_PROPERTY_ID);
        assertThat(configRead.getAreaType()).isEqualTo(CAR_AREA_TYPE);
        assertThat(configRead.getPropertyType()).isEqualTo(Float.class);
        assertThat(configRead.getAreaCount()).isEqualTo(2);

        // We didn't assign any restrictions to WINDOW_DRIVER area.
        assertThat(configRead.getMinValue(WINDOW_DRIVER)).isNull();
        assertThat(configRead.getMaxValue(WINDOW_DRIVER)).isNull();

        assertThat(configRead.getMinValue(WINDOW_PASSENGER)).isEqualTo(10f);
        assertThat(configRead.getMaxValue(WINDOW_PASSENGER)).isEqualTo(20f);
    }

    @Test
    public void testWriteReadIntegerValue() {
        Integer expectedMinValue = 1;
        Integer expectedMaxValue = 20;
        CarPropertyConfig<Integer> config = CarPropertyConfig
                .newBuilder(Integer.class, INT_PROPERTY_ID, CAR_AREA_TYPE)
                .addAreaConfig(WINDOW_PASSENGER, expectedMinValue, expectedMaxValue)
                .addArea(WINDOW_DRIVER)
                .build();
        writeToParcel(config);
        CarPropertyConfig<Integer> configRead = readFromParcel();

        assertThat(configRead.getPropertyId()).isEqualTo(INT_PROPERTY_ID);
        assertThat(configRead.getAreaType()).isEqualTo(CAR_AREA_TYPE);
        assertThat(configRead.getPropertyType()).isEqualTo(Integer.class);
        assertThat(configRead.getAreaCount()).isEqualTo(2);

        assertThat(configRead.getMinValue(WINDOW_DRIVER)).isNull();
        assertThat(configRead.getMaxValue(WINDOW_DRIVER)).isNull();

        assertThat(configRead.getMinValue(WINDOW_PASSENGER)).isEqualTo(expectedMinValue);
        assertThat(configRead.getMaxValue(WINDOW_PASSENGER)).isEqualTo(expectedMaxValue);
    }

    @Test
    public void testWriteReadLongValue() {
        Long expectedMinValue = 0L;
        Long expectedMaxValue = 100L;
        CarPropertyConfig<Long> config = CarPropertyConfig
                .newBuilder(Long.class, LONG_PROPERTY_ID, CAR_AREA_TYPE)
                .addAreaConfig(WINDOW_PASSENGER, expectedMinValue, expectedMaxValue)
                .addArea(WINDOW_DRIVER)
                .build();
        writeToParcel(config);
        CarPropertyConfig<Long> configRead = readFromParcel();

        assertThat(configRead.getPropertyId()).isEqualTo(LONG_PROPERTY_ID);
        assertThat(configRead.getAreaType()).isEqualTo(CAR_AREA_TYPE);
        assertThat(configRead.getPropertyType()).isEqualTo(Long.class);
        assertThat(configRead.getAreaCount()).isEqualTo(2);

        assertThat(configRead.getMinValue(WINDOW_DRIVER)).isNull();
        assertThat(configRead.getMaxValue(WINDOW_DRIVER)).isNull();

        assertThat(configRead.getMinValue(WINDOW_PASSENGER)).isEqualTo(expectedMinValue);
        assertThat(configRead.getMaxValue(WINDOW_PASSENGER)).isEqualTo(expectedMaxValue);
    }

    @Test
    public void testWriteReadIntegerArray() {
        CarPropertyConfig<Integer[]> config = CarPropertyConfig
                .newBuilder(Integer[].class, INT_ARRAY_PROPERTY_ID, CAR_AREA_TYPE)
                // We do not set range for integer array properties.
                .addArea(WINDOW_DRIVER)
                .addArea(WINDOW_PASSENGER)
                .build();

        writeToParcel(config);
        CarPropertyConfig<Integer[]> configRead = readFromParcel();

        assertThat(configRead.getPropertyId()).isEqualTo(INT_ARRAY_PROPERTY_ID);
        assertThat(configRead.getAreaType()).isEqualTo(CAR_AREA_TYPE);
        assertThat(configRead.getPropertyType()).isEqualTo(Integer[].class);
        assertThat(configRead.getAreaCount()).isEqualTo(2);

        // We didn't assign any restrictions to WINDOW_DRIVER area.
        assertThat(configRead.getMinValue(WINDOW_PASSENGER)).isNull();
        assertThat(configRead.getMaxValue(WINDOW_PASSENGER)).isNull();
        assertThat(configRead.getMinValue(WINDOW_DRIVER)).isNull();
        assertThat(configRead.getMaxValue(WINDOW_DRIVER)).isNull();
    }

    @Test
    public void testWriteReadUnexpectedType() {
        CarPropertyConfig<Float> config = createFloatPropertyConfig();
        writeToParcel(config);

        CarPropertyConfig<Integer> integerConfig = readFromParcel();

        // Wrote float, attempted to read integer.
        assertThrows(ClassCastException.class, () -> {
            // Need to keep this unused variable for the test to pass.
            Integer unusedMinValue = integerConfig.getMinValue(WINDOW_PASSENGER);
        });

        // Type casting from raw CarPropertyConfig should be fine, just confidence check.
        CarPropertyConfig<?> rawTypeConfig = readFromParcel();
        assertThat(rawTypeConfig.getMinValue(WINDOW_PASSENGER)).isEqualTo(10f);

        // Wrote float, attempted to read integer.
        assertThrows(ClassCastException.class, () -> {
            // Need to keep this unused variable for the test to pass.
            int unusedMinValue = (Integer) rawTypeConfig.getMinValue(WINDOW_PASSENGER);
        });
    }

    @Test
    public void testWriteReadMixedType() {
        List<Integer> configArray = Arrays.asList(1, 0, 1, 0, 1, 0, 0, 0, 0);

        CarPropertyConfig<Object> mixedTypePropertyConfig = CarPropertyConfig
                .newBuilder(Object.class, MIXED_TYPE_PROPERTY_ID,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .addArea(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .setConfigArray(new ArrayList<Integer>(configArray))
                .build();
        writeToParcel(mixedTypePropertyConfig);

        CarPropertyConfig<Object> configRead = readFromParcel();

        assertThat(configRead.getPropertyId()).isEqualTo(MIXED_TYPE_PROPERTY_ID);
        assertThat(configRead.getAreaType()).isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        assertThat(configRead.getPropertyType()).isEqualTo(Object.class);
        assertThat(configRead.getAreaCount()).isEqualTo(1);
        assertThat(configRead.getConfigArray()).containsExactlyElementsIn(configArray).inOrder();
    }

    @Test
    public void getConfigString_returnsExpectedValue() {
        String testConfigString = "testConfigString";
        CarPropertyConfig<Integer> carPropertyConfig = CarPropertyConfig
                .newBuilder(Integer.class, INT_PROPERTY_ID,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL).setConfigString(testConfigString)
                .build();

        assertThat(carPropertyConfig.getConfigString()).isEqualTo(testConfigString);
    }

    @Test
    public void getFirstAndOnlyAreaId_returnsAreaId() {
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).addArea(
                VehicleAreaWindow.WINDOW_ROW_1_LEFT).build();

        assertThat(carPropertyConfig.getFirstAndOnlyAreaId()).isEqualTo(
                VehicleAreaWindow.WINDOW_ROW_1_LEFT);
    }

    @Test
    public void getFirstAndOnlyAreaId_throwsIllegalStateException() {
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).addArea(
                VehicleAreaWindow.WINDOW_ROW_1_LEFT).addArea(
                VehicleAreaWindow.WINDOW_ROW_1_RIGHT).build();

        assertThrows(IllegalStateException.class, () -> carPropertyConfig.getFirstAndOnlyAreaId());
    }

    @Test
    public void getAreaIdConfigs_returnsEmptyIfNoAreaIdsSupplied() {
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).build();

        assertThat(carPropertyConfig.getAreaIdConfigs()).isEmpty();
    }

    @Test
    public void getAreaIdConfigs_returnsAreaIdConfigThatIsSupplied() {
        AreaIdConfig<Long> areaIdConfig = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD).build();
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).addAreaIdConfig(
                areaIdConfig).build();

        assertThat(carPropertyConfig.getAreaIdConfigs()).containsExactly(areaIdConfig);
    }

    @Test
    public void getAreaIdConfigs_returnsMultipleAreaIdConfigsThatAreSupplied() {
        AreaIdConfig<Long> areaIdConfig = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD).build();
        AreaIdConfig<Long> areaIdConfig2 = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD).build();
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).addAreaIdConfig(
                areaIdConfig).addAreaIdConfig(areaIdConfig2).build();

        assertThat(carPropertyConfig.getAreaIdConfigs()).containsExactly(areaIdConfig,
                areaIdConfig2);
    }

    @Test
    public void getAreaIdConfig_throwsIllegalArgumentException() {
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).build();

        assertThrows(IllegalArgumentException.class,
                () -> carPropertyConfig.getAreaIdConfig(/*areaId=*/0));
    }

    @Test
    public void getAreaIdConfig_returnsAreaIdConfigThatIsSupplied() {
        AreaIdConfig<Long> areaIdConfig = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD).build();
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).addAreaIdConfig(
                areaIdConfig).build();

        assertThat(carPropertyConfig.getAreaIdConfig(
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD)).isEqualTo(areaIdConfig);
    }

    @Test
    public void getAreaIdConfig_returnsMultipleAreaIdConfigsThatAreSupplied() {
        AreaIdConfig<Long> areaIdConfig = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD).build();
        AreaIdConfig<Long> areaIdConfig2 = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD).build();
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).addAreaIdConfig(
                areaIdConfig).addAreaIdConfig(areaIdConfig2).build();

        assertThat(carPropertyConfig.getAreaIdConfig(
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD)).isEqualTo(areaIdConfig);
        assertThat(carPropertyConfig.getAreaIdConfig(
                VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD)).isEqualTo(areaIdConfig2);
    }

    @Test
    public void getAreaIds_isEmptyIfNoAreaIdsAreSupplied() {
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).build();

        assertThat(carPropertyConfig.getAreaIds()).isEmpty();
    }

    @Test
    public void getAreaIds_returnsAreaIdThatIsSupplied() {
        AreaIdConfig<Long> areaIdConfig = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD).build();
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).addAreaIdConfig(
                areaIdConfig).build();

        assertThat(carPropertyConfig.getAreaIds()).hasLength(1);
        assertThat(carPropertyConfig.getAreaIds()[0]).isEqualTo(
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD);
    }

    @Test
    public void getAreaIds_returnsMultipleAreaIdsThatAreSupplied() {
        AreaIdConfig<Long> areaIdConfig = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD).build();
        AreaIdConfig<Long> areaIdConfig2 = new AreaIdConfig.Builder<Long>(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD).build();
        CarPropertyConfig<Long> carPropertyConfig = CarPropertyConfig.newBuilder(Long.class,
                LONG_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW).addAreaIdConfig(
                areaIdConfig).addAreaIdConfig(areaIdConfig2).build();

        assertThat(carPropertyConfig.getAreaIds()).hasLength(2);
        assertThat(carPropertyConfig.getAreaIds()[0]).isEqualTo(
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD);
        assertThat(carPropertyConfig.getAreaIds()[1]).isEqualTo(
                VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD);

    }
}
