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

package com.android.car.scriptexecutortest.unit;

import static com.google.common.truth.Truth.assertThat;

import android.os.PersistableBundle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public final class JniUtilsTest {

    private static final String TAG = JniUtilsTest.class.getSimpleName();

    private static final String BOOLEAN_KEY = "boolean_key";
    private static final String INT_KEY = "int_key";
    private static final String STRING_KEY = "string_key";
    private static final String NUMBER_KEY = "number_key";
    private static final String BOOLEAN_ARRAY_KEY = "boolean_array_key";
    private static final String INT_ARRAY_KEY = "int_array_key";
    private static final String LONG_ARRAY_KEY = "long_array_key";
    private static final String DOUBLE_ARRAY_KEY = "double_array_key";
    private static final String PERSISTABLE_BUNDLE_KEY = "persistable_bundle_key";

    private static final boolean BOOLEAN_VALUE = true;
    private static final double NUMBER_VALUE = 0.1;
    private static final int INT_VALUE = 10;
    private static final int INT_VALUE_2 = 20;
    private static final String STRING_VALUE = "test";
    private static final boolean[] BOOLEAN_ARRAY_VALUE = new boolean[]{true, false, true};
    private static final int[] INT_ARRAY_VALUE = new int[]{1, 2, 3};
    private static final long[] LONG_ARRAY_VALUE = new long[]{1, 2, 3, 4};
    private static final double[] DOUBLE_ARRAY_VALUE = new double[]{1.1d, 2.2d, 3.3d, 4.4d};
    private static final PersistableBundle PERSISTABLE_BUNDLE_VALUE = new PersistableBundle();

    // Pointer to Lua Engine instantiated in native space.
    private long mLuaEnginePtr = 0;

    static {
        System.loadLibrary("scriptexecutorjniutils-test");
    }

    @Before
    public void setUp() {
        mLuaEnginePtr = nativeCreateLuaEngine();
    }

    @After
    public void tearDown() {
        nativeDestroyLuaEngine(mLuaEnginePtr);
    }

    // Simply invokes PushBundleToLuaTable native method under test.
    private native void nativePushBundleToLuaTableCaller(
            long luaEnginePtr, PersistableBundle bundle);

    // Invokes pushBundleListToLuaTable native method.
    private native void nativePushBundleListToLuaTableCaller(
            long luaEnginePtr, List<PersistableBundle> bundleList);

    // Creates an instance of LuaEngine on the heap and returns the pointer.
    private native long nativeCreateLuaEngine();

    // Destroys instance of LuaEngine on the native side at provided memory address.
    private native void nativeDestroyLuaEngine(long luaEnginePtr);

    // Returns size of a Lua object located at the specified position on the stack.
    private native int nativeGetObjectSize(long luaEnginePtr, int index);

    /*
     * Family of methods to check if the table on top of the stack has
     * the given value under provided key.
     */
    private native boolean nativeHasBooleanValue(long luaEnginePtr, String key, boolean value);

    private native boolean nativeHasStringValue(long luaEnginePtr, String key, String value);

    private native boolean nativeHasIntValue(long luaEnginePtr, String key, int value);

    private native boolean nativeHasBooleanArrayValue(
            long luaEnginePtr, String key, boolean[] value);

    private native boolean nativeHasDoubleValue(long luaEnginePtr, String key, double value);

    private native boolean nativeHasIntArrayValue(long luaEnginePtr, String key, int[] value);

    private native boolean nativeHasLongArrayValue(long luaEnginePtr, String key, long[] value);

    private native boolean nativeHasDoubleArrayValue(long luaEnginePtr, String key, double[] value);

    /*
     * Checks if the key maps to a Lua table/PersistableBundle, and checks if the PersistableBundle
     * has the string representation equal to the parameter {@code expected}.
     */
    private native boolean nativeHasPersistableBundleOfStringValue(
            long luaEnginePtr, String key, String expected);

    private native boolean nativeHasNumberOfTables(long luaEnginePtr, int num);

    private native boolean nativeHasTableAtIndexWithIntValue(
            long luaEnginePtr, int index, String key, int value);

    @Test
    public void pushBundleToLuaTable_nullBundleMakesEmptyLuaTable() {
        nativePushBundleToLuaTableCaller(mLuaEnginePtr, null);
        // Get the size of the object on top of the stack,
        // which is where our table is supposed to be.
        assertThat(nativeGetObjectSize(mLuaEnginePtr, 1)).isEqualTo(0);
    }

    @Test
    public void pushBundleToLuaTable_valuesOfDifferentTypes() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(BOOLEAN_KEY, BOOLEAN_VALUE);
        bundle.putInt(INT_KEY, INT_VALUE);
        bundle.putDouble(NUMBER_KEY, NUMBER_VALUE);
        bundle.putString(STRING_KEY, STRING_VALUE);
        bundle.putPersistableBundle(PERSISTABLE_BUNDLE_KEY, PERSISTABLE_BUNDLE_VALUE);

        // Invokes the corresponding helper method to convert the bundle
        // to Lua table on Lua stack.
        nativePushBundleToLuaTableCaller(mLuaEnginePtr, bundle);

        // Check contents of Lua table.
        assertThat(nativeHasBooleanValue(mLuaEnginePtr, BOOLEAN_KEY, BOOLEAN_VALUE)).isTrue();
        assertThat(nativeHasIntValue(mLuaEnginePtr, INT_KEY, INT_VALUE)).isTrue();
        assertThat(nativeHasDoubleValue(mLuaEnginePtr, NUMBER_KEY, NUMBER_VALUE)).isTrue();
        assertThat(nativeHasStringValue(mLuaEnginePtr, STRING_KEY, STRING_VALUE)).isTrue();
        assertThat(nativeHasPersistableBundleOfStringValue(mLuaEnginePtr, PERSISTABLE_BUNDLE_KEY,
                PERSISTABLE_BUNDLE_VALUE.toString())).isTrue();
    }

    @Test
    public void pushBundleToLuaTable_wrongKey() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(BOOLEAN_KEY, BOOLEAN_VALUE);

        // Invokes the corresponding helper method to convert the bundle
        // to Lua table on Lua stack.
        nativePushBundleToLuaTableCaller(mLuaEnginePtr, bundle);

        // Check contents of Lua table.
        assertThat(nativeHasBooleanValue(mLuaEnginePtr, "wrong key", BOOLEAN_VALUE)).isFalse();
    }

    @Test
    public void pushBundleToLuaTable_arrays() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBooleanArray(BOOLEAN_ARRAY_KEY, BOOLEAN_ARRAY_VALUE);
        bundle.putIntArray(INT_ARRAY_KEY, INT_ARRAY_VALUE);
        bundle.putLongArray(LONG_ARRAY_KEY, LONG_ARRAY_VALUE);
        bundle.putDoubleArray(DOUBLE_ARRAY_KEY, DOUBLE_ARRAY_VALUE);

        // Invokes the corresponding helper method to convert the bundle
        // to Lua table on Lua stack.
        nativePushBundleToLuaTableCaller(mLuaEnginePtr, bundle);

        // Check contents of Lua table.
        // Java int and long arrays both end up being arrays of Lua's Integer type,
        // which is interpreted as a 8-byte int type.
        assertThat(nativeHasBooleanArrayValue(
                mLuaEnginePtr, BOOLEAN_ARRAY_KEY, BOOLEAN_ARRAY_VALUE)).isTrue();
        assertThat(nativeHasIntArrayValue(mLuaEnginePtr, INT_ARRAY_KEY, INT_ARRAY_VALUE)).isTrue();
        assertThat(nativeHasLongArrayValue(mLuaEnginePtr, LONG_ARRAY_KEY, LONG_ARRAY_VALUE))
                .isTrue();
        assertThat(nativeHasDoubleArrayValue(mLuaEnginePtr, DOUBLE_ARRAY_KEY, DOUBLE_ARRAY_VALUE))
                .isTrue();
    }

    @Test
    public void pushBundleListToLuaTable_makesArrayOfTables() {
        PersistableBundle bundle1 = new PersistableBundle();
        PersistableBundle bundle2 = new PersistableBundle();
        bundle1.putInt(INT_KEY, INT_VALUE);
        bundle2.putInt(INT_KEY, INT_VALUE_2);
        List<PersistableBundle> bundleList = new ArrayList<>();
        bundleList.add(bundle1);
        bundleList.add(bundle2);

        nativePushBundleListToLuaTableCaller(mLuaEnginePtr, bundleList);

        assertThat(nativeHasNumberOfTables(mLuaEnginePtr, 2)).isTrue();
        assertThat(nativeHasTableAtIndexWithIntValue(mLuaEnginePtr, 1, INT_KEY, INT_VALUE))
                .isTrue();
        assertThat(nativeHasTableAtIndexWithIntValue(mLuaEnginePtr, 2, INT_KEY, INT_VALUE_2))
                .isTrue();
    }

    @Test
    public void pushBundleToLuaTable_nestedBundle() {
        PersistableBundle bundle = new PersistableBundle();
        PersistableBundle nestedBundle = new PersistableBundle();
        nestedBundle.putInt(INT_KEY, INT_VALUE);
        nestedBundle.putDouble(NUMBER_KEY, NUMBER_VALUE);
        nestedBundle.putString(STRING_KEY, STRING_VALUE);
        nestedBundle.putPersistableBundle(PERSISTABLE_BUNDLE_KEY, PERSISTABLE_BUNDLE_VALUE);
        bundle.putPersistableBundle(PERSISTABLE_BUNDLE_KEY, nestedBundle);

        // Invokes the corresponding helper method to convert the bundle
        // to Lua table on Lua stack.
        nativePushBundleToLuaTableCaller(mLuaEnginePtr, bundle);

        // Check contents of Lua table/PersistableBundle.
        assertThat(nativeHasPersistableBundleOfStringValue(
                mLuaEnginePtr, PERSISTABLE_BUNDLE_KEY, nestedBundle.toString())).isTrue();
    }
}
