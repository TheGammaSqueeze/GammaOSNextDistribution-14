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

package android.os.inattentivesleeptests;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

/** Service that holds a partial wakelock until stopped */
public class PartialWakeLockService extends Service {
    private static final String TAG = "PartialWakeLockService";
    private static final String NOTIFICATION_CHANNEL_ID = "Inattentive Sleep Tests";

    private PowerManager.WakeLock mWakeLock;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mWakeLock == null) {
            PowerManager powerManager = getSystemService(PowerManager.class);
            mWakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, TAG);
            mWakeLock.setReferenceCounted(false);
        }
        mWakeLock.acquire();

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Inattentive Sleep CTS Tests")
                .setContentText("Keeping partial wakelock during the test")
                .setSmallIcon(android.R.drawable.ic_secure)
                .build();

        startForeground(1, notification);

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }
}
