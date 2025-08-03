/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * All executors of the OnDevicePersonalization module.
 */
public final class OnDevicePersonalizationExecutors {
    private static final ListeningExecutorService sHighPriorityBackgroundExecutor =
            MoreExecutors.listeningDecorator(
                    Executors.newFixedThreadPool(
                            /* nThreads */ 2,
                            createThreadFactory(
                                    "HPBG Thread",
                                    Process.THREAD_PRIORITY_BACKGROUND
                                            + Process.THREAD_PRIORITY_MORE_FAVORABLE,
                                    Optional.of(getIoThreadPolicy()))));

    private static final ListeningExecutorService sLowPriorityBackgroundExecutor =
            MoreExecutors.listeningDecorator(
                    Executors.newFixedThreadPool(
                            /* nThreads */ 2,
                            createThreadFactory(
                                    "LPBG Thread",
                                    Process.THREAD_PRIORITY_BACKGROUND
                                            + Process.THREAD_PRIORITY_LESS_FAVORABLE,
                                    Optional.of(getIoThreadPolicy()))));

    private static final ListeningExecutorService sBackgroundExecutor =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(
                    /* nThreads */ 4,
                    createThreadFactory("BG Thread", Process.THREAD_PRIORITY_BACKGROUND,
                            Optional.of(getIoThreadPolicy()))));

    private static final ListeningExecutorService sLightweightExecutor =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(
                    /* nThreads */ Math.max(2, Runtime.getRuntime().availableProcessors() - 2),
                    createThreadFactory("Lite Thread", Process.THREAD_PRIORITY_DEFAULT,
                            Optional.of(getAsyncThreadPolicy()))));

    private static final ListeningExecutorService sBlockingExecutor =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(
                    createThreadFactory("Blocking Thread", Process.THREAD_PRIORITY_BACKGROUND
                            + Process.THREAD_PRIORITY_LESS_FAVORABLE, Optional.empty())));

    private static final ListeningScheduledExecutorService sScheduledExecutor =
            MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(
                    /* nThreads */ 4,
                    createThreadFactory("SCH Thread", Process.THREAD_PRIORITY_BACKGROUND,
                            Optional.of(getIoThreadPolicy()))));

    private static final Handler sHandlerForMainThread = new Handler(Looper.getMainLooper());

    private OnDevicePersonalizationExecutors() {
    }


    /**
     * Returns the higher priority BG executor.
     */
    @NonNull
    public static ListeningExecutorService getHighPriorityBackgroundExecutor() {
        return sHighPriorityBackgroundExecutor;
    }


    /**
     * Returns the lower priority BG executor.
     */
    @NonNull
    public static ListeningExecutorService getLowPriorityBackgroundExecutor() {
        return sLowPriorityBackgroundExecutor;
    }

    /**
     * Returns an executor suitable for long-running tasks, like database operations, file I/O, or
     * heavy CPU-bound computation.
     */
    @NonNull
    public static ListeningExecutorService getBackgroundExecutor() {
        return sBackgroundExecutor;
    }

    /**
     * Returns an executor for tasks that don't do direct I/O and that are fast (<10ms).
     */
    @NonNull
    public static ListeningExecutorService getLightweightExecutor() {
        return sLightweightExecutor;
    }

    /**
     * Returns an executor suitable for tasks which block for indeterminate amounts of time and
     * are not CPU bound.
     */
    @NonNull
    public static ListeningExecutorService getBlockingExecutor() {
        return sBlockingExecutor;
    }

    /**
     * Returns an executor that can start tasks after a delay.
     */
    @NonNull
    public static ListeningScheduledExecutorService getScheduledExecutor() {
        return sScheduledExecutor;
    }

    /**
     * Returns a Handler for the main thread.
     */
    public static Handler getHandlerForMainThread() {
        return sHandlerForMainThread;
    }

    private static ThreadFactory createThreadFactory(
            final String name, final int priority, final Optional<StrictMode.ThreadPolicy> policy) {
        return new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat(name + " #%d")
                .setThreadFactory(
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(final Runnable runnable) {
                                return new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (policy.isPresent()) {
                                            StrictMode.setThreadPolicy(policy.get());
                                        }
                                        // Process class operates on the current thread.
                                        Process.setThreadPriority(priority);
                                        runnable.run();
                                    }
                                });
                            }
                        })
                .build();
    }

    private static ThreadPolicy getAsyncThreadPolicy() {
        return new ThreadPolicy.Builder().detectAll().penaltyLog().build();
    }

    private static ThreadPolicy getIoThreadPolicy() {
        return new ThreadPolicy.Builder()
                .detectNetwork()
                .detectResourceMismatches()
                .detectUnbufferedIo()
                .penaltyLog()
                .build();
    }
}
