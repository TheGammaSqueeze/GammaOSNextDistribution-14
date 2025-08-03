/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.vehiclehal.test;

import android.car.hardware.CarPropertyValue;

import java.util.Arrays;

final class Utils {
    private Utils() {}

    private static final String TAG = concatTag(Utils.class);

    static String concatTag(Class clazz) {
        return "VehicleHalTest." + clazz.getSimpleName();
    }

    /**
     * Check the equality of two VehiclePropValue object ignoring timestamp and status.
     *
     * @param value1
     * @param value2
     * @return true if equal
     */
    static boolean areCarPropertyValuesEqual(CarPropertyValue value1, CarPropertyValue value2) {
        if (value1 == value2) {
            return true;
        }
        if (value1 == null || value2 == null) {
            return false;
        }
        if (value1.getPropertyId() != value2.getPropertyId()) {
            return false;
        }
        if (value1.getAreaId() != value2.getAreaId()) {
            return false;
        }

        if (value1.getValue().equals(value2.getValue())) {
            return true;
        }

        Object value1Values = value1.getValue();
        Object value2Values = value2.getValue();
        if (!(value1Values instanceof Object[]) || !(value2Values instanceof Object[])) {
            return false;
        }

        Object[] value1Objects = (Object[]) value1Values;
        Object[] value2Objects = (Object[]) value2Values;

        return Arrays.equals(value1Objects, value2Objects);
    }
}
