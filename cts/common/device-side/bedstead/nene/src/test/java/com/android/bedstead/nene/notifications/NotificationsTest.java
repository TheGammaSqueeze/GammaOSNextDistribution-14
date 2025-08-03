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

package com.android.bedstead.nene.notifications;

import static android.Manifest.permission.POST_NOTIFICATIONS;

import static com.android.bedstead.nene.notifications.NotificationListenerQuerySubject.assertThat;
import static com.android.bedstead.nene.notifications.Notifications.LISTENER_COMPONENT;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(BedsteadJUnit4.class)
public class NotificationsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final UserReference USER = TestApis.users().instrumented();

    private static NotificationManager sNotificationManager =
            TestApis.context().instrumentedContext().getSystemService(NotificationManager.class);

    @Test
    public void setNotificationListenerAccessGranted_true_notificationListenerAccessIsGranted() {
        TestApis.notifications().setNotificationListenerAccessGranted(
                LISTENER_COMPONENT, true, USER);

        assertThat(sNotificationManager.isNotificationListenerAccessGranted(
                LISTENER_COMPONENT.componentName())).isTrue();
    }

    @Test
    public void setNotificationListenerAccessGranted_false_notificationListenerAccessIsNotGranted() {
        TestApis.notifications().setNotificationListenerAccessGranted(
                LISTENER_COMPONENT, false, USER);

        assertThat(sNotificationManager.isNotificationListenerAccessGranted(
                LISTENER_COMPONENT.componentName())).isFalse();
    }

    @Test
    public void setNotificationListenerAccessGranted_nullComponent_throwsException() {
        assertThrows(NullPointerException.class, () -> TestApis.notifications()
                .setNotificationListenerAccessGranted(
                        /* component= */ null, /* granted= */ true, /* user= */ USER));
    }

    @Test
    public void setNotificationListenerAccessGranted_nullUser_throwsException() {
        assertThrows(NullPointerException.class, () -> TestApis.notifications()
                .setNotificationListenerAccessGranted(
                        LISTENER_COMPONENT, /* granted= */ true, /* user= */ null));
    }

    @Test
    @com.android.bedstead.harrier.annotations.NotificationsTest
    public void query_notificationIsPosted_findsNotification() {
        try (NotificationListener notifications = TestApis.notifications().createListener()) {
            createNotification();

            assertThat(notifications.query()
                    .wherePackageName().isEqualTo(TestApis.packages().instrumented().packageName())
            ).wasPosted();
        }
    }

    @Test
    public void createListener_noNotificationsTestAnnotation_throwsException() {
        assertThrows(NeneException.class, () -> TestApis.notifications().createListener());
    }

    private void createNotification() {
        try (PermissionContext p = TestApis.permissions().withPermission(POST_NOTIFICATIONS)) {
            String channelId = "notifications";
            sNotificationManager.createNotificationChannel(new NotificationChannel(channelId,
                    "notifications",
                    NotificationChannel.USER_LOCKED_IMPORTANCE));

            Notification.Builder notificationBuilder =
                    new Notification.Builder(TestApis.context().instrumentedContext(), channelId)
                    .setSmallIcon(R.drawable.sym_def_app_icon);

            sNotificationManager.notify(1, notificationBuilder.build());
        }
    }

}
