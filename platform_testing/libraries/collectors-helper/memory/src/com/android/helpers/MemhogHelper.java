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
package com.android.helpers;

import android.os.SystemClock;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.util.Locale;

/** MemhogHelper is a helper for starting and stopping a memhog process. */
public class MemhogHelper {
    private static final String TAG = MemhogHelper.class.getSimpleName();
    // Command to make memhog executable.
    private static final String CHMOD_CMD = "chmod 755 %s";
    // Location of memhog on the device, defined in heavyweight-memhog.gcl.
    private static final String MEMHOG_FILE_PATH = "/data/local/tmp/memhog";
    private static final String MEMHOG_PROC_ID_CMD = "pidof memhog";
    private static final String MEMHOG_START_CMD =
            "%s -m %d -s -1 -M -r 1 </dev/null &>/dev/null &";
    private static final String MEMHOG_STOP_CMD = "pkill memhog";

    private static final int MEMHOG_START_RETRY_COUNT = 3;
    private static final int MEMHOG_START_RETRY_TIME_MS = 1000;

    private static final int MEMHOG_STOP_RETRY_COUNT = 5;
    private static final int MEMHOG_STOP_RETRY_TIME_MS = 1000;

    private UiDevice mUiDevice;

    // Starts memhog with the specified amount of memory in bytes.
    public boolean startMemhog(long memorySizeBytes) {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Log.i(TAG, "Starting memhog.");
        new Thread() {
            @Override
            public void run() {
                UiDevice uiDevice =
                        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
                try {
                    // Makes memhog executable, then runs it.
                    uiDevice.executeShellCommand(String.format(CHMOD_CMD, MEMHOG_FILE_PATH));
                    uiDevice.executeShellCommand(
                            String.format(
                                    Locale.US,
                                    MEMHOG_START_CMD,
                                    MEMHOG_FILE_PATH,
                                    memorySizeBytes));
                } catch (IOException e) {
                    Log.e(TAG, "Failed to start memhog: " + e.getMessage());
                }
            }
        }.start();

        // Waits for the memhog process to be found.
        int waitCount = 0;
        while (!isMemhogRunning()) {
            if (waitCount < MEMHOG_START_RETRY_COUNT) {
                SystemClock.sleep(MEMHOG_START_RETRY_TIME_MS);
                waitCount++;
                continue;
            }
            Log.e(TAG, "Memhog process not found; failed to start memhog.");
            return false;
        }
        Log.i(TAG, "Successfully started memhog.");
        return true;
    }

    // Stops the memhog process with pkill.
    public boolean stopMemhog() {
        if (!isMemhogRunning()) {
            Log.e(TAG, "stopMemhog() was called, but memhog is not running.");
            return false;
        }
        try {
            mUiDevice.executeShellCommand(MEMHOG_STOP_CMD);
        } catch (IOException e) {
            Log.e(TAG, "Failed to stop memhog: " + e);
            return false;
        }

        // Waits for the memhog process to disappear.
        int waitCount = 0;
        while (isMemhogRunning()) {
            if (waitCount < MEMHOG_STOP_RETRY_COUNT) {
                SystemClock.sleep(MEMHOG_STOP_RETRY_TIME_MS);
                waitCount++;
                continue;
            }
            Log.e(TAG, "Memhog process still present; failed to stop memhog.");
            return false;
        }
        Log.i(TAG, "Successfully stopped memhog.");
        return true;
    }

    // Utility method for checking if memhog is running.
    private boolean isMemhogRunning() {
        try {
            String memhogProcId = mUiDevice.executeShellCommand(MEMHOG_PROC_ID_CMD);
            Log.i(TAG, String.format("Memhog pid - %s", memhogProcId));
            if (memhogProcId.isEmpty()) {
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to check memhog status: " + e.getMessage());
            return false;
        }
        return true;
    }
}
