/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.scopedstorage.cts.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.CtsDownstreamingTest;
import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs the legacy file path access tests.
 */
@CtsDownstreamingTest
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class PreserveLegacyStorageHostTest extends BaseHostTestCase {
    private static final String LEGACY_29_APK = "CtsLegacyStorageTestAppRequestLegacy.apk";
    private static final String PRESERVE_30_APK = "CtsLegacyStorageTestAppPreserveLegacy.apk";
    private static final String PACKAGE_NAME = "android.scopedstorage.cts.legacy.preserve";

    protected void installApp(String appFileName) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        int userId = getCurrentUserId();
        String result = getDevice().installPackageForUser(
                buildHelper.getTestFile(appFileName), true, true, userId, "-t");
        assertNull("Failed to install " + appFileName + " for user " + userId + ": " + result,
                result);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(PACKAGE_NAME);
    }

    @Test
    public void testPreserveLegacy() throws Exception {
        // This was broken on R, so only run the test on S+ devices
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(getDevice());
        assumeTrue(deviceSdkLevel.isDeviceAtLeastS());

        // Most of these tests are done device-side; see RestrictedStoragePermissionTest.java
        // This test is done on the host, because we want to verify preserveLegacyExternalStorage
        // is sticky across a reboot.
        installApp(LEGACY_29_APK);
        String result = getDevice().executeShellCommand(
                                    "appops get " + PACKAGE_NAME + " LEGACY_STORAGE");
        assertThat(result).contains(": allow");

        // Upgrade to targetSdk 30 with preserveLegacyExternalStorage
        installApp(PRESERVE_30_APK);
        result = getDevice().executeShellCommand(
                                    "appops get " + PACKAGE_NAME + " LEGACY_STORAGE");

        // And make sure we still have legacy
        assertThat(result).contains(": allow");

        // Reboot, and again make sure we have legacy
        getDevice().reboot();
        result = getDevice().executeShellCommand(
                                    "appops get " + PACKAGE_NAME + " LEGACY_STORAGE");
        assertThat(result).contains(": allow");
    }
}
