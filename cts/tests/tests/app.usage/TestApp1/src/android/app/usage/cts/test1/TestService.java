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

package android.app.usage.cts.test1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.app.usage.cts.ITestReceiver;
import android.content.Intent;
import android.os.IBinder;

public class TestService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return new TestReceiver();
    }

    private class TestReceiver extends ITestReceiver.Stub {
        @Override
        public boolean isAppInactive(String pkg) {
            UsageStatsManager usm = getSystemService(UsageStatsManager.class);
            return usm.isAppInactive(pkg);
        }

        @Override
        public void createNotificationChannel(String channelId, String channelName,
                String channelDescription) {
            final NotificationChannel channel = new NotificationChannel(channelId, channelName,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(channelDescription);
            getNotificationManager().createNotificationChannel(channel);
        }

        @Override
        public void postNotification(int notificationId, Notification notification) {
            getNotificationManager().notify(notificationId, notification);
        }

        @Override
        public void cancelNotification(int notificationId) {
            getNotificationManager().cancel(notificationId);
        }

        @Override
        public void cancelAll() {
            getNotificationManager().cancelAll();
        }

        private NotificationManager getNotificationManager() {
            return getSystemService(NotificationManager.class);
        }
    }
}
