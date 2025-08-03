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
import com.android.quicksearchbox.util.NamedTaskExecutor
import com.android.quicksearchbox.util.NoOpConsumer

/**
 * Suggestions provider implementation.
 *
 * The provider will only handle a single query at a time. If a new query comes in, the old one is
 * cancelled.
 */
class SuggestionsProviderImpl(
  private val mConfig: Config,
  private val mQueryExecutor: NamedTaskExecutor,
  publishThread: Handler?,
  logger: Logger?
) : SuggestionsProvider {

  private val mPublishThread: Handler?

  private val mLogger: Logger?

  @Override override fun close() {}

  @Override
  override fun getSuggestions(query: String, source: Source): Suggestions {
    if (DBG) Log.d(TAG, "getSuggestions($query)")
    val suggestions = Suggestions(query, source)
    Log.i(TAG, "chars:" + query.length.toString() + ",source:" + source)
    val receiver: Consumer<SourceResult?>
    if (shouldDisplayResults(query)) {
      receiver = SuggestionCursorReceiver(suggestions)
    } else {
      receiver = NoOpConsumer()
      suggestions.done()
    }
    val maxResults: Int = mConfig.maxResultsPerSource
    QueryTask.startQuery(query, maxResults, source, mQueryExecutor, mPublishThread, receiver)
    return suggestions
  }

  private fun shouldDisplayResults(query: String): Boolean {
    return !(query.isEmpty() && !mConfig.showSuggestionsForZeroQuery())
  }

  private inner class SuggestionCursorReceiver(private val mSuggestions: Suggestions) :
    Consumer<SourceResult?> {
    @Override
    override fun consume(value: SourceResult?): Boolean {
      if (DBG) {
        Log.d(
          TAG,
          "SuggestionCursorReceiver.consume(" +
            value +
            ") corpus=" +
            value?.source +
            " count = " +
            value?.count
        )
      }
      // publish immediately
      if (DBG) Log.d(TAG, "Publishing results")
      mSuggestions.addResults(value)
      if (value != null && mLogger != null) {
        mLogger.logLatency(value)
      }
      return true
    }
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.SuggestionsProviderImpl"
  }

  init {
    mPublishThread = publishThread
    mLogger = logger
  }
}
