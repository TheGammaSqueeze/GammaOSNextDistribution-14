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

package com.google.android.tv.btservices;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_MAX;

import android.app.Notification;
import android.app.Notification.TvExtender;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.android.tv.btservices.settings.RemoteDfuConfirmationActivity;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Singleton that manages all notifications posted by BluetoothDeviceService.
 *
 * <p>This singleton class provides static methods for other components within
 * this service to post Android system notifications. This currently includes
 * low battery notification, device connect/disconnect notification, etc.
 */
public class NotificationCenter {
    private static final String TAG = "Atv.BtServices.NotificationCenter";

    private static final long REMOTE_UPDATE_SNOOZE_PERIOD =
            TimeUnit.MILLISECONDS.convert(12, TimeUnit.HOURS);
    private static final String DFU_NOTIFICATION_CHANNEL = "btservices-remote-dfu-channel";
    private static final String DEFAULT_NOTIFICATION_CHANNEL = "btservices-default-channel";
    private static final String HIGH_PRIORITY_NOTIFICATION_CHANNEL = "btservices-high-channel";
    private static final String CRITICAL_NOTIFICATION_CHANNEL = "btservices-critical-channel";

    private static final int NOTIFICATION_RESET_HOUR_OF_DAY = 3;

    private static class InstanceHolder {
        public static NotificationCenter instance = new NotificationCenter();
    }

    private static NotificationCenter getInstance() {
        return InstanceHolder.instance;
    }

    /**
     * Represents different battery state for battery notification.
     *
     * <p>{@code GOOD} represents good battery level that does not require
     * notification; {@code LOW} represents low battery level, user will be
     * notified to change battery soon; {@code CRITICAL} represents that battery
     * is so low that the device has disconnected and is no longer functional.
     */
    public enum BatteryState {
        GOOD,
        LOW,
        CRITICAL,
        DEPLETED,
    }

    public static synchronized void initialize(Context context) {
        NotificationCenter nc = getInstance();
        nc.mContext = context;
        nc.mNotificationManager = context.getSystemService(NotificationManager.class);
        nc.createNotificationChannel();
    }

    public static synchronized void refreshLowBatteryNotification(
            BluetoothDevice device,
            BatteryState state,
            boolean forceNotification) {
        getInstance().refreshLowBatteryNotificationImpl(device, state, forceNotification);
    }

    public static synchronized void sendDfuNotification(BluetoothDevice device) {
        getInstance().sendDfuNotificationImpl(device);
    }

    public static synchronized void dismissUpdateNotification(BluetoothDevice device) {
        getInstance().dismissUpdateNotificationImpl(device);
    }

    public static synchronized void resetUpdateNotification() {
        getInstance().dfuNotificationSnoozeWatch.clear();
    }

    private void createNotificationChannel() {
        // Create notification channel for firmware update notification
        CharSequence dfuName = mContext.getString(R.string.settings_notif_update_channel_name);
        String dfuDescr = mContext.getString(R.string.settings_notif_update_channel_description);
        ensureNotificationChannel(DFU_NOTIFICATION_CHANNEL, IMPORTANCE_MAX, dfuName, dfuDescr);

        // Create notification channels with different priorities for battery notifications
        CharSequence name = mContext.getString(R.string.settings_notif_battery_channel_name);
        String descr = mContext.getString(R.string.settings_notif_battery_channel_description);
        ensureNotificationChannel(DEFAULT_NOTIFICATION_CHANNEL, IMPORTANCE_LOW, name, descr);
        ensureNotificationChannel(HIGH_PRIORITY_NOTIFICATION_CHANNEL, IMPORTANCE_DEFAULT, name,
                descr);
        ensureNotificationChannel(CRITICAL_NOTIFICATION_CHANNEL, IMPORTANCE_HIGH, name, descr);
    }

