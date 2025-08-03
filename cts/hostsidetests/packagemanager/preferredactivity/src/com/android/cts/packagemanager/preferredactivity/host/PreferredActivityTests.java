/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.packagemanager.preferredactivity.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.LargeTest;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class PreferredActivityTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String TEST_PACKAGE_NAME =
            "com.android.cts.packagemanager.preferredactivity.app";
    private static final String TEST_CLASS_NAME =
            TEST_PACKAGE_NAME + ".PreferredActivityDeviceTests";
    private static final String TEST_APK_NAME = "CtsPackageManagerPreferredActivityApp.apk";
    private CompatibilityBuildHelper mBuildHelper;
    private static final int PREFERRED_ACTIVITY_WRITE_SLEEP_MS = 10000;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    @Before
    public void setUp() throws DeviceNotAvailableException, FileNotFoundException {
        getDevice().installPackage(mBuildHelper.getTestFile(TEST_APK_NAME), false, false, "");
        assertTrue(getDevice().isPackageInstalled(TEST_PACKAGE_NAME));
    }

    @After
    public void tearDown() throws DeviceNotAvailableException {
        // Uninstall to clean up the preferred activity added from the test
        getDevice().uninstallPackage(TEST_PACKAGE_NAME);
        assertFalse(getDevice().isPackageInstalled(TEST_PACKAGE_NAME));
    }

    @LargeTest
    @Test
    @AppModeFull
    public void testAddPreferredActivity() throws Exception {
        assertTrue(runDeviceTestsWithArgs(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testHasPreferredActivities",
                Collections.singletonMap("numPreferredActivities", "0")));
        assertTrue(runDeviceTests(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testAddOnePreferredActivity"));
        assertTrue(runDeviceTestsWithArgs(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testHasPreferredActivities",
                Collections.singletonMap("numPreferredActivities", "1")));
        // Wait a bit before reboot, allowing the preferred activity info to be written to disk.
        RunUtil.getDefault().sleep(PREFERRED_ACTIVITY_WRITE_SLEEP_MS);
        getDevice().reboot();
        assertTrue(runDeviceTestsWithArgs(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testHasPreferredActivities",
                Collections.singletonMap("numPreferredActivities", "1")));
    }

    @LargeTest
    @Test
    @AppModeFull
    public void testAddDuplicatedPreferredActivity() throws Exception {
        assertTrue(runDeviceTestsWithArgs(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testHasPreferredActivities",
                Collections.singletonMap("numPreferredActivities", "0")));
        assertTrue(runDeviceTests(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testAddOnePreferredActivity"));
        // Add again
        assertTrue(runDeviceTests(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testAddOnePreferredActivity"));
        assertTrue(runDeviceTestsWithArgs(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testHasPreferredActivities",
                Collections.singletonMap("numPreferredActivities", "2")));
        // Wait a bit before reboot, allowing the preferred activity info to be written to disk.
        RunUtil.getDefault().sleep(PREFERRED_ACTIVITY_WRITE_SLEEP_MS);
        getDevice().reboot();
        // Test that duplicated entries are removed after reboot
        assertTrue(runDeviceTestsWithArgs(
                TEST_PACKAGE_NAME, TEST_CLASS_NAME, "testHasPreferredActivities",
                Collections.singletonMap("numPreferredActivities", "1")));
    }

    private boolean runDeviceTestsWithArgs(String pkgName, String testClassName,
            String testMethodName, Map<String, String> testArgs) throws Exception {
        final String testRunner = "androidx.test.runner.AndroidJUnitRunner";
        final long defaultTestTimeoutMs = 60 * 1000L;
        final long defaultMaxTimeoutToOutputMs = 60 * 1000L; // 1min
        return runDeviceTests(getDevice(), testRunner, pkgName, testClassName, testMethodName,
                null, defaultTestTimeoutMs, defaultMaxTimeoutToOutputMs,
                0L, true, false, testArgs);
    }
}
