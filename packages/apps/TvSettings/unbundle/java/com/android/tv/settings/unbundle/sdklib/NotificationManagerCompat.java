/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.annotation.NonNull;
import android.app.NotificationManager;
import android.content.ComponentName;

public class NotificationManagerCompat {
    public NotificationManager mNotificationManager;

    public NotificationManagerCompat(NotificationManager notificationManager) {
        mNotificationManager = notificationManager;
    }

    public static Object getService() {
        return NotificationManager.getService();
    }

    public void setNotificationListenerAccessGranted(
            @NonNull ComponentName listener, boolean granted) {
        mNotificationManager.setNotificationListenerAccessGranted(listener, granted);
    }
}
