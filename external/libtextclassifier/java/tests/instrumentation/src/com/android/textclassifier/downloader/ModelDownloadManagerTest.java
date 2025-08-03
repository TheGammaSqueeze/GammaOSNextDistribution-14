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
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.LocaleList;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.android.os.AtomsProto.TextClassifierDownloadWorkScheduled;
import com.android.os.AtomsProto.TextClassifierDownloadWorkScheduled.ReasonToSchedule;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.common.statsd.TextClassifierDownloadLoggerTestRule;
import com.android.textclassifier.testing.SetDefaultLocalesRule;
import com.android.textclassifier.testing.TestingDeviceConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
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

@RunWith(AndroidJUnit4.class)
public final class ModelDownloadManagerTest {
  private static final String MODEL_PATH = "/data/test.model";
  @ModelType.ModelTypeDef private static final String MODEL_TYPE = ModelType.ANNOTATOR;
  private static final String LOCALE_TAG = "en";
  private static final LocaleList DEFAULT_LOCALE_LIST = new LocaleList(new Locale(LOCALE_TAG));

  @Rule public final SetDefaultLocalesRule setDefaultLocalesRule = new SetDefaultLocalesRule();

  @Rule
  public final TextClassifierDownloadLoggerTestRule loggerTestRule =
      new TextClassifierDownloadLoggerTestRule();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private TestingDeviceConfig deviceConfig;
  private WorkManager workManager;
  private ModelDownloadManager downloadManager;
  private ModelDownloadManager downloadManagerWithBadWorkManager;
  @Mock DownloadedModelManager downloadedModelManager;

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    WorkManagerTestInitHelper.initializeTestWorkManager(context);

    this.deviceConfig = new TestingDeviceConfig();
    this.workManager = WorkManager.getInstance(context);
    this.downloadManager =
        new ModelDownloadManager(
            context,
            ModelDownloadWorker.class,
            () -> workManager,
            downloadedModelManager,
            new TextClassifierSettings(deviceConfig, /* isWear= */ false),
            MoreExecutors.newDirectExecutorService());
    this.downloadManagerWithBadWorkManager =
        new ModelDownloadManager(
            context,
            ModelDownloadWorker.class,
            () -> {
              throw new IllegalStateException("WorkManager may fail!");
            },
            downloadedModelManager,
            new TextClassifierSettings(deviceConfig, /* isWear= */ false),
            MoreExecutors.newDirectExecutorService());