    private void ensureNotificationChannel(String channelId,
            int importance, CharSequence name, String description) {
        if (mNotificationManager.getNotificationChannel(channelId) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(channelId, name, importance);
        channel.setDescription(description);
        mNotificationManager.createNotificationChannel(channel);
    }


    private final Map<BluetoothDevice, Integer> dfuNotifications = new HashMap<>();
    private final Map<BluetoothDevice, Integer> lowBatteryNotifications = new HashMap<>();
    private final Map<BluetoothDevice, Integer> criticalBatteryNotifications = new HashMap<>();
    private final Map<BluetoothDevice, Integer> depletedBatteryNotifications = new HashMap<>();
    private final Map<BluetoothDevice, Stopwatch> dfuNotificationSnoozeWatch = new HashMap<>();
    private Context mContext;
    private NotificationManager mNotificationManager;
    private int notificationIdCounter = 0;
    private ZonedDateTime lastNotificationTime =
            Instant.ofEpochSecond(0).atZone(ZoneId.systemDefault());

    private final Ticker ticker = new Ticker() {
        public long read() {
            return android.os.SystemClock.elapsedRealtimeNanos();
        }
    };

    private NotificationCenter() {}

    private void sendDfuNotificationImpl(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "sendDfuNotification: Bluetooth device null");
            return;
        }

        Stopwatch stopwatch = dfuNotificationSnoozeWatch.get(device);
        if (stopwatch != null &&
                stopwatch.elapsed(TimeUnit.MILLISECONDS) < REMOTE_UPDATE_SNOOZE_PERIOD) {
            return;
        }

        if (stopwatch == null) {
            stopwatch = Stopwatch.createStarted(ticker);
            dfuNotificationSnoozeWatch.put(device, stopwatch);
        }

        stopwatch.reset();
        stopwatch.start();

        int notificationId;
        if (dfuNotifications.get(device) != null) {
            notificationId = dfuNotifications.get(device);
        } else {
            notificationId = notificationIdCounter++;
            dfuNotifications.put(device, notificationId);
        }
        final String name = BluetoothUtils.getName(device);
        Intent intent = new Intent(mContext, RemoteDfuConfirmationActivity.class);
        intent.putExtra(RemoteDfuConfirmationActivity.EXTRA_BT_ADDRESS, device.getAddress());
        intent.putExtra(RemoteDfuConfirmationActivity.EXTRA_BT_NAME, name);

        PendingIntent updateIntent = PendingIntent.getActivity(mContext,
                /* requestCode= */ 0, intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Action updateAction = new Notification.Action.Builder(/* icon= */ null,
                mContext.getString(R.string.settings_notif_update_action),
                updateIntent).build();

        Notification.Action dismissAction = new Notification.Action.Builder(/* icon= */ null,
                mContext.getString(R.string.settings_notif_update_dismiss), null)
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_DELETE)
                .build();

