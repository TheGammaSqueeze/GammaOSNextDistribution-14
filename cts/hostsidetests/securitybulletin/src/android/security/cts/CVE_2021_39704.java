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

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39704 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 209965481)
    @Test
    public void testPocCVE_2021_39704() {
        try {
            final String testPkg = "android.security.cts.CVE_2021_39704";

            ITestDevice device = getDevice();

            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            installPackage("CVE-2021-39704.apk");
            AdbUtils.runCommandLine(
                    "pm revoke " + "android.security.cts.CVE_2021_39704 "
                            + "android.permission.ACCESS_COARSE_LOCATION",
                    device);

            runDeviceTests(testPkg, testPkg + "." + "DeviceTest",
                    "testdeleteNotificationChannelGroup");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
