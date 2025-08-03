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

import android.content.Context;
import android.util.ArrayMap;
import androidx.annotation.GuardedBy;
import androidx.room.Room;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.TextClassifierServiceExecutors;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Manifest;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ManifestEnrollment;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Model;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ModelView;
import com.android.textclassifier.utils.IndentingPrintWriter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** A singleton implementation of DownloadedModelManager. */
public final class DownloadedModelManagerImpl implements DownloadedModelManager {
  private static final String TAG = "DownloadedModelManagerImpl";
  private static final String DOWNLOAD_SUB_DIR_NAME = "textclassifier/downloads/models";
  private static final String DOWNLOADED_MODEL_DATABASE_NAME = "tcs-downloaded-model-db";

  private static final Object staticLock = new Object();

  @GuardedBy("staticLock")
  private static DownloadedModelManagerImpl instance;

  private final File modelDownloaderDir;
  private final DownloadedModelDatabase db;
  private final TextClassifierSettings settings;

  private final Object cacheLock = new Object();

  // modeltype -> downloaded model files
  @GuardedBy("cacheLock")
  private final ArrayMap<String, List<Model>> modelLookupCache;

  @GuardedBy("cacheLock")
  private boolean cacheInitialized;

  @Nullable
  public static DownloadedModelManager getInstance(Context context) {
    synchronized (staticLock) {
      if (instance == null) {
        DownloadedModelDatabase db =
            Room.databaseBuilder(
                    context, DownloadedModelDatabase.class, DOWNLOADED_MODEL_DATABASE_NAME)
                .build();
        File modelDownloaderDir = new File(context.getFilesDir(), DOWNLOAD_SUB_DIR_NAME);
        instance =
            new DownloadedModelManagerImpl(
                db, modelDownloaderDir, new TextClassifierSettings(context));
      }
      return instance;
    }
  }

  @VisibleForTesting
  static DownloadedModelManagerImpl getInstanceForTesting(
      DownloadedModelDatabase db, File modelDownloaderDir, TextClassifierSettings settings) {
    return new DownloadedModelManagerImpl(db, modelDownloaderDir, settings);
  }

  private DownloadedModelManagerImpl(
      DownloadedModelDatabase db, File modelDownloaderDir, TextClassifierSettings settings) {
    this.db = db;
    this.modelDownloaderDir = modelDownloaderDir;
    this.modelLookupCache = new ArrayMap<>();
    for (String modelType : ModelType.values()) {
      this.modelLookupCache.put(modelType, new ArrayList<>());
    }
    this.settings = settings;
    this.cacheInitialized = false;
  }

  @Override
  public File getModelDownloaderDir() {
    if (!modelDownloaderDir.exists()) {
      modelDownloaderDir.mkdirs();
    }
    return modelDownloaderDir;
  }

  @Override
  @Nullable
  public ImmutableList<File> listModels(@ModelTypeDef String modelType) {
    synchronized (cacheLock) {
      if (!cacheInitialized) {
        updateCache();
      }
      ImmutableList.Builder<File> builder = ImmutableList.builder();
      ImmutableList<String> blockedModels = settings.getModelUrlBlocklist();
      for (Model model : modelLookupCache.get(modelType)) {
        if (blockedModels.contains(model.getModelUrl())) {
          TcLog.d(TAG, "Model is blocklisted: " + model);
          continue;
        }
        builder.add(new File(model.getModelPath()));
      }
      return builder.build();
    }
  }

  @Override
  @Nullable
  public Model getModel(String modelUrl) {
    List<Model> models = db.dao().queryModelWithModelUrl(modelUrl);
    return Iterables.getFirst(models, null);
  }

  @Override
  @Nullable
  public Manifest getManifest(String manifestUrl) {
    List<Manifest> manifests = db.dao().queryManifestWithManifestUrl(manifestUrl);
    return Iterables.getFirst(manifests, null);
  }

  @Override
  @Nullable
  public ManifestEnrollment getManifestEnrollment(
      @ModelTypeDef String modelType, String localeTag) {
    List<ManifestEnrollment> manifestEnrollments =
        db.dao().queryManifestEnrollmentWithModelTypeAndLocaleTag(modelType, localeTag);
    return Iterables.getFirst(manifestEnrollments, null);
  }

  @Override
  public void registerModel(String modelUrl, String modelPath) {
    db.dao().insert(Model.create(modelUrl, modelPath));
  }

  @Override
  public void registerManifest(String manifestUrl, String modelUrl) {
    db.dao().insertManifestAndModelCrossRef(manifestUrl, modelUrl);
  }

  @Override
  public void registerManifestDownloadFailure(String manifestUrl) {
    db.dao().increaseManifestFailureCounts(manifestUrl);
  }

  @Override
  public void registerManifestEnrollment(
      @ModelTypeDef String modelType, String localeTag, String manifestUrl) {
    db.dao().insert(ManifestEnrollment.create(modelType, localeTag, manifestUrl));
  }

