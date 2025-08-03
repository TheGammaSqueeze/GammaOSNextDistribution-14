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

import static com.android.car.internal.util.ConstantDebugUtils.toName;

import static java.lang.Integer.toHexString;

import android.annotation.Nullable;
import android.hardware.automotive.vehicle.EnumForVehicleProperty;
import android.hardware.automotive.vehicle.UnitsForVehicleProperty;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehicleAreaDoor;
import android.hardware.automotive.vehicle.VehicleAreaMirror;
import android.hardware.automotive.vehicle.VehicleAreaSeat;
import android.hardware.automotive.vehicle.VehicleAreaWheel;
import android.hardware.automotive.vehicle.VehicleAreaWindow;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.hardware.automotive.vehicle.VehicleUnit;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.car.hal.HalPropValue;
import com.android.car.internal.property.CarPropertyHelper;
import com.android.car.internal.util.ConstantDebugUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for converting {@link VehicleProperty} related information to human-readable names.
 */
public final class HalPropertyDebugUtils {
    private static final String TAG = HalPropertyDebugUtils.class.getSimpleName();
    private static final int MAX_BYTE_SIZE = 20;
    private static final AtomicReference<Map<Class<?>, List<Integer>>> sClazzToAreaBitsHolder =
            new AtomicReference<>();
    private static final String NO_VALUE = "NO_VALUE";


    /**
     * HalPropertyDebugUtils only contains static fields and methods and must never be
     * instantiated.
     */
    private HalPropertyDebugUtils() {
        throw new UnsupportedOperationException("Must never be called");
    }

    /**
     * Gets a user-friendly representation string representation of a {@code propertyId}.
     */
    public static String toPropertyIdString(int propertyId) {
        String hexSuffix = "(0x" + toHexString(propertyId) + ")";
        if (isSystemPropertyId(propertyId)) {
            return toName(VehicleProperty.class, propertyId) + hexSuffix;
        } else if (CarPropertyHelper.isVendorProperty(propertyId)) {
            return "VENDOR_PROPERTY" + hexSuffix;
        } else if (CarPropertyHelper.isBackportedProperty(propertyId)) {
            return "BACKPORTED_PROPERTY" + hexSuffix;
        }
        return "INVALID_PROPERTY_ID" + hexSuffix;
    }

    /**
     * Gets the HAL property's ID based on the passed name.
     */
    @Nullable
    public static Integer toPropertyId(String propertyName) {
        return ConstantDebugUtils.toValue(VehicleProperty.class, propertyName);
    }

    /**
     * Gets a user-friendly string representation of an {@code areaId} for the given
     * {@code propertyId}.
     */
    public static String toAreaIdString(int propertyId, int areaId) {
        switch (propertyId & VehicleArea.MASK) {
            case VehicleArea.GLOBAL -> {
                if (areaId == 0) {
                    return "GLOBAL(0x0)";
                }
                return "INVALID_GLOBAL_AREA_ID(0x" + toHexString(areaId) + ")";
            }
            case VehicleArea.DOOR -> {
                return convertAreaIdToDebugString(VehicleAreaDoor.class, areaId);
            }
            case VehicleArea.SEAT -> {
                if (areaId == VehicleAreaSeat.UNKNOWN) {
                    return toName(VehicleAreaSeat.class, areaId);
                }
                return convertAreaIdToDebugString(VehicleAreaSeat.class, areaId);
            }
            case VehicleArea.MIRROR -> {
                return convertAreaIdToDebugString(VehicleAreaMirror.class, areaId);
            }
            case VehicleArea.WHEEL -> {
                if (areaId == VehicleAreaWheel.UNKNOWN) {
                    return toName(VehicleAreaWheel.class, areaId);
                }
                return convertAreaIdToDebugString(VehicleAreaWheel.class, areaId);
            }
            case VehicleArea.WINDOW -> {
                return convertAreaIdToDebugString(VehicleAreaWindow.class, areaId);
            }
            default -> {
                return "UNKNOWN_AREA_ID(0x" + toHexString(areaId) + ")";
            }
        }
    }

