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

package android.jobscheduler.cts.jobtestapp;

import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.PACKAGE_NAME;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.util.Log;

public class TestFgsService extends Service {
    private static final String TAG = TestFgsService.class.getSimpleName();
    public static final String ACTION_START_FGS = PACKAGE_NAME + ".action.START_FGS";
    public static final String ACTION_FGS_STARTED = PACKAGE_NAME + ".action.FGS_STARTED";
    public static final String ACTION_STOP_FOREGROUND = PACKAGE_NAME + ".action.STOP_FOREGROUND";

    private final BroadcastReceiver mStopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            unregisterReceiver(mStopReceiver);
            stopSelf();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Test fgs onStartCommand");
        final NotificationManager notificationManager = getSystemService(NotificationManager.class);
        final NotificationChannel channel =
                new NotificationChannel(TAG, TAG, NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
        final Notification notification = new Notification.Builder(this, TAG)
                .setContentTitle("Test")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText(TAG)
                .build();
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        registerReceiver(mStopReceiver, new IntentFilter(ACTION_STOP_FOREGROUND),
                Context.RECEIVER_EXPORTED);

        final Intent reportFgsStartIntent = new Intent(ACTION_FGS_STARTED);
        sendBroadcast(reportFgsStartIntent);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
