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

package com.android.imsserviceentitlement.fcm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.WorkerThread;

/** Stores FCM token. */
public final class FcmTokenStore {
    private static final String TAG = "IMSSE-FcmTokenStore";

    private static final String FCM_TOKEN_FILE = "FCM_TOKEN";
    private static final String FCM_TOKEN_KEY = "FCM_TOKEN_SUB_";

    private FcmTokenStore() {}

    /** Returns FCM token or empty string if not available. */
    public static String getToken(Context context, int subId) {
        return getFcmTokenFile(context).getString(FCM_TOKEN_KEY + subId, "");
    }

    /** Returns {@code true} if FCM token available. */
    public static boolean hasToken(Context context, int subId) {
        return !TextUtils.isEmpty(getToken(context, subId));
    }

    /** Saves the FCM token into data store. */
    @WorkerThread
    public static boolean setToken(Context context, int subId, String token) {
        if (!TextUtils.isEmpty(token)) {
            return getFcmTokenFile(context)
                    .edit()
                    .putString(FCM_TOKEN_KEY + subId, token)
                    .commit();
        } else {
            return getFcmTokenFile(context)
                    .edit()
                    .remove(FCM_TOKEN_KEY + subId)
                    .commit();
        }
    }

    /** Registers a listener for FCM token update. */
    public static void registerTokenUpdateListener(
            Context context, OnSharedPreferenceChangeListener listener) {
        Log.d(TAG, "registerTokenUpdateListener");
        // Since FCM_TOKEN_FILE only contains one item FCM_TOKEN_KEY, a change to FCM_TOKEN_FILE
        // means a change to FCM_TOKEN_KEY. The listener can ignore its arguments.
        getFcmTokenFile(context).registerOnSharedPreferenceChangeListener(listener);
    }

    /** Unregisters a listener for FCM token update. */
    public static void unregisterTokenUpdateListener(
            Context context, OnSharedPreferenceChangeListener listener) {
        Log.d(TAG, "unregisterTokenUpdateListener");
        // Since FCM_TOKEN_FILE only contains one item FCM_TOKEN_KEY, a change to FCM_TOKEN_FILE
        // means a change to FCM_TOKEN_KEY. The listener can ignore its arguments.
        getFcmTokenFile(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    private static SharedPreferences getFcmTokenFile(Context context) {
        return context.getSharedPreferences(FCM_TOKEN_FILE, Context.MODE_PRIVATE);
    }
}
