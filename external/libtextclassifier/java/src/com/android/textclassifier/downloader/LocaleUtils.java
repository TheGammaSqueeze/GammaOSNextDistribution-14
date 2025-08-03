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

import android.text.TextUtils;
import android.util.Pair;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.TextClassifierSettings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Locale;
import javax.annotation.Nullable;

/** Utilities for locale matching. */
final class LocaleUtils {
  @VisibleForTesting static final String UNIVERSAL_LOCALE_TAG = "universal";

  /**
   * Find the best locale tag as well as the configured manfiest url from device config.
   *
   * @param modelType the model type
   * @param targetLocale target locale
   * @param settings TextClassifierSettings to check device config
   * @return a pair of <bestLocaleTag, manfiestUrl>. Null if not found.
   */
  @Nullable
  static Pair<String, String> lookupBestLocaleTagAndManifestUrl(
      @ModelTypeDef String modelType, Locale targetLocale, TextClassifierSettings settings) {
    ImmutableMap<String, String> localeTagUrlMap =
        settings.getLanguageTagAndManifestUrlMap(modelType);
    Collection<String> allLocaleTags = localeTagUrlMap.keySet();
    String bestLocaleTag = lookupBestLocaleTag(targetLocale, allLocaleTags);
    if (bestLocaleTag == null) {
      return null;
    }
    String manifestUrl = localeTagUrlMap.get(bestLocaleTag);
    if (TextUtils.isEmpty(manifestUrl)) {
      return null;
    }
    return Pair.create(bestLocaleTag, manifestUrl);
  }
  /** Find the best locale tag for the target locale. Return null if no one is suitable. */
  @Nullable
  static String lookupBestLocaleTag(Locale targetLocale, Collection<String> availableTags) {
    // Notice: this lookup API just trys to match the longest prefix for the target locale tag.
    // Its implementation looks inefficient and the behavior may not be 100% desired. E.g. if the
    // target locale is en, and we only have en-uk in available tags, the current API returns null.
    String bestTag =
        Locale.lookupTag(Locale.LanguageRange.parse(targetLocale.toLanguageTag()), availableTags);
    if (bestTag != null) {
      return bestTag;
    }
    if (availableTags.contains(UNIVERSAL_LOCALE_TAG)) {
      return UNIVERSAL_LOCALE_TAG;
    }
    return null;
  }

  private LocaleUtils() {}
}
