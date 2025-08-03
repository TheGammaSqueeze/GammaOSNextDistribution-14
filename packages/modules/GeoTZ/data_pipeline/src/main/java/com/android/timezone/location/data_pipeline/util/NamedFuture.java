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

package com.android.timezone.location.data_pipeline.util;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link Future} that has a name. It wraps and delegates to another {@link Future}.
 *
 * @param <V> the result type of the wrapped future
 */
public final class NamedFuture<V> implements Future<V> {

    private final String mName;

    private final Future<V> mDelegate;

    /** Creates a {@link NamedFuture}. */
    public NamedFuture(String name, Future<V> delegate) {
        this.mName = Objects.requireNonNull(name);
        this.mDelegate = Objects.requireNonNull(delegate);
    }

    /** Returns the name of this. */
    public String getName() {
        return mName;
    }

    @Override
    public boolean cancel(boolean b) {
        return mDelegate.cancel(b);
    }

    @Override
    public boolean isCancelled() {
        return mDelegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return mDelegate.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return mDelegate.get();
    }

    @Override
    public V get(long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return mDelegate.get(l, timeUnit);
    }
}
