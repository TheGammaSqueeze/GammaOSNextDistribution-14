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
public final class RandomGeneratorFactory<T extends java.util.random.RandomGenerator> {

RandomGeneratorFactory() { throw new RuntimeException("Stub!"); }

public static <T extends java.util.random.RandomGenerator> java.util.random.RandomGeneratorFactory<T> of(java.lang.String name) { throw new RuntimeException("Stub!"); }

public static java.util.random.RandomGeneratorFactory<java.util.random.RandomGenerator> getDefault() { throw new RuntimeException("Stub!"); }

public static java.util.stream.Stream<java.util.random.RandomGeneratorFactory<java.util.random.RandomGenerator>> all() { throw new RuntimeException("Stub!"); }

public java.lang.String name() { throw new RuntimeException("Stub!"); }

public java.lang.String group() { throw new RuntimeException("Stub!"); }

public int stateBits() { throw new RuntimeException("Stub!"); }

public int equidistribution() { throw new RuntimeException("Stub!"); }

public java.math.BigInteger period() { throw new RuntimeException("Stub!"); }

public boolean isStatistical() { throw new RuntimeException("Stub!"); }

public boolean isStochastic() { throw new RuntimeException("Stub!"); }

public boolean isHardware() { throw new RuntimeException("Stub!"); }

public boolean isArbitrarilyJumpable() { throw new RuntimeException("Stub!"); }

public boolean isJumpable() { throw new RuntimeException("Stub!"); }

public boolean isLeapable() { throw new RuntimeException("Stub!"); }

public boolean isSplittable() { throw new RuntimeException("Stub!"); }

public boolean isStreamable() { throw new RuntimeException("Stub!"); }

public boolean isDeprecated() { throw new RuntimeException("Stub!"); }

public T create() { throw new RuntimeException("Stub!"); }

public T create(long seed) { throw new RuntimeException("Stub!"); }

public T create(byte[] seed) { throw new RuntimeException("Stub!"); }
}

