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

package android.tools.traces.events

import android.tools.Timestamp
import android.tools.TraceEntry

/**
 * Represents an Event from the [EventLog]
 *
 * @param timestamp The wall clock time in nanoseconds when the entry was written.
 * @param processId The process ID which wrote the log entry
 * @param uid The UID which wrote the log entry, special UIDs are strings instead of numbers (e.g.
 *   root)
 * @param threadId The thread which wrote the log entry
 * @param tag The type tag code of the entry
 */
open class Event(
    override val timestamp: Timestamp,
    val processId: Int,
    val uid: String,
    val threadId: Int,
    val tag: String
) : TraceEntry
