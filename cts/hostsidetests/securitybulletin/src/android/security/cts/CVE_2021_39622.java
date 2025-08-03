/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39622 extends NonRootSecurityTestCase {
    private static final String GBOARD_PKG_NAME = "com.google.android.inputmethod.latin";
    private static final String RECORD_AUDIO_PERMISSION = "android.permission.RECORD_AUDIO";

    @AsbSecurityTest(cveBugId = 192663648)
    @Test
    public void testPocCVE_2021_39622() {
        ITestDevice iTestDevice = null;
        boolean wasPermissionGranted = false;
        try {
            final String testPkg = "android.security.cts.CVE_2021_39622";
            final String testClass = testPkg + "." + "DeviceTest";
            final String testApp = "CVE-2021-39622.apk";
            final String mIdGboard = "/com.android.inputmethod.latin.LatinIME";
            iTestDevice = getDevice();
            assumeTrue(
                    AdbUtils.runCommandLine("pm list packages", iTestDevice)
                            .contains(GBOARD_PKG_NAME));

            if (AdbUtils.runCommandLine(
                            "dumpsys package "
                                    + GBOARD_PKG_NAME
                                    + " | grep "
                                    + RECORD_AUDIO_PERMISSION,
                            iTestDevice)
                    .contains("granted=true")) {
                // android.permission.RECORD_AUDIO has flag USER_SENSITIVE_WHEN_GRANTED
                AdbUtils.runCommandLine(
                        "pm revoke " + GBOARD_PKG_NAME + " " + RECORD_AUDIO_PERMISSION,
                        iTestDevice);
                wasPermissionGranted = true;
            }

            if (!AdbUtils.runCommandLine("ime list -s", iTestDevice)
                    .contains(GBOARD_PKG_NAME + mIdGboard)) {
                AdbUtils.runCommandLine("ime enable " + GBOARD_PKG_NAME + mIdGboard, iTestDevice);
            }

            AdbUtils.runCommandLine(
                    "ime set " + GBOARD_PKG_NAME + RECORD_AUDIO_PERMISSION, iTestDevice);
            installPackage(testApp);
            runDeviceTests(testPkg, testClass, "testPocCVE_2021_39622");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // revert RECORD_AUDIO permission changes for Gboard
                if (wasPermissionGranted) {
                    AdbUtils.runCommandLine(
                            "pm grant " + GBOARD_PKG_NAME + " " + RECORD_AUDIO_PERMISSION,
                            iTestDevice);
                }

                // Revert input method changes
                AdbUtils.runCommandLine("ime reset", iTestDevice);
            } catch (Exception e) {
                // Ignore exceptions as the test has finished
            }
        }
    }
}
