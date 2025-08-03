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

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_NOT_FOREGROUND;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.protobuf.ExtensionRegistryLite;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;

/**
 * ModelDownloader implementation that forwards requests to ModelDownloaderService. This is to
 * restrict the INTERNET permission to the service process only (instead of the whole ExtServices).
 */
final class ModelDownloaderImpl implements ModelDownloader {
  private static final String TAG = "ModelDownloaderImpl";

  private final Context context;
  private final ExecutorService bgExecutorService;
  private final Class<?> downloaderServiceClass;

  public ModelDownloaderImpl(Context context, ExecutorService bgExecutorService) {
    this(context, bgExecutorService, ModelDownloaderService.class);
  }

  @VisibleForTesting
  ModelDownloaderImpl(
      Context context, ExecutorService bgExecutorService, Class<?> downloaderServiceClass) {
    this.context = context.getApplicationContext();
    this.bgExecutorService = bgExecutorService;
    this.downloaderServiceClass = downloaderServiceClass;
  }

  @Override
  public ListenableFuture<ModelManifest> downloadManifest(String manifestUrl) {
    File manifestFile =
        new File(context.getCacheDir(), manifestUrl.replaceAll("[^A-Za-z0-9]", "_") + ".manifest");
    return Futures.transform(
        download(URI.create(manifestUrl), manifestFile),
        bytesWritten -> {
          try {
            return ModelManifest.parseFrom(
                new FileInputStream(manifestFile), ExtensionRegistryLite.getEmptyRegistry());
          } catch (Throwable t) {
            throw new ModelDownloadException(ModelDownloadException.FAILED_TO_PARSE_MANIFEST, t);
          } finally {
            manifestFile.delete();
          }
        },
        bgExecutorService);
  }

  @Override
  public ListenableFuture<File> downloadModel(File targetDir, ModelManifest.Model model) {
    File modelFile = new File(targetDir, model.getUrl().replaceAll("[^A-Za-z0-9]", "_") + ".model");
    ListenableFuture<File> modelFileFuture =
        Futures.transform(
            download(URI.create(model.getUrl()), modelFile),
            bytesWritten -> {
              validateModel(modelFile, model.getSizeInBytes(), model.getFingerprint());
              return modelFile;
            },
            bgExecutorService);
    Futures.addCallback(
        modelFileFuture,
        new FutureCallback<File>() {
          @Override
          public void onSuccess(File pendingModelFile) {
            TcLog.d(TAG, "Download model successfully: " + pendingModelFile.getAbsolutePath());
          }

          @Override
          public void onFailure(Throwable t) {
            modelFile.delete();
            TcLog.e(TAG, "Failed to download: " + modelFile.getAbsolutePath(), t);
          }
        },
        bgExecutorService);
    return modelFileFuture;
  }

  // TODO(licha): Make this visible for testing. So we can avoid some duplicated test cases.
  /**
   * Downloads the file from uri to the targetFile. If the targetFile already exists, it will be
   * deleted. Return bytes written if succeeds.
   */
  private ListenableFuture<Long> download(URI uri, File targetFile) {
    if (targetFile.exists()) {
      TcLog.w(
          TAG,
          "Target file already exists. Delete it before downloading: "
              + targetFile.getAbsolutePath());
      targetFile.delete();
    }
    DownloaderServiceConnection conn = new DownloaderServiceConnection();
    ListenableFuture<IModelDownloaderService> downloaderServiceFuture = connect(conn);
    ListenableFuture<Long> bytesWrittenFuture =
        Futures.transformAsync(
            downloaderServiceFuture,
            service -> scheduleDownload(service, uri, targetFile),
            bgExecutorService);
    bytesWrittenFuture.addListener(
        () -> {
          try {
            context.unbindService(conn);
          } catch (IllegalArgumentException e) {
            TcLog.e(TAG, "Error when unbind", e);
          }
        },
        bgExecutorService);
    return bytesWrittenFuture;
  }

