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
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.android.quicksearchbox.R
import com.android.quicksearchbox.Suggestion

/** Base class for suggestion views. */
abstract class BaseSuggestionView : RelativeLayout, SuggestionView {
  @JvmField protected var mText1: TextView? = null
  @JvmField protected var mText2: TextView? = null
  @JvmField protected var mIcon1: ImageView? = null
  @JvmField protected var mIcon2: ImageView? = null
  private var mSuggestionId: Long = 0
  private var mAdapter: SuggestionsAdapter<*>? = null

  constructor(
    context: Context?,
    attrs: AttributeSet?,
    defStyle: Int
  ) : super(context, attrs, defStyle)

  constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context?) : super(context)

  @Override
  protected override fun onFinishInflate() {
    super.onFinishInflate()
    mText1 = findViewById(R.id.text1) as TextView?
    mText2 = findViewById(R.id.text2) as TextView?
    mIcon1 = findViewById(R.id.icon1) as ImageView?
    mIcon2 = findViewById(R.id.icon2) as ImageView?
  }

  @Override
  override fun bindAsSuggestion(suggestion: Suggestion?, userQuery: String?) {
    setOnClickListener(ClickListener())
  }

  @Override
  override fun bindAdapter(adapter: SuggestionsAdapter<*>?, position: Long) {
    mAdapter = adapter
    mSuggestionId = position
  }

  protected fun isFromHistory(suggestion: Suggestion?): Boolean {
    return suggestion?.isSuggestionShortcut == true || suggestion?.isHistorySuggestion == true
  }

  /** Sets the first text line. */
  protected fun setText1(text: CharSequence?) {
    mText1?.setText(text)
  }

  /** Sets the second text line. */
  protected fun setText2(text: CharSequence?) {
    mText2?.setText(text)
    if (TextUtils.isEmpty(text)) {
      mText2?.setVisibility(GONE)
    } else {
      mText2?.setVisibility(VISIBLE)
    }
  }

  protected fun onSuggestionClicked() {
    if (mAdapter != null) {
      mAdapter!!.onSuggestionClicked(mSuggestionId)
    }
  }

  protected fun onSuggestionQueryRefineClicked() {
    if (mAdapter != null) {
      mAdapter!!.onSuggestionQueryRefineClicked(mSuggestionId)
    }
  }

  private inner class ClickListener : OnClickListener {
    @Override
    override fun onClick(v: View?) {
      onSuggestionClicked()
    }
  }
}
