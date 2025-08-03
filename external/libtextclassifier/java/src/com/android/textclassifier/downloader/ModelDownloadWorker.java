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

import static java.lang.Math.min;

import android.content.Context;
import android.os.LocaleList;
import android.util.ArrayMap;
import android.util.Pair;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.TextClassifierServiceExecutors;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Manifest;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ManifestEnrollment;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Model;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Locale;

/** The WorkManager worker to download models for TextClassifierService. */
public final class ModelDownloadWorker extends ListenableWorker {
  private static final String TAG = "ModelDownloadWorker";

  public static final String INPUT_DATA_KEY_WORK_ID = "ModelDownloadWorker_workId";
  public static final String INPUT_DATA_KEY_SCHEDULED_TIMESTAMP =
      "ModelDownloadWorker_scheduledTimestamp";

  private final ListeningExecutorService executorService;
  private final ModelDownloader downloader;
  private final DownloadedModelManager downloadedModelManager;
  private final TextClassifierSettings settings;

  private final long workId;

  private final Clock clock;
  private final long workScheduledTimeMillis;

  private final Object lock = new Object();

  private long workStartedTimeMillis = 0;

  @GuardedBy("lock")
  private final ArrayMap<String, ListenableFuture<Void>> pendingDownloads;

  private ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload;

  public ModelDownloadWorker(Context context, WorkerParameters workerParams) {
    super(context, workerParams);
    this.executorService = TextClassifierServiceExecutors.getDownloaderExecutor();
    this.downloader = new ModelDownloaderImpl(context, executorService);
    this.downloadedModelManager = DownloadedModelManagerImpl.getInstance(context);
    this.settings = new TextClassifierSettings(context);
    this.pendingDownloads = new ArrayMap<>();
    this.manifestsToDownload = null;

    this.workId = workerParams.getInputData().getLong(INPUT_DATA_KEY_WORK_ID, 0);
    this.workScheduledTimeMillis =
        workerParams.getInputData().getLong(INPUT_DATA_KEY_SCHEDULED_TIMESTAMP, 0);
    this.clock = Clock.systemUTC();
  }

  @VisibleForTesting
  ModelDownloadWorker(
      Context context,
      WorkerParameters workerParams,
      ListeningExecutorService executorService,
      ModelDownloader modelDownloader,
      DownloadedModelManager downloadedModelManager,
      TextClassifierSettings settings,
      long workId,
      Clock clock,
      long workScheduledTimeMillis) {
    super(context, workerParams);
    this.executorService = executorService;
    this.downloader = modelDownloader;
    this.downloadedModelManager = downloadedModelManager;
    this.settings = settings;
    this.pendingDownloads = new ArrayMap<>();
    this.manifestsToDownload = null;
    this.workId = workId;
    this.clock = clock;
    this.workScheduledTimeMillis = workScheduledTimeMillis;
  }

