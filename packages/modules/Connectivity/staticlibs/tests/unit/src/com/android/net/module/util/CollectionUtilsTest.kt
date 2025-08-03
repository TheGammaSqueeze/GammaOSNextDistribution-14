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

package com.android.net.module.util

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.testutils.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@SmallTest
class CollectionUtilsTest {
    @Test
    fun testAny() {
        assertTrue(CollectionUtils.any(listOf("A", "B", "C", "D", "E")) { it == "E" })
        assertFalse(CollectionUtils.any(listOf("A", "B", "C", "D", "E")) { it == "F" })
        assertTrue(CollectionUtils.any(listOf("AA", "BBB")) { it.length >= 3 })
        assertFalse(CollectionUtils.any(listOf("A", "BB", "CCC")) { it.length >= 4 })
        assertFalse(CollectionUtils.any(listOf("A", "BB", "CCC")) { it.length < 0 })
        assertFalse(CollectionUtils.any(listOf<String>()) { true })
        assertFalse(CollectionUtils.any(listOf<String>()) { false })
        assertTrue(CollectionUtils.any(listOf("A")) { true })
        assertFalse(CollectionUtils.any(listOf("A")) { false })
    }

    @Test
    fun testIndexOf() {
        assertEquals(4, CollectionUtils.indexOf(listOf("A", "B", "C", "D", "E")) { it == "E" })
        assertEquals(0, CollectionUtils.indexOf(listOf("A", "B", "C", "D", "E")) { it == "A" })
        assertEquals(1, CollectionUtils.indexOf(listOf("AA", "BBB", "CCCC")) { it.length >= 3 })
        assertEquals(1, CollectionUtils.indexOf(listOf("AA", null, "CCCC")) { it == null })
        assertEquals(1, CollectionUtils.indexOf(listOf(null, "CCCC")) { it != null })
    }

    @Test
    fun testIndexOfSubArray() {
        val haystack = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(2, CollectionUtils.indexOfSubArray(haystack, byteArrayOf(3, 4)))
        assertEquals(3, CollectionUtils.indexOfSubArray(haystack, byteArrayOf(4, 5)))
        assertEquals(4, CollectionUtils.indexOfSubArray(haystack, byteArrayOf(5)))
        assertEquals(-1, CollectionUtils.indexOfSubArray(haystack, byteArrayOf(3, 2)))
        assertEquals(0, CollectionUtils.indexOfSubArray(haystack, byteArrayOf()))
        assertEquals(-1, CollectionUtils.indexOfSubArray(byteArrayOf(), byteArrayOf(3, 2)))
        assertEquals(0, CollectionUtils.indexOfSubArray(byteArrayOf(), byteArrayOf()))
    }

    @Test
    fun testAll() {
        assertFalse(CollectionUtils.all(listOf("A", "B", "C", "D", "E")) { it != "E" })
        assertTrue(CollectionUtils.all(listOf("A", "B", "C", "D", "E")) { it != "F" })
        assertFalse(CollectionUtils.all(listOf("A", "BB", "CCC")) { it.length > 2 })
        assertTrue(CollectionUtils.all(listOf("A", "BB", "CCC")) { it.length >= 1 })
        assertTrue(CollectionUtils.all(listOf("A", "BB", "CCC")) { it.length < 4 })
        assertTrue(CollectionUtils.all(listOf<String>()) { true })
        assertTrue(CollectionUtils.all(listOf<String>()) { false })
        assertTrue(CollectionUtils.all(listOf(1)) { true })
        assertFalse(CollectionUtils.all(listOf(1)) { false })
    }

    @Test
    fun testContains() {
        assertTrue(CollectionUtils.contains(shortArrayOf(10, 20, 30), 10))
        assertTrue(CollectionUtils.contains(shortArrayOf(10, 20, 30), 30))
        assertFalse(CollectionUtils.contains(shortArrayOf(10, 20, 30), 40))
        assertFalse(CollectionUtils.contains(null, 10.toShort()))
        assertTrue(CollectionUtils.contains(intArrayOf(10, 20, 30), 10))
        assertTrue(CollectionUtils.contains(intArrayOf(10, 20, 30), 30))
        assertFalse(CollectionUtils.contains(intArrayOf(10, 20, 30), 40))
        assertFalse(CollectionUtils.contains(null, 10.toInt()))
        assertTrue(CollectionUtils.contains(arrayOf("A", "B", "C"), "A"))
        assertTrue(CollectionUtils.contains(arrayOf("A", "B", "C"), "C"))
        assertFalse(CollectionUtils.contains(arrayOf("A", "B", "C"), "D"))
        assertFalse(CollectionUtils.contains(null, "A"))

        val list = listOf("A", "B", "Ab", "C", "D", "E", "A", "E")
        assertTrue(CollectionUtils.contains(list) { it.length == 2 })
        assertFalse(CollectionUtils.contains(list) { it.length < 1 })
        assertTrue(CollectionUtils.contains(list) { it > "A" })
        assertFalse(CollectionUtils.contains(list) { it > "F" })
    }

