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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group.  Adapted and released, under explicit permission,
 * from JDK ArrayList.java which carries the following copyright:
 *
 * Copyright 1997 by Sun Microsystems, Inc.,
 * 901 San Antonio Road, Palo Alto, California, 94303, U.S.A.
 * All rights reserved.
 */


package java.util.concurrent;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class CopyOnWriteArrayList<E> implements java.util.List<E>, java.util.RandomAccess, java.lang.Cloneable, java.io.Serializable {

public CopyOnWriteArrayList() { throw new RuntimeException("Stub!"); }

public CopyOnWriteArrayList(java.util.Collection<? extends E> c) { throw new RuntimeException("Stub!"); }

public CopyOnWriteArrayList(E[] toCopyIn) { throw new RuntimeException("Stub!"); }

public int size() { throw new RuntimeException("Stub!"); }

public boolean isEmpty() { throw new RuntimeException("Stub!"); }

public boolean contains(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public int indexOf(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public int indexOf(E e, int index) { throw new RuntimeException("Stub!"); }

public int lastIndexOf(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public int lastIndexOf(E e, int index) { throw new RuntimeException("Stub!"); }

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

public java.lang.Object[] toArray() { throw new RuntimeException("Stub!"); }

public <T> T[] toArray(T[] a) { throw new RuntimeException("Stub!"); }

public E get(int index) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public E getFirst() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public E getLast() { throw new RuntimeException("Stub!"); }

public E set(int index, E element) { throw new RuntimeException("Stub!"); }

public boolean add(E e) { throw new RuntimeException("Stub!"); }

public void add(int index, E element) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public void addFirst(E e) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public void addLast(E e) { throw new RuntimeException("Stub!"); }

public E remove(int index) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public E removeFirst() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public E removeLast() { throw new RuntimeException("Stub!"); }

public boolean remove(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public boolean addIfAbsent(E e) { throw new RuntimeException("Stub!"); }

public boolean containsAll(java.util.Collection<?> c) { throw new RuntimeException("Stub!"); }

public boolean removeAll(java.util.Collection<?> c) { throw new RuntimeException("Stub!"); }

public boolean retainAll(java.util.Collection<?> c) { throw new RuntimeException("Stub!"); }

public int addAllAbsent(java.util.Collection<? extends E> c) { throw new RuntimeException("Stub!"); }

public void clear() { throw new RuntimeException("Stub!"); }

public boolean addAll(java.util.Collection<? extends E> c) { throw new RuntimeException("Stub!"); }

public boolean addAll(int index, java.util.Collection<? extends E> c) { throw new RuntimeException("Stub!"); }

public void forEach(java.util.function.Consumer<? super E> action) { throw new RuntimeException("Stub!"); }

public boolean removeIf(java.util.function.Predicate<? super E> filter) { throw new RuntimeException("Stub!"); }

public void replaceAll(java.util.function.UnaryOperator<E> operator) { throw new RuntimeException("Stub!"); }

public void sort(java.util.Comparator<? super E> c) { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

public boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public int hashCode() { throw new RuntimeException("Stub!"); }

public java.util.Iterator<E> iterator() { throw new RuntimeException("Stub!"); }

public java.util.ListIterator<E> listIterator() { throw new RuntimeException("Stub!"); }

public java.util.ListIterator<E> listIterator(int index) { throw new RuntimeException("Stub!"); }

public java.util.Spliterator<E> spliterator() { throw new RuntimeException("Stub!"); }

public java.util.List<E> subList(int fromIndex, int toIndex) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.util.List<E> reversed() { throw new RuntimeException("Stub!"); }
}

