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

package com.android.queryable.queries;

import static com.android.bedstead.nene.utils.ParcelTest.assertParcelsCorrectly;
import static com.android.queryable.queries.NotificationQuery.notification;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.queryable.Queryable;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class NotificationQueryHelperTest {

    @ClassRule @Rule
    public static DeviceState sDeviceState = new DeviceState();

    private static final Context CONTEXT = TestApis.context().instrumentedContext();

    private final Queryable mQuery = null;
    private static final String DIFFERENT_STRING_VALUE = "String2";

    // TODO: We can't easily create a StatusBarNotification for testing - this will need to test
    // with real notifications.

//    @Test
//    public void matches_noRestrictions_returnsTrue() {
//        Notification notification = new Notification();
//        NotificationQueryHelper<Queryable> notificationQueryHelper =
//                new NotificationQueryHelper<>(mQuery);
//
//        assertThat(notificationQueryHelper.matches(notification)).isTrue();
//    }
//
//    @Test
//    public void matches_channelId_meetsRestriction_returnsTrue() {
//        Notification notification =
//                new Notification.Builder(CONTEXT, /* channelId= */ STRING_VALUE).build();
//        NotificationQueryHelper<Queryable> notificationQueryHelper =
//                new NotificationQueryHelper<>(mQuery);
//
//        notificationQueryHelper.channelId().isEqualTo(STRING_VALUE);
//
//        assertThat(notificationQueryHelper.matches(notification)).isTrue();
//    }
//
//    @Test
//    public void matches_channelId_doesNotMeetRestriction_returnsFalse() {
//        Notification notification =
//                new Notification.Builder(CONTEXT, /* channelId= */ STRING_VALUE).build();
//        NotificationQueryHelper<Queryable> notificationQueryHelper =
//                new NotificationQueryHelper<>(mQuery);
//
//        notificationQueryHelper.channelId().isEqualTo(DIFFERENT_STRING_VALUE);
//
//        assertThat(notificationQueryHelper.matches(notification)).isFalse();
//    }

    @Test
    public void parcel_parcelsCorrectly() {
        NotificationQueryHelper<Queryable> notificationQueryHelper =
                new NotificationQueryHelper<>(mQuery);

        notificationQueryHelper.channelId().isEqualTo("");

        assertParcelsCorrectly(NotificationQueryHelper.class, notificationQueryHelper);
    }

//    @Test
//    public void notificationQueryHelper_queries() {
//        Notification notification =
//                new Notification.Builder(CONTEXT, /* channelId= */ STRING_VALUE).build();
//
//        assertThat(notification()
//                .where().channelId().isEqualTo(STRING_VALUE)
//                .matches(notification)).isTrue();
//    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        NotificationQueryHelper<Queryable> notificationQueryHelper =
                new NotificationQueryHelper<>(mQuery);

        assertThat(notificationQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasChannelIdQuery_returnsFalse() {
        NotificationQueryHelper<Queryable> notificationQueryHelper =
                new NotificationQueryHelper<>(mQuery);

        notificationQueryHelper.channelId().isEqualTo("");

        assertThat(notificationQueryHelper.isEmptyQuery()).isFalse();
    }
}
