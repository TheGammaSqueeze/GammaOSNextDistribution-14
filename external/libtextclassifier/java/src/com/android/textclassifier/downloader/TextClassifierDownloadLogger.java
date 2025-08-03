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

import static com.android.textclassifier.downloader.ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE;

import android.text.TextUtils;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.common.statsd.TextClassifierStatsLog;
import com.android.textclassifier.downloader.ModelDownloadException.ErrorCode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Locale;

/** Logs TextClassifier download event. */
final class TextClassifierDownloadLogger {
  private static final String TAG = "TextClassifierDownloadLogger";

  // Values for TextClassifierDownloadReported.download_status
  private static final int DOWNLOAD_STATUS_SUCCEEDED =
      TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_REPORTED__DOWNLOAD_STATUS__SUCCEEDED;
  private static final int DOWNLOAD_STATUS_FAILED_AND_RETRY =
      TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_REPORTED__DOWNLOAD_STATUS__FAILED_AND_RETRY;

  private static final int DEFAULT_MODEL_TYPE =
      TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_REPORTED__MODEL_TYPE__UNKNOWN_MODEL_TYPE;
  private static final ImmutableMap<String, Integer> MODEL_TYPE_MAP =
      ImmutableMap.of(
          ModelType.ANNOTATOR,
              TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_REPORTED__MODEL_TYPE__ANNOTATOR,
          ModelType.LANG_ID,
              TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_REPORTED__MODEL_TYPE__LANG_ID,
          ModelType.ACTIONS_SUGGESTIONS,
              TextClassifierStatsLog
                  .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__MODEL_TYPE__ACTIONS_SUGGESTIONS);

  private static final int DEFAULT_FILE_TYPE =
      TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FILE_TYPE__UNKNOWN_FILE_TYPE;

  private static final int DEFAULT_FAILURE_REASON =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FAILURE_REASON__UNKNOWN_FAILURE_REASON;
  private static final ImmutableMap<Integer, Integer> FAILURE_REASON_MAP =
      ImmutableMap.<Integer, Integer>builder()
          .put(
              ModelDownloadException.UNKNOWN_FAILURE_REASON,
              TextClassifierStatsLog
                  .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FAILURE_REASON__UNKNOWN_FAILURE_REASON)
          .put(
              ModelDownloadException.FAILED_TO_DOWNLOAD_SERVICE_CONN_BROKEN,
              TextClassifierStatsLog
                  .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FAILURE_REASON__FAILED_TO_DOWNLOAD_SERVICE_CONN_BROKEN)
          .put(
              ModelDownloadException.FAILED_TO_DOWNLOAD_404_ERROR,
              TextClassifierStatsLog
                  .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FAILURE_REASON__FAILED_TO_DOWNLOAD_404_ERROR)
          .put(
              ModelDownloadException.FAILED_TO_DOWNLOAD_OTHER,
              TextClassifierStatsLog
                  .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FAILURE_REASON__FAILED_TO_DOWNLOAD_OTHER)
          .put(
              ModelDownloadException.DOWNLOADED_FILE_MISSING,
              TextClassifierStatsLog
                  .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FAILURE_REASON__DOWNLOADED_FILE_MISSING)
          .put(
              ModelDownloadException.FAILED_TO_PARSE_MANIFEST,
              TextClassifierStatsLog
                  .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FAILURE_REASON__FAILED_TO_PARSE_MANIFEST)
          .put(
              ModelDownloadException.FAILED_TO_VALIDATE_MODEL,
              TextClassifierStatsLog
                  .TEXT_CLASSIFIER_DOWNLOAD_REPORTED__FAILURE_REASON__FAILED_TO_VALIDATE_MODEL)
          .buildOrThrow();

  // Reasons to schedule
  public static final int REASON_TO_SCHEDULE_TCS_STARTED =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_SCHEDULED__REASON_TO_SCHEDULE__TCS_STARTED;
  public static final int REASON_TO_SCHEDULE_LOCALE_SETTINGS_CHANGED =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_SCHEDULED__REASON_TO_SCHEDULE__LOCALE_SETTINGS_CHANGED;
  public static final int REASON_TO_SCHEDULE_DEVICE_CONFIG_UPDATED =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_SCHEDULED__REASON_TO_SCHEDULE__DEVICE_CONFIG_UPDATED;

  // Work results
  public static final int WORK_RESULT_UNKNOWN_WORK_RESULT =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED__WORK_RESULT__UNKNOWN_WORK_RESULT;
  public static final int WORK_RESULT_SUCCESS_MODEL_DOWNLOADED =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED__WORK_RESULT__SUCCESS_MODEL_DOWNLOADED;
  public static final int WORK_RESULT_SUCCESS_NO_UPDATE_AVAILABLE =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED__WORK_RESULT__SUCCESS_NO_UPDATE_AVAILABLE;
  public static final int WORK_RESULT_FAILURE_MODEL_DOWNLOADER_DISABLED =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED__WORK_RESULT__FAILURE_MODEL_DOWNLOADER_DISABLED;
  public static final int WORK_RESULT_FAILURE_MAX_RUN_ATTEMPT_REACHED =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED__WORK_RESULT__FAILURE_MAX_RUN_ATTEMPT_REACHED;
  public static final int WORK_RESULT_RETRY_MODEL_DOWNLOAD_FAILED =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED__WORK_RESULT__RETRY_MODEL_DOWNLOAD_FAILED;
  public static final int WORK_RESULT_RETRY_RUNTIME_EXCEPTION =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED__WORK_RESULT__RETRY_RUNTIME_EXCEPTION;
  public static final int WORK_RESULT_RETRY_STOPPED_BY_OS =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED__WORK_RESULT__RETRY_STOPPED_BY_OS;

