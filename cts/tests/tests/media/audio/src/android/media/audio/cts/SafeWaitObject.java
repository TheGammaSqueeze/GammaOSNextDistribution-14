/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.media.audio.cts;

import android.util.Log;

import java.util.function.BooleanSupplier;

/**
 * Helper class to simplify the handling of spurious wakeups in Object.wait()
 */
final class SafeWaitObject {
    /**
     * Causes the current thread to wait until this object is notified and stopWaiting returns true,
     * or a specified amount of time has elapsed.
     * Unlike Object.wait(), it will not throw an InterruptedException, and will instead retry
     * waiting until the deadline expires.
     *
     * @see Object#wait()
     * @param timeoutMs The maximum time to wait in milliseconds.
     * @param stopWaiting Predicate which returns false if the waiting should be continued
     * @return false if the predicate stopWaiting still evaluates to false after the timeout expired
     *         , otherwise true.
     */
    public boolean waitFor(long timeoutMs, BooleanSupplier stopWaiting) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (this) {
            while (!stopWaiting.getAsBoolean()) {
                final long timeToWait = deadline - System.currentTimeMillis();
                if (timeToWait <= 0) {
                    return false;
                }
                try {
                    this.wait(timeToWait);
                } catch (InterruptedException e) {
                    Log.v("SafeWaitObject", "waiting interrupted, resuming");
                }
            }
        }
        return true;
    }

    /**
     * Blocks the current thread until stopWaiting returns true
     * @param timeoutMs the maximum amount of time to block for
     * @param periodMs the period between two checking attempts
     * @param stopWaiting Predicate which returns false if the waiting should be continued
     * @return false if the predicate stopWaiting still evaluates to false after the timeout
     *          expired, otherwise true.
     */
    public static boolean checkConditionFor(long timeoutMs, long periodMs,
            BooleanSupplier stopWaiting) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (!stopWaiting.getAsBoolean() && (System.currentTimeMillis() < deadline)) {
            try {
                Thread.sleep(periodMs);
            } catch (InterruptedException e) {
                Log.v("SafeWaitObject", "sleeping interrupted, resuming");
            }
        }
        return true;
    }
}
