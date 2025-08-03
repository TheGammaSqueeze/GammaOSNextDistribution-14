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
package com.android.adservices.ui.settingsga;

import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;

import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;

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
public final class SettingsGaUiAutomatorTest extends AdServicesUiTestCase {

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, false);

    @Before
    public void setup() throws RemoteException {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
    }

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() throws Exception {
        SettingsTestUtil.settingsRemoveMainToggleAndMeasurementEntryTestUtil(mDevice);
    }

    @Test
    public void measurementDialogTest() throws Exception {
        SettingsTestUtil.measurementDialogTestUtil(mDevice);
    }

    @Test
    public void topicsToggleTest() throws Exception {
        SettingsTestUtil.topicsToggleTestUtil(mDevice);
    }

    @Test
    public void fledgeToggleTest() throws Exception {
        SettingsTestUtil.fledgeToggleTestUtil(mDevice);
    }

    @Test
    public void measurementToggleTest() throws Exception {
        SettingsTestUtil.measurementToggleTestUtil(mDevice);
    }

    @Test
    public void topicsSubtitleTest() throws Exception {
        SettingsTestUtil.topicsSubtitleTestUtil(mDevice);
    }

    @FlakyTest(bugId = 326706521)
    @Test
    public void appsSubtitleTest() throws Exception {
        SettingsTestUtil.appsSubtitleTestUtil(mDevice);
    }

    @Test
    public void measurementSubtitleTest() throws Exception {
        SettingsTestUtil.measurementSubtitleTestUtil(mDevice);
    }

    @Test
    public void topicsToggleDialogTest() throws Exception {
        SettingsTestUtil.topicsToggleDialogTestUtil(mDevice);
    }

    @Test
    public void appsToggleDialogTest() throws Exception {
        SettingsTestUtil.appsToggleDialogTestUtil(mDevice);
    }

    @Test
    public void measurementToggleDialogTest() throws Exception {
        SettingsTestUtil.measurementToggleDialogTestUtil(mDevice);
    }
}
