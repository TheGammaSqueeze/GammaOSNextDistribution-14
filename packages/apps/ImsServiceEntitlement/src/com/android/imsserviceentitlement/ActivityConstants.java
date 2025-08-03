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

package com.android.imsserviceentitlement;

import android.content.Intent;
import android.telephony.SubscriptionManager;
import android.util.Log;

/**
 * Constants shared by framework to start WFC activation activity.
 *
 * <p>Must match with WifiCallingSettings.
 */
public final class ActivityConstants {
    public static final String TAG = "IMSSE-ActivityConstants";

    /** Constants shared by WifiCallingSettings */
    public static final String EXTRA_LAUNCH_CARRIER_APP = "EXTRA_LAUNCH_CARRIER_APP";

    public static final int LAUNCH_APP_ACTIVATE = 0;
    public static final int LAUNCH_APP_UPDATE = 1;
    public static final int LAUNCH_APP_SHOW_TC = 2;

    /** Constants shared by QNS module */
    public static final String EXTRA_URL = "EXTRA_URL";

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
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        int subId =
                intent.getIntExtra(
                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        Log.d(TAG, "Start Activity with subId : " + subId);
        return subId;
    }

    public static String getUrl(Intent intent) {
        String url = "";
        if (intent == null || (url = intent.getStringExtra(EXTRA_URL)) == null) {
            return "";
        }
        return url;
    }

    private ActivityConstants() {}
}
