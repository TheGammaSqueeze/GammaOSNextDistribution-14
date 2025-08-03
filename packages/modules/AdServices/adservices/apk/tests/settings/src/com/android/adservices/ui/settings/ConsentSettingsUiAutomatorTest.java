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

package com.android.adservices.ui.settings;

import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.ui.util.AdServicesUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConsentSettingsUiAutomatorTest extends AdServicesUiTestCase {

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, false);

    @Test
    @Ignore("b/293366771")
    public void consentSystemServerOnlyTest() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());

        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 0");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    @Ignore("b/293366771")
    public void consentPpApiOnlyTest() {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    @Ignore("b/293366771")
    public void consentSystemServerAndPpApiTest() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 2");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    @Ignore("b/293366771")
    public void consentSystemServerOnlyDialogsOnTest() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 0");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    @Test
    @Ignore("b/293366771")
    public void consentPpApiOnlyDialogsOnTest() {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");

        consentTest(true);
    }

    @Test
    @Ignore("b/293366771")
    public void consentSystemServerAndPpApiDialogsOnTest() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 2");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    @Test
    @Ignore("b/293366771")
    public void consentAppSearchOnlyTest() {
        // APPSEARCH_ONLY is not a valid choice of consent_source_of_truth on T+.
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 3");
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        consentTest(false);
    }

    @Test
    @Ignore("b/293366771")
    public void consentAppSearchOnlyDialogsOnTest() throws UiObjectNotFoundException {
        // APPSEARCH_ONLY is not a valid choice of consent_source_of_truth on T+.
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 3");
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        consentTest(true);
    }

    private void consentTest(boolean dialogsOn) {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_activity_debug_mode true");
        ShellUtils.runShellCommand("device_config put adservices debug_ux BETA_UX");

        ApkTestUtil.launchSettingViewGivenUx(mDevice, LAUNCH_TIMEOUT, "BETA_UX");

        UiObject2 consentSwitch = ApkTestUtil.getConsentSwitch(mDevice);
        setConsentToFalse(dialogsOn);

        // click switch
        performSwitchClick(dialogsOn, consentSwitch);
        assertThat(consentSwitch.isChecked()).isTrue();

        // click switch
        performSwitchClick(dialogsOn, consentSwitch);
        assertThat(consentSwitch.isChecked()).isFalse();
    }

    private void setConsentToFalse(boolean dialogsOn) {
        UiObject2 consentSwitch = ApkTestUtil.getConsentSwitch(mDevice);
        if (consentSwitch.isChecked()) {
            performSwitchClick(dialogsOn, consentSwitch);
        }
    }

    private void performSwitchClick(boolean dialogsOn, UiObject2 mainSwitch) {
        if (dialogsOn && mainSwitch.isChecked()) {
            mainSwitch.click();
            UiObject2 dialogTitle =
                    ApkTestUtil.getElement(mDevice, R.string.settingsUI_dialog_opt_out_title);
            UiObject2 positiveText =
                    ApkTestUtil.getElement(
                            mDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogTitle).isNotNull();
            assertThat(positiveText).isNotNull();
            positiveText.click();
        } else {
            mainSwitch.click();
        }
    }
}
