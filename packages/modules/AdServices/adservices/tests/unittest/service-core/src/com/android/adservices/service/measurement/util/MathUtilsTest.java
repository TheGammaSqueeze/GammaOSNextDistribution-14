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

package com.android.adservices.service.measurement.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;


public class MathUtilsTest {
    @Test
    public void extractValidNumberInRange_success() {
        // Execution and assertion
        assertEquals(10L, MathUtils.extractValidNumberInRange(9L, 10L, 20L));
        assertEquals(10L, MathUtils.extractValidNumberInRange(10L, 10L, 20L));
        assertEquals(20L, MathUtils.extractValidNumberInRange(20L, 10L, 20L));
        assertEquals(20L, MathUtils.extractValidNumberInRange(21L, 10L, 20L));
        assertEquals(15L, MathUtils.extractValidNumberInRange(15L, 10L, 20L));

        // UnsignedLong below or equal lower limit
        assertEquals(new UnsignedLong("10"), MathUtils.extractValidNumberInRange(
                new UnsignedLong("9"), new UnsignedLong("10"), new UnsignedLong("20")));
        assertEquals(new UnsignedLong("10"), MathUtils.extractValidNumberInRange(
                new UnsignedLong("10"), new UnsignedLong("10"), new UnsignedLong("20")));
        // UnsignedLong above or equal higher limit
        assertEquals(new UnsignedLong("20"), MathUtils.extractValidNumberInRange(
                new UnsignedLong("20"), new UnsignedLong("10"), new UnsignedLong("20")));
        assertEquals(new UnsignedLong("20"), MathUtils.extractValidNumberInRange(
                new UnsignedLong("21"), new UnsignedLong("10"), new UnsignedLong("20")));

        // UnsignedLong with 64th bit

        UnsignedLong twoToThe64Minus1 = new UnsignedLong("18446744073709551615");
        UnsignedLong twoToThe64Minus2 = new UnsignedLong("18446744073709551614");
        UnsignedLong twoToThe64Minus5 = new UnsignedLong("18446744073709551611");
        UnsignedLong twoToThe64Minus6 = new UnsignedLong("18446744073709551610");

        // Below or equal lower limit
        assertEquals(twoToThe64Minus5, MathUtils.extractValidNumberInRange(
                twoToThe64Minus6, twoToThe64Minus5, twoToThe64Minus2));
        assertEquals(twoToThe64Minus5, MathUtils.extractValidNumberInRange(
                twoToThe64Minus5, twoToThe64Minus5, twoToThe64Minus2));
        // Above or equal higher limit
        assertEquals(twoToThe64Minus2, MathUtils.extractValidNumberInRange(
                twoToThe64Minus1, twoToThe64Minus5, twoToThe64Minus2));
        assertEquals(twoToThe64Minus2, MathUtils.extractValidNumberInRange(
                twoToThe64Minus2, twoToThe64Minus5, twoToThe64Minus2));
    }
}
