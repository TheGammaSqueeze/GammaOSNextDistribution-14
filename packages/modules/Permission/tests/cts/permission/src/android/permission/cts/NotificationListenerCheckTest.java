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

package android.permission.cts;

import static android.permission.cts.PermissionUtils.clearAppState;
import static android.permission.cts.PermissionUtils.install;
import static android.permission.cts.PermissionUtils.uninstallApp;
import static android.permission.cts.TestUtils.ensure;
import static android.permission.cts.TestUtils.eventually;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.rule.ScreenRecordRule;
import android.service.notification.StatusBarNotification;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the {@code NotificationListenerCheck} in permission controller.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot set system settings as instant app. Also we never show a notification"
        + " listener check notification for instant apps.")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
@ScreenRecordRule.ScreenRecord
@FlakyTest
public class NotificationListenerCheckTest extends BaseNotificationListenerCheckTest {

    public final ScreenRecordRule mScreenRecordRule = new ScreenRecordRule(false, false);

    @Before
    public void setup() throws Throwable {
        // Skip tests if safety center not allowed
        assumeDeviceSupportsSafetyCenter();

        wakeUpAndDismissKeyguard();
        resetPermissionControllerBeforeEachTest();

        // Cts NLS is required to verify sent Notifications, however, we don't want it to show up in
        // testing
        triggerAndDismissCtsNotificationListenerNotification();

        clearNotifications();

        // Install and allow the app with NLS for testing
        install(TEST_APP_NOTIFICATION_LISTENER_APK);
        allowTestAppNotificationListenerService();
    }

    @After
    public void tearDown() throws Throwable {
        // Disallow and uninstall the app with NLS for testing
        disallowTestAppNotificationListenerService();
        uninstallApp(TEST_APP_PKG);

        clearNotifications();
    }

    @Test
    public void noNotificationIfFeatureDisabled() throws Throwable {
        setNotificationListenerCheckEnabled(false);

        runNotificationListenerCheck();

        ensure(() -> assertNull("Expected no notifications", getNotification(false)),
                ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void noNotificationIfSafetyCenterDisabled() throws Throwable {
        SafetyCenterUtils.setSafetyCenterEnabled(false);

        runNotificationListenerCheck();

        ensure(() -> assertNull("Expected no notifications", getNotification(false)),
                ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsShown() throws Throwable {
        runNotificationListenerCheck();

        eventually(() -> assertNotNull("Expected notification, none found", getNotification(false)),
                UNEXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsShownOnlyOnce() throws Throwable {
        runNotificationListenerCheck();
        eventually(() -> assertNotNull(getNotification(true)), UNEXPECTED_TIMEOUT_MILLIS);

        runNotificationListenerCheck();
        ensure(() -> assertNull("Expected no notifications", getNotification(false)),
                ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsShownAgainAfterClear() throws Throwable {
        runNotificationListenerCheck();
        eventually(() -> assertNotNull(getNotification(true)), UNEXPECTED_TIMEOUT_MILLIS);

        clearAppState(TEST_APP_PKG);
        // Wait until package is cleared and permission controller has cleared the state
        Thread.sleep(2000);

        allowTestAppNotificationListenerService();
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), UNEXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsShownAgainAfterUninstallAndReinstall() throws Throwable {
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), UNEXPECTED_TIMEOUT_MILLIS);

        uninstallApp(TEST_APP_PKG);

        // Wait until package permission controller has cleared the state
        Thread.sleep(2000);

        install(TEST_APP_NOTIFICATION_LISTENER_APK);

        allowTestAppNotificationListenerService();
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), UNEXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsShownAgainAfterDisableAndReenableAppNotificationListener()
            throws Throwable {
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), UNEXPECTED_TIMEOUT_MILLIS);

        // Disallow NLS, and run NLS check job. This  should clear NLS off notified list
        disallowTestAppNotificationListenerService();
        runNotificationListenerCheck();

        // Re-allow NLS, and run NLS check job. This work now that it's cleared NLS off notified
        // list
        allowTestAppNotificationListenerService();
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), UNEXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void removeNotificationOnUninstall() throws Throwable {
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(false)), UNEXPECTED_TIMEOUT_MILLIS);

        uninstallApp(TEST_APP_PKG);

        // Wait until package permission controller has cleared the state
        Thread.sleep(2000);

        eventually(() -> assertNull(getNotification(false)), UNEXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsNotShownAfterDisableAppNotificationListener() throws Throwable {
        disallowTestAppNotificationListenerService();

        runNotificationListenerCheck();

        // We don't expect a notification, but try to trigger one anyway
        ensure(() -> assertNull("Expected no notifications", getNotification(false)),
                ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationOnClick_opensSafetyCenter() throws Throwable {
        runNotificationListenerCheck();

        StatusBarNotification currentNotification = eventually(
                () -> {
                    StatusBarNotification notification = getNotification(false);
                    assertNotNull(notification);
                    return notification;
                }, UNEXPECTED_TIMEOUT_MILLIS);

        // Verify content intent
        PendingIntent contentIntent = currentNotification.getNotification().contentIntent;
        if (SdkLevel.isAtLeastU()) {
            contentIntent.send(null, 0, null, null, null, null,
                    ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle());
        } else {
            contentIntent.send();
        }

        SafetyCenterUtils.assertSafetyCenterStarted();
    }
}
