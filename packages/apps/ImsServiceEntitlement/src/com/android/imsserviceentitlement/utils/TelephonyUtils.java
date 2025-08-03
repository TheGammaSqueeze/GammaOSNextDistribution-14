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

package com.android.imsserviceentitlement.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.common.collect.ImmutableSet;

import java.util.List;

/** This class implements Telephony helper methods. */
public class TelephonyUtils {
    public static final String TAG = "IMSSE-TelephonyUtils";

    private final ConnectivityManager mConnectivityManager;
    private final TelephonyManager mTelephonyManager;

    public TelephonyUtils(Context context) {
        this(context, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    public TelephonyUtils(Context context, int subId) {
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            mTelephonyManager =
                    context.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
        } else {
            mTelephonyManager = context.getSystemService(TelephonyManager.class);
        }
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
    }

    /** Returns device timestamp in milliseconds. */
    public long getTimeStamp() {
        return System.currentTimeMillis();
    }

    /** Returns device uptime in milliseconds. */
    public long getUptimeMillis() {
        return android.os.SystemClock.uptimeMillis();
    }

    /** Returns {@code true} if network is connected (cellular or WiFi). */
    public boolean isNetworkConnected() {
        NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    /** Returns carrier ID. */
    public int getCarrierId() {
        return mTelephonyManager.getSimCarrierId();
    }

    /** Returns fine-grained carrier ID. */
    public int getSpecificCarrierId() {
        return mTelephonyManager.getSimSpecificCarrierId();
    }

    /** Returns SIM card application state. */
    public int getSimApplicationState() {
        return mTelephonyManager.getSimApplicationState();
    }

    /**
     * Returns {@code true} if the {@code subId} still point to a actived SIM; {@code false}
     * otherwise.
     */
    public static boolean isActivedSubId(Context context, int subId) {
        SubscriptionManager subscriptionManager =
                (SubscriptionManager) context.getSystemService(
                        Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        SubscriptionInfo subInfo = subscriptionManager.getActiveSubscriptionInfo(subId);
        return subInfo != null;
    }

    /**
     * Returns the slot index for the actived {@code subId}; {@link
     * SubscriptionManager#INVALID_SIM_SLOT_INDEX} otherwise.
     */
    public static int getSlotId(Context context, int subId) {
        SubscriptionManager subscriptionManager =
                (SubscriptionManager) context.getSystemService(
                        Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        SubscriptionInfo subInfo = subscriptionManager.getActiveSubscriptionInfo(subId);
        if (subInfo != null) {
            return subInfo.getSimSlotIndex();
        }
        Log.d(TAG, "Can't find actived subscription for " + subId);
        return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    /** Returns carrier config for the {@code subId}. */
    private static PersistableBundle getConfigForSubId(Context context, int subId) {
        CarrierConfigManager carrierConfigManager =
                context.getSystemService(CarrierConfigManager.class);
        PersistableBundle carrierConfig = carrierConfigManager.getConfigForSubId(subId);
        if (carrierConfig == null) {
            Log.d(TAG, "getDefaultConfig");
            carrierConfig = CarrierConfigManager.getDefaultConfig();
        }
        return carrierConfig;
    }

    /**
     * Returns FCM sender id for the {@code subId} or a default empty string if it is not available.
     */
    public static String getFcmSenderId(Context context, int subId) {
        return getConfigForSubId(context, subId).getString(
                CarrierConfigManager.ImsServiceEntitlement.KEY_FCM_SENDER_ID_STRING,
                ""
        );
    }

    /**
     * Returns entitlement server url for the {@code subId} or
     * a default empty string if it is not available.
     */
    public static String getEntitlementServerUrl(Context context, int subId) {
        return getConfigForSubId(context, subId).getString(
                CarrierConfigManager.ImsServiceEntitlement.KEY_ENTITLEMENT_SERVER_URL_STRING,
                ""
        );
    }

    /**
     * Returns true if app needs to do IMS (VoLTE/VoWiFi/SMSoIP) provisioning in the background
     * or false if it doesn't need to do.
     */
    public static boolean isImsProvisioningRequired(Context context, int subId) {
        return getConfigForSubId(context, subId).getBoolean(
                CarrierConfigManager.ImsServiceEntitlement.KEY_IMS_PROVISIONING_BOOL,
                false
        );
    }

    /** Returns SubIds which support FCM. */
    public static ImmutableSet<Integer> getSubIdsWithFcmSupported(Context context) {
        SubscriptionManager subscriptionManager =
                context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> infos = subscriptionManager.getActiveSubscriptionInfoList();
        if (infos == null) {
            return ImmutableSet.of();
        }

        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        for (SubscriptionInfo info : infos) {
            int subId = info.getSubscriptionId();
            if (isFcmPushNotificationSupported(context, subId)) {
                builder.add(subId);
            }
        }
        return builder.build();
    }

    private static boolean isFcmPushNotificationSupported(Context context, int subId) {
        return !TelephonyUtils.getFcmSenderId(context, subId).isEmpty();
    }
}
