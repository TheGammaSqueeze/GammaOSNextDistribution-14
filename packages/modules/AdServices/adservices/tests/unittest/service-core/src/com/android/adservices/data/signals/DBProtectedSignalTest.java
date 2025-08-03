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

package com.android.adservices.data.signals;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class DBProtectedSignalTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testCreateSignal() {
        DBProtectedSignal signal =
                DBProtectedSignal.create(
                        null,
                        CommonFixture.VALID_BUYER_1,
                        DBProtectedSignalFixture.KEY,
                        DBProtectedSignalFixture.VALUE,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        CommonFixture.TEST_PACKAGE_NAME_1);
        assertNull(signal.getId());
        assertEquals(CommonFixture.VALID_BUYER_1, signal.getBuyer());
        assertArrayEquals(DBProtectedSignalFixture.KEY, signal.getKey());
        assertArrayEquals(DBProtectedSignalFixture.VALUE, signal.getValue());
        assertEquals(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI, signal.getCreationTime());
        assertEquals(CommonFixture.TEST_PACKAGE_NAME_1, signal.getPackageName());
    }

    @Test
    public void testBuildSignal() {
        DBProtectedSignal signal =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        assertNull(signal.getId());
        assertEquals(CommonFixture.VALID_BUYER_1, signal.getBuyer());
        assertArrayEquals(DBProtectedSignalFixture.KEY, signal.getKey());
        assertArrayEquals(DBProtectedSignalFixture.VALUE, signal.getValue());
        assertEquals(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI, signal.getCreationTime());
        assertEquals(CommonFixture.TEST_PACKAGE_NAME_1, signal.getPackageName());
    }

    @Test
    public void testEqual() {
        DBProtectedSignal signal1 =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        DBProtectedSignal signal2 =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        assertEquals(signal1, signal2);
    }

    @Test
    public void testNotEqual() {
        DBProtectedSignal signal1 =
                DBProtectedSignal.builder()
                        .setId(1L)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        DBProtectedSignal signal2 =
                DBProtectedSignal.builder()
                        .setId(2L)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        assertNotEquals(signal1, signal2);
    }

    @Test
    public void testHashCode() {
        DBProtectedSignal signal1 =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        DBProtectedSignal signal2 =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        assertEquals(signal1.hashCode(), signal2.hashCode());
    }
}
