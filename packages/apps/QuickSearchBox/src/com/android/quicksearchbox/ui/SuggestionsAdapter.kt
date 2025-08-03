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

import android.view.View.OnFocusChangeListener
import android.widget.ExpandableListAdapter
import android.widget.ListAdapter
import com.android.quicksearchbox.SuggestionPosition
import com.android.quicksearchbox.Suggestions

/**
 * Interface for suggestions adapters.
 *
 * @param <A> the adapter class used by the UI, probably either [ListAdapter] or
 * [ExpandableListAdapter].
 */
interface SuggestionsAdapter<A> {
  /** Sets the listener to be notified of clicks on suggestions. */
  fun setSuggestionClickListener(listener: SuggestionClickListener?)

  /** Sets the listener to be notified of focus change events on suggestion views. */
  fun setOnFocusChangeListener(l: OnFocusChangeListener?)

  /** Indicates if there's any suggestions in this adapter. */
  val isEmpty: Boolean
  /** Gets the current suggestions. */
  /** Sets the current suggestions. */
  var suggestions: Suggestions?

  /**
   * Gets the cursor and position corresponding to the given suggestion ID.
   * @param suggestionId Suggestion ID.
   */
  fun getSuggestion(suggestionId: Long): SuggestionPosition?

  /**
   * Handles a regular click on a suggestion.
   *
   * @param suggestionId The ID of the suggestion clicked. If the suggestion list is flat, this will
   * be the position within the list.
   */
  fun onSuggestionClicked(suggestionId: Long)

  /**
   * Handles a click on the query refinement button.
   *
   * @param suggestionId The ID of the suggestion clicked. If the suggestion list is flat, this will
   * be the position within the list.
   */
  fun onSuggestionQueryRefineClicked(suggestionId: Long)

  /** Gets the adapter to be used by the UI view. */
  val listAdapter: A
}
