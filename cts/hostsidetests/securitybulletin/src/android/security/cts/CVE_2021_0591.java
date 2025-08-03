/**
 * Copyright (C) 2021 The Android Open Source Project
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

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.RequiresDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0591 extends NonRootSecurityTestCase {

    private static final String TEST_PKG = "android.security.cts.CVE_2021_0591";
    private static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";
    private static final String TEST_APP = "CVE-2021-0591.apk";

    @Before
    public void setUp() throws Exception {
        uninstallPackage(getDevice(), TEST_PKG);
    }

    /**
     * b/179386960
     */
    @AppModeFull
    @AsbSecurityTest(cveBugId = 179386960)
    @Test
    public void testPocCVE_2021_0591() throws Exception {
        ITestDevice device = getDevice();

        assumeTrue("Bluetooth is not available on device",
                device.hasFeature("android.hardware.bluetooth"));

        /* Clear the logs in the beginning */
        AdbUtils.runCommandLine("logcat -c", device);
        installPackage();
        try {
            runDeviceTests(TEST_PKG, TEST_CLASS, "testClick");
        } catch (AssertionError error) {
            /* runDeviceTests crashed, do not continue */
            error.printStackTrace();
            return;
        }
        String screenshotServiceErrorReceiver =
                "com.android.systemui.screenshot.ScreenshotServiceErrorReceiver";
        String logcat =
                AdbUtils.runCommandLine("logcat -d BluetoothPermissionActivity *:S", device);
        Pattern pattern = Pattern.compile(screenshotServiceErrorReceiver, Pattern.MULTILINE);
        String message = "Device is vulnerable to b/179386960 "
                + "hence it is possible to sent a broadcast intent to "
                + screenshotServiceErrorReceiver;
        assertThat(message, pattern.matcher(logcat).find(), is(false));
    }

    private void installPackage() throws Exception {
        installPackage(TEST_APP, new String[0]);
    }
}
