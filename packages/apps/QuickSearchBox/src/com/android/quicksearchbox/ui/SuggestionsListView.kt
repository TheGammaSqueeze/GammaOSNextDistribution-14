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

import android.view.View
import android.widget.AbsListView

/** Interface for suggestions list UI views. */
interface SuggestionsListView<A> {
  /** See [View.setOnKeyListener]. */
  fun setOnKeyListener(l: View.OnKeyListener?)

  /** See [AbsListView.setOnScrollListener]. */
  fun setOnScrollListener(l: AbsListView.OnScrollListener?)

  /** See [View.setOnFocusChangeListener]. */
  fun setOnFocusChangeListener(l: View.OnFocusChangeListener?)

  /** See [View.setVisibility]. */
  fun setVisibility(visibility: Int)

  /** Sets the adapter for the list. See [AbsListView.setAdapter] */
  fun setSuggestionsAdapter(adapter: SuggestionsAdapter<A?>?)

  /** Gets the adapter for the list. */
  fun getSuggestionsAdapter(): SuggestionsAdapter<A?>?

  /** Gets the ID of the currently selected item. */
  fun getSelectedItemId(): Long
}
