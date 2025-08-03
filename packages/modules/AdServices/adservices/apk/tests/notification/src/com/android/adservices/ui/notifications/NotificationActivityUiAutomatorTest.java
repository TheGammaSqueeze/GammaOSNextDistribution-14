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

package com.android.adservices.ui.notifications;

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_RECORD_MANUAL_INTERACTION_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_FEATURE_TYPE_LOGGING_ENABLED;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.BETA_UX;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.ui.util.AdServicesUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.NotificationActivityTestUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.io.IOException;

@SpyStatic(PhFlags.class)
@SpyStatic(BackgroundJobsManager.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(UxStatesManager.class)
@RunWith(AndroidJUnit4.class)
public final class NotificationActivityUiAutomatorTest extends AdServicesUiTestCase {

    @Mock private ConsentManager mConsentManager;

    // TODO(b/261216850): Migrate this NotificationActivity to non-mock test
    @Mock private UxStatesManager mUxStatesManager;

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, true)
                    .setFlag(KEY_UI_FEATURE_TYPE_LOGGING_ENABLED, true)
                    .setFlag(KEY_RECORD_MANUAL_INTERACTION_ENABLED, true)
                    .setFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true)
                    .setFlag(KEY_DEBUG_UX, "GA_UX")
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true);

    @Before
    public void setup() throws UiObjectNotFoundException, IOException {
        doReturn(BETA_UX).when(mUxStatesManager).getUx();
        ExtendedMockito.doReturn(mUxStatesManager).when(() -> UxStatesManager.getInstance());
        ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance());
        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void notificationRowGaTest() throws Exception {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);
        UiObject2 notificationGaTitle =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_header_ga_title);
        assertThat(notificationGaTitle).isNotNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_left_control_button_text);
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_right_control_button_text);
        assertThat(leftControlButton).isNotNull();
        assertThat(rightControlButton).isNotNull();
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void notificationEuGaTest() throws Exception {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ true, mDevice);
        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_confirmation_left_control_button_text);
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_confirmation_right_control_button_text);
        assertThat(leftControlButton).isNotNull();
        assertThat(rightControlButton).isNotNull();

        rightControlButton.clickAndWait(Until.newWindow(), LAUNCH_TIMEOUT);

        UiObject2 acceptedTitle =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_header_ga_title_eu_v2);
        assertThat(acceptedTitle).isNotNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButtonOnSecondPage =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_left_control_button_text_eu_v2);
        UiObject2 rightControlButtonOnSecondPage =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_right_control_button_ga_text_eu_v2);
        assertThat(leftControlButtonOnSecondPage).isNotNull();
        assertThat(rightControlButtonOnSecondPage).isNotNull();
    }
}
