/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.verifier.net;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Foreground service to avoid CtsVerifier is being frozen while ConnectivityBackgroundTestActivity
 * is running background connectivity check
 */
public class ConnectivityForegroundService extends Service {
    public static final String TAG = "ConnectivityForegroundService";
    private static final String NOTIFICATION_CHANNEL_ID = "ctsVerifier/" + TAG;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(
                new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        NOTIFICATION_CHANNEL_ID,
                        NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification =
                new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID).build();
        startForeground(1 /* id */, notification);

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
