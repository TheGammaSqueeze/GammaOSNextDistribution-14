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

package android.devicepolicy.cts.utils;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import java.util.Arrays;

/**
 * Utility class for {@link Bundle} related operations.
 */
public final class BundleUtils {

    private static final String TAG = BundleUtils.class.getSimpleName();

    private static final String[] TEST_STRINGS = new String[]{
            "<bad/>",
            ">worse!\"£$%^&*()'<",
            "<JSON>\"{ \\\"One\\\": { \\\"OneOne\\\": \\\"11\\\", \\\""
                    + "OneTwo\\\": \\\"12\\\" }, \\\"Two\\\": \\\"2\\\" } <JSON/>\""
    };

    private BundleUtils() {}

    /**
     * Returns a {@link Bundle} that is uniquely identified by the provided {@code id}, see
     * {@link #assertEqualToBundle(String, Bundle)}.
     */
    // Should be consistent with assertEqualToBundle
    public static Bundle createBundle(String id) {
        Bundle result = new Bundle();
        // Tests for 6 allowed types: Integer, Boolean, String, String[], Bundle and Parcelable[]
        // Also test for string escaping handling
        result.putBoolean("boolean_0", false);
        result.putBoolean("boolean_1", true);
        result.putInt("integer", 0xfffff);
        // If a null is stored, "" will be read back
        result.putString("empty", "");
        result.putString("string", id);
        result.putStringArray("string[]", TEST_STRINGS);

        // Adding a bundle, which contain 2 nested restrictions - bundle_string and bundle_int
        Bundle bundle = new Bundle();
        bundle.putString("bundle_string", "bundle_string");
        bundle.putInt("bundle_int", 1);
        result.putBundle("bundle", bundle);

        // Adding an array of 2 bundles
        Bundle[] bundleArray = new Bundle[2];
        bundleArray[0] = new Bundle();
        bundleArray[0].putString("bundle_array_string", "bundle_array_string");
        // Put bundle inside bundle
        bundleArray[0].putBundle("bundle_array_bundle", bundle);
        bundleArray[1] = new Bundle();
        bundleArray[1].putString("bundle_array_string2", "bundle_array_string2");
        result.putParcelableArray("bundle_array", bundleArray);
        return result;
    }

    /**
     * Returns {@code true} if the provided {@code bundle} matches a bundle created using
     * {@link #createBundle(String)} with the provided {@code id}.
     */
    // Should be consistent with createBundle
    public static void assertEqualToBundle(String id, Bundle bundle) {
        assertWithMessage("bundle0 size")
                .that(bundle.size()).isEqualTo(8);
        assertBooleanKey(bundle, "boolean_0", false);
        assertBooleanKey(bundle, "boolean_1", true);
        assertIntKey(bundle, "integer", 0xfffff);
        assertStringKey(bundle, "empty", "");
        assertStringKey(bundle, "string", id);
        assertStringsKey(bundle, "string[]", TEST_STRINGS);

        Bundle childBundle = bundle.getBundle("bundle");
        assertStringKey(childBundle, "bundle_string", "bundle_string");
        assertIntKey(childBundle, "bundle_int", 1);

        Parcelable[] bundleArray = bundle.getParcelableArray("bundle_array");
        assertWithMessage("size of bundle_array").that(bundleArray).hasLength(2);

        // Verifying bundle_array[0]
        Bundle bundle1 = (Bundle) bundleArray[0];
        assertStringKey(bundle1, "bundle_array_string", "bundle_array_string");

        Bundle bundle1ChildBundle = getBundleKey(bundle1, "bundle_array_bundle");

        assertWithMessage("bundle_array_bundle")
                .that(bundle1ChildBundle).isNotNull();
        assertStringKey(bundle1ChildBundle, "bundle_string", "bundle_string");
        assertIntKey(bundle1ChildBundle, "bundle_int", 1);

        // Verifying bundle_array[1]
        Bundle bundle2 = (Bundle) bundleArray[1];
        assertStringKey(bundle2, "bundle_array_string2", "bundle_array_string2");
    }

    /**
     * Returns {@code true} if the provided {@code bundle} does NOT match a bundle created using
     * {@link #createBundle(String)} with the provided {@code id}.
     */
    public static void assertNotEqualToBundle(String id, Bundle value) {
        // This uses an arbitrary value from the test bundle
        assertWithMessage("Bundle should not be equal to test bundle")
                .that(value.getString("string")).isNotEqualTo(id);
    }

    private static void assertBooleanKey(Bundle bundle, String key, boolean expectedValue) {

        boolean value = bundle.getBoolean(key);
        Log.v(TAG, "assertBooleanKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key", key)
                .that(value).isEqualTo(expectedValue);
    }

    private static void assertIntKey(Bundle bundle, String key, int expectedValue) {
        int value = bundle.getInt(key);
        Log.v(TAG, "assertIntKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key", key)
                .that(value).isEqualTo(expectedValue);
    }

    private static void assertStringKey(Bundle bundle, String key, String expectedValue) {
        String value = bundle.getString(key);
        Log.v(TAG, "assertStringKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key", key)
                .that(value).isEqualTo(expectedValue);
    }

    private static void assertStringsKey(Bundle bundle, String key, String[] expectedValue) {
        String[] value = bundle.getStringArray(key);
        Log.v(TAG, "assertStringsKey(): " + key + "="
                + (value == null ? "null" : Arrays.toString(value)));

        assertWithMessage("bundle's '%s' key", key).that(value).asList()
                .containsExactlyElementsIn(expectedValue).inOrder();
    }

    private static Bundle getBundleKey(Bundle bundle, String key) {
        Bundle value = bundle.getBundle(key);
        Log.v(TAG, "getBundleKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key", key).that(value).isNotNull();
        return value;
    }
}
