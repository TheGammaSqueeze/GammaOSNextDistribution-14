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

internal expect fun traceCounter(counterName: String, counterValue: Int)

internal expect fun traceBegin(methodName: String)

internal expect fun traceEnd()

internal expect fun asyncTraceBegin(methodName: String, cookie: Int)

internal expect fun asyncTraceEnd(methodName: String, cookie: Int)

internal expect fun asyncTraceForTrackBegin(trackName: String, methodName: String, cookie: Int)

internal expect fun asyncTraceForTrackEnd(trackName: String, methodName: String, cookie: Int)

/**
 * Writes a trace message indicating that an instant event occurred on the current thread. Unlike
 * slices, instant events have no duration and do not need to be matched with another call. Perfetto
 * will display instant events using an arrow pointing to the timestamp they occurred:
 * ```
 * Thread #1 | [==============]               [======]
 *           |     [====]                        ^
 *           |        ^
 * ```
 *
 * @param eventName The name of the event to appear in the trace.
 */
internal expect fun instant(eventName: String)

/**
 * Writes a trace message indicating that an instant event occurred on the given track. Unlike
 * slices, instant events have no duration and do not need to be matched with another call. Perfetto
 * will display instant events using an arrow pointing to the timestamp they occurred:
 * ```
 * Async  | [==============]               [======]
 *  Track |     [====]                        ^
 *   Name |        ^
 * ```
 *
 * @param trackName The track where the event should appear in the trace.
 * @param eventName The name of the event to appear in the trace.
 */
internal expect fun instantForTrack(trackName: String, eventName: String)
