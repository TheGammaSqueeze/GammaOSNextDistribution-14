/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
public class Collections {

Collections() { throw new RuntimeException("Stub!"); }

public static <T extends java.lang.Comparable<? super T>> void sort(java.util.List<T> list) { throw new RuntimeException("Stub!"); }

public static <T> void sort(java.util.List<T> list, java.util.Comparator<? super T> c) { throw new RuntimeException("Stub!"); }

public static <T> int binarySearch(java.util.List<? extends java.lang.Comparable<? super T>> list, T key) { throw new RuntimeException("Stub!"); }

public static <T> int binarySearch(java.util.List<? extends T> list, T key, java.util.Comparator<? super T> c) { throw new RuntimeException("Stub!"); }

public static void reverse(java.util.List<?> list) { throw new RuntimeException("Stub!"); }

public static void shuffle(java.util.List<?> list) { throw new RuntimeException("Stub!"); }

public static void shuffle(java.util.List<?> list, java.util.Random rnd) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static void shuffle(java.util.List<?> list, java.util.random.RandomGenerator rnd) { throw new RuntimeException("Stub!"); }

public static void swap(java.util.List<?> list, int i, int j) { throw new RuntimeException("Stub!"); }

public static <T> void fill(java.util.List<? super T> list, T obj) { throw new RuntimeException("Stub!"); }

public static <T> void copy(java.util.List<? super T> dest, java.util.List<? extends T> src) { throw new RuntimeException("Stub!"); }

public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T min(java.util.Collection<? extends T> coll) { throw new RuntimeException("Stub!"); }

public static <T> T min(java.util.Collection<? extends T> coll, java.util.Comparator<? super T> comp) { throw new RuntimeException("Stub!"); }

public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T> coll) { throw new RuntimeException("Stub!"); }

public static <T> T max(java.util.Collection<? extends T> coll, java.util.Comparator<? super T> comp) { throw new RuntimeException("Stub!"); }

public static void rotate(java.util.List<?> list, int distance) { throw new RuntimeException("Stub!"); }

public static <T> boolean replaceAll(java.util.List<T> list, T oldVal, T newVal) { throw new RuntimeException("Stub!"); }

public static int indexOfSubList(java.util.List<?> source, java.util.List<?> target) { throw new RuntimeException("Stub!"); }

public static int lastIndexOfSubList(java.util.List<?> source, java.util.List<?> target) { throw new RuntimeException("Stub!"); }

public static <T> java.util.Collection<T> unmodifiableCollection(java.util.Collection<? extends T> c) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static <T> java.util.SequencedCollection<T> unmodifiableSequencedCollection(java.util.SequencedCollection<? extends T> c) { throw new RuntimeException("Stub!"); }

public static <T> java.util.Set<T> unmodifiableSet(java.util.Set<? extends T> s) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static <T> java.util.SequencedSet<T> unmodifiableSequencedSet(java.util.SequencedSet<? extends T> s) { throw new RuntimeException("Stub!"); }

public static <T> java.util.SortedSet<T> unmodifiableSortedSet(java.util.SortedSet<T> s) { throw new RuntimeException("Stub!"); }

public static <T> java.util.NavigableSet<T> unmodifiableNavigableSet(java.util.NavigableSet<T> s) { throw new RuntimeException("Stub!"); }

public static <T> java.util.List<T> unmodifiableList(java.util.List<? extends T> list) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> unmodifiableMap(java.util.Map<? extends K,? extends V> m) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static <K, V> java.util.SequencedMap<K,V> unmodifiableSequencedMap(java.util.SequencedMap<? extends K,? extends V> m) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.SortedMap<K,V> unmodifiableSortedMap(java.util.SortedMap<K,? extends V> m) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.NavigableMap<K,V> unmodifiableNavigableMap(java.util.NavigableMap<K,? extends V> m) { throw new RuntimeException("Stub!"); }

public static <T> java.util.Collection<T> synchronizedCollection(java.util.Collection<T> c) { throw new RuntimeException("Stub!"); }

public static <T> java.util.Set<T> synchronizedSet(java.util.Set<T> s) { throw new RuntimeException("Stub!"); }

public static <T> java.util.SortedSet<T> synchronizedSortedSet(java.util.SortedSet<T> s) { throw new RuntimeException("Stub!"); }

public static <T> java.util.NavigableSet<T> synchronizedNavigableSet(java.util.NavigableSet<T> s) { throw new RuntimeException("Stub!"); }

public static <T> java.util.List<T> synchronizedList(java.util.List<T> list) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> synchronizedMap(java.util.Map<K,V> m) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.SortedMap<K,V> synchronizedSortedMap(java.util.SortedMap<K,V> m) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.NavigableMap<K,V> synchronizedNavigableMap(java.util.NavigableMap<K,V> m) { throw new RuntimeException("Stub!"); }

