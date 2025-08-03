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

/** A Suggestion that delegates all calls to other suggestions. */
abstract class AbstractSuggestionWrapper : Suggestion {
  /** Gets the current suggestion. */
  protected abstract fun current(): Suggestion?
  override val shortcutId: String?
    get() = current()?.shortcutId
  override val suggestionFormat: String?
    get() = current()?.suggestionFormat
  override val suggestionIcon1: String?
    get() = current()?.suggestionIcon1
  override val suggestionIcon2: String?
    get() = current()?.suggestionIcon2
  override val suggestionIntentAction: String?
    get() = current()?.suggestionIntentAction
  override val suggestionIntentComponent: ComponentName?
    get() = current()?.suggestionIntentComponent
  override val suggestionIntentDataString: String?
    get() = current()?.suggestionIntentDataString
  override val suggestionIntentExtraData: String?
    get() = current()?.suggestionIntentExtraData
  override val suggestionLogType: String?
    get() = current()?.suggestionLogType
  override val suggestionQuery: String?
    get() = current()?.suggestionQuery
  override val suggestionSource: Source?
    get() = current()?.suggestionSource
  override val suggestionText1: String?
    get() = current()?.suggestionText1
  override val suggestionText2: String?
    get() = current()?.suggestionText2
  override val suggestionText2Url: String?
    get() = current()?.suggestionText2Url
  override val isSpinnerWhileRefreshing: Boolean
    get() = current()?.isSpinnerWhileRefreshing == true
  override val isSuggestionShortcut: Boolean
    get() = current()?.isSuggestionShortcut == true
  override val isWebSearchSuggestion: Boolean
    get() = current()?.isWebSearchSuggestion == true
  override val isHistorySuggestion: Boolean
    get() = current()?.isHistorySuggestion == true
  override val extras: SuggestionExtras?
    get() = current()?.extras
}
