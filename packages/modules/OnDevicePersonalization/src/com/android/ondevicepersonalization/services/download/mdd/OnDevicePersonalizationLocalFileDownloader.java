/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import android.content.Context;
import android.net.Uri;


import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import com.google.android.libraries.mobiledatadownload.DownloadException;
import com.google.android.libraries.mobiledatadownload.downloader.DownloadRequest;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.Opener;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.WriteStreamOpener;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A {@link FileDownloader} that "downloads" by copying the file from the Resources.
 * Files for local download should be placed in the package's Resources.
 *
 * <p>Note that OnDevicePersonalizationLocalFileDownloader ignores DownloadConditions.
 */
public final class OnDevicePersonalizationLocalFileDownloader implements FileDownloader {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationLocalFileDownloader";

    /**
     * The uri to download should be formatted as an android.resource uri:
     * android.resource://<package_name>/<resource_type>/<resource_name>
     */
    private static final Set<String> sDebugSchemes = Set.of("android.resource", "file");

    private final Executor mExecutor;
    private final SynchronousFileStorage mFileStorage;
    private final Context mContext;

    public OnDevicePersonalizationLocalFileDownloader(
            SynchronousFileStorage fileStorage, Executor executor,
            Context context) {
        this.mFileStorage = fileStorage;
        this.mExecutor = executor;
        this.mContext = context;
    }

    /**
     * Determines if given uri is local odp uri
     *
     * @return true if uri is a local odp uri, false otherwise
     */
    public static boolean isLocalOdpUri(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme != null && sDebugSchemes.contains(scheme)) {
            return true;
        }
        return false;
    }

    /**
     * Performs a localFile download for the given request
     */
    @Override
    public ListenableFuture<Void> startDownloading(DownloadRequest downloadRequest) {
        return Futures.submitAsync(() -> startDownloadingInternal(downloadRequest),
                mExecutor);
    }

    private ListenableFuture<Void> startDownloadingInternal(DownloadRequest downloadRequest) {
        Uri fileUri = downloadRequest.fileUri();
        String urlToDownload = downloadRequest.urlToDownload();
        Uri uriToDownload = Uri.parse(urlToDownload);
        // Strip away the query params for local download.
        uriToDownload = new Uri.Builder()
                .scheme(uriToDownload.getScheme())
                .authority(uriToDownload.getAuthority())
                .path(uriToDownload.getPath()).build();
        sLogger.d(TAG + ": Starting local download for url: " + urlToDownload);

        try {
            Opener<OutputStream> writeStreamOpener = WriteStreamOpener.create();
            long writtenBytes;
            try (OutputStream out = mFileStorage.open(fileUri, writeStreamOpener)) {
                InputStream in = mContext.getContentResolver().openInputStream(uriToDownload);
                writtenBytes = ByteStreams.copy(in, out);
            }
            sLogger.d(TAG + "File URI " + fileUri
                    + " download complete, writtenBytes: %d" + writtenBytes);
        } catch (Exception e) {
            sLogger.e(TAG + ": %s: startDownloading got exception", e);
            return immediateFailedFuture(
                    DownloadException.builder()
                            .setDownloadResultCode(
                                    DownloadException.DownloadResultCode
                                            .ANDROID_DOWNLOADER_HTTP_ERROR)
                            .build());
        }

        return immediateVoidFuture();
    }
}
