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

import static java.lang.Math.min;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.LocaleList;
import androidx.annotation.GuardedBy;
import androidx.collection.ArrayMap;
import com.android.textclassifier.common.ModelFile;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.downloader.ModelDownloadManager;
import com.android.textclassifier.utils.IndentingPrintWriter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

// TODO(licha): Consider making this a singleton class
// TODO(licha): Check whether this is thread-safe
/**
 * Manages all model files in storage. {@link TextClassifierImpl} depends on this class to get the
 * model files to load.
 */
final class ModelFileManagerImpl implements ModelFileManager {

  private static final String TAG = "ModelFileManagerImpl";

  private static final File CONFIG_UPDATER_DIR = new File("/data/misc/textclassifier/");
  private static final String ASSETS_DIR = "textclassifier";

  private ImmutableList<ModelFileLister> modelFileListers;

  private final TextClassifierSettings settings;

  public ModelFileManagerImpl(
      Context context, ModelDownloadManager modelDownloadManager, TextClassifierSettings settings) {

    Preconditions.checkNotNull(context);
    Preconditions.checkNotNull(modelDownloadManager);

    this.settings = Preconditions.checkNotNull(settings);

    AssetManager assetManager = context.getAssets();
    modelFileListers =
        ImmutableList.of(
            // Annotator models.
            new RegularFileFullMatchLister(
                ModelType.ANNOTATOR,
                new File(CONFIG_UPDATER_DIR, "textclassifier.model"),
                /* isEnabled= */ () -> settings.isConfigUpdaterModelEnabled()),
            new AssetFilePatternMatchLister(
                assetManager,
                ModelType.ANNOTATOR,
                ASSETS_DIR,
                "annotator\\.(.*)\\.model",
                /* isEnabled= */ () -> true),
            // Actions models.
            new RegularFileFullMatchLister(
                ModelType.ACTIONS_SUGGESTIONS,
                new File(CONFIG_UPDATER_DIR, "actions_suggestions.model"),
                /* isEnabled= */ () -> settings.isConfigUpdaterModelEnabled()),
            new AssetFilePatternMatchLister(
                assetManager,
                ModelType.ACTIONS_SUGGESTIONS,
                ASSETS_DIR,
                "actions_suggestions\\.(.*)\\.model",
                /* isEnabled= */ () -> true),
            // LangID models.
            new RegularFileFullMatchLister(
                ModelType.LANG_ID,
                new File(CONFIG_UPDATER_DIR, "lang_id.model"),
                /* isEnabled= */ () -> settings.isConfigUpdaterModelEnabled()),
            new AssetFilePatternMatchLister(
                assetManager,
                ModelType.LANG_ID,
                ASSETS_DIR,
                "lang_id.model",
                /* isEnabled= */ () -> true),
            new DownloaderModelsLister(modelDownloadManager, settings));
  }

  @VisibleForTesting
  public ModelFileManagerImpl(
      Context context, List<ModelFileLister> modelFileListers, TextClassifierSettings settings) {
    this.modelFileListers = ImmutableList.copyOf(modelFileListers);
    this.settings = settings;
  }

  public ImmutableList<ModelFile> listModelFiles(@ModelTypeDef String modelType) {
    Preconditions.checkNotNull(modelType);

    ImmutableList.Builder<ModelFile> modelFiles = new ImmutableList.Builder<>();
    for (ModelFileLister modelFileLister : modelFileListers) {
      modelFiles.addAll(modelFileLister.list(modelType));
    }
    return modelFiles.build();
  }

  /** Lists model files. */
  @FunctionalInterface
  public interface ModelFileLister {
    List<ModelFile> list(@ModelTypeDef String modelType);
  }

  /** Lists Downloader models */
  public static class DownloaderModelsLister implements ModelFileLister {

    private final ModelDownloadManager modelDownloadManager;
    private final TextClassifierSettings settings;

