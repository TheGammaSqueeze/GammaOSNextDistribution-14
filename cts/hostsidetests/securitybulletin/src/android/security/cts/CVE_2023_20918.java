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

import com.android.sts.common.SystemUtil;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_20918 extends NonRootSecurityTestCase {

    // b/243794108
    // Vulnerable library : services.jar
    // Vulnerable module  : Not applicable
    // Is Play Managed    : No
    @AsbSecurityTest(cveBugId = 243794108)
    @Test
    public void testPocCVE_2023_20918() {
        try {
            final String testPkg = "android.security.cts.CVE_2023_20918_test";

            // Install the test and attacker apps
            installPackage("CVE-2023-20918-test.apk");
            installPackage("CVE-2023-20918-attacker.apk");

            // Allow access to hidden api "ActivityOptions#setPendingIntentLaunchFlags()"
            try (AutoCloseable closable =
                    SystemUtil.withSetting(getDevice(), "global", "hidden_api_policy", "1")) {
                // Run the test "testCVE_2023_20918"
                runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2023_20918");
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
