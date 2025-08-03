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

package com.android.quicksearchbox.ui

import android.content.Context
import android.database.DataSetObserver
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import com.android.quicksearchbox.*
import com.android.quicksearchbox.R
import java.util.Arrays
import kotlin.collections.ArrayList

abstract class SearchActivityView : RelativeLayout {
  @JvmField protected var mQueryTextView: QueryTextView? = null

  // True if the query was empty on the previous call to updateQuery()
  @JvmField protected var mQueryWasEmpty = true
  @JvmField protected var mQueryTextEmptyBg: Drawable? = null
  protected var mQueryTextNotEmptyBg: Drawable? = null
  @JvmField protected var mSuggestionsView: SuggestionsListView<ListAdapter?>? = null
  @JvmField protected var mSuggestionsAdapter: SuggestionsAdapter<ListAdapter?>? = null
  @JvmField protected var mSearchGoButton: ImageButton? = null
  @JvmField protected var mVoiceSearchButton: ImageButton? = null
  @JvmField protected var mButtonsKeyListener: ButtonsKeyListener? = null
  private var mUpdateSuggestions = false
  private var mQueryListener: QueryListener? = null
  private var mSearchClickListener: SearchClickListener? = null
  @JvmField protected var mExitClickListener: View.OnClickListener? = null

  constructor(context: Context?) : super(context)
  constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
  constructor(
    context: Context?,
    attrs: AttributeSet?,
    defStyle: Int
  ) : super(context, attrs, defStyle)

  @Override
  protected override fun onFinishInflate() {
    mQueryTextView = findViewById(R.id.search_src_text) as QueryTextView?
    mSuggestionsView = findViewById(R.id.suggestions) as SuggestionsView?
    mSuggestionsView!!.setOnScrollListener(InputMethodCloser() as AbsListView.OnScrollListener?)
    mSuggestionsView!!.setOnKeyListener(SuggestionsViewKeyListener())
    mSuggestionsView!!.setOnFocusChangeListener(SuggestListFocusListener())
    mSuggestionsAdapter = createSuggestionsAdapter()
    // TODO: why do we need focus listeners both on the SuggestionsView and the individual
    // suggestions?
    mSuggestionsAdapter!!.setOnFocusChangeListener(SuggestListFocusListener())
    mSearchGoButton = findViewById(R.id.search_go_btn) as ImageButton?
    mVoiceSearchButton = findViewById(R.id.search_voice_btn) as ImageButton?
    mVoiceSearchButton?.setImageDrawable(voiceSearchIcon)
    mQueryTextView?.addTextChangedListener(SearchTextWatcher())
    mQueryTextView?.setOnEditorActionListener(QueryTextEditorActionListener())
    mQueryTextView?.setOnFocusChangeListener(QueryTextViewFocusListener())
    mQueryTextEmptyBg = mQueryTextView?.getBackground()
    mSearchGoButton?.setOnClickListener(SearchGoButtonClickListener())
    mButtonsKeyListener = ButtonsKeyListener()
    mSearchGoButton?.setOnKeyListener(mButtonsKeyListener)
    mVoiceSearchButton?.setOnKeyListener(mButtonsKeyListener)
    mUpdateSuggestions = true
  }

  abstract fun onResume()
  abstract fun onStop()
  fun onPause() {
    // Override if necessary
  }

  fun start() {
    mSuggestionsAdapter?.listAdapter?.registerDataSetObserver(SuggestionsObserver())
    mSuggestionsView!!.setSuggestionsAdapter(mSuggestionsAdapter)
  }

  fun destroy() {
    mSuggestionsView!!.setSuggestionsAdapter(null) // closes mSuggestionsAdapter
  }

  // TODO: Get rid of this. To make it more easily testable,
  // the SearchActivityView should not depend on QsbApplication.
  protected val qsbApplication: QsbApplication
    get() = QsbApplication[getContext()]
  protected val voiceSearchIcon: Drawable
    get() = getResources().getDrawable(R.drawable.ic_btn_speak_now, null)
  protected val voiceSearch: VoiceSearch?
    get() = qsbApplication.voiceSearch

