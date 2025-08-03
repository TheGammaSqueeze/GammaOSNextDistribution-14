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

package com.android.textclassifier.common.statsd;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.os.SystemClock;
import android.view.textclassifier.TextClassificationSessionId;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.android.textclassifier.common.base.TcLog;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import java.lang.annotation.Retention;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executor;

/** Logs the TextClassifier API usages. */
public final class TextClassifierApiUsageLogger {
  private static final String TAG = "ApiUsageLogger";

  public static final int API_TYPE_SUGGEST_SELECTION =
      TextClassifierStatsLog.TEXT_CLASSIFIER_API_USAGE_REPORTED__API_TYPE__SUGGEST_SELECTION;
  public static final int API_TYPE_CLASSIFY_TEXT =
      TextClassifierStatsLog.TEXT_CLASSIFIER_API_USAGE_REPORTED__API_TYPE__CLASSIFY_TEXT;
  public static final int API_TYPE_GENERATE_LINKS =
      TextClassifierStatsLog.TEXT_CLASSIFIER_API_USAGE_REPORTED__API_TYPE__GENERATE_LINKS;
  public static final int API_TYPE_SUGGEST_CONVERSATION_ACTIONS =
      TextClassifierStatsLog
          .TEXT_CLASSIFIER_API_USAGE_REPORTED__API_TYPE__SUGGEST_CONVERSATION_ACTIONS;
  public static final int API_TYPE_DETECT_LANGUAGES =
      TextClassifierStatsLog.TEXT_CLASSIFIER_API_USAGE_REPORTED__API_TYPE__DETECT_LANGUAGES;

  /** The type of the API. */
  @Retention(SOURCE)
  @IntDef({
    API_TYPE_SUGGEST_SELECTION,
    API_TYPE_CLASSIFY_TEXT,
    API_TYPE_GENERATE_LINKS,
    API_TYPE_SUGGEST_CONVERSATION_ACTIONS,
    API_TYPE_DETECT_LANGUAGES
  })
  public @interface ApiType {}

  private final Executor executor;

  private final Supplier<Integer> sampleRateSupplier;

  private final Random random;

  /**
   * @param sampleRateSupplier The rate at which log events are written. (e.g. 100 means there is a
   *     0.01 chance that a call to logGenerateLinks results in an event being written). To write
   *     all events, pass 1. To disable logging, pass any number < 1. Sampling is used to reduce the
   *     amount of logging data generated.
   * @param executor that is used to execute the logging work.
   */
  public TextClassifierApiUsageLogger(Supplier<Integer> sampleRateSupplier, Executor executor) {
    this.executor = Preconditions.checkNotNull(executor);
    this.sampleRateSupplier = sampleRateSupplier;
    this.random = new Random();
  }

  public Session createSession(
      @ApiType int apiType, @Nullable TextClassificationSessionId sessionId) {
    return new Session(apiType, sessionId);
  }

  /** A session to log an API invocation. Creates a new session for each API call. */
  public final class Session {
    @ApiType private final int apiType;
    @Nullable private final TextClassificationSessionId sessionId;
    private final long beginElapsedRealTime;

    private Session(@ApiType int apiType, @Nullable TextClassificationSessionId sessionId) {
      this.apiType = apiType;
      this.sessionId = sessionId;
      beginElapsedRealTime = SystemClock.elapsedRealtime();
    }

    public void reportSuccess() {
      reportInternal(/* success= */ true);
    }

    public void reportFailure() {
      reportInternal(/* success= */ false);
    }

    private void reportInternal(boolean success) {
      if (!shouldLog()) {
        return;
      }
      final long latencyInMillis = SystemClock.elapsedRealtime() - beginElapsedRealTime;
      if (TcLog.ENABLE_FULL_LOGGING) {
        TcLog.v(
            TAG,
            String.format(
                Locale.ENGLISH,
                "TextClassifierApiUsageLogger: apiType=%d success=%b latencyInMillis=%d",
                apiType,
                success,
                latencyInMillis));
      }
      executor.execute(
          () ->
              TextClassifierStatsLog.write(
                  TextClassifierStatsLog.TEXT_CLASSIFIER_API_USAGE_REPORTED,
                  apiType,
                  success
                      ? TextClassifierStatsLog
                          .TEXT_CLASSIFIER_API_USAGE_REPORTED__RESULT_TYPE__SUCCESS
                      : TextClassifierStatsLog
                          .TEXT_CLASSIFIER_API_USAGE_REPORTED__RESULT_TYPE__FAIL,
                  latencyInMillis,
                  sessionId == null ? "" : sessionId.getValue()));
    }
  }

  /** Returns whether this particular event should be logged. */
  private boolean shouldLog() {
    if (sampleRateSupplier.get() < 1) {
      return false;
    } else {
      return random.nextInt(sampleRateSupplier.get()) == 0;
    }
  }
}
