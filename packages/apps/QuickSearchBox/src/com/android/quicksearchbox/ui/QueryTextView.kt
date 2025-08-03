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
import android.util.Log
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** The query text field. */
class QueryTextView : EditText {
  private var mCommitCompletionListener: CommitCompletionListener? = null

  constructor(
    context: Context?,
    attrs: AttributeSet?,
    defStyle: Int
  ) : super(context, attrs, defStyle)

  constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context?) : super(context)

  /**
   * Sets the text selection in the query text view.
   *
   * @param selectAll If `true`, selects the entire query. If {@false}, no characters are selected,
   * and the cursor is placed at the end of the query.
   */
  fun setTextSelection(selectAll: Boolean) {
    if (selectAll) {
      selectAll()
    } else {
      setSelection(length())
    }
  }

  protected fun replaceText(text: CharSequence?) {
    clearComposingText()
    setText(text)
    setTextSelection(false)
  }

  fun setCommitCompletionListener(listener: CommitCompletionListener?) {
    mCommitCompletionListener = listener
  }

  private val inputMethodManager: InputMethodManager?
    get() = getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

  fun showInputMethod() {
    val imm: InputMethodManager? = inputMethodManager
    if (imm != null) {
      imm.showSoftInput(this, 0)
    }
  }

  fun hideInputMethod() {
    val imm: InputMethodManager? = inputMethodManager
    if (imm != null) {
      imm.hideSoftInputFromWindow(getWindowToken(), 0)
    }
  }

  @Override
  override fun onCommitCompletion(completion: CompletionInfo) {
    if (DBG) Log.d(TAG, "onCommitCompletion($completion)")
    hideInputMethod()
    replaceText(completion.getText())
    if (mCommitCompletionListener != null) {
      mCommitCompletionListener?.onCommitCompletion(completion.getPosition())
    }
  }

  interface CommitCompletionListener {
    fun onCommitCompletion(position: Int)
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.QueryTextView"
  }
}
