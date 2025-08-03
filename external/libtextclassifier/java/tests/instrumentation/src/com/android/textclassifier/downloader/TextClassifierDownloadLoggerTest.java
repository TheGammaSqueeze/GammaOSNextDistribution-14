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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.os.AtomsProto.TextClassifierDownloadReported;
import com.android.os.AtomsProto.TextClassifierDownloadWorkCompleted;
import com.android.os.AtomsProto.TextClassifierDownloadWorkScheduled;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.statsd.TextClassifierDownloadLoggerTestRule;
import com.google.common.collect.Iterables;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class TextClassifierDownloadLoggerTest {
  private static final String MODEL_TYPE = ModelType.LANG_ID;
  private static final TextClassifierDownloadReported.ModelType MODEL_TYPE_ATOM =
      TextClassifierDownloadReported.ModelType.LANG_ID;
  private static final String URL =
      "https://www.gstatic.com/android/text_classifier/x/v123/en.fb.manifest";
  private static final int ERROR_CODE = ModelDownloadException.FAILED_TO_DOWNLOAD_404_ERROR;
  private static final TextClassifierDownloadReported.FailureReason FAILURE_REASON_ATOM =
      TextClassifierDownloadReported.FailureReason.FAILED_TO_DOWNLOAD_404_ERROR;
  private static final int RUN_ATTEMPT_COUNT = 1;
  private static final long WORK_ID = 123456789L;
  private static final long DOWNLOAD_DURATION_MILLIS = 666L;
  private static final int DOWNLOADER_LIB_ERROR_CODE = 500;
  private static final int REASON_TO_SCHEDULE =
      TextClassifierDownloadLogger.REASON_TO_SCHEDULE_TCS_STARTED;
  private static final TextClassifierDownloadWorkScheduled.ReasonToSchedule
      REASON_TO_SCHEDULE_ATOM = TextClassifierDownloadWorkScheduled.ReasonToSchedule.TCS_STARTED;
  private static final int WORK_RESULT =
      TextClassifierDownloadLogger.WORK_RESULT_SUCCESS_MODEL_DOWNLOADED;
  private static final TextClassifierDownloadWorkCompleted.WorkResult WORK_RESULT_ATOM =
      TextClassifierDownloadWorkCompleted.WorkResult.SUCCESS_MODEL_DOWNLOADED;
  private static final long SCHEDULED_TO_START_DURATION_MILLIS = 777L;
  private static final long STARTED_TO_FINISHED_DURATION_MILLIS = 888L;

  @Rule
  public final TextClassifierDownloadLoggerTestRule loggerTestRule =
      new TextClassifierDownloadLoggerTestRule();

  @Test
  public void downloadSucceeded() throws Exception {
    TextClassifierDownloadLogger.downloadSucceeded(
        WORK_ID, MODEL_TYPE, URL, RUN_ATTEMPT_COUNT, DOWNLOAD_DURATION_MILLIS);

    TextClassifierDownloadReported atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadReportedAtoms());
    assertThat(atom.getWorkId()).isEqualTo(WORK_ID);
    assertThat(atom.getDownloadStatus())
        .isEqualTo(TextClassifierDownloadReported.DownloadStatus.SUCCEEDED);
    assertThat(atom.getModelType()).isEqualTo(MODEL_TYPE_ATOM);
    assertThat(atom.getUrlSuffix()).isEqualTo(URL);
    assertThat(atom.getRunAttemptCount()).isEqualTo(RUN_ATTEMPT_COUNT);
    assertThat(atom.getDownloadDurationMillis()).isEqualTo(DOWNLOAD_DURATION_MILLIS);
  }

  @Test
  public void downloadFailed() throws Exception {
    TextClassifierDownloadLogger.downloadFailed(
        WORK_ID,
        MODEL_TYPE,
        URL,
        ERROR_CODE,
        RUN_ATTEMPT_COUNT,
        DOWNLOADER_LIB_ERROR_CODE,
        DOWNLOAD_DURATION_MILLIS);

    TextClassifierDownloadReported atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadReportedAtoms());
    assertThat(atom.getWorkId()).isEqualTo(WORK_ID);
    assertThat(atom.getDownloadStatus())
        .isEqualTo(TextClassifierDownloadReported.DownloadStatus.FAILED_AND_RETRY);
    assertThat(atom.getModelType()).isEqualTo(MODEL_TYPE_ATOM);
    assertThat(atom.getUrlSuffix()).isEqualTo(URL);
    assertThat(atom.getRunAttemptCount()).isEqualTo(RUN_ATTEMPT_COUNT);
    assertThat(atom.getFailureReason()).isEqualTo(FAILURE_REASON_ATOM);
    assertThat(atom.getDownloaderLibFailureCode()).isEqualTo(DOWNLOADER_LIB_ERROR_CODE);
    assertThat(atom.getDownloadDurationMillis()).isEqualTo(DOWNLOAD_DURATION_MILLIS);
  }

  @Test
  public void downloadWorkScheduled_succeeded() throws Exception {
    TextClassifierDownloadLogger.downloadWorkScheduled(
        WORK_ID, REASON_TO_SCHEDULE, /* failedToSchedule= */ false);

    TextClassifierDownloadWorkScheduled atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadWorkScheduledAtoms());
    assertThat(atom.getWorkId()).isEqualTo(WORK_ID);
    assertThat(atom.getReasonToSchedule()).isEqualTo(REASON_TO_SCHEDULE_ATOM);
    assertThat(atom.getFailedToSchedule()).isFalse();
  }

  @Test
  public void downloadWorkScheduled_failed() throws Exception {
    TextClassifierDownloadLogger.downloadWorkScheduled(
        WORK_ID, REASON_TO_SCHEDULE, /* failedToSchedule= */ true);

    TextClassifierDownloadWorkScheduled atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadWorkScheduledAtoms());
    assertThat(atom.getWorkId()).isEqualTo(WORK_ID);
    assertThat(atom.getReasonToSchedule()).isEqualTo(REASON_TO_SCHEDULE_ATOM);
    assertThat(atom.getFailedToSchedule()).isTrue();
  }

  @Test
  public void downloadWorkCompleted() throws Exception {
    TextClassifierDownloadLogger.downloadWorkCompleted(
        WORK_ID,
        WORK_RESULT,
        RUN_ATTEMPT_COUNT,
        SCHEDULED_TO_START_DURATION_MILLIS,
        STARTED_TO_FINISHED_DURATION_MILLIS);

    TextClassifierDownloadWorkCompleted atom =
        Iterables.getOnlyElement(loggerTestRule.getLoggedDownloadWorkCompletedAtoms());
    assertThat(atom.getWorkId()).isEqualTo(WORK_ID);
    assertThat(atom.getWorkResult()).isEqualTo(WORK_RESULT_ATOM);
    assertThat(atom.getRunAttemptCount()).isEqualTo(RUN_ATTEMPT_COUNT);
    assertThat(atom.getWorkScheduledToStartedDurationMillis())
        .isEqualTo(SCHEDULED_TO_START_DURATION_MILLIS);
    assertThat(atom.getWorkStartedToEndedDurationMillis())
        .isEqualTo(STARTED_TO_FINISHED_DURATION_MILLIS);
  }
}
