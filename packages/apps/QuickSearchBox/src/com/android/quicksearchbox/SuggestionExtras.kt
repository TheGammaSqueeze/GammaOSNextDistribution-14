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

import org.json.JSONException

/** Extra data that can be attached to a suggestion. */
interface SuggestionExtras {
  /** Return the names of custom columns present in these extras. */
  val extraColumnNames: Collection<String>

  /** @param columnName The column to get a value from. */
  fun getExtra(columnName: String?): String?

  /** Flatten these extras as a JSON object. */
  @Throws(JSONException::class) fun toJsonString(): String?
}
