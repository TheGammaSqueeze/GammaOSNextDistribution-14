/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.imsserviceentitlement.utils;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

import java.util.concurrent.Executor;

/** Provides executors for running the tasks asynchronized. */
public final class Executors {
    /**
     * Whether to execute entitlementCheck in caller's thread, set to true via reflection for test.
     */
    private static boolean sUseDirectExecutorForTest = false;

    private static final Executor ASYNC_EXECUTOR = THREAD_POOL_EXECUTOR;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private Executors() {}

    /** Returns {@link Executor} executing tasks asynchronously. */
    public static Executor getAsyncExecutor() {
        return sUseDirectExecutorForTest ? DIRECT_EXECUTOR : ASYNC_EXECUTOR;
    }

    /** Returns {@link Executor} executing tasks from the caller's thread. */
    public static Executor getDirectExecutor() {
        return DIRECT_EXECUTOR;
    }
}
