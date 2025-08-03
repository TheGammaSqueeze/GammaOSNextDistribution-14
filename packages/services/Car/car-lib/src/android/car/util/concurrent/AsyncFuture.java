/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.car.util.concurrent;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TestApi;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Custom {@code Future} that provides a way to be handled asynchronously.
 *
 * <p><b>Note: it doesn't extend {@link java.util.concurrent.Future} because it's implemented by
 * {@code AndroidFuture}, which doesn't propagate
 * {@link java.util.concurrent.Future#cancel(boolean)} to the remote object.
 *
 * @param <T> type returned by the {@code Future}.
 *
 * @hide
 */
@TestApi
public interface AsyncFuture<T> {

    /**
     * See {@link java.util.concurrent.Future#get()}.
     */
    @Nullable
    T get() throws InterruptedException, ExecutionException;

    /**
     * See {@link java.util.concurrent.Future#get(long, TimeUnit).
     */
    @Nullable
    T get(long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException;

    /**
     * See {@link java.util.concurrent.CompletableFuture#whenCompleteAsync(BiConsumer, Executor).
     */
    @NonNull
    AsyncFuture<T> whenCompleteAsync(@NonNull BiConsumer<? super T, ? super Throwable> action,
            @NonNull Executor executor);
}
