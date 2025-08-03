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

package com.android.compatibility.common.util;

import android.os.Build;
import android.os.SystemProperties;

import java.lang.reflect.Field;

/**
 * Device-side compatibility utility class for reading device API level.
 */
public class ApiLevelUtil {
    // os.Build.VERSION.DEVICE_INITIAL_SDK_INT can be used here, but it was called
    // os.Build.VERSION.FIRST_SDK_INT in Android R and below. Using DEVICE_INITIAL_SDK_INT
    // will mean that the tests built in Android S and above can't be run on Android R and below.
    private static final int FIRST_API_LEVEL =
            SystemProperties.getInt("ro.product.first_api_level", 0);

    private static void verifyVersion(int version) {
        if (version == Build.VERSION_CODES.CUR_DEVELOPMENT) {
            throw new RuntimeException("Invalid version: " + version);
        }
    }

    public static boolean isBefore(int version) {
        verifyVersion(version);
        return Build.VERSION.SDK_INT < version;
    }

    public static boolean isBefore(String version) {
        return isBefore(resolveVersionString(version));
    }

    public static boolean isAfter(int version) {
        verifyVersion(version);
        return Build.VERSION.SDK_INT > version;
    }

    public static boolean isAfter(String version) {
        return isAfter(resolveVersionString(version));
    }

    public static boolean isAtLeast(int version) {
        verifyVersion(version);
        return Build.VERSION.SDK_INT >= version;
    }

    public static boolean isAtLeast(String version) {
        return isAtLeast(resolveVersionString(version));
    }

    public static boolean isAtMost(int version) {
        verifyVersion(version);
        return Build.VERSION.SDK_INT <= version;
    }

    public static boolean isAtMost(String version) {
        return isAtMost(resolveVersionString(version));
    }

    public static int getApiLevel() {
        return Build.VERSION.SDK_INT;
    }

    public static boolean isFirstApiBefore(int version) {
        verifyVersion(version);
        return FIRST_API_LEVEL < version;
    }

    public static boolean isFirstApiBefore(String version) {
        return isFirstApiBefore(resolveVersionString(version));
    }

    public static boolean isFirstApiAfter(int version) {
        verifyVersion(version);
        return FIRST_API_LEVEL > version;
    }

    public static boolean isFirstApiAfter(String version) {
        return isFirstApiAfter(resolveVersionString(version));
    }

    public static boolean isFirstApiAtLeast(int version) {
        return FIRST_API_LEVEL >= version;
    }

    public static boolean isFirstApiAtLeast(String version) {
        return isFirstApiAtLeast(resolveVersionString(version));
    }

    public static boolean isFirstApiAtMost(int version) {
        return FIRST_API_LEVEL <= version;
    }

    public static boolean isFirstApiAtMost(String version) {
        return isFirstApiAtMost(resolveVersionString(version));
    }

    public static int getFirstApiLevel() {
        return FIRST_API_LEVEL;
    }

    public static boolean codenameEquals(String name) {
        return Build.VERSION.CODENAME.equalsIgnoreCase(name.trim());
    }

    public static boolean codenameStartsWith(String prefix) {
        return Build.VERSION.CODENAME.startsWith(prefix);
    }

    public static String getCodename() {
        return Build.VERSION.CODENAME;
    }

    protected static int resolveVersionString(String versionString) {
        // Attempt 1: Parse version string as an integer, e.g. "23" for M
        try {
            return Integer.parseInt(versionString);
        } catch (NumberFormatException e) { /* ignore for alternate approaches below */ }
        // Attempt 2: Find matching field in VersionCodes utility class, return value
        try {
            Field versionField = VersionCodes.class.getField(versionString.toUpperCase());
            return versionField.getInt(null); // no instance for VERSION_CODES, use null
        } catch (IllegalAccessException | NoSuchFieldException e) { /* ignore */ }
        // Attempt 3: Find field within android.os.Build.VERSION_CODES
        try {
            Field versionField = Build.VERSION_CODES.class.getField(versionString.toUpperCase());
            return versionField.getInt(null); // no instance for VERSION_CODES, use null
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(
                    String.format("Failed to parse version string %s", versionString), e);
        }
    }
}
