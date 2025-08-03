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
public class CVE_2021_0595 extends NonRootSecurityTestCase {

    // b/177457096
    // Vulnerable library : services.jar
    // Vulnerable module : Not applicable
    // Is Play Managed : No
    @AsbSecurityTest(cveBugId = 177457096)
    @Test
    public void testPocCVE_2021_0595() {
        final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min
        ITestDevice device = null;
        int workUserId = -1;
        final String testPkg = "android.security.cts.CVE_2021_0595_test";
        final String pocDeviceAdminPkg = testPkg + "/.PocDeviceAdminReceiver";
        try {
            device = getDevice();

            // Install the helper app
            installPackage("CVE-2021-0595-helper.apk");
            String output =
                    AdbUtils.runCommandLine(
                            "pm create-user --profileOf 0 --managed TestWork", device);
            workUserId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
            device.startUser(workUserId);

            // Install the test app
            installPackageAsUser("CVE-2021-0595-test.apk", true, workUserId, "-t");
            AdbUtils.runCommandLine(
                    "dpm set-profile-owner --user " + workUserId + " " + pocDeviceAdminPkg, device);
            AdbUtils.runCommandLine(
                    "locksettings set-pin --user " + workUserId + " 1234" /* testPassword */,
                    device);

            // Run the test "testCVE_2021_0595"
            runDeviceTests(
                    device,
                    testPkg,
                    testPkg + ".DeviceTest",
                    "testCVE_2021_0595",
                    workUserId,
                    DEFAULT_INSTRUMENTATION_TIMEOUT_MS);
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Remove the work profile
                device.removeAdmin(pocDeviceAdminPkg, workUserId);
                device.removeUser(workUserId);
                AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);
            } catch (Exception ignored) {
                // ignore the exceptions
            }
        }
    }
}
