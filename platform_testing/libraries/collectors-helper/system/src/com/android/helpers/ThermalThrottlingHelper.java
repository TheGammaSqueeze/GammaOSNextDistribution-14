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
package com.android.helpers;

import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;

/** Helper for starting/stopping emulated thermal throttling in the CPU/GPU. */
public class ThermalThrottlingHelper {
    private static final String TAG = ThermalThrottlingHelper.class.getSimpleName();

    private static final String THROTTLING_DUMPSYS_CMD =
            "dumpsys android.hardware.thermal.IThermal/default";

    // max_throttling indicates that the emulated throttling will be the maximum possible given the
    // input severity level.
    private static final String THROTTLING_START_CMD =
            THROTTLING_DUMPSYS_CMD + " emul_severity VIRTUAL-SKIN %d max_throttling";
    private static final String THROTTLING_STOP_CMD =
            THROTTLING_DUMPSYS_CMD + " emul_clear VIRTUAL-SKIN";

    private static final int THROTTLING_STATE_POLL_COUNT = 3;
    private static final int THROTTLING_STATE_POLL_INTERVAL = 1000;

    private UiDevice mUiDevice;

    /** Starts emulating throttling at the given severity. */
    public boolean startThrottling(int emulSeverity) {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Log.i(TAG, "Starting emulating thermal throttling at level " + emulSeverity);
        try {
            mUiDevice.executeShellCommand(String.format(THROTTLING_START_CMD, emulSeverity));
        } catch (IOException e) {
            Log.e(TAG, "Failed to start emulating throttling: " + e.getMessage());
            return false;
        }

        int waitCount = 0;
        while (!isThrottlingEnabled()) {
            if (waitCount < THROTTLING_STATE_POLL_COUNT) {
                SystemClock.sleep(THROTTLING_STATE_POLL_INTERVAL);
                waitCount++;
                continue;
            }
            Log.e(TAG, "Failed to start emulating thermal throttling.");
            return false;
        }
        Log.i(TAG, "Successfully started emulating throttling.");
        return true;
    }

    /** Stops thermal throttling emulation. */
    public boolean stopThrottling() {
        if (!isThrottlingEnabled()) {
            Log.w(TAG, "stopThrottling() was called, but thermal throttling is not enabled.");
            return true;
        }
        try {
            mUiDevice.executeShellCommand(THROTTLING_STOP_CMD);
        } catch (IOException e) {
            Log.e(TAG, "Failed to stop emulating throttling: " + e);
            return false;
        }

        int waitCount = 0;
        while (isThrottlingEnabled()) {
            if (waitCount < THROTTLING_STATE_POLL_COUNT) {
                SystemClock.sleep(THROTTLING_STATE_POLL_INTERVAL);
                waitCount++;
                continue;
            }
            Log.e(TAG, "Throttling still ongoing; failed to stop emulating thermal throttling.");
            return false;
        }
        Log.i(TAG, "Successfully stopped emulating throttling.");
        return true;
    }

    private boolean isThrottlingEnabled() {
        try {
            String[] lines = mUiDevice.executeShellCommand(THROTTLING_DUMPSYS_CMD).split("\n");
            for (String line : lines) {
                // This line will only exist if thermal throttling is being emulated.
                if (line.contains("EmulTemp")) {
                    return true;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to check throttling status: " + e.getMessage());
        }
        return false;
    }
}
