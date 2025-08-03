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

package com.android.car.internal.util;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.PRIVATE_CONSTRUCTOR;

import android.annotation.Nullable;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class to convert integer constants to and from their value or name.
 *
 * @hide
 */
public final class ConstantDebugUtils {
    private static final String TAG = ConstantDebugUtils.class.getSimpleName();
    private static final AtomicReference<Map<Class<?>, ConstantDebugUtils>>
            CLAZZ_TO_CONSTANT_DEBUG_UTILS_HOLDER = new AtomicReference<>();
    private final Field[] mClazzDeclaredFields;
    /*
     * Used to cache the mapping of property names to IDs. This
     * will be initialized during the first usage.
     */
    private final AtomicReference<ArrayMap<String, Integer>> mNameToValueHolder =
            new AtomicReference<>();
    /*
     * Used to cache the mapping of property IDs to names. This
     * will be initialized during the first usage.
     */
    private final AtomicReference<SparseArray<String>> mValueToNameHolder = new AtomicReference<>();

    @ExcludeFromCodeCoverageGeneratedReport(reason = PRIVATE_CONSTRUCTOR)
    private ConstantDebugUtils(Class<?> clazz) {
        mClazzDeclaredFields = clazz.getDeclaredFields();
    }

    /**
     * Gets the constant's name based on the value for the specified {@code clazz}. Returns
     * {@code null} if value does not exist.
     */
    @Nullable
    public static String toName(Class<?> clazz, int value) {
        return cacheClazzToConstantDebugUtilsMapping(clazz).toName(value);
    }

    /**
     * Gets the constant's value based on the passed name for the specified {@code clazz}. Returns
     * {@code null} if name does not exist.
     */
    @Nullable
    public static Integer toValue(Class<?> clazz, String name) {
        return cacheClazzToConstantDebugUtilsMapping(clazz).toValue(name);
    }

    /**
     * Gets the all the constant values for the specified {@code clazz}.
     */
    public static Collection<Integer> getValues(Class<?> clazz) {
        return cacheClazzToConstantDebugUtilsMapping(
                clazz).cacheConstantNameToValueMapping().values();
    }

    private static ConstantDebugUtils cacheClazzToConstantDebugUtilsMapping(Class<?> clazz) {

        Map<Class<?>, ConstantDebugUtils> clazzToConstantDebugUtils =
                CLAZZ_TO_CONSTANT_DEBUG_UTILS_HOLDER.get();
        if (clazzToConstantDebugUtils == null || clazzToConstantDebugUtils.get(clazz) == null) {
            clazzToConstantDebugUtils = getClazzToConstantDebugUtilsMapping(
                    clazzToConstantDebugUtils, clazz);
            CLAZZ_TO_CONSTANT_DEBUG_UTILS_HOLDER.set(clazzToConstantDebugUtils);
        }
        return clazzToConstantDebugUtils.get(clazz);
    }

    private static Map<Class<?>, ConstantDebugUtils> getClazzToConstantDebugUtilsMapping(
            @Nullable Map<Class<?>, ConstantDebugUtils> clazzToConstantDebugUtils, Class<?> clazz) {
        Map<Class<?>, ConstantDebugUtils> outputClazzToConstantDebugsUtils;
        if (clazzToConstantDebugUtils == null) {
            outputClazzToConstantDebugsUtils = new ArrayMap<>();
        } else {
            outputClazzToConstantDebugsUtils = new ArrayMap<>(clazzToConstantDebugUtils.size());
            outputClazzToConstantDebugsUtils.putAll(clazzToConstantDebugUtils);
        }
        outputClazzToConstantDebugsUtils.put(clazz, new ConstantDebugUtils(clazz));
        return outputClazzToConstantDebugsUtils;
    }

    private static boolean isIntConstant(Field field) {
        // We only want public static final int values
        return field.getType() == int.class && field.getModifiers() == (Modifier.STATIC
                | Modifier.FINAL | Modifier.PUBLIC);
    }

    @Nullable
    private String toName(int value) {
        return cacheConstantValueToNameMapping().get(value);
    }

    @Nullable
    private Integer toValue(String name) {
        return cacheConstantNameToValueMapping().get(name);
    }

    private ArrayMap<String, Integer> cacheConstantNameToValueMapping() {
        ArrayMap<String, Integer> nameToValue = mNameToValueHolder.get();
        if (nameToValue == null) {
            nameToValue = getConstantNameToValueMapping();
            mNameToValueHolder.compareAndSet(null, nameToValue);
        }
        return nameToValue;
    }

    private SparseArray<String> cacheConstantValueToNameMapping() {
        SparseArray<String> valueToName = mValueToNameHolder.get();
        if (valueToName == null) {
            valueToName = getConstantValueToNameMapping();
            mValueToNameHolder.compareAndSet(null, valueToName);
        }
        return valueToName;
    }

    /**
     * Creates a mapping property names to their IDs.
     */
    private ArrayMap<String, Integer> getConstantNameToValueMapping() {
        ArrayMap<String, Integer> constantNameToValue = new ArrayMap<>();
        for (int i = 0; i < mClazzDeclaredFields.length; i++) {
            Field candidateField = mClazzDeclaredFields[i];
            try {
                if (isIntConstant(candidateField)) {
                    constantNameToValue.put(candidateField.getName(), candidateField.getInt(null));
                }
            } catch (IllegalAccessException e) {
                Log.wtf(TAG, "Failed trying to find value for " + candidateField.getName(), e);
            }
        }
        return constantNameToValue;
    }

    /**
     * Creates a SparseArray mapping constant values to their String representations
     * directly from this class.
     */
    private SparseArray<String> getConstantValueToNameMapping() {
        SparseArray<String> constantValueToName = new SparseArray<>();
        for (int i = 0; i < mClazzDeclaredFields.length; i++) {
            Field candidateField = mClazzDeclaredFields[i];
            try {
                if (isIntConstant(candidateField)) {
                    constantValueToName.put(candidateField.getInt(null), candidateField.getName());
                }
            } catch (IllegalAccessException e) {
                Log.wtf(TAG, "Failed trying to find value for " + candidateField.getName(), e);
            }
        }
        return constantValueToName;
    }
}
