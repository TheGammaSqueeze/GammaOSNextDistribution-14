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

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.View
import com.android.common.Search
import com.android.quicksearchbox.ui.SearchActivityView
import com.android.quicksearchbox.ui.SuggestionClickListener
import com.android.quicksearchbox.ui.SuggestionsAdapter
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.CharMatcher
import java.io.File

/** The main activity for Quick Search Box. Shows the search UI. */
class SearchActivity : Activity() {
  private var mTraceStartUp = false

  // Measures time from for last onCreate()/onNewIntent() call.
  private var mStartLatencyTracker: LatencyTracker? = null

  // Measures time spent inside onCreate()
  private var mOnCreateTracker: LatencyTracker? = null
  private var mOnCreateLatency = 0

  // Whether QSB is starting. True between the calls to onCreate()/onNewIntent() and onResume().
  private var mStarting = false

  // True if the user has taken some action, e.g. launching a search, voice search,
  // or suggestions, since QSB was last started.
  private var mTookAction = false
  private var mSearchActivityView: SearchActivityView? = null
  protected var searchSource: Source? = null
    private set
  private var mAppSearchData: Bundle? = null
  private val mHandler: Handler = Handler(Looper.getMainLooper())
  private val mUpdateSuggestionsTask: Runnable =
    object : Runnable {
      @Override
      override fun run() {
        updateSuggestions()
      }
    }
  private val mShowInputMethodTask: Runnable =
    object : Runnable {
      @Override
      override fun run() {
        mSearchActivityView?.showInputMethodForQuery()
      }
    }
  private var mDestroyListener: OnDestroyListener? = null

  /** Called when the activity is first created. */
  @Override
  override fun onCreate(savedInstanceState: Bundle?) {
    mTraceStartUp = getIntent().hasExtra(INTENT_EXTRA_TRACE_START_UP)
    if (mTraceStartUp) {
      val traceFile: String = File(getDir("traces", 0), "qsb-start.trace").getAbsolutePath()
      Log.i(TAG, "Writing start-up trace to $traceFile")
      Debug.startMethodTracing(traceFile)
    }
    recordStartTime()
    if (DBG) Log.d(TAG, "onCreate()")
    super.onCreate(savedInstanceState)

    // This forces the HTTP request to check the users domain to be
    // sent as early as possible.
    QsbApplication[this].searchBaseUrlHelper
    searchSource = QsbApplication[this].googleSource
    mSearchActivityView = setupContentView()
    if (config?.showScrollingResults() == true) {
      mSearchActivityView?.setMaxPromotedResults(config!!.maxPromotedResults)
    } else {
      mSearchActivityView?.limitResultsToViewHeight()
    }
    mSearchActivityView?.setSearchClickListener(
      object : SearchActivityView.SearchClickListener {
        @Override
        override fun onSearchClicked(method: Int): Boolean {
          return this@SearchActivity.onSearchClicked(method)
        }
      }
    )
    mSearchActivityView?.setQueryListener(
      object : SearchActivityView.QueryListener {
        @Override
        override fun onQueryChanged() {
          updateSuggestionsBuffered()
        }
      }
    )
    mSearchActivityView?.setSuggestionClickListener(ClickHandler())
    mSearchActivityView?.setVoiceSearchButtonClickListener(
      object : View.OnClickListener {
        @Override
        override fun onClick(view: View?) {
          onVoiceSearchClicked()
        }
      }
    )
    val finishOnClick: View.OnClickListener =
      object : View.OnClickListener {
        @Override
        override fun onClick(v: View?) {
          finish()
        }
      }
    mSearchActivityView?.setExitClickListener(finishOnClick)

    // First get setup from intent
    val intent: Intent = getIntent()
    setupFromIntent(intent)
    // Then restore any saved instance state
    restoreInstanceState(savedInstanceState)

    // Do this at the end, to avoid updating the list view when setSource()
    // is called.
    mSearchActivityView?.start()
    recordOnCreateDone()
  }

  protected fun setupContentView(): SearchActivityView {
    setContentView(R.layout.search_activity)
    return findViewById(R.id.search_activity_view) as SearchActivityView
  }

  protected val searchActivityView: SearchActivityView?
    get() = mSearchActivityView

  @Override
  protected override fun onNewIntent(intent: Intent) {
    if (DBG) Log.d(TAG, "onNewIntent()")
    recordStartTime()
    setIntent(intent)
    setupFromIntent(intent)
  }

  private fun recordStartTime() {
    mStartLatencyTracker = LatencyTracker()
    mOnCreateTracker = LatencyTracker()
    mStarting = true
    mTookAction = false
  }

  private fun recordOnCreateDone() {
    mOnCreateLatency = mOnCreateTracker!!.latency
  }

  protected fun restoreInstanceState(savedInstanceState: Bundle?) {
    if (savedInstanceState == null) return
    val query: String? = savedInstanceState.getString(INSTANCE_KEY_QUERY)
    setQuery(query, false)
  }

