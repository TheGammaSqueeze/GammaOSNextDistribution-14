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
package android.platform.test.util;

import android.os.SystemClock;
import android.util.Log;

/** Contains methods for running actions in a timed way. */
public final class TimedAction {

    private static final String LOG_TAG = TimedAction.class.getSimpleName();

    private TimedAction() { }

    /** Runs an action and, if it completes early, waits until the intended duration has passed. */
    public static void runWithIntendedDuration (Runnable action, long durationMs) {
        long startTimeMs = SystemClock.elapsedRealtime();
        action.run();

        long leftoverDurationMs = (startTimeMs + durationMs) - SystemClock.elapsedRealtime();
        if (leftoverDurationMs > 0) {
            // Sleep for the provided duration.
            SystemClock.sleep(leftoverDurationMs);
        } else {
            Log.w(LOG_TAG, "This action went over its intended duration.");
        }
    }
}
