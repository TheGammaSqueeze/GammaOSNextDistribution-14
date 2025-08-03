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

package android.os.cts;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

public class LowPowerStandbyForegroundService extends Service {
    private static final int ID = 1;
    private static final String EXTRA_FGS_TYPE = "EXTRA_FGS_TYPE";

    private PowerManager.WakeLock mTestWakeLock;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        NotificationChannel notificationChannel = new NotificationChannel("fgsNotifChannel",
                "fgsNotifChannel", NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(notificationChannel);

        Notification notification = new Notification.Builder(this, notificationChannel.getId())
                .setContentTitle("ForegroundService")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_info)
                .build();

        startForeground(ID, notification, intent.getIntExtra(EXTRA_FGS_TYPE, 0));

        PowerManager powerManager = getApplicationContext().getSystemService(PowerManager.class);
        mTestWakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK,
                LowPowerStandbyTest.TEST_WAKE_LOCK_TAG);
        mTestWakeLock.acquire(10000);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTestWakeLock != null && mTestWakeLock.isHeld()) {
            mTestWakeLock.release();
        }
    }

    public static Intent createIntentWithForegroundServiceType(Context context, int fgsType) {
        Intent intent = new Intent(context, LowPowerStandbyForegroundService.class);
        intent.putExtra(EXTRA_FGS_TYPE, fgsType);
        return intent;
    }
}
