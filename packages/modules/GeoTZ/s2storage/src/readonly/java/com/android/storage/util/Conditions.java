/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.storage.util;

/** Useful condition check methods. */
public final class Conditions {

    private Conditions() {
    }

    /**
     * Throws {@link IllegalArgumentException} with a suitable message if value is outside of the
     * inclusive range specified.
     */
    public static void checkArgInRange(String valueName, int value, int minIncl, int maxIncl) {
        if (minIncl > value || value > maxIncl) {
            throw new IllegalArgumentException(
                    valueName + "=" + value + " must be between " + minIncl + " and " + maxIncl);
        }
    }

    /**
     * Throws {@link IllegalArgumentException} with a suitable message if value is outside of the
     * inclusive range specified.
     */
    public static void checkArgInRange(
            String valueName, int value, String minName, int minIncl, String maxName, int maxIncl) {
        if (minIncl > value || value > maxIncl) {
            throw new IllegalArgumentException(valueName + "=" + value + " must be between "
                    + minName + "=" + minIncl + " and " + maxName + "=" + maxIncl);
        }
    }

    /**
     * Throws {@link IllegalStateException} with a suitable message if value is outside of the
     * inclusive range specified.
     */
    public static void checkStateInRange(
            String valueName, int value, String minName, int minIncl, String maxName, int maxIncl) {
        if (minIncl > value || value > maxIncl) {
            throw new IllegalStateException(valueName + "=" + value + " must be between "
                    + minName + "=" + minIncl + " and " + maxName + "=" + maxIncl);
        }
    }
}
