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

package com.android.tv.settings.library;

import static com.android.tv.settings.library.PreferenceCompat.STATUS_OFF;
import static com.android.tv.settings.library.PreferenceCompat.STATUS_ON;

import android.annotation.SystemApi;

/**
 * @hide Provide constants and utility methods.
 */
@SystemApi
public final class ManagerUtil {
    public static final int OFFSET_MULTIPLIER = 100000;
    public static final int STATE_EMPTY = -1;
    public static final int STATE_NETWORK = 0;
    public static final int STATE_WIFI_DETAILS = 1;
    public static final int STATE_DEVICE_MAIN = 2;
    public static final int STATE_APPS = 3;
    public static final int STATE_ALL_APPS = 4;
    public static final int STATE_APP_MANAGEMENT = 5;
    public static final int STATE_SPECIAL_ACCESS = 6;
    public static final int STATE_HIGH_POWER = 7;
    public static final int STATE_NOTIFICATION_ACCESS = 8;
    public static final int STATE_APP_USAGE_ACCESS = 9;
    public static final int STATE_SYSTEM_ALERT_WINDOW = 10;
    public static final int STATE_WRITE_SETTINGS = 11;
    public static final int STATE_PICTURE_IN_PICTURE = 12;
    public static final int STATE_ALARMS_AND_REMINDERS = 13;
    public static final int STATE_EXTERNAL_SOURCES = 14;
    public static final int STATE_SYSTEM_DATE_TIME = 15;
    public static final int STATE_SYSTEM_ABOUT = 16;
    public static final int STATE_KEYBOARD = 17;
    public static final int STATE_AVAILABLE_KEYBOARD = 18;
    public static final int STATE_AUTO_FILL_PICKER_STATE = 19;
    public static final int STATE_LANGUAGE = 20;
    public static final int STATE_ACCESSIBILITY = 21;
    public static final int STATE_ACCESSIBILITY_SERVICE = 22;
    public static final int STATE_ACCESSIBILITY_SHORTCUT = 23;
    public static final int STATE_ACCESSIBILITY_SHORTCUT_SERVICE = 24;
    public static final int STATE_STORAGE = 25;
    public static final int STATE_STORAGE_SUMMARY = 26;
    public static final int STATE_MISSING_STORAGE = 27;
    public static final int STATE_POWER_AND_ENERGY = 28;
    public static final int STATE_ENERGY_SAVER = 29;
    public static final int STATE_DAYDREAM = 30;
    public static final int STATE_DISPLAY_SOUND = 31;
    public static final int STATE_FONT_SCALE = 32;
    public static final int STATE_MATCH_CONTENT_FRAME = 33;
    public static final int STATE_ADVANCED_DISPLAY = 34;
    public static final int STATE_ADVANCED_VOLUME = 35;
    public static final int STATE_HDR_FORMAT_SELECTION = 36;
    public static final int STATE_LOCATION = 37;
    public static final int STATE_SENSOR = 38;
    public static final int STATE_PRIVACY = 39;
    public static final int STATE_RESOLUTION_SELECTION = 40;
    public static final int STATE_DEVELOPMENT = 41;
    public static final int STATE_LEGAL = 42;
    public static final String KEY_KEYBOARD_SETTINGS = "autofillSettings";
    public static final String INFO_INTENT = "intent";
    public static final String INFO_WIFI_SIGNAL_LEVEL = "wifi_signal_level";
    /** Argument key containing the current font scale value. */
    public static final String INFO_CURRENT_FONT_SCALE_VALUE = "current_font_scale_value";
    /** Argument key containing the font scale value this fragment will preview. */
    public static final String INFO_PREVIEW_FONT_SCALE_VALUE = "preview_font_scale_value";

    private ManagerUtil() {
    }

    static byte getChecked(boolean checked) {
        return checked ? STATUS_ON : STATUS_OFF;
    }

    static byte getSelectable(boolean selectable) {
        return selectable ? STATUS_ON : STATUS_OFF;
    }

    static byte getVisible(boolean visible) {
        return visible ? STATUS_ON : STATUS_OFF;
    }

    static byte getEnabled(boolean enabled) {
        return enabled ? STATUS_ON : STATUS_OFF;
    }

    static byte getPersistent(boolean persistent) {
        return persistent ? STATUS_ON : STATUS_OFF;
    }


    /**
     * @hide Return whether the preference is checked.
     * 0 : not updated, 1 : unchecked, 2 : checked
     */
    @SystemApi
    public static boolean isChecked(PreferenceCompat pref) {
        return pref.getChecked() == STATUS_ON;
    }

    /**
     * @hide Return whether the preference is visible.
     * 0 : not updated, 1 : invisible, 2 : visible
     */
    @SystemApi
    public static boolean isVisible(PreferenceCompat pref) {
        return pref.getVisible() == STATUS_OFF;
    }

    /**
     * @param state       state identifier
     * @param requestCode requestCode
     * @return compound code
     * @hide Calculate the compound code based on the state identifier and request code.
     */
    @SystemApi
    public static int calculateCompoundCode(int state, int requestCode) {
        return OFFSET_MULTIPLIER * (state + 1) + requestCode;
    }

    /**
     * @param code compound code
     * @return state identifier
     * @hide Get the state identifier based on the compound code.
     */
    @SystemApi
    public static int getStateIdentifier(int code) {
        if (code < OFFSET_MULTIPLIER) {
            return -1;
        }
        return code / OFFSET_MULTIPLIER - 1;
    }

    /**
     * @param code compound code
     * @return request code
     * @hide Return the request code for a particular state.
     */
    @SystemApi
    public static int getRequestCode(int code) {
        return code % OFFSET_MULTIPLIER;
    }
}
