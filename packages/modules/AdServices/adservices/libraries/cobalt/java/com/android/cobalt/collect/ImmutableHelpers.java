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

package com.android.cobalt.collect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;

import java.util.function.Function;
import java.util.stream.Collector;

/** Helper functions for working with immutable collections. */
public final class ImmutableHelpers {
    private ImmutableHelpers() {}

    /** Collector to create immutable lists. */
    public static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
        return Collector.of(
                ImmutableList.Builder<T>::new,
                (l, v) -> l.add(v),
                (l1, l2) -> l1.addAll(l2.build()),
                ImmutableList.Builder::build);
    }

    /** Collector to create immutable maps. */
    public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
            Function<? super T, ? extends K> keyMap, Function<? super T, ? extends V> valueMap) {
        return Collector.of(
                ImmutableMap.Builder<K, V>::new,
                (m, e) -> m.put(keyMap.apply(e), valueMap.apply(e)),
                (m1, m2) -> m1.putAll(m2.build()),
                ImmutableMap.Builder::build);
    }

    /** Collector to create immutable list multimaps. */
    public static <T, K, V> Collector<T, ?, ImmutableListMultimap<K, V>> toImmutableListMultimap(
            Function<? super T, ? extends K> keyMap, Function<? super T, ? extends V> valueMap) {
        return Collector.of(
                ImmutableListMultimap.Builder<K, V>::new,
                (m, e) -> m.put(keyMap.apply(e), valueMap.apply(e)),
                (m1, m2) -> m1.putAll(m2.build()),
                ImmutableListMultimap.Builder::build);
    }
}
