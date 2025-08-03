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

package com.android.safetycenter;

import android.os.Handler;

import com.android.permission.util.ForegroundThread;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class to track timeouts related to Safety Center.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@NotThreadSafe
final class SafetyCenterTimeouts {

    /**
     * The maximum number of timeouts we are tracking at a given time. This is to avoid having the
     * {@code mTimeouts} queue grow unbounded. In practice, we should never have more than 1 or 2
     * timeouts in flight.
     */
    private static final int MAX_TRACKED = 10;

    private final ArrayDeque<Runnable> mTimeouts = new ArrayDeque<>(MAX_TRACKED);

    private final Handler mForegroundHandler = ForegroundThread.getHandler();

    SafetyCenterTimeouts() {}

    /** Adds the given {@link Runnable} to run as a timeout after the given {@link Duration}. */
    void add(Runnable timeoutAction, Duration timeoutDuration) {
        if (mTimeouts.size() + 1 >= MAX_TRACKED) {
            remove(mTimeouts.pollFirst());
        }
        mTimeouts.addLast(timeoutAction);
        mForegroundHandler.postDelayed(timeoutAction, timeoutDuration.toMillis());
    }

    /** Removes the given {@link Runnable} to run as a timeout. */
    void remove(Runnable timeoutAction) {
        mTimeouts.remove(timeoutAction);
        mForegroundHandler.removeCallbacks(timeoutAction);
    }

    /** Clears all timeouts. */
    void clear() {
        while (!mTimeouts.isEmpty()) {
            mForegroundHandler.removeCallbacks(mTimeouts.pollFirst());
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(PrintWriter fout) {
        int count = mTimeouts.size();
        fout.println("TIMEOUTS (" + count + ")");
        Iterator<Runnable> it = mTimeouts.iterator();
        int i = 0;
        while (it.hasNext()) {
            fout.println("\t[" + i++ + "] " + it.next());
        }
        fout.println();
    }
}
