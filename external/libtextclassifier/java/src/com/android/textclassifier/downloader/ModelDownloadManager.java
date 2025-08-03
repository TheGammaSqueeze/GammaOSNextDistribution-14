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

import static com.android.textclassifier.downloader.TextClassifierDownloadLogger.REASON_TO_SCHEDULE_DEVICE_CONFIG_UPDATED;
import static com.android.textclassifier.downloader.TextClassifierDownloadLogger.REASON_TO_SCHEDULE_LOCALE_SETTINGS_CHANGED;
import static com.android.textclassifier.downloader.TextClassifierDownloadLogger.REASON_TO_SCHEDULE_TCS_STARTED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.LocaleList;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkManager;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.utils.IndentingPrintWriter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Enums;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Manager to listen to config update and download latest models. */
public final class ModelDownloadManager {
  private static final String TAG = "ModelDownloadManager";

  @VisibleForTesting static final String UNIQUE_QUEUE_NAME = "ModelDownloadWorkManagerQueue";

  private final Context appContext;
  private final Class<? extends ListenableWorker> modelDownloadWorkerClass;
  private final Callable<WorkManager> workManagerSupplier;
  private final DownloadedModelManager downloadedModelManager;
  private final TextClassifierSettings settings;
  private final ListeningExecutorService executorService;
  private final DeviceConfig.OnPropertiesChangedListener deviceConfigListener;
  private final BroadcastReceiver localeChangedReceiver;

  /**
   * Constructor for ModelDownloadManager.
   *
   * @param appContext the context of this application
   * @param settings TextClassifierSettings to access DeviceConfig and other settings
   * @param executorService background executor service
   */
  public ModelDownloadManager(
      Context appContext,
      TextClassifierSettings settings,
      ListeningExecutorService executorService) {
    this(
        appContext,
        ModelDownloadWorker.class,
        () -> WorkManager.getInstance(appContext),
        DownloadedModelManagerImpl.getInstance(appContext),
        settings,
        executorService);
  }

