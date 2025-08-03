/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.managedprovisioning;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;
import androidx.test.uiautomator.UiDevice;

import com.android.managedprovisioning.preprovisioning.EncryptionController;
import com.android.managedprovisioning.preprovisioning.PostEncryptionActivity;

import java.lang.reflect.Array;
import java.util.Objects;
import java.util.Set;

public class TestUtils extends AndroidTestCase {
    @SmallTest
    public void testIntentWithActionEquals() {
        Intent i = new Intent("aa");
        assertTrue(intentEquals(i, i));
    }

    @SmallTest
    public void testIntentWithExtraEquals() {
        Intent i = new Intent().putExtra("bb", "cc");
        assertTrue(intentEquals(i, i));
    }

    @SmallTest
    public void testIntentActionNotEqual() {
        Intent i1 = new Intent("aa");
        Intent i2 = new Intent("bb");
        assertFalse(intentEquals(i1, i2));
    }

    @SmallTest
    public void testIntentExtraNotEqual() {
        Intent i1 = new Intent().putExtra("aa", "bb");
        Intent i2 = new Intent().putExtra("aa", "cc");
        assertFalse(intentEquals(i1, i2));
    }

    @SmallTest
    public void testIntentNotSameExtra() {
        Intent i1 = new Intent().putExtra("aa", "bb");
        Intent i2 = new Intent().putExtra("dd", "cc");
        assertFalse(intentEquals(i1, i2));
    }

    @SmallTest
    public void testIntentMultipleExtrasOfArray() {
        Intent i1 = new Intent()
                .putExtra("aa", new int[] {1, 2})
                .putExtra("aaa", new int[] {3, 4});
        Intent i2 = new Intent(i1);
        assertTrue(intentEquals(i1, i2));

        i2.putExtra("aaa", new int[] {5, 6});
        assertFalse(intentEquals(i1, i2));
    }

    @SmallTest
    public void testIntentMultipleExtrasOfBundle() {
        Bundle b1 = new Bundle();
        b1.putString("b1", "11");
        Bundle b2 = new Bundle();
        b2.putString("b2", "22");
        Intent i1 = new Intent()
                .putExtra("aa", b1)
                .putExtra("aaa", b2);
        Intent i2 = new Intent(i1);
        assertTrue(intentEquals(i1, i2));

        Bundle b3 = new Bundle();
        b3.putString("b3", "33");
        i2.putExtra("aaa", b3);
        assertFalse(intentEquals(i1, i2));
    }

    /**
     * This method uses Object.equals to compare the extras.
     * Which means that it will always return false if one of the intents has an extra with an
     * embedded bundle.
     */
    public static boolean intentEquals(Intent intent1, Intent intent2) {
        // both are null? return true
        if (intent1 == null && intent2 == null) {
            return true;
        }
        // Only one is null? return false
        if (intent1 == null || intent2 == null) {
            return false;
        }
        return intent1.filterEquals(intent2) && bundleEquals(intent1.getExtras(),
                intent2.getExtras());
    }

    public static boolean bundleEquals(BaseBundle bundle1, BaseBundle bundle2) {
        // both are null? return true
        if (bundle1 == null && bundle2 == null) {
            return true;
        }
        // Only one is null? return false
        if (bundle1 == null || bundle2 == null) {
            return false;
        }
        if (bundle1.size() != bundle2.size()) {
            return false;
        }
        Set<String> keys = bundle1.keySet();
        for (String key : keys) {
            Object value1 = bundle1.get(key);
            Object value2 = bundle2.get(key);
            if (value1 != null && value1.getClass().isArray()
                    && value2 != null && value2.getClass().isArray()) {
                if (!arrayEquals(value1, value2)) {
                    return false;
                }
            } else if (value1 instanceof BaseBundle && value2 instanceof BaseBundle) {
                if (!bundleEquals((BaseBundle) value1, (BaseBundle) value2)) {
                    return false;
                }
            } else if (!Objects.equals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean arrayEquals(Object value1, Object value2) {
        final int length = Array.getLength(value1);
        if (length != Array.getLength(value2)) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!Objects.equals(Array.get(value1, i), Array.get(value2, i))) {
                return false;
            }
        }
        return true;
    }

    public static void assertIntentEquals(Intent i1, Intent i2) {
        if (!intentEquals(i1, i2)) {
            failIntentsNotEqual(i1, i2);
        }
    }

    public static void failIntentsNotEqual(Intent i1, Intent i2) {
        fail("Intent " + intentToString(i1) + " is not equal to " + intentToString(i2));
    }

    public static String intentToString(Intent i) {
        return i.toString() + " with extras " + i.getExtras();
    }

    public static PersistableBundle createTestAdminExtras() {
        PersistableBundle adminExtras = new PersistableBundle();
        adminExtras.putBoolean("boolean", true);
        adminExtras.putBooleanArray("boolean_array", new boolean[] { true, false });
        adminExtras.putDouble("double", 1.1);
        adminExtras.putDoubleArray("double_array", new double[] { 1.1, 2.2 });
        adminExtras.putInt("int", 1);
        adminExtras.putIntArray("int_array", new int[] { 1, 2 } );
        adminExtras.putLong("long", 1L);
        adminExtras.putLongArray("long_array", new long[] { 1L, 2L });
        adminExtras.putString("string", "Hello");
        adminExtras.putStringArray("string_array", new String[] { "Hello", "World" } );

        PersistableBundle nestedBundle = new PersistableBundle();
        nestedBundle.putInt("int", 1);
        nestedBundle.putStringArray("string_array", new String[] { "Hello", "World" } );
        adminExtras.putPersistableBundle("persistable_bundle", nestedBundle);
        return adminExtras;
    }

    public static void wakeupDeviceAndPressHome(UiDevice uiDevice) throws RemoteException {
        uiDevice.wakeUp();
        uiDevice.pressMenu();
        uiDevice.pressHome();
    }

    public static EncryptionController createEncryptionController(
            Context context) {
        return EncryptionController.getInstance(
                context,
                new ComponentName(context, PostEncryptionActivity.class));
    }

    public static void assertIntentsEqual(Intent intent1, Intent intent2) {
        assertWithMessage("Intent actions are not equal")
                .that(intent1.getAction())
                .isEqualTo(intent2.getAction());
        assertWithMessage("Package names are not equal")
                .that(intent1.getPackage())
                .isEqualTo(intent2.getPackage());
        assertBundlesEqual(intent1.getExtras(), intent2.getExtras());
    }

    public static void assertBundlesEqual(BaseBundle bundle1, BaseBundle bundle2) {
        if (bundle1 != null) {
            assertWithMessage("Intent bundles are not equal, " + bundle1 + " " + bundle2)
                    .that(bundle2).isNotNull();
            assertWithMessage("Intent bundles are not equal, " + bundle1 + " " + bundle2)
                    .that(bundle1.keySet().size()).isEqualTo(bundle2.keySet().size());
            for (String key : bundle1.keySet()) {
                assertWithMessage("Intent bundles are not equal, " + bundle1 + " " + bundle2)
                        .that(bundle1.get(key))
                        .isEqualTo(bundle2.get(key));
            }
        } else {
            assertWithMessage("Intent bundles are not equal").that(bundle2).isNull();
        }
    }
}
