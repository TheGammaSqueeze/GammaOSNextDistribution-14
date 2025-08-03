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
package com.android.tradefed.cluster;

import static org.junit.Assert.assertFalse;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.FuseUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.ZipUtil2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Unit tests for {@link ClusterBuildProvider}. */
@RunWith(JUnit4.class)
public class ClusterBuildProviderTest {

    private static final String RESOURCE_KEY = "resource_key_1";
    private static final String EXTRA_RESOURCE_KEY = "resource_key_2";
    private static final String ZIP_KEY = "resource_key_3.zip";
    private static final String TAR_GZIP_KEY = "resource_key_4.tar.gz";
    private static final String[] FILE_NAMES_IN_ARCHIVE = {"resource_1.txt", "resource_2.txt"};

    private File mRootDir;
    private File mResourceFile;
    private File mTarGzipResourceFile;
    private String mResourceUrl;
    private Map<String, File> mDownloadCache;
    private Map<String, File> mCreatedResources;
    private TestResourceDownloader mSpyDownloader;
    private FuseUtil mMockFuseUtil;

    @Before
    public void setUp() throws IOException {
        mRootDir = FileUtil.createTempDir("ClusterBuildProvider");
        File zipDir = FileUtil.createTempDir("ClusterBuildProviderZip");
        try {
            List<File> filesInZip = new ArrayList<>();
            for (String name : FILE_NAMES_IN_ARCHIVE) {
                File file = new File(zipDir, name);
                file.createNewFile();
                filesInZip.add(file);
            }
            mResourceFile = ZipUtil.createZip(filesInZip);
        } finally {
            FileUtil.recursiveDelete(zipDir);
        }
        mTarGzipResourceFile = null;
        mResourceUrl = mResourceFile.toURI().toURL().toString();
        mDownloadCache = ClusterBuildProvider.sDownloadCache.get();
        mCreatedResources = ClusterBuildProvider.sCreatedResources.get();
        mSpyDownloader = Mockito.spy(new TestResourceDownloader());
        mMockFuseUtil = Mockito.mock(FuseUtil.class);
        Mockito.when(mMockFuseUtil.canMountZip()).thenReturn(false);
    }

    @After
    public void tearDown() {
        if (mDownloadCache != null) {
            mDownloadCache.clear();
        }
        if (mCreatedResources != null) {
            mCreatedResources.clear();
        }
        FileUtil.deleteFile(mResourceFile);
        FileUtil.deleteFile(mTarGzipResourceFile);
        FileUtil.recursiveDelete(mRootDir);
    }

    private ClusterBuildProvider createClusterBuildProvider() {
        ClusterBuildProvider provider =
                new ClusterBuildProvider() {
                    @Override
                    TestResourceDownloader createTestResourceDownloader() {
                        return mSpyDownloader;
                    }

                    @Override
                    FuseUtil getFuseUtil() {
                        return mMockFuseUtil;
                    }
                };
        provider.setRootDir(mRootDir);
        return provider;
    }

