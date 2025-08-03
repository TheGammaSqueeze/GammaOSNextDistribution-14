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

import static org.junit.Assert.fail;

import com.android.tradefed.log.LogUtil.CLog;

/**
 * Base class for {@link DeviceOwnerTest} and {@link HeadlessSystemUserDeviceOwnerTest} - it
 * provides the common infra, but doesn't have any test method.
 */
abstract class BaseDeviceOwnerTest extends BaseDevicePolicyTest {

    private static final String PROPERTY_STOP_BG_USERS_ON_SWITCH = "fw.stop_bg_users_on_switch";

    protected static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    protected static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";

    protected static final String ADMIN_RECEIVER_TEST_CLASS =
            DEVICE_OWNER_PKG + ".BasicAdminReceiver";
    protected static final String DEVICE_OWNER_COMPONENT = DEVICE_OWNER_PKG + "/"
            + ADMIN_RECEIVER_TEST_CLASS;

    private boolean mDeviceOwnerSet;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        installDeviceOwnerApp(DEVICE_OWNER_APK);

        mDeviceOwnerSet = setDeviceOwner(DEVICE_OWNER_COMPONENT, mDeviceOwnerUserId,
                /*expectFailure= */ false);

        if (!mDeviceOwnerSet) {
            removeAdmin(DEVICE_OWNER_COMPONENT, mDeviceOwnerUserId);
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
            fail("Failed to set device owner on user " + mDeviceOwnerUserId);
        }

        if (isHeadlessSystemUserMode()) {
            affiliateUsers(DEVICE_OWNER_PKG, mDeviceOwnerUserId, mPrimaryUserId);
        }

        // Enable the notification listener
        executeShellCommand("cmd notification allow_listener com.android.cts."
                + "deviceowner/com.android.cts.deviceowner.NotificationListener");
    }

    @Override
    public void tearDown() throws Exception {
        if (mDeviceOwnerSet) {
            removeDeviceOwnerAdmin(DEVICE_OWNER_COMPONENT);
        }

        String status = getDevice().uninstallPackage(DEVICE_OWNER_PKG);
        if (status != null) {
            CLog.e("Could not uninstall package %s: %s", DEVICE_OWNER_PKG, status);
        }

        super.tearDown();
    }

    protected final void executeDeviceOwnerTest(String testClassName) throws Exception {
        executeDeviceOwnerTestOnSpecificUser(testClassName, mPrimaryUserId);
    }

    protected final void executeDeviceOwnerTestOnDeviceOwnerUser(String testClassName)
            throws Exception {
        executeDeviceOwnerTestOnSpecificUser(testClassName, mDeviceOwnerUserId);
    }

    private void executeDeviceOwnerTestOnSpecificUser(String testClassName, int userId)
            throws Exception {
        String testClass = DEVICE_OWNER_PKG + "." + testClassName;
        runDeviceTestsAsUser(DEVICE_OWNER_PKG, testClass, userId);
    }

    protected final void executeDeviceOwnerTestMethod(String className, String testName)
            throws Exception {
        executeDeviceOwnerPackageTestMethod(className, testName, mDeviceOwnerUserId);
    }

    protected final void executeDeviceTestMethod(String className, String testName)
            throws Exception {
        executeDeviceOwnerPackageTestMethod(className, testName, mPrimaryUserId);
    }

    protected final String getStopBgUsersOnSwitchProperty() throws Exception {
        return executeShellCommand("getprop %s", PROPERTY_STOP_BG_USERS_ON_SWITCH).trim();
    }

    protected final void setStopBgUsersOnSwitchProperty(String value) throws Exception  {
        CLog.d("Value of %s before: %s", PROPERTY_STOP_BG_USERS_ON_SWITCH,
                getStopBgUsersOnSwitchProperty());
        executeShellCommand("setprop %s '%s'", PROPERTY_STOP_BG_USERS_ON_SWITCH, value);
    }

    protected boolean isPackageInstalledForUser(String packageName, int userId) throws Exception {
        String result = executeShellCommand("pm list packages --user %d %s", userId, packageName);
        return result != null && !result.isEmpty();
    }

    private void executeDeviceOwnerPackageTestMethod(String className, String testName,
            int userId) throws Exception {
        runDeviceTestsAsUser(DEVICE_OWNER_PKG, className, testName, userId);
    }
}
