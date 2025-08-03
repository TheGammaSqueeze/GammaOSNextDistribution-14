/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnsignedLongTest {
    private static final UnsignedLong ULONG_LONG = new UnsignedLong(2L);
    private static final UnsignedLong ULONG_STR = new UnsignedLong("2");
    private static final UnsignedLong LARGE = new UnsignedLong("9223372036854775808");
    private static final UnsignedLong LARGER = new UnsignedLong("9223372036854775809");

    @Test
    public void testEqualsPass() {
        assertEquals(ULONG_STR, ULONG_STR);
        assertEquals(ULONG_STR, ULONG_LONG);
        assertEquals(ULONG_LONG, ULONG_LONG);
    }

    @Test
    public void testEqualsFail() {
        assertNotEquals(ULONG_LONG, new UnsignedLong(3L));
        assertNotEquals(ULONG_STR, new UnsignedLong(3L));
    }

    @Test
    public void testCompareTo_equal() {
        assertTrue(ULONG_LONG.compareTo(ULONG_STR) == 0);
        assertTrue(LARGE.compareTo(new UnsignedLong("9223372036854775808")) == 0);
    }

    @Test
    public void testCompareTo_smaller() {
        assertTrue(ULONG_LONG.compareTo(new UnsignedLong(3L)) < 0);
        assertTrue(ULONG_LONG.compareTo(LARGE) < 0);
        assertTrue(LARGE.compareTo(LARGER) < 0);
    }

    @Test
    public void testCompareTo_larger() {
        assertTrue(ULONG_LONG.compareTo(new UnsignedLong(1L)) > 0);
        assertTrue(LARGE.compareTo(ULONG_LONG) > 0);
        assertTrue(LARGER.compareTo(LARGE) > 0);
    }

    @Test
    public void testHashCode_equals() {
        assertEquals(ULONG_STR.hashCode(), ULONG_STR.hashCode());
        assertEquals(ULONG_STR.hashCode(), ULONG_LONG.hashCode());
        assertEquals(ULONG_LONG.hashCode(), ULONG_LONG.hashCode());
    }

    @Test
    public void testHashCode_notEquals() {
        assertNotEquals(ULONG_LONG.hashCode(), new UnsignedLong(3L).hashCode());
        assertNotEquals(ULONG_STR.hashCode(), new UnsignedLong(3L).hashCode());
    }

    @Test
    public void testValidateConstructorArgument_long() {
        Long arg = null;
        assertThrows(
                IllegalArgumentException.class,
                () -> new UnsignedLong(arg));
    }

    @Test
    public void testValidateConstructorArgument_string() {
        String arg = null;
        assertThrows(
                IllegalArgumentException.class,
                () -> new UnsignedLong(arg));
    }

    @Test
    public void testMod() {
        // uint64 18446744073709501613 = long -50003
        // 18446744073709501613 % 8 = 5
        assertEquals(new UnsignedLong(5L), new UnsignedLong(-50003L).mod(8));
    }

    @Test
    public void testToString() {
        assertEquals("18446744073709551615", new UnsignedLong(-1L).toString());
    }
}