public static <E> java.util.Collection<E> checkedCollection(java.util.Collection<E> c, java.lang.Class<E> type) { throw new RuntimeException("Stub!"); }

public static <E> java.util.Queue<E> checkedQueue(java.util.Queue<E> queue, java.lang.Class<E> type) { throw new RuntimeException("Stub!"); }

public static <E> java.util.Set<E> checkedSet(java.util.Set<E> s, java.lang.Class<E> type) { throw new RuntimeException("Stub!"); }

public static <E> java.util.SortedSet<E> checkedSortedSet(java.util.SortedSet<E> s, java.lang.Class<E> type) { throw new RuntimeException("Stub!"); }

public static <E> java.util.NavigableSet<E> checkedNavigableSet(java.util.NavigableSet<E> s, java.lang.Class<E> type) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> checkedList(java.util.List<E> list, java.lang.Class<E> type) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> checkedMap(java.util.Map<K,V> m, java.lang.Class<K> keyType, java.lang.Class<V> valueType) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.SortedMap<K,V> checkedSortedMap(java.util.SortedMap<K,V> m, java.lang.Class<K> keyType, java.lang.Class<V> valueType) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.NavigableMap<K,V> checkedNavigableMap(java.util.NavigableMap<K,V> m, java.lang.Class<K> keyType, java.lang.Class<V> valueType) { throw new RuntimeException("Stub!"); }

public static <T> java.util.Iterator<T> emptyIterator() { throw new RuntimeException("Stub!"); }

public static <T> java.util.ListIterator<T> emptyListIterator() { throw new RuntimeException("Stub!"); }

public static <T> java.util.Enumeration<T> emptyEnumeration() { throw new RuntimeException("Stub!"); }

public static final <T> java.util.Set<T> emptySet() { throw new RuntimeException("Stub!"); }

public static <E> java.util.SortedSet<E> emptySortedSet() { throw new RuntimeException("Stub!"); }

public static <E> java.util.NavigableSet<E> emptyNavigableSet() { throw new RuntimeException("Stub!"); }

public static final <T> java.util.List<T> emptyList() { throw new RuntimeException("Stub!"); }

public static final <K, V> java.util.Map<K,V> emptyMap() { throw new RuntimeException("Stub!"); }

public static final <K, V> java.util.SortedMap<K,V> emptySortedMap() { throw new RuntimeException("Stub!"); }

public static final <K, V> java.util.NavigableMap<K,V> emptyNavigableMap() { throw new RuntimeException("Stub!"); }

public static <T> java.util.Set<T> singleton(T o) { throw new RuntimeException("Stub!"); }

public static <T> java.util.List<T> singletonList(T o) { throw new RuntimeException("Stub!"); }

public static <K, V> java.util.Map<K,V> singletonMap(K key, V value) { throw new RuntimeException("Stub!"); }

public static <T> java.util.List<T> nCopies(int n, T o) { throw new RuntimeException("Stub!"); }

public static <T> java.util.Comparator<T> reverseOrder() { throw new RuntimeException("Stub!"); }

public static <T> java.util.Comparator<T> reverseOrder(java.util.Comparator<T> cmp) { throw new RuntimeException("Stub!"); }

public static <T> java.util.Enumeration<T> enumeration(java.util.Collection<T> c) { throw new RuntimeException("Stub!"); }

public static <T> java.util.ArrayList<T> list(java.util.Enumeration<T> e) { throw new RuntimeException("Stub!"); }

public static int frequency(java.util.Collection<?> c, java.lang.Object o) { throw new RuntimeException("Stub!"); }

public static boolean disjoint(java.util.Collection<?> c1, java.util.Collection<?> c2) { throw new RuntimeException("Stub!"); }

@java.lang.SafeVarargs
public static <T> boolean addAll(java.util.Collection<? super T> c, T... elements) { throw new RuntimeException("Stub!"); }

public static <E> java.util.Set<E> newSetFromMap(java.util.Map<E,java.lang.Boolean> map) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static <E> java.util.SequencedSet<E> newSequencedSetFromMap(java.util.SequencedMap<E,java.lang.Boolean> map) { throw new RuntimeException("Stub!"); }

public static <T> java.util.Queue<T> asLifoQueue(java.util.Deque<T> deque) { throw new RuntimeException("Stub!"); }

public static final java.util.List EMPTY_LIST;
static { EMPTY_LIST = null; }

public static final java.util.Map EMPTY_MAP;
static { EMPTY_MAP = null; }

public static final java.util.Set EMPTY_SET;
static { EMPTY_SET = null; }
}

