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

import com.android.permissioncontroller.permission.utils.CollectionUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CollectionUtilsTest {
    @Test
    fun testContains_true() {
        val byteArrays = setOf(TEST_BYTE_ARRAY_1, TEST_BYTE_ARRAY_2, TEST_BYTE_ARRAY_3)

        assertThat(CollectionUtils.contains(byteArrays, TEST_BYTE_ARRAY_1)).isTrue()
    }

    @Test
    fun testContains_false() {
        val byteArrays = setOf(TEST_BYTE_ARRAY_1, TEST_BYTE_ARRAY_2, TEST_BYTE_ARRAY_3)

        assertThat(CollectionUtils.contains(byteArrays, TEST_BYTE_ARRAY_4)).isFalse()
    }

    @Test
    fun testContainsSubset_true() {
        val byteArrays = setOf(TEST_BYTE_ARRAY_1, TEST_BYTE_ARRAY_2, TEST_BYTE_ARRAY_3)
        val otherByteArrays = setOf(TEST_BYTE_ARRAY_2, TEST_BYTE_ARRAY_3)

        assertThat(CollectionUtils.containsSubset(byteArrays, otherByteArrays)).isTrue()
    }

    @Test
    fun testContainsSubset_false() {
        val byteArrays = setOf(TEST_BYTE_ARRAY_1, TEST_BYTE_ARRAY_2, TEST_BYTE_ARRAY_3)
        val otherByteArrays = setOf(TEST_BYTE_ARRAY_3, TEST_BYTE_ARRAY_4)

        assertThat(CollectionUtils.containsSubset(byteArrays, otherByteArrays)).isFalse()
    }

    companion object {
        private val TEST_BYTE_ARRAY_1 = "I".toByteArray()
        private val TEST_BYTE_ARRAY_2 = "love".toByteArray()
        private val TEST_BYTE_ARRAY_3 = "Android".toByteArray()
        private val TEST_BYTE_ARRAY_4 = "Hello".toByteArray()
    }
}
