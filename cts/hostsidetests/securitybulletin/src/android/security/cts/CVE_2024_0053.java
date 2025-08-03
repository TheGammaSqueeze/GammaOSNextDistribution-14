/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.sts.common.CommandUtil.runAndCheck;

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils.SecondaryUser;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_0053 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 281525042)
    @Test
    public void testPocCVE_2024_0053() {
        ITestDevice device = null;
        try {
            device = getDevice();

            // Get userId of primary user
            int primaryUserId = device.getCurrentUser();
            try (AutoCloseable asSecondaryUser =
                    new SecondaryUser(device).name("cve_2024_0053_user").doSwitch().withUser()) {

                // Capture Screenshot
                runAndCheck(device, "input keyevent KEYCODE_SYSRQ");

                // Switch back to primary user
                assume().withMessage("Failed to switch user back to primary user")
                        .that(device.switchUser(primaryUserId))
                        .isTrue();

                // Install application in primary user
                installPackage("CVE-2024-0053.apk");

                // Run DeviceTest
                final String testPkg = "android.security.cts.CVE_2024_0053";
                runDeviceTests(
                        new DeviceTestRunOptions(testPkg)
                                .setDevice(getDevice())
                                .setTestClassName(testPkg + ".DeviceTest")
                                .setTestMethodName("testPocCVE_2024_0053")
                                .setDisableHiddenApiCheck(true));
            }
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // To exit the test gracefully
                runAndCheck(device, "input keyevent KEYCODE_HOME");
            } catch (Exception e) {
                // Ignore all exceptions
            }
        }
    }
}
