/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.phone;

import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.os.DropBoxManager;
import android.os.SystemClock;
import android.os.TransactionTooLargeException;
import android.telephony.AnomalyReporter;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * A class to help collect dumpsys/logcat and persist it to the
 * on-device dropbox service. It is purely a utility and does
 * not make decisions on if/when to collect.
 */
public class DiagnosticDataCollector {

    //error msg that is appended to output if cmd execution results in error
    public static final String ERROR_MSG = "DiagnosticDataCollector error executing cmd";
    private static final String TAG = "DDC";
    private static final String[] TELECOM_DUMPSYS_COMMAND =
            {"/system/bin/dumpsys", "telecom", "EmergencyDiagnostics"};
    private static final String[] TELEPHONY_DUMPSYS_COMMAND =
            {"/system/bin/dumpsys", "telephony.registry", "EmergencyDiagnostics"};
    private static final String LOGCAT_BINARY =
            "/system/bin/logcat";
    private static final String LOGCAT_BUFFERS = "system,radio";
    private static final long LOG_TIME_OFFSET_MILLIS = 75L;
    private static final String DUMPSYS_BINARY = "/system/bin/dumpsys";
    private final Runtime mJavaRuntime;
    private final Executor mAsyncTaskExecutor;
    private final DropBoxManager mDropBoxManager;
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.mmm",
            Locale.US);
    private final boolean mIsLowRamDevice;
    public static final UUID DROPBOX_TRANSACTION_TOO_LARGE_EXCEPTION =
            UUID.fromString("ab27e97a-ef7b-11ed-a05b-0242ac120003");
    public static final String DROPBOX_TRANSACTION_TOO_LARGE_MSG =
            "DiagnosticDataCollector: transaction too large";
    public DiagnosticDataCollector(Runtime javaRuntime, Executor asyncTaskExecutor,
            DropBoxManager dropBoxManager, boolean isLowRamDevice) {
        mJavaRuntime = javaRuntime;
        mAsyncTaskExecutor = asyncTaskExecutor;
        mDropBoxManager = dropBoxManager;
        mIsLowRamDevice = isLowRamDevice;
    }

    public void persistEmergencyDianosticData(@NonNull DataCollectorConfig.Adapter dc,
            @NonNull TelephonyManager.EmergencyCallDiagnosticData ecdData, @NonNull String tag) {

        if (ecdData.isTelephonyDumpsysCollectionEnabled()) {
            persistTelephonyState(dc, tag);
        }
        if (ecdData.isTelecomDumpsysCollectionEnabled()) {
            persistTelecomState(dc, tag);
        }
        if (ecdData.isLogcatCollectionEnabled()) {
            persistLogcat(dc, tag, ecdData.getLogcatCollectionStartTimeMillis());
        }
    }


    @SuppressWarnings("JavaUtilDate") //just used for DateFormatter.format (required by logcat)
    private void persistLogcat(DataCollectorConfig.Adapter dc, String tag, long logcatStartTime) {
        String startTime = mDateFormat.format(new Date(logcatStartTime - LOG_TIME_OFFSET_MILLIS));
        Log.d(TAG, "Persisting Logcat");
        int maxLines;
        if (mIsLowRamDevice) {
            maxLines = dc.getMaxLogcatLinesForLowMemDevice();
        } else {
            maxLines = dc.getMaxLogcatLines();
        }
        DiagnosticRunnable dr = new DiagnosticRunnable(
                new String[]{LOGCAT_BINARY, "-t", startTime, "-b", LOGCAT_BUFFERS},
                dc.getLogcatReadTimeoutMillis(), dc.getLogcatProcTimeoutMillis(),
                tag, dc.getMaxLogcatLinesForLowMemDevice());
        mAsyncTaskExecutor.execute(dr);
    }

    private void persistTelecomState(DataCollectorConfig.Adapter dc, String tag) {
        Log.d(TAG, "Persisting Telecom state");
        DiagnosticRunnable dr = new DiagnosticRunnable(TELECOM_DUMPSYS_COMMAND,
                dc.getDumpsysReadTimeoutMillis(), dc.getDumpsysProcTimeoutMillis(),
                tag, dc.getMaxLogcatLines());
        mAsyncTaskExecutor.execute(dr);
    }

    private void persistTelephonyState(DataCollectorConfig.Adapter dc, String tag) {
        Log.d(TAG, "Persisting Telephony state");
        DiagnosticRunnable dr = new DiagnosticRunnable(TELEPHONY_DUMPSYS_COMMAND,
                dc.getDumpsysReadTimeoutMillis(),
                dc.getDumpsysProcTimeoutMillis(),
                tag, dc.getMaxLogcatLines());
        mAsyncTaskExecutor.execute(dr);
    }

    private class DiagnosticRunnable implements Runnable {

        private static final String TAG = "DDC-DiagnosticRunnable";
        private final String[] mCmd;
        private final String mDropBoxTag;
        private final int mMaxLogcatLines;
        private long mStreamTimeout;
        private long mProcTimeout;

        DiagnosticRunnable(String[] cmd, long streamTimeout, long procTimeout, String dropboxTag,
                int maxLogcatLines) {
            mCmd = cmd;
            mStreamTimeout = streamTimeout;
            mProcTimeout = procTimeout;
            mDropBoxTag = dropboxTag;
            mMaxLogcatLines = maxLogcatLines;
            Log.d(TAG, "Runnable created with cmd: " + Arrays.toString(cmd));
        }

        @Override
        @WorkerThread
        public void run() {
            Log.d(TAG, "Running async persist for tag" + mDropBoxTag);
            getProcOutputAndPersist(mCmd,
                    mStreamTimeout, mProcTimeout, mDropBoxTag, mMaxLogcatLines);
        }

        @WorkerThread
        private void getProcOutputAndPersist(String[] cmd, long streamTimeout, long procTimeout,
                String dropboxTag, int maxLogcatLines) {
            Process process = null;
            StringBuilder output = new StringBuilder();
            long startProcTime = SystemClock.elapsedRealtime();
            int outputSizeFromErrorStream = 0;
            try {
                process = mJavaRuntime.exec(cmd);
                readStreamLinesWithTimeout(
                        new BufferedReader(new InputStreamReader(process.getInputStream())), output,
                        streamTimeout, maxLogcatLines);
                int outputSizeFromInputStream = output.length();
                readStreamLinesWithTimeout(
                        new BufferedReader(new InputStreamReader(process.getErrorStream())), output,
                        streamTimeout, maxLogcatLines);
                Log.d(TAG, "[" + cmd[0] + "]" + "streams read in " + (SystemClock.elapsedRealtime()
                        - startProcTime) + " milliseconds");
                process.waitFor(procTimeout, TimeUnit.MILLISECONDS);
                outputSizeFromErrorStream = output.length() - outputSizeFromInputStream;
            } catch (InterruptedException e) {
                output.append(ERROR_MSG + e.toString() + System.lineSeparator());
            } catch (IOException e) {
                output.append(ERROR_MSG + e.toString() + System.lineSeparator());
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
            Log.d(TAG, "[" + cmd[0] + "]" + "output collected in " + (SystemClock.elapsedRealtime()
                    - startProcTime) + " milliseconds. Size:" + output.toString().length());
            if (outputSizeFromErrorStream > 0) {
                Log.w(TAG, "Cmd ran with errors");
                output.append(ERROR_MSG + System.lineSeparator());
            }
            try {
                mDropBoxManager.addText(dropboxTag, output.toString());
            } catch (Exception e) {
                if (e instanceof TransactionTooLargeException) {
                    AnomalyReporter.reportAnomaly(
                            DROPBOX_TRANSACTION_TOO_LARGE_EXCEPTION,
                            DROPBOX_TRANSACTION_TOO_LARGE_MSG);
                }
                Log.w(TAG, "Exception while writing to Dropbox " + e);
            }
        }

        @WorkerThread
        private void readStreamLinesWithTimeout(
                BufferedReader inReader, StringBuilder outLines, long timeout, int maxLines)
                throws IOException {
            long startTimeMs = SystemClock.elapsedRealtime();
            int totalLines = 0;
            while (SystemClock.elapsedRealtime() < startTimeMs + timeout) {
                // If there is a burst of data, continue reading without checking for timeout.
                while (inReader.ready() && (totalLines < maxLines)) {
                    String line = inReader.readLine();
                    if (line == null) return; // end of stream.
                    outLines.append(line);
                    totalLines++;
                    outLines.append(System.lineSeparator());
                }
                SystemClock.sleep(timeout / 10);
            }
        }
    }
}
