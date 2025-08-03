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

package android.adservices.rootcts;

import android.util.Log;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

class DeviceTimeRule implements TestRule {

    private static final String TAG = "DeviceTimeRule";
    public static final int HOURS_TO_ADD = 25;
    private boolean mIsAlreadyOverriding = false;

    void overrideDeviceTimeToPlus25Hours() {
        if (mIsAlreadyOverriding) {
            throw new IllegalStateException("Device time already being overridden.");
        }
        Log.d(TAG, "Overriding device time to +25 hours.");
        overrideDeviceTime(Instant.now().plus(HOURS_TO_ADD, ChronoUnit.HOURS).getEpochSecond());
        mIsAlreadyOverriding = true;
    }

    private static void overrideDeviceTime(long secondsSinceUnixEpoch) {
        Log.d(TAG, String.format("Override device time to %ss.", secondsSinceUnixEpoch));
        ShellUtils.runShellCommand(String.format("date -s @%s", secondsSinceUnixEpoch));
        ShellUtils.runShellCommand("settings put global auto_time 0");
        ShellUtils.runShellCommand("am broadcast -a android.intent.action.TIME_SET");
        Log.d(TAG, "Device time now set to " + ShellUtils.runShellCommand("date"));
    }

    private static void resetDeviceTime() {
        Log.d(TAG, "Reset device time to automatic.");
        ShellUtils.runShellCommand("settings put global auto_time 1");
        ShellUtils.runShellCommand("am broadcast -a android.intent.action.TIME_SET");
        Log.d(TAG, "Device time now set to " + ShellUtils.runShellCommand("date"));
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    resetDeviceTime();
                    mIsAlreadyOverriding = false;
                }
            }
        };
    }
}
