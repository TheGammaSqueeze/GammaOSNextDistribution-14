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
public class CVE_2022_20230 extends NonRootSecurityTestCase {
    public static final int USER_ID = 0;
    static final String TEST_APP = "CVE-2022-20230.apk";
    static final String TEST_PKG = "android.security.cts.CVE_2022_20230";
    static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";
    public static final String TEST_DEVICE_ADMIN_RECEIVER = TEST_PKG + ".PocDeviceAdminReceiver";

    @AsbSecurityTest(cveBugId = 221859869)
    @Test
    public void testPocCVE_2022_20230() throws Exception {
        try {
            ITestDevice device = getDevice();

            /* Wake up the screen */
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);
            installPackage(TEST_APP, "-t");

            /* Set Device Admin Component */
            AdbUtils.runCommandLine("dpm set-device-owner --user " + USER_ID + " '" + TEST_PKG + "/"
                    + TEST_DEVICE_ADMIN_RECEIVER + "'", device);

            runDeviceTests(TEST_PKG, TEST_CLASS, "testCVE_2022_20230");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
