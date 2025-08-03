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

package com.android.car.garagemode;

import android.car.builtin.os.BuildHelper;
import android.car.builtin.util.Slogf;
import android.util.AtomicFile;

import com.android.car.CarLocalServices;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.Locale;

/**
 * GarageModeRecorder is saving Garage mode start/finish times.
 * Information is stored in plain text file and printed to car_service dumpsys.
 */
public final class GarageModeRecorder {
    @VisibleForTesting
    static final String GARAGE_MODE_RECORDING_FILE_NAME = "GarageModeSession.txt";
    @VisibleForTesting
    static final String SESSION_START_TIME = "Session start time: ";
    @VisibleForTesting
    static final String SESSION_FINISH_TIME = "Session finish time: ";
    @VisibleForTesting
    static final String SESSION_DURATION = "Session duration: ";
    @VisibleForTesting
    static final String TIME_UNIT_MS = " ms";
    @VisibleForTesting
    static final String SESSION_WAS_CANCELLED = "Session was cancelled : ";
    @VisibleForTesting
    static final String DATE_FORMAT = "HH:mm:ss.SSS z MM/dd/yyyy";
    @VisibleForTesting
    static final String GARAGE_MODE_RECORDER_IS_SACTIVE = "GarageModeRecorder is %sactive\n";
    @VisibleForTesting
    static final String NOT = "not ";

    private static final String GARAGEMODE_DIR_NAME = "garagemode";
    private static final String TAG = "GarageModeRecorder";
    private static final int EVENT_SESSION_START = 1;
    private static final int EVENT_SESSION_FINISH = 2;
    private static final int EVENT_SESSION_CANCELLED = 3;
    private static final String FALLBACK_CAR_DIR_PATH = "/data/system/car";

    private final SimpleDateFormat mDateFormat;
    private final AtomicFile mGarageModeRecorderFile;
    private final Clock mClock;
    private long mSessionStartTime;
    private long mSessionFinishTime;

    public GarageModeRecorder(Clock clock) {
        mDateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        mClock = clock;
        SystemInterface systemInterface = CarLocalServices.getService(SystemInterface.class);
        File systemCarDir = systemInterface == null ? new File(FALLBACK_CAR_DIR_PATH)
                : systemInterface.getSystemCarDir();
        File garageModeDir = new File(systemCarDir, GARAGEMODE_DIR_NAME);
        garageModeDir.mkdirs();

        mGarageModeRecorderFile = new AtomicFile(
                new File(garageModeDir, GARAGE_MODE_RECORDING_FILE_NAME));
    }

    /**
     * Prints garage mode session history to the {@code dumpsys}.
     */
    public void dump(IndentingPrintWriter writer) {
        //TODO(b/223241457) Add dump to protobuf
        writer.printf(GARAGE_MODE_RECORDER_IS_SACTIVE, (isRecorderEnabled() ? "" : NOT));

        if (!isRecorderEnabled()) return;

        readFileToWriter(writer);
    }

    /**
     * Saves information about start of the Garage mode.
     */
    public void startSession() {
        if (!isRecorderEnabled()) return;

        if (mSessionStartTime != 0) {
            Slogf.e(TAG, "Error, garage mode session is started twice, previous start - %s",
                    mDateFormat.format(mSessionStartTime));
            return;
        }

        mSessionStartTime = mClock.millis();
        recordEvent(EVENT_SESSION_START);
    }

    /**
     * Saves information about finish of the Garage mode.
     */
    public void finishSession() {
        if (!isRecorderEnabled()) return;

        if (mSessionStartTime == 0) {
            Slogf.e(TAG, "Error, garage mode session finish called without start");
            return;
        }

        mSessionFinishTime = mClock.millis();
        recordEvent(EVENT_SESSION_FINISH);
        cleanupRecorder();
    }

    /**
     * Save information about cancellation of the Garage mode.
     */
    public void cancelSession() {
        if (!isRecorderEnabled()) return;

        if (mSessionStartTime == 0) {
            Slogf.e(TAG, "Error, garage mode session cancel called without start");
            return;
        }

        mSessionFinishTime = mClock.millis();
        recordEvent(EVENT_SESSION_CANCELLED);
        cleanupRecorder();
    }

    private void cleanupRecorder() {
        mSessionStartTime = 0;
        mSessionFinishTime = 0;
    }

    // recording is not available on user builds
    @VisibleForTesting
    boolean isRecorderEnabled() {
        return !BuildHelper.isUserBuild();
    }

    private void writeToSessionFile(String buffer, boolean append) {
        StringWriter oldContents = new StringWriter();

        if (append) {
            readFileToWriter(new PrintWriter(oldContents));
        }

        try (FileOutputStream outStream = mGarageModeRecorderFile.startWrite()) {
            if (append) {
                outStream.write(oldContents.toString().getBytes(StandardCharsets.UTF_8));
            }

            outStream.write(buffer.getBytes(StandardCharsets.UTF_8));
            mGarageModeRecorderFile.finishWrite(outStream);
        } catch (IOException e) {
            Slogf.w(TAG, e, "Failed to write buffer of size %d", buffer.length());
        }
    }

    private void recordEvent(int event) {
        StringBuilder stringBuilder = new StringBuilder();
        boolean appendToFile = true;

        switch (event) {
            case EVENT_SESSION_START:
                appendToFile = false;
                stringBuilder.append(SESSION_START_TIME);
                stringBuilder.append(mDateFormat.format(new Date(mSessionStartTime)));
                stringBuilder.append('\n');
                break;
            case EVENT_SESSION_FINISH:
                stringBuilder.append(SESSION_FINISH_TIME);
                stringBuilder.append(mDateFormat.format(new Date(mSessionFinishTime)));
                stringBuilder.append('\n');
                stringBuilder.append(SESSION_DURATION);
                stringBuilder.append(mSessionFinishTime - mSessionStartTime);
                stringBuilder.append(TIME_UNIT_MS);
                stringBuilder.append('\n');
                break;
            case EVENT_SESSION_CANCELLED:
                stringBuilder.append(SESSION_WAS_CANCELLED);
                stringBuilder.append(mDateFormat.format(new Date(mSessionFinishTime)));
                stringBuilder.append('\n');
                break;
            default:
                break;
        }

        writeToSessionFile(stringBuilder.toString(), appendToFile);
    }

    private void readFileToWriter(PrintWriter writer) {
        if (!mGarageModeRecorderFile.getBaseFile().exists()) {
            Slogf.e(TAG, "GarageMode session file is not found %s",
                    mGarageModeRecorderFile.getBaseFile().getAbsolutePath());
            return; // nothing to write to dump
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(mGarageModeRecorderFile.openRead()))) {

            int lineCount = 0;
            while (reader.ready()) {
                writer.println(reader.readLine());
                lineCount++;
            }

            Slogf.d(TAG, "Read %d lines from GarageMode session file ", lineCount);
        } catch (IOException e) {
            Slogf.e(TAG, e, "Error reading GarageMode session file %s",
                    mGarageModeRecorderFile.getBaseFile().getAbsolutePath(), e);
        }
    }
}
