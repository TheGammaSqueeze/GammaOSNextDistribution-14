/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
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


package java.util.zip;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class Inflater {

public Inflater(boolean nowrap) { throw new RuntimeException("Stub!"); }

public Inflater() { throw new RuntimeException("Stub!"); }

public void setInput(byte[] input, int off, int len) { throw new RuntimeException("Stub!"); }

public void setInput(byte[] input) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi("com.android.libcore.v_apis")
public void setInput(java.nio.ByteBuffer input) { throw new RuntimeException("Stub!"); }

public void setDictionary(byte[] dictionary, int off, int len) { throw new RuntimeException("Stub!"); }

public void setDictionary(byte[] dictionary) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi("com.android.libcore.v_apis")
public void setDictionary(java.nio.ByteBuffer dictionary) { throw new RuntimeException("Stub!"); }

public int getRemaining() { throw new RuntimeException("Stub!"); }

public boolean needsInput() { throw new RuntimeException("Stub!"); }

public boolean needsDictionary() { throw new RuntimeException("Stub!"); }

public boolean finished() { throw new RuntimeException("Stub!"); }

public int inflate(byte[] output, int off, int len) throws java.util.zip.DataFormatException { throw new RuntimeException("Stub!"); }

public int inflate(byte[] output) throws java.util.zip.DataFormatException { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi("com.android.libcore.v_apis")
public int inflate(java.nio.ByteBuffer output) throws java.util.zip.DataFormatException { throw new RuntimeException("Stub!"); }

public int getAdler() { throw new RuntimeException("Stub!"); }

public int getTotalIn() { throw new RuntimeException("Stub!"); }

public long getBytesRead() { throw new RuntimeException("Stub!"); }

public int getTotalOut() { throw new RuntimeException("Stub!"); }

public long getBytesWritten() { throw new RuntimeException("Stub!"); }

public void reset() { throw new RuntimeException("Stub!"); }

public void end() { throw new RuntimeException("Stub!"); }
}

