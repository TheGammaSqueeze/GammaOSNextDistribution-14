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

package com.android.car.hal;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class BidirectionalSparseIntArrayTest {
    private static final int SOME_DEFAULT_KEY = -1;
    private static final int SOME_DEFAULT_VALUE = -1;

    @Test
    public void testGetKey() throws Exception {
        int key = 1;
        int val = 1000;
        BidirectionalSparseIntArray intArr =
                BidirectionalSparseIntArray.create(new int[]{key, val});

        assertThat(intArr.getKey(val, /* defaultKey= */ SOME_DEFAULT_KEY)).isEqualTo(key);
    }

    @Test
    public void testGetValue() throws Exception {
        int key = 1;
        int val = 1000;
        BidirectionalSparseIntArray intArr =
                BidirectionalSparseIntArray.create(new int[]{key, val});

        assertThat(intArr.getValue(key, /* defaultValue= */ SOME_DEFAULT_VALUE)).isEqualTo(val);
    }

    @Test
    public void testGetKey_returnsDefaultKeyForNonexistentKey() throws Exception {
        int key = 5;
        int val = 100;
        BidirectionalSparseIntArray intArr =
                BidirectionalSparseIntArray.create(new int[]{key, val});

        assertThat(intArr.getKey(val + 1, SOME_DEFAULT_KEY)).isEqualTo(SOME_DEFAULT_KEY);
    }

    @Test
    public void testGetValue_returnsDefaultValueForNonexistentValue() throws Exception {
        int key = 5;
        int val = 100;
        BidirectionalSparseIntArray intArr =
                BidirectionalSparseIntArray.create(new int[]{key, val});

        assertThat(intArr.getValue(key + 1, SOME_DEFAULT_VALUE)).isEqualTo(SOME_DEFAULT_VALUE);
    }

    @Test
    public void testCreate_failToCreateArrayWithOddNumberOfElements() throws Exception {
        int[] keyvaluePairs = {1, 2, 3};

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BidirectionalSparseIntArray.create(keyvaluePairs));

        assertThat(thrown).hasMessageThat().contains("Odd number of key-value elements");
    }
}
