/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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


package java.util.random;

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
public interface RandomGenerator {

public static java.util.random.RandomGenerator of(java.lang.String name) { throw new RuntimeException("Stub!"); }

public static java.util.random.RandomGenerator getDefault() { throw new RuntimeException("Stub!"); }

public default boolean isDeprecated() { throw new RuntimeException("Stub!"); }

public default java.util.stream.DoubleStream doubles() { throw new RuntimeException("Stub!"); }

public default java.util.stream.DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) { throw new RuntimeException("Stub!"); }

public default java.util.stream.DoubleStream doubles(long streamSize) { throw new RuntimeException("Stub!"); }

public default java.util.stream.DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) { throw new RuntimeException("Stub!"); }

public default java.util.stream.IntStream ints() { throw new RuntimeException("Stub!"); }

public default java.util.stream.IntStream ints(int randomNumberOrigin, int randomNumberBound) { throw new RuntimeException("Stub!"); }

public default java.util.stream.IntStream ints(long streamSize) { throw new RuntimeException("Stub!"); }

public default java.util.stream.IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) { throw new RuntimeException("Stub!"); }

public default java.util.stream.LongStream longs() { throw new RuntimeException("Stub!"); }

public default java.util.stream.LongStream longs(long randomNumberOrigin, long randomNumberBound) { throw new RuntimeException("Stub!"); }

public default java.util.stream.LongStream longs(long streamSize) { throw new RuntimeException("Stub!"); }

public default java.util.stream.LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) { throw new RuntimeException("Stub!"); }

public default boolean nextBoolean() { throw new RuntimeException("Stub!"); }

public default void nextBytes(byte[] bytes) { throw new RuntimeException("Stub!"); }

public default float nextFloat() { throw new RuntimeException("Stub!"); }

public default float nextFloat(float bound) { throw new RuntimeException("Stub!"); }

public default float nextFloat(float origin, float bound) { throw new RuntimeException("Stub!"); }

public default double nextDouble() { throw new RuntimeException("Stub!"); }

public default double nextDouble(double bound) { throw new RuntimeException("Stub!"); }

public default double nextDouble(double origin, double bound) { throw new RuntimeException("Stub!"); }

public default int nextInt() { throw new RuntimeException("Stub!"); }

public default int nextInt(int bound) { throw new RuntimeException("Stub!"); }

public default int nextInt(int origin, int bound) { throw new RuntimeException("Stub!"); }

public long nextLong();

public default long nextLong(long bound) { throw new RuntimeException("Stub!"); }

public default long nextLong(long origin, long bound) { throw new RuntimeException("Stub!"); }

public default double nextGaussian() { throw new RuntimeException("Stub!"); }

public default double nextGaussian(double mean, double stddev) { throw new RuntimeException("Stub!"); }

public default double nextExponential() { throw new RuntimeException("Stub!"); }
@SuppressWarnings({"unchecked", "deprecation", "all"})
@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static interface ArbitrarilyJumpableGenerator extends java.util.random.RandomGenerator.LeapableGenerator {

public static java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator of(java.lang.String name) { throw new RuntimeException("Stub!"); }

public java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator copy();

public void jumpPowerOfTwo(int logDistance);

public void jump(double distance);

public default void jump() { throw new RuntimeException("Stub!"); }

public default java.util.stream.Stream<java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator> jumps(double distance) { throw new RuntimeException("Stub!"); }

public default java.util.stream.Stream<java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator> jumps(long streamSize, double distance) { throw new RuntimeException("Stub!"); }

public default void leap() { throw new RuntimeException("Stub!"); }

public default java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator copyAndJump(double distance) { throw new RuntimeException("Stub!"); }
}

@SuppressWarnings({"unchecked", "deprecation", "all"})
@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static interface JumpableGenerator extends java.util.random.RandomGenerator.StreamableGenerator {

public static java.util.random.RandomGenerator.JumpableGenerator of(java.lang.String name) { throw new RuntimeException("Stub!"); }

public java.util.random.RandomGenerator.JumpableGenerator copy();

public void jump();

public double jumpDistance();

public default java.util.stream.Stream<java.util.random.RandomGenerator> jumps() { throw new RuntimeException("Stub!"); }

public default java.util.stream.Stream<java.util.random.RandomGenerator> jumps(long streamSize) { throw new RuntimeException("Stub!"); }

public default java.util.stream.Stream<java.util.random.RandomGenerator> rngs() { throw new RuntimeException("Stub!"); }

public default java.util.stream.Stream<java.util.random.RandomGenerator> rngs(long streamSize) { throw new RuntimeException("Stub!"); }

public default java.util.random.RandomGenerator copyAndJump() { throw new RuntimeException("Stub!"); }
}

@SuppressWarnings({"unchecked", "deprecation", "all"})
@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static interface LeapableGenerator extends java.util.random.RandomGenerator.JumpableGenerator {

public static java.util.random.RandomGenerator.LeapableGenerator of(java.lang.String name) { throw new RuntimeException("Stub!"); }

public java.util.random.RandomGenerator.LeapableGenerator copy();

public void leap();

public double leapDistance();

public default java.util.stream.Stream<java.util.random.RandomGenerator.JumpableGenerator> leaps() { throw new RuntimeException("Stub!"); }

public default java.util.stream.Stream<java.util.random.RandomGenerator.JumpableGenerator> leaps(long streamSize) { throw new RuntimeException("Stub!"); }

public default java.util.random.RandomGenerator.JumpableGenerator copyAndLeap() { throw new RuntimeException("Stub!"); }
}

@SuppressWarnings({"unchecked", "deprecation", "all"})
@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static interface SplittableGenerator extends java.util.random.RandomGenerator.StreamableGenerator {

public static java.util.random.RandomGenerator.SplittableGenerator of(java.lang.String name) { throw new RuntimeException("Stub!"); }

public java.util.random.RandomGenerator.SplittableGenerator split();

public java.util.random.RandomGenerator.SplittableGenerator split(java.util.random.RandomGenerator.SplittableGenerator source);

public default java.util.stream.Stream<java.util.random.RandomGenerator.SplittableGenerator> splits() { throw new RuntimeException("Stub!"); }

public java.util.stream.Stream<java.util.random.RandomGenerator.SplittableGenerator> splits(long streamSize);

public java.util.stream.Stream<java.util.random.RandomGenerator.SplittableGenerator> splits(java.util.random.RandomGenerator.SplittableGenerator source);

public java.util.stream.Stream<java.util.random.RandomGenerator.SplittableGenerator> splits(long streamSize, java.util.random.RandomGenerator.SplittableGenerator source);

public default java.util.stream.Stream<java.util.random.RandomGenerator> rngs() { throw new RuntimeException("Stub!"); }

public default java.util.stream.Stream<java.util.random.RandomGenerator> rngs(long streamSize) { throw new RuntimeException("Stub!"); }
}

@SuppressWarnings({"unchecked", "deprecation", "all"})
@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static interface StreamableGenerator extends java.util.random.RandomGenerator {

public static java.util.random.RandomGenerator.StreamableGenerator of(java.lang.String name) { throw new RuntimeException("Stub!"); }

public java.util.stream.Stream<java.util.random.RandomGenerator> rngs();

public default java.util.stream.Stream<java.util.random.RandomGenerator> rngs(long streamSize) { throw new RuntimeException("Stub!"); }
}

}

