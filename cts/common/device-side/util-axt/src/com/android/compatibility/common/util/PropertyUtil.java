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

import androidx.test.InstrumentationRegistry;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Device-side utility class for reading properties and gathering information for testing
 * Android device compatibility.
 */
public class PropertyUtil {

    /**
     * Name of read-only property detailing the first API level for which the product was
     * shipped. Property should be undefined for factory ROM products.
     */
    public static final String FIRST_API_LEVEL = "ro.product.first_api_level";
    private static final String BOARD_API_LEVEL = "ro.board.api_level";
    private static final String BOARD_FIRST_API_LEVEL = "ro.board.first_api_level";
    private static final String BUILD_TYPE_PROPERTY = "ro.build.type";
    private static final String CAMERAX_EXTENSIONS_ENABLED = "ro.camerax.extensions.enabled";
    private static final String MANUFACTURER_PROPERTY = "ro.product.manufacturer";
    private static final String TAG_DEV_KEYS = "dev-keys";
    private static final String TAG_TEST_KEYS = "test-keys";
    private static final String VENDOR_API_LEVEL = "ro.vendor.api_level";
    private static final String VNDK_VERSION = "ro.vndk.version";

    public static final String GOOGLE_SETTINGS_QUERY =
            "content query --uri content://com.google.settings/partner";

    /** Value to be returned by getPropertyInt() if property is not found */
    public static int INT_VALUE_IF_UNSET = -1;

    /** API level for current in development */
    public static final int API_LEVEL_CURRENT = 10000;

    /** Returns whether the device build is a user build */
    public static boolean isUserBuild() {
        return propertyEquals(BUILD_TYPE_PROPERTY, "user");
    }

    /** Returns whether this build is built with dev-keys */
    public static boolean isDevKeysBuild() {
        return isBuildWithKeyType(TAG_DEV_KEYS);
    }

    /** Returns whether this build is built with test-keys */
    public static boolean isTestKeysBuild() {
        return isBuildWithKeyType(TAG_TEST_KEYS);
    }

    /**
     * Return the CameraX extensions enabled property value. If the read-only property is unset,
     * the default value returned will be 'false'.
     */
    public static boolean areCameraXExtensionsEnabled() {
        return getPropertyBoolean(CAMERAX_EXTENSIONS_ENABLED);
    }

    /**
     * Return the first API level for this product. If the read-only property is unset,
     * this means the first API level is the current API level, and the current API level
     * is returned.
     */
    public static int getFirstApiLevel() {
        int firstApiLevel = getPropertyInt(FIRST_API_LEVEL);
        return (firstApiLevel == INT_VALUE_IF_UNSET) ? Build.VERSION.SDK_INT : firstApiLevel;
    }

    /**
     * Return the API level that the VSR requirement must be fulfilled. It reads
     * ro.vendor.api_level. If not provided for old devices, read ro.product.first_api_level,
     * ro.board.api_level and ro.board.first_api_level to find the minimum required VSR api level of
     * the DUT.
     */
    public static int getVsrApiLevel() {
        int vendorApiLevel = getPropertyInt(VENDOR_API_LEVEL);
        if (vendorApiLevel != INT_VALUE_IF_UNSET) {
            return vendorApiLevel;
        }
        // Fallback to api level calculation for old devices.
        String[] boardApiLevelProps = {BOARD_API_LEVEL, BOARD_FIRST_API_LEVEL};
        for (String apiLevelProp : boardApiLevelProps) {
            int apiLevel = getPropertyInt(apiLevelProp);
            if (apiLevel != INT_VALUE_IF_UNSET) {
                return Math.min(apiLevel, getFirstApiLevel());
            }
        }
        return getFirstApiLevel();
    }

    /**
     * Return the API level of the vendor partition. It will read the following properties in order
     * and returns the value of the first defined property. If none of them are defined, or the
     * value is a VERSION CODENAME, returns the current API level which is defined in
     * API_LEVEL_CURRENT.
     *
     * <ul>
     *   <li> ro.board.api_level
     *   <li> ro.board.first_api_level
     *   <li> ro.vndk.version
     * </ul>
     */
    public static int getVendorApiLevel() {
        String[] vendorApiLevelProps = {
            // Use the properties in order.
            BOARD_API_LEVEL, BOARD_FIRST_API_LEVEL, VNDK_VERSION,
        };
        for (String prop : vendorApiLevelProps) {
            int apiLevel = getPropertyInt(prop);
            if (apiLevel != INT_VALUE_IF_UNSET) {
                return apiLevel;
            }
        }
        return API_LEVEL_CURRENT;
    }

    /**
     * Return whether the API level of the vendor partition is newer than the given API level.
     */
    public static boolean isVendorApiLevelNewerThan(int apiLevel) {
        return getVendorApiLevel() > apiLevel;
    }

