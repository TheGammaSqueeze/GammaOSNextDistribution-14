/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.logger.InvocationLocal;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.FuseUtil;
import com.android.tradefed.util.TarUtil;
import com.android.tradefed.util.ZipUtil2;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** A {@link IBuildProvider} to download TFC test resources. */
@OptionClass(alias = "cluster", global_namespace = false)
public class ClusterBuildProvider implements IBuildProvider {

    private static final String DEFAULT_FILE_VERSION = "0";

    @Option(name = "root-dir", description = "A root directory", mandatory = true)
    private File mRootDir;

    @Option(
            name = "test-resource",
            description = "A list of JSON-serialized test resource objects",
            mandatory = true)
    private List<String> mTestResources = new ArrayList<>();

    @Option(name = "build-id", description = "Build ID")
    private String mBuildId = IBuildInfo.UNKNOWN_BUILD_ID;

    @Option(name = "build-target", description = "Build target name")
    private String mBuildTarget = "stub";

    // The keys are the URLs; the values are the downloaded files shared among all build providers
    // in the invocation.
    // TODO(b/139876060): Use dynamic download when it supports caching HTTPS and GCS files.
    @VisibleForTesting
    static final InvocationLocal<ConcurrentHashMap<String, File>> sDownloadCache =
            new InvocationLocal<ConcurrentHashMap<String, File>>() {
                @Override
                protected ConcurrentHashMap<String, File> initialValue() {
                    return new ConcurrentHashMap<String, File>();
                }
            };

    // The keys are the resource names; the values are the files and directories.
    @VisibleForTesting
    static final InvocationLocal<ConcurrentHashMap<String, File>> sCreatedResources =
            new InvocationLocal<ConcurrentHashMap<String, File>>() {
                @Override
                protected ConcurrentHashMap<String, File> initialValue() {
                    return new ConcurrentHashMap<String, File>();
                }
            };

    private List<TestResource> parseTestResources() {
        final List<TestResource> objs = new ArrayList<>();
        for (final String s : mTestResources) {
            try {
                final JSONObject json = new JSONObject(s);
                final TestResource obj = TestResource.fromJson(json);
                objs.add(obj);
            } catch (JSONException e) {
                throw new RuntimeException("Failed to parse a test resource option: " + s, e);
            }
        }
        return objs;
    }

    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        mRootDir.mkdirs();
        final ClusterBuildInfo buildInfo = new ClusterBuildInfo(mRootDir, mBuildId, mBuildTarget);
        final TestResourceDownloader downloader = createTestResourceDownloader();
        final ConcurrentHashMap<String, File> cache = sDownloadCache.get();
        final ConcurrentHashMap<String, File> createdResources = sCreatedResources.get();

