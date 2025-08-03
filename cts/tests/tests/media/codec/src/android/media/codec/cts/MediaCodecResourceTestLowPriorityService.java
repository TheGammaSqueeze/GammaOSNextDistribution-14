/*
 * Copyright 2022 The Android Open Source Project
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
package android.media.codec.cts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;

public class MediaCodecResourceTestLowPriorityService extends Service {
    public static final String ACTION_LOW_PRIORITY_SERVICE_READY =
            "android.media.codec.cts.LOW_PRIORITY_SERVICE_READY";

    public static final String NOTIFICATION_CHANNEL_ID =
            "cts/MediaCodecResourceTestLowPriorityService";

    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        MediaCodecResourceTestLowPriorityService getService() {
            return MediaCodecResourceTestLowPriorityService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private int mNotificationId = 1;

    @Override
    public int onStartCommand(Intent otherIntent, int flags, int startId) {
        Notification notification = showNotification();
        startForeground(mNotificationId++, notification);

        Intent intent = new Intent();
        intent.setAction(ACTION_LOW_PRIORITY_SERVICE_READY);
        intent.putExtra("pid", Process.myPid());
        intent.putExtra("uid", Process.myUid());
        sendBroadcast(intent);

        return START_NOT_STICKY;
    }

    private Notification showNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        return new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("MediaCodecResourceTestLowPriorityService")
                .setSmallIcon(R.drawable.icon_black)
                .build();
    }
}