    private static String convertAreaIdToDebugString(Class<?> clazz, int areaId) {
        String output = "";

        Map<Class<?>, List<Integer>> clazzToAreaBits = sClazzToAreaBitsHolder.get();
        if (clazzToAreaBits == null || clazzToAreaBits.get(clazz) == null) {
            clazzToAreaBits = getClazzToAreaBitsMapping(clazzToAreaBits, clazz);
            sClazzToAreaBitsHolder.set(clazzToAreaBits);
        }

        int areaBitMask = 0;
        for (int i = 0; i < clazzToAreaBits.get(clazz).size(); i++) {
            int areaBit = clazzToAreaBits.get(clazz).get(i).intValue();
            if (areaBit == 0) {
                continue;
            }
            areaBitMask |= areaBit;
            if ((areaId & areaBit) == areaBit) {
                if (!output.isEmpty()) {
                    output += "|";
                }
                output += toName(clazz, areaBit);
            }
        }

        if ((areaId | areaBitMask) != areaBitMask || output.isEmpty()) {
            output += "INVALID_" + clazz.getSimpleName() + "_AREA_ID";
        }

        output += "(0x" + toHexString(areaId) + ")";
        return output;
    }

    private static Map<Class<?>, List<Integer>> getClazzToAreaBitsMapping(
            @Nullable Map<Class<?>, List<Integer>> clazzToAreaBits, Class<?> clazz) {
        Map<Class<?>, List<Integer>> outputClazzToAreaBits;
        if (clazzToAreaBits == null) {
            outputClazzToAreaBits = new ArrayMap<>();
        } else {
            outputClazzToAreaBits = new ArrayMap<>(clazzToAreaBits.size());
            outputClazzToAreaBits.putAll(clazzToAreaBits);
        }

        List<Integer> areaBits = new ArrayList<>(ConstantDebugUtils.getValues(clazz));
        Collections.sort(areaBits, Collections.reverseOrder());

        outputClazzToAreaBits.put(clazz, areaBits);
        return outputClazzToAreaBits;
    }

    /**
     * Gets a user-friendly representation string representation of the value of a
     * {@link HalPropValue} instance.
     */
    public static String toValueString(HalPropValue halPropValue) {
        int propertyId = halPropValue.getPropId();
        int valueType = propertyId & VehiclePropertyType.MASK;
        String propertyUnits = getUnitsIfSupported(propertyId);
        StringJoiner stringJoiner = new StringJoiner(", ", "[", "]");
        switch (valueType) {
            case VehiclePropertyType.BOOLEAN -> {
                if (halPropValue.getInt32ValuesSize() != 1) {
                    return NO_VALUE;
                }
                return halPropValue.getInt32Value(0) == 0 ? "FALSE" : "TRUE";
            }
            case VehiclePropertyType.INT32 -> {
                if (halPropValue.getInt32ValuesSize() != 1) {
                    return NO_VALUE;
                }
                return getIntValueName(propertyId, halPropValue.getInt32Value(0), propertyUnits);
            }
            case VehiclePropertyType.INT32_VEC -> {
                for (int i = 0; i < halPropValue.getInt32ValuesSize(); i++) {
                    stringJoiner.add(getIntValueName(propertyId, halPropValue.getInt32Value(i),
                            propertyUnits));
                }
                return stringJoiner.toString();
            }
            case VehiclePropertyType.FLOAT -> {
                if (halPropValue.getFloatValuesSize() != 1) {
                    return NO_VALUE;
                }
                return halPropValue.getFloatValue(0) + propertyUnits;
            }
            case VehiclePropertyType.FLOAT_VEC -> {
                for (int i = 0; i < halPropValue.getFloatValuesSize(); i++) {
                    stringJoiner.add(halPropValue.getFloatValue(i) + propertyUnits);
                }
                return stringJoiner.toString();
            }
            case VehiclePropertyType.INT64 -> {
                if (halPropValue.getInt64ValuesSize() != 1) {
                    return NO_VALUE;
                }
                return halPropValue.getInt64Value(0) + propertyUnits;
            }
            case VehiclePropertyType.INT64_VEC -> {
                for (int i = 0; i < halPropValue.getInt64ValuesSize(); i++) {
                    stringJoiner.add(halPropValue.getInt64Value(i) + propertyUnits);
                }
                return stringJoiner.toString();
            }
            case VehiclePropertyType.STRING -> {
                return halPropValue.getStringValue();
            }
            case VehiclePropertyType.BYTES -> {
                String bytesString = "";
                byte[] byteValues = halPropValue.getByteArray();
                if (byteValues.length > MAX_BYTE_SIZE) {
                    byte[] bytes = Arrays.copyOf(byteValues, MAX_BYTE_SIZE);
                    bytesString = Arrays.toString(bytes);
                } else {
                    bytesString = Arrays.toString(byteValues);
                }
                return bytesString;
            }
        }
        String bytesString = "";
        byte[] byteValues = halPropValue.getByteArray();
        if (byteValues.length > MAX_BYTE_SIZE) {
            byte[] bytes = Arrays.copyOf(byteValues, MAX_BYTE_SIZE);
            bytesString = Arrays.toString(bytes);
        } else {
            bytesString = Arrays.toString(byteValues);
        }
        return "floatValues: " + halPropValue.dumpFloatValues() + ", int32Values: "
                + halPropValue.dumpInt32Values() + ", int64Values: "
                + halPropValue.dumpInt64Values() + ", bytes: " + bytesString + ", string: "
                + halPropValue.getStringValue();
    }

