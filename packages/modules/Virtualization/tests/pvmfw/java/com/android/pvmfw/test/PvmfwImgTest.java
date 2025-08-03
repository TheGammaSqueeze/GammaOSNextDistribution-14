/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.pvmfw.test;

import static com.android.tradefed.device.TestDevice.MicrodroidBuilder;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertThrows;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.microdroid.test.host.MicrodroidHostTestCaseBase;
import com.android.pvmfw.test.host.Pvmfw;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Tests pvmfw.img and pvmfw */
@RunWith(DeviceJUnit4ClassRunner.class)
public class PvmfwImgTest extends MicrodroidHostTestCaseBase {
    @NonNull private static final String PVMFW_FILE_NAME = "pvmfw_test.bin";
    @NonNull private static final String BCC_FILE_NAME = "bcc.dat";
    @NonNull private static final String PACKAGE_FILE_NAME = "MicrodroidTestApp.apk";
    @NonNull private static final String PACKAGE_NAME = "com.android.microdroid.test";
    @NonNull private static final String MICRODROID_DEBUG_FULL = "full";

    @NonNull
    private static final String MICRODROID_CONFIG_PATH = "assets/microdroid/vm_config_apex.json";

    private static final int BOOT_COMPLETE_TIMEOUT_MS = 30000; // 30 seconds
    private static final int BOOT_FAILURE_WAIT_TIME_MS = 10000; // 10 seconds

    @NonNull private static final String CUSTOM_PVMFW_FILE_PREFIX = "pvmfw";
    @NonNull private static final String CUSTOM_PVMFW_FILE_SUFFIX = ".bin";
    @NonNull private static final String CUSTOM_PVMFW_IMG_PATH = TEST_ROOT + PVMFW_FILE_NAME;
    @NonNull private static final String CUSTOM_PVMFW_IMG_PATH_PROP = "hypervisor.pvmfw.path";

    @Nullable private static File mPvmfwBinFileOnHost;
    @Nullable private static File mBccFileOnHost;

    @Nullable private TestDevice mAndroidDevice;
    @Nullable private ITestDevice mMicrodroidDevice;
    @Nullable private File mCustomPvmfwBinFileOnHost;

    @Before
    public void setUp() throws Exception {
        mAndroidDevice = (TestDevice) Objects.requireNonNull(getDevice());

        // Check device capabilities
        assumeDeviceIsCapable(mAndroidDevice);
        assumeTrue(
                "Skip if protected VMs are not supported",
                mAndroidDevice.supportsMicrodroid(/* protectedVm= */ true));

        // tradefed copies the test artfacts under /tmp when running tests,
        // so we should *find* the artifacts with the file name.
        mPvmfwBinFileOnHost =
                getTestInformation().getDependencyFile(PVMFW_FILE_NAME, /* targetFirst= */ false);
        mBccFileOnHost =
                getTestInformation().getDependencyFile(BCC_FILE_NAME, /* targetFirst= */ false);

        // Prepare for system properties for custom pvmfw.img.
        // File will be prepared later in individual test and then pushed to device
        // when launching with launchProtectedVmAndWaitForBootCompleted().
        mCustomPvmfwBinFileOnHost =
                FileUtil.createTempFile(CUSTOM_PVMFW_FILE_PREFIX, CUSTOM_PVMFW_FILE_SUFFIX);
        setPropertyOrThrow(mAndroidDevice, CUSTOM_PVMFW_IMG_PATH_PROP, CUSTOM_PVMFW_IMG_PATH);

        // Prepare for launching microdroid
        mAndroidDevice.installPackage(findTestFile(PACKAGE_FILE_NAME), /* reinstall */ false);
        prepareVirtualizationTestSetup(mAndroidDevice);
        mMicrodroidDevice = null;
    }

    @After
    public void shutdown() throws Exception {
        if (!mAndroidDevice.supportsMicrodroid(/* protectedVm= */ true)) {
            return;
        }
        if (mMicrodroidDevice != null) {
            mAndroidDevice.shutdownMicrodroid(mMicrodroidDevice);
            mMicrodroidDevice = null;
        }
        mAndroidDevice.uninstallPackage(PACKAGE_NAME);

        // Cleanup for custom pvmfw.img
        setPropertyOrThrow(mAndroidDevice, CUSTOM_PVMFW_IMG_PATH_PROP, "");
        FileUtil.deleteFile(mCustomPvmfwBinFileOnHost);

        cleanUpVirtualizationTestSetup(mAndroidDevice);
    }

    @Test
    public void testConfigVersion1_0_boots() throws Exception {
        Pvmfw pvmfw =
                new Pvmfw.Builder(mPvmfwBinFileOnHost, mBccFileOnHost).setVersion(1, 0).build();
        pvmfw.serialize(mCustomPvmfwBinFileOnHost);

        launchProtectedVmAndWaitForBootCompleted(BOOT_COMPLETE_TIMEOUT_MS);
    }

    @Test
    public void testConfigVersion1_1_boots() throws Exception {
        Pvmfw pvmfw =
                new Pvmfw.Builder(mPvmfwBinFileOnHost, mBccFileOnHost).setVersion(1, 1).build();
        pvmfw.serialize(mCustomPvmfwBinFileOnHost);

        launchProtectedVmAndWaitForBootCompleted(BOOT_COMPLETE_TIMEOUT_MS);
    }

    @Test
    public void testInvalidConfigVersion_doesNotBoot() throws Exception {
        // Disclaimer: Update versions when they become valid
        List<int[]> invalid_versions =
                Arrays.asList(
                        new int[] {0, 0},
                        new int[] {0, 1},
                        new int[] {0, 0xFFFF},
                        new int[] {2, 0},
                        new int[] {2, 1},
                        new int[] {2, 0xFFFF},
                        new int[] {0xFFFF, 0},
                        new int[] {0xFFFF, 1},
                        new int[] {0xFFFF, 0xFFFF});

        Pvmfw.Builder builder = new Pvmfw.Builder(mPvmfwBinFileOnHost, mBccFileOnHost);

        for (int[] pair : invalid_versions) {
            int major = pair[0];
            int minor = pair[1];
            String version = "v" + major + "." + minor;

            Pvmfw pvmfw = builder.setVersion(major, minor).build();
            pvmfw.serialize(mCustomPvmfwBinFileOnHost);

            assertThrows(
                    "pvmfw shouldn't boot with invalid version " + version,
                    DeviceRuntimeException.class,
                    () -> launchProtectedVmAndWaitForBootCompleted(BOOT_FAILURE_WAIT_TIME_MS));
        }
    }

    private ITestDevice launchProtectedVmAndWaitForBootCompleted(long adbTimeoutMs)
            throws DeviceNotAvailableException {
        mMicrodroidDevice =
                MicrodroidBuilder.fromDevicePath(
                                getPathForPackage(PACKAGE_NAME), MICRODROID_CONFIG_PATH)
                        .debugLevel(MICRODROID_DEBUG_FULL)
                        .protectedVm(true)
                        .addBootFile(mCustomPvmfwBinFileOnHost, PVMFW_FILE_NAME)
                        .setAdbConnectTimeoutMs(adbTimeoutMs)
                        .build(mAndroidDevice);
        assertThat(mMicrodroidDevice.waitForBootComplete(BOOT_COMPLETE_TIMEOUT_MS)).isTrue();
        return mMicrodroidDevice;
    }
}
