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
public class CVE_2023_20913 extends NonRootSecurityTestCase {

    // b/246933785
    // Vulnerable app    : Telephony.apk
    // Vulnerable module : com.android.phone
    // Is Play managed   : No
    @AsbSecurityTest(cveBugId = 246933785)
    @Test
    public void testPocCVE_2023_20913() {
        try {
            final String testPkg = "android.security.cts.CVE_2023_20913";
            ITestDevice device = getDevice();
            installPackage("CVE-2023-20913.apk");

            AdbUtils.runCommandLine(
                    "pm grant " + testPkg + " android.permission.SYSTEM_ALERT_WINDOW", device);
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testOverlayButtonPresence");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
