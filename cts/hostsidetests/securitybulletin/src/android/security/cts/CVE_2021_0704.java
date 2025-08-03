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

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0704 extends NonRootSecurityTestCase {
    private static final String TEST_PKG = "android.security.cts.CVE_2021_0704_test";

    // b/179338675
    // Vulnerable library : services.jar
    // Vulnerable module  : Not applicable
    // Is Play managed    : No
    @AsbSecurityTest(cveBugId = 179338675)
    @Test
    public void testPocCVE_2021_0704() {
        try {
            ITestDevice device = getDevice();

            // Installing the stub and test applications
            installPackage("CVE-2021-0704-test.apk");
            installPackage("CVE-2021-0704-stub.apk");

            // Running the test "testAccountsObtainable"
            runDeviceTests(TEST_PKG, TEST_PKG + ".DeviceTest", "testAccountsObtainable");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
