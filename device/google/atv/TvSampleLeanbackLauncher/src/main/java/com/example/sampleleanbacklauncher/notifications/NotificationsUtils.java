/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.example.sampleleanbacklauncher.notifications;

import android.content.Context;
import android.content.Intent;

import com.example.sampleleanbacklauncher.LauncherConstants;

import java.util.List;

public final class NotificationsUtils {
    static void dismissNotification(Context context, String key) {
        Intent dismiss = new Intent(Intent.ACTION_DELETE);
        dismiss.setPackage(LauncherConstants.TVRECOMMENDATIONS_PACKAGE_NAME);

        dismiss.putExtra(NotificationsContract.NOTIFICATION_KEY, key);
        context.sendBroadcast(dismiss);
    }

    static void openNotification(Context context, String key) {
        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setPackage(LauncherConstants.TVRECOMMENDATIONS_PACKAGE_NAME);
        open.putExtra(NotificationsContract.NOTIFICATION_KEY, key);
        context.sendBroadcast(open);
    }
}
