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
package com.android.tradefed.util;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.cache.PartialZipDownloadCache;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.invoker.tracing.TracePropagatingExecutorService;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.executor.ParallelDeviceExecutor;
import com.android.tradefed.util.zip.CentralDirectoryInfo;
import com.android.tradefed.util.zip.EndCentralDirectoryInfo;
import com.android.tradefed.util.zip.LocalFileHeader;
import com.android.tradefed.util.zip.MergedZipEntryCollection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Utilities to unzip individual files inside a remote zip file. */
public class RemoteZip {

    private static final int POOL_MAX_SIZE = 8;
    private String mRemoteFilePath;
    private List<CentralDirectoryInfo> mZipEntries;
    private long mFileSize;
    private IFileDownloader mDownloader;
    // Last time this object is accessed. The timestamp is used to maintain the cache of RemoteZip
    // objects.
    private long mLastAccess;
    private boolean mUseZip64;

    private boolean mUseCache;

    /**
     * Constructor
     *
     * @param remoteFilePath the remote path to the file to download.
     * @param fileSize size of the remote file.
     * @param downloader a @{link IFileDownloader} used to download a remote file.
     * @param useZip64 whether to use zip64 format for partial download or not.
     */
    public RemoteZip(
            String remoteFilePath,
            long fileSize,
            IFileDownloader downloader,
            boolean useZip64,
            boolean useCache) {
        mRemoteFilePath = remoteFilePath;
        mFileSize = fileSize;
        mDownloader = downloader;
        mZipEntries = null;
        mUseZip64 = useZip64;
        mLastAccess = System.currentTimeMillis();
        mUseCache = useCache;
    }

    public RemoteZip(
            String remoteFilePath, long fileSize, IFileDownloader downloader, boolean useZip64) {
        this(remoteFilePath, fileSize, downloader, useZip64, false);
    }

    /**
     * Constructor
     *
     * @param remoteFilePath the remote path to the file to download.
     * @param fileSize size of the remote file.
     * @param downloader a @{link IFileDownloader} used to download a remote file.
     */
    public RemoteZip(String remoteFilePath, long fileSize, IFileDownloader downloader) {
        this(remoteFilePath, fileSize, downloader, false);
    }

    /** Get the remote file path of the remote zip artifact. */
    public String getRemoteFilePath() {
        return mRemoteFilePath;
    }

    /** Get the last time this object is accessed. */
    public long getLastAccess() {
        return mLastAccess;
    }

    /** Update the last access timestamp of the object. */
    public void setLastAccess(long timestamp) {
        mLastAccess = timestamp;
    }

    /**
     * Gets the zip file entries of a remote zip file.
     *
     * @throws BuildRetrievalError if file could not be downloaded.
     */
    public List<CentralDirectoryInfo> getZipEntries() throws BuildRetrievalError, IOException {
        if (mZipEntries != null) {
            return mZipEntries;
        }

        File partialZipFile = FileUtil.createTempFileForRemote(mRemoteFilePath, null);
        // Delete it so name is available
        partialZipFile.delete();

        try {
            // Get the end central directory of the zip file requested.
            // Download last 64kb (or entire file if the size is less than 64kb)
            long size = EndCentralDirectoryInfo.MAX_LOOKBACK;
            long startOffset = mFileSize - size;
            if (startOffset < 0) {
                // The default lookback size is greater than the size of the file, so read the whole
                // file by setting size to -1.
                startOffset = 0;
                size = -1;
            }

            mDownloader.downloadFile(mRemoteFilePath, partialZipFile, startOffset, size);
            EndCentralDirectoryInfo endCentralDirInfo =
                    new EndCentralDirectoryInfo(partialZipFile, mUseZip64);
            partialZipFile.delete();

            // Read central directory infos
            mDownloader.downloadFile(
                    mRemoteFilePath,
                    partialZipFile,
                    endCentralDirInfo.getCentralDirOffset(),
                    endCentralDirInfo.getCentralDirSize());

            mZipEntries =
                    ZipUtil.getZipCentralDirectoryInfos(
                            partialZipFile,
                            endCentralDirInfo,
                            mUseZip64);
            return mZipEntries;
        } catch (IOException e) {
            throw new BuildRetrievalError(
                    String.format("Failed to get zip entries of remote file %s", mRemoteFilePath),
                    e);
        } finally {
            FileUtil.deleteFile(partialZipFile);
        }
    }

