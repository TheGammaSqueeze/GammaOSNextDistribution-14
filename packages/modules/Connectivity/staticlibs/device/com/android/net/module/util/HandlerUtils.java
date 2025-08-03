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

package com.android.net.module.util;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class for Handler related utilities.
 *
 * @hide
 */
public class HandlerUtils {
    /**
     * Runs the specified task synchronously for dump method.
     * <p>
     * If the current thread is the same as the handler thread, then the runnable
     * runs immediately without being enqueued.  Otherwise, posts the runnable
     * to the handler and waits for it to complete before returning.
     * </p><p>
     * This method is dangerous!  Improper use can result in deadlocks.
     * Never call this method while any locks are held or use it in a
     * possibly re-entrant manner.
     * </p><p>
     * This method is made to let dump method access members on the handler thread to
     * avoid concurrent access problems or races.
     * </p><p>
     * If timeout occurs then this method returns <code>false</code> but the runnable
     * will remain posted on the handler and may already be in progress or
     * complete at a later time.
     * </p><p>
     * When using this method, be sure to use {@link Looper#quitSafely} when
     * quitting the looper.  Otherwise {@link #runWithScissorsForDump} may hang indefinitely.
     * (TODO: We should fix this by making MessageQueue aware of blocking runnables.)
     * </p>
     *
     * @param h The target handler.
     * @param r The Runnable that will be executed synchronously.
     * @param timeout The timeout in milliseconds, or 0 to not wait at all.
     *
     * @return Returns true if the Runnable was successfully executed.
     *         Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     *
     * @hide
     */
    public static boolean runWithScissorsForDump(@NonNull Handler h, @NonNull Runnable r,
                                                 long timeout) {
        if (r == null) {
            throw new IllegalArgumentException("runnable must not be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        if (Looper.myLooper() == h.getLooper()) {
            r.run();
            return true;
        }

        final CountDownLatch latch = new CountDownLatch(1);

        // Don't crash in the handler if something in the runnable throws an exception,
        // but try to propagate the exception to the caller.
        AtomicReference<RuntimeException> exceptionRef = new AtomicReference<>();
        h.post(() -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                exceptionRef.set(e);
            }
            latch.countDown();
        });

        try {
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            exceptionRef.compareAndSet(null, new IllegalStateException("Thread interrupted", e));
        }

        final RuntimeException e = exceptionRef.get();
        if (e != null) throw e;
        return true;
    }
}
