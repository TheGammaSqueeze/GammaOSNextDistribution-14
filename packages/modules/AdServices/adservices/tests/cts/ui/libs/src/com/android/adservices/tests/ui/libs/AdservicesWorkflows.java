/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.tests.ui.libs;

import static android.Manifest.permission.READ_DEVICE_CONFIG;

import static com.android.adservices.tests.ui.libs.UiUtils.LAUNCH_TIMEOUT;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.adservices.tests.ui.libs.pages.NotificationPages;
import com.android.adservices.tests.ui.libs.pages.SettingsPages;

public class AdservicesWorkflows {
    private static final String NOTIFICATION_PACKAGE = "android.adservices.ui.NOTIFICATIONS";
    private static final String SETTINGS_PACKAGE = "android.adservices.ui.SETTINGS";

    public static void startNotificationActivity(
            Context context, UiDevice device, boolean isEUActivity, String packageName) {
        if (context.checkCallingOrSelfPermission(READ_DEVICE_CONFIG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("adservices", "this does not have read_device_config permission");
        } else {
            Log.d("adservices", "this has read_device_config permission");
        }
        Intent intent = new Intent(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("isEUDevice", isEUActivity);
        context.startActivity(intent);
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), LAUNCH_TIMEOUT);
    }

    public static void startSettingsActivity(Context context, UiDevice device, String packageName) {
        if (context.checkCallingOrSelfPermission(READ_DEVICE_CONFIG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("adservices", "this does not have read_device_config permission");
        } else {
            Log.d("adservices", "this has read_device_config permission");
        }
        // Launch the setting view.
        Intent intent = new Intent(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        // Wait for the view to appear
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), LAUNCH_TIMEOUT);
    }

    public static void testNotificationActivityFlow(
            Context context,
            UiDevice device,
            boolean isEuDevice,
            UiConstants.UX ux,
            boolean isV2,
            boolean isGoSettings,
            boolean isOptin)
            throws Exception {
        testNotificationActivityFlow(
                context, device, NOTIFICATION_PACKAGE, isEuDevice, ux, isV2, isGoSettings, isOptin);
    }

    public static void testNotificationActivityFlow(
            Context context,
            UiDevice device,
            String packageName,
            boolean isEuDevice,
            UiConstants.UX ux,
            boolean isV2,
            boolean isGoSettings,
            boolean isOptin)
            throws Exception {
        if (isEuDevice) {
            UiUtils.setAsEuDevice();
        } else {
            UiUtils.setAsRowDevice();
        }
        switch (ux) {
            case GA_UX:
                UiUtils.enableGa();
                break;
            case BETA_UX:
                UiUtils.enableBeta();
                break;
            case U18_UX:
                UiUtils.enableGa();
                UiUtils.enableU18();
                break;
            case RVC_UX:
                UiUtils.enableGa();
                UiUtils.enableRvc();
                break;
        }

        UiUtils.setFlipFlow(isV2);

        startNotificationActivity(context, device, isEuDevice, packageName);
        notificationConfirmWorkflow(
                context, device, true, isEuDevice, ux, isV2, isGoSettings, isOptin);
    }

    public static void testSettingsPageFlow(
            Context context,
            UiDevice device,
            UiConstants.UX ux,
            boolean isOptin,
            boolean flipConsent,
            boolean assertOptIn)
            throws Exception {
        testSettingsPageFlow(
                context, device, SETTINGS_PACKAGE, ux, isOptin, flipConsent, assertOptIn);
    }

    public static void testSettingsPageFlow(
            Context context,
            UiDevice device,
            String packageName,
            UiConstants.UX ux,
            boolean isOptin,
            boolean flipConsent,
            boolean assertOptIn)
            throws Exception {
        switch (ux) {
            case GA_UX:
                UiUtils.enableGa();
                break;
            case BETA_UX:
                UiUtils.enableBeta();
                break;
            case U18_UX:
                UiUtils.enableGa();
                UiUtils.enableU18();
                break;
            case RVC_UX:
                UiUtils.enableGa();
                UiUtils.enableRvc();
        }
        startSettingsActivity(context, device, packageName);
        SettingsPages.testSettingsPageConsents(
                context, device, ux, isOptin, flipConsent, assertOptIn);
    }

    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuDevice,
            UiConstants.UX ux)
            throws Exception {
        NotificationPages.verifyNotification(context, device, isDisplayed, isEuDevice, ux, true);
    }

    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuDevice,
            UiConstants.UX ux,
            boolean isV2)
            throws Exception {
        NotificationPages.verifyNotification(context, device, isDisplayed, isEuDevice, ux, isV2);
    }

    public static void testClickNotificationFlow(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuDevice,
            UiConstants.UX ux,
            boolean isV2,
            boolean isOptin)
            throws Exception {
        NotificationPages.verifyNotification(context, device, isDisplayed, isEuDevice, ux, isV2);
        // Only GA and row devices needs to got to settings page to set up consent.
        boolean isGoSettings = !isEuDevice;
        notificationConfirmWorkflow(
                context, device, isDisplayed, isEuDevice, ux, isV2, isGoSettings, isOptin);
    }

    public static void notificationConfirmWorkflow(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuDevice,
            UiConstants.UX ux,
            boolean isV2,
            boolean isGoSettings,
            boolean isOptin)
            throws Exception {
        if (!isDisplayed) {
            return;
        }
        switch (ux) {
            case GA_UX:
                if (!isEuDevice) {
                    NotificationPages.rowNotificationLandingPage(context, device, isGoSettings);
                } else {
                    if (isV2) {
                        NotificationPages.euNotificationLandingPageMsmtAndFledgePage(
                                context, device, isGoSettings, isV2);
                        if (!isGoSettings) {
                            NotificationPages.euNotificationLandingPageTopicsPage(
                                    context, device, isOptin, isV2);
                        }
                    } else {
                        NotificationPages.euNotificationLandingPageTopicsPage(
                                context, device, isOptin, isV2);
                        NotificationPages.euNotificationLandingPageMsmtAndFledgePage(
                                context, device, isGoSettings, isV2);
                    }
                }
                break;
            case BETA_UX:
                NotificationPages.betaNotificationPage(
                        context, device, isEuDevice, isGoSettings, isOptin);
                if (isEuDevice) {
                    NotificationPages.betaNotificationConfirmPage(context, device, isGoSettings);
                }
                break;
            case U18_UX:
                NotificationPages.u18NotifiacitonLandingPage(context, device, isGoSettings);
        }

        // if decide to go settings page, then we test settings page consent
        if (isGoSettings) {
            SettingsPages.testSettingsPageConsents(context, device, ux, isOptin, false, false);
        }
    }
}
