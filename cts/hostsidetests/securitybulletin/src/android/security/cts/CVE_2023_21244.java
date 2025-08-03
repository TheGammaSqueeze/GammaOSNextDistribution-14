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

import java.util.HashMap;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21244 extends NonRootSecurityTestCase {

    // b/276729064
    // Vulnerable module : framework.jar (Notification)
    // Vulnerable app    : Not applicable
    @AsbSecurityTest(cveBugId = 276729064)
    @Test
    public void testPocCVE_2023_21244() {
        try {
            ITestDevice device = getDevice();

            // Install the test app
            installPackage("CVE-2023-21244.apk", "-g");

            // Enable "hidden_api_policy" to use the hidden APIs of the class RemoteInputHistoryItem
            // and run the test with a new user
            try (AutoCloseable asSecondaryUser =
                            new UserUtils.SecondaryUser(device)
                                    .name("cve_2023_21244_user")
                                    .withUser();
                    AutoCloseable withHiddenPolicy =
                            SystemUtil.withSetting(device, "global", "hidden_api_policy", "1")) {
                // Run the test "testCVE_2023_21244"
                final String testPkg = "android.security.cts.CVE_2023_21244";
                final long timeout = 10 * 60 * 1000L;   // DEFAULT_TEST_TIMEOUT_MS

                // Pass 'isHiddenApiCheckDisabled' as 'true' in runDeviceTests() to prevent
                // 'NoSuchMethodError' in case 'hidden_api_policy' setting is ineffective. All other
                // arguments are set to their default values.
                runDeviceTests(
                        device,
                        null /* runner */,
                        testPkg,
                        testPkg + ".DeviceTest",
                        "testCVE_2023_21244",
                        null /* userId */,
                        timeout /* testTimeoutMs */,
                        timeout /* maxTimeToOutputMs */,
                        0L /* maxInstrumentationTimeoutMs */,
                        true /* checkResults */,
                        true /* isHiddenApiCheckDisabled */,
                        new HashMap<>());
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
