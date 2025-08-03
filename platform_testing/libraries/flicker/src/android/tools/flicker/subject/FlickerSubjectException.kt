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

package android.tools.flicker.subject

import android.tools.Timestamp
import android.tools.flicker.assertions.Fact

/** Exception thrown by flicker subjects */
class FlickerSubjectException(
    timestamp: Timestamp,
    val facts: List<Fact>,
    override val cause: Throwable? = null
) : AssertionError() {
    override val message = buildString {
        appendLine(errorType)

        appendLine()
        appendLine("What? ")
        cause?.message?.split("\n")?.forEach {
            appendLine()
            appendLine(it.prependIndent("\t"))
        }

        appendLine()
        appendLine("Where?")
        appendLine(timestamp.toString().prependIndent("\t"))

        appendLine()
        appendLine("Facts")
        facts.forEach { appendLine(it.toString().prependIndent("\t")) }
    }

    private val errorType: String =
        if (cause == null) "Flicker assertion error" else "Unknown error"
}
