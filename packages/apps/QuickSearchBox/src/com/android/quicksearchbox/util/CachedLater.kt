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

import android.util.Log
import kotlin.collections.MutableList

/**
 * Abstract base class for a one-place cache that holds a value that is produced asynchronously.
 *
 * @param <A> The type of the data held in the cache.
 */
abstract class CachedLater<A> : NowOrLater<A> {
  private val mLock: Any = Any()
  private var mValue: A? = null
  private var mCreating = false
  private var mValid = false
  private var mWaitingConsumers: MutableList<Consumer<in A>>? = null

  /**
   * Creates the object to store in the cache. This method must call [.store] when it's done. This
   * method must not block.
   */
  protected abstract fun create()

  /** Saves a new value to the cache. */
  protected fun store(value: A) {
    if (DBG) Log.d(TAG, "store()")
    var waitingConsumers: MutableList<Consumer<in A>>?
    synchronized(mLock) {
      mValue = value
      mValid = true
      mCreating = false
      waitingConsumers = mWaitingConsumers
      mWaitingConsumers = null
    }
    if (waitingConsumers != null) {
      for (consumer in waitingConsumers!!) {
        if (DBG) Log.d(TAG, "Calling consumer: $consumer")
        consumer.consume(value)
      }
    }
  }

  /**
   * Gets the value.
   *
   * @param consumer A consumer that will be given the cached value. The consumer may be called
   * synchronously, or asynchronously on an unspecified thread.
   */
  override fun getLater(consumer: Consumer<in A>?) {
    if (DBG) Log.d(TAG, "getLater()")
    var valid: Boolean
    var value: A?
    synchronized(mLock) {
      valid = mValid
      value = mValue
      if (!valid) {
        if (mWaitingConsumers == null) {
          mWaitingConsumers = mutableListOf()
        }
        mWaitingConsumers?.add(consumer!!)
      }
    }
    if (valid) {
      if (DBG) Log.d(TAG, "valid, calling consumer synchronously")
      consumer!!.consume(value!!)
    } else {
      var create = false
      synchronized(mLock) {
        if (!mCreating) {
          mCreating = true
          create = true
        }
      }
      if (create) {
        if (DBG) Log.d(TAG, "not valid, calling create()")
        create()
      } else {
        if (DBG) Log.d(TAG, "not valid, already creating")
      }
    }
  }

  /** Clears the cache. */
  fun clear() {
    if (DBG) Log.d(TAG, "clear()")
    synchronized(mLock) {
      mValue = null
      mValid = false
    }
  }

  override fun haveNow(): Boolean {
    synchronized(mLock) {
      return mValid
    }
  }

  @get:Synchronized
  override val now: A
    get() {
      synchronized(mLock) {
        if (!haveNow()) {
          throw IllegalStateException("getNow() called when haveNow() is false")
        }
        return mValue!!
      }
    }

  companion object {
    private const val TAG = "QSB.AsyncCache"
    private const val DBG = false
  }
}
