/**
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

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0441 extends NonRootSecurityTestCase {
    static final String TEST_PKG = "android.security.cts.CVE_2021_0441";
    static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";
    static final String TEST_APP = "CVE-2021-0441.apk";

    /**
     * b/174495520
     * Vulnerable Module : com.google.android.mediaprovider
     * Is Play managed   : Yes
     */
    @AsbSecurityTest(cveBugId = 174495520)
    @Test
    public void testPocCVE_2021_0441() {
        try {
            assumeFalse(moduleIsPlayManaged("com.google.android.mediaprovider"));
            ITestDevice device = getDevice();
            uninstallPackage(device, TEST_PKG);

            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            installPackage(TEST_APP);
            runDeviceTests(TEST_PKG, TEST_CLASS, "testCVE_2021_0441");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
