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
public class CVE_2023_21128 extends NonRootSecurityTestCase {

    // b/272042183
    // Vulnerable module : Not applicable
    // Vulnerable library : services.jar, framework.jar
    @AsbSecurityTest(cveBugId = 272042183)
    @Test
    public void testPocCVE_2023_21128() {
        ITestDevice device = null;
        final String testPkg = "android.security.cts.CVE_2023_21128_test";
        final String pocDeviceAdminPkg = testPkg + "/.PocDeviceAdminReceiver";
        int currentUserId = -1;
        try {
            device = getDevice();
            currentUserId = device.getCurrentUser();

            // Install the helper app
            installPackage("CVE-2023-21128-helper.apk");

            // Install the test app
            installPackage("CVE-2023-21128-test.apk", "-t");

            // Set the test app as device owner for the current user
            device.setDeviceOwner(pocDeviceAdminPkg, currentUserId);

            // Run the test "testCVE_2023_21128"
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2023_21128");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Remove test app as active admin
                device.removeAdmin(pocDeviceAdminPkg, currentUserId);
            } catch (Exception ignored) {
                // Ignore the exceptions
            }
        }
    }
}