  @Override
  public void dump(IndentingPrintWriter printWriter) {
    printWriter.println("DownloadedModelManagerImpl:");
    printWriter.increaseIndent();
    db.dump(printWriter, TextClassifierServiceExecutors.getDownloaderExecutor());
    printWriter.println("ModelLookupCache:");
    synchronized (cacheLock) {
      for (Map.Entry<String, List<Model>> entry : modelLookupCache.entrySet()) {
        printWriter.println(entry.getKey());
        printWriter.increaseIndent();
        for (Model model : entry.getValue()) {
          printWriter.println(model.toString());
        }
        printWriter.decreaseIndent();
      }
    }
    printWriter.decreaseIndent();
  }

  @Override
  public void onDownloadCompleted(
      ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload) {
    TcLog.d(TAG, "Start to clean up models and update model lookup cache...");
    // Step 1: Clean up ManifestEnrollment table
    List<ManifestEnrollment> allManifestEnrollments = db.dao().queryAllManifestEnrollments();
    List<ManifestEnrollment> manifestEnrollmentsToDelete = new ArrayList<>();
    for (String modelType : ModelType.values()) {
      List<ManifestEnrollment> manifestEnrollmentsByType =
          allManifestEnrollments.stream()
              .filter(modelEnrollment -> modelEnrollment.getModelType().equals(modelType))
              .collect(Collectors.toList());
      ManifestsToDownloadByType manifestsToDownloadByType = manifestsToDownload.get(modelType);

      if (manifestsToDownloadByType == null) {
        // No suitable manifests configured for this model type. Delete everything.
        manifestEnrollmentsToDelete.addAll(manifestEnrollmentsByType);
        continue;
      }
      ImmutableMap<String, String> localeTagToManifestUrl =
          manifestsToDownloadByType.localeTagToManifestUrl();

      boolean allModelsDownloaded = true;
      for (Map.Entry<String, String> entry : localeTagToManifestUrl.entrySet()) {
        String localeTag = entry.getKey();
        String manifestUrl = entry.getValue();
        Optional<ManifestEnrollment> manifestEnrollmentForLocaleTagAndManifestUrl =
            manifestEnrollmentsByType.stream()
                .filter(
                    manifestEnrollment ->
                        manifestEnrollment.getLocaleTag().equals(localeTag)
                            && manifestEnrollment.getManifestUrl().equals(manifestUrl))
                .findAny();
        if (!manifestEnrollmentForLocaleTagAndManifestUrl.isPresent()) {
          // The desired manifest failed to be downloaded.
          TcLog.w(
              TAG,
              String.format(
                  "Desired manifest is missing on download completed: %s, %s, %s",
                  modelType, localeTag, manifestUrl));
          allModelsDownloaded = false;
        }
      }
      if (allModelsDownloaded) {
        // Delete unused manifest enrollments.
        manifestEnrollmentsToDelete.addAll(
            manifestEnrollmentsByType.stream()
                .filter(
                    manifestEnrollment ->
                        !manifestEnrollment
                            .getManifestUrl()
                            .equals(localeTagToManifestUrl.get(manifestEnrollment.getLocaleTag())))
                .collect(Collectors.toList()));
      } else {
        // TODO(licha): We may still need to delete models here. E.g. we are switching from en to
        // zh. Although we fail to download zh model, we still want to delete en models.
        TcLog.w(
            TAG, "Unused models were not deleted because downloading of at least one model failed");
      }
    }
    db.dao().deleteManifestEnrollments(manifestEnrollmentsToDelete);
    // Step 2: Clean up Manifests and Models that are not linked to any ManifestEnrollment
    db.dao().deleteUnusedManifestsAndModels();
    // Step 3: Clean up Manifest failure records
    // We only keep a failure record if the worker stills trys to download it
    // We restrict the deletion to failure records only because although some manifest urls are not
    // in allAttemptedManifestUrls, they can still be useful (e.g. current manifest is v901, and we
    // failed to download v902. v901 will not be in the map, but it should be kept.)
    List<String> allAttemptedManifestUrls =
        manifestsToDownload.entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().localeTagToManifestUrl().entrySet().stream()
                        .map(Map.Entry::getValue))
            .collect(Collectors.toList());
    db.dao().deleteUnusedManifestFailureRecords(allAttemptedManifestUrls);
    // Step 4: Update lookup cache
    updateCache();
    // Step 5: Clean up unused model files.
    Set<String> modelPathsToKeep =
        db.dao().queryAllModels().stream().map(Model::getModelPath).collect(Collectors.toSet());
    for (File modelFile : getModelDownloaderDir().listFiles()) {
      if (!modelPathsToKeep.contains(modelFile.getAbsolutePath())) {
        TcLog.d(TAG, "Delete model file: " + modelFile.getAbsolutePath());
        if (!modelFile.delete()) {
          TcLog.e(TAG, "Failed to delete model file: " + modelFile.getAbsolutePath());
        }
      }
    }
  }

  // Clear the cache table and rebuild the cache based on ModelView table
  private void updateCache() {
    synchronized (cacheLock) {
      TcLog.d(TAG, "Updating model lookup cache...");
      for (String modelType : ModelType.values()) {
        modelLookupCache.get(modelType).clear();
      }
      for (ModelView modelView : db.dao().queryAllModelViews()) {
        modelLookupCache
            .get(modelView.getManifestEnrollment().getModelType())
            .add(modelView.getModel());
      }
      cacheInitialized = true;
    }
  }
}
