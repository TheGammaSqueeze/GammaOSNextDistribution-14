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
import android.util.Log

/** Collects all corpus results for a single query. */
class Suggestions(val query: String, val source: Source) {

  /**
   * The observers that want notifications of changes to the published suggestions. This object may
   * be accessed on any thread.
   */
  private val mDataSetObservable: DataSetObservable = DataSetObservable()

  private var mResult: SourceResult? = null

  private var mRefCount = 0

  private var mDone = false

  /** True if [Suggestions.close] has been called. */
  var isClosed = false
    private set

  /**
   * Gets the list of corpus results reported so far. Do not modify or hang on to the returned
   * iterator.
   */
  fun getResult(): SourceResult? {
    return mResult
  }

  fun getWebResult(): SourceResult? {
    return mResult
  }

  fun acquire() {
    mRefCount++
  }

  fun release() {
    mRefCount--
    if (mRefCount <= 0) {
      close()
    }
  }

  /** Marks the suggestions set as complete, regardless of whether all corpora have returned. */
  fun done() {
    mDone = true
  }

  /**
   * Checks whether all sources have reported. Must be called on the UI thread, or before this
   * object is seen by the UI thread.
   */
  val isDone: Boolean
    get() = mDone || mResult != null

  /**
   * Adds a list of corpus results. Must be called on the UI thread, or before this object is seen
   * by the UI thread.
   */
  fun addResults(result: SourceResult?) {
    if (isClosed) {
      result?.close()
      return
    }
    if (DBG) {
      Log.d(
        TAG,
        "addResults[" +
          hashCode().toString() +
          "] source:" +
          result?.source?.name.toString() +
          " results:" +
          result?.count
      )
    }
    if (query != result?.userQuery) {
      throw IllegalArgumentException(
        "Got result for wrong query: " + query + " != " + result?.userQuery
      )
    }
    mResult = result
    notifyDataSetChanged()
  }

  /**
   * Registers an observer that will be notified when the reported results or the done status
   * changes.
   */
  fun registerDataSetObserver(observer: DataSetObserver?) {
    if (isClosed) {
      throw IllegalStateException("registerDataSetObserver() when closed")
    }
    mDataSetObservable.registerObserver(observer)
  }

  /** Unregisters an observer. */
  fun unregisterDataSetObserver(observer: DataSetObserver?) {
    mDataSetObservable.unregisterObserver(observer)
  }

  /** Calls [DataSetObserver.onChanged] on all observers. */
  protected fun notifyDataSetChanged() {
    if (DBG) Log.d(TAG, "notifyDataSetChanged()")
    mDataSetObservable.notifyChanged()
  }

  /** Closes all the source results and unregisters all observers. */
  private fun close() {
    if (DBG) Log.d(TAG, "close() [" + hashCode().toString() + "]")
    if (isClosed) {
      throw IllegalStateException("Double close()")
    }
    isClosed = true
    mDataSetObservable.unregisterAll()
    mResult?.close()
    mResult = null
  }

  @Override
  protected fun finalize() {
    if (!isClosed) {
      Log.e(TAG, "LEAK! Finalized without being closed: Suggestions[$query]")
    }
  }

  /**
   * Gets the number of source results. Must be called on the UI thread, or before this object is
   * seen by the UI thread.
   */
  val resultCount: Int
    get() {
      if (isClosed) {
        throw IllegalStateException("Called resultCount when closed.")
      }
      return mResult?.count ?: 0
    }

  @Override
  override fun toString(): String {
    return "Suggestions@" +
      hashCode().toString() +
      "{source=" +
      source.toString() +
      ",resultCount=" +
      resultCount.toString() +
      "}"
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.Suggestions"
  }
}
