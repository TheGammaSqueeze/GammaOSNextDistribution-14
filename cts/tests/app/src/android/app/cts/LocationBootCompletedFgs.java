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

package android.app.cts;

import static android.app.cts.fgs.bootcompleted.BootCompletedFgsStartTest.BOOT_COMPLETED_FGS_FINISHED;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class LocationBootCompletedFgs extends Service {

    private static final int FGS_NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID =
            LocationBootCompletedFgs.class.getSimpleName();

    public static int types = FOREGROUND_SERVICE_TYPE_LOCATION;
    @Override
    public void onCreate() {
        createNotificationChannelId(this, NOTIFICATION_CHANNEL_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // When this service is started, make it a foreground service
        final Notification.Builder builder =
                new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.btn_star)
                        .setContentTitle(NOTIFICATION_CHANNEL_ID)
                        .setContentText(LocationBootCompletedFgs.class.getName());
        try {
            startForeground(FGS_NOTIFICATION_ID, builder.build(), types);
            Intent finishedStartIntent = new Intent();
            finishedStartIntent.setAction(BOOT_COMPLETED_FGS_FINISHED);
            this.sendBroadcast(finishedStartIntent);
        } catch (Exception e) {
            // we will rely on the latch to track if the FGS start was successful
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Create a notification channel. */
    private static void createNotificationChannelId(Context context, String id) {
        final NotificationManager nm =
                context.getSystemService(NotificationManager.class);
        final CharSequence name = id;
        final String description = LocationBootCompletedFgs.class.getName();
        final int importance = NotificationManager.IMPORTANCE_DEFAULT;
        final NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        nm.createNotificationChannel(channel);
    }
}
