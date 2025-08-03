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
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.ui.util.NotificationActivityTestUtil.WINDOW_LAUNCH_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;


import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.ui.util.AdServicesUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.NotificationActivityTestUtil;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NotificationActivityU18UxSelectorUiAutomatorTest extends AdServicesUiTestCase {

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, true)
                    .setFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true)
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_U18_UX_ENABLED, true)
                    .setFlag(KEY_DEBUG_UX, "U18_UX");

    @BeforeClass
    public static void classSetup() throws Exception {
        NotificationActivityTestUtil.setupBeforeTests();
    }

    @Test
    public void acceptFlowTest() throws Exception {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);

        UiObject2 u18NotificationTitle =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_u18_notification_title);
        assertThat(u18NotificationTitle).isNotNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_u18_left_control_button_text);
        assertThat(leftControlButton).isNotNull();
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_u18_right_control_button_text);
        rightControlButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);

        // Retrieve a new instance to avoid android.support.test.uiautomator.StaleObjectException.
        u18NotificationTitle =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_u18_notification_title);
        assertThat(u18NotificationTitle).isNull();
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void clickSettingsTest() throws InterruptedException {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);
        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_u18_left_control_button_text);
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_u18_right_control_button_text);
        assertThat(rightControlButton).isNotNull();

        leftControlButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);

        // make sure it goes to u18 page rather than GA page
        UiObject2 topicTitle = ApkTestUtil.getElement(mDevice, R.string.settingsUI_topics_ga_title);
        assertThat(topicTitle).isNull();
        UiObject2 measurementTitle =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_u18_measurement_view_title);
        assertThat(measurementTitle).isNotNull();
    }

}
