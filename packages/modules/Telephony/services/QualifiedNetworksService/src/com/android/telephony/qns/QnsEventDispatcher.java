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

package com.android.telephony.qns;

import static android.telephony.SubscriptionManager.EXTRA_SLOT_INDEX;
import static android.telephony.ims.ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED;
import static android.telephony.ims.ImsMmTelManager.WIFI_MODE_WIFI_ONLY;
import static android.telephony.ims.ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED;

import static com.android.telephony.qns.wfc.WfcActivationHelper.ACTION_TRY_WFC_CONNECTION;
import static com.android.telephony.qns.wfc.WfcActivationHelper.EXTRA_SUB_ID;
import static com.android.telephony.qns.wfc.WfcActivationHelper.EXTRA_TRY_STATUS;
import static com.android.telephony.qns.wfc.WfcActivationHelper.STATUS_START;

import android.annotation.IntDef;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ProvisioningManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.TelephonyIntents;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** QnsEventDispatcher Delivers Broadcasted Intent & change on setting to registered Handlers. */
class QnsEventDispatcher {

    @IntDef(
            prefix = {"QNS_EVENT_"},
            value = {
                QNS_EVENT_BASE,
                QNS_EVENT_CARRIER_CONFIG_CHANGED,
                QNS_EVENT_CARRIER_CONFIG_UNKNOWN_CARRIER,
                QNS_EVENT_WIFI_DISABLING,
                QNS_EVENT_WIFI_AP_CHANGED,
                QNS_EVENT_APM_DISABLED,
                QNS_EVENT_APM_ENABLED,
                QNS_EVENT_WFC_ENABLED,
                QNS_EVENT_WFC_DISABLED,
                QNS_EVENT_WFC_MODE_TO_WIFI_ONLY,
                QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED,
                QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED,
                QNS_EVENT_WFC_ROAMING_ENABLED,
                QNS_EVENT_WFC_ROAMING_DISABLED,
                QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY,
                QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED,
                QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED,
                QNS_EVENT_CROSS_SIM_CALLING_ENABLED,
                QNS_EVENT_CROSS_SIM_CALLING_DISABLED,
                QNS_EVENT_EMERGENCY_CALLBACK_MODE_ON,
                QNS_EVENT_EMERGENCY_CALLBACK_MODE_OFF,
                QNS_EVENT_WFC_PLATFORM_ENABLED,
                QNS_EVENT_WFC_PLATFORM_DISABLED,
                QNS_EVENT_SIM_ABSENT,
                QNS_EVENT_SIM_LOADED,
                QNS_EVENT_WIFI_ENABLED,
            })
    @interface QnsEventType {}

