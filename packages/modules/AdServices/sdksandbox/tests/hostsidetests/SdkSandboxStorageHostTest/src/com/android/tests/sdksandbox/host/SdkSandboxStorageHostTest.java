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

package com.android.tests.sdksandbox.host;

import static android.appsecurity.cts.Utils.waitForBootCompleted;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.hosttestutils.AdoptableStorageUtils;
import android.app.sdksandbox.hosttestutils.AwaitUtils;
import android.app.sdksandbox.hosttestutils.DeviceSupportHostUtils;
import android.app.sdksandbox.hosttestutils.SecondaryUserUtils;
import android.platform.test.annotations.LargeTest;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxStorageHostTest extends BaseHostJUnit4Test {

    private static final String TEST_APP_STORAGE_PACKAGE = "com.android.tests.sdksandbox";
    private static final String TEST_APP_STORAGE_APK = "SdkSandboxStorageTestApp.apk";
    private static final String TEST_APP_STORAGE_V2_NO_SDK =
            "SdkSandboxStorageTestAppV2_DoesNotConsumeSdk.apk";
    private static final String SDK_NAME = "com.android.tests.codeprovider.storagetest";

    private static final String SHARED_DIR = "shared";
    private static final String SANDBOX_DIR = "sandbox";

    // Needs to be at least 20s since that's how long we delay reconcile on SdkSandboxManagerService
    private static final long WAIT_FOR_RECONCILE_MS = 30000;

    private final SecondaryUserUtils mUserUtils = new SecondaryUserUtils(this);
    private final AdoptableStorageUtils mAdoptableUtils = new AdoptableStorageUtils(this);
    private final DeviceLockUtils mDeviceLockUtils = new DeviceLockUtils(this);
    private final DeviceSupportHostUtils mDeviceSupportUtils = new DeviceSupportHostUtils(this);

    private DeviceSdkLevel mDeviceSdkLevel;

    /**
     * Runs the given phase of a test by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(
                        runDeviceTests(
                                TEST_APP_STORAGE_PACKAGE,
                                TEST_APP_STORAGE_PACKAGE + ".SdkSandboxStorageTestApp",
                                phase))
                .isTrue();
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(mDeviceSupportUtils.isSdkSandboxSupported());
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);
        mDeviceSdkLevel = new DeviceSdkLevel(getDevice());
    }

    @After
    public void tearDown() throws Exception {
        mUserUtils.removeSecondaryUserIfNecessary();
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);
    }

    @Test
    public void testSelinuxLabel() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);

        assertSelinuxLabel("/data/misc_ce/0/sdksandbox", "sdk_sandbox_system_data_file");
        assertSelinuxLabel("/data/misc_de/0/sdksandbox", "sdk_sandbox_system_data_file");

        // Check label of /data/misc_{ce,de}/0/sdksandbox/<package-name>
        assertSelinuxLabel(getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true),
                "sdk_sandbox_system_data_file");
        assertSelinuxLabel(getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false),
                "sdk_sandbox_system_data_file");
        // Check label of /data/misc_{ce,de}/0/sdksandbox/<app-name>/shared
        assertSelinuxLabel(
                getSdkDataInternalPath(0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, true),
                "sdk_sandbox_data_file");
        assertSelinuxLabel(
                getSdkDataInternalPath(0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, false),
                "sdk_sandbox_data_file");
        // Check label of /data/misc_{ce,de}/0/sdksandbox/<app-name>/<sdk-package>
        assertSelinuxLabel(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true),
                "sdk_sandbox_data_file");
        assertSelinuxLabel(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false),
                "sdk_sandbox_data_file");
    }

    /**
     * Verify that {@code /data/misc_{ce,de}/<user-id>/sdksandbox} is created when {@code <user-id>}
     * is created.
     */
    @Test
    @LargeTest // New User Created
    public void testSdkDataRootDirectory_IsCreatedOnUserCreate() throws Exception {
        assumeTrue("Multiple user not supported", mUserUtils.isMultiUserSupported());

        {
            // Verify root directory exists for primary user
            final String cePath = getSdkDataRootPath(0, true);
            final String dePath = getSdkDataRootPath(0, false);
            assertThat(getDevice().isDirectory(dePath)).isTrue();
            assertThat(getDevice().isDirectory(cePath)).isTrue();
        }

        {
            // Verify root directory is created for new user
            int secondaryUserId = mUserUtils.createAndStartSecondaryUser();
            final String cePath = getSdkDataRootPath(secondaryUserId, true);
            final String dePath = getSdkDataRootPath(secondaryUserId, false);
            assertThat(getDevice().isDirectory(dePath)).isTrue();
            assertThat(getDevice().isDirectory(cePath)).isTrue();
        }
    }

    @Test
    @LargeTest // New User Created
    public void testSdkDataRootDirectory_IsDestroyedOnUserDeletion() throws Exception {
        assumeTrue("Multiple user not supported", mUserUtils.isMultiUserSupported());

        // delete the new user
        final int newUser = mUserUtils.createAndStartSecondaryUser();
        mUserUtils.removeSecondaryUserIfNecessary(/*waitForUserDataDeletion=*/ true);

        // Sdk Sandbox root directories should not exist as the user was removed
        final String ceSdkSandboxDataRootPath = getSdkDataRootPath(newUser, true);
        final String deSdkSandboxDataRootPath = getSdkDataRootPath(newUser, false);
        assertThat(getDevice().isDirectory(ceSdkSandboxDataRootPath)).isFalse();
        assertThat(getDevice().isDirectory(deSdkSandboxDataRootPath)).isFalse();
    }

    @Test
    public void testSdkSandboxDataMirrorAppDirectory_IsCreatedOnInstall() throws Exception {
        // Sandbox data isolation fixes are in U+.
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastU());

        final String cePath = getSdkDataMirrorPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataMirrorPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);

        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
        installPackage(TEST_APP_STORAGE_APK);
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();
    }

    @Test
    @LargeTest // New volume created
    public void testSdkSandboxDataMirrorDirectory_IsVolumeSpecific() throws Exception {
        // Sandbox data isolation fixes are in U+.
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastU());
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        installPackage(TEST_APP_STORAGE_APK);

        String mirrorCeVolPath;
        String mirrorDeVolPath;
        try {
            final String uuid = mAdoptableUtils.createNewVolume();

            mirrorCeVolPath = "/data_mirror/misc_ce/" + uuid;
            mirrorDeVolPath = "/data_mirror/misc_de/" + uuid;
            final String mirrorCeVolPackagePath =
                    mirrorCeVolPath + "/0/sdksandbox/" + TEST_APP_STORAGE_PACKAGE;
            final String mirrorDeVolPackagePath =
                    mirrorDeVolPath + "/0/sdksandbox/" + TEST_APP_STORAGE_PACKAGE;

            assertThat(getDevice().isDirectory(mirrorCeVolPath)).isTrue();
            assertThat(getDevice().isDirectory(mirrorDeVolPath)).isTrue();
            assertThat(getDevice().isDirectory(mirrorCeVolPackagePath)).isFalse();
            assertThat(getDevice().isDirectory(mirrorDeVolPackagePath)).isFalse();

            // Move package to the newly created volume
            assertSuccess(
                    getDevice()
                            .executeShellCommand(
                                    "pm move-package " + TEST_APP_STORAGE_PACKAGE + " " + uuid));

            assertThat(getDevice().isDirectory(mirrorCeVolPackagePath)).isTrue();
            assertThat(getDevice().isDirectory(mirrorDeVolPackagePath)).isTrue();
        } finally {
            mAdoptableUtils.cleanUpVolume();
        }

        assertThat(getDevice().isDirectory(mirrorCeVolPath)).isFalse();
        assertThat(getDevice().isDirectory(mirrorDeVolPath)).isFalse();
    }

    /**
     * Verify that {@code /data/misc_{ce,de}/<user-id>/sdksandbox} is not accessible by apps
     */
    @Test
    public void testSdkSandboxDataRootDirectory_IsNotAccessibleByApps() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Verify root directory exists for primary user
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(dePath)).isTrue();
        assertThat(getDevice().isDirectory(cePath)).isTrue();

        runPhase("testSdkSandboxDataRootDirectory_IsNotAccessibleByApps");
    }

    @Test
    public void testSdkDataPackageDirectory_IsCreatedOnInstall() throws Exception {
        // Directory should not exist before install
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();

        // Install the app
        installPackage(TEST_APP_STORAGE_APK);
        waitForSdkDirectoryCreatedForUser(0);

        // Verify directory is created
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();
    }

    @Test
    public void testSdkDataPackageDirectory_IsNotCreatedWithoutSdkConsumption()
            throws Exception {
        // Install the an app that does not consume sdk
        installPackage(TEST_APP_STORAGE_V2_NO_SDK);

        // Verify directories are not created
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Test
    public void testSdkDataPackageDirectory_IsDestroyedOnUninstall() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        //Uninstall the app
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);

        // Directory should not exist after uninstall
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        // Verify directory is destoyed
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Ignore("b/260659816")
    @Test
    @LargeTest
    public void testSdkDataPackageDirectory_IsDestroyedOnUninstall_DeviceLocked() throws Exception {
        assumeThat("Device is NOT encrypted with file-based encryption.",
                getDevice().getProperty("ro.crypto.type"), equalTo("file"));
        assumeTrue("Screen lock is not supported so skip direct boot test",
                hasDeviceFeature("android.software.secure_lock_screen"));

        installPackage(TEST_APP_STORAGE_APK);

        // Verify sdk ce directory contains TEST_APP_STORAGE_PACKAGE
        final String ceSandboxPath = getSdkDataRootPath(0, /*isCeData=*/ true);
        String[] children = getDevice().getChildren(ceSandboxPath);
        assertThat(children).isNotEmpty();
        final int numberOfChildren = children.length;
        assertThat(children).asList().contains(TEST_APP_STORAGE_PACKAGE);

        try {
            mDeviceLockUtils.rebootToLockedDevice();

            // Verify sdk ce package directory is encrypted, so longer contains the test package
            children = getDevice().getChildren(ceSandboxPath);
            assertThat(children).hasLength(numberOfChildren);
            assertThat(children).asList().doesNotContain(TEST_APP_STORAGE_PACKAGE);

            // Uninstall while device is locked
            uninstallPackage(TEST_APP_STORAGE_PACKAGE);

            // Verify ce sdk data did not change while device is locked
            children = getDevice().getChildren(ceSandboxPath);
            assertThat(children).hasLength(numberOfChildren);

            // Meanwhile, de storage area should already be deleted
            final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
            assertThat(getDevice().isDirectory(dePath)).isFalse();
        } finally {
            mDeviceLockUtils.clearScreenLock();
        }

        // Once device is unlocked, the uninstallation during locked state should take effect.
        // Allow some time for background task to run.
        Thread.sleep(WAIT_FOR_RECONCILE_MS);

        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        assertDirectoryDoesNotExist(cePath);
        // Verify number of children under root directory is one less than before
        children = getDevice().getChildren(ceSandboxPath);
        assertThat(children).hasLength(numberOfChildren - 1);
        assertThat(children).asList().doesNotContain(TEST_APP_STORAGE_PACKAGE);
    }

    @Test
    @LargeTest // Device reboot
    public void testSdkDataPackageDirectory_IsReconciled_InvalidAndMissingPackage()
            throws Exception {

        installPackage(TEST_APP_STORAGE_APK);

        // Rename the sdk data directory to some non-existing package name
        final String cePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String ceInvalidDir = getSdkDataPackagePath(0, "com.invalid.foo", true);
        getDevice().executeShellCommand(String.format("mv %s %s", cePackageDir, ceInvalidDir));
        assertDirectoryExists(ceInvalidDir);

        final String dePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        final String deInvalidDir = getSdkDataPackagePath(0, "com.invalid.foo", false);
        getDevice().executeShellCommand(String.format("mv %s %s", dePackageDir, deInvalidDir));
        assertDirectoryExists(deInvalidDir);

        // Reboot since reconcilation happens on user unlock only
        getDevice().reboot();
        Thread.sleep(WAIT_FOR_RECONCILE_MS);

        // Verify invalid directory doesn't exist
        assertDirectoryDoesNotExist(ceInvalidDir);
        assertDirectoryDoesNotExist(deInvalidDir);
        assertDirectoryExists(cePackageDir);
        assertDirectoryExists(dePackageDir);
    }

    @Test
    @LargeTest // Device reboot
    public void testSdkDataPackageDirectory_IsReconciled_IncludesDifferentVolumes()
            throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        try {
            installPackage(TEST_APP_STORAGE_APK);

            final String newVolumeUuid = mAdoptableUtils.createNewVolume();

            assertSuccess(
                    getDevice()
                            .executeShellCommand(
                                    "pm move-package "
                                            + TEST_APP_STORAGE_PACKAGE
                                            + " "
                                            + newVolumeUuid));

            final String ceSdkDataPackagePath =
                    getSdkDataPackagePath(newVolumeUuid, 0, TEST_APP_STORAGE_PACKAGE, true);
            final String deSdkDataPackagePath =
                    getSdkDataPackagePath(newVolumeUuid, 0, TEST_APP_STORAGE_PACKAGE, false);

            // Rename the sdk data directory to some non-existing package name
            final String ceInvalidDir =
                    getSdkDataPackagePath(newVolumeUuid, 0, "com.invalid.foo", true);
            getDevice()
                    .executeShellCommand(
                            String.format("mv %s %s", ceSdkDataPackagePath, ceInvalidDir));
            assertDirectoryExists(ceInvalidDir);

            final String deInvalidDir =
                    getSdkDataPackagePath(newVolumeUuid, 0, "com.invalid.foo", false);
            getDevice()
                    .executeShellCommand(
                            String.format("mv %s %s", deSdkDataPackagePath, deInvalidDir));
            assertDirectoryExists(deInvalidDir);

            // Reboot since reconcilation happens on user unlock only
            getDevice().reboot();
            Thread.sleep(WAIT_FOR_RECONCILE_MS);

            // Verify invalid directory doesn't exist
            assertDirectoryDoesNotExist(ceInvalidDir);
            assertDirectoryDoesNotExist(deInvalidDir);
            assertDirectoryExists(ceSdkDataPackagePath);
            assertDirectoryExists(deSdkDataPackagePath);

        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    @LargeTest // Create new volume
    public void testSdkDataPackageDirectory_IsReconciled_ChecksForPackageOnWrongVolume()
            throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        try {
            installPackage(TEST_APP_STORAGE_APK);

            final String newVolumeUuid = mAdoptableUtils.createNewVolume();

            assertSuccess(
                    getDevice()
                            .executeShellCommand(
                                    "pm move-package "
                                            + TEST_APP_STORAGE_PACKAGE
                                            + " "
                                            + newVolumeUuid));

            final String ceInvalidDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
            final String deInvalidDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);

            // Create sdk package directory for testapp on null volume
            getDevice().executeShellCommand(String.format("mkdir %s", ceInvalidDir));
            getDevice().executeShellCommand(String.format("mkdir %s", deInvalidDir));

            // Reboot since reconcilation happens on user unlock only
            getDevice().reboot();
            Thread.sleep(WAIT_FOR_RECONCILE_MS);

            // Verify invalid directory doesn't exist
            assertDirectoryDoesNotExist(ceInvalidDir);
            assertDirectoryDoesNotExist(deInvalidDir);

        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    @LargeTest // Device reboot
    public void testSdkDataPackageDirectory_IsReconciled_MissingSubDirs() throws Exception {

        installPackage(TEST_APP_STORAGE_APK);

        final String cePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        // Delete the shared directory
        final String sharedDir = cePackageDir + "/" + SHARED_DIR;
        getDevice().deleteFile(sharedDir);
        assertDirectoryDoesNotExist(sharedDir);

        // Reboot since reconcilation happens on user unlock only
        getDevice().reboot();
        Thread.sleep(WAIT_FOR_RECONCILE_MS);

        // Verify shared dir exists
        assertDirectoryExists(sharedDir);
    }

    @Test
    @LargeTest // Device reboot
    public void testSdkDataPackageDirectory_IsReconciled_DeleteKeepData() throws Exception {

        installPackage(TEST_APP_STORAGE_APK);

        // Uninstall while keeping the data
        getDevice().executeShellCommand("pm uninstall -k --user 0 " + TEST_APP_STORAGE_PACKAGE);

        final String cePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertDirectoryExists(cePackageDir);
        assertDirectoryExists(dePackageDir);

        // Reboot since reconcilation happens on user unlock only
        getDevice().reboot();
        Thread.sleep(WAIT_FOR_RECONCILE_MS);

        // Verify sdk data are not cleaned up during reconcilation
        assertDirectoryExists(cePackageDir);
        assertDirectoryExists(dePackageDir);
    }

    @Test
    @LargeTest // Device reboot
    public void testSdkDataPackageDirectory_IsReconciled_DeleteKeepNewVolumeData()
            throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        try {
            installPackage(TEST_APP_STORAGE_APK);

            final String newVolumeUuid = mAdoptableUtils.createNewVolume();

            assertSuccess(
                    getDevice()
                            .executeShellCommand(
                                    "pm move-package "
                                            + TEST_APP_STORAGE_PACKAGE
                                            + " "
                                            + newVolumeUuid));

            // Uninstall while keeping the data
            getDevice().executeShellCommand("pm uninstall -k --user 0 " + TEST_APP_STORAGE_PACKAGE);

            final String ceSdkDataPackagePath =
                    getSdkDataPackagePath(newVolumeUuid, 0, TEST_APP_STORAGE_PACKAGE, true);
            final String deSdkDataPackagePath =
                    getSdkDataPackagePath(newVolumeUuid, 0, TEST_APP_STORAGE_PACKAGE, false);

            assertDirectoryExists(ceSdkDataPackagePath);
            assertDirectoryExists(deSdkDataPackagePath);

            // Reboot since reconcilation happens on user unlock only
            getDevice().reboot();
            Thread.sleep(WAIT_FOR_RECONCILE_MS);

            // Verify sdk data are not cleaned up during reconcilation
            assertDirectoryExists(ceSdkDataPackagePath);
            assertDirectoryExists(deSdkDataPackagePath);
        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    public void testSdkDataPackageDirectory_IsClearedOnClearAppData() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Ensure per-sdk storage has been created
        runPhase("loadSdk");

        // Create app data to be cleared
        final List<String> dataPaths =
                Arrays.asList(
                        getAppDataPath(0, TEST_APP_STORAGE_PACKAGE, true), // CE app data
                        getAppDataPath(0, TEST_APP_STORAGE_PACKAGE, false), // DE app data
                        getSdkDataInternalPath(
                                0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, true), // CE sdk data
                        getSdkDataInternalPath(
                                0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, false), // DE sdk data
                        getSdkDataPerSdkPath(
                                0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true), // CE per-sdk
                        getSdkDataPerSdkPath(
                                0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false) // DE per-sdk
                        );
        for (String dataPath : dataPaths) {
            final String fileToDelete = dataPath + "/cache/deleteme.txt";
            getDevice().executeShellCommand("echo something to delete > " + fileToDelete);
            assertThat(getDevice().doesFileExist(fileToDelete)).isTrue();
        }

        // Clear the app data
        getDevice().executeShellCommand("pm clear " + TEST_APP_STORAGE_PACKAGE);

        // Verify cache directories are empty
        for (String dataPath : dataPaths) {
            final String[] cacheChildren = getDevice().getChildren(dataPath);
            assertWithMessage(dataPath + " is not empty").that(cacheChildren).asList().isEmpty();
        }
    }

    @Test
    public void testSdkDataPackageDirectory_IsClearedOnFreeCache() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Ensure per-sdk storage has been created
        runPhase("loadSdk");

        // Create cache data to be cleared
        final List<String> dataPaths =
                Arrays.asList(
                        getAppDataPath(0, TEST_APP_STORAGE_PACKAGE, true), // CE app data
                        getAppDataPath(0, TEST_APP_STORAGE_PACKAGE, false), // DE app data
                        getSdkDataInternalPath(
                                0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, true), // CE sdk data
                        getSdkDataInternalPath(
                                0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, false), // DE sdk data
                        getSdkDataPerSdkPath(
                                0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true), // CE per-sdk
                        getSdkDataPerSdkPath(
                                0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false) // DE per-sdk
                        );
        for (String dataPath : dataPaths) {
            final String fileToDelete = dataPath + "/cache/deleteme.txt";
            getDevice().executeShellCommand("echo something to delete > " + fileToDelete);
            assertThat(getDevice().doesFileExist(fileToDelete)).isTrue();
        }

        // Clear all other cached data to give ourselves a clean slate
        getDevice().executeShellCommand("pm trim-caches 4096G");

        // Verify cache directories are empty
        for (String dataPath : dataPaths) {
            final String[] cacheChildren = getDevice().getChildren(dataPath + "/cache");
            assertWithMessage(dataPath + " is not empty").that(cacheChildren).asList().isEmpty();
        }
    }

    @Test
    public void testSdkDataPackageDirectory_IsClearedOnClearCache() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Ensure per-sdk storage has been created
        runPhase("loadSdk");

        // Create cache data to be cleared
        final List<String> dataPaths =
                Arrays.asList(
                        getAppDataPath(0, TEST_APP_STORAGE_PACKAGE, true), // CE app data
                        getAppDataPath(0, TEST_APP_STORAGE_PACKAGE, false), // DE app data
                        getSdkDataInternalPath(
                                0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, true), // CE sdk data
                        getSdkDataInternalPath(
                                0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, false), // DE sdk data
                        getSdkDataPerSdkPath(
                                0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true), // CE per-sdk
                        getSdkDataPerSdkPath(
                                0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false) // DE per-sdk
                        );
        for (String dataPath : dataPaths) {
            final String fileToDelete = dataPath + "/cache/deleteme.txt";
            getDevice().executeShellCommand("echo something to delete > " + fileToDelete);
            assertThat(getDevice().doesFileExist(fileToDelete)).isTrue();
        }

        // Clear the cached data for the test app
        getDevice()
                .executeShellCommand("pm clear --user 0 --cache-only com.android.tests.sdksandbox");

        // Verify cache directories are empty
        for (String dataPath : dataPaths) {
            final String[] cacheChildren = getDevice().getChildren(dataPath + "/cache");
            assertWithMessage(dataPath + " is not empty").that(cacheChildren).asList().isEmpty();
        }
    }

    @Test
    @LargeTest // New user created
    public void testSdkDataPackageDirectory_IsUserSpecific() throws Exception {
        assumeTrue("Multiple user not supported", mUserUtils.isMultiUserSupported());

        // Install first before creating the user
        installPackage(TEST_APP_STORAGE_APK, "--user all");
        waitForSdkDirectoryCreatedForUser(0);

        int secondaryUserId = mUserUtils.createAndStartSecondaryUser();

        // Data directories should not exist as the package is not installed on new user
        final String ceAppPath = getAppDataPath(secondaryUserId, TEST_APP_STORAGE_PACKAGE, true);
        final String deAppPath = getAppDataPath(secondaryUserId, TEST_APP_STORAGE_PACKAGE, false);
        final String cePath =
                getSdkDataPackagePath(secondaryUserId, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath =
                getSdkDataPackagePath(secondaryUserId, TEST_APP_STORAGE_PACKAGE, false);

        assertThat(getDevice().isDirectory(ceAppPath)).isFalse();
        assertThat(getDevice().isDirectory(deAppPath)).isFalse();
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();

        // Install the app on new user
        installPackage(TEST_APP_STORAGE_APK);

        assertThat(getDevice().isDirectory(ceAppPath)).isTrue();
        assertThat(getDevice().isDirectory(deAppPath)).isTrue();
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();

        mUserUtils.removeSecondaryUserIfNecessary(/*waitForUserDataDeletion=*/ true);
    }

    @Test
    public void testSdkDataPackageDirectory_SharedStorageIsUsable() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        waitForSdkDirectoryCreatedForUser(0);

        // Verify that shared storage exist
        final String sharedCePath =
                getSdkDataInternalPath(0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, true);
        assertThat(getDevice().isDirectory(sharedCePath)).isTrue();

        // Write a file in the shared storage that code needs to read and write it back
        // in another file
        String fileToRead = sharedCePath + "/readme.txt";
        getDevice().executeShellCommand("echo something to read > " + fileToRead);
        assertThat(getDevice().doesFileExist(fileToRead)).isTrue();

        runPhase("testSdkDataPackageDirectory_SharedStorageIsUsable");

        // Assert that code was able to create file and directories
        assertThat(getDevice().isDirectory(sharedCePath + "/dir")).isTrue();
        assertThat(getDevice().doesFileExist(sharedCePath + "/dir/file")).isTrue();
        String content = getDevice().executeShellCommand("cat " + sharedCePath + "/dir/file");
        assertThat(content).isEqualTo("something to read");
    }

    @Test
    public void testSdkDataPackageDirectory_CreateMissingSdkSubDirsWhenPackageDirEmpty()
            throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        waitForSdkDirectoryCreatedForUser(0);

        // Now delete the sdk data sub-dirs so that package directory is empty
        final String cePackagePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        final List<String> ceSdkDirsBeforeLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/true);
        final List<String> deSdkDirsBeforeLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/true);
        assertThat(getDevice().getChildren(cePackagePath)).asList().isNotEmpty();
        // Delete the sdk sub directories
        for (String child : ceSdkDirsBeforeLoadingSdksList) {
            getDevice().deleteFile(cePackagePath + "/" + child);
        }
        for (String child : deSdkDirsBeforeLoadingSdksList) {
            getDevice().deleteFile(dePackagePath + "/" + child);
        }
        assertThat(getDevice().getChildren(cePackagePath)).asList().isEmpty();

        runPhase("loadSdk");

        final List<String> ceSdkDirsAfterLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/false);
        final List<String> deSdkDirsAfterLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/false);
        assertThat(ceSdkDirsAfterLoadingSdksList)
                .containsExactly(SHARED_DIR, SDK_NAME, SANDBOX_DIR);
        assertThat(deSdkDirsAfterLoadingSdksList)
                .containsExactly(SHARED_DIR, SDK_NAME, SANDBOX_DIR);
    }

    @Test
    public void testSdkDataPackageDirectory_CreateMissingSdkSubDirsWhenPackageDirMissing()
            throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        waitForSdkDirectoryCreatedForUser(0);

        final String cePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        // Delete the package paths
        getDevice().deleteFile(cePackagePath);
        getDevice().deleteFile(dePackagePath);
        runPhase("loadSdk");

        final List<String> ceSdkDirsAfterLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/false);
        final List<String> deSdkDirsAfterLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/false);
        assertThat(ceSdkDirsAfterLoadingSdksList)
                .containsExactly(SHARED_DIR, SDK_NAME, SANDBOX_DIR);
        assertThat(deSdkDirsAfterLoadingSdksList)
                .containsExactly(SHARED_DIR, SDK_NAME, SANDBOX_DIR);
    }

    @Test
    public void testSdkDataPackageDirectory_CreateMissingSdkSubDirsWhenPackageDirIsNotEmpty()
            throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        final String cePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        final List<String> ceSdkDirsBeforeLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/true);
        final List<String> deSdkDirsBeforeLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/true);
        // Delete the sdk sub directories
        getDevice().deleteFile(cePackagePath + "/" + ceSdkDirsBeforeLoadingSdksList.get(0));
        getDevice().deleteFile(dePackagePath + "/" + deSdkDirsBeforeLoadingSdksList.get(0));
        runPhase("loadSdk");

        final List<String> ceSdkDirsAfterLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/false);
        final List<String> deSdkDirsAfterLoadingSdksList =
                getSubDirs(dePackagePath, /*includeRandomSuffix=*/ false);
        assertThat(ceSdkDirsAfterLoadingSdksList)
                .containsExactly(SHARED_DIR, SDK_NAME, SANDBOX_DIR);
        assertThat(deSdkDirsAfterLoadingSdksList)
                .containsExactly(SHARED_DIR, SDK_NAME, SANDBOX_DIR);
    }

    @Test
    public void testSdkDataPackageDirectory_ReuseExistingRandomSuffixInReconcile()
            throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        waitForSdkDirectoryCreatedForUser(0);

        final String cePackagePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackagePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        final List<String> ceSdkDirsBeforeLoadingSdksList =
                getSubDirs(cePackagePath, /*includeRandomSuffix=*/ true);
        final List<String> deSdkDirsBeforeLoadingSdksList =
                getSubDirs(dePackagePath, /*includeRandomSuffix=*/ true);

        // Delete the sdk sub directories
        getDevice().deleteFile(cePackagePath + "/" + SHARED_DIR);
        getDevice().deleteFile(dePackagePath + "/" + SHARED_DIR);

        runPhase("loadSdk");

        final List<String> ceSdkDirsAfterLoadingSdksList =
                getSubDirs(cePackagePath, /*includeRandomSuffix=*/ true);
        final List<String> deSdkDirsAfterLoadingSdksList =
                getSubDirs(dePackagePath, /*includeRandomSuffix=*/ true);
        assertThat(ceSdkDirsAfterLoadingSdksList)
                .containsExactlyElementsIn(ceSdkDirsBeforeLoadingSdksList);
        assertThat(deSdkDirsAfterLoadingSdksList)
                .containsExactlyElementsIn(deSdkDirsBeforeLoadingSdksList);
    }

    @Test
    public void testSdkDataPackageDirectory_OnUpdateDoesNotConsumeSdk() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);

        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();

        // Update app so that it no longer consumes any sdk
        installPackage(TEST_APP_STORAGE_V2_NO_SDK);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Test
    public void testSdkDataSubDirectory_IsCreatedOnInstall() throws Exception {
        // Directory should not exist before install
        assertThat(getSdkDataInternalPath(0, TEST_APP_STORAGE_PACKAGE, SANDBOX_DIR, true)).isNull();
        assertThat(getSdkDataInternalPath(0, TEST_APP_STORAGE_PACKAGE, SANDBOX_DIR, false))
                .isNull();
        assertThat(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true)).isNull();
        assertThat(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false)).isNull();

        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Verify directory is created
        assertThat(getSdkDataInternalPath(0, TEST_APP_STORAGE_PACKAGE, SANDBOX_DIR, true))
                .isNotNull();
        assertThat(getSdkDataInternalPath(0, TEST_APP_STORAGE_PACKAGE, SANDBOX_DIR, false))
                .isNotNull();
        assertThat(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true)).isNotNull();
        assertThat(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false)).isNotNull();
    }

    @Ignore("b/260659816")
    @Test
    @LargeTest
    public void testSdkDataSubDirectory_IsCreatedOnInstall_DeviceLocked() throws Exception {
        assumeThat(
                "Device is NOT encrypted with file-based encryption.",
                getDevice().getProperty("ro.crypto.type"),
                equalTo("file"));
        assumeTrue(
                "Screen lock is not supported so skip direct boot test",
                hasDeviceFeature("android.software.secure_lock_screen"));

        try {
            mDeviceLockUtils.rebootToLockedDevice();
            // Install app after installation
            installPackage(TEST_APP_STORAGE_APK);
            // De storage area should already have per-sdk directories
            assertThat(
                            getSdkDataPerSdkPath(
                                    0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, /*isCeData=*/ false))
                    .isNotNull();

            mDeviceLockUtils.unlockDevice();

            // Allow some time for reconciliation task to finish
            Thread.sleep(WAIT_FOR_RECONCILE_MS);

            assertThat(
                            getSdkDataPerSdkPath(
                                    0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, /*isCeData=*/ false))
                    .isNotNull();
            // Once device is unlocked, the per-sdk ce directories should be created
            assertThat(
                            getSdkDataPerSdkPath(
                                    0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, /*isCeData=*/ true))
                    .isNotNull();
        } finally {
            mDeviceLockUtils.clearScreenLock();
        }
    }

    @Test
    public void testSdkDataSubDirectory_PerSdkStorageIsUsable() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        waitForSdkDirectoryCreatedForUser(0);

        // Verify that per-sdk storage exist
        final String perSdkStorage =
                getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true);
        assertThat(getDevice().isDirectory(perSdkStorage)).isTrue();

        // Write a file in the storage that code needs to read and write it back
        // in another file
        String fileToRead = perSdkStorage + "/readme.txt";
        getDevice().executeShellCommand("echo something to read > " + fileToRead);
        assertThat(getDevice().doesFileExist(fileToRead)).isTrue();

        runPhase("testSdkDataSubDirectory_PerSdkStorageIsUsable");

        // Assert that code was able to create file and directories
        assertWithMessage("Failed to create directory in per-sdk storage")
                .that(getDevice().isDirectory(perSdkStorage + "/dir"))
                .isTrue();
        assertThat(getDevice().doesFileExist(perSdkStorage + "/dir/file")).isTrue();
        String content = getDevice().executeShellCommand("cat " + perSdkStorage + "/dir/file");
        assertThat(content).isEqualTo("something to read");
    }

    @Test
    @LargeTest // New volume created
    public void testSdkDataSubDirectory_PerSdkStorageIsUsable_DifferentVolume() throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        installPackage(TEST_APP_STORAGE_APK);

        try {
            final String newVolumeUuid = mAdoptableUtils.createNewVolume();
            assertSuccess(
                    getDevice()
                            .executeShellCommand(
                                    "pm move-package "
                                            + TEST_APP_STORAGE_PACKAGE
                                            + " "
                                            + newVolumeUuid));

            // Verify that per-sdk storage exist
            final String perSdkStorage =
                    getSdkDataPerSdkPath(
                            newVolumeUuid, 0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true);

            assertThat(getDevice().isDirectory(perSdkStorage)).isTrue();

            // Write a file in the storage that code needs to read and write it back
            // in another file
            String fileToRead = perSdkStorage + "/readme.txt";
            getDevice().executeShellCommand("echo something to read > " + fileToRead);
            assertThat(getDevice().doesFileExist(fileToRead)).isTrue();

            runPhase("testSdkDataSubDirectory_PerSdkStorageIsUsable");

            // Assert that code was able to create file and directories
            assertWithMessage("Failed to create directory in per-sdk storage")
                    .that(getDevice().isDirectory(perSdkStorage + "/dir"))
                    .isTrue();
            assertThat(getDevice().doesFileExist(perSdkStorage + "/dir/file")).isTrue();
            String content = getDevice().executeShellCommand("cat " + perSdkStorage + "/dir/file");
            assertThat(content).isEqualTo("something to read");
        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    @LargeTest // New volume created
    public void testSdkData_CanBeMovedToDifferentVolume() throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        installPackage(TEST_APP_STORAGE_APK);
        waitForSdkDirectoryCreatedForUser(0);

        // Create a new adoptable storage where we will be moving our installed package
        try {
            final String newVolumeUuid = mAdoptableUtils.createNewVolume();

            assertSuccess(getDevice().executeShellCommand(
                    "pm move-package " + TEST_APP_STORAGE_PACKAGE + " " + newVolumeUuid));

            // Verify that sdk data is moved
            for (int i = 0; i < 2; i++) {
                boolean isCeData = (i == 0) ? true : false;
                final String sdkDataRootPath = getSdkDataRootPath(newVolumeUuid, 0, isCeData);
                final String sdkDataPackagePath = sdkDataRootPath + "/" + TEST_APP_STORAGE_PACKAGE;
                final String sdkDataSharedPath = sdkDataPackagePath + "/" + SHARED_DIR;

                assertThat(getDevice().isDirectory(sdkDataRootPath)).isTrue();
                assertThat(getDevice().isDirectory(sdkDataPackagePath)).isTrue();
                assertThat(getDevice().isDirectory(sdkDataSharedPath)).isTrue();

                assertSelinuxLabel(sdkDataRootPath, "system_data_file");
                assertSelinuxLabel(sdkDataPackagePath, "system_data_file");
                assertSelinuxLabel(sdkDataSharedPath, "sdk_sandbox_data_file");
            }
        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    @LargeTest // New volume created
    public void testSdkSharedStorage_DifferentVolumeIsUsable() throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        installPackage(TEST_APP_STORAGE_APK);

        // Move the app to another volume and check if the sdk can read and write to it.
        try {
            final String newVolumeUuid = mAdoptableUtils.createNewVolume();
            assertSuccess(getDevice().executeShellCommand(
                    "pm move-package " + TEST_APP_STORAGE_PACKAGE + " " + newVolumeUuid));

            final String sharedCePath =
                    getSdkDataInternalPath(
                            newVolumeUuid, 0, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, true);
            assertThat(getDevice().isDirectory(sharedCePath)).isTrue();

            String fileToRead = sharedCePath + "/readme.txt";
            getDevice().executeShellCommand("echo something to read > " + fileToRead);
            assertThat(getDevice().doesFileExist(fileToRead)).isTrue();

            runPhase("testSdkDataPackageDirectory_SharedStorageIsUsable");

            // Assert that the sdk was able to create file and directories
            assertThat(getDevice().isDirectory(sharedCePath + "/dir")).isTrue();
            assertThat(getDevice().doesFileExist(sharedCePath + "/dir/file")).isTrue();
            String content = getDevice().executeShellCommand("cat " + sharedCePath + "/dir/file");
            assertThat(content).isEqualTo("something to read");

        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    @LargeTest // New volume created
    public void testSdkData_ReconcileSdkDataSubDirsIncludesDifferentVolumes() throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        installPackage(TEST_APP_STORAGE_APK);
        waitForSdkDirectoryCreatedForUser(0);

        // Create a new adoptable storage where we will be moving our installed package
        try {
            final String newVolumeUuid = mAdoptableUtils.createNewVolume();

            assertSuccess(
                    getDevice()
                            .executeShellCommand(
                                    "pm move-package "
                                            + TEST_APP_STORAGE_PACKAGE
                                            + " "
                                            + newVolumeUuid));

            // Verify that sdk data is moved
            for (int i = 0; i < 2; i++) {
                boolean isCeData = (i == 0) ? true : false;
                final String sdkDataPackagePath =
                        getSdkDataPackagePath(newVolumeUuid, 0, TEST_APP_STORAGE_PACKAGE, isCeData);

                final List<String> sdkDirsBeforeLoadingSdksList =
                        getSubDirs(sdkDataPackagePath, /*includeRandomSuffix=*/ true);
                // Forcing the reconciling by deleting the sdk sub directory
                getDevice()
                        .deleteFile(sdkDataPackagePath + "/" + sdkDirsBeforeLoadingSdksList.get(0));

                runPhase("loadSdk");

                final String OldPackagePath =
                        getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, isCeData);
                assertDirectoryDoesNotExist(OldPackagePath);
                final List<String> SdkDirsInNewVolume =
                        getSubDirs(sdkDataPackagePath, /*includeRandomSuffix=*/ false);
                assertThat(SdkDirsInNewVolume).containsExactly(SHARED_DIR, SDK_NAME, SANDBOX_DIR);
            }
        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    public void testSdkData_IsAttributedToApp() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        runPhase("testSdkDataIsAttributedToApp");
    }

    @Ignore("b/261429833")
    @Test
    public void testSdkData_IsAttributedToApp_DisableQuota() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        String initialValue = getDevice().getProperty("fw.disable_quota");
        try {
            assertThat(getDevice().setProperty("fw.disable_quota", "true")).isTrue();
            runPhase("testSdkDataIsAttributedToApp");
        } finally {
            if (initialValue == null) initialValue = "false";
            assertThat(getDevice().setProperty("fw.disable_quota", initialValue)).isTrue();
        }
    }

    private String getAppDataPath(int userId, String packageName, boolean isCeData) {
        return getAppDataPath(/*volumeUuid=*/ null, userId, packageName, isCeData);
    }

    private String getAppDataPath(
            @Nullable String volumeUuid, int userId, String packageName, boolean isCeData) {
        if (isCeData) {
            return String.format(
                    "/%s/user/%d/%s", getDataDirectory(volumeUuid), userId, packageName);
        } else {
            return String.format(
                    "/%s/user_de/%d/%s", getDataDirectory(volumeUuid), userId, packageName);
        }
    }

    private String getSdkDataMirrorRootPath(int userId, boolean isCeData) {
        if (isCeData) {
            return String.format("/data_mirror/misc_ce/null/%d/sdksandbox", userId);
        } else {
            return String.format("/data_mirror/misc_de/null/%d/sdksandbox", userId);
        }
    }

    private String getSdkDataMirrorPackagePath(int userId, String packageName, boolean isCeData) {
        return String.format("%s/%s", getSdkDataMirrorRootPath(userId, isCeData), packageName);
    }

    private String getDataDirectory(@Nullable String volumeUuid) {
        if (volumeUuid == null) {
            return "/data";
        } else {
            return "/mnt/expand/" + volumeUuid;
        }
    }

    private String getSdkDataRootPath(int userId, boolean isCeData) {
        return getSdkDataRootPath(/*volumeUuid=*/ null, userId, isCeData);
    }

    private String getSdkDataRootPath(@Nullable String volumeUuid, int userId, boolean isCeData) {
        return String.format(
                "%s/%s/%d/%s",
                getDataDirectory(volumeUuid),
                (isCeData ? "misc_ce" : "misc_de"),
                userId,
                "sdksandbox");
    }

    private String getSdkDataPackagePath(int userId, String packageName, boolean isCeData) {
        return getSdkDataPackagePath(/*volumeUuid=*/ null, userId, packageName, isCeData);
    }

    private String getSdkDataPackagePath(
            @Nullable String volumeUuid, int userId, String packageName, boolean isCeData) {
        return String.format(
                "%s/%s", getSdkDataRootPath(volumeUuid, userId, isCeData), packageName);
    }

    private String getSdkDataPerSdkPath(
            int userId, String packageName, String sdkName, boolean isCeData) throws Exception {
        return getSdkDataPerSdkPath(/*volumeUuid=*/ null, userId, packageName, sdkName, isCeData);
    }

    @Nullable
    private String getSdkDataInternalPath(
            int userId, String packageName, String internalDirName, boolean isCeData)
            throws Exception {
        return getSdkDataInternalPath(
                /*volumeUuid=*/ null, userId, packageName, internalDirName, isCeData);
    }

    // Internal sub-directory can have random suffix. So we need to iterate over the app-level
    // directory to find it.
    @Nullable
    private String getSdkDataInternalPath(
            @Nullable String volumeUuid,
            int userId,
            String packageName,
            String internalDirName,
            boolean isCeData)
            throws Exception {
        final String appLevelPath =
                getSdkDataPackagePath(volumeUuid, userId, packageName, isCeData);
        if (internalDirName.equals(SHARED_DIR)) {
            return Paths.get(appLevelPath, SHARED_DIR).toString();
        }

        final String[] children = getDevice().getChildren(appLevelPath);
        String result = null;
        for (String child : children) {
            if (!child.contains("#")) continue;
            String[] tokens = child.split("#");
            if (tokens.length != 2) {
                continue;
            }
            String dirNameFound = tokens[0];
            if (internalDirName.equals(dirNameFound)) {
                if (result == null) {
                    result = Paths.get(appLevelPath, child).toString();
                } else {
                    throw new IllegalStateException(
                            "Found two internal directory with same name: " + internalDirName);
                }
            }
        }
        return result;
    }

    // Per-Sdk directory has random suffix. So we need to iterate over the app-level directory
    // to find it.
    @Nullable
    private String getSdkDataPerSdkPath(
            @Nullable String volumeUuid,
            int userId,
            String packageName,
            String sdkName,
            boolean isCeData)
            throws Exception {
        final String appLevelPath =
                getSdkDataPackagePath(volumeUuid, userId, packageName, isCeData);
        final String[] children = getDevice().getChildren(appLevelPath);
        String result = null;
        for (String child : children) {
            if (!child.contains("@")) continue;
            String[] tokens = child.split("@");
            if (tokens.length != 2) {
                continue;
            }
            String sdkNameFound = tokens[0];
            if (sdkName.equals(sdkNameFound)) {
                if (result == null) {
                    result = appLevelPath + "/" + child;
                } else {
                    throw new IllegalStateException("Found two per-sdk directory for " + sdkName);
                }
            }
        }
        return result;
    }

    private List<String> getSubDirs(String path, boolean includeRandomSuffix)
            throws Exception {
        final String[] children = getDevice().getChildren(path);
        if (children == null) {
            return Collections.emptyList();
        }
        if (includeRandomSuffix) {
            return new ArrayList<>(Arrays.asList(children));
        }
        final List<String> result = new ArrayList();
        for (int i = 0; i < children.length; i++) {
            String[] tokens;
            if (children[i].contains("@")) {
                tokens = children[i].split("@");
            } else {
                tokens = children[i].split("#");
            }
            result.add(tokens[0]);
        }
        return result;
    }

    private void assertSelinuxLabel(@Nullable String path, String label) throws Exception {
        assertThat(path).isNotNull();
        final String output = getDevice().executeShellCommand("ls -ldZ " + path);
        assertThat(output).contains("u:object_r:" + label);
    }

    private static void assertSuccess(String str) {
        if (str == null || !str.startsWith("Success")) {
            throw new AssertionError("Expected success string but found " + str);
        }
    }

    private void assertDirectoryExists(String path) throws Exception {
        assertWithMessage(path + " is not a directory or does not exist")
                .that(getDevice().isDirectory(path))
                .isTrue();
    }

    private void assertDirectoryDoesNotExist(String path) throws Exception {
        assertWithMessage(path + " exists when expected not to")
                .that(getDevice().doesFileExist(path))
                .isFalse();
    }

    private void waitForSdkDirectoryCreatedForUser(int userId) throws Exception {
        final String sharedDir =
                getSdkDataInternalPath(userId, TEST_APP_STORAGE_PACKAGE, SHARED_DIR, true);
        waitForDirectoryCreated(sharedDir);
    }

    private void waitForDirectoryCreated(String path) throws Exception {
        AwaitUtils.waitFor(() -> getDevice().isDirectory(path), path + " wasn't created");
    }

    private static class DeviceLockUtils {

        private final BaseHostJUnit4Test mTest;

        DeviceLockUtils(BaseHostJUnit4Test test) {
            mTest = test;
        }

        public void rebootToLockedDevice() throws Exception {
            // Setup screenlock
            mTest.getDevice().executeShellCommand("locksettings set-disabled false");
            String response = mTest.getDevice().executeShellCommand("locksettings set-pin 1234");
            if (!response.contains("1234")) {
                // This seems to fail occasionally. Try again once, then give up.
                Thread.sleep(500);
                response = mTest.getDevice().executeShellCommand("locksettings set-pin 1234");
                assertWithMessage("Test requires setting a pin, which failed: " + response)
                        .that(response)
                        .contains("1234");
            }

            // Give enough time for vold to update keys
            Thread.sleep(15000);

            // Follow DirectBootHostTest, reboot system into known state with keys ejected
            mTest.getDevice().rebootUntilOnline();
            waitForBootCompleted(mTest.getDevice());
        }

        public void clearScreenLock() throws Exception {
            Thread.sleep(5000);
            try {
                unlockDevice();
                mTest.getDevice().executeShellCommand("locksettings clear --old 1234");
                mTest.getDevice().executeShellCommand("locksettings set-disabled true");
            } finally {
                // Get ourselves back into a known-good state
                mTest.getDevice().rebootUntilOnline();
                mTest.getDevice().waitForDeviceAvailable();
            }
        }

        public void unlockDevice() throws Exception {
            try {
                mTest.runDeviceTests(
                        TEST_APP_STORAGE_PACKAGE,
                        TEST_APP_STORAGE_PACKAGE + ".SdkSandboxStorageTestApp",
                        "unlockDevice");
            } catch (Exception ignore) {
            }
        }
    }
}