  @Override
  public final ListenableFuture<ListenableWorker.Result> startWork() {
    TcLog.d(TAG, "Start download work...");
    workStartedTimeMillis = getCurrentTimeMillis();
    // Notice: startWork() is invoked on the main thread
    if (!settings.isModelDownloadManagerEnabled()) {
      TcLog.e(TAG, "Model Downloader is disabled. Abort the work.");
      logDownloadWorkCompleted(
          TextClassifierDownloadLogger.WORK_RESULT_FAILURE_MODEL_DOWNLOADER_DISABLED);
      return Futures.immediateFuture(ListenableWorker.Result.failure());
    }
    if (getRunAttemptCount() >= settings.getModelDownloadWorkerMaxAttempts()) {
      TcLog.d(TAG, "Max attempt reached. Abort download work.");
      logDownloadWorkCompleted(
          TextClassifierDownloadLogger.WORK_RESULT_FAILURE_MAX_RUN_ATTEMPT_REACHED);
      return Futures.immediateFuture(ListenableWorker.Result.failure());
    }

    return FluentFuture.from(Futures.submitAsync(this::checkAndDownloadModels, executorService))
        .transform(
            downloadResult -> {
              Preconditions.checkNotNull(manifestsToDownload);
              downloadedModelManager.onDownloadCompleted(manifestsToDownload);
              TcLog.d(TAG, "Download work completed: " + downloadResult);
              if (downloadResult.failureCount() == 0) {
                logDownloadWorkCompleted(
                    downloadResult.successCount() > 0
                        ? TextClassifierDownloadLogger.WORK_RESULT_SUCCESS_MODEL_DOWNLOADED
                        : TextClassifierDownloadLogger.WORK_RESULT_SUCCESS_NO_UPDATE_AVAILABLE);
                return ListenableWorker.Result.success();
              } else {
                logDownloadWorkCompleted(
                    TextClassifierDownloadLogger.WORK_RESULT_RETRY_MODEL_DOWNLOAD_FAILED);
                return ListenableWorker.Result.retry();
              }
            },
            executorService)
        .catching(
            Throwable.class,
            t -> {
              TcLog.e(TAG, "Unexpected Exception during downloading: ", t);
              logDownloadWorkCompleted(
                  TextClassifierDownloadLogger.WORK_RESULT_RETRY_RUNTIME_EXCEPTION);
              return ListenableWorker.Result.retry();
            },
            executorService);
  }

  /**
   * Checks device settings and returns the list of locales to download according to multi language
   * support settings. Guarantees that the primary locale goes first.
   */
  private ImmutableList<Locale> getLocalesToDownload() {
    LocaleList localeList = LocaleList.getAdjustedDefault();
    Locale primaryLocale = localeList.get(0);
    if (!settings.isMultiLanguageSupportEnabled()) {
      return ImmutableList.of(primaryLocale);
    }
    ImmutableList.Builder<Locale> localesToDownloadBuilder = ImmutableList.builder();
    int size = min(settings.getMultiLanguageModelsLimit(), localeList.size());
    for (int i = 0; i < size; i++) {
      localesToDownloadBuilder.add(localeList.get(i));
    }
    return localesToDownloadBuilder.build();
  }

  /**
   * Returns list of locales to download from {@code localeList} for the given {@code modelType}.
   */
  private ImmutableList<Locale> getLocalesToDownloadByType(
      ImmutableList<Locale> localeList, @ModelTypeDef String modelType) {
    if (!settings.getEnabledModelTypesForMultiLanguageSupport().contains(modelType)) {
      return ImmutableList.of(Locale.getDefault());
    }
    return localeList;
  }

  /**
   * Check device config and dispatch download tasks for all modelTypes.
   *
   * <p>Download tasks will be combined and logged after completion. Return true if all tasks
   * succeeded
   */
  private ListenableFuture<DownloadResult> checkAndDownloadModels() {
    ImmutableList<Locale> localesToDownload = getLocalesToDownload();
    ArrayList<ListenableFuture<Boolean>> downloadResultFutures = new ArrayList<>();
    ImmutableMap.Builder<String, ManifestsToDownloadByType> manifestsToDownloadBuilder =
        ImmutableMap.builder();
    for (String modelType : ModelType.values()) {
      ImmutableList<Locale> localesToDownloadByType =
          getLocalesToDownloadByType(localesToDownload, modelType);
      ImmutableMap.Builder<String, String> localeTagToManifestUrlBuilder = ImmutableMap.builder();
      for (Locale locale : localesToDownloadByType) {
        Pair<String, String> bestLocaleTagAndManifestUrl =
            LocaleUtils.lookupBestLocaleTagAndManifestUrl(modelType, locale, settings);
        if (bestLocaleTagAndManifestUrl == null) {
          TcLog.w(
              TAG,
              String.format(
                  Locale.US, "No suitable manifest for %s, %s", modelType, locale.toLanguageTag()));
          continue;
        }
        String bestLocaleTag = bestLocaleTagAndManifestUrl.first;
        String manifestUrl = bestLocaleTagAndManifestUrl.second;
        localeTagToManifestUrlBuilder.put(bestLocaleTag, manifestUrl);
        TcLog.d(
            TAG,
            String.format(
                Locale.US,
                "model type: %s, current locale tag: %s, best locale tag: %s, manifest url: %s",
                modelType,
                locale.toLanguageTag(),
                bestLocaleTag,
                manifestUrl));
        if (!shouldDownloadManifest(modelType, bestLocaleTag, manifestUrl)) {
          continue;
        }
        downloadResultFutures.add(
            downloadManifestAndRegister(modelType, bestLocaleTag, manifestUrl));
      }
      manifestsToDownloadBuilder.put(
          modelType,
          ManifestsToDownloadByType.create(localeTagToManifestUrlBuilder.buildOrThrow()));
    }
    manifestsToDownload = manifestsToDownloadBuilder.buildOrThrow();

    return Futures.whenAllComplete(downloadResultFutures)
        .call(
            () -> {
              TcLog.d(TAG, "All Download Tasks Completed");
              int successCount = 0;
              int failureCount = 0;
              for (ListenableFuture<Boolean> downloadResultFuture : downloadResultFutures) {
                if (Futures.getDone(downloadResultFuture)) {
                  successCount += 1;
                } else {
                  failureCount += 1;
                }
              }
              return DownloadResult.create(successCount, failureCount);
            },
            executorService);
  }

