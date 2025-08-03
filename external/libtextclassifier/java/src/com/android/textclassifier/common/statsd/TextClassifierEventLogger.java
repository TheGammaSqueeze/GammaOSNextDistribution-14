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

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Strings.nullToEmpty;

import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.common.logging.ResultIdUtils;
import com.android.textclassifier.common.logging.TextClassificationContext;
import com.android.textclassifier.common.logging.TextClassificationSessionId;
import com.android.textclassifier.common.logging.TextClassifierEvent;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import java.util.List;
import javax.annotation.Nullable;

/** Logs {@link android.view.textclassifier.TextClassifierEvent}. */
public final class TextClassifierEventLogger {
  private static final String TAG = "TCEventLogger";

  /** Emits a text classifier event to the logs. */
  public void writeEvent(
      @Nullable TextClassificationSessionId sessionId, TextClassifierEvent event) {
    Preconditions.checkNotNull(event);
    if (TcLog.ENABLE_FULL_LOGGING) {
      TcLog.v(
          TAG,
          String.format(
              "TextClassifierEventLogger.writeEvent: sessionId=%s,event=%s", sessionId, event));
    }
    if (event instanceof TextClassifierEvent.TextSelectionEvent) {
      logTextSelectionEvent(sessionId, (TextClassifierEvent.TextSelectionEvent) event);
    } else if (event instanceof TextClassifierEvent.TextLinkifyEvent) {
      logTextLinkifyEvent(sessionId, (TextClassifierEvent.TextLinkifyEvent) event);
    } else if (event instanceof TextClassifierEvent.ConversationActionsEvent) {
      logConversationActionsEvent(sessionId, (TextClassifierEvent.ConversationActionsEvent) event);
    } else if (event instanceof TextClassifierEvent.LanguageDetectionEvent) {
      logLanguageDetectionEvent(sessionId, (TextClassifierEvent.LanguageDetectionEvent) event);
    } else {
      TcLog.w(TAG, "Unexpected events, category=" + event.getEventCategory());
    }
  }

  private static void logTextSelectionEvent(
      @Nullable TextClassificationSessionId sessionId,
      TextClassifierEvent.TextSelectionEvent event) {
    ImmutableList<String> modelNames = getModelNames(event);
    TextClassifierStatsLog.write(
        TextClassifierStatsLog.TEXT_SELECTION_EVENT,
        sessionId == null ? null : sessionId.getValue(),
        getEventType(event),
        getItemAt(modelNames, /* index= */ 0, /* defaultValue= */ null),
        getWidgetType(event),
        event.getEventIndex(),
        getItemAt(event.getEntityTypes(), /* index= */ 0),
        event.getRelativeWordStartIndex(),
        event.getRelativeWordEndIndex(),
        event.getRelativeSuggestedWordStartIndex(),
        event.getRelativeSuggestedWordEndIndex(),
        getPackageName(event),
        getItemAt(modelNames, /* index= */ 1, /* defaultValue= */ null));
  }

  private static int getEventType(TextClassifierEvent.TextSelectionEvent event) {
    if (event.getEventType() == TextClassifierEvent.TYPE_AUTO_SELECTION) {
      if (ResultIdUtils.isFromDefaultTextClassifier(event.getResultId())) {
        return event.getRelativeWordEndIndex() - event.getRelativeWordStartIndex() > 1
            ? TextClassifierEvent.TYPE_SMART_SELECTION_MULTI
            : TextClassifierEvent.TYPE_SMART_SELECTION_SINGLE;
      }
    }
    return event.getEventType();
  }

