/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.hal.test;

import android.annotation.CheckResult;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.os.SystemClock;

/** A builder class for {@link VehiclePropValue} */
public final class AidlVehiclePropValueBuilder {
    private final VehiclePropValue mPropValue;

    /**
     * Get a new builder based on the property ID.
     */
    public static AidlVehiclePropValueBuilder newBuilder(int propId) {
        return new AidlVehiclePropValueBuilder(propId);
    }

    /**
     * Get a new builder based on the {@link VehiclePropValue}.
     */
    public static AidlVehiclePropValueBuilder newBuilder(VehiclePropValue propValue) {
        return new AidlVehiclePropValueBuilder(propValue);
    }

    private AidlVehiclePropValueBuilder(int propId) {
        mPropValue = new VehiclePropValue();
        mPropValue.value = new RawPropValues();
        mPropValue.value.int32Values = new int[0];
        mPropValue.value.floatValues = new float[0];
        mPropValue.value.int64Values = new long[0];
        mPropValue.value.byteValues = new byte[0];
        mPropValue.value.stringValue = new String();
        mPropValue.prop = propId;
    }

    private AidlVehiclePropValueBuilder(VehiclePropValue propValue) {
        mPropValue = clone(propValue);
    }

    private VehiclePropValue clone(VehiclePropValue propValue) {
        VehiclePropValue newValue = new VehiclePropValue();

        newValue.prop = propValue.prop;
        newValue.areaId = propValue.areaId;
        newValue.status = propValue.status;
        newValue.timestamp = propValue.timestamp;
        newValue.value = new RawPropValues();
        newValue.value.stringValue = propValue.value.stringValue;
        if (propValue.value.int32Values != null) {
            newValue.value.int32Values = propValue.value.int32Values.clone();
        } else {
            newValue.value.int32Values = new int[0];
        }
        if (propValue.value.floatValues != null) {
            newValue.value.floatValues = propValue.value.floatValues.clone();
        } else {
            newValue.value.floatValues = new float[0];
        }
        if (propValue.value.int64Values != null) {
            newValue.value.int64Values = propValue.value.int64Values.clone();
        } else {
            newValue.value.int64Values = new long[0];
        }
        if (propValue.value.byteValues != null) {
            newValue.value.byteValues = propValue.value.byteValues.clone();
        } else {
            newValue.value.byteValues = new byte[0];
        }

        return newValue;
    }

    /**
     * Set the area ID.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder setAreaId(int areaId) {
        mPropValue.areaId = areaId;
        return this;
    }

    /**
     * Set the status
     */
    @CheckResult
    public AidlVehiclePropValueBuilder setStatus(@VehiclePropertyStatus int status) {
        mPropValue.status = status;
        return this;
    }

    /**
     * Set the timestamp.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder setTimestamp(long timestamp) {
        mPropValue.timestamp = timestamp;
        return this;
    }

    /**
     * Set the timestamp to the current time.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder setCurrentTimestamp() {
        mPropValue.timestamp = SystemClock.elapsedRealtimeNanos();
        return this;
    }

    /**
     * Add int32 values.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder addIntValues(int... values) {
        int oldSize = mPropValue.value.int32Values.length;
        int newSize = oldSize + values.length;
        int[] newValues = new int[newSize];
        for (int i = 0; i < oldSize; i++) {
            newValues[i] = mPropValue.value.int32Values[i];
        }
        for (int i = 0; i < values.length; i++) {
            newValues[oldSize + i] = values[i];
        }
        mPropValue.value.int32Values = newValues;
        return this;
    }

    /**
     * Add float values.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder addFloatValues(float... values) {
        int oldSize = mPropValue.value.floatValues.length;
        int newSize = oldSize + values.length;
        float[] newValues = new float[newSize];
        for (int i = 0; i < oldSize; i++) {
            newValues[i] = mPropValue.value.floatValues[i];
        }
        for (int i = 0; i < values.length; i++) {
            newValues[oldSize + i] = values[i];
        }
        mPropValue.value.floatValues = newValues;
        return this;
    }

    /**
     * Add byte values.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder addByteValues(byte... values) {
        int oldSize = mPropValue.value.byteValues.length;
        int newSize = oldSize + values.length;
        byte[] newValues = new byte[newSize];
        for (int i = 0; i < oldSize; i++) {
            newValues[i] = mPropValue.value.byteValues[i];
        }
        for (int i = 0; i < values.length; i++) {
            newValues[oldSize + i] = values[i];
        }
        mPropValue.value.byteValues = newValues;
        return this;
    }

    /**
     * Add int64 values.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder addInt64Values(long... values) {
        int oldSize = mPropValue.value.int64Values.length;
        int newSize = oldSize + values.length;
        long[] newValues = new long[newSize];
        for (int i = 0; i < oldSize; i++) {
            newValues[i] = mPropValue.value.int64Values[i];
        }
        for (int i = 0; i < values.length; i++) {
            newValues[oldSize + i] = values[i];
        }
        mPropValue.value.int64Values = newValues;
        return this;
    }

    /**
     * Set boolean value.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder setBooleanValue(boolean value) {
        mPropValue.value.int32Values = new int[1];
        mPropValue.value.int32Values[0] = value ? 1 : 0;
        return this;
    }

    /**
     * Set string value.
     */
    @CheckResult
    public AidlVehiclePropValueBuilder setStringValue(String val) {
        mPropValue.value.stringValue = val;
        return this;
    }

    /**
     * Build the {@link VehiclePropValue}.
     */
    public VehiclePropValue build() {
        return clone(mPropValue);
    }
}
