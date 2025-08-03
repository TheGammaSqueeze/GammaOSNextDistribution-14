/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.dexmetadata;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Verifies that dex metadata files are installed and updated successfully.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class InstallDexMetadataHostTest extends BaseHostJUnit4Test {

    private static final String TEST_PACKAGE = "com.android.cts.dexmetadata";
    private static final String TEST_CLASS = TEST_PACKAGE + ".InstallDexMetadataTest";
    private static final String INSTALL_PACKAGE = "com.android.cts.dexmetadata.splitapp";

    private static final String APK_BASE = "CtsDexMetadataSplitApp.apk";
    private static final String APK_FEATURE_A = "CtsDexMetadataSplitAppFeatureA.apk";
    private static final String APK_BASE_WITH_VDEX = "CtsDexMetadataSplitAppWithVdex.apk";
    private static final String APK_FEATURE_A_WITH_VDEX
            = "CtsDexMetadataSplitAppFeatureAWithVdex.apk";

    private static final String DM_BASE = "CtsDexMetadataSplitApp.dm";
    private static final String DM_S_BASE = "CtsDexMetadataSplitApp-S.dm";
    private static final String DM_FEATURE_A = "CtsDexMetadataSplitAppFeatureA.dm";
    private static final String DM_BASE_WITH_VDEX = "CtsDexMetadataSplitAppWithVdex.dm";
    private static final String DM_FEATURE_A_WITH_VDEX
    = "CtsDexMetadataSplitAppFeatureAWithVdex.dm";

    private File mTmpDir;
    private File mApkBaseFile = null;
    private File mApkFeatureAFile = null;
    private File mApkBaseFileWithVdex = null;
    private File mApkFeatureAFileWithVdex = null;
    private File mDmBaseFile = null;
    private File mDmBaseFileForS = null;
    private File mDmFeatureAFile = null;
    private File mDmBaseFileWithVdex = null;
    private File mDmFeatureAFileWithVdex = null;
    private boolean mShouldRunTests;

    /**
     * Setup the test.
     */
    @Before
    public void setUp() throws Exception {
        ITestDevice device = getDevice();
        device.uninstallPackage(INSTALL_PACKAGE);
        mShouldRunTests = ApiLevelUtil.isAtLeast(getDevice(), 28)
                || ApiLevelUtil.isAtLeast(getDevice(), "P")
                || ApiLevelUtil.codenameEquals(getDevice(), "P");

        Assume.assumeTrue("Skip DexMetadata tests on releases before P.", mShouldRunTests);

        if (mShouldRunTests) {
            mTmpDir = FileUtil.createTempDir("InstallDexMetadataHostTest");
            mApkBaseFile = extractResource(APK_BASE, mTmpDir);
            mApkFeatureAFile = extractResource(APK_FEATURE_A, mTmpDir);
            mApkBaseFileWithVdex = extractResource(APK_BASE_WITH_VDEX, mTmpDir);
            mApkFeatureAFileWithVdex = extractResource(APK_FEATURE_A_WITH_VDEX, mTmpDir);
            mDmBaseFile = extractResource(DM_BASE, mTmpDir);
            mDmBaseFileForS = extractResource(DM_S_BASE, mTmpDir);
            mDmFeatureAFile = extractResource(DM_FEATURE_A, mTmpDir);
            mDmBaseFileWithVdex = extractResource(DM_BASE_WITH_VDEX, mTmpDir);
            mDmFeatureAFileWithVdex = extractResource(DM_FEATURE_A_WITH_VDEX, mTmpDir);
        }
    }

    /**
     * Tear down the test.
     */
    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(INSTALL_PACKAGE);
        FileUtil.recursiveDelete(mTmpDir);
    }

    /**
     * Verify .dm installation for stand-alone base (no splits)
     */
    @Test
    public void testInstallDmForBase() throws Exception {
        new InstallMultiple().addApk(mApkBaseFile).addDm(mDmBaseFile).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBase"));
    }

    /**
     * Verify .dm installation for base and splits
     */
    @Test
    public void testInstallDmForBaseAndSplit() throws Exception {
        new InstallMultiple()
                .addApk(mApkBaseFile)
                .addDm(mDmBaseFile)
                .addApk(mApkFeatureAFile)
                .addDm(mDmFeatureAFile)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseAndSplit"));
    }

    /**
     * Verify .dm installation for base but not for splits.
     */
    @Test
    public void testInstallDmForBaseButNoSplit() throws Exception {
        new InstallMultiple()
                .addApk(mApkBaseFile)
                .addDm(mDmBaseFile)
                .addApk(mApkFeatureAFile)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseButNoSplit"));
    }

    /**
     * Verify .dm installation for splits but not for base.
     */
    @Test
    public void testInstallDmForSplitButNoBase() throws Exception {
        new InstallMultiple()
                .addApk(mApkBaseFile)
                .addApk(mApkFeatureAFile)
                .addDm(mDmFeatureAFile)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForSplitButNoBase"));
    }

    /**
     * Verify that updating .dm files works as expected.
     */
    @Test
    public void testUpdateDm() throws Exception {
        new InstallMultiple()
                .addApk(mApkBaseFile)
                .addDm(mDmBaseFile)
                .addApk(mApkFeatureAFile)
                .addDm(mDmFeatureAFile)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseAndSplit"));

        // Remove .dm files during update.
        new InstallMultiple().addArg("-r").addApk(mApkBaseFile)
                .addApk(mApkFeatureAFile).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testNoDm"));

        // Add only a split .dm file during update.
        new InstallMultiple()
                .addArg("-r")
                .addApk(mApkBaseFile)
                .addApk(mApkFeatureAFile)
                .addDm(mDmFeatureAFile)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForSplitButNoBase"));
    }

    /**
     * Verify .dm installation for base but not for splits and with a .dm name
     * that doesn't match the apk name.
     */
    @Test
    public void testInstallDmForBaseButNoSplitWithNoMatchingDm() throws Exception {
        String nonMatchingDmName = mDmFeatureAFile.getName().replace(".dm", ".not.there.dm");
        new InstallMultiple()
                .addApk(mApkBaseFile)
                .addDm(mDmBaseFile)
                .addApk(mApkFeatureAFile)
                .addDm(mDmFeatureAFile, nonMatchingDmName)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseButNoSplit"));
    }

    static class ProfileReaderV10 {
        byte[] data;

        ProfileReaderV10(byte[] bytes) throws Exception {
            ByteBuffer bb = ByteBuffer.wrap(bytes);

            // Read header.
            bb.order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0x006f7270 /* LE "pro\0" */, bb.getInt());
            assertEquals(0x00303130 /* LE "010\0" */, bb.getInt());
            bb.get(); // Skip dex file count.
            int uncompressed_size = bb.getInt();
            int compressed_size = bb.getInt();

            // Decompress profile.
            Inflater inflater = new Inflater();
            inflater.setInput(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
            data = new byte[uncompressed_size];
            assertEquals(uncompressed_size, inflater.inflate(data));
        }
    }

    static class ProfileReaderV15 {
        byte[] dexFilesData;
        byte[] extraDescriptorsData;
        byte[] classesData;
        byte[] methodsData;

        ProfileReaderV15(byte[] bytes) throws Exception {
            ByteBuffer bb = ByteBuffer.wrap(bytes);

            // Read header.
            bb.order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0x006f7270 /* LE "pro\0" */, bb.getInt());
            assertEquals(0x00353130 /* LE "015\0" */, bb.getInt());
            int section_count = bb.getInt();
            assertFalse(section_count == 0);

            // Mandatory dex files section.
            assertEquals(/*kDexFiles*/ 0, bb.getInt());
            dexFilesData = readSection(bb);

            // Read optional sections. Assume no more than one occurrence of each known section.
            for (int i = 1; i != section_count; ++i) {
                int sectionType = bb.getInt();
                switch (sectionType) {
                    case 1:  // kExtraDescriptors
                        assertTrue(extraDescriptorsData == null);
                        extraDescriptorsData = readSection(bb);
                        break;
                    case 2:  // kClasses
                        assertTrue(classesData == null);
                        classesData = readSection(bb);
                        break;
                    case 3:  // kMethods
                        assertTrue(methodsData == null);
                        methodsData = readSection(bb);
                        break;
                    default:
                        // Unknown section. Skip it. New versions of ART are allowed
                        // to add sections that shall be ignored by old versions.
                        skipSection(bb);
                        break;
                }
            }
        }

        private byte[] readSection(ByteBuffer bb) throws Exception {
            int fileOffset = bb.getInt();
            int fileSize = bb.getInt();
            int inflatedSize = bb.getInt();
            if (inflatedSize != 0) {
                // Decompress section.
                byte[] data = new byte[inflatedSize];
                Inflater inflater = new Inflater();
                inflater.setInput(bb.array(), fileOffset, fileSize);
                assertEquals(inflatedSize, inflater.inflate(data));
                return data;
            } else {
                // Copy uncompressed data.
                byte[] data = new byte[fileSize];
                System.arraycopy(bb.array(), fileOffset, data, 0, fileSize);
                return data;
            }
        }

        private void skipSection(ByteBuffer bb) {
            bb.getInt();  // fileOffset
            bb.getInt();  // fileSize
            bb.getInt();  // inflatedSize
        }
    }

    // This test is questionable because it assumes that ART can understand the format of the
    // profiles in the DM file passed by this test.
    //
    // As ART is updatable, this assumption can be broken by a future change.
    //
    // TODO(jiakaiz): Re-evaluate the necessity of having this test in CTS. Maybe move it to MTS.
    @Test
    public void testProfileSnapshotAfterInstall() throws Exception {
        // Determine which profile to use.
        boolean useProfileForS = ApiLevelUtil.isAtLeast(getDevice(), "S");

        // Install the app.
        File dmBaseFile = useProfileForS ? mDmBaseFileForS : mDmBaseFile;
        String dmName = mDmBaseFile.getName();  // APK name with ".apk" replaced by ".dm".
        new InstallMultiple().addApk(mApkBaseFile).addDm(dmBaseFile, dmName).run();

        // Take a snapshot of the installed profile.
        String snapshotCmd = "cmd package snapshot-profile " + INSTALL_PACKAGE;
        CommandResult result = getDevice().executeShellV2Command(snapshotCmd);
        assertEquals(result.getStdout().trim() /* message */, 0L, (long) result.getExitCode());

        // Extract the profile bytes from the dex metadata and from the profile snapshot.
        byte[] rawDeviceProfile = extractProfileSnapshotFromDevice();
        byte[] rawMetadataProfile = extractProfileFromDexMetadata(dmBaseFile);
        if (useProfileForS) {
            ProfileReaderV15 snapshotReader = new ProfileReaderV15(rawDeviceProfile);
            ProfileReaderV15 expectedReader = new ProfileReaderV15(rawMetadataProfile);

            assertArrayEquals(expectedReader.dexFilesData, snapshotReader.dexFilesData);
            assertArrayEquals(
                    expectedReader.extraDescriptorsData, snapshotReader.extraDescriptorsData);
            assertArrayEquals(expectedReader.classesData, snapshotReader.classesData);
            assertArrayEquals(expectedReader.methodsData, snapshotReader.methodsData);
        } else {
            byte[] snapshotProfileBytes = new ProfileReaderV10(rawDeviceProfile).data;
            byte[] expectedProfileBytes = new ProfileReaderV10(rawMetadataProfile).data;

            assertArrayEquals(expectedProfileBytes, snapshotProfileBytes);
        }
    }

    /**
     * Verify .dm installation for stand-alone base (no splits) with vdex file.
     */
    @Test
    public void testInstallDmForBaseWithVdex() throws Exception {
        new InstallMultiple().addApk(mApkBaseFileWithVdex).addDm(mDmBaseFileWithVdex).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBase"));
    }

    /**
     * Verify .dm installation for base and splits with vdex files.
     */
    @Test
    public void testInstallDmForBaseAndSplitWithVdex() throws Exception {
        new InstallMultiple()
                .addApk(mApkBaseFileWithVdex)
                .addDm(mDmBaseFileWithVdex)
                .addApk(mApkFeatureAFileWithVdex)
                .addDm(mDmFeatureAFileWithVdex)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseAndSplit"));
    }

    /** Verify .dm installation for split-only install. */
    @Test
    public void testInstallDmForSplitOnlyInstall() throws Exception {
        new InstallMultiple().addApk(mApkBaseFile).addDm(mDmBaseFile).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        new InstallMultiple()
                .inheritFrom(TEST_PACKAGE)
                .addApk(mApkFeatureAFile)
                .addDm(mDmFeatureAFile)
                .runExpectingFailure();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));
    }

    /** Extracts the profile bytes for the snapshot captured with 'cmd package snapshot-profile' */
    private byte[] extractProfileSnapshotFromDevice() throws Exception {
        File snapshotFile = File.createTempFile(INSTALL_PACKAGE, "primary.prof");
        snapshotFile.deleteOnExit();
        getDevice().pullFile(getSnapshotLocation(INSTALL_PACKAGE), snapshotFile);
        return Files.readAllBytes(snapshotFile.toPath());
    }

    static private String getSnapshotLocation(String pkg) {
        return "/data/misc/profman/" + pkg + ".prof";
    }

    /** Extracts the profile bytes from the dex metadata profile. */
    static private byte[] extractProfileFromDexMetadata(File dmFile) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(dmFile))) {
            for (ZipEntry ze; (ze = in.getNextEntry()) != null; ) {
                if (!"primary.prof".equals(ze.getName())) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();

                final byte[] buffer = new byte[128];
                for (int count; (count = in.read(buffer)) != -1; ) {
                    bos.write(buffer, 0, count);
                }
                return bos.toByteArray();
            }
        }
        throw new IllegalArgumentException("primary.prof not found in the .dm file");
    }

    /**
     * Extract a resource into the given directory and return a reference to its file.
     */
    private File extractResource(String fullResourceName, File outputDir)
            throws Exception {
        File outputFile = new File(outputDir, fullResourceName);
        try (InputStream in = getClass().getResourceAsStream("/" + fullResourceName);
                OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + fullResourceName);
            }
            byte[] buf = new byte[65536];
            int chunkSize;
            while ((chunkSize = in.read(buf)) != -1) {
                out.write(buf, 0, chunkSize);
            }
        }
        return outputFile;
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        InstallMultiple() {
            super(getDevice(), getBuild());
        }
    }
}
