/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.federatedcompute.services.training.util;

import com.google.common.base.Preconditions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Manages updating listeners for a value which is maintained externally (and made available through
 * a {@link Supplier}).
 *
 * @param <V> the type of supplier
 */
public class ListenableSupplier<V> implements Supplier<V> {
    private final Supplier<V> mSupplier;
    private final ConcurrentHashMap<Runnable, Executor> mMap;

    public ListenableSupplier(Supplier<V> supplier) {
        Preconditions.checkNotNull(supplier);
        this.mSupplier = supplier;
        mMap = new ConcurrentHashMap<>();
    }

    @Override
    public V get() {
        return mSupplier.get();
    }

    /** Notifies all listeners that the value has changed by executing subscribed Runnables. */
    public void runListeners() {
        for (Map.Entry<Runnable, Executor> entry : mMap.entrySet()) {
            entry.getValue().execute(entry.getKey());
        }
    }

    /**
     * Registers a listener to be run on the given executor. The listener will run when the supplier
     * value is manually changed via {@link #runListeners()}.
     */
    public void addListener(Runnable listener, Executor executor) {
        Preconditions.checkNotNull(listener);
        Preconditions.checkNotNull(executor);
        mMap.put(listener, executor);
    }

    /** Unregisters a previously registered listener. */
    public void removeListener(Runnable listener) {
        mMap.remove(listener);
    }
}
