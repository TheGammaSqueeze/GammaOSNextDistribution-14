/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.util;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.LogDataType;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Test class for {@link TarUtil}. */
@RunWith(JUnit4.class)
public class TarUtilTest {

    // This archive contains TEST.log and TEST2.log.
    private static final String TAR_GZ_RESOURCE_PATH = "/testdata/LOG.tar.gz";
    // This archive contains TEST.log and TEST2.log.
    private static final String TGZ_RESOURCE_PATH = "/testdata/LOG.tgz";
    // This archive contains TEST.log and LINK.log.
    private static final String SYMLINK_TAR_PATH = "/testdata/symlink.tar";
    private static final String TAR_ENTRY_NAME = "TEST.log";
    private static final String SYMLINK_ENTRY_NAME = "LINK.log";
    private File mWorkDir;

    @Before
    public void setUp() throws Exception {
        mWorkDir = FileUtil.createTempDir("tarutil_unit_test_dir");
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mWorkDir);
    }

    /** Test that {@link TarUtil#isGzip(File)} determines the file type. */
    @Test
    public void testIsGzip() throws IOException {
        InputStream logTarGz = getClass().getResourceAsStream(TAR_GZ_RESOURCE_PATH);
        File tmpFile = FileUtil.createTempFile("log_tarutil_test", ".tar.gz");
        try {
            FileUtil.writeToFile(logTarGz, tmpFile);
            assertTrue(TarUtil.isGzip(tmpFile));

            FileUtil.writeToFile("test", tmpFile);
            assertFalse(TarUtil.isGzip(tmpFile));

            FileUtil.writeToFile("", tmpFile);
            assertFalse(TarUtil.isGzip(tmpFile));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /** Test that {TarUtil#unGzip(File, File)} can ungzip properly a tar.gz file. */
    @Test
    public void testUnGzip_withTargzFormat() throws Exception {
        InputStream logTarGz = getClass().getResourceAsStream(TAR_GZ_RESOURCE_PATH);
        File logTarGzFile = FileUtil.createTempFile("log_tarutil_test", ".tar.gz");
        try {
            FileUtil.writeToFile(logTarGz, logTarGzFile);
            File testFile = TarUtil.unGzip(logTarGzFile, mWorkDir);
            Assert.assertTrue(testFile.exists());
            // Expect same name without the .gz extension.
            String expectedName =
                    logTarGzFile.getName().substring(0, logTarGzFile.getName().length() - 3);
            Assert.assertEquals(expectedName, testFile.getName());
        } finally {
            FileUtil.deleteFile(logTarGzFile);
        }
    }

    /** Test that {TarUtil#unGzip(File, File)} can ungzip properly a tgz file. */
    @Test
    public void testUnGzip_withTgzFormat() throws Exception {
        InputStream logTgz = getClass().getResourceAsStream(TGZ_RESOURCE_PATH);
        File logTgzFile = FileUtil.createTempFile("log_tarutil_test", ".tgz");
        try {
            FileUtil.writeToFile(logTgz, logTgzFile);
            File testFile = TarUtil.unGzip(logTgzFile, mWorkDir);
            Assert.assertTrue(testFile.exists());
            // Expect same name without the .gz extension.
            String expectedName = logTgzFile.getName().replace(".tgz", ".tar");
            Assert.assertEquals(expectedName, testFile.getName());
        } finally {
            FileUtil.deleteFile(logTgzFile);
        }
    }

    /** Test that {TarUtil#unTar(File, File)} can untar properly a tar file. */
    @Test
    public void testUntar() throws Exception {
        InputStream logTarGz = getClass().getResourceAsStream(TAR_GZ_RESOURCE_PATH);
        File logTarGzFile = FileUtil.createTempFile("log_tarutil_test", ".tar.gz");
        try {
            FileUtil.writeToFile(logTarGz, logTarGzFile);
            File testFile = TarUtil.unGzip(logTarGzFile, mWorkDir);
            Assert.assertTrue(testFile.exists());
            List<File> untaredList = TarUtil.unTar(testFile, mWorkDir);
            Assert.assertEquals(2, untaredList.size());
        } finally {
            FileUtil.deleteFile(logTarGzFile);
        }
    }

    /** Test that {TarUtil#unTar(File, File)} can untar a symbolic link from a tar file. */
    @Test
    public void testUnTar_symlink() throws IOException {
        InputStream tarStream = getClass().getResourceAsStream(SYMLINK_TAR_PATH);
        File tarFile = FileUtil.createTempFile("symlink", ".tar");
        try {
            FileUtil.writeToFile(tarStream, tarFile);
            List<File> untaredList = TarUtil.unTar(tarFile, mWorkDir);
            Assert.assertEquals(2, untaredList.size());
            Assert.assertTrue(new File(mWorkDir, TAR_ENTRY_NAME).isFile());
            File linkFile = new File(mWorkDir, SYMLINK_ENTRY_NAME);
            Assert.assertTrue(linkFile.exists());
            Assert.assertTrue(Files.isSymbolicLink(linkFile.toPath()));
        } finally {
            FileUtil.deleteFile(tarFile);
        }
    }

    /** Test that {TarUtil#unTar(File, File, Collection<String>)} can untar properly a tar file. */
    @Test
    public void testUnTar_withFileNames() throws IOException {
        InputStream logTarGz = getClass().getResourceAsStream(TAR_GZ_RESOURCE_PATH);
        File logTarGzFile = FileUtil.createTempFile("log_tarutil_test", ".tar.gz");
        try {
            FileUtil.writeToFile(logTarGz, logTarGzFile);
            File testFile = TarUtil.unGzip(logTarGzFile, mWorkDir);
            Assert.assertTrue(testFile.exists());
            List<File> untaredList =
                    TarUtil.unTar(testFile, mWorkDir, Arrays.asList(TAR_ENTRY_NAME));
            Assert.assertEquals(Arrays.asList(new File(mWorkDir, TAR_ENTRY_NAME)), untaredList);
        } finally {
            FileUtil.deleteFile(logTarGzFile);
        }
    }

    /** Test that {TarUtil#unTar(File, File, Collection<String>)} can untar properly a tar file. */
    @Test
    public void testUnTar_withFileNamesNotFound() throws IOException {
        InputStream logTarGz = getClass().getResourceAsStream(TAR_GZ_RESOURCE_PATH);
        File logTarGzFile = FileUtil.createTempFile("log_tarutil_test", ".tar.gz");
        try {
            FileUtil.writeToFile(logTarGz, logTarGzFile);
            File testFile = TarUtil.unGzip(logTarGzFile, mWorkDir);
            Assert.assertTrue(testFile.exists());
            TarUtil.unTar(testFile, mWorkDir, Arrays.asList("NOT_EXIST"));
            Assert.fail("Expect unTar to throw an exception.");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().endsWith("NOT_EXIST"));
        } finally {
            FileUtil.deleteFile(logTarGzFile);
        }
    }

    /**
     * Test that {TarUtil#extractTarGzipToTemp(File, String)} can extract properly a tar.gz file.
     */
    @Test
    public void testExtractTarGzipToTemp() throws Exception {
        InputStream logTarGz = getClass().getResourceAsStream(TAR_GZ_RESOURCE_PATH);
        File tarGzFile = FileUtil.createTempFile("extract_tar_gz_test", ".tar.gz");
        File tempDir = null;
        try {
            FileUtil.writeToFile(logTarGz, tarGzFile);
            tempDir = TarUtil.extractTarGzipToTemp(tarGzFile, "extract_tar_gz_test");
            Assert.assertEquals(2, tempDir.list().length);
        } finally {
            FileUtil.recursiveDelete(tempDir);
            FileUtil.deleteFile(tarGzFile);
        }
    }

    /**
     * Test that {TarUtil#extractAndLog(ITestLogger, File, String)} can untar properly a tar file
     * and export its content.
     */
    @Test
    public void testExtractAndLog() throws Exception {
        final String baseName = "BASE_NAME";
        InputStream logTarGz = getClass().getResourceAsStream(TAR_GZ_RESOURCE_PATH);
        File logTarGzFile = FileUtil.createTempFile("log_tarutil_test", ".tar.gz");
        try {
            FileUtil.writeToFile(logTarGz, logTarGzFile);
            ITestLogger listener = mock(ITestLogger.class);
            // Main tar file is logged under the base name directly

            // Contents is log under baseName_filename

            TarUtil.extractAndLog(listener, logTarGzFile, baseName);

            verify(listener).testLog(eq(baseName), eq(LogDataType.TAR_GZ), any());
            verify(listener).testLog(eq(baseName + "_TEST.log"), eq(LogDataType.TEXT), any());
            verify(listener).testLog(eq(baseName + "_TEST2.log"), eq(LogDataType.TEXT), any());
        } finally {
            FileUtil.deleteFile(logTarGzFile);
        }
    }

    /**
     * Test that {@link TarUtil#gzip(File)} is properly zipping the file and can be unzipped to
     * recover the original file.
     */
    @Test
    public void testGzipDir_unGzip() throws Exception {
        final String content = "I'LL BE ZIPPED";
        File tmpFile = FileUtil.createTempFile("base_file", ".txt", mWorkDir);
        File zipped = null;
        File unzipped = FileUtil.createTempDir("unzip-test-dir", mWorkDir);
        try {
            FileUtil.writeToFile(content, tmpFile);
            zipped = TarUtil.gzip(tmpFile);
            assertTrue(zipped.exists());
            assertTrue(zipped.getName().endsWith(".gz"));
            // unzip the file to ensure our utility can go both way
            TarUtil.unGzip(zipped, unzipped);
            assertEquals(1, unzipped.list().length);
            // the original file is found
            assertEquals(content, FileUtil.readStringFromFile(unzipped.listFiles()[0]));
        } finally {
            FileUtil.recursiveDelete(zipped);
            FileUtil.recursiveDelete(unzipped);
        }
    }

    /** Test to ensure that {@link TarUtil#gzip(File)} properly throws if the file is not valid. */
    @Test
    public void testGzip_invalidFile() throws Exception {
        try {
            TarUtil.gzip(new File("i_do_not_exist"));
            fail("Should have thrown an exception.");
        } catch (FileNotFoundException expected) {
            // expected
        }
    }
}
