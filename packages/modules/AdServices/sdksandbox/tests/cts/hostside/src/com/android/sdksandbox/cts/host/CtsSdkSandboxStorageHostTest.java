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

import android.app.sdksandbox.hosttestutils.DeviceSupportHostUtils;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CtsSdkSandboxStorageHostTest extends BaseHostJUnit4Test {

    private static final String TEST_APP_PACKAGE_NAME = "com.android.sdksandbox.cts.app";
    private static final String TEST_APP_APK_NAME = "CtsSdkSandboxHostTestApp.apk";

    private final DeviceSupportHostUtils mDeviceSupportUtils = new DeviceSupportHostUtils(this);

    /**
     * Runs the given phase of a test by calling into the device. Throws an exception if the test
     * phase fails.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(
                        runDeviceTests(
                                TEST_APP_PACKAGE_NAME,
                                TEST_APP_PACKAGE_NAME + ".CtsSdkSandboxStorageTestApp",
                                phase))
                .isTrue();
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue("Device supports SdkSandbox", mDeviceSupportUtils.isSdkSandboxSupported());
        uninstallPackage(TEST_APP_PACKAGE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP_PACKAGE_NAME);
    }

    @Test
    public void testSharedPreferences_IsSyncedFromAppToSandbox() throws Exception {
        installPackage(TEST_APP_APK_NAME);
        runPhase("testSharedPreferences_IsSyncedFromAppToSandbox");
    }

    @Test
    public void testSharedPreferences_SyncPropagatesUpdates() throws Exception {
        installPackage(TEST_APP_APK_NAME);
        runPhase("testSharedPreferences_SyncPropagatesUpdates");
    }

    @Test
    public void testSharedPreferences_SyncStartedBeforeLoadingSdk() throws Exception {
        installPackage(TEST_APP_APK_NAME);
        runPhase("testSharedPreferences_SyncStartedBeforeLoadingSdk");
    }

    @Test
    public void testSharedPreferences_SyncRemoveKeys() throws Exception {
        installPackage(TEST_APP_APK_NAME);
        runPhase("testSharedPreferences_SyncRemoveKeys");
    }

    @Test
    public void testSharedPreferences_SyncedDataClearedOnSandboxRestart() throws Exception {
        installPackage(TEST_APP_APK_NAME);
        runPhase("testSharedPreferences_IsSyncedFromAppToSandbox");
        runPhase("testSharedPreferences_SyncedDataClearedOnSandboxRestart");
    }
}
