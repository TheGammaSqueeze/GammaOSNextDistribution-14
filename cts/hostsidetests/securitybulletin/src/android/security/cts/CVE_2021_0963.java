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
public class CVE_2021_0963 extends NonRootSecurityTestCase {

    // b/199754277
    // Vulnerable app    : KeyChain.apk
    // Vulnerable module : com.android.keychain
    // Is Play managed   : No
    @AsbSecurityTest(cveBugId = 199754277)
    @Test
    public void testPocCVE_2021_0963() {
        int userId = 0;
        String component = null;
        ITestDevice device = null;
        try {
            // Install the application
            installPackage("CVE-2021-0963.apk", "-t");

            // Set test-app as device owner.
            final String testPkg = "android.security.cts.CVE_2021_0963";
            component = testPkg + "/" + testPkg + ".PocDeviceAdminReceiver";
            device = getDevice();
            device.setDeviceOwner(component, userId);

            // Run the device test "testOverlayButtonPresence"
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testOverlayButtonPresence");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Remove test-app as device owner.
                device.removeAdmin(component, userId);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
