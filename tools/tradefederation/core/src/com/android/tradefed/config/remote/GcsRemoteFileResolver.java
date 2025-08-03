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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.gcs.GCSDownloaderHelper;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.invoker.logger.CurrentInvocation.InvocationInfo;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.invoker.tracing.TracePropagatingExecutorService;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.GCSFileDownloader;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil2;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

/** Implementation of {@link IRemoteFileResolver} that allows downloading from a GCS bucket. */
public class GcsRemoteFileResolver implements IRemoteFileResolver {

    public static final String PROTOCOL = "gs";

    private static final long SLEEP_INTERVAL_MS = 5 * 1000;
    private static final String RETRY_TIMEOUT_MS_ARG = "retry_timeout_ms";

    private GCSDownloaderHelper mHelper = null;

    @Override
    public ResolvedFile resolveRemoteFile(RemoteFileResolverArgs args) throws BuildRetrievalError {
        File consideredFile = args.getConsideredFile();
        // Don't use absolute path as it would not start with gs:
        String path = consideredFile.getPath();

        File destFile = GCSFileDownloader.createTempFileForRemote(path, null);
        try {
            if (canUseParallelDownload(args.getQueryArgs())) {
                Entry<File, Future<BuildRetrievalError>> parallelDownload =
                        fetchResourceWithRetryParallel(path, args.getQueryArgs(), destFile);
                ExtendedFile eFile = new ExtendedFile(parallelDownload.getKey(), "gcs", "gcs");
                eFile.setDownloadFuture(parallelDownload.getValue());
                // Return the file with metadata
                return new ResolvedFile(eFile);
            } else {
                // We need to download the file from the bucket
                fetchResourceWithRetry(path, args.getQueryArgs(), destFile);
                // Unzip it if required
                return new ResolvedFile(
                        DynamicRemoteFileResolver.unzipIfRequired(destFile, args.getQueryArgs()));
            }
        } catch (IOException e) {
            FileUtil.deleteFile(destFile);
            CLog.e(e);
            throw new BuildRetrievalError(
                    String.format("Failed to download %s due to: %s", path, e.getMessage()),
                    e,
                    InfraErrorIdentifier.GCS_ERROR);
        }
    }

    @Override
    public @Nonnull String getSupportedProtocol() {
        return PROTOCOL;
    }

    @VisibleForTesting
    protected GCSDownloaderHelper getDownloader() {
        if (mHelper == null) {
            mHelper = new GCSDownloaderHelper();
        }
        return mHelper;
    }

    @VisibleForTesting
    void sleep() {
        RunUtil.getDefault().sleep(SLEEP_INTERVAL_MS);
    }

    /** If the retry arg is set, we retry downloading until timeout is reached */
    private void fetchResourceWithRetry(String path, Map<String, String> queryArgs, File destFile)
            throws BuildRetrievalError {
        try (CloseableTraceScope ignored = new CloseableTraceScope("gs_download " + path)) {
            String timeoutStringValue = queryArgs.get(RETRY_TIMEOUT_MS_ARG);
            if (timeoutStringValue == null) {
                getDownloader().fetchTestResource(destFile, path);
                return;
            }
            long timeout = System.currentTimeMillis() + Long.parseLong(timeoutStringValue);
            BuildRetrievalError error = null;
            while (System.currentTimeMillis() < timeout) {
                try {
                    getDownloader().fetchTestResource(destFile, path);
                    return;
                } catch (BuildRetrievalError e) {
                    error = e;
                }
                sleep();
            }
            throw error;
        }
    }

    private Entry<File, Future<BuildRetrievalError>> fetchResourceWithRetryParallel(
            String path, Map<String, String> queryArgs, File destFile) throws IOException {
        String unzipValue = queryArgs.get(DynamicRemoteFileResolver.UNZIP_KEY);
        boolean useDirectory = unzipValue != null && "true".equals(unzipValue.toLowerCase());
        File possibleDir = null;
        if (useDirectory) {
            possibleDir =
                    FileUtil.createTempDir(
                            FileUtil.getBaseName(destFile.getName()),
                            CurrentInvocation.getInfo(InvocationInfo.WORK_FOLDER));
        }
        File destDir = possibleDir;
        CompletableFuture<BuildRetrievalError> futureClient =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                fetchResourceWithRetry(path, queryArgs, destFile);
                                if (useDirectory) {
                                    ZipUtil2.extractZip(destFile, destDir);
                                    FileUtil.deleteFile(destFile);
                                }
                                return null;
                            } catch (IOException ioe) {
                                FileUtil.deleteFile(destFile);
                                return new BuildRetrievalError(ioe.getMessage(), ioe);
                            } catch (BuildRetrievalError e) {
                                return e;
                            }
                        },
                        TracePropagatingExecutorService.create(ForkJoinPool.commonPool()));
        if (useDirectory) {
            return new AbstractMap.SimpleEntry<File, Future<BuildRetrievalError>>(
                    destDir, futureClient);
        } else {
            return new AbstractMap.SimpleEntry<File, Future<BuildRetrievalError>>(
                    destFile, futureClient);
        }
    }

    private boolean canUseParallelDownload(Map<String, String> query) {
        String parallelValue = query.get("parallel");
        if (parallelValue != null && "false".equals(parallelValue.toLowerCase())) {
            return false;
        }
        return true;
    }
}