    /**
     * @param modelDownloadManager manager of downloaded models
     * @param settings current settings
     */
    public DownloaderModelsLister(
        ModelDownloadManager modelDownloadManager, TextClassifierSettings settings) {
      this.modelDownloadManager = Preconditions.checkNotNull(modelDownloadManager);
      this.settings = Preconditions.checkNotNull(settings);
    }

    @Override
    public ImmutableList<ModelFile> list(@ModelTypeDef String modelType) {
      ImmutableList.Builder<ModelFile> modelFilesBuilder = ImmutableList.builder();
      if (settings.isModelDownloadManagerEnabled()) {
        for (File modelFile : modelDownloadManager.listDownloadedModels(modelType)) {
          try {
            // TODO(licha): Construct downloader model files with locale tag in our internal
            // database
            modelFilesBuilder.add(ModelFile.createFromRegularFile(modelFile, modelType));
          } catch (IOException e) {
            TcLog.e(TAG, "Failed to create ModelFile: " + modelFile.getAbsolutePath(), e);
          }
        }
      }
      return modelFilesBuilder.build();
    }
  }

  /** Lists model files by performing full match on file path. */
  public static class RegularFileFullMatchLister implements ModelFileLister {
    private final String modelType;
    private final File targetFile;
    private final Supplier<Boolean> isEnabled;

    /**
     * @param modelType the type of the model
     * @param targetFile the expected model file
     * @param isEnabled whether this lister is enabled
     */
    public RegularFileFullMatchLister(
        @ModelTypeDef String modelType, File targetFile, Supplier<Boolean> isEnabled) {
      this.modelType = Preconditions.checkNotNull(modelType);
      this.targetFile = Preconditions.checkNotNull(targetFile);
      this.isEnabled = Preconditions.checkNotNull(isEnabled);
    }

    @Override
    public ImmutableList<ModelFile> list(@ModelTypeDef String modelType) {
      if (!this.modelType.equals(modelType)) {
        return ImmutableList.of();
      }
      if (!isEnabled.get()) {
        return ImmutableList.of();
      }
      if (!targetFile.exists()) {
        return ImmutableList.of();
      }
      try {
        return ImmutableList.of(ModelFile.createFromRegularFile(targetFile, modelType));
      } catch (IOException e) {
        TcLog.e(
            TAG, "Failed to call createFromRegularFile with: " + targetFile.getAbsolutePath(), e);
      }
      return ImmutableList.of();
    }
  }

  /** Lists model file in a specified folder by doing pattern matching on file names. */
  public static class RegularFilePatternMatchLister implements ModelFileLister {
    private final String modelType;
    private final File folder;
    private final Pattern fileNamePattern;
    private final Supplier<Boolean> isEnabled;

    /**
     * @param modelType the type of the model
     * @param folder the folder to list files
     * @param fileNameRegex the regex to match the file name in the specified folder
     * @param isEnabled whether the lister is enabled
     */
    public RegularFilePatternMatchLister(
        @ModelTypeDef String modelType,
        File folder,
        String fileNameRegex,
        Supplier<Boolean> isEnabled) {
      this.modelType = Preconditions.checkNotNull(modelType);
      this.folder = Preconditions.checkNotNull(folder);
      this.fileNamePattern = Pattern.compile(Preconditions.checkNotNull(fileNameRegex));
      this.isEnabled = Preconditions.checkNotNull(isEnabled);
    }

    @Override
    public ImmutableList<ModelFile> list(@ModelTypeDef String modelType) {
      if (!this.modelType.equals(modelType)) {
        return ImmutableList.of();
      }
      if (!isEnabled.get()) {
        return ImmutableList.of();
      }
      if (!folder.isDirectory()) {
        return ImmutableList.of();
      }
      File[] files = folder.listFiles();
      if (files == null) {
        return ImmutableList.of();
      }
      ImmutableList.Builder<ModelFile> modelFilesBuilder = ImmutableList.builder();
      for (File file : files) {
        final Matcher matcher = fileNamePattern.matcher(file.getName());
        if (!matcher.matches() || !file.isFile()) {
          continue;
        }
        try {
          modelFilesBuilder.add(ModelFile.createFromRegularFile(file, modelType));
        } catch (IOException e) {
          TcLog.w(TAG, "Failed to call createFromRegularFile with: " + file.getAbsolutePath());
        }
      }
      return modelFilesBuilder.build();
    }
  }