    private static String getIntValueName(int propertyId, int value, String propertyUnits) {
        if (EnumForVehicleProperty.values.containsKey(propertyId)) {
            for (int i = 0; i < EnumForVehicleProperty.values.get(propertyId).size(); i++) {
                Class<?> enumClazz = EnumForVehicleProperty.values.get(propertyId).get(i);
                String valueName = ConstantDebugUtils.toName(enumClazz, value);
                if (valueName != null) {
                    return valueName + "(0x" + toHexString(value) + ")";
                }
            }
            Slog.w(TAG,
                    "Failed to find enum name for property ID: " + toPropertyIdString(propertyId)
                            + " value: " + value);
        }
        return value + propertyUnits;
    }

    private static String getUnitsIfSupported(int propertyId) {
        if (!UnitsForVehicleProperty.values.containsKey(propertyId)) {
            return "";
        }
        Integer units = UnitsForVehicleProperty.values.get(propertyId);
        String unitsString = ConstantDebugUtils.toName(VehicleUnit.class, units);
        if (unitsString == null) {
            return "";
        }
        return " " + unitsString;
    }

    /**
     * Gets a user-friendly representation string representation of {@link VehicleArea}
     * constant for the passed {@code propertyId}.
     */
    public static String toAreaTypeString(int propertyId) {
        int areaType = propertyId & VehicleArea.MASK;
        return toDebugString(VehicleArea.class, areaType);
    }

    /**
     * Gets a user-friendly representation string representation of {@link VehiclePropertyGroup}
     * constant for the passed {@code propertyId}.
     */
    public static String toGroupString(int propertyId) {
        int group = propertyId & VehiclePropertyGroup.MASK;
        return toDebugString(VehiclePropertyGroup.class, group);
    }

    /**
     * Gets a user-friendly representation string representation of {@link VehiclePropertyType}
     * constant for the passed {@code propertyId}.
     */
    public static String toValueTypeString(int propertyId) {
        int valueType = propertyId & VehiclePropertyType.MASK;
        return toDebugString(VehiclePropertyType.class, valueType);
    }

    /**
     * Gets a user-friendly representation string representation of
     * {@link VehiclePropertyAccess} constant.
     */
    public static String toAccessString(int access) {
        return toDebugString(VehiclePropertyAccess.class, access);
    }

    /**
     * Gets a user-friendly representation string representation of
     * {@link VehiclePropertyChangeMode} constant.
     */
    public static String toChangeModeString(int changeMode) {
        return toDebugString(VehiclePropertyChangeMode.class, changeMode);
    }

    /**
     * Gets a user-friendly representation string representation of
     * {@link VehiclePropertyStatus} constant.
     */
    public static String toStatusString(int status) {
        return toDebugString(VehiclePropertyStatus.class, status);
    }

    private static String toDebugString(Class<?> clazz, int constantValue) {
        String hexSuffix = "(0x" + toHexString(constantValue) + ")";
        if (toName(clazz, constantValue) == null) {
            String invalidConstantValue = "INVALID_" + clazz.getSimpleName() + hexSuffix;
            Slog.e(TAG, invalidConstantValue);
            return invalidConstantValue;
        }
        return toName(clazz, constantValue) + hexSuffix;
    }

    /**
     * Returns {@code true} if {@code propertyId} is defined in {@link VehicleProperty}.
     * {@code false} otherwise.
     */
    private static boolean isSystemPropertyId(int propertyId) {
        return toName(VehicleProperty.class, propertyId) != null;
    }
}
