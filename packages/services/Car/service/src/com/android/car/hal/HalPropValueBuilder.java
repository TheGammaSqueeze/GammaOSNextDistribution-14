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

import static com.android.car.internal.common.CommonConstants.EMPTY_BYTE_ARRAY;
import static com.android.car.internal.common.CommonConstants.EMPTY_FLOAT_ARRAY;
import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;
import static com.android.car.internal.common.CommonConstants.EMPTY_LONG_ARRAY;
import static com.android.car.CarServiceUtils.toByteArray;
import static com.android.car.CarServiceUtils.toFloatArray;
import static com.android.car.CarServiceUtils.toIntArray;
import static com.android.car.CarServiceUtils.toLongArray;

import android.car.hardware.CarPropertyValue;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * HalPropValueBuilder is a factory class used to build a HalPropValue.
 */
public final class HalPropValueBuilder {
    // configArray[0], 1 indicates the property has a String value.
    private static final int CONFIG_ARRAY_INDEX_STRING = 0;
    // configArray[1], 1 indicates the property has a Boolean value.
    private static final int CONFIG_ARRAY_INDEX_BOOLEAN = 1;
    // configArray[2], 1 indicates the property has a Integer value.
    private static final int CONFIG_ARRAY_INDEX_INT = 2;
    // configArray[3], 1 indicates the property has a Integer[] value.
    private static final int CONFIG_ARRAY_INDEX_INT_ARRAY = 3;
    // configArray[4], 1 indicates the property has a Long value.
    private static final int CONFIG_ARRAY_INDEX_LONG = 4;
    // configArray[5], the number indicates the size of Long[]  in the property.
    private static final int CONFIG_ARRAY_INDEX_LONG_ARRAY = 5;
    // configArray[6], 1 indicates the property has a Float value.
    private static final int CONFIG_ARRAY_INDEX_FLOAT = 6;
    // configArray[7], the number indicates the size of Float[] in the property.
    private static final int CONFIG_ARRAY_INDEX_FLOAT_ARRAY = 7;
    // configArray[8], the number indicates the size of byte[] in the property.
    private static final int CONFIG_ARRAY_INDEX_BYTES = 8;
    // Length of mixed type properties' configArray should always be 9.
    private static final int CONFIG_ARRAY_LENGTH = 9;

    private boolean mIsAidl;

    public HalPropValueBuilder(boolean isAidl) {
        mIsAidl = isAidl;
    }

    /**
     * Creates a HalPropValue with no value.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE);
    }

    /**
     * Creates a HalPropValue with no value.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status);
    }

    /**
     * Creates an INT32 type HalPropValue.
     *
     * @param prop The property ID.
     * @param value The property value.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, int value) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE, value);
    }

    /**
     * Creates an INT32 type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param value The property value.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, int value) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, value);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, value);
    }

    /**
     * Creates an INT32_VEC type HalPropValue.
     *
     * @param prop The property ID.
     * @param values The property values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, int[] values) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE, values);
    }

    /**
     * Creates an INT32_VEC type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param values The property values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, int[] values) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, values);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, values);
    }

    /**
     * Creates a FLOAT type HalPropValue.
     *
     * @param prop The property ID.
     * @param value The property value.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, float value) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE, value);
    }

    /**
     * Creates a FLOAT type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param value The property value.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, float value) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, value);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, value);
    }

    /**
     * Creates a FLOAT_VEC type HalPropValue.
     *
     * @param prop The property ID.
     * @param values The property values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, float[] values) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE, values);
    }

    /**
     * Creates a FLOAT_VEC type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param values The property values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, float[] values) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, values);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, values);
    }

    /**
     * Creates an INT64 type HalPropValue.
     *
     * @param prop The property ID.
     * @param value The property value.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long value) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE, value);
    }

    /**
     * Creates an INT64 type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param value The property value.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, long value) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, value);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, value);
    }

    /**
     * Creates an INT64_VEC type HalPropValue.
     *
     * @param prop The property ID.
     * @param values The property values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long[] values) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE, values);
    }

    /**
     * Creates an INT64_VEC type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param values The property values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, long[] values) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, values);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, values);
    }

    /**
     * Creates a STRING type HalPropValue.
     *
     * @param prop The property ID.
     * @param value The property value.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, String value) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE, value);
    }

    /**
     * Creates a STRING type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param value The property value.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, String value) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, value);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, value);
    }

    /**
     * Creates a BYTES type HalPropValue.
     *
     * @param prop The property ID.
     * @param values The property values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, byte[] values) {
        return build(prop, areaId, /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE, values);
    }

    /**
     * Creates a BYTES type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param values The property values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, byte[] values) {
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, values);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, values);
    }

    /**
     * Creates a MIXED type HalPropValue.
     *
     * @param prop The property ID.
     * @param areaId The area ID.
     * @param timestamp The timestamp for the property.
     * @param status The status for the property.
     * @param int32Values The int values.
     * @param floatValues The float values.
     * @param int64Values The long values.
     * @param stringValue The string value.
     * @param byteValues The byte values.
     * @return a HalPropValue.
     */
    public HalPropValue build(int prop, int areaId, long timestamp, int status, int[] int32Values,
            float[] floatValues, long[] int64Values, String stringValue, byte[] byteValues) {
        Objects.requireNonNull(int32Values, "Use empty value, not null for empty values");
        Objects.requireNonNull(floatValues, "Use empty value, not null for empty values");
        Objects.requireNonNull(int64Values, "Use empty value, not null for empty values");
        Objects.requireNonNull(stringValue, "Use empty value, not null for empty values");
        Objects.requireNonNull(byteValues, "Use empty value, not null for empty values");
        if (mIsAidl) {
            return new AidlHalPropValue(prop, areaId, timestamp, status, int32Values, floatValues,
                    int64Values, stringValue, byteValues);
        }
        return new HidlHalPropValue(prop, areaId, timestamp, status, int32Values, floatValues,
                int64Values, stringValue, byteValues);
    }

