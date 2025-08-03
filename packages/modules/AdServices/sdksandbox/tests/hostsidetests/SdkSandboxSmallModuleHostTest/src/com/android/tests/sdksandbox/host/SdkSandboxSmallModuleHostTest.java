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

package com.android.tests.sdksandbox.host;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.hosttestutils.DeviceSupportHostUtils;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxSmallModuleHostTest extends BaseHostJUnit4Test {

    private static final String TEST_APP_PACKAGE = "com.android.tests.sdksandbox";
    private static final String MODULE_NAME = "com.android.adservices";
    private static final String SYSTEM_APEX_PATH = "/system/apex/" + MODULE_NAME + ".capex";
    private static final String PRIV_APP_DIR = "/apex/" + MODULE_NAME + "/priv-app";
    private static final String ACTIVE_APEX_DIR = "/data/apex/active/";

    private final DeviceSupportHostUtils mDeviceSupportUtils = new DeviceSupportHostUtils(this);

    /**
     * Runs the given phase of a test by calling into the device.
     *
     * <p>Hidden API checks are disabled.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     *
     * @throws AssertionException if test phase fails.
     */
    private void runPhase(String phase) throws Exception {
        DeviceTestRunOptions options =
                new DeviceTestRunOptions(TEST_APP_PACKAGE)
                        .setTestClassName(TEST_APP_PACKAGE + ".SdkSandboxSmallModuleTestApp")
                        .setTestMethodName(phase)
                        .setDisableHiddenApiCheck(true);

        assertThat(runDeviceTests(options)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(
                "Device needs to support SdkSandbox", mDeviceSupportUtils.isSdkSandboxSupported());

        // Determine if Small module related test can be run on the device
        boolean canBeUpdatedWithSmallModule = isSmallModuleUpdatePossible();
        boolean alreadyHasSmallModuleInstalled = !isAdServicesApkPresent();
        assumeTrue(
                "Device has com.android.adservices APEX or small module pre-installed",
                canBeUpdatedWithSmallModule || alreadyHasSmallModuleInstalled);

        removeUpdatedApexIfNecessary();
    }

    @After
    public void tearDown() throws Exception {
        removeUpdatedApexIfNecessary();
    }

    @Test
    public void testSmallModuleCanBeInstalled() throws Exception {
        // This test only makes sense for devices where we can install the small module apex
        assumeTrue(
                "Device has com.android.adservices APEX pre-installed",
                isSmallModuleUpdatePossible());

        runPhase("installSmallModulePendingReboot");
        getDevice().reboot();

        assertWithMessage("AdServices APK is present").that(isAdServicesApkPresent()).isFalse();
    }

    @Test
    public void testSmallModuleCanBeInstalled_andThenUpdatedToFullModule() throws Exception {
        // This test only makes sense for devices where we can install the small module apex
        assumeTrue(
                "Device has com.android.adservices APEX pre-installed",
                isSmallModuleUpdatePossible());

        runPhase("installSmallModulePendingReboot");
        getDevice().reboot();

        runPhase("installFullModulePendingReboot");
        getDevice().reboot();

        assertWithMessage("AdServices APK is present").that(isAdServicesApkPresent()).isTrue();
    }

    /** Verify services exported from AdServices APK are unavailable on small module. */
    @Test
    public void testVerifyAdServicesAreUnavailableOnSmallModule() throws Exception {
        if (isAdServicesApkPresent()) {
            // Device does not have small module pre-installed. Prepare it.
            runPhase("testVerifyAdServicesAreAvailable_preSmallModuleInstall");

            installSmallModule();
        }
        runPhase("testVerifyAdServicesAreUnavailable_postSmallModuleInstall");
    }

    @Test
    public void testLoadSdk() throws Exception {
        if (isAdServicesApkPresent()) {
            // Device does not have small module pre-installed and loadSdk should work
            // Test that loadSdk works
            runPhase("testLoadSdkWithAdServiceApk");

            installSmallModule();
        }
        // Test that loadSdk returns error when AdServices apk is not present
        runPhase("testLoadSdkWithoutAdServiceApk");
    }

    private void installSmallModule() throws Exception {
        runPhase("installSmallModulePendingReboot");
        getDevice().reboot();
    }

    private boolean isSmallModuleUpdatePossible() throws Exception {
        return getDevice().doesFileExist(SYSTEM_APEX_PATH);
    }

    private boolean isAdServicesApkPresent() throws Exception {
        // If the mounted module contains AdServices apk, it will get mounted as priv-app
        // apk at PRIV_APP_DIR
        return getDevice().isDirectory(PRIV_APP_DIR);
    }

    private void removeUpdatedApexIfNecessary() throws Exception {
        String[] children = getDevice().getChildren(ACTIVE_APEX_DIR);
        boolean activeApexFound = false;
        for (int i = 0; i < children.length; i++) {
            String child = children[i];
            if (child.startsWith(MODULE_NAME)) {
                activeApexFound = true;
                String childPath = ACTIVE_APEX_DIR + "/" + child;
                getDevice().deleteFile(childPath);
                getDevice().reboot();
                assertWithMessage("Module update removed").that(isAdServicesApkPresent()).isTrue();
            }
        }
    }
}
