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
import android.content.ComponentName
import android.content.Intent
import android.database.Cursor
import android.database.DataSetObserver
import android.net.Uri
import android.util.Log

abstract class CursorBackedSuggestionCursor(override val userQuery: String?, cursor: Cursor?) :
  SuggestionCursor {

  /** The suggestions, or `null` if the suggestions query failed. */
  @JvmField protected val mCursor: Cursor?

  /** Column index of [SearchManager.SUGGEST_COLUMN_FORMAT] in @{link mCursor}. */
  private val mFormatCol: Int

  /** Column index of [SearchManager.SUGGEST_COLUMN_TEXT_1] in @{link mCursor}. */
  private val mText1Col: Int

  /** Column index of [SearchManager.SUGGEST_COLUMN_TEXT_2] in @{link mCursor}. */
  private val mText2Col: Int

  /** Column index of [SearchManager.SUGGEST_COLUMN_TEXT_2_URL] in @{link mCursor}. */
  private val mText2UrlCol: Int

  /** Column index of [SearchManager.SUGGEST_COLUMN_ICON_1] in @{link mCursor}. */
  private val mIcon1Col: Int

  /** Column index of [SearchManager.SUGGEST_COLUMN_ICON_1] in @{link mCursor}. */
  private val mIcon2Col: Int

  /** Column index of [SearchManager.SUGGEST_COLUMN_SPINNER_WHILE_REFRESHING] in @{link mCursor}. */
  private val mRefreshSpinnerCol: Int

  /** True if this result has been closed. */
  private var mClosed = false
  abstract override val suggestionSource: Source?
  override val suggestionLogType: String?
    get() = getStringOrNull(SUGGEST_COLUMN_LOG_TYPE)

  override fun close() {
    if (DBG) Log.d(TAG, "close()")
    if (mClosed) {
      throw IllegalStateException("Double close()")
    }
    mClosed = true
    if (mCursor != null) {
      try {
        mCursor.close()
      } catch (ex: RuntimeException) {
        // all operations on cross-process cursors can throw random exceptions
        Log.e(TAG, "close() failed, ", ex)
      }
    }
  }

  @Override
  protected fun finalize() {
    if (!mClosed) {
      Log.e(TAG, "LEAK! Finalized without being closed: " + toString())
    }
  }

  override val count: Int
    get() {
      if (mClosed) {
        throw IllegalStateException("getCount() after close()")
      }
      return if (mCursor == null) 0
      else
        try {
          mCursor.getCount()
        } catch (ex: RuntimeException) {
          // all operations on cross-process cursors can throw random exceptions
          Log.e(TAG, "getCount() failed, ", ex)
          0
        }
    }

  override fun moveTo(pos: Int) {
    if (mClosed) {
      throw IllegalStateException("moveTo($pos) after close()")
    }
    try {
      if (!mCursor!!.moveToPosition(pos)) {
        Log.e(TAG, "moveToPosition($pos) failed, count=$count")
      }
    } catch (ex: RuntimeException) {
      // all operations on cross-process cursors can throw random exceptions
      Log.e(TAG, "moveToPosition() failed, ", ex)
    }
  }

  override fun moveToNext(): Boolean {
    if (mClosed) {
      throw IllegalStateException("moveToNext() after close()")
    }
    return try {
      mCursor!!.moveToNext()
    } catch (ex: RuntimeException) {
      // all operations on cross-process cursors can throw random exceptions
      Log.e(TAG, "moveToNext() failed, ", ex)
      false
    }
  }

  override val position: Int
    get() {
      if (mClosed) {
        throw IllegalStateException("get() on position after close()")
      }
      return try {
        mCursor!!.position
      } catch (ex: RuntimeException) {
        // all operations on cross-process cursors can throw random exceptions
        Log.e(TAG, "get() on position failed, ", ex)
        -1
      }
    }
  override val shortcutId: String?
    get() = getStringOrNull(SearchManager.SUGGEST_COLUMN_SHORTCUT_ID)
  override val suggestionFormat: String?
    get() = getStringOrNull(mFormatCol)
  override val suggestionText1: String?
    get() = getStringOrNull(mText1Col)
  override val suggestionText2: String?
    get() = getStringOrNull(mText2Col)
  override val suggestionText2Url: String?
    get() = getStringOrNull(mText2UrlCol)
  override val suggestionIcon1: String?
    get() = getStringOrNull(mIcon1Col)
  override val suggestionIcon2: String?
    get() = getStringOrNull(mIcon2Col)
  override val isSpinnerWhileRefreshing: Boolean
    get() = "true".equals(getStringOrNull(mRefreshSpinnerCol))

  /** Gets the intent action for the current suggestion. */
  override val suggestionIntentAction: String?
    get() {
      val action: String? = getStringOrNull(SearchManager.SUGGEST_COLUMN_INTENT_ACTION)
      return action
    }
  abstract override val suggestionIntentComponent: ComponentName?

  /** Gets the query for the current suggestion. */
  override val suggestionQuery: String?
    get() = getStringOrNull(SearchManager.SUGGEST_COLUMN_QUERY)

  override val suggestionIntentDataString: String?
    get() {
      // use specific data if supplied, or default data if supplied
      var data: String? = getStringOrNull(SearchManager.SUGGEST_COLUMN_INTENT_DATA)
      if (data == null) {
        data = suggestionSource?.defaultIntentData
      }
      // then, if an ID was provided, append it.
      if (data != null) {
        val id: String? = getStringOrNull(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID)
        if (id != null) {
          data = data.toString() + "/" + Uri.encode(id)
        }
      }
      return data
    }

  /** Gets the intent extra data for the current suggestion. */
  override val suggestionIntentExtraData: String?
    get() = getStringOrNull(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA)
  override val isWebSearchSuggestion: Boolean
    get() = Intent.ACTION_WEB_SEARCH.equals(suggestionIntentAction)

  /**
   * Gets the index of a column in [.mCursor] by name.
   *
   * @return The index, or `-1` if the column was not found.
   */
  protected fun getColumnIndex(colName: String?): Int {
    return if (mCursor == null) -1
    else
      try {
        mCursor.getColumnIndex(colName)
      } catch (ex: RuntimeException) {
        // all operations on cross-process cursors can throw random exceptions
        Log.e(TAG, "getColumnIndex() failed, ", ex)
        -1
      }
  }

  /**
   * Gets the string value of a column in [.mCursor] by column index.
   *
   * @param col Column index.
   * @return The string value, or `null`.
   */
  protected fun getStringOrNull(col: Int): String? {
    if (mCursor == null) return null
    return if (col == -1) {
      null
    } else
      try {
        mCursor.getString(col)
      } catch (ex: RuntimeException) {
        // all operations on cross-process cursors can throw random exceptions
        Log.e(TAG, "getString() failed, ", ex)
        null
      }
  }

  /**
   * Gets the string value of a column in [.mCursor] by column name.
   *
   * @param colName Column name.
   * @return The string value, or `null`.
   */
  protected fun getStringOrNull(colName: String?): String? {
    val col = getColumnIndex(colName)
    return getStringOrNull(col)
  }

  override fun registerDataSetObserver(observer: DataSetObserver?) {
    // We don't watch Cursor-backed SuggestionCursors for changes
  }

  override fun unregisterDataSetObserver(observer: DataSetObserver?) {
    // We don't watch Cursor-backed SuggestionCursors for changes
  }

  @Override
  override fun toString(): String {
    return this::class.simpleName.toString() + "[" + userQuery + "]"
  }

  companion object {
    private const val DBG = false
    protected const val TAG = "QSB.CursorBackedSuggestionCursor"
    const val SUGGEST_COLUMN_LOG_TYPE = "suggest_log_type"
  }

  init {
    mCursor = cursor
    mFormatCol = getColumnIndex(SearchManager.SUGGEST_COLUMN_FORMAT)
    mText1Col = getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1)
    mText2Col = getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2)
    mText2UrlCol = getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2_URL)
    mIcon1Col = getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_1)
    mIcon2Col = getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_2)
    mRefreshSpinnerCol = getColumnIndex(SearchManager.SUGGEST_COLUMN_SPINNER_WHILE_REFRESHING)
  }
}
