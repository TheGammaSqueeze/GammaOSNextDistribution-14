/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
public final class Collectors {

Collectors() { throw new RuntimeException("Stub!"); }

public static <T, C extends java.util.Collection<T>> java.util.stream.Collector<T,?,C> toCollection(java.util.function.Supplier<C> collectionFactory) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.List<T>> toList() { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.List<T>> toUnmodifiableList() { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.Set<T>> toSet() { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.Set<T>> toUnmodifiableSet() { throw new RuntimeException("Stub!"); }

public static java.util.stream.Collector<java.lang.CharSequence,?,java.lang.String> joining() { throw new RuntimeException("Stub!"); }

public static java.util.stream.Collector<java.lang.CharSequence,?,java.lang.String> joining(java.lang.CharSequence delimiter) { throw new RuntimeException("Stub!"); }

public static java.util.stream.Collector<java.lang.CharSequence,?,java.lang.String> joining(java.lang.CharSequence delimiter, java.lang.CharSequence prefix, java.lang.CharSequence suffix) { throw new RuntimeException("Stub!"); }

public static <T, U, A, R> java.util.stream.Collector<T,?,R> mapping(java.util.function.Function<? super T,? extends U> mapper, java.util.stream.Collector<? super U,A,R> downstream) { throw new RuntimeException("Stub!"); }

public static <T, U, A, R> java.util.stream.Collector<T,?,R> flatMapping(java.util.function.Function<? super T,? extends java.util.stream.Stream<? extends U>> mapper, java.util.stream.Collector<? super U,A,R> downstream) { throw new RuntimeException("Stub!"); }

public static <T, A, R> java.util.stream.Collector<T,?,R> filtering(java.util.function.Predicate<? super T> predicate, java.util.stream.Collector<? super T,A,R> downstream) { throw new RuntimeException("Stub!"); }

public static <T, A, R, RR> java.util.stream.Collector<T,A,RR> collectingAndThen(java.util.stream.Collector<T,A,R> downstream, java.util.function.Function<R,RR> finisher) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.lang.Long> counting() { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.Optional<T>> minBy(java.util.Comparator<? super T> comparator) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.Optional<T>> maxBy(java.util.Comparator<? super T> comparator) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.lang.Integer> summingInt(java.util.function.ToIntFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.lang.Long> summingLong(java.util.function.ToLongFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.lang.Double> summingDouble(java.util.function.ToDoubleFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.lang.Double> averagingInt(java.util.function.ToIntFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.lang.Double> averagingLong(java.util.function.ToLongFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.lang.Double> averagingDouble(java.util.function.ToDoubleFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,T> reducing(T identity, java.util.function.BinaryOperator<T> op) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.Optional<T>> reducing(java.util.function.BinaryOperator<T> op) { throw new RuntimeException("Stub!"); }

public static <T, U> java.util.stream.Collector<T,?,U> reducing(U identity, java.util.function.Function<? super T,? extends U> mapper, java.util.function.BinaryOperator<U> op) { throw new RuntimeException("Stub!"); }

public static <T, K> java.util.stream.Collector<T,?,java.util.Map<K,java.util.List<T>>> groupingBy(java.util.function.Function<? super T,? extends K> classifier) { throw new RuntimeException("Stub!"); }

public static <T, K, A, D> java.util.stream.Collector<T,?,java.util.Map<K,D>> groupingBy(java.util.function.Function<? super T,? extends K> classifier, java.util.stream.Collector<? super T,A,D> downstream) { throw new RuntimeException("Stub!"); }

public static <T, K, D, A, M extends java.util.Map<K, D>> java.util.stream.Collector<T,?,M> groupingBy(java.util.function.Function<? super T,? extends K> classifier, java.util.function.Supplier<M> mapFactory, java.util.stream.Collector<? super T,A,D> downstream) { throw new RuntimeException("Stub!"); }

public static <T, K> java.util.stream.Collector<T,?,java.util.concurrent.ConcurrentMap<K,java.util.List<T>>> groupingByConcurrent(java.util.function.Function<? super T,? extends K> classifier) { throw new RuntimeException("Stub!"); }

public static <T, K, A, D> java.util.stream.Collector<T,?,java.util.concurrent.ConcurrentMap<K,D>> groupingByConcurrent(java.util.function.Function<? super T,? extends K> classifier, java.util.stream.Collector<? super T,A,D> downstream) { throw new RuntimeException("Stub!"); }

public static <T, K, A, D, M extends java.util.concurrent.ConcurrentMap<K, D>> java.util.stream.Collector<T,?,M> groupingByConcurrent(java.util.function.Function<? super T,? extends K> classifier, java.util.function.Supplier<M> mapFactory, java.util.stream.Collector<? super T,A,D> downstream) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.Map<java.lang.Boolean,java.util.List<T>>> partitioningBy(java.util.function.Predicate<? super T> predicate) { throw new RuntimeException("Stub!"); }

public static <T, D, A> java.util.stream.Collector<T,?,java.util.Map<java.lang.Boolean,D>> partitioningBy(java.util.function.Predicate<? super T> predicate, java.util.stream.Collector<? super T,A,D> downstream) { throw new RuntimeException("Stub!"); }

public static <T, K, U> java.util.stream.Collector<T,?,java.util.Map<K,U>> toMap(java.util.function.Function<? super T,? extends K> keyMapper, java.util.function.Function<? super T,? extends U> valueMapper) { throw new RuntimeException("Stub!"); }

public static <T, K, U> java.util.stream.Collector<T,?,java.util.Map<K,U>> toUnmodifiableMap(java.util.function.Function<? super T,? extends K> keyMapper, java.util.function.Function<? super T,? extends U> valueMapper) { throw new RuntimeException("Stub!"); }

public static <T, K, U> java.util.stream.Collector<T,?,java.util.Map<K,U>> toMap(java.util.function.Function<? super T,? extends K> keyMapper, java.util.function.Function<? super T,? extends U> valueMapper, java.util.function.BinaryOperator<U> mergeFunction) { throw new RuntimeException("Stub!"); }

public static <T, K, U> java.util.stream.Collector<T,?,java.util.Map<K,U>> toUnmodifiableMap(java.util.function.Function<? super T,? extends K> keyMapper, java.util.function.Function<? super T,? extends U> valueMapper, java.util.function.BinaryOperator<U> mergeFunction) { throw new RuntimeException("Stub!"); }

public static <T, K, U, M extends java.util.Map<K, U>> java.util.stream.Collector<T,?,M> toMap(java.util.function.Function<? super T,? extends K> keyMapper, java.util.function.Function<? super T,? extends U> valueMapper, java.util.function.BinaryOperator<U> mergeFunction, java.util.function.Supplier<M> mapFactory) { throw new RuntimeException("Stub!"); }

public static <T, K, U> java.util.stream.Collector<T,?,java.util.concurrent.ConcurrentMap<K,U>> toConcurrentMap(java.util.function.Function<? super T,? extends K> keyMapper, java.util.function.Function<? super T,? extends U> valueMapper) { throw new RuntimeException("Stub!"); }

public static <T, K, U> java.util.stream.Collector<T,?,java.util.concurrent.ConcurrentMap<K,U>> toConcurrentMap(java.util.function.Function<? super T,? extends K> keyMapper, java.util.function.Function<? super T,? extends U> valueMapper, java.util.function.BinaryOperator<U> mergeFunction) { throw new RuntimeException("Stub!"); }

public static <T, K, U, M extends java.util.concurrent.ConcurrentMap<K, U>> java.util.stream.Collector<T,?,M> toConcurrentMap(java.util.function.Function<? super T,? extends K> keyMapper, java.util.function.Function<? super T,? extends U> valueMapper, java.util.function.BinaryOperator<U> mergeFunction, java.util.function.Supplier<M> mapFactory) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.IntSummaryStatistics> summarizingInt(java.util.function.ToIntFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.LongSummaryStatistics> summarizingLong(java.util.function.ToLongFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

public static <T> java.util.stream.Collector<T,?,java.util.DoubleSummaryStatistics> summarizingDouble(java.util.function.ToDoubleFunction<? super T> mapper) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static <T, R1, R2, R> java.util.stream.Collector<T,?,R> teeing(java.util.stream.Collector<? super T,?,R1> downstream1, java.util.stream.Collector<? super T,?,R2> downstream2, java.util.function.BiFunction<? super R1,? super R2,R> merger) { throw new RuntimeException("Stub!"); }
}

