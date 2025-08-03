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

package com.android.threadnetwork.demoapp.concurrent;

import androidx.annotation.GuardedBy;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;

/** Provides executors for executing tasks in background. */
public final class BackgroundExecutorProvider {
    private static final int CONCURRENCY = 4;

    @GuardedBy("BackgroundExecutorProvider.class")
    private static ListeningScheduledExecutorService backgroundExecutor;

    private BackgroundExecutorProvider() {}

    public static synchronized ListeningScheduledExecutorService getBackgroundExecutor() {
        if (backgroundExecutor == null) {
            backgroundExecutor =
                    MoreExecutors.listeningDecorator(
                            Executors.newScheduledThreadPool(/* maxConcurrency= */ CONCURRENCY));
        }
        return backgroundExecutor;
    }
}
