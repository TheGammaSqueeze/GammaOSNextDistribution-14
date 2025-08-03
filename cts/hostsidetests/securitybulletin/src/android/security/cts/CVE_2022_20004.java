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

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20004 extends NonRootSecurityTestCase {
    final static String TEST_PKG = "android.security.cts.CVE_2022_20004_test";
    final static String PROVIDER_PKG = "android.security.cts.CVE_2022_20004_provider";

    @AsbSecurityTest(cveBugId = 179699767)
    @Test
    public void testPocCVE_2022_20004() throws Exception {
        ITestDevice device = getDevice();
        uninstallPackage(device, TEST_PKG);
        uninstallPackage(device, PROVIDER_PKG);

        /* Wake up the screen */
        AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

        installPackage("CVE-2022-20004-test.apk");
        installPackage("CVE-2022-20004-provider.apk");
        runDeviceTests(TEST_PKG, TEST_PKG + ".DeviceTest", "testCVE_2022_20004");
    }
}
