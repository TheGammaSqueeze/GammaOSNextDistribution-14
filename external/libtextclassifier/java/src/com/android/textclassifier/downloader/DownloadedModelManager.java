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

import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Manifest;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ManifestEnrollment;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Model;
import com.android.textclassifier.utils.IndentingPrintWriter;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

// TODO(licha): Let Worker access DB class directly, then we can make this a lister interface
/** An interface to provide easy access to DownloadedModelDatabase. */
public interface DownloadedModelManager {

  /** Returns the directory containing models downloaded by the downloader. */
  File getModelDownloaderDir();

  /**
   * Returns all downloaded model files for the given modelType
   *
   * <p>This method should return quickly as it may be on the critical path of serving requests.
   *
   * @param modelType the type of the model
   * @return the model files. Empty if no suitable model found
   */
  @Nullable
  List<File> listModels(@ModelTypeDef String modelType);

  /**
   * Returns the model entry if the model represented by the url is in our database.
   *
   * @param modelUrl the model url
   * @return model entry from internal database, null if not exist
   */
  @Nullable
  Model getModel(String modelUrl);

  /**
   * Returns the manifest entry if the manifest represented by the url is in our database.
   *
   * @param manifestUrl the manifest url
   * @return manifest entry from internal database, null if not exist
   */
  @Nullable
  Manifest getManifest(String manifestUrl);

  /**
   * Returns the manifest enrollment entry if a manifest is registered for the given type and
   * locale.
   *
   * @param modelType the model type of the enrollment
   * @param localeTag the locale tag of the enrollment
   * @return manifest enrollment entry from internal database, null if not exist
   */
  @Nullable
  ManifestEnrollment getManifestEnrollment(@ModelTypeDef String modelType, String localeTag);

  /**
   * Add a newly downloaded model to the internal database.
   *
   * <p>The model must be linked to a manifest via #registerManifest(). Otherwise it will be cleaned
   * up automatically later.
   *
   * @param modelUrl the url where we downloaded model from
   * @param modelPath the path where we store the downloaded model
   */
  void registerModel(String modelUrl, String modelPath);

  /**
   * Add a newly downloaded manifest to the internal database.
   *
   * <p>The manifest must be linked to a specific use case via #registerManifestEnrollment().
   * Otherwise it will be cleaned up automatically later. Currently there is only one model in one
   * manifest.
   *
   * @param manifestUrl the url where we downloaded manifest
   * @param modelUrl the url where we downloaded the only model inside the manifest
   */
  void registerManifest(String manifestUrl, String modelUrl);

  /**
   * Add a failure records for the given manifest url.
   *
   * <p>If the manifest failed before, then increase the prevFailureCounts by one. We skip manifest
   * if it failed too many times before.
   *
   * @param manifestUrl the failed manifest url
   */
  void registerManifestDownloadFailure(String manifestUrl);

  /**
   * Link a manifest to a specific (modelType, localeTag) use case.
   *
   * <p>After this registration, we will start to use this model file for all requests for the given
   * locale and the specified model type.
   *
   * @param modelType the model type
   * @param localeTag the tag of the locale on user's device that this manifest should be used for
   * @param manifestUrl the url of the manifest
   */
  void registerManifestEnrollment(
      @ModelTypeDef String modelType, String localeTag, String manifestUrl);

  /**
   * Clean up unused downloaded models and update other internal states.
   *
   * @param manifestsToDownload Map<modelType, manifestsToDownloadMyType> that the worker tried to
   *     download
   */
  void onDownloadCompleted(ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload);

  /**
   * Dumps the internal state for debugging.
   *
   * @param printWriter writer to write dumped states
   */
  void dump(IndentingPrintWriter printWriter);
}