  private boolean shouldDownloadManifest(
      @ModelTypeDef String modelType, String localeTag, String manifestUrl) {
    Manifest downloadedManifest = downloadedModelManager.getManifest(manifestUrl);
    if (downloadedManifest == null) {
      return true;
    }
    if (downloadedManifest.getStatus() == Manifest.STATUS_FAILED) {
      if (downloadedManifest.getFailureCounts() >= settings.getManifestDownloadMaxAttempts()) {
        TcLog.w(
            TAG,
            String.format(
                Locale.US,
                "Manifest failed too many times, stop retrying: %s %d",
                manifestUrl,
                downloadedManifest.getFailureCounts()));
        return false;
      } else {
        return true;
      }
    }
    ManifestEnrollment manifestEnrollment =
        downloadedModelManager.getManifestEnrollment(modelType, localeTag);
    return manifestEnrollment == null || !manifestUrl.equals(manifestEnrollment.getManifestUrl());
  }

  /**
   * Downloads a single manifest and models configured inside it.
   *
   * <p>The returned future should always resolve to a ManifestDownloadResult as we catch all
   * exceptions.
   */
  private ListenableFuture<Boolean> downloadManifestAndRegister(
      @ModelTypeDef String modelType, String localeTag, String manifestUrl) {
    long downloadStartTimestamp = getCurrentTimeMillis();
    return FluentFuture.from(downloadManifest(manifestUrl))
        .transform(
            unused -> {
              downloadedModelManager.registerManifestEnrollment(modelType, localeTag, manifestUrl);
              TextClassifierDownloadLogger.downloadSucceeded(
                  workId,
                  modelType,
                  manifestUrl,
                  getRunAttemptCount(),
                  getCurrentTimeMillis() - downloadStartTimestamp);
              TcLog.d(TAG, "Manifest downloaded and registered: " + manifestUrl);
              return true;
            },
            executorService)
        .catching(
            Throwable.class,
            t -> {
              downloadedModelManager.registerManifestDownloadFailure(manifestUrl);
              int errorCode = ModelDownloadException.UNKNOWN_FAILURE_REASON;
              int downloaderLibErrorCode = 0;
              if (t instanceof ModelDownloadException) {
                ModelDownloadException mde = (ModelDownloadException) t;
                errorCode = mde.getErrorCode();
                downloaderLibErrorCode = mde.getDownloaderLibErrorCode();
              }
              TcLog.e(TAG, "Failed to download manfiest: " + manifestUrl, t);
              TextClassifierDownloadLogger.downloadFailed(
                  workId,
                  modelType,
                  manifestUrl,
                  errorCode,
                  getRunAttemptCount(),
                  downloaderLibErrorCode,
                  getCurrentTimeMillis() - downloadStartTimestamp);
              return false;
            },
            executorService);
  }

