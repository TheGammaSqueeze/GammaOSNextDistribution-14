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

package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import static java.util.stream.Collectors.toList;

import android.util.Pair;
import android.util.SparseArray;

import com.android.car.util.SparseArrayStream;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class SparseArrayStreamTest {

    @Test
    public void testKeyStream() {
        SparseArray<String> array = createSparseStringArray();
        ImmutableList<Integer> expected = ImmutableList.of(5, 20, 1000);

        IntStream actual = SparseArrayStream.keyStream(array);

        assertContainsExactlyInOrder(actual.boxed(), expected);
    }

    @Test
    public void testKeyStream_empty() {
        assertContainsExactlyInOrder(
                SparseArrayStream.keyStream(new SparseArray<String>()).boxed(), ImmutableList.of());
    }

    @Test
    public void testValueStream() {
        SparseArray<String> array = createSparseStringArray();
        ImmutableList<String> expected = ImmutableList.of("five", "twenty", "thousand");

        Stream<String> actual = SparseArrayStream.valueStream(array);

        assertContainsExactlyInOrder(actual, expected);
    }

    @Test
    public void testValueStream_empty() {
        assertContainsExactlyInOrder(
                SparseArrayStream.valueStream(new SparseArray<String>()), ImmutableList.of());
    }

    @Test
    public void testPairStream() {
        SparseArray<String> array = createSparseStringArray();
        ImmutableList<Pair<Integer, String>> expected = ImmutableList.of(
                new Pair<>(5, "five"), new Pair<>(20, "twenty"), new Pair<>(1000, "thousand"));

        Stream<Pair<Integer, String>> actual = SparseArrayStream.pairStream(array);

        assertContainsExactlyInOrder(actual, expected);
    }

    @Test
    public void testPairStream_empty() {
        assertContainsExactlyInOrder(
                SparseArrayStream.pairStream(new SparseArray<String>()), ImmutableList.of());
    }

    private static SparseArray<String> createSparseStringArray() {
        SparseArray<String> array = new SparseArray<>();
        array.put(5, "five");
        array.put(20, "twenty");
        array.put(1000, "thousand");

        return array;
    }

    private static <T> void assertContainsExactlyInOrder(Stream<T> actual, List<T> expected) {
        // TODO: Use Truth8 StreamSubject when it becomes available.
        assertThat(actual.collect(toList())).containsExactlyElementsIn(expected).inOrder();
    }
}