    static final int QNS_EVENT_BASE = 0;
    static final int QNS_EVENT_CARRIER_CONFIG_CHANGED = QNS_EVENT_BASE + 1;
    static final int QNS_EVENT_CARRIER_CONFIG_UNKNOWN_CARRIER = QNS_EVENT_BASE + 2;
    static final int QNS_EVENT_WIFI_DISABLING = QNS_EVENT_BASE + 3;
    static final int QNS_EVENT_WIFI_AP_CHANGED = QNS_EVENT_BASE + 4;
    static final int QNS_EVENT_APM_DISABLED = QNS_EVENT_BASE + 5;
    static final int QNS_EVENT_APM_ENABLED = QNS_EVENT_BASE + 6;
    static final int QNS_EVENT_WFC_ENABLED = QNS_EVENT_BASE + 7;
    static final int QNS_EVENT_WFC_DISABLED = QNS_EVENT_BASE + 8;
    static final int QNS_EVENT_WFC_MODE_TO_WIFI_ONLY = QNS_EVENT_BASE + 9;
    static final int QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED = QNS_EVENT_BASE + 10;
    static final int QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED = QNS_EVENT_BASE + 11;
    static final int QNS_EVENT_WFC_ROAMING_ENABLED = QNS_EVENT_BASE + 12;
    static final int QNS_EVENT_WFC_ROAMING_DISABLED = QNS_EVENT_BASE + 13;
    static final int QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY = QNS_EVENT_BASE + 14;
    static final int QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED = QNS_EVENT_BASE + 15;
    static final int QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED = QNS_EVENT_BASE + 16;
    static final int QNS_EVENT_CROSS_SIM_CALLING_ENABLED = QNS_EVENT_BASE + 17;
    static final int QNS_EVENT_CROSS_SIM_CALLING_DISABLED = QNS_EVENT_BASE + 18;
    static final int QNS_EVENT_EMERGENCY_CALLBACK_MODE_ON = QNS_EVENT_BASE + 19;
    static final int QNS_EVENT_EMERGENCY_CALLBACK_MODE_OFF = QNS_EVENT_BASE + 20;
    static final int QNS_EVENT_WFC_PLATFORM_ENABLED = QNS_EVENT_BASE + 21;
    static final int QNS_EVENT_WFC_PLATFORM_DISABLED = QNS_EVENT_BASE + 22;
    static final int QNS_EVENT_SIM_ABSENT = QNS_EVENT_BASE + 23;
    static final int QNS_EVENT_SIM_LOADED = QNS_EVENT_BASE + 24;
    static final int QNS_EVENT_WIFI_ENABLED = QNS_EVENT_BASE + 25;
    static final int QNS_EVENT_TRY_WFC_ACTIVATION = QNS_EVENT_BASE + 100;
    static final int QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION = QNS_EVENT_BASE + 101;
    private static final int EVENT_PROVISIONING_INFO_CHANGED = QNS_EVENT_BASE + 200;
    private static Boolean sIsAirplaneModeOn;
    private static int sWiFiState = WifiManager.WIFI_STATE_UNKNOWN;
    private final String mLogTag;
    private final Context mContext;
    private final int mSlotIndex;
    SparseArray<Set<Handler>> mEventHandlers = new SparseArray<>();
    private int mSubId;
    private Uri mCrossSimCallingUri;
    private Uri mWfcEnabledUri;
    private Uri mWfcModeUri;
    private Uri mWfcRoamingEnabledUri;
    private Uri mWfcRoamingModeUri;
    @VisibleForTesting UserSettingObserver mUserSettingObserver;
    private HandlerThread mUserSettingHandlerThread;
    boolean mLastWfcEnabledByPlatform = false;
    boolean mLastCrossSimCallingEnabled = false;
    boolean mLastWfcEnabled = false;
    int mLastWfcMode = WIFI_MODE_CELLULAR_PREFERRED;
    boolean mLastWfcRoamingEnabled = false;
    int mLastWfcModeRoaming = WIFI_MODE_WIFI_PREFERRED;
    protected final QnsProvisioningListener mQnsProvisioningListener;
    protected final QnsImsManager mQnsImsManager;
    private QnsProvisioningListener.QnsProvisioningInfo mLastProvisioningInfo;
    @VisibleForTesting final QnsEventDispatcherHandler mQnsEventDispatcherHandler;

