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

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.car.cts.builtin.util.LogcatHelper.Level;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public final class LogcatHelper {

    private static final String TAG = LogcatHelper.class.getSimpleName();

    private static final boolean VERBOSE = false;

    private static final int DEFAULT_TIMEOUT_MS = 60_000;

    private LogcatHelper() {}

    /**
     * Logcat buffers to search.
     */
    public enum Buffer{
        EVENTS, MAIN, SYSTEM, ALL;
    }

    /**
     * Logcat levels to search.
     */
    public enum Level {
        VERBOSE("V"), DEBUG("D"), INFO("I"), WARN("W"), ERROR("E"), ASSERT("A");

        private String mValue;

        public String getValue() {
            return mValue;
        }

        Level(String v) {
            mValue = v;
        }
    }

    /**
     * Asserts that a message appears on {@code logcat}, using a default timeout.
     *
     * @param buffer logcat buffer to search
     * @param level expected log level
     * @parma tag expected log tag
     * @param message substring of the expected message
     * @param timeout for waiting the message
     */
    public static void assertLogcatMessage(Buffer buffer, Level level, String tag, String message) {
        assertLogcatMessage(buffer, level, tag, message, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Asserts that a message appears on {@code logcat}.
     *
     * @param buffer logcat buffer to search
     * @param level expected log level
     * @parma tag expected log tag
     * @param message substring of the expected message
     * @param timeout for waiting the message
     */
    public static void assertLogcatMessage(Buffer buffer, Level level, String tag, String message,
            int timeout) {
        String match = String.format("%s %s: %s", level.mValue, tag, message);
        long startTime = SystemClock.elapsedRealtime();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String command = "logcat -b " + buffer.name().toLowerCase();
        ParcelFileDescriptor output = automation.executeShellCommand(command);
        Log.d(TAG, "ran '" + command + "'; will now look for '" + match + "'");
        FileDescriptor fd = output.getFileDescriptor();
        FileInputStream fileInputStream = new FileInputStream(fd);
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(fileInputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (VERBOSE) {
                    Log.v(TAG, "Checking line '" + line + "'");
                }
                if (line.contains(match)) {
                    Log.d(TAG, "Found match on line '" + line + "', returning");
                    return;
                }
                if ((SystemClock.elapsedRealtime() - startTime) > timeout) {
                    fail("match '" + match + "' was not found, Timeout: " + timeout + " ms");
                }
            }
        } catch (IOException e) {
            fail("match '" + match + "' was not found, IO exception: " + e);
        }
    }

    /**
     * Asserts that a message DOESN'T appears on {@code logcat}.
     *
     * @param buffer is logcat buffer to search
     * @param level expected log level
     * @parma tag expected log tag
     * @param message substring of the message that shouldn't appeard
     * @param timeout for waiting the message
     */
    public static void assertNoLogcatMessage(Buffer buffer, Level level, String tag, String message,
            int timeout) throws Exception {
        String match = String.format("%s %s: %s", level.mValue, tag, message);
        long startTime = SystemClock.elapsedRealtime();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ParcelFileDescriptor output = automation.executeShellCommand("logcat -b all");
        FileDescriptor fd = output.getFileDescriptor();
        FileInputStream fileInputStream = new FileInputStream(fd);
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(fileInputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(match)) {
                    fail("Match was not expected, but found: " + match);
                }
                if ((SystemClock.elapsedRealtime() - startTime) > timeout) {
                    return;
                }
            }
        } catch (IOException e) {
            fail("match was not found, IO exception: " + e);
        }
    }

    /**
     * Clears all logs.
     */
    public static void clearLog() {
        if (VERBOSE) {
            Log.d(TAG, "Clearing logcat logs");
        }
        SystemUtil.runShellCommand("logcat -b all -c");
    }

    /**
     * Sets the log level of the given tag.
     */
    public static void setLogLevel(String tag, Level level) {
        SystemUtil.runShellCommand("setprop log.tag." + tag + " " + level.getValue());
    }
}
