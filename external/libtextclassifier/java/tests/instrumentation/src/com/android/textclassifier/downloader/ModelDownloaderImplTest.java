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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.expectThrows;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.android.textclassifier.downloader.TestModelDownloaderService.DownloadResult;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModelDownloaderImplTest {
  private static final String MANIFEST_URL = "https://manifest.url";
  private static final String MODEL_URL = "https://model.url";
  private static final byte[] MODEL_CONTENT_BYTES = "content".getBytes();
  private static final long MODEL_SIZE_IN_BYTES = 7L;
  private static final String MODEL_FINGERPRINT =
      "5406ebea1618e9b73a7290c5d716f0b47b4f1fbc5d8c"
          + "5e78c9010a3e01c18d8594aa942e3536f7e01574245d34647523";
  private static final ModelManifest.Model MODEL_PROTO =
      ModelManifest.Model.newBuilder()
          .setUrl(MODEL_URL)
          .setSizeInBytes(MODEL_SIZE_IN_BYTES)
          .setFingerprint(MODEL_FINGERPRINT)
          .build();
  private static final ModelManifest MODEL_MANIFEST_PROTO =
      ModelManifest.newBuilder().addModels(MODEL_PROTO).build();

  private ModelDownloaderImpl modelDownloaderImpl;
  private File modelDownloaderDir;

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    this.modelDownloaderImpl =
        new ModelDownloaderImpl(
            context, MoreExecutors.newDirectExecutorService(), TestModelDownloaderService.class);
    this.modelDownloaderDir = new File(context.getFilesDir(), "downloader");
    this.modelDownloaderDir.mkdirs();

    TestModelDownloaderService.reset();
  }

  @After
  public void tearDown() {
    DownloaderTestUtils.deleteRecursively(modelDownloaderDir);
  }

  @Test
  public void downloadManifest_failToBind() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(false);
    ListenableFuture<ModelManifest> manifestFuture =
        modelDownloaderImpl.downloadManifest(MANIFEST_URL);

    Throwable t = expectThrows(Throwable.class, manifestFuture::get);
    assertThat(t).hasCauseThat().isInstanceOf(ModelDownloadException.class);
    ModelDownloadException e = (ModelDownloadException) t.getCause();
    assertThat(e.getErrorCode())
        .isEqualTo(ModelDownloadException.FAILED_TO_DOWNLOAD_SERVICE_CONN_BROKEN);
    assertThat(e.getDownloaderLibErrorCode())
        .isEqualTo(ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE);
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
  }

  @Test
  public void downloadManifest_succeed() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(true);
    TestModelDownloaderService.setDownloadResult(
        MANIFEST_URL, DownloadResult.SUCCEEDED, MODEL_MANIFEST_PROTO.toByteArray());
    ListenableFuture<ModelManifest> manifestFuture =
        modelDownloaderImpl.downloadManifest(MANIFEST_URL);

    assertThat(manifestFuture.get()).isEqualTo(MODEL_MANIFEST_PROTO); // ProtoTruth is not available
    assertThat(TestModelDownloaderService.getOnUnbindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isTrue();
  }

  @Test
  public void downloadManifest_failToDownload() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(true);
    TestModelDownloaderService.setDownloadResult(MANIFEST_URL, DownloadResult.FAILED, null);
    ListenableFuture<ModelManifest> manifestFuture =
        modelDownloaderImpl.downloadManifest(MANIFEST_URL);

    Throwable t = expectThrows(Throwable.class, manifestFuture::get);
    assertThat(t).hasCauseThat().isInstanceOf(ModelDownloadException.class);
    ModelDownloadException e = (ModelDownloadException) t.getCause();
    assertThat(e.getErrorCode()).isEqualTo(ModelDownloadException.FAILED_TO_DOWNLOAD_OTHER);
    assertThat(e.getDownloaderLibErrorCode())
        .isEqualTo(TestModelDownloaderService.DOWNLOADER_LIB_ERROR_CODE);
    assertThat(e).hasMessageThat().contains(TestModelDownloaderService.ERROR_MSG);
    assertThat(TestModelDownloaderService.getOnUnbindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isTrue();
  }

  @Test
  public void downloadManifest_failToParse() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(true);
    TestModelDownloaderService.setDownloadResult(
        MANIFEST_URL, DownloadResult.SUCCEEDED, "randomString".getBytes());
    ListenableFuture<ModelManifest> manifestFuture =
        modelDownloaderImpl.downloadManifest(MANIFEST_URL);

    Throwable t = expectThrows(Throwable.class, manifestFuture::get);
    assertThat(t).hasCauseThat().isInstanceOf(ModelDownloadException.class);
    ModelDownloadException e = (ModelDownloadException) t.getCause();
    assertThat(e.getErrorCode()).isEqualTo(ModelDownloadException.FAILED_TO_PARSE_MANIFEST);
    assertThat(e.getDownloaderLibErrorCode())
        .isEqualTo(ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE);
    assertThat(TestModelDownloaderService.getOnUnbindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isTrue();
  }

  @Test
  public void downloadManifest_cancelAndUnbind() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(true);
    TestModelDownloaderService.setDownloadResult(MANIFEST_URL, DownloadResult.DO_NOTHING, null);
    ListenableFuture<ModelManifest> manifestFuture =
        modelDownloaderImpl.downloadManifest(MANIFEST_URL);

    assertThat(TestModelDownloaderService.getOnBindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isTrue();
    manifestFuture.cancel(true);

    expectThrows(CancellationException.class, manifestFuture::get);
    assertThat(TestModelDownloaderService.getOnUnbindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isTrue();
  }

  @Test
  public void downloadModel_failToBind() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(false);
    ListenableFuture<File> modelFuture =
        modelDownloaderImpl.downloadModel(modelDownloaderDir, MODEL_PROTO);

    Throwable t = expectThrows(Throwable.class, modelFuture::get);
    assertThat(t).hasCauseThat().isInstanceOf(ModelDownloadException.class);
    ModelDownloadException e = (ModelDownloadException) t.getCause();
    assertThat(e.getErrorCode())
        .isEqualTo(ModelDownloadException.FAILED_TO_DOWNLOAD_SERVICE_CONN_BROKEN);
    assertThat(e.getDownloaderLibErrorCode())
        .isEqualTo(ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE);
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
  }

  @Test
  public void downloadModel_succeed() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(true);
    TestModelDownloaderService.setDownloadResult(
        MODEL_URL, DownloadResult.SUCCEEDED, MODEL_CONTENT_BYTES);
    ListenableFuture<File> modelFuture =
        modelDownloaderImpl.downloadModel(modelDownloaderDir, MODEL_PROTO);

    File modelFile = modelFuture.get();
    assertThat(modelFile.getParentFile()).isEqualTo(modelDownloaderDir);
    assertThat(Files.readAllBytes(modelFile.toPath())).isEqualTo(MODEL_CONTENT_BYTES);
    assertThat(TestModelDownloaderService.getOnUnbindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isTrue();
  }

  @Test
  public void downloadModel_failToDownload() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(true);
    TestModelDownloaderService.setDownloadResult(MODEL_URL, DownloadResult.FAILED, null);
    ListenableFuture<File> modelFuture =
        modelDownloaderImpl.downloadModel(modelDownloaderDir, MODEL_PROTO);

    Throwable t = expectThrows(Throwable.class, modelFuture::get);
    assertThat(t).hasCauseThat().isInstanceOf(ModelDownloadException.class);
    ModelDownloadException e = (ModelDownloadException) t.getCause();
    assertThat(e.getErrorCode()).isEqualTo(ModelDownloadException.FAILED_TO_DOWNLOAD_OTHER);
    assertThat(e.getDownloaderLibErrorCode())
        .isEqualTo(TestModelDownloaderService.DOWNLOADER_LIB_ERROR_CODE);
    assertThat(e).hasMessageThat().contains(TestModelDownloaderService.ERROR_MSG);
    assertThat(TestModelDownloaderService.getOnUnbindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isTrue();
  }

  @Test
  public void downloadModel_failToValidate() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(true);
    TestModelDownloaderService.setDownloadResult(
        MODEL_URL, DownloadResult.SUCCEEDED, "randomString".getBytes());
    ListenableFuture<File> modelFuture =
        modelDownloaderImpl.downloadModel(modelDownloaderDir, MODEL_PROTO);

    Throwable t = expectThrows(Throwable.class, modelFuture::get);
    assertThat(t).hasCauseThat().isInstanceOf(ModelDownloadException.class);
    ModelDownloadException e = (ModelDownloadException) t.getCause();
    assertThat(e.getErrorCode()).isEqualTo(ModelDownloadException.FAILED_TO_VALIDATE_MODEL);
    assertThat(e.getDownloaderLibErrorCode())
        .isEqualTo(ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE);
    assertThat(TestModelDownloaderService.getOnUnbindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isTrue();
  }

  @Test
  public void downloadModel_cancelAndUnbind() throws Exception {
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isFalse();
    assertThat(TestModelDownloaderService.isBound()).isFalse();

    TestModelDownloaderService.setBindSucceed(true);
    TestModelDownloaderService.setDownloadResult(MODEL_URL, DownloadResult.DO_NOTHING, null);
    ListenableFuture<File> modelFuture =
        modelDownloaderImpl.downloadModel(modelDownloaderDir, MODEL_PROTO);

    assertThat(TestModelDownloaderService.getOnBindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isTrue();
    modelFuture.cancel(true);

    expectThrows(CancellationException.class, modelFuture::get);
    assertThat(TestModelDownloaderService.getOnUnbindInvokedLatch().await(1L, SECONDS)).isTrue();
    assertThat(TestModelDownloaderService.isBound()).isFalse();
    assertThat(TestModelDownloaderService.hasEverBeenBound()).isTrue();
  }
}
