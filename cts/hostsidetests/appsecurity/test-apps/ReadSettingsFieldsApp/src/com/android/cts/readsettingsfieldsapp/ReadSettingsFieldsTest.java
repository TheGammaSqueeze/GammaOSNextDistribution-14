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

package com.android.cts.readsettingsfieldsapp;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.util.ArraySet;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

public class ReadSettingsFieldsTest extends AndroidTestCase {
    /** Test public keys are readable with annotation */
    public void testSecureNonHiddenSettingsKeysAreReadable() {
        testNonHiddenSettingsKeysAreReadable(Settings.Secure.class);
    }

    public void testSystemNonHiddenSettingsKeysAreReadable() {
        testNonHiddenSettingsKeysAreReadable(Settings.System.class);
    }

    public void testGlobalNonHiddenSettingsKeysAreReadable() {
        testNonHiddenSettingsKeysAreReadable(Settings.Global.class);
    }

    private <T extends Settings.NameValueTable> void testNonHiddenSettingsKeysAreReadable(
            Class<T> settingsClass) {
        for (String key : getNonHiddenSettingsKeys(settingsClass)) {
            try {
                callGetStringMethod(settingsClass, key);
            } catch (SecurityException ex) {
                if (isSettingsDeprecated(ex) || isAvailableForLowerOrEqualTargetedSDK(ex)) {
                    continue;
                }
                fail("Reading non-hidden " + settingsClass.getSimpleName() + " settings key <" + key
                        + "> should not raise exception! "
                        + "Did you forget to add @Readable annotation?\n" + ex.getMessage());
            }
        }
    }

    private <T extends Settings.NameValueTable> void callGetStringMethod(Class<T> settingsClass,
            String key) throws SecurityException {
        try {
            Method getStringMethod = settingsClass.getMethod("getString",
                    ContentResolver.class, String.class);
            getStringMethod.invoke(null, getContext().getContentResolver(), key);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            throw new SecurityException(e.getCause());
        }
    }

