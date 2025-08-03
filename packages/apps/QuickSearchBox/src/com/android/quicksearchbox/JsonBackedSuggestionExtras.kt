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

import android.util.Log
import java.util.ArrayList
import org.json.JSONException
import org.json.JSONObject

/** SuggestionExtras taking values from a [JSONObject]. */
class JsonBackedSuggestionExtras : SuggestionExtras {
  private val mExtras: JSONObject
  override val extraColumnNames: Collection<String>

  constructor(json: String?) {
    mExtras = JSONObject(json!!)
    extraColumnNames = ArrayList<String>(mExtras.length())
    val it: Iterator<String> = mExtras.keys()
    while (it.hasNext()) {
      extraColumnNames.add(it.next())
    }
  }

  constructor(extras: SuggestionExtras) {
    mExtras = JSONObject()
    extraColumnNames = extras.extraColumnNames
    for (column in extras.extraColumnNames) {
      val value = extras.getExtra(column)
      mExtras.put(column, value ?: JSONObject.NULL)
    }
  }

  override fun getExtra(columnName: String?): String? {
    return try {
      if (mExtras.isNull(columnName)) {
        null
      } else {
        mExtras.getString(columnName!!)
      }
    } catch (e: JSONException) {
      Log.w(TAG, "Could not extract JSON extra", e)
      null
    }
  }

  @Override
  override fun toString(): String {
    return mExtras.toString()
  }

  override fun toJsonString(): String? {
    return toString()
  }

  companion object {
    private const val TAG = "QSB.JsonBackedSuggestionExtras"
  }
}
