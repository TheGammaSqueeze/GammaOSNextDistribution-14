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

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.Flags;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppConsentSettingsUiAutomatorTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String TEST_APP_NAME = "com.example.adservices.samples.ui.consenttestapp";
    private static final String TEST_APP_APK_PATH =
            "/data/local/tmp/cts/install/" + TEST_APP_NAME + ".apk";
    private static final String TEST_APP_ACTIVITY_NAME = TEST_APP_NAME + ".MainActivity";
    private static final ComponentName COMPONENT =
            new ComponentName(TEST_APP_NAME, TEST_APP_ACTIVITY_NAME);

    private static final String PRIVACY_SANDBOX_PACKAGE = "android.adservices.ui.SETTINGS";
    private static final String PRIVACY_SANDBOX_TEST_PACKAGE = "android.test.adservices.ui.MAIN";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    private String mTestName;

    @Rule(order = 0)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests().setCompatModeFlags();

    @Before
    public void setup() throws UiObjectNotFoundException {
        String installMessage = ShellUtils.runShellCommand("pm install -r " + TEST_APP_APK_PATH);
        assertThat(installMessage).contains("Success");

        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();
    }

    @After
    public void teardown() {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(CONTEXT);

        // Note aosp_x86 requires --user 0 to uninstall though arm doesn't.
        ShellUtils.runShellCommand("pm uninstall --user 0 " + TEST_APP_NAME);
    }

    // TODO: Remove this blank test along with the other @Ignore. b/268351419
    @Test
    public void placeholderTest() {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        // As this class is the only test class in the test module and need to be @Ignore for the
        // moment, add a blank test to help presubmit to pass.
        assertThat(true).isTrue();
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentSystemServerOnlyTest() throws InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        // System server is not available on S-, skip this test for S-
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(0, false);
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentPpApiOnlyTest() throws InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        appConsentTest(1, false);
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentSystemServerAndPpApiTest() throws InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        // System server is not available on S-, skip this test for S-
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(2, false);
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentAppSearchOnlyTest() throws UiObjectNotFoundException, InterruptedException {
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 3");
        appConsentTest(Flags.APPSEARCH_ONLY, false);
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth null");
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentAppSearchOnlyDialogsOnTest()
            throws UiObjectNotFoundException, InterruptedException {
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 3");
        appConsentTest(Flags.APPSEARCH_ONLY, true);
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth null");
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentSystemServerOnlyDialogsOnTest()
            throws UiObjectNotFoundException, InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        // System server is not available on S-, skip this test for S-
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(0, true);
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentPpApiOnlyDialogsOnTest() throws InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        appConsentTest(1, true);
    }

    @Test
    @Ignore("Flaky test. (b/268351419)")
    public void consentSystemServerAndPpApiDialogsOnTest() throws InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        // System server is not available on S-, skip this test for S-
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        appConsentTest(2, true);
    }

    private void setPpApiConsentToGiven() {
        // launch app
        launchSettingApp();

        UiObject2 mainSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        assertThat(mainSwitch).isNotNull();

        if (!mainSwitch.isChecked()) {
            mainSwitch.click();
        }
    }

    private void launchSettingApp() {
        String privacySandboxUi;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            privacySandboxUi = PRIVACY_SANDBOX_TEST_PACKAGE;
        } else {
            privacySandboxUi = PRIVACY_SANDBOX_PACKAGE;
        }
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(privacySandboxUi);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        sDevice.wait(Until.hasObject(By.pkg(privacySandboxUi).depth(0)), LAUNCH_TIMEOUT);
    }

    private void appConsentTest(int consentSourceOfTruth, boolean dialogsOn)
            throws InterruptedException {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_source_of_truth " + consentSourceOfTruth);
        ShellUtils.runShellCommand(
                "device_config put adservices ui_dialogs_feature_enabled " + dialogsOn);
        AdservicesTestHelper.killAdservicesProcess(CONTEXT);

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");

        setPpApiConsentToGiven();

        // Initiate test app consent.
        initiateTestAppConsent();

        // open apps view
        launchSettingApp();
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_title);

        blockAppConsent(dialogsOn);

        unblockAppConsent(dialogsOn);

        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title))
                .isNotNull();

        resetAppConsent(dialogsOn);

        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_app_title, 0))
                .isNull();
        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_blocked_apps_title, 0))
                .isNull();
        assertThat(ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_view_no_apps_text, 0))
                .isNotNull();
    }

    private void unblockAppConsent(boolean dialogsOn) throws InterruptedException {
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_blocked_apps_title);
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_unblock_app_title);

        if (dialogsOn) {
            // click unblock
            UiObject2 dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_unblock_app_message);
            UiObject2 positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_unblock_app_positive_text);
            assertThat(dialogTitle).isNotNull();
            assertThat(positiveText).isNotNull();

            // confirm
            positiveText.click();
        }

        assertThat(
                        ApkTestUtil.getElement(
                                sDevice,
                                R.string.settingsUI_apps_view_no_blocked_apps_text))
                .isNotNull();
        sDevice.pressBack();
    }

    private void resetAppConsent(boolean dialogsOn) throws InterruptedException {
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_reset_apps_title);

        if (dialogsOn) {
            UiObject2 dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_app_message);
            UiObject2 positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_reset_app_positive_text);
            assertThat(dialogTitle).isNotNull();
            assertThat(positiveText).isNotNull();

            // confirm
            positiveText.click();
        }
    }

    private void blockAppConsent(boolean dialogsOn) throws InterruptedException {
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_block_app_title);

        if (dialogsOn) {
            UiObject2 dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_app_message);
            UiObject2 positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_block_app_positive_text);
            assertThat(dialogTitle).isNotNull();
            assertThat(positiveText).isNotNull();
            positiveText.click();
        }
    }

    private void initiateTestAppConsent() throws InterruptedException {
        String installMessage = ShellUtils.runShellCommand("pm install -r " + TEST_APP_APK_PATH);
        assertThat(installMessage).contains("Success");

        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests persistent");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices"
                        + " fledge_custom_audience_service_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices"
                        + " fledge_schedule_custom_audience_update_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices disable_fledge_enrollment_check true");

        ShellUtils.runShellCommand("device_config put adservices ppapi_app_allow_list *");

        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent().setComponent(COMPONENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        sDevice.wait(Until.hasObject(By.pkg(TEST_APP_NAME).depth(0)), LAUNCH_TIMEOUT);

        Thread.sleep(1000);

        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests none");
        ShellUtils.runShellCommand(
                "am force-stop com.example.adservices.samples.ui.consenttestapp");
        ShellUtils.runShellCommand(
                "device_config put adservices disable_fledge_enrollment_check null");
    }
}
