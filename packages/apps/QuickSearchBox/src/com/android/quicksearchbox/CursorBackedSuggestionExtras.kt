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

import android.database.Cursor
import android.util.Log
import kotlin.Array
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/** SuggestionExtras taking values from the extra columns in a suggestion cursor. */
class CursorBackedSuggestionExtras
private constructor(cursor: Cursor?, position: Int, extraColumns: List<String>) :
  AbstractSuggestionExtras(null) {
  companion object {
    private const val TAG = "QSB.CursorBackedSuggestionExtras"
    private val DEFAULT_COLUMNS: HashSet<String> = HashSet<String>()
    @JvmStatic
    fun createExtrasIfNecessary(cursor: Cursor?, position: Int): CursorBackedSuggestionExtras? {
      val extraColumns: List<String>? =
        CursorBackedSuggestionExtras.Companion.getExtraColumns(cursor)
      return if (extraColumns != null) {
        CursorBackedSuggestionExtras(cursor, position, extraColumns)
      } else {
        null
      }
    }

    @JvmStatic
    fun getCursorColumns(cursor: Cursor?): Array<String>? {
      return try {
        cursor?.getColumnNames()
      } catch (ex: RuntimeException) {
        // all operations on cross-process cursors can throw random exceptions
        Log.e(CursorBackedSuggestionExtras.Companion.TAG, "getColumnNames() failed, ", ex)
        null
      }
    }

    fun cursorContainsExtras(cursor: Cursor?): Boolean {
      val columns: Array<String>? = CursorBackedSuggestionExtras.Companion.getCursorColumns(cursor)
      if (columns != null) {
        for (cursorColumn in columns) {
          if (!CursorBackedSuggestionExtras.Companion.DEFAULT_COLUMNS.contains(cursorColumn)) {
            return true
          }
        }
      }
      return false
    }

    @JvmStatic
    fun getExtraColumns(cursor: Cursor?): List<String>? {
      val columns: Array<String> =
        CursorBackedSuggestionExtras.Companion.getCursorColumns(cursor) ?: return null
      var extraColumns: ArrayList<String>? = null
      for (cursorColumn in columns) {
        if (!CursorBackedSuggestionExtras.Companion.DEFAULT_COLUMNS.contains(cursorColumn)) {
          if (extraColumns == null) {
            extraColumns = arrayListOf<String>()
          }
          extraColumns.add(cursorColumn)
        }
      }
      return extraColumns
    }

    init {
      CursorBackedSuggestionExtras.Companion.DEFAULT_COLUMNS.addAll(
        SuggestionCursorBackedCursor.COLUMNS.asList()
      )
    }
  }

  private val mCursor: Cursor?
  private val mCursorPosition: Int
  private val mExtraColumns: List<String>
  @Override
  override fun doGetExtra(columnName: String?): String? {
    return try {
      mCursor?.moveToPosition(mCursorPosition)
      val columnIdx: Int = mCursor!!.getColumnIndex(columnName)
      if (columnIdx < 0) null else mCursor.getString(columnIdx)
    } catch (ex: RuntimeException) {
      // all operations on cross-process cursors can throw random exceptions
      Log.e(CursorBackedSuggestionExtras.Companion.TAG, "getExtra($columnName) failed, ", ex)
      null
    }
  }

  @Override
  public override fun doGetExtraColumnNames(): List<String> {
    return mExtraColumns
  }

  init {
    mCursor = cursor
    mCursorPosition = position
    mExtraColumns = extraColumns
  }
}
