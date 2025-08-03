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

import android.app.SearchManager
import android.database.AbstractCursor
import android.database.CursorIndexOutOfBoundsException
import kotlin.collections.ArrayList

class SuggestionCursorBackedCursor(private val mCursor: SuggestionCursor?) : AbstractCursor() {
  private var mExtraColumns: ArrayList<String>? = null

  @Override
  override fun close() {
    super.close()
    mCursor?.close()
  }

  @Override
  override fun getColumnNames(): Array<String> {
    val extraColumns: Collection<String>? = mCursor?.extraColumns
    return if (extraColumns != null) {
      val allColumns: ArrayList<String> = ArrayList<String>(COLUMNS.size + extraColumns.size)
      mExtraColumns = ArrayList<String>(extraColumns)
      allColumns.addAll(COLUMNS.asList())
      mExtraColumns?.let { allColumns.addAll(it) }
      allColumns.toArray(arrayOfNulls<String>(allColumns.size))
    } else {
      COLUMNS
    }
  }

  @Override
  override fun getCount(): Int {
    return mCursor!!.count
  }

  private fun get(): SuggestionCursor? {
    mCursor?.moveTo(position)
    return mCursor
  }

  private fun getExtra(columnIdx: Int): String? {
    val extraColumn = columnIdx - COLUMNS.size
    val extras: SuggestionExtras? = get()?.extras
    return extras?.getExtra(mExtraColumns!!.get(extraColumn))
  }

  @Override
  override fun getInt(column: Int): Int {
    return if (column == COLUMN_INDEX_ID) {
      position
    } else {
      try {
        getString(column)!!.toInt()
      } catch (e: NumberFormatException) {
        0
      }
    }
  }

  @Override
  override fun getString(column: Int): String? {
    return if (column < COLUMNS.size) {
      when (column) {
        COLUMN_INDEX_ID -> position.toString()
        COLUMN_INDEX_TEXT1 -> get()?.suggestionText1
        COLUMN_INDEX_TEXT2 -> get()?.suggestionText2
        COLUMN_INDEX_TEXT2_URL -> get()?.suggestionText2Url
        COLUMN_INDEX_ICON1 -> get()?.suggestionIcon1
        COLUMN_INDEX_ICON2 -> get()?.suggestionIcon2
        COLUMN_INDEX_INTENT_ACTION -> get()?.suggestionIntentAction
        COLUMN_INDEX_INTENT_DATA -> get()?.suggestionIntentDataString
        COLUMN_INDEX_INTENT_EXTRA_DATA -> get()?.suggestionIntentExtraData
        COLUMN_INDEX_QUERY -> get()?.suggestionQuery
        COLUMN_INDEX_FORMAT -> get()?.suggestionFormat
        COLUMN_INDEX_SHORTCUT_ID -> get()?.shortcutId
        COLUMN_INDEX_SPINNER_WHILE_REFRESHING -> get()?.isSpinnerWhileRefreshing.toString()
        else ->
          throw CursorIndexOutOfBoundsException(
            "Requested column " + column + " of " + COLUMNS.size
          )
      }
    } else {
      getExtra(column)
    }
  }

  @Override
  override fun getLong(column: Int): Long {
    return try {
      getString(column)!!.toLong()
    } catch (e: NumberFormatException) {
      0
    }
  }

  @Override
  override fun isNull(column: Int): Boolean {
    return getString(column) == null
  }

  @Override
  override fun getShort(column: Int): Short {
    return try {
      getString(column)!!.toShort()
    } catch (e: NumberFormatException) {
      0
    }
  }

  @Override
  override fun getDouble(column: Int): Double {
    return try {
      getString(column)!!.toDouble()
    } catch (e: NumberFormatException) {
      0.0
    }
  }

  @Override
  override fun getFloat(column: Int): Float {
    return try {
      getString(column)!!.toFloat()
    } catch (e: NumberFormatException) {
      0.0F
    }
  }

  companion object {
    // This array also used in CursorBackedSuggestionExtras to avoid duplication.
    val COLUMNS =
      arrayOf(
        "_id", // 0, This will contain the row number. CursorAdapter, used by SuggestionsAdapter,
        // used by SearchDialog, expects an _id column.
        SearchManager.SUGGEST_COLUMN_TEXT_1, // 1
        SearchManager.SUGGEST_COLUMN_TEXT_2, // 2
        SearchManager.SUGGEST_COLUMN_TEXT_2_URL, // 3
        SearchManager.SUGGEST_COLUMN_ICON_1, // 4
        SearchManager.SUGGEST_COLUMN_ICON_2, // 5
        SearchManager.SUGGEST_COLUMN_INTENT_ACTION, // 6
        SearchManager.SUGGEST_COLUMN_INTENT_DATA, // 7
        SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA, // 8
        SearchManager.SUGGEST_COLUMN_QUERY, // 9
        SearchManager.SUGGEST_COLUMN_FORMAT, // 10
        SearchManager.SUGGEST_COLUMN_SHORTCUT_ID, // 11
        SearchManager.SUGGEST_COLUMN_SPINNER_WHILE_REFRESHING
      )
    private const val COLUMN_INDEX_ID = 0
    private const val COLUMN_INDEX_TEXT1 = 1
    private const val COLUMN_INDEX_TEXT2 = 2
    private const val COLUMN_INDEX_TEXT2_URL = 3
    private const val COLUMN_INDEX_ICON1 = 4
    private const val COLUMN_INDEX_ICON2 = 5
    private const val COLUMN_INDEX_INTENT_ACTION = 6
    private const val COLUMN_INDEX_INTENT_DATA = 7
    private const val COLUMN_INDEX_INTENT_EXTRA_DATA = 8
    private const val COLUMN_INDEX_QUERY = 9
    private const val COLUMN_INDEX_FORMAT = 10
    private const val COLUMN_INDEX_SHORTCUT_ID = 11
    private const val COLUMN_INDEX_SPINNER_WHILE_REFRESHING = 12
  }
}
