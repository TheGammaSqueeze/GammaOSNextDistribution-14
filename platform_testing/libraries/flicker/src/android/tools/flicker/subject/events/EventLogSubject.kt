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

package android.tools.flicker.subject.events

import android.tools.Timestamps
import android.tools.flicker.assertions.Fact
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.exceptions.ExceptionMessageBuilder
import android.tools.flicker.subject.exceptions.IncorrectFocusException
import android.tools.io.Reader
import android.tools.traces.events.EventLog
import android.tools.traces.events.FocusEvent

/** Truth subject for [FocusEvent] objects. */
class EventLogSubject(val eventLog: EventLog, override val reader: Reader) : FlickerSubject() {
    override val timestamp = eventLog.entries.firstOrNull()?.timestamp ?: Timestamps.empty()

    private val _focusChanges by lazy {
        val focusList = mutableListOf<FocusEvent>()
        eventLog.focusEvents.firstOrNull { !it.hasFocus() }?.let { focusList.add(it) }
        focusList + eventLog.focusEvents.filter { it.hasFocus() }
    }

    private val exceptionMessageBuilder
        get() = ExceptionMessageBuilder().forSubject(this)

    fun focusChanges(vararg windows: String) = apply {
        if (windows.isNotEmpty()) {
            val focusChanges =
                _focusChanges.dropWhile { !it.window.contains(windows.first()) }.take(windows.size)

            val builder =
                exceptionMessageBuilder
                    .setExpected(windows.joinToString(", "))
                    .setActual(focusChanges)

            if (focusChanges.isEmpty()) {
                val errorMsgBuilder = builder.setMessage("Focus did not change")
                throw IncorrectFocusException(errorMsgBuilder)
            }

            val actual = focusChanges.map { Fact("Focus change", it) }
            builder.setActual(actual)

            val success =
                windows.size <= focusChanges.size &&
                    focusChanges.zip(windows).all { (focus, search) ->
                        focus.window.contains(search)
                    }

            if (!success) {
                val errorMsgBuilder = builder.setMessage("Incorrect focus change")
                throw IncorrectFocusException(errorMsgBuilder)
            }
        }
    }

    fun focusDoesNotChange() = apply {
        if (_focusChanges.isNotEmpty()) {
            val actual = _focusChanges.map { Fact("Focus change", it) }
            val errorMsgBuilder =
                exceptionMessageBuilder
                    .setMessage("Focus changes")
                    .setExpected("")
                    .setActual(actual)
            throw IncorrectFocusException(errorMsgBuilder)
        }
    }
}
