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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.quicksearchbox.Suggestion
import com.android.quicksearchbox.SuggestionCursor

/** Suggestion view factory that inflates views from XML. */
open class SuggestionViewInflater(
  private val mViewType: String,
  viewClass: Class<out SuggestionView?>,
  layoutId: Int,
  context: Context?
) : SuggestionViewFactory {
  private val mViewClass: Class<*>
  private val mLayoutId: Int
  private val mContext: Context?

  protected val inflater: LayoutInflater
    get() = mContext?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

  override val suggestionViewTypes: Collection<String>
    get() = listOf(mViewType)

  override fun getView(
    suggestion: SuggestionCursor?,
    userQuery: String?,
    convertView: View?,
    parent: ViewGroup?
  ): View? {
    var mConvertView: View? = convertView
    if (mConvertView == null || !mConvertView::class.equals(mViewClass)) {
      val layoutId = mLayoutId
      mConvertView = inflater.inflate(layoutId, parent, false)
    }
    if (mConvertView !is SuggestionView) {
      throw IllegalArgumentException("Not a SuggestionView: $mConvertView")
    }
    (mConvertView as SuggestionView).bindAsSuggestion(suggestion, userQuery)
    return mConvertView
  }

  override fun getViewType(suggestion: Suggestion?): String {
    return mViewType
  }

  override fun canCreateView(suggestion: Suggestion?): Boolean {
    return true
  }

  /**
   * @param viewType The unique type of views inflated by this factory
   * @param viewClass The expected type of view classes.
   * @param layoutId resource ID of layout to use.
   * @param context Context to use for inflating the views.
   */
  init {
    mViewClass = viewClass
    mLayoutId = layoutId
    mContext = context
  }
}
