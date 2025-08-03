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

package com.android.media.audiotestharness.tradefed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.media.audiotestharness.server.AudioTestHarnessGrpcServer;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

@RunWith(JUnitParamsRunner.class)
public class AudioTestHarnessServerLogForwardingHandlerTests {

    @Test
    @Parameters(method = "getBuildLogOutputLineParams")
    public void buildLogOutputLine_properlyBuildsLine(
            String testCaseName, LogRecord record, String expectedLogOutput) {}

    public static Object[] getBuildLogOutputLineParams() {

        LogRecord[] logsRecords = new LogRecord[3];

        logsRecords[0] = new LogRecord(Level.SEVERE, "Failed to start gRPC Server");
        logsRecords[0].setLoggerName(
                "com.android.media.audiotestharness.server.AudioTestHarnessGrpcServer");

        logsRecords[1] = new LogRecord(Level.WARNING, "Resource leak!");
        logsRecords[1].setLoggerName("com.android.media.audiotestharness.ResourceClass");

        logsRecords[2] = new LogRecord(Level.FINE, "Some debug message");
        logsRecords[2].setLoggerName("com.android.media.audiotestharness.DebugClass");

        return new Object[][] {
            {
                "Test Case One",
                logsRecords[0],
                "com.android.media.audiotestharness.server.AudioTestHarnessGrpcServer - "
                        + "Failed to start gRPC Server"
            },
            {
                "Test Case Two",
                logsRecords[1],
                "com.android.media.audiotestharness.server.ResourceClass - Resource leak!"
            },
            {
                "Test Case Three",
                logsRecords[2],
                "com.android.media.audiotestharness.server.DebugClass - Some debug message"
            }
        };
    }

    @Test
    public void configureServerLoggerWithHandler_properlyConfiguresRootLogger() throws Exception {
        AudioTestHarnessServerLogForwardingHandler.configureServerLoggerWithHandler(/*
        shouldClearExistingHandlers= */ true);

        Logger serverRootLogger =
                LogManager.getLogManager()
                        .getLogger(AudioTestHarnessGrpcServer.class.getName())
                        .getParent();

        assertEquals(Level.ALL, serverRootLogger.getLevel());
        assertEquals(1, serverRootLogger.getHandlers().length);
        assertTrue(
                serverRootLogger.getHandlers()[0]
                        instanceof AudioTestHarnessServerLogForwardingHandler);
    }
}
