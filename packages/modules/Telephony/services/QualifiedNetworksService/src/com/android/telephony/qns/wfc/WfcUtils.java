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
package com.android.telephony.qns.wfc;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsMmTelManager;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.VisibleForTesting;

public final class WfcUtils {
    private static final String TAG = WfcActivationActivity.TAG;

    // Constants shared by WifiCallingSettings
    static final String EXTRA_LAUNCH_CARRIER_APP = "EXTRA_LAUNCH_CARRIER_APP";
    static final int LAUNCH_APP_ACTIVATE = 0;
    static final int LAUNCH_APP_UPDATE = 1;

    // OK to suppress warnings here because it's used only for unit tests
    @SuppressLint("StaticFieldLeak")
    private static WfcActivationHelper mWfcActivationHelper;
    private static ActivityResultLauncher mWebViewResultsLauncher;

    private WfcUtils() {}

    /**
     * Returns {@code true} if the app is launched for WFC activation; {@code false} for emergency
     * address update or displaying terms & conditions.
     */
    public static boolean isActivationFlow(Intent intent) {
        int intention = getLaunchIntention(intent);
        Log.d(TAG, "Start Activity intention : " + intention);
        return intention == LAUNCH_APP_ACTIVATE;
    }

    /** Returns the launch intention extra in the {@code intent}. */
    public static int getLaunchIntention(Intent intent) {
        if (intent == null) {
            return LAUNCH_APP_ACTIVATE;
        }

        return intent.getIntExtra(EXTRA_LAUNCH_CARRIER_APP, LAUNCH_APP_ACTIVATE);
    }

    /** Returns the subscription id of starting the WFC activation activity. */
    public static int getSubId(Intent intent) {
        if (intent == null) {
            return SubscriptionManager.getDefaultDataSubscriptionId();
        }
        int subId =
                intent.getIntExtra(
                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.getDefaultDataSubscriptionId());
        Log.d(TAG, "Start Activity with subId : " + subId);
        return subId;
    }

    /**
     * Returns {@link ImsMmTelManager} with specific subscription id. Returns {@code null} if
     * provided subscription id invalid.
     */
    @Nullable
    public static ImsMmTelManager getImsMmTelManager(int subId) {
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            try {
                return ImsMmTelManager.createForSubscriptionId(subId);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Can't get ImsMmTelManager, IllegalArgumentException: subId = " + subId);
            }
        }
        return null;
    }

    /**
     * Dependency providers.
     *
     * <p>In normal case, setters are not invoked, hence getters return null. The component is
     * supposed to do null check and initialize dependencies by itself. In tests, setters can be
     * invoked to provide mock dependencies.
     */
    @VisibleForTesting
    public static void setWfcActivationHelper(WfcActivationHelper obj) {
        mWfcActivationHelper = obj;
    }

    @VisibleForTesting
    public static void setWebviewResultLauncher(ActivityResultLauncher obj) {
        mWebViewResultsLauncher = obj;
    }

    @Nullable
    public static WfcActivationHelper getWfcActivationHelper() {
        return mWfcActivationHelper;
    }

    @Nullable
    public static ActivityResultLauncher getWebviewResultLauncher() {
        return mWebViewResultsLauncher;
    }
}
