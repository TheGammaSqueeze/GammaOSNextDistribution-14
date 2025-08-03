/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.hal;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.CarPropertyValue;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehiclePropertyType;

import org.junit.Test;

public final class HalPropValueTest {

    private static final long TEST_TIMESTAMP = 1;
    private static final int TEST_AREA_ID = 2;
    private static final int TEST_PROP = 3;
    private static final int TEST_STATUS = VehiclePropertyStatus.AVAILABLE;
    private static final int TEST_INT32_VALUE = 5;
    private static final float TEST_FLOAT_VALUE = 6.0f;
    private static final long TEST_INT64_VALUE = 7;
    private static final byte TEST_BYTE_VALUE = (byte) 1;
    private static final String TEST_STRING_VALUE = "test string";
    private static final int TEST_BOOL_PROP = VehiclePropertyType.BOOLEAN;
    private static final int TEST_INT32_PROP = VehiclePropertyType.INT32;
    private static final int TEST_INT32_VEC_PROP = VehiclePropertyType.INT32_VEC;
    private static final int TEST_FLOAT_PROP = VehiclePropertyType.FLOAT;
    private static final int TEST_FLOAT_VEC_PROP = VehiclePropertyType.FLOAT_VEC;
    private static final int TEST_INT64_PROP = VehiclePropertyType.INT64;
    private static final int TEST_INT64_VEC_PROP = VehiclePropertyType.INT64_VEC;
    private static final int TEST_BYTES_PROP = VehiclePropertyType.BYTES;
    private static final int TEST_STRING_PROP = VehiclePropertyType.STRING;
    private static final int TEST_MIXED_PROP = VehiclePropertyType.MIXED;
    private static final int TEST_MGR_PROP = 8;

    private android.hardware.automotive.vehicle.V2_0.VehiclePropValue getTestHidlPropValue() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.timestamp = TEST_TIMESTAMP;
        hidlValue.areaId = TEST_AREA_ID;
        hidlValue.prop = TEST_PROP;
        hidlValue.status = TEST_STATUS;
        hidlValue.value.int32Values.add(TEST_INT32_VALUE);
        hidlValue.value.floatValues.add(TEST_FLOAT_VALUE);
        hidlValue.value.int64Values.add(TEST_INT64_VALUE);
        hidlValue.value.bytes.add(TEST_BYTE_VALUE);
        hidlValue.value.stringValue = TEST_STRING_VALUE;

