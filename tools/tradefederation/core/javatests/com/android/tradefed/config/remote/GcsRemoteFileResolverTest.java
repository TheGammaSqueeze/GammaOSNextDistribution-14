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
package com.android.tradefed.config.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.gcs.GCSDownloaderHelper;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.config.remote.IRemoteFileResolver.RemoteFileResolverArgs;
import com.android.tradefed.config.remote.IRemoteFileResolver.ResolvedFile;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link GcsRemoteFileResolver}. */
@RunWith(JUnit4.class)
public class GcsRemoteFileResolverTest {

    private GcsRemoteFileResolver mResolver;
    private GCSDownloaderHelper mMockHelper;

    @Before
    public void setUp() {
        mMockHelper = Mockito.mock(GCSDownloaderHelper.class);
        mResolver =
                new GcsRemoteFileResolver() {
                    @Override
                    protected GCSDownloaderHelper getDownloader() {
                        return mMockHelper;
                    }

                    @Override
                    void sleep() {
                        // Skip sleeping in tests
                    }
                };
    }

    @Test
    public void testResolve() throws Exception {
        RemoteFileResolverArgs args = new RemoteFileResolverArgs();
        args.setConsideredFile(new File("gs:/fake/file"));
        ResolvedFile resFile = mResolver.resolveRemoteFile(args);
        assertTrue(resFile.getResolvedFile() instanceof ExtendedFile);
        ((ExtendedFile) resFile.getResolvedFile()).waitForDownload();

        Mockito.verify(mMockHelper, times(1))
                .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
    }