    /**
     * Download the specified files in the remote zip file.
     *
     * @param destDir the directory to place the downloaded files to.
     * @param originalFiles a list of entries to download from the remote zip file.
     * @throws BuildRetrievalError
     * @throws IOException
     */
    public void downloadFiles(File destDir, List<CentralDirectoryInfo> originalFiles)
            throws BuildRetrievalError, IOException {
        long startTime = System.currentTimeMillis();
        final TracePropagatingExecutorService service =
                TracePropagatingExecutorService.create(Executors.newCachedThreadPool());
        List<CentralDirectoryInfo> toBeDownloaded = Collections.synchronizedList(new ArrayList<>());
        List<String> skipDownloadName = Collections.synchronizedList(new ArrayList<>());
        try (CloseableTraceScope ignored = new CloseableTraceScope("filter_existing")) {
            originalFiles.parallelStream()
                    .forEach(
                            info -> {
                                File targetFile = new File(destDir, info.getFileName());
                                if (targetFile.exists()) {
                                    skipDownloadName.add(info.getFileName());
                                } else {
                                    toBeDownloaded.add(info);
                                }
                            });
        }

        if (!skipDownloadName.isEmpty()) {
            CLog.d("skip download on already existing files: %s", skipDownloadName);
            skipDownloadName.clear();
        }

        // Remove from download anything that is in our cache
        if (mUseCache) {
            CLog.d("RemoteZip caching is enabled, evaluating.");
            try (CloseableTraceScope ignored = new CloseableTraceScope("apply_cache")) {
                for (CentralDirectoryInfo info : new ArrayList<>(toBeDownloaded)) {
                    File targetFile = new File(destDir, info.getFileName());
                    boolean cacheHit =
                            PartialZipDownloadCache.getDefaultCache()
                                    .getCachedFile(
                                            targetFile,
                                            info.getFileName(),
                                            Long.toString(info.getCrc()));
                    if (cacheHit) {
                        toBeDownloaded.remove(info);
                        CLog.d("Retrieved %s from cache", info.getFileName());
                        InvocationMetricLogger.addInvocationMetrics(
                                InvocationMetricKey.ZIP_PARTIAL_DOWNLOAD_CACHE_HIT, 1);
                    }
                }
            }
        }

        // Merge the entries into sections to minimize the download attempts.
        List<MergedZipEntryCollection> collections =
                MergedZipEntryCollection.createCollections(toBeDownloaded);
        File downloadParentDir = FileUtil.createTempDir(new File(mRemoteFilePath).getName());
        CLog.d(
                "Downloading %d files from remote zip file %s in %d sections to %s/",
                toBeDownloaded.size(), mRemoteFilePath, collections.size(), downloadParentDir);
        ParallelDeviceExecutor<Long> executor =
                new ParallelDeviceExecutor<>(Math.min(collections.size(), POOL_MAX_SIZE));
        List<Callable<Long>> callableTasks = new ArrayList<>();

        // Extract each file from the partial download.
        List<Future<IOException>> futures = Collections.synchronizedList(new ArrayList<>());

        for (MergedZipEntryCollection collection : collections) {
            Callable<Long> callableTask =
                    () -> {
                        File partialZipFile = null;
                        long downloadedSize = 0;
                        try {
                            partialZipFile =
                                    FileUtil.createTempFileForRemote(
                                            mRemoteFilePath, downloadParentDir);
                            // Delete it so name is available
                            partialZipFile.delete();
                            // End offset is based on the maximum guess of local file header size
                            // (2KB). So it can exceed the file size.
                            downloadedSize =
                                    collection.getEndOffset() - collection.getStartOffset();
                            if (collection.getStartOffset() + downloadedSize > mFileSize) {
                                downloadedSize = mFileSize - collection.getStartOffset();
                            }
                            mDownloader.downloadFile(
                                    mRemoteFilePath,
                                    partialZipFile,
                                    collection.getStartOffset(),
                                    downloadedSize);
                            final File partialZipFinal = partialZipFile;
                            Future<IOException> futureClient =
                                    service.submit(
                                            () ->
                                                    unzipDownloadedCollection(
                                                            destDir, partialZipFinal, collection));
                            futures.add(futureClient);
                        } finally {
                            // FileUtil.deleteFile(partialZipFile);
                        }
                        return downloadedSize;
                    };
            callableTasks.add(callableTask);
        }
        List<Long> downloadSizes = executor.invokeAll(callableTasks, 0L, TimeUnit.MINUTES);
        try (CloseableTraceScope finishUnzip = new CloseableTraceScope("finish_unzipping")) {
            for (Future<IOException> f : futures) {
                try {
                    IOException e = f.get();
                    if (e != null) {
                        throw e;
                    }
                } catch (ExecutionException | InterruptedException ee) {
                    throw new RuntimeException(ee);
                }
            }
        } finally {
            service.shutdown();
        }
        CLog.d(
                "%d files downloaded from remote zip file in %s. Total download size: %d bytes.",
                toBeDownloaded.size(),
                TimeUtil.formatElapsedTime(System.currentTimeMillis() - startTime),
                downloadSizes.stream().mapToLong(Long::longValue).sum());
        FileUtil.recursiveDelete(downloadParentDir);
        if (executor.hasErrors()) {
            List<Throwable> errors = executor.getErrors();
            CLog.e(
                    "%d exceptions raised when downloading partially from remote zip.",
                    errors.size());
            for (Throwable e : errors) {
                CLog.e(e);
            }
            if (errors.get(0) instanceof IOException) {
                throw (IOException) errors.get(0);
            }
            throw new RuntimeException(errors.get(0));
        }
    }

    private IOException unzipDownloadedCollection(
            File destDir, File partialZipFile, MergedZipEntryCollection collection) {
        try {
            for (CentralDirectoryInfo entry : collection.getZipEntries()) {
                File targetFile =
                        new File(Paths.get(destDir.toString(), entry.getFileName()).toString());
                if (targetFile.exists()) {
                    CLog.d(
                            "Downloaded %s already exists, skip partial unzip.",
                            entry.getFileName());
                    continue;
                }
                try (CloseableTraceScope unzipTrace =
                        new CloseableTraceScope("unzipPartialZipFile")) {
                    LocalFileHeader localFileHeader =
                            new LocalFileHeader(
                                    partialZipFile,
                                    entry.getLocalHeaderOffset() - collection.getStartOffset());
                    ZipUtil.unzipPartialZipFile(
                            partialZipFile,
                            targetFile,
                            entry,
                            localFileHeader,
                            entry.getLocalHeaderOffset() - collection.getStartOffset());
                    CLog.d(
                            "Downloaded %s. unzipped size: %s",
                            entry.getFileName(), targetFile.length());
                } catch (IOException e) {
                    CLog.e(e);
                    return e;
                }
                if (mUseCache) {
                    PartialZipDownloadCache.getDefaultCache()
                            .populateCacheFile(
                                    targetFile, entry.getFileName(), Long.toString(entry.getCrc()));
                }
            }
            return null;
        } finally {
            FileUtil.deleteFile(partialZipFile);
        }
    }
}
