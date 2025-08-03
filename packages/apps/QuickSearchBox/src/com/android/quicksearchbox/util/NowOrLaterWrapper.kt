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

package com.android.quicksearchbox.util

/** [NowOrLater] class that converts from one type to another. */
abstract class NowOrLaterWrapper<A, B>(private val mWrapped: NowOrLater<A>) : NowOrLater<B> {
  override fun getLater(consumer: Consumer<in B>?) {
    mWrapped.getLater(
      object : Consumer<A> {
        override fun consume(value: A): Boolean {
          return consumer!!.consume(get(value))
        }
      }
    )
  }

  override val now: B
    get() = get(mWrapped.now)

  override fun haveNow(): Boolean {
    return mWrapped.haveNow()
  }

  /**
   * Perform the appropriate conversion. This will be called once for every call to [.getLater] or
   * [.getNow]. The thread that it's called on will depend on the behaviour of the wrapped object
   * and the caller.
   */
  abstract operator fun get(value: A): B
}
