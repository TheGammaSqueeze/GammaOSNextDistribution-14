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

package com.android.car.internal.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

// Copied from frameworks/base and kept only used codes
/**
 * <p>Various utilities for debugging and logging.</p>
 *
 * @hide
 */
public final class DebugUtils {
    private DebugUtils() {}

    /**
     * Use prefixed constants (static final values) on given class to turn value
     * into human-readable string.
     *
     * @hide
     */
    public static String valueToString(Class<?> clazz, String prefix, int value) {
        for (Field field : clazz.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    && field.getType().equals(int.class) && field.getName().startsWith(prefix)) {
                try {
                    if (value == field.getInt(null)) {
                        return constNameWithoutPrefix(prefix, field);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return Integer.toString(value);
    }

    /**
     * Use prefixed constants (static final values) on given class to turn flags
     * into human-readable string.
     *
     * @hide
     */
    public static String flagsToString(Class<?> clazz, String prefix, int flagsToConvert) {
        int flags = flagsToConvert;
        final StringBuilder res = new StringBuilder();
        boolean flagsWasZero = flags == 0;

        for (Field field : clazz.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    && field.getType().equals(int.class) && field.getName().startsWith(prefix)) {
                try {
                    final int value = field.getInt(null);
                    if (value == 0 && flagsWasZero) {
                        return constNameWithoutPrefix(prefix, field);
                    }
                    if (value != 0 && (flags & value) == value) {
                        flags &= ~value;
                        res.append(constNameWithoutPrefix(prefix, field)).append('|');
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        if (flags != 0 || res.length() == 0) {
            res.append(Integer.toHexString(flags));
        } else {
            res.deleteCharAt(res.length() - 1);
        }
        return res.toString();
    }

    /**
     * Gets human-readable representation of constants (static final values).
     *
     * @see #constantToString(Class, String, int)
     *
     * @hide
     */
    public static String constantToString(Class<?> clazz, int value) {
        return constantToString(clazz, "", value);
    }

    /**
     * Gets human-readable representation of constants (static final values).
     *
     * @hide
     */
    public static String constantToString(Class<?> clazz, String prefix, int value) {
        for (Field field : clazz.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            try {
                if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                        && field.getType().equals(int.class) && field.getName().startsWith(prefix)
                        && field.getInt(null) == value) {
                    return constNameWithoutPrefix(prefix, field);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return prefix + Integer.toString(value);
    }

    private static String constNameWithoutPrefix(String prefix, Field field) {
        return field.getName().substring(prefix.length());
    }
}
