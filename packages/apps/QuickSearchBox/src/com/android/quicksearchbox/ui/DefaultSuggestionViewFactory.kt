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
import android.view.View
import android.view.ViewGroup
import com.android.quicksearchbox.Suggestion
import com.android.quicksearchbox.SuggestionCursor
import java.util.LinkedList

/** Suggestion view factory for Google suggestions. */
class DefaultSuggestionViewFactory(context: Context?) : SuggestionViewFactory {
  private val mFactories: LinkedList<SuggestionViewFactory> = LinkedList<SuggestionViewFactory>()
  private val mDefaultFactory: SuggestionViewFactory
  private var mViewTypes: HashSet<String>? = null

  /** Must only be called from the constructor */
  protected fun addFactory(factory: SuggestionViewFactory?) {
    mFactories.addFirst(factory)
  }

  @get:Override
  override val suggestionViewTypes: Collection<String>
    get() {
      if (mViewTypes == null) {
        mViewTypes = hashSetOf()
        mViewTypes?.addAll(mDefaultFactory.suggestionViewTypes)
        for (factory in mFactories) {
          mViewTypes?.addAll(factory.suggestionViewTypes)
        }
      }
      return mViewTypes as Collection<String>
    }

  @Override
  override fun getView(
    suggestion: SuggestionCursor?,
    userQuery: String?,
    convertView: View?,
    parent: ViewGroup?
  ): View? {
    for (factory in mFactories) {
      if (factory.canCreateView(suggestion)) {
        return factory.getView(suggestion, userQuery, convertView, parent)
      }
    }
    return mDefaultFactory.getView(suggestion, userQuery, convertView, parent)
  }

  @Override
  override fun getViewType(suggestion: Suggestion?): String {
    for (factory in mFactories) {
      if (factory.canCreateView(suggestion)) {
        return factory.getViewType(suggestion)!!
      }
    }
    return mDefaultFactory.getViewType(suggestion)!!
  }

  @Override
  override fun canCreateView(suggestion: Suggestion?): Boolean {
    return true
  }

  init {
    mDefaultFactory = DefaultSuggestionView.Factory(context)
    addFactory(WebSearchSuggestionView.Factory(context))
  }
}
