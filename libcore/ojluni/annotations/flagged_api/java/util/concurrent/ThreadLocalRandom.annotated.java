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
 *
 * Additional modifications by Guy Steele in 2019 to refactor the code
 * and to implement the {@link RandomGenerator} interface.
 */


package java.util.concurrent;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ThreadLocalRandom extends java.util.Random {

ThreadLocalRandom() { throw new RuntimeException("Stub!"); }

public static java.util.concurrent.ThreadLocalRandom current() { throw new RuntimeException("Stub!"); }

public void setSeed(long seed) { throw new RuntimeException("Stub!"); }

protected int next(int bits) { throw new RuntimeException("Stub!"); }

public boolean nextBoolean() { throw new RuntimeException("Stub!"); }

public int nextInt() { throw new RuntimeException("Stub!"); }

public int nextInt(int bound) { throw new RuntimeException("Stub!"); }

public int nextInt(int origin, int bound) { throw new RuntimeException("Stub!"); }

public long nextLong() { throw new RuntimeException("Stub!"); }

public long nextLong(long bound) { throw new RuntimeException("Stub!"); }

public long nextLong(long origin, long bound) { throw new RuntimeException("Stub!"); }

public float nextFloat() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi("com.android.libcore.v_apis")
public float nextFloat(float bound) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi("com.android.libcore.v_apis")
public float nextFloat(float origin, float bound) { throw new RuntimeException("Stub!"); }

public double nextDouble() { throw new RuntimeException("Stub!"); }

public double nextDouble(double bound) { throw new RuntimeException("Stub!"); }

public double nextDouble(double origin, double bound) { throw new RuntimeException("Stub!"); }

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

