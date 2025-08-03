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
public class ConcurrentSkipListSet<E> extends java.util.AbstractSet<E> implements java.util.NavigableSet<E>, java.lang.Cloneable, java.io.Serializable {

public ConcurrentSkipListSet() { throw new RuntimeException("Stub!"); }

public ConcurrentSkipListSet(java.util.Comparator<? super E> comparator) { throw new RuntimeException("Stub!"); }

public ConcurrentSkipListSet(java.util.Collection<? extends E> c) { throw new RuntimeException("Stub!"); }

public ConcurrentSkipListSet(java.util.SortedSet<E> s) { throw new RuntimeException("Stub!"); }

public java.util.concurrent.ConcurrentSkipListSet<E> clone() { throw new RuntimeException("Stub!"); }

public int size() { throw new RuntimeException("Stub!"); }

public boolean isEmpty() { throw new RuntimeException("Stub!"); }

public boolean contains(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public boolean add(E e) { throw new RuntimeException("Stub!"); }

public boolean remove(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public void clear() { throw new RuntimeException("Stub!"); }

public java.util.Iterator<E> iterator() { throw new RuntimeException("Stub!"); }

public java.util.Iterator<E> descendingIterator() { throw new RuntimeException("Stub!"); }

public boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public boolean removeAll(java.util.Collection<?> c) { throw new RuntimeException("Stub!"); }

public E lower(E e) { throw new RuntimeException("Stub!"); }

public E floor(E e) { throw new RuntimeException("Stub!"); }

public E ceiling(E e) { throw new RuntimeException("Stub!"); }

public E higher(E e) { throw new RuntimeException("Stub!"); }

public E pollFirst() { throw new RuntimeException("Stub!"); }

public E pollLast() { throw new RuntimeException("Stub!"); }

public java.util.Comparator<? super E> comparator() { throw new RuntimeException("Stub!"); }

public E first() { throw new RuntimeException("Stub!"); }

public E last() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public void addFirst(E e) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public void addLast(E e) { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<E> headSet(E toElement, boolean inclusive) { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<E> tailSet(E fromElement, boolean inclusive) { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<E> subSet(E fromElement, E toElement) { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<E> headSet(E toElement) { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<E> tailSet(E fromElement) { throw new RuntimeException("Stub!"); }

public java.util.NavigableSet<E> descendingSet() { throw new RuntimeException("Stub!"); }

public java.util.Spliterator<E> spliterator() { throw new RuntimeException("Stub!"); }
}

