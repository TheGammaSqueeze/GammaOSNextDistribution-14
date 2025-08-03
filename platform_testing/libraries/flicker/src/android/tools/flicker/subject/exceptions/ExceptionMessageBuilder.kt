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

package android.tools.flicker.subject.exceptions

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.flicker.assertions.Fact
import android.tools.flicker.subject.FlickerSubject
import android.tools.io.Reader

/** Class to build flicker exception messages */
class ExceptionMessageBuilder {
    private var timestamp = Timestamps.empty()
    private var expected = ""
    private var actual = mutableListOf<String>()
    private var headerDescription = ""
    private var extraDescription = mutableListOf<Fact>()

    fun forSubject(value: FlickerSubject) = apply {
        setTimestamp(value.timestamp)
        addExtraDescription(value.selfFacts)
        val reader = value.reader
        if (reader != null) {
            setReader(reader)
        }
    }

    fun forInvalidElement(elementName: String, expectElementExists: Boolean) =
        setMessage("$elementName should ${if (expectElementExists) "" else "not "}exist")
            .setExpected(elementName)

    fun forIncorrectVisibility(elementName: String, expectElementVisible: Boolean) =
        setMessage("$elementName should ${if (expectElementVisible) "" else "not "}be visible")
            .setExpected(elementName)

    fun forInvalidProperty(propertyName: String) = setMessage("Incorrect value for $propertyName")

    fun forIncorrectRegion(propertyName: String) = setMessage("Incorrect $propertyName")

    fun setTimestamp(value: Timestamp) = apply { timestamp = value }

    fun setMessage(value: String) = apply { headerDescription = value }

    fun setExpected(value: Any?) = apply { expected = value?.toString() ?: "null" }

    fun setActual(value: Collection<String>) = apply { actual.addAll(value) }

    fun setActual(value: Any?) = setActual(listOf(value?.toString() ?: "null"))

    fun setReader(value: Reader) = addExtraDescription("Artifact", value.artifact)

    fun addExtraDescription(key: String, value: Any?) = addExtraDescription(Fact(key, value))

    fun addExtraDescription(vararg value: Fact) = addExtraDescription(value.toList())

    fun addExtraDescription(value: Collection<Fact>) = apply { extraDescription.addAll(value) }

    fun build(): String = buildString {
        if (headerDescription.isNotEmpty()) {
            appendLine(headerDescription)
            appendLine()
        }

        if (!timestamp.isEmpty) {
            appendLine("Where?")
            appendLine(timestamp.toString().prependIndent("\t"))
        }

        if (expected.isNotEmpty() || actual.isNotEmpty()) {
            appendLine()
            appendLine("What?")
        }

        if (expected.isNotEmpty()) {
            append("Expected: ".prependIndent("\t"))
            appendLine(expected)
        }

        actual
            .filter { it.isNotEmpty() }
            .forEach {
                append("Actual: ".prependIndent("\t"))
                appendLine(it)
            }

        if (extraDescription.isNotEmpty()) {
            appendLine()
            appendLine("Other information")
            extraDescription.forEach { appendLine(it.toString().prependIndent("\t")) }
        }

        appendLine()
        appendLine("Check the test run artifacts for trace files")
    }
}