    /** Create a temporary tar.gz containing empty files. */
    private static File createTarGzipResource(String... fileNames) throws IOException {
        boolean success = false;
        final File file = FileUtil.createTempFile("ClusterBuildProvider", ".tar.gz");
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out = new GZIPOutputStream(out);
            out = new TarArchiveOutputStream(out);
            TarArchiveOutputStream tar = (TarArchiveOutputStream) out;
            for (String name : fileNames) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(name);
                tar.putArchiveEntry(tarEntry);
                tar.closeArchiveEntry();
            }
            tar.finish();
            success = true;
        } finally {
            StreamUtil.close(out);
            if (!success) {
                FileUtil.deleteFile(file);
            }
        }
        return file;
    }

    private List<File> setUpMockFuseUtil() {
        List<File> mountDirs = new ArrayList<File>();
        Mockito.when(mMockFuseUtil.canMountZip()).thenReturn(true);
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                File zipFile = (File) invocation.getArgument(0);
                                File mountDir = (File) invocation.getArgument(1);
                                mountDirs.add(mountDir);
                                try (ZipFile zip = new ZipFile(zipFile)) {
                                    ZipUtil2.extractZip(zip, mountDir);
                                }
                                return null;
                            }
                        })
                .when(mMockFuseUtil)
                .mountZip(Mockito.any(File.class), Mockito.any(File.class));
        return mountDirs;
    }

    private void verifyDownloadedResource() throws IOException {
        File file = new File(mRootDir, RESOURCE_KEY);
        Assert.assertTrue(file.isFile());
        Assert.assertEquals(file, mDownloadCache.get(mResourceUrl));
        Mockito.verify(mSpyDownloader).download(Mockito.any(), Mockito.eq(file));

        if (mTarGzipResourceFile != null) {
            file = new File(mRootDir, TAR_GZIP_KEY);
            Assert.assertTrue(file.isFile());
            Assert.assertEquals(
                    file, mDownloadCache.get(mTarGzipResourceFile.toURI().toURL().toString()));
            Mockito.verify(mSpyDownloader).download(Mockito.any(), Mockito.eq(file));
        }
    }

    /** Test one provider with two identical URLs. */
    @Test
    public void testGetBuild_multipleTestResources()
            throws BuildRetrievalError, IOException, JSONException {
        ClusterBuildProvider provider = createClusterBuildProvider();
        provider.addTestResource(new TestResource(RESOURCE_KEY, mResourceUrl));
        provider.addTestResource(new TestResource(ZIP_KEY, mResourceUrl));
        provider.getBuild();

        verifyDownloadedResource();
        Assert.assertEquals(1, mDownloadCache.size());
        Assert.assertEquals(2, mCreatedResources.size());
        File zipFile = new File(mRootDir, ZIP_KEY);
        Assert.assertTrue(zipFile.isFile());
        for (String name : FILE_NAMES_IN_ARCHIVE) {
            Assert.assertTrue(new File(mRootDir, name).isFile());
        }
        Mockito.verify(mSpyDownloader, Mockito.never())
                .download(Mockito.any(), Mockito.eq(zipFile));
    }

    /** Test one provider with different decompress directories. */
    @Test
    public void testGetBuild_decompressTestResources()
            throws BuildRetrievalError, IOException, JSONException {
        ClusterBuildProvider provider = createClusterBuildProvider();
        provider.addTestResource(
                new TestResource(RESOURCE_KEY, mResourceUrl, true, "dir1", false, null));
        provider.addTestResource(
                new TestResource(ZIP_KEY, mResourceUrl, true, "dir2", false, null));
        mTarGzipResourceFile = createTarGzipResource(FILE_NAMES_IN_ARCHIVE);
        provider.addTestResource(
                new TestResource(
                        TAR_GZIP_KEY,
                        mTarGzipResourceFile.toURI().toURL().toString(),
                        true,
                        "dir3",
                        false,
                        null));

        provider.getBuild();

        verifyDownloadedResource();
        Assert.assertEquals(2, mDownloadCache.size());
        Assert.assertEquals(3, mCreatedResources.size());
        File zipFile = new File(mRootDir, ZIP_KEY);
        Assert.assertTrue(zipFile.isFile());
        for (String name : FILE_NAMES_IN_ARCHIVE) {
            assertFalse(FileUtil.getFileForPath(mRootDir, name).isFile());
            Assert.assertTrue(FileUtil.getFileForPath(mRootDir, "dir1", name).isFile());
            Assert.assertTrue(FileUtil.getFileForPath(mRootDir, "dir2", name).isFile());
            Assert.assertTrue(FileUtil.getFileForPath(mRootDir, "dir3", name).isFile());
        }
        Mockito.verify(mSpyDownloader, Mockito.never())
                .download(Mockito.any(), Mockito.eq(zipFile));
    }

    /** Test one provider with different decompress directories when zip mount is supported. */
    @Test
    public void testGetBuild_decompressTestResources_withMountZip()
            throws BuildRetrievalError, IOException, JSONException {
        List<File> mountDirs = setUpMockFuseUtil();
        try {
            final String url = mResourceFile.toURI().toURL().toString();
            ClusterBuildProvider provider = createClusterBuildProvider();
            provider.addTestResource(new TestResource(RESOURCE_KEY, url, true, null, false, null));
            provider.addTestResource(new TestResource(ZIP_KEY, url, true, "dir", true, null));

            provider.getBuild();

            verifyDownloadedResource();
            Assert.assertEquals(1, mDownloadCache.size());
            Assert.assertEquals(2, mCreatedResources.size());
            File file = new File(mRootDir, ZIP_KEY);
            Assert.assertTrue(file.isFile());
            for (String name : FILE_NAMES_IN_ARCHIVE) {
                Assert.assertTrue(FileUtil.getFileForPath(mRootDir, name).isFile());
                Assert.assertTrue(FileUtil.getFileForPath(mRootDir, "dir", name).isFile());
            }
            Mockito.verify(mSpyDownloader, Mockito.never())
                    .download(Mockito.any(), Mockito.eq(file));
            Mockito.verify(mMockFuseUtil, Mockito.never())
                    .mountZip(
                            Mockito.eq(new File(mRootDir, RESOURCE_KEY)), Mockito.any(File.class));
            Mockito.verify(mMockFuseUtil).mountZip(Mockito.eq(file), Mockito.any(File.class));
        } finally {
            for (File dir : mountDirs) {
                FileUtil.recursiveDelete(dir);
            }
        }
    }

    /** Test decompressing specific files from resources when zip mount is supported. */
    @Test
    public void testGetBuild_decompressTestResources_withFileNamesAndMountZip()
            throws BuildRetrievalError, IOException, JSONException {
        List<File> mountDirs = setUpMockFuseUtil();
        try {
            final List<String> decompressFileNames = Arrays.asList(FILE_NAMES_IN_ARCHIVE[0]);
            ClusterBuildProvider provider = createClusterBuildProvider();
            provider.addTestResource(
                    new TestResource(
                            RESOURCE_KEY, mResourceUrl, true, "dir1", false, decompressFileNames));
            provider.addTestResource(
                    new TestResource(
                            ZIP_KEY, mResourceUrl, true, "dir2", true, decompressFileNames));
            mTarGzipResourceFile = createTarGzipResource(FILE_NAMES_IN_ARCHIVE);
            provider.addTestResource(
                    new TestResource(
                            TAR_GZIP_KEY,
                            mTarGzipResourceFile.toURI().toURL().toString(),
                            true,
                            "dir3",
                            false,
                            decompressFileNames));

            provider.getBuild();

            for (String name : FILE_NAMES_IN_ARCHIVE) {
                boolean shouldExist = decompressFileNames.contains(name);
                Assert.assertEquals(
                        shouldExist, FileUtil.getFileForPath(mRootDir, "dir1", name).isFile());
                Assert.assertEquals(
                        shouldExist, FileUtil.getFileForPath(mRootDir, "dir2", name).isFile());
                Assert.assertEquals(
                        shouldExist, FileUtil.getFileForPath(mRootDir, "dir3", name).isFile());
            }
            Mockito.verify(mMockFuseUtil, Mockito.never())
                    .mountZip(
                            Mockito.eq(new File(mRootDir, RESOURCE_KEY)), Mockito.any(File.class));
            Mockito.verify(mMockFuseUtil)
                    .mountZip(Mockito.eq(new File(mRootDir, ZIP_KEY)), Mockito.any(File.class));
        } finally {
            for (File dir : mountDirs) {
                FileUtil.recursiveDelete(dir);
            }
        }
    }

    /** Test decompressing resource to a directory outside of working directory. */
    @Test
    public void testGetBuild_invalidDecompressDirectory() throws JSONException {
        ClusterBuildProvider provider = createClusterBuildProvider();
        provider.addTestResource(
                new TestResource(RESOURCE_KEY, mResourceUrl, true, "../out", false, null));
        try {
            provider.getBuild();
            Assert.fail("Expect getBuild to throw an exception.");
        } catch (BuildRetrievalError e) {
            Assert.assertTrue(e.getMessage().contains("outside of working directory"));
        }
    }

    /** Test decompressing resource files outside of working directory. */
    @Test
    public void testGetBuild_invalidDecompressFiles() throws JSONException {
        ClusterBuildProvider provider = createClusterBuildProvider();
        provider.addTestResource(
                new TestResource(
                        RESOURCE_KEY, mResourceUrl, true, "dir", false, Arrays.asList("../out")));
        try {
            provider.getBuild();
            Assert.fail("Expect getBuild to throw an exception.");
        } catch (BuildRetrievalError e) {
            Assert.assertTrue(e.getMessage().contains("outside of working directory"));
        }
    }

    /** Test two providers downloading from the same URL. */
    @Test
    public void testGetBuild_multipleBuildProviders()
            throws BuildRetrievalError, IOException, JSONException {
        ClusterBuildProvider provider1 = createClusterBuildProvider();
        provider1.addTestResource(
                new TestResource(RESOURCE_KEY, mResourceUrl, false, null, false, null));
        ClusterBuildProvider provider2 = createClusterBuildProvider();
        provider2.addTestResource(
                new TestResource(RESOURCE_KEY, mResourceUrl, false, null, false, null));
        provider1.getBuild();
        provider2.getBuild();

        verifyDownloadedResource();
        Assert.assertEquals(1, mDownloadCache.size());
        Assert.assertEquals(1, mCreatedResources.size());
    }

    /** Test {@link ClusterBuildProvider#getBuild()} in an invocation thread. */
    private void testGetBuild(File extraResourceFile)
            throws BuildRetrievalError, IOException, JSONException {
        ClusterBuildProvider provider = createClusterBuildProvider();
        provider.addTestResource(new TestResource(RESOURCE_KEY, mResourceUrl));
        provider.addTestResource(
                new TestResource(EXTRA_RESOURCE_KEY, extraResourceFile.toURI().toURL().toString()));
        provider.getBuild();

        verifyDownloadedResource();
        Assert.assertEquals(2, mDownloadCache.size());
        Assert.assertEquals(2, mCreatedResources.size());
        File file = new File(mRootDir, EXTRA_RESOURCE_KEY);
        Assert.assertTrue(file.isFile());
        Mockito.verify(mSpyDownloader).download(Mockito.any(), Mockito.eq(file));
    }

    /** Test two invocation threads downloading twice from the same URL. */
    @Test
    public void testGetBuild_multipleInvocations()
            throws BuildRetrievalError, IOException, InterruptedException, JSONException {
        File sharedResourceFile = FileUtil.createTempFile("SharedTestResource", ".txt");
        ClusterBuildProviderTest anotherTest = new ClusterBuildProviderTest();
        try {
            ArrayList<Throwable> threadExceptions = new ArrayList<>();
            Runnable runnable =
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                anotherTest.setUp();
                                anotherTest.testGetBuild(sharedResourceFile);
                            } catch (Throwable e) {
                                threadExceptions.add(e);
                            }
                        }
                    };

            ThreadGroup anotherGroup = new ThreadGroup("unit test");
            anotherGroup.setDaemon(true);
            Thread anotherThread =
                    new Thread(anotherGroup, runnable, "ClusterBuildProviderTestThread");
            // Terminate the thread when the main thread throws an exception and exits.
            anotherThread.setDaemon(true);
            anotherThread.start();
            testGetBuild(sharedResourceFile);
            anotherThread.join();
            if (threadExceptions.size() > 0) {
                throw new AssertionError(
                        anotherThread.getName() + " failed.", threadExceptions.get(0));
            }
        } finally {
            FileUtil.deleteFile(sharedResourceFile);
            anotherTest.tearDown();
        }
    }

    @Test
    public void testCleanUp() throws IOException {
        List<File> zipMounts = new ArrayList<>();
        try {
            ClusterBuildInfo buildInfo = new ClusterBuildInfo(mRootDir, "buildId", "buildName");
            for (int i = 0; i < 10; i++) {
                File dir = FileUtil.createTempDir("ClusterBuildProvider");
                buildInfo.addZipMount(dir);
                zipMounts.add(dir);
            }

            ClusterBuildProvider provider = createClusterBuildProvider();
            provider.cleanUp(buildInfo);

            for (File dir : zipMounts) {
                Mockito.verify(mMockFuseUtil).unmountZip(dir);
                assertFalse(dir.exists());
            }
        } finally {
            for (File dir : zipMounts) {
                FileUtil.recursiveDelete(dir);
            }
        }
    }
}
