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

import android.hardware.automotive.vehicle.DiagnosticFloatSensorIndex;
import android.hardware.automotive.vehicle.DiagnosticIntegerSensorIndex;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehiclePropValue;

public final class DiagnosticJsonTestUtils {

    public static final int ANY_TIMESTAMP_VALUE = 1;

    public static VehiclePropValue buildEmptyVehiclePropertyValue(int expectedProperty,
            int expectedTimestamp) {
        VehiclePropValue expected = new VehiclePropValue();
        expected.prop = expectedProperty;
        expected.timestamp = expectedTimestamp;
        expected.value = new RawPropValues();

        int size = DiagnosticEventBuilder.getLastIndex(DiagnosticIntegerSensorIndex.class) + 1;
        expected.value.int32Values = new int[size];
        for (int i = 0; i < size; i++) {
            expected.value.int32Values[i] = 0;
        }
        size = DiagnosticEventBuilder.getLastIndex(DiagnosticFloatSensorIndex.class) + 1;
        expected.value.floatValues = new float[size];
        for (int i = 0; i < size; i++) {
            expected.value.floatValues[i] = 0f;
        }
        expected.value.int64Values = new long[0];
        expected.value.byteValues = new byte[0];
        expected.value.stringValue = new String();
        return expected;
    }

    private DiagnosticJsonTestUtils() {
    }
}
