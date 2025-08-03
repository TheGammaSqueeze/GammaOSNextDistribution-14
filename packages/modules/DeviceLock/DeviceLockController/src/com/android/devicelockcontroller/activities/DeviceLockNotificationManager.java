/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.activities;

import static com.android.devicelockcontroller.activities.ProvisioningActivity.EXTRA_SHOW_PROVISION_FAILED_UI_ON_START;

import static com.google.common.base.Preconditions.checkNotNull;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;
import com.android.devicelockcontroller.util.StringUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * A utility class used to send notification.
 */
public final class DeviceLockNotificationManager {

    private static final String TAG = "DeviceLockNotificationManager";

    private static final String PROVISION_NOTIFICATION_CHANNEL_ID_BASE = "devicelock-provision";
    @VisibleForTesting
    public static final String DEVICE_RESET_NOTIFICATION_TAG = "devicelock-device-reset";
    @VisibleForTesting
    public static final int DEVICE_RESET_NOTIFICATION_ID = 0;
    private static final int DEFER_PROVISIONING_NOTIFICATION_ID = 1;

    private static final ListeningExecutorService sListeningExecutorService =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    /**
     * Similar to {@link #sendDeviceResetNotification(Context, int)}, except that:
     * 1. The number of days to reset is always one.
     * 2. The notification is ongoing.
     */
    public static void sendDeviceResetInOneDayOngoingNotification(Context context) {
        sendDeviceResetNotification(context, /* days= */ 1, /* ongoing= */ true);
    }

    /**
     * Send the device reset notification. The call is thread safe and can be called from any
     * thread.
     *
     * @param context the context where the notification will be sent out
     * @param days    the number of days the reset will happen
     */
    public static void sendDeviceResetNotification(Context context, int days) {
        sendDeviceResetNotification(context, days, /* ongoing= */ false);
    }

    /**
     * Send the device reset timer notification.
     *
     * @param context       the context where the notification will be sent out
     * @param countDownBase the time when device will be reset in
     *                      {@link SystemClock#elapsedRealtime()}.
     */
    public static void sendDeviceResetTimerNotification(Context context, long countDownBase) {
        ListenableFuture<String> channelIdFuture = createNotificationChannel(context);
        ListenableFuture<String> kioskAppProviderNameFuture =
                SetupParametersClient.getInstance().getKioskAppProviderName();
        ListenableFuture<Void> result =
                Futures.whenAllSucceed(channelIdFuture, kioskAppProviderNameFuture).call(() -> {
                    String channelId = Futures.getDone(channelIdFuture);
                    String providerName = Futures.getDone(kioskAppProviderNameFuture);
                    Notification notification =
                            new NotificationCompat.Builder(context,
                                    channelId)
                                    .setSmallIcon(R.drawable.ic_action_lock)
                                    .setColor(context.getResources()
                                            .getColor(R.color.notification_background_color,
                                                    context.getTheme()))
                                    .setOngoing(true)
                                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                                    .setCustomContentView(
                                            buildResetTimerNotif(countDownBase,
                                                    providerName,
                                                    false))
                                    .setCustomBigContentView(
                                            buildResetTimerNotif(countDownBase, providerName,
                                                    true))
                                    .build();
                    NotificationManagerCompat notificationManager =
                            NotificationManagerCompat.from(context);
                    notificationManager.notify(DEVICE_RESET_NOTIFICATION_TAG,
                            DEVICE_RESET_NOTIFICATION_ID, notification);
                    return null;
                }, sListeningExecutorService);

        Futures.addCallback(result, new FutureCallback<>() {
            @Override
            public void onSuccess(Void result) {
                LogUtil.d(TAG, "send device reset notification");
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to create device reset notification", t);
            }
        }, sListeningExecutorService);
    }

    private static RemoteViews buildResetTimerNotif(long countDownBase, String providerName,
            boolean isExpanded) {
        Context appContext = DeviceLockControllerApplication.getAppContext();
        RemoteViews content = new RemoteViews(appContext.getPackageName(),
                isExpanded ? R.layout.reset_timer_notif_large : R.layout.reset_timer_notif_small);
        content.setChronometer(R.id.reset_timer_title, countDownBase,
                appContext.getString(R.string.device_reset_timer_notification_title),
                /* started= */ true);
        if (isExpanded) {
            content.setCharSequence(R.id.reset_content, "setText",
                    appContext.getString(R.string.device_reset_notification_content, providerName));
        }
        return content;
    }

