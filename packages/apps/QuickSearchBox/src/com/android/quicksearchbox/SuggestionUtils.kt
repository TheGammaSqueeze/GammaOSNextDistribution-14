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

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.common.annotations.VisibleForTesting
import kotlin.text.StringBuilder

/** Some utilities for suggestions. */
object SuggestionUtils {
  @JvmStatic
  fun getSuggestionIntent(suggestion: SuggestionCursor?, appSearchData: Bundle?): Intent {
    val action: String? = suggestion?.suggestionIntentAction
    val data: String? = suggestion?.suggestionIntentDataString
    val query: String? = suggestion?.suggestionQuery
    val userQuery: String? = suggestion?.userQuery
    val extraData: String? = suggestion?.suggestionIntentExtraData

    // Now build the Intent
    val intent = Intent(action)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // We need CLEAR_TOP to avoid reusing an old task that has other activities
    // on top of the one we want.
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (data != null) {
      intent.setData(Uri.parse(data))
    }
    intent.putExtra(SearchManager.USER_QUERY, userQuery)
    if (query != null) {
      intent.putExtra(SearchManager.QUERY, query)
    }
    if (extraData != null) {
      intent.putExtra(SearchManager.EXTRA_DATA_KEY, extraData)
    }
    if (appSearchData != null) {
      intent.putExtra(SearchManager.APP_DATA, appSearchData)
    }
    intent.setComponent(suggestion?.suggestionIntentComponent)
    return intent
  }

  /**
   * Gets a unique key that identifies a suggestion. This is used to avoid duplicate suggestions.
   */
  @JvmStatic
  fun getSuggestionKey(suggestion: Suggestion): String {
    val action: String = makeKeyComponent(suggestion.suggestionIntentAction)
    val data: String = makeKeyComponent(normalizeUrl(suggestion.suggestionIntentDataString))
    val query: String = makeKeyComponent(normalizeUrl(suggestion.suggestionQuery))
    // calculating accurate size of string builder avoids an allocation vs starting with
    // the default size and having to expand.
    val size: Int = action.length + 2 + data.length + query.length
    return StringBuilder(size)
      .append(action)
      .append('#')
      .append(data)
      .append('#')
      .append(query)
      .toString()
  }

  private fun makeKeyComponent(str: String?): String {
    return str ?: ""
  }

  private const val SCHEME_SEPARATOR = "://"
  private const val DEFAULT_SCHEME = "http"

  /**
   * Simple url normalization that adds http:// if no scheme exists, and strips empty paths, e.g.,
   * www.google.com/ -> http://www.google.com. Used to prevent obvious duplication of nav
   * suggestions, bookmarks and urls entered by the user.
   */
  @JvmStatic
  @VisibleForTesting
  fun normalizeUrl(url: String?): String? {
    val normalized: String
    if (url != null) {
      val start: Int
      val schemePos: Int = url.indexOf(SCHEME_SEPARATOR)
      if (schemePos == -1) {
        // no scheme - add the default
        normalized = DEFAULT_SCHEME + SCHEME_SEPARATOR + url
        start = DEFAULT_SCHEME.length + SCHEME_SEPARATOR.length
      } else {
        normalized = url
        start = schemePos + SCHEME_SEPARATOR.length
      }
      var end: Int = normalized.length
      if (normalized.indexOf('/', start) == end - 1) {
        end--
      }
      return normalized.substring(0, end)
    }
    return url
  }
}
