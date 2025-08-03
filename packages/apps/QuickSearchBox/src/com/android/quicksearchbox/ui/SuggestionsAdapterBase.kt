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
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import com.android.quicksearchbox.Suggestion
import com.android.quicksearchbox.SuggestionCursor
import com.android.quicksearchbox.SuggestionPosition
import com.android.quicksearchbox.Suggestions
import kotlin.collections.HashMap

/** Base class for suggestions adapters. The templated class A is the list adapter class. */
abstract class SuggestionsAdapterBase<A>
protected constructor(private val mViewFactory: SuggestionViewFactory) : SuggestionsAdapter<A> {
  private var mDataSetObserver: DataSetObserver? = null
  var currentSuggestions: SuggestionCursor? = null
    private set
  private val mViewTypeMap: HashMap<String, Int>
  private var mSuggestions: Suggestions? = null
  private var mSuggestionClickListener: SuggestionClickListener? = null
  private var mOnFocusChangeListener: OnFocusChangeListener? = null
  var isClosed = false
    private set

  @get:Override abstract override val isEmpty: Boolean
  fun close() {
    suggestions = null
    isClosed = true
  }

  @Override
  override fun setSuggestionClickListener(listener: SuggestionClickListener?) {
    mSuggestionClickListener = listener
  }

  @Override
  override fun setOnFocusChangeListener(l: OnFocusChangeListener?) {
    mOnFocusChangeListener = l
  }

  // TODO: delay the change if there are no suggestions for the currently visible tab.
  @get:Override
  @set:Override
  override var suggestions: Suggestions?
    get() = mSuggestions
    set(suggestions) {
      if (mSuggestions === suggestions) {
        return
      }
      if (isClosed) {
        suggestions?.release()
        return
      }
      if (mDataSetObserver == null) {
        mDataSetObserver = MySuggestionsObserver()
      }
      // TODO: delay the change if there are no suggestions for the currently visible tab.
      if (mSuggestions != null) {
        mSuggestions!!.unregisterDataSetObserver(mDataSetObserver)
        mSuggestions!!.release()
      }
      mSuggestions = suggestions
      if (mSuggestions != null) {
        mSuggestions!!.registerDataSetObserver(mDataSetObserver)
      }
      onSuggestionsChanged()
    }

  @Override abstract override fun getSuggestion(suggestionId: Long): SuggestionPosition
  protected val count: Int
    get() = if (currentSuggestions == null) 0 else currentSuggestions!!.count

  protected fun getSuggestion(position: Int): SuggestionPosition? {
    return if (currentSuggestions == null) null
    else SuggestionPosition(currentSuggestions!!, position)
  }

  protected val viewTypeCount: Int
    get() = mViewTypeMap.size

  private fun suggestionViewType(suggestion: Suggestion): String? {
    val viewType = mViewFactory.getViewType(suggestion)
    if (!mViewTypeMap.containsKey(viewType)) {
      throw IllegalStateException("Unknown viewType $viewType")
    }
    return viewType
  }

  protected fun getSuggestionViewType(cursor: SuggestionCursor?, position: Int): Int {
    if (cursor == null) {
      return 0
    }
    cursor.moveTo(position)
    return mViewTypeMap.get(suggestionViewType(cursor)!!) as Int
  }

  protected val suggestionViewTypeCount: Int
    get() = mViewTypeMap.size

  protected fun getView(
    suggestions: SuggestionCursor?,
    position: Int,
    suggestionId: Long,
    convertView: View?,
    parent: ViewGroup?
  ): View? {
    suggestions?.moveTo(position)
    val v: View? = mViewFactory.getView(suggestions, suggestions?.userQuery, convertView, parent)
    if (v is SuggestionView) {
      (v as SuggestionView?)!!.bindAdapter(this, suggestionId)
    } else {
      val l = SuggestionViewClickListener(suggestionId)
      v?.setOnClickListener(l)
    }
    if (mOnFocusChangeListener != null) {
      v?.setOnFocusChangeListener(mOnFocusChangeListener)
    }
    return v
  }

  protected fun onSuggestionsChanged() {
    if (DBG) Log.d(TAG, "onSuggestionsChanged($mSuggestions)")
    var cursor: SuggestionCursor? = null
    if (mSuggestions != null) {
      cursor = mSuggestions!!.getResult()
    }
    changeSuggestions(cursor)
  }

  /**
   * Replace the cursor.
   *
   * This does not close the old cursor. Instead, all the cursors are closed in [.setSuggestions].
   */
  private fun changeSuggestions(newCursor: SuggestionCursor?) {
    if (DBG) {
      Log.d(TAG, "changeCursor(" + newCursor + ") count=" + (newCursor?.count ?: 0))
    }
    if (newCursor === currentSuggestions) {
      if (newCursor != null) {
        // Shortcuts may have changed without the cursor changing.
        notifyDataSetChanged()
      }
      return
    }
    currentSuggestions = newCursor
    if (currentSuggestions != null) {
      notifyDataSetChanged()
    } else {
      notifyDataSetInvalidated()
    }
  }

  @Override
  override fun onSuggestionClicked(suggestionId: Long) {
    if (isClosed) {
      Log.w(TAG, "onSuggestionClicked after close")
    } else if (mSuggestionClickListener != null) {
      mSuggestionClickListener!!.onSuggestionClicked(this, suggestionId)
    }
  }

  @Override
  override fun onSuggestionQueryRefineClicked(suggestionId: Long) {
    if (isClosed) {
      Log.w(TAG, "onSuggestionQueryRefineClicked after close")
    } else if (mSuggestionClickListener != null) {
      mSuggestionClickListener!!.onSuggestionQueryRefineClicked(this, suggestionId)
    }
  }

  @get:Override abstract override val listAdapter: A
  protected abstract fun notifyDataSetInvalidated()
  protected abstract fun notifyDataSetChanged()
  private inner class MySuggestionsObserver : DataSetObserver() {
    @Override
    override fun onChanged() {
      onSuggestionsChanged()
    }
  }

  private inner class SuggestionViewClickListener(private val mSuggestionId: Long) :
    View.OnClickListener {
    @Override
    override fun onClick(v: View?) {
      onSuggestionClicked(mSuggestionId)
    }
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.SuggestionsAdapter"
  }

  init {
    mViewTypeMap = hashMapOf<String, Int>()
    for (viewType in mViewFactory.suggestionViewTypes) {
      if (!mViewTypeMap.containsKey(viewType)) {
        mViewTypeMap.put(viewType, mViewTypeMap.size)
      }
    }
  }
}