  /** Logs a succeeded download task. */
  public static void downloadSucceeded(
      long workId,
      @ModelTypeDef String modelType,
      String url,
      int runAttemptCount,
      long downloadDurationMillis) {
    Preconditions.checkArgument(!TextUtils.isEmpty(url), "url cannot be null/empty");
    TextClassifierStatsLog.write(
        TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_REPORTED,
        MODEL_TYPE_MAP.getOrDefault(modelType, DEFAULT_MODEL_TYPE),
        DEFAULT_FILE_TYPE,
        DOWNLOAD_STATUS_SUCCEEDED,
        url,
        DEFAULT_FAILURE_REASON,
        runAttemptCount,
        DEFAULT_DOWNLOADER_LIB_ERROR_CODE,
        downloadDurationMillis,
        workId);
    if (TcLog.ENABLE_FULL_LOGGING) {
      TcLog.v(
          TAG,
          String.format(
              Locale.US,
              "Download Reported: modelType=%s, fileType=%d, status=%d, url=%s, "
                  + "failureReason=%d, runAttemptCount=%d, downloaderLibErrorCode=%d, "
                  + "downloadDurationMillis=%d, workId=%d",
              MODEL_TYPE_MAP.getOrDefault(modelType, DEFAULT_MODEL_TYPE),
              DEFAULT_FILE_TYPE,
              DOWNLOAD_STATUS_SUCCEEDED,
              url,
              DEFAULT_FAILURE_REASON,
              runAttemptCount,
              DEFAULT_DOWNLOADER_LIB_ERROR_CODE,
              downloadDurationMillis,
              workId));
    }
  }

  /** Logs a failed download task which will be retried later. */
  public static void downloadFailed(
      long workId,
      @ModelTypeDef String modelType,
      String url,
      @ErrorCode int errorCode,
      int runAttemptCount,
      int downloaderLibErrorCode,
      long downloadDurationMillis) {
    Preconditions.checkArgument(!TextUtils.isEmpty(url), "url cannot be null/empty");
    TextClassifierStatsLog.write(
        TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_REPORTED,
        MODEL_TYPE_MAP.getOrDefault(modelType, DEFAULT_MODEL_TYPE),
        DEFAULT_FILE_TYPE,
        DOWNLOAD_STATUS_FAILED_AND_RETRY,
        url,
        FAILURE_REASON_MAP.getOrDefault(errorCode, DEFAULT_FAILURE_REASON),
        runAttemptCount,
        downloaderLibErrorCode,
        downloadDurationMillis,
        workId);
    if (TcLog.ENABLE_FULL_LOGGING) {
      TcLog.v(
          TAG,
          String.format(
              Locale.US,
              "Download Reported: modelType=%s, fileType=%d, status=%d, url=%s, "
                  + "failureReason=%d, runAttemptCount=%d, downloaderLibErrorCode=%d, "
                  + "downloadDurationMillis=%d, workId=%d",
              MODEL_TYPE_MAP.getOrDefault(modelType, DEFAULT_MODEL_TYPE),
              DEFAULT_FILE_TYPE,
              DOWNLOAD_STATUS_FAILED_AND_RETRY,
              url,
              FAILURE_REASON_MAP.getOrDefault(errorCode, DEFAULT_FAILURE_REASON),
              runAttemptCount,
              downloaderLibErrorCode,
              downloadDurationMillis,
              workId));
    }
  }

  public static void downloadWorkScheduled(
      long workId, int reasonToSchedule, boolean failedToSchedule) {
    TextClassifierStatsLog.write(
        TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_WORK_SCHEDULED,
        workId,
        reasonToSchedule,
        failedToSchedule);
    if (TcLog.ENABLE_FULL_LOGGING) {
      TcLog.v(
          TAG,
          String.format(
              Locale.US,
              "Download Work Scheduled: workId=%d, reasonToSchedule=%d, failedToSchedule=%b",
              workId,
              reasonToSchedule,
              failedToSchedule));
    }
  }

  public static void downloadWorkCompleted(
      long workId,
      int workResult,
      int runAttemptCount,
      long workScheduledToStartedDurationMillis,
      long workStartedToEndedDurationMillis) {
    TextClassifierStatsLog.write(
        TextClassifierStatsLog.TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED,
        workId,
        workResult,
        runAttemptCount,
        workScheduledToStartedDurationMillis,
        workStartedToEndedDurationMillis);
    if (TcLog.ENABLE_FULL_LOGGING) {
      TcLog.v(
          TAG,
          String.format(
              Locale.US,
              "Download Work Completed: workId=%d, result=%d, runAttemptCount=%d, "
                  + "workScheduledToStartedDurationMillis=%d, "
                  + "workStartedToEndedDurationMillis=%d",
              workId,
              workResult,
              runAttemptCount,
              workScheduledToStartedDurationMillis,
              workStartedToEndedDurationMillis));
    }
  }

  private TextClassifierDownloadLogger() {}
}
