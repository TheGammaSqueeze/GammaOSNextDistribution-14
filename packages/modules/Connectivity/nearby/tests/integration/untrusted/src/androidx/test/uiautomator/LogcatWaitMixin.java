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

package androidx.test.uiautomator;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;

/** A helper class to wait the specific log appear in the logcat logs. */
public class LogcatWaitMixin extends WaitMixin<UiDevice> {

    private static final String LOG_TAG = LogcatWaitMixin.class.getSimpleName();

    public LogcatWaitMixin() {
        this(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()));
    }

    public LogcatWaitMixin(UiDevice device) {
        super(device);
    }

    /**
     * Waits the {@code specificLog} appear in the logcat logs after the specific {@code startTime}.
     *
     * @param waitTime the maximum time for waiting
     * @return true if the specific log appear within timeout and after the startTime
     */
    public boolean waitForSpecificLog(
            @NonNull String specificLog, @NonNull Date startTime, @NonNull Duration waitTime) {
        return wait(createWaitCondition(specificLog, startTime), waitTime.toMillis());
    }

    @NonNull
    Condition<UiDevice, Boolean> createWaitCondition(
            @NonNull String specificLog, @NonNull Date startTime) {
        return new Condition<UiDevice, Boolean>() {
            @Override
            public Boolean apply(UiDevice device) {
                String logcatLogs;
                try {
                    logcatLogs = device.executeShellCommand("logcat -v time -v year -d");
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Fail to dump logcat logs on the device!", e);
                    return Boolean.FALSE;
                }
                return !LogcatParser.INSTANCE
                        .findSpecificLogAfter(logcatLogs, specificLog, startTime)
                        .isEmpty();
            }
        };
    }
}
