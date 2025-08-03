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

import android.os.Handler
import android.util.Log
import com.android.quicksearchbox.util.Consumer
import com.android.quicksearchbox.util.Consumers
import com.android.quicksearchbox.util.NamedTask
import com.android.quicksearchbox.util.NamedTaskExecutor

/** A task that gets suggestions from a corpus. */
class QueryTask<C : SuggestionCursor?>(
  private val mQuery: String?,
  private val mQueryLimit: Int,
  private val mProvider: SuggestionCursorProvider<C>?,
  handler: Handler?,
  consumer: Consumer<C>?
) : NamedTask {

  private val mHandler: Handler?

  private val mConsumer: Consumer<C>?

  @get:Override
  override val name: String?
    get() = mProvider?.name

  @Override
  override fun run() {
    val cursor = mProvider?.getSuggestions(mQuery, mQueryLimit)
    if (DBG) Log.d(TAG, "Suggestions from $mProvider = $cursor")
    Consumers.consumeCloseableAsync(mHandler, mConsumer, cursor)
  }

  @Override
  override fun toString(): String {
    return "$mProvider[$mQuery]"
  }

  companion object {
    private const val TAG = "QSB.QueryTask"
    private const val DBG = false

    @JvmStatic
    fun <C : SuggestionCursor?> startQuery(
      query: String?,
      maxResults: Int,
      provider: SuggestionCursorProvider<C>?,
      executor: NamedTaskExecutor,
      handler: Handler?,
      consumer: Consumer<C>?
    ) {
      val task = QueryTask(query, maxResults, provider, handler, consumer)
      executor.execute(task)
    }
  }

  /**
   * Creates a new query task.
   *
   * @param query Query to run.
   * @param queryLimit The number of suggestions to ask each provider for.
   * @param provider The provider to ask for suggestions.
   * @param handler Handler that [Consumer.consume] will get called on. If null, the method is
   * called on the query thread.
   * @param consumer Consumer to notify when the suggestions have been returned.
   */
  init {
    mHandler = handler
    mConsumer = consumer
  }
}
