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

package com.android.server.uwb;

import android.util.Log;

import static com.android.server.uwb.UwbSettingsStore.SETTINGS_LOG_MODE;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provide functions for setting Log mode for capturing UCI messages as packets.
 * Log mode defaults to disabled if was never set. However, log mode setting persists after reboot.
 * The log mode parameter is sent to native stack through JNI where logging is implemented.
 */
public class UciLogModeStore {
    private static final String TAG = "UciLogModeStore";

    /**
     * ModeName represents the log mode as a string.
     */
    public enum Mode {
        DISABLED("disabled"),
        FILTERED("filtered"),
        UNFILTERED("unfiltered");

        private final String mMode;

        Mode(String mode) {
            this.mMode = mode;
        }

        public String getMode() {
            return mMode;
        }

        /**
         * Attempts to parse a string to corresponding LogMode
         * @param modeNameStr is one of Disabled, Filtered, or Unfiltered (case insensitive).
         * @return enum ModeName if successful, empty if failed.
         */
        public static Optional<Mode> fromName(String modeNameStr) {
            return Stream.of(values())
                    .filter(mode -> mode.getMode().equals(
                            modeNameStr.toLowerCase(Locale.US)))
                    .findFirst();
        }
    }

    private Mode mLogMode;
    private final UwbSettingsStore mUwbSettingsStore;

    public UciLogModeStore(UwbSettingsStore uwbSettingsStore) {
        mUwbSettingsStore = uwbSettingsStore;
        mLogMode = Mode.FILTERED;
    }

    /**
     * Initialize the module.
     */
    public void initialize() {
        Optional<Mode> logModeOption = Mode.fromName(
                mUwbSettingsStore.get(SETTINGS_LOG_MODE));
        if (logModeOption.isPresent()) {
            mLogMode = logModeOption.get();
        }
    }

    /**
     * Is this a valid log mode
     *
     * @param logMode is one of Disabled, Filtered, or Unfiltered (case insensitive).
     * @return true if the logMode is valid, false otherwise.
     */
    public static boolean isValid(String logMode) {
        return Mode.fromName(logMode).isPresent();
    }

    /**
     * Sets the log mode for current session, and store for future UWB UCI messages.
     *
     * @param modeStr is one of Disabled, Filtered, or Unfiltered (case insensitive).
     * @return true if the log mode is set successfully, false otherwise.
     */
    public boolean storeMode(String modeStr) {
        Optional<Mode> logModeOption = Mode.fromName(modeStr);
        if (logModeOption.isPresent()) {
            mLogMode = logModeOption.get();
            mUwbSettingsStore.put(SETTINGS_LOG_MODE, mLogMode.getMode());
            Log.d(TAG, " set UCI log mode to " + mLogMode.getMode());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Gets the log mode.
     *
     * @return the log mode as a String
     */
    public String getMode() {
        return mLogMode.getMode();
    }
}
