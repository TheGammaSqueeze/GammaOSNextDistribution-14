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

package com.android.net.module.util

import com.android.net.module.util.BitUtils.appendStringRepresentationOfBitMaskToStringBuilder
import com.android.net.module.util.BitUtils.describeDifferences
import com.android.net.module.util.BitUtils.packBits
import com.android.net.module.util.BitUtils.unpackBits
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BitUtilsTests {
    @Test
    fun testBitPackingTestCase() {
        runBitPackingTestCase(0, intArrayOf())
        runBitPackingTestCase(1, intArrayOf(0))
        runBitPackingTestCase(3, intArrayOf(0, 1))
        runBitPackingTestCase(4, intArrayOf(2))
        runBitPackingTestCase(63, intArrayOf(0, 1, 2, 3, 4, 5))
        runBitPackingTestCase(Long.MAX_VALUE.inv(), intArrayOf(63))
        runBitPackingTestCase(Long.MAX_VALUE.inv() + 1, intArrayOf(0, 63))
        runBitPackingTestCase(Long.MAX_VALUE.inv() + 2, intArrayOf(1, 63))
    }

    fun runBitPackingTestCase(packedBits: Long, bits: IntArray) {
        assertEquals(packedBits, packBits(bits))
        assertTrue(bits contentEquals unpackBits(packedBits))
    }

    @Test
    fun testAppendStringRepresentationOfBitMaskToStringBuilder() {
        runTestAppendStringRepresentationOfBitMaskToStringBuilder("", 0)
        runTestAppendStringRepresentationOfBitMaskToStringBuilder("BIT0", 0b1)
        runTestAppendStringRepresentationOfBitMaskToStringBuilder("BIT1&BIT2&BIT4", 0b10110)
        runTestAppendStringRepresentationOfBitMaskToStringBuilder(
                "BIT0&BIT60&BIT61&BIT62&BIT63",
                (0b11110000_00000000_00000000_00000000 shl 32) +
                        0b00000000_00000000_00000000_00000001)
    }

    fun runTestAppendStringRepresentationOfBitMaskToStringBuilder(expected: String, bitMask: Long) {
        StringBuilder().let {
            appendStringRepresentationOfBitMaskToStringBuilder(it, bitMask, { i -> "BIT$i" }, "&")
            assertEquals(expected, it.toString())
        }
    }

    @Test
    fun testDescribeDifferences() {
        fun describe(a: Long, b: Long) = describeDifferences(a, b, Integer::toString)
        assertNull(describe(0, 0))
        assertNull(describe(5, 5))
        assertNull(describe(Long.MAX_VALUE, Long.MAX_VALUE))

        assertEquals("+0", describe(0, 1))
        assertEquals("-0", describe(1, 0))

        assertEquals("+0+2", describe(0, 5))
        assertEquals("+2", describe(1, 5))
        assertEquals("-0+2", describe(1, 4))

        fun makeField(vararg i: Int) = i.sumOf { 1L shl it }
        assertEquals("-0-4-6-9+1+3+11", describe(makeField(0, 4, 6, 9), makeField(1, 3, 11)))
        assertEquals("-1-5-9+6+8", describe(makeField(0, 1, 3, 4, 5, 9), makeField(0, 3, 4, 6, 8)))
    }
}
