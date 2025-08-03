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

/**
 * An Event from the [EventLog] representing a window focus change or request.
 *
 * @param timestamp The wall clock time in nanoseconds when the entry was written.
 * @param window The window that is the target of the focus event.
 * @param type The type of focus event this is.
 * @param reason The reason for the focus event.
 * @param processId The process ID which wrote the log entry
 * @param uid The UID which wrote the log entry, special UIDs are strings instead of numbers (e.g.
 *   root)
 * @param threadId The thread which wrote the log entry
 * @param tag The type tag code of the entry
 */
class FocusEvent(
    timestamp: Timestamp,
    val window: String,
    val type: Type,
    val reason: String,
    processId: Int,
    uid: String,
    threadId: Int
) : Event(timestamp, processId, uid, threadId, INPUT_FOCUS_TAG) {
    enum class Type {
        GAINED,
        LOST,
        REQUESTED
    }

    override fun toString(): String {
        return "$timestamp: Focus ${type.name} $window Reason=$reason"
    }

    fun hasFocus(): Boolean {
        return this.type == Type.GAINED
    }

    companion object {
        fun from(
            timestamp: Timestamp,
            processId: Int,
            uid: String,
            threadId: Int,
            data: Collection<String>
        ) =
            FocusEvent(
                timestamp,
                getWindowFromData(data.first()),
                getFocusFromData(data.first()),
                data.drop(1).first().removePrefix("reason="),
                processId,
                uid,
                threadId
            )

        private fun getWindowFromData(data: String): String {
            var expectedWhiteSpace = 2
            return data.dropWhile { !it.isWhitespace() || --expectedWhiteSpace > 0 }.drop(1)
        }

        private fun getFocusFromData(data: String): Type {
            return when {
                data.contains(" entering ") -> Type.GAINED
                data.contains(" leaving ") -> Type.LOST
                else -> Type.REQUESTED
            }
        }

        const val INPUT_FOCUS_TAG = "input_focus"
    }
}
