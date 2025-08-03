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

package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.List;

public final class ListsTest {

    @Test
    public void testNewArrayList_passingEmptyParam() {
        // Test random array
        assertThat(Lists.newArrayList()).isEmpty();
    }

    @Test
    public void testNewArrayList_passingParams() {
        // Test random array
        assertThat(Lists.newArrayList(1, 2, 3)).containsExactly(1, 2, 3);
    }

    @Test
    public void testAsImmutableIntegerList() {
        // Test empty array
        assertThat(Lists.asImmutableList(new int[0])).isEmpty();

        // Test random array
        assertThat(Lists.asImmutableList(new int[]{1, 2, 3})).containsExactly(1, 2, 3);
    }

    @Test
    public void testAsImmutableIntegerList_returnsImmutableList() {
        List<Integer> immutableList = Lists.asImmutableList(new int[]{1, 2, 3});
        assertThrows(UnsupportedOperationException.class, () -> immutableList.add(4));
    }
}
