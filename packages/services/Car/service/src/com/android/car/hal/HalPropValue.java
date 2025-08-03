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

import static com.android.car.hal.property.HalPropertyDebugUtils.toAreaIdString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toPropertyIdString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toStatusString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toValueString;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.util.Log;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.property.CarPropertyHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * HalPropValue represents a vehicle property value.
 *
 * It could be used to convert between AIDL or HIDL VehiclePropValue used in vehicle HAL and
 * {@link CarPropertyValue} used in CarPropertyManager.
 */
public abstract class HalPropValue {
    private static final String TAG = HalPropValue.class.getSimpleName();

    /**
     * Gets the timestamp.
     *
     * @return The timestamp.
     */
    public abstract long getTimestamp();

    /**
     * Gets the area ID.
     *
     * @return The area ID.
     */
    public abstract int getAreaId();

    /**
     * Gets the property ID.
     *
     * @return The property ID.
     */
    public abstract int getPropId();

    /**
     * Gets the property status.
     *
     * @return The property status.
     */
    public abstract int getStatus();

    /**
     * Get stored int32 values size.
     *
     * @return The size for the stored int32 values.
     */
    public abstract int getInt32ValuesSize();

    /**
     * Gets the int32 value at index.
     *
     * @param index The index.
     * @return The int32 value at index.
     */
    public abstract int getInt32Value(int index);

    /**
     * Dump all int32 values as a string. Used for debugging.
     *
     * @return A String representation of all int32 values.
     */
    public abstract String dumpInt32Values();

    /**
     * Get stored float values size.
     *
     * @return The size for the stored float values.
     */
    public abstract int getFloatValuesSize();

    /**
     * Gets the float value at index.
     *
     * @param index The index.
     * @return The float value at index.
     */
    public abstract float getFloatValue(int index);

    /**
     * Dump all float values as a string. Used for debugging.
     *
     * @return A String representation of all float values.
     */
    public abstract String dumpFloatValues();

    /**
     * Get stored inn64 values size.
     *
     * @return The size for the stored inn64 values.
     */
    public abstract int getInt64ValuesSize();

    /**
     * Gets the int64 value at index.
     *
     * @param index The index.
     * @return The int64 value at index.
     */
    public abstract long getInt64Value(int index);

    /**
     * Dump all int64 values as a string. Used for debugging.
     *
     * @return A String representation of all int64 values.
     */
    public abstract String dumpInt64Values();

    /**
     * Get stored byte values size.
     *
     * @return The size for the stored byte values.
     */
    public abstract int getByteValuesSize();

    /**
     * Gets the byte value at index.
     *
     * @param index The index.
     * @return The byte value at index.
     */
    public abstract byte getByteValue(int index);

    /**
     * Gets the byte values.
     *
     * @return The byte values.
     */
    public abstract byte[] getByteArray();

    /**
     * Gets the string value.
     *
     * @return The stored string value.
     */
    public abstract String getStringValue();

    /**
     * Converts to an AIDL/HIDL VehiclePropValue that could be used to sent to vehicle HAL.
     *
     * @return An AIDL or HIDL VehiclePropValue.
     */
    public abstract Object toVehiclePropValue();

    /**
     * Turns this class to a {@link CarPropertyValue}.
     *
     * @param mgrPropId The property ID used in {@link android.car.VehiclePropertyIds}.
     * @param config The config for the property.
     * @return A CarPropertyValue that could be passed to upper layer
     * @throws IllegalStateException If property has unsupported type
     */
    public CarPropertyValue toCarPropertyValue(int mgrPropId, HalPropConfig config) {
        if (isMixedTypeProperty(getPropId())) {
            int[] configArray = config.getConfigArray();
            boolean containStringType = configArray[0] == 1;
            boolean containBooleanType = configArray[1] == 1;
            return toMixedCarPropertyValue(mgrPropId, containBooleanType, containStringType);
        }
        return toCarPropertyValue(mgrPropId);
    }