        return hidlValue;
    }

    private android.hardware.automotive.vehicle.VehiclePropValue getTestAidlPropValue() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.timestamp = TEST_TIMESTAMP;
        aidlValue.areaId = TEST_AREA_ID;
        aidlValue.prop = TEST_PROP;
        aidlValue.status = TEST_STATUS;
        aidlValue.value = new RawPropValues();
        aidlValue.value.int32Values = new int[] {TEST_INT32_VALUE};
        aidlValue.value.floatValues = new float[] {TEST_FLOAT_VALUE};
        aidlValue.value.int64Values = new long[] {TEST_INT64_VALUE};
        aidlValue.value.byteValues = new byte[] {TEST_BYTE_VALUE};
        aidlValue.value.stringValue = TEST_STRING_VALUE;

        return aidlValue;
    }

    @Test
    public void testBuildFromHidlValue() throws Exception {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(getTestHidlPropValue());

        assertThat(value.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getStatus()).isEqualTo(TEST_STATUS);
        assertThat(value.getInt32ValuesSize()).isEqualTo(1);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);
        assertThat(value.getFloatValuesSize()).isEqualTo(1);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
        assertThat(value.getInt64ValuesSize()).isEqualTo(1);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
        assertThat(value.getByteValuesSize()).isEqualTo(1);
        assertThat(value.getByteValue(0)).isEqualTo(TEST_BYTE_VALUE);
        assertThat(value.getStringValue()).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testBuildFromAidlValue() throws Exception {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(getTestAidlPropValue());

        assertThat(value.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getStatus()).isEqualTo(TEST_STATUS);
        assertThat(value.getInt32ValuesSize()).isEqualTo(1);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);
        assertThat(value.getFloatValuesSize()).isEqualTo(1);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
        assertThat(value.getInt64ValuesSize()).isEqualTo(1);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
        assertThat(value.getByteValuesSize()).isEqualTo(1);
        assertThat(value.getByteValue(0)).isEqualTo(TEST_BYTE_VALUE);
        assertThat(value.getStringValue()).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testBuildFromInt32Hidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_INT32_VALUE);

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getInt32ValuesSize()).isEqualTo(1);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.int32Values).containsExactly(TEST_INT32_VALUE);
    }

    @Test
    public void testBuildFromInt32sHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID,
                new int[]{TEST_INT32_VALUE, TEST_INT32_VALUE});

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getInt32ValuesSize()).isEqualTo(2);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);
        assertThat(value.getInt32Value(1)).isEqualTo(TEST_INT32_VALUE);

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.int32Values).containsExactly(
                TEST_INT32_VALUE, TEST_INT32_VALUE);
    }

    @Test
    public void testBuildFromFloatHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_FLOAT_VALUE);

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getFloatValuesSize()).isEqualTo(1);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.floatValues).containsExactly(
                TEST_FLOAT_VALUE);
    }

    @Test
    public void testBuildFromFloatsHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID,
                new float[]{TEST_FLOAT_VALUE, TEST_FLOAT_VALUE});

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getFloatValuesSize()).isEqualTo(2);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
        assertThat(value.getFloatValue(1)).isEqualTo(TEST_FLOAT_VALUE);

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.floatValues).containsExactly(
                TEST_FLOAT_VALUE, TEST_FLOAT_VALUE);
    }

    @Test
    public void testBuildFromInt64Hidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_INT64_VALUE);

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getInt64ValuesSize()).isEqualTo(1);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.int64Values).containsExactly(TEST_INT64_VALUE);
    }

    @Test
    public void testBuildFromInt64sHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID,
                new long[]{TEST_INT64_VALUE, TEST_INT64_VALUE});

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getInt64ValuesSize()).isEqualTo(2);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
        assertThat(value.getInt64Value(1)).isEqualTo(TEST_INT64_VALUE);

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.int64Values).containsExactly(
                TEST_INT64_VALUE, TEST_INT64_VALUE);

    }

    @Test
    public void testBuildFromStringHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_STRING_VALUE);

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getStringValue()).isEqualTo(TEST_STRING_VALUE);

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.stringValue).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testBuildFromMixedHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[]{TEST_INT32_VALUE}, new float[]{TEST_FLOAT_VALUE},
                new long[]{TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[]{TEST_BYTE_VALUE});

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(value.getStatus()).isEqualTo(TEST_STATUS);

        assertThat(vehiclePropValue).isEqualTo(getTestHidlPropValue());
    }

    @Test
    public void testBuildFromBytesHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, new byte[]{TEST_BYTE_VALUE});

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getByteValuesSize()).isEqualTo(1);
        assertThat(value.getByteValue(0)).isEqualTo(TEST_BYTE_VALUE);

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.bytes).containsExactly(TEST_BYTE_VALUE);
    }

    @Test
    public void testBuildFromInt32Aidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_INT32_VALUE);

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getInt32ValuesSize()).isEqualTo(1);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.int32Values).asList().containsExactly(TEST_INT32_VALUE);
    }

    @Test
    public void testBuildFromInt32sAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID,
                new int[]{TEST_INT32_VALUE, TEST_INT32_VALUE});

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getInt32ValuesSize()).isEqualTo(2);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);
        assertThat(value.getInt32Value(1)).isEqualTo(TEST_INT32_VALUE);

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.int32Values).asList().containsExactly(
                TEST_INT32_VALUE, TEST_INT32_VALUE);
    }

    @Test
    public void testBuildFromFloatAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_FLOAT_VALUE);

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getFloatValuesSize()).isEqualTo(1);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.floatValues).usingExactEquality().containsExactly(
                TEST_FLOAT_VALUE);
    }

    @Test
    public void testBuildFromFloatsAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID,
                new float[]{TEST_FLOAT_VALUE, TEST_FLOAT_VALUE});

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getFloatValuesSize()).isEqualTo(2);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
        assertThat(value.getFloatValue(1)).isEqualTo(TEST_FLOAT_VALUE);

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.floatValues).usingExactEquality().containsExactly(
                TEST_FLOAT_VALUE, TEST_FLOAT_VALUE);
    }

    @Test
    public void testBuildFromInt64Aidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_INT64_VALUE);

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getInt64ValuesSize()).isEqualTo(1);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.int64Values).asList().containsExactly(TEST_INT64_VALUE);
    }

    @Test
    public void testBuildFromInt64sAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID,
                new long[]{TEST_INT64_VALUE, TEST_INT64_VALUE});

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getInt64ValuesSize()).isEqualTo(2);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
        assertThat(value.getInt64Value(1)).isEqualTo(TEST_INT64_VALUE);

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.int64Values).asList().containsExactly(
                TEST_INT64_VALUE, TEST_INT64_VALUE);

    }

    @Test
    public void testBuildFromStringAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_STRING_VALUE);

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getStringValue()).isEqualTo(TEST_STRING_VALUE);

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.stringValue).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testBuildFromBytesAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, new byte[]{TEST_BYTE_VALUE});

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getByteValuesSize()).isEqualTo(1);
        assertThat(value.getByteValue(0)).isEqualTo(TEST_BYTE_VALUE);

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(vehiclePropValue.prop).isEqualTo(TEST_PROP);
        assertThat(vehiclePropValue.value.byteValues).asList().containsExactly(TEST_BYTE_VALUE);
    }

    @Test
    public void testBuildFromMixedAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[]{TEST_INT32_VALUE}, new float[]{TEST_FLOAT_VALUE},
                new long[]{TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[]{TEST_BYTE_VALUE});

        android.hardware.automotive.vehicle.VehiclePropValue vehiclePropValue =
                (android.hardware.automotive.vehicle.VehiclePropValue) (
                        value.toVehiclePropValue());

        assertThat(value.getPropId()).isEqualTo(TEST_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(value.getStatus()).isEqualTo(TEST_STATUS);

        assertThat(vehiclePropValue).isEqualTo(getTestAidlPropValue());
    }

    @Test
    public void testToCarPropertyValueFromHidlBool() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.timestamp = TEST_TIMESTAMP;
        hidlValue.areaId = TEST_AREA_ID;
        hidlValue.prop = TEST_BOOL_PROP;
        hidlValue.status = TEST_STATUS;
        hidlValue.value.int32Values.add(1);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Boolean content = (Boolean) propValue.getValue();

        assertThat(content).isTrue();
        assertThat(propValue.getPropertyId()).isEqualTo(TEST_MGR_PROP);
        assertThat(propValue.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(propValue.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(propValue.getStatus()).isEqualTo(TEST_STATUS);
    }

    @Test
    public void testToCarPropertyValueFromHidlInt32() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.prop = TEST_INT32_PROP;
        hidlValue.value.int32Values.add(TEST_INT32_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Integer content = (Integer) propValue.getValue();

        assertThat(content).isEqualTo(TEST_INT32_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromHidlInt32Vec() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.prop = TEST_INT32_VEC_PROP;
        hidlValue.value.int32Values.add(TEST_INT32_VALUE);
        hidlValue.value.int32Values.add(TEST_INT32_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Integer[] content = (Integer[]) propValue.getValue();

        assertThat(content).asList().containsExactly(TEST_INT32_VALUE, TEST_INT32_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromHidlFloat() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.prop = TEST_FLOAT_PROP;
        hidlValue.value.floatValues.add(TEST_FLOAT_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Float content = (Float) propValue.getValue();

        assertThat(content).isEqualTo(TEST_FLOAT_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromHidlFloatVec() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.prop = TEST_FLOAT_VEC_PROP;
        hidlValue.value.floatValues.add(TEST_FLOAT_VALUE);
        hidlValue.value.floatValues.add(TEST_FLOAT_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Float[] content = (Float[]) propValue.getValue();

        assertThat(content).asList().containsExactly(TEST_FLOAT_VALUE, TEST_FLOAT_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromHidlInt64() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.prop = TEST_INT64_PROP;
        hidlValue.value.int64Values.add(TEST_INT64_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Long content = (Long) propValue.getValue();

        assertThat(content).isEqualTo(TEST_INT64_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromHidlInt64Vec() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.prop = TEST_INT64_VEC_PROP;
        hidlValue.value.int64Values.add(TEST_INT64_VALUE);
        hidlValue.value.int64Values.add(TEST_INT64_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Long[] content = (Long[]) propValue.getValue();

        assertThat(content).asList().containsExactly(TEST_INT64_VALUE, TEST_INT64_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromHidlBytes() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.prop = TEST_BYTES_PROP;
        hidlValue.value.bytes.add(TEST_BYTE_VALUE);
        hidlValue.value.bytes.add(TEST_BYTE_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        byte[] content = (byte[]) propValue.getValue();

        assertThat(content).asList().containsExactly(TEST_BYTE_VALUE, TEST_BYTE_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromHidlString() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.prop = TEST_STRING_PROP;
        hidlValue.value.stringValue = TEST_STRING_VALUE;

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        String content = (String) propValue.getValue();

        assertThat(content).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromHidlMixed() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        hidlValue.timestamp = TEST_TIMESTAMP;
        hidlValue.areaId = TEST_AREA_ID;
        hidlValue.prop = TEST_MIXED_PROP;
        hidlValue.status = TEST_STATUS;
        hidlValue.value.int32Values.add(1);
        hidlValue.value.int32Values.add(TEST_INT32_VALUE);
        hidlValue.value.floatValues.add(TEST_FLOAT_VALUE);
        hidlValue.value.int64Values.add(TEST_INT64_VALUE);
        hidlValue.value.bytes.add(TEST_BYTE_VALUE);
        hidlValue.value.stringValue = TEST_STRING_VALUE;
        VehiclePropConfig config = new VehiclePropConfig();
        config.configArray = new int[] {1, 1};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(hidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(TEST_MGR_PROP,
                new AidlHalPropConfig(config));

        Object[] content = (Object[]) propValue.getValue();

        assertThat((String) content[0]).isEqualTo(TEST_STRING_VALUE);
        assertThat((Boolean) content[1]).isTrue();
        assertThat((Integer) content[2]).isEqualTo(TEST_INT32_VALUE);
        assertThat((Long) content[3]).isEqualTo(TEST_INT64_VALUE);
        assertThat((Float) content[4]).isEqualTo(TEST_FLOAT_VALUE);
        assertThat((Byte) content[5]).isEqualTo(TEST_BYTE_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlBool() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.timestamp = TEST_TIMESTAMP;
        aidlValue.areaId = TEST_AREA_ID;
        aidlValue.prop = TEST_BOOL_PROP;
        aidlValue.status = TEST_STATUS;
        aidlValue.value = new RawPropValues();
        aidlValue.value.int32Values = new int[] {1};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Boolean content = (Boolean) propValue.getValue();

        assertThat(content).isTrue();
        assertThat(propValue.getPropertyId()).isEqualTo(TEST_MGR_PROP);
        assertThat(propValue.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(propValue.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(propValue.getStatus()).isEqualTo(TEST_STATUS);
    }

    @Test
    public void testToCarPropertyValueFromAidlInt32() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_INT32_PROP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.int32Values = new int[] {TEST_INT32_VALUE};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Integer content = (Integer) propValue.getValue();

        assertThat(content).isEqualTo(TEST_INT32_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlInt32Vec() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_INT32_VEC_PROP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.int32Values = new int[] {TEST_INT32_VALUE, TEST_INT32_VALUE};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Integer[] content = (Integer[]) propValue.getValue();

        assertThat(content).asList().containsExactly(TEST_INT32_VALUE, TEST_INT32_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlFloat() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_FLOAT_PROP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.floatValues = new float[] {TEST_FLOAT_VALUE};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Float content = (Float) propValue.getValue();

        assertThat(content).isEqualTo(TEST_FLOAT_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlFloatVec() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_FLOAT_VEC_PROP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.floatValues = new float[] {TEST_FLOAT_VALUE, TEST_FLOAT_VALUE};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Float[] content = (Float[]) propValue.getValue();

        assertThat(content).asList().containsExactly(TEST_FLOAT_VALUE, TEST_FLOAT_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlInt64() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_INT64_PROP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.int64Values = new long[] {TEST_INT64_VALUE};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Long content = (Long) propValue.getValue();

        assertThat(content).isEqualTo(TEST_INT64_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlInt64Vec() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_INT64_VEC_PROP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.int64Values = new long[] {TEST_INT64_VALUE, TEST_INT64_VALUE};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        Long[] content = (Long[]) propValue.getValue();

        assertThat(content).asList().containsExactly(TEST_INT64_VALUE, TEST_INT64_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlBytes() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_BYTES_PROP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.byteValues = new byte[] {TEST_BYTE_VALUE, TEST_BYTE_VALUE};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        byte[] content = (byte[]) propValue.getValue();

        assertThat(content).asList().containsExactly(TEST_BYTE_VALUE, TEST_BYTE_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlString() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_STRING_PROP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.stringValue = TEST_STRING_VALUE;

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));

        String content = (String) propValue.getValue();

        assertThat(content).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testToCarPropertyValueFromAidlMixed() {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.timestamp = TEST_TIMESTAMP;
        aidlValue.areaId = TEST_AREA_ID;
        aidlValue.prop = TEST_MIXED_PROP;
        aidlValue.status = TEST_STATUS;
        aidlValue.value = new RawPropValues();
        aidlValue.value.int32Values = new int[] {1, TEST_INT32_VALUE};
        aidlValue.value.floatValues = new float[] {TEST_FLOAT_VALUE};
        aidlValue.value.int64Values = new long[] {TEST_INT64_VALUE};
        aidlValue.value.byteValues = new byte[] {TEST_BYTE_VALUE};
        aidlValue.value.stringValue = TEST_STRING_VALUE;
        VehiclePropConfig config = new VehiclePropConfig();
        config.configArray = new int[] {1, 1, 1, 0, 1, 0, 1, 0, 1};

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(aidlValue);

        CarPropertyValue propValue = value.toCarPropertyValue(TEST_MGR_PROP,
                new AidlHalPropConfig(config));

        Object[] content = (Object[]) propValue.getValue();

        assertThat((String) content[0]).isEqualTo(TEST_STRING_VALUE);
        assertThat((Boolean) content[1]).isTrue();
        assertThat((Integer) content[2]).isEqualTo(TEST_INT32_VALUE);
        assertThat((Long) content[3]).isEqualTo(TEST_INT64_VALUE);
        assertThat((Float) content[4]).isEqualTo(TEST_FLOAT_VALUE);
        assertThat((Byte) content[5]).isEqualTo(TEST_BYTE_VALUE);
    }

    private HalPropValue createTestHalPropValueWithStatus(int status) {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = TEST_BOOL_PROP;
        aidlValue.areaId = TEST_AREA_ID;
        aidlValue.status = status;
        aidlValue.timestamp = TEST_TIMESTAMP;
        aidlValue.value = new RawPropValues();
        aidlValue.value.int32Values = new int[] {1};
        return new HalPropValueBuilder(/*isAidl=*/true).build(aidlValue);
    }

    @Test
    public void testToCarPropertyValueUnavailableStatus() {
        HalPropValue value = createTestHalPropValueWithStatus(VehiclePropertyStatus.UNAVAILABLE);

        assertThat(value.toCarPropertyValue(TEST_MGR_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()))).isEqualTo(
                new CarPropertyValue<>(TEST_MGR_PROP, TEST_AREA_ID,
                        CarPropertyValue.STATUS_UNAVAILABLE, TEST_TIMESTAMP, Boolean.TRUE));
    }

    @Test
    public void testToCarPropertyValueInternalErrorStatus() {
        HalPropValue value = createTestHalPropValueWithStatus(VehiclePropertyStatus.ERROR);

        assertThat(value.toCarPropertyValue(TEST_MGR_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()))).isEqualTo(
                new CarPropertyValue<>(TEST_MGR_PROP, TEST_AREA_ID, CarPropertyValue.STATUS_ERROR,
                        TEST_TIMESTAMP, Boolean.TRUE));
    }

    // Creates an empty HalPropValue that does not have any value.
    private HalPropValue createTestHalPropValueWithNoValue(int prop) {
        android.hardware.automotive.vehicle.VehiclePropValue aidlValue =
                new android.hardware.automotive.vehicle.VehiclePropValue();
        aidlValue.prop = prop;
        aidlValue.value = new RawPropValues();
        return new HalPropValueBuilder(/*isAidl=*/true).build(aidlValue);
    }

    @Test
    public void testToCarPropertyValueInvalidBoolProp() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_BOOL_PROP);

        assertThat(value.toCarPropertyValue(TEST_MGR_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()))).isEqualTo(
                new CarPropertyValue<>(TEST_MGR_PROP, /*areaId=*/0,
                        CarPropertyValue.STATUS_ERROR, /*timestampNanos=*/0, Boolean.FALSE));
    }

    @Test
    public void testToCarPropertyValueInvalidInt32Prop() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_INT32_PROP);

        assertThat(value.toCarPropertyValue(TEST_MGR_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()))).isEqualTo(
                new CarPropertyValue<>(TEST_MGR_PROP, /*areaId=*/0,
                        CarPropertyValue.STATUS_ERROR, /*timestampNanos=*/0, Integer.valueOf(0)));
    }

    @Test
    public void testToCarPropertyValueInvalidInt64Prop() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_INT64_PROP);

        assertThat(value.toCarPropertyValue(TEST_MGR_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()))).isEqualTo(
                new CarPropertyValue<>(TEST_MGR_PROP, /*areaId=*/0,
                        CarPropertyValue.STATUS_ERROR, /*timestampNanos=*/0, Long.valueOf(0)));
    }

    @Test
    public void testToCarPropertyValueInvalidFloatProp() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_FLOAT_PROP);

        assertThat(value.toCarPropertyValue(TEST_MGR_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()))).isEqualTo(
                new CarPropertyValue<>(TEST_MGR_PROP, /*areaId=*/0,
                        CarPropertyValue.STATUS_ERROR, /*timestampNanos=*/0, Float.valueOf(0)));
    }

    // For int32_vec property, an empty array is considered a valid return value.
    @Test
    public void testToCarPropertyValueEmptyInt32VecProp() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_INT32_VEC_PROP);

        CarPropertyValue carPropValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));
        assertThat(carPropValue.getValue()).isEqualTo(new Integer[0]);
    }

    // For int64_vec property, an empty array is considered a valid return value.
    @Test
    public void testToCarPropertyValueEmptyInt64VecProp() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_INT64_VEC_PROP);

        CarPropertyValue carPropValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));
        assertThat(carPropValue.getValue()).isEqualTo(new Long[0]);
    }

    // For float_vec property, an empty array is considered a valid return value.
    @Test
    public void testToCarPropertyValueEmptyFloatVecProp() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_FLOAT_VEC_PROP);

        CarPropertyValue carPropValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));
        assertThat(carPropValue.getValue()).isEqualTo(new Float[0]);
    }

    // For string property, an empty string is considered a valid return value.
    @Test
    public void testToCarPropertyValueEmptyStringProp() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_STRING_PROP);

        CarPropertyValue carPropValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));
        assertThat(carPropValue.getValue()).isEqualTo(new String());
    }

    // For bytes property, an empty byte array is considered a valid return value.
    @Test
    public void testToCarPropertyValueEmptyBytesProp() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_BYTES_PROP);

        CarPropertyValue carPropValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(new VehiclePropConfig()));
        assertThat(carPropValue.getValue()).isEqualTo(new byte[0]);
    }

    @Test
    public void testToCarPropertyValueMixedPropNoValidBoolean() {
        HalPropValue value = createTestHalPropValueWithNoValue(TEST_MIXED_PROP);
        VehiclePropConfig config = new VehiclePropConfig();
        // We only care about the second element, which indicates containing boolean value.
        config.configArray = new int[] {1, 1, 1, 0, 1, 0, 1, 0, 1};

        CarPropertyValue<Object[]> carPropertyValue = value.toCarPropertyValue(
                TEST_MGR_PROP, new AidlHalPropConfig(config));
        assertThat(carPropertyValue.getPropertyId()).isEqualTo(TEST_MGR_PROP);
        assertThat(carPropertyValue.getStatus()).isEqualTo(CarPropertyValue.STATUS_ERROR);
    }

    @Test
    public void testBuildFromCarPropertyValueBoolHidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, (Boolean) true);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_BOOL_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_BOOL_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt32Value(0)).isEqualTo(1);
    }

    @Test
    public void testBuildFromCarPropertyValueInt32Hidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, (Integer) TEST_INT32_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_INT32_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_INT32_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueInt32VecHidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(
                        TEST_MGR_PROP,
                        TEST_AREA_ID,
                        new Integer[] {TEST_INT32_VALUE, TEST_INT32_VALUE});

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_INT32_VEC_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_INT32_VEC_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt32ValuesSize()).isEqualTo(2);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);
        assertThat(value.getInt32Value(1)).isEqualTo(TEST_INT32_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueFloatHidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, (Float) TEST_FLOAT_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_FLOAT_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_FLOAT_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueFloatVecHidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(
                        TEST_MGR_PROP,
                        TEST_AREA_ID,
                        new Float[] {TEST_FLOAT_VALUE, TEST_FLOAT_VALUE});

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_FLOAT_VEC_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_FLOAT_VEC_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getFloatValuesSize()).isEqualTo(2);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
        assertThat(value.getFloatValue(1)).isEqualTo(TEST_FLOAT_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueInt64Hidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, (Long) TEST_INT64_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_INT64_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_INT64_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueInt64VecHidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(
                        TEST_MGR_PROP,
                        TEST_AREA_ID,
                        new Long[] {TEST_INT64_VALUE, TEST_INT64_VALUE});

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_INT64_VEC_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_INT64_VEC_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt64ValuesSize()).isEqualTo(2);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
        assertThat(value.getInt64Value(1)).isEqualTo(TEST_INT64_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueBytesHidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, new byte[] {TEST_BYTE_VALUE});

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_BYTES_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_BYTES_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getByteValue(0)).isEqualTo(TEST_BYTE_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueStringHidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, TEST_STRING_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(propValue, TEST_STRING_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_STRING_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getStringValue()).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueMixedHidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(
                        TEST_MGR_PROP,
                        TEST_AREA_ID,
                        new Object[] {
                            TEST_STRING_VALUE,
                            (Boolean) true,
                            (Integer) TEST_INT32_VALUE,
                            (Long) TEST_INT64_VALUE,
                            (Float) TEST_FLOAT_VALUE,
                            (Byte) TEST_BYTE_VALUE,
                        });

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        VehiclePropConfig config = new VehiclePropConfig();
        config.configArray = new int[] {1, 1, 1, 0, 1, 0, 1, 0, 1};
        HalPropValue value = builder.build(propValue, TEST_MIXED_PROP,
                new AidlHalPropConfig(config));

        assertThat(value.getPropId()).isEqualTo(TEST_MIXED_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt32Value(0)).isEqualTo(1);
        assertThat(value.getInt32Value(1)).isEqualTo(TEST_INT32_VALUE);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
        assertThat(value.getByteValue(0)).isEqualTo(TEST_BYTE_VALUE);
        assertThat(value.getStringValue()).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueBoolAidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, (Boolean) true);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_BOOL_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_BOOL_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt32Value(0)).isEqualTo(1);
    }

    @Test
    public void testBuildFromCarPropertyValueInt32Aidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, (Integer) TEST_INT32_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_INT32_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_INT32_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueInt32VecAidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(
                        TEST_MGR_PROP,
                        TEST_AREA_ID,
                        new Integer[] {TEST_INT32_VALUE, TEST_INT32_VALUE});

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_INT32_VEC_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_INT32_VEC_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt32ValuesSize()).isEqualTo(2);
        assertThat(value.getInt32Value(0)).isEqualTo(TEST_INT32_VALUE);
        assertThat(value.getInt32Value(1)).isEqualTo(TEST_INT32_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueFloatAidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, (Float) TEST_FLOAT_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_FLOAT_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_FLOAT_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueFloatVecAidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(
                        TEST_MGR_PROP,
                        TEST_AREA_ID,
                        new Float[] {TEST_FLOAT_VALUE, TEST_FLOAT_VALUE});

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_FLOAT_VEC_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_FLOAT_VEC_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getFloatValuesSize()).isEqualTo(2);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
        assertThat(value.getFloatValue(1)).isEqualTo(TEST_FLOAT_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueInt64Aidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, (Long) TEST_INT64_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_INT64_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_INT64_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueInt64VecAidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(
                        TEST_MGR_PROP,
                        TEST_AREA_ID,
                        new Long[] {TEST_INT64_VALUE, TEST_INT64_VALUE});

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_INT64_VEC_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_INT64_VEC_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt64ValuesSize()).isEqualTo(2);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
        assertThat(value.getInt64Value(1)).isEqualTo(TEST_INT64_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueBytesAidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, new byte[] {TEST_BYTE_VALUE});

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_BYTES_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_BYTES_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getByteValue(0)).isEqualTo(TEST_BYTE_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueStringAidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(TEST_MGR_PROP, TEST_AREA_ID, TEST_STRING_VALUE);

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(propValue, TEST_STRING_PROP,
                new AidlHalPropConfig(new VehiclePropConfig()));

        assertThat(value.getPropId()).isEqualTo(TEST_STRING_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getStringValue()).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testBuildFromCarPropertyValueMixedAidl() {
        CarPropertyValue propValue =
                new CarPropertyValue(
                        TEST_MGR_PROP,
                        TEST_AREA_ID,
                        new Object[] {
                            TEST_STRING_VALUE,
                            (Boolean) true,
                            (Integer) TEST_INT32_VALUE,
                            (Long) TEST_INT64_VALUE,
                            (Float) TEST_FLOAT_VALUE,
                            (Byte) TEST_BYTE_VALUE,
                        });

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        VehiclePropConfig config = new VehiclePropConfig();
        config.configArray = new int[] {1, 1, 1, 0, 1, 0, 1, 0, 1};
        HalPropValue value = builder.build(propValue, TEST_MIXED_PROP,
                new AidlHalPropConfig(config));

        assertThat(value.getPropId()).isEqualTo(TEST_MIXED_PROP);
        assertThat(value.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(value.getInt32Value(0)).isEqualTo(1);
        assertThat(value.getInt32Value(1)).isEqualTo(TEST_INT32_VALUE);
        assertThat(value.getInt64Value(0)).isEqualTo(TEST_INT64_VALUE);
        assertThat(value.getFloatValue(0)).isEqualTo(TEST_FLOAT_VALUE);
        assertThat(value.getByteValue(0)).isEqualTo(TEST_BYTE_VALUE);
        assertThat(value.getStringValue()).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testDumpValuesAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE, TEST_INT32_VALUE},
                new float[] {TEST_FLOAT_VALUE, TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE, TEST_INT64_VALUE},
                TEST_STRING_VALUE,
                new byte[] {TEST_BYTE_VALUE, TEST_BYTE_VALUE});

        assertThat(value.dumpInt32Values()).isEqualTo("[5, 5]");
        assertThat(value.dumpFloatValues()).isEqualTo("[6.0, 6.0]");
        assertThat(value.dumpInt64Values()).isEqualTo("[7, 7]");
    }

    @Test
    public void testDumpValuesHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE, TEST_INT32_VALUE},
                new float[] {TEST_FLOAT_VALUE, TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE, TEST_INT64_VALUE},
                TEST_STRING_VALUE,
                new byte[] {TEST_BYTE_VALUE, TEST_BYTE_VALUE});

        assertThat(value.dumpInt32Values()).isEqualTo("[5, 5]");
        assertThat(value.dumpFloatValues()).isEqualTo("[6.0, 6.0]");
        assertThat(value.dumpInt64Values()).isEqualTo("[7, 7]");
    }

    @Test
    public void testEquals() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value1 = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});
        HalPropValue value2 = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value2)).isTrue();

        HalPropValue value3 = builder.build(0, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value3)).isFalse();

        HalPropValue value4 = builder.build(TEST_PROP, 0, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value4)).isFalse();

        HalPropValue value5 = builder.build(TEST_PROP, TEST_AREA_ID, 0, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value5)).isFalse();

        HalPropValue value6 = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP,
                VehiclePropertyStatus.UNAVAILABLE,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value6)).isFalse();

        HalPropValue value7 = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value7)).isFalse();

        HalPropValue value8 = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value8)).isFalse();

        HalPropValue value9 = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {}, TEST_STRING_VALUE, new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value9)).isFalse();

        HalPropValue value10 = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, new String(), new byte[] {TEST_BYTE_VALUE});

        assertThat(value1.equals(value10)).isFalse();

        HalPropValue value11 = builder.build(TEST_PROP, TEST_AREA_ID, TEST_TIMESTAMP, TEST_STATUS,
                new int[] {TEST_INT32_VALUE}, new float[] {TEST_FLOAT_VALUE},
                new long[] {TEST_INT64_VALUE}, TEST_STRING_VALUE, new byte[] {});

        assertThat(value1.equals(value11)).isFalse();
    }

    @Test
    public void testHashCodeHidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value1 = builder.build(getTestHidlPropValue());
        HalPropValue value2 = builder.build(getTestHidlPropValue());

        assertThat(value1.hashCode()).isEqualTo(value2.hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue3 =
                getTestHidlPropValue();
        hidlValue3.prop = 0;

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue3).hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue4 =
                getTestHidlPropValue();
        hidlValue4.areaId = 0;

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue4).hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue5 =
                getTestHidlPropValue();
        hidlValue5.timestamp = 0;

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue5).hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue6 =
                getTestHidlPropValue();
        hidlValue6.status = VehiclePropertyStatus.UNAVAILABLE;

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue6).hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue7 =
                getTestHidlPropValue();
        hidlValue7.value.int32Values.add(1);

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue7).hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue8 =
                getTestHidlPropValue();
        hidlValue8.value.int64Values.add((long) 1);

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue8).hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue9 =
                getTestHidlPropValue();
        hidlValue9.value.floatValues.add(1.0f);

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue9).hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue10 =
                getTestHidlPropValue();
        hidlValue10.value.bytes.add((byte) 1);

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue10).hashCode());

        android.hardware.automotive.vehicle.V2_0.VehiclePropValue hidlValue11 =
                getTestHidlPropValue();
        hidlValue11.value.stringValue = "blahblah";

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(hidlValue11).hashCode());
    }

    @Test
    public void testHashCodeAidl() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value1 = builder.build(getTestAidlPropValue());
        HalPropValue value2 = builder.build(getTestAidlPropValue());

        assertThat(value1.hashCode()).isEqualTo(value2.hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue3 =
                getTestAidlPropValue();
        aidlValue3.prop = 0;

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue3).hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue4 =
                getTestAidlPropValue();
        aidlValue4.areaId = 0;

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue4).hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue5 =
                getTestAidlPropValue();
        aidlValue5.timestamp = 0;

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue5).hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue6 =
                getTestAidlPropValue();
        aidlValue6.status = VehiclePropertyStatus.UNAVAILABLE;

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue6).hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue7 =
                getTestAidlPropValue();
        aidlValue7.value.int32Values = new int[]{0};

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue7).hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue8 =
                getTestAidlPropValue();
        aidlValue8.value.int64Values = new long[]{0};

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue8).hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue9 =
                getTestAidlPropValue();
        aidlValue9.value.floatValues = new float[]{0.0f};

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue9).hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue10 =
                getTestAidlPropValue();
        aidlValue10.value.byteValues = new byte[]{0};

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue10).hashCode());

        android.hardware.automotive.vehicle.VehiclePropValue aidlValue11 =
                getTestAidlPropValue();
        aidlValue11.value.stringValue = "blahblah";

        assertThat(value1.hashCode()).isNotEqualTo(builder.build(aidlValue11).hashCode());
    }
}
