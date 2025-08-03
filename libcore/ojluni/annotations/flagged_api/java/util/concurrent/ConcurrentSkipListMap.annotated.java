/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */


package java.util.concurrent;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ConcurrentSkipListMap<K, V> extends java.util.AbstractMap<K,V> implements java.util.concurrent.ConcurrentNavigableMap<K,V>, java.lang.Cloneable, java.io.Serializable {

public ConcurrentSkipListMap() { throw new RuntimeException("Stub!"); }

public ConcurrentSkipListMap(java.util.Comparator<? super K> comparator) { throw new RuntimeException("Stub!"); }

public ConcurrentSkipListMap(java.util.Map<? extends K,? extends V> m) { throw new RuntimeException("Stub!"); }

public ConcurrentSkipListMap(java.util.SortedMap<K,? extends V> m) { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentSkipListMap<K,V> clone() { throw new RuntimeException("Stub!"); }

public boolean containsKey(java.lang.Object key) { throw new RuntimeException("Stub!"); }

public V get(java.lang.Object key) { throw new RuntimeException("Stub!"); }

public V getOrDefault(java.lang.Object key, V defaultValue) { throw new RuntimeException("Stub!"); }

public V put(K key, V value) { throw new RuntimeException("Stub!"); }

public V remove(java.lang.Object key) { throw new RuntimeException("Stub!"); }

public boolean containsValue(java.lang.Object value) { throw new RuntimeException("Stub!"); }

public int size() { throw new RuntimeException("Stub!"); }

public boolean isEmpty() { throw new RuntimeException("Stub!"); }

public void clear() { throw new RuntimeException("Stub!"); }

public V computeIfAbsent(K key, java.util.function.Function<? super K,? extends V> mappingFunction) { throw new RuntimeException("Stub!"); }

public V computeIfPresent(K key, java.util.function.BiFunction<? super K,? super V,? extends V> remappingFunction) { throw new RuntimeException("Stub!"); }

public V compute(K key, java.util.function.BiFunction<? super K,? super V,? extends V> remappingFunction) { throw new RuntimeException("Stub!"); }

public V merge(K key, V value, java.util.function.BiFunction<? super V,? super V,? extends V> remappingFunction) { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<K> keySet() { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<K> navigableKeySet() { throw new RuntimeException("Stub!"); }

public java.util.Collection<V> values() { throw new RuntimeException("Stub!"); }

public java.util.Set<java.util.Map.Entry<K,V>> entrySet() { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentNavigableMap<K,V> descendingMap() { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<K> descendingKeySet() { throw new RuntimeException("Stub!"); }

public boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public V putIfAbsent(K key, V value) { throw new RuntimeException("Stub!"); }

public boolean remove(java.lang.Object key, java.lang.Object value) { throw new RuntimeException("Stub!"); }

public boolean replace(K key, V oldValue, V newValue) { throw new RuntimeException("Stub!"); }

public V replace(K key, V value) { throw new RuntimeException("Stub!"); }

public java.util.Comparator<? super K> comparator() { throw new RuntimeException("Stub!"); }

public K firstKey() { throw new RuntimeException("Stub!"); }

public K lastKey() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public V putFirst(K k, V v) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public V putLast(K k, V v) { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentNavigableMap<K,V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentNavigableMap<K,V> headMap(K toKey, boolean inclusive) { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentNavigableMap<K,V> tailMap(K fromKey, boolean inclusive) { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentNavigableMap<K,V> subMap(K fromKey, K toKey) { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentNavigableMap<K,V> headMap(K toKey) { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentNavigableMap<K,V> tailMap(K fromKey) { throw new RuntimeException("Stub!"); }

public java.util.Map.Entry<K,V> lowerEntry(K key) { throw new RuntimeException("Stub!"); }

public K lowerKey(K key) { throw new RuntimeException("Stub!"); }

public java.util.Map.Entry<K,V> floorEntry(K key) { throw new RuntimeException("Stub!"); }

public K floorKey(K key) { throw new RuntimeException("Stub!"); }

public java.util.Map.Entry<K,V> ceilingEntry(K key) { throw new RuntimeException("Stub!"); }

public K ceilingKey(K key) { throw new RuntimeException("Stub!"); }

public java.util.Map.Entry<K,V> higherEntry(K key) { throw new RuntimeException("Stub!"); }

public K higherKey(K key) { throw new RuntimeException("Stub!"); }

public java.util.Map.Entry<K,V> firstEntry() { throw new RuntimeException("Stub!"); }

public java.util.Map.Entry<K,V> lastEntry() { throw new RuntimeException("Stub!"); }

public java.util.Map.Entry<K,V> pollFirstEntry() { throw new RuntimeException("Stub!"); }

public java.util.Map.Entry<K,V> pollLastEntry() { throw new RuntimeException("Stub!"); }

public void forEach(java.util.function.BiConsumer<? super K,? super V> action) { throw new RuntimeException("Stub!"); }

public void replaceAll(java.util.function.BiFunction<? super K,? super V,? extends V> function) { throw new RuntimeException("Stub!"); }
}

