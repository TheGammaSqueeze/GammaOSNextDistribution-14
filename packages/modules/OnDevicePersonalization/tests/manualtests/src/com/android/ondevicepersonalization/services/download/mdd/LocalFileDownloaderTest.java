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

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.libraries.mobiledatadownload.downloader.DownloadConstraints;
import com.google.android.libraries.mobiledatadownload.downloader.DownloadRequest;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.backends.AndroidUri;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// Before running the test, run the command "adb push test_data1.json /data/local/tmp".
@RunWith(JUnit4.class)
public class LocalFileDownloaderTest {
    private static final String BASE_URL = "file:///data/local/tmp/test_data1.json";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private SynchronousFileStorage mFileStorage;

    @Before
    public void setup() throws Exception {
        mFileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
    }

    @Test
    public void testValidDebugUrl() throws Exception {
        FileDownloader downloader = new OnDevicePersonalizationFileDownloader(mFileStorage,
                MoreExecutors.directExecutor(), mContext);
        Uri fileUri = AndroidUri.builder(mContext).setModule("mdd").setRelativePath(
                "file_1").build();
        DownloadRequest downloadRequest = DownloadRequest.newBuilder().setUrlToDownload(
                BASE_URL).setFileUri(fileUri).setDownloadConstraints(
                DownloadConstraints.NONE).build();

        ListenableFuture<Void> future = downloader.startDownloading(downloadRequest);
        future.get();
        assertTrue(mFileStorage.exists(fileUri));
        mFileStorage.deleteFile(fileUri);
    }
}
