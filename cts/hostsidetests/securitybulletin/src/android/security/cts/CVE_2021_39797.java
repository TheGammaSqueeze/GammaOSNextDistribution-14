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

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39797 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 209607104)
    @Test
    public void testPocCVE_2021_39797() throws Exception {
        ITestDevice device = getDevice();
        final String testPkg = "android.security.cts.CVE_2021_39797_test";
        final String targetPkg = "android.security.cts.CVE_2021_39797_target";
        uninstallPackage(device, testPkg);
        uninstallPackage(device, targetPkg);

        /* Wake up the screen */
        AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

        installPackage("CVE-2021-39797-test.apk");
        installPackage("CVE-2021-39797-target.apk");
        String previous = AdbUtils.runCommandLine("settings get global hidden_api_policy", device);

        /* Set the property hidden_api_policy to 1 in order to access the vulnerable function
           getMainActivityLaunchIntent of class LauncherApps */
        AdbUtils.runCommandLine("settings put global hidden_api_policy 1", device);
        runDeviceTests(testPkg, testPkg + ".DeviceTest", "testTaskOverride");

        /* Restore the property hidden_api_policy to its previous value */
        AdbUtils.runCommandLine("settings put global hidden_api_policy " + previous, device);
    }
}
