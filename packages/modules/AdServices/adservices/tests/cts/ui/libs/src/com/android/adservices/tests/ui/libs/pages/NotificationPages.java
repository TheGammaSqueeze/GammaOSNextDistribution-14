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
package com.android.adservices.tests.ui.libs.pages;

import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_RESOURCE_ID;
import static com.android.adservices.tests.ui.libs.UiUtils.LAUNCH_TIMEOUT;
import static com.android.adservices.tests.ui.libs.UiUtils.PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT;
import static com.android.adservices.tests.ui.libs.UiUtils.SCROLL_WAIT_TIME;
import static com.android.adservices.tests.ui.libs.UiUtils.getElement;
import static com.android.adservices.tests.ui.libs.UiUtils.getPageElement;
import static com.android.adservices.tests.ui.libs.UiUtils.getString;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.tests.ui.libs.UiConstants;

public class NotificationPages {
    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuTest,
            UiConstants.UX ux,
            boolean isV2)
            throws Exception {
        device.openNotification();
        Thread.sleep(LAUNCH_TIMEOUT);

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
            case RVC_UX:
                notificationTitle = R.string.notificationUI_u18_notification_title;
                notificationHeader = R.string.notificationUI_u18_header_title;
                break;
        }

        Log.d(
                "adservices",
                "Expected notification card title is: " + getString(context, notificationTitle));
        Log.d(
                "adservices",
                "Expected notification landing page title is: "
                        + getString(context, notificationHeader));

        UiSelector notificationCardSelector =
                new UiSelector().text(getString(context, notificationTitle));

        UiObject2 scroller = device.findObject(By.res(SYSTEM_UI_RESOURCE_ID));
        UiObject2 notificationCard =
                scroller.findObject(By.textContains(getString(context, notificationTitle)));
        if (!isDisplayed) {
            assertThat(notificationCard).isNull();
            return;
        }

        assertThat(notificationCard).isNotNull();
        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject2 title = getPageElement(context, device, notificationHeader);

        assertThat(title).isNotNull();
    }

    public static void betaNotificationPage(
            Context context,
            UiDevice device,
            boolean isEuDevice,
            boolean isGoSettings,
            boolean isOptin)
            throws UiObjectNotFoundException, InterruptedException {
        int leftButtonResId =
                isEuDevice
                        ? R.string.notificationUI_left_control_button_text_eu
                        : R.string.notificationUI_left_control_button_text;

        int rightButtonResId =
                isEuDevice
                        ? R.string.notificationUI_right_control_button_text_eu
                        : R.string.notificationUI_right_control_button_text;

        goThroughNotificationPage(context, device, leftButtonResId, rightButtonResId);
        UiObject2 leftControlButton = getElement(context, device, leftButtonResId);
        UiObject2 rightControlButton = getElement(context, device, rightButtonResId);

        if (isEuDevice) {
            if (!isOptin) {
                leftControlButton.click();
            } else {
                rightControlButton.click();
                Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
                UiObject2 acceptedTitle =
                        getElement(
                                context, device, R.string.notificationUI_confirmation_accept_title);
                assertThat(acceptedTitle).isNotNull();
            }
        } else {
            if (isGoSettings) {
                leftControlButton.click();
            } else {
                rightControlButton.click();
            }
            Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        }
    }

    public static void betaNotificationConfirmPage(
            Context context, UiDevice device, boolean isGoSettings)
            throws UiObjectNotFoundException, InterruptedException {
        int leftButtonResId = R.string.notificationUI_confirmation_left_control_button_text;
        int rightButtonResId = R.string.notificationUI_confirmation_right_control_button_text;

        UiObject2 leftControlButton = getElement(context, device, leftButtonResId);
        UiObject2 rightControlButton = getElement(context, device, rightButtonResId);

        if (isGoSettings) {
            leftControlButton.click();
        } else {
            rightControlButton.click();
        }
        Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
    }

    public static void euNotificationLandingPageMsmtAndFledgePage(
            Context context, UiDevice device, boolean isGoSettings, boolean isFlip)
            throws UiObjectNotFoundException, InterruptedException {
        int leftButtonResId = R.string.notificationUI_left_control_button_text;
        int rightButtonResId = R.string.notificationUI_right_control_button_text;
        goThroughNotificationPage(context, device, leftButtonResId, rightButtonResId);
        UiObject2 leftControlButton = getElement(context, device, leftButtonResId);
        UiObject2 rightControlButton = getElement(context, device, rightButtonResId);
        if (isGoSettings) {
            leftControlButton.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        } else {
            rightControlButton.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            if (isFlip) {
                UiObject2 title2 =
                        getElement(context, device, R.string.notificationUI_header_ga_title_eu_v2);
                assertThat(title2).isNotNull();
            }
        }
    }

    public static void euNotificationLandingPageTopicsPage(
            Context context, UiDevice device, boolean isOptin, boolean isFlip)
            throws UiObjectNotFoundException, InterruptedException {
        int leftButtonResId = R.string.notificationUI_left_control_button_text_eu;
        int rightButtonResId =
                isFlip
                        ? R.string.notificationUI_right_control_button_ga_text_eu_v2
                        : R.string.notificationUI_right_control_button_ga_text_eu;
        goThroughNotificationPage(context, device, leftButtonResId, rightButtonResId);
        UiObject2 leftControlButton = getElement(context, device, leftButtonResId);
        UiObject2 rightControlButton = getElement(context, device, rightButtonResId);
        if (isOptin) {
            rightControlButton.click();
            if (!isFlip) {
                Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
                UiObject2 acceptedTitle =
                        getElement(
                                context, device, R.string.notificationUI_fledge_measurement_title);
                assertThat(acceptedTitle).isNotNull();
            }
        } else {
            leftControlButton.click();
        }
    }

    public static void rowNotificationLandingPage(
            Context context, UiDevice device, boolean isGoSettings)
            throws UiObjectNotFoundException, InterruptedException {
        int leftButtonResId = R.string.notificationUI_left_control_button_text;
        int rightButtonResId = R.string.notificationUI_right_control_button_text;
        goThroughNotificationPage(context, device, leftButtonResId, rightButtonResId);
        UiObject2 leftControlButton = getElement(context, device, leftButtonResId);
        UiObject2 rightControlButton = getElement(context, device, rightButtonResId);
        if (isGoSettings) {
            leftControlButton.click();
        } else {
            rightControlButton.click();
        }
        Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
    }

    public static void u18NotifiacitonLandingPage(
            Context context, UiDevice device, boolean isGoSettings)
            throws UiObjectNotFoundException, InterruptedException {
        int leftButtonResId = R.string.notificationUI_u18_left_control_button_text;
        int rightButtonResId = R.string.notificationUI_u18_right_control_button_text;
        goThroughNotificationPage(context, device, leftButtonResId, rightButtonResId);
        UiObject2 leftControlButton = getElement(context, device, leftButtonResId);
        UiObject2 rightControlButton = getElement(context, device, rightButtonResId);
        if (isGoSettings) {
            leftControlButton.click();
        } else {
            rightControlButton.click();
        }
        Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        UiObject2 topicTitle = getElement(context, device, R.string.settingsUI_topics_ga_title);
        assertThat(topicTitle).isNotNull();
    }

    public static void goThroughNotificationPage(
            Context context, UiDevice device, int leftButtonResId, int rightButtonResId)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject2 leftControlButton = getElement(context, device, leftButtonResId);
        UiObject2 rightControlButton = getElement(context, device, rightButtonResId);
        UiObject2 moreButton =
                getElement(context, device, R.string.notificationUI_more_button_text);
        UiObject2 scrollView = device.findObject(By.clazz("android.widget.ScrollView"));
        if (scrollView.isScrollable()) {
            assertThat(leftControlButton).isNull();
            assertThat(rightControlButton).isNull();
            assertThat(moreButton).isNotNull();
            int clickCount = 10;
            while (moreButton != null && clickCount-- > 0) {
                moreButton.click();
                Thread.sleep(SCROLL_WAIT_TIME);
                moreButton = getElement(context, device, R.string.notificationUI_more_button_text);
            }
        } else {
            leftControlButton = getElement(context, device, leftButtonResId);
            rightControlButton = getElement(context, device, rightButtonResId);
            assertThat(leftControlButton).isNotNull();
            assertThat(rightControlButton).isNotNull();
            assertThat(moreButton).isNull();
        }
    }
}
