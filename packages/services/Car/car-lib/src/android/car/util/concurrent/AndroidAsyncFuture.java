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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Implements {@link AsyncFuture} by wrapping a {@link AndroidFuture}.
 *
 * @hide
 */
public final class AndroidAsyncFuture<T> implements AsyncFuture<T> {

    @NonNull
    private final AndroidFuture<T> mFuture;

    public AndroidAsyncFuture(@NonNull AndroidFuture<T> future) {
        mFuture = future;
    }
    @Override
    public T get() throws InterruptedException, ExecutionException {
        return mFuture.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return mFuture.get(timeout, unit);
    }

    @Override
    public AsyncFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action,
            Executor executor) {
        mFuture.whenCompleteAsync(action, executor);
        return this;
    }
}