  @Override
  protected override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    // We don't save appSearchData, since we always get the value
    // from the intent and the user can't change it.
    outState.putString(INSTANCE_KEY_QUERY, query)
  }

  private fun setupFromIntent(intent: Intent) {
    if (DBG) Log.d(TAG, "setupFromIntent(" + intent.toUri(0).toString() + ")")
    @Suppress("UNUSED_VARIABLE") val corpusName = getCorpusNameFromUri(intent.getData())
    val query: String? = intent.getStringExtra(SearchManager.QUERY)
    val appSearchData: Bundle? = intent.getBundleExtra(SearchManager.APP_DATA)
    val selectAll: Boolean = intent.getBooleanExtra(SearchManager.EXTRA_SELECT_QUERY, false)
    setQuery(query, selectAll)
    mAppSearchData = appSearchData
  }

  private fun getCorpusNameFromUri(uri: Uri?): String? {
    if (uri == null) return null
    return if (SCHEME_CORPUS != uri.getScheme()) null else uri.getAuthority()
  }

  private val qsbApplication: QsbApplication
    get() = QsbApplication[this]

  private val config: Config?
    get() = qsbApplication.config

  protected val settings: SearchSettings?
    get() = qsbApplication.settings

  private val suggestionsProvider: SuggestionsProvider?
    get() = qsbApplication.suggestionsProvider

  private val logger: Logger?
    get() = qsbApplication.logger

  @VisibleForTesting
  fun setOnDestroyListener(l: OnDestroyListener?) {
    mDestroyListener = l
  }

  @Override
  protected override fun onDestroy() {
    if (DBG) Log.d(TAG, "onDestroy()")
    mSearchActivityView?.destroy()
    super.onDestroy()
    if (mDestroyListener != null) {
      mDestroyListener?.onDestroyed()
    }
  }

  @Override
  protected override fun onStop() {
    if (DBG) Log.d(TAG, "onStop()")
    if (!mTookAction) {
      // TODO: This gets logged when starting other activities, e.g. by opening the search
      // settings, or clicking a notification in the status bar.
      // TODO we should log both sets of suggestions in 2-pane mode
      logger?.logExit(currentSuggestions, query!!.length)
    }
    // Close all open suggestion cursors. The query will be redone in onResume()
    // if we come back to this activity.
    mSearchActivityView?.clearSuggestions()
    mSearchActivityView?.onStop()
    super.onStop()
  }

  @Override
  protected override fun onPause() {
    if (DBG) Log.d(TAG, "onPause()")
    mSearchActivityView?.onPause()
    super.onPause()
  }

  @Override
  protected override fun onRestart() {
    if (DBG) Log.d(TAG, "onRestart()")
    super.onRestart()
  }

  @Override
  protected override fun onResume() {
    if (DBG) Log.d(TAG, "onResume()")
    super.onResume()
    updateSuggestionsBuffered()
    mSearchActivityView?.onResume()
    if (mTraceStartUp) Debug.stopMethodTracing()
  }

  @Override
  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    // Since the menu items are dynamic, we recreate the menu every time.
    menu.clear()
    createMenuItems(menu, true)
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun createMenuItems(menu: Menu, showDisabled: Boolean) {
    qsbApplication.help.addHelpMenuItem(menu, ACTIVITY_HELP_CONTEXT)
  }

  @Override
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      // Launch the IME after a bit
      mHandler.postDelayed(mShowInputMethodTask, 0)
    }
  }

  protected val query: String?
    get() = mSearchActivityView?.query

  protected fun setQuery(query: String?, selectAll: Boolean) {
    mSearchActivityView?.setQuery(query, selectAll)
  }

  /** @return true if a search was performed as a result of this click, false otherwise. */
  protected fun onSearchClicked(method: Int): Boolean {
    val query: String = CharMatcher.whitespace().trimAndCollapseFrom(query as CharSequence, ' ')
    if (DBG) Log.d(TAG, "Search clicked, query=$query")

    // Don't do empty queries
    if (TextUtils.getTrimmedLength(query) == 0) return false
    mTookAction = true

    // Log search start
    logger?.logSearch(method, query.length)

    // Start search
    startSearch(searchSource, query)
    return true
  }

  protected fun startSearch(searchSource: Source?, query: String?) {
    val intent: Intent? = searchSource!!.createSearchIntent(query, mAppSearchData)
    launchIntent(intent)
  }

  protected fun onVoiceSearchClicked() {
    if (DBG) Log.d(TAG, "Voice Search clicked")
    mTookAction = true

    // Log voice search start
    logger?.logVoiceSearch()

    // Start voice search
    val intent: Intent? = searchSource!!.createVoiceSearchIntent(mAppSearchData)
    launchIntent(intent)
  }

  protected val currentSuggestions: SuggestionCursor?
    get() {
      val suggestions: Suggestions = mSearchActivityView?.suggestions ?: return null
      return suggestions.getResult()
    }

  protected fun getCurrentSuggestions(
    adapter: SuggestionsAdapter<*>?,
    id: Long
  ): SuggestionPosition? {
    val pos: SuggestionPosition = adapter?.getSuggestion(id) ?: return null
    val suggestions: SuggestionCursor? = pos.cursor
    val position: Int = pos.position
    if (suggestions == null) {
      return null
    }
    val count: Int = suggestions.count
    if (position < 0 || position >= count) {
      Log.w(TAG, "Invalid suggestion position $position, count = $count")
      return null
    }
    suggestions.moveTo(position)
    return pos
  }

  protected fun launchIntent(intent: Intent?) {
    if (DBG) Log.d(TAG, "launchIntent $intent")
    if (intent == null) {
      return
    }
    try {
      startActivity(intent)
    } catch (ex: RuntimeException) {
      // Since the intents for suggestions specified by suggestion providers,
      // guard against them not being handled, not allowed, etc.
      Log.e(TAG, "Failed to start " + intent.toUri(0), ex)
    }
  }

  private fun launchSuggestion(adapter: SuggestionsAdapter<*>?, id: Long): Boolean {
    val suggestion = getCurrentSuggestions(adapter, id) ?: return false
    if (DBG) Log.d(TAG, "Launching suggestion $id")
    mTookAction = true

    // Log suggestion click
    logger?.logSuggestionClick(id, suggestion.cursor, Logger.SUGGESTION_CLICK_TYPE_LAUNCH)

    // Launch intent
    launchSuggestion(suggestion.cursor, suggestion.position)
    return true
  }

  protected fun launchSuggestion(suggestions: SuggestionCursor?, position: Int) {
    suggestions?.moveTo(position)
    val intent: Intent = SuggestionUtils.getSuggestionIntent(suggestions, mAppSearchData)
    launchIntent(intent)
  }

  protected fun refineSuggestion(adapter: SuggestionsAdapter<*>?, id: Long) {
    if (DBG) Log.d(TAG, "query refine clicked, pos $id")
    val suggestion = getCurrentSuggestions(adapter, id) ?: return
    val query: String? = suggestion.suggestionQuery
    if (TextUtils.isEmpty(query)) {
      return
    }

    // Log refine click
    logger?.logSuggestionClick(id, suggestion.cursor, Logger.SUGGESTION_CLICK_TYPE_REFINE)

    // Put query + space in query text view
    val queryWithSpace = "$query "
    setQuery(queryWithSpace, false)
    updateSuggestions()
    mSearchActivityView?.focusQueryTextView()
  }

  private fun updateSuggestionsBuffered() {
    if (DBG) Log.d(TAG, "updateSuggestionsBuffered()")
    mHandler.removeCallbacks(mUpdateSuggestionsTask)
    val delay: Long = config!!.typingUpdateSuggestionsDelayMillis
    mHandler.postDelayed(mUpdateSuggestionsTask, delay)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun gotSuggestions(suggestions: Suggestions?) {
    if (mStarting) {
      mStarting = false
      val source: String? = getIntent().getStringExtra(Search.SOURCE)
      val latency: Int = mStartLatencyTracker!!.latency
      logger?.logStart(mOnCreateLatency, latency, source)
      qsbApplication.onStartupComplete()
    }
  }

  fun updateSuggestions() {
    if (DBG) Log.d(TAG, "updateSuggestions()")
    val query: String = CharMatcher.whitespace().trimLeadingFrom(query as CharSequence)
    updateSuggestions(query, searchSource)
  }

  protected fun updateSuggestions(query: String, source: Source?) {
    if (DBG) Log.d(TAG, "updateSuggestions(\"$query\",$source)")
    val suggestions = suggestionsProvider?.getSuggestions(query, source!!)

    // Log start latency if this is the first suggestions update
    gotSuggestions(suggestions)
    showSuggestions(suggestions)
  }

  protected fun showSuggestions(suggestions: Suggestions?) {
    mSearchActivityView?.suggestions = suggestions
  }

  private inner class ClickHandler : SuggestionClickListener {
    @Override
    override fun onSuggestionClicked(adapter: SuggestionsAdapter<*>?, suggestionId: Long) {
      launchSuggestion(adapter, suggestionId)
    }

    @Override
    override fun onSuggestionQueryRefineClicked(
      adapter: SuggestionsAdapter<*>?,
      suggestionId: Long
    ) {
      refineSuggestion(adapter, suggestionId)
    }
  }

  interface OnDestroyListener {
    fun onDestroyed()
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.SearchActivity"
    private const val SCHEME_CORPUS = "qsb.corpus"
    private const val INTENT_EXTRA_TRACE_START_UP = "trace_start_up"

    // Keys for the saved instance state.
    private const val INSTANCE_KEY_QUERY = "query"
    private const val ACTIVITY_HELP_CONTEXT = "search"
  }
}