    /**
     * Creates a HalPropValue based on a {@link CarPropretyValue}.
     *
     * @param carPropertyValue The car property value to convert from.
     * @param halPropId The property ID used in vehicle HAL.
     * @param config The property config.
     * @return a HalPropValue.
     */
    public HalPropValue build(CarPropertyValue carPropertyValue, int halPropId,
            HalPropConfig config) {
        if (mIsAidl) {
            return new AidlHalPropValue(carPropertyValue, halPropId, config);
        }
        return new HidlHalPropValue(carPropertyValue, halPropId, config);
    }

    /**
     * Creates a HalPropValue based on an
     * {@link android.hardware.automotive.vehicle.V2_0.VehiclePropValue}.
     *
     * @param value The HIDL VehiclePropValue to convert from.
     * @return a HalPropValue.
     */
    public HalPropValue build(android.hardware.automotive.vehicle.V2_0.VehiclePropValue value) {
        mIsAidl = false;
        return new HidlHalPropValue(value);
    }

    /**
     * Creates a HalPropValue based on an
     * {@link android.hardware.automotive.vehicle.VehiclePropValue}.
     *
     * @param value The AIDL VehiclePropValue to convert from.
     * @return a HalPropValue.
     */
    public HalPropValue build(android.hardware.automotive.vehicle.VehiclePropValue value) {
        mIsAidl = true;
        return new AidlHalPropValue(value);
    }

    private static class AidlHalPropValue extends HalPropValue {
        private android.hardware.automotive.vehicle.VehiclePropValue mVehiclePropValue;

