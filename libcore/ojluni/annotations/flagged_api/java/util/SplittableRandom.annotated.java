/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
public final class SplittableRandom implements java.util.random.RandomGenerator, java.util.random.RandomGenerator.SplittableGenerator {

public SplittableRandom(long seed) { throw new RuntimeException("Stub!"); }

public SplittableRandom() { throw new RuntimeException("Stub!"); }

public java.util.SplittableRandom split() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.util.SplittableRandom split(java.util.random.RandomGenerator.SplittableGenerator source) { throw new RuntimeException("Stub!"); }

public int nextInt() { throw new RuntimeException("Stub!"); }

public long nextLong() { throw new RuntimeException("Stub!"); }

public void nextBytes(byte[] bytes) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.util.stream.Stream<java.util.random.RandomGenerator.SplittableGenerator> splits() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.util.stream.Stream<java.util.random.RandomGenerator.SplittableGenerator> splits(long streamSize) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.util.stream.Stream<java.util.random.RandomGenerator.SplittableGenerator> splits(java.util.random.RandomGenerator.SplittableGenerator source) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.util.stream.Stream<java.util.random.RandomGenerator.SplittableGenerator> splits(long streamSize, java.util.random.RandomGenerator.SplittableGenerator source) { throw new RuntimeException("Stub!"); }

public java.util.stream.IntStream ints(long streamSize) { throw new RuntimeException("Stub!"); }

public java.util.stream.IntStream ints() { throw new RuntimeException("Stub!"); }

public java.util.stream.IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) { throw new RuntimeException("Stub!"); }

public java.util.stream.IntStream ints(int randomNumberOrigin, int randomNumberBound) { throw new RuntimeException("Stub!"); }

public java.util.stream.LongStream longs(long streamSize) { throw new RuntimeException("Stub!"); }

public java.util.stream.LongStream longs() { throw new RuntimeException("Stub!"); }

public java.util.stream.LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) { throw new RuntimeException("Stub!"); }

public java.util.stream.LongStream longs(long randomNumberOrigin, long randomNumberBound) { throw new RuntimeException("Stub!"); }

public java.util.stream.DoubleStream doubles(long streamSize) { throw new RuntimeException("Stub!"); }

public java.util.stream.DoubleStream doubles() { throw new RuntimeException("Stub!"); }

public java.util.stream.DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) { throw new RuntimeException("Stub!"); }

public java.util.stream.DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) { throw new RuntimeException("Stub!"); }
}

