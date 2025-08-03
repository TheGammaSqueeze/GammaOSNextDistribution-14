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

import android.util.Pair;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.testing.TestingDeviceConfig;
import com.google.common.collect.ImmutableList;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LocaleUtilsTest {
  private static final String MODEL_TYPE = ModelType.ANNOTATOR;

  private TestingDeviceConfig deviceConfig;
  private TextClassifierSettings settings;

  @Before
  public void setUp() {
    deviceConfig = new TestingDeviceConfig();
    settings = new TextClassifierSettings(deviceConfig, /* isWear= */ false);
  }

  @Test
  public void lookupBestLocaleTag_simpleMatch() {
    assertThat(
            LocaleUtils.lookupBestLocaleTag(
                Locale.forLanguageTag("en"), ImmutableList.of("en", "zh")))
        .isEqualTo("en");
  }

  @Test
  public void lookupBestLocaleTag_noMatch() {
    assertThat(LocaleUtils.lookupBestLocaleTag(Locale.forLanguageTag("en"), ImmutableList.of("zh")))
        .isNull();
    assertThat(
            LocaleUtils.lookupBestLocaleTag(Locale.forLanguageTag("en"), ImmutableList.of("en-uk")))
        .isNull();
    assertThat(
            LocaleUtils.lookupBestLocaleTag(
                Locale.forLanguageTag("en-US"), ImmutableList.of("en-uk")))
        .isNull();
  }

  @Test
  public void lookupBestLocaleTag_partialMatch() {
    assertThat(
            LocaleUtils.lookupBestLocaleTag(
                Locale.forLanguageTag("en-US"), ImmutableList.of("en", "zh")))
        .isEqualTo("en");
    assertThat(
            LocaleUtils.lookupBestLocaleTag(
                Locale.forLanguageTag("en-US"), ImmutableList.of("en", "en-us")))
        .isEqualTo("en-us");
    assertThat(
            LocaleUtils.lookupBestLocaleTag(
                Locale.forLanguageTag("en-US"), ImmutableList.of("en", "en-uk")))
        .isEqualTo("en");
  }

  @Test
  public void lookupBestLocaleTag_universalMatch() {
    assertThat(
            LocaleUtils.lookupBestLocaleTag(
                Locale.forLanguageTag("en"),
                ImmutableList.of("zh", LocaleUtils.UNIVERSAL_LOCALE_TAG)))
        .isEqualTo(LocaleUtils.UNIVERSAL_LOCALE_TAG);
  }

  @Test
  public void lookupBestLocaleTagAndManifestUrl_found() throws Exception {
    setUpManifestUrl(MODEL_TYPE, "en", "url_1");
    Pair<String, String> pair =
        LocaleUtils.lookupBestLocaleTagAndManifestUrl(
            MODEL_TYPE, Locale.forLanguageTag("en"), settings);
    assertThat(pair.first).isEqualTo("en");
    assertThat(pair.second).isEqualTo("url_1");
  }

  @Test
  public void lookupBestLocaleTagAndManifestUrl_notFound() throws Exception {
    Pair<String, String> pair =
        LocaleUtils.lookupBestLocaleTagAndManifestUrl(
            MODEL_TYPE, Locale.forLanguageTag("en"), settings);
    assertThat(pair).isNull();
  }

  private void setUpManifestUrl(
      @ModelType.ModelTypeDef String modelType, String localeTag, String url) {
    String deviceConfigFlag =
        String.format(TextClassifierSettings.MANIFEST_URL_TEMPLATE, modelType, localeTag);
    deviceConfig.setConfig(deviceConfigFlag, url);
  }
}
