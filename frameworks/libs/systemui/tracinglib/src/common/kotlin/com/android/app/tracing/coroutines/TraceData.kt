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

package com.android.app.tracing.coroutines

import android.util.Log
import com.android.app.tracing.beginSlice
import com.android.app.tracing.endSlice
import kotlin.random.Random

/**
 * Used for giving each thread a unique [TraceData] for thread-local storage. `null` by default.
 * [threadLocalTrace] can only be used when it is paired with a [TraceContextElement].
 *
 * This ThreadLocal will be `null` if either 1) we aren't in a coroutine, or 2) the coroutine we are
 * in does not have a [TraceContextElement].
 *
 * This is internal machinery for [traceCoroutine]. It cannot be made `internal` or `private`
 * because [traceCoroutine] is a Public-API inline function.
 *
 * @see traceCoroutine
 */
internal val threadLocalTrace = ThreadLocal<TraceData?>()

@PublishedApi internal sealed interface TraceStatus

@PublishedApi internal data class MissingTraceData(val message: String) : TraceStatus

/**
 * Used for storing trace sections so that they can be added and removed from the currently running
 * thread when the coroutine is suspended and resumed.
 *
 * This is internal machinery for [traceCoroutine]. It cannot be made `internal` or `private`
 * because [traceCoroutine] is a Public-API inline function.
 *
 * @see traceCoroutine
 */
@PublishedApi
internal class TraceData : TraceStatus {
    private var slices = mutableListOf<TraceSection>()

    /** Adds current trace slices back to the current thread. Called when coroutine is resumed. */
    internal fun beginAllOnThread() {
        slices.forEach { beginSlice(it.name) }
    }

    /**
     * Removes all current trace slices from the current thread. Called when coroutine is suspended.
     */
    internal fun endAllOnThread() {
        for (i in 0..slices.size) {
            endSlice()
        }
    }

    /**
     * Creates a new trace section with a unique ID and adds it to the current trace data. The slice
     * will also be added to the current thread immediately. This slice will not propagate to parent
     * coroutines, or to child coroutines that have already started. The unique ID is used to verify
     * that the [endSpan] is corresponds to a [beginSpan].
     */
    @PublishedApi
    internal fun beginSpan(name: String): Int {
        val newSlice = TraceSection(name, Random.nextInt(FIRST_VALID_SPAN, Int.MAX_VALUE))
        slices.add(newSlice)
        beginSlice(name)
        return newSlice.id
    }

    /**
     * Used by [TraceContextElement] when launching a child coroutine so that the child coroutine's
     * state is isolated from the parent.
     */
    internal fun copy(): TraceData {
        return TraceData().also { it.slices.addAll(slices) }
    }

    /**
     * Ends the trace section and validates it corresponds with an earlier call to [beginSpan]. The
     * trace slice will immediately be removed from the current thread. This information will not
     * propagate to parent coroutines, or to child coroutines that have already started.
     */
    @PublishedApi
    internal fun endSpan(id: Int) {
        val v = slices.removeAt(slices.lastIndex)
        if (v.id != id) {
            if (STRICT_MODE) {
                throw IllegalArgumentException(MISMATCHED_TRACE_ERROR_MESSAGE)
            } else if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, MISMATCHED_TRACE_ERROR_MESSAGE)
            }
        }
        endSlice()
    }

    @PublishedApi
    internal companion object {
        private const val TAG = "TraceData"
        @PublishedApi internal const val INVALID_SPAN = -1
        @PublishedApi internal val FIRST_VALID_SPAN = 1

        /**
         * If true, throw an exception instead of printing a warning when trace sections beginnings
         * and ends are mismatched.
         */
        private val STRICT_MODE = false

        private val MISMATCHED_TRACE_ERROR_MESSAGE =
            """
              Mismatched trace section. This likely means you are accessing the trace local \
              storage (threadLocalTrace) without a corresponding CopyableThreadContextElement. \
              This could happen if you are using a global dispatcher like Dispatchers.IO. \
              To fix this, use one of the coroutine contexts provided by the dagger scope  \
              (e.g. \"@Main CoroutineContext\").
        """
                .trimIndent()
                .replace("\\\n", "")
    }
}