    private <T> ArraySet<String> getNonHiddenSettingsKeys(Class<T> settingsClass) {
        final ArraySet<String> publicSettingsKeys = new ArraySet<>();
        final Field[] allFields = settingsClass.getDeclaredFields();
        try {
            for (int i = 0; i < allFields.length; i++) {
                final Field field = allFields[i];
                if (field.getType().equals(String.class)) {
                    final Object value = field.get(settingsClass);
                    if (value.getClass().equals(String.class)) {
                        publicSettingsKeys.add((String) value);
                    }
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return publicSettingsKeys;
    }

    private boolean isSettingsDeprecated(SecurityException ex) {
        return ex.getMessage().contains("is deprecated and no longer accessible");
    }

    private boolean isAvailableForLowerOrEqualTargetedSDK(SecurityException ex) {
        return ex.getMessage().contains("targetSdkVersion lower than or equal");
    }

    /** Test hidden keys are readable with annotation */
    public void testSecureSomeHiddenSettingsKeysAreReadable() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.Secure.class);
        final String[] hiddenSettingsKeys = {"adaptive_sleep", "bugreport_in_power_menu",
                "input_methods_subtype_history"};
        testHiddenSettingsKeysReadable(Settings.Secure.class, publicSettingsKeys,
                hiddenSettingsKeys);
    }

    public void testSystemSomeHiddenSettingsKeysAreReadable() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.System.class);
        final String[] hiddenSettingsKeys = {"advanced_settings", "system_locales",
                "display_color_mode", "min_refresh_rate"};
        testHiddenSettingsKeysReadable(Settings.System.class, publicSettingsKeys,
                hiddenSettingsKeys);
    }

    public void testGlobalSomeHiddenSettingsKeysAreReadable() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.Secure.class);
        final String[] hiddenSettingsKeys = {"add_users_when_locked",
                "enable_accessibility_global_gesture_enabled"};
        testHiddenSettingsKeysReadable(Settings.Global.class, publicSettingsKeys,
                hiddenSettingsKeys);
    }

    private <T extends Settings.NameValueTable> void testHiddenSettingsKeysReadable(
            Class<T> settingsClass, ArraySet<String> publicKeys, String[] targetKeys) {
        for (String key : targetKeys) {
            // Verify that the hidden keys are not visible to the test app
            assertFalse("Settings key <" + key + "> should not be visible",
                    publicKeys.contains(key));
            try {
                // Verify that the hidden keys can still be read
                callGetStringMethod(settingsClass, key);
            } catch (SecurityException ex) {
                fail("Reading hidden " + settingsClass.getSimpleName() + " settings key <" + key
                        + "> should not raise!");
            }
        }
    }

    /** Test hidden keys are not readable without annotation */
    public void testSecureHiddenSettingsKeysNotReadableWithoutAnnotation() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.Secure.class);
        final String[] hiddenSettingsKeys = {"camera_autorotate",
                "location_time_zone_detection_enabled"};
        testHiddenSettingsKeysNotReadableWithoutAnnotation(Settings.Secure.class,
                publicSettingsKeys, hiddenSettingsKeys);
    }

    public void testSystemHiddenSettingsKeysNotReadableWithoutAnnotation() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.System.class);
        final String[] hiddenSettingsKeys = {"display_color_mode_vendor_hint"};
        testHiddenSettingsKeysNotReadableWithoutAnnotation(Settings.System.class,
                publicSettingsKeys, hiddenSettingsKeys);
    }

    public void testGlobalHiddenSettingsKeysNotReadableWithoutAnnotation() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.Global.class);
        final String[] hiddenSettingsKeys = {"restricted_networking_mode",
                "people_space_conversation_type"};
        testHiddenSettingsKeysNotReadableWithoutAnnotation(Settings.Global.class,
                publicSettingsKeys, hiddenSettingsKeys);
    }

    // test the cases that hidden keys are marked with readable annotation but access should be
    // protected by additional permission check.
    public void testGlobalHiddenSettingsKeyNotReadableWithoutPermissions() {
        final String[] hiddenSettingsKeysRequiresPermissions = {"multi_sim_data_call"};
        for (String key : hiddenSettingsKeysRequiresPermissions) {
            try {
                // Verify that the hidden keys can't be accessed due to lack of permissions.
                callGetStringMethod(Settings.Global.class, key);
            } catch (SecurityException ex) {
                assertTrue(ex.getMessage().contains("permission"));
                continue;
            }
            fail("Reading hidden " + Settings.Global.class.getSimpleName() + " settings key <" + key
                    + "> should be protected with permission!");
        }
    }

    private <T extends Settings.NameValueTable>
    void testHiddenSettingsKeysNotReadableWithoutAnnotation(
            Class<T> settingsClass, ArraySet<String> publicKeys, String[] targetKeys) {
        for (String key : targetKeys) {
            // Verify that the hidden keys are not visible to the test app
            assertFalse("Settings key <" + key + "> should not be visible",
                    publicKeys.contains(key));
            try {
                // Verify that the hidden keys cannot be read
                callGetStringMethod(settingsClass, key);
                fail("Reading hidden " + settingsClass.getSimpleName() + " settings key <" + key
                        + "> should raise!");
            } catch (SecurityException ex) {
                assertTrue(ex.getMessage().contains(
                        "Settings key: <" + key + "> is not readable."));
            }
        }
    }

    /** Test hidden keys are readable if the app is test only, even without annotation */
    public void testSecureHiddenSettingsKeysReadableWithoutAnnotation() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.Secure.class);
        final String[] hiddenSettingsKeys = {"camera_autorotate",
                "location_time_zone_detection_enabled"};
        testHiddenSettingsKeysReadable(Settings.Secure.class, publicSettingsKeys,
                hiddenSettingsKeys);
    }

    public void testSystemHiddenSettingsKeysReadableWithoutAnnotation() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.System.class);
        final String[] hiddenSettingsKeys = {"display_color_mode_vendor_hint"};
        testHiddenSettingsKeysReadable(Settings.System.class, publicSettingsKeys,
                hiddenSettingsKeys);
    }

    public void testGlobalHiddenSettingsKeysReadableWithoutAnnotation() {
        final ArraySet<String> publicSettingsKeys = getNonHiddenSettingsKeys(Settings.Global.class);
        final String[] hiddenSettingsKeys = {"restricted_networking_mode",
                "people_space_conversation_type"};
        testHiddenSettingsKeysReadable(Settings.Global.class, publicSettingsKeys,
                hiddenSettingsKeys);
    }


    public void testSettingsKeysNotReadableForAfterR() {
        final String keyWithTargetSdkR = "media_button_receiver";
        try {
            // Verify that the hidden key is not readable because of maxTargetSdk restriction
            callGetStringMethod(Settings.System.class, keyWithTargetSdkR);
            fail("Reading hidden settings key <" + keyWithTargetSdkR
                    + "> should raise!");
        } catch (SecurityException ex) {
            assertTrue(ex.getMessage().contains("targetSdkVersion"));
        }
    }

    public void testSettingsKeysReadableForRMinus() {
        final String keyWithTargetSdkR = "media_button_receiver";
        try {
            // Verify that the hidden key can still be read
            callGetStringMethod(Settings.System.class, keyWithTargetSdkR);
        } catch (SecurityException ex) {
            fail("Reading hidden settings key <" + keyWithTargetSdkR + "> should not raise!");
        }
    }

    public void testQueryGlobalSettingsNoHiddenKeysWithoutAnnotation() {
        checkQueryResults(Settings.Global.CONTENT_URI, Settings.Global.class);
    }

    public void testQuerySystemSettingsNoHiddenKeysWithoutAnnotation() {
        checkQueryResults(Settings.System.CONTENT_URI, Settings.System.class);
    }

    public void testQuerySecureSettingsNoHiddenKeysWithoutAnnotation() {
        checkQueryResults(Settings.Secure.CONTENT_URI, Settings.Secure.class);
    }

    /**
     * Check the result of ContentResolver.query() and make sure all the settings keys in the result
     * is Readable.
     */
    private <T extends Settings.NameValueTable> void checkQueryResults(Uri uri,
            Class<T> settingsClass) {
        try {
            final ContentResolver resolver = getContext().getContentResolver();
            final Cursor cursor = resolver.query(uri, new String[]{"name", "value"}, null,
                    null, null);
            assertTrue(cursor.getCount() > 0);
            while (cursor.moveToNext()) {
                final String key = cursor.getString(0);
                try {
                    // every key in the result should be readable
                    callGetStringMethod(settingsClass, key);
                } catch (SecurityException ex) {
                    if (isSettingsDeprecated(ex)) {
                        continue;
                    }
                    fail("Hidden settings key <" + key + "> should not be included in the "
                            + "query result!");
                }
            }
            cursor.close();
        } catch (Exception e) {
            fail("Failed to query ContentResolver: " + e.getMessage());
        }
    }

    public void testListGlobalSettingsNoHiddenKeysWithoutAnnotation() {
        checkListResults("LIST_global", Settings.Global.class);
    }

    public void testListSystemSettingsNoHiddenKeysWithoutAnnotation() {
        checkListResults("LIST_system", Settings.System.class);
    }

    public void testListSecureSettingsNoHiddenKeysWithoutAnnotation() {
        checkListResults("LIST_secure", Settings.Secure.class);
    }

    /**
     * Check the result of ContentResolver.call() and make sure all the settings keys in the result
     * is Readable.
     */
    private  <T extends Settings.NameValueTable> void checkListResults(String listSettingsType,
            Class<T> settingsClass) {
        try {
            final ContentResolver resolver = getContext().getContentResolver();
            final Uri uri;
            switch (listSettingsType) {
                case "LIST_global":
                    uri = Settings.Global.CONTENT_URI;
                    break;
                case "LIST_system":
                    uri = Settings.System.CONTENT_URI;
                    break;
                case "LIST_secure":
                    // fall through
                default:
                    uri = Settings.Secure.CONTENT_URI;
            }
            final Bundle data = resolver.call(uri, listSettingsType, null, null);
            final ArrayList<String> result = data.getStringArrayList("result_settings_list");
            assertTrue(result.size() > 0);
            Set<String> keysFromResolverCall = result.stream().map(line -> line.split("=")[0])
                    .collect(Collectors.toSet());

            // Verify result against resolver.query
            Cursor cursor = resolver.query(uri, new String[]{"name", "value"}, null, null, null);
            Set<String> keysFromResolverQuery = new ArraySet<>();
            while (cursor.moveToNext()) {
                keysFromResolverQuery.add(cursor.getString(0));
            }
            cursor.close();
            assertEquals(keysFromResolverQuery, keysFromResolverCall);

            for (String key : keysFromResolverCall) {
                try {
                    // every key in the result should be readable
                    callGetStringMethod(settingsClass, key);
                } catch (SecurityException ex) {
                    if (isSettingsDeprecated(ex)) {
                        continue;
                    }
                    fail("Non-readable settings key <" + key + "> should not be included in the "
                            + "query or call result!");
                }
            }
        } catch (Exception e) {
            fail("Failed to query ContentResolver: " + e.getMessage());
        }
    }
}

