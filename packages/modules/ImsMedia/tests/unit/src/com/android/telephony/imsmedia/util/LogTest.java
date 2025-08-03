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

package com.android.telephony.imsmedia.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LogTest {

    private static final String TAG = "LogTest";
    private static final String LOG_TEST_MESSAGE = "LogTestMessage";

    @Test
    @SmallTest
    public void testInit() {
        int logLevel = Log.DEFAULT_LOG_LEVEL;
        Log.init(logLevel);
        assertEquals(Log.DEFAULT_LOG_LEVEL, Log.getLogLevel());

        logLevel = Log.LOG_LEVEL_DEBUG;
        Log.init(logLevel);
        assertEquals(Log.LOG_LEVEL_DEBUG, Log.getLogLevel());
    }

    @Test
    @SmallTest
    public void logLevels() throws Exception {

        Exception throwable = null;

        try {
            throw new Exception();
        } catch (Exception e) {
            throwable = e;
        }

        // Checks the logs in the logcat.
        Log.v(TAG, LOG_TEST_MESSAGE + ": v");
        Log.d(TAG, LOG_TEST_MESSAGE + ": d");
        Log.i(TAG, LOG_TEST_MESSAGE + ": i");
        Log.w(TAG, LOG_TEST_MESSAGE + ": w");
        Log.e(TAG, LOG_TEST_MESSAGE + ": e");
        Log.e(TAG, LOG_TEST_MESSAGE + ": e with exception", throwable);
    }

    @Test
    @SmallTest
    public void debugConditional() throws Exception {
        // when debug mode is enabled
        Log.setDebuggable(true);
        Log.dc(TAG, LOG_TEST_MESSAGE);

        // when debug mode is disabled
        Log.setDebuggable(false);
        Log.dc(TAG, LOG_TEST_MESSAGE + ": debug conditional");
    }

    @Test
    @SmallTest
    public void setDebuggable() throws Exception {
        Log.setDebuggable(false);
        assertFalse(Log.isDebuggable());

        Log.setDebuggable(true);
        assertTrue(Log.isDebuggable());
    }

    @Test
    @SmallTest
    public void setLogLevel() throws Exception {
        Log.setLogLevel(Log.LOG_LEVEL_INFO);
        assertFalse(Log.isLogEnabled(Log.LOG_LEVEL_VERBOSE));
        assertFalse(Log.isLogEnabled(Log.LOG_LEVEL_DEBUG));
        assertTrue(Log.isLogEnabled(Log.LOG_LEVEL_INFO));
        assertTrue(Log.isLogEnabled(Log.LOG_LEVEL_WARN));
        assertTrue(Log.isLogEnabled(Log.LOG_LEVEL_ERROR));

        Log.setLogLevel(Log.DEFAULT_LOG_LEVEL);
        assertTrue(Log.isLogEnabled(Log.LOG_LEVEL_VERBOSE));
        assertTrue(Log.isLogEnabled(Log.LOG_LEVEL_DEBUG));
        assertTrue(Log.isLogEnabled(Log.LOG_LEVEL_INFO));
        assertTrue(Log.isLogEnabled(Log.LOG_LEVEL_WARN));
        assertTrue(Log.isLogEnabled(Log.LOG_LEVEL_ERROR));
    }

    @Test
    @SmallTest
    public void hidePii() throws Exception {
        // when debug mode is enabled
        Log.setDebuggable(true);
        String hiddenLogMessage;

        hiddenLogMessage = Log.hidePii(null);
        assertEquals(Log.NULL, hiddenLogMessage);

        hiddenLogMessage = Log.hidePii("");
        assertEquals(Log.EMPTY, hiddenLogMessage);

        hiddenLogMessage = Log.hidePii(LOG_TEST_MESSAGE);
        assertEquals(LOG_TEST_MESSAGE, hiddenLogMessage);

        // when debug mode is disabled
        Log.setDebuggable(false);

        hiddenLogMessage = Log.hidePii(null);
        assertEquals(Log.NULL, hiddenLogMessage);

        hiddenLogMessage = Log.hidePii("");
        assertEquals(Log.EMPTY, hiddenLogMessage);

        hiddenLogMessage = Log.hidePii(LOG_TEST_MESSAGE);
        assertEquals(Log.HIDDEN, hiddenLogMessage);
    }
}
