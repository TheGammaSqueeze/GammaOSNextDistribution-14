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

package android.server.wm.app;

import static android.server.wm.app.Components.OverlayTestService.EXTRA_LAYOUT_PARAMS;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

public class OverlayTestService extends Service {
    private WindowManager mWindowManager;
    private View mView;

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = getSystemService(WindowManager.class);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_LAYOUT_PARAMS)) {
            // Have to be a foreground service since this app is in the background
            startForeground();
            addWindow(intent.getParcelableExtra(EXTRA_LAYOUT_PARAMS));
        }
        return START_NOT_STICKY;
    }

    private void addWindow(final LayoutParams params) {
        mView = new View(this);
        mView.setBackgroundColor(Color.RED);
        mWindowManager.addView(mView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mView != null) {
            mWindowManager.removeViewImmediate(mView);
            mView = null;
        }
    }

    private void startForeground() {
        String channel = Components.Notifications.CHANNEL_MAIN;
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(
                new NotificationChannel(channel, channel, NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification =
                new Notification.Builder(this, channel)
                        .setContentTitle("CTS")
                        .setContentText(getClass().getCanonicalName())
                        .setSmallIcon(android.R.drawable.btn_default)
                        .build();
        startForeground(Components.Notifications.ID_OVERLAY_TEST_SERVICE, notification);
    }
}
