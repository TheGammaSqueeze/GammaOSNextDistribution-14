/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
public interface SortedSet<E> extends java.util.Set<E>, java.util.SequencedSet<E> {

public java.util.Comparator<? super E> comparator();

public java.util.SortedSet<E> subSet(E fromElement, E toElement);

public java.util.SortedSet<E> headSet(E toElement);

public java.util.SortedSet<E> tailSet(E fromElement);

public E first();

public E last();

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.Spliterator<E> spliterator() { throw new RuntimeException("Stub!"); }

public default void addFirst(E e) { throw new RuntimeException("Stub!"); }

public default void addLast(E e) { throw new RuntimeException("Stub!"); }

public default E getFirst() { throw new RuntimeException("Stub!"); }

public default E getLast() { throw new RuntimeException("Stub!"); }

public default E removeFirst() { throw new RuntimeException("Stub!"); }

public default E removeLast() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.SortedSet<E> reversed() { throw new RuntimeException("Stub!"); }
}

