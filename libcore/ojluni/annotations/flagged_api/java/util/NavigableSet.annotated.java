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
 * Written by Doug Lea and Josh Bloch with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

// BEGIN android-note
// removed link to collections framework docs
// END android-note


package java.util;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public interface NavigableSet<E> extends java.util.SortedSet<E> {

public E lower(E e);

public E floor(E e);

public E ceiling(E e);

public E higher(E e);

public E pollFirst();

public E pollLast();

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.util.Iterator<E> iterator();

public java.util.NavigableSet<E> descendingSet();

public java.util.Iterator<E> descendingIterator();

public java.util.NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive);

public java.util.NavigableSet<E> headSet(E toElement, boolean inclusive);

public java.util.NavigableSet<E> tailSet(E fromElement, boolean inclusive);

public java.util.SortedSet<E> subSet(E fromElement, E toElement);

public java.util.SortedSet<E> headSet(E toElement);

public java.util.SortedSet<E> tailSet(E fromElement);

public default E removeFirst() { throw new RuntimeException("Stub!"); }

public default E removeLast() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.NavigableSet<E> reversed() { throw new RuntimeException("Stub!"); }
}

