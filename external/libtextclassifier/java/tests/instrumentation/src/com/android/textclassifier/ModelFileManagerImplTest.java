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

import static com.android.textclassifier.common.ModelFile.LANGUAGE_INDEPENDENT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.LocaleList;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.work.WorkManager;
import com.android.textclassifier.ModelFileManagerImpl.DownloaderModelsLister;
import com.android.textclassifier.ModelFileManagerImpl.RegularFileFullMatchLister;
import com.android.textclassifier.ModelFileManagerImpl.RegularFilePatternMatchLister;
import com.android.textclassifier.common.ModelFile;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.downloader.DownloadedModelManager;
import com.android.textclassifier.downloader.ModelDownloadManager;
import com.android.textclassifier.downloader.ModelDownloadWorker;
import com.android.textclassifier.testing.SetDefaultLocalesRule;
import com.android.textclassifier.testing.TestingDeviceConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ModelFileManagerImplTest {
  private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("en-US");

  @ModelTypeDef private static final String MODEL_TYPE = ModelType.ANNOTATOR;

  private TestingDeviceConfig deviceConfig;

  @Mock private DownloadedModelManager downloadedModelManager;

  @Rule public final SetDefaultLocalesRule setDefaultLocalesRule = new SetDefaultLocalesRule();
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private File rootTestDir;
  private ModelFileManagerImpl modelFileManager;
  private ModelDownloadManager modelDownloadManager;
  private TextClassifierSettings settings;

  @Before
  public void setup() {
    deviceConfig = new TestingDeviceConfig();
    rootTestDir =
        new File(ApplicationProvider.getApplicationContext().getCacheDir(), "rootTestDir");
    rootTestDir.mkdirs();
    Context context = ApplicationProvider.getApplicationContext();
    settings = new TextClassifierSettings(deviceConfig, /* isWear= */ false);
    modelDownloadManager =
        new ModelDownloadManager(
            context,
            ModelDownloadWorker.class,
            () -> WorkManager.getInstance(context),
            downloadedModelManager,
            settings,
            MoreExecutors.newDirectExecutorService());
    modelFileManager = new ModelFileManagerImpl(context, modelDownloadManager, settings);
    setDefaultLocalesRule.set(new LocaleList(DEFAULT_LOCALE));
  }

  @After
  public void removeTestDir() {
    recursiveDelete(rootTestDir);
  }

  @Test
  public void annotatorModelPreloaded() {
    verifyModelPreloadedAsAsset(ModelType.ANNOTATOR, "textclassifier/annotator.universal.model");
  }

  @Test
  public void actionsModelPreloaded() {
    verifyModelPreloadedAsAsset(
        ModelType.ACTIONS_SUGGESTIONS, "textclassifier/actions_suggestions.universal.model");
  }

  @Test
  public void langIdModelPreloaded() {
    verifyModelPreloadedAsAsset(ModelType.LANG_ID, "textclassifier/lang_id.model");
  }

  private void verifyModelPreloadedAsAsset(
      @ModelTypeDef String modelType, String expectedModelPath) {
    List<ModelFile> modelFiles = modelFileManager.listModelFiles(modelType);
    List<ModelFile> assetFiles =
        modelFiles.stream().filter(modelFile -> modelFile.isAsset).collect(Collectors.toList());

    assertThat(assetFiles).hasSize(1);
    assertThat(assetFiles.get(0).absolutePath).isEqualTo(expectedModelPath);
  }

  @Test
  public void findBestModel_versionCode() {
    ModelFile olderModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 1);
    ModelFile newerModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 2);
    ModelFileManager modelFileManager = createModelFileManager(olderModelFile, newerModelFile);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, /* localePreferences= */ null, /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(newerModelFile);
  }

  @Test
  public void findBestModel_languageDependentModelIsPreferred() {
    ModelFile languageIndependentModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 1);
    ModelFile languageDependentModelFile =
        createModelFile(DEFAULT_LOCALE.toLanguageTag(), /* version */ 1);
    ModelFileManager modelFileManager =
        createModelFileManager(languageIndependentModelFile, languageDependentModelFile);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, new LocaleList(DEFAULT_LOCALE), /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(languageDependentModelFile);
  }

  @Test
  public void findBestModel_noMatchedLanguageModel() {
    ModelFile languageIndependentModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 1);
    ModelFile languageDependentModelFile = createModelFile("zh-hk", /* version */ 1);
    ModelFileManager modelFileManager =
        createModelFileManager(languageIndependentModelFile, languageDependentModelFile);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, new LocaleList(DEFAULT_LOCALE), /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(languageIndependentModelFile);
  }

  @Test
  public void findBestModel_languageIsMoreImportantThanVersion() {
    ModelFile matchButOlderModel = createModelFile(DEFAULT_LOCALE.toLanguageTag(), /* version */ 1);
    ModelFile mismatchButNewerModel = createModelFile("zh-hk", /* version */ 2);
    ModelFileManager modelFileManager =
        createModelFileManager(matchButOlderModel, mismatchButNewerModel);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, new LocaleList(DEFAULT_LOCALE), /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(matchButOlderModel);
  }

  @Test
  public void findBestModel_filterOutLocalePreferenceNotInDefaultLocaleList_onlyCheckLanguage() {
    setDefaultLocalesRule.set(LocaleList.forLanguageTags("zh"));
    ModelFile languageIndependentModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 1);
    ModelFile languageDependentModelFile = createModelFile("zh", /* version */ 1);
    ModelFileManager modelFileManager =
        createModelFileManager(languageIndependentModelFile, languageDependentModelFile);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, LocaleList.forLanguageTags("zh-hk"), /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(languageDependentModelFile);
  }

  @Test
  public void findBestModel_filterOutLocalePreferenceNotInDefaultLocaleList_match() {
    setDefaultLocalesRule.set(LocaleList.forLanguageTags("zh-hk"));
    ModelFile languageIndependentModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 1);
    ModelFile languageDependentModelFile = createModelFile("zh", /* version */ 1);
    ModelFileManager modelFileManager =
        createModelFileManager(languageIndependentModelFile, languageDependentModelFile);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, LocaleList.forLanguageTags("zh"), /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(languageDependentModelFile);
  }

  @Test
  public void findBestModel_filterOutLocalePreferenceNotInDefaultLocaleList_doNotMatch() {
    setDefaultLocalesRule.set(LocaleList.forLanguageTags("en"));
    ModelFile languageIndependentModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 1);
    ModelFile languageDependentModelFile = createModelFile("zh", /* version */ 1);
    ModelFileManager modelFileManager =
        createModelFileManager(languageIndependentModelFile, languageDependentModelFile);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, LocaleList.forLanguageTags("zh"), /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(languageIndependentModelFile);
  }

  @Test
  public void findBestModel_onlyPrimaryLocaleConsidered_noLocalePreferencesProvided() {
    setDefaultLocalesRule.set(
        new LocaleList(Locale.forLanguageTag("en"), Locale.forLanguageTag("zh-hk")));
    ModelFile languageIndependentModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 1);
    ModelFile nonPrimaryLocaleModelFile = createModelFile("zh-hk", /* version */ 1);
    ModelFileManager modelFileManager =
        createModelFileManager(languageIndependentModelFile, nonPrimaryLocaleModelFile);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, /* localePreferences= */ null, /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(languageIndependentModelFile);
  }

  @Test
  public void findBestModel_onlyPrimaryLocaleConsidered_localePreferencesProvided() {
    setDefaultLocalesRule.set(
        new LocaleList(Locale.forLanguageTag("en"), Locale.forLanguageTag("zh-hk")));

    ModelFile languageIndependentModelFile = createModelFile(LANGUAGE_INDEPENDENT, /* version */ 1);
    ModelFile nonPrimaryLocalePreferenceModelFile = createModelFile("zh-hk", /* version */ 1);
    ModelFileManager modelFileManager =
        createModelFileManager(languageIndependentModelFile, nonPrimaryLocalePreferenceModelFile);

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE,
            new LocaleList(Locale.forLanguageTag("en"), Locale.forLanguageTag("zh-hk")),
            /* detectedLocales= */ null);
    assertThat(bestModelFile).isEqualTo(languageIndependentModelFile);
  }

  @Test
  public void findBestModel_multiLanguageEnabled_noMatchedModel() {
    setDefaultLocalesRule.set(LocaleList.forLanguageTags("en"));
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, true);

    ModelFile primaryLocalePreferenceModelFile = createModelFile("en", /* version= */ 1);
    ModelFile secondaryLocalePreferencetModelFile = createModelFile("zh-hk", /* version */ 1);
    ModelFileManager modelFileManager =
        createModelFileManager(
            primaryLocalePreferenceModelFile, secondaryLocalePreferencetModelFile);
    final LocaleList requestLocalePreferences =
        new LocaleList(Locale.forLanguageTag("ja"), Locale.forLanguageTag("fy"));
    final LocaleList detectedLocalePreferences = LocaleList.forLanguageTags("hr");

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, requestLocalePreferences, detectedLocalePreferences);
    assertThat(bestModelFile).isEqualTo(primaryLocalePreferenceModelFile);
  }

  @Test
  public void findBestModel_multiLanguageEnabled_matchDetected() {
    setDefaultLocalesRule.set(
        new LocaleList(Locale.forLanguageTag("en-GB"), Locale.forLanguageTag("zh-hk")));
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, true);

    ModelFile localePreferenceModelFile = createModelFile("zh", /*version*/ 1);
    ModelFileManager modelFileManager = createModelFileManager(localePreferenceModelFile);
    final LocaleList requestLocalePreferences =
        new LocaleList(Locale.forLanguageTag("ja"), Locale.forLanguageTag("zh"));
    final LocaleList detectedLocalePreferences = LocaleList.forLanguageTags("zh");

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, requestLocalePreferences, detectedLocalePreferences);
    assertThat(bestModelFile).isEqualTo(localePreferenceModelFile);
  }

  @Test
  public void findBestModel_multiLanguageDisabled_matchDetected() {
    setDefaultLocalesRule.set(
        new LocaleList(Locale.forLanguageTag("en-GB"), Locale.forLanguageTag("zh-hk")));
    deviceConfig.setConfig(TextClassifierSettings.MULTI_LANGUAGE_SUPPORT_ENABLED, false);

    ModelFile nonLocalePreferenceModelFile = createModelFile("zh", /*version*/ 1);
    ModelFileManager modelFileManager = createModelFileManager(nonLocalePreferenceModelFile);
    final LocaleList requestLocalePreferences = new LocaleList(Locale.forLanguageTag("en"));
    final LocaleList detectedLocalePreferences = LocaleList.getEmptyLocaleList();

    ModelFile bestModelFile =
        modelFileManager.findBestModelFile(
            MODEL_TYPE, requestLocalePreferences, detectedLocalePreferences);
    assertThat(bestModelFile).isEqualTo(null);
  }

  @Test
  public void downloaderModelsLister() throws IOException {
    File annotatorFile = new File(rootTestDir, "annotator.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), annotatorFile);
    File langIdFile = new File(rootTestDir, "langId.model");
    Files.copy(TestDataUtils.getLangIdModelFile(), langIdFile);

    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, true);

    DownloaderModelsLister downloaderModelsLister =
        new DownloaderModelsLister(modelDownloadManager, settings);

    when(downloadedModelManager.listModels(MODEL_TYPE)).thenReturn(Arrays.asList(annotatorFile));
    when(downloadedModelManager.listModels(ModelType.LANG_ID))
        .thenReturn(Arrays.asList(langIdFile));
    when(downloadedModelManager.listModels(ModelType.ACTIONS_SUGGESTIONS))
        .thenReturn(new ArrayList<>());
    assertThat(downloaderModelsLister.list(MODEL_TYPE))
        .containsExactly(ModelFile.createFromRegularFile(annotatorFile, MODEL_TYPE));
    assertThat(downloaderModelsLister.list(ModelType.LANG_ID))
        .containsExactly(ModelFile.createFromRegularFile(langIdFile, ModelType.LANG_ID));
    assertThat(downloaderModelsLister.list(ModelType.ACTIONS_SUGGESTIONS)).isEmpty();
  }

  @Test
  public void downloaderModelsLister_checkModelFileManager() throws IOException {
    File annotatorFile = new File(rootTestDir, "test.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), annotatorFile);

    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, true);
    when(downloadedModelManager.listModels(MODEL_TYPE)).thenReturn(Arrays.asList(annotatorFile));
    assertThat(modelFileManager.listModelFiles(MODEL_TYPE))
        .contains(ModelFile.createFromRegularFile(annotatorFile, MODEL_TYPE));
  }

  @Test
  public void downloaderModelsLister_disabled() throws IOException {
    File annotatorFile = new File(rootTestDir, "test.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), annotatorFile);

    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, false);
    DownloaderModelsLister downloaderModelsLister =
        new DownloaderModelsLister(modelDownloadManager, settings);
    when(downloadedModelManager.listModels(MODEL_TYPE)).thenReturn(Arrays.asList(annotatorFile));
    assertThat(downloaderModelsLister.list(MODEL_TYPE)).isEmpty();
  }

  @Test
  public void regularFileFullMatchLister() throws IOException {
    File modelFile = new File(rootTestDir, "test.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), modelFile);
    File wrongFile = new File(rootTestDir, "wrong.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), wrongFile);

    RegularFileFullMatchLister regularFileFullMatchLister =
        new RegularFileFullMatchLister(MODEL_TYPE, modelFile, () -> true);
    ImmutableList<ModelFile> listedModels = regularFileFullMatchLister.list(MODEL_TYPE);

    assertThat(listedModels).hasSize(1);
    assertThat(listedModels.get(0).absolutePath).isEqualTo(modelFile.getAbsolutePath());
    assertThat(listedModels.get(0).isAsset).isFalse();
  }

  @Test
  public void regularFilePatternMatchLister() throws IOException {
    File modelFile1 = new File(rootTestDir, "annotator.en.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), modelFile1);
    File modelFile2 = new File(rootTestDir, "annotator.fr.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), modelFile2);
    File mismatchedModelFile = new File(rootTestDir, "actions.en.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), mismatchedModelFile);

    RegularFilePatternMatchLister regularFilePatternMatchLister =
        new RegularFilePatternMatchLister(
            MODEL_TYPE, rootTestDir, "annotator\\.(.*)\\.model", () -> true);
    ImmutableList<ModelFile> listedModels = regularFilePatternMatchLister.list(MODEL_TYPE);

    assertThat(listedModels).hasSize(2);
    assertThat(listedModels.get(0).isAsset).isFalse();
    assertThat(listedModels.get(1).isAsset).isFalse();
    assertThat(ImmutableList.of(listedModels.get(0).absolutePath, listedModels.get(1).absolutePath))
        .containsExactly(modelFile1.getAbsolutePath(), modelFile2.getAbsolutePath());
  }

  @Test
  public void regularFilePatternMatchLister_disabled() throws IOException {
    File modelFile1 = new File(rootTestDir, "annotator.en.model");
    Files.copy(TestDataUtils.getTestAnnotatorModelFile(), modelFile1);

    RegularFilePatternMatchLister regularFilePatternMatchLister =
        new RegularFilePatternMatchLister(
            MODEL_TYPE, rootTestDir, "annotator\\.(.*)\\.model", () -> false);
    ImmutableList<ModelFile> listedModels = regularFilePatternMatchLister.list(MODEL_TYPE);

    assertThat(listedModels).isEmpty();
  }

  private ModelFileManager createModelFileManager(ModelFile... modelFiles) {
    return new ModelFileManagerImpl(
        ApplicationProvider.getApplicationContext(),
        ImmutableList.of(modelType -> ImmutableList.copyOf(modelFiles)),
        settings);
  }

  private ModelFile createModelFile(String supportedLocaleTags, int version) {
    return new ModelFile(
        MODEL_TYPE,
        new File(rootTestDir, String.format("%s-%d", supportedLocaleTags, version))
            .getAbsolutePath(),
        version,
        supportedLocaleTags,
        /* isAsset= */ false);
  }

  private static void recursiveDelete(File f) {
    if (f.isDirectory()) {
      for (File innerFile : f.listFiles()) {
        recursiveDelete(innerFile);
      }
    }
    f.delete();
  }
}
