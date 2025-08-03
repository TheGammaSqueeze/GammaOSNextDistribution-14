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
package com.android.telephony.imsmedia.config;

import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.SparseArray;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import com.android.telephony.imsmedia.JNIImsMediaService;
import com.android.telephony.imsmedia.R;
import com.android.telephony.imsmedia.util.Log;

/**
 * The configuration of logging and test options for the libimsmedia
 */
public class ConfigPreference extends PreferenceActivity {
    private static final String LOG_TAG = "ConfigPreference";
    private static final String PREFERENCE_ACTION = "com.android.telephony.imsmedia.PREFERENCE";
    private static final String KEY_LOG_MODE = "list_log_level";
    private static final String KEY_DEBUG_LOG_MODE_SOCKET = "log_mode_socket";
    private static final String KEY_DEBUG_LOG_MODE_AUDIO = "log_mode_audio";
    private static final String KEY_DEBUG_LOG_MODE_VIDEO = "log_mode_video";
    private static final String KEY_DEBUG_LOG_MODE_TEXT = "log_mode_text";
    private static final String KEY_DEBUG_LOG_MODE_RTP = "log_mode_rtp";
    private static final String KEY_DEBUG_LOG_MODE_PAYLOAD = "log_mode_payload";
    private static final String KEY_DEBUG_LOG_MODE_JITTER = "log_mode_jitterbuffer";
    private static final String KEY_DEBUG_LOG_MODE_RTCP = "log_mode_rtcp";
    private static final String KEY_DEBUG_LOG_MODE_RTPSTACK = "log_mode_rtpstack";
    private static final String KEY_DEBUG_LOG_MODE_VIDEO_JITTER = "log_mode_video_jitter";

    private static final int DEBUG_LOG_MODE_SOCKET = 1 << 0;
    private static final int DEBUG_LOG_MODE_AUDIO = 1 << 1;
    private static final int DEBUG_LOG_MODE_VIDEO = 1 << 2;
    private static final int DEBUG_LOG_MODE_TEXT = 1 << 3;
    private static final int DEBUG_LOG_MODE_RTP = 1 << 4;
    private static final int DEBUG_LOG_MODE_PAYLOAD = 1 << 5;
    private static final int DEBUG_LOG_MODE_JITTER = 1 << 6;
    private static final int DEBUG_LOG_MODE_RTCP = 1 << 7;
    private static final int DEBUG_LOG_MODE_RTPSTACK = 1 << 8;
    private static final int DEBUG_LOG_MODE_VIDEO_JITTER = 1 << 9;

    private static final String[] KEY_LIST_PREFERENCES = {
        KEY_LOG_MODE
    };

    private static final String[] KEY_CHECKBOX_PREFERENCES = {
        KEY_DEBUG_LOG_MODE_SOCKET,
        KEY_DEBUG_LOG_MODE_AUDIO,
        KEY_DEBUG_LOG_MODE_VIDEO,
        KEY_DEBUG_LOG_MODE_TEXT,
        KEY_DEBUG_LOG_MODE_RTP,
        KEY_DEBUG_LOG_MODE_PAYLOAD,
        KEY_DEBUG_LOG_MODE_JITTER,
        KEY_DEBUG_LOG_MODE_RTCP,
        KEY_DEBUG_LOG_MODE_RTPSTACK,
        KEY_DEBUG_LOG_MODE_VIDEO_JITTER
    };

    private static final int[] DEBUG_MODE_ARRAY = {
        DEBUG_LOG_MODE_SOCKET,
        DEBUG_LOG_MODE_AUDIO,
        DEBUG_LOG_MODE_VIDEO,
        DEBUG_LOG_MODE_TEXT,
        DEBUG_LOG_MODE_RTP,
        DEBUG_LOG_MODE_PAYLOAD,
        DEBUG_LOG_MODE_JITTER,
        DEBUG_LOG_MODE_RTCP,
        DEBUG_LOG_MODE_RTPSTACK,
        DEBUG_LOG_MODE_VIDEO_JITTER
    };

    private final SparseArray<ListPreference> mListPrefs =
            new SparseArray<>(KEY_LIST_PREFERENCES.length);
    private final SparseArray<CheckBoxPreference> mCheckboxPrefs =
            new SparseArray<>(KEY_CHECKBOX_PREFERENCES.length);
    private int mLogMode = 0;
    private int mDebugLogMode = 0;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.dc(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);

