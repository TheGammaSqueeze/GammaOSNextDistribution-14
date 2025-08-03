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

/** Interface for logging implementations. */
interface Logger {
  /**
   * Called when QSB has started.
   *
   * @param latency User-visible start-up latency in milliseconds.
   */
  fun logStart(onCreateLatency: Int, latency: Int, intentSource: String?)

  /**
   * Called when a suggestion is clicked.
   *
   * @param suggestionId Suggestion ID; 0-based position of the suggestion in the UI if the list is
   * flat.
   * @param suggestionCursor all the suggestions shown in the UI.
   * @param clickType One of the SUGGESTION_CLICK_TYPE constants.
   */
  fun logSuggestionClick(suggestionId: Long, suggestionCursor: SuggestionCursor?, clickType: Int)

  /**
   * The user launched a search.
   *
   * @param startMethod One of [.SEARCH_METHOD_BUTTON] or [.SEARCH_METHOD_KEYBOARD].
   * @param numChars The number of characters in the query.
   */
  fun logSearch(startMethod: Int, numChars: Int)

  /** The user launched a voice search. */
  fun logVoiceSearch()

  /**
   * The user left QSB without performing any action (click suggestions, search or voice search).
   *
   * @param suggestionCursor all the suggestions shown in the UI when the user left
   * @param numChars The number of characters in the query typed when the user left.
   */
  fun logExit(suggestionCursor: SuggestionCursor?, numChars: Int)

  /**
   * Logs the latency of a suggestion query to a specific source.
   *
   * @param result The result of the query.
   */
  fun logLatency(result: SourceResult?)

  companion object {
    const val SEARCH_METHOD_BUTTON = 0
    const val SEARCH_METHOD_KEYBOARD = 1
    const val SUGGESTION_CLICK_TYPE_LAUNCH = 0
    const val SUGGESTION_CLICK_TYPE_REFINE = 1
    const val SUGGESTION_CLICK_TYPE_QUICK_CONTACT = 2
  }
}
