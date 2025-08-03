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

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

final class ConcurrencyHelper {

    private static int sThreadId;

    private static final String TAG = ConcurrencyHelper.class.getSimpleName();

    public static void runAsync(long timeoutMs, Runnable r) {
        Thread t =
                new Thread(
                        () -> {
                            Log.v(TAG, "Sleeping " + timeoutMs + "ms on " + Thread.currentThread());
                            SystemClock.sleep(timeoutMs);
                            Log.v(TAG, "Woke up");
                            r.run();
                            Log.v(TAG, "Done");
                        },
                        TAG + ".runAsync()_thread#" + ++sThreadId);
        Log.v(TAG, "Starting thread " + t);
        t.start();
    }

    public static void runOnMainThread(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    private ConcurrencyHelper() {
        throw new UnsupportedOperationException("Contains only static members");
    }
}
