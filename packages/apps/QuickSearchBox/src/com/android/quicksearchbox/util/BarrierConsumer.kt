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

import java.util.ArrayList
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * A consumer that consumes a fixed number of values. When the expected number of values has been
 * consumed, further values are rejected.
 */
class BarrierConsumer<A>(private val mExpectedCount: Int) : Consumer<A> {
  private val mLock: Lock = ReentrantLock()
  private val mNotFull: Condition = mLock.newCondition()

  // Set to null when getValues() returns.
  private var mValues: ArrayList<A>?

  /**
   * Blocks until the expected number of results is available, or until the thread is interrupted.
   * This method should not be called multiple times.
   *
   * @return A list of values, never `null`.
   */
  val values: ArrayList<A>?
    get() {
      mLock.lock()
      return try {
        try {
          while (!isFull) {
            mNotFull.await()
          }
        } catch (ex: InterruptedException) {
          // Return the values that we've gotten so far
        }
        val values = mValues
        mValues = null // mark that getValues() has returned
        values
      } finally {
        mLock.unlock()
      }
    }

  override fun consume(value: A): Boolean {
    mLock.lock()
    return try {
      // Do nothing if getValues() has already returned,
      // or enough values have already been consumed
      if (mValues == null || isFull) {
        return false
      }
      mValues?.add(value)
      if (isFull) {
        // Wake up any thread waiting in getValues()
        mNotFull.signal()
      }
      true
    } finally {
      mLock.unlock()
    }
  }

  private val isFull: Boolean
    get() = mValues!!.size == mExpectedCount

  /**
   * Constructs a new BarrierConsumer.
   *
   * @param expectedCount The number of values to consume.
   */
  init {
    mValues = ArrayList<A>(mExpectedCount)
  }
}
