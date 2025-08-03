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

import android.app.AlarmManager
import android.content.Context
import android.net.Uri
import android.os.Process
import java.util.HashSet

/**
 * Provides values for configurable parameters in all of QSB.
 *
 * All the methods in this class return fixed default values. Subclasses may make these values
 * server-side settable.
 */
class Config(context: Context?) {
  private val mContext: Context?
  private val mDefaultCorpora: HashSet<String>? = null
  private val mHiddenCorpora: HashSet<String>? = null
  private val mDefaultCorporaSuggestUris: HashSet<String>? = null
  protected val context: Context?
    get() = mContext

  /**
   * Releases any resources used by the configuration object.
   *
   * Default implementation does nothing.
   */
  fun close() {}
  private fun loadResourceStringSet(res: Int): HashSet<String> {
    val set: HashSet<String> = HashSet<String>()
    val items: Array<String> = mContext?.getResources()!!.getStringArray(res)
    for (item in items) {
      set.add(item)
    }
    return set
  }

  /** The number of promoted sources. */
  val numPromotedSources: Int
    get() =
      NUM_PROMOTED_SOURCES // Get the list of default corpora from a resource, which allows vendor
  // overlays.

  /** The number of suggestions visible above the onscreen keyboard. */
  val numSuggestionsAboveKeyboard: Int
    get() = // Get the list of default corpora from a resource, which allows vendor overlays.
    mContext?.getResources()!!.getInteger(R.integer.num_suggestions_above_keyboard)

  /** The maximum number of suggestions to promote. */
  val maxPromotedSuggestions: Int
    get() = mContext?.getResources()!!.getInteger(R.integer.max_promoted_suggestions)

  val maxPromotedResults: Int
    get() = mContext?.getResources()!!.getInteger(R.integer.max_promoted_results)

  /** The number of results to ask each source for. */
  val maxResultsPerSource: Int
    get() = MAX_RESULTS_PER_SOURCE

  /** The maximum number of shortcuts to show for the web source in All mode. */
  val maxShortcutsPerWebSource: Int
    get() = mContext?.getResources()!!.getInteger(R.integer.max_shortcuts_per_web_source)

  /** The maximum number of shortcuts to show for each non-web source in All mode. */
  val maxShortcutsPerNonWebSource: Int
    get() = mContext?.getResources()!!.getInteger(R.integer.max_shortcuts_per_non_web_source)

  /** Gets the maximum number of shortcuts that will be shown from the given source. */
  @Suppress("UNUSED_PARAMETER")
  fun getMaxShortcuts(sourceName: String?): Int {
    return maxShortcutsPerNonWebSource
  }

  /** The timeout for querying each source, in milliseconds. */
  val sourceTimeoutMillis: Long
    get() = SOURCE_TIMEOUT_MILLIS

  /**
   * The priority of query threads.
   *
   * @return A thread priority, as defined in [Process].
   */
  val queryThreadPriority: Int
    get() = QUERY_THREAD_PRIORITY

  /** The maximum age of log data used for shortcuts. */
  val maxStatAgeMillis: Long
    get() = MAX_STAT_AGE_MILLIS

  /** The minimum number of clicks needed to rank a source. */
  val minClicksForSourceRanking: Int
    get() = MIN_CLICKS_FOR_SOURCE_RANKING

  val numWebCorpusThreads: Int
    get() = NUM_WEB_CORPUS_THREADS

  /**
   * How often query latency should be logged.
   *
   * @return An integer in the range 0-1000. 0 means that no latency events should be logged. 1000
   * means that all latency events should be logged.
   */
  val latencyLogFrequency: Int
    get() = LATENCY_LOG_FREQUENCY

  /**
   * The delay in milliseconds before suggestions are updated while typing. If a new character is
   * typed before this timeout expires, the timeout is reset.
   */
  val typingUpdateSuggestionsDelayMillis: Long
    get() = TYPING_SUGGESTIONS_UPDATE_DELAY_MILLIS

  fun allowVoiceSearchHints(): Boolean {
    return true
  }

  /**
   * The period of time for which after installing voice search we should consider showing voice
   * search hints.
   *
   * @return The period in milliseconds.
   */
  val voiceSearchHintActivePeriod: Long
    get() = VOICE_SEARCH_HINT_ACTIVE_PERIOD

