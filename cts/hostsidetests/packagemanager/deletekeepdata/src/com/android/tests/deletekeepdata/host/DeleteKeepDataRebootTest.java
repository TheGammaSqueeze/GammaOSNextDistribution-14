/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tests.deletekeepdata.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)

public class DeleteKeepDataRebootTest extends BaseHostJUnit4Test {
    private static final String PERM_READ_EXTERNAL_STORAGE =
            "android.permission.READ_EXTERNAL_STORAGE";
    private static final String PERM_WRITE_EXTERNAL_STORAGE =
            "android.permission.WRITE_EXTERNAL_STORAGE";
    private static final String TEST_APK = "DeleteKeepDataTestApp.apk";
    private static final String TEST_PACKAGE = "com.android.tests.deletekeepdata.app";
    private static final String TEST_CLASS = TEST_PACKAGE + ".DeleteKeepDataDeviceTest";
    private static final String TEST_WRITE_METHOD = "testWriteData";
    private static final String TEST_READ_METHOD = "testReadData";
    private static final String TEST_READ_METHOD_FAIL = "testReadDataFail";

    @Before
    public void setUp() throws Exception {
        installPackage(TEST_APK);
        assertTrue(isPackageInstalled(TEST_PACKAGE));
    }

    @After
    public void cleanUp() throws Exception {
        uninstallPackage(getDevice(), TEST_PACKAGE);
        assertFalse(isPackageInstalled(TEST_PACKAGE));
    }

    @Test
    @AppModeFull
    public void testDataNotPreservedWithoutFlag() throws Exception {
        runDeviceTests(getDevice(), TEST_PACKAGE, TEST_CLASS, TEST_WRITE_METHOD);
        uninstallPackage(getDevice(), TEST_PACKAGE);
        assertFalse(isPackageInstalled(TEST_PACKAGE));
        // Re-install after DELETE_KEEP_DATA should find the same data
        installPackage(TEST_APK);
        runDeviceTests(getDevice(), TEST_PACKAGE, TEST_CLASS, TEST_READ_METHOD_FAIL);
    }

    @Test
    @AppModeFull
    public void testDataPreservedWithFlagWithoutReboot() throws Exception {
        runDeviceTests(getDevice(), TEST_PACKAGE, TEST_CLASS, TEST_WRITE_METHOD);
        deleteWithKeepData();
        assertFalse(isPackageInstalled(TEST_PACKAGE));
        // Re-install after DELETE_KEEP_DATA should find the same data
        installPackage(TEST_APK);
        runDeviceTests(getDevice(), TEST_PACKAGE, TEST_CLASS, TEST_READ_METHOD);
    }

    @Test
    @AppModeFull
    public void testDataPreservedWithFlagWithReboot() throws Exception {
        runDeviceTests(getDevice(), TEST_PACKAGE, TEST_CLASS, TEST_WRITE_METHOD);
        deleteWithKeepData();
        assertFalse(isPackageInstalled(TEST_PACKAGE));
        // Re-install after DELETE_KEEP_DATA should find the same data, even after reboot
        getDevice().reboot();
        installPackage(TEST_APK);
        runDeviceTests(getDevice(), TEST_PACKAGE, TEST_CLASS, TEST_READ_METHOD);
    }

    private void deleteWithKeepData() throws Exception {
        getDevice().executeShellCommand("pm uninstall -k " + TEST_PACKAGE);
    }
}
