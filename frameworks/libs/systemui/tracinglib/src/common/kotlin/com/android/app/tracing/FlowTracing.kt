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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/** Utilities to trace Flows */
object FlowTracing {

    /** Logs each flow element to a trace. */
    inline fun <T> Flow<T>.traceEach(
        flowName: String,
        logcat: Boolean = false,
        traceEmissionCount: Boolean = false,
        crossinline valueToString: (T) -> String = { it.toString() }
    ): Flow<T> {
        val stateLogger = TraceStateLogger(flowName, logcat = logcat)
        val baseFlow = if (traceEmissionCount) traceEmissionCount(flowName) else this
        return baseFlow.onEach { stateLogger.log(valueToString(it)) }
    }

    fun <T> Flow<T>.traceEmissionCount(flowName: String): Flow<T> {
        val trackName = "$flowName#emissionCount"
        var count = 0
        return onEach {
            count += 1
            traceCounter(trackName, count)
        }
    }
}
