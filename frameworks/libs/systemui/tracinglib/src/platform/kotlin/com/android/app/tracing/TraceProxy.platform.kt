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

import android.os.Trace

internal actual fun traceCounter(counterName: String, counterValue: Int) {
    Trace.traceCounter(Trace.TRACE_TAG_APP, counterName, counterValue)
}

internal actual fun traceBegin(methodName: String) {
    Trace.traceBegin(Trace.TRACE_TAG_APP, methodName)
}

internal actual fun traceEnd() {
    Trace.traceEnd(Trace.TRACE_TAG_APP)
}

internal actual fun asyncTraceBegin(methodName: String, cookie: Int) {
    Trace.asyncTraceBegin(Trace.TRACE_TAG_APP, methodName, cookie)
}

internal actual fun asyncTraceEnd(methodName: String, cookie: Int) {
    Trace.asyncTraceEnd(Trace.TRACE_TAG_APP, methodName, cookie)
}

@PublishedApi
internal actual fun asyncTraceForTrackBegin(trackName: String, methodName: String, cookie: Int) {
    Trace.asyncTraceForTrackBegin(Trace.TRACE_TAG_APP, trackName, methodName, cookie)
}

@PublishedApi
internal actual fun asyncTraceForTrackEnd(trackName: String, methodName: String, cookie: Int) {
    Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_APP, trackName, cookie)
}

internal actual fun instant(eventName: String) {
    Trace.instant(Trace.TRACE_TAG_APP, eventName)
}

@PublishedApi
internal actual fun instantForTrack(trackName: String, eventName: String) {
    Trace.instantForTrack(Trace.TRACE_TAG_APP, trackName, eventName)
}
