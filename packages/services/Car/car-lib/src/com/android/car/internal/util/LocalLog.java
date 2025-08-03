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

package com.android.car.internal.util;

import android.os.SystemClock;

import com.android.internal.annotations.GuardedBy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

// Copied from frameworks/base
/**
 * Utility for in memory logging
 *
 * @hide
 */
public final class LocalLog {

    private final int mMaxLines;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final Deque<String> mLog;

    /**
     * {@code true} to use log timestamps expressed in local date/time, {@code false} to use log
     * timestamped expressed with the elapsed realtime clock and UTC system clock. {@code false} is
     * useful when logging behavior that modifies device time zone or system clock.
     */
    private final boolean mUseLocalTimestamps;

    /** Constructor with max lines limit */
    public LocalLog(int maxLines) {
        this(maxLines, true /* useLocalTimestamps */);
    }

    /** Constructor */
    public LocalLog(int maxLines, boolean useLocalTimestamps) {
        mMaxLines = Math.max(0, maxLines);
        mLog = new ArrayDeque<>(mMaxLines);
        mUseLocalTimestamps = useLocalTimestamps;
    }

    /** Adds log */
    public void log(String msg) {
        if (mMaxLines <= 0) {
            return;
        }
        final String logLine;
        if (mUseLocalTimestamps) {
            logLine = LocalDateTime.now() + " - " + msg;
        } else {
            logLine = SystemClock.elapsedRealtime() + " / " + Instant.now() + " - " + msg;
        }
        append(logLine);
    }

    /** Appends log and trims */
    private void append(String logLine) {
        synchronized (mLock) {
            while (mLog.size() >= mMaxLines) {
                mLog.remove();
            }
            mLog.add(logLine);
        }
    }

    /** Dumps saved log */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        dump(pw);
    }

    /** Dumps saved log */
    public void dump(PrintWriter pw) {
        dump("", pw);
    }

    /**
     * Dumps the content of local log to print writer with each log entry predeced with indent
     *
     * @param indent indent that precedes each log entry
     * @param pw printer writer to write into
     */
    public void dump(String indent, PrintWriter pw) {
        synchronized (mLock) {
            Iterator<String> itr = mLog.iterator();
            while (itr.hasNext()) {
                pw.printf("%s%s\n", indent, itr.next());
            }
        }
    }

    /** Dumps saved log in reerse order */
    public void reverseDump(FileDescriptor fd, PrintWriter pw, String[] args) {
        reverseDump(pw);
    }

    /** Dumps saved log in reerse order */
    public void reverseDump(PrintWriter pw) {
        synchronized (mLock) {
            Iterator<String> itr = mLog.descendingIterator();
            while (itr.hasNext()) {
                pw.println(itr.next());
            }
        }
    }
}
