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

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PerUidCounterTest {
    private val UID_A = 1000
    private val UID_B = 1001
    private val UID_C = 1002

    @Test
    fun testCounterMaximum() {
        assertFailsWith<IllegalArgumentException> {
            PerUidCounter(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            PerUidCounter(0)
        }

        val testLimit = 1000
        val testCounter = PerUidCounter(testLimit)
        assertEquals(0, testCounter[UID_A])
        repeat(testLimit) {
            testCounter.incrementCountOrThrow(UID_A)
        }
        assertEquals(testLimit, testCounter[UID_A])
        assertFailsWith<IllegalStateException> {
            testCounter.incrementCountOrThrow(UID_A)
        }
        assertEquals(testLimit, testCounter[UID_A])
    }

    @Test
    fun testIncrementCountOrThrow() {
        val counter = PerUidCounter(3)

        // Verify the counters work independently.
        counter.incrementCountOrThrow(UID_A)
        counter.incrementCountOrThrow(UID_B)
        counter.incrementCountOrThrow(UID_B)
        counter.incrementCountOrThrow(UID_A)
        counter.incrementCountOrThrow(UID_A)
        assertEquals(3, counter[UID_A])
        assertEquals(2, counter[UID_B])
        assertFailsWith<IllegalStateException> {
            counter.incrementCountOrThrow(UID_A)
        }
        counter.incrementCountOrThrow(UID_B)
        assertFailsWith<IllegalStateException> {
            counter.incrementCountOrThrow(UID_B)
        }

        // Verify exception can be triggered again.
        assertFailsWith<IllegalStateException> {
            counter.incrementCountOrThrow(UID_A)
        }
        assertFailsWith<IllegalStateException> {
            repeat(3) {
                counter.incrementCountOrThrow(UID_A)
            }
        }
        assertEquals(3, counter[UID_A])
        assertEquals(3, counter[UID_B])
        assertEquals(0, counter[UID_C])
    }

    @Test
    fun testDecrementCountOrThrow() {
        val counter = PerUidCounter(3)

        // Verify the count cannot go below zero.
        assertFailsWith<IllegalStateException> {
            counter.decrementCountOrThrow(UID_A)
        }
        assertFailsWith<IllegalStateException> {
            repeat(5) {
                counter.decrementCountOrThrow(UID_A)
            }
        }

        // Verify the counters work independently.
        counter.incrementCountOrThrow(UID_A)
        counter.incrementCountOrThrow(UID_B)
        assertEquals(1, counter[UID_A])
        assertEquals(1, counter[UID_B])
        assertFailsWith<IllegalStateException> {
            repeat(3) {
                counter.decrementCountOrThrow(UID_A)
            }
        }
        assertFailsWith<IllegalStateException> {
            counter.decrementCountOrThrow(UID_A)
        }
        assertEquals(0, counter[UID_A])
        assertEquals(1, counter[UID_B])

        // Verify mixing increment and decrement.
        val largeCounter = PerUidCounter(100)
        repeat(90) {
            largeCounter.incrementCountOrThrow(UID_A)
        }
        repeat(70) {
            largeCounter.decrementCountOrThrow(UID_A)
        }
        repeat(80) {
            largeCounter.incrementCountOrThrow(UID_A)
        }
        assertFailsWith<IllegalStateException> {
            largeCounter.incrementCountOrThrow(UID_A)
        }
        assertEquals(100, largeCounter[UID_A])
        repeat(100) {
            largeCounter.decrementCountOrThrow(UID_A)
        }
        assertFailsWith<IllegalStateException> {
            largeCounter.decrementCountOrThrow(UID_A)
        }
        assertEquals(0, largeCounter[UID_A])
    }
}