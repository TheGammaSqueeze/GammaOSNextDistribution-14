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

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Effectively an enum class to represent types of files to be downloaded. */
final class DownloadFileType {
  /** File types to be downloaded for TextClassifier. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNKNOWN, MANIFEST, MODEL})
  public @interface DownloadFileTypeDef {}

  public static final int UNKNOWN = 0;
  public static final int MANIFEST = 1;
  public static final int MODEL = 2;

  private DownloadFileType() {}
}
