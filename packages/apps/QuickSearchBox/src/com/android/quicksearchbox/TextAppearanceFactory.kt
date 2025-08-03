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

import android.content.Context
import android.text.style.TextAppearanceSpan

/** Factory class for text appearances. */
open class TextAppearanceFactory(context: Context?) {
  private val mContext: Context?
  open fun createSuggestionQueryTextAppearance(): Array<Any> {
    return arrayOf(TextAppearanceSpan(mContext, R.style.SuggestionText1_Query))
  }

  open fun createSuggestionSuggestedTextAppearance(): Array<Any> {
    return arrayOf(TextAppearanceSpan(mContext, R.style.SuggestionText1_Suggested))
  }

  init {
    mContext = context
  }
}
