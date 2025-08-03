/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.permissions.CommonPermissions.POST_NOTIFICATIONS;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.sysui.DoesTheNotificationTitledNotificationHaveAWorkBadgeStep;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class NotificationTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .wherePermissions().contains(POST_NOTIFICATIONS)
            .get();

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "NotificationTest";
    private static final CharSequence CHANNEL_NAME = "NotificationTest";
    private static final int IMPORTANCE = NotificationManager.IMPORTANCE_MAX;

    @Interactive
    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    @CddTest(requirements = "3.9.2/C-1-3")
    @NotFullyAutomated(reason = "DoesTheNotificationTitledNotificationHaveAWorkBadgeStep")
    public void notification_fromPersonalProfile_isNotBadged() throws Exception {
        try (TestAppInstance initialUserApp = sTestApp.install(sDeviceState.initialUser());
             PermissionContext p = initialUserApp.permissions().withPermission(POST_NOTIFICATIONS)) {
            showNotificationWithTitleNotification(initialUserApp);

            // We must show the instruction before opening the notification shade because steps
            // can't show over the shade
            assertThat(Step.execute(DoesTheNotificationTitledNotificationHaveAWorkBadgeStep.class))
                    .isFalse();
        }
    }

    @Interactive
    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    @CddTest(requirements = "3.9.2/C-1-3")
    @NotFullyAutomated(reason = "DoesTheNotificationTitledNotificationHaveAWorkBadgeStep")
    public void notification_fromWorkProfile_isBadged() throws Exception {
        try (TestAppInstance workProfileApp = sTestApp.install(sDeviceState.workProfile());
             PermissionContext p = workProfileApp.permissions().withPermission(POST_NOTIFICATIONS)) {
            showNotificationWithTitleNotification(workProfileApp);

            // We must show the instruction before opening the notification shade because steps
            // can't show over the shade
            assertThat(Step.execute(DoesTheNotificationTitledNotificationHaveAWorkBadgeStep.class))
                    .isTrue();
        }
    }

    private void showNotificationWithTitleNotification(TestAppInstance testApp) {
        NotificationChannel c = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE);

        Notification notification =
                new Notification.Builder(TestApis.context().instrumentedContext(), CHANNEL_ID)
                        .setSmallIcon(R.drawable.test_drawable_1)
                        .setContentTitle("Notification")
                        .setContentText("Notification")
                        .setAutoCancel(true)
                        .build();

        testApp.notificationManager().createNotificationChannel(c);
        testApp.notificationManager().notify(NOTIFICATION_ID, notification);
    }
}