        final List<TestResource> testResources = parseTestResources();
        for (TestResource resource : testResources) {
            // For backward compatibility.
            if (resource.getName().endsWith(".zip") && !resource.getDecompress()) {
                resource =
                        new TestResource(
                                resource.getName(),
                                resource.getUrl(),
                                true,
                                new File(resource.getName()).getParent(),
                                resource.mountZip(),
                                resource.getDecompressFiles());
            }
            // Validate the paths before the file operations.
            final File resourceFile = resource.getFile(mRootDir);
            validateTestResourceFile(mRootDir, resourceFile);
            if (resource.getDecompress()) {
                File dir = resource.getDecompressDir(mRootDir);
                validateTestResourceFile(mRootDir, dir);
                for (String name : resource.getDecompressFiles()) {
                    validateTestResourceFile(dir, new File(dir, name));
                }
            }
            // Download and decompress.
            File file;
            try {
                File cachedFile = retrieveFile(resource.getUrl(), cache, downloader, resourceFile);
                file = prepareTestResource(resource, createdResources, cachedFile, buildInfo);
            } catch (UncheckedIOException e) {
                throw new BuildRetrievalError("failed to get test resources", e);
            }
            buildInfo.setFile(resource.getName(), file, DEFAULT_FILE_VERSION);
        }
        return buildInfo;
    }

    /** Check if a resource file is under the working directory. */
    private static void validateTestResourceFile(File workDir, File file)
            throws BuildRetrievalError {
        if (!file.toPath().normalize().startsWith(workDir.toPath().normalize())) {
            throw new BuildRetrievalError(file + " is outside of working directory.");
        }
    }

    /**
     * Retrieve a file from cache or URL.
     *
     * <p>If the URL is in the cache, this method returns the cached file. Otherwise, it downloads
     * and adds the file to the cache. If any file operation fails, this method throws {@link
     * UncheckedIOException}.
     *
     * @param downloadUrl the file to be retrieved.
     * @param cache the cache that maps URLs to files.
     * @param downloader the downloader that gets the file.
     * @param downloadDest the file to be created if the URL isn't in the cache.
     * @return the cached or downloaded file.
     */
    private File retrieveFile(
            String downloadUrl,
            ConcurrentHashMap<String, File> cache,
            TestResourceDownloader downloader,
            File downloadDest) {
        return cache.computeIfAbsent(
                downloadUrl,
                url -> {
                    CLog.i("Download %s from %s.", downloadDest, url);
                    try {
                        downloader.download(url, downloadDest);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return downloadDest;
                });
    }

    /**
     * Create a resource file from cache and decompress it if needed.
     *
     * <p>If any file operation fails, this method throws {@link UncheckedIOException}.
     *
     * @param resource the resource to be created.
     * @param createdResources the map from created resource names to paths.
     * @param source the local cache of the file.
     * @param buildInfo the current build info.
     * @return the file or directory to be added to build info.
     */
    private File prepareTestResource(
            TestResource resource,
            ConcurrentHashMap<String, File> createdResources,
            File source,
            ClusterBuildInfo buildInfo) {
        return createdResources.computeIfAbsent(
                resource.getName(),
                name -> {
                    // Create the file regardless of the decompress flag.
                    final File file = resource.getFile(mRootDir);
                    if (!source.equals(file)) {
                        if (file.exists()) {
                            CLog.w("Overwrite %s.", name);
                            file.delete();
                        } else {
                            CLog.i("Create %s.", name);
                            file.getParentFile().mkdirs();
                        }
                        try {
                            FileUtil.hardlinkFile(source, file);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    // Decompress if needed.
                    if (resource.getDecompress()) {
                        final File dir = resource.getDecompressDir(mRootDir);
                        try {
                            decompressArchive(
                                    file,
                                    dir,
                                    resource.mountZip(),
                                    resource.getDecompressFiles(),
                                    buildInfo);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        return dir;
                    }
                    return file;
                });
    }

    @VisibleForTesting
    FuseUtil getFuseUtil() {
        return new FuseUtil();
    }

    /**
     * Extracts a zip or a gzip to a directory.
     *
     * @param archive the archive to be extracted.
     * @param destDir the directory where the archive is extracted.
     * @param mountZip whether to mount the zip or extract it.
     * @param fileNames the files to be extracted from the archive. If the list is empty, all files
     *     are extracted.
     * @param buildInfo the {@link ClusterBuildInfo} that records mounted zip files.
     * @throws IOException if any file operation fails or any file name is not found in the archive.
     */
    private void decompressArchive(
            File archive,
            File destDir,
            boolean mountZip,
            List<String> fileNames,
            ClusterBuildInfo buildInfo)
            throws IOException {
        if (!destDir.exists()) {
            if (!destDir.mkdirs()) {
                CLog.e("Cannot create %s.", destDir);
            }
        }

        if (TarUtil.isGzip(archive)) {
            decompressTarGzip(archive, destDir, fileNames);
            return;
        }

        if (mountZip) {
            FuseUtil fuseUtil = getFuseUtil();
            if (fuseUtil.canMountZip()) {
                File mountDir = mountZip(fuseUtil, archive, buildInfo);
                // Build a shadow directory structure with symlinks to allow a test to create files
                // within it. This allows xTS to write result files under its own directory
                // structure (e.g. android-cts/results).
                symlinkFiles(mountDir, destDir, fileNames);
                return;
            }
            CLog.w("Mounting zip requested but not supported; falling back to extracting...");
        }

        decompressZip(archive, destDir, fileNames);
    }

    private void decompressTarGzip(File archive, File destDir, List<String> fileNames)
            throws IOException {
        File unGzipDir = FileUtil.createTempDir("ClusterBuildProviderUnGzip");
        try {
            File tar = TarUtil.unGzip(archive, unGzipDir);
            if (fileNames.isEmpty()) {
                TarUtil.unTar(tar, destDir);
            } else {
                TarUtil.unTar(tar, destDir, fileNames);
            }
        } finally {
            FileUtil.recursiveDelete(unGzipDir);
        }
    }

    /** Mount a zip to a temporary directory if zip mounting is supported. */
    private File mountZip(FuseUtil fuseUtil, File archive, ClusterBuildInfo buildInfo)
            throws IOException {
        File mountDir = FileUtil.createTempDir("ClusterBuildProviderZipMount");
        buildInfo.addZipMount(mountDir);
        CLog.i("Mounting %s to %s...", archive, mountDir);
        fuseUtil.mountZip(archive, mountDir);
        return mountDir;
    }

    private void symlinkFiles(File origDir, File destDir, List<String> fileNames)
            throws IOException {
        if (fileNames.isEmpty()) {
            CLog.i("Recursive symlink %s to %s...", origDir, destDir);
            FileUtil.recursiveSymlink(origDir, destDir);
        } else {
            for (String name : fileNames) {
                File origFile = new File(origDir, name);
                if (!origFile.exists()) {
                    throw new IOException(String.format("%s does not exist.", origFile));
                }
                File destFile = new File(destDir, name);
                CLog.i("Symlink %s to %s", origFile, destFile);
                destFile.getParentFile().mkdirs();
                FileUtil.symlinkFile(origFile, destFile);
            }
        }
    }

    private void decompressZip(File archive, File destDir, List<String> fileNames)
            throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            if (fileNames.isEmpty()) {
                CLog.i("Extracting %s to %s...", archive, destDir);
                ZipUtil2.extractZip(zip, destDir);
            } else {
                for (String name : fileNames) {
                    File destFile = new File(destDir, name);
                    CLog.i("Extracting %s from %s to %s", name, archive, destFile);
                    destFile.getParentFile().mkdirs();
                    if (!ZipUtil2.extractFileFromZip(zip, name, destFile)) {
                        throw new IOException(
                                String.format("%s is not found in %s", name, archive));
                    }
                }
            }
        }
    }

    @Override
    public void buildNotTested(IBuildInfo info) {}

    @Override
    public void cleanUp(IBuildInfo info) {
        if (!(info instanceof ClusterBuildInfo)) {
            throw new IllegalArgumentException("info is not an instance of ClusterBuildInfo");
        }
        FuseUtil fuseUtil = getFuseUtil();
        for (File dir : ((ClusterBuildInfo) info).getZipMounts()) {
            fuseUtil.unmountZip(dir);
            FileUtil.recursiveDelete(dir);
        }
    }

    @VisibleForTesting
    TestResourceDownloader createTestResourceDownloader() {
        return new TestResourceDownloader();
    }

    @VisibleForTesting
    void setRootDir(File rootDir) {
        mRootDir = rootDir;
    }

    @VisibleForTesting
    void addTestResource(TestResource resource) throws JSONException {
        mTestResources.add(resource.toJson().toString());
    }

    @VisibleForTesting
    List<TestResource> getTestResources() {
        return parseTestResources();
    }
}
