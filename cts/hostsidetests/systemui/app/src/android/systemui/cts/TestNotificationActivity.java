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

package android.systemui.cts;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class TestNotificationActivity extends Activity {
    private static final String TAG = TestNotificationActivity.class.getSimpleName();

    public static final String KEY_ACTION = "action";
    public static final String ACTION_SHOW_NOTIFICATION = "action.show_notification";

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        Intent intent = this.getIntent();
        if (intent == null) {
            Log.e(TAG, "Intent was null.");
            finish();
        }

        String action = intent.getStringExtra(KEY_ACTION);
        Log.i(TAG, "Starting " + action + " from foreground activity.");

        switch (action) {
            case ACTION_SHOW_NOTIFICATION:
                doShowNotification();
                break;
            default:
                Log.e(TAG, "Intent had invalid action " + action);
                finish();
        }
    }

    private void doShowNotification() {
        final int notificationId = R.layout.activity_notification;
        final String notificationChannelId = "SystemUiCtsChannel";

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(notificationChannelId, "SystemUi Cts",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("SystemUi Cts Channel");
        nm.createNotificationChannel(channel);

        nm.notify(
                notificationId,
                new Notification.Builder(this, notificationChannelId)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("SystemUiCts")
                        .setContentText("SystemUiCts")
                        .build());
        finish();
    }
}
