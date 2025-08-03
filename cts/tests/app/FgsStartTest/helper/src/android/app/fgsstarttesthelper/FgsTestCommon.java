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
package android.app.fgsstarttesthelper;

import android.R.drawable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class FgsTestCommon {
    private FgsTestCommon() {
    }

    public static final String ACTION_START_NEW_LOGIC_TEST = "ACTION_START_NEW_LOGIC_TEST";
    public static final String EXTRA_REPLY_INTENT = "EXTRA_REPLY_INTENT";

    private static String getNotificationChannelId(Context context) {
        return "channel/" + context.getPackageName();
    }

    public static String ensureNotificationChannel(Context context) {
        final var channel = getNotificationChannelId(context);
        context.getSystemService(NotificationManager.class)
                .createNotificationChannel(
                        new NotificationChannel(channel, channel,
                                NotificationManager.IMPORTANCE_DEFAULT));
        return channel;
    }

    public static Notification createNotification(Context context) {
        Notification.Builder builder =
                new Notification.Builder(context, ensureNotificationChannel(context))
                        .setContentTitle("Title: " + context.getPackageName())
                        .setSmallIcon(drawable.star_on);
        return builder.build();
    }
}
