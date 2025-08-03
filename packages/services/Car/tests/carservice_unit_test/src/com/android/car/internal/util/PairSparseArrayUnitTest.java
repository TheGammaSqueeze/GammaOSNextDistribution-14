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

package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;

import org.junit.Test;

public class PairSparseArrayUnitTest extends AbstractExtendedMockitoTestCase {
    private static final int FIRST_KEY = 10;
    private static final int SECOND_KEY = 20;
    private static final int THIRD_KEY = 30;
    private static final int FOURTH_KEY = 40;
    private static final int NEGATIVE_FIRST_KEY = -10;
    private static final int NEGATIVE_SECOND_KEY = -20;
    private static final int NEGATIVE_THIRD_KEY = -30;
    private static final int NEGATIVE_FOURTH_KEY = -40;
    private static final int VALUE_1 = 40;
    private static final int VALUE_2 = 60;

    @Test
    public void test_sizeGetPut() {
        PairSparseArray<Integer> map = new PairSparseArray<>();

        assertThat(map.size()).isEqualTo(0);
        assertThat(map.get(FIRST_KEY, SECOND_KEY)).isEqualTo(null);

        map.put(FIRST_KEY, SECOND_KEY, VALUE_1);

        assertThat(map.size()).isEqualTo(1);
        assertThat(map.get(FIRST_KEY, SECOND_KEY, null)).isEqualTo(VALUE_1);
    }

    @Test
    public void test_appendContainsRemove() {
        PairSparseArray<Integer> map = new PairSparseArray<>();

        map.append(FIRST_KEY, SECOND_KEY, VALUE_1);

        assertThat(map.contains(FIRST_KEY, SECOND_KEY)).isTrue();
        assertThat(map.size()).isEqualTo(1);

        map.remove(FIRST_KEY, SECOND_KEY);

        assertThat(map.contains(FIRST_KEY, SECOND_KEY)).isFalse();
    }

    @Test
    public void test_indexMethods() {
        PairSparseArray<Integer> map = new PairSparseArray<>();

        map.append(FIRST_KEY, SECOND_KEY, VALUE_1);

        assertThat(map.indexOfKeyPair(FIRST_KEY, SECOND_KEY)).isEqualTo(0);
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.keyPairAt(0)).asList().containsExactly(FIRST_KEY, SECOND_KEY);

        map.setValueAt(0, VALUE_2);

        assertThat(map.valueAt(0)).isEqualTo(VALUE_2);

        map.removeAt(0);

