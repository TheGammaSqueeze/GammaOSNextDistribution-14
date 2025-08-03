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
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21088 extends NonRootSecurityTestCase {

    // b/235823542
    // Vulnerable module : services.jar
    // Vulnerable module : Not applicable
    // Is Play Managed : No
    @AsbSecurityTest(cveBugId = 235823542)
    @Test
    public void testPocCVE_2023_21088() {
        try {
            final String testPkg = "android.security.cts.CVE_2023_21088";

            // Install the app
            installPackage("CVE-2023-21088.apk", "-g");

            // Run the test "testCVE_2023_21088"
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2023_21088");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
