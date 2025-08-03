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

package com.android.car.hal.property;

import android.hardware.automotive.vehicle.EnumForVehicleProperty;

import com.android.car.internal.util.ConstantDebugUtils;

/**
 * Utility class for converting HAL values to readable names.
 */
public final class HalValueDebugUtils {
    /**
     * HalValueDebugUtils only contains static fields and methods and must never be
     * instantiated.
     */
    private HalValueDebugUtils() {
        throw new UnsupportedOperationException("Must never be called");
    }

    /**
     * Gets a user-friendly string representation of a {@code value} for the given
     * {@code propertyId}.
     */
    public static String toDebugString(int propertyId, Object value) {
        if (value instanceof Integer && EnumForVehicleProperty.values.containsKey(propertyId)) {
            for (int i = 0; i < EnumForVehicleProperty.values.get(propertyId).size(); i++) {
                Class<?> enumClazz = EnumForVehicleProperty.values.get(propertyId).get(i);
                String valueName = ConstantDebugUtils.toName(enumClazz,
                        ((Integer) value).intValue());
                if (valueName != null) {
                    return valueName;
                }
            }
        }
        return value.toString();
    }
}