    @Test
    fun testTotal() {
        assertEquals(10, CollectionUtils.total(longArrayOf(3, 6, 1)))
        assertEquals(10, CollectionUtils.total(longArrayOf(6, 1, 3)))
        assertEquals(10, CollectionUtils.total(longArrayOf(1, 3, 6)))
        assertEquals(3, CollectionUtils.total(longArrayOf(1, 1, 1)))
        assertEquals(0, CollectionUtils.total(null))
    }

    @Test
    fun testFindFirstFindLast() {
        val listAE = listOf("A", "B", "C", "D", "E")
        assertSame(CollectionUtils.findFirst(listAE) { it == "A" }, listAE[0])
        assertSame(CollectionUtils.findFirst(listAE) { it == "B" }, listAE[1])
        assertSame(CollectionUtils.findFirst(listAE) { it == "E" }, listAE[4])
        assertNull(CollectionUtils.findFirst(listAE) { it == "F" })
        assertSame(CollectionUtils.findLast(listAE) { it == "A" }, listAE[0])
        assertSame(CollectionUtils.findLast(listAE) { it == "B" }, listAE[1])
        assertSame(CollectionUtils.findLast(listAE) { it == "E" }, listAE[4])
        assertNull(CollectionUtils.findLast(listAE) { it == "F" })

        val listMulti = listOf("A", "B", "A", "C", "D", "E", "A", "E")
        assertSame(CollectionUtils.findFirst(listMulti) { it == "A" }, listMulti[0])
        assertSame(CollectionUtils.findFirst(listMulti) { it == "B" }, listMulti[1])
        assertSame(CollectionUtils.findFirst(listMulti) { it == "E" }, listMulti[5])
        assertNull(CollectionUtils.findFirst(listMulti) { it == "F" })
        assertSame(CollectionUtils.findLast(listMulti) { it == "A" }, listMulti[6])
        assertSame(CollectionUtils.findLast(listMulti) { it == "B" }, listMulti[1])
        assertSame(CollectionUtils.findLast(listMulti) { it == "E" }, listMulti[7])
        assertNull(CollectionUtils.findLast(listMulti) { it == "F" })
    }

    @Test
    fun testMap() {
        val listAE = listOf("A", "B", "C", "D", "E", null)
        assertEquals(listAE.map { "-$it-" }, CollectionUtils.map(listAE) { "-$it-" })
    }

    @Test
    fun testZip() {
        val listAE = listOf("A", "B", "C", "D", "E")
        val list15 = listOf(1, 2, 3, 4, 5)
        // Normal #zip returns kotlin.Pair, not android.util.Pair
        assertEquals(list15.zip(listAE).map { android.util.Pair(it.first, it.second) },
                CollectionUtils.zip(list15, listAE))
        val listNull = listOf("A", null, "B", "C", "D")
        assertEquals(list15.zip(listNull).map { android.util.Pair(it.first, it.second) },
                CollectionUtils.zip(list15, listNull))
        assertEquals(emptyList<android.util.Pair<Int, Int>>(),
                CollectionUtils.zip(emptyList<Int>(), emptyList<Int>()))
        assertFailsWith<IllegalArgumentException> {
            // Different size
            CollectionUtils.zip(listOf(1, 2), list15)
        }
    }

    @Test
    fun testAssoc() {
        val listADA = listOf("A", "B", "C", "D", "A")
        val list15 = listOf(1, 2, 3, 4, 5)
        assertEquals(list15.zip(listADA).toMap(), CollectionUtils.assoc(list15, listADA))

        // Null key is fine
        val assoc = CollectionUtils.assoc(listOf(1, 2, null), listOf("A", "B", "C"))
        assertEquals("C", assoc[null])

        assertFailsWith<IllegalArgumentException> {
            // Same key multiple times
            CollectionUtils.assoc(listOf("A", "B", "A"), listOf(1, 2, 3))
        }
        assertFailsWith<IllegalArgumentException> {
            // Same key multiple times, but it's null
            CollectionUtils.assoc(listOf(null, "B", null), listOf(1, 2, 3))
        }
        assertFailsWith<IllegalArgumentException> {
            // Different size
            CollectionUtils.assoc(listOf(1, 2), list15)
        }
    }
}
