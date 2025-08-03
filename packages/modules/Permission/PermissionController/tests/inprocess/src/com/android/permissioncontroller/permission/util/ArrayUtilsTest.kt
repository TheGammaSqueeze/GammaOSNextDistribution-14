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

package com.android.permissioncontroller.permission.util

import com.android.permissioncontroller.permission.utils.ArrayUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArrayUtilsTest {
    @Test
    fun appendString_appendToNull_returnsArrayWithString() {
        assertThat(ArrayUtils.appendString(null, TEST_STRING)).isEqualTo(arrayOf(TEST_STRING))
    }

    @Test
    fun appendString_appendToNull_returnsArrayWithNull() {
        val result = ArrayUtils.appendString(null, null)
        assertThat(result.size).isEqualTo(1)
        assertThat(result[0]).isEqualTo(null)
    }

    @Test
    fun appendString_duplicatedString_returnsArray() {
        val cur = arrayOf("a", "b", TEST_STRING)
        assertThat(ArrayUtils.appendString(cur, TEST_STRING)).isEqualTo(cur)
    }

    @Test
    fun appendString_appendNull_returnsArray() {
        val cur = arrayOf("a", "b", null)
        assertThat(ArrayUtils.appendString(cur, null)).isEqualTo(cur)
    }

    @Test
    fun appendString_appendToEmptyArray_returnsArrayWithNewString() {
        val cur = arrayOf<String>()
        val new = arrayOf(TEST_STRING)
        assertThat(ArrayUtils.appendString(cur, TEST_STRING)).isEqualTo(new)
    }

    @Test
    fun appendString_appendNullToEmptyArray_returnsArrayWithNewString() {
        val cur = arrayOf<String>()
        val result = ArrayUtils.appendString(cur, null)
        assertThat(result.size).isEqualTo(1)
        assertThat(result[0]).isNull()
    }

    @Test
    fun appendString_appendNewString() {
        val cur = arrayOf("old test")
        val new = arrayOf("old test", TEST_STRING)
        assertThat(ArrayUtils.appendString(cur, TEST_STRING)).isEqualTo(new)
    }

    companion object {
        private const val TEST_STRING = "test"
    }
}