        AidlHalPropValue(int prop, int areaId, long timestamp, int status) {
            init(prop, areaId, timestamp, status);
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, int value) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int32Values = new int[]{value};
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, int[] values) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int32Values = values;
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, float value) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.floatValues = new float[]{value};
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, float[] values) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.floatValues = values;
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, long value) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int64Values = new long[]{value};
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, long[] values) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int64Values = values;
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, byte[] values) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.byteValues = values;
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, String value) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.stringValue = value;
        }

        AidlHalPropValue(int prop, int areaId, long timestamp, int status, int[] int32Values,
                float[] floatValues, long[] int64Values, String stringValue, byte[] byteValues) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int32Values = int32Values;
            mVehiclePropValue.value.floatValues = floatValues;
            mVehiclePropValue.value.int64Values = int64Values;
            mVehiclePropValue.value.stringValue = stringValue;
            mVehiclePropValue.value.byteValues = byteValues;
        }

        AidlHalPropValue(CarPropertyValue value, int halPropId, HalPropConfig config) {
            init(halPropId, value.getAreaId(), 0, VehiclePropertyStatus.AVAILABLE);

            if (HalPropValue.isMixedTypeProperty(halPropId)) {
                setMixedCarProperty(value, config.getConfigArray());
            } else {
                setCarProperty(value);
            }
        }

        AidlHalPropValue(android.hardware.automotive.vehicle.VehiclePropValue value) {
            mVehiclePropValue = value;

            // Make sure the stored VehiclePropValue does not contain any null values.
            if (mVehiclePropValue.value == null) {
                mVehiclePropValue.value = emptyRawPropValues();
                return;
            }
            if (mVehiclePropValue.value.int32Values == null) {
                mVehiclePropValue.value.int32Values = EMPTY_INT_ARRAY;
            }
            if (mVehiclePropValue.value.floatValues == null) {
                mVehiclePropValue.value.floatValues = EMPTY_FLOAT_ARRAY;
            }
            if (mVehiclePropValue.value.int64Values == null) {
                mVehiclePropValue.value.int64Values = EMPTY_LONG_ARRAY;
            }
            if (mVehiclePropValue.value.byteValues == null) {
                mVehiclePropValue.value.byteValues = EMPTY_BYTE_ARRAY;
            }
            if (mVehiclePropValue.value.stringValue == null) {
                mVehiclePropValue.value.stringValue = "";
            }
        }

        public Object toVehiclePropValue() {
            return mVehiclePropValue;
        }

        /**
         * Get the timestamp.
         *
         * @return The timestamp.
         */
        public long getTimestamp() {
            return mVehiclePropValue.timestamp;
        }

        /**
         * Get the area ID.
         *
         * @return The area ID.
         */
        public int getAreaId() {
            return mVehiclePropValue.areaId;
        }

        /**
         * Get the property ID.
         *
         * @return The property ID.
         */
        public int getPropId() {
            return mVehiclePropValue.prop;
        }

        /**
         * Get the property status.
         *
         * @return The property status.
         */
        public int getStatus() {
            return mVehiclePropValue.status;
        }

        /**
         * Get stored int32 values size.
         *
         * @return The size for the stored int32 values.
         */
        public int getInt32ValuesSize() {
            return mVehiclePropValue.value.int32Values.length;
        }

        /**
         * Get the int32 value at index.
         *
         * @param index The index.
         * @return The int32 value at index.
         */
        public int getInt32Value(int index) {
            return mVehiclePropValue.value.int32Values[index];
        }

        /**
         * Dump all int32 values as a string. Used for debugging.
         *
         * @return A String representation of all int32 values.
         */
        public String dumpInt32Values() {
            return Arrays.toString(mVehiclePropValue.value.int32Values);
        }

        /**
         * Get stored float values size.
         *
         * @return The size for the stored float values.
         */
        public int getFloatValuesSize() {
            return mVehiclePropValue.value.floatValues.length;
        }

        /**
         * Get the float value at index.
         *
         * @param index The index.
         * @return The float value at index.
         */
        public float getFloatValue(int index) {
            return mVehiclePropValue.value.floatValues[index];
        }

        /**
         * Dump all float values as a string. Used for debugging.
         *
         * @return A String representation of all float values.
         */
        public String dumpFloatValues() {
            return Arrays.toString(mVehiclePropValue.value.floatValues);
        }

        /**
         * Get stored inn64 values size.
         *
         * @return The size for the stored inn64 values.
         */
        public int getInt64ValuesSize() {
            return mVehiclePropValue.value.int64Values.length;
        }

        /**
         * Dump all int64 values as a string. Used for debugging.
         *
         * @return A String representation of all int64 values.
         */
        public String dumpInt64Values() {
            return Arrays.toString(mVehiclePropValue.value.int64Values);
        }

        /**
         * Get the int64 value at index.
         *
         * @param index The index.
         * @return The int64 value at index.
         */
        public long getInt64Value(int index) {
            return mVehiclePropValue.value.int64Values[index];
        }

        /**
         * Get stored byte values size.
         *
         * @return The size for the stored byte values.
         */
        public int getByteValuesSize() {
            return mVehiclePropValue.value.byteValues.length;
        }

        /**
         * Get the byte value at index.
         *
         * @param index The index.
         * @return The byte value at index.
         */
        public byte getByteValue(int index) {
            return mVehiclePropValue.value.byteValues[index];
        }

        /**
         * Gets the byte values.
         *
         * @return The byte values.
         */
        public byte[] getByteArray() {
            return mVehiclePropValue.value.byteValues;
        }

        /**
         * Get the string value.
         *
         * @return The stored string value.
         */
        public String getStringValue() {
            return mVehiclePropValue.value.stringValue;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                getPropId(),
                getAreaId(),
                getStatus(),
                getTimestamp(),
                Arrays.hashCode(mVehiclePropValue.value.int32Values),
                Arrays.hashCode(mVehiclePropValue.value.floatValues),
                Arrays.hashCode(mVehiclePropValue.value.int64Values),
                mVehiclePropValue.value.stringValue.hashCode(),
                Arrays.hashCode(mVehiclePropValue.value.byteValues));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AidlHalPropValue)) return false;
            if (!super.equals(o)) return false;
            AidlHalPropValue that = (AidlHalPropValue) o;
            return mVehiclePropValue.equals(that.mVehiclePropValue);
        }

        protected Float[] getFloatContainerArray() {
            int size =  getFloatValuesSize();
            Float[] array = new Float[size];
            for (int i = 0; i < size; i++) {
                array[i] = mVehiclePropValue.value.floatValues[i];
            }
            return array;
        }

        protected Integer[] getInt32ContainerArray() {
            int size = getInt32ValuesSize();
            Integer[] array = new Integer[size];
            for (int i = 0; i < size; i++) {
                array[i] = mVehiclePropValue.value.int32Values[i];
            }
            return array;
        }

        protected Long[] getInt64ContainerArray() {
            int size = getInt64ValuesSize();
            Long[] array = new Long[size];
            for (int i = 0; i < size; i++) {
                array[i] = mVehiclePropValue.value.int64Values[i];
            }
            return array;
        }

        private void init(int prop, int areaId, long timestamp, int status) {
            mVehiclePropValue = new android.hardware.automotive.vehicle.VehiclePropValue();
            mVehiclePropValue.areaId = areaId;
            mVehiclePropValue.timestamp = timestamp;
            mVehiclePropValue.prop = prop;
            mVehiclePropValue.status = status;
            mVehiclePropValue.value = emptyRawPropValues();
        }

        private void setCarProperty(CarPropertyValue carProp) {
            Object o = carProp.getValue();

            if (o instanceof Boolean) {
                mVehiclePropValue.value.int32Values = new int[]{((Boolean) o) ? 1 : 0};
            } else if (o instanceof Integer) {
                mVehiclePropValue.value.int32Values = new int[]{((Integer) o)};
            } else if (o instanceof Integer[]) {
                Integer[] array = (Integer[]) o;
                mVehiclePropValue.value.int32Values = new int[array.length];
                for (int i = 0; i < array.length; i++) {
                    mVehiclePropValue.value.int32Values[i] = array[i];
                }
            } else if (o instanceof Float) {
                mVehiclePropValue.value.floatValues = new float[]{((Float) o)};
            } else if (o instanceof Float[]) {
                Float[] array = (Float[]) o;
                mVehiclePropValue.value.floatValues = new float[array.length];
                for (int i = 0; i < array.length; i++) {
                    mVehiclePropValue.value.floatValues[i] = array[i];
                }
            } else if (o instanceof Long) {
                mVehiclePropValue.value.int64Values = new long[]{((Long) o)};
            } else if (o instanceof Long[]) {
                Long[] array = (Long[]) o;
                mVehiclePropValue.value.int64Values = new long[array.length];
                for (int i = 0; i < array.length; i++) {
                    mVehiclePropValue.value.int64Values[i] = array[i];
                }
            } else if (o instanceof String) {
                mVehiclePropValue.value.stringValue = (String) o;
            } else if (o instanceof byte[]) {
                byte[] array = (byte[]) o;
                mVehiclePropValue.value.byteValues = array;
            } else {
                throw new IllegalArgumentException("Unexpected type in: " + carProp);
            }
        }

        /**
         * Set the vehicle property value for MIXED type properties according to configArray.
         * configArray[0], 1 indicates the property has a String value.
         * configArray[1], 1 indicates the property has a Boolean value.
         * configArray[2], 1 indicates the property has a Integer value.
         * configArray[3], the number indicates the size of Integer[] in the property.
         * configArray[4], 1 indicates the property has a Long value.
         * configArray[5], the number indicates the size of Long[] in the property.
         * configArray[6], 1 indicates the property has a Float value.
         * configArray[7], the number indicates the size of Float[] in the property.
         * configArray[8], the number indicates the size of byte[] in the property.
         *
         * <p>For example: configArray = {1, 1, 1, 3, 0, 0, 0, 0, 0} indicates the property has a
         * String value, a Boolean value, an Integer value, an Integer array with 3 enums.
         */
        private void setMixedCarProperty(CarPropertyValue carProp, int[] configArray) {
            if (configArray.length != CONFIG_ARRAY_LENGTH) {
                throw new IllegalArgumentException("Unexpected configArray in:" + carProp);
            }

            Object[] values = (Object[]) carProp.getValue();
            int indexOfValues = 0;
            if (configArray[CONFIG_ARRAY_INDEX_STRING] != 0) {
                // Add a string value
                mVehiclePropValue.value.stringValue = (String) values[indexOfValues];
                indexOfValues++;
            }

            ArrayList<Integer> int32Values = new ArrayList<Integer>();
            ArrayList<Long> int64Values = new ArrayList<Long>();
            ArrayList<Byte> byteValues = new ArrayList<Byte>();
            ArrayList<Float> floatValues = new ArrayList<Float>();

            setMixedTypeValues(indexOfValues, values, configArray, int32Values, floatValues,
                    int64Values, byteValues);

            mVehiclePropValue.value.int32Values = toIntArray(int32Values);
            mVehiclePropValue.value.floatValues = toFloatArray(floatValues);
            mVehiclePropValue.value.int64Values = toLongArray(int64Values);
            mVehiclePropValue.value.byteValues = toByteArray(byteValues);
        }
    }

    private static class HidlHalPropValue extends HalPropValue {
        private android.hardware.automotive.vehicle.V2_0.VehiclePropValue mVehiclePropValue;

        HidlHalPropValue(int prop, int areaId, long timestamp, int status) {
            init(prop, areaId, timestamp, status);
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, int value) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int32Values = new ArrayList<Integer>(Arrays.asList(value));
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, int[] values) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int32Values = int32ArrayToList(values);
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, float value) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.floatValues = new ArrayList<Float>(Arrays.asList(value));
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, float[] values) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.floatValues = floatArrayToList(values);
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, long value) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int64Values = new ArrayList<Long>(Arrays.asList(value));
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, long[] values) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int64Values = int64ArrayToList(values);
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, byte[] values) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.bytes = byteArrayToList(values);
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, String value) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.stringValue = value;
        }

        HidlHalPropValue(int prop, int areaId, long timestamp, int status, int[] int32Values,
                float[] floatValues, long[] int64Values, String stringValue, byte[] byteValues) {
            init(prop, areaId, timestamp, status);
            mVehiclePropValue.value.int32Values = int32ArrayToList(int32Values);
            mVehiclePropValue.value.floatValues = floatArrayToList(floatValues);
            mVehiclePropValue.value.int64Values = int64ArrayToList(int64Values);
            mVehiclePropValue.value.stringValue = stringValue;
            mVehiclePropValue.value.bytes = byteArrayToList(byteValues);
        }

        HidlHalPropValue(CarPropertyValue value, int halPropId, HalPropConfig config) {
            init(halPropId, value.getAreaId(), 0, VehiclePropertyStatus.AVAILABLE);

            if (HalPropValue.isMixedTypeProperty(halPropId)) {
                setMixedCarProperty(value, config.getConfigArray());
            } else {
                setCarProperty(value);
            }
        }

        HidlHalPropValue(android.hardware.automotive.vehicle.V2_0.VehiclePropValue value) {
            mVehiclePropValue = value;

            // Make sure the stored VehiclePropValue does not contain any null values.
            if (mVehiclePropValue.value == null) {
                mVehiclePropValue.value =
                        new android.hardware.automotive.vehicle.V2_0.VehiclePropValue.RawValue();
                return;
            }
            if (mVehiclePropValue.value.int32Values == null) {
                mVehiclePropValue.value.int32Values = new ArrayList<Integer>();
            }
            if (mVehiclePropValue.value.floatValues == null) {
                mVehiclePropValue.value.floatValues = new ArrayList<Float>();
            }
            if (mVehiclePropValue.value.int64Values == null) {
                mVehiclePropValue.value.int64Values = new ArrayList<Long>();
            }
            if (mVehiclePropValue.value.bytes == null) {
                mVehiclePropValue.value.bytes = new ArrayList<Byte>();
            }
            if (mVehiclePropValue.value.stringValue == null) {
                mVehiclePropValue.value.stringValue = "";
            }
        }

        public Object toVehiclePropValue() {
            return mVehiclePropValue;
        }

        /**
         * Get the timestamp.
         *
         * @return The timestamp.
         */
        public long getTimestamp() {
            return mVehiclePropValue.timestamp;
        }

        /**
         * Get the area ID.
         *
         * @return The area ID.
         */
        public int getAreaId() {
            return mVehiclePropValue.areaId;
        }

        /**
         * Get the property ID.
         *
         * @return The property ID.
         */
        public int getPropId() {
            return mVehiclePropValue.prop;
        }

        /**
         * Get the property status.
         *
         * @return The property status.
         */
        public int getStatus() {
            return mVehiclePropValue.status;
        }

        /**
         * Get stored int32 values size.
         *
         * @return The size for the stored int32 values.
         */
        public int getInt32ValuesSize() {
            return mVehiclePropValue.value.int32Values.size();
        }

        /**
         * Get the int32 value at index.
         *
         * @param index The index.
         * @return The int32 value at index.
         */
        public int getInt32Value(int index) {
            return mVehiclePropValue.value.int32Values.get(index);
        }

        /**
         * Dump all int32 values as a string. Used for debugging.
         *
         * @return A String representation of all int32 values.
         */
        public String dumpInt32Values() {
            return Arrays.toString(mVehiclePropValue.value.int32Values.toArray());
        }

        /**
         * Get stored float values size.
         *
         * @return The size for the stored float values.
         */
        public int getFloatValuesSize() {
            return mVehiclePropValue.value.floatValues.size();
        }

        /**
         * Get the float value at index.
         *
         * @param index The index.
         * @return The float value at index.
         */
        public float getFloatValue(int index) {
            return mVehiclePropValue.value.floatValues.get(index);
        }

        /**
         * Dump all float values as a string. Used for debugging.
         *
         * @return A String representation of all float values.
         */
        public String dumpFloatValues() {
            return Arrays.toString(mVehiclePropValue.value.floatValues.toArray());
        }

        /**
         * Get stored inn64 values size.
         *
         * @return The size for the stored inn64 values.
         */
        public int getInt64ValuesSize() {
            return mVehiclePropValue.value.int64Values.size();
        }

        /**
         * Get the int64 value at index.
         *
         * @param index The index.
         * @return The int64 value at index.
         */
        public long getInt64Value(int index) {
            return mVehiclePropValue.value.int64Values.get(index);
        }

        /**
         * Dump all int64 values as a string. Used for debugging.
         *
         * @return A String representation of all int64 values.
         */
        public String dumpInt64Values() {
            return Arrays.toString(mVehiclePropValue.value.int64Values.toArray());
        }

        /**
         * Get stored byte values size.
         *
         * @return The size for the stored byte values.
         */
        public int getByteValuesSize() {
            return mVehiclePropValue.value.bytes.size();
        }

        /**
         * Get the byte value at index.
         *
         * @param index The index.
         * @return The byte value at index.
         */
        public byte getByteValue(int index) {
            return mVehiclePropValue.value.bytes.get(index);
        }

        /**
         * Gets the byte values.
         *
         * @return The byte values.
         */
        public byte[] getByteArray() {
            return toByteArray(mVehiclePropValue.value.bytes);
        }

        /**
         * Get the string value.
         *
         * @return The stored string value.
         */
        public String getStringValue() {
            return mVehiclePropValue.value.stringValue;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                getPropId(),
                getAreaId(),
                getStatus(),
                getTimestamp(),
                mVehiclePropValue.value.int32Values.hashCode(),
                mVehiclePropValue.value.floatValues.hashCode(),
                mVehiclePropValue.value.int64Values.hashCode(),
                mVehiclePropValue.value.stringValue.hashCode(),
                mVehiclePropValue.value.bytes.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HidlHalPropValue)) return false;
            if (!super.equals(o)) return false;
            HidlHalPropValue that = (HidlHalPropValue) o;
            return mVehiclePropValue.equals(that.mVehiclePropValue);
        }

        protected Float[] getFloatContainerArray() {
            return mVehiclePropValue.value.floatValues.toArray(new Float[getFloatValuesSize()]);
        }

        protected Integer[] getInt32ContainerArray() {
            return mVehiclePropValue.value.int32Values.toArray(new Integer[getInt32ValuesSize()]);
        }

        protected Long[] getInt64ContainerArray() {
            return mVehiclePropValue.value.int64Values.toArray(new Long[getInt64ValuesSize()]);
        }

        private void init(int prop, int areaId, long timestamp, int status) {
            mVehiclePropValue = new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
            mVehiclePropValue.areaId = areaId;
            mVehiclePropValue.timestamp = timestamp;
            mVehiclePropValue.prop = prop;
            mVehiclePropValue.status = status;
            // The default initializer would fill in empty array for the values.
            mVehiclePropValue.value =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropValue.RawValue();
        }

        private void setCarProperty(CarPropertyValue carProp) {
            Object o = carProp.getValue();

            if (o instanceof Boolean) {
                mVehiclePropValue.value.int32Values.add(((Boolean) o) ? 1 : 0);
            } else if (o instanceof Integer) {
                mVehiclePropValue.value.int32Values.add((Integer) o);
            } else if (o instanceof Integer[]) {
                Collections.addAll(mVehiclePropValue.value.int32Values, (Integer[]) o);
            } else if (o instanceof Float) {
                mVehiclePropValue.value.floatValues.add((Float) o);
            } else if (o instanceof Float[]) {
                Collections.addAll(mVehiclePropValue.value.floatValues, (Float[]) o);
            } else if (o instanceof Long) {
                mVehiclePropValue.value.int64Values.add((Long) o);
            } else if (o instanceof Long[]) {
                Collections.addAll(mVehiclePropValue.value.int64Values, (Long[]) o);
            } else if (o instanceof String) {
                mVehiclePropValue.value.stringValue = (String) o;
            } else if (o instanceof byte[]) {
                for (byte b : (byte[]) o) {
                    mVehiclePropValue.value.bytes.add(b);
                }
            } else {
                throw new IllegalArgumentException("Unexpected type in: " + carProp);
            }
        }

         /**
         * Set the vehicle property value for MIXED type properties according to configArray.
         * configArray[0], 1 indicates the property has a String value.
         * configArray[1], 1 indicates the property has a Boolean value.
         * configArray[2], 1 indicates the property has a Integer value.
         * configArray[3], the number indicates the size of Integer[] in the property.
         * configArray[4], 1 indicates the property has a Long value.
         * configArray[5], the number indicates the size of Long[] in the property.
         * configArray[6], 1 indicates the property has a Float value.
         * configArray[7], the number indicates the size of Float[] in the property.
         * configArray[8], the number indicates the size of byte[] in the property.
         *
         * <p>For example: configArray = {1, 1, 1, 3, 0, 0, 0, 0, 0} indicates the property has a
         * String value, a Boolean value, an Integer value, an Integer array with 3 enums.
         */
        private void setMixedCarProperty(CarPropertyValue carProp, int[] configArray) {
            if (configArray.length != CONFIG_ARRAY_LENGTH) {
                throw new IllegalArgumentException("Unexpected configArray in:" + carProp);
            }

            Object[] values = (Object[]) carProp.getValue();
            int indexOfValues = 0;
            if (configArray[CONFIG_ARRAY_INDEX_STRING] != 0) {
                // Add a string value
                mVehiclePropValue.value.stringValue = (String) values[indexOfValues];
                indexOfValues++;
            }

            ArrayList<Integer> int32Values = new ArrayList<Integer>();
            ArrayList<Long> int64Values = new ArrayList<Long>();
            ArrayList<Byte> byteValues = new ArrayList<Byte>();
            ArrayList<Float> floatValues = new ArrayList<Float>();

            setMixedTypeValues(indexOfValues, values, configArray, int32Values, floatValues,
                    int64Values, byteValues);

            mVehiclePropValue.value.int32Values = int32Values;
            mVehiclePropValue.value.floatValues = floatValues;
            mVehiclePropValue.value.int64Values = int64Values;
            mVehiclePropValue.value.bytes = byteValues;
        }
    }

    private static ArrayList<Integer> int32ArrayToList(int[] int32Array) {
        ArrayList<Integer> int32Values = new ArrayList<Integer>(int32Array.length);
        for (int v : int32Array) {
            int32Values.add(v);
        }
        return int32Values;
    }

    private static ArrayList<Float> floatArrayToList(float[] floatArray) {
        ArrayList<Float> floatValues = new ArrayList<Float>(floatArray.length);
        for (float v : floatArray) {
            floatValues.add(v);
        }
        return floatValues;
    }

    private static ArrayList<Long> int64ArrayToList(long[] int64Array) {
        ArrayList<Long> int64Values = new ArrayList<Long>(int64Array.length);
        for (long v : int64Array) {
            int64Values.add(v);
        }
        return int64Values;
    }

    private static ArrayList<Byte> byteArrayToList(byte[] byteArray) {
        ArrayList<Byte> byteValues = new ArrayList<Byte>(byteArray.length);
        for (byte v : byteArray) {
            byteValues.add(v);
        }
        return byteValues;
    }

    private static void setMixedTypeValues(int indexOfValues, Object[]values,
            int[] configArray, ArrayList<Integer> int32Values, ArrayList<Float> floatValues,
            ArrayList<Long> int64Values, ArrayList<Byte> byteValues) {

        int index = indexOfValues;
        if (configArray[CONFIG_ARRAY_INDEX_BOOLEAN] != 0) {
            // Add a boolean value
            int32Values.add((Boolean) values[index] ? 1 : 0); // in HAL, 1 indicates true
            index++;
        }

        /*
         * configArray[2], 1 indicates the property has a Integer value.
         * configArray[3], the number indicates the size of Integer[]  in the property.
         */
        int integerSize =
                configArray[CONFIG_ARRAY_INDEX_INT] + configArray[CONFIG_ARRAY_INDEX_INT_ARRAY];
        while (integerSize != 0) {
            int32Values.add((Integer) values[index]);
            index++;
            integerSize--;
        }
        /* configArray[4], 1 indicates the property has a Long value .
         * configArray[5], the number indicates the size of Long[]  in the property.
         */
        int longSize =
                configArray[CONFIG_ARRAY_INDEX_LONG] + configArray[CONFIG_ARRAY_INDEX_LONG_ARRAY];
        while (longSize != 0) {
            int64Values.add((Long) values[index]);
            index++;
            longSize--;
        }
        /* configArray[6], 1 indicates the property has a Float value .
         * configArray[7], the number indicates the size of Float[] in the property.
         */
        int floatSize =
                configArray[CONFIG_ARRAY_INDEX_FLOAT] + configArray[CONFIG_ARRAY_INDEX_FLOAT_ARRAY];
        while (floatSize != 0) {
            floatValues.add((Float) values[index]);
            index++;
            floatSize--;
        }

        /* configArray[8], the number indicates the size of byte[] in the property. */
        int byteSize = configArray[CONFIG_ARRAY_INDEX_BYTES];
        while (byteSize != 0) {
            byteValues.add((Byte) values[index]);
            index++;
            byteSize--;
        }
    }

    private static RawPropValues emptyRawPropValues() {
        RawPropValues values = new RawPropValues();
        values.int32Values = EMPTY_INT_ARRAY;
        values.floatValues = EMPTY_FLOAT_ARRAY;
        values.int64Values = EMPTY_LONG_ARRAY;
        values.byteValues = EMPTY_BYTE_ARRAY;
        values.stringValue = "";
        return values;
    }
}
