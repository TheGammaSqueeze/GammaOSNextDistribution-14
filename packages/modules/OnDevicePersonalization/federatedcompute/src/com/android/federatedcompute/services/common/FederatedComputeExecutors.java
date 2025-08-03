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

package com.android.federatedcompute.services.common;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * All executors of the FederatedCompute APK. Copied from
 * com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors.
 */
public final class FederatedComputeExecutors {
    private static final ListeningExecutorService sBackgroundExecutor =
            MoreExecutors.listeningDecorator(
                    Executors.newFixedThreadPool(
                            /* nThreads */ 4,
                            createThreadFactory(
                                    "BG Thread",
                                    Process.THREAD_PRIORITY_BACKGROUND,
                                    Optional.of(getIoThreadPolicy()))));

    private static final ListeningExecutorService sLightweightExecutor =
            MoreExecutors.listeningDecorator(
                    Executors.newFixedThreadPool(
                            /* nThreads */ Math.max(
                                    2, Runtime.getRuntime().availableProcessors() - 2),
                            createThreadFactory(
                                    "Lite Thread",
                                    Process.THREAD_PRIORITY_DEFAULT,
                                    Optional.of(getAsyncThreadPolicy()))));

    private static final ListeningExecutorService sBlockingExecutor =
            MoreExecutors.listeningDecorator(
                    Executors.newCachedThreadPool(
                            createThreadFactory(
                                    "Blocking Thread",
                                    Process.THREAD_PRIORITY_BACKGROUND
                                            + Process.THREAD_PRIORITY_LESS_FAVORABLE,
                                    Optional.empty())));

    private static final HandlerThread sHandlerThread = createHandlerThread();

    private static final Handler sHandler = new Handler(sHandlerThread.getLooper());

    private FederatedComputeExecutors() {}

    /**
     * Returns an executor suitable for long-running tasks, like database operations, file I/O, or
     * heavy CPU-bound computation.
     */
    @NonNull
    public static ListeningExecutorService getBackgroundExecutor() {
        return sBackgroundExecutor;
    }

    /** Returns an executor for tasks that don't do direct I/O and that are fast (<10ms). */
    @NonNull
    public static ListeningExecutorService getLightweightExecutor() {
        return sLightweightExecutor;
    }

    /**
     * Returns an executor suitable for tasks which block for indeterminate amounts of time and are
     * not CPU bound.
     */
    @NonNull
    public static ListeningExecutorService getBlockingExecutor() {
        return sBlockingExecutor;
    }

    /** Returns a Handler that can post messages to a HandlerThread. */
    public static Handler getHandler() {
        return sHandler;
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
                                return new Thread(
                                        () -> {
                                            if (policy.isPresent()) {
                                                StrictMode.setThreadPolicy(policy.get());
                                            }
                                            // Process class operates on the current thread.
                                            Process.setThreadPriority(priority);
                                            runnable.run();
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

    private static HandlerThread createHandlerThread() {
        HandlerThread handlerThread = new HandlerThread("DisplayThread");
        handlerThread.start();
        return handlerThread;
    }
}
