/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.platform.helpers;

/**
 * An App Helper interface for the Quick Settings bar.
 *
 * @deprecated use {@link android.system.helpers.QuickSettingsHelper} instead.
 */
@Deprecated
public interface IQuickSettingsHelper extends IAppHelper {
    /**
     * Represents the state of a Quick Setting. Currently this is limited to ON and OFF states;
     * however, other states will be added in the future, for example to differentiate between
     * paired and un-paired, active bluetooth states.
     *
     * @deprecated use {@link android.system.helpers.QuickSettingsHelper} instead.
     */
    @Deprecated
    public enum State {
        ON,
        OFF,
        UNAVAILABLE,
    }

    /**
     * Represents a Quick Setting switch that can be toggled ON and OFF during a test.
     *
     * @deprecated use {@link android.system.helpers.QuickSettingsHelper} instead.
     */
    @Deprecated
    public enum Setting {
        AIRPLANE("Airplane", "airplane", 2000),
        AUTO_ROTATE("Auto-rotate", "rotation", 2000),
        BLUETOOTH("Bluetooth", "bt", 15000),
        DO_NOT_DISTURB("Do Not Disturb", "dnd", 2000),
        FLASHLIGHT("Flashlight", "flashlight", 5000),
        NIGHT_LIGHT("Night Light", "night", 2000);

        private final String mContentDescSubstring;
        private final String mTileName;
        private final long mExpectedWait;

        Setting(String substring, String tileName, long wait) {
            mContentDescSubstring = substring;
            mTileName = tileName;
            mExpectedWait = wait;
        }

        /** Returns a substring to identify the {@code Setting} by content description. */
        public String getContentDescSubstring() {
            return mContentDescSubstring;
        }

        /** Returns a substring to identify the {@code Setting} by content description. */
        public String getTileName() {
            return mTileName;
        }

        /** Returns the longest expected wait time for this option to be toggled ON or OFF. */
        public long getExpectedWait() {
            return mExpectedWait;
        }
    }

    /**
     * Toggles a {@link Setting} either {@link State.ON} or {@link State.OFF}. If {@code setting} is
     * already found to be in {@code state}, then no operation is performed. There are no setup
     * requirements to call this method, except that {@code setting} is available from the test and
     * in the Quick Settings menu.
     *
     * @param setting The setting defined in enum {@link Setting}
     * @param tileName The name of tile spec which recognized by quick settings host
     * @param state The state of specific setting
     * @deprecated use {@link android.system.helpers.QuickSettingsHelper} instead.
     */
    @Deprecated
    public void toggleSetting(Setting setting, String tileName, State state);
}
