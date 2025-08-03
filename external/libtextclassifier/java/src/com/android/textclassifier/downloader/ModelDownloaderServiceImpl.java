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

package com.android.textclassifier.downloader;

import static com.android.textclassifier.downloader.ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Throwables.getCausalChain;

import android.os.RemoteException;
import com.android.textclassifier.common.base.TcLog;
import com.google.android.downloader.AndroidDownloaderLogger;
import com.google.android.downloader.ConnectivityHandler;
import com.google.android.downloader.DownloadConstraints;
import com.google.android.downloader.DownloadRequest;
import com.google.android.downloader.DownloadResult;
import com.google.android.downloader.Downloader;
import com.google.android.downloader.PlatformUrlEngine;
import com.google.android.downloader.RequestException;
import com.google.android.downloader.SimpleFileDownloadDestination;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import javax.annotation.concurrent.ThreadSafe;

/** IModelDownloaderService implementation with Android Downloader library. */
@ThreadSafe
final class ModelDownloaderServiceImpl extends IModelDownloaderService.Stub {
  private static final String TAG = "ModelDownloaderServiceImpl";

  // Connectivity constraints will be checked by WorkManager instead.
  private static class NoOpConnectivityHandler implements ConnectivityHandler {
    @Override
    public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
      return Futures.immediateVoidFuture();
    }
  }

  private final ExecutorService bgExecutorService;
  private final Downloader downloader;

  public ModelDownloaderServiceImpl(
      ExecutorService bgExecutorService, ListeningExecutorService transportExecutorService) {
    this.bgExecutorService = bgExecutorService;
    this.downloader =
        new Downloader.Builder()
            // This executor is for callbacks, not network IO. See discussions in cl/337156844
            .withIOExecutor(bgExecutorService)
            .withConnectivityHandler(new NoOpConnectivityHandler())
            .addUrlEngine(
                // clear text traffic won't actually work without a manifest change, so http link
                // is still not supported on production builds.
                // Adding "http" here only for testing purposes.
                ImmutableList.of("https", "http"),
                new PlatformUrlEngine(
                    // This executor handles network transportation and can stall for long
                    transportExecutorService,
                    /* connectTimeoutMs= */ 60 * 1000,
                    /* readTimeoutMs= */ 60 * 1000))
            .withLogger(new AndroidDownloaderLogger())
            .build();
  }

  @VisibleForTesting
  ModelDownloaderServiceImpl(ExecutorService bgExecutorService, Downloader downloader) {
    this.bgExecutorService = Preconditions.checkNotNull(bgExecutorService);
    this.downloader = Preconditions.checkNotNull(downloader);
  }

  @Override
  public void download(String uri, String targetFilePath, IModelDownloaderCallback callback) {
    TcLog.d(TAG, "Download request received: " + uri);
    try {
      File targetFile = new File(targetFilePath);
      File tempMetadataFile = getMetadataFile(targetFile);
      DownloadRequest request =
          downloader
              .newRequestBuilder(
                  URI.create(uri), new SimpleFileDownloadDestination(targetFile, tempMetadataFile))
              .build();
      downloader
          .execute(request)
          .transform(DownloadResult::bytesWritten, MoreExecutors.directExecutor())
          .addCallback(
              new FutureCallback<Long>() {
                @Override
                public void onSuccess(Long bytesWritten) {
                  tempMetadataFile.delete();
                  dispatchOnSuccessSafely(callback, bytesWritten);
                }

                @Override
                public void onFailure(Throwable t) {
                  TcLog.e(TAG, "onFailure", t);
                  // TODO(licha): We may be able to resume the download if we keep those files
                  targetFile.delete();
                  tempMetadataFile.delete();
                  // Try to infer the failure reason
                  RequestException requestException =
                      (RequestException)
                          Iterables.find(
                              getCausalChain(t),
                              instanceOf(RequestException.class),
                              /* defaultValue= */ null);
                  // TODO(b/181805039): Use error code once downloader lib supports it.
                  int downloaderLibErrorCode =
                      requestException != null
                          ? requestException.getErrorDetails().getHttpStatusCode()
                          : DEFAULT_DOWNLOADER_LIB_ERROR_CODE;
                  dispatchOnFailureSafely(callback, downloaderLibErrorCode, t);
                }
              },
              bgExecutorService);
    } catch (Throwable t) {
      dispatchOnFailureSafely(callback, DEFAULT_DOWNLOADER_LIB_ERROR_CODE, t);
    }
  }

  @VisibleForTesting
  static File getMetadataFile(File targetFile) {
    return new File(targetFile.getParentFile(), targetFile.getName() + ".metadata");
  }

  private static void dispatchOnSuccessSafely(
      IModelDownloaderCallback callback, long bytesWritten) {
    try {
      callback.onSuccess(bytesWritten);
    } catch (RemoteException e) {
      TcLog.e(TAG, "Unable to notify successful download", e);
    }
  }

  private static void dispatchOnFailureSafely(
      IModelDownloaderCallback callback, int downloaderLibErrorCode, Throwable throwable) {
    try {
      callback.onFailure(downloaderLibErrorCode, throwable.getMessage());
    } catch (RemoteException e) {
      TcLog.e(TAG, "Unable to notify failures in download", e);
    }
  }
}
