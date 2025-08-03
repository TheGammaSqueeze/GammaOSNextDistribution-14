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

package android.security.cts;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_20917 extends NonRootSecurityTestCase {

    // b/242605257
    // Vulnerable library : framework.jar
    // Vulnerable module  : Not applicable
    @AsbSecurityTest(cveBugId = 242605257)
    @Test
    public void testPocCVE_2023_20917() {
        ITestDevice device = null;
        int workUserId = -1;
        final String testPkg = "android.security.cts.CVE_2023_20917";
        try {
            device = getDevice();

            String output =
                    AdbUtils.runCommandLine(
                            "pm create-user --profileOf 0 --managed TestWork", device);
            workUserId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
            device.startUser(workUserId);
            // Install the test app
            installPackage("CVE-2023-20917.apk", "-t");
            AdbUtils.runCommandLine(
                    "dpm set-profile-owner --user "
                            + workUserId
                            + " "
                            + testPkg
                            + "/.PocDeviceAdminReceiver",
                    device);
            AdbUtils.runCommandLine(
                    "pm grant --user "
                            + workUserId
                            + " "
                            + testPkg
                            + " android.permission.INTERACT_ACROSS_USERS",
                    device);
            // Run the test "testCVE_2023_20917"
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2023_20917");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Remove the work profile and uninstall the test apk
                device.removeAdmin(testPkg + "/.PocDeviceAdminReceiver", workUserId);
                device.removeUser(workUserId);
            } catch (Exception ignored) {
                // ignore the exceptions
            }
        }
    }
}
