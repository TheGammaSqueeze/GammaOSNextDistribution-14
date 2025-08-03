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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.LocaleList;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;
import com.android.os.AtomsProto.TextClassifierDownloadReported;
import com.android.os.AtomsProto.TextClassifierDownloadReported.DownloadStatus;
import com.android.os.AtomsProto.TextClassifierDownloadReported.FailureReason;
import com.android.os.AtomsProto.TextClassifierDownloadWorkCompleted;
import com.android.os.AtomsProto.TextClassifierDownloadWorkCompleted.WorkResult;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.common.statsd.TextClassifierDownloadLoggerTestRule;
import com.android.textclassifier.testing.SetDefaultLocalesRule;
import com.android.textclassifier.testing.TestingDeviceConfig;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class ModelDownloadWorkerTest {
  private static final long WORK_ID = 123456789L;
  private static final String MODEL_TYPE = ModelType.ANNOTATOR;
  private static final String MODEL_TYPE_2 = ModelType.ACTIONS_SUGGESTIONS;
  private static final TextClassifierDownloadReported.ModelType MODEL_TYPE_ATOM =
      TextClassifierDownloadReported.ModelType.ANNOTATOR;
  private static final String LOCALE_TAG = "en";
  private static final String LOCALE_TAG_2 = "zh";
  private static final String LOCALE_TAG_3 = "es";
  private static final String MANIFEST_URL =
      "https://www.gstatic.com/android/text_classifier/q/v711/en.fb.manifest";
  private static final String MANIFEST_URL_2 =
      "https://www.gstatic.com/android/text_classifier/q/v711/zh.fb.manifest";
  private static final String MANIFEST_URL_3 =
      "https://www.gstatic.com/android/text_classifier/q/v711/es.fb.manifest";
  private static final String MODEL_URL =
      "https://www.gstatic.com/android/text_classifier/q/v711/en.fb";
  private static final String MODEL_URL_2 =
      "https://www.gstatic.com/android/text_classifier/q/v711/zh.fb";
  private static final String MODEL_URL_3 =
      "https://www.gstatic.com/android/text_classifier/q/v711/es.fb";
  private static final int RUN_ATTEMPT_COUNT = 1;
  private static final int WORKER_MAX_RUN_ATTEMPT_COUNT = 5;
  private static final int MANIFEST_MAX_ATTEMPT_COUNT = 2;
  private static final ModelManifest.Model MODEL_PROTO =
      ModelManifest.Model.newBuilder()
          .setUrl(MODEL_URL)
          .setSizeInBytes(1)
          .setFingerprint("fingerprint")
          .build();
  private static final ModelManifest.Model MODEL_PROTO_2 =
      ModelManifest.Model.newBuilder()
          .setUrl(MODEL_URL_2)
          .setSizeInBytes(1)
          .setFingerprint("fingerprint")
          .build();
  private static final ModelManifest.Model MODEL_PROTO_3 =
      ModelManifest.Model.newBuilder()
          .setUrl(MODEL_URL_3)
          .setSizeInBytes(1)
          .setFingerprint("fingerprint")
          .build();

  private static final ModelManifest MODEL_MANIFEST_PROTO =
      ModelManifest.newBuilder().addModels(MODEL_PROTO).build();
  private static final ModelManifest MODEL_MANIFEST_PROTO_2 =
      ModelManifest.newBuilder().addModels(MODEL_PROTO_2).build();
  private static final ModelManifest MODEL_MANIFEST_PROTO_3 =
      ModelManifest.newBuilder().addModels(MODEL_PROTO_3).build();
  private static final ModelDownloadException FAILED_TO_DOWNLOAD_EXCEPTION =
      new ModelDownloadException(
          ModelDownloadException.FAILED_TO_DOWNLOAD_OTHER, "failed to download");
  private static final FailureReason FAILED_TO_DOWNLOAD_FAILURE_REASON =
      TextClassifierDownloadReported.FailureReason.FAILED_TO_DOWNLOAD_OTHER;
  private static final LocaleList DEFAULT_LOCALE_LIST = new LocaleList(new Locale(LOCALE_TAG));
  private static final LocaleList LOCALE_LIST_2 =
      new LocaleList(new Locale(LOCALE_TAG), new Locale(LOCALE_TAG_2));
  private static final LocaleList LOCALE_LIST_3 =
      new LocaleList(new Locale(LOCALE_TAG), new Locale(LOCALE_TAG_2), new Locale(LOCALE_TAG_3));
  private static final Instant WORK_SCHEDULED_TIME = Instant.now();
  private static final Instant WORK_STARTED_TIME = WORK_SCHEDULED_TIME.plusSeconds(100);
  // Make sure any combination has a different diff
  private static final Instant DOWNLOAD_STARTED_TIME = WORK_STARTED_TIME.plusSeconds(1);
  private static final Instant DOWNLOAD_ENDED_TIME = WORK_STARTED_TIME.plusSeconds(1 + 2);
  private static final Instant DOWNLOAD_STARTED_TIME_2 = WORK_STARTED_TIME.plusSeconds(1 + 2 + 3);
  private static final Instant DOWNLOAD_ENDED_TIME_2 = WORK_STARTED_TIME.plusSeconds(1 + 2 + 3 + 4);
  private static final Instant WORK_ENDED_TIME = WORK_STARTED_TIME.plusSeconds(1 + 2 + 3 + 4 + 5);
  private static final long DOWNLOAD_STARTED_TO_ENDED_MILLIS =
      DOWNLOAD_ENDED_TIME.toEpochMilli() - DOWNLOAD_STARTED_TIME.toEpochMilli();
  private static final long DOWNLOAD_STARTED_TO_ENDED_2_MILLIS =
      DOWNLOAD_ENDED_TIME_2.toEpochMilli() - DOWNLOAD_STARTED_TIME_2.toEpochMilli();
  private static final long WORK_SCHEDULED_TO_STARTED_MILLIS =
      WORK_STARTED_TIME.toEpochMilli() - WORK_SCHEDULED_TIME.toEpochMilli();
  private static final long WORK_STARTED_TO_ENDED_MILLIS =
      WORK_ENDED_TIME.toEpochMilli() - WORK_STARTED_TIME.toEpochMilli();

  @Mock private Clock clock;
  @Mock private ModelDownloader modelDownloader;
  private File modelDownloaderDir;
  private File modelFile;
  private File modelFile2;
  private File modelFile3;
  private DownloadedModelDatabase db;
  private DownloadedModelManager downloadedModelManager;
  private TestingDeviceConfig deviceConfig;
  private TextClassifierSettings settings;

  @Rule public final SetDefaultLocalesRule setDefaultLocalesRule = new SetDefaultLocalesRule();

  @Rule
  public final TextClassifierDownloadLoggerTestRule loggerTestRule =
      new TextClassifierDownloadLoggerTestRule();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    Context context = ApplicationProvider.getApplicationContext();
    this.deviceConfig = new TestingDeviceConfig();
    this.settings = new TextClassifierSettings(deviceConfig, /* isWear= */ false);
    this.modelDownloaderDir = new File(context.getCacheDir(), "downloaded");
    this.modelDownloaderDir.mkdirs();
    this.modelFile = new File(modelDownloaderDir, "test.model");
    this.modelFile2 = new File(modelDownloaderDir, "test2.model");
    this.modelFile3 = new File(modelDownloaderDir, "test3.model");
    this.db = Room.inMemoryDatabaseBuilder(context, DownloadedModelDatabase.class).build();
    this.downloadedModelManager =
        DownloadedModelManagerImpl.getInstanceForTesting(db, modelDownloaderDir, settings);

    setDefaultLocalesRule.set(DEFAULT_LOCALE_LIST);
    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, true);
  }

  @After
  public void cleanUp() {
    db.close();
    DownloaderTestUtils.deleteRecursively(modelDownloaderDir);
  }

  @Test
  public void downloadSucceed() throws Exception {
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    modelFile.createNewFile();
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    verifySucceededDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceed_modelAlreadyExists() throws Exception {
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    modelFile.createNewFile();
    downloadedModelManager.registerModel(MODEL_URL, modelFile.getAbsolutePath());
    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    verifySucceededDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceed_manifestAlreadyExists() throws Exception {
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    modelFile.createNewFile();
    downloadedModelManager.registerModel(MODEL_URL, modelFile.getAbsolutePath());
    downloadedModelManager.registerManifest(MANIFEST_URL, MODEL_URL);
    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    verifySucceededDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceed_downloadMultipleModels() throws Exception {
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE_2, LOCALE_TAG, MANIFEST_URL_2);
    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadManifest(MANIFEST_URL_2))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO_2));
    modelFile.createNewFile();
    modelFile2.createNewFile();
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO_2))
        .thenReturn(Futures.immediateFuture(modelFile2));
    // We assume we always download MODEL_TYPE first and then MODEL_TYPE_2, o/w this will be flaky
    when(clock.instant())
        .thenReturn(
            WORK_STARTED_TIME,
            DOWNLOAD_STARTED_TIME,
            DOWNLOAD_ENDED_TIME,
            DOWNLOAD_STARTED_TIME_2,
            DOWNLOAD_ENDED_TIME_2,
            WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(modelFile2.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    assertThat(downloadedModelManager.listModels(MODEL_TYPE_2)).containsExactly(modelFile2);
    List<TextClassifierDownloadReported> atoms = loggerTestRule.getLoggedDownloadReportedAtoms();
    assertThat(atoms).hasSize(2);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getUrlSuffix)
                .collect(Collectors.toList()))
        .containsExactly(MANIFEST_URL, MANIFEST_URL_2);
    assertThat(atoms.get(0).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(atoms.get(1).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getDownloadDurationMillis)
                .collect(Collectors.toList()))
        .containsExactly(DOWNLOAD_STARTED_TO_ENDED_MILLIS, DOWNLOAD_STARTED_TO_ENDED_2_MILLIS);
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceed_shareSingleModelDownloadForMultipleManifest() throws Exception {
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE_2, LOCALE_TAG, MANIFEST_URL_2);
    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadManifest(MANIFEST_URL_2))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    modelFile.createNewFile();
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    // We assume we always download MODEL_TYPE first and then MODEL_TYPE_2, o/w this will be flaky
    when(clock.instant())
        .thenReturn(
            WORK_STARTED_TIME,
            DOWNLOAD_STARTED_TIME,
            DOWNLOAD_ENDED_TIME,
            DOWNLOAD_STARTED_TIME_2,
            DOWNLOAD_ENDED_TIME_2,
            WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    assertThat(downloadedModelManager.listModels(MODEL_TYPE_2)).containsExactly(modelFile);
    verify(modelDownloader, times(1)).downloadModel(modelDownloaderDir, MODEL_PROTO);
    List<TextClassifierDownloadReported> atoms = loggerTestRule.getLoggedDownloadReportedAtoms();
    assertThat(atoms).hasSize(2);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getUrlSuffix)
                .collect(Collectors.toList()))
        .containsExactly(MANIFEST_URL, MANIFEST_URL_2);
    assertThat(atoms.get(0).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(atoms.get(1).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getDownloadDurationMillis)
                .collect(Collectors.toList()))
        .containsExactly(DOWNLOAD_STARTED_TO_ENDED_MILLIS, DOWNLOAD_STARTED_TO_ENDED_2_MILLIS);
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceed_shareManifest() throws Exception {
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE_2, LOCALE_TAG, MANIFEST_URL);
    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    modelFile.createNewFile();
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    // We assume we always download MODEL_TYPE first and then MODEL_TYPE_2, o/w this will be flaky
    when(clock.instant())
        .thenReturn(
            WORK_STARTED_TIME,
            DOWNLOAD_STARTED_TIME,
            DOWNLOAD_ENDED_TIME,
            DOWNLOAD_STARTED_TIME_2,
            DOWNLOAD_ENDED_TIME_2,
            WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    assertThat(downloadedModelManager.listModels(MODEL_TYPE_2)).containsExactly(modelFile);
    verify(modelDownloader, times(1)).downloadManifest(MANIFEST_URL);
    List<TextClassifierDownloadReported> atoms = loggerTestRule.getLoggedDownloadReportedAtoms();
    assertThat(atoms).hasSize(2);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getUrlSuffix)
                .collect(Collectors.toList()))
        .containsExactly(MANIFEST_URL, MANIFEST_URL);
    assertThat(atoms.get(0).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(atoms.get(1).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getDownloadDurationMillis)
                .collect(Collectors.toList()))
        .containsExactly(DOWNLOAD_STARTED_TO_ENDED_MILLIS, DOWNLOAD_STARTED_TO_ENDED_2_MILLIS);
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadFailed_failedToDownloadManifest() throws Exception {
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFailedFuture(FAILED_TO_DOWNLOAD_EXCEPTION));
    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.retry());
    verifyFailedDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.RETRY_MODEL_DOWNLOAD_FAILED);
  }

  @Test
  public void downloadFailed_failedToDownloadModel() throws Exception {
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFailedFuture(FAILED_TO_DOWNLOAD_EXCEPTION));
    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.retry());
    verifyFailedDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.RETRY_MODEL_DOWNLOAD_FAILED);
  }

  @Test
  public void downloadFailed_modelDownloadManagerDisabled() throws Exception {
    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, false);
    when(clock.instant()).thenReturn(WORK_STARTED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.failure());
    assertThat(loggerTestRule.getLoggedDownloadReportedAtoms()).isEmpty();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.FAILURE_MODEL_DOWNLOADER_DISABLED);
  }

  @Test
  public void downloadFailed_reachWorkerMaxRunAttempts() throws Exception {
    when(clock.instant()).thenReturn(WORK_STARTED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(WORKER_MAX_RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.failure());
    assertThat(loggerTestRule.getLoggedDownloadReportedAtoms()).isEmpty();
    verifyWorkLogging(WORKER_MAX_RUN_ATTEMPT_COUNT, WorkResult.FAILURE_MAX_RUN_ATTEMPT_REACHED);
  }

  @Test
  public void downloadSkipped_reachManifestMaxAttempts() throws Exception {
    when(clock.instant()).thenReturn(WORK_STARTED_TIME, WORK_ENDED_TIME);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    deviceConfig.setConfig(
        TextClassifierSettings.MANIFEST_DOWNLOAD_MAX_ATTEMPTS, MANIFEST_MAX_ATTEMPT_COUNT);

    for (int i = 0; i < MANIFEST_MAX_ATTEMPT_COUNT; i++) {
      downloadedModelManager.registerManifestDownloadFailure(MANIFEST_URL);
    }
    ModelDownloadWorker worker = createWorker(MANIFEST_MAX_ATTEMPT_COUNT);

    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(loggerTestRule.getLoggedDownloadReportedAtoms()).isEmpty();
    verifyWorkLogging(MANIFEST_MAX_ATTEMPT_COUNT, WorkResult.SUCCESS_NO_UPDATE_AVAILABLE);
  }

  @Test
  public void downloadSkipped_manifestAlreadyProcessed() throws Exception {
    when(clock.instant()).thenReturn(WORK_STARTED_TIME, WORK_ENDED_TIME);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    modelFile.createNewFile();
    downloadedModelManager.registerModel(MODEL_URL, modelFile.getAbsolutePath());
    downloadedModelManager.registerManifest(MANIFEST_URL, MODEL_URL);
    downloadedModelManager.registerManifestEnrollment(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(loggerTestRule.getLoggedDownloadReportedAtoms()).isEmpty();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_NO_UPDATE_AVAILABLE);
  }

  @Test
  public void downloadSucceeded_multiLanguageSupportEnabled() throws Exception {
    setDefaultLocalesRule.set(LOCALE_LIST_2);
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, true);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG_2, MANIFEST_URL_2);

    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadManifest(MANIFEST_URL_2))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO_2));

    modelFile.createNewFile();
    modelFile2.createNewFile();
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO_2))
        .thenReturn(Futures.immediateFuture(modelFile2));
    // We assume we always download MODEL_TYPE first and then MODEL_TYPE_2, o/w this will be flaky
    when(clock.instant())
        .thenReturn(
            WORK_STARTED_TIME,
            DOWNLOAD_STARTED_TIME,
            DOWNLOAD_ENDED_TIME,
            DOWNLOAD_STARTED_TIME_2,
            DOWNLOAD_ENDED_TIME_2,
            WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(modelFile2.exists()).isTrue();

    assertThat(downloadedModelManager.listModels(MODEL_TYPE))
        .containsExactly(modelFile, modelFile2);
    List<TextClassifierDownloadReported> atoms = loggerTestRule.getLoggedDownloadReportedAtoms();
    assertThat(atoms).hasSize(2);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getUrlSuffix)
                .collect(Collectors.toList()))
        .containsExactly(MANIFEST_URL, MANIFEST_URL_2);
    assertThat(atoms.get(0).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(atoms.get(1).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getDownloadDurationMillis)
                .collect(Collectors.toList()))
        .containsExactly(DOWNLOAD_STARTED_TO_ENDED_MILLIS, DOWNLOAD_STARTED_TO_ENDED_2_MILLIS);
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceeded_multiLanguageSupportEnabled_singleLocale() throws Exception {
    setDefaultLocalesRule.set(DEFAULT_LOCALE_LIST);
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, true);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);

    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));

    modelFile.createNewFile();
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    verifySucceededDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceeded_multiLanguageSupportEnabled_oneManifestAlreadyDownloaded()
      throws Exception {
    setDefaultLocalesRule.set(LOCALE_LIST_2);
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, true);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG_2, MANIFEST_URL_2);

    modelFile.createNewFile();
    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));

    modelFile2.createNewFile();
    downloadedModelManager.registerModel(MODEL_URL_2, modelFile2.getAbsolutePath());
    downloadedModelManager.registerManifest(MANIFEST_URL_2, MODEL_URL_2);
    downloadedModelManager.registerManifestEnrollment(MODEL_TYPE, LOCALE_TAG_2, MANIFEST_URL_2);

    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(modelFile2.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE))
        .containsExactly(modelFile, modelFile2);
    verifySucceededDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceeded_multiLanguageSupportDisabled() throws Exception {
    setDefaultLocalesRule.set(LOCALE_LIST_2);
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, false);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG_2, MANIFEST_URL_2);

    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadManifest(MANIFEST_URL_2))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO_2));

    modelFile.createNewFile();
    modelFile2.createNewFile();
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO_2))
        .thenReturn(Futures.immediateFuture(modelFile2));
    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    verifySucceededDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void oneDownloadFailed_multiLanguageSupportEnabled() throws Exception {
    setDefaultLocalesRule.set(LOCALE_LIST_2);
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, true);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG_2, MANIFEST_URL_2);

    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadManifest(MANIFEST_URL_2))
        .thenReturn(Futures.immediateFailedFuture(FAILED_TO_DOWNLOAD_EXCEPTION));

    modelFile.createNewFile();
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    // We assume we always download MODEL_TYPE first and then MODEL_TYPE_2, o/w this will be flaky
    when(clock.instant())
        .thenReturn(
            WORK_STARTED_TIME,
            DOWNLOAD_STARTED_TIME,
            DOWNLOAD_ENDED_TIME,
            DOWNLOAD_STARTED_TIME_2,
            DOWNLOAD_ENDED_TIME_2,
            WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.retry());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    List<TextClassifierDownloadReported> atoms = loggerTestRule.getLoggedDownloadReportedAtoms();
    assertThat(atoms).hasSize(2);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getUrlSuffix)
                .collect(Collectors.toList()))
        .containsExactly(MANIFEST_URL, MANIFEST_URL_2);
    assertThat(atoms.get(0).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(atoms.get(1).getDownloadStatus()).isEqualTo(DownloadStatus.FAILED_AND_RETRY);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getDownloadDurationMillis)
                .collect(Collectors.toList()))
        .containsExactly(DOWNLOAD_STARTED_TO_ENDED_MILLIS, DOWNLOAD_STARTED_TO_ENDED_2_MILLIS);
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.RETRY_MODEL_DOWNLOAD_FAILED);
  }

  @Test
  public void downloadSucceeded_multiLanguageSupportEnabled_checkLimit() throws Exception {
    setDefaultLocalesRule.set(LOCALE_LIST_3);
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, true);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG_2, MANIFEST_URL_2);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG_3, MANIFEST_URL_3);

    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadManifest(MANIFEST_URL_2))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO_2));
    when(modelDownloader.downloadManifest(MANIFEST_URL_3))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO_3));

    modelFile.createNewFile();
    modelFile2.createNewFile();
    modelFile3.createNewFile();

    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO_2))
        .thenReturn(Futures.immediateFuture(modelFile2));
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO_3))
        .thenReturn(Futures.immediateFuture(modelFile3));
    // We assume we always download MODEL_TYPE first and then MODEL_TYPE_2, o/w this will be flaky
    when(clock.instant())
        .thenReturn(
            WORK_STARTED_TIME,
            DOWNLOAD_STARTED_TIME,
            DOWNLOAD_ENDED_TIME,
            DOWNLOAD_STARTED_TIME_2,
            DOWNLOAD_ENDED_TIME_2,
            WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(modelFile2.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE))
        .containsExactly(modelFile, modelFile2);
    List<TextClassifierDownloadReported> atoms = loggerTestRule.getLoggedDownloadReportedAtoms();
    assertThat(atoms).hasSize(2);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getUrlSuffix)
                .collect(Collectors.toList()))
        .containsExactly(MANIFEST_URL, MANIFEST_URL_2);
    assertThat(atoms.get(0).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(atoms.get(1).getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(
            atoms.stream()
                .map(TextClassifierDownloadReported::getDownloadDurationMillis)
                .collect(Collectors.toList()))
        .containsExactly(DOWNLOAD_STARTED_TO_ENDED_MILLIS, DOWNLOAD_STARTED_TO_ENDED_2_MILLIS);
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  @Test
  public void downloadSucceed_multiLanguageSupportEnabled_onlyDownloadMultipleForEnabledModelType()
      throws Exception {
    setDefaultLocalesRule.set(LOCALE_LIST_2);
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, true);
    deviceConfig.setConfig(
        TextClassifierSettings.ENABLED_MODEL_TYPES_FOR_MULTI_LANGUAGE_SUPPORT, MODEL_TYPE_2);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    setUpManifestUrl(MODEL_TYPE, LOCALE_TAG_2, MANIFEST_URL_2);

    when(modelDownloader.downloadManifest(MANIFEST_URL))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO));
    when(modelDownloader.downloadManifest(MANIFEST_URL_2))
        .thenReturn(Futures.immediateFuture(MODEL_MANIFEST_PROTO_2));

    modelFile.createNewFile();
    modelFile2.createNewFile();

    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO))
        .thenReturn(Futures.immediateFuture(modelFile));
    when(modelDownloader.downloadModel(modelDownloaderDir, MODEL_PROTO_2))
        .thenReturn(Futures.immediateFuture(modelFile2));
    when(clock.instant())
        .thenReturn(WORK_STARTED_TIME, DOWNLOAD_STARTED_TIME, DOWNLOAD_ENDED_TIME, WORK_ENDED_TIME);

    ModelDownloadWorker worker = createWorker(RUN_ATTEMPT_COUNT);
    assertThat(worker.startWork().get()).isEqualTo(ListenableWorker.Result.success());
    assertThat(modelFile.exists()).isTrue();
    assertThat(downloadedModelManager.listModels(MODEL_TYPE)).containsExactly(modelFile);
    verifySucceededDownloadLogging();
    verifyWorkLogging(RUN_ATTEMPT_COUNT, WorkResult.SUCCESS_MODEL_DOWNLOADED);
  }

  private ModelDownloadWorker createWorker(int runAttemptCount) {
    return TestListenableWorkerBuilder.from(
            ApplicationProvider.getApplicationContext(), ModelDownloadWorker.class)
        .setRunAttemptCount(runAttemptCount)
        .setWorkerFactory(
            new WorkerFactory() {
              @Override
              public ListenableWorker createWorker(
                  Context appContext, String workerClassName, WorkerParameters workerParameters) {
                return new ModelDownloadWorker(
                    appContext,
                    workerParameters,
                    MoreExecutors.newDirectExecutorService(),
                    modelDownloader,
                    downloadedModelManager,
                    settings,
                    WORK_ID,
                    clock,
                    WORK_SCHEDULED_TIME.toEpochMilli());
              }
            })
        .build();
  }

  private void verifySucceededDownloadLogging() throws Exception {
    TextClassifierDownloadReported atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadReportedAtoms());
    assertThat(atom.getWorkId()).isEqualTo(WORK_ID);
    assertThat(atom.getDownloadStatus()).isEqualTo(DownloadStatus.SUCCEEDED);
    assertThat(atom.getModelType()).isEqualTo(MODEL_TYPE_ATOM);
    assertThat(atom.getUrlSuffix()).isEqualTo(MANIFEST_URL);
    assertThat(atom.getRunAttemptCount()).isEqualTo(RUN_ATTEMPT_COUNT);
    assertThat(atom.getFailureReason()).isEqualTo(FailureReason.UNKNOWN_FAILURE_REASON);
    assertThat(atom.getDownloadDurationMillis()).isEqualTo(DOWNLOAD_STARTED_TO_ENDED_MILLIS);
  }

  private void verifyFailedDownloadLogging() throws Exception {
    TextClassifierDownloadReported atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadReportedAtoms());
    assertThat(atom.getWorkId()).isEqualTo(WORK_ID);
    assertThat(atom.getDownloadStatus()).isEqualTo(DownloadStatus.FAILED_AND_RETRY);
    assertThat(atom.getModelType()).isEqualTo(MODEL_TYPE_ATOM);
    assertThat(atom.getUrlSuffix()).isEqualTo(MANIFEST_URL);
    assertThat(atom.getRunAttemptCount()).isEqualTo(RUN_ATTEMPT_COUNT);
    assertThat(atom.getFailureReason()).isEqualTo(FAILED_TO_DOWNLOAD_FAILURE_REASON);
    assertThat(atom.getDownloaderLibFailureCode())
        .isEqualTo(ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE);
    assertThat(atom.getDownloadDurationMillis()).isEqualTo(DOWNLOAD_STARTED_TO_ENDED_MILLIS);
  }

  private void verifyWorkLogging(int runTimeAttempt, WorkResult workResult) throws Exception {
    TextClassifierDownloadWorkCompleted atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadWorkCompletedAtoms());
    assertThat(atom.getWorkId()).isEqualTo(WORK_ID);
    assertThat(atom.getWorkResult()).isEqualTo(workResult);
    assertThat(atom.getRunAttemptCount()).isEqualTo(runTimeAttempt);
    assertThat(atom.getWorkScheduledToStartedDurationMillis())
        .isEqualTo(WORK_SCHEDULED_TO_STARTED_MILLIS);
    assertThat(atom.getWorkStartedToEndedDurationMillis()).isEqualTo(WORK_STARTED_TO_ENDED_MILLIS);
  }

  private void setUpManifestUrl(
      @ModelType.ModelTypeDef String modelType, String localeTag, String url) {
    String deviceConfigFlag =
        String.format(TextClassifierSettings.MANIFEST_URL_TEMPLATE, modelType, localeTag);
    deviceConfig.setConfig(deviceConfigFlag, url);
  }
}