    @VisibleForTesting
    final BroadcastReceiver mIntentReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    int event;
                    Log.d(mLogTag, "onReceive: " + action);
                    switch (action) {
                        case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED:
                            int slotId =
                                    intent.getIntExtra(
                                            CarrierConfigManager.EXTRA_SLOT_INDEX,
                                            SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                            int carrierId =
                                    intent.getIntExtra(
                                            TelephonyManager.EXTRA_CARRIER_ID,
                                            TelephonyManager.UNKNOWN_CARRIER_ID);

                            onCarrierConfigChanged(context, slotId, carrierId);
                            break;

                        case TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED:
                        case TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED:
                            slotId =
                                    intent.getIntExtra(
                                            CarrierConfigManager.EXTRA_SLOT_INDEX,
                                            SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                            int simState =
                                    intent.getIntExtra(
                                            TelephonyManager.EXTRA_SIM_STATE,
                                            TelephonyManager.SIM_STATE_UNKNOWN);
                            onSimStateChanged(slotId, simState);
                            break;

                        case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                            Boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
                            if (sIsAirplaneModeOn != null
                                    && sIsAirplaneModeOn.equals(isAirplaneModeOn)) {
                                // no change in apm state
                                break;
                            }
                            sIsAirplaneModeOn = isAirplaneModeOn;
                            event =
                                    sIsAirplaneModeOn
                                            ? QNS_EVENT_APM_ENABLED
                                            : QNS_EVENT_APM_DISABLED;
                            updateHandlers(event);
                            break;

                        case WifiManager.WIFI_STATE_CHANGED_ACTION:
                            int wifiState =
                                    intent.getIntExtra(
                                            WifiManager.EXTRA_WIFI_STATE,
                                            WifiManager.WIFI_STATE_UNKNOWN);
                            if (wifiState != sWiFiState) {
                                sWiFiState = wifiState;
                                if (wifiState == WifiManager.WIFI_STATE_DISABLING) {
                                    event = QNS_EVENT_WIFI_DISABLING;
                                } else if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                                    event = QNS_EVENT_WIFI_ENABLED;
                                } else {
                                    break;
                                }
                                updateHandlers(event);
                            }
                            break;

                        case TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED:
                            boolean emergencyMode =
                                    intent.getBooleanExtra(
                                            TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false);
                            event =
                                    emergencyMode
                                            ? QNS_EVENT_EMERGENCY_CALLBACK_MODE_ON
                                            : QNS_EVENT_EMERGENCY_CALLBACK_MODE_OFF;
                            int slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, 0);
                            // getInstance(mContext, slotIndex).
                            if (mSlotIndex == slotIndex) {
                                updateHandlers(event);
                            }
                            break;
                    }
                }
            };

    final BroadcastReceiver mWfcActivationIntentReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    int event;
                    Log.d(mLogTag, "mWfcActivationIntentReceiver onReceive: " + action);
                    switch (action) {
                        case ACTION_TRY_WFC_CONNECTION:
                            int subId = intent.getIntExtra(EXTRA_SUB_ID, -1);
                            if (subId != mSubId) {
                                Log.d(mLogTag, "Intent subId: " + subId + ", mSubId: " + mSubId);
                                break;
                            }
                            int request = intent.getIntExtra(EXTRA_TRY_STATUS, 0);
                            event =
                                    request == STATUS_START
                                            ? QNS_EVENT_TRY_WFC_ACTIVATION
                                            : QNS_EVENT_CANCEL_TRY_WFC_ACTIVATION;
                            updateHandlers(event);
                            break;
                    }
                }
            };

    /** QnsEventDispatcher constructor */
    QnsEventDispatcher(
            Context context,
            QnsProvisioningListener provisioningListener,
            QnsImsManager imsManager,
            int slotIndex) {
        mContext = context;
        mSlotIndex = slotIndex;
        mLogTag = QnsEventDispatcher.class.getSimpleName() + "[" + mSlotIndex + "]";
        mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mQnsProvisioningListener = provisioningListener;
        mQnsImsManager = imsManager;
        final ContentResolver cr = mContext.getContentResolver();
        int airplaneMode = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0);
        sIsAirplaneModeOn = (airplaneMode == 1);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        intentFilter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        intentFilter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        mContext.registerReceiver(mIntentReceiver, intentFilter);

        IntentFilter wfcIntentFilter = new IntentFilter();
        wfcIntentFilter.addAction(ACTION_TRY_WFC_CONNECTION);
        mContext.registerReceiver(
                mWfcActivationIntentReceiver, wfcIntentFilter, Context.RECEIVER_NOT_EXPORTED);

        HandlerThread handlerThread = new HandlerThread(mLogTag);
        handlerThread.start();
        mQnsEventDispatcherHandler = new QnsEventDispatcherHandler(handlerThread.getLooper());
        mQnsEventDispatcherHandler.post(() -> loadAndNotifyWfcSettings(mContext, mSlotIndex));

        mLastProvisioningInfo = new QnsProvisioningListener.QnsProvisioningInfo();
        mQnsProvisioningListener.registerProvisioningItemInfoChanged(
                mQnsEventDispatcherHandler, EVENT_PROVISIONING_INFO_CHANGED, null, true);
    }

    private synchronized void loadAndNotifyWfcSettings(Context context, int slotIndex) {
        int subId = QnsUtils.getSubId(context, slotIndex);
        Log.d(mLogTag, "loadAndNotifyWfcSettings for sub:" + subId);
        if (subId != mSubId) {
            unregisterContentObserver();
            mSubId = subId;
            registerContentObserver();
        }
        notifyWfcEnabledByPlatform();
        notifyCurrentSetting(mCrossSimCallingUri, true);
        notifyCurrentSetting(mWfcEnabledUri, true);
        notifyCurrentSetting(mWfcModeUri, true);
        notifyCurrentSetting(mWfcRoamingEnabledUri, true);
        notifyCurrentSetting(mWfcRoamingModeUri, true);
    }

    private synchronized void onCarrierConfigChanged(Context context, int slotId, int carrierId) {
        if (slotId != mSlotIndex) {
            return;
        }

        Log.d(mLogTag, "onCarrierConfigChanged");
        loadAndNotifyWfcSettings(context, slotId);

        int event;
        if (carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
            event = QNS_EVENT_CARRIER_CONFIG_CHANGED;
        } else {
            event = QNS_EVENT_CARRIER_CONFIG_UNKNOWN_CARRIER;
        }

        updateHandlers(event);
    }

    synchronized void registerEvent(List<Integer> events, Handler handler) {
        for (@QnsEventType int event : events) {
            if (mEventHandlers.contains(event)) {
                mEventHandlers.get(event).add(handler);
            } else {
                Set<Handler> handlers = new HashSet<>();
                handlers.add(handler);
                mEventHandlers.append(event, handlers);
            }
        }
        notifyImmediately(events, handler);
    }

    private void onSimStateChanged(int slotId, int simState) {
        if (slotId != mSlotIndex) {
            return;
        }
        if (simState == TelephonyManager.SIM_STATE_ABSENT) {
            updateHandlers(QNS_EVENT_SIM_ABSENT);
        } else if (simState == TelephonyManager.SIM_STATE_LOADED) {
            updateHandlers(QNS_EVENT_SIM_LOADED);
        }
    }

    private synchronized void notifyImmediately(List<Integer> events, Handler handler) {
        boolean bWfcPlatformSetting = false;
        boolean bWfcSetting = false;
        boolean bWfcModeSetting = false;
        boolean bWfcRoamingSetting = false;
        boolean bWfcRoamingModeSetting = false;
        boolean bCrossSimSetting = false;
        for (@QnsEventType int event : events) {
            switch (event) {
                case QNS_EVENT_WFC_PLATFORM_ENABLED:
                case QNS_EVENT_WFC_PLATFORM_DISABLED:
                    bWfcPlatformSetting = true;
                    break;
                case QNS_EVENT_WFC_ENABLED:
                case QNS_EVENT_WFC_DISABLED:
                    bWfcSetting = true;
                    break;
                case QNS_EVENT_WFC_ROAMING_ENABLED:
                case QNS_EVENT_WFC_ROAMING_DISABLED:
                    bWfcRoamingSetting = true;
                    break;
                case QNS_EVENT_WFC_MODE_TO_WIFI_ONLY:
                case QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED:
                case QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED:
                    bWfcModeSetting = true;
                    break;
                case QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY:
                case QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED:
                case QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED:
                    bWfcRoamingModeSetting = true;
                    break;
                case QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_ENABLED:
                case QnsEventDispatcher.QNS_EVENT_CROSS_SIM_CALLING_DISABLED:
                    bCrossSimSetting = true;
                    break;
            }
        }

        if (bWfcPlatformSetting) {
            if (mLastWfcEnabledByPlatform) {
                updateHandler(handler, QNS_EVENT_WFC_PLATFORM_ENABLED);
            } else {
                updateHandler(handler, QNS_EVENT_WFC_PLATFORM_DISABLED);
            }
            // checks again whether setting is changed.
            notifyWfcEnabledByPlatform();
        }
        if (bWfcSetting) {
            if (mLastWfcEnabled) {
                updateHandler(handler, QNS_EVENT_WFC_ENABLED);
            } else {
                updateHandler(handler, QNS_EVENT_WFC_DISABLED);
            }
            // checks again whether setting is changed.
            notifyCurrentSetting(mWfcEnabledUri, false);
        }
        if (bWfcModeSetting) {
            switch (mLastWfcMode) {
                case WIFI_MODE_WIFI_ONLY:
                    updateHandler(handler, QNS_EVENT_WFC_MODE_TO_WIFI_ONLY);
                    break;
                case WIFI_MODE_CELLULAR_PREFERRED:
                    updateHandler(handler, QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED);
                    break;
                case WIFI_MODE_WIFI_PREFERRED:
                    updateHandler(handler, QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED);
                    break;
            }
            // checks again whether setting is changed.
            notifyCurrentSetting(mWfcModeUri, false);
        }
        if (bWfcRoamingSetting) {
            if (mLastWfcRoamingEnabled) {
                updateHandler(handler, QNS_EVENT_WFC_ROAMING_ENABLED);
            } else {
                updateHandler(handler, QNS_EVENT_WFC_ROAMING_DISABLED);
            }
            // checks again whether setting is changed.
            notifyCurrentSetting(mWfcRoamingEnabledUri, false);
        }
        if (bWfcRoamingModeSetting) {
            switch (mLastWfcModeRoaming) {
                case WIFI_MODE_WIFI_ONLY:
                    updateHandler(handler, QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY);
                    break;
                case WIFI_MODE_CELLULAR_PREFERRED:
                    updateHandler(handler, QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED);
                    break;
                case WIFI_MODE_WIFI_PREFERRED:
                    updateHandler(handler, QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED);
                    break;
            }
            // checks again whether setting is changed.
            notifyCurrentSetting(mWfcRoamingModeUri, false);
        }
        if (bCrossSimSetting) {
            if (mLastCrossSimCallingEnabled) {
                updateHandler(handler, QNS_EVENT_CROSS_SIM_CALLING_ENABLED);
            } else {
                updateHandler(handler, QNS_EVENT_CROSS_SIM_CALLING_DISABLED);
            }
            // checks again whether setting is changed.
            notifyCurrentSetting(mCrossSimCallingUri, false);
        }
    }

    synchronized void unregisterEvent(Handler handler) {
        for (int i = 0; i < mEventHandlers.size(); i++) {
            Set<Handler> handlers = mEventHandlers.valueAt(i);
            handlers.remove(handler);
            if (handlers.isEmpty()) {
                mEventHandlers.delete(mEventHandlers.keyAt(i));
                i--;
            }
        }
        if (mEventHandlers.size() == 0) {
            close();
        }
    }

    public void close() {
        try {
            mContext.unregisterReceiver(mIntentReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (mUserSettingObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mUserSettingObserver);
        }
        if (mUserSettingHandlerThread != null) {
            mUserSettingHandlerThread.quit();
        }
        if (mQnsProvisioningListener != null) {
            mQnsProvisioningListener.unregisterProvisioningItemInfoChanged(
                    mQnsEventDispatcherHandler);
        }
    }

    private synchronized void unregisterContentObserver() {
        if (mUserSettingObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mUserSettingObserver);
        }
        mCrossSimCallingUri = null;
        mWfcEnabledUri = null;
        mWfcModeUri = null;
        mWfcRoamingEnabledUri = null;
        mWfcRoamingModeUri = null;
    }

    private void registerContentObserver() {
        // Register for content observer
        if (mUserSettingObserver == null) {
            Log.d(mLogTag, "create mUserSettingObserver");
            mUserSettingHandlerThread = new HandlerThread(QnsEventDispatcher.class.getSimpleName());
            mUserSettingHandlerThread.start();
            Looper looper = mUserSettingHandlerThread.getLooper();
            Handler handler = new Handler(looper);
            mUserSettingObserver = new UserSettingObserver(handler);

            // init
            mLastWfcEnabledByPlatform = QnsUtils.isWfcEnabledByPlatform(mQnsImsManager);
            mLastCrossSimCallingEnabled = QnsUtils.isCrossSimCallingEnabled(mQnsImsManager);
            mLastWfcEnabled =
                    QnsUtils.isWfcEnabled(mQnsImsManager, mQnsProvisioningListener, false);
            mLastWfcMode = QnsUtils.getWfcMode(mQnsImsManager, false);
            mLastWfcRoamingEnabled =
                    QnsUtils.isWfcEnabled(mQnsImsManager, mQnsProvisioningListener, true);
            mLastWfcModeRoaming = QnsUtils.getWfcMode(mQnsImsManager, true);
        }

        if (mSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return;
        }

        StringBuilder sb = new StringBuilder("registerContentObserver");
        sb.append(" subId:").append(mSubId);
        ContentResolver resolver = mContext.getContentResolver();

        // Update current Cross Sim Calling setting
        Uri crossSimCallingUri = getUri(SubscriptionManager.CROSS_SIM_ENABLED_CONTENT_URI, mSubId);
        if (!crossSimCallingUri.equals(mCrossSimCallingUri)) {
            mCrossSimCallingUri = crossSimCallingUri;
            sb.append(" crossSimCallingUri/").append(mSubId);
            resolver.registerContentObserver(mCrossSimCallingUri, true, mUserSettingObserver);
        }

        // Update current Wi-Fi Calling setting
        Uri wfcEnabledUri = getUri(SubscriptionManager.WFC_ENABLED_CONTENT_URI, mSubId);
        if (!wfcEnabledUri.equals(mWfcEnabledUri)) {
            mWfcEnabledUri = wfcEnabledUri;
            sb.append(" wfcEnabledUri/").append(mSubId);
            resolver.registerContentObserver(mWfcEnabledUri, true, mUserSettingObserver);
        }

        // Update current Wi-Fi Calling Mode setting
        Uri wfcModeUri = getUri(SubscriptionManager.WFC_MODE_CONTENT_URI, mSubId);
        if (!wfcModeUri.equals(mWfcModeUri)) {
            mWfcModeUri = wfcModeUri;
            sb.append(" wfcModeUri/").append(mSubId);
            resolver.registerContentObserver(mWfcModeUri, true, mUserSettingObserver);
        }

        // Update current Wi-Fi Calling setting for roaming
        Uri wfcRoamEnabledUri = getUri(SubscriptionManager.WFC_ROAMING_ENABLED_CONTENT_URI, mSubId);
        if (!wfcRoamEnabledUri.equals(mWfcRoamingEnabledUri)) {
            mWfcRoamingEnabledUri = wfcRoamEnabledUri;
            sb.append(" wfcRoamEnabledUri/").append(mSubId);
            resolver.registerContentObserver(mWfcRoamingEnabledUri, true, mUserSettingObserver);
        }

        // Update current Wi-Fi Calling Mode setting for roaming
        Uri wfcRoamingModeUri = getUri(SubscriptionManager.WFC_ROAMING_MODE_CONTENT_URI, mSubId);
        if (!wfcRoamingModeUri.equals(mWfcRoamingModeUri)) {
            mWfcRoamingModeUri = wfcRoamingModeUri;
            sb.append(" wfcRoamingModeUri/").append(mSubId);
            resolver.registerContentObserver(mWfcRoamingModeUri, true, mUserSettingObserver);
        }

        Log.d(mLogTag, sb.toString());
    }

    private Uri getUri(Uri uri, int subId) {
        return Uri.withAppendedPath(uri, String.valueOf(subId));
    }

    void notifyCurrentSetting(Uri uri, boolean bForceUpdate) {
        if (uri == null) {
            return;
        }
        try {
            String uriString = uri.getPath();
            int subIndex = Integer.parseInt(uriString.substring(uriString.lastIndexOf('/') + 1));
            int slotIndex = SubscriptionManager.getSlotIndex(subIndex);
            int event = QNS_EVENT_BASE;

            if (slotIndex == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.e(mLogTag, "Invalid slot index: " + slotIndex);
                return;
            }

            StringBuilder sb = new StringBuilder("notifyCurrentSetting");
            sb.append(", bForceUpdate:").append(bForceUpdate);

            Log.d(mLogTag, "CrossSimCallingUri:" + mCrossSimCallingUri);
            Log.d(mLogTag, "mWfcEnabledUri:" + mWfcEnabledUri);
            Log.d(mLogTag, "mWfcModeUri:" + mWfcModeUri);
            Log.d(mLogTag, "mWfcRoamingEnabledUri:" + mWfcRoamingEnabledUri);
            Log.d(mLogTag, "mWfcRoamingModeUri:" + mWfcRoamingModeUri);

            if (uri.equals(mCrossSimCallingUri)) {
                boolean isCrossSimCallingEnabled =
                        QnsUtils.isCrossSimCallingEnabled(mQnsImsManager);
                if (mLastCrossSimCallingEnabled != isCrossSimCallingEnabled || bForceUpdate) {
                    if (isCrossSimCallingEnabled) {
                        event = QNS_EVENT_CROSS_SIM_CALLING_ENABLED;
                    } else {
                        event = QNS_EVENT_CROSS_SIM_CALLING_DISABLED;
                    }
                    mLastCrossSimCallingEnabled = isCrossSimCallingEnabled;
                    sb.append(", isCrossSimCallingEnabled:").append(isCrossSimCallingEnabled);
                    Log.d(mLogTag, sb.toString());
                    updateHandlers(event);
                }
            } else if (uri.equals(mWfcEnabledUri)) {
                boolean isWfcEnabled =
                        QnsUtils.isWfcEnabled(mQnsImsManager, mQnsProvisioningListener, false);
                if (mLastWfcEnabled != isWfcEnabled || bForceUpdate) {
                    if (isWfcEnabled) {
                        event = QNS_EVENT_WFC_ENABLED;
                    } else {
                        event = QNS_EVENT_WFC_DISABLED;
                    }
                    mLastWfcEnabled = isWfcEnabled;
                    sb.append(", isWfcEnabled:").append(isWfcEnabled);
                    Log.d(mLogTag, sb.toString());
                    updateHandlers(event);
                }
            } else if (uri.equals(mWfcModeUri)) {
                int wfcMode = QnsUtils.getWfcMode(mQnsImsManager, false);
                if (mLastWfcMode != wfcMode || bForceUpdate) {
                    switch (wfcMode) {
                        case WIFI_MODE_WIFI_ONLY:
                            event = QNS_EVENT_WFC_MODE_TO_WIFI_ONLY;
                            break;
                        case WIFI_MODE_CELLULAR_PREFERRED:
                            event = QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED;
                            break;
                        case WIFI_MODE_WIFI_PREFERRED:
                            event = QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED;
                            break;
                    }
                    mLastWfcMode = wfcMode;
                    sb.append(", wfcMode:").append(wfcMode);
                    Log.d(mLogTag, sb.toString());
                    updateHandlers(event);
                }
            } else if (uri.equals(mWfcRoamingEnabledUri)) {
                boolean isWfcRoamingEnabled =
                        QnsUtils.isWfcEnabled(mQnsImsManager, mQnsProvisioningListener, true);
                if (mLastWfcRoamingEnabled != isWfcRoamingEnabled || bForceUpdate) {
                    if (isWfcRoamingEnabled) {
                        event = QNS_EVENT_WFC_ROAMING_ENABLED;
                    } else {
                        event = QNS_EVENT_WFC_ROAMING_DISABLED;
                    }
                    mLastWfcRoamingEnabled = isWfcRoamingEnabled;
                    sb.append(", isWfcRoamingEnabled:").append(isWfcRoamingEnabled);
                    Log.d(mLogTag, sb.toString());
                    updateHandlers(event);
                }
            } else if (uri.equals(mWfcRoamingModeUri)) {
                int wfcModeRoaming = QnsUtils.getWfcMode(mQnsImsManager, true);
                if (mLastWfcModeRoaming != wfcModeRoaming || bForceUpdate) {
                    switch (wfcModeRoaming) {
                        case WIFI_MODE_WIFI_ONLY:
                            event = QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_ONLY;
                            break;
                        case WIFI_MODE_CELLULAR_PREFERRED:
                            event = QNS_EVENT_WFC_ROAMING_MODE_TO_CELLULAR_PREFERRED;
                            break;
                        case WIFI_MODE_WIFI_PREFERRED:
                            event = QNS_EVENT_WFC_ROAMING_MODE_TO_WIFI_PREFERRED;
                            break;
                    }
                    mLastWfcModeRoaming = wfcModeRoaming;
                    sb.append(", wfcModeRoaming:").append(wfcModeRoaming);
                    Log.d(mLogTag, sb.toString());
                    updateHandlers(event);
                }
            } else {
                Log.e(mLogTag, "Unknown Uri : " + uri);
            }
        } catch (Exception e) {
            Log.e(mLogTag, "notifyCurrentSetting got exception:" + e);
            e.printStackTrace();
        }
    }

    void notifyWfcEnabledByPlatform() {
        boolean isWfcEnabledByPlatform = QnsUtils.isWfcEnabledByPlatform(mQnsImsManager);
        if (mLastWfcEnabledByPlatform != isWfcEnabledByPlatform) {
            mLastWfcEnabledByPlatform = isWfcEnabledByPlatform;
            Log.d(mLogTag, "notifyWfcEnabledByPlatform:" + isWfcEnabledByPlatform);
            if (isWfcEnabledByPlatform) {
                updateHandlers(QNS_EVENT_WFC_PLATFORM_ENABLED);
            } else {
                updateHandlers(QNS_EVENT_WFC_PLATFORM_DISABLED);
            }
        }
    }

    private synchronized void updateHandler(Handler handler, int event) {
        try {
            if (mEventHandlers.get(event).contains(handler)) {
                Log.d(mLogTag, "Updating handler for the event: " + event);
                handler.obtainMessage(event).sendToTarget();
            }
        } catch (Exception e) {
            Log.e(mLogTag, "updateHandler got exception e:" + e);
        }
    }

    private synchronized void updateHandlers(int event) {
        if (mEventHandlers.contains(event)) {
            Log.d(mLogTag, "Updating handlers for the event: " + event);
            for (Handler handler : mEventHandlers.get(event)) {
                handler.obtainMessage(event).sendToTarget();
            }
        }
    }

    @VisibleForTesting
    class UserSettingObserver extends ContentObserver {
        UserSettingObserver(Handler h) {
            super(h);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Log.d(mLogTag, "onUserSettingChanged");
            onUserSettingChanged(uri);
        }
    }

    private synchronized void onUserSettingChanged(Uri uri) {
        if (mCrossSimCallingUri.equals(uri)) {
            notifyCurrentSetting(uri, false);
        } else if (mWfcEnabledUri.equals(uri)) {
            // checks platform changes first.
            notifyWfcEnabledByPlatform();

            notifyCurrentSetting(uri, false);
        } else if (mWfcModeUri.equals(uri)) {
            notifyCurrentSetting(uri, false);
        } else if (mWfcRoamingEnabledUri.equals(uri)) {
            // checks platform changes first.
            notifyWfcEnabledByPlatform();

            notifyCurrentSetting(uri, false);
        } else if (mWfcRoamingModeUri.equals(uri)) {
            notifyCurrentSetting(uri, false);
        }
    }

    private class QnsEventDispatcherHandler extends Handler {
        /**
         * Use the provided {@link Looper} instead of the default one.
         *
         * @param looper The looper, must not be null.
         */
        QnsEventDispatcherHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            Log.d(mLogTag, "handleMessage msg=" + message.what);
            QnsAsyncResult ar = (QnsAsyncResult) message.obj;
            switch (message.what) {
                case EVENT_PROVISIONING_INFO_CHANGED:
                    onProvisioningInfoChanged(
                            (QnsProvisioningListener.QnsProvisioningInfo) ar.mResult);
                    break;
                default:
                    break;
            }
        }
    }

    private synchronized void onProvisioningInfoChanged(
            QnsProvisioningListener.QnsProvisioningInfo info) {

        Log.d(mLogTag, "onProvisioningInfoChanged info:" + info);

        if (!info.equalsIntegerItem(
                mLastProvisioningInfo,
                ProvisioningManager.KEY_VOICE_OVER_WIFI_ROAMING_ENABLED_OVERRIDE)) {
            Log.d(
                    mLogTag,
                    "onProvisioningInfoChanged, KEY_VOICE_OVER_WIFI_ROAMING_ENABLED_OVERRIDE("
                            + ProvisioningManager.KEY_VOICE_OVER_WIFI_ROAMING_ENABLED_OVERRIDE
                            + ") is provisioned to "
                            + info.getIntegerItem(
                                    ProvisioningManager
                                            .KEY_VOICE_OVER_WIFI_ROAMING_ENABLED_OVERRIDE));
            // checks platform changes first.
            notifyWfcEnabledByPlatform();
            notifyCurrentSetting(mWfcRoamingEnabledUri, false);
        }
        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE)) {
            Log.d(
                    mLogTag,
                    "onProvisioningInfoChanged, KEY_VOICE_OVER_WIFI_MODE_OVERRIDE("
                            + ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE
                            + ") is provisioned to "
                            + info.getIntegerItem(
                                    ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE));
            notifyCurrentSetting(mWfcModeUri, false);
        }
        if (!info.equalsIntegerItem(
                mLastProvisioningInfo, ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE)) {
            Log.d(
                    mLogTag,
                    "onProvisioningInfoChanged, KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE("
                            + ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE
                            + ") is provisioned to "
                            + info.getIntegerItem(
                                    ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE));
            // checks platform changes first.
            notifyWfcEnabledByPlatform();
            notifyCurrentSetting(mWfcEnabledUri, false);
        }

        mLastProvisioningInfo = info;
    }

    boolean isAirplaneModeToggleOn() {
        return sIsAirplaneModeOn;
    }
}
