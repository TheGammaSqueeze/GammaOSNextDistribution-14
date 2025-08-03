/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.internal.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit Tests of PersistableBundleUtilsTest.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersistableBundleUtilsTest {
    @Test
    public void testNullOrEmptyInput() throws Exception {
        assertNull(PersistableBundleUtils.toByteArray(null));
        assertNull(PersistableBundleUtils.fromByteArray(null));
        assertTrue(
                PersistableBundleUtils.fromByteArray(
                        PersistableBundleUtils.toByteArray(PersistableBundle.EMPTY))
                .isEmpty());
        assertTrue(PersistableBundleUtils.fromByteArray(new byte[]{' '}).isEmpty());
    }

    @Test
    public void testRoundTrip() throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt("a", 1);
        bundle.putString("b", "abc");
        bundle.putDouble("c", 5.0);
        bundle.putIntArray("d", new int[]{2, 4, 6});
        PersistableBundle nestedBundle = new PersistableBundle();
        nestedBundle.putInt("x", 10);
        bundle.putPersistableBundle("e", nestedBundle);

        byte[] serialized = PersistableBundleUtils.toByteArray(bundle);

        PersistableBundle deserialized = PersistableBundleUtils.fromByteArray(serialized);

        // PersistableBundle does not implement equals().
        assertEquals(1, deserialized.getInt("a"));
        assertEquals("abc", deserialized.getString("b"));
        assertEquals(5.0, deserialized.getDouble("c"), 0.01);
        assertArrayEquals(new int[]{2, 4, 6}, deserialized.getIntArray("d"));
        assertEquals(10, deserialized.getPersistableBundle("e").getInt("x"));
    }

    @Test
    public void testRoundTripLarge() throws Exception {
        final int targetLength = 10 * 1048576;
        final String value = createString(targetLength);
        assertEquals(value.length(), targetLength);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("a", value);
        bundle.putString("b", value);

        byte[] serialized = PersistableBundleUtils.toByteArray(bundle);

        PersistableBundle deserialized = PersistableBundleUtils.fromByteArray(serialized);

        assertEquals(value, deserialized.getString("a"));
        assertEquals(value, deserialized.getString("b"));
    }

    private String createString(int length) {
        final String src = "ABCDEFGHIJ";
        final int count = length / src.length();
        StringBuffer buf = new StringBuffer(length);
        for (int i = 0; i < count; ++i) {
            buf.append(src);
        }
        return buf.toString();
    }
}
