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

package com.android.cts.verifier;

import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class NotificationsTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void NotificationListenerTest() throws Exception {
        runTest(".notifications.NotificationListenerVerifierActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void NotificationPrivacyTest() throws Exception {
        requireFeatures("android.software.secure_lock_screen");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".notifications.NotificationPrivacyVerifierActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ConditionProviderTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.automotive",
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch");

        runTest(".notifications.ConditionProviderVerifierActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ToastTest() throws Exception {
        runTest(".notifications.ToastVerifierActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void BubblesTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.watch",
                "android.software.leanback",
                "android.hardware.type.automotive");

        runTest(".notifications.BubblesVerifierActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void MediaPlayerTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.watch",
                "android.software.leanback",
                "android.hardware.type.automotive");

        runTest(".notifications.MediaPlayerVerifierActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ShortcutThrottlingResetTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.watch",
                "android.software.leanback",
                "android.hardware.type.automotive");

        runTest(".notifications.ShortcutThrottlingResetActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(
            apis = {
                "android.provider.Settings#ACTION_SECURITY_SETTINGS",
                "android.provider.Settings#ACTION_TRUSTED_CREDENTIALS_USER"
            })
    public void CAInstallNotificationTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.watch",
                "android.hardware.type.television",
                "android.software.leanback");
        requireFeatures("android.software.device_admin");
        requireActions("com.android.settings.TRUSTED_CREDENTIALS_USER");

        runTest(".security.CAInstallNotificationVerifierActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(
            apis = {
                "android.provider.Settings#ACTION_SECURITY_SETTINGS",
                "android.provider.Settings#ACTION_TRUSTED_CREDENTIALS_USER"
            })
    public void CANotifyOnBootTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.watch",
                "android.hardware.type.television",
                "android.software.leanback");
        requireFeatures("android.software.device_admin");
        requireActions("com.android.settings.TRUSTED_CREDENTIALS_USER");

        runTest(".security.CANotifyOnBootActivity");
    }
}