  /** Lists the model files preloaded in the APK file. */
  public static class AssetFilePatternMatchLister implements ModelFileLister {
    private final AssetManager assetManager;
    private final String modelType;
    private final String pathToList;
    private final Pattern fileNamePattern;
    private final Supplier<Boolean> isEnabled;
    private final Object lock = new Object();
    // Assets won't change without updating the app, so cache the result for performance reason.
    @GuardedBy("lock")
    private final Map<String, ImmutableList<ModelFile>> resultCache;

    /**
     * @param modelType the type of the model.
     * @param pathToList the folder to list files
     * @param fileNameRegex the regex to match the file name in the specified folder
     * @param isEnabled whether this lister is enabled
     */
    public AssetFilePatternMatchLister(
        AssetManager assetManager,
        @ModelTypeDef String modelType,
        String pathToList,
        String fileNameRegex,
        Supplier<Boolean> isEnabled) {
      this.assetManager = Preconditions.checkNotNull(assetManager);
      this.modelType = Preconditions.checkNotNull(modelType);
      this.pathToList = Preconditions.checkNotNull(pathToList);
      this.fileNamePattern = Pattern.compile(Preconditions.checkNotNull(fileNameRegex));
      this.isEnabled = Preconditions.checkNotNull(isEnabled);
      resultCache = new ArrayMap<>();
    }

    @Override
    public ImmutableList<ModelFile> list(@ModelTypeDef String modelType) {
      if (!this.modelType.equals(modelType)) {
        return ImmutableList.of();
      }
      if (!isEnabled.get()) {
        return ImmutableList.of();
      }
      synchronized (lock) {
        if (resultCache.get(modelType) != null) {
          return resultCache.get(modelType);
        }
        String[] fileNames = null;
        try {
          fileNames = assetManager.list(pathToList);
        } catch (IOException e) {
          TcLog.e(TAG, "Failed to list assets", e);
        }
        if (fileNames == null) {
          return ImmutableList.of();
        }
        ImmutableList.Builder<ModelFile> modelFilesBuilder = ImmutableList.builder();
        for (String fileName : fileNames) {
          final Matcher matcher = fileNamePattern.matcher(fileName);
          if (!matcher.matches()) {
            continue;
          }
          String absolutePath =
              new StringBuilder(pathToList).append('/').append(fileName).toString();
          try {
            modelFilesBuilder.add(ModelFile.createFromAsset(assetManager, absolutePath, modelType));
          } catch (IOException e) {
            TcLog.e(TAG, "Failed to call createFromAsset with: " + absolutePath, e);
          }
        }
        ImmutableList<ModelFile> result = modelFilesBuilder.build();
        resultCache.put(modelType, result);
        return result;
      }
    }
  }

  /**
   * Returns the best locale matching the given detected locales and the default device localelist.
   * Default locale returned if no matching locale is found.
   *
   * @param localePreferences list of optional locale preferences. Used if request contains
   *     preference and multi_language_support is disabled.
   * @param detectedLocales ordered list of locales detected from Tcs request text, use {@code null}
   *     if no detected locales provided.
   */
  public Locale findBestModelLocale(
      @Nullable LocaleList localePreferences, @Nullable LocaleList detectedLocales) {
    if (!settings.isMultiLanguageSupportEnabled() || isEmptyLocaleList(detectedLocales)) {
      return isEmptyLocaleList(localePreferences) ? Locale.getDefault() : localePreferences.get(0);
    }
    Locale bestLocale = Locale.getDefault();
    LocaleList adjustedLocales = LocaleList.getAdjustedDefault();
    // we only intersect detected locales with locales for which we have predownloaded models.
    // Number of downlaoded locale models is determined by flag in tcs settings
    int numberOfActiveModels = min(adjustedLocales.size(), settings.getMultiLanguageModelsLimit());
    List<String> filteredDeviceLocales =
        Splitter.on(",")
            .splitToList(adjustedLocales.toLanguageTags())
            .subList(0, numberOfActiveModels);
    LocaleList filteredDeviceLocaleList =
        LocaleList.forLanguageTags(String.join(",", filteredDeviceLocales));
    List<Locale.LanguageRange> deviceLanguageRange =
        Locale.LanguageRange.parse(filteredDeviceLocaleList.toLanguageTags());
    for (int i = 0; i < detectedLocales.size(); i++) {
      if (Locale.lookupTag(
              deviceLanguageRange, ImmutableList.of(detectedLocales.get(i).getLanguage()))
          != null) {
        bestLocale = detectedLocales.get(i);
        break;
      }
    }
    return bestLocale;
  }

