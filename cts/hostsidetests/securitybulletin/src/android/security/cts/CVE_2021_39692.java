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

import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39692 extends NonRootSecurityTestCase {

    @AppModeFull
    @AsbSecurityTest(cveBugId = 209611539)
    @Test
    public void testPocCVE_2021_39692() throws Exception {
        ITestDevice device = getDevice();
        final String testPkg = "android.security.cts.CVE_2021_39692";
        uninstallPackage(device, testPkg);

        /* Wake up the screen */
        AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

        installPackage("CVE-2021-39692.apk");
        AdbUtils.runCommandLine("pm grant " + testPkg + " android.permission.SYSTEM_ALERT_WINDOW",
                device);
        assertTrue(runDeviceTests(testPkg, testPkg + ".DeviceTest", "testOverlayButtonPresence"));
    }
}
