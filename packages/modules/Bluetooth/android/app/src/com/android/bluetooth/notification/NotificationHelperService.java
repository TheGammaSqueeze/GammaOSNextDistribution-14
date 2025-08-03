/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.notification;

import static java.util.Objects.requireNonNull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.Pair;

import com.android.bluetooth.R;
import com.android.internal.messages.SystemMessageProto.SystemMessage;

import java.util.Map;

public class NotificationHelperService extends Service {
    private static final String TAG = NotificationHelperService.class.getSimpleName();

    // Keeps track of whether wifi and bt remains on notification was shown
    private static final String APM_WIFI_BT_NOTIFICATION = "apm_wifi_bt_notification";
    // Keeps track of whether bt remains on notification was shown
    private static final String APM_BT_NOTIFICATION = "apm_bt_notification";
    // Keeps track of whether user enabling bt notification was shown
    private static final String APM_BT_ENABLED_NOTIFICATION = "apm_bt_enabled_notification";
    // Keeps track of whether auto on enabling bt notification was shown
    private static final String AUTO_ON_BT_ENABLED_NOTIFICATION = "auto_on_bt_enabled_notification";

    private static final String NOTIFICATION_TAG = "com.android.bluetooth";
    private static final String NOTIFICATION_CHANNEL = "notification_toggle_channel";
    private static final int NOTIFICATION_GROUP = R.string.bluetooth_notification_group;

    private static final String NOTIFICATION_ACTION =
            "android.bluetooth.notification.action.SEND_TOGGLE_NOTIFICATION";
    private static final String NOTIFICATION_EXTRA =
            "android.bluetooth.notification.extra.NOTIFICATION_REASON";

    private static final String AUTO_ON_USER_ACTION =
            "android.bluetooth.notification.action.AUTO_ON_USER_ACTION";
    private static final String AUTO_ON_USER_EXTRA =
            "android.bluetooth.notification.extra.AUTO_ON_DISABLE";

    private static final Map<String, Pair<Integer /* titleId */, Integer /* messageId */>>
            NOTIFICATION_MAP =
                    Map.of(
                            APM_WIFI_BT_NOTIFICATION,
                            Pair.create(
                                    R.string.bluetooth_and_wifi_stays_on_title,
                                    R.string.bluetooth_and_wifi_stays_on_message),
                            APM_BT_NOTIFICATION,
                            Pair.create(
                                    R.string.bluetooth_stays_on_title,
                                    R.string.bluetooth_stays_on_message),
                            APM_BT_ENABLED_NOTIFICATION,
                            Pair.create(
                                    R.string.bluetooth_enabled_apm_title,
                                    R.string.bluetooth_enabled_apm_message),
                            AUTO_ON_BT_ENABLED_NOTIFICATION,
                            Pair.create(
                                    R.string.bluetooth_enabled_auto_on_title,
                                    R.string.bluetooth_enabled_auto_on_message));

    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is not a bound service
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case NOTIFICATION_ACTION -> {
                sendToggleNotification(intent.getStringExtra(NOTIFICATION_EXTRA));
            }
            case AUTO_ON_USER_ACTION -> {
                autoOnUserAction(intent.getBooleanExtra(AUTO_ON_USER_EXTRA, false));
            }
        }
        return Service.START_NOT_STICKY;
    }

    private void sendToggleNotification(String notificationReason) {
        String logHeader = "sendToggleNotification(" + notificationReason + "): ";
        Pair<Integer, Integer> notificationContent = NOTIFICATION_MAP.get(notificationReason);
        if (notificationContent == null) {
            Log.e(TAG, logHeader + "unknown action");
            return;
        }

        if (!isFirstTimeNotification(notificationReason)) {
            Log.d(TAG, logHeader + "already displayed");
            return;
        }
        Settings.Secure.putInt(getContentResolver(), notificationReason, 1);

        Log.d(TAG, logHeader + "sending");

        NotificationManager notificationManager =
                requireNonNull(getSystemService(NotificationManager.class));
        String tag = NOTIFICATION_TAG + "/" + notificationReason;
        for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
            if (tag.equals(notification.getTag())) {
                notificationManager.cancel(tag, notification.getId());
            }
        }

        notificationManager.createNotificationChannel(
                new NotificationChannel(
                        NOTIFICATION_CHANNEL,
                        getString(NOTIFICATION_GROUP),
                        NotificationManager.IMPORTANCE_HIGH));

        String title = getString(notificationContent.first);
        String message = getString(notificationContent.second);

        Notification.Builder builder =
                new Notification.Builder(this, NOTIFICATION_CHANNEL)
                        .setAutoCancel(true)
                        .setLocalOnly(true)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setStyle(new Notification.BigTextStyle().bigText(message))
                        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth);

        if (!notificationReason.equals(AUTO_ON_BT_ENABLED_NOTIFICATION)) {
            // Do not display airplane link when the notification is due to auto_on feature
            String helpLinkUrl = getString(R.string.config_apmLearnMoreLink);
            builder.setContentIntent(
                    PendingIntent.getActivity(
                            this,
                            PendingIntent.FLAG_UPDATE_CURRENT,
                            new Intent(Intent.ACTION_VIEW)
                                    .setData(Uri.parse(helpLinkUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE));
        } else {
            Intent baseIntent =
                    new Intent()
                            .setAction(AUTO_ON_USER_ACTION)
                            .setClass(this, NotificationHelperService.class);
            PendingIntent disablePendingIntent =
                    PendingIntent.getService(
                            this,
                            0,
                            new Intent(baseIntent).putExtra(AUTO_ON_USER_EXTRA, true),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
            builder.addAction(
                    new Notification.Action.Builder(
                                    Icon.createWithResource(this, R.drawable.ic_bluetooth_settings),
                                    getString(R.string.bluetooth_disable_auto_on),
                                    disablePendingIntent)
                            .build());
        }

        notificationManager.notify(
                tag, SystemMessage.ID.NOTE_BT_APM_NOTIFICATION_VALUE, builder.build());
    }

    /** Return whether the notification has been shown */
    private boolean isFirstTimeNotification(String name) {
        return Settings.Secure.getInt(getContentResolver(), name, 0) == 0;
    }

    private void autoOnUserAction(boolean disableAutoOn) {
        if (disableAutoOn) {
            getSystemService(BluetoothManager.class).getAdapter().setAutoOnEnabled(false);
        }
    }
}
