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
package com.android.car.hal;

import static java.lang.Integer.toHexString;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.hardware.automotive.vehicle.VehiclePropertyType;


/**
 * Utility functions to work with {@link CarPropertyConfig} and {@link CarPropertyValue}
 */
/*package*/ final class CarPropertyUtils {

    /* Utility class has no public constructor */
    private CarPropertyUtils() {}

    /**
     * Gets the Java Class for the given property type.
     *
     * @param halType One of the {@link VehiclePropertyType}.
     * @return The java class for the type.
     */
    public static Class<?> getJavaClass(int halType) {
        switch (halType) {
            case VehiclePropertyType.BOOLEAN:
                return Boolean.class;
            case VehiclePropertyType.FLOAT:
                return Float.class;
            case VehiclePropertyType.INT32:
                return Integer.class;
            case VehiclePropertyType.INT64:
                return Long.class;
            case VehiclePropertyType.FLOAT_VEC:
                return Float[].class;
            case VehiclePropertyType.INT32_VEC:
                return Integer[].class;
            case VehiclePropertyType.INT64_VEC:
                return Long[].class;
            case VehiclePropertyType.STRING:
                return String.class;
            case VehiclePropertyType.BYTES:
                return byte[].class;
            case VehiclePropertyType.MIXED:
                return Object[].class;
            default:
                throw new IllegalArgumentException("Unexpected type: " + toHexString(halType));
        }
    }
}
