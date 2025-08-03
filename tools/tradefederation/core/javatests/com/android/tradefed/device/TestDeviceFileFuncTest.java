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
package com.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.IDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Functional tests for the {@link ITestDevice} file management APIs */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TestDeviceFileFuncTest implements IDeviceTest {
    private String mInternalStorage;
    private String mExternalStorage;
    private TestDevice mTestDevice;

    @Before
    public void setUp() throws Exception {
        // Ensure at set-up that the device is available.
        mTestDevice.waitForDeviceAvailable();
        mExternalStorage = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mInternalStorage = "/data/local/tmp";
    }

    /** Basic push-pull consistency test */
    @Test
    public void testPushPull_Basic() throws Exception {
        String deviceFilePath = null;
        File returnedFile = null;
        try {
            File testFile = WifiHelper.extractWifiUtilApk();
            deviceFilePath = String.format("%s/%s", mInternalStorage, "testPushPull_Basic.txt");
            mTestDevice.pushFile(testFile, deviceFilePath);
            returnedFile = mTestDevice.pullFile(deviceFilePath);

            assertNotNull(returnedFile);

            assertEquals(computeChecksum(testFile), computeChecksum(returnedFile));
        } finally {
            if (deviceFilePath != null) {
                mTestDevice.deleteFile(deviceFilePath);
            }
            if (returnedFile != null) {
                returnedFile.delete();
            }
        }
    }
    /** Push-pull consistency test, but with external storage */
    @Test
    public void testPushPull_ExtStorage() throws Exception {
        String deviceFilePath = null;
        File returnedFile = null;
        try {
            File testFile = WifiHelper.extractWifiUtilApk();
            deviceFilePath =
                    String.format("%s/%s", mExternalStorage, "tmp_testPushPull_ExtStorage.txt");
            mTestDevice.pushFile(testFile, deviceFilePath);
            returnedFile = mTestDevice.pullFile(deviceFilePath);

            assertNotNull(returnedFile);

            assertEquals(computeChecksum(testFile), computeChecksum(returnedFile));
        } finally {
            if (deviceFilePath != null) {
                mTestDevice.deleteFile(deviceFilePath);
            }
            if (returnedFile != null) {
                returnedFile.delete();
            }
        }
    }
    /** Ensures the string method is also push-pull consistent */
    @Test
    public void testPushPull_FromString() throws Exception {
        String deviceFilePath = null;
        File returnedFile = null;
        try {
            String testString = generateRandomString(128 * 1024); // 128 kilobyte-ish string;
            deviceFilePath =
                    String.format("%s/%s", mInternalStorage, "tmp_testPushPull_String.txt");
            mTestDevice.pushString(testString, deviceFilePath);
            returnedFile = mTestDevice.pullFile(deviceFilePath);

            assertNotNull(returnedFile);

            assertEquals(computeChecksum(testString), computeChecksum(returnedFile));
        } finally {
            if (deviceFilePath != null) {
                mTestDevice.deleteFile(deviceFilePath);
            }
            if (returnedFile != null) {
                returnedFile.delete();
            }
        }
    }

    /** Ensures the pull contents method is also push-pull consistent */
    @Test
    public void testPushPull_PullContents() throws Exception {
        String deviceFilePath = null;
        try {
            String testString = generateRandomString(128 * 1024); // 128 kilobyte-ish string;
            deviceFilePath =
                    String.format("%s/%s", mInternalStorage, "tmp_testPushPull_String.txt");
            mTestDevice.pushString(testString, deviceFilePath);
            String returnedContents = mTestDevice.pullFileContents(deviceFilePath);

            assertNotNull(returnedContents);
            assertEquals(computeChecksum(testString), computeChecksum(returnedContents));
        } finally {
            if (deviceFilePath != null) {
                mTestDevice.deleteFile(deviceFilePath);
            }
        }
    }

    /** Tests when trying to pull a file that does not exist */
    @Test
    public void testPull_NoExist() throws Exception {
        String deviceFilePath = String.format("%s/%s", mInternalStorage, "thisfiledoesntexist");
        assertFalse(
                String.format("%s exists", deviceFilePath),
                mTestDevice.doesFileExist(deviceFilePath));
        assertNull(mTestDevice.pullFile(deviceFilePath));
    }

    /** Tests when trying to pull a file that does not exist */
    @Test
    public void testPull_NoExistExtStorage() throws Exception {
        String deviceFilePath = String.format("%s/%s", mExternalStorage, "thisfiledoesntexist");
        assertFalse(
                String.format("%s exists", deviceFilePath),
                mTestDevice.doesFileExist(deviceFilePath));
        assertNull(mTestDevice.pullFile(deviceFilePath));
    }

    /** Tests when trying to pull a file that we lack permission to */
    @Test
    public void testPull_NoPermissions() throws Exception {
        String filePath = "/data/system/packages.xml";
        mTestDevice.disableAdbRoot();
        File returned = mTestDevice.pullFile(filePath);
        assertNull(returned);
    }

    /** Tests pushing a non-existent file */
    @Test
    public void testPush_NoExist() throws Exception {
        final String filename = "this_file_does_not_exist.txt";
        String remotePath = null;
        boolean needDelete = false;
        try {
            remotePath = String.format("%s/%s", mInternalStorage, filename);
            boolean didPushFile = mTestDevice.pushFile(new File(filename), remotePath);
            needDelete = didPushFile;
            assertFalse(didPushFile);
        } finally {
            if (needDelete) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }

    /** Tests push-pull consistency for directories */
    @Test
    public void testPushPullDir_Basic() throws Exception {
        final int NUM_FILES = 10; // 10 files per level
        final int FILE_SIZE = 16 * 1024; // 16 KB files
        final String TEST_FILE_PREFIX = "test_file";
        final String TEST_LABEL = "testPushPullDir_Basic";
        File localParentDir = null;
        String remotePath = null;
        try {
            // Set up directories for comparison
            localParentDir = FileUtil.createTempDir(TEST_LABEL);
            File localDir = FileUtil.createTempDir("local", localParentDir);
            File pulledDir = FileUtil.createTempDir("pulled", localParentDir);

            // populate the local one with some test files
            for (int i = 0; i < NUM_FILES; i++) {
                File tmp =
                        FileUtil.createTempFile(
                                String.format("%s_%s", TEST_FILE_PREFIX, i), "", localDir);
                FileUtil.writeToFile(generateRandomString(FILE_SIZE), tmp);
            }

            remotePath = String.format("%s/%s", mInternalStorage, TEST_LABEL);
            CommandResult res =
                    mTestDevice.executeShellV2Command(String.format("mkdir -p \"%s\"", remotePath));
            assertEquals(CommandStatus.SUCCESS, res.getStatus());

            boolean didPushSucceed = mTestDevice.pushDir(localDir, remotePath);
            assertTrue(didPushSucceed);

            boolean didPullSucceed = mTestDevice.pullDir(remotePath, pulledDir);
            assertTrue(didPullSucceed);

            // contains a bunch of equality assertions in the helper method
            compareFiles(localDir, pulledDir);
        } finally {
            if (localParentDir != null && localParentDir.exists()) {
                FileUtil.recursiveDelete(localParentDir);
            }
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }

    /** Tests push-pull consistency for directories in external storage */
    @Test
    public void testPushPullDir_ExtStorage() throws Exception {
        final int NUM_FILES = 10; // 10 files per level
        final int FILE_SIZE = 16 * 1024; // 16 KB files
        final String TEST_FILE_PREFIX = "test_file";
        final String TEST_LABEL = "testPushPullDir_Basic";
        File localParentDir = null;
        String remotePath = null;
        try {
            // Set up directories for comparison
            localParentDir = FileUtil.createTempDir(TEST_LABEL);
            File localDir = FileUtil.createTempDir("local", localParentDir);
            File pulledDir = FileUtil.createTempDir("pulled", localParentDir);

            // populate the local one with some test files
            for (int i = 0; i < NUM_FILES; i++) {
                File tmp =
                        FileUtil.createTempFile(
                                String.format("%s_%s", TEST_FILE_PREFIX, i), "", localDir);
                FileUtil.writeToFile(generateRandomString(FILE_SIZE), tmp);
            }

            remotePath = String.format("%s/%s", mExternalStorage, TEST_LABEL);
            CommandResult res =
                    mTestDevice.executeShellV2Command(String.format("mkdir -p \"%s\"", remotePath));
            assertEquals(CommandStatus.SUCCESS, res.getStatus());

            boolean didPushSucceed = mTestDevice.pushDir(localDir, remotePath);
            assertTrue(didPushSucceed);

            boolean didPullSucceed = mTestDevice.pullDir(remotePath, pulledDir);
            assertTrue(didPullSucceed);

            // contains a bunch of equality assertions in the helper method
            compareFiles(localDir, pulledDir);
        } finally {
            if (localParentDir != null && localParentDir.exists()) {
                FileUtil.recursiveDelete(localParentDir);
            }
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }
    /** Tests the doesFileExist method with by pushing a file */
    @Test
    public void testDoesFileExist_Basic() throws Exception {
        String remotePath = null;
        final String filename = "testDoesFileExist";
        try {
            remotePath = String.format("%s/%s", mInternalStorage, filename);

            mTestDevice.pushString(generateRandomString(10 * 1024), remotePath);

            boolean doesFileExist = mTestDevice.doesFileExist(remotePath);

            assertTrue(doesFileExist);
        } finally {
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }
    /** Tests deleting a file, via push, delete, then doesFileExist */
    @Test
    public void testDeleteFile_Basic() throws Exception {
        String remotePath = null;
        final String testTag = "testDeleteFile_basic";
        final String filename = String.format("%s%s", testTag, ".txt");
        try {
            remotePath = String.format("%s/%s", mInternalStorage, filename);

            mTestDevice.pushString(generateRandomString(10 * 1024), remotePath);

            assertTrue(mTestDevice.doesFileExist(remotePath));

            mTestDevice.deleteFile(remotePath);

            assertFalse(mTestDevice.doesFileExist(remotePath));

            remotePath = null;
        } finally {
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }
    /** Tests deleting a file on external storage by same way as the basic */
    @Test
    public void testDeleteFile_ExtStorage() throws Exception {
        String remotePath = null;
        final String testTag = "testDeleteFile_extStorage";
        final String filename = String.format("%s%s", testTag, ".txt");
        try {
            remotePath = String.format("%s/%s", mExternalStorage, filename);

            mTestDevice.pushString(generateRandomString(10 * 1024), remotePath);

            assertTrue(mTestDevice.doesFileExist(remotePath));

            mTestDevice.deleteFile(remotePath);

            assertFalse(mTestDevice.doesFileExist(remotePath));

            remotePath = null;
        } finally {
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }
    /** Tests isDirectory method by using a known existent directory */
    @Test
    public void testIsDirectory() throws Exception {
        final int NUM_FILES = 10; // 10 files per level
        final int FILE_SIZE = 16 * 1024; // 16 KB files
        final String TEST_FILE_PREFIX = "test_file";
        final String TEST_LABEL = "testIsDirectory";
        File localParentDir = null;
        String remotePath = null;
        try {
            // Set up directories for comparison
            localParentDir = FileUtil.createTempDir(TEST_LABEL);
            File localDir = FileUtil.createTempDir("local", localParentDir);

            // populate the local one with some test files
            for (int i = 0; i < NUM_FILES; i++) {
                File tmp =
                        FileUtil.createTempFile(
                                String.format("%s_%s", TEST_FILE_PREFIX, i), "", localDir);
                FileUtil.writeToFile(generateRandomString(FILE_SIZE), tmp);
            }

            remotePath = String.format("%s/%s", mInternalStorage, TEST_LABEL);
            CommandResult res =
                    mTestDevice.executeShellV2Command(String.format("mkdir -p \"%s\"", remotePath));
            assertEquals(CommandStatus.SUCCESS, res.getStatus());

            boolean didPushSucceed = mTestDevice.pushDir(localDir, remotePath);
            assertTrue(didPushSucceed);

            assertTrue(mTestDevice.isDirectory(remotePath));
        } finally {
            if (localParentDir != null && localParentDir.exists()) {
                FileUtil.recursiveDelete(localParentDir);
            }
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }
    /** Tests getChildren method by pushing a directory then listing its children */
    @Test
    public void testGetChildren_Basic() throws Exception {
        final int NUM_FILES = 10; // 10 files per level
        final int FILE_SIZE = 16 * 1024; // 16 KB files
        final String TEST_FILE_PREFIX = "test_file";
        final String TEST_LABEL = "testGetChildren_Basic";
        File localParentDir = null;
        String remotePath = null;
        try {
            // Set up directories for comparison
            localParentDir = FileUtil.createTempDir(TEST_LABEL);
            File localDir = FileUtil.createTempDir("local", localParentDir);

            // populate the local one with some test files
            for (int i = 0; i < NUM_FILES; i++) {
                File tmp =
                        FileUtil.createTempFile(
                                String.format("%s_%s", TEST_FILE_PREFIX, i), "", localDir);
                FileUtil.writeToFile(generateRandomString(FILE_SIZE), tmp);
            }

            remotePath = String.format("%s/%s", mInternalStorage, TEST_LABEL);
            CommandResult res =
                    mTestDevice.executeShellV2Command(String.format("mkdir -p \"%s\"", remotePath));
            assertEquals(CommandStatus.SUCCESS, res.getStatus());

            boolean didPushSucceed = mTestDevice.pushDir(localDir, remotePath);
            assertTrue(didPushSucceed);

            assertTrue(mTestDevice.isDirectory(remotePath));

            String[] pulledChildren = mTestDevice.getChildren(remotePath);

            List<String> pulledChildrenList =
                    Arrays.asList(pulledChildren).stream().sorted().collect(Collectors.toList());
            List<String> localChildrenList =
                    Arrays.asList(localDir.list()).stream().sorted().collect(Collectors.toList());

            assertEquals(localChildrenList.size(), pulledChildrenList.size());

            for (int i = 0; i < localChildrenList.size(); i++) {
                assertEquals(localChildrenList.get(i), pulledChildrenList.get(i));
            }

        } finally {
            if (localParentDir != null && localParentDir.exists()) {
                FileUtil.recursiveDelete(localParentDir);
            }
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }
    /** Tests getChildren on a file */
    @Test
    public void testGetChildren_NotDirectory() throws Exception {
        final String TEST_LABEL = "testGetChildren_NotDirectory";
        String remotePath = null;
        try {
            remotePath = String.format("%s/%s", mInternalStorage, TEST_LABEL);
            boolean didPushSucceed =
                    mTestDevice.pushString(generateRandomString(10 * 1024), remotePath);
            assertTrue(didPushSucceed);

            assertFalse(mTestDevice.isDirectory(remotePath));

            String[] pulledChildren = mTestDevice.getChildren(remotePath);

            // This is really weird behavior, but is what the harness does right now.
            // The contract wasn't specific on that method, so for now this test will at least
            // enforce consistency
            assertEquals(1, pulledChildren.length);
            assertEquals(remotePath, pulledChildren[0]);
        } finally {
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }
    /** Tests getChildren on an empty directory */
    @Test
    public void testGetChildren_EmptyDirectory() throws Exception {
        final String TEST_LABEL = "testGetChildren_EmptyDir";
        String remotePath = null;
        try {
            remotePath = String.format("%s/%s", mInternalStorage, TEST_LABEL);
            CommandResult res =
                    mTestDevice.executeShellV2Command(String.format("mkdir -p \"%s\"", remotePath));
            assertEquals(CommandStatus.SUCCESS, res.getStatus());

            assertTrue(mTestDevice.isDirectory(remotePath));

            String[] pulledChildren = mTestDevice.getChildren(remotePath);
            assertEquals(0, pulledChildren.length);
        } finally {
            if (remotePath != null) {
                mTestDevice.deleteFile(remotePath);
            }
        }
    }

    private static String computeChecksum(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return StreamUtil.calculateMd5(in);
        }
    }

    private static String computeChecksum(String str) throws IOException {
        try (InputStream in = new ByteArrayInputStream(str.getBytes("UTF-8"))) {
            return StreamUtil.calculateMd5(in);
        }
    }

    private static String generateRandomString(int length) {
        int unicodeLower = 32;
        int unicodeUpper = 126;
        Random gen = new Random();

        return gen.ints(unicodeLower, unicodeUpper + 1)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /**
     * Performs a recursive pair-wise comparison of two file/directories
     *
     * <p>First this ensures the files have the same name (not path) and type (directory or file).
     * Then this checks that the contents are the same. If they are directories then that means
     * listing out their children, sorting them by name, ensuring the same number of elements, then
     * recursing on them pairwise. If the two files are in fact regular files, to compare contents
     * we just compute the checksum and compare those.
     */
    private static void compareFiles(File firstFile, File secondFile) throws Exception {
        compareFiles(firstFile, secondFile, false);
        return;
    }

    private static void compareFiles(File firstFile, File secondFile, boolean checkName)
            throws Exception {
        assertEquals(firstFile.isDirectory(), secondFile.isDirectory());
        if (checkName) {
            assertEquals(firstFile.getName(), secondFile.getName());
        }

        if (firstFile.isDirectory()) {
            List<Path> firstFileList;
            try (Stream<Path> stream = Files.list(firstFile.toPath())) {
                firstFileList = stream.sorted().collect(Collectors.toList());
            }
            List<Path> secondFileList;
            try (Stream<Path> stream = Files.list(secondFile.toPath())) {
                secondFileList = stream.sorted().collect(Collectors.toList());
            }
            assertEquals(firstFileList.size(), secondFileList.size());

            for (int i = 0; i < firstFileList.size(); i++) {
                compareFiles(firstFileList.get(i).toFile(), secondFileList.get(i).toFile(), true);
            }
        } else {
            assertEquals(computeChecksum(firstFile), computeChecksum(secondFile));
        }
        return;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = (TestDevice) device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
