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
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.StsExtraBusinessLogicHostTestBase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20116 extends StsExtraBusinessLogicHostTestBase {

    @AsbSecurityTest(cveBugId = 212467440)
    @Test
    public void testPocCVE_2022_20223() {
        ITestDevice device = getDevice();
        final String testPkg = "android.security.cts.CVE_2022_20116";
        try {
            // Wake up the screen
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            // Install PoC application
            installPackage("CVE-2022-20116.apk");

            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testOngoingCallController");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Back to home screen after test
                AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);
            } catch (Exception e) {
                // ignore exceptions here
            }
        }
    }
}
