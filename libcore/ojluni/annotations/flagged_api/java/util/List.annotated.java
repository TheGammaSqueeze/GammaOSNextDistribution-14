/*
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
public interface List<E> extends java.util.SequencedCollection<E>, java.util.Collection<E> {

public int size();

public boolean isEmpty();

public boolean contains(java.lang.Object o);

public java.util.Iterator<E> iterator();

public java.lang.Object[] toArray();

public <T> T[] toArray(T[] a);

public boolean add(E e);

public boolean remove(java.lang.Object o);

public boolean containsAll(java.util.Collection<?> c);

public boolean addAll(java.util.Collection<? extends E> c);

public boolean addAll(int index, java.util.Collection<? extends E> c);

public boolean removeAll(java.util.Collection<?> c);

public boolean retainAll(java.util.Collection<?> c);

public default void replaceAll(java.util.function.UnaryOperator<E> operator) { throw new RuntimeException("Stub!"); }

public default void sort(java.util.Comparator<? super E> c) { throw new RuntimeException("Stub!"); }

public void clear();

public boolean equals(java.lang.Object o);

public int hashCode();

public E get(int index);

public E set(int index, E element);

public void add(int index, E element);

public E remove(int index);

public int indexOf(java.lang.Object o);

public int lastIndexOf(java.lang.Object o);

public java.util.ListIterator<E> listIterator();

public java.util.ListIterator<E> listIterator(int index);

public java.util.List<E> subList(int fromIndex, int toIndex);

public default java.util.Spliterator<E> spliterator() { throw new RuntimeException("Stub!"); }

public default void addFirst(E e) { throw new RuntimeException("Stub!"); }

public default void addLast(E e) { throw new RuntimeException("Stub!"); }

public default E getFirst() { throw new RuntimeException("Stub!"); }

public default E getLast() { throw new RuntimeException("Stub!"); }

public default E removeFirst() { throw new RuntimeException("Stub!"); }

public default E removeLast() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.List<E> reversed() { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of() { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2, E e3) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2, E e3, E e4) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2, E e3, E e4, E e5) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2, E e3, E e4, E e5, E e6) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) { throw new RuntimeException("Stub!"); }

@java.lang.SafeVarargs
public static <E> java.util.List<E> of(E... elements) { throw new RuntimeException("Stub!"); }

public static <E> java.util.List<E> copyOf(java.util.Collection<? extends E> coll) { throw new RuntimeException("Stub!"); }
}

