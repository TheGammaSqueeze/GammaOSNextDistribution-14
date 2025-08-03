/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.notifications;

import android.net.Uri;

/**
 * Constants which represent the "contract" for interacting with TV notifications.
 */

public final class NotificationsContract {
    private static final String PATH_NOTIFS = "notifications";
    private static final String PATH_NOTIFS_COUNT = PATH_NOTIFS + "/count";

    // Content provider for notifications
    private static final String AUTHORITY =
            "com.android.tv.notifications.NotificationContentProvider";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" +
            PATH_NOTIFS);
    public static final Uri NOTIFS_COUNT_URI = Uri.parse("content://" + AUTHORITY + "/" +
            PATH_NOTIFS_COUNT);

    public static final String ACTION_NOTIFICATION_HIDE =
            "android.tvservice.action.NOTIFICATION_HIDE";

    public static final String ACTION_SHOW_UNSHOWN_NOTIFICATIONS =
            "android.tvservice.action.SHOW_UNSHOWN_NOTIFICATIONS";

    public static final String ACTION_OPEN_NOTIFICATION_PANEL =
            "com.android.tv.NOTIFICATIONS_PANEL";

    public static final String NOTIFICATION_KEY = "sbn_key";

    public static final String COLUMN_SBN_KEY = "sbn_key";
    public static final String COLUMN_PACKAGE_NAME = "package_name";
    public static final String COLUMN_NOTIF_TITLE = "title";
    public static final String COLUMN_NOTIF_TEXT = "text";
    public static final String COLUMN_AUTODISMISS = "is_auto_dismiss";
    public static final String COLUMN_DISMISSIBLE = "dismissible";
    public static final String COLUMN_ONGOING = "ongoing";
    public static final String COLUMN_SMALL_ICON = "small_icon";
    public static final String COLUMN_CHANNEL = "channel";
    public static final String COLUMN_PROGRESS = "progress";
    public static final String COLUMN_PROGRESS_MAX = "progress_max";
    public static final String COLUMN_NOTIFICATION_HIDDEN = "notification_hidden";
    public static final String COLUMN_FLAGS = "flags";
    public static final String COLUMN_HAS_CONTENT_INTENT = "has_content_intent";
    public static final String COLUMN_BIG_PICTURE = "big_picture";
    public static final String COLUMN_CONTENT_BUTTON_LABEL = "content_button_label";
    public static final String COLUMN_DISMISS_BUTTON_LABEL = "dismiss_button_label";
    public static final String COLUMN_TAG = "tag";

    public static final String COLUMN_COUNT = "count";
}
