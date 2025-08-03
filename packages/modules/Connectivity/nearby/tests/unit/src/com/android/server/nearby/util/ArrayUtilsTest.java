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

package com.android.server.nearby.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public final class ArrayUtilsTest {

    private static final byte[] BYTES_ONE = new byte[] {7, 9};
    private static final byte[] BYTES_TWO = new byte[] {8};
    private static final byte[] BYTES_EMPTY = new byte[] {};
    private static final byte[] BYTES_ALL = new byte[] {7, 9, 8};

    @Test
    public void testConcatByteArraysNoInput() {
        assertThat(ArrayUtils.concatByteArrays().length).isEqualTo(0);
    }

    @Test
    public void testConcatByteArraysOneEmptyArray() {
        assertThat(ArrayUtils.concatByteArrays(BYTES_EMPTY).length).isEqualTo(0);
    }

    @Test
    public void testConcatByteArraysOneNonEmptyArray() {
        assertThat(ArrayUtils.concatByteArrays(BYTES_ONE)).isEqualTo(BYTES_ONE);
    }

    @Test
    public void testConcatByteArraysMultipleNonEmptyArrays() {
        assertThat(ArrayUtils.concatByteArrays(BYTES_ONE, BYTES_TWO)).isEqualTo(BYTES_ALL);
    }

    @Test
    public void testConcatByteArraysMultipleArrays() {
        assertThat(ArrayUtils.concatByteArrays(BYTES_ONE, BYTES_EMPTY, BYTES_TWO))
                .isEqualTo(BYTES_ALL);
    }

    @Test
    public void testIsEmptyNull_returnsTrue() {
        assertThat(ArrayUtils.isEmpty(null)).isTrue();
    }

    @Test
    public void testIsEmpty_returnsTrue() {
        assertThat(ArrayUtils.isEmpty(new byte[]{})).isTrue();
    }

    @Test
    public void testIsEmpty_returnsFalse() {
        assertThat(ArrayUtils.isEmpty(BYTES_ALL)).isFalse();
    }

    @Test
    public void testAppendByte() {
        assertThat(ArrayUtils.append((byte) 2, BYTES_ONE)).isEqualTo(new byte[]{2, 7, 9});
    }

    @Test
    public void testAppendByteNull() {
        assertThat(ArrayUtils.append((byte) 2, null)).isEqualTo(new byte[]{2});
    }

    @Test
    public void testAppendByteToArray() {
        assertThat(ArrayUtils.append(BYTES_ONE, (byte) 2)).isEqualTo(new byte[]{7, 9, 2});
    }
}
