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

import android.view.textclassifier.TextClassifier;

/** Converts TextClassifier's WidgetTypes to enum values that are logged to server. */
final class WidgetTypeConverter {
  public static int toLoggingValue(String widgetType) {
    switch (widgetType) {
      case TextClassifier.WIDGET_TYPE_UNKNOWN:
        return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_UNKNOWN;
      case TextClassifier.WIDGET_TYPE_TEXTVIEW:
        return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_TEXTVIEW;
      case TextClassifier.WIDGET_TYPE_EDITTEXT:
        return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_EDITTEXT;
      case TextClassifier.WIDGET_TYPE_UNSELECTABLE_TEXTVIEW:
        return TextClassifierStatsLog
            .TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_UNSELECTABLE_TEXTVIEW;
      case TextClassifier.WIDGET_TYPE_WEBVIEW:
        return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_WEBVIEW;
      case TextClassifier.WIDGET_TYPE_EDIT_WEBVIEW:
        return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_EDIT_WEBVIEW;
      case TextClassifier.WIDGET_TYPE_CUSTOM_TEXTVIEW:
        return TextClassifierStatsLog
            .TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_CUSTOM_TEXTVIEW;
      case TextClassifier.WIDGET_TYPE_CUSTOM_EDITTEXT:
        return TextClassifierStatsLog
            .TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_CUSTOM_EDITTEXT;
      case TextClassifier.WIDGET_TYPE_CUSTOM_UNSELECTABLE_TEXTVIEW:
        return TextClassifierStatsLog
            .TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_CUSTOM_UNSELECTABLE_TEXTVIEW;
      case TextClassifier.WIDGET_TYPE_NOTIFICATION:
        return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_NOTIFICATION;
      case "clipboard": // TODO(tonymak) Replace it with WIDGET_TYPE_CLIPBOARD once S SDK is dropped
        return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_CLIPBOARD;
      default: // fall out
    }
    return TextClassifierStatsLog.TEXT_SELECTION_EVENT__WIDGET_TYPE__WIDGET_TYPE_UNKNOWN;
  }

  private WidgetTypeConverter() {}
}
