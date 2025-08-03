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

package com.android.cts.graphics.displaymode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Tests for boot display mode APIs.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class BootDisplayModeHostTest extends BaseHostJUnit4Test {
    private static final String RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String TEST_CLASS = "BootDisplayModeTest";

    /**
     * The name of the APK.
     */
    private static final String TEST_APK = "CtsBootDisplayModeApp.apk";

    /**
     * The package name of the APK.
     */
    private static final String TEST_PKG = "com.android.cts.graphics.displaymode";

    @Before
    public void setUp() throws Exception {
        installPackage(TEST_APK);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_PKG);
    }

    @Test
    public void testGetBootDisplayMode() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");

        boolean deviceTestPassed = runDeviceTest(device,
                TEST_PKG + "." + TEST_CLASS, "testGetBootDisplayMode");
        assumeTrue("Skip the test if deviceSideTest fails.", deviceTestPassed);

        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d",
                TEST_CLASS + ":I", "*:S");

        String userPreferredMode = "";
        // Search for string.
        try (Scanner in = new Scanner(logs)) {
            while (in.hasNextLine()) {
                String line = in.nextLine();

                if (line.contains("user-preferred-mode")) {
                    userPreferredMode = line.split(":")[2].trim();
                }
            }
        }

        device.reboot();
        String bootDisplayMode =
                device.executeShellCommand("cmd display get-active-display-mode-at-start "
                        + "0")
                        .split(":")[1].trim();
        assertEquals(userPreferredMode, bootDisplayMode);
    }

    @Test
    public void testClearBootDisplayMode() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");

        boolean deviceTestPassed = runDeviceTest(device,
                TEST_PKG + "." + TEST_CLASS, "testClearBootDisplayMode");
        assumeTrue("Skip the test if deviceSideTest fails.", deviceTestPassed);

        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d",
                TEST_CLASS + ":I", "*:S");

        String systemPreferredMode = "";
        // Search for string.
        try (Scanner in = new Scanner(logs)) {
            while (in.hasNextLine()) {
                String line = in.nextLine();

                if (line.contains("system-preferred-mode")) {
                    systemPreferredMode = line.split(":")[2].trim();
                }
            }
        }

        device.reboot();
        String bootDisplayMode =
                device.executeShellCommand("cmd display get-active-display-mode-at-start "
                        + "0")
                        .split(":")[1].trim();
        assertEquals(systemPreferredMode, bootDisplayMode);
    }

    private boolean runDeviceTest(ITestDevice device, String className, String methodName)
            throws DeviceNotAvailableException {
        RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PKG,
                RUNNER,
                device.getIDevice());
        // set a max deadline limit to avoid hanging forever
        runner.setMaxTimeToOutputResponse(5, TimeUnit.MINUTES);
        runner.setClassName(className);
        runner.setMethodName(className, methodName);
        CollectingTestListener listener = new CollectingTestListener();

        device.runInstrumentationTests(runner, listener);
        return listener.getCurrentRunResults().getNumTestsInState(
                TestResult.TestStatus.PASSED) != 0;
    }
}
