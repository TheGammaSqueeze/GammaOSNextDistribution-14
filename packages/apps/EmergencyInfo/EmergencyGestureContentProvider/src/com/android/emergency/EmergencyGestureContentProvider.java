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

package com.android.emergency;


import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.EMERGENCY_GESTURE_CALL_NUMBER;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.EMERGENCY_GESTURE_UI_SHOWING_VALUE;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.EMERGENCY_NUMBER_OVERRIDE_AUTHORITY;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.EMERGENCY_SETTING_OFF;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.EMERGENCY_SETTING_ON;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.EMERGENCY_SETTING_VALUE;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.METHOD_NAME_GET_EMERGENCY_GESTURE_ENABLED;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.METHOD_NAME_GET_EMERGENCY_GESTURE_SOUND_ENABLED;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.METHOD_NAME_GET_EMERGENCY_NUMBER_OVERRIDE;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.METHOD_NAME_SET_EMERGENCY_GESTURE;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.METHOD_NAME_SET_EMERGENCY_GESTURE_UI_SHOWING;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.METHOD_NAME_SET_EMERGENCY_NUMBER_OVERRIDE;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.METHOD_NAME_SET_EMERGENCY_SOUND;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

/**
 * Content provider that gets/sets emergency number override for emergency gesture.
 */
public class EmergencyGestureContentProvider extends ContentProvider {
    private static final String TAG = "EmergencyNumberContentP";
    private static final boolean DEBUG = true;
    private static final String SHARED_PREFERENCES_NAME =
            "local_emergency_number_override_shared_pref";

    @Override
    public Bundle call(String authority, String method, String arg, Bundle extras) {
        Log.d(TAG, "calling pid/uid" + Binder.getCallingPid() + "/" + Binder.getCallingUid());
        final Bundle bundle = new Bundle();
        final SharedPreferences preferences = getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        switch (method) {
            case METHOD_NAME_GET_EMERGENCY_NUMBER_OVERRIDE:
                if (DEBUG) {
                    Log.d(TAG, METHOD_NAME_GET_EMERGENCY_NUMBER_OVERRIDE);
                }
                bundle.putString(EMERGENCY_GESTURE_CALL_NUMBER,
                        preferences.getString(EMERGENCY_GESTURE_CALL_NUMBER, null));
                break;
            case METHOD_NAME_SET_EMERGENCY_NUMBER_OVERRIDE:
                if (DEBUG) {
                    Log.d(TAG, METHOD_NAME_SET_EMERGENCY_NUMBER_OVERRIDE);
                }
                final String inputNumber = extras.getString(EMERGENCY_GESTURE_CALL_NUMBER);
                preferences.edit().putString(EMERGENCY_GESTURE_CALL_NUMBER, inputNumber).apply();
                getContext().getContentResolver().notifyChange(EMERGENCY_NUMBER_OVERRIDE_AUTHORITY,
                        null);
                break;
            case METHOD_NAME_SET_EMERGENCY_GESTURE:
                if (DEBUG) {
                    Log.d(TAG, METHOD_NAME_SET_EMERGENCY_GESTURE);
                }
                final int gestureSettingValue = extras.getInt(EMERGENCY_SETTING_VALUE);
                Settings.Secure.putInt(getContext().getContentResolver(),
                        Settings.Secure.EMERGENCY_GESTURE_ENABLED, gestureSettingValue);
                break;
            case METHOD_NAME_SET_EMERGENCY_GESTURE_UI_SHOWING:
                if (DEBUG) {
                    Log.d(TAG, METHOD_NAME_SET_EMERGENCY_GESTURE_UI_SHOWING);
                }
                long now = SystemClock.elapsedRealtime();
                final int gestureUiShowingValue = extras.getInt(
                        EMERGENCY_GESTURE_UI_SHOWING_VALUE);
                Settings.Secure.putInt(getContext().getContentResolver(),
                        Settings.Secure.EMERGENCY_GESTURE_UI_SHOWING, gestureUiShowingValue);
                if (gestureUiShowingValue != 0) {
                    Settings.Secure.putLong(getContext().getContentResolver(),
                            Settings.Secure.EMERGENCY_GESTURE_UI_LAST_STARTED_MILLIS, now);
                }
                break;
            case METHOD_NAME_SET_EMERGENCY_SOUND:
                if (DEBUG) {
                    Log.d(TAG, METHOD_NAME_SET_EMERGENCY_SOUND);
                }
                final int soundSettingValue = extras.getInt(EMERGENCY_SETTING_VALUE);
                Settings.Secure.putInt(getContext().getContentResolver(),
                        Settings.Secure.EMERGENCY_GESTURE_SOUND_ENABLED, soundSettingValue);
                break;
            case METHOD_NAME_GET_EMERGENCY_GESTURE_ENABLED:
                if (DEBUG) {
                    Log.d(TAG, METHOD_NAME_GET_EMERGENCY_GESTURE_ENABLED);
                }
                bundle.putInt(EMERGENCY_SETTING_VALUE,
                        Settings.Secure.getInt(getContext().getContentResolver(),
                                Settings.Secure.EMERGENCY_GESTURE_ENABLED,
                                isDefaultEmergencyGestureEnabled(getContext()) ?
                                        EMERGENCY_SETTING_ON : EMERGENCY_SETTING_OFF));
                break;
            case METHOD_NAME_GET_EMERGENCY_GESTURE_SOUND_ENABLED:
                if (DEBUG) {
                    Log.d(TAG, METHOD_NAME_GET_EMERGENCY_GESTURE_SOUND_ENABLED);
                }
                bundle.putInt(EMERGENCY_SETTING_VALUE,
                        Settings.Secure.getInt(getContext().getContentResolver(),
                                Settings.Secure.EMERGENCY_GESTURE_SOUND_ENABLED,
                                isDefaultEmergencyGestureSoundEnabled(getContext()) ?
                                        EMERGENCY_SETTING_ON : EMERGENCY_SETTING_OFF));
                break;
        }
        return bundle;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private boolean isDefaultEmergencyGestureEnabled(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_defaultEmergencyGestureEnabled);
    }

    private boolean isDefaultEmergencyGestureSoundEnabled(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_defaultEmergencyGestureSoundEnabled);
    }
}
