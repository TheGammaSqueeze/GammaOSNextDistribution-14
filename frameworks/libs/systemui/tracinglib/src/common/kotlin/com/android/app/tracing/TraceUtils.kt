/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.app.tracing

import android.os.Trace
import com.android.app.tracing.coroutines.traceCoroutine
import kotlin.random.Random

/**
 * Writes a trace message to indicate that a given section of code has begun running __on the
 * current thread__. This must be followed by a corresponding call to [endSlice] in a reasonably
 * short amount of time __on the same thread__ (i.e. _before_ the thread becomes idle again and
 * starts running other, unrelated work).
 *
 * Calls to [beginSlice] and [endSlice] may be nested, and they will render in Perfetto as follows:
 * ```
 * Thread #1 | [==========================]
 *           |       [==============]
 *           |           [====]
 * ```
 *
 * This function is provided for convenience to wrap a call to [Trace.traceBegin], which is more
 * verbose to call than [Trace.beginSection], but has the added benefit of not throwing an
 * [IllegalArgumentException] if the provided string is longer than 127 characters. We use the term
 * "slice" instead of "section" to be consistent with Perfetto.
 *
 * # Avoiding malformed traces
 *
 * Improper usage of this API will lead to malformed traces with long slices that sometimes never
 * end. This will look like the following:
 * ```
 * Thread #1 | [===================================================================== ...
 *           |       [==============]         [====================================== ...
 *           |           [=======]              [======]       [===================== ...
 *           |                                                       [=======]
 * ```
 *
 * To avoid this, [beginSlice] and [endSlice] should never be called from `suspend` blocks (instead,
 * use [traceCoroutine] for tracing suspending functions). While it would be technically okay to
 * call from a suspending function if that function were to only wrap non-suspending blocks with
 * [beginSlice] and [endSlice], doing so is risky because suspend calls could be mistakenly added to
 * that block as the code is refactored.
 *
 * Additionally, it is _not_ okay to call [beginSlice] when registering a callback and match it with
 * a call to [endSlice] inside that callback, even if the callback runs on the same thread. Doing so
 * would cause malformed traces because the [beginSlice] wasn't closed before the thread became idle
 * and started running unrelated work.
 *
 * @param sliceName The name of the code section to appear in the trace
 * @see endSlice
 * @see traceCoroutine
 */
fun beginSlice(sliceName: String) {
    traceBegin(sliceName)
}

/**
 * Writes a trace message to indicate that a given section of code has ended. This call must be
 * preceded by a corresponding call to [beginSlice]. See [beginSlice] for important information
 * regarding usage.
 *
 * @see beginSlice
 * @see traceCoroutine
 */
fun endSlice() {
    traceEnd()
}

fun isEnabled(): Boolean {
    return Trace.isEnabled()
}

/**
 * Run a block within a [Trace] section. Calls [Trace.beginSection] before and [Trace.endSection]
 * after the passed block.
 */
inline fun <T> traceSection(tag: String, block: () -> T): T {
    val tracingEnabled = isEnabled()
    if (tracingEnabled) beginSlice(tag)
    return try {
        // Note that as this is inline, the block section would be duplicated if it is called
        // several times. For this reason, we're using the try/finally even if tracing is disabled.
        block()
    } finally {
        if (tracingEnabled) endSlice()
    }
}

/**
 * Same as [traceSection], but the tag is provided as a lambda to help avoiding creating expensive
 * strings when not needed.
 */
inline fun <T> traceSection(tag: () -> String, block: () -> T): T {
    val tracingEnabled = isEnabled()
    if (tracingEnabled) beginSlice(tag())
    return try {
        block()
    } finally {
        if (tracingEnabled) endSlice()
    }
}

object TraceUtils {
    const val TAG = "TraceUtils"
    const val DEFAULT_TRACK_NAME = "AsyncTraces"

    @JvmStatic
    inline fun <T> trace(tag: () -> String, block: () -> T): T {
        return traceSection(tag) { block() }
    }

    @JvmStatic
    inline fun <T> trace(tag: String, crossinline block: () -> T): T {
        return traceSection(tag) { block() }
    }

    @JvmStatic
    inline fun traceRunnable(tag: String, crossinline block: () -> Unit): Runnable {
        return Runnable { traceSection(tag) { block() } }
    }

    @JvmStatic
    inline fun traceRunnable(
        crossinline tag: () -> String,
        crossinline block: () -> Unit
    ): Runnable {
        return Runnable { traceSection(tag) { block() } }
    }

    /**
     * Creates an async slice in a track called "AsyncTraces".
     *
     * This can be used to trace coroutine code. Note that all usages of this method will appear
     * under a single track.
     */
    @JvmStatic
    inline fun <T> traceAsync(method: String, block: () -> T): T =
        traceAsync(DEFAULT_TRACK_NAME, method, block)

    /**
     * Creates an async slice in a track with [trackName] while [block] runs.
     *
     * This can be used to trace coroutine code. [method] will be the name of the slice, [trackName]
     * of the track. The track is one of the rows visible in a perfetto trace inside the app
     * process.
     */
    @JvmStatic
    inline fun <T> traceAsync(trackName: String, method: String, block: () -> T): T {
        val cookie = Random.nextInt()
        asyncTraceForTrackBegin(trackName, method, cookie)
        try {
            return block()
        } finally {
            asyncTraceForTrackEnd(trackName, method, cookie)
        }
    }
}
