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

import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21286 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 277740082)
    @Test
    public void testPocCVE_2023_21286() {
        try {
            ITestDevice device = getDevice();
            final String testPkg = "android.security.cts.CVE_2023_21286";

            // Install test app in device
            installPackage("CVE-2023-21286.apk", "-g");

            // Create new user and save a screenshot in that user
            final int currentUserId = device.getCurrentUser();
            try (AutoCloseable asSecondaryUser =
                    new UserUtils.SecondaryUser(device)
                            .name("cve_2023_21286_user")
                            .doSwitch()
                            .withUser()) {
                int userId = device.getCurrentUser();
                device.executeShellCommand("input keyevent KEYCODE_SYSRQ");

                // Wait for screenshot to get saved in the created user
                final long timeout = 5_000L;
                final long waitPerIteration = 500L;
                boolean screenshotSaved = false;
                IRunUtil runUtil = RunUtil.getDefault();
                long start = System.currentTimeMillis();
                do {
                    screenshotSaved =
                            device.executeShellCommand(
                                            "content query --user "
                                                    + userId
                                                    + " --projection _id --uri"
                                                    + " content://media/external/images/media/")
                                    .contains("Row");
                    if (screenshotSaved) {
                        break;
                    }
                    runUtil.sleep(waitPerIteration);
                } while (System.currentTimeMillis() - start <= timeout);
                assumeTrue(
                        "Screenshot was not saved in the created userId = " + userId,
                        screenshotSaved);
                // Switch back to original user
                assumeTrue(device.switchUser(currentUserId));

                // Run DeviceTest
                runDeviceTests(testPkg, testPkg + ".DeviceTest", "testPocCVE_2023_21286");
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
