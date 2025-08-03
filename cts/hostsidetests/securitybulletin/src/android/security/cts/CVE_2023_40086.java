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

import static com.android.sts.common.CommandUtil.runAndCheck;

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_40086 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 296915211)
    @Test
    public void testPocCVE_2023_40086() {
        try {
            ITestDevice device = getDevice();

            // Install test app in device
            installPackage("CVE-2023-40086.apk", "-g");

            // Create new user and save a screenshot in that user
            final int currentUserId = device.getCurrentUser();
            try (AutoCloseable asSecondaryUser =
                    new UserUtils.SecondaryUser(device)
                            .name("cve_2023_40086_user")
                            .doSwitch()
                            .withUser()) {
                // Taking screenshot in the secondary user
                runAndCheck(device, "input keyevent KEYCODE_SYSRQ");

                // Wait for screenshot to get saved in the secondary user
                boolean screenshotSaved = false;
                int userId = device.getCurrentUser();
                long startTime = System.currentTimeMillis();
                do {
                    screenshotSaved =
                            runAndCheck(
                                            device,
                                            "content query --user "
                                                    + userId
                                                    + " --projection _id --uri"
                                                    + " content://media/external/images/media/")
                                    .getStdout()
                                    .contains("Row");
                    if (screenshotSaved) {
                        break;
                    }
                    RunUtil.getDefault().sleep(100L /* waitPerIteration */);
                } while ((System.currentTimeMillis() - startTime) <= 5_000L /* timeout  */);
                assume().withMessage("Screenshot was not saved in the created userId = " + userId)
                        .that(screenshotSaved)
                        .isTrue();

                // Switch back to primary user
                assume().withMessage("Unable to switch back to original user")
                        .that(device.switchUser(currentUserId))
                        .isTrue();

                // Run DeviceTest
                final String testPkg = "android.security.cts.CVE_2023_40086";
                runDeviceTests(testPkg, testPkg + ".DeviceTest", "testPocCVE_2023_40086");
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