        String action = getIntent().getAction();

        // The Configuration Menu is blocked for production builds
        // and access in debug/eng builds is restricted to preference action
        if (!Build.IS_DEBUGGABLE || !(action != null && action.equals(PREFERENCE_ACTION))) {
            Log.e(LOG_TAG, "Configuration Menu cannot be launched");
            finish(); // Close the current activity
            return;
        }

        Window wd = getWindow();

        if (wd != null) {
            LayoutParams layoutParams = wd.getAttributes();
            wd.setAttributes(layoutParams);
        }

        addPreferencesFromResource(R.xml.config_preference);
        initPreferences();
    }

    @Override
    public void onPause() {
        super.onPause();
        mListPrefs.clear();
        mCheckboxPrefs.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.dc(LOG_TAG, "onResume");
        initPreferences();
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ConfigListItemChangeListener.class.getName().equals(fragmentName)
                || CheckBoxItemChangeListener.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }

    private void initPreferences() {
        Log.dc(LOG_TAG, "initPreferences");
        for (int i = 0; i < KEY_LIST_PREFERENCES.length; ++i) {
            Log.dc(LOG_TAG, "initPreferences, key=" + KEY_LIST_PREFERENCES[i]);
            ListPreference itemList = (ListPreference) findPreference(KEY_LIST_PREFERENCES[i]);
            mListPrefs.put(i, itemList);
            if (itemList != null) {
                itemList.setOnPreferenceChangeListener(new ConfigListItemChangeListener(i));
                if (KEY_LIST_PREFERENCES[i].equals(KEY_LOG_MODE)) {
                    mLogMode = parseInt(itemList.getValue(), 0);
                }
            }
        }

        for (int i = 0; i < KEY_CHECKBOX_PREFERENCES.length; ++i) {
            Log.dc(LOG_TAG, "initPreferences, key=" + KEY_CHECKBOX_PREFERENCES[i]);
            CheckBoxPreference check =
                    (CheckBoxPreference) findPreference(KEY_CHECKBOX_PREFERENCES[i]);
            mCheckboxPrefs.put(i, check);
            if (check != null) {
                check.setOnPreferenceChangeListener(new CheckBoxItemChangeListener(i));
                if (check.isChecked()) {
                    mDebugLogMode |= DEBUG_MODE_ARRAY[i];
                } else {
                    mDebugLogMode &= ~DEBUG_MODE_ARRAY[i];
                }
            }
        }

        Log.dc(LOG_TAG, "initPreferences, LogMode=" + mLogMode + ", DebugLogMode=" + mDebugLogMode);
        JNIImsMediaService.setLogMode(mLogMode, mDebugLogMode);
        Log.init(mLogMode);
    }

    private final class ConfigListItemChangeListener
            implements Preference.OnPreferenceChangeListener {
        private int mPrefIndex;
        ConfigListItemChangeListener(int index) {
            mPrefIndex = index;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String value = newValue.toString();
            Log.dc(LOG_TAG, "onPreferenceChange: key=" + preference.getKey() + ",value=" + value);
            ListPreference itemList = mListPrefs.valueAt(mPrefIndex);
            if (itemList != null) {
                mLogMode = parseInt(value, 0);
                itemList.setSummary(value);
                JNIImsMediaService.setLogMode(mLogMode, mDebugLogMode);
                Log.setLogLevel(mLogMode);
            }
            return true;
        }
    }

    private final class CheckBoxItemChangeListener
            implements Preference.OnPreferenceChangeListener {
        private int mPrefIndex;
        CheckBoxItemChangeListener(int index) {
            mPrefIndex = index;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String value = newValue.toString();
            Log.dc(LOG_TAG, "onPreferenceChange: key=" + preference.getKey() + ", value=" + value);
            boolean boolValue = Boolean.valueOf(value);
            if (boolValue) {
                mDebugLogMode |= DEBUG_MODE_ARRAY[mPrefIndex];
            } else {
                mDebugLogMode &= ~DEBUG_MODE_ARRAY[mPrefIndex];
            }
            JNIImsMediaService.setLogMode(mLogMode, mDebugLogMode);
            Log.setLogLevel(mLogMode);
            return true;
        }
    }

    private static int parseInt(String value, int defaultValue) {
        int intValue = defaultValue;
        try {
            intValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Log.e(LOG_TAG, "NumberFormatException: " + e.toString());
        }
        return intValue;
    }
}
