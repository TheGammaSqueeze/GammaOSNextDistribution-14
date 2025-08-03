/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.telephony.CellInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

import com.google.android.iwlan.flags.FeatureFlags;
import com.google.android.iwlan.flags.FeatureFlagsImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IwlanEventListener {

    private final FeatureFlags mFeatureFlags;
    public static final int UNKNOWN_EVENT = -1;

    /** On {@link IwlanCarrierConfigChangeListener#onCarrierConfigChanged} is called. */
    public static final int CARRIER_CONFIG_CHANGED_EVENT = 1;

    /** Wifi turned off or disabled. */
    public static final int WIFI_DISABLE_EVENT = 2;

    /** Airplane mode turned off or disabled. */
    public static final int APM_DISABLE_EVENT = 3;
    /** Airplane mode turned on or enabled */
    public static final int APM_ENABLE_EVENT = 4;

    /** Wifi AccessPoint changed. */
    public static final int WIFI_AP_CHANGED_EVENT = 5;

    /** Wifi calling turned on or enabled */
    public static final int WIFI_CALLING_ENABLE_EVENT = 6;

    /** Wifi calling turned off or disabled */
    public static final int WIFI_CALLING_DISABLE_EVENT = 7;

    /** Cross sim calling enabled */
    public static final int CROSS_SIM_CALLING_ENABLE_EVENT = 8;

    /** Cross sim calling disabled */
    public static final int CROSS_SIM_CALLING_DISABLE_EVENT = 9;

    /**
     * On {@link IwlanCarrierConfigChangeListener#onCarrierConfigChanged} is called with
     * UNKNOWN_CARRIER_ID.
     */
    public static final int CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT = 10;

    /** On Cellinfo changed */
    public static final int CELLINFO_CHANGED_EVENT = 11;

    /** On Call state changed */
    public static final int CALL_STATE_CHANGED_EVENT = 12;

    /** On Preferred Network Type changed */
    public static final int PREFERRED_NETWORK_TYPE_CHANGED_EVENT = 13;

    /* Events used and handled by IwlanDataService internally */
    public static final int DATA_SERVICE_INTERNAL_EVENT_BASE = 100;

    /* Events used and handled by IwlanNetworkService internally */
    public static final int NETWORK_SERVICE_INTERNAL_EVENT_BASE = 200;

    @IntDef({
        CARRIER_CONFIG_CHANGED_EVENT,
        WIFI_DISABLE_EVENT,
        APM_DISABLE_EVENT,
        APM_ENABLE_EVENT,
        WIFI_AP_CHANGED_EVENT,
        WIFI_CALLING_ENABLE_EVENT,
        WIFI_CALLING_DISABLE_EVENT,
        CROSS_SIM_CALLING_ENABLE_EVENT,
        CROSS_SIM_CALLING_DISABLE_EVENT,
        CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT,
        CELLINFO_CHANGED_EVENT,
        CALL_STATE_CHANGED_EVENT,
        PREFERRED_NETWORK_TYPE_CHANGED_EVENT,
    })
    @interface IwlanEventType {}

    private static final String LOG_TAG = IwlanEventListener.class.getSimpleName();

    private final String SUB_TAG;

    private static Boolean sIsAirplaneModeOn;

    private static String sWifiSSID = "";

    private static final Map<Integer, IwlanEventListener> mInstances = new ConcurrentHashMap<>();

    private final Context mContext;
    private final int mSlotId;
    private int mSubId;
    private Uri mCrossSimCallingUri;
    private Uri mWfcEnabledUri;
    private UserSettingContentObserver mUserSettingContentObserver;
    private RadioInfoTelephonyCallback mTelephonyCallback;

    SparseArray<Set<Handler>> eventHandlers = new SparseArray<>();

    private class UserSettingContentObserver extends ContentObserver {
        UserSettingContentObserver(Handler h) {
            super(h);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Objects.requireNonNull(mCrossSimCallingUri, "CrossSimCallingUri must not be null");
            Objects.requireNonNull(mWfcEnabledUri, "WfcEnabledUri must not be null");
            if (mCrossSimCallingUri.equals(uri)) {
                notifyCurrentSetting(uri);
            } else if (mWfcEnabledUri.equals(uri)) {
                notifyCurrentSetting(uri);
            }
        }
    }

    private class RadioInfoTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.CellInfoListener,
                    TelephonyCallback.CallStateListener,
                    TelephonyCallback.AllowedNetworkTypesListener {
        @Override
        public void onCellInfoChanged(List<CellInfo> arrayCi) {
            Log.d(LOG_TAG, "Cellinfo changed");

            for (Map.Entry<Integer, IwlanEventListener> entry : mInstances.entrySet()) {
                IwlanEventListener instance = entry.getValue();
                if (instance != null) {
                    instance.updateHandlers(arrayCi);
                }
            }
        }

        @Override
        public void onCallStateChanged(int state) {
            Log.d(
                    LOG_TAG,
                    "Call state changed to " + callStateToString(state) + " for slot " + mSlotId);

            IwlanEventListener instance = mInstances.get(mSlotId);
            if (instance != null) {
                instance.updateHandlers(CALL_STATE_CHANGED_EVENT, state);
            }
        }

        @Override
        public void onAllowedNetworkTypesChanged(
                @TelephonyManager.AllowedNetworkTypesReason int reason,
                @TelephonyManager.NetworkTypeBitMask long allowedNetworkType) {
            if (reason != TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER) {
                return;
            }

            IwlanEventListener instance = mInstances.get(mSlotId);
            if (instance != null) {
                instance.updateHandlers(PREFERRED_NETWORK_TYPE_CHANGED_EVENT, allowedNetworkType);
            }
        }
    }

    /**
     * Returns IwlanEventListener instance
     */
    public static IwlanEventListener getInstance(@NonNull Context context, int slotId) {
        return mInstances.computeIfAbsent(
                slotId, k -> new IwlanEventListener(context, slotId, new FeatureFlagsImpl()));
    }

    @VisibleForTesting
    public static void resetAllInstances() {
        mInstances.clear();
    }

    /**
     * Adds handler for the list of events.
     *
     * @param events lists of events for which the handler needs to be notified.
     * @param handler handler to be called when the events happen
     */
    public synchronized void addEventListener(List<Integer> events, Handler handler) {
        for (@IwlanEventType int event : events) {
            if (eventHandlers.contains(event)) {
                eventHandlers.get(event).add(handler);
            } else {
                Set<Handler> handlers = new HashSet<>();
                handlers.add(handler);
                eventHandlers.append(event, handlers);
            }
        }
    }

    /**
     * Removes handler for the list of events.
     *
     * @param events lists of events for which the handler needs to be removed.
     * @param handler handler to be removed
     */
    public synchronized void removeEventListener(List<Integer> events, Handler handler) {
        for (int event : events) {
            if (eventHandlers.contains(event)) {
                Set<Handler> handlers = eventHandlers.get(event);
                handlers.remove(handler);
                if (handlers.isEmpty()) {
                    eventHandlers.delete(event);
                }
            }
        }
        if (eventHandlers.size() == 0) {
            mInstances.remove(mSlotId, this);
        }
    }

    /**
     * Removes handler for all events it is registered
     *
     * @param handler handler to be removed
     */
    public synchronized void removeEventListener(Handler handler) {
        for (int i = 0; i < eventHandlers.size(); i++) {
            Set<Handler> handlers = eventHandlers.valueAt(i);
            handlers.remove(handler);
            if (handlers.isEmpty()) {
                eventHandlers.delete(eventHandlers.keyAt(i));
                i--;
            }
        }
        if (eventHandlers.size() == 0) {
            mInstances.remove(mSlotId, this);
        }
    }

    /**
     * Report a Broadcast received. Mainly used by IwlanBroadcastReceiver to report the following
     * broadcasts: ACTION_AIRPLANE_MODE_CHANGED, WIFI_STATE_CHANGED_ACTION
     *
     * @param intent intent
     */
    public static synchronized void onBroadcastReceived(Intent intent) {
        int event = UNKNOWN_EVENT;
        switch (intent.getAction()) {
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                Boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
                if (sIsAirplaneModeOn != null && sIsAirplaneModeOn.equals(isAirplaneModeOn)) {
                    // no change in apm state
                    break;
                }
                sIsAirplaneModeOn = isAirplaneModeOn;
                event = sIsAirplaneModeOn ? APM_ENABLE_EVENT : APM_DISABLE_EVENT;
                for (Map.Entry<Integer, IwlanEventListener> entry : mInstances.entrySet()) {
                    IwlanEventListener instance = entry.getValue();
                    instance.updateHandlers(event);
                }
                break;
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                int wifiState =
                        intent.getIntExtra(
                                WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                    event = WIFI_DISABLE_EVENT;
                    for (Map.Entry<Integer, IwlanEventListener> entry : mInstances.entrySet()) {
                        IwlanEventListener instance = entry.getValue();
                        instance.updateHandlers(event);
                    }
                }
                break;
        }
    }

    /**
     * Broadcast WIFI_AP_CHANGED_EVENT if Wifi SSID changed after Wifi connected.
     *
     * @param context context
     */
    public static void onWifiConnected(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        if (wifiManager == null) {
            Log.e(LOG_TAG, "Could not find wifiManager");
            return;
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            Log.e(LOG_TAG, "wifiInfo is null");
            return;
        }
        String wifiSSID = wifiInfo.getSSID();
        if (wifiSSID.equals(WifiManager.UNKNOWN_SSID)) {
            Log.e(LOG_TAG, "Could not get Wifi SSID");
            return;
        }

        // Check sWifiSSID is greater than 0 to avoid trigger event after device first camps on
        // Wifi.
        if (sWifiSSID.length() > 0 && !sWifiSSID.equals(wifiSSID)) {
            Log.d(LOG_TAG, "Wifi SSID changed");
            for (Map.Entry<Integer, IwlanEventListener> entry : mInstances.entrySet()) {
                IwlanEventListener instance = entry.getValue();
                if (instance != null) {
                    instance.updateHandlers(WIFI_AP_CHANGED_EVENT);
                }
            }
        }
        sWifiSSID = wifiSSID;
    }

    /**
     * Report Carrier Config changed. Mainly used by IwlanCarrierConfigChangeListener.
     *
     * @param context context
     * @param slotId slot id which carrier config is changed
     * @param subId sub id which carrier config is changed
     * @param carrierId carrier id
     */
    public static synchronized void onCarrierConfigChanged(
            Context context, int slotId, int subId, int carrierId) {
        getInstance(context, slotId).onCarrierConfigChanged(subId, carrierId);
    }

    /**
     * Returns the Event id of the String. String that matches the name of the event
     *
     * @param event String form of the event.
     */
    public static int getUnthrottlingEvent(String event) {
        int ret = UNKNOWN_EVENT;
        switch (event) {
            case "CARRIER_CONFIG_CHANGED_EVENT":
                ret = CARRIER_CONFIG_CHANGED_EVENT;
                break;
            case "WIFI_DISABLE_EVENT":
                ret = WIFI_DISABLE_EVENT;
                break;
            case "APM_DISABLE_EVENT":
                ret = APM_DISABLE_EVENT;
                break;
            case "APM_ENABLE_EVENT":
                ret = APM_ENABLE_EVENT;
                break;
            case "WIFI_AP_CHANGED_EVENT":
                ret = WIFI_AP_CHANGED_EVENT;
                break;
            case "WIFI_CALLING_ENABLE_EVENT":
                ret = WIFI_CALLING_ENABLE_EVENT;
                break;
            case "WIFI_CALLING_DISABLE_EVENT":
                ret = WIFI_CALLING_DISABLE_EVENT;
                break;
            case "CROSS_SIM_CALLING_ENABLE_EVENT":
                ret = CROSS_SIM_CALLING_ENABLE_EVENT;
                break;
            case "CROSS_SIM_CALLING_DISABLE_EVENT":
                ret = CROSS_SIM_CALLING_DISABLE_EVENT;
                break;
            case "CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT":
                ret = CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT;
                break;
            case "CELLINFO_CHANGED_EVENT":
                ret = CELLINFO_CHANGED_EVENT;
                break;
            case "PREFERRED_NETWORK_TYPE_CHANGED_EVENT":
                ret = PREFERRED_NETWORK_TYPE_CHANGED_EVENT;
                break;
        }
        return ret;
    }

    IwlanEventListener(Context context, int slotId, FeatureFlags featureFlags) {
        mContext = context;
        mSlotId = slotId;
        mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        SUB_TAG = IwlanEventListener.class.getSimpleName() + "[" + slotId + "]";
        sIsAirplaneModeOn = null;
        mFeatureFlags = featureFlags;
    }

    private void onCarrierConfigChanged(int subId, int carrierId) {
        Log.d(SUB_TAG, "onCarrierConfigChanged");
        if (subId != mSubId) {
            unregisterContentObserver();
            mSubId = subId;
            registerContentObserver();
        }
        notifyCurrentSetting(mCrossSimCallingUri);
        notifyCurrentSetting(mWfcEnabledUri);

        int event;
        if (carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
            event = CARRIER_CONFIG_CHANGED_EVENT;
            registerTelephonyCallback();
        } else {
            event = CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT;
        }
        updateHandlers(event);
    }

    /** Unregister ContentObserver. */
    void unregisterContentObserver() {
        if (mUserSettingContentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mUserSettingContentObserver);
        }
        mCrossSimCallingUri = null;
        mWfcEnabledUri = null;
    }

    /** Initiate ContentObserver if it is not created. And, register it with the current sub id. */
    private void registerContentObserver() {
        if (mUserSettingContentObserver == null) {
            HandlerThread userSettingHandlerThread =
                    new HandlerThread(IwlanNetworkService.class.getSimpleName());
            userSettingHandlerThread.start();
            Looper looper = userSettingHandlerThread.getLooper();
            Handler handler = new Handler(looper);
            mUserSettingContentObserver = new UserSettingContentObserver(handler);
        }

        if (mSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return;
        }

        ContentResolver resolver = mContext.getContentResolver();
        // Register for CrossSimCalling setting uri
        mCrossSimCallingUri =
                Uri.withAppendedPath(
                        SubscriptionManager.CROSS_SIM_ENABLED_CONTENT_URI, String.valueOf(mSubId));
        resolver.registerContentObserver(mCrossSimCallingUri, true, mUserSettingContentObserver);

        // Register for WifiCalling setting uri
        mWfcEnabledUri =
                Uri.withAppendedPath(
                        SubscriptionManager.WFC_ENABLED_CONTENT_URI, String.valueOf(mSubId));
        resolver.registerContentObserver(mWfcEnabledUri, true, mUserSettingContentObserver);
    }

    @VisibleForTesting
    void notifyCurrentSetting(Uri uri) {
        if (uri == null) {
            return;
        }
        String uriString = uri.getPath();
        int subIndex = Integer.parseInt(uriString.substring(uriString.lastIndexOf('/') + 1));
        int slotIndex = SubscriptionManager.getSlotIndex(subIndex);

        if (slotIndex == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            Log.e(SUB_TAG, "Invalid slot index: " + slotIndex);
            return;
        }

        if (uri.equals(mCrossSimCallingUri)) {
            boolean isCstEnabled = IwlanHelper.isCrossSimCallingEnabled(mContext, slotIndex);
            int event =
                    (isCstEnabled)
                            ? CROSS_SIM_CALLING_ENABLE_EVENT
                            : CROSS_SIM_CALLING_DISABLE_EVENT;
            getInstance(mContext, slotIndex).updateHandlers(event);
        } else if (uri.equals(mWfcEnabledUri)) {
            ImsManager imsManager = mContext.getSystemService(ImsManager.class);
            if (imsManager == null) {
                Log.e(SUB_TAG, "Could not find  ImsManager");
                return;
            }
            ImsMmTelManager imsMmTelManager = imsManager.getImsMmTelManager(subIndex);
            if (imsMmTelManager == null) {
                Log.e(SUB_TAG, "Could not find  ImsMmTelManager");
                return;
            }
            boolean wfcEnabled = false;
            try {
                wfcEnabled = imsMmTelManager.isVoWiFiSettingEnabled();
            } catch (IllegalArgumentException e) {
                Log.w(SUB_TAG, e.getMessage());
            }
            int event = (wfcEnabled) ? WIFI_CALLING_ENABLE_EVENT : WIFI_CALLING_DISABLE_EVENT;
            getInstance(mContext, slotIndex).updateHandlers(event);
        } else {
            Log.e(SUB_TAG, "Unknown Uri : " + uri);
        }
    }

    @VisibleForTesting
    void registerTelephonyCallback() {
        Log.d(SUB_TAG, "registerTelephonyCallback");
        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        telephonyManager =
                Objects.requireNonNull(telephonyManager)
                        .createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));
        mTelephonyCallback = new RadioInfoTelephonyCallback();
        telephonyManager.registerTelephonyCallback(Runnable::run, mTelephonyCallback);
    }

    @VisibleForTesting
    void setCrossSimCallingUri(Uri uri) {
        mCrossSimCallingUri = uri;
    }

    @VisibleForTesting
    void setWfcEnabledUri(Uri uri) {
        mWfcEnabledUri = uri;
    }

    @VisibleForTesting
    RadioInfoTelephonyCallback getTelephonyCallback() {
        return mTelephonyCallback;
    }

    private synchronized void updateHandlers(int event) {
        if (eventHandlers.contains(event)) {
            Log.d(SUB_TAG, "Updating handlers for the event: " + event);
            for (Handler handler : eventHandlers.get(event)) {
                handler.obtainMessage(event, mSlotId, 0 /* unused */).sendToTarget();
            }
        }
    }

    private synchronized void updateHandlers(List<CellInfo> arrayCi) {
        int event = IwlanEventListener.CELLINFO_CHANGED_EVENT;
        if (eventHandlers.contains(event)) {
            Log.d(SUB_TAG, "Updating handlers for the event: " + event);
            for (Handler handler : eventHandlers.get(event)) {
                handler.obtainMessage(event, mSlotId, 0 /* unused */, arrayCi).sendToTarget();
            }
        }
    }

    private synchronized void updateHandlers(int event, int state) {
        if (eventHandlers.contains(event)) {
            Log.d(SUB_TAG, "Updating handlers for the event: " + event);
            for (Handler handler : eventHandlers.get(event)) {
                handler.obtainMessage(event, mSlotId, state).sendToTarget();
            }
        }
    }

    private synchronized void updateHandlers(int event, long allowedNetworkType) {
        if (eventHandlers.contains(event)) {
            Log.d(SUB_TAG, "Updating handlers for the event: " + event);
            for (Handler handler : eventHandlers.get(event)) {
                handler.obtainMessage(event, mSlotId, 0 /* unused */, allowedNetworkType)
                        .sendToTarget();
            }
        }
    }

    private String callStateToString(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                return "CALL_STATE_IDLE";
            case TelephonyManager.CALL_STATE_RINGING:
                return "CALL_STATE_RINGING";
            case TelephonyManager.CALL_STATE_OFFHOOK:
                return "CALL_STATE_OFFHOOK";
            default:
                return "Unknown Call State (" + state + ")";
        }
    }
}
