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
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import com.android.quicksearchbox.util.NowOrLater

/** Interface for suggestion sources. */
interface Source : SuggestionCursorProvider<com.android.quicksearchbox.SourceResult?> {
  /** Gets the name activity that intents from this source are sent to. */
  val intentComponent: ComponentName?

  /** Gets the suggestion URI for getting suggestions from this Source. */
  val suggestUri: String?

  /** Gets the localized, human-readable label for this source. */
  val label: CharSequence?

  /** Gets the icon for this suggestion source. */
  val sourceIcon: Drawable?

  /** Gets the icon URI for this suggestion source. */
  val sourceIconUri: Uri?

  /**
   * Gets an icon from this suggestion source.
   *
   * @param drawableId Resource ID or URI.
   */
  fun getIcon(drawableId: String?): NowOrLater<Drawable?>?

  /**
   * Gets the URI for an icon form this suggestion source.
   *
   * @param drawableId Resource ID or URI.
   */
  fun getIconUri(drawableId: String?): Uri?

  /** Gets the search hint text for this suggestion source. */
  val hint: CharSequence?

  /** Gets the description to use for this source in system search settings. */
  val settingsDescription: CharSequence?

  /**
   *
   * Note: this does not guarantee that this source will be queried for queries of this length or
   * longer, only that it will not be queried for anything shorter.
   *
   * @return The minimum number of characters needed to trigger this source.
   */
  val queryThreshold: Int

  /**
   * Indicates whether a source should be invoked for supersets of queries it has returned zero
   * results for in the past. For example, if a source returned zero results for "bo", it would be
   * ignored for "bob".
   *
   * If set to `false`, this source will only be ignored for a single session; the next time the
   * search dialog is brought up, all sources will be queried.
   *
   * @return `true` if this source should be queried after returning no results.
   */
  fun queryAfterZeroResults(): Boolean
  fun voiceSearchEnabled(): Boolean

  /**
   * Whether this source should be included in the blended All mode. The source must also be enabled
   * to be included in All.
   */
  fun includeInAll(): Boolean
  fun createSearchIntent(query: String?, appData: Bundle?): Intent?
  fun createVoiceSearchIntent(appData: Bundle?): Intent?

  /** Checks if the current process can read the suggestions from this source. */
  fun canRead(): Boolean

  /**
   * Gets suggestions from this source.
   *
   * @param query The user query.
   * @return The suggestion results.
   */
  @Override override fun getSuggestions(query: String?, queryLimit: Int): SourceResult?

  /**
   * Gets the default intent action for suggestions from this source.
   *
   * @return The default intent action, or `null`.
   */
  val defaultIntentAction: String?

  /**
   * Gets the default intent data for suggestions from this source.
   *
   * @return The default intent data, or `null`.
   */
  val defaultIntentData: String?

  /**
   * Gets the root source, if this source is a wrapper around another. Otherwise, returns this
   * source.
   */
  fun getRoot(): Source
}
