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

import android.util.Log

/**
 * A SuggestionCursor that is backed by a list of SuggestionPosition objects and doesn't allow
 * duplicate suggestions.
 */
class ListSuggestionCursorNoDuplicates(userQuery: String?) : ListSuggestionCursor(userQuery) {
  private val mSuggestionKeys: HashSet<String>

  @Override
  override fun add(suggestion: Suggestion): Boolean {
    val key = SuggestionUtils.getSuggestionKey(suggestion)
    return if (mSuggestionKeys.add(key)) {
      super.add(suggestion)
    } else {
      if (DBG) Log.d(TAG, "Rejecting duplicate $key")
      false
    }
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.ListSuggestionCursorNoDuplicates"
  }

  init {
    mSuggestionKeys = HashSet<String>()
  }
}
