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
import android.view.View
import android.widget.ExpandableListAdapter
import android.widget.ExpandableListView

/** Suggestions view that displays suggestions clustered by corpus type. */
class ClusteredSuggestionsView(context: Context?, attrs: AttributeSet?) :
  ExpandableListView(context, attrs), SuggestionsListView<ExpandableListAdapter?> {

  @JvmField var mSuggestionsAdapter: SuggestionsAdapter<ExpandableListAdapter?>? = null

  override fun setSuggestionsAdapter(adapter: SuggestionsAdapter<ExpandableListAdapter?>?) {
    mSuggestionsAdapter = adapter
    super.setAdapter(adapter?.listAdapter)
  }

  override fun getSuggestionsAdapter(): SuggestionsAdapter<ExpandableListAdapter?>? {
    return mSuggestionsAdapter
  }

  // TODO: this function does not appear to be used currently and remains unimplemented
  override fun getSelectedItemId(): Long {
    return 0
  }

  @Suppress("UNUSED_PARAMETER")
  fun setLimitSuggestionsToViewHeight(limit: Boolean) {
    // not supported
  }

  @Override
  override fun onFinishInflate() {
    super.onFinishInflate()
    setItemsCanFocus(false)
    setOnGroupClickListener(
      object : OnGroupClickListener {
        override fun onGroupClick(
          parent: ExpandableListView?,
          v: View?,
          groupPosition: Int,
          id: Long
        ): Boolean {
          // disable collapsing / expanding
          return true
        }
      }
    )
  }

  fun expandAll() {
    if (mSuggestionsAdapter != null) {
      val adapter: ExpandableListAdapter? = mSuggestionsAdapter?.listAdapter
      for (i in 0 until adapter!!.getGroupCount()) {
        expandGroup(i)
      }
    }
  }
}
