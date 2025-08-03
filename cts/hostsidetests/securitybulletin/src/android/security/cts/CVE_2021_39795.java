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
public class CVE_2021_39795 extends NonRootSecurityTestCase {
    private static final String TEST_PKG = "android.security.cts.CVE_2021_39795";
    private static final String DIR_PATH = "/storage/emulated/0/Android/data/CVE-2021-39795-dir";

    @AsbSecurityTest(cveBugId = 201667614)
    @Test
    public void testPocCVE_2021_39795() {
        ITestDevice device = null;
        try {
            device = getDevice();

            /* Wake up the screen */
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            installPackage("CVE-2021-39795.apk");

            /* Make a directory inside "Android/data" folder */
            AdbUtils.runCommandLine("mkdir " + DIR_PATH, device);

            /* Allow Read and Write to external storage */
            AdbUtils.runCommandLine(
                    "pm grant " + TEST_PKG + " android.permission.READ_EXTERNAL_STORAGE", device);
            AdbUtils.runCommandLine(
                    "pm grant " + TEST_PKG + " android.permission.WRITE_EXTERNAL_STORAGE", device);

            /* Allow the app to manage all files */
            AdbUtils.runCommandLine(
                    "appops set --uid " + TEST_PKG + " MANAGE_EXTERNAL_STORAGE allow", device);

            runDeviceTests(TEST_PKG, TEST_PKG + ".DeviceTest", "testFilePresence");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                AdbUtils.runCommandLine("rm -rf " + DIR_PATH, device);
            } catch (Exception e) {
                // ignore the exceptions
            }
        }
    }
}
