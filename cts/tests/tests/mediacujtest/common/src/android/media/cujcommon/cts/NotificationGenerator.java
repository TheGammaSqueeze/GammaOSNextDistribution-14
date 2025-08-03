/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.cujcommon.cts;

import static android.media.cujcommon.cts.CujTestBase.SHORTFORM_PLAYBAK_TEST_APP;
import static android.Manifest.permission.POST_NOTIFICATIONS;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.permission.cts.PermissionUtils;

public class NotificationGenerator {

  private static final String CHANNEL_ID = "NotificationChannelID";
  private static final int NOTIFICATION_ID = 1;

  /**
   * Create and send a sample notification
   */
  public static void createNotification(Context context) throws Exception {
    PermissionUtils.grantPermission(SHORTFORM_PLAYBAK_TEST_APP, POST_NOTIFICATIONS);
    NotificationChannel notificationChannel = new NotificationChannel(
        CHANNEL_ID,
        "Channel 1",
        NotificationManager.IMPORTANCE_HIGH
    );
    AudioAttributes audioAttributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build();
    notificationChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        audioAttributes);
    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    notificationManager.createNotificationChannel(notificationChannel);
    Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_notification_overlay)
        .setContentTitle("Sample Message Notification")
        .setContentText("This is a sample notification for E2E CUJ")
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setTimeoutAfter(3000)
        .setAutoCancel(true);
    notificationManager.notify(NOTIFICATION_ID, builder.build());
  }
}
