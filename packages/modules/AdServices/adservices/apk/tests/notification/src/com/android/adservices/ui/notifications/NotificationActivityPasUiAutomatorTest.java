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
package com.android.adservices.ui.notifications;

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_TOGGLE_SPEED_BUMP_ENABLED;
import static com.android.adservices.ui.util.NotificationActivityTestUtil.WINDOW_LAUNCH_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.ui.util.AdServicesUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.NotificationActivityTestUtil;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NotificationActivityPasUiAutomatorTest extends AdServicesUiTestCase {

    private static final String ANDROID_WIDGET_SWITCH = "android.widget.Switch";
    private static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS = 2_000;

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, true)
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_U18_UX_ENABLED, true)
                    .setFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true)
                    .setFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, true)
                    .setFlag(KEY_DEBUG_UX, "GA_UX")
                    .setFlag(KEY_PAS_UX_ENABLED, true)
                    .setFlag(KEY_IS_EEA_DEVICE_FEATURE_ENABLED, true)
                    .setFlag(KEY_IS_EEA_DEVICE, false)
                    .setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, false);

    /**
     * Setup before notification tests.
     *
     * @throws Exception general exception
     */
    @BeforeClass
    public static void classSetup() throws Exception {
        NotificationActivityTestUtil.setupBeforeTests();
    }

    @Before
    public void setup() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
    }

    @Test
    public void renotifyClickSettingsTest() throws Exception {
        // enable at least one of Fledge or Mesurement API
        ApkTestUtil.launchSettingView(mDevice, LAUNCH_TIMEOUT);
        mDevice.waitForIdle();
        ApkTestUtil.scrollToAndClick(mDevice, R.string.settingsUI_apps_ga_title);
        UiObject2 appsToggle =
                mDevice.wait(
                        Until.findObject(By.clazz(ANDROID_WIDGET_SWITCH)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (!appsToggle.isChecked()) {
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
        }
        mDevice.waitForIdle();

        // start renotify notice
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);

        UiObject2 pasNotificationHeader =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_pas_renotify_header_title);
        assertThat(pasNotificationHeader).isNotNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_left_control_button_text);
        assertThat(leftControlButton).isNotNull();
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_right_control_button_text);
        assertThat(rightControlButton).isNotNull();

        // check manage settings button works
        leftControlButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
        UiObject2 topicsTitle =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_topics_ga_title);
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_topics_ga_title);
        assertThat(topicsTitle).isNotNull();
        UiObject2 appsTitle = ApkTestUtil.getElement(mDevice, R.string.settingsUI_apps_ga_title);
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_apps_ga_title);
        assertThat(appsTitle).isNotNull();
    }

    @Test
    public void firstTimeRowCombinedTextShownTest() throws Exception {
        // disable both Fledge and Measurement
        ApkTestUtil.launchSettingView(mDevice, LAUNCH_TIMEOUT);
        mDevice.waitForIdle();
        ApkTestUtil.scrollToAndClick(mDevice, R.string.settingsUI_apps_ga_title);
        UiObject2 appsToggle =
                mDevice.wait(
                        Until.findObject(By.clazz(ANDROID_WIDGET_SWITCH)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (appsToggle.isChecked()) {
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
        }
        mDevice.waitForIdle();
        mDevice.pressBack();
        mDevice.waitForIdle();
        ApkTestUtil.scrollToAndClick(mDevice, R.string.settingsUI_measurement_ga_title);
        UiObject2 measurementToggle =
                mDevice.wait(
                        Until.findObject(By.clazz(ANDROID_WIDGET_SWITCH)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (measurementToggle.isChecked()) {
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
        }
        mDevice.waitForIdle();

        // start combined notice activity
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);

        UiObject2 pasNotificationHeader =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_pas_combined_header_title);
        assertThat(pasNotificationHeader).isNotNull();
        UiObject2 pasNotificationBody =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_pas_combined_body_2);
        assertThat(pasNotificationBody).isNotNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_left_control_button_text);
        assertThat(leftControlButton).isNotNull();
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_right_control_button_text);
        assertThat(rightControlButton).isNotNull();
    }
}