        Icon icon = Icon.createWithResource(mContext, R.drawable.ic_official_remote);
        Notification notification = new Notification.Builder(mContext, DFU_NOTIFICATION_CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(mContext.getString(R.string.settings_notif_update_title))
                .setContentText(mContext.getString(R.string.settings_notif_update_text))
                .setContentIntent(updateIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(updateAction)
                .addAction(dismissAction)
                .extend(new TvExtender())
                .build();
        mNotificationManager.notify(notificationId, notification);
    }

    private void dismissUpdateNotificationImpl(BluetoothDevice device) {
        if (dfuNotifications.get(device) != null) {
            int notificationId = dfuNotifications.get(device);
            mNotificationManager.cancel(notificationId);
        }
    }

    private void refreshLowBatteryNotificationImpl(BluetoothDevice device, BatteryState state,
            boolean forceNotification) {
        // Dismiss outdated notifications.
        if (state != BatteryState.LOW) {
            if (lowBatteryNotifications.get(device) != null) {
                int notificationId = lowBatteryNotifications.remove(device);
                mNotificationManager.cancel(notificationId);
            }
        }

        if (state != BatteryState.CRITICAL) {
            if (criticalBatteryNotifications.get(device) != null) {
                int notificationId = criticalBatteryNotifications.remove(device);
                mNotificationManager.cancel(notificationId);
            }
        }

        switch (state) {
            case GOOD:
                // do nothing
                break;

            case LOW:
                postLowBatteryNotification(device, forceNotification);
                break;

            case CRITICAL:
                postCriticalBatteryNotification(device, forceNotification);
                break;

            case DEPLETED:
                postDepletedBatteryNotification(device);
                break;

            default:
                // impossible to reach
                throw new AssertionError();
        }
    }

    private void postLowBatteryNotification(BluetoothDevice device, boolean forced) {
        if ((!forced && lowBatteryNotifications.get(device) != null) || !isNotificationAllowed()) {
            return;
        }

        int notificationId = lowBatteryNotifications.getOrDefault(device, notificationIdCounter);

        if (notificationId == notificationIdCounter) {
            notificationIdCounter++;
            lowBatteryNotifications.put(device, notificationId);
        }

        Log.w(TAG, "Low battery for remote device: " + device);
        Icon icon = Icon.createWithResource(mContext, R.drawable.ic_official_remote);

        Notification notification = new Notification.Builder(mContext, DEFAULT_NOTIFICATION_CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(mContext.getString(R.string.settings_notif_low_battery_title))
                .setContentText(mContext.getString(R.string.settings_notif_low_battery_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .extend(new TvExtender())
                .build();
        mNotificationManager.notify(notificationId, notification);
        logLastNotificationTime();
    }

    private void postCriticalBatteryNotification(BluetoothDevice device, boolean forced) {

        if ((!forced && criticalBatteryNotifications.get(device) != null) ||
                !isNotificationAllowed()) {
            return;
        }

        int notificationId = criticalBatteryNotifications.getOrDefault(device, notificationIdCounter);

        if (notificationId == notificationIdCounter) {
            notificationIdCounter++;
            criticalBatteryNotifications.put(device, notificationId);
        }

        Log.w(TAG, "Critical battery for remote device: " + device);
        Icon icon = Icon.createWithResource(mContext, R.drawable.ic_official_remote);

        Notification notification =
                new Notification.Builder(mContext, HIGH_PRIORITY_NOTIFICATION_CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(mContext.getString(R.string.settings_notif_critical_battery_title))
                .setContentText(mContext.getString(R.string.settings_notif_critical_battery_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .extend(new TvExtender())
                .build();
        mNotificationManager.notify(notificationId, notification);
        logLastNotificationTime();
    }

    private void postDepletedBatteryNotification(BluetoothDevice device) {

        if (depletedBatteryNotifications.get(device) != null || !isNotificationAllowed()) {
            return;
        }

        int notificationId = notificationIdCounter++;
        depletedBatteryNotifications.put(device, notificationId);

        Log.w(TAG, "Depleted battery for remote device: " + device);
        Icon icon = Icon.createWithResource(mContext, R.drawable.ic_official_remote);

        Notification notification =
                new Notification.Builder(mContext, HIGH_PRIORITY_NOTIFICATION_CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(mContext.getString(R.string.settings_notif_depleted_battery_title))
                .setContentText(mContext.getString(R.string.settings_notif_depleted_battery_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .extend(new TvExtender())
                .build();
        mNotificationManager.notify(notificationId, notification);
        logLastNotificationTime();
    }

    private void logLastNotificationTime() {
        lastNotificationTime = Instant.now().atZone(ZoneId.systemDefault());
    }

    private boolean isNotificationAllowed() {
        final ZonedDateTime currentTime = Instant.now().atZone(ZoneId.systemDefault());
        final ZonedDateTime resetTime =
            lastNotificationTime.plusDays(1).withHour(NOTIFICATION_RESET_HOUR_OF_DAY).withMinute(0);

        // return true if it has passed notification reset time
        return resetTime.until(currentTime, ChronoUnit.MILLIS) > 0;
    }
}
