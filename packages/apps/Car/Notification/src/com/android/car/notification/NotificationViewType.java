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
package com.android.car.notification;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({
        NotificationViewType.GROUP,
        NotificationViewType.GROUP_SUMMARY,
        NotificationViewType.BASIC,
        NotificationViewType.BASIC_IN_GROUP,
        NotificationViewType.MESSAGE,
        NotificationViewType.MESSAGE_IN_GROUP,
        NotificationViewType.PROGRESS,
        NotificationViewType.PROGRESS_IN_GROUP,
        NotificationViewType.INBOX,
        NotificationViewType.INBOX_IN_GROUP,
        NotificationViewType.CAR_EMERGENCY,
        NotificationViewType.CAR_WARNING,
        NotificationViewType.CAR_INFORMATION,
        NotificationViewType.CAR_INFORMATION_IN_GROUP,
        NotificationViewType.NAVIGATION,
        NotificationViewType.CALL,
        NotificationViewType.HEADER,
        NotificationViewType.FOOTER,
        NotificationViewType.RECENTS,
        NotificationViewType.OLDER,
})
@Retention(RetentionPolicy.SOURCE)
@interface NotificationViewType {

    int GROUP = 1;
    int GROUP_SUMMARY = 2;

    int BASIC = 3;
    int BASIC_IN_GROUP = 4;

    int MESSAGE = 5;
    int MESSAGE_IN_GROUP = 6;

    int PROGRESS = 7;
    int PROGRESS_IN_GROUP = 8;

    int INBOX = 9;
    int INBOX_IN_GROUP = 10;

    int CAR_EMERGENCY = 11;

    int CAR_WARNING = 12;

    int CAR_INFORMATION = 13;
    int CAR_INFORMATION_IN_GROUP = 14;

    int NAVIGATION = 15;
    int CALL = 16;

    int HEADER = 17;
    int FOOTER = 18;

    int RECENTS = 19;
    int OLDER = 20;
}