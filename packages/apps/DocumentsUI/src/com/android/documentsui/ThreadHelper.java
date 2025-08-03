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

package com.android.documentsui;

import android.os.Looper;

/** Class for handler/thread utils. */
public final class ThreadHelper {
    private ThreadHelper() {
    }

    static final String MUST_NOT_ON_MAIN_THREAD_MSG =
            "This method should not be called on main thread.";
    static final String MUST_ON_MAIN_THREAD_MSG =
            "This method should only be called on main thread.";

    /** Verifies that current thread is not the UI thread. */
    public static void assertNotOnMainThread() {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            fatalAssert(MUST_NOT_ON_MAIN_THREAD_MSG);
        }
    }

    /** Verifies that current thread is the UI thread. */
    public static void assertOnMainThread() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            fatalAssert(MUST_ON_MAIN_THREAD_MSG);
        }
    }

    /**
     * Exceptions thrown in background threads are silently swallowed on Android. Use the
     * uncaught exception handler of the UI thread to force the app to crash.
     */
    public static void fatalAssert(final String message) {
        crashMainThread(new AssertionError(message));
    }

    private static void crashMainThread(Throwable t) {
        Thread.UncaughtExceptionHandler uiThreadExceptionHandler =
                Looper.getMainLooper().getThread().getUncaughtExceptionHandler();
        uiThreadExceptionHandler.uncaughtException(Thread.currentThread(), t);
    }
}