    /**
     * Check whether this property is equal to another property.
     *
     * @param argument The property to compare.
     * @return true if equal, false if not.
     */
    @Override
    public boolean equals(Object argument) {
        if (!(argument instanceof HalPropValue)) {
            return false;
        }

        HalPropValue other = (HalPropValue) argument;

        if (other.getPropId() != getPropId()) {
            Log.i(TAG, "Property ID mismatch, got " + other.getPropId() + " want "
                    + getPropId());
            return false;
        }
        if (other.getAreaId() != getAreaId()) {
            Log.i(TAG, "Area ID mismatch, got " + other.getAreaId() + " want " + getAreaId());
            return false;
        }
        if (other.getStatus() != getStatus()) {
            Log.i(TAG, "Status mismatch, got " + other.getStatus() + " want " + getStatus());
            return false;
        }
        if (other.getTimestamp() != getTimestamp()) {
            Log.i(TAG, "Timestamp mismatch, got " + other.getTimestamp() + " want "
                    + getTimestamp());
            return false;
        }
        if (!equalInt32Values(other)) {
            Log.i(TAG, "Int32Values mismatch, got " + other.dumpInt32Values() + " want "
                    + dumpInt32Values());
            return false;
        }
        if (!equalFloatValues(other)) {
            Log.i(TAG, "FloatValues mismatch, got " + other.dumpFloatValues() + " want "
                    + dumpFloatValues());
            return false;
        }
        if (!equalInt64Values(other)) {
            Log.i(TAG, "Int64Values mismatch, got " + other.dumpInt64Values() + " want "
                    + dumpInt64Values());
            return false;
        }
        if (!Arrays.equals(other.getByteArray(), getByteArray())) {
            Log.i(TAG, "ByteValues mismatch, got " + Arrays.toString(other.getByteArray())
                    + " want " + Arrays.toString(getByteArray()));
            return false;
        }
        if (!other.getStringValue().equals(getStringValue())) {
            Log.i(TAG, "StringValue mismatch, got " + other.getStringValue() + " want "
                    + getStringValue());
            return false;
        }
        return true;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public String toString() {
        StringJoiner debugStringJoiner = new StringJoiner(", ", "{", "}");
        debugStringJoiner.add("Property ID: " + toPropertyIdString(getPropId()));
        debugStringJoiner.add("Area ID: " + toAreaIdString(getPropId(), getAreaId()));
        debugStringJoiner.add("ElapsedRealtimeNanos: " + getTimestamp());
        debugStringJoiner.add("Status: " + toStatusString(getStatus()));
        debugStringJoiner.add("Value: " + toValueString(this));
        return "HalPropValue" + debugStringJoiner;
    }

    /**
     * Get the hashCode for this value.
     */
    @Override
    public abstract int hashCode();

    protected static boolean isMixedTypeProperty(int prop) {
        return (prop & VehiclePropertyType.MASK) == VehiclePropertyType.MIXED;
    }

    protected abstract Float[] getFloatContainerArray();

    protected abstract Integer[] getInt32ContainerArray();

    protected abstract Long[] getInt64ContainerArray();

    private CarPropertyValue<?> toCarPropertyValue(int propertyId) {
        Class<?> clazz = CarPropertyUtils.getJavaClass(getPropId() & VehiclePropertyType.MASK);
        int areaId = getAreaId();
        int status = vehiclePropertyStatusToCarPropertyStatus(getStatus());
        long timestampNanos = getTimestamp();
        Object value = null;

        if (Boolean.class == clazz) {
            if (getInt32ValuesSize() > 0) {
                value = Boolean.valueOf(getInt32Value(0) == 1);
            } else if (status == CarPropertyValue.STATUS_AVAILABLE) {
                status = CarPropertyValue.STATUS_ERROR;
            }
        } else if (Float.class == clazz) {
            if (getFloatValuesSize() > 0) {
                value = Float.valueOf(getFloatValue(0));
            } else if (status == CarPropertyValue.STATUS_AVAILABLE) {
                status = CarPropertyValue.STATUS_ERROR;
            }
        } else if (Integer.class == clazz) {
            if (getInt32ValuesSize() > 0) {
                value = Integer.valueOf(getInt32Value(0));
            } else if (status == CarPropertyValue.STATUS_AVAILABLE) {
                status = CarPropertyValue.STATUS_ERROR;
            }
        } else if (Long.class == clazz) {
            if (getInt64ValuesSize() > 0) {
                value = Long.valueOf(getInt64Value(0));
            } else if (status == CarPropertyValue.STATUS_AVAILABLE) {
                status = CarPropertyValue.STATUS_ERROR;
            }
        } else if (Float[].class == clazz) {
            value = getFloatContainerArray();
        } else if (Integer[].class == clazz) {
            value = getInt32ContainerArray();
        } else if (Long[].class == clazz) {
            value = getInt64ContainerArray();
        } else if (String.class == clazz) {
            value = getStringValue();
        } else if (byte[].class == clazz) {
            value = getByteArray();
        } else {
            throw new IllegalStateException(
                    "Unexpected type " + clazz + " - propertyId: " + VehiclePropertyIds.toString(
                            propertyId));
        }
        if (value == null) {
            value = CarPropertyHelper.getDefaultValue(clazz);
        }
        return new CarPropertyValue<>(propertyId, areaId, status, timestampNanos, value);
    }

    private CarPropertyValue<?> toMixedCarPropertyValue(
            int propertyId, boolean containBoolean, boolean containString) {
        int areaId = getAreaId();
        int status = vehiclePropertyStatusToCarPropertyStatus(getStatus());
        long timestampNanos = getTimestamp();

        List<Object> valuesList = new ArrayList<>();
        if (containString) {
            valuesList.add(getStringValue());
        }
        if (containBoolean) {
            if (getInt32ValuesSize() > 0) {
                boolean boolValue = getInt32Value(0) == 1;
                valuesList.add(boolValue);
                for (int i = 1; i < getInt32ValuesSize(); i++) {
                    valuesList.add(getInt32Value(i));
                }
            } else if (status == CarPropertyValue.STATUS_AVAILABLE) {
                status = CarPropertyValue.STATUS_ERROR;
            }
        } else {
            for (int i = 0; i < getInt32ValuesSize(); i++) {
                valuesList.add(getInt32Value(i));
            }
        }
        for (int i = 0; i < getInt64ValuesSize(); i++) {
            valuesList.add(getInt64Value(i));
        }
        for (int i = 0; i < getFloatValuesSize(); i++) {
            valuesList.add(getFloatValue(i));
        }
        for (int i = 0; i < getByteValuesSize(); i++) {
            valuesList.add(getByteValue(i));
        }
        return new CarPropertyValue<>(propertyId, areaId, status, timestampNanos,
                valuesList.toArray());
    }

    private boolean equalInt32Values(HalPropValue argument) {
        if (getInt32ValuesSize() != argument.getInt32ValuesSize()) {
            return false;
        }
        for (int i = 0; i < getInt32ValuesSize(); i++) {
            if (getInt32Value(i) != argument.getInt32Value(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean equalFloatValues(HalPropValue argument) {
        if (getFloatValuesSize() != argument.getFloatValuesSize()) {
            return false;
        }
        for (int i = 0; i < getFloatValuesSize(); i++) {
            if (getFloatValue(i) != argument.getFloatValue(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean equalInt64Values(HalPropValue argument) {
        if (getInt64ValuesSize() != argument.getInt64ValuesSize()) {
            return false;
        }
        for (int i = 0; i < getInt64ValuesSize(); i++) {
            if (getInt64Value(i) != argument.getInt64Value(i)) {
                return false;
            }
        }
        return true;
    }

    private static @CarPropertyValue.PropertyStatus int vehiclePropertyStatusToCarPropertyStatus(
            @VehiclePropertyStatus int status) {
        switch (status) {
            case VehiclePropertyStatus.AVAILABLE:
                return CarPropertyValue.STATUS_AVAILABLE;
            case VehiclePropertyStatus.ERROR:
                return CarPropertyValue.STATUS_ERROR;
            case VehiclePropertyStatus.UNAVAILABLE:
                return CarPropertyValue.STATUS_UNAVAILABLE;
        }
        return CarPropertyValue.STATUS_ERROR;
    }
}
