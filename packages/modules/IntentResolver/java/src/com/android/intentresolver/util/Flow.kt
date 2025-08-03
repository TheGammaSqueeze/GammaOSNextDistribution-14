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

package com.android.intentresolver.util

import android.os.SystemClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Returns a flow that mirrors the original flow, but delays values following emitted values for the
 * given [periodMs]. If the original flow emits more than one value during this period, only the
 * latest value is emitted.
 *
 * Example:
 *
 * ```kotlin
 * flow {
 *     emit(1)     // t=0ms
 *     delay(90)
 *     emit(2)     // t=90ms
 *     delay(90)
 *     emit(3)     // t=180ms
 *     delay(1010)
 *     emit(4)     // t=1190ms
 *     delay(1010)
 *     emit(5)     // t=2200ms
 * }.throttle(1000)
 * ```
 *
 * produces the following emissions at the following times
 *
 * ```text
 * 1 (t=0ms), 3 (t=1000ms), 4 (t=2000ms), 5 (t=3000ms)
 * ```
 */
// A SystemUI com.android.systemui.util.kotlin.throttle copy.
fun <T> Flow<T>.throttle(periodMs: Long): Flow<T> = channelFlow {
    coroutineScope {
        var previousEmitTimeMs = 0L
        var delayJob: Job? = null
        var sendJob: Job? = null
        val outerScope = this

        collect {
            delayJob?.cancel()
            sendJob?.join()
            val currentTimeMs = SystemClock.elapsedRealtime()
            val timeSinceLastEmit = currentTimeMs - previousEmitTimeMs
            val timeUntilNextEmit = maxOf(0L, periodMs - timeSinceLastEmit)
            if (timeUntilNextEmit > 0L) {
                // We create delayJob to allow cancellation during the delay period
                delayJob = launch {
                    delay(timeUntilNextEmit)
                    sendJob = outerScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        send(it)
                        previousEmitTimeMs = SystemClock.elapsedRealtime()
                    }
                }
            } else {
                send(it)
                previousEmitTimeMs = currentTimeMs
            }
        }
    }
}
