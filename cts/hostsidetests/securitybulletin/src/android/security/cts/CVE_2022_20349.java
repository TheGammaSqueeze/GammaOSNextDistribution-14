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
public class CVE_2022_20349 extends NonRootSecurityTestCase {
    static final String TEST_PKG = "android.security.cts.CVE_2022_20349";
    public static final String TEST_DEVICE_ADMIN_RECEIVER = ".PocDeviceAdminReceiver";

    @AsbSecurityTest(cveBugId = 228315522)
    @Test
    public void testPocCVE_2022_20349() throws Exception {
        try {
            ITestDevice device = getDevice();

            /* Wake up the screen */
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            /* Install the test application */
            installPackage("CVE-2022-20349.apk");

            /* Set Device Admin Component */
            AdbUtils.runCommandLine(
                    "dpm set-device-owner '" + TEST_PKG + "/" + TEST_DEVICE_ADMIN_RECEIVER + "'",
                    device);

            /* Run the test "testBluetoothScanningDisallowed" */
            runDeviceTests(TEST_PKG, TEST_PKG + ".DeviceTest", "testBluetoothScanningDisallowed");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
