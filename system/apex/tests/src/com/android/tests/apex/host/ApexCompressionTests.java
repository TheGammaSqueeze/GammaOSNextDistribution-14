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

package com.android.tests.apex.host;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;
import android.platform.test.annotations.LargeTest;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Test for platform support for Apex Compression feature
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ApexCompressionTests extends BaseHostJUnit4Test {
    private static final String COMPRESSED_APEX_PACKAGE_NAME = "com.android.apex.compressed";
    private static final String ORIGINAL_APEX_FILE_NAME =
            COMPRESSED_APEX_PACKAGE_NAME + ".v1_original.apex";
    private static final String DECOMPRESSED_DIR_PATH = "/data/apex/decompressed/";
    private static final String APEX_ACTIVE_DIR = "/data/apex/active/";
    private static final String OTA_RESERVED_DIR = "/data/apex/ota_reserved/";
    private static final String DECOMPRESSED_APEX_SUFFIX = ".decompressed.apex";

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);
    private boolean mWasAdbRoot = false;

    @Before
    public void setUp() throws Exception {
        mWasAdbRoot = getDevice().isAdbRoot();
        if (!mWasAdbRoot) {
            assumeTrue("Requires root", getDevice().enableAdbRoot());
        }
        deleteFiles("/system/apex/" + COMPRESSED_APEX_PACKAGE_NAME + "*apex",
                APEX_ACTIVE_DIR + COMPRESSED_APEX_PACKAGE_NAME + "*apex",
                DECOMPRESSED_DIR_PATH + COMPRESSED_APEX_PACKAGE_NAME + "*apex",
                OTA_RESERVED_DIR + "*");
    }

    @After
    public void tearDown() throws Exception {
        if (!mWasAdbRoot) {
            getDevice().disableAdbRoot();
        }
        deleteFiles("/system/apex/" + COMPRESSED_APEX_PACKAGE_NAME + "*apex",
                APEX_ACTIVE_DIR + COMPRESSED_APEX_PACKAGE_NAME + "*apex",
                DECOMPRESSED_DIR_PATH + COMPRESSED_APEX_PACKAGE_NAME + "*apex",
                OTA_RESERVED_DIR + "*");
    }

    /**
     * Runs the given phase of a test by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("testApkOnlyEnableRollback");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertTrue(runDeviceTests("com.android.tests.apex.compression.app",
                "com.android.tests.apex.app.ApexCompressionTests",
                phase));
    }

    /**
     * Deletes files and reboots the device if necessary.
     * @param files the paths of files which might contain wildcards
     */
    private void deleteFiles(String... files) throws Exception {
        boolean found = false;
        for (String file : files) {
            CommandResult result = getDevice().executeShellV2Command("ls " + file);
            if (result.getStatus() == CommandStatus.SUCCESS) {
                found = true;
                break;
            }
        }

        if (found) {
            getDevice().remountSystemWritable();
            for (String file : files) {
                getDevice().executeShellCommand("rm -rf " + file);
            }
            getDevice().reboot();
        }
    }

    private void pushTestApex(final String fileName) throws Exception {
        final File apex = mHostUtils.getTestFile(fileName);
        getDevice().remountSystemWritable();
        assertTrue(getDevice().pushFile(apex, "/system/apex/" + fileName));
        getDevice().reboot();
    }

    private List<String> getFilesInDir(String baseDir) throws DeviceNotAvailableException {
        return getDevice().getFileEntry(baseDir).getChildren(false)
                .stream().map(entry -> entry.getName())
                .collect(Collectors.toList());
    }

    /**
     * Returns the active apex info as optional.
     */
    private Optional<ITestDevice.ApexInfo> getActiveApexInfo(String packageName)
            throws DeviceNotAvailableException {
        return getDevice().getActiveApexes().stream().filter(
                apex -> apex.name.equals(packageName)).findAny();
    }

    @Test
    @LargeTest
    public void testDecompressedApexIsConsideredFactory() throws Exception {
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");
        runPhase("testDecompressedApexIsConsideredFactory");
    }

    @Test
    @LargeTest
    public void testCompressedApexIsDecompressedAndActivated() throws Exception {
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");

        // Ensure that compressed APEX was decompressed in DECOMPRESSED_DIR_PATH
        List<String> files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);

        // Match the decompressed apex with original byte for byte
        final File originalApex = mHostUtils.getTestFile(ORIGINAL_APEX_FILE_NAME);
        final byte[] originalApexFileBytes = Files.readAllBytes(Paths.get(originalApex.toURI()));
        final File decompressedFile = getDevice().pullFile(
                DECOMPRESSED_DIR_PATH + COMPRESSED_APEX_PACKAGE_NAME + "@1"
                + DECOMPRESSED_APEX_SUFFIX);
        final byte[] decompressedFileBytes =
                Files.readAllBytes(Paths.get(decompressedFile.toURI()));
        assertThat(decompressedFileBytes).isEqualTo(originalApexFileBytes);

        // The decompressed APEX should note be hard linked to APEX_ACTIVE_DIR
        files = getFilesInDir(APEX_ACTIVE_DIR);
        assertThat(files).doesNotContain(
                COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);
    }

    @Test
    @LargeTest
    public void testDecompressedApexSurvivesReboot() throws Exception {
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");

        // Ensure that compressed APEX was activated from DECOMPRESSED_DIR_PATH
        List<String> files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);
        final File decompressedFile = getDevice().pullFile(
                DECOMPRESSED_DIR_PATH + COMPRESSED_APEX_PACKAGE_NAME + "@1"
                + DECOMPRESSED_APEX_SUFFIX);
        final byte[] decompressedFileBytes =
                Files.readAllBytes(Paths.get(decompressedFile.toURI()));

        getDevice().reboot();

        // Ensure it gets activated again on reboot
        files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);
        final File decompressedFileAfterReboot = getDevice().pullFile(
                DECOMPRESSED_DIR_PATH + COMPRESSED_APEX_PACKAGE_NAME + "@1"
                + DECOMPRESSED_APEX_SUFFIX);
        final byte[] decompressedFileBytesAfterReboot =
                Files.readAllBytes(Paths.get(decompressedFileAfterReboot.toURI()));
        assertThat(decompressedFileBytes).isEqualTo(decompressedFileBytesAfterReboot);
    }

    @Test
    @LargeTest
    public void testDecompressionDoesNotHappenOnEveryReboot() throws Exception {
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");

        final String decompressedApexFilePath = DECOMPRESSED_DIR_PATH
                + COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX;
        String lastModifiedTime1 =
                getDevice().executeShellCommand("stat -c %Y " + decompressedApexFilePath);

        getDevice().reboot();
        getDevice().waitForDeviceAvailable();

        String lastModifiedTime2 =
                getDevice().executeShellCommand("stat -c %Y " + decompressedApexFilePath);
        assertThat(lastModifiedTime1).isEqualTo(lastModifiedTime2);
    }

    @Test
    @LargeTest
    public void testHigherVersionOnSystemTriggerDecompression() throws Exception {
        // Install v1 on /system partition
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");
        // On boot, /data partition will have decompressed v1 APEX in it
        List<String> files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);

        // Now replace /system APEX with v2
        getDevice().remountSystemWritable();
        getDevice().executeShellCommand("rm -rf /system/apex/"
                + COMPRESSED_APEX_PACKAGE_NAME + "*apex");
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v2.capex");

        // Ensure that v2 was decompressed
        files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@2" + DECOMPRESSED_APEX_SUFFIX);
    }


    @Test
    @LargeTest
    public void testDifferentRootDigestTriggersDecompression() throws Exception {
        // Install v1 on /system partition
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");
        // On boot, /data partition will have decompressed v1 APEX in it
        List<String> files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);
        final File decompressedFile = getDevice().pullFile(
                DECOMPRESSED_DIR_PATH + COMPRESSED_APEX_PACKAGE_NAME + "@1"
                + DECOMPRESSED_APEX_SUFFIX);
        final byte[] decompressedFileBytes =
                Files.readAllBytes(Paths.get(decompressedFile.toURI()));

        // Now replace /system APEX with same version but different root digest
        getDevice().remountSystemWritable();
        getDevice().executeShellCommand("rm -rf /system/apex/"
                + COMPRESSED_APEX_PACKAGE_NAME + "*apex");
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1_different_digest.capex");

        // Ensure that decompressed APEX is different than before
        files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);
        final File decompressedFileAfterReboot = getDevice().pullFile(
                DECOMPRESSED_DIR_PATH + COMPRESSED_APEX_PACKAGE_NAME + "@1"
                + DECOMPRESSED_APEX_SUFFIX);
        final byte[] decompressedFileBytesAfterReboot =
                Files.readAllBytes(Paths.get(decompressedFileAfterReboot.toURI()));
        assertThat(decompressedFileBytes).isNotEqualTo(decompressedFileBytesAfterReboot);
    }

    @Test
    @LargeTest
    public void testUnusedDecompressedApexIsCleanedUp_HigherVersion() throws Exception {
        // Install v1 on /system partition
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");
        // Ensure that compressed APEX was decompressed in DECOMPRESSED_DIR_PATH
        List<String> files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);

        // Now install an update for that APEX so that decompressed APEX becomes redundant
        runPhase("testUnusedDecompressedApexIsCleanedUp_HigherVersion");
        getDevice().reboot();

        // Verify that the decompressed APEX has been cleaned up
        String filePath = Paths.get(DECOMPRESSED_DIR_PATH,
                COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX).toString();
        mHostUtils.waitForFileDeleted(filePath, Duration.ofSeconds(15));
    }

    @Test
    @LargeTest
    public void testUnusedDecompressedApexIsCleanedUp_SameVersion() throws Exception {
        // Install v1 on /system partition
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");
        // Ensure that compressed APEX was decompressed in DECOMPRESSED_DIR_PATH
        List<String> files = getFilesInDir(DECOMPRESSED_DIR_PATH);
        assertThat(files).contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);

        // Now install an update for that APEX so that decompressed APEX becomes redundant
        runPhase("testUnusedDecompressedApexIsCleanedUp_SameVersion");
        getDevice().reboot();

        // Verify that the decompressed APEX has been cleaned up
        String filePath = Paths.get(DECOMPRESSED_DIR_PATH,
                COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX).toString();
        mHostUtils.waitForFileDeleted(filePath, Duration.ofSeconds(15));
    }

    @Test
    @LargeTest
    public void testReservedSpaceIsNotCleanedOnReboot() throws Exception {
        getDevice().executeShellCommand("touch " + OTA_RESERVED_DIR + "random");

        getDevice().reboot();

        List<String> files = getFilesInDir(OTA_RESERVED_DIR);
        assertThat(files).hasSize(1);
        assertThat(files).contains("random");
    }

    @Test
    @LargeTest
    public void testReservedSpaceIsCleanedUpOnDecompression() throws Exception {
        getDevice().executeShellCommand("touch " + OTA_RESERVED_DIR + "random1");
        getDevice().executeShellCommand("touch " + OTA_RESERVED_DIR + "random2");

        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");

        assertThat(getFilesInDir(OTA_RESERVED_DIR)).isEmpty();
    }

    @Test
    @LargeTest
    public void testFailsToActivateApexOnDataFallbacksToPreInstalled() throws Exception {
        // Push a data apex that will fail to activate
        final File file =
                mHostUtils.getTestFile("com.android.apex.compressed.v2_manifest_mismatch.apex");
        getDevice().pushFile(file, APEX_ACTIVE_DIR + COMPRESSED_APEX_PACKAGE_NAME + "@2.apex");
        // Push a CAPEX which should act as the fallback
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v2.capex");
        assertWithMessage("Timed out waiting for device to boot").that(
                getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();

        // After reboot pre-installed version of shim apex should be activated, and corrupted
        // version on /data should be deleted.
        final ITestDevice.ApexInfo activeApex =
                getActiveApexInfo(COMPRESSED_APEX_PACKAGE_NAME).get();
        assertThat(activeApex.versionCode).isEqualTo(2);
        assertThat(getDevice().doesFileExist(
                DECOMPRESSED_DIR_PATH + COMPRESSED_APEX_PACKAGE_NAME + "@2"
                + DECOMPRESSED_APEX_SUFFIX)).isTrue();
        assertThat(getDevice().doesFileExist(
                APEX_ACTIVE_DIR + COMPRESSED_APEX_PACKAGE_NAME + "@2"
                + DECOMPRESSED_APEX_SUFFIX)).isFalse();
        assertThat(getDevice().doesFileExist(
                APEX_ACTIVE_DIR + COMPRESSED_APEX_PACKAGE_NAME + "@2.apex")).isFalse();
    }

    @Test
    @LargeTest
    public void testCapexToApexSwitch() throws Exception {
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");
        assertThat(getFilesInDir(DECOMPRESSED_DIR_PATH))
            .contains(COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX);

        // Now replace the CAPEX with an uncompressed APEX
        getDevice().remountSystemWritable();
        getDevice().executeShellCommand("rm -rf /system/apex/"
                + COMPRESSED_APEX_PACKAGE_NAME + "*apex");
        pushTestApex(ORIGINAL_APEX_FILE_NAME);
        runPhase("testCapexToApexSwitch");

        // Ensure active apex is running from /system
        final ITestDevice.ApexInfo activeApex = getActiveApexInfo(COMPRESSED_APEX_PACKAGE_NAME)
                .orElseThrow(() -> new AssertionError(
                        "Can't find " + COMPRESSED_APEX_PACKAGE_NAME));
        assertThat(activeApex.sourceDir).startsWith("/system");
        // Ensure previous decompressed APEX has been cleaned up
        String filePath = Paths.get(DECOMPRESSED_DIR_PATH,
                COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX).toString();
        mHostUtils.waitForFileDeleted(filePath, Duration.ofSeconds(15));
    }

    @Test
    @LargeTest
    public void testDecompressedApexVersionAlwaysHasSameVersionAsCapex() throws Exception {
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v2.capex");
        // Now replace /system APEX with v1
        getDevice().remountSystemWritable();
        getDevice().executeShellCommand("rm -rf /system/apex/"
                + COMPRESSED_APEX_PACKAGE_NAME + "*apex");
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");
        runPhase("testDecompressedApexVersionAlwaysHasSameVersionAsCapex");
    }

    @Test
    @LargeTest
    public void testCompressedApexCanBeRolledBack() throws Exception {
        pushTestApex(COMPRESSED_APEX_PACKAGE_NAME + ".v1.capex");

        // Now install update with rollback
        runPhase("testCompressedApexCanBeRolledBack_Commit");
        getDevice().reboot();

        // Rollback the apex
        runPhase("testCompressedApexCanBeRolledBack_Rollback");
        getDevice().reboot();

        runPhase("testCompressedApexCanBeRolledBack_Verify");
    }

    @Test
    @LargeTest
    public void testOrphanedDecompressedApexInActiveDirIsIgnored() throws Exception {
        final File apex = mHostUtils.getTestFile(
                COMPRESSED_APEX_PACKAGE_NAME + ".v1_original.apex");
        // Prepare an APEX in active directory with .decompressed.apex suffix.
        // Place the same apex in system too. When booting, system APEX should
        // be mounted while the decomrpessed APEX in active direcotyr should
        // be ignored.
        getDevice().remountSystemWritable();
        assertTrue(getDevice().pushFile(apex,
                APEX_ACTIVE_DIR + COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX));
        assertTrue(getDevice().pushFile(apex,
                "/system/apex/" + COMPRESSED_APEX_PACKAGE_NAME + ".v1.apex"));
        getDevice().reboot();
        // Ensure active apex is running from /system
        final ITestDevice.ApexInfo activeApex = getActiveApexInfo(COMPRESSED_APEX_PACKAGE_NAME)
                .orElseThrow(() -> new AssertionError(
                        "Can't find " + COMPRESSED_APEX_PACKAGE_NAME));
        assertThat(activeApex.sourceDir).startsWith("/system");
        // Ensure orphaned decompressed APEX has been cleaned up
        String filePath = Paths.get(DECOMPRESSED_DIR_PATH,
                COMPRESSED_APEX_PACKAGE_NAME + "@1" + DECOMPRESSED_APEX_SUFFIX).toString();
        mHostUtils.waitForFileDeleted(filePath, Duration.ofSeconds(15));
    }
}

