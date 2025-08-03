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

import static com.android.sts.common.SystemUtil.withSetting;

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class TestLocationScanningServicesUsingSlices extends NonRootSecurityTestCase {
    private final String mTestPkg = "android.security.cts.TestLocationScanningServicesUsingSlices";
    private final String mTestClass = mTestPkg + "." + "DeviceTest";
    private ITestDevice mDevice = null;

    @Before
    public void setUp() {
        try {
            mDevice = getDevice();

            // Install test app
            installPackage("TestLocationScanningServicesUsingSlices.apk", "-t");

            // Set test app as device owner
            assumeTrue(
                    mDevice.setDeviceOwner(
                            mTestPkg + "/.PocDeviceAdminReceiver", mDevice.getCurrentUser()));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @After
    public void tearDown() {
        try {
            mDevice.removeAdmin(mTestPkg + "/.PocDeviceAdminReceiver", mDevice.getCurrentUser());
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", mDevice);
        } catch (Exception ignored) {
            // ignore all exceptions
        }
    }

    // b/277333781
    // Vulnerable module : com.android.settings
    // Vulnerable apk : Settings.apk
    @AsbSecurityTest(cveBugId = 277333781)
    @Test
    public void testPocCVE_2023_21247() {
        try (AutoCloseable withBluetoothDisabled =
                withSetting(mDevice, "global", "ble_scan_always_enabled", "0")) {
            runDeviceTests(mTestPkg, mTestClass, "testPocCVE_2023_21247");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    // b/277333746
    // Vulnerable module : com.android.settings
    // Vulnerable apk : Settings.apk
    @AsbSecurityTest(cveBugId = 277333746)
    @Test
    public void testPocCVE_2023_21248() {
        try (AutoCloseable withWifiDisabled =
                withSetting(mDevice, "global", "wifi_scan_always_enabled", "0")) {
            runDeviceTests(mTestPkg, mTestClass, "testPocCVE_2023_21248");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
