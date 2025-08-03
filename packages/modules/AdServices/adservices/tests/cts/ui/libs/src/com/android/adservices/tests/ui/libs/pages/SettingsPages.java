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

import static com.android.adservices.tests.ui.libs.UiUtils.PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT;
import static com.android.adservices.tests.ui.libs.UiUtils.scrollToAndClick;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import com.android.adservices.api.R;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;

public class SettingsPages {
    public static void testSettingsPageConsents(
            Context context,
            UiDevice device,
            UiConstants.UX ux,
            boolean isOptin,
            boolean flipConsent,
            boolean assertOptIn)
            throws UiObjectNotFoundException, InterruptedException {
        switch (ux) {
            case GA_UX:
                enterTopicsConsentPage(context, device);
                flipConsent(device, isOptin, flipConsent, assertOptIn);
                device.pressBack();
                enterFledgeConsentPage(context, device);
                flipConsent(device, isOptin, flipConsent, assertOptIn);
                device.pressBack();
                enterMsmtConsentPage(context, device);
                flipConsent(device, isOptin, flipConsent, assertOptIn);
                device.pressBack();
                break;
            case BETA_UX:
                flipConsent(device, isOptin, flipConsent, assertOptIn);
                break;
            case U18_UX:
                enterU18ConsentPage(context, device);
                flipConsent(device, isOptin, flipConsent, assertOptIn);
                break;
            case RVC_UX:
                enterU18ConsentPage(context, device);
                flipConsent(device, isOptin, flipConsent, assertOptIn);
        }
    }

    public static void enterTopicsConsentPage(Context context, UiDevice device)
            throws UiObjectNotFoundException, InterruptedException {
        scrollToAndClick(context, device, R.string.settingsUI_topics_ga_title);
    }

    public static void enterMsmtConsentPage(Context context, UiDevice device)
            throws UiObjectNotFoundException, InterruptedException {
        scrollToAndClick(context, device, R.string.settingsUI_measurement_view_title);
    }

    public static void enterU18ConsentPage(Context context, UiDevice device)
            throws UiObjectNotFoundException, InterruptedException {
        scrollToAndClick(context, device, R.string.settingsUI_u18_measurement_view_title);
    }

    public static void enterFledgeConsentPage(Context context, UiDevice device)
            throws UiObjectNotFoundException, InterruptedException {
        scrollToAndClick(context, device, R.string.settingsUI_apps_ga_title);
    }

    /** Flips the consent toggle. */
    public static void flipConsent(
            UiDevice device, boolean isOptin, boolean flipConsent, boolean assertOptIn)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject consentSwitch =
                device.findObject(new UiSelector().className("android.widget.Switch"));
        consentSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(consentSwitch.exists()).isTrue();

        // The consent toggle can be blocked by the navigation bar on some devices.
        UiUtils.gentleSwipe(device);

        boolean consentStatus = consentSwitch.isChecked();
        if (flipConsent) {
            consentSwitch.clickTopLeft();
            Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(consentSwitch.isChecked()).isEqualTo(!consentStatus);
        } else if (isOptin) {
            if (!consentStatus) {
                consentSwitch.clickTopLeft();
            }
            Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(consentSwitch.isChecked()).isTrue();
        } else if (assertOptIn) {
            assertThat(consentSwitch.isChecked()).isTrue();
        } else {
            if (consentStatus) {
                consentSwitch.clickTopLeft();
            }
            Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(consentSwitch.isChecked()).isFalse();
        }
    }
}
