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

package android.car.cts.builtin.util;

import static android.car.cts.builtin.util.LogcatHelper.Buffer.SYSTEM;
import static android.car.cts.builtin.util.LogcatHelper.Level.VERBOSE;
import static android.car.cts.builtin.util.LogcatHelper.assertLogcatMessage;
import static android.car.cts.builtin.util.LogcatHelper.clearLog;

import android.car.builtin.os.BuildHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.TimingsTraceLog;

import org.junit.Before;
import org.junit.Test;

public final class TimingsTraceLogTest {

    private static final String TAG = TimingsTraceLogTest.class.getSimpleName();

    @Before
    public void clearLogcat() {
        clearLog();
    }

    @Test
    public void testTimingsTraceLog() {
        TimingsTraceLog timingsTraceLog =
                new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        timingsTraceLog.traceBegin("testTimingsTraceLog");
        timingsTraceLog.traceEnd();

        // TODO(b/232814433): assert Trace is called including the user build.
        if (!BuildHelper.isUserBuild()) {
            assertLogMessage("testTimingsTraceLog took to complete");
        }
    }

    @Test
    public void testTimingsTraceLogDuration() {
        TimingsTraceLog timingsTraceLog =
                new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        timingsTraceLog.logDuration("testTimingsTraceLogDuration", 159);

        assertLogMessage("testTimingsTraceLogDuration took to complete: 159ms");
    }

    private void assertLogMessage(String message) {
        assertLogcatMessage(SYSTEM, VERBOSE, TAG, message);
    }
}
