/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.devicepolicy;

import org.junit.Test;

/**
 * Set of tests for use cases that apply to profile and device owner with DPC
 * targeting API level 25.
 */
public abstract class DeviceAndProfileOwnerTestApi25 extends BaseDevicePolicyTest {

    protected static final String DEVICE_ADMIN_PKG = "com.android.cts.deviceandprofileowner";
    protected static final String DEVICE_ADMIN_APK = "CtsDeviceAndProfileOwnerApp25.apk";

    private static final String TEST_APP_APK = "CtsSimpleApp.apk";
    private static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    private static final String SIMPLE_PRE_M_APP_APK = "CtsSimplePreMApp.apk";

    protected static final String ADMIN_RECEIVER_TEST_CLASS
            = ".BaseDeviceAdminTest$BasicAdminReceiver";

    protected int mUserId;

    @Override
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
        getDevice().uninstallPackage(TEST_APP_PKG);

        // Clear device lock in case test fails (testUnlockFbe in particular)
        getDevice().executeShellCommand("cmd lock_settings clear --old 12345");
        // Press the HOME key to close any alart dialog that may be shown.
        getDevice().executeShellCommand("input keyevent 3");

        super.tearDown();
    }

    @Test
    // TODO: This can't be migrated until we support specifying alternative RemoteDPC configurations
    // (in this case targeting preQ)
    public void testPermissionGrantPreMApp() throws Exception {
        installAppAsUser(SIMPLE_PRE_M_APP_APK, mUserId);
        executeDeviceTestMethod(".PermissionsTest", "testPermissionGrantState_preMApp_preQDeviceAdmin");
    }

    protected void executeDeviceTestMethod(String className, String testName) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, className, testName, mUserId);
    }
}
