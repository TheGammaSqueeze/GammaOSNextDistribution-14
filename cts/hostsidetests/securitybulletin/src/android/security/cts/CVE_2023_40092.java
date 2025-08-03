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

import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_40092 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 288110451)
    @Test
    public void testPocCVE_2023_40092() {
        try {
            installPackage("CVE-2023-40092.apk");
            ITestDevice device = getDevice();

            // Get userId of primary user
            int primaryUserId = device.getCurrentUser();
            try (AutoCloseable asSecondaryUser =
                    new UserUtils.SecondaryUser(device)
                            .name("cve_2023_40092_user")
                            .doSwitch()
                            .withUser()) {
                installPackage("CVE-2023-40092.apk", "--user " + device.getCurrentUser());

                // Switch to primary user
                device.switchUser(primaryUserId);

                // Run DeviceTest
                final String testPkg = "android.security.cts.CVE_2023_40092";
                runDeviceTests(
                        new DeviceTestRunOptions(testPkg)
                                .setDevice(getDevice())
                                .setTestClassName(testPkg + ".DeviceTest")
                                .setTestMethodName("testPocCVE_2023_40092")
                                .setDisableHiddenApiCheck(true));
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
