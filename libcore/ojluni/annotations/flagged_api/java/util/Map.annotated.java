/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package java.util;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public interface Map<K, V> {

public int size();

public boolean isEmpty();

public boolean containsKey(java.lang.Object key);

public boolean containsValue(java.lang.Object value);

public V get(java.lang.Object key);

public V put(K key, V value);

public V remove(java.lang.Object key);

public void putAll(java.util.Map<? extends K,? extends V> m);

public void clear();

public java.util.Set<K> keySet();

public java.util.Collection<V> values();

public java.util.Set<java.util.Map.Entry<K,V>> entrySet();

public boolean equals(java.lang.Object o);

public int hashCode();

public default V getOrDefault(java.lang.Object key, V defaultValue) { throw new RuntimeException("Stub!"); }

public default void forEach(java.util.function.BiConsumer<? super K,? super V> action) { throw new RuntimeException("Stub!"); }

public default void replaceAll(java.util.function.BiFunction<? super K,? super V,? extends V> function) { throw new RuntimeException("Stub!"); }

public default V putIfAbsent(K key, V value) { throw new RuntimeException("Stub!"); }

public default boolean remove(java.lang.Object key, java.lang.Object value) { throw new RuntimeException("Stub!"); }

public default boolean replace(K key, V oldValue, V newValue) { throw new RuntimeException("Stub!"); }

public default V replace(K key, V value) { throw new RuntimeException("Stub!"); }

public default V computeIfAbsent(K key, java.util.function.Function<? super K,? extends V> mappingFunction) { throw new RuntimeException("Stub!"); }

public default V computeIfPresent(K key, java.util.function.BiFunction<? super K,? super V,? extends V> remappingFunction) { throw new RuntimeException("Stub!"); }

public default V compute(K key, java.util.function.BiFunction<? super K,? super V,? extends V> remappingFunction) { throw new RuntimeException("Stub!"); }

public default V merge(K key, V value, java.util.function.BiFunction<? super V,? super V,? extends V> remappingFunction) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of() { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) { throw new RuntimeException("Stub!"); }

@java.lang.SafeVarargs
public static <K, V> java.util.Map<K,V> ofEntries(java.util.Map.Entry<? extends K,? extends V>... entries) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map.Entry<K,V> entry(K k, V v) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> copyOf(java.util.Map<? extends K,? extends V> map) { throw new RuntimeException("Stub!"); }
@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface Entry<K, V> {

public K getKey();

public V getValue();

public V setValue(V value);

public boolean equals(java.lang.Object o);

public int hashCode();

public static <K extends java.lang.Comparable<? super K>, V> java.util.Comparator<java.util.Map.Entry<K,V>> comparingByKey() { throw new RuntimeException("Stub!"); }

public static <K, V extends java.lang.Comparable<? super V>> java.util.Comparator<java.util.Map.Entry<K,V>> comparingByValue() { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Comparator<java.util.Map.Entry<K,V>> comparingByKey(java.util.Comparator<? super K> cmp) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Comparator<java.util.Map.Entry<K,V>> comparingByValue(java.util.Comparator<? super V> cmp) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static <K, V> java.util.Map.Entry<K,V> copyOf(java.util.Map.Entry<? extends K,? extends V> e) { throw new RuntimeException("Stub!"); }
}

}

