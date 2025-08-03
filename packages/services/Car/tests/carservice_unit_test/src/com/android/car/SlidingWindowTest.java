/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.car.util.SlidingWindow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class SlidingWindowTest {

    private static <T> boolean sameContents(Iterator<T> t1, Iterator<T> t2) {
        while (t1.hasNext()) {
            if (!t2.hasNext()) {
                return false;
            }
            T e1 = t1.next();
            T e2 = t2.next();
            if (!e1.equals(e2)) {
                return false;
            }
        }
        return !t2.hasNext();
    }

    @Test
    public void testAdd() {
        List<Integer> elements = Arrays.asList(3, 4);
        SlidingWindow<Integer> window = new SlidingWindow<>(5);
        elements.forEach(window::add);
        assertThat(sameContents(elements.iterator(), window.iterator())).isTrue();
    }

    @Test
    public void testSize() {
        SlidingWindow<Integer> window = new SlidingWindow<>(10);
        assertThat(window.size()).isEqualTo(0);
        window.addAll(Arrays.asList(1, 2, 3));
        assertThat(window.size()).isEqualTo(3);
    }

    @Test
    public void testAddOverflow() {
        List<Integer> elements = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Integer> expectedElements = Arrays.asList(6, 7, 8, 9, 10);
        SlidingWindow<Integer> window = new SlidingWindow<>(5);
        window.addAll(elements);
        assertThat(sameContents(expectedElements.iterator(), window.iterator())).isTrue();
    }

    @Test
    public void testStream() {
        List<Integer> elements = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Integer> expectedElements = Arrays.asList(6, 7, 8, 9, 10);
        SlidingWindow<Integer> window = new SlidingWindow<>(5);
        window.addAll(elements);
        expectedElements.stream().forEach(
                e -> assertThat(window.stream().anyMatch(e::equals)).isTrue());
    }

    @Test
    public void testCount() {
        List<Integer> elements = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        SlidingWindow<Integer> window = new SlidingWindow<>(5);
        window.addAll(elements);
        assertThat(window.count((Integer e) -> (e % 2) == 0)).isEqualTo(3);
    }
}