  protected fun createSuggestionsAdapter(): SuggestionsAdapter<ListAdapter?> {
    return DelayingSuggestionsAdapter(SuggestionsListAdapter(qsbApplication.suggestionViewFactory))
  }

  @Suppress("UNUSED_PARAMETER") fun setMaxPromotedResults(maxPromoted: Int) {}

  fun limitResultsToViewHeight() {}

  fun setQueryListener(listener: QueryListener?) {
    mQueryListener = listener
  }

  fun setSearchClickListener(listener: SearchClickListener?) {
    mSearchClickListener = listener
  }

  fun setVoiceSearchButtonClickListener(listener: View.OnClickListener?) {
    if (mVoiceSearchButton != null) {
      mVoiceSearchButton?.setOnClickListener(listener)
    }
  }

  fun setSuggestionClickListener(listener: SuggestionClickListener?) {
    mSuggestionsAdapter!!.setSuggestionClickListener(listener)
    mQueryTextView!!.setCommitCompletionListener(
      object : QueryTextView.CommitCompletionListener {
        @Override
        override fun onCommitCompletion(position: Int) {
          mSuggestionsAdapter!!.onSuggestionClicked(position.toLong())
        }
      }
    )
  }

  fun setExitClickListener(listener: View.OnClickListener?) {
    mExitClickListener = listener
  }

  var suggestions: Suggestions?
    get() = mSuggestionsAdapter?.suggestions
    set(suggestions) {
      suggestions?.acquire()
      mSuggestionsAdapter?.suggestions = suggestions
    }
  val currentSuggestions: SuggestionCursor
    get() = mSuggestionsAdapter?.suggestions?.getResult() as SuggestionCursor

  fun clearSuggestions() {
    mSuggestionsAdapter?.suggestions = null
  }

  val query: String
    get() {
      val q: CharSequence? = mQueryTextView?.getText()
      return q.toString()
    }
  val isQueryEmpty: Boolean
    get() = TextUtils.isEmpty(query)

  /** Sets the text in the query box. Does not update the suggestions. */
  fun setQuery(query: String?, selectAll: Boolean) {
    mUpdateSuggestions = false
    mQueryTextView?.setText(query)
    mQueryTextView!!.setTextSelection(selectAll)
    mUpdateSuggestions = true
  }

  protected val activity: SearchActivity?
    get() {
      val context: Context = getContext()
      return if (context is SearchActivity) {
        context
      } else {
        null
      }
    }

  fun hideSuggestions() {
    mSuggestionsView!!.setVisibility(GONE)
  }

  fun showSuggestions() {
    mSuggestionsView!!.setVisibility(VISIBLE)
  }

  fun focusQueryTextView() {
    mQueryTextView?.requestFocus()
  }

  protected fun updateUi(queryEmpty: Boolean = isQueryEmpty) {
    updateQueryTextView(queryEmpty)
    updateSearchGoButton(queryEmpty)
    updateVoiceSearchButton(queryEmpty)
  }

  protected fun updateQueryTextView(queryEmpty: Boolean) {
    if (queryEmpty) {
      mQueryTextView?.setBackground(mQueryTextEmptyBg)
      mQueryTextView?.setHint(null)
    } else {
      mQueryTextView?.setBackgroundResource(R.drawable.textfield_search)
    }
  }

  private fun updateSearchGoButton(queryEmpty: Boolean) {
    if (queryEmpty) {
      mSearchGoButton?.setVisibility(View.GONE)
    } else {
      mSearchGoButton?.setVisibility(View.VISIBLE)
    }
  }

  protected fun updateVoiceSearchButton(queryEmpty: Boolean) {
    if (shouldShowVoiceSearch(queryEmpty) && voiceSearch!!.shouldShowVoiceSearch()) {
      mVoiceSearchButton?.setVisibility(View.VISIBLE)
      mQueryTextView?.setPrivateImeOptions(IME_OPTION_NO_MICROPHONE)
    } else {
      mVoiceSearchButton?.setVisibility(View.GONE)
      mQueryTextView?.setPrivateImeOptions(null)
    }
  }

