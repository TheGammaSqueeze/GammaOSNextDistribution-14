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

import static android.car.cts.builtin.util.LogcatHelper.Level.ASSERT;
import static android.car.cts.builtin.util.LogcatHelper.Level.DEBUG;
import static android.car.cts.builtin.util.LogcatHelper.Level.ERROR;
import static android.car.cts.builtin.util.LogcatHelper.Level.INFO;
import static android.car.cts.builtin.util.LogcatHelper.Level.VERBOSE;
import static android.car.cts.builtin.util.LogcatHelper.Level.WARN;
import static android.car.cts.builtin.util.LogcatHelper.clearLog;

import static com.google.common.truth.Truth.assertThat;

import android.car.builtin.util.Slogf;
import android.car.cts.builtin.util.LogcatHelper.Level;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class SlogfTest {
    private static final String TAG = SlogfTest.class.getSimpleName();

    private static final int TIMEOUT_MS = 10_000;
    // All Slogf would be logged to system buffer.
    private static final LogcatHelper.Buffer BUFFER = LogcatHelper.Buffer.SYSTEM;
    // wait time for waiting to make sure msg is not logged. Should not be a high value as tests
    // waits for this much time.
    private static final int NOT_LOGGED_WAIT_TIME_MS = 5_000;
    private static final String LOGCAT_LINE_FORMAT = "%s %s: %s";

    private static final String LOGGED_MSG = "This message should exist in logcat.";
    private static final String NOT_LOGGED_MSG = "This message should not exist in logcat.";
    private static final String FORMATTED_MSG = "This message is a format with two args %s and %s.";
    private static final String EXCEPTION_MSG = "This message should exist in logcat.";

    @Before
    public void setup() {
        setLogLevel(VERBOSE);
        clearLog();
    }

    @After
    public void reset() {
        setLogLevel(VERBOSE);
        clearLog();
    }

    @Test
    public void testV_msg1() {
        Slogf.v(TAG, LOGGED_MSG);

        assertLogcatMessage(VERBOSE, LOGGED_MSG);
    }

    @Test
    public void testV_msg2() throws Exception {
        Throwable throwable = new Throwable(EXCEPTION_MSG);

        Slogf.v(TAG, LOGGED_MSG, throwable);

        assertLogcatMessage(VERBOSE, LOGGED_MSG);
        assertLogcatMessage(VERBOSE, "java.lang.Throwable: " + EXCEPTION_MSG);
        assertLogcatStackTrace(VERBOSE, throwable);

    }

    @Test
    public void testV_msg3() {
        Slogf.v(TAG, FORMATTED_MSG, "input1", "input2");

        assertLogcatMessage(VERBOSE, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testV_noMsg1() throws Exception {
        setLogLevel(ERROR);

        Slogf.v(TAG, NOT_LOGGED_MSG);

        assertNoLogcatMessage(VERBOSE, NOT_LOGGED_MSG);
    }

    @Test
    public void testV_noMsg2() throws Exception {
        setLogLevel(ERROR);

        Slogf.v(TAG, FORMATTED_MSG, "input1", "input2");

        assertNoLogcatMessage(VERBOSE, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testD_msg1() {
        Slogf.d(TAG, LOGGED_MSG);

        assertLogcatMessage(DEBUG, LOGGED_MSG);
    }

    @Test
    public void testD_msg2() {
        Throwable throwable = new Throwable(EXCEPTION_MSG);
        Slogf.d(TAG, LOGGED_MSG, throwable);

        assertLogcatMessage(DEBUG, LOGGED_MSG);
        assertLogcatMessage(DEBUG, "java.lang.Throwable: " + EXCEPTION_MSG);
        assertLogcatStackTrace(DEBUG, throwable);
    }

    @Test
    public void testD_msg3() {
        Slogf.d(TAG, FORMATTED_MSG, "input1", "input2");

        assertLogcatMessage(DEBUG, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testD_noMsg1() throws Exception {
        setLogLevel(ERROR);

        Slogf.d(TAG, NOT_LOGGED_MSG);

        assertNoLogcatMessage(DEBUG, NOT_LOGGED_MSG);
    }

    @Test
    public void testD_noMsg2() throws Exception {
        setLogLevel(ERROR);

        Slogf.d(TAG, FORMATTED_MSG, "input1", "input2");

        assertNoLogcatMessage(DEBUG, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testI_msg1() {
        Slogf.i(TAG, LOGGED_MSG);

        assertLogcatMessage(INFO, LOGGED_MSG);
    }

    @Test
    public void testI_msg2() throws Exception {
        Throwable throwable = new Throwable(EXCEPTION_MSG);

        Slogf.i(TAG, LOGGED_MSG, throwable);

        assertLogcatMessage(INFO, LOGGED_MSG);
        assertLogcatMessage(INFO, "java.lang.Throwable: " + EXCEPTION_MSG);
        assertLogcatStackTrace(INFO, throwable);

    }

    @Test
    public void testI_msg3() {
        Slogf.i(TAG, FORMATTED_MSG, "input1", "input2");

        assertLogcatMessage(INFO, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testI_noMsg1() throws Exception {
        setLogLevel(ERROR);

        Slogf.i(TAG, NOT_LOGGED_MSG);

        assertNoLogcatMessage(INFO, NOT_LOGGED_MSG);
    }

    @Test
    public void testI_noMsg2() throws Exception {
        setLogLevel(ERROR);

        Slogf.i(TAG, FORMATTED_MSG, "input1", "input2");

        assertNoLogcatMessage(INFO, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testW_msg1() {
        Slogf.w(TAG, LOGGED_MSG);

        assertLogcatMessage(WARN, LOGGED_MSG);
    }

    @Test
    public void testW_msg2() throws Exception {
        Throwable throwable = new Throwable(EXCEPTION_MSG);

        Slogf.w(TAG, LOGGED_MSG, throwable);

        assertLogcatMessage(WARN, LOGGED_MSG);
        assertLogcatMessage(WARN, "java.lang.Throwable: " + EXCEPTION_MSG);
        assertLogcatStackTrace(WARN, throwable);

    }

    @Test
    public void testW_msg3() {
        Slogf.w(TAG, FORMATTED_MSG, "input1", "input2");

        assertLogcatMessage(WARN, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testW_msg4() throws Exception {
        Throwable throwable = new Throwable(EXCEPTION_MSG);

        Slogf.w(TAG, throwable);

        assertLogcatMessage(WARN, "java.lang.Throwable: " + EXCEPTION_MSG);
        assertLogcatStackTrace(WARN, throwable);
    }

    @Test
    public void testW_msg5() {
        Exception exception = new Exception(EXCEPTION_MSG);

        Slogf.w(TAG, exception, FORMATTED_MSG, "input1", "input2");

        assertLogcatMessage(WARN, FORMATTED_MSG, "input1", "input2");
        assertLogcatMessage(WARN, "java.lang.Exception: " + EXCEPTION_MSG);
        assertLogcatStackTrace(WARN, exception);
    }

    @Test
    public void testW_noMsg1() throws Exception {
        setLogLevel(ERROR);

        Slogf.w(TAG, NOT_LOGGED_MSG);

        assertNoLogcatMessage(WARN, NOT_LOGGED_MSG);
    }

    @Test
    public void testW_noMsg2() throws Exception {
        setLogLevel(ERROR);

        Slogf.w(TAG, FORMATTED_MSG, "input1", "input2");

        assertNoLogcatMessage(WARN, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testE_msg1() {
        Slogf.e(TAG, LOGGED_MSG);

        assertLogcatMessage(ERROR, LOGGED_MSG);
    }

    @Test
    public void testE_msg2() throws Exception {
        Throwable throwable = new Throwable(EXCEPTION_MSG);

        Slogf.e(TAG, LOGGED_MSG, throwable);

        assertLogcatMessage(ERROR, LOGGED_MSG);
        assertLogcatMessage(ERROR, "java.lang.Throwable: " + EXCEPTION_MSG);
        assertLogcatStackTrace(ERROR, throwable);

    }

    @Test
    public void testE_msg3() {
        Slogf.e(TAG, FORMATTED_MSG, "input1", "input2");

        assertLogcatMessage(ERROR, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testE_msg4() {
        Exception exception = new Exception(EXCEPTION_MSG);

        Slogf.e(TAG, exception, FORMATTED_MSG, "input1", "input2");

        assertLogcatMessage(ERROR, FORMATTED_MSG, "input1", "input2");
        assertLogcatMessage(ERROR, "java.lang.Exception: " + EXCEPTION_MSG);
        assertLogcatStackTrace(ERROR, exception);
    }

    @Test
    public void testE_noMsg1() throws Exception {
        setLogLevel(ASSERT);

        Slogf.e(TAG, NOT_LOGGED_MSG);

        assertNoLogcatMessage(ERROR, NOT_LOGGED_MSG);
    }

    @Test
    public void testE_noMsg2() throws Exception {
        setLogLevel(ASSERT);

        Slogf.e(TAG, FORMATTED_MSG, "input1", "input2");

        assertNoLogcatMessage(ERROR, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testWTF_msg1() {
        Slogf.wtf(TAG, LOGGED_MSG);

        // WTF is logged as ERROR
        assertLogcatMessage(ERROR, LOGGED_MSG);
    }

    @Test
    public void testWTF_msg2() throws Exception {
        Throwable throwable = new Throwable(EXCEPTION_MSG);

        Slogf.wtf(TAG, LOGGED_MSG, throwable);

        // WTF is logged as ERROR
        assertLogcatMessage(ERROR, LOGGED_MSG);
        assertLogcatMessage(ERROR, "java.lang.Throwable: " + EXCEPTION_MSG);
        assertLogcatStackTrace(ERROR, throwable);

    }

    @Test
    public void testWTF_msg3() {
        Slogf.wtf(TAG, FORMATTED_MSG, "input1", "input2");

        // WTF is logged as ERROR
        assertLogcatMessage(ERROR, FORMATTED_MSG, "input1", "input2");
    }

    @Test
    public void testWTF_msg4() throws Exception {
        Throwable throwable = new Throwable(EXCEPTION_MSG);

        Slogf.wtf(TAG, throwable);

        // WTF is logged as ERROR
        assertLogcatMessage(ERROR, "java.lang.Throwable: " + EXCEPTION_MSG);
        assertLogcatStackTrace(ERROR, throwable);
    }

    @Test
    public void testWTF_msg5() {
        Exception exception = new Exception(EXCEPTION_MSG);

        Slogf.wtf(TAG, exception, FORMATTED_MSG, "input1", "input2");

        // WTF is logged as ERROR
        assertLogcatMessage(ERROR, FORMATTED_MSG, "input1", "input2");
        assertLogcatMessage(ERROR, "java.lang.Exception: " + EXCEPTION_MSG);
        assertLogcatStackTrace(ERROR, exception);
    }

    @Test
    public void testIsLoggableTrue() throws Exception {
        setLogLevel(VERBOSE);

        assertThat(Slogf.isLoggable(TAG, Log.VERBOSE)).isTrue();
    }

    @Test
    public void testIsLoggableFalse() throws Exception {
        setLogLevel(ERROR);
        setCarTestTagLogLevel(ASSERT);

        assertThat(Slogf.isLoggable(TAG, Log.VERBOSE)).isFalse();
    }

    @Test
    public void testIsLoggableFalse_withCarTestTagEnabled() throws Exception {
        setLogLevel(ERROR);
        setCarTestTagLogLevel(VERBOSE);

        assertThat(Slogf.isLoggable(TAG, Log.VERBOSE)).isTrue();
    }

    @Test
    public void testSlogfIsfLoggableWorksSameAsLogIsLoggable() throws Exception {
        setLogLevel(INFO);
        // Emulate the tag as if it's not in the car tests.
        setCarTestTagLogLevel(ASSERT);

        assertThat(Log.isLoggable(TAG, Log.DEBUG)).isFalse();
        assertThat(Slogf.isLoggable(TAG, Log.DEBUG)).isFalse();

        assertThat(Log.isLoggable(TAG, Log.INFO)).isTrue();
        assertThat(Slogf.isLoggable(TAG, Log.INFO)).isTrue();
    }

    @Test
    public void testSlogfIsfLoggableEnabledInCarTests() throws Exception {
        setLogLevel(INFO);

        assertThat(Log.isLoggable(TAG, Log.DEBUG)).isFalse();
        assertThat(Slogf.isLoggable(TAG, Log.DEBUG)).isTrue();
    }

    private void setLogLevel(Level level) {
        LogcatHelper.setLogLevel(TAG, level);
    }

    private void setCarTestTagLogLevel(Level level) {
        // CAR.TEST Comes from Slogf.CAR_TEST_TAG;
        LogcatHelper.setLogLevel("CAR.TEST", level);
    }

    private void assertNoLogcatMessage(Level level, String format, Object... args)
            throws Exception {
        String message = String.format(format, args);
        LogcatHelper.assertNoLogcatMessage(BUFFER, level, TAG, message, NOT_LOGGED_WAIT_TIME_MS);
    }

    private void assertLogcatMessage(Level level, String format, Object... args) {
        String message = String.format(format, args);
        LogcatHelper.assertLogcatMessage(BUFFER, level, TAG, message, TIMEOUT_MS);
    }

    private void assertLogcatStackTrace(Level level, Throwable throwable) {
        StackTraceElement[] elements = throwable.getStackTrace();
        for (int i = 0; i < elements.length; i++) {
            assertLogcatMessage(level, "\tat " + elements[i]);
        }
    }
}
