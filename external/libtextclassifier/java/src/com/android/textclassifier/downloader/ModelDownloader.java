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

import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;

/** Interface for downloading files from certain URI. */
interface ModelDownloader {

  /**
   * Downloads a manifest file from given url, parse it and return the proto.
   *
   * <p>The downloaded file should be deleted no matter the download succeeds or not.
   *
   * @param manifestUrl url to download manifest file from
   * @return listenable future of ModelManifest proto
   */
  ListenableFuture<ModelManifest> downloadManifest(String manifestUrl);

  /**
   * Downloads a model file and validate it based on given model info.
   *
   * <p>The file should be in the target folder. Returns the File if succeed. If the download or
   * validation fails, the unfinished model file should be cleaned up. Failures should be wrapped
   * inside a {@link ModelDownloadException} and throw.
   *
   * @param targetDir the target directory for the downloaded model
   * @param modelInfo the model information in manifest used for downloading and validation
   * @return the downloaded model file
   */
  ListenableFuture<File> downloadModel(File targetDir, ModelManifest.Model modelInfo);
}
