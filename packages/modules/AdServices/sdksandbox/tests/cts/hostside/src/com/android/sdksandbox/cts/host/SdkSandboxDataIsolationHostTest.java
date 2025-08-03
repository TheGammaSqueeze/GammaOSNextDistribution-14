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

package com.android.sdksandbox.cts.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.hosttestutils.AdoptableStorageUtils;
import android.app.sdksandbox.hosttestutils.DeviceSupportHostUtils;
import android.app.sdksandbox.hosttestutils.SecondaryUserUtils;
import android.platform.test.annotations.LargeTest;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SdkSandboxDataIsolationHostTest extends BaseHostJUnit4Test {

    private static final String APP_PACKAGE = "com.android.sdksandbox.cts.app";
    private static final String APP_APK = "CtsSdkSandboxHostTestApp.apk";
    private static final String APP_TEST_CLASS = APP_PACKAGE + ".SdkSandboxDataIsolationTestApp";

    private static final String APP_2_PACKAGE = "com.android.sdksandbox.cts.app2";
    private static final String APP_2_APK = "CtsSdkSandboxHostTestApp2.apk";

    private final SecondaryUserUtils mUserUtils = new SecondaryUserUtils(this);
    private final AdoptableStorageUtils mAdoptableUtils = new AdoptableStorageUtils(this);
    private final DeviceSupportHostUtils mDeviceSupportUtils = new DeviceSupportHostUtils(this);

    private DeviceSdkLevel mDeviceSdkLevel;

    /**
     * Runs the given phase of a test by calling into the device. Throws an exception if the test
     * phase fails.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests(APP_PACKAGE, APP_TEST_CLASS, phase)).isTrue();
    }

    private void runPhase(
            String phase, String instrumentationArgKey, String instrumentationArgValue)
            throws Exception {
        runDeviceTests(
                new DeviceTestRunOptions(APP_PACKAGE)
                        .setDevice(getDevice())
                        .setTestClassName(APP_TEST_CLASS)
                        .setTestMethodName(phase)
                        .addInstrumentationArg(instrumentationArgKey, instrumentationArgValue));
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue("Device supports SdkSandbox", mDeviceSupportUtils.isSdkSandboxSupported());
        mDeviceSdkLevel = new DeviceSdkLevel(getDevice());
        // These tests run on system user
        uninstallPackage(APP_PACKAGE);
        uninstallPackage(APP_2_PACKAGE);
    }

    @After
    public void tearDown() throws Exception {
        mUserUtils.removeSecondaryUserIfNecessary();
        uninstallPackage(APP_PACKAGE);
        uninstallPackage(APP_2_PACKAGE);
    }

    @Test
    public void testAppCannotAccessAnySandboxDirectories() throws Exception {
        installPackage(APP_APK);
        installPackage(APP_2_APK);

        runPhase("testAppCannotAccessAnySandboxDirectories");
    }

    /** Test whether an SDK can access its provided data directories after data isolation. */
    @Test
    public void testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory() throws Exception {
        installPackage(APP_APK);
        runPhase("testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory");
    }

    /**
     * Test whether an SDK can detect if an app is installed by the error obtained from accessing
     * other sandbox app directories. ENOENT error should occur regardless of whether the app exists
     * or not.
     */
    @Test
    public void testSdkSandboxDataIsolation_CannotVerifyAppExistence() throws Exception {
        // TODO(b/254608808,b/214241165): Remove once merged into QPR.
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastU());

        installPackage(APP_APK);
        installPackage(APP_2_APK);

        runPhase("testSdkSandboxDataIsolation_CannotVerifyAppExistence");
    }

    /**
     * Test whether an SDK can detect if an app is installed by the error obtained from accessing
     * other sandbox user directories. Permission errors should show up regardless of whether the
     * app exists, when trying to access other user data.
     */
    @Test
    @LargeTest // Creates user
    public void testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence() throws Exception {
        // TODO(b/254608808,b/214241165): Remove once merged into QPR.
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastU());

        assumeTrue(getDevice().isMultiUserSupported());

        installPackage(APP_APK);

        int userId = mUserUtils.createAndStartSecondaryUser();
        installPackageAsUser(APP_2_APK, true, userId);

        runPhase(
                "testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence",
                "sandbox_isolation_user_id",
                Integer.toString(userId));
    }

    /**
     * Test whether an SDK can verify an app's existence by checking other volumes, after data
     * isolation has occurred.
     */
    @Test
    @LargeTest // Creates volume
    public void testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes() throws Exception {
        // TODO(b/254608808,b/214241165): Remove once merged into QPR.
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastU());

        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());
        mAdoptableUtils.enableVirtualDisk();
        installPackage(APP_APK);
        installPackage(APP_2_APK);

        try {
            final String uuid = mAdoptableUtils.createNewVolume();

            // Move second package to the newly created volume
            assertSuccess(
                    getDevice()
                            .executeShellCommand("pm move-package " + APP_2_PACKAGE + " " + uuid));

            runPhase(
                    "testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes",
                    "sandbox_isolation_uuid",
                    uuid);
        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    private static void assertSuccess(String str) {
        if (str == null || !str.startsWith("Success")) {
            throw new AssertionError("Expected success string but found " + str);
        }
    }
}