    private static void sendDeviceResetNotification(Context context, int days, boolean ongoing) {
        // TODO: check/request permission first

        // re-creating the same notification channel is essentially no-op
        ListenableFuture<String> channelIdFuture = createNotificationChannel(context);
        ListenableFuture<Notification> notificationFuture = Futures.transformAsync(channelIdFuture,
                channelId -> createDeviceResetNotification(context, days, ongoing, channelId),
                sListeningExecutorService);
        Futures.addCallback(notificationFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Notification notification) {
                        LogUtil.d(TAG, "send device reset notification");
                        NotificationManagerCompat notificationManager =
                                NotificationManagerCompat.from(context);
                        notificationManager.notify(DEVICE_RESET_NOTIFICATION_TAG,
                                DEVICE_RESET_NOTIFICATION_ID, notification);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to create device reset notification", t);
                    }
                }, sListeningExecutorService);
    }

    /**
     * Send the device deferred provisioning notification.
     * (POST_NOTIFICATION already requested permission in ProvisionInfoFragment).
     *
     * @param context        the context where the notification will be sent out
     * @param resumeDateTime the time when device will show the notification
     * @Param pendingIntent  pending intent for the notification
     */
    @SuppressLint("MissingPermission")
    public static void sendDeferredProvisioningNotification(Context context,
            LocalDateTime resumeDateTime, PendingIntent pendingIntent) {
        ListenableFuture<String> channelIdFuture = createNotificationChannel(context);

        Futures.addCallback(channelIdFuture, new FutureCallback<String>() {
            @Override
            public void onSuccess(String channelId) {
                DateTimeFormatter timeFormatter =
                        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
                String enrollmentResumeTime = timeFormatter.format(resumeDateTime);
                NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                        context, channelId)
                        .setContentTitle(context.getString(R.string.device_enrollment_header_text))
                        .setContentText(context
                                .getString(R.string.device_enrollment_notification_body_text,
                                        enrollmentResumeTime))
                        .setSmallIcon(R.drawable.ic_action_lock)
                        .setColor(context.getResources()
                                .getColor(R.color.notification_background_color,
                                        context.getTheme()))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setOngoing(true);
                NotificationManagerCompat notificationManager =
                        NotificationManagerCompat.from(context);
                notificationManager.notify(DEFER_PROVISIONING_NOTIFICATION_ID,
                        notificationBuilder.build());
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to create deferred provisioning notification", t);
            }
        }, sListeningExecutorService);

    }

    static void cancelDeferredProvisioningNotification(Context context) {
        LogUtil.d(TAG, "cancelDeferredEnrollmentNotification");
        NotificationManagerCompat.from(context).cancel(DEFER_PROVISIONING_NOTIFICATION_ID);
    }

    private static ListenableFuture<Notification> createDeviceResetNotification(Context context,
            int days, boolean ongoing, String channelId) {
        return Futures.transform(SetupParametersClient.getInstance().getKioskAppProviderName(),
                providerName ->
                        new NotificationCompat.Builder(context,
                                channelId)
                                .setSmallIcon(R.drawable.ic_action_lock)
                                .setColor(context.getResources()
                                        .getColor(R.color.notification_background_color,
                                                context.getTheme()))
                                .setOngoing(ongoing)
                                .setContentTitle(StringUtil.getPluralString(context, days,
                                        R.string.device_reset_in_days_notification_title))
                                .setContentText(context.getString(
                                        R.string.device_reset_notification_content,
                                        providerName))
                                .setContentIntent(
                                        PendingIntent.getActivity(context, /* requestCode= */0,
                                                new Intent(context,
                                                        ProvisioningActivity.class).putExtra(
                                                        EXTRA_SHOW_PROVISION_FAILED_UI_ON_START,
                                                        true),
                                                PendingIntent.FLAG_IMMUTABLE)).build(),
                context.getMainExecutor());
    }

    private static String getProvisionNotificationChannelId(Context context) {
        return PROVISION_NOTIFICATION_CHANNEL_ID_BASE + "-"
                + UserParameters.getNotificationChannelIdSuffix(context);
    }

    private static String createProvisionNotificationChannelId(Context context) {
        String notificationChannelIdSuffix = UUID.randomUUID().toString();
        String provisioningChannelId = PROVISION_NOTIFICATION_CHANNEL_ID_BASE
                + "-" + notificationChannelIdSuffix;
        UserParameters.setNotificationChannelIdSuffix(context, notificationChannelIdSuffix);

        return provisioningChannelId;
    }

    // Create a notification channel and return a future with its ID.
    private static ListenableFuture<String> createNotificationChannel(Context context) {
        return sListeningExecutorService.submit(() -> {
            String provisioningChannelId = getProvisionNotificationChannelId(context);
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            checkNotNull(notificationManager);
            NotificationChannel notificationChannel =
                    notificationManager.getNotificationChannel(provisioningChannelId);
            if (notificationChannel != null
                    && notificationChannel.getImportance() != NotificationManager.IMPORTANCE_HIGH) {
                // Channel importance has been changed by user
                LogUtil.w(TAG, "Channel " + provisioningChannelId
                        + " importance changed by user, deleting it");
                notificationManager.deleteNotificationChannel(provisioningChannelId);
                notificationChannel = null;
            }

            if (notificationChannel == null) {
                provisioningChannelId = createProvisionNotificationChannelId(context);
                notificationChannel = new NotificationChannel(
                        provisioningChannelId,
                        context.getString(R.string.provision_notification_channel_name),
                        NotificationManager.IMPORTANCE_HIGH);
                LogUtil.i(TAG, "New notification channel: " + provisioningChannelId);
            }
            notificationManager.createNotificationChannel(notificationChannel);

            return provisioningChannelId;
        });
    }
}
