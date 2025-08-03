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

import kotlin.collections.HashSet
import org.json.JSONException

/** Abstract SuggestionExtras supporting flattening to JSON. */
abstract class AbstractSuggestionExtras
protected constructor(private val mMore: SuggestionExtras?) : SuggestionExtras {
  @get:Override
  override val extraColumnNames: Collection<String>
    get() {
      val columns: HashSet<String> = HashSet<String>()
      columns.addAll(doGetExtraColumnNames())
      if (mMore != null) {
        columns.addAll(mMore.extraColumnNames)
      }
      return columns
    }

  protected abstract fun doGetExtraColumnNames(): Collection<String>
  override fun getExtra(columnName: String?): String? {
    var extra = doGetExtra(columnName)
    if (extra == null && mMore != null) {
      extra = mMore.getExtra(columnName)
    }
    return extra
  }

  protected abstract fun doGetExtra(columnName: String?): String?

  @Throws(JSONException::class)
  override fun toJsonString(): String? {
    return JsonBackedSuggestionExtras(this).toString()
  }
}
