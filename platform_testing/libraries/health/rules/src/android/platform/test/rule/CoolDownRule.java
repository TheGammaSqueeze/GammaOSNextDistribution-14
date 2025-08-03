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
package android.platform.test.rule;

import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import com.android.helpers.ThermalHelper;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

import org.junit.runner.Description;

/**
 * This rule will cool DUT phone before a test case.
 *
 * <p>The rule takes a temperature name from command-line argument
 * "cooldown-device-temperature-name" to check temperature. The spaces in temperature name must be
 * replaced with '#' and '#' should be replaced with '##'. The rule unescapes argument value string
 * of temperature name accordingly.
 */
public class CoolDownRule extends TestWatcher {

    private static final String LOG_TAG = CoolDownRule.class.getSimpleName();

    // Interval in seconds, to poll for device temperature; defaults to 30s
    @VisibleForTesting static final String POLL_INTERVAL_OPTION = "cooldown-poll-interval";
    private long mPollIntervalSecs = 30;

    // Max wait time in seconds, for device cool down to target temperature; defaults to 20 minutes
    @VisibleForTesting static final String MAX_WAIT_OPTION = "cooldown-max-wait";
    private long mMaxWaitSecs = 60 * 20;

    // If test should be aborted if device is still above expected temperature; defaults to false
    @VisibleForTesting static final String ABORT_ON_TIMEOUT_OPTION = "cooldown-abort-on-timeout";
    private boolean mAbortOnTimeout = false;

    // Additional time to wait in seconds, after temperature has reached to target; defaults to 30s
    @VisibleForTesting static final String POST_IDLE_WAIT_OPTION = "cooldown-post-idle-wait";
    private long mPostIdleWaitSecs = 30;

    @VisibleForTesting
    static final String DEVICE_TEMPERATURE_NAME_OPTION = "cooldown-device-temperature-name";

    private String mDeviceTemperatureName = "";

    // Target Temperature that device should have; defaults to 35 degree Celsius
    @VisibleForTesting
    static final String TARGET_TEMPERATURE_OPTION = "cooldown-target-temperature";

    private int mTargetTemperature = 35;

    private ThermalHelper mThermalHelper;

    @Override
    protected void starting(Description description) {
        mDeviceTemperatureName =
                CoolDownRule.unescapeOptionStr(
                        getArguments().getString(DEVICE_TEMPERATURE_NAME_OPTION, ""));
        if (mDeviceTemperatureName.isEmpty()) {
            Log.w(LOG_TAG, "Missed device temperature name. Skipped waiting for DUT cooling down.");
            return;
        }
        mPollIntervalSecs = Long.valueOf(getArguments().getString(POLL_INTERVAL_OPTION, "30"));
        mMaxWaitSecs = Long.valueOf(getArguments().getString(MAX_WAIT_OPTION, "1200"));
        mAbortOnTimeout =
                Boolean.valueOf(getArguments().getString(ABORT_ON_TIMEOUT_OPTION, "false"));
        mPostIdleWaitSecs = Long.valueOf(getArguments().getString(POST_IDLE_WAIT_OPTION, "30"));
        mTargetTemperature =
                Integer.valueOf(getArguments().getString(TARGET_TEMPERATURE_OPTION, "35"));
        if (mTargetTemperature > (100) || mTargetTemperature <= 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid target target temperature: %d degree Celsius",
                            mTargetTemperature));
        }
        mThermalHelper = initThermalHelper();

        try {
            // Turn off the screen if necessary.
            final boolean screenOnOriginal = getUiDevice().isScreenOn();
            if (screenOnOriginal) {
                getUiDevice().sleep();
            }

            waitTemperature();

            // Turn on the screen if necessary.
            if (screenOnOriginal && !getUiDevice().isScreenOn()) {
                getUiDevice().wakeUp();
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Could not cool down device in time.", e);
        }
    }

    @VisibleForTesting
    ThermalHelper initThermalHelper() {
        return new ThermalHelper();
    }

    private void waitTemperature() {
        long start = System.currentTimeMillis();
        long maxWaitMs = mMaxWaitSecs * 1000;
        long intervalMs = mPollIntervalSecs * 1000;
        int deviceTemperature = getDeviceTemperature(mDeviceTemperatureName);

        while ((deviceTemperature > mTargetTemperature) && (elapsedMs(start) < maxWaitMs)) {
            Log.i(
                    LOG_TAG,
                    String.format(
                            "Temperature is still high actual %d/expected %d",
                            deviceTemperature, mTargetTemperature));
            sleepMs(intervalMs);
            deviceTemperature = getDeviceTemperature(mDeviceTemperatureName);
        }

        if (deviceTemperature <= mTargetTemperature) {
            Log.i(
                    LOG_TAG,
                    String.format(
                            "Total time elapsed to get to %dc : %ds",
                            mTargetTemperature, (System.currentTimeMillis() - start) / 1000));
        } else {
            Log.w(
                    LOG_TAG,
                    String.format(
                            "Temperature is still high, actual %d/expected %d; waiting after %ds",
                            deviceTemperature,
                            mTargetTemperature,
                            (System.currentTimeMillis() - start) / 1000));
            if (mAbortOnTimeout) {
                throw new IllegalStateException(
                        String.format(
                                "Temperature is still high after wait timeout; actual %d/expected"
                                        + " %d",
                                deviceTemperature, mTargetTemperature));
            }
        }

        // Extra idle time after reaching the target to stabilize the system
        sleepMs(mPostIdleWaitSecs * 1000);
        Log.i(
                LOG_TAG,
                String.format(
                        "Done waiting, total time elapsed: %ds",
                        (System.currentTimeMillis() - start) / 1000));
    }

    @VisibleForTesting
    void sleepMs(long milliSeconds) {
        SystemClock.sleep(milliSeconds);
    }

    @VisibleForTesting
    long elapsedMs(long start) {
        return System.currentTimeMillis() - start;
    }

    /**
     * @param name : temperature name in need.
     * @return Device temperature in unit of millidegree Celsius
     */
    @VisibleForTesting
    int getDeviceTemperature(String name) {
        return (int) mThermalHelper.getTemperature(name);
    }

    /**
     * @param input : Option value string to be unescaped. Option value string should be escaped by
     *     replacing ' ' (space) with '#' and '#' with '##'. Not support to unescaped consecutive
     *     spaces like ' ' and space before '#' like ' #'.
     * @return Unescaped string.
     */
    @VisibleForTesting
    static String unescapeOptionStr(String input) {
        final StringBuilder result = new StringBuilder();
        final StringCharacterIterator iterator = new StringCharacterIterator(input);
        final char escape_char = '#';
        final char space_char = ' ';
        char character = iterator.current();
        boolean escapeFlag = false;

        while (character != CharacterIterator.DONE) {
            if (character == space_char) {
                throw new IllegalArgumentException(
                        "Non-escaped option value string (please replace ' ' to '#'; '#' to '##'): "
                                + input);
            } else if (escapeFlag) {
                if (character == escape_char) {
                    result.append(escape_char);
                } else {
                    result.append(space_char);
                    result.append(character);
                }
                escapeFlag = false;
            } else if (character == escape_char) {
                escapeFlag = true;
            } else {
                result.append(character);
            }
            character = iterator.next();
        }
        if (escapeFlag) {
            result.append(space_char);
        }
        return result.toString();
    }
}
