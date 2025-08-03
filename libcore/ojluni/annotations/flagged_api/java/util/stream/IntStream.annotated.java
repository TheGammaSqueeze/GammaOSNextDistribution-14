/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.util.stream;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public interface IntStream extends java.util.stream.BaseStream<java.lang.Integer,java.util.stream.IntStream> {

public java.util.stream.IntStream filter(java.util.function.IntPredicate predicate);

public java.util.stream.IntStream map(java.util.function.IntUnaryOperator mapper);

public <U> java.util.stream.Stream<U> mapToObj(java.util.function.IntFunction<? extends U> mapper);

public java.util.stream.LongStream mapToLong(java.util.function.IntToLongFunction mapper);

public java.util.stream.DoubleStream mapToDouble(java.util.function.IntToDoubleFunction mapper);

public java.util.stream.IntStream flatMap(java.util.function.IntFunction<? extends java.util.stream.IntStream> mapper);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.stream.IntStream mapMulti(java.util.stream.IntStream.IntMapMultiConsumer mapper) { throw new RuntimeException("Stub!"); }

public java.util.stream.IntStream distinct();

public java.util.stream.IntStream sorted();

public java.util.stream.IntStream peek(java.util.function.IntConsumer action);

public java.util.stream.IntStream limit(long maxSize);

public java.util.stream.IntStream skip(long n);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.stream.IntStream takeWhile(java.util.function.IntPredicate predicate) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.stream.IntStream dropWhile(java.util.function.IntPredicate predicate) { throw new RuntimeException("Stub!"); }

public void forEach(java.util.function.IntConsumer action);

public void forEachOrdered(java.util.function.IntConsumer action);

public int[] toArray();

public int reduce(int identity, java.util.function.IntBinaryOperator op);

public java.util.OptionalInt reduce(java.util.function.IntBinaryOperator op);

public <R> R collect(java.util.function.Supplier<R> supplier, java.util.function.ObjIntConsumer<R> accumulator, java.util.function.BiConsumer<R,R> combiner);

public int sum();

public java.util.OptionalInt min();

public java.util.OptionalInt max();

public long count();

public java.util.OptionalDouble average();

public java.util.IntSummaryStatistics summaryStatistics();

public boolean anyMatch(java.util.function.IntPredicate predicate);

public boolean allMatch(java.util.function.IntPredicate predicate);

public boolean noneMatch(java.util.function.IntPredicate predicate);

public java.util.OptionalInt findFirst();

public java.util.OptionalInt findAny();

public java.util.stream.LongStream asLongStream();

public java.util.stream.DoubleStream asDoubleStream();

public java.util.stream.Stream<java.lang.Integer> boxed();

public java.util.stream.IntStream sequential();

public java.util.stream.IntStream parallel();

public java.util.PrimitiveIterator.OfInt iterator();

public java.util.Spliterator.OfInt spliterator();

public static java.util.stream.IntStream.Builder builder() { throw new RuntimeException("Stub!"); }

public static java.util.stream.IntStream empty() { throw new RuntimeException("Stub!"); }

public static java.util.stream.IntStream of(int t) { throw new RuntimeException("Stub!"); }

public static java.util.stream.IntStream of(int... values) { throw new RuntimeException("Stub!"); }

public static java.util.stream.IntStream iterate(int seed, java.util.function.IntUnaryOperator f) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static java.util.stream.IntStream iterate(int seed, java.util.function.IntPredicate hasNext, java.util.function.IntUnaryOperator next) { throw new RuntimeException("Stub!"); }

public static java.util.stream.IntStream generate(java.util.function.IntSupplier s) { throw new RuntimeException("Stub!"); }

public static java.util.stream.IntStream range(int startInclusive, int endExclusive) { throw new RuntimeException("Stub!"); }

public static java.util.stream.IntStream rangeClosed(int startInclusive, int endInclusive) { throw new RuntimeException("Stub!"); }

public static java.util.stream.IntStream concat(java.util.stream.IntStream a, java.util.stream.IntStream b) { throw new RuntimeException("Stub!"); }
@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface Builder extends java.util.function.IntConsumer {

public void accept(int t);

public default java.util.stream.IntStream.Builder add(int t) { throw new RuntimeException("Stub!"); }

public java.util.stream.IntStream build();
}

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
@java.lang.FunctionalInterface
public static interface IntMapMultiConsumer {

public void accept(int value, java.util.function.IntConsumer ic);
}

}

