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
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.android.quicksearchbox.QsbApplication
import com.android.quicksearchbox.R
import com.android.quicksearchbox.Suggestion
import com.android.quicksearchbox.SuggestionFormatter

/** View for web search suggestions. */
class WebSearchSuggestionView(context: Context?, attrs: AttributeSet?) :
  BaseSuggestionView(context, attrs) {
  private val mSuggestionFormatter: SuggestionFormatter?

  @Override
  override fun onFinishInflate() {
    super.onFinishInflate()
    val keyListener: WebSearchSuggestionView.KeyListener = KeyListener()
    setOnKeyListener(keyListener)
    mIcon2?.setOnKeyListener(keyListener)
    mIcon2?.setOnClickListener(
      object : OnClickListener {
        override fun onClick(v: View?) {
          onSuggestionQueryRefineClicked()
        }
      }
    )
    mIcon2?.setFocusable(true)
  }

  @Override
  override fun bindAsSuggestion(suggestion: Suggestion?, userQuery: String?) {
    super.bindAsSuggestion(suggestion, userQuery)
    val text1 = mSuggestionFormatter?.formatSuggestion(userQuery, suggestion?.suggestionText1)
    setText1(text1)
    setIsHistorySuggestion(suggestion?.isHistorySuggestion)
  }

  private fun setIsHistorySuggestion(isHistory: Boolean?) {
    if (isHistory == true) {
      mIcon1?.setImageResource(R.drawable.ic_history_suggestion)
      mIcon1?.setVisibility(VISIBLE)
    } else {
      mIcon1?.setVisibility(INVISIBLE)
    }
  }

  private inner class KeyListener : View.OnKeyListener {
    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
      var consumed = false
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v !== mIcon2) {
          consumed = mIcon2!!.requestFocus()
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v === mIcon2) {
          consumed = requestFocus()
        }
      }
      return consumed
    }
  }

  class Factory(context: Context?) :
    SuggestionViewInflater(
      VIEW_ID,
      WebSearchSuggestionView::class.java,
      R.layout.web_search_suggestion,
      context
    ) {
    @Override
    override fun canCreateView(suggestion: Suggestion?): Boolean {
      return suggestion!!.isWebSearchSuggestion
    }
  }

  companion object {
    private const val VIEW_ID = "web_search"
  }

  init {
    mSuggestionFormatter = QsbApplication[context].suggestionFormatter
  }
}
