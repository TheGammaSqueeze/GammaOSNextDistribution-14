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

import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassification.Request;
import androidx.test.filters.FlakyTest;
import com.android.textclassifier.testing.ExtServicesTextClassifierRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModelDownloaderIntegrationTest {
  private static final String TAG = "ModelDownloaderTest";
  private static final String EXPERIMENTAL_EN_ANNOTATOR_MANIFEST_URL =
      "https://www.gstatic.com/android/text_classifier/r/experimental/v999999999/en.fb.manifest";
  private static final String EXPERIMENTAL_EN_TAG = "en_v999999999";
  private static final String V804_EN_ANNOTATOR_MANIFEST_URL =
      "https://www.gstatic.com/android/text_classifier/r/v804/en.fb.manifest";
  private static final String V804_RU_ANNOTATOR_MANIFEST_URL =
      "https://www.gstatic.com/android/text_classifier/r/v804/ru.fb.manifest";
  private static final String V804_EN_TAG = "en_v804";
  private static final String V804_RU_TAG = "ru_v804";
  private static final String FACTORY_MODEL_TAG = "*";
  private static final int ASSERT_MAX_ATTEMPTS = 20;
  private static final int ASSERT_SLEEP_BEFORE_RETRY_MS = 1000;

  @Rule
  public final ExtServicesTextClassifierRule extServicesTextClassifierRule =
      new ExtServicesTextClassifierRule();

  @Before
  public void setup() throws Exception {
    extServicesTextClassifierRule.addDeviceConfigOverride("config_updater_model_enabled", "false");
    extServicesTextClassifierRule.addDeviceConfigOverride("model_download_manager_enabled", "true");
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "model_download_backoff_delay_in_millis", "5");
    extServicesTextClassifierRule.addDeviceConfigOverride("testing_locale_list_override", "en-US");
    extServicesTextClassifierRule.overrideDeviceConfig();

    extServicesTextClassifierRule.enableVerboseLogging();
    // Verbose logging only takes effect after restarting ExtServices
    extServicesTextClassifierRule.forceStopExtServices();
  }

  @After
  public void tearDown() throws Exception {
    // This is to reset logging/locale_override for ExtServices.
    extServicesTextClassifierRule.forceStopExtServices();
  }

  @Test
  @FlakyTest(bugId = 284901878)
  public void smokeTest() throws Exception {
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "manifest_url_annotator_en", V804_EN_ANNOTATOR_MANIFEST_URL);

    assertWithRetries(() -> verifyActiveEnglishModel(V804_EN_TAG));
  }

  @Test
  @FlakyTest(bugId = 284901878)
  public void downgradeModel() throws Exception {
    // Download an experimental model.
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "manifest_url_annotator_en", EXPERIMENTAL_EN_ANNOTATOR_MANIFEST_URL);

    assertWithRetries(() -> verifyActiveEnglishModel(EXPERIMENTAL_EN_TAG));

    // Downgrade to an older model.
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "manifest_url_annotator_en", V804_EN_ANNOTATOR_MANIFEST_URL);

    assertWithRetries(() -> verifyActiveEnglishModel(V804_EN_TAG));
  }

  @Test
  @FlakyTest(bugId = 284901878)
  public void upgradeModel() throws Exception {
    // Download a model.
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "manifest_url_annotator_en", V804_EN_ANNOTATOR_MANIFEST_URL);

    assertWithRetries(() -> verifyActiveEnglishModel(V804_EN_TAG));

    // Upgrade to an experimental model.
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "manifest_url_annotator_en", EXPERIMENTAL_EN_ANNOTATOR_MANIFEST_URL);

    assertWithRetries(() -> verifyActiveEnglishModel(EXPERIMENTAL_EN_TAG));
  }

  @Test
  @FlakyTest(bugId = 284901878)
  public void clearFlag() throws Exception {
    // Download a new model.
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "manifest_url_annotator_en", EXPERIMENTAL_EN_ANNOTATOR_MANIFEST_URL);

    assertWithRetries(() -> verifyActiveEnglishModel(EXPERIMENTAL_EN_TAG));

    // Revert the flag.
    extServicesTextClassifierRule.addDeviceConfigOverride("manifest_url_annotator_en", "");
    // Fallback to use the universal model.
    assertWithRetries(
        () -> verifyActiveModel(/* text= */ "abc", /* expectedVersion= */ FACTORY_MODEL_TAG));
  }

  @Test
  @FlakyTest(bugId = 267344737)
  public void modelsForMultipleLanguagesDownloaded() throws Exception {
    extServicesTextClassifierRule.addDeviceConfigOverride("multi_language_support_enabled", "true");
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "testing_locale_list_override", "en-US,ru-RU");

    // download en model
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "manifest_url_annotator_en", EXPERIMENTAL_EN_ANNOTATOR_MANIFEST_URL);

    // download ru model
    extServicesTextClassifierRule.addDeviceConfigOverride(
        "manifest_url_annotator_ru", V804_RU_ANNOTATOR_MANIFEST_URL);
    assertWithRetries(() -> verifyActiveEnglishModel(EXPERIMENTAL_EN_TAG));

    assertWithRetries(this::verifyActiveRussianModel);

    assertWithRetries(
        () -> verifyActiveModel(/* text= */ "français", /* expectedVersion= */ FACTORY_MODEL_TAG));
  }

  private void assertWithRetries(Runnable assertRunnable) throws Exception {
    for (int i = 0; i < ASSERT_MAX_ATTEMPTS; i++) {
      try {
        extServicesTextClassifierRule.overrideDeviceConfig();
        assertRunnable.run();
        break; // success. Bail out.
      } catch (AssertionError ex) {
        if (i == ASSERT_MAX_ATTEMPTS - 1) { // last attempt, give up.
          extServicesTextClassifierRule.dumpDefaultTextClassifierService();
          throw ex;
        } else {
          Thread.sleep(ASSERT_SLEEP_BEFORE_RETRY_MS);
        }
      } catch (Exception unknownException) {
        throw unknownException;
      }
    }
  }

  private void verifyActiveModel(String text, String expectedVersion) {
    TextClassification textClassification =
        extServicesTextClassifierRule
            .getTextClassifier()
            .classifyText(new Request.Builder(text, 0, text.length()).build());
    // The result id contains the name of the just used model.
    assertThat(textClassification.getId()).contains(expectedVersion);
  }

  private void verifyActiveEnglishModel(String expectedVersion) {
    verifyActiveModel("abc", expectedVersion);
  }

  private void verifyActiveRussianModel() {
    verifyActiveModel("привет", V804_RU_TAG);
  }
}
