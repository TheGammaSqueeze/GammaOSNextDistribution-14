/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates. All rights reserved.
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


package java.nio;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class MappedByteBuffer extends java.nio.ByteBuffer {

MappedByteBuffer(int mark, int pos, int lim, int cap) { throw new RuntimeException("Stub!"); }

public final boolean isLoaded() { throw new RuntimeException("Stub!"); }

public final java.nio.MappedByteBuffer load() { throw new RuntimeException("Stub!"); }

public final java.nio.MappedByteBuffer force() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public final java.nio.MappedByteBuffer force(int index, int length) { throw new RuntimeException("Stub!"); }

public final java.nio.Buffer position(int newPosition) { throw new RuntimeException("Stub!"); }

public final java.nio.Buffer limit(int newLimit) { throw new RuntimeException("Stub!"); }

public final java.nio.Buffer mark() { throw new RuntimeException("Stub!"); }

public final java.nio.Buffer reset() { throw new RuntimeException("Stub!"); }

public final java.nio.Buffer clear() { throw new RuntimeException("Stub!"); }

public final java.nio.Buffer flip() { throw new RuntimeException("Stub!"); }

public final java.nio.Buffer rewind() { throw new RuntimeException("Stub!"); }

public abstract java.nio.ByteBuffer slice();

public abstract java.nio.MappedByteBuffer slice(int index, int length);

public abstract java.nio.ByteBuffer duplicate();

public abstract java.nio.ByteBuffer compact();
}

