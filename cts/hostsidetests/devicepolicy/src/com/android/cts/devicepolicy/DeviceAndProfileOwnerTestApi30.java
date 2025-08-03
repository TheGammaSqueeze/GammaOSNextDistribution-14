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

package com.android.cts.devicepolicy;

/**
 * Set of tests for use cases that apply to profile and device owner with DPC
 * targeting API level 25.
 */
public abstract class DeviceAndProfileOwnerTestApi30 extends BaseDevicePolicyTest {

    protected static final String DEVICE_ADMIN_PKG = "com.android.cts.deviceandprofileowner";
    protected static final String DEVICE_ADMIN_APK = "CtsDeviceAndProfileOwnerApp30.apk";

    protected static final String ADMIN_RECEIVER_TEST_CLASS =
            ".BaseDeviceAdminTest$BasicAdminReceiver";

    protected int mUserId;

    @Override
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_ADMIN_PKG);

        // Clear device lock in case test fails (testUnlockFbe in particular)
        getDevice().executeShellCommand("cmd lock_settings clear --old 12345");
        // Press the HOME key to close any alart dialog that may be shown.
        getDevice().executeShellCommand("input keyevent 3");

        super.tearDown();
    }

    protected void executeDeviceTestClass(String className) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, className, mUserId);
    }

    protected void executeDeviceTestMethod(String className, String testName) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, className, testName, mUserId);
    }
}
