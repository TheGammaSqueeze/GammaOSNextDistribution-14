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

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.os.Handler;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.wifi.resources.R;

/**
 * This class may be used to launch notifications when wifi connections fail.
 */
public class ConnectionFailureNotifier {
    private static final String TAG = "ConnectionFailureNotifier";

    private final WifiContext mContext;
    private final FrameworkFacade mFrameworkFacade;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiConnectivityManager mWifiConnectivityManager;
    private final WifiNotificationManager mNotificationManager;
    private final WifiDialogManager mWifiDialogManager;
    private final Handler mHandler;
    private final ConnectionFailureNotificationBuilder mConnectionFailureNotificationBuilder;

    public ConnectionFailureNotifier(
            WifiContext context,
            FrameworkFacade framework,
            WifiConfigManager wifiConfigManager,
            WifiConnectivityManager wifiConnectivityManager,
            Handler handler,
            WifiNotificationManager notificationManager,
            ConnectionFailureNotificationBuilder connectionFailureNotificationBuilder,
            WifiDialogManager wifiDialogManager) {
        mContext = context;
        mFrameworkFacade = framework;
        mWifiConfigManager = wifiConfigManager;
        mWifiConnectivityManager = wifiConnectivityManager;
        mNotificationManager = notificationManager;
        mWifiDialogManager = wifiDialogManager;
        mHandler = handler;
        mConnectionFailureNotificationBuilder = connectionFailureNotificationBuilder;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectionFailureNotificationBuilder
                .ACTION_SHOW_SET_RANDOMIZATION_DETAILS);
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (TextUtils.equals(action, ConnectionFailureNotificationBuilder
                                .ACTION_SHOW_SET_RANDOMIZATION_DETAILS)) {
                            int networkId = intent.getIntExtra(
                                    ConnectionFailureNotificationBuilder
                                            .RANDOMIZATION_SETTINGS_NETWORK_ID,
                                    WifiConfiguration.INVALID_NETWORK_ID);
                            String ssidAndSecurityType = intent.getStringExtra(
                                    ConnectionFailureNotificationBuilder
                                            .RANDOMIZATION_SETTINGS_NETWORK_SSID);
                            showRandomizationSettingsDialog(networkId, ssidAndSecurityType);
                        }
                    }
                }, filter);
    }

    /**
     * Shows a notification which will bring up a dialog which offers the user an option to disable
     * MAC randomization on |networkdId|.
     * @param networkId
     */
    public void showFailedToConnectDueToNoRandomizedMacSupportNotification(int networkId) {
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(networkId);
        if (config == null) {
            return;
        }
        Notification notification = mConnectionFailureNotificationBuilder
                .buildNoMacRandomizationSupportNotification(config);
        mNotificationManager.notify(SystemMessage.NOTE_NETWORK_NO_MAC_RANDOMIZATION_SUPPORT,
                notification);
    }

    /**
     * Class to show a AlertDialog which notifies the user of a network not being privacy
     * compliant and then suggests an action.
     */
    private void showRandomizationSettingsDialog(int networkId, String ssidAndSecurityType) {
        Resources res = mContext.getResources();
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(networkId);
        // Make sure the networkId is still pointing to the correct WifiConfiguration since
        // there might be a large time gap between when the notification shows and when
        // it's tapped.
        if (config == null || !TextUtils.equals(ssidAndSecurityType,
                config.getSsidAndSecurityTypeString())) {
            String message = res.getString(
                    R.string.wifi_disable_mac_randomization_dialog_network_not_found);
            mFrameworkFacade.showToast(mContext, message);
            return;
        }

        mWifiDialogManager.createSimpleDialog(
                res.getString(R.string.wifi_disable_mac_randomization_dialog_title),
                res.getString(R.string.wifi_disable_mac_randomization_dialog_message, config.SSID),
                res.getString(R.string.wifi_disable_mac_randomization_dialog_confirm_text),
                res.getString(android.R.string.cancel),
                null /* neutralButtonText */,
                new WifiDialogManager.SimpleDialogCallback() {
                    @Override
                    public void onPositiveButtonClicked() {
                        config.macRandomizationSetting =
                                WifiConfiguration.RANDOMIZATION_NONE;
                        mWifiConfigManager.addOrUpdateNetwork(config, Process.SYSTEM_UID);
                        WifiConfiguration updatedConfig =
                                mWifiConfigManager.getConfiguredNetwork(config.networkId);
                        if (updatedConfig.macRandomizationSetting
                                == WifiConfiguration.RANDOMIZATION_NONE) {
                            String message = mContext.getResources().getString(
                                    R.string.wifi_disable_mac_randomization_dialog_success);
                            mFrameworkFacade.showToast(mContext, message);
                            mWifiConfigManager.enableNetwork(updatedConfig.networkId, true,
                                    Process.SYSTEM_UID, null);
                            mWifiConnectivityManager.forceConnectivityScan(
                                    ClientModeImpl.WIFI_WORK_SOURCE);
                        } else {
                            // Shouldn't ever fail, but here for completeness
                            String message = mContext.getResources().getString(
                                    R.string.wifi_disable_mac_randomization_dialog_failure);
                            mFrameworkFacade.showToast(mContext, message);
                            Log.e(TAG, "Failed to modify mac randomization setting");
                        }
                    }

                    @Override
                    public void onNegativeButtonClicked() {
                        // Do nothing.
                    }

                    @Override
                    public void onNeutralButtonClicked() {
                        // Not used.
                    }

                    @Override
                    public void onCancelled() {
                        // Do nothing.
                    }
                },
                new WifiThreadRunner(mHandler)).launchDialog();
    }
}
