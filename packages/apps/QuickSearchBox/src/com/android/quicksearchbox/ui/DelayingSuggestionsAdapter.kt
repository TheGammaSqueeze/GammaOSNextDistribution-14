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

import android.database.DataSetObserver
import android.util.Log
import android.view.View.OnFocusChangeListener
import com.android.quicksearchbox.SuggestionCursor
import com.android.quicksearchbox.SuggestionPosition
import com.android.quicksearchbox.Suggestions

/**
 * A [SuggestionsListAdapter] that doesn't expose the new suggestions until there are some results
 * to show.
 */
class DelayingSuggestionsAdapter<A>(private val mDelayedAdapter: SuggestionsAdapterBase<A>) :
  SuggestionsAdapter<A> {
  private var mPendingDataSetObserver: DataSetObserver? = null
  private var mPendingSuggestions: Suggestions? = null
  fun close() {
    setPendingSuggestions(null)
    mDelayedAdapter.close()
  }

  /** Gets whether the given suggestions are non-empty for the selected source. */
  private fun shouldPublish(suggestions: Suggestions?): Boolean {
    if (suggestions!!.isDone) return true
    val cursor: SuggestionCursor? = suggestions.getResult()
    return cursor != null && cursor.count > 0
  }

  private fun setPendingSuggestions(suggestions: Suggestions?) {
    if (mPendingSuggestions === suggestions) {
      return
    }
    if (mDelayedAdapter.isClosed) {
      suggestions?.release()
      return
    }
    if (mPendingDataSetObserver == null) {
      mPendingDataSetObserver = PendingSuggestionsObserver()
    }
    if (mPendingSuggestions != null) {
      mPendingSuggestions!!.unregisterDataSetObserver(mPendingDataSetObserver)
      // Close old suggestions, but only if they are not also the current
      // suggestions.
      if (mPendingSuggestions !== this.suggestions) {
        mPendingSuggestions!!.release()
      }
    }
    mPendingSuggestions = suggestions
    if (mPendingSuggestions != null) {
      mPendingSuggestions!!.registerDataSetObserver(mPendingDataSetObserver)
    }
  }

  protected fun onPendingSuggestionsChanged() {
    if (DBG) Log.d(TAG, "onPendingSuggestionsChanged(), mPendingSuggestions=" + mPendingSuggestions)
    if (shouldPublish(mPendingSuggestions)) {
      if (DBG) Log.d(TAG, "Suggestions now available, publishing: $mPendingSuggestions")
      mDelayedAdapter.suggestions = mPendingSuggestions
      // The suggestions are no longer pending.
      setPendingSuggestions(null)
    }
  }

  private inner class PendingSuggestionsObserver : DataSetObserver() {
    @Override
    override fun onChanged() {
      onPendingSuggestionsChanged()
    }
  }

  @get:Override
  override val listAdapter: A
    get() = mDelayedAdapter.listAdapter
  val currentPromotedSuggestions: SuggestionCursor?
    get() = mDelayedAdapter.currentSuggestions

  // Clear any old pending suggestions.
  @get:Override
  @set:Override
  override var suggestions: Suggestions?
    get() = mDelayedAdapter.suggestions
    set(suggestions) {
      if (suggestions == null) {
        mDelayedAdapter.suggestions = null
        setPendingSuggestions(null)
        return
      }
      if (shouldPublish(suggestions)) {
        if (DBG) Log.d(TAG, "Publishing suggestions immediately: $suggestions")
        mDelayedAdapter.suggestions = suggestions
        // Clear any old pending suggestions.
        setPendingSuggestions(null)
      } else {
        if (DBG) Log.d(TAG, "Delaying suggestions publishing: $suggestions")
        setPendingSuggestions(suggestions)
      }
    }

  @Override
  override fun getSuggestion(suggestionId: Long): SuggestionPosition? {
    return mDelayedAdapter.getSuggestion(suggestionId)
  }

  @Override
  override fun onSuggestionClicked(suggestionId: Long) {
    mDelayedAdapter.onSuggestionClicked(suggestionId)
  }

  @Override
  override fun onSuggestionQueryRefineClicked(suggestionId: Long) {
    mDelayedAdapter.onSuggestionQueryRefineClicked(suggestionId)
  }

  @Override
  override fun setOnFocusChangeListener(l: OnFocusChangeListener?) {
    mDelayedAdapter.setOnFocusChangeListener(l)
  }

  @Override
  override fun setSuggestionClickListener(listener: SuggestionClickListener?) {
    mDelayedAdapter.setSuggestionClickListener(listener)
  }

  @get:Override
  override val isEmpty: Boolean
    get() = mDelayedAdapter.isEmpty

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.DelayingSuggestionsAdapter"
  }
}
