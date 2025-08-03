/*
 * Copyright 2019 The Android Open Source Project
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
package android.media.cts;

import static android.media.cts.ForegroundServiceUtil.EXTRA_MESSENGER;
import static android.media.cts.ForegroundServiceUtil.MSG_SERVICE_DESTROYED;
import static android.media.cts.ForegroundServiceUtil.MSG_START_FOREGROUND_DONE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class LocalMediaProjectionService extends Service {
    private static final String TAG = "LocalMediaProjectionService";

    private Bitmap mTestBitmap;

    private static final String NOTIFICATION_CHANNEL_ID = "AudioPlaybackCaptureTest";
    private static final String CHANNEL_NAME = "ProjectionService";
    private static final int NOTIFICATION_ID = 1;

    Messenger mMessenger;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mMessenger = intent.getParcelableExtra(EXTRA_MESSENGER);
        startForeground();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mTestBitmap != null) {
            mTestBitmap.recycle();
            mTestBitmap = null;
        }
        sendMessage(MSG_SERVICE_DESTROYED);
        super.onDestroy();
    }

    private Icon createNotificationIcon() {
        mTestBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(mTestBitmap);
        canvas.drawColor(Color.BLUE);
        return Icon.createWithBitmap(mTestBitmap);
    }

    private void startForeground() {
        final NotificationChannel channel = new NotificationChannel(getNotificationChannelId(),
                getNotificationChannelName(), NotificationManager.IMPORTANCE_NONE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        final NotificationManager notificationManager =
                getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        final Notification.Builder notificationBuilder =
                new Notification.Builder(this, getNotificationChannelId());

        final Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("App is running")
                .setSmallIcon(createNotificationIcon())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentText("Context")
                .build();

        startForeground(getNotificationId(), notification);

        sendMessage(MSG_START_FOREGROUND_DONE);
    }

    void sendMessage(int what) {
        final Message msg = Message.obtain();
        msg.what = what;
        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
        }
    }

    int getNotificationId() {
        return NOTIFICATION_ID;
    }

    String getNotificationChannelId() {
        return NOTIFICATION_CHANNEL_ID;
    }

    String getNotificationChannelName() {
        return CHANNEL_NAME;
    }
}