  /**
   * The time interval at which we should consider whether or not to show some voice search hints.
   *
   * @return The period in milliseconds.
   */
  val voiceSearchHintUpdatePeriod: Long
    get() = VOICE_SEARCH_HINT_UPDATE_INTERVAL

  /**
   * The time interval at which, on average, voice search hints are displayed.
   *
   * @return The period in milliseconds.
   */
  val voiceSearchHintShowPeriod: Long
    get() = VOICE_SEARCH_HINT_SHOW_PERIOD_MILLIS

  /**
   * The amount of time for which voice search hints are displayed in one go.
   *
   * @return The period in milliseconds.
   */
  val voiceSearchHintVisibleTime: Long
    get() = VOICE_SEARCH_HINT_VISIBLE_PERIOD

  /**
   * The period that we change voice search hints at while they're being displayed.
   *
   * @return The period in milliseconds.
   */
  val voiceSearchHintChangePeriod: Long
    get() = VOICE_SEARCH_HINT_CHANGE_PERIOD

  fun showSuggestionsForZeroQuery(): Boolean {
    // Get the list of default corpora from a resource, which allows vendor overlays.
    return mContext?.getResources()!!.getBoolean(R.bool.show_zero_query_suggestions)
  }

  fun showShortcutsForZeroQuery(): Boolean {
    // Get the list of default corpora from a resource, which allows vendor overlays.
    return mContext?.getResources()!!.getBoolean(R.bool.show_zero_query_shortcuts)
  }

  fun showScrollingSuggestions(): Boolean {
    return mContext?.getResources()!!.getBoolean(R.bool.show_scrolling_suggestions)
  }

  fun showScrollingResults(): Boolean {
    return mContext?.getResources()!!.getBoolean(R.bool.show_scrolling_results)
  }

  @Suppress("UNUSED_PARAMETER")
  fun getHelpUrl(activity: String?): Uri? {
    return null
  }

  val httpConnectTimeout: Int
    get() = HTTP_CONNECT_TIMEOUT_MILLIS

  val httpReadTimeout: Int
    get() = HTTP_READ_TIMEOUT_MILLIS

  val userAgent: String
    get() = USER_AGENT

  companion object {
    protected const val SECOND_MILLIS = 1000L

    @JvmField protected val MINUTE_MILLIS: Long = 60L * SECOND_MILLIS
    private val VOICE_SEARCH_HINT_CHANGE_PERIOD: Long = 2L * MINUTE_MILLIS
    private val VOICE_SEARCH_HINT_VISIBLE_PERIOD: Long = 6L * MINUTE_MILLIS
    protected const val DAY_MILLIS = 86400000L
    private const val TAG = "QSB.Config"
    private const val DBG = false
    private const val NUM_PROMOTED_SOURCES = 3
    private const val MAX_RESULTS_PER_SOURCE = 50
    private const val SOURCE_TIMEOUT_MILLIS: Long = 10000
    private val QUERY_THREAD_PRIORITY: Int =
      Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE
    private val MAX_STAT_AGE_MILLIS: Long = 30 * DAY_MILLIS
    private const val MIN_CLICKS_FOR_SOURCE_RANKING = 3
    private const val NUM_WEB_CORPUS_THREADS = 2
    private const val LATENCY_LOG_FREQUENCY = 1000
    private const val TYPING_SUGGESTIONS_UPDATE_DELAY_MILLIS: Long = 100
    private const val PUBLISH_RESULT_DELAY_MILLIS: Long = 200
    private val VOICE_SEARCH_HINT_ACTIVE_PERIOD: Long = 7L * DAY_MILLIS
    private val VOICE_SEARCH_HINT_UPDATE_INTERVAL: Long = AlarmManager.INTERVAL_FIFTEEN_MINUTES
    private val VOICE_SEARCH_HINT_SHOW_PERIOD_MILLIS: Long = AlarmManager.INTERVAL_HOUR * 2
    private const val HTTP_CONNECT_TIMEOUT_MILLIS = 4000
    private const val HTTP_READ_TIMEOUT_MILLIS = 4000
    private const val USER_AGENT = "Android/1.0"
  }

  /** Creates a new config that uses hard-coded default values. */
  init {
    mContext = context
  }
}