    /**
     * Return whether the API level of the vendor partition is same or newer than the
     * given API level.
     */
    public static boolean isVendorApiLevelAtLeast(int apiLevel) {
        return getVendorApiLevel() >= apiLevel;
    }

    /**
     * Return whether the VNDK version of the vendor partition is newer than the given API level.
     * If the property is set to non-integer value, this means the vendor partition is using
     * current API level and true is returned.
     */
    public static boolean isVndkApiLevelNewerThan(int apiLevel) {
        int vndkApiLevel = getPropertyInt(VNDK_VERSION);
        if (vndkApiLevel == INT_VALUE_IF_UNSET) {
            return true;
        }
        return vndkApiLevel > apiLevel;
    }

    /**
     * Return whether the VNDK version of the vendor partition is same or newer than the
     * given API level.
     * If the property is set to non-integer value, this means the vendor partition is using
     * current API level and true is returned.
     */
    public static boolean isVndkApiLevelAtLeast(int apiLevel) {
        int vndkApiLevel = getPropertyInt(VNDK_VERSION);
        if (vndkApiLevel == INT_VALUE_IF_UNSET) {
            return true;
        }
        return vndkApiLevel >= apiLevel;
    }

    /**
     * Return the manufacturer of this product. If unset, return null.
     */
    public static String getManufacturer() {
        return getProperty(MANUFACTURER_PROPERTY);
    }

    /** Returns a mapping from client ID names to client ID values */
    public static Map<String, String> getClientIds() throws IOException {
        Map<String,String> clientIds = new HashMap<>();
        String queryOutput = SystemUtil.runShellCommand(
                InstrumentationRegistry.getInstrumentation(), GOOGLE_SETTINGS_QUERY);
        for (String line : queryOutput.split("[\\r?\\n]+")) {
            // Expected line format: "Row: 1 _id=123, name=<property_name>, value=<property_value>"
            Pattern pattern = Pattern.compile("name=([a-z_]*), value=(.*)$");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String name = matcher.group(1);
                String value = matcher.group(2);
                if (name.contains("client_id")) {
                    clientIds.put(name, value); // only add name-value pair for client ids
                }
            }
        }
        return clientIds;
    }

    /** Returns whether the property exists on this device */
    public static boolean propertyExists(String property) {
        return getProperty(property) != null;
    }

    /** Returns whether the property value is equal to a given string */
    public static boolean propertyEquals(String property, String value) {
        if (value == null) {
            return !propertyExists(property); // null value implies property does not exist
        }
        return value.equals(getProperty(property));
    }

    /**
     * Returns whether the property value matches a given regular expression. The method uses
     * String.matches(), requiring a complete match (i.e. expression matches entire value string)
     */
    public static boolean propertyMatches(String property, String regex) {
        if (regex == null || regex.isEmpty()) {
            // null or empty pattern implies property does not exist
            return !propertyExists(property);
        }
        String value = getProperty(property);
        return (value == null) ? false : value.matches(regex);
    }

    /**
     * Retrieves the desired boolean property, returning false if not found.
     */
    public static boolean getPropertyBoolean(String property) {
        String value = getProperty(property);
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Retrieves the desired integer property, returning INT_VALUE_IF_UNSET if not found.
     */
    public static int getPropertyInt(String property) {
        String value = getProperty(property);
        if (value == null) {
            return INT_VALUE_IF_UNSET;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return INT_VALUE_IF_UNSET;
        }
    }

    /** Retrieves the desired property value in string form */
    public static String getProperty(String property) {
        Scanner scanner = null;
        try {
            Process process = new ProcessBuilder("getprop", property).start();
            scanner = new Scanner(process.getInputStream());
            String value = scanner.nextLine().trim();
            return (value.isEmpty()) ? null : value;
        } catch (IOException e) {
            return null;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /** Retrieves a map of prop to value for all props with the given prefix */
    public static Map<String, String> getPropertiesWithPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        Pattern pattern = Pattern.compile("\\[(.*)\\]: \\[(.*)\\]");
        Scanner scanner = null;
        try {
            Process process = new ProcessBuilder("getprop").start();
            scanner = new Scanner(process.getInputStream());
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String prop = matcher.group(1);
                    String value = matcher.group(2);
                    if (prop.startsWith(prefix)) {
                        result.put(prop, value);
                    }
                }
            }
            return result;
        } catch (IOException e) {
            return result;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * Checks if the current build uses a specific type of key.
     *
     * @param keyType The type of key to check for (e.g., "dev-keys", "test-keys")
     * @return true if the build uses the specified key type, false otherwise
    */
    private static boolean isBuildWithKeyType(String keyType) {
        for (String tag : Build.TAGS.split(",")) {
            if (keyType.equals(tag.trim())) {
                return true;
            }
        }
        return false;
    }
}
