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

import android.os.Trace
import android.util.Log
import com.android.app.tracing.asyncTraceForTrackBegin
import com.android.app.tracing.asyncTraceForTrackEnd
import com.android.systemui.Flags.coroutineTracing
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@PublishedApi internal const val DEBUG_COROUTINE_TRACING = false
@PublishedApi internal const val TAG = "CoroutineTracing"
@PublishedApi internal const val DEFAULT_TRACK_NAME = "AsyncTraces"

/**
 * Convenience function for calling [CoroutineScope.launch] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun CoroutineScope.launch(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    // TODO(b/306457056): DO NOT pass CoroutineStart; doing so will regress .odex size
    crossinline block: suspend CoroutineScope.() -> Unit
): Job = launch(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [CoroutineScope.launch] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun CoroutineScope.launch(
    spanName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    // TODO(b/306457056): DO NOT pass CoroutineStart; doing so will regress .odex size
    crossinline block: suspend CoroutineScope.() -> Unit
): Job = launch(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [CoroutineScope.async] with [traceCoroutine] enable tracing
 *
 * @see traceCoroutine
 */
inline fun <T> CoroutineScope.async(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    // TODO(b/306457056): DO NOT pass CoroutineStart; doing so will regress .odex size
    crossinline block: suspend CoroutineScope.() -> T
): Deferred<T> = async(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [CoroutineScope.async] with [traceCoroutine] enable tracing.
 *
 * @see traceCoroutine
 */
inline fun <T> CoroutineScope.async(
    spanName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    // TODO(b/306457056): DO NOT pass CoroutineStart; doing so will regress .odex size
    crossinline block: suspend CoroutineScope.() -> T
): Deferred<T> = async(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [runBlocking] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun <T> runBlocking(
    crossinline spanName: () -> String,
    context: CoroutineContext,
    crossinline block: suspend () -> T
): T = runBlocking(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [runBlocking] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun <T> runBlocking(
    spanName: String,
    context: CoroutineContext,
    crossinline block: suspend CoroutineScope.() -> T
): T = runBlocking(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [withContext] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
suspend inline fun <T> withContext(
    spanName: String,
    context: CoroutineContext,
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [withContext] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
suspend inline fun <T> withContext(
    crossinline spanName: () -> String,
    context: CoroutineContext,
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(context) { traceCoroutine(spanName) { block() } }

/**
 * Traces a section of work of a `suspend` [block]. The trace sections will appear on the thread
 * that is currently executing the [block] of work. If the [block] is suspended, all trace sections
 * added using this API will end until the [block] is resumed, which could happen either on this
 * thread or on another thread. If a child coroutine is started, it will inherit the trace sections
 * of its parent. The child will continue to print these trace sections whether or not the parent
 * coroutine is still running them.
 *
 * The current [CoroutineContext] must have a [TraceContextElement] for this API to work. Otherwise,
 * the trace sections will be dropped.
 *
 * For example, in the following trace, Thread #1 ran some work, suspended, then continued working
 * on Thread #2. Meanwhile, Thread #2 created a new child coroutine which inherited its trace
 * sections. Then, the original coroutine resumed on Thread #1 before ending. Meanwhile Thread #3 is
 * still printing trace sections from its parent because they were copied when it was created. There
 * is no way for the parent to communicate to the child that it marked these slices as completed.
 * While this might seem counterintuitive, it allows us to pinpoint the origin of the child
 * coroutine's work.
 *
 * ```
 * Thread #1 | [==== Slice A ====]                        [==== Slice A ====]
 *           |       [==== B ====]                        [=== B ===]
 * --------------------------------------------------------------------------------------
 * Thread #2 |                    [====== Slice A ======]
 *           |                    [========= B =========]
 *           |                        [===== C ======]
 * --------------------------------------------------------------------------------------
 * Thread #3 |                            [== Slice A ==]                [== Slice A ==]
 *           |                            [===== B =====]                [===== B =====]
 *           |                            [===== C =====]                [===== C =====]
 *           |                                                               [=== D ===]
 * ```
 *
 * @param name The name of the code section to appear in the trace
 * @see endSlice
 * @see traceCoroutine
 */
suspend inline fun <T> traceCoroutine(spanName: () -> String, block: () -> T): T {
    // For coroutine tracing to work, trace spans must be added and removed even when
    // tracing is not active (i.e. when TRACE_TAG_APP is disabled). Otherwise, when the
    // coroutine resumes when tracing is active, we won't know its name.
    val tracer = getTraceData()
    var spanString = "<none>"
    var coroutineSpanCookie = TraceData.INVALID_SPAN
    when (tracer) {
        is MissingTraceData -> logVerbose(tracer.message, spanName)
        is TraceData -> {
            spanString = spanName()
            coroutineSpanCookie = tracer.beginSpan(spanString)
        }
    }

    // For now, also trace to "AsyncTraces". This will allow us to verify the correctness
    // of the COROUTINE_TRACING feature flag.
    val asyncTraceCookie =
        if (Trace.isEnabled()) Random.nextInt(TraceData.FIRST_VALID_SPAN, Int.MAX_VALUE)
        else TraceData.INVALID_SPAN
    if (asyncTraceCookie != TraceData.INVALID_SPAN) {
        asyncTraceForTrackBegin(DEFAULT_TRACK_NAME, spanString, asyncTraceCookie)
    }
    try {
        return block()
    } finally {
        if (asyncTraceCookie != TraceData.INVALID_SPAN) {
            asyncTraceForTrackEnd(DEFAULT_TRACK_NAME, spanString, asyncTraceCookie)
        }
        if (tracer is TraceData) {
            tracer.endSpan(coroutineSpanCookie)
        }
    }
}

/** @see traceCoroutine */
suspend inline fun <T> traceCoroutine(spanName: String, block: () -> T): T =
    traceCoroutine({ spanName }, block)

@PublishedApi
internal suspend fun getTraceData(): TraceStatus {
    return if (!coroutineTracing()) {
        MissingTraceData("Experimental flag COROUTINE_TRACING is off")
    } else if (coroutineContext[TraceContextElement] == null) {
        MissingTraceData("Current CoroutineContext is missing TraceContextElement")
    } else {
        threadLocalTrace.get() ?: MissingTraceData("ThreadLocal TraceData is null")
    }
}

@PublishedApi
internal inline fun logVerbose(logMessage: String, spanName: () -> String) {
    if (DEBUG_COROUTINE_TRACING && Log.isLoggable(TAG, Log.VERBOSE)) {
        Log.v(TAG, "$logMessage. Dropping trace section: \"${spanName()}\"")
    }
}