    @Test
    public void testResolveWithTimeout() throws Exception {
        // First attempt fails
        Mockito.doThrow(
                        new BuildRetrievalError(
                                "download failure", InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR))
                .doNothing() // Second attempt is good
                .when(mMockHelper)
                .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));

        RemoteFileResolverArgs args = new RemoteFileResolverArgs();
        args.setConsideredFile(new File("gs:/fake/file"));
        Map<String, String> queryArgs = new HashMap<>();
        queryArgs.put("retry_timeout_ms", "5000");
        args.addQueryArgs(queryArgs);
        ResolvedFile resFile = mResolver.resolveRemoteFile(args);
        assertTrue(resFile.getResolvedFile() instanceof ExtendedFile);
        ((ExtendedFile) resFile.getResolvedFile()).waitForDownload();

        Mockito.verify(mMockHelper, times(2))
                .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
    }

    @Test
    public void testResolve_error() throws Exception {
        Mockito.doThrow(
                        new BuildRetrievalError(
                                "download failure", InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR))
                .when(mMockHelper)
                .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
        try {
            RemoteFileResolverArgs args = new RemoteFileResolverArgs();
            args.setConsideredFile(new File("gs:/fake/file"));
            ResolvedFile resFile = mResolver.resolveRemoteFile(args);
            assertTrue(resFile.getResolvedFile() instanceof ExtendedFile);
            ((ExtendedFile) resFile.getResolvedFile()).waitForDownload();
            fail("Should have thrown an exception.");
        } catch (BuildRetrievalError expected) {
            assertEquals("download failure", expected.getMessage());
        }

        Mockito.verify(mMockHelper).fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
    }

    /** Test that we can request a zip to be unzipped automatically. */
    @Test
    public void testResolve_unzip() throws Exception {
        File testDir = FileUtil.createTempDir("test-resolve-dir");
        File zipFile = ZipUtil.createZip(testDir);
        File file = null;
        try {
            doAnswer(
                            invocation -> {
                                File destFile = (File) invocation.getArgument(0);
                                FileUtil.hardlinkFile(zipFile, destFile);
                                FileUtil.deleteFile(zipFile);
                                return null;
                            })
                    .when(mMockHelper)
                    .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
            Map<String, String> query = new HashMap<>();
            query.put(DynamicRemoteFileResolver.UNZIP_KEY, /* Case doesn't matter */ "TrUe");
            query.put(
                    DynamicRemoteFileResolver.OPTION_PARALLEL_KEY, /* Case doesn't matter */
                    "false");
            RemoteFileResolverArgs args = new RemoteFileResolverArgs();
            args.setConsideredFile(new File("gs:/fake/file")).addQueryArgs(query);
            ResolvedFile resolvedFile = mResolver.resolveRemoteFile(args);
            file = resolvedFile.getResolvedFile();
            if (file instanceof ExtendedFile) {
                ((ExtendedFile) file).waitForDownload();
            }

            // File was unzipped
            assertTrue(file.isDirectory());
            // Zip file was cleaned
            assertFalse(zipFile.exists());

            Mockito.verify(mMockHelper, times(1))
                    .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
        } finally {
            FileUtil.recursiveDelete(testDir);
            FileUtil.deleteFile(zipFile);
            FileUtil.recursiveDelete(file);
        }
    }

    /** Test that if we request to unzip a non-zip file it throws an exception. */
    @Test
    public void testResolve_notZip() throws Exception {
        File testFile = FileUtil.createTempFile("test-resolve-file", ".txt");
        try {
            doAnswer(
                            invocation -> {
                                File destFile = (File) invocation.getArgument(0);
                                FileUtil.hardlinkFile(testFile, destFile);
                                FileUtil.deleteFile(testFile);
                                return null;
                            })
                    .when(mMockHelper)
                    .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
            Map<String, String> query = new HashMap<>();
            query.put(DynamicRemoteFileResolver.UNZIP_KEY, /* Case doesn't matter */ "TrUe");
            query.put(
                    DynamicRemoteFileResolver.OPTION_PARALLEL_KEY, /* Case doesn't matter */
                    "false");
            RemoteFileResolverArgs args = new RemoteFileResolverArgs();
            args.setConsideredFile(new File("gs:/fake/file")).addQueryArgs(query);
            mResolver.resolveRemoteFile(args);
            fail("Should have thrown an exception");
        } catch (BuildRetrievalError expected) {
            // Expected
            assertTrue(expected.getMessage().contains("not a valid zip."));
        } finally {
            FileUtil.deleteFile(testFile);
        }
        Mockito.verify(mMockHelper, times(1))
                .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
    }

    /** Test that if we request to unzip a non-zip file it throws an exception. */
    @Test
    public void testResolve_notZip_parallel() throws Exception {
        File testFile = FileUtil.createTempFile("test-resolve-file", ".txt");
        FileUtil.writeToFile("some non zip content", testFile);
        File downloadedFile = null;
        try {
            doAnswer(
                            invocation -> {
                                File destFile = (File) invocation.getArgument(0);
                                FileUtil.hardlinkFile(testFile, destFile);
                                FileUtil.deleteFile(testFile);
                                return null;
                            })
                    .when(mMockHelper)
                    .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
            Map<String, String> query = new HashMap<>();
            query.put(DynamicRemoteFileResolver.UNZIP_KEY, /* Case doesn't matter */ "TrUe");
            query.put(
                    DynamicRemoteFileResolver.OPTION_PARALLEL_KEY, /* Case doesn't matter */
                    "true");
            RemoteFileResolverArgs args = new RemoteFileResolverArgs();
            args.setConsideredFile(new File("gs:/fake/file")).addQueryArgs(query);
            ResolvedFile resolvedFile = mResolver.resolveRemoteFile(args);
            downloadedFile = resolvedFile.getResolvedFile();
            if (downloadedFile instanceof ExtendedFile) {
                ((ExtendedFile) downloadedFile).waitForDownload();
            }
            fail("Should have thrown an exception");
        } catch (BuildRetrievalError expected) {
            // Expected
            assertTrue(expected.getMessage().contains("zip"));
        } finally {
            FileUtil.deleteFile(testFile);
            FileUtil.recursiveDelete(downloadedFile);
        }
        Mockito.verify(mMockHelper, times(1))
                .fetchTestResource(Mockito.any(), Mockito.eq("gs:/fake/file"));
    }
}
