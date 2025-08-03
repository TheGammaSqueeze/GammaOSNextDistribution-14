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
package com.android.adservices.tests.ui.gaux;

import static com.android.adservices.tests.ui.libs.UiConstants.AD_ID_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.ENTRY_POINT_ENABLED;

import android.adservices.common.AdServicesCommonManager;
import android.content.Context;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.UiUtils;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
@ScreenRecordRule.ScreenRecord
public class OTAStringsUiAutomatorTest {
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final String MDD_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices"
                    + "-ui-ota-strings/1522/bd19b7e7d207afc30f1c07de7df5766a21a1b0eb";
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();
    private static UiDevice sDevice;

    private static AdServicesCommonManager sCommonManager;

    private String mTestName;

    @BeforeClass
    public static void initTestClass() throws Exception, UiObjectNotFoundException {
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // enable wifi
        UiUtils.connectToWifi();

        // wait for wifi to connect
        Thread.sleep(LAUNCH_TIMEOUT);

        UiUtils.disableNotificationFlowV2();

        // download test OTA strings
        sCommonManager = sContext.getSystemService(AdServicesCommonManager.class);

        UiUtils.setupOTAStrings(sContext, sDevice, sCommonManager, MDD_URL);

        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @AfterClass
    public static void cleanup() throws InterruptedException, UiObjectNotFoundException {
        UiUtils.turnOffWifi();
        ShellUtils.runShellCommand(
                "rm -rf /data/data/com.google.android.adservices.api/files/"
                        + "datadownload/shared/public");
    }

    @Before
    public void initTestCase() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        if (launcherPackage == null) {
            return;
        }
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void tearDown() throws Exception {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
    }

    @Test
    public void checkOTAStringsNotificationAndSettingsPageTest()
            throws UiObjectNotFoundException, Exception {
        UiUtils.enableBeta();
        UiUtils.setAsRowDevice();
        AdservicesTestHelper.killAdservicesProcess(sContext);
        // open notification tray so notification is expanded when shown to show full string
        sDevice.openNotification();
        sCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        UiUtils.verifyNotificationAndSettingsPage(sContext, sDevice, true);
    }
}
