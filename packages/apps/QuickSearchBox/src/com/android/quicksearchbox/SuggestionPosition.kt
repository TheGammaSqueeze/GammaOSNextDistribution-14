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

/** A pointer to a suggestion in a [SuggestionCursor]. */
class SuggestionPosition
@JvmOverloads
constructor(val cursor: SuggestionCursor?, val position: Int = cursor!!.position) :
  AbstractSuggestionWrapper() {

  /** Gets the suggestion cursor, moved to point to the right suggestion. */
  @Override
  override fun current(): Suggestion? {
    cursor?.moveTo(position)
    return cursor
  }

  @Override
  override fun toString(): String {
    return cursor.toString() + ":" + position
  }
}
