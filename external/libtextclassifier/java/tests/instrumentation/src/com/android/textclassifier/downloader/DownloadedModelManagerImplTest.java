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

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Manifest;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ManifestEnrollment;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ManifestModelCrossRef;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Model;
import com.android.textclassifier.testing.TestingDeviceConfig;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DownloadedModelManagerImplTest {

  private File modelDownloaderDir;
  private DownloadedModelDatabase db;
  private DownloadedModelManagerImpl downloadedModelManagerImpl;
  private TestingDeviceConfig deviceConfig;
  private TextClassifierSettings settings;

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    modelDownloaderDir = new File(context.getFilesDir(), "test_dir");
    modelDownloaderDir.mkdirs();
    deviceConfig = new TestingDeviceConfig();
    settings = new TextClassifierSettings(deviceConfig, /* isWear= */ false);
    db = Room.inMemoryDatabaseBuilder(context, DownloadedModelDatabase.class).build();
    downloadedModelManagerImpl =
        DownloadedModelManagerImpl.getInstanceForTesting(db, modelDownloaderDir, settings);
  }

  @After
  public void cleanUp() {
    DownloaderTestUtils.deleteRecursively(modelDownloaderDir);
    db.close();
  }

  @Test
  public void getModelDownloaderDir() throws Exception {
    modelDownloaderDir.delete();
    assertThat(downloadedModelManagerImpl.getModelDownloaderDir().exists()).isTrue();
    assertThat(downloadedModelManagerImpl.getModelDownloaderDir()).isEqualTo(modelDownloaderDir);
  }

  @Test
  public void listModels_cacheNotInitialized() throws Exception {
    registerManifestToDB(ModelType.ANNOTATOR, "en", "manifestUrlEn", "modelUrlEn", "modelPathEn");
    registerManifestToDB(ModelType.ANNOTATOR, "zh", "manifestUrlZh", "modelUrlZh", "modelPathZh");

    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(new File("modelPathEn"), new File("modelPathZh"));
    assertThat(downloadedModelManagerImpl.listModels(ModelType.LANG_ID)).isEmpty();
  }

  @Test
  public void listModels_doNotListBlockedModels() throws Exception {
    registerManifestToDB(ModelType.ANNOTATOR, "en", "manifestUrlEn", "modelUrlEn", "modelPathEn");
    registerManifestToDB(ModelType.ANNOTATOR, "zh", "manifestUrlZh", "modelUrlZh", "modelPathZh");
    deviceConfig.setConfig(
        TextClassifierSettings.MODEL_URL_BLOCKLIST,
        String.format(
            "%s%s%s",
            "modelUrlEn", TextClassifierSettings.MODEL_URL_BLOCKLIST_SEPARATOR, "modelUrlXX"));

    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(new File("modelPathZh"));
  }

  @Test
  public void listModels_cacheNotUpdatedUnlessOnDownloadCompleted() throws Exception {
    registerManifestToDB(ModelType.ANNOTATOR, "en", "manifestUrlEn", "modelUrlEn", "modelPathEn");
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(new File("modelPathEn"));

    registerManifestToDB(ModelType.ANNOTATOR, "zh", "manifestUrlZh", "modelUrlZh", "modelPathZh");
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(new File("modelPathEn"));

    ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(ImmutableMap.of("zh", "manifestUrlZh")));
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .contains(new File("modelPathZh"));
  }

  @Test
  public void getModel() throws Exception {
    registerManifestToDB(ModelType.ANNOTATOR, "en", "manifestUrl", "modelUrl", "modelPath");
    assertThat(downloadedModelManagerImpl.getModel("modelUrl").getModelPath())
        .isEqualTo("modelPath");
    assertThat(downloadedModelManagerImpl.getModel("modelUrl2")).isNull();
  }

  @Test
  public void getManifest() throws Exception {
    registerManifestToDB(ModelType.ANNOTATOR, "en", "manifestUrl", "modelUrl", "modelPath");
    assertThat(downloadedModelManagerImpl.getManifest("manifestUrl")).isNotNull();
    assertThat(downloadedModelManagerImpl.getManifest("manifestUrl2")).isNull();
  }

  @Test
  public void getManifestEnrollment() throws Exception {
    registerManifestToDB(ModelType.ANNOTATOR, "en", "manifestUrl", "modelUrl", "modelPath");
    assertThat(
            downloadedModelManagerImpl
                .getManifestEnrollment(ModelType.ANNOTATOR, "en")
                .getManifestUrl())
        .isEqualTo("manifestUrl");
    assertThat(downloadedModelManagerImpl.getManifestEnrollment(ModelType.ANNOTATOR, "zh"))
        .isNull();
  }

  @Test
  public void registerModel() throws Exception {
    downloadedModelManagerImpl.registerModel("modelUrl", "modelPath");

    assertThat(downloadedModelManagerImpl.getModel("modelUrl").getModelPath())
        .isEqualTo("modelPath");
  }

  @Test
  public void registerManifest() throws Exception {
    downloadedModelManagerImpl.registerModel("modelUrl", "modelPath");
    downloadedModelManagerImpl.registerManifest("manifestUrl", "modelUrl");

    assertThat(downloadedModelManagerImpl.getManifest("manifestUrl")).isNotNull();
  }

  @Test
  public void registerManifestDownloadFailure() throws Exception {
    downloadedModelManagerImpl.registerManifestDownloadFailure("manifestUrl");

    Manifest manifest = downloadedModelManagerImpl.getManifest("manifestUrl");
    assertThat(manifest.getStatus()).isEqualTo(Manifest.STATUS_FAILED);
    assertThat(manifest.getFailureCounts()).isEqualTo(1);
  }

  @Test
  public void registerManifestEnrollment() throws Exception {
    downloadedModelManagerImpl.registerModel("modelUrl", "modelPath");
    downloadedModelManagerImpl.registerManifest("manifestUrl", "modelUrl");
    downloadedModelManagerImpl.registerManifestEnrollment(ModelType.ANNOTATOR, "en", "manifestUrl");

    ManifestEnrollment manifestEnrollment =
        downloadedModelManagerImpl.getManifestEnrollment(ModelType.ANNOTATOR, "en");
    assertThat(manifestEnrollment.getModelType()).isEqualTo(ModelType.ANNOTATOR);
    assertThat(manifestEnrollment.getLocaleTag()).isEqualTo("en");
    assertThat(manifestEnrollment.getManifestUrl()).isEqualTo("manifestUrl");
  }

  @Test
  public void onDownloadCompleted_newModelDownloaded() throws Exception {
    ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(ImmutableMap.of("en", "manifestUrl1")));
    File modelFile1 = new File(modelDownloaderDir, "modelFile1");
    modelFile1.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "en", "manifestUrl1", "modelUrl1", modelFile1.getAbsolutePath());
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);

    assertThat(modelFile1.exists()).isTrue();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(modelFile1);

    manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(ImmutableMap.of("en", "manifestUrl2")));
    File modelFile2 = new File(modelDownloaderDir, "modelFile2");
    modelFile2.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "en", "manifestUrl2", "modelUrl2", modelFile2.getAbsolutePath());
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);

    assertThat(modelFile1.exists()).isFalse();
    assertThat(modelFile2.exists()).isTrue();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(modelFile2);
  }

  @Test
  public void onDownloadCompleted_newModelDownloadFailed() throws Exception {
    ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(ImmutableMap.of("en", "manifestUrl1")));
    File modelFile1 = new File(modelDownloaderDir, "modelFile1");
    modelFile1.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "en", "manifestUrl1", "modelUrl1", modelFile1.getAbsolutePath());
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);

    assertThat(modelFile1.exists()).isTrue();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(modelFile1);

    manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(ImmutableMap.of("en", "manifestUrl2")));
    downloadedModelManagerImpl.registerManifestDownloadFailure("manifestUrl2");
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);

    assertThat(modelFile1.exists()).isTrue();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(modelFile1);
  }

  @Test
  public void onDownloadCompleted_flatUnset() throws Exception {
    ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(ImmutableMap.of("en", "manifestUrl1")));
    File modelFile1 = new File(modelDownloaderDir, "modelFile1");
    modelFile1.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "en", "manifestUrl1", "modelUrl1", modelFile1.getAbsolutePath());
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);

    assertThat(modelFile1.exists()).isTrue();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(modelFile1);

    manifestsToDownload = ImmutableMap.of();
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);

    assertThat(modelFile1.exists()).isFalse();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR)).isEmpty();
  }

  @Test
  public void onDownloadCompleted_cleanUpFailureRecords() throws Exception {
    ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(ImmutableMap.of("en", "manifestUrl1")));
    downloadedModelManagerImpl.registerManifestDownloadFailure("manifestUrl1");
    downloadedModelManagerImpl.registerManifestDownloadFailure("manifestUrl2");
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);

    assertThat(downloadedModelManagerImpl.getManifest("manifestUrl1").getStatus())
        .isEqualTo(Manifest.STATUS_FAILED);
    assertThat(downloadedModelManagerImpl.getManifest("manifestUrl2")).isNull();
  }

  @Test
  public void onDownloadCompleted_modelsForMultipleLocalesDownloaded() throws Exception {
    ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(
                ImmutableMap.of("en", "manifestUrl1", "es", "manifestUrl2")));

    File modelFile1 = new File(modelDownloaderDir, "modelFile1");
    modelFile1.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "en", "manifestUrl1", "modelUrl1", modelFile1.getAbsolutePath());

    File modelFile2 = new File(modelDownloaderDir, "modelFile2");
    modelFile2.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "es", "manifestUrl2", "modelUrl2", modelFile2.getAbsolutePath());

    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);
    assertThat(modelFile1.exists()).isTrue();
    assertThat(modelFile2.exists()).isTrue();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(modelFile1, modelFile2);
  }

  @Test
  public void onDownloadCompleted_multipleLocales_oneDownloadFailed() throws Exception {
    File modelFile1 = new File(modelDownloaderDir, "modelFile1");
    modelFile1.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "en", "manifestUrl1", "modelUrl1", modelFile1.getAbsolutePath());

    ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(
                ImmutableMap.of("es", "manifestUrl2", "en", "manifestUrl3")));
    File modelFile2 = new File(modelDownloaderDir, "modelFile2");
    modelFile2.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "es", "manifestUrl2", "modelUrl2", modelFile2.getAbsolutePath());
    downloadedModelManagerImpl.registerManifestDownloadFailure("manifestUrl3");
    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);

    assertThat(modelFile1.exists()).isTrue();
    assertThat(modelFile2.exists()).isTrue();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(modelFile1, modelFile2);
  }

  @Test
  public void onDownoadCompleted_multipleLocales_replaceOldModel() throws Exception {
    File modelFile1 = new File(modelDownloaderDir, "modelFile1");
    modelFile1.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "en", "manifestUrl1", "modelUrl1", modelFile1.getAbsolutePath());

    ImmutableMap<String, ManifestsToDownloadByType> manifestsToDownload =
        ImmutableMap.of(
            ModelType.ANNOTATOR,
            ManifestsToDownloadByType.create(
                ImmutableMap.of("en", "manifestUrl2", "es", "manifestUrl3")));

    File modelFile2 = new File(modelDownloaderDir, "modelFile2");
    modelFile2.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "en", "manifestUrl2", "modelUrl2", modelFile2.getAbsolutePath());

    File modelFile3 = new File(modelDownloaderDir, "modelFile3");
    modelFile3.createNewFile();
    registerManifestToDB(
        ModelType.ANNOTATOR, "es", "manifestUrl3", "modelUrl3", modelFile3.getAbsolutePath());

    downloadedModelManagerImpl.onDownloadCompleted(manifestsToDownload);
    assertThat(modelFile2.exists()).isTrue();
    assertThat(modelFile3.exists()).isTrue();
    assertThat(downloadedModelManagerImpl.listModels(ModelType.ANNOTATOR))
        .containsExactly(modelFile2, modelFile3);
  }

  private void registerManifestToDB(
      @ModelTypeDef String modelType,
      String localeTag,
      String manifestUrl,
      String modelUrl,
      String modelPath) {
    db.dao().insert(Model.create(modelUrl, modelPath));
    db.dao()
        .insert(Manifest.create(manifestUrl, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0));
    db.dao().insert(ManifestModelCrossRef.create(manifestUrl, modelUrl));
    db.dao().insert(ManifestEnrollment.create(modelType, localeTag, manifestUrl));
  }
}
