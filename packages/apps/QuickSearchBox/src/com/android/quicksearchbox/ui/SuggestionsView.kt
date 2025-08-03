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

package com.android.quicksearchbox.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ListAdapter
import android.widget.ListView
import com.android.quicksearchbox.SuggestionPosition

/** Holds a list of suggestions. */
class SuggestionsView(context: Context?, attrs: AttributeSet?) :
  ListView(context, attrs), SuggestionsListView<ListAdapter?> {
  private var mSuggestionsAdapter: SuggestionsAdapter<ListAdapter?>? = null

  @Override
  override fun setSuggestionsAdapter(adapter: SuggestionsAdapter<ListAdapter?>?) {
    super.setAdapter(adapter?.listAdapter)
    mSuggestionsAdapter = adapter
  }

  @Override
  override fun getSuggestionsAdapter(): SuggestionsAdapter<ListAdapter?>? {
    return mSuggestionsAdapter
  }

  @Override
  override fun onFinishInflate() {
    super.onFinishInflate()
    setItemsCanFocus(true)
  }

  /**
   * Gets the position of the selected suggestion.
   *
   * @return A 0-based index, or `-1` if no suggestion is selected.
   */
  val selectedPosition: Int
    get() = getSelectedItemPosition()

  /**
   * Gets the selected suggestion.
   *
   * @return `null` if no suggestion is selected.
   */
  val selectedSuggestion: SuggestionPosition
    get() = getSelectedItem() as SuggestionPosition

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.SuggestionsView"
  }
}
