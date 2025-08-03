/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.RequiresProfileOwnerSupport;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Test;

/**
 * Host side tests for profile owner.  Run the CtsProfileOwnerApp device side test.
 */
@RequiresProfileOwnerSupport
public final class ProfileOwnerTest extends BaseDevicePolicyTest {
    private static final String PROFILE_OWNER_PKG = "com.android.cts.profileowner";
    private static final String PROFILE_OWNER_APK = "CtsProfileOwnerApp.apk";

    private static final String ADMIN_RECEIVER_TEST_CLASS =
            PROFILE_OWNER_PKG + ".BaseProfileOwnerTest$BasicAdminReceiver";

    private int mUserId = 0;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mUserId = isHeadlessSystemUserMode() ? getCurrentUser() : getMainUser();


        installAppAsUser(PROFILE_OWNER_APK, mUserId);
        if (!setProfileOwner(
                PROFILE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId,
                /* expectFailure */ false)) {
            removeAdmin(PROFILE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
            getDevice().uninstallPackage(PROFILE_OWNER_PKG);
            fail("Failed to set profile owner");
        }
    }

    @Test
    public void testManagement() throws Exception {
        executeProfileOwnerTest("ManagementTest");
    }

    @Test
    public void testDevicePolicySafetyCheckerIntegration_allOperations() throws Exception {
        executeDevicePolicySafetyCheckerIntegrationTest("testAllOperations");
    }

    @Test
    public void testDevicePolicySafetyCheckerIntegration_unsafeStateException() throws Exception {
        executeDevicePolicySafetyCheckerIntegrationTest("testUnsafeStateException");
    }

    @Test
    public void testDevicePolicySafetyCheckerIntegration_onOperationSafetyStateChanged()
            throws Exception {
        executeDevicePolicySafetyCheckerIntegrationTest("testOnOperationSafetyStateChanged");
    }

    @Override
    public void tearDown() throws Exception {
        assertTrue("Failed to remove profile owner.",
                removeAdmin(PROFILE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId));
        getDevice().uninstallPackage(PROFILE_OWNER_PKG);

        super.tearDown();
    }

    private void executeProfileOwnerTest(String testClassName) throws Exception {
        String testClass = PROFILE_OWNER_PKG + "." + testClassName;
        CLog.d("executeProfileOwnerTest(): running %s on user %d", testClassName, mUserId);
        runDeviceTestsAsUser(PROFILE_OWNER_PKG, testClass, mUserId);
    }

    protected void executeProfileOwnerTestMethod(String className, String testName)
            throws Exception {
        CLog.d("executeProfileOwnerTestMethod(): running %s.%s on user %d", className, testName,
                mUserId);
        runDeviceTestsAsUser(PROFILE_OWNER_PKG, className, testName, mUserId);
    }

    private void executeDevicePolicySafetyCheckerIntegrationTest(String testName) throws Exception {
        executeProfileOwnerTestMethod(
                PROFILE_OWNER_PKG + "." + "DevicePolicySafetyCheckerIntegrationTest", testName);
    }
}
