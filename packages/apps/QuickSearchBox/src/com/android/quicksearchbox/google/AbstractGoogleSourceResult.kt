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
package com.android.quicksearchbox.google

import android.content.ComponentName
import android.database.DataSetObserver
import com.android.quicksearchbox.R
import com.android.quicksearchbox.Source
import com.android.quicksearchbox.SourceResult
import com.android.quicksearchbox.SuggestionExtras

abstract class AbstractGoogleSourceResult(source: Source, userQuery: String) : SourceResult {
  private val mSource: Source
  @Suppress("MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT_WARNING")
  override val userQuery: String
  override var position = 0
  abstract override val count: Int
  abstract override val suggestionQuery: String?
  override val source: Source
    get() = mSource

  override fun close() {}
  override fun moveTo(pos: Int) {
    position = pos
  }

  override fun moveToNext(): Boolean {
    val size = count
    if (position >= size) {
      // Already past the end
      return false
    }
    position++
    return position < size
  }

  override fun registerDataSetObserver(observer: DataSetObserver?) {}
  override fun unregisterDataSetObserver(observer: DataSetObserver?) {}
  override val suggestionText1: String?
    get() = suggestionQuery
  override val suggestionSource: Source
    get() = mSource
  override val isSuggestionShortcut: Boolean
    get() = false
  override val shortcutId: String?
    get() = null
  override val suggestionFormat: String?
    get() = null
  override val suggestionIcon1: String
    get() = R.drawable.magnifying_glass.toString()
  override val suggestionIcon2: String?
    get() = null
  override val suggestionIntentAction: String?
    get() = mSource.defaultIntentAction
  override val suggestionIntentComponent: ComponentName?
    get() = mSource.intentComponent
  override val suggestionIntentDataString: String?
    get() = null
  override val suggestionIntentExtraData: String?
    get() = null
  override val suggestionLogType: String?
    get() = null
  override val suggestionText2: String?
    get() = null
  override val suggestionText2Url: String?
    get() = null
  override val isSpinnerWhileRefreshing: Boolean
    get() = false
  override val isWebSearchSuggestion: Boolean
    get() = true
  override val isHistorySuggestion: Boolean
    get() = false
  override val extras: SuggestionExtras?
    get() = null
  override val extraColumns: Collection<String>?
    get() = null

  init {
    mSource = source
    this.userQuery = userQuery
  }
}
