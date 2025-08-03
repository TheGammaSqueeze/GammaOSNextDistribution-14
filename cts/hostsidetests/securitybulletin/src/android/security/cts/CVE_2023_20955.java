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
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_20955 extends NonRootSecurityTestCase {

    // b/258653813
    // Vulnerable app     : Settings.apk
    // Vulnerable package : com.android.settings
    // Is Play Managed    : No
    @AsbSecurityTest(cveBugId = 240663194)
    @Test
    public void testPocCVE_2023_20955() {
        int userId = -1;
        ITestDevice device = null;
        final String testPkg = "android.security.cts.CVE_2023_20955_test";
        final String componentName = testPkg + "/.PocDeviceAdminReceiver";
        try {
            device = getDevice();

            // Install the test app
            installPackage("CVE-2023-20955-test.apk", "-t");

            // Set test app as device owner
            assumeTrue("Failed to set test app as device owner",
                    device.setDeviceOwner(componentName, 0));

            // Create a new user
            userId = device.createUser("CTSUser");
            assumeTrue("Failed to create a user. ITestDevice.createUser() returned -1",
                    userId != -1);

            // Install test helper app for all users
            installPackage("CVE-2023-20955-test-helper.apk", "--user all");

            // Run device test to check if App Info window allows uninstall for all users if
            // DevicePolicyManager has restricted it.
            runDeviceTests(testPkg, testPkg + ".DeviceTest",
                    "testAppInfoUninstallForAllUsersDisabled");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Remove user
                device.removeUser(userId);

                // Remove test app as device owner
                device.removeAdmin(componentName, 0);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
