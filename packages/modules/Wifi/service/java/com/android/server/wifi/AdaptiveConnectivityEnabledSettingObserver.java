/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Observer for adaptive connectivity enable settings changes.
 * This is enabled by default. Will be toggled off via adb command or a settings
 * toggle by the user to disable adaptive connectivity.
 */
public class AdaptiveConnectivityEnabledSettingObserver {

    private static final String TAG = "WifiAdaptConnObserver";
    /**
     * Copy of the settings string. Can't directly use the constant because it is @hide.
     * See {@link Settings.Secure#ADAPTIVE_CONNECTIVITY_ENABLED}.
     * TODO(b/167709538): remove this hardcoded string and create new API in Wifi mainline.
     */
    @VisibleForTesting
    public static final String SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED =
            "adaptive_connectivity_enabled";

    private final WifiMetrics mWifiMetrics;
    private final FrameworkFacade mFrameworkFacade;
    private final Context mContext;

    private final ContentObserver mContentObserver;

    private boolean mAdaptiveConnectivityEnabled = true;

    public AdaptiveConnectivityEnabledSettingObserver(Handler handler,
            WifiMetrics wifiMetrics, FrameworkFacade frameworkFacade,
            Context context) {
        mWifiMetrics = wifiMetrics;
        mFrameworkFacade = frameworkFacade;
        mContext = context;
        mContentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                handleChange();
            }
        };
    }

    private void handleChange() {
        mAdaptiveConnectivityEnabled = readValueFromSettings();
        Log.d(TAG, "Adaptive connectivity status changed: " + mAdaptiveConnectivityEnabled);
        mWifiMetrics.setAdaptiveConnectivityState(mAdaptiveConnectivityEnabled);
        mWifiMetrics.logUserActionEvent(
                WifiMetrics.convertAdaptiveConnectivityStateToUserActionEventType(
                        mAdaptiveConnectivityEnabled));
    }

    /** Register settings change observer. */
    public void initialize() {
        Uri uri = Settings.Secure.getUriFor(SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED);
        if (uri == null) {
            Log.e(TAG, "Adaptive connectivity user toggle does not exist in Settings");
            return;
        }
        mFrameworkFacade.registerContentObserver(
                mContext, uri, true, mContentObserver);
        mAdaptiveConnectivityEnabled = readValueFromSettings();
        Log.d(TAG, "Adaptive connectivity status initialized to: " + mAdaptiveConnectivityEnabled);
        mWifiMetrics.setAdaptiveConnectivityState(mAdaptiveConnectivityEnabled);
    }

    private boolean readValueFromSettings() {
        return mFrameworkFacade.getSecureIntegerSetting(
                mContext, SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED, 1) == 1;
    }

    /** True if adaptive connectivity is enabled, false otherwise. */
    public boolean get() {
        return mAdaptiveConnectivityEnabled;
    }

    /** Dump method for debugging */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of AdaptiveConnectivityEnabledSettingObserver");
        pw.println("mAdaptiveConnectivityEnabled=" + mAdaptiveConnectivityEnabled);
    }
}
