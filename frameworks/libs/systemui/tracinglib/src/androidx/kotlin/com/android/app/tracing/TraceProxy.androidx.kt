/*
 * Copyright (C) 2024 The Android Open Source Project
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

import androidx.tracing.Trace
import kotlin.random.Random

internal actual fun traceCounter(counterName: String, counterValue: Int) {
    Trace.setCounter(counterName, counterValue)
}

internal actual fun traceBegin(methodName: String) {
    Trace.beginSection(methodName)
}

internal actual fun traceEnd() {
    Trace.endSection()
}

internal actual fun asyncTraceBegin(methodName: String, cookie: Int) {
    Trace.beginAsyncSection(methodName, cookie)
}

internal actual fun asyncTraceEnd(methodName: String, cookie: Int) {
    Trace.endAsyncSection(methodName, cookie)
}

private fun namedSlice(trackName: String, methodName: String) = "$trackName:$methodName"

@PublishedApi
internal actual fun asyncTraceForTrackBegin(trackName: String, methodName: String, cookie: Int) {
    if (isEnabled()) {
        asyncTraceBegin(namedSlice(trackName, methodName), cookie)
    }
}

@PublishedApi
internal actual fun asyncTraceForTrackEnd(trackName: String, methodName: String, cookie: Int) {
    if (isEnabled()) {
        asyncTraceEnd(namedSlice(trackName, methodName), cookie)
    }
}

internal actual fun instant(eventName: String) {
    if (isEnabled()) {
        traceBegin("instant:$eventName")
        traceEnd()
    }
}

internal actual fun instantForTrack(trackName: String, eventName: String) {
    if (Trace.isEnabled()) {
        val cookie = Random.nextInt()
        val name = "instant:${namedSlice(trackName,eventName)}"
        asyncTraceBegin(name, cookie)
        asyncTraceEnd(name, cookie)
    }
}
