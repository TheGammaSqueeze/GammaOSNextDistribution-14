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

import com.android.car.util.SetMultimap;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import org.junit.Test;

public final class SetMultimapTest {

    @Test
    public void testGet_empty_returnsEmptySet() {
        SetMultimap<Integer, String> map = new SetMultimap<>();

        assertThat(map.get(1)).isEmpty();
    }

    @Test
    public void testGet_keyDoesNotExist_returnsEmptySet() {
        SetMultimap<Integer, String> map = createFromMultimap(ImmutableMultimap.of(1, "value1"));

        assertThat(map.get(2)).isEmpty();
    }

    @Test
    public void testGet() {
        SetMultimap<Integer, String> map = new SetMultimap<>();

        map.put(1, "value1");
        assertThat(map.get(1)).containsExactly("value1");
        map.put(1, "value2");
        assertThat(map.get(1)).containsExactly("value1", "value2");
        map.put(1, "value3");
        assertThat(map.get(1)).containsExactly("value1", "value2", "value3");
    }

    @Test
    public void testPut_duplicateValue() {
        SetMultimap<Integer, String> map = new SetMultimap<>();
        map.put(1, "value1");
        map.put(1, "value1");

        assertThat(map.get(1)).containsExactly("value1");
    }

    @Test
    public void testContainsEntry() {
        SetMultimap<Integer, String> map = createFromMultimap(ImmutableMultimap.of(1, "value1"));

        assertThat(map.containsEntry(1, "value1")).isTrue();
    }

    @Test
    public void testContainsEntry_keyDoesNotExist() {
        SetMultimap<Integer, String> map = createFromMultimap(ImmutableMultimap.of(1, "value1"));

        assertThat(map.containsEntry(2, "value1")).isFalse();
    }

    @Test
    public void testContainsEntry_valueDoesNotExist() {
        SetMultimap<Integer, String> map = createFromMultimap(ImmutableMultimap.of(1, "value1"));

        assertThat(map.containsEntry(1, "value2")).isFalse();
    }

    @Test
    public void testRemove_success() {
        SetMultimap<Integer, String> map = createFromMultimap(
                ImmutableMultimap.of(1, "value1", 1, "value2", 2, "value3"));

        assertThat(map.remove(1, "value1")).isTrue();
        assertContainsExactlyEntries(map, ImmutableMultimap.of(1, "value2", 2, "value3"));
    }

    @Test
    public void testRemove_lastEntryOfKey() {
        SetMultimap<Integer, String> map = createFromMultimap(
                ImmutableMultimap.of(1, "value1", 1, "value2", 2, "value3"));

        assertThat(map.remove(2, "value3")).isTrue();
        assertContainsExactlyEntries(map, ImmutableMultimap.of(1, "value1", 1, "value2"));
    }

    @Test
    public void testRemove_keyDoesNotExist() {
        SetMultimap<Integer, String> map = createFromMultimap(
                ImmutableMultimap.of(1, "value1", 1, "value2", 2, "value3"));

        assertThat(map.remove(3, "value3")).isFalse();
        assertContainsExactlyEntries(
                map, ImmutableMultimap.of(1, "value1", 1, "value2", 2, "value3"));
    }

    @Test
    public void testRemove_entryDoesNotExist() {
        SetMultimap<Integer, String> map = createFromMultimap(
                ImmutableMultimap.of(1, "value1", 1, "value2", 2, "value3"));

        assertThat(map.remove(1, "value3")).isFalse();
        assertContainsExactlyEntries(
                map, ImmutableMultimap.of(1, "value1", 1, "value2", 2, "value3"));
    }

    @Test
    public void testClear() {
        SetMultimap<Integer, String> map = createFromMultimap(
                ImmutableMultimap.of(1, "value1", 1, "value2", 2, "value3"));

        map.clear();

        assertContainsExactlyEntries(map, ImmutableMultimap.<Integer, String>of());
    }

    @Test
    public void testKeySet() {
        SetMultimap<Integer, String> map = createFromMultimap(
                ImmutableMultimap.of(1, "value1", 1, "value2", 2, "value3"));

        assertThat(map.keySet()).containsExactly(1, 2);
    }

    @Test
    public void testKeySet_empty() {
        SetMultimap<Integer, String> map = new SetMultimap<>();

        assertThat(map.keySet()).isEmpty();
    }

    private static <K, V> SetMultimap<K, V> createFromMultimap(Multimap<K, V> multimap) {
        SetMultimap<K, V> map = new SetMultimap<>();
        multimap.entries().forEach(entry -> map.put(entry.getKey(), entry.getValue()));

        return map;
    }

    private static <K, V> void assertContainsExactlyEntries(
            SetMultimap<K, V> actual, Multimap<K, V> expected) {
        ImmutableMultimap.Builder<K, V> multimapBuilder = ImmutableMultimap.builder();
        actual.keySet().forEach(key -> multimapBuilder.putAll(key, actual.get(key)));

        assertThat(multimapBuilder.build()).containsExactlyEntriesIn(expected);
    }
}
