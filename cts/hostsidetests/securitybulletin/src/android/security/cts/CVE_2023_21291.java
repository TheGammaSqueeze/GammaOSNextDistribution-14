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

import com.android.sts.common.SystemUtil;
import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21291 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 277593270)
    @Test
    public void testPocCVE_2023_21291() {
        try {
            // Install application
            installPackage("CVE-2023-21291.apk", "-g");

            // Create a secondary user cve_2023_21291_user and enable global hidden_api_policy to
            // access hidden field in DeviceTest
            ITestDevice device = getDevice();
            try (AutoCloseable closable =
                            SystemUtil.withSetting(device, "global", "hidden_api_policy", "1");
                    AutoCloseable asSecondaryUser =
                            new UserUtils.SecondaryUser(device)
                                    .name("cve_2023_21291_user")
                                    .withUser()) {

                // Run DeviceTest
                final String testPkg = "android.security.cts.CVE_2023_21291";
                runDeviceTests(testPkg, testPkg + ".DeviceTest", "testPocCVE_2023_21291");
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
