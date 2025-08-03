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

import static android.car.cts.builtin.util.LogcatHelper.Buffer.MAIN;
import static android.car.cts.builtin.util.LogcatHelper.Level.INFO;
import static android.car.cts.builtin.util.LogcatHelper.assertLogcatMessage;
import static android.car.cts.builtin.util.LogcatHelper.clearLog;

import android.car.builtin.util.TimeUtils;

import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;

public final class TimeUtilsTest {

    private final PrintWriter mWriter = new PrintWriter(System.out);

    @Before
    public void clearLogcat() {
        clearLog();
    }

    @Test
    public void testDumpTime() {
        TimeUtils.dumpTime(mWriter, 179);
        mWriter.flush();

        // Time utils change long into date-time format.
        assertLogMessage("1970-01-01 00:00:00.179");
    }

    @Test
    public void testFormatDuration() {
        TimeUtils.formatDuration(789, mWriter);
        mWriter.flush();

        // Time utils change long into human readable text.
        assertLogMessage("+789ms");
    }

    private void assertLogMessage(String message) {
        assertLogcatMessage(MAIN, INFO, "System.out", message);
    }
}