  @VisibleForTesting
  public ModelDownloadManager(
      Context appContext,
      Class<? extends ListenableWorker> modelDownloadWorkerClass,
      Callable<WorkManager> workManagerSupplier,
      DownloadedModelManager downloadedModelManager,
      TextClassifierSettings settings,
      ListeningExecutorService executorService) {
    this.appContext = Preconditions.checkNotNull(appContext);
    this.modelDownloadWorkerClass = Preconditions.checkNotNull(modelDownloadWorkerClass);
    this.workManagerSupplier = Preconditions.checkNotNull(workManagerSupplier);
    this.downloadedModelManager = Preconditions.checkNotNull(downloadedModelManager);
    this.settings = Preconditions.checkNotNull(settings);
    this.executorService = Preconditions.checkNotNull(executorService);

    this.deviceConfigListener =
        new DeviceConfig.OnPropertiesChangedListener() {
          @Override
          public void onPropertiesChanged(DeviceConfig.Properties unused) {
            onTextClassifierDeviceConfigChanged();
          }
        };
    this.localeChangedReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            onLocaleChanged();
          }
        };
  }

  /** Returns the downlaoded models for the given modelType. */
  @Nullable
  public List<File> listDownloadedModels(@ModelTypeDef String modelType) {
    try {
      return downloadedModelManager.listModels(modelType);
    } catch (Throwable t) {
      TcLog.e(TAG, "Failed to list downloaded models", t);
      return ImmutableList.of();
    }
  }

  /** Notifies the model downlaoder that the text classifier service is created. */
  public void onTextClassifierServiceCreated() {
    try {
      DeviceConfig.addOnPropertiesChangedListener(
          DeviceConfig.NAMESPACE_TEXTCLASSIFIER, executorService, deviceConfigListener);
      appContext.registerReceiver(
          localeChangedReceiver, new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
      TcLog.d(TAG, "DeviceConfig listener and locale change listener are registered.");
      if (!settings.isModelDownloadManagerEnabled()) {
        return;
      }
      maybeOverrideLocaleListForTesting();
      TcLog.d(TAG, "Try to schedule model download work because TextClassifierService started.");
      scheduleDownloadWork(REASON_TO_SCHEDULE_TCS_STARTED);
    } catch (Throwable t) {
      TcLog.e(TAG, "Failed inside onTextClassifierServiceCreated", t);
    }
  }

  // TODO(licha): Make this private. Let the constructor accept a receiver to enable testing.
  /** Notifies the model downlaoder that the system locale setting is changed. */
  @VisibleForTesting
  void onLocaleChanged() {
    if (!settings.isModelDownloadManagerEnabled()) {
      return;
    }
    TcLog.d(TAG, "Try to schedule model download work because of system locale changes.");
    try {
      scheduleDownloadWork(REASON_TO_SCHEDULE_LOCALE_SETTINGS_CHANGED);
    } catch (Throwable t) {
      TcLog.e(TAG, "Failed inside onLocaleChanged", t);
    }
  }

  // TODO(licha): Make this private. Let the constructor accept a receiver to enable testing.
  /** Notifies the model downlaoder that the device config for textclassifier is changed. */
  @VisibleForTesting
  void onTextClassifierDeviceConfigChanged() {
    if (!settings.isModelDownloadManagerEnabled()) {
      return;
    }
    TcLog.d(TAG, "Try to schedule model download work because of device config changes.");
    try {
      maybeOverrideLocaleListForTesting();
      scheduleDownloadWork(REASON_TO_SCHEDULE_DEVICE_CONFIG_UPDATED);
    } catch (Throwable t) {
      TcLog.e(TAG, "Failed inside onTextClassifierDeviceConfigChanged", t);
    }
  }

  /** Clean up internal states on destroying. */
  public void destroy() {
    try {
      DeviceConfig.removeOnPropertiesChangedListener(deviceConfigListener);
      appContext.unregisterReceiver(localeChangedReceiver);
      TcLog.d(TAG, "DeviceConfig and Locale listener unregistered by ModelDownloadeManager");
    } catch (Throwable t) {
      TcLog.e(TAG, "Failed to destroy ModelDownloadManager", t);
    }
  }

  /**
   * Dumps the internal state for debugging.
   *
   * @param printWriter writer to write dumped states
   */
  public void dump(IndentingPrintWriter printWriter) {
    if (!settings.isModelDownloadManagerEnabled()) {
      return;
    }
    try {
      printWriter.println("ModelDownloadManager:");
      printWriter.increaseIndent();
      downloadedModelManager.dump(printWriter);
      printWriter.decreaseIndent();
    } catch (Throwable t) {
      TcLog.e(TAG, "Failed to dump ModelDownloadManager", t);
    }
  }

  /**
   * Enqueue an idempotent work to check device configs and download model files if necessary.
   *
   * <p>At any time there will only be at most one work running. If a work is already pending or
   * running, the newly scheduled work will be appended as a child of that work.
   */
  private void scheduleDownloadWork(int reasonToSchedule) {
    long workId =
        Hashing.farmHashFingerprint64().hashUnencodedChars(UUID.randomUUID().toString()).asLong();
    try {
      NetworkType networkType =
          Enums.getIfPresent(NetworkType.class, settings.getManifestDownloadRequiredNetworkType())
              .or(NetworkType.UNMETERED);
      OneTimeWorkRequest downloadRequest =
          new OneTimeWorkRequest.Builder(modelDownloadWorkerClass)
              .setConstraints(
                  new Constraints.Builder()
                      .setRequiredNetworkType(networkType)
                      .setRequiresBatteryNotLow(true)
                      .setRequiresStorageNotLow(true)
                      .setRequiresDeviceIdle(settings.getManifestDownloadRequiresDeviceIdle())
                      .setRequiresCharging(settings.getManifestDownloadRequiresCharging())
                      .build())
              .setBackoffCriteria(
                  BackoffPolicy.EXPONENTIAL,
                  settings.getModelDownloadBackoffDelayInMillis(),
                  MILLISECONDS)
              .setInputData(
                  new Data.Builder()
                      .putLong(ModelDownloadWorker.INPUT_DATA_KEY_WORK_ID, workId)
                      .putLong(
                          ModelDownloadWorker.INPUT_DATA_KEY_SCHEDULED_TIMESTAMP,
                          Instant.now().toEpochMilli())
                      .build())
              .build();
      ListenableFuture<Operation.State.SUCCESS> enqueueResultFuture =
          workManagerSupplier
              .call()
              .enqueueUniqueWork(
                  UNIQUE_QUEUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, downloadRequest)
              .getResult();
      Futures.addCallback(
          enqueueResultFuture,
          new FutureCallback<Operation.State.SUCCESS>() {
            @Override
            public void onSuccess(Operation.State.SUCCESS unused) {
              TcLog.d(TAG, "Download work scheduled.");
              TextClassifierDownloadLogger.downloadWorkScheduled(
                  workId, reasonToSchedule, /* failedToSchedule= */ false);
            }

            @Override
            public void onFailure(Throwable t) {
              TcLog.e(TAG, "Failed to schedule download work: ", t);
              TextClassifierDownloadLogger.downloadWorkScheduled(
                  workId, reasonToSchedule, /* failedToSchedule= */ true);
            }
          },
          executorService);
    } catch (Throwable t) {
      // TODO(licha): this is just for temporary fix. Refactor the try-catch in the future.
      TcLog.e(TAG, "Failed to schedule download work: ", t);
      TextClassifierDownloadLogger.downloadWorkScheduled(
          workId, reasonToSchedule, /* failedToSchedule= */ true);
    }
  }

  private void maybeOverrideLocaleListForTesting() {
    String localeList = settings.getTestingLocaleListOverride();
    if (TextUtils.isEmpty(localeList)) {
      return;
    }
    TcLog.d(
        TAG,
        String.format(
            Locale.US,
            "Override LocaleList from %s to %s",
            LocaleList.getAdjustedDefault().toLanguageTags(),
            localeList));
    LocaleList.setDefault(LocaleList.forLanguageTags(localeList));
  }
}
