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

package android.security.cts;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39796 extends NonRootSecurityTestCase {
    static final int USER_ID = 0;
    static final String TEST_PKG = "android.security.cts.CVE_2021_39796";
    static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";
    static final String TEST_APP = "CVE-2021-39796.apk";
    static final String HARMFUL_APP = "CVE-2021-39796-harmful.apk";
    static final String HARMFUL_PKG = "android.security.cts.CVE_2021_39796_harmful";

    @AsbSecurityTest(cveBugId = 205595291)
    @Test
    public void testPocCVE_2021_39796() throws Exception {
        ITestDevice device = getDevice();
        uninstallPackage(device, TEST_PKG);

        /* Wake up the screen */
        AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

        installPackage(HARMFUL_APP);
        /* Set the harmful app as harmful */
        AdbUtils.runCommandLine("pm set-harmful-app-warning " + HARMFUL_PKG + " harmful 0", device);

        installPackage(TEST_APP);

        AdbUtils.runCommandLine("pm grant " + TEST_PKG + " android.permission.SYSTEM_ALERT_WINDOW",
                device);
        Assert.assertTrue(runDeviceTests(TEST_PKG, TEST_CLASS, "testOverlayButtonPresence"));

        AdbUtils.runCommandLine("input keyevent KEYCODE_BACK", device);
    }
}
