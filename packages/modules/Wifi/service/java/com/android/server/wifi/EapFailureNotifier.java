/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.Nullable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiStringResourceWrapper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.modules.utils.build.SdkLevel;

/**
 * This class may be used to launch notifications when EAP failure occurs.
 */
public class EapFailureNotifier {
    private static final String TAG = "EapFailureNotifier";
    @VisibleForTesting
    static final String ERROR_MESSAGE_OVERLAY_PREFIX = "wifi_eap_error_message_code_";
    @VisibleForTesting
    static final String ERROR_MESSAGE_OVERLAY_UNKNOWN_ERROR_CODE =
            "wifi_eap_error_message_unknown_error_code";
    @VisibleForTesting
    static final String CONFIG_EAP_FAILURE_DISABLE_THRESHOLD =
            "config_wifiDisableReasonAuthenticationFailureCarrierSpecificThreshold";
    @VisibleForTesting
    static final String CONFIG_EAP_FAILURE_DISABLE_DURATION =
            "config_wifiDisableReasonAuthenticationFailureCarrierSpecificDurationMs";
    // Special error message that results in the EAP failure code to get ignored on devices pre
    // Android 13.
    public static final String ERROR_MESSAGE_IGNORE_ON_PRE_ANDROID_13 = "IgnorePreAndroid13";

    private static final long CANCEL_TIMEOUT_MILLISECONDS = 5 * 60 * 1000;
    private final WifiContext mContext;
    private final WifiNotificationManager mNotificationManager;
    private final FrameworkFacade mFrameworkFacade;
    private final WifiCarrierInfoManager mWifiCarrierInfoManager;
    private final WifiGlobals mWifiGlobals;

    // Unique ID associated with the notification.
    public static final int NOTIFICATION_ID = SystemMessage.NOTE_WIFI_EAP_FAILURE;
    private String mCurrentShownSsid;

    public EapFailureNotifier(WifiContext context, FrameworkFacade frameworkFacade,
            WifiCarrierInfoManager wifiCarrierInfoManager,
            WifiNotificationManager wifiNotificationManager,
            WifiGlobals wifiGlobals) {
        mContext = context;
        mFrameworkFacade = frameworkFacade;
        mWifiCarrierInfoManager = wifiCarrierInfoManager;
        mNotificationManager = wifiNotificationManager;
        mWifiGlobals = wifiGlobals;
    }

    /**
     * Invoked when EAP failure occurs.
     *
     * @param errorCode error code which delivers from supplicant
     * @param showNotification whether to display the notification
     * @return CarrierSpecificEapFailureConfig if the receiving error code is found in wifi resource
     *         null if not found.
     */
    public @Nullable WifiBlocklistMonitor.CarrierSpecificEapFailureConfig onEapFailure(
            int errorCode, WifiConfiguration config, boolean showNotification) {
        if (errorCode < 0) {
            // EapErrorCode is defined as an unsigned uint32_t in ISupplicantStaIfaceCallback, so
            // only consider non-negative error codes for carrier-specific error messages.
            return null;
        }
        int carrierId = config.carrierId == TelephonyManager.UNKNOWN_CARRIER_ID
                ? mWifiCarrierInfoManager.getDefaultDataSimCarrierId()
                : config.carrierId;
        WifiStringResourceWrapper sr = mContext.getStringResourceWrapper(
                mWifiCarrierInfoManager.getBestMatchSubscriptionId(config),
                carrierId);
        String errorMessage = sr.getString(ERROR_MESSAGE_OVERLAY_PREFIX + errorCode, config.SSID);
        if (SdkLevel.isAtLeastT()) {
            if (errorMessage == null) {
                // Use the generic error message if the code does not match any known code.
                errorMessage = sr.getString(ERROR_MESSAGE_OVERLAY_UNKNOWN_ERROR_CODE, config.SSID);
            }
        } else if (errorMessage != null
                && errorMessage.contains(ERROR_MESSAGE_IGNORE_ON_PRE_ANDROID_13)) {
            return null;
        }
        if (TextUtils.isEmpty(errorMessage)) return null;
        WifiBlocklistMonitor.CarrierSpecificEapFailureConfig eapFailureConfig =
                new WifiBlocklistMonitor.CarrierSpecificEapFailureConfig(
                        sr.getInt(CONFIG_EAP_FAILURE_DISABLE_THRESHOLD, 1),
                        sr.getInt(CONFIG_EAP_FAILURE_DISABLE_DURATION, -1),
                        true);
        if (SdkLevel.isAtLeastU()) {
            WifiBlocklistMonitor.CarrierSpecificEapFailureConfig carrierSpecificOverride =
                    mWifiGlobals.getCarrierSpecificEapFailureConfig(carrierId, errorCode);
            if (carrierSpecificOverride != null) {
                eapFailureConfig = carrierSpecificOverride;
            }
        }

        StatusBarNotification[] activeNotifications = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification activeNotification : activeNotifications) {
            if ((activeNotification.getId() == NOTIFICATION_ID)
                    && TextUtils.equals(config.SSID, mCurrentShownSsid)) {
                return eapFailureConfig;
            }
        }

        if (showNotification && eapFailureConfig.displayNotification) {
            showNotification(errorMessage, config.SSID);
        }
        return eapFailureConfig;
    }

    /**
     * Display eap error notification which defined by carrier.
     *
     * @param ssid Error Message which defined by carrier
     */
    private void showNotification(String errorMessage, String ssid) {
        String settingsPackage = mFrameworkFacade.getSettingsPackageName(mContext);
        if (settingsPackage == null) return;
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS)
                .setPackage(settingsPackage);
        Notification.Builder builder = mFrameworkFacade.makeNotificationBuilder(mContext,
                WifiService.NOTIFICATION_NETWORK_ALERTS)
                .setAutoCancel(true)
                .setTimeoutAfter(CANCEL_TIMEOUT_MILLISECONDS)
                .setSmallIcon(Icon.createWithResource(mContext.getWifiOverlayApkPkgName(),
                        com.android.wifi.resources.R.drawable.stat_notify_wifi_in_range))
                .setContentTitle(mContext.getString(
                        com.android.wifi.resources.R.string.wifi_available_title_failed_to_connect))
                .setContentText(errorMessage)
                .setStyle(new Notification.BigTextStyle().bigText(errorMessage))
                .setContentIntent(mFrameworkFacade.getActivity(
                        mContext, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setColor(mContext.getResources().getColor(
                        android.R.color.system_notification_accent_color));
        mNotificationManager.notify(NOTIFICATION_ID,
                builder.build());
        mCurrentShownSsid = ssid;
    }

    /**
     * Allow tests to modify mCurrentShownSsid
     */
    @VisibleForTesting
    void setCurrentShownSsid(String currentShownSsid) {
        mCurrentShownSsid = currentShownSsid;
    }
}
