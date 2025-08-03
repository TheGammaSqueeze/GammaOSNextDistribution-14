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

package com.android.textclassifier.common;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.LocaleList;
import android.os.ParcelFileDescriptor;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.logging.ResultIdUtils.ModelInfo;
import com.google.android.textclassifier.ActionsSuggestionsModel;
import com.google.android.textclassifier.AnnotatorModel;
import com.google.android.textclassifier.LangIdModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Describes TextClassifier model files on disk. */
public class ModelFile {
  public static final String LANGUAGE_INDEPENDENT = "*";

  @ModelTypeDef public final String modelType;
  public final String absolutePath;
  public final int version;
  public final LocaleList supportedLocales;
  public final boolean languageIndependent;
  public final boolean isAsset;

  public static ModelFile createFromRegularFile(File file, @ModelTypeDef String modelType)
      throws IOException {
    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    try (AssetFileDescriptor afd = new AssetFileDescriptor(pfd, 0, file.length())) {
      return createFromAssetFileDescriptor(
          file.getAbsolutePath(), modelType, afd, /* isAsset= */ false);
    }
  }

  public static ModelFile createFromAsset(
      AssetManager assetManager, String absolutePath, @ModelTypeDef String modelType)
      throws IOException {
    try (AssetFileDescriptor assetFileDescriptor = assetManager.openFd(absolutePath)) {
      return createFromAssetFileDescriptor(
          absolutePath, modelType, assetFileDescriptor, /* isAsset= */ true);
    }
  }

  private static ModelFile createFromAssetFileDescriptor(
      String absolutePath,
      @ModelTypeDef String modelType,
      AssetFileDescriptor assetFileDescriptor,
      boolean isAsset) {
    ModelInfoFetcher modelInfoFetcher = ModelInfoFetcher.create(modelType);
    return new ModelFile(
        modelType,
        absolutePath,
        modelInfoFetcher.getVersion(assetFileDescriptor),
        modelInfoFetcher.getSupportedLocales(assetFileDescriptor),
        isAsset);
  }

  @VisibleForTesting
  public ModelFile(
      @ModelTypeDef String modelType,
      String absolutePath,
      int version,
      String supportedLocaleTags,
      boolean isAsset) {
    this.modelType = modelType;
    this.absolutePath = absolutePath;
    this.version = version;
    this.languageIndependent = LANGUAGE_INDEPENDENT.equals(supportedLocaleTags);
    this.supportedLocales =
        languageIndependent
            ? LocaleList.getEmptyLocaleList()
            : LocaleList.forLanguageTags(supportedLocaleTags);
    this.isAsset = isAsset;
  }

  /** Returns if this model file is preferred to the given one. */
  public boolean isPreferredTo(@Nullable ModelFile model) {
    // A model is preferred to no model.
    if (model == null) {
      return true;
    }

    // A language-specific model is preferred to a language independent
    // model.
    if (!languageIndependent && model.languageIndependent) {
      return true;
    }
    if (languageIndependent && !model.languageIndependent) {
      return false;
    }

    // A higher-version model is preferred.
    if (version > model.version) {
      return true;
    }
    return false;
  }

  /** Returns whether the language supports any language in the given ranges. */
  public boolean isAnyLanguageSupported(List<Locale.LanguageRange> languageRanges) {
    Preconditions.checkNotNull(languageRanges);
    if (languageIndependent) {
      return true;
    }
    List<String> supportedLocaleTags = Arrays.asList(supportedLocales.toLanguageTags().split(","));
    return Locale.lookupTag(languageRanges, supportedLocaleTags) != null;
  }

  public AssetFileDescriptor open(AssetManager assetManager) throws IOException {
    if (isAsset) {
      return assetManager.openFd(absolutePath);
    }
    File file = new File(absolutePath);
    ParcelFileDescriptor parcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    return new AssetFileDescriptor(parcelFileDescriptor, 0, file.length());
  }

  public boolean canWrite() {
    if (isAsset) {
      return false;
    }
    return new File(absolutePath).canWrite();
  }

  public boolean delete() {
    if (isAsset) {
      throw new IllegalStateException("asset is read-only, deleting it is not allowed.");
    }
    return new File(absolutePath).delete();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ModelFile)) {
      return false;
    }
    ModelFile modelFile = (ModelFile) o;
    return version == modelFile.version
        && languageIndependent == modelFile.languageIndependent
        && isAsset == modelFile.isAsset
        && Objects.equals(modelType, modelFile.modelType)
        && Objects.equals(absolutePath, modelFile.absolutePath)
        && Objects.equals(supportedLocales, modelFile.supportedLocales);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        modelType, absolutePath, version, supportedLocales, languageIndependent, isAsset);
  }

  public ModelInfo toModelInfo() {
    return new ModelInfo(
        version, languageIndependent ? LANGUAGE_INDEPENDENT : supportedLocales.toLanguageTags());
  }

  @Override
  public String toString() {
    return String.format(
        Locale.US,
        "ModelFile { type=%s path=%s version=%d locales=%s isAsset=%b}",
        modelType,
        absolutePath,
        version,
        languageIndependent ? LANGUAGE_INDEPENDENT : supportedLocales.toLanguageTags(),
        isAsset);
  }

  public static ImmutableList<Optional<ModelInfo>> toModelInfos(Optional<ModelFile>... modelFiles) {
    return Arrays.stream(modelFiles)
        .map(modelFile -> modelFile.transform(ModelFile::toModelInfo))
        .collect(Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
  }

  /** Fetch metadata of a model file. */
  private static class ModelInfoFetcher {
    private final Function<AssetFileDescriptor, Integer> versionFetcher;
    private final Function<AssetFileDescriptor, String> supportedLocalesFetcher;

    private ModelInfoFetcher(
        Function<AssetFileDescriptor, Integer> versionFetcher,
        Function<AssetFileDescriptor, String> supportedLocalesFetcher) {
      this.versionFetcher = versionFetcher;
      this.supportedLocalesFetcher = supportedLocalesFetcher;
    }

    int getVersion(AssetFileDescriptor assetFileDescriptor) {
      return versionFetcher.apply(assetFileDescriptor);
    }

    String getSupportedLocales(AssetFileDescriptor assetFileDescriptor) {
      return supportedLocalesFetcher.apply(assetFileDescriptor);
    }

    static ModelInfoFetcher create(@ModelTypeDef String modelType) {
      switch (modelType) {
        case ModelType.ANNOTATOR:
          return new ModelInfoFetcher(AnnotatorModel::getVersion, AnnotatorModel::getLocales);
        case ModelType.ACTIONS_SUGGESTIONS:
          return new ModelInfoFetcher(
              ActionsSuggestionsModel::getVersion, ActionsSuggestionsModel::getLocales);
        case ModelType.LANG_ID:
          return new ModelInfoFetcher(
              LangIdModel::getVersion, afd -> ModelFile.LANGUAGE_INDEPENDENT);
        default: // fall out
      }
      throw new IllegalStateException("Unsupported model types");
    }
  }
}