    setDefaultLocalesRule.set(DEFAULT_LOCALE_LIST);
    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, true);
  }

  @After
  public void tearDown() {
    workManager.cancelUniqueWork(ModelDownloadManager.UNIQUE_QUEUE_NAME);
    DownloaderTestUtils.deleteRecursively(
        ApplicationProvider.getApplicationContext().getFilesDir());
  }

  @Test
  public void onTextClassifierServiceCreated_workManagerCrashed() throws Exception {
    assertThat(loggerTestRule.getLoggedDownloadWorkScheduledAtoms()).isEmpty();
    downloadManagerWithBadWorkManager.onTextClassifierServiceCreated();

    // Assertion below is flaky: DeviceConfig listener may be trigerred by OS during test
    TextClassifierDownloadWorkScheduled atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadWorkScheduledAtoms());
    assertThat(atom.getReasonToSchedule()).isEqualTo(ReasonToSchedule.TCS_STARTED);
    assertThat(atom.getFailedToSchedule()).isTrue();
  }

  @Test
  public void onTextClassifierServiceCreated_requestEnqueued() throws Exception {
    assertThat(loggerTestRule.getLoggedDownloadWorkScheduledAtoms()).isEmpty();
    downloadManager.onTextClassifierServiceCreated();

    WorkInfo workInfo =
        Iterables.getOnlyElement(
            DownloaderTestUtils.queryWorkInfos(
                workManager, ModelDownloadManager.UNIQUE_QUEUE_NAME));
    assertThat(workInfo.getState()).isEqualTo(WorkInfo.State.ENQUEUED);
    // Assertion below is flaky: DeviceConfig listener may be trigerred by OS during test
    verifyWorkScheduledLogging(ReasonToSchedule.TCS_STARTED);
  }

  @Test
  public void onTextClassifierServiceCreated_localeListOverridden() throws Exception {
    assertThat(loggerTestRule.getLoggedDownloadWorkScheduledAtoms()).isEmpty();
    deviceConfig.setConfig(TextClassifierSettings.TESTING_LOCALE_LIST_OVERRIDE, "zh,fr");
    downloadManager.onTextClassifierServiceCreated();

    assertThat(Locale.getDefault()).isEqualTo(Locale.forLanguageTag("zh"));
    assertThat(LocaleList.getDefault()).isEqualTo(LocaleList.forLanguageTags("zh,fr"));
    assertThat(LocaleList.getAdjustedDefault()).isEqualTo(LocaleList.forLanguageTags("zh,fr"));
    // Assertion below is flaky: DeviceConfig listener may be trigerred by OS during test
    verifyWorkScheduledLogging(ReasonToSchedule.TCS_STARTED);
  }

  @Test
  public void onLocaleChanged_workManagerCrashed() throws Exception {
    downloadManagerWithBadWorkManager.onLocaleChanged();

    TextClassifierDownloadWorkScheduled atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadWorkScheduledAtoms());
    assertThat(atom.getReasonToSchedule()).isEqualTo(ReasonToSchedule.LOCALE_SETTINGS_CHANGED);
    assertThat(atom.getFailedToSchedule()).isTrue();
  }

  @Test
  public void onLocaleChanged_requestEnqueued() throws Exception {
    downloadManager.onLocaleChanged();

    WorkInfo workInfo =
        Iterables.getOnlyElement(
            DownloaderTestUtils.queryWorkInfos(
                workManager, ModelDownloadManager.UNIQUE_QUEUE_NAME));
    assertThat(workInfo.getState()).isEqualTo(WorkInfo.State.ENQUEUED);
    verifyWorkScheduledLogging(ReasonToSchedule.LOCALE_SETTINGS_CHANGED);
  }

  @Test
  public void onTextClassifierDeviceConfigChanged_workManagerCrashed() throws Exception {
    downloadManagerWithBadWorkManager.onTextClassifierDeviceConfigChanged();

    TextClassifierDownloadWorkScheduled atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadWorkScheduledAtoms());
    assertThat(atom.getReasonToSchedule()).isEqualTo(ReasonToSchedule.DEVICE_CONFIG_UPDATED);
    assertThat(atom.getFailedToSchedule()).isTrue();
  }

  @Test
  public void onTextClassifierDeviceConfigChanged_requestEnqueued() throws Exception {
    downloadManager.onTextClassifierDeviceConfigChanged();

    WorkInfo workInfo =
        Iterables.getOnlyElement(
            DownloaderTestUtils.queryWorkInfos(
                workManager, ModelDownloadManager.UNIQUE_QUEUE_NAME));
    assertThat(workInfo.getState()).isEqualTo(WorkInfo.State.ENQUEUED);
    verifyWorkScheduledLogging(ReasonToSchedule.DEVICE_CONFIG_UPDATED);
  }

  @Test
  public void onTextClassifierDeviceConfigChanged_downloaderDisabled() throws Exception {
    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, false);
    downloadManager.onTextClassifierDeviceConfigChanged();

    assertThat(
            DownloaderTestUtils.queryWorkInfos(workManager, ModelDownloadManager.UNIQUE_QUEUE_NAME))
        .isEmpty();
    assertThat(loggerTestRule.getLoggedDownloadWorkScheduledAtoms()).isEmpty();
  }

  @Test
  public void onTextClassifierDeviceConfigChanged_newWorkDoNotReplaceOldWork() throws Exception {
    downloadManager.onTextClassifierDeviceConfigChanged();
    downloadManager.onTextClassifierDeviceConfigChanged();
    List<WorkInfo> workInfos =
        DownloaderTestUtils.queryWorkInfos(workManager, ModelDownloadManager.UNIQUE_QUEUE_NAME);

    assertThat(workInfos.stream().map(WorkInfo::getState).collect(Collectors.toList()))
        .containsExactly(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED);
    List<TextClassifierDownloadWorkScheduled> atoms =
        loggerTestRule.getLoggedDownloadWorkScheduledAtoms();
    assertThat(atoms).hasSize(2);
    verifyWorkScheduledAtom(atoms.get(0), ReasonToSchedule.DEVICE_CONFIG_UPDATED);
    verifyWorkScheduledAtom(atoms.get(1), ReasonToSchedule.DEVICE_CONFIG_UPDATED);
  }

  @Test
  public void onTextClassifierDeviceConfigChanged_localeListOverridden() throws Exception {
    deviceConfig.setConfig(TextClassifierSettings.TESTING_LOCALE_LIST_OVERRIDE, "zh,fr");
    downloadManager.onTextClassifierDeviceConfigChanged();

    assertThat(Locale.getDefault()).isEqualTo(Locale.forLanguageTag("zh"));
    assertThat(LocaleList.getDefault()).isEqualTo(LocaleList.forLanguageTags("zh,fr"));
    assertThat(LocaleList.getAdjustedDefault()).isEqualTo(LocaleList.forLanguageTags("zh,fr"));
    verifyWorkScheduledLogging(ReasonToSchedule.DEVICE_CONFIG_UPDATED);
  }

  @Test
  public void listDownloadedModels() throws Exception {
    File modelFile = new File(MODEL_PATH);
    when(downloadedModelManager.listModels(MODEL_TYPE)).thenReturn(ImmutableList.of(modelFile));

    assertThat(downloadManager.listDownloadedModels(MODEL_TYPE)).containsExactly(modelFile);
  }

  @Test
  public void listDownloadedModels_doNotCrashOnError() throws Exception {
    when(downloadedModelManager.listModels(MODEL_TYPE)).thenThrow(new IllegalStateException());

    assertThat(downloadManager.listDownloadedModels(MODEL_TYPE)).isEmpty();
  }

  private void verifyWorkScheduledLogging(ReasonToSchedule reasonToSchedule) throws Exception {
    TextClassifierDownloadWorkScheduled atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadWorkScheduledAtoms());
    verifyWorkScheduledAtom(atom, reasonToSchedule);
  }

  private void verifyWorkScheduledAtom(
      TextClassifierDownloadWorkScheduled atom, ReasonToSchedule reasonToSchedule) {
    assertThat(atom.getReasonToSchedule()).isEqualTo(reasonToSchedule);
    assertThat(atom.getFailedToSchedule()).isFalse();
  }
}
