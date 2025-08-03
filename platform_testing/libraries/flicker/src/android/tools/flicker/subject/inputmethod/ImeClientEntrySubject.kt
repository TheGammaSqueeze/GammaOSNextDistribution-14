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

import android.tools.flicker.subject.FlickerSubject
import android.tools.io.Reader
import android.tools.traces.inputmethod.ImeClientEntry
import android.tools.traces.inputmethod.ImeClientTrace

/**
 * Truth subject for [ImeClientEntry] objects, used to make assertions over behaviors that occur on
 * a single ImeClientEntry state.
 *
 * Example:
 * ```
 *  val trace = ImeClientTraceParser.parseFromTrace(myTraceFile)
 *  val subject = ImeClientTraceSubject(trace).first()
 *      ...
 *      .invoke { myCustomAssertion(this) }
 *  ```
 */
class ImeClientEntrySubject(
    val entry: ImeClientEntry,
    val trace: ImeClientTrace?,
    override val reader: Reader? = null
) : FlickerSubject(), IImeClientSubject<ImeClientEntrySubject> {
    override val timestamp = entry.timestamp

    /** Executes a custom [assertion] on the current subject */
    operator fun invoke(assertion: (ImeClientEntry) -> Unit): ImeClientEntrySubject = apply {
        assertion(this.entry)
    }

    /** {@inheritDoc} */
    override fun isEmpty(): ImeClientEntrySubject = apply {
        check { "ImeClientEntry" }.that(entry).isNull()
    }

    /** {@inheritDoc} */
    override fun isNotEmpty(): ImeClientEntrySubject = apply {
        check { "ImeClientEntry" }.that(entry).isNotNull()
    }

    override fun toString(): String {
        return "ImeClientEntrySubject($entry)"
    }
}
