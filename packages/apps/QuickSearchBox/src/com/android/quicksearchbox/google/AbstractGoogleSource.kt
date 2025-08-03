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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import com.android.quicksearchbox.AbstractInternalSource
import com.android.quicksearchbox.CursorBackedSourceResult
import com.android.quicksearchbox.R
import com.android.quicksearchbox.SourceResult
import com.android.quicksearchbox.SuggestionCursor
import com.android.quicksearchbox.util.NamedTaskExecutor

/** Special source implementation for Google suggestions. */
abstract class AbstractGoogleSource(
  context: Context?,
  uiThread: Handler?,
  iconLoader: NamedTaskExecutor
) :
  AbstractInternalSource(context, uiThread, iconLoader),
  com.android.quicksearchbox.google.GoogleSource {
  @get:Override abstract override val intentComponent: ComponentName?

  @Override
  abstract override fun refreshShortcut(shortcutId: String?, extraData: String?): SuggestionCursor?

  /** Called by QSB to get web suggestions for a query. */
  @Override abstract override fun queryInternal(query: String?): SourceResult?

  /** Called by external apps to get web suggestions for a query. */
  @Override abstract override fun queryExternal(query: String?): SourceResult?

  @Override
  override fun createVoiceSearchIntent(appData: Bundle?): Intent? {
    return createVoiceWebSearchIntent(appData)
  }

  @get:Override
  override val defaultIntentAction: String
    get() = Intent.ACTION_WEB_SEARCH

  @get:Override
  override val hint: CharSequence
    get() = context!!.getString(R.string.google_search_hint)

  @get:Override
  override val label: CharSequence
    get() = context!!.getString(R.string.google_search_label)

  @get:Override
  override val name: String
    get() = AbstractGoogleSource.Companion.GOOGLE_SOURCE_NAME

  @get:Override
  override val settingsDescription: CharSequence
    get() = context!!.getString(R.string.google_search_description)

  @get:Override
  override val sourceIconResource: Int
    get() = R.mipmap.google_icon

  @Override
  override fun getSuggestions(query: String?, queryLimit: Int): SourceResult? {
    return emptyIfNull(queryInternal(query), query)
  }

  fun getSuggestionsExternal(query: String?): SourceResult {
    return emptyIfNull(queryExternal(query), query)
  }

  private fun emptyIfNull(result: SourceResult?, query: String?): SourceResult {
    return if (result == null) CursorBackedSourceResult(this, query) else result
  }

  @Override
  override fun voiceSearchEnabled(): Boolean {
    return true
  }

  @Override
  override fun includeInAll(): Boolean {
    return true
  }

  companion object {
    /*
     * This name corresponds to what was used in previous version of quick search box. We use the
     * same name so that shortcuts continue to work after an upgrade. (It also makes logging more
     * consistent).
     */
    private const val GOOGLE_SOURCE_NAME = "com.android.quicksearchbox/.google.GoogleSearch"
  }
}
