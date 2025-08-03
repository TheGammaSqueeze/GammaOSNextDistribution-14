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

package com.android.textclassifier;

import android.os.LocaleList;
import com.android.textclassifier.common.ModelFile;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.utils.IndentingPrintWriter;
import javax.annotation.Nullable;

/**
 * Interface to list model files, find the best model file for a use case and dump internal state
 */
interface ModelFileManager {

  /**
   * Returns the best model file for the given localelist, {@code null} if nothing is found.
   *
   * @param modelType the type of model to look up (e.g. annotator, lang_id, etc.)
   * @param localePreferences an ordered list of user preferences for locales, use {@code null} if
   *     there is no preference.
   * @param detectedLocales an ordered list of locales detected from the Tcs request text, use
   *     {@code null} if no detected locales are provided
   */
  @Nullable
  ModelFile findBestModelFile(
      @ModelTypeDef String modelType,
      @Nullable LocaleList localePreferences,
      @Nullable LocaleList detectedLocales);

  /**
   * Dumps the internal state for debugging.
   *
   * @param printWriter writer to write dumped states
   */
  void dump(IndentingPrintWriter printWriter);
}
