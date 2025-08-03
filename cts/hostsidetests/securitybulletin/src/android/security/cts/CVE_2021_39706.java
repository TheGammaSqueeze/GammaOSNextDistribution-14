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

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39706 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 200164168)
    @Test
    public void testPocCVE_2021_39706() {
        final int userId = 0;
        final String testApp = "CVE-2021-39706.apk";
        final String testPkg = "android.security.cts.CVE_2021_39706";
        final String testClass = testPkg + "." + "DeviceTest";
        final String testDeviceAdminReceiver = testPkg + ".PocDeviceAdminReceiver";
        boolean cmdResult = false;
        try {
            ITestDevice device = getDevice();
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);
            installPackage(testApp, "-t");
            // Set Device Admin Component
            String result = AdbUtils.runCommandLine("dpm set-device-owner --user " + userId + " '"
                    + testPkg + "/" + testDeviceAdminReceiver + "'", device);
            cmdResult = result.startsWith("Success");
            assumeTrue("Device admin not set", cmdResult);
            runDeviceTests(testPkg, testClass, "testCredentialReset");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                if (cmdResult) {
                    // Remove Device Admin Component
                    AdbUtils.runCommandLine("dpm remove-active-admin --user " + userId + " '"
                            + testPkg + "/" + testDeviceAdminReceiver + "'", getDevice());
                }
            } catch (Exception e) {
                assumeNoException(e);
            }
        }
    }
}
