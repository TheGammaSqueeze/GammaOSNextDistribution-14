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

import com.android.app.tracing.instant
import com.android.systemui.Flags.coroutineTracing
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Returns a new [CoroutineContext] used for tracing. Used to hide internal implementation details.
 */
fun createCoroutineTracingContext(): CoroutineContext {
    return if (coroutineTracing()) TraceContextElement() else EmptyCoroutineContext
}

/**
 * Used for safely persisting [TraceData] state when coroutines are suspended and resumed.
 *
 * This is internal machinery for [traceCoroutine]. It cannot be made `internal` or `private`
 * because [traceCoroutine] is a Public-API inline function.
 *
 * @see traceCoroutine
 */
internal class TraceContextElement(private val traceData: TraceData = TraceData()) :
    CopyableThreadContextElement<TraceData?> {

    internal companion object Key : CoroutineContext.Key<TraceContextElement>

    override val key: CoroutineContext.Key<*>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): TraceData? {
        val oldState = threadLocalTrace.get()
        oldState?.endAllOnThread()
        threadLocalTrace.set(traceData)
        instant("resuming ${context[CoroutineDispatcher]}")
        traceData.beginAllOnThread()
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: TraceData?) {
        instant("suspending ${context[CoroutineDispatcher]}")
        traceData.endAllOnThread()
        threadLocalTrace.set(oldState)
        oldState?.beginAllOnThread()
    }

    override fun copyForChild(): CopyableThreadContextElement<TraceData?> {
        return TraceContextElement(traceData.copy())
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        return TraceContextElement(traceData.copy())
    }
}
