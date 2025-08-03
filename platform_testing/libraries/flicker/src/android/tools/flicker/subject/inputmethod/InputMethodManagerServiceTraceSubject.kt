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

package android.tools.flicker.subject.inputmethod

import android.tools.flicker.subject.FlickerTraceSubject
import android.tools.io.Reader
import android.tools.traces.inputmethod.InputMethodManagerServiceEntry
import android.tools.traces.inputmethod.InputMethodManagerServiceTrace

/**
 * Truth subject for [InputMethodManagerServiceTrace] objects, used to make assertions over
 * behaviors that occur throughout a whole trace
 *
 * Example:
 * ```
 *  val trace = InputMethodManagerServiceTraceParser.parseFromTrace(myTraceFile)
 *  val subject = InputMethodManagerServiceTraceSubject(trace) {
 *      check("Custom check") { myCustomAssertion(this) }
 *  }
 * ```
 */
class InputMethodManagerServiceTraceSubject
private constructor(
    val trace: InputMethodManagerServiceTrace,
    override val reader: Reader? = null
) :
    FlickerTraceSubject<InputMethodManagerServiceEntrySubject>(),
    IInputMethodManagerServiceSubject<InputMethodManagerServiceTraceSubject> {

    override val subjects by lazy {
        trace.entries.map { InputMethodManagerServiceEntrySubject(it, trace, reader) }
    }

    /** {@inheritDoc} */
    override fun then(): InputMethodManagerServiceTraceSubject = apply { super.then() }

    /** {@inheritDoc} */
    override fun isEmpty(): InputMethodManagerServiceTraceSubject = apply {
        check { "InputMethodManagerServiceTrace" }.that(trace.entries.size).isEqual(0)
    }

    /** {@inheritDoc} */
    override fun isNotEmpty(): InputMethodManagerServiceTraceSubject = apply {
        check { "InputMethodManagerServiceTrace" }.that(trace.entries.size).isGreater(0)
    }

    /** Executes a custom [assertion] on the current subject */
    operator fun invoke(
        name: String,
        isOptional: Boolean = false,
        assertion: (InputMethodManagerServiceEntrySubject) -> Unit
    ): InputMethodManagerServiceTraceSubject = apply { addAssertion(name, isOptional, assertion) }

    /**
     * @return List of [InputMethodManagerServiceEntrySubject]s matching [predicate] in the order
     *   they appear in the trace
     */
    fun imeClientEntriesThat(
        predicate: (InputMethodManagerServiceEntry) -> Boolean
    ): List<InputMethodManagerServiceEntrySubject> = subjects.filter { predicate(it.entry) }
}
