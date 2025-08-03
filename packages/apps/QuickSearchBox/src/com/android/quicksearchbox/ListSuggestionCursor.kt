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

import android.database.DataSetObservable
import android.database.DataSetObserver
import com.google.common.annotations.VisibleForTesting
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/** A SuggestionCursor that is backed by a list of Suggestions. */
open class ListSuggestionCursor(userQuery: String?, capacity: Int) :
  AbstractSuggestionCursorWrapper(userQuery!!) {
  private val mDataSetObservable: DataSetObservable = DataSetObservable()

  private val mSuggestions: ArrayList<Entry>

  private var mExtraColumns: HashSet<String>? = null

  override var position = 0

  constructor(userQuery: String?) : this(userQuery, DEFAULT_CAPACITY)

  @VisibleForTesting
  constructor(
    userQuery: String?,
    vararg suggestions: Suggestion?
  ) : this(userQuery, suggestions.size) {
    for (suggestion in suggestions) {
      add(suggestion!!)
    }
  }

  /**
   * Adds a suggestion from another suggestion cursor.
   *
   * @return `true` if the suggestion was added.
   */
  open fun add(suggestion: Suggestion): Boolean {
    mSuggestions.add(Entry(suggestion))
    return true
  }

  override fun close() {
    mSuggestions.clear()
  }

  override fun moveTo(pos: Int) {
    position = pos
  }

  override fun moveToNext(): Boolean {
    val size: Int = mSuggestions.size
    if (position >= size) {
      // Already past the end
      return false
    }
    position++
    return position < size
  }

  fun removeRow() {
    mSuggestions.removeAt(position)
  }

  fun replaceRow(suggestion: Suggestion) {
    mSuggestions.set(position, Entry(suggestion))
  }

  override val count: Int
    get() = mSuggestions.size

  @Override
  override fun current(): Suggestion {
    return mSuggestions.get(position).get()
  }

  @Override
  override fun toString(): String {
    return this::class.simpleName.toString() + "{[" + userQuery + "] " + mSuggestions + "}"
  }

  /**
   * Register an observer that is called when changes happen to this data set.
   *
   * @param observer gets notified when the data set changes.
   */
  override fun registerDataSetObserver(observer: DataSetObserver?) {
    mDataSetObservable.registerObserver(observer)
  }

  /**
   * Unregister an observer that has previously been registered with [.registerDataSetObserver]
   *
   * @param observer the observer to unregister.
   */
  override fun unregisterDataSetObserver(observer: DataSetObserver?) {
    mDataSetObservable.unregisterObserver(observer)
  }

  protected fun notifyDataSetChanged() {
    mDataSetObservable.notifyChanged()
  }

  // override with caching to avoid re-parsing the extras
  @get:Override
  override val extras: SuggestionExtras?
    // override with caching to avoid re-parsing the extras
    get() = mSuggestions.get(position).getExtras()

  override val extraColumns: Collection<String>?
    get() {
      if (mExtraColumns == null) {
        mExtraColumns = HashSet<String>()
        for (e in mSuggestions) {
          val extras: SuggestionExtras? = e.getExtras()
          val extraColumns: Collection<String>? =
            if (extras == null) null else extras.extraColumnNames
          if (extraColumns != null) {
            for (column in extras!!.extraColumnNames) {
              mExtraColumns?.add(column)
            }
          }
        }
      }
      return if (mExtraColumns!!.isEmpty()) null else mExtraColumns
    }

  /** This class exists purely to cache the suggestion extras. */
  private class Entry(private val mSuggestion: Suggestion) {
    private var mExtras: SuggestionExtras? = null
    fun get(): Suggestion {
      return mSuggestion
    }

    fun getExtras(): SuggestionExtras? {
      if (mExtras == null) {
        mExtras = mSuggestion.extras
      }
      return mExtras
    }
  }

  companion object {
    private const val DEFAULT_CAPACITY = 16
  }

  init {
    mSuggestions = ArrayList<Entry>(capacity)
  }
}
