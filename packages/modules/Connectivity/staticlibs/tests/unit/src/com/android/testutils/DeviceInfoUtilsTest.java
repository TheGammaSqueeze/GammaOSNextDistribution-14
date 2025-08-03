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

package com.android.testutils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public final class DeviceInfoUtilsTest {
    /**
     * Verifies that version string compare logic returns expected result for various cases.
     * Note that only major and minor number are compared.
     */
    @Test
    public void testMajorMinorVersionCompare() {
        assertEquals(0, DeviceInfoUtils.compareMajorMinorVersion("4.8.1", "4.8"));
        assertEquals(1, DeviceInfoUtils.compareMajorMinorVersion("4.9", "4.8.1"));
        assertEquals(1, DeviceInfoUtils.compareMajorMinorVersion("5.0", "4.8"));
        assertEquals(1, DeviceInfoUtils.compareMajorMinorVersion("5", "4.8"));
        assertEquals(0, DeviceInfoUtils.compareMajorMinorVersion("5", "5.0"));
        assertEquals(1, DeviceInfoUtils.compareMajorMinorVersion("5-beta1", "4.8"));
        assertEquals(0, DeviceInfoUtils.compareMajorMinorVersion("4.8.0.0", "4.8"));
        assertEquals(0, DeviceInfoUtils.compareMajorMinorVersion("4.8-RC1", "4.8"));
        assertEquals(0, DeviceInfoUtils.compareMajorMinorVersion("4.8", "4.8"));
        assertEquals(-1, DeviceInfoUtils.compareMajorMinorVersion("3.10", "4.8.0"));
        assertEquals(-1, DeviceInfoUtils.compareMajorMinorVersion("4.7.10.10", "4.8"));
    }

    @Test
    public void testGetMajorMinorSubminorVersion() throws Exception {
        final DeviceInfoUtils.KVersion expected = new DeviceInfoUtils.KVersion(4, 19, 220);
        assertEquals(expected, DeviceInfoUtils.getMajorMinorSubminorVersion("4.19.220"));
        assertEquals(expected, DeviceInfoUtils.getMajorMinorSubminorVersion("4.19.220.50"));
        assertEquals(expected, DeviceInfoUtils.getMajorMinorSubminorVersion(
                "4.19.220-g500ede0aed22-ab8272303"));

        final DeviceInfoUtils.KVersion expected2 = new DeviceInfoUtils.KVersion(5, 17, 0);
        assertEquals(expected2, DeviceInfoUtils.getMajorMinorSubminorVersion("5.17"));
        assertEquals(expected2, DeviceInfoUtils.getMajorMinorSubminorVersion("5.17."));
        assertEquals(expected2, DeviceInfoUtils.getMajorMinorSubminorVersion("5.17.beta"));
        assertEquals(expected2, DeviceInfoUtils.getMajorMinorSubminorVersion(
                "5.17-rc6-g52099515ca00-ab8032400"));

        final DeviceInfoUtils.KVersion invalid = new DeviceInfoUtils.KVersion(0, 0, 0);
        assertEquals(invalid, DeviceInfoUtils.getMajorMinorSubminorVersion(""));
        assertEquals(invalid, DeviceInfoUtils.getMajorMinorSubminorVersion("4"));
        assertEquals(invalid, DeviceInfoUtils.getMajorMinorSubminorVersion("4."));
        assertEquals(invalid, DeviceInfoUtils.getMajorMinorSubminorVersion("4-beta"));
        assertEquals(invalid, DeviceInfoUtils.getMajorMinorSubminorVersion("1.x.1"));
        assertEquals(invalid, DeviceInfoUtils.getMajorMinorSubminorVersion("x.1.1"));
    }

    @Test
    public void testVersion() throws Exception {
        final DeviceInfoUtils.KVersion v1 = new DeviceInfoUtils.KVersion(4, 8, 1);
        final DeviceInfoUtils.KVersion v2 = new DeviceInfoUtils.KVersion(4, 8, 1);
        final DeviceInfoUtils.KVersion v3 = new DeviceInfoUtils.KVersion(4, 8, 2);
        final DeviceInfoUtils.KVersion v4 = new DeviceInfoUtils.KVersion(4, 9, 1);
        final DeviceInfoUtils.KVersion v5 = new DeviceInfoUtils.KVersion(5, 8, 1);

        assertEquals(v1, v2);
        assertNotEquals(v1, v3);
        assertNotEquals(v1, v4);
        assertNotEquals(v1, v5);

        assertEquals(0, v1.compareTo(v2));
        assertEquals(-1, v1.compareTo(v3));
        assertEquals(1, v3.compareTo(v1));
        assertEquals(-1, v1.compareTo(v4));
        assertEquals(1, v4.compareTo(v1));
        assertEquals(-1, v1.compareTo(v5));
        assertEquals(1, v5.compareTo(v1));

        assertTrue(v2.isInRange(v1, v5));
        assertTrue(v3.isInRange(v1, v5));
        assertTrue(v4.isInRange(v1, v5));
        assertFalse(v5.isInRange(v1, v5));
        assertFalse(v1.isInRange(v3, v5));
        assertFalse(v5.isInRange(v2, v4));

        assertTrue(v2.isAtLeast(v1));
        assertTrue(v3.isAtLeast(v1));
        assertTrue(v4.isAtLeast(v1));
        assertTrue(v5.isAtLeast(v1));
        assertFalse(v1.isAtLeast(v3));
        assertFalse(v1.isAtLeast(v4));
        assertFalse(v1.isAtLeast(v5));
    }

    @Test
    public void testKernelVersionIsAtLeast() {
        // Pick a lower kernel version 4.0 which was released at April 2015, the kernel
        // version running on all test devices nowadays should be higher than it.
        assertTrue(DeviceInfoUtils.isKernelVersionAtLeast("4.0"));

        // Invalid kernel version.
        assertTrue(DeviceInfoUtils.isKernelVersionAtLeast("0.0.0"));

        // Pick a higher kernel version which isn't released yet, comparison should return false.
        // Need to update the target version in the future to make sure the test still passes.
        assertFalse(DeviceInfoUtils.isKernelVersionAtLeast("20.0.0"));
    }
}
