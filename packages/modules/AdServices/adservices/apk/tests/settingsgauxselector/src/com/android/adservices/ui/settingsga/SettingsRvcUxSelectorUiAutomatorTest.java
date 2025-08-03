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
package com.android.adservices.ui.settingsga;

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_RVC_POST_OTA_NOTIFICATION_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_RVC_UX_ENABLED;



import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.ui.util.AdServicesUiTestCase;
import com.android.adservices.ui.util.SettingsTestUtil;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SettingsRvcUxSelectorUiAutomatorTest extends AdServicesUiTestCase {

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, true)
                    .setFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true)
                    .setFlag(KEY_RVC_UX_ENABLED, true)
                    .setFlag(KEY_RVC_POST_OTA_NOTIFICATION_ENABLED, true)
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_DEBUG_UX, "RVC_UX")
                    .setCompatModeFlags();

    @Before
    public void setup() {
        // This test is only enabled on R.
        Assume.assumeTrue(SdkLevel.isAtLeastR() && !SdkLevel.isAtLeastS());
    }

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() throws Exception {
        SettingsTestUtil.settingsRemoveMainToggleAndMeasurementEntryTestRvcUxUtil(mDevice);
    }

    @Test
    public void measurementDialogTest() throws Exception {
        SettingsTestUtil.measurementDialogTestUtil(mDevice);
    }

    @Test
    public void measurementToggleTest() throws Exception {
        SettingsTestUtil.measurementToggleTestUtil(mDevice);
    }

    @Test
    public void measurementSubtitleTest() throws Exception {
        SettingsTestUtil.measurementSubtitleTestUtil(mDevice);
    }

    @Test
    public void measurementToggleDialogTest() throws Exception {
        SettingsTestUtil.measurementToggleDialogTestUtil(mDevice);
    }
}
