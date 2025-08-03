/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
public interface LongStream extends java.util.stream.BaseStream<java.lang.Long,java.util.stream.LongStream> {

public java.util.stream.LongStream filter(java.util.function.LongPredicate predicate);

public java.util.stream.LongStream map(java.util.function.LongUnaryOperator mapper);

public <U> java.util.stream.Stream<U> mapToObj(java.util.function.LongFunction<? extends U> mapper);

public java.util.stream.IntStream mapToInt(java.util.function.LongToIntFunction mapper);

public java.util.stream.DoubleStream mapToDouble(java.util.function.LongToDoubleFunction mapper);

public java.util.stream.LongStream flatMap(java.util.function.LongFunction<? extends java.util.stream.LongStream> mapper);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.stream.LongStream mapMulti(java.util.stream.LongStream.LongMapMultiConsumer mapper) { throw new RuntimeException("Stub!"); }

public java.util.stream.LongStream distinct();

public java.util.stream.LongStream sorted();

public java.util.stream.LongStream peek(java.util.function.LongConsumer action);

public java.util.stream.LongStream limit(long maxSize);

public java.util.stream.LongStream skip(long n);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.stream.LongStream takeWhile(java.util.function.LongPredicate predicate) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public default java.util.stream.LongStream dropWhile(java.util.function.LongPredicate predicate) { throw new RuntimeException("Stub!"); }

public void forEach(java.util.function.LongConsumer action);

public void forEachOrdered(java.util.function.LongConsumer action);

public long[] toArray();

public long reduce(long identity, java.util.function.LongBinaryOperator op);

public java.util.OptionalLong reduce(java.util.function.LongBinaryOperator op);

public <R> R collect(java.util.function.Supplier<R> supplier, java.util.function.ObjLongConsumer<R> accumulator, java.util.function.BiConsumer<R,R> combiner);

public long sum();

public java.util.OptionalLong min();

public java.util.OptionalLong max();

public long count();

public java.util.OptionalDouble average();

public java.util.LongSummaryStatistics summaryStatistics();

public boolean anyMatch(java.util.function.LongPredicate predicate);

public boolean allMatch(java.util.function.LongPredicate predicate);

public boolean noneMatch(java.util.function.LongPredicate predicate);

public java.util.OptionalLong findFirst();

public java.util.OptionalLong findAny();

public java.util.stream.DoubleStream asDoubleStream();

public java.util.stream.Stream<java.lang.Long> boxed();

public java.util.stream.LongStream sequential();

public java.util.stream.LongStream parallel();

public java.util.PrimitiveIterator.OfLong iterator();

public java.util.Spliterator.OfLong spliterator();

public static java.util.stream.LongStream.Builder builder() { throw new RuntimeException("Stub!"); }

public static java.util.stream.LongStream empty() { throw new RuntimeException("Stub!"); }

public static java.util.stream.LongStream of(long t) { throw new RuntimeException("Stub!"); }

public static java.util.stream.LongStream of(long... values) { throw new RuntimeException("Stub!"); }

public static java.util.stream.LongStream iterate(long seed, java.util.function.LongUnaryOperator f) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static java.util.stream.LongStream iterate(long seed, java.util.function.LongPredicate hasNext, java.util.function.LongUnaryOperator next) { throw new RuntimeException("Stub!"); }

public static java.util.stream.LongStream generate(java.util.function.LongSupplier s) { throw new RuntimeException("Stub!"); }

public static java.util.stream.LongStream range(long startInclusive, long endExclusive) { throw new RuntimeException("Stub!"); }

public static java.util.stream.LongStream rangeClosed(long startInclusive, long endInclusive) { throw new RuntimeException("Stub!"); }

public static java.util.stream.LongStream concat(java.util.stream.LongStream a, java.util.stream.LongStream b) { throw new RuntimeException("Stub!"); }
@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface Builder extends java.util.function.LongConsumer {

public void accept(long t);

public default java.util.stream.LongStream.Builder add(long t) { throw new RuntimeException("Stub!"); }

public java.util.stream.LongStream build();
}

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
@java.lang.FunctionalInterface
public static interface LongMapMultiConsumer {

public void accept(long value, java.util.function.LongConsumer lc);
}

}

