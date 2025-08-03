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

import com.android.net.module.util.ByteUtils.indexOf
import com.android.net.module.util.ByteUtils.concat
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class ByteUtilsTests {
    private val EMPTY = byteArrayOf()
    private val ARRAY1 = byteArrayOf(1)
    private val ARRAY234 = byteArrayOf(2, 3, 4)

    @Test
    fun testIndexOf() {
        assertEquals(-1, indexOf(EMPTY, 1))
        assertEquals(-1, indexOf(ARRAY1, 2))
        assertEquals(-1, indexOf(ARRAY234, 1))
        assertEquals(0, indexOf(byteArrayOf(-1), -1))
        assertEquals(0, indexOf(ARRAY234, 2))
        assertEquals(1, indexOf(ARRAY234, 3))
        assertEquals(2, indexOf(ARRAY234, 4))
        assertEquals(1, indexOf(byteArrayOf(2, 3, 2, 3), 3))
    }

    @Test
    fun testConcat() {
        assertContentEquals(EMPTY, concat())
        assertContentEquals(EMPTY, concat(EMPTY))
        assertContentEquals(EMPTY, concat(EMPTY, EMPTY, EMPTY))
        assertContentEquals(ARRAY1, concat(ARRAY1))
        assertNotSame(ARRAY1, concat(ARRAY1))
        assertContentEquals(ARRAY1, concat(EMPTY, ARRAY1, EMPTY))
        assertContentEquals(byteArrayOf(1, 1, 1), concat(ARRAY1, ARRAY1, ARRAY1))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), concat(ARRAY1, ARRAY234))
    }
}