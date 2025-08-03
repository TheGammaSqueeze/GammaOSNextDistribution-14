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

import android.os.Handler

/** Consumer utilities. */
object Consumers {
  @JvmStatic
  fun <A : QuietlyCloseable?> consumeCloseable(consumer: Consumer<A>?, value: A?) {
    var accepted = false
    try {
      accepted = consumer!!.consume(value!!)
    } finally {
      if (!accepted && value != null) value.close()
    }
  }

  @JvmStatic
  fun <A> consumeAsync(handler: Handler?, consumer: Consumer<A?>?, value: A?) {
    if (handler == null) {
      consumer?.consume(value)
    } else {
      handler.post(
        object : Runnable {
          override fun run() {
            consumer?.consume(value)
          }
        }
      )
    }
  }

  @JvmStatic
  fun <A : QuietlyCloseable?> consumeCloseableAsync(
    handler: Handler?,
    consumer: Consumer<A>?,
    value: A?
  ) {
    if (handler == null) {
      consumeCloseable(consumer, value)
    } else {
      handler.post(
        object : Runnable {
          override fun run() {
            consumeCloseable(consumer, value)
          }
        }
      )
    }
  }

  fun <A> createAsyncConsumer(handler: Handler?, consumer: Consumer<A?>?): Consumer<A?> {
    return object : Consumer<A?> {
      override fun consume(value: A?): Boolean {
        consumeAsync(handler, consumer, value)
        return true
      }
    }
  }

  fun <A : QuietlyCloseable?> createAsyncCloseableConsumer(
    handler: Handler?,
    consumer: Consumer<A>?
  ): Consumer<A?> {
    return object : Consumer<A?> {
      override fun consume(value: A?): Boolean {
        consumeCloseableAsync(handler, consumer, value)
        return true
      }
    }
  }
}