  @Nullable
  @Override
  public ModelFile findBestModelFile(
      @ModelTypeDef String modelType,
      @Nullable LocaleList localePreferences,
      @Nullable LocaleList detectedLocales) {
    Locale targetLocale = findBestModelLocale(localePreferences, detectedLocales);
    // detectedLocales usually only contains 2-char language (e.g. en), while locale in
    // localePreferences is usually complete (e.g. en_US). Log only if targetLocale is not a prefix.
    if (!isEmptyLocaleList(localePreferences)
        && !localePreferences.get(0).toString().startsWith(targetLocale.toString())) {
      TcLog.d(
          TAG,
          String.format(
              Locale.US,
              "localePreference and targetLocale mismatch: preference: %s, target: %s",
              localePreferences.get(0),
              targetLocale));
    }
    return findBestModelFile(modelType, targetLocale);
  }

  /**
   * Returns the best model file for the given locale, {@code null} if nothing is found.
   *
   * @param modelType the type of model to look up (e.g. annotator, lang_id, etc.)
   * @param targetLocale the preferred locale from preferences or detected locales default locales
   *     if non given or detected.
   */
  @Nullable
  private ModelFile findBestModelFile(@ModelTypeDef String modelType, Locale targetLocale) {
    List<Locale.LanguageRange> deviceLanguageRanges =
        Locale.LanguageRange.parse(LocaleList.getDefault().toLanguageTags());
    boolean languageIndependentModelOnly = false;
    if (Locale.lookupTag(deviceLanguageRanges, ImmutableList.of(targetLocale.getLanguage()))
        == null) {
      // If the targetLocale's language is not in device locale list, we don't match it to avoid
      // leaking user language profile to the callers.
      languageIndependentModelOnly = true;
    }
    List<Locale.LanguageRange> targetLanguageRanges =
        Locale.LanguageRange.parse(targetLocale.toLanguageTag());
    ModelFile bestModel = null;
    for (ModelFile model : listModelFiles(modelType)) {
      if (languageIndependentModelOnly && !model.languageIndependent) {
        continue;
      }
      if (model.isAnyLanguageSupported(targetLanguageRanges)) {
        if (model.isPreferredTo(bestModel)) {
          bestModel = model;
        }
      }
    }
    return bestModel;
  }

  /**
   * Helpter function to check if LocaleList is null or empty
   *
   * @param localeList locale list to be checked
   */
  private static boolean isEmptyLocaleList(@Nullable LocaleList localeList) {
    return localeList == null || localeList.isEmpty();
  }

  @Override
  public void dump(IndentingPrintWriter printWriter) {
    printWriter.println("ModelFileManagerImpl:");
    printWriter.increaseIndent();
    for (@ModelTypeDef String modelType : ModelType.values()) {
      printWriter.println(modelType + " model file(s):");
      printWriter.increaseIndent();
      for (ModelFile modelFile : listModelFiles(modelType)) {
        printWriter.println(modelFile.toString());
      }
      printWriter.decreaseIndent();
    }
    printWriter.decreaseIndent();
  }
}
