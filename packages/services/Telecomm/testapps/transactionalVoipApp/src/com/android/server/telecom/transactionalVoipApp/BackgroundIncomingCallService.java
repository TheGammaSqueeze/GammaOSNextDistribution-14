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

package com.android.server.telecom.transactionalVoipApp;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class BackgroundIncomingCallService extends Service {
    // finals
    private static final String TAG = "BackgroundIncomingCallService";
    // instance vars
    private NotificationManager mNotificationManager;
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        mNotificationManager = getSystemService(NotificationManager.class);
    }

    @Override
    @StartResult
    public int onStartCommand(Intent intent, @StartArgFlags int flags, int startId) {
        Log.i(TAG, String.format("onStartCommand: intent=[%s]", intent));

        // create the notification channel
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    Utils.CHANNEL_ID, "incoming calls", NotificationManager.IMPORTANCE_DEFAULT));
        }

        // start the foreground service and post a notification
        startForeground(98765, Utils.createCallStyleNotification(this),
                FOREGROUND_SERVICE_TYPE_PHONE_CALL);

        return Service.START_STICKY_COMPATIBILITY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, String.format("onBind: intent=[%s]", intent));
        return mBinder;
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        BackgroundIncomingCallService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BackgroundIncomingCallService.this;
        }
    }
}
