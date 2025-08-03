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

import static android.permission.cts.PermissionUtils.install;
import static android.permission.cts.PermissionUtils.uninstallApp;
import static android.permission.cts.TestUtils.ensure;

import static org.junit.Assert.assertNull;

import android.os.Build;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

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
public class NotificationListenerCheckWithSafetyCenterUnsupportedTest
        extends BaseNotificationListenerCheckTest  {

    @Before
    public void setup() throws Throwable {
        // Skip tests if safety center is supported
        assumeDeviceDoesNotSupportSafetyCenter();

        wakeUpAndDismissKeyguard();
        resetPermissionControllerBeforeEachTest();

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
    public void noNotifications_featureEnabled_safetyCenterEnabled() throws Throwable {
        runNotificationListenerCheck();

        ensure(() -> assertNull("Expected no notifications", getNotification(false)),
                ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void noNotifications_featureDisabled_safetyCenterEnabled() throws Throwable {
        setNotificationListenerCheckEnabled(false);

        runNotificationListenerCheck();

        ensure(() -> assertNull("Expected no notifications", getNotification(false)),
                ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void noNotifications_featureEnabled_safetyCenterDisabled() throws Throwable {
        SafetyCenterUtils.setSafetyCenterEnabled(false);

        runNotificationListenerCheck();

        ensure(() -> assertNull("Expected no notifications", getNotification(false)),
                ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void noNotifications_featureDisabled_safetyCenterDisabled() throws Throwable {
        setNotificationListenerCheckEnabled(false);
        SafetyCenterUtils.setSafetyCenterEnabled(false);

        runNotificationListenerCheck();

        ensure(() -> assertNull("Expected no notifications", getNotification(false)),
                ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS);
    }
}
