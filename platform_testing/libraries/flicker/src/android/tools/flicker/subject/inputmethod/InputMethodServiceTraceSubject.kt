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
import android.tools.traces.inputmethod.InputMethodServiceEntry
import android.tools.traces.inputmethod.InputMethodServiceTrace

/**
 * Truth subject for [InputMethodServiceTrace] objects, used to make assertions over behaviors that
 * occur throughout a whole trace
 *
 * Example: val trace = InputMethodServiceTraceParser.parseFromTrace(myTraceFile) val subject =
 * InputMethodServiceTraceSubject.assertThat(trace) Example2: val trace =
 * InputMethodServiceTraceParser.parseFromTrace(myTraceFile) val subject =
 * InputMethodServiceTraceSubject.assertThat(trace) { check("Custom check") {
 * myCustomAssertion(this) } }
 */
class InputMethodServiceTraceSubject
private constructor(val trace: InputMethodServiceTrace, override val reader: Reader? = null) :
    FlickerTraceSubject<InputMethodServiceEntrySubject>(),
    IInputMethodServiceSubject<InputMethodServiceTraceSubject> {

    override val subjects by lazy {
        trace.entries.map { InputMethodServiceEntrySubject(it, trace, reader) }
    }

    /** {@inheritDoc} */
    override fun then(): InputMethodServiceTraceSubject = apply { super.then() }

    /** {@inheritDoc} */
    override fun isEmpty(): InputMethodServiceTraceSubject = apply {
        check { "InputMethodServiceTrace" }.that(trace.entries.size).isEqual(0)
    }

    /** {@inheritDoc} */
    override fun isNotEmpty(): InputMethodServiceTraceSubject = apply {
        check { "InputMethodServiceTrace" }.that(trace.entries.size).isGreater(0)
    }

    /** Executes a custom [assertion] on the current subject */
    operator fun invoke(
        name: String,
        isOptional: Boolean = false,
        assertion: (InputMethodServiceEntrySubject) -> Unit
    ): InputMethodServiceTraceSubject = apply { addAssertion(name, isOptional, assertion) }

    /**
     * @return List of [InputMethodServiceEntrySubject]s matching [predicate] in the order they
     *   appear in the trace
     */
    fun imeClientEntriesThat(
        predicate: (InputMethodServiceEntry) -> Boolean
    ): List<InputMethodServiceEntrySubject> = subjects.filter { predicate(it.entry) }
}
