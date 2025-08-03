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
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class TestBluetoothDiscoverable extends NonRootSecurityTestCase {
    private final String mTestPkg = "android.security.cts.TestBluetoothDiscoverable";
    private final String mTestClass = mTestPkg + "." + "DeviceTest";

    @Before
    public void setUp() {
        try {
            // Install test app
            installPackage("TestBluetoothDiscoverable.apk");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @After
    public void tearDown() {
        try {
            // Back to home screen after test
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", getDevice());
        } catch (Exception e) {
            // Ignore exceptions here
        }
    }


    // b/228450811
    // Vulnerable module : com.android.settings
    // Vulnerable apk : Settings.apk
    // Is play managed : No
    @AsbSecurityTest(cveBugId = 228450811)
    @Test
    public void testPocCVE_2022_20347() {
        try {
            runDeviceTests(mTestPkg, mTestClass, "testConnectedDeviceDashboardFragment");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    // b/244423101
    // Vulnerable module : com.android.settings
    // Vulnerable apk : Settings.apk
    // Is play managed : No
    @AsbSecurityTest(cveBugId = 244423101)
    @Test
    public void testPocCVE_2023_20946() {
        try {
            runDeviceTests(mTestPkg, mTestClass, "testBluetoothDashboardFragment");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
