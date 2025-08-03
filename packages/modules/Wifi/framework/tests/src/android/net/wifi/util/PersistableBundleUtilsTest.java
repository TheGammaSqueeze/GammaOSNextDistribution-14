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

package android.net.wifi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;

import org.junit.Test;

public class PersistableBundleUtilsTest {
    private static final String INT_FIELD_KEY = "intField";
    private static final String STRING_FIELD_KEY = "stringField";
    private static final String ARRAY_FIELD_KEY = "arrayField";
    private static final String BUNDLE_FIELD_KEY = "bundleField";
    private static final String EXTRA_FIELD_KEY = "extraField";

    private int mIntField = 12345;
    private String mStringField = "someString";
    private int[] mArrayField = new int[] {1, 2, 3, 4, 5};

    private PersistableBundle createTestBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(INT_FIELD_KEY, mIntField);
        bundle.putString(STRING_FIELD_KEY, mStringField);
        bundle.putIntArray(ARRAY_FIELD_KEY, mArrayField.clone());
        return bundle;
    }

    private PersistableBundle createNestedTestBundle(int depth) {
        PersistableBundle bundle = createTestBundle();
        if (depth == 0) {
            return bundle;
        }
        bundle.putPersistableBundle(BUNDLE_FIELD_KEY, createNestedTestBundle(depth - 1));
        return bundle;
    }

    /** Verify that the same hashcode is returned for two different instances of the same object. */
    @Test
    public void testHashCode() {
        PersistableBundle bundle1 = createTestBundle();
        PersistableBundle bundle2 = createTestBundle();
        assertEquals(
                PersistableBundleUtils.getHashCode(bundle1),
                PersistableBundleUtils.getHashCode(bundle2));
    }

    /**
     * Verify that the same hashcode is returned for two different instances of the same nested
     * object.
     */
    @Test
    public void testHashCode_nested() {
        PersistableBundle bundle1 = createNestedTestBundle(10);
        PersistableBundle bundle2 = createNestedTestBundle(10);
        assertEquals(
                PersistableBundleUtils.getHashCode(bundle1),
                PersistableBundleUtils.getHashCode(bundle2));
    }

    /** Verify that a different hashcode is returned for two different objects. */
    @Test
    public void testHashCode_different() {
        PersistableBundle bundle1 = createTestBundle();
        PersistableBundle bundle2 = createTestBundle();
        bundle2.putString(EXTRA_FIELD_KEY, "anotherStringField");
        assertNotEquals(
                PersistableBundleUtils.getHashCode(bundle1),
                PersistableBundleUtils.getHashCode(bundle2));
    }

    /** Verify that two different instances of the same object are considered equal. */
    @Test
    public void testEquality() {
        PersistableBundle bundle1 = createTestBundle();
        PersistableBundle bundle2 = createTestBundle();
        assertTrue(PersistableBundleUtils.isEqual(bundle1, bundle2));
    }

    /** Verify that the same instance of an object is considered equal to itself. */
    @Test
    public void testEquality_sameInstance() {
        PersistableBundle bundle = createTestBundle();
        assertTrue(PersistableBundleUtils.isEqual(bundle, bundle));
    }

    /** Verify the equality when one or both inputs are null. */
    @Test
    public void testEquality_nullInstance() {
        PersistableBundle bundle = createTestBundle();
        assertTrue(PersistableBundleUtils.isEqual(null, null));
        assertFalse(PersistableBundleUtils.isEqual(bundle, null));
        assertFalse(PersistableBundleUtils.isEqual(null, bundle));
    }

    /** Test that fields with a null value are compared correctly. */
    @Test
    public void testEquality_nullField() {
        PersistableBundle bundle1 = createTestBundle();
        PersistableBundle bundle2 = createTestBundle();
        bundle1.putString(EXTRA_FIELD_KEY, null);
        bundle2.putString(EXTRA_FIELD_KEY, null);
        assertTrue(PersistableBundleUtils.isEqual(bundle1, bundle2));
    }

    /** Verify that two objects with different keysets are not considered equal. */
    @Test
    public void testEquality_differentKeys() {
        PersistableBundle bundle1 = createTestBundle();
        PersistableBundle bundle2 = createTestBundle();
        bundle1.putString(EXTRA_FIELD_KEY, null);
        assertFalse(PersistableBundleUtils.isEqual(bundle1, bundle2));
    }

    /**
     * Verify that a field with the same key, but a different value type, is not considered equal.
     */
    @Test
    public void testEquality_differentFieldType() {
        PersistableBundle bundle1 = createTestBundle();
        PersistableBundle bundle2 = createTestBundle();
        bundle2.remove(STRING_FIELD_KEY);
        bundle2.putInt(STRING_FIELD_KEY, 1337);
        assertFalse(PersistableBundleUtils.isEqual(bundle1, bundle2));
    }

    /**
     * Verify that a field with the same key and value type, but a different value, is not
     * considered equal.
     */
    @Test
    public void testEquality_differentFieldVal() {
        PersistableBundle bundle1 = createTestBundle();
        PersistableBundle bundle2 = createTestBundle();
        bundle2.putString(STRING_FIELD_KEY, "differentString");
        assertFalse(PersistableBundleUtils.isEqual(bundle1, bundle2));
    }

    /** Verify that an array field with a different value is not considered equal. */
    @Test
    public void testEquality_differentArray() {
        PersistableBundle bundle1 = createTestBundle();
        PersistableBundle bundle2 = createTestBundle();
        bundle2.putIntArray(ARRAY_FIELD_KEY, new int[] {7, 8, 9});
        assertFalse(PersistableBundleUtils.isEqual(bundle1, bundle2));
    }

    /** Verify that two instances of the same nested object are considered equal. */
    @Test
    public void testEquality_nested_same() {
        PersistableBundle bundle1 = createNestedTestBundle(10);
        PersistableBundle bundle2 = createNestedTestBundle(10);
        assertTrue(PersistableBundleUtils.isEqual(bundle1, bundle2));
    }

    /** Verify that two different nested objects are not considered equal. */
    @Test
    public void testEquality_nested_different() {
        PersistableBundle bundle1 = createNestedTestBundle(9);
        PersistableBundle bundle2 = createNestedTestBundle(10);
        assertFalse(PersistableBundleUtils.isEqual(bundle1, bundle2));
    }
}
