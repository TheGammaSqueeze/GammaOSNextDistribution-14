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

package com.android.cts.verifier.managedprovisioning;

import static android.Manifest.permission.POST_NOTIFICATIONS;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.verifier.R;

/**
 * Activity to create a notification.
 */
@ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
public class NotificationActivity extends Activity {

    NotificationManager mNotificationManager;

    private static final String NOTIFICATION_CHANNEL_ID =
            "ctsVerifier/Non-DismissibleNotifications";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);

        mNotificationManager = getSystemService(NotificationManager.class);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.provisioning_byod_allow_nondismissible_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean hasPostNotificationsPermission = ContextCompat.checkSelfPermission(this,
                POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;

        if (hasPostNotificationsPermission) {
            Intent intent = new Intent(this, NotificationActivity.class);

            Notification builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(getString(
                            R.string.provisioning_byod_allow_nondismissible_notification_title))
                    .setContentText(getString(
                            R.string.provisioning_byod_allow_nondismissible_notification_text))
                    .setSmallIcon(R.drawable.icon)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .build();
            mNotificationManager.notify(1, builder);

        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        finish();
    }
}
