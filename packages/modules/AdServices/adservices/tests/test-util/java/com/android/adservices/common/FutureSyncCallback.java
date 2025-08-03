/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.common;

import android.annotation.Nullable;

import com.google.common.util.concurrent.FutureCallback;

/**
 * * SyncCallback based implementation for FutureCallback.
 *
 * @param <T> type of the object received on success.
 */
public class FutureSyncCallback<T> extends SyncCallback<T, Throwable> implements FutureCallback<T> {

    /**
     * Default constructor, uses {@link #DEFAULT_TIMEOUT_MS} for timeout and fails if the {@code
     * inject...} method is called in the main thread.
     */
    public FutureSyncCallback() {
        super();
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    public FutureSyncCallback(int timeoutMs) {
        super(timeoutMs);
    }

    @Override
    public void onSuccess(@Nullable T result) {
        injectResult(result);
    }

    @Override
    public void onFailure(Throwable t) {
        injectError(t);
    }
}
