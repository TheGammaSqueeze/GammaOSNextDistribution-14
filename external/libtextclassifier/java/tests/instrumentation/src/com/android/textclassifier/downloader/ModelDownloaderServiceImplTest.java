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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.downloader.DownloadConstraints;
import com.google.android.downloader.DownloadDestination;
import com.google.android.downloader.DownloadRequest;
import com.google.android.downloader.DownloadResult;
import com.google.android.downloader.Downloader;
import com.google.android.downloader.ErrorDetails;
import com.google.android.downloader.RequestException;
import com.google.android.downloader.SimpleFileDownloadDestination;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.net.URI;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ModelDownloaderServiceImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final long BYTES_WRITTEN = 1L;
  private static final String DOWNLOAD_URI =
      "https://www.gstatic.com/android/text_classifier/r/v999/en.fb";
  private static final int DOWNLOADER_LIB_ERROR_CODE = 500;
  private static final String ERROR_MESSAGE = "err_msg";
  private static final Exception DOWNLOADER_LIB_EXCEPTION =
      new RequestException(
          ErrorDetails.builder()
              .setErrorMessage(ERROR_MESSAGE)
              .setHttpStatusCode(DOWNLOADER_LIB_ERROR_CODE)
              .build());

  @Mock private Downloader downloader;
  private File targetModelFile;
  private File targetMetadataFile;
  private ModelDownloaderServiceImpl modelDownloaderServiceImpl;
  private TestSuccessCallbackImpl successCallback;
  private TestFailureCallbackImpl failureCallback;

  @Before
  public void setUp() {

    this.targetModelFile =
        new File(ApplicationProvider.getApplicationContext().getCacheDir(), "model.fb");
    this.targetMetadataFile = ModelDownloaderServiceImpl.getMetadataFile(targetModelFile);
    this.modelDownloaderServiceImpl =
        new ModelDownloaderServiceImpl(MoreExecutors.newDirectExecutorService(), downloader);
    this.successCallback = new TestSuccessCallbackImpl();
    this.failureCallback = new TestFailureCallbackImpl();

    targetModelFile.deleteOnExit();
    targetMetadataFile.deleteOnExit();
    when(downloader.newRequestBuilder(any(), any(DownloadDestination.class)))
        .thenReturn(
            DownloadRequest.newBuilder()
                .setUri(URI.create(DOWNLOAD_URI))
                .setDownloadConstraints(DownloadConstraints.NONE)
                .setDestination(
                    new SimpleFileDownloadDestination(targetModelFile, targetMetadataFile)));
  }

  @Test
  public void download_succeeded() throws Exception {
    targetModelFile.createNewFile();
    targetMetadataFile.createNewFile();
    when(downloader.execute(any()))
        .thenReturn(
            FluentFuture.from(Futures.immediateFuture(DownloadResult.create(BYTES_WRITTEN))));
    modelDownloaderServiceImpl.download(
        DOWNLOAD_URI, targetModelFile.getAbsolutePath(), successCallback);

    assertThat(successCallback.getBytesWrittenFuture().get()).isEqualTo(BYTES_WRITTEN);
    assertThat(targetModelFile.exists()).isTrue();
    assertThat(targetMetadataFile.exists()).isFalse();
  }

  @Test
  public void download_failed() throws Exception {
    targetModelFile.createNewFile();
    targetMetadataFile.createNewFile();
    when(downloader.execute(any()))
        .thenReturn(FluentFuture.from(Futures.immediateFailedFuture(DOWNLOADER_LIB_EXCEPTION)));
    modelDownloaderServiceImpl.download(
        DOWNLOAD_URI, targetModelFile.getAbsolutePath(), successCallback);

    Throwable t =
        expectThrows(Throwable.class, () -> successCallback.getBytesWrittenFuture().get());
    assertThat(t).hasCauseThat().isInstanceOf(ModelDownloadException.class);
    ModelDownloadException e = (ModelDownloadException) t.getCause();
    assertThat(e.getErrorCode()).isEqualTo(ModelDownloadException.FAILED_TO_DOWNLOAD_OTHER);
    assertThat(e.getDownloaderLibErrorCode()).isEqualTo(DOWNLOADER_LIB_ERROR_CODE);
    assertThat(e).hasMessageThat().contains(ERROR_MESSAGE);
    assertThat(targetModelFile.exists()).isFalse();
    assertThat(targetMetadataFile.exists()).isFalse();
  }

  @Test
  public void download_succeeded_callbackFailed() throws Exception {
    targetModelFile.createNewFile();
    targetMetadataFile.createNewFile();
    when(downloader.execute(any()))
        .thenReturn(
            FluentFuture.from(Futures.immediateFuture(DownloadResult.create(BYTES_WRITTEN))));
    modelDownloaderServiceImpl.download(
        DOWNLOAD_URI, targetModelFile.getAbsolutePath(), failureCallback);

    assertThat(failureCallback.onSuccessCalled).isTrue();
    assertThat(targetModelFile.exists()).isTrue();
    assertThat(targetMetadataFile.exists()).isFalse();
  }

  @Test
  public void download_failed_callbackFailed() throws Exception {
    targetModelFile.createNewFile();
    targetMetadataFile.createNewFile();
    when(downloader.execute(any()))
        .thenReturn(FluentFuture.from(Futures.immediateFailedFuture(DOWNLOADER_LIB_EXCEPTION)));
    modelDownloaderServiceImpl.download(
        DOWNLOAD_URI, targetModelFile.getAbsolutePath(), failureCallback);

    assertThat(failureCallback.onFailureCalled).isTrue();
    assertThat(targetModelFile.exists()).isFalse();
    assertThat(targetMetadataFile.exists()).isFalse();
  }

  // NOTICE: Had some problem mocking this AIDL interface, so created fake impls
  private static final class TestSuccessCallbackImpl extends IModelDownloaderCallback.Stub {
    private final SettableFuture<Long> bytesWrittenFuture = SettableFuture.<Long>create();

    public ListenableFuture<Long> getBytesWrittenFuture() {
      return bytesWrittenFuture;
    }

    @Override
    public void onSuccess(long bytesWritten) {
      bytesWrittenFuture.set(bytesWritten);
    }

    @Override
    public void onFailure(int downloaderLibErrorCode, String errorMsg) {
      bytesWrittenFuture.setException(
          new ModelDownloadException(
              ModelDownloadException.FAILED_TO_DOWNLOAD_OTHER, downloaderLibErrorCode, errorMsg));
    }
  }

  private static final class TestFailureCallbackImpl extends IModelDownloaderCallback.Stub {
    public boolean onSuccessCalled = false;
    public boolean onFailureCalled = false;

    @Override
    public void onSuccess(long bytesWritten) {
      onSuccessCalled = true;
      throw new RuntimeException();
    }

    @Override
    public void onFailure(int downloaderLibErrorCode, String errorMsg) {
      onFailureCalled = true;
      throw new RuntimeException();
    }
  }
}
