/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.alarmmanager.alarmtestapp.cts.common;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * An empty service, that makes itself as a foreground service, and stops itself.
 */
public class TestService extends Service {
    private static final String CHANNEL_ID = "mychannel";

    private static final int FGS_NOTIFICATION_ID = 1;

    /** Create a notification channel. */
    private static String getNotificationChannelId(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);

        CharSequence name = "my_channel";
        String description = "my channel";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        nm.createNotificationChannel(channel);

        return CHANNEL_ID;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // When this service is started, make it a foreground service, and stop itself.
        Notification.Builder builder =
                new Notification.Builder(this, getNotificationChannelId(this))
                        .setSmallIcon(android.R.drawable.btn_star)
                        .setContentTitle("My FGS")
                        .setContentText("My FGS");
        startForeground(FGS_NOTIFICATION_ID, builder.build());

        new Handler(Looper.getMainLooper()).post(() -> {
            stopSelf();
        });

        return Service.START_NOT_STICKY;
    }
}
