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

package com.android.server.wifi;

import static com.android.server.wifi.WifiService.NOTIFICATION_APM_ALERTS;
import static com.android.server.wifi.WifiService.NOTIFICATION_NETWORK_ALERTS;
import static com.android.server.wifi.WifiService.NOTIFICATION_NETWORK_AVAILABLE;
import static com.android.server.wifi.WifiService.NOTIFICATION_NETWORK_STATUS;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.wifi.resources.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification manager for Wifi. All notification will be sent to the current user.
 */
public class WifiNotificationManager {
    private static final String TAG = "WifiNotificationManager";
    private static final String NOTIFICATION_TAG = "com.android.wifi";

    private final Context mContext;
    private NotificationManager mNotificationManager;

    public WifiNotificationManager(Context context) {
        mContext = context;
    }

    private NotificationManager getNotificationManagerForCurrentUser() {
        try {
            return mContext.createPackageContextAsUser(mContext.getPackageName(), 0,
                    UserHandle.CURRENT).getSystemService(NotificationManager.class);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get NotificationManager for current user: " + e.getMessage());
        }
        return null;
    }

    /**
     * Update to the notification manager fot current user and create notification channels.
     */
    // TODO(b/193460475): Remove when tooling supports APIS moving from SystemApi to public
    @SuppressLint("NewApi")
    public void createNotificationChannels() {
        if (mNotificationManager != null) {
            // Cancel all active notification from Wi-Fi Stack.
            cleanAllWifiNotification();
        }
        mNotificationManager = getNotificationManagerForCurrentUser();
        if (mNotificationManager == null) {
            return;
        }
        List<NotificationChannel> channelsList = new ArrayList<>();
        final NotificationChannel networkStatusChannel = new NotificationChannel(
                NOTIFICATION_NETWORK_STATUS,
                mContext.getResources().getString(
                        R.string.notification_channel_network_status),
                NotificationManager.IMPORTANCE_LOW);
        channelsList.add(networkStatusChannel);

        final NotificationChannel networkAlertsChannel = new NotificationChannel(
                NOTIFICATION_NETWORK_ALERTS,
                mContext.getResources().getString(
                        R.string.notification_channel_network_alerts),
                NotificationManager.IMPORTANCE_HIGH);
        networkAlertsChannel.setBlockable(true);
        channelsList.add(networkAlertsChannel);

        final NotificationChannel networkAvailable = new NotificationChannel(
                NOTIFICATION_NETWORK_AVAILABLE,
                mContext.getResources().getString(
                        R.string.notification_channel_network_available),
                NotificationManager.IMPORTANCE_LOW);
        networkAvailable.setBlockable(true);
        channelsList.add(networkAvailable);

        final NotificationChannel apmAlertsChannel = new NotificationChannel(
                NOTIFICATION_APM_ALERTS,
                mContext.getResources().getString(
                        R.string.notification_channel_apm_alerts),
                NotificationManager.IMPORTANCE_HIGH);
        apmAlertsChannel.setBlockable(true);
        channelsList.add(apmAlertsChannel);

        mNotificationManager.createNotificationChannels(channelsList);
    }

    private void cleanAllWifiNotification() {
        for (StatusBarNotification notification : getActiveNotifications()) {
            if (NOTIFICATION_TAG.equals(notification.getTag())
                    && notification.getId() != SystemMessage.NOTE_WIFI_APM_NOTIFICATION) {
                cancel(notification.getId());
            }
        }
    }

    /**
     * Send notification to the current user.
     */
    public void notify(int id, Notification notification) {
        if (mNotificationManager == null) {
            return;
        }
        mNotificationManager.notify(NOTIFICATION_TAG, id, notification);
    }

    /**
     * Cancel the notification fot current user.
     */
    public void cancel(int id) {
        if (mNotificationManager == null) {
            return;
        }
        mNotificationManager.cancel(NOTIFICATION_TAG, id);
    }

    /**
     * Get active notifications for current user.
     */
    public StatusBarNotification[] getActiveNotifications() {
        if (mNotificationManager == null) {
            return new StatusBarNotification[0];
        }
        return mNotificationManager.getActiveNotifications();
    }
}
