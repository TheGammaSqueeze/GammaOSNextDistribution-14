/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.telecom.cts.apps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationUtils {
    private static final String TAG = NotificationUtils.class.getSimpleName();
    private static final String CALL_STYLE_INITIAL_TEXT = "New Call";
    private static final String CALL_STYLE_ONGOING_TEXT = "Ongoing Call";
    private static final String CALL_STYLE_TITLE = "Telecom CTS Test App";

    public static Notification createCallStyleNotification(
            Context context,
            String channelId,
            String callerName,
            boolean isOutgoing) {
        PendingIntent fullScreenIntent = createPendingIntent(context);
        Person person = new Person.Builder().setName(callerName).setImportant(true).build();
        return new Notification.Builder(context, channelId)
                .setContentText(CALL_STYLE_INITIAL_TEXT)
                .setContentTitle(CALL_STYLE_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setStyle(getCallStyle(isOutgoing, person, fullScreenIntent))
                .setFullScreenIntent(fullScreenIntent, true)
                .setOngoing(isOutgoing)
                .build();
    }

    private static Notification.CallStyle getCallStyle(
            boolean isOutgoing,
            Person person,
            PendingIntent fullScreenIntent) {
        if (isOutgoing) {
            return Notification.CallStyle.forOngoingCall(
                    person,
                    fullScreenIntent);
        } else {
            return Notification.CallStyle.forIncomingCall(
                    person,
                    fullScreenIntent,
                    fullScreenIntent);
        }
    }

    public static void updateNotificationToOngoing(Context context,
            String channelId,
            String callerName,
            int notificationId) {
        PendingIntent ongoingCall = createPendingIntent(context);

        Notification callStyleNotification = new Notification.Builder(context,
                channelId)
                .setContentText(CALL_STYLE_ONGOING_TEXT)
                .setContentTitle(CALL_STYLE_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setStyle(Notification.CallStyle.forOngoingCall(
                        new Person.Builder().setName(callerName).setImportant(true).build(),
                        ongoingCall)
                )
                .setFullScreenIntent(ongoingCall, true)
                .setOngoing(true)
                .build();

        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);

        notificationManager.notify(notificationId, callStyleNotification);
    }

    private static PendingIntent createPendingIntent(Context context) {
        return PendingIntent.getActivity(context, 0,
                new Intent(context, context.getClass()), PendingIntent.FLAG_IMMUTABLE);
    }

    public static void clearNotification(Context c, int notificationId) {
        NotificationManager notificationManager = c.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(notificationId);
        }
    }

    public static void deleteNotificationChannel(Context c, String notificationChannelId){
        NotificationManager notificationManager = c.getSystemService(NotificationManager.class);
        try {
            // deleteNotificationChannel has sometimes thrown exceptions causing test failures
            notificationManager.deleteNotificationChannel(notificationChannelId);
        }
        catch (Exception e){
            Log.i(TAG, String.format("onUnbind: hit exception=[%s] while deleting the"
                            + " call channel with id=[%s]", e, notificationChannelId));
        }
    }

    public static boolean isTargetNotificationPosted(Context c, int targetNotificationId) {
        NotificationManager notificationManager = c.getSystemService(NotificationManager.class);
        StatusBarNotification[] sbinArray = notificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbinArray) {
            if (sbn.getId() == targetNotificationId) {
                return true;
            }
        }
        return false;
    }
}
