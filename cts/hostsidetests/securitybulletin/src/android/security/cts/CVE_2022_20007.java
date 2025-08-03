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
public class CVE_2022_20007 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 211481342)
    @Test
    public void testPocCVE_2022_20007() {
        final String testPkg = "android.security.cts.CVE_2022_20007";
        final String testClass = testPkg + "." + "DeviceTest";
        final String testApp = "CVE-2022-20007.apk";
        final String testAttackerApp = "CVE-2022-20007-Attacker.apk";
        final String testSecondApp = "CVE-2022-20007-Second.apk";
        ITestDevice device = getDevice();
        try {
            installPackage(testApp);
            installPackage(testAttackerApp);
            installPackage(testSecondApp);
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);
            runDeviceTests(testPkg, testClass, "testRaceCondition");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
