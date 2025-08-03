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
public class CVE_2021_0602 extends NonRootSecurityTestCase {

    // b/177573895
    // Vulnerable package : com.android.settings
    // Vulnerable app     : settings.apk
    @AsbSecurityTest(cveBugId = 177573895)
    @Test
    public void testPocCVE_2021_0602() {
        ITestDevice device = null;
        int guestUserId = -1, initialUserId = -1;
        try {
            final String testPkg = "android.security.cts.CVE_2021_0602";
            device = getDevice();
            initialUserId = device.getCurrentUser();

            // Install the test app
            installPackage("CVE-2021-0602.apk");

            guestUserId = device.createUser("Guest", true /* guest */, false /* ephemeral */);

            // Run the test "testCVE_2021_0602"
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2021_0602");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                if (device.getCurrentUser() != initialUserId) {
                    device.switchUser(initialUserId);
                }
                device.removeUser(guestUserId);
            } catch (Exception e) {
                // ignore exceptions
            }
        }
    }
}
