/*
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
package com.android.tradefed.device;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;

import java.io.File;

/** Functional tests the {@link ITestDevice} package management APIs */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TestDevicePackageFuncTest implements IDeviceTest {
    private TestDevice mTestDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = (TestDevice) device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Before
    public void setUp() throws Exception {
        // Ensure at set-up that the device is available.
        mTestDevice.waitForDeviceAvailable();

        boolean wasInstalled =
                mTestDevice.getInstalledPackageNames().contains(WifiHelper.INSTRUMENTATION_PKG);
        if (wasInstalled) {
            // Try to make sure the WiFi helper isn't already installed
            mTestDevice.uninstallPackage(WifiHelper.INSTRUMENTATION_PKG);
        }
    }

    /**
     * Performs a basic install/uninstall flow
     *
     * <p>This is very similar to the old test except this tries to uninstall the package after
     * installing it, and this one includes more documentation.
     */
    @Test
    public void testInstallListUninstall_basic() throws Exception {
        File testApkFile = WifiHelper.extractWifiUtilApk();
        try {
            // Install the WiFi helper
            assertNull(mTestDevice.installPackage(testApkFile, false));
            assertTrue(
                    mTestDevice
                            .getInstalledPackageNames()
                            .contains(WifiHelper.INSTRUMENTATION_PKG));

            // Ensure the APK is cleaned up
            assertFalse(
                    "apk file was not cleaned up after install",
                    mTestDevice.doesFileExist(
                            String.format("/data/local/tmp/%s", testApkFile.getName())));

            // Try uninstalling the WiFi helper
            mTestDevice.uninstallPackage(WifiHelper.INSTRUMENTATION_PKG);
            assertFalse(
                    mTestDevice
                            .getInstalledPackageNames()
                            .contains(WifiHelper.INSTRUMENTATION_PKG));
        } finally {
            FileUtil.deleteFile(testApkFile);
        }
    }

    /**
     * Tests the {@link ITestDevice#isPackageInstalled(String)} method
     *
     * <p>This test ensures the method is consistent with the package listing method.
     */
    @Test
    public void testIsPackageInstalled_basic() throws Exception {
        File testApkFile = WifiHelper.extractWifiUtilApk();
        try {
            // Install the WiFi helper
            assertNull(mTestDevice.installPackage(testApkFile, false));
            assertTrue(
                    mTestDevice
                            .getInstalledPackageNames()
                            .contains(WifiHelper.INSTRUMENTATION_PKG));

            // Only try to uninstall the package if the install appears to have succeeded
            try {
                assertTrue(mTestDevice.isPackageInstalled(WifiHelper.INSTRUMENTATION_PKG));
            } finally {
                // Try cleaning up the WiFi helper
                mTestDevice.uninstallPackage(WifiHelper.INSTRUMENTATION_PKG);
            }
        } finally {
            FileUtil.deleteFile(testApkFile);
        }
    }

    /**
     * Performs a install/uninstall flow with a created user
     *
     * <p>This tests the user-specific flow of installing packages
     */
    @Ignore
    @Test
    public void testInstallListUninstall_forUser() throws Exception {
        File testApkFile = WifiHelper.extractWifiUtilApk();
        final String username = "Google";
        int userId = -1;
        try {
            userId = mTestDevice.createUser(username, false, false);

            // Install the WiFi helper
            assertNull(mTestDevice.installPackageForUser(testApkFile, false, userId));
            assertTrue(
                    mTestDevice.isPackageInstalled(
                            WifiHelper.INSTRUMENTATION_PKG, Integer.toString(userId)));

            // Ensure the APK is cleaned up
            assertFalse(
                    "apk file was not cleaned up after install",
                    mTestDevice.doesFileExist(
                            String.format("/data/local/tmp/%s", testApkFile.getName())));

            // Try uninstalling the WiFi helper
            mTestDevice.uninstallPackage(WifiHelper.INSTRUMENTATION_PKG);
            assertFalse(
                    mTestDevice
                            .getInstalledPackageNames()
                            .contains(WifiHelper.INSTRUMENTATION_PKG));
        } finally {
            if (userId != -1) {
                mTestDevice.removeUser(userId);
            }
            FileUtil.deleteFile(testApkFile);
        }
    }
}
