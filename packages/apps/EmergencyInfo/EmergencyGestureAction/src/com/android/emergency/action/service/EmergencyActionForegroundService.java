/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.emergency.action.service;

import static android.app.Notification.FOREGROUND_SERVICE_IMMEDIATE;
import static android.app.NotificationManager.IMPORTANCE_HIGH;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.android.emergency.action.R;
import com.android.emergency.action.broadcast.EmergencyActionBroadcastReceiver;
import com.android.emergency.action.sensoryfeedback.EmergencyActionAlarmHelper;
import com.android.settingslib.emergencynumber.EmergencyNumberUtils;

/**
 * A service that counts down for emergency gesture.
 */
public class EmergencyActionForegroundService extends Service {
    private static final String TAG = "EmergencyActionSvc";
    /** The notification that current service should be started with. */
    private static final String SERVICE_EXTRA_NOTIFICATION = "service.extra.notification";
    /** The remaining time in milliseconds before taking emergency action */
    private static final String SERVICE_EXTRA_REMAINING_TIME_MS = "service.extra.remaining_time_ms";
    /** The alarm volume user setting before triggering this gesture */
    private static final String SERVICE_EXTRA_ALARM_VOLUME = "service.extra.alarm_volume";
    /** Random unique number for the notification */
    private static final int COUNT_DOWN_NOTIFICATION_ID = 0x112;

    private static final long[] TIMINGS = new long[]{200, 20, 20, 20, 20, 100, 20, 600};
    private static final int[] AMPLITUDES = new int[]{0, 39, 82, 139, 213, 0, 127, 0};
    private static final VibrationEffect VIBRATION_EFFECT =
            VibrationEffect.createWaveform(TIMINGS, AMPLITUDES, /*repeat=*/ -1);

    private TelecomManager mTelecomManager;
    private Vibrator mVibrator;
    private EmergencyNumberUtils mEmergencyNumberUtils;
    private NotificationManager mNotificationManager;
    private EmergencyActionAlarmHelper mAlarmHelper;


    @Override
    public void onCreate() {
        super.onCreate();
        PackageManager pm = getPackageManager();
        mVibrator = getSystemService(Vibrator.class);
        mNotificationManager = getSystemService(NotificationManager.class);
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            mTelecomManager = getSystemService(TelecomManager.class);
            mEmergencyNumberUtils = new EmergencyNumberUtils(this);
            mAlarmHelper = new EmergencyActionAlarmHelper(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        if (mTelecomManager == null || mEmergencyNumberUtils == null) {
            Log.d(TAG, "Device does not have telephony support, nothing to do");
            stopSelf();
            return START_NOT_STICKY;
        }
        long remainingTimeMs = intent.getLongExtra(SERVICE_EXTRA_REMAINING_TIME_MS, -1);
        if (remainingTimeMs <= 0) {
            Log.d(TAG, "Invalid remaining countdown time, nothing to do");
            stopSelf();
            return START_NOT_STICKY;
        }
        mNotificationManager.createNotificationChannel(buildNotificationChannel(this));
        Notification notification = intent.getParcelableExtra(SERVICE_EXTRA_NOTIFICATION,
                Notification.class);

        // Immediately show notification And now put the service in foreground mode
        startForeground(COUNT_DOWN_NOTIFICATION_ID, notification);
        scheduleEmergencyCallBroadcast(remainingTimeMs);
        // vibration
        mVibrator.vibrate(VIBRATION_EFFECT);
        mAlarmHelper.playWarningSound();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Take notification down
        mNotificationManager.cancel(COUNT_DOWN_NOTIFICATION_ID);
        // Stop sound
        mAlarmHelper.stopWarningSound();
        // Stop vibrate
        mVibrator.cancel();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Build {@link Intent} that launches foreground service for emergency gesture's countdown
     * action
     */
    public static Intent newStartCountdownIntent(
            Context context, long remainingTimeMs, int userSetAlarmVolume) {
        return new Intent(context, EmergencyActionForegroundService.class)
                .putExtra(SERVICE_EXTRA_REMAINING_TIME_MS, remainingTimeMs)
                .putExtra(SERVICE_EXTRA_ALARM_VOLUME, userSetAlarmVolume)
                .putExtra(SERVICE_EXTRA_NOTIFICATION,
                        buildCountDownNotification(context, remainingTimeMs));
    }

    /** End all work in this service and remove the foreground notification. */
    public static void stopService(Context context) {
        // Cancel previously scheduled eCall broadcast
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        alarmManager.cancel(
                EmergencyActionBroadcastReceiver.newCallEmergencyPendingIntent(context));
        // Stop service
        context.stopService(new Intent(context, EmergencyActionForegroundService.class));
    }

    /**
     * Creates a {@link NotificationChannel} object for emergency action notifications.
     *
     * <p/> Note this does not create notification channel in the system.
     */
    private static NotificationChannel buildNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel("EmergencyGesture",
                context.getString(R.string.emergency_action_title), IMPORTANCE_HIGH);
        return channel;
    }

    private static Notification buildCountDownNotification(Context context, long remainingTimeMs) {
        NotificationChannel channel = buildNotificationChannel(context);
        EmergencyNumberUtils emergencyNumberUtils = new EmergencyNumberUtils(context);
        long targetTimeMs = SystemClock.elapsedRealtime() + remainingTimeMs;
        RemoteViews contentView =
                new RemoteViews(context.getPackageName(),
                        R.layout.emergency_action_count_down_notification);
        contentView.setTextViewText(R.id.notification_text,
                context.getString(R.string.emergency_action_subtitle,
                        emergencyNumberUtils.getPoliceNumber()));
        contentView.setChronometerCountDown(R.id.chronometer, true);
        contentView.setChronometer(
                R.id.chronometer,
                targetTimeMs,
                /* format= */ null,
                /* started= */ true);
        return new Notification.Builder(context, channel.getId())
                .setColor(context.getColor(R.color.emergency_primary))
                .setSmallIcon(R.drawable.ic_launcher_settings)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setAutoCancel(false)
                .setOngoing(true)
                // This is set to make sure that device doesn't vibrate twice when client
                // attempts to post currently displayed notification again
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setCustomContentView(contentView)
                .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(new Notification.Action.Builder(null, context.getText(R.string.cancel),
                        EmergencyActionBroadcastReceiver.newCancelCountdownPendingIntent(
                                context)).build())
                .build();
    }

    private void scheduleEmergencyCallBroadcast(long remainingTimeMs) {
        long alarmTimeMs = System.currentTimeMillis() + remainingTimeMs;
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, alarmTimeMs,
                EmergencyActionBroadcastReceiver.newCallEmergencyPendingIntent(this));
    }

}
