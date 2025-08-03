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

import static android.Manifest.permission.POST_NOTIFICATIONS;

import static com.android.adservices.tests.ui.libs.UiConstants.AD_ID_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.ENTRY_POINT_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_RESOURCE_ID;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.graphics.Point;
import android.os.OutcomeReceiver;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.SearchCondition;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.common.FileHelper;
import com.android.compatibility.common.util.ShellUtils;

import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class UiUtils {
    private static final String PRIVACY_SANDBOX_PACKAGE_NAME = "android.adservices.ui.SETTINGS";
    private static final String NOTIFICATION_PACKAGE_NAME = "android.adservices.ui.NOTIFICATIONS";
    public static final int LAUNCH_TIMEOUT = 5000;
    public static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT = 500;
    public static final int SCROLL_WAIT_TIME = 1000;

    private static final String ANDROID_WIDGET_SCROLLVIEW = "android.widget.ScrollView";

    private static void forceSetFlag(String flagName, boolean newFlagValue) throws Exception {
        String currentFlagValue =
                ShellUtils.runShellCommand("device_config get adservices " + flagName);

        for (int i = 0; i < 5; i++) {
            if (currentFlagValue.equals(String.valueOf(newFlagValue))) {
                return;
            }

            ShellUtils.runShellCommand(
                    String.format("device_config put adservices %s %s", flagName, newFlagValue));

            Thread.sleep(250);

            currentFlagValue =
                    ShellUtils.runShellCommand("device_config get adservices " + flagName);

            LogUtil.e(String.format("Flag was not set on iteration %d.", i));
        }

        throw new IllegalStateException("Unable to set flag in 5 iterations.");
    }

    public static void refreshConsentResetToken() {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_reset_token "
                        + UUID.randomUUID().toString());
    }

    public static void turnOffEnableAdsServicesAPI() throws Exception {
        forceSetFlag("enable_ad_services_system_api", false);
    }

    public static void turnOnEnableAdsServicesAPI() throws Exception {
        forceSetFlag("enable_ad_services_system_api", true);
    }

    public static void disableSchedulingParams() {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_interval_begin_ms 0");
        // set the notification interval end time to 12:00 AM
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_interval_end_ms 86400000");
    }

    public static void enableConsentDebugMode() throws Exception {
        forceSetFlag("consent_notification_debug_mode", true);
    }

    public static void disableConsentDebugMode() throws Exception {
        forceSetFlag("consent_notification_debug_mode", false);
    }

    public static void enableGlobalKillSwitch() throws Exception {
        forceSetFlag("global_kill_switch", true);
    }

    public static void setAsEuDevice() throws Exception {
        forceSetFlag("is_eea_device", true);
    }

    public static void setAsRowDevice() throws Exception {
        forceSetFlag("is_eea_device", false);
    }

    public static void enableU18() throws Exception {
        forceSetFlag("u18_ux_enabled", true);
    }

    public static void enableGa() throws Exception {
        forceSetFlag("ga_ux_enabled", true);
    }

    /** Override flag rvc_ux_enabled in tests to true */
    public static void enableRvc() throws Exception {
        forceSetFlag("rvc_ux_enabled", true);
    }

    /** Override flag rvc_post_ota_notification_enabled in tests to true */
    public static void enableRvcNotification() throws Exception {
        forceSetFlag("rvc_post_ota_notification_enabled", true);
    }

    /** Override flag rvc_ux_enabled in tests to false */
    public static void disableRvc() throws Exception {
        forceSetFlag("rvc_ux_enabled", false);
    }

    /** Disables the enableAdServices system API. */
    public static void turnOffEnableAdServicesSystemApi() throws Exception {
        forceSetFlag("enable_ad_services_system_api", false);
    }

    /** Enables the enableAdServices system API. */
    public static void turnOnAdServicesSystemApi() throws Exception {
        forceSetFlag("enable_ad_services_system_api", true);
    }

    public static void enableBeta() throws Exception {
        forceSetFlag("ga_ux_enabled", false);
    }

    public static void enableOtaStrings() throws Exception {
        forceSetFlag("ui_ota_strings_feature_enabled", true);
    }

    public static void disableOtaStrings() throws Exception {
        forceSetFlag("ui_ota_strings_feature_enabled", false);
    }

    public static void restartAdservices() {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
        ShellUtils.runShellCommand("am force-stop com.android.adservices.api");
    }

    public static void clearSavedStatus() {
        ShellUtils.runShellCommand(
                "rm /data/user/0/com.google.android.adservices.api/files/"
                        + "ConsentManagerStorageIdentifier.xml");
        ShellUtils.runShellCommand(
                "rm /data/user/0/com.android.adservices.api/files/"
                        + "ConsentManagerStorageIdentifier.xml");
        ShellUtils.runShellCommand(
                "rm /data/system/adservices/0/consent/ConsentManagerStorageIdentifier.xml");
    }

    public static void setSourceOfTruthToPPAPI() {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
    }

    /** Set flag consent_manager_debug_mode to true in tests */
    public static void setConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
    }

    /** Set flag consent_manager_ota_debug_mode to true in tests */
    public static void setConsentManagerOtaDebugMode() {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_manager_ota_debug_mode true");
    }

    /** Set flag consent_manager_debug_mode to false in tests */
    public static void resetConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode false");
    }

    public static void enableNotificationPermission() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .grantRuntimePermission("com.android.adservices.api", POST_NOTIFICATIONS);
    }

    public static void disableNotificationFlowV2() throws Exception {
        forceSetFlag("eu_notif_flow_change_enabled", false);
    }

    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuTest,
            UiConstants.UX ux)
            throws Exception {
        int notificationTitle = -1;
        int notificationHeader = -1;
        switch (ux) {
            case GA_UX:
                // Should match the contentTitle string in ConsentNotificationTrigger.java.
                notificationTitle =
                        isEuTest
                                ? R.string.notificationUI_notification_ga_title_eu_v2
                                : R.string.notificationUI_notification_ga_title_v2;
                // Should match the text in consent_notification_screen_1_ga_v2_eu.xml and
                // consent_notification_screen_1_ga_v2_row.xml, respectively.
                notificationHeader =
                        isEuTest
                                ? R.string.notificationUI_fledge_measurement_title_v2
                                : R.string.notificationUI_header_ga_title_v2;
                break;
            case BETA_UX:
                notificationTitle =
                        isEuTest
                                ? R.string.notificationUI_notification_title_eu
                                : R.string.notificationUI_notification_title;
                notificationHeader =
                        isEuTest
                                ? R.string.notificationUI_header_title_eu
                                : R.string.notificationUI_header_title;
                break;
            case U18_UX:
                notificationTitle = R.string.notificationUI_u18_notification_title;
                notificationHeader = R.string.notificationUI_u18_header_title;
                break;
        }

        verifyNotification(context, device, isDisplayed, notificationTitle, notificationHeader);
    }

    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            int notificationTitle,
            int notificationHeader)
            throws Exception {
        device.openNotification();
        device.waitForIdle(LAUNCH_TIMEOUT);
        // Wait few seconds for Adservices notification to show, waitForIdle is not enough.
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject2 scroller = device.findObject(By.res(SYSTEM_UI_RESOURCE_ID));

        BySelector notificationTitleSelector = By.text(getString(context, notificationTitle));
        if (!isDisplayed) {
            assertThat(scroller.hasObject(notificationTitleSelector)).isFalse();
            return;
        }
        assertThat(scroller.hasObject(notificationTitleSelector)).isTrue();
        UiObject2 notificationCard =
                scroller.findObject(By.text(getString(context, notificationTitle)));

        notificationCard.click();
        device.waitForIdle(LAUNCH_TIMEOUT);
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject2 title = getElement(context, device, notificationHeader);
        assertThat(title).isNotNull();
    }

    public static void setupOTAStrings(
            Context context, UiDevice device, AdServicesCommonManager commonManager, String mddURL)
            throws Exception {
        setAdServicesFlagsForOTATesting(mddURL);

        AdservicesTestHelper.killAdservicesProcess(context);

        // Consent is required to trigger the MDD job.
        commonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        Thread.sleep(LAUNCH_TIMEOUT);

        ShellUtils.runShellCommand("cmd jobscheduler run -f com.google.android.adservices.api 14");

        Thread.sleep(LAUNCH_TIMEOUT);
        clearNotifications(context, device);
    }

    public static void setAdServicesFlagsForOTATesting(String mddURL) throws Exception {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_ota_strings_feature_enabled true");
        ShellUtils.runShellCommand("device_config put adservices adservice_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_debug_mode true");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices mdd_ui_ota_strings_manifest_file_url " + mddURL);

        // Set as ROW device for default consent opt-in.
        UiUtils.setAsRowDevice();
    }

    public static void setOTADownloadTimeout(long timeout) {
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices ui_ota_strings_download_deadline %d",
                        timeout));
    }

    public static void clearNotifications(Context context, UiDevice device)
            throws InterruptedException {
        device.openNotification();
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject2 scroller2 =
                device.findObject(By.res("com.android.systemui:id/notification_stack_scroller"));
        UiObject2 notificationCard =
                scroller2.findObject(
                        By.textContains(
                                getString(context, R.string.notificationUI_notification_title)));

        if (notificationCard != null) {
            notificationCard.click();
        }
        Thread.sleep(LAUNCH_TIMEOUT);
        device.pressHome();
    }

    public static void verifyNotificationAndSettingsPage(
            Context context, UiDevice device, Boolean isOTA) throws InterruptedException {
        // open notification tray
        device.openNotification();
        Thread.sleep(LAUNCH_TIMEOUT);
        device.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

        // verify notification card
        UiObject2 scroller =
                device.findObject(By.res("com.android.systemui:id/notification_stack_scroller"));

        String targetStr =
                isOTA
                        ? getOTAString(context, R.string.notificationUI_notification_title)
                        : getString(context, R.string.notificationUI_notification_title);

        UiObject2 notificationCard = scroller.findObject(By.text(targetStr));
        Thread.sleep(LAUNCH_TIMEOUT);

        notificationCard.click();

        // Wait for the notification landing page to appear
        device.wait(Until.hasObject(By.pkg(NOTIFICATION_PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT);

        // verify strings
        UiObject2 title =
                isOTA
                        ? scrollToOTAElement(context, device, R.string.notificationUI_header_title)
                        : scrollToElement(context, device, R.string.notificationUI_header_title);

        assertThat(title).isNotNull();
        // open settings
        scrollToThenClickElementContainingText(
                device,
                isOTA
                        ? "Manage privacy settings!"
                        : getString(context, R.string.notificationUI_left_control_button_text));

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(PRIVACY_SANDBOX_PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT);

        // verify strings have changed
        UiObject2 appButton =
                isOTA
                        ? scrollToOTAElement(context, device, R.string.settingsUI_apps_title)
                        : scrollToElement(context, device, R.string.settingsUI_apps_title);

        device.waitForIdle(LAUNCH_TIMEOUT);
        assertThat(appButton).isNotNull();
        UiObject2 topicsButton =
                isOTA
                        ? scrollToOTAElement(context, device, R.string.settingsUI_topics_title)
                        : scrollToElement(context, device, R.string.settingsUI_topics_title);
        assertThat(topicsButton).isNotNull();
    }

    public static void connectToWifi() {
        ShellUtils.runShellCommand("svc wifi enable");
        ShellUtils.runShellCommand("cmd wifi connect-network VirtWifi open");
    }

    public static void turnOffWifi() {
        ShellUtils.runShellCommand("svc wifi disable");
    }

    private static void scrollToThenClickElementContainingText(UiDevice device, String text) {
        UiObject2 element = scrollToElement(device, text);
        element.click();
    }

    private static UiObject2 scrollToOTAElement(Context context, UiDevice device, int resId) {
        return scrollToElement(device, getOTAString(context, resId));
    }

    private static UiObject2 scrollToElement(UiDevice device, String targetStr) {
        UiObject2 scrollView =
                device.findObject(By.clazz(ANDROID_WIDGET_SCROLLVIEW).scrollable(true));
        UiObject2 element =
                scrollView.scrollUntil(
                        Direction.DOWN, Until.findObject(By.textContains(targetStr)));
        if (element != null) {
            return element;
        }
        return device.findObject(By.textContains(targetStr));
    }

    private static UiObject2 scrollToElement(Context context, UiDevice device, int resId) {
        return scrollToElement(device, getString(context, resId));
    }

    // Test strings for OTA have an exclamation mark appended to the end
    private static String getOTAString(Context context, int resourceId) {
        return getString(context, resourceId) + "!";
    }

    /**
     * Swipes through the screen to show elements on the button of the page but hidden by the
     * navigation bar.
     */
    public static void gentleSwipe(UiDevice device) {
        UiObject2 scrollView =
                device.findObject(By.scrollable(true).clazz(ANDROID_WIDGET_SCROLLVIEW));
        // Some devices on R is not scrollable
        if (scrollView != null) {
                scrollView.scroll(Direction.DOWN, /* percent */ 0.25F);
        }
    }

    public static void setFlipFlow(boolean isFlip) {
        ShellUtils.runShellCommand(
                "device_config put adservices eu_notif_flow_change_enabled " + isFlip);
    }

    /** set get adservices common states services enabled */
    public static void setGetAdservicesCommonStatesServiceEnable(boolean enable) {
        ShellUtils.runShellCommand(
                "device_config put adservices get_adservices_common_states_api_enabled " + enable);
    }

    /** set get adservices common states services enabled */
    public static void setGetAdservicesCommonStatesAllowList(String list) {
        ShellUtils.runShellCommand(
                "device_config put adservices get_adservices_common_states_allow_list " + list);
    }

    public static String getString(Context context, int resourceId) {
        return context.getResources().getString(resourceId);
    }

    public static void scrollToAndClick(Context context, UiDevice device, int resId) {
        scrollTo(context, device, resId);
        UiObject2 consentPageButton =
                device.wait(
                        getSearchCondByResId(context, resId), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        clickTopLeft(consentPageButton);
    }

    public static SearchCondition<UiObject2> getSearchCondByResId(Context context, int resId) {
        String targetStr = getString(context, resId);
        return Until.findObject(By.text(Pattern.compile(targetStr, Pattern.CASE_INSENSITIVE)));
    }

    public static UiObject2 getPageElement(Context context, UiDevice device, int resId) {
        return device.findObject(By.text(getString(context, resId)));
    }

    public static UiObject2 scrollTo(Context context, UiDevice device, int resId) {
        UiObject2 scrollView =
                device.findObject(By.scrollable(true).clazz(ANDROID_WIDGET_SCROLLVIEW));
        if (scrollView != null) {
            String targetStr = getString(context, resId);
            scrollView.scrollUntil(
                    Direction.DOWN,
                    Until.findObject(
                            By.text(Pattern.compile(targetStr, Pattern.CASE_INSENSITIVE))));
        }
        return getElement(context, device, resId);
    }

    public static UiObject2 getElement(Context context, UiDevice device, int resId) {
        return getElement(context, device, resId, 0);
    }

    public static UiObject2 getElement(Context context, UiDevice device, int resId, int index) {
        String targetStr = getString(context, resId);
        List<UiObject2> objList =
                device.findObjects(By.text(Pattern.compile(targetStr, Pattern.CASE_INSENSITIVE)));
        if (objList.size() <= index) {
            return null;
        }
        return objList.get(index);
    }

    public static void clickTopLeft(UiObject2 obj) {
        assertThat(obj).isNotNull();
        obj.click(new Point(obj.getVisibleBounds().top, obj.getVisibleBounds().left));
    }

    public static void takeScreenshot(UiDevice device, String methodName) {
        try {
            String timeStamp =
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                            .format(Date.from(Instant.now()));

            File screenshotFile =
                    new File(
                            FileHelper.getAdServicesTestsOutputDir(),
                            methodName + timeStamp + ".png");
            device.takeScreenshot(screenshotFile);
        } catch (RuntimeException e) {
            LogUtil.e("Failed to take screenshot: " + e.getMessage());
        }
    }

    public static void resetAdServicesConsentData(Context context) throws Exception {
        // Neeed to disable debug mode since it takes precedence over reset channel.
        disableConsentDebugMode();
        turnOnAdServicesSystemApi();

        AdServicesCommonManager mCommonManager = AdServicesCommonManager.get(context);

        // Reset consent and thereby AdServices data before each test.
        UiUtils.refreshConsentResetToken();

        SettableFuture<Boolean> responseFuture = SettableFuture.create();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build(),
                Executors.newCachedThreadPool(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        responseFuture.set(result);
                    }

                    @Override
                    public void onError(Exception exception) {
                        responseFuture.setException(exception);
                    }
                });

        Boolean response = responseFuture.get();
        assertThat(response).isTrue();
    }
}