  protected fun shouldShowVoiceSearch(queryEmpty: Boolean): Boolean {
    return queryEmpty
  }

  /** Hides the input method. */
  protected fun hideInputMethod() {
    val imm: InputMethodManager? =
      getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (imm != null) {
      imm.hideSoftInputFromWindow(getWindowToken(), 0)
    }
  }

  abstract fun considerHidingInputMethod()
  fun showInputMethodForQuery() {
    mQueryTextView!!.showInputMethod()
  }

  /** Dismiss the activity if BACK is pressed when the search box is empty. */
  @Suppress("Deprecation")
  @Override
  override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
    val activity = activity
    if (activity != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && isQueryEmpty) {
      val state: KeyEvent.DispatcherState? = getKeyDispatcherState()
      if (state != null) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
          state.startTracking(event, this)
          return true
        } else if (
          event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && state.isTracking(event)
        ) {
          hideInputMethod()
          activity.onBackPressed()
          return true
        }
      }
    }
    return super.dispatchKeyEventPreIme(event)
  }

  /**
   * If the input method is in fullscreen mode, and the selector corpus is All or Web, use the web
   * search suggestions as completions.
   */
  protected fun updateInputMethodSuggestions() {
    val imm: InputMethodManager? =
      getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (imm == null || !imm.isFullscreenMode()) return
    val suggestions: Suggestions = mSuggestionsAdapter?.suggestions ?: return
    val completions: Array<CompletionInfo>? = webSuggestionsToCompletions(suggestions)
    if (DBG) Log.d(TAG, "displayCompletions(" + Arrays.toString(completions).toString() + ")")
    imm.displayCompletions(mQueryTextView, completions)
  }

  private fun webSuggestionsToCompletions(suggestions: Suggestions): Array<CompletionInfo>? {
    val cursor = suggestions.getWebResult() ?: return null
    val count: Int = cursor.count
    val completions: ArrayList<CompletionInfo> = ArrayList<CompletionInfo>(count)
    for (i in 0 until count) {
      cursor.moveTo(i)
      val text1: String? = cursor.suggestionText1
      completions.add(CompletionInfo(i.toLong(), i, text1))
    }
    return completions.toArray(arrayOfNulls<CompletionInfo>(completions.size))
  }

  protected fun onSuggestionsChanged() {
    updateInputMethodSuggestions()
  }

  @Suppress("UNUSED_PARAMETER")
  protected fun onSuggestionKeyDown(
    adapter: SuggestionsAdapter<*>?,
    suggestionId: Long,
    keyCode: Int,
    event: KeyEvent?
  ): Boolean {
    // Treat enter or search as a click
    return if (
      keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_SEARCH ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER
    ) {
      if (adapter != null) {
        adapter.onSuggestionClicked(suggestionId)
        true
      } else {
        false
      }
    } else false
  }

  protected fun onSearchClicked(method: Int): Boolean {
    return if (mSearchClickListener != null) {
      mSearchClickListener!!.onSearchClicked(method)
    } else false
  }

  /** Filters the suggestions list when the search text changes. */
  private inner class SearchTextWatcher : TextWatcher {
    @Override
    override fun afterTextChanged(s: Editable) {
      val empty = s.length == 0
      if (empty != mQueryWasEmpty) {
        mQueryWasEmpty = empty
        updateUi(empty)
      }
      if (mUpdateSuggestions) {
        if (mQueryListener != null) {
          mQueryListener!!.onQueryChanged()
        }
      }
    }

    @Override
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    @Override override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
  }

  /** Handles key events on the suggestions list view. */
  protected inner class SuggestionsViewKeyListener : View.OnKeyListener {
    @Override
    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
      if (event.getAction() == KeyEvent.ACTION_DOWN && v is SuggestionsListView<*>) {
        val listView = v as SuggestionsListView<*>
        if (
          onSuggestionKeyDown(
            listView.getSuggestionsAdapter(),
            listView.getSelectedItemId(),
            keyCode,
            event
          )
        ) {
          return true
        }
      }
      return forwardKeyToQueryTextView(keyCode, event)
    }
  }

  private inner class InputMethodCloser : AbsListView.OnScrollListener {
    @Override
    override fun onScroll(
      view: AbsListView?,
      firstVisibleItem: Int,
      visibleItemCount: Int,
      totalItemCount: Int
    ) {}

    @Override
    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
      considerHidingInputMethod()
    }
  }

  /** Listens for clicks on the source selector. */
  private inner class SearchGoButtonClickListener : View.OnClickListener {
    @Override
    override fun onClick(view: View?) {
      onSearchClicked(Logger.SEARCH_METHOD_BUTTON)
    }
  }

  /** This class handles enter key presses in the query text view. */
  private inner class QueryTextEditorActionListener : OnEditorActionListener {
    @Override
    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
      var consumed = false
      if (event != null) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
          consumed = onSearchClicked(Logger.SEARCH_METHOD_KEYBOARD)
        } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
          // we have to consume the down event so that we receive the up event too
          consumed = true
        }
      }
      if (DBG) Log.d(TAG, "onEditorAction consumed=$consumed")
      return consumed
    }
  }

  /** Handles key events on the search and voice search buttons, by refocusing to EditText. */
  protected inner class ButtonsKeyListener : View.OnKeyListener {
    @Override
    override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
      return forwardKeyToQueryTextView(keyCode, event)
    }
  }

  private fun forwardKeyToQueryTextView(keyCode: Int, event: KeyEvent): Boolean {
    if (!event.isSystem() && shouldForwardToQueryTextView(keyCode)) {
      if (DBG) Log.d(TAG, "Forwarding key to query box: $event")
      if (mQueryTextView!!.requestFocus()) {
        return mQueryTextView!!.dispatchKeyEvent(event)
      }
    }
    return false
  }

  private fun shouldForwardToQueryTextView(keyCode: Int): Boolean {
    return when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP,
      KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_LEFT,
      KeyEvent.KEYCODE_DPAD_RIGHT,
      KeyEvent.KEYCODE_DPAD_CENTER,
      KeyEvent.KEYCODE_ENTER,
      KeyEvent.KEYCODE_SEARCH -> false
      else -> true
    }
  }

  /** Hides the input method when the suggestions get focus. */
  private inner class SuggestListFocusListener : OnFocusChangeListener {
    @Override
    override fun onFocusChange(v: View?, focused: Boolean) {
      if (DBG) Log.d(TAG, "Suggestions focus change, now: $focused")
      if (focused) {
        considerHidingInputMethod()
      }
    }
  }

  private inner class QueryTextViewFocusListener : OnFocusChangeListener {
    @Override
    override fun onFocusChange(v: View?, focused: Boolean) {
      if (DBG) Log.d(TAG, "Query focus change, now: $focused")
      if (focused) {
        // The query box got focus, show the input method
        showInputMethodForQuery()
      }
    }
  }

  protected inner class SuggestionsObserver : DataSetObserver() {
    @Override
    override fun onChanged() {
      onSuggestionsChanged()
    }
  }

  interface QueryListener {
    fun onQueryChanged()
  }

  interface SearchClickListener {
    fun onSearchClicked(method: Int): Boolean
  }

  private inner class CloseClickListener : OnClickListener {
    @Override
    override fun onClick(v: View?) {
      if (!isQueryEmpty) {
        mQueryTextView?.setText("")
      } else {
        mExitClickListener?.onClick(v)
      }
    }
  }

  companion object {
    protected const val DBG = false
    protected const val TAG = "QSB.SearchActivityView"

    // The string used for privateImeOptions to identify to the IME that it should not show
    // a microphone button since one already exists in the search dialog.
    // TODO: This should move to android-common or something.
    private const val IME_OPTION_NO_MICROPHONE = "nm"
  }
}