  // Download a manifest and its models, and register it to Manifest table.
  private ListenableFuture<Void> downloadManifest(String manifestUrl) {
    synchronized (lock) {
      Manifest downloadedManifest = downloadedModelManager.getManifest(manifestUrl);
      if (downloadedManifest != null
          && downloadedManifest.getStatus() == Manifest.STATUS_SUCCEEDED) {
        TcLog.d(TAG, "Manifest already downloaded: " + manifestUrl);
        return Futures.immediateVoidFuture();
      }
      if (pendingDownloads.containsKey(manifestUrl)) {
        return pendingDownloads.get(manifestUrl);
      }
      ListenableFuture<Void> manfiestDownloadFuture =
          FluentFuture.from(downloader.downloadManifest(manifestUrl))
              .transformAsync(
                  manifest -> {
                    ModelManifest.Model modelInfo = manifest.getModels(0);
                    return Futures.transform(
                        downloadModel(modelInfo), unused -> modelInfo, executorService);
                  },
                  executorService)
              .transform(
                  modelInfo -> {
                    downloadedModelManager.registerManifest(manifestUrl, modelInfo.getUrl());
                    return null;
                  },
                  executorService);
      pendingDownloads.put(manifestUrl, manfiestDownloadFuture);
      return manfiestDownloadFuture;
    }
  }

  // Download a model and register it into Model table.
  private ListenableFuture<Void> downloadModel(ModelManifest.Model modelInfo) {
    String modelUrl = modelInfo.getUrl();
    synchronized (lock) {
      Model downloadedModel = downloadedModelManager.getModel(modelUrl);
      if (downloadedModel != null) {
        TcLog.d(TAG, "Model file already exists: " + downloadedModel.getModelPath());
        return Futures.immediateVoidFuture();
      }
      if (pendingDownloads.containsKey(modelUrl)) {
        return pendingDownloads.get(modelUrl);
      }
      ListenableFuture<Void> modelDownloadFuture =
          FluentFuture.from(
                  downloader.downloadModel(
                      downloadedModelManager.getModelDownloaderDir(), modelInfo))
              .transform(
                  modelFile -> {
                    downloadedModelManager.registerModel(modelUrl, modelFile.getAbsolutePath());
                    TcLog.d(TAG, "Model File downloaded: " + modelUrl);
                    return null;
                  },
                  executorService);
      pendingDownloads.put(modelUrl, modelDownloadFuture);
      return modelDownloadFuture;
    }
  }

  /**
   * This method will be called when we our work gets interrupted by the system. Result future
   * should have already been cancelled in that case. Unless it's because the REPLACE policy of
   * WorkManager unique queue, the interrupted work will be rescheduled later.
   */
  @Override
  public final void onStopped() {
    TcLog.d(TAG, String.format(Locale.US, "Stop download. Attempt:%d", getRunAttemptCount()));
    logDownloadWorkCompleted(TextClassifierDownloadLogger.WORK_RESULT_RETRY_STOPPED_BY_OS);
  }

  private long getCurrentTimeMillis() {
    return clock.instant().toEpochMilli();
  }

  private void logDownloadWorkCompleted(int workResult) {
    if (workStartedTimeMillis < workScheduledTimeMillis) {
      TcLog.w(
          TAG,
          String.format(
              Locale.US,
              "Bad workStartedTimeMillis: %d, workScheduledTimeMillis: %d",
              workStartedTimeMillis,
              workScheduledTimeMillis));
      workStartedTimeMillis = workScheduledTimeMillis;
    }
    TextClassifierDownloadLogger.downloadWorkCompleted(
        workId,
        workResult,
        getRunAttemptCount(),
        workStartedTimeMillis - workScheduledTimeMillis,
        getCurrentTimeMillis() - workStartedTimeMillis);
  }

  @AutoValue
  abstract static class DownloadResult {
    public abstract int successCount();

    public abstract int failureCount();

    public static DownloadResult create(int successCount, int failureCount) {
      return new AutoValue_ModelDownloadWorker_DownloadResult(successCount, failureCount);
    }
  }
}