        assertThat(map.indexOfValue(VALUE_1)).isLessThan(0);
    }

    @Test
    public void test_similarKeys() {
        PairSparseArray<Integer> map = new PairSparseArray<>(16);

        map.put(FIRST_KEY, FIRST_KEY, VALUE_1);
        map.put(FIRST_KEY, SECOND_KEY, VALUE_1);
        map.put(FIRST_KEY, THIRD_KEY, VALUE_1);
        map.put(FIRST_KEY, FOURTH_KEY, VALUE_1);
        map.put(SECOND_KEY, FIRST_KEY, VALUE_1);
        map.put(SECOND_KEY, SECOND_KEY, VALUE_1);
        map.put(SECOND_KEY, THIRD_KEY, VALUE_1);
        map.put(SECOND_KEY, FOURTH_KEY, VALUE_1);
        map.put(THIRD_KEY, FIRST_KEY, VALUE_1);
        map.put(THIRD_KEY, SECOND_KEY, VALUE_1);
        map.put(THIRD_KEY, THIRD_KEY, VALUE_1);
        map.put(THIRD_KEY, FOURTH_KEY, VALUE_1);
        map.put(FOURTH_KEY, FIRST_KEY, VALUE_1);
        map.put(FOURTH_KEY, SECOND_KEY, VALUE_1);
        map.put(FOURTH_KEY, THIRD_KEY, VALUE_1);
        map.put(FOURTH_KEY, FOURTH_KEY, VALUE_1);

        assertThat(map.size()).isEqualTo(16);
        assertThat(map.indexOfKeyPair(FIRST_KEY, SECOND_KEY))
                .isNotEqualTo(map.indexOfKeyPair(SECOND_KEY, FIRST_KEY));

        map.delete(FIRST_KEY, SECOND_KEY);

        assertThat(map.size()).isEqualTo(15);

        map.clear();

        assertThat(map.size()).isEqualTo(0);
    }

    @Test
    public void test_negativeKeys() {
        PairSparseArray<Integer> map = new PairSparseArray<>(12);

        map.put(FIRST_KEY, NEGATIVE_FIRST_KEY, VALUE_1);
        map.put(NEGATIVE_FIRST_KEY, FIRST_KEY, VALUE_1);
        map.put(NEGATIVE_FIRST_KEY, NEGATIVE_FIRST_KEY, VALUE_1);
        map.put(SECOND_KEY, NEGATIVE_SECOND_KEY, VALUE_1);
        map.put(NEGATIVE_SECOND_KEY, SECOND_KEY, VALUE_1);
        map.put(NEGATIVE_SECOND_KEY, NEGATIVE_SECOND_KEY, VALUE_1);
        map.put(THIRD_KEY, NEGATIVE_THIRD_KEY, VALUE_1);
        map.put(NEGATIVE_THIRD_KEY, THIRD_KEY, VALUE_1);
        map.put(NEGATIVE_THIRD_KEY, NEGATIVE_THIRD_KEY, VALUE_1);
        map.put(FOURTH_KEY, NEGATIVE_FOURTH_KEY, VALUE_1);
        map.put(NEGATIVE_FOURTH_KEY, FOURTH_KEY, VALUE_1);
        map.put(NEGATIVE_FOURTH_KEY, NEGATIVE_FOURTH_KEY, VALUE_1);

        assertThat(map.size()).isEqualTo(12);
        assertThat(map.indexOfKeyPair(FIRST_KEY, NEGATIVE_FIRST_KEY))
                .isNotEqualTo(map.indexOfKeyPair(NEGATIVE_FIRST_KEY, FIRST_KEY));
        assertThat(map.indexOfKeyPair(NEGATIVE_FIRST_KEY, NEGATIVE_FIRST_KEY)).isGreaterThan(-1);

        map.remove(NEGATIVE_FOURTH_KEY, FOURTH_KEY);

        assertThat(map.size()).isEqualTo(11);
    }

    @Test
    public void test_getSecondKeysForFirstKey() {
        PairSparseArray<Integer> map = new PairSparseArray<>(16);

        map.put(FIRST_KEY, FIRST_KEY, VALUE_1);
        map.put(SECOND_KEY, FIRST_KEY, VALUE_1);
        map.put(SECOND_KEY, SECOND_KEY, VALUE_1);
        map.put(THIRD_KEY, FIRST_KEY, VALUE_1);
        map.put(THIRD_KEY, SECOND_KEY, VALUE_1);
        map.put(THIRD_KEY, THIRD_KEY, VALUE_1);
        map.put(FOURTH_KEY, FIRST_KEY, VALUE_1);
        map.put(FOURTH_KEY, SECOND_KEY, VALUE_1);
        map.put(FOURTH_KEY, THIRD_KEY, VALUE_1);
        map.put(FOURTH_KEY, FOURTH_KEY, VALUE_1);

        expectThat(map.getSecondKeysForFirstKey(FIRST_KEY)).containsExactly(FIRST_KEY);
        expectThat(map.getSecondKeysForFirstKey(SECOND_KEY)).containsExactly(FIRST_KEY, SECOND_KEY);
        expectThat(map.getSecondKeysForFirstKey(THIRD_KEY)).containsExactly(FIRST_KEY, SECOND_KEY,
                THIRD_KEY);
        expectThat(map.getSecondKeysForFirstKey(FOURTH_KEY)).containsExactly(FIRST_KEY, SECOND_KEY,
                THIRD_KEY, FOURTH_KEY);
    }

    @Test
    public void test_getFirstKeys() {
        PairSparseArray<Integer> map = new PairSparseArray<>(16);

        map.put(FIRST_KEY, FIRST_KEY, VALUE_1);
        map.put(SECOND_KEY, FIRST_KEY, VALUE_1);
        map.put(THIRD_KEY, FIRST_KEY, VALUE_1);
        map.put(FOURTH_KEY, FIRST_KEY, VALUE_1);
        map.put(FOURTH_KEY, SECOND_KEY, VALUE_1);

        assertThat(map.getFirstKeys()).containsExactly(
                FIRST_KEY, SECOND_KEY, THIRD_KEY, FOURTH_KEY);
    }
}
