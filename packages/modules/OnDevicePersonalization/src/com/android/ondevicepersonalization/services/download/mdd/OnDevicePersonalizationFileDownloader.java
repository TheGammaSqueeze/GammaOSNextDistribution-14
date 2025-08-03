/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.download.mdd;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;


import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.android.downloader.AndroidDownloaderLogger;
import com.google.android.downloader.ConnectivityHandler;
import com.google.android.downloader.DownloadConstraints;
import com.google.android.downloader.Downloader;
import com.google.android.downloader.PlatformUrlEngine;
import com.google.android.downloader.UrlEngine;
import com.google.android.libraries.mobiledatadownload.DownloadException;
import com.google.android.libraries.mobiledatadownload.downloader.DownloadRequest;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.downloader.offroad.ExceptionHandler;
import com.google.android.libraries.mobiledatadownload.downloader.offroad.Offroad2FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.integration.downloader.DownloadMetadataStore;
import com.google.android.libraries.mobiledatadownload.file.integration.downloader.SharedPreferencesDownloadMetadata;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/**
 * A OnDevicePersonalization custom {@link FileDownloader}
 */
public class OnDevicePersonalizationFileDownloader implements FileDownloader {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationFileDownloader";

    /** Downloader Connection Timeout in Milliseconds. */
    private static final int DOWNLOADER_CONNECTION_TIMEOUT_MS = 10 * 1000; // 10 seconds
    /** Downloader Read Timeout in Milliseconds. */
    private static final int DOWNLOADER_READ_TIMEOUT_MS = 10 * 1000; // 10 seconds.
    /** Downloader max download threads. */
    private static final int DOWNLOADER_MAX_DOWNLOAD_THREADS = 2;

    private static final String MDD_METADATA_SHARED_PREFERENCES = "mdd_metadata_store";

    private final SynchronousFileStorage mFileStorage;
    private final Context mContext;

    private final Executor mDownloadExecutor;

    private final FileDownloader mOffroad2FileDownloader;
    private final FileDownloader mLocalFileDownloader;

    public OnDevicePersonalizationFileDownloader(
            SynchronousFileStorage fileStorage, Executor downloadExecutor,
            Context context) {
        this.mFileStorage = fileStorage;
        this.mDownloadExecutor = downloadExecutor;
        this.mContext = context;

        this.mOffroad2FileDownloader = getOffroad2FileDownloader(mContext, mFileStorage,
                mDownloadExecutor);
        this.mLocalFileDownloader = new OnDevicePersonalizationLocalFileDownloader(mFileStorage,
                mDownloadExecutor, mContext);

    }

    @NonNull
    private static FileDownloader getOffroad2FileDownloader(
            @NonNull Context context, @NonNull SynchronousFileStorage fileStorage,
            @NonNull Executor downloadExecutor) {
        DownloadMetadataStore downloadMetadataStore = getDownloadMetadataStore(context);

        Downloader downloader =
                new Downloader.Builder()
                        .withIOExecutor(OnDevicePersonalizationExecutors.getBlockingExecutor())
                        .withConnectivityHandler(new NoOpConnectivityHandler())
                        .withMaxConcurrentDownloads(DOWNLOADER_MAX_DOWNLOAD_THREADS)
                        .withLogger(new AndroidDownloaderLogger())
                        .addUrlEngine("https", getUrlEngine())
                        .build();

        return new Offroad2FileDownloader(
                downloader,
                fileStorage,
                downloadExecutor,
                /* authTokenProvider */ null,
                downloadMetadataStore,
                getExceptionHandler(),
                Optional.absent());
    }

    @NonNull
    private static ExceptionHandler getExceptionHandler() {
        return ExceptionHandler.withDefaultHandling();
    }

    @NonNull
    private static DownloadMetadataStore getDownloadMetadataStore(@NonNull Context context) {
        SharedPreferences sharedPrefs =
                context.getSharedPreferences(MDD_METADATA_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        DownloadMetadataStore downloadMetadataStore =
                new SharedPreferencesDownloadMetadata(
                        sharedPrefs, OnDevicePersonalizationExecutors.getBackgroundExecutor());
        return downloadMetadataStore;
    }

    @NonNull
    private static UrlEngine getUrlEngine() {
        // TODO(b/219594618): Switch to use CronetUrlEngine.
        return new PlatformUrlEngine(
                OnDevicePersonalizationExecutors.getBlockingExecutor(),
                DOWNLOADER_CONNECTION_TIMEOUT_MS,
                DOWNLOADER_READ_TIMEOUT_MS);
    }

    @Override
    public ListenableFuture<Void> startDownloading(DownloadRequest downloadRequest) {
        Uri fileUri = downloadRequest.fileUri();
        String urlToDownload = downloadRequest.urlToDownload();
        sLogger.d(TAG + ": startDownloading; fileUri: " + fileUri
                + "; urlToDownload: " + urlToDownload);

        Uri uriToDownload = Uri.parse(urlToDownload);
        if (uriToDownload == null || fileUri == null) {
            sLogger.e(TAG + ": : Invalid urlToDownload " + urlToDownload);
            return immediateFailedFuture(new IllegalArgumentException("Invalid urlToDownload"));
        }

        // Check for debug enabled package and download url.
        if (OnDevicePersonalizationLocalFileDownloader.isLocalOdpUri(uriToDownload)) {
            sLogger.d(TAG + ": Handling debug download url: " + urlToDownload);
            return mLocalFileDownloader.startDownloading(downloadRequest);
        }

        if (!urlToDownload.startsWith("https")) {
            sLogger.e(TAG + ": File url is not secure: " + urlToDownload);
            return immediateFailedFuture(
                    DownloadException.builder()
                            .setDownloadResultCode(
                                    DownloadException.DownloadResultCode.INSECURE_URL_ERROR)
                            .build());
        }

        return mOffroad2FileDownloader.startDownloading(downloadRequest);
    }

    // Connectivity constraints will be checked by JobScheduler/WorkManager instead.
    @VisibleForTesting
    static class NoOpConnectivityHandler implements ConnectivityHandler {
        @Override
        public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
            return Futures.immediateVoidFuture();
        }
    }
}