  private static void logTextLinkifyEvent(
      TextClassificationSessionId sessionId, TextClassifierEvent.TextLinkifyEvent event) {
    ImmutableList<String> modelNames = getModelNames(event);
    TextClassifierStatsLog.write(
        TextClassifierStatsLog.TEXT_LINKIFY_EVENT,
        sessionId == null ? null : sessionId.getValue(),
        event.getEventType(),
        getItemAt(modelNames, /* index= */ 0, /* defaultValue= */ null),
        getWidgetType(event),
        event.getEventIndex(),
        getItemAt(event.getEntityTypes(), /* index= */ 0),
        /* numOfLinks */ 0,
        /* linkedTextLength */ 0,
        /* textLength */ 0,
        /* latencyInMillis */ 0L,
        getPackageName(event),
        getItemAt(modelNames, /* index= */ 1, /* defaultValue= */ null));
  }

  private static void logConversationActionsEvent(
      @Nullable TextClassificationSessionId sessionId,
      TextClassifierEvent.ConversationActionsEvent event) {
    String resultId = nullToEmpty(event.getResultId());
    ImmutableList<String> modelNames = ResultIdUtils.getModelNames(resultId);
    TextClassifierStatsLog.write(
        TextClassifierStatsLog.CONVERSATION_ACTIONS_EVENT,
        // TODO: Update ExtServices to set the session id.
        sessionId == null
            ? Hashing.goodFastHash(64).hashString(resultId, UTF_8).toString()
            : sessionId.getValue(),
        event.getEventType(),
        getItemAt(modelNames, /* index= */ 0, /* defaultValue= */ null),
        getWidgetType(event),
        getItemAt(event.getEntityTypes(), /* index= */ 0),
        getItemAt(event.getEntityTypes(), /* index= */ 1),
        getItemAt(event.getEntityTypes(), /* index= */ 2),
        getFloatAt(event.getScores(), /* index= */ 0),
        getPackageName(event),
        getItemAt(modelNames, /* index= */ 1, /* defaultValue= */ null),
        getItemAt(modelNames, /* index= */ 2, /* defaultValue= */ null));
  }

  private static void logLanguageDetectionEvent(
      @Nullable TextClassificationSessionId sessionId,
      TextClassifierEvent.LanguageDetectionEvent event) {
    TextClassifierStatsLog.write(
        TextClassifierStatsLog.LANGUAGE_DETECTION_EVENT,
        sessionId == null ? null : sessionId.getValue(),
        event.getEventType(),
        getItemAt(getModelNames(event), /* index= */ 0, /* defaultValue= */ null),
        getWidgetType(event),
        getItemAt(event.getEntityTypes(), /* index= */ 0),
        getFloatAt(event.getScores(), /* index= */ 0),
        getIntAt(event.getActionIndices(), /* index= */ 0),
        getPackageName(event));
  }

  @Nullable
  private static <T> T getItemAt(List<T> list, int index, T defaultValue) {
    if (list == null) {
      return defaultValue;
    }
    if (index >= list.size()) {
      return defaultValue;
    }
    return list.get(index);
  }

  @Nullable
  private static <T> T getItemAt(@Nullable T[] array, int index) {
    if (array == null) {
      return null;
    }
    if (index >= array.length) {
      return null;
    }
    return array[index];
  }

  private static float getFloatAt(@Nullable float[] array, int index) {
    if (array == null) {
      return 0f;
    }
    if (index >= array.length) {
      return 0f;
    }
    return array[index];
  }

  private static int getIntAt(@Nullable int[] array, int index) {
    if (array == null) {
      return 0;
    }
    if (index >= array.length) {
      return 0;
    }
    return array[index];
  }

  private static ImmutableList<String> getModelNames(TextClassifierEvent event) {
    if (event.getModelName() != null) {
      return ImmutableList.of(event.getModelName());
    }
    return ResultIdUtils.getModelNames(event.getResultId());
  }

  private static int getWidgetType(TextClassifierEvent event) {
    TextClassificationContext eventContext = event.getEventContext();
    if (eventContext == null) {
      return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_UNKNOWN;
    }
    return WidgetTypeConverter.toLoggingValue(eventContext.getWidgetType());
  }

  @Nullable
  private static String getPackageName(TextClassifierEvent event) {
    TextClassificationContext eventContext = event.getEventContext();
    if (eventContext == null) {
      return null;
    }
    return eventContext.getPackageName();
  }
}
