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

import com.android.media.audiotestharness.server.AudioTestHarnessGrpcServer;
import com.android.tradefed.log.LogUtil;

import com.google.common.annotations.VisibleForTesting;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@link Handler} that outputs all log records to the {@link com.android.tradefed.log.LogUtil.CLog}
 * CLog shim methods thus forwarding messages from the Java Logging API to the Tradefed Logging API.
 *
 * <p>For portability reasons, the Audio Test Harness Server uses the Java Logging API internally
 * for logging purposes.
 */
public class AudioTestHarnessServerLogForwardingHandler extends Handler {
    private static final String LOG_LINE_ITEM_SEPARATOR = "/";

    /**
     * {@inheritDoc}
     *
     * <p>Forwards the log onto the CLog shim provided in the {@link LogUtil} class.
     */
    @Override
    public void publish(LogRecord record) {
        String logMessage = buildLogOutputLine(record);
        if (record.getLevel().equals(Level.SEVERE)) {
            LogUtil.CLog.e(logMessage);
            if (record.getThrown() != null) {
                LogUtil.CLog.e(record.getThrown());
            }
        } else if (record.getLevel().equals(Level.WARNING)) {
            LogUtil.CLog.w(logMessage);
            if (record.getThrown() != null) {
                LogUtil.CLog.w(record.getThrown());
            }
        } else if (record.getLevel().equals(Level.INFO)) {
            LogUtil.CLog.i(logMessage);
        } else if (record.getLevel().equals(Level.FINE)) {
            LogUtil.CLog.d(logMessage);
        } else {
            LogUtil.CLog.v(logMessage);
        }
    }

    /** Does nothing. */
    @Override
    public void flush() {}

    /** Does nothing. */
    @Override
    public void close() {}

    /**
     * Constructs a string representation of the provided {@link LogRecord} that consists of the
     * logger name and message.
     *
     * <p>A sample log message from this would look like:
     *
     * <pre>com.android.media.audiotestharness.server.AudioTestHarnessGrpcServer - Failed to start
     * gRPC Server.</pre>
     */
    @VisibleForTesting
    static String buildLogOutputLine(LogRecord logRecord) {
        StringBuilder sb = new StringBuilder();

        sb.append(logRecord.getLoggerName());
        sb.append(LOG_LINE_ITEM_SEPARATOR);
        sb.append(logRecord.getMessage());

        return sb.toString();
    }

    /**
     * Helper method which configures the root server logger (defined by the main class's package)
     * with a handler that forwards all log messages.
     *
     * @param shouldClearExistingHandlers flag that determines whether or not existing handlers
     *     should be cleared out from the Server loggers. In general, this flag should most likely
     *     be true to ensure that nothing is double-logged. However, there may be cases where this
     *     may not be desired.
     */
    public static void configureServerLoggerWithHandler(boolean shouldClearExistingHandlers) {
        LogManager.getLogManager().reset();
        Logger serverRootLogger =
                LogManager.getLogManager()
                        .getLogger(AudioTestHarnessGrpcServer.class.getName())
                        .getParent();

        // Ignore programmatically configured log levels since that will be handled by the TradeFed
        // logging system.
        serverRootLogger.setUseParentHandlers(false);
        serverRootLogger.setLevel(Level.ALL);

        // Remove all other handlers from the logger, ensuring that logs are only forwarded
        // to the TradeFed logging system.
        if (shouldClearExistingHandlers) {
            for (Handler handler : serverRootLogger.getHandlers()) {
                serverRootLogger.removeHandler(handler);
            }
        }

        serverRootLogger.addHandler(new AudioTestHarnessServerLogForwardingHandler());
    }
}
