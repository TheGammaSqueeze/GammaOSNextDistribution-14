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

package com.android.cts.webkit;

import android.platform.test.annotations.SystemUserOnly;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;

// TODO(torne): once secondary user testing of CtsWebkitTestCases is fully automated
//              we can consider deleting this test.
@RunWith(DeviceJUnit4ClassRunner.class)
@SystemUserOnly(reason = "This test explicitly manages multiple users itself")
public class WebViewHostSideMultipleProfileTest extends BaseHostJUnit4Test {
    private static final String DEVICE_PACKAGE = "com.android.cts.webkit";
    private static final String SIMPLE_DEVICE_TEST_CLASS = "WebViewDeviceSideMultipleProfileTest";
    private static final String DEVICE_TEST_CLASS = DEVICE_PACKAGE + "." + SIMPLE_DEVICE_TEST_CLASS;
    private static final String DEVICE_TEST_APK = "CtsWebViewStartupApp.apk";

    static final String ADMIN_RECEIVER_TEST_CLASS =
            DEVICE_TEST_CLASS + "$BasicAdminReceiver";
    static final String PROFILE_OWNER_CLASS = DEVICE_PACKAGE + "/" + ADMIN_RECEIVER_TEST_CLASS;

    private static final String SECONDARY_USER_NAME = "WebViewTestProfile";

    private ITestDevice mDevice;
    private int mInitialUserId;
    private int mUserId = -1;


    @Before
    public void setUp() throws InterruptedException, DeviceNotAvailableException {
        mDevice = getDevice();
        mInitialUserId = mDevice.getCurrentUser();
    }

    @After
    public void tearDown() throws DeviceNotAvailableException {
        if (mUserId > 0) {
            Assert.assertTrue(mDevice.switchUser(mInitialUserId));
            stopAndRemoveUser(mUserId);
        }
    }

    @Test
    public void testSecondaryUser() throws DeviceNotAvailableException, TargetSetupError {
        Assume.assumeTrue(isMultiUsersSupported());

        mUserId = createUser(SECONDARY_USER_NAME + System.currentTimeMillis(), false);
        startUser(mUserId);
        switchUser(mUserId);
        installTestApkForUser(mUserId);

        assertWebViewDeviceTestAsUserPasses(this, "testCreateWebViewAndNavigate", mUserId);
    }

    @Test
    public void testManagedProfile() throws DeviceNotAvailableException, TargetSetupError {
        Assume.assumeTrue(isMultiUsersSupported() && isManagedProfileSupported());

        mUserId = createUser(SECONDARY_USER_NAME + System.currentTimeMillis(), true);
        startUser(mUserId);
        installTestApkForUser(mUserId);
        setProfileOwnwer(mUserId, PROFILE_OWNER_CLASS);

        assertWebViewDeviceTestAsUserPasses(this, "testCreateWebViewAndNavigate", mUserId);
    }

    private void installTestApkForUser(int userId) throws DeviceNotAvailableException {
        try {
            // TODO: it would be nice to use BaseHostJUnit4Test#installPackageAsUser instead.
            // However, this method removes installed package for all users after test is finished.
            // Therefore it breaks other tests that rely on targetprep which installs test APK once
            // before tests are executed.
            // See b/178367954.
            File file = getTestInformation().getDependencyFile(DEVICE_TEST_APK, true);

            // --dont-kill is to avoid the test app being killed if ActivityManager is slow to
            // respond to the install event.
            // See b/202824003.
            String output = mDevice.installPackageForUser(file, true, false, userId, "--dont-kill");
            if (output != null) {
                stopAndRemoveUser(userId);
                Assert.fail("Failed to install test apk " + output);
            }
        } catch (FileNotFoundException e) {
            stopAndRemoveUser(userId);
            Assert.fail("Failed to install test apk " + DEVICE_TEST_APK);
        }
    }

    private int createUser(String profileName, boolean isManaged)
            throws DeviceNotAvailableException {
        String command = isManaged ?
                "pm create-user --profileOf %d --managed %s" :
                "pm create-user %s";
        command = String.format(command, mDevice.getMainUserId(), profileName);

        CommandResult output = mDevice.executeShellV2Command(command);
        if (CommandStatus.SUCCESS.equals(output.getStatus())) {
            try {
                String[] tokens = output.getStdout().split("\\s+");
                return Integer.parseInt(tokens[tokens.length - 1]);
            } catch (NumberFormatException e) {
                LogUtil.CLog.d("Failed to parse user id when creating a profile user");
            }
        }
        throw new IllegalStateException(String.format("Failed to create user: %s", output));
    }

    private void startUser(int userId) throws DeviceNotAvailableException {
        if (!mDevice.startUser(userId)) {
            mDevice.removeUser(userId);
            Assert.fail("Failed to start user " + userId);
        }
    }

    private void switchUser(int userId) throws DeviceNotAvailableException {
        if (!mDevice.switchUser(userId)) {
            stopAndRemoveUser(userId);
            Assert.fail("Failed to switch to user " + userId + ". This is usually not a bug in "
                    + "WebView, but might mean a system process crashed. Check logcat for "
                    + "\"FATAL EXCEPTION IN SYSTEM PROCESS\".");
        }
    }

    private void setProfileOwnwer(int userId, String componentName)
            throws DeviceNotAvailableException {
        String command = "dpm set-profile-owner --user " + userId + " '" + componentName + "'";
        CommandResult output = mDevice.executeShellV2Command(command);
        if (!CommandStatus.SUCCESS.equals(output.getStatus())) {
            stopAndRemoveUser(mUserId);
            Assert.fail("Failed to set profile owner");
        }
    }

    private void stopAndRemoveUser(int userId) throws DeviceNotAvailableException {
        mDevice.switchUser(mInitialUserId);
        if (!mDevice.stopUser(userId, /*waitFlag */true, /* stopFlag = */ true)) {
            Assert.fail("Failed to stop user " + userId);
        }
        if (!mDevice.removeUser(mUserId)) {
            Assert.fail("Failed to remove user " + userId);
        }
    }

    private boolean isMultiUsersSupported() throws DeviceNotAvailableException {
        return mDevice.getMaxNumberOfUsersSupported() > 1;
    }

    private boolean isManagedProfileSupported() throws DeviceNotAvailableException {
        return mDevice.hasFeature("android.software.managed_users");
    }

    private static void assertWebViewDeviceTestAsUserPasses(BaseHostJUnit4Test hostTest,
            String methodName, int userId) throws DeviceNotAvailableException {
        hostTest.runDeviceTests(
                new DeviceTestRunOptions(DEVICE_PACKAGE)
                        .setTestClassName(DEVICE_TEST_CLASS)
                        .setTestMethodName(methodName)
                        .setUserId(userId)
                        // Fail the host-side test if the device-side test fails.
                        .setCheckResults(true));
    }
}
