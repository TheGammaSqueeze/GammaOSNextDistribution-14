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

package android.car.app;

import static android.car.app.CarTaskViewController.DBG;

import android.os.Handler;
import android.os.Looper;
import android.util.Slog;

/** A wrapper class for {@link Runnable} which retries in an exponential backoff manner. */
final class RunnerWithBackoff {
    private static final String TAG = RunnerWithBackoff.class.getSimpleName();
    private static final int MAXIMUM_ATTEMPTS = 5;
    private static final int FIRST_BACKOFF_TIME_MS = 1_000; // 1 second
    private static final int MAXIMUM_BACKOFF_TIME_MS = 8_000; // 8 seconds
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mAction;

    private int mBackoffTimeMs;
    private int mAttempts;

    RunnerWithBackoff(Runnable action) {
        mAction = action;
    }

    private final Runnable mRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAttempts >= MAXIMUM_ATTEMPTS) {
                Slog.e(TAG, "Failed to perform action, even after " + mAttempts + " attempts");
                return;
            }
            if (DBG) {
                Slog.d(TAG, "Executing the action. Attempt number " + mAttempts);
            }
            mAction.run();

            mHandler.postDelayed(mRetryRunnable, mBackoffTimeMs);
            increaseBackoff();
            mAttempts++;
        }
    };

    private void increaseBackoff() {
        mBackoffTimeMs *= 2;
        if (mBackoffTimeMs > MAXIMUM_BACKOFF_TIME_MS) {
            mBackoffTimeMs = MAXIMUM_BACKOFF_TIME_MS;
        }
    }

    /** Starts the retrying. The first try happens synchronously. */
    public void start() {
        if (DBG) {
            Slog.d(TAG, "start backoff runner");
        }
        // Stop the existing retrying as a safeguard to prevent multiple starts.
        stopInternal();

        mBackoffTimeMs = FIRST_BACKOFF_TIME_MS;
        mAttempts = 0;
        // Call .run() instead of posting to handler so that first try can happen synchronously.
        mRetryRunnable.run();
    }

    /** Stops the retrying. */
    public void stop() {
        if (DBG) {
            Slog.d(TAG, "stop backoff runner");
        }
        stopInternal();
    }

    private void stopInternal() {
        mHandler.removeCallbacks(mRetryRunnable);
    }
}
