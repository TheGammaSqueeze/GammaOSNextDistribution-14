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

import android.database.DataSetObserver

/** A suggestion cursor that delegates all methods to another SuggestionCursor. */
open class SuggestionCursorWrapper(userQuery: String?, private val mCursor: SuggestionCursor?) :
  AbstractSuggestionCursorWrapper(userQuery!!) {
  override fun close() {
    if (mCursor != null) {
      mCursor.close()
    }
  }

  override val count: Int
    get() = if (mCursor == null) 0 else mCursor.count
  override val position: Int
    get() = if (mCursor == null) 0 else mCursor.position

  override fun moveTo(pos: Int) {
    if (mCursor != null) {
      mCursor.moveTo(pos)
    }
  }

  override fun moveToNext(): Boolean {
    return mCursor?.moveToNext() ?: false
  }

  override fun registerDataSetObserver(observer: DataSetObserver?) {
    if (mCursor != null) {
      mCursor.registerDataSetObserver(observer)
    }
  }

  override fun unregisterDataSetObserver(observer: DataSetObserver?) {
    if (mCursor != null) {
      mCursor.unregisterDataSetObserver(observer)
    }
  }

  @Override
  override fun current(): SuggestionCursor {
    return mCursor!!
  }

  override val extraColumns: Collection<String>?
    get() = mCursor?.extraColumns
}