  /** Model verification. Throws unchecked Exceptions if validation fails. */
  private static void validateModel(File pendingModelFile, long sizeInBytes, String fingerprint) {
    if (!pendingModelFile.exists()) {
      throw new ModelDownloadException(
          ModelDownloadException.DOWNLOADED_FILE_MISSING, "PendingModelFile does not exist.");
    }
    if (pendingModelFile.length() != sizeInBytes) {
      throw new ModelDownloadException(
          ModelDownloadException.FAILED_TO_VALIDATE_MODEL,
          String.format(
              "PendingModelFile size does not match: expected [%d] actual [%d]",
              sizeInBytes, pendingModelFile.length()));
    }
    try {
      HashCode pendingModelFingerprint =
          Files.asByteSource(pendingModelFile).hash(Hashing.sha384());
      if (!pendingModelFingerprint.equals(HashCode.fromString(fingerprint))) {
        throw new ModelDownloadException(
            ModelDownloadException.FAILED_TO_VALIDATE_MODEL,
            String.format(
                "PendingModelFile fingerprint does not match: expected [%s] actual [%s]",
                fingerprint, pendingModelFingerprint));
      }
    } catch (IOException e) {
      throw new ModelDownloadException(ModelDownloadException.FAILED_TO_VALIDATE_MODEL, e);
    }
    TcLog.d(TAG, "Pending model file passed validation.");
  }

  private ListenableFuture<IModelDownloaderService> connect(DownloaderServiceConnection conn) {
    TcLog.d(TAG, "Starting a new connection to ModelDownloaderService");
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          conn.attachCompleter(completer);
          Intent intent = new Intent(context, downloaderServiceClass);
          if (context.bindService(intent, conn, BIND_AUTO_CREATE | BIND_NOT_FOREGROUND)) {
            return "Binding to service";
          } else {
            completer.setException(
                new ModelDownloadException(
                    ModelDownloadException.FAILED_TO_DOWNLOAD_SERVICE_CONN_BROKEN,
                    "Unable to bind to service"));
            return "Binding failed";
          }
        });
  }

  // Here the returned download result future can be set by: 1) the service can invoke the callback
  // and set the result/exception; 2) If the service crashed, the CallbackToFutureAdapter will try
  // to fail the future when the callback is garbage collected. If somehow none of them worked, the
  // restult future will hang there until time out. (WorkManager forces a 10-min running time.)
  private static ListenableFuture<Long> scheduleDownload(
      IModelDownloaderService service, URI uri, File targetFile) {
    TcLog.d(TAG, "Scheduling a new download task with ModelDownloaderService");
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          service.download(
              uri.toString(),
              targetFile.getAbsolutePath(),
              new IModelDownloaderCallback.Stub() {
                @Override
                public void onSuccess(long bytesWritten) {
                  completer.set(bytesWritten);
                }

                @Override
                public void onFailure(int downloaderLibErrorCode, String errorMsg) {
                  completer.setException(
                      new ModelDownloadException(
                          ModelDownloadException.FAILED_TO_DOWNLOAD_OTHER,
                          downloaderLibErrorCode,
                          errorMsg));
                }
              });
          return "downlaoderService.download";
        });
  }

  /** The implementation of {@link ServiceConnection} that handles changes in the connection. */
  @VisibleForTesting
  static class DownloaderServiceConnection implements ServiceConnection {
    private static final String TAG = "ModelDownloaderImpl.DownloaderServiceConnection";

    private CallbackToFutureAdapter.Completer<IModelDownloaderService> completer;

    public void attachCompleter(
        CallbackToFutureAdapter.Completer<IModelDownloaderService> completer) {
      this.completer = completer;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
      TcLog.d(TAG, "DownloaderService connected");
      completer.set(Preconditions.checkNotNull(IModelDownloaderService.Stub.asInterface(iBinder)));
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
      // If this is invoked after onServiceConnected, it will be ignored by the completer.
      completer.setException(
          new ModelDownloadException(
              ModelDownloadException.FAILED_TO_DOWNLOAD_SERVICE_CONN_BROKEN,
              "Service disconnected"));
    }

    @Override
    public void onBindingDied(ComponentName name) {
      completer.setException(
          new ModelDownloadException(
              ModelDownloadException.FAILED_TO_DOWNLOAD_SERVICE_CONN_BROKEN, "Binding died"));
    }

    @Override
    public void onNullBinding(ComponentName name) {
      completer.setException(
          new ModelDownloadException(
              ModelDownloadException.FAILED_TO_DOWNLOAD_SERVICE_CONN_BROKEN,
              "Unable to bind to DownloaderService"));
    }
  }
}
