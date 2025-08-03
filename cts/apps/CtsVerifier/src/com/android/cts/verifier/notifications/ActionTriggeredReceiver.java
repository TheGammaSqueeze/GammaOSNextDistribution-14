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

package com.android.cts.verifier.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.cts.verifier.R;

public class ActionTriggeredReceiver extends BroadcastReceiver {

    public static String ACTION = "com.android.cts.verifier.notifications.ActionTriggeredReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            sendNotification(context, false);
        }
    }

    public static void sendNotification(Context context, boolean initialSend) {
        String initialSendText = context.getString(R.string.action_not_sent);
        String updateSendText = context.getString(R.string.action_received);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        Notification n1 = new Notification.Builder(
                context, NotificationPrivacyVerifierActivity.TAG)
                .setContentTitle(initialSend ?  initialSendText: updateSendText)
                .setContentText(initialSend ? initialSendText : updateSendText)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setCategory(Notification.CATEGORY_REMINDER)
                .addAction(new Notification.Action.Builder(R.drawable.ic_stat_charlie,
                        context.getString(R.string.action_test_title),
                        makeBroadcastIntent(context))
                        .setAuthenticationRequired(true).build())
                .build();
        nm.notify(ACTION, 0, n1);
    }

    protected static PendingIntent makeBroadcastIntent(Context context) {
        Intent intent = new Intent(ACTION);
        intent.setComponent(new ComponentName(context, ActionTriggeredReceiver.class));
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return pi;
    }
}
