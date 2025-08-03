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

import android.database.DataSetObserver
import com.android.quicksearchbox.util.QuietlyCloseable
import kotlin.collections.Collection

/** A sequence of suggestions, with a current position. */
interface SuggestionCursor : Suggestion, QuietlyCloseable {
  /** Gets the query that the user typed to get this suggestion. */
  val userQuery: String?

  /**
   * Gets the number of suggestions in this result.
   *
   * @return The number of suggestions, or `0` if this result represents a failed query.
   */
  val count: Int

  /**
   * Moves to a given suggestion.
   *
   * @param pos The position to move to.
   * @throws IndexOutOfBoundsException if `pos < 0` or `pos >= getCount()`.
   */
  fun moveTo(pos: Int)

  /**
   * Moves to the next suggestion, if there is one.
   *
   * @return `false` if there is no next suggestion.
   */
  fun moveToNext(): Boolean

  /** Gets the current position within the cursor. */
  val position: Int

  /** Frees any resources used by this cursor. */
  @Override override fun close()

  /**
   * Register an observer that is called when changes happen to this data set.
   *
   * @param observer gets notified when the data set changes.
   */
  fun registerDataSetObserver(observer: DataSetObserver?)

  /**
   * Unregister an observer that has previously been registered with [.registerDataSetObserver]
   *
   * @param observer the observer to unregister.
   */
  fun unregisterDataSetObserver(observer: DataSetObserver?)

  /** Return the extra columns present in this cursor, or null if none exist. */
  val extraColumns: Collection<String>?
}
