/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quicksearchbox

import android.content.ComponentName

/** Interface for individual suggestions. */
interface Suggestion {
  /** Gets the source that produced the current suggestion. */
  val suggestionSource: com.android.quicksearchbox.Source?

  /** Gets the shortcut ID of the current suggestion. */
  val shortcutId: String?

  /** Whether to show a spinner while refreshing this shortcut. */
  val isSpinnerWhileRefreshing: Boolean

  /**
   * Gets the format of the text returned by [.getSuggestionText1] and [.getSuggestionText2].
   *
   * @return `null` or "html"
   */
  val suggestionFormat: String?

  /** Gets the first text line for the current suggestion. */
  val suggestionText1: String?

  /** Gets the second text line for the current suggestion. */
  val suggestionText2: String?

  /** Gets the second text line URL for the current suggestion. */
  val suggestionText2Url: String?

  /**
   * Gets the left-hand-side icon for the current suggestion.
   *
   * @return A string that can be passed to [Source.getIcon].
   */
  val suggestionIcon1: String?

  /**
   * Gets the right-hand-side icon for the current suggestion.
   *
   * @return A string that can be passed to [Source.getIcon].
   */
  val suggestionIcon2: String?

  /** Gets the intent action for the current suggestion. */
  val suggestionIntentAction: String?

  /** Gets the name of the activity that the intent for the current suggestion will be sent to. */
  val suggestionIntentComponent: ComponentName?

  /** Gets the extra data associated with this suggestion's intent. */
  val suggestionIntentExtraData: String?

  /** Gets the data associated with this suggestion's intent. */
  val suggestionIntentDataString: String?

  /** Gets the query associated with this suggestion's intent. */
  val suggestionQuery: String?

  /**
   * Gets the suggestion log type for the current suggestion. This is logged together with the value
   * returned from [Source.getName]. The value is source-specific. Most sources return `null`.
   */
  val suggestionLogType: String?

  /** Checks if this suggestion is a shortcut. */
  val isSuggestionShortcut: Boolean

  /** Checks if this is a web search suggestion. */
  val isWebSearchSuggestion: Boolean

  /** Checks whether this suggestion comes from the user's search history. */
  val isHistorySuggestion: Boolean

  /** Returns any extras associated with this suggestion, or `null` if there are none. */
  val extras: com.android.quicksearchbox.SuggestionExtras?
}
