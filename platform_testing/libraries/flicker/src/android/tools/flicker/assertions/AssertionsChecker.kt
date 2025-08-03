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

package android.tools.flicker.assertions

import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.exceptions.ExceptionMessageBuilder
import android.tools.flicker.subject.exceptions.SubjectAssertionError

/**
 * Runs sequences of assertions on sequences of subjects.
 *
 * Starting at the first assertion and first trace entry, executes the assertions iteratively on the
 * trace until all assertions and trace entries succeed.
 *
 * @param <T> trace entry type </T>
 */
class AssertionsChecker<T : FlickerSubject> {
    private val assertions = mutableListOf<CompoundAssertion<T>>()
    private var skipUntilFirstAssertion = false

    internal fun isEmpty() = assertions.isEmpty()

    /** Add [assertion] to a new [CompoundAssertion] block. */
    fun add(name: String, isOptional: Boolean = false, assertion: (T) -> Unit) {
        assertions.add(CompoundAssertion(assertion, name, isOptional))
    }

    /** Append [assertion] to the last existing set of assertions. */
    fun append(name: String, isOptional: Boolean = false, assertion: (T) -> Unit) {
        assertions.last().add(assertion, name, isOptional)
    }

    /**
     * Steps through each trace entry checking if provided assertions are true in the order they are
     * added. Each assertion must be true for at least a single trace entry.
     *
     * This can be used to check for asserting a change in property over a trace. Such as visibility
     * for a window changes from true to false or top-most window changes from A to B and back to A
     * again.
     *
     * It is also possible to ignore failures on initial elements, until the first assertion passes,
     * this allows the trace to be recorded for longer periods, and the checks to happen only after
     * some time.
     *
     * @param entries list of entries to perform assertions on
     * @return list of failed assertion results
     */
    fun test(entries: List<T>) {
        if (assertions.isEmpty() || entries.isEmpty()) {
            return
        }

        var entryIndex = 0
        var assertionIndex = 0
        var lastPassedAssertionIndex = -1
        val assertionTrace = mutableListOf<String>()
        while (assertionIndex < assertions.size && entryIndex < entries.size) {
            val currentAssertion = assertions[assertionIndex]
            val currEntry = entries[entryIndex]
            try {
                val log =
                    "${assertionIndex + 1}/${assertions.size}:[${currentAssertion.name}]\t" +
                        "Entry: ${entryIndex + 1}/${entries.size} $currEntry"
                assertionTrace.add(log)
                currentAssertion.invoke(currEntry)
                lastPassedAssertionIndex = assertionIndex
                entryIndex++
            } catch (e: AssertionError) {
                // ignore errors at the start of the trace
                val ignoreFailure = skipUntilFirstAssertion && lastPassedAssertionIndex == -1
                if (ignoreFailure) {
                    entryIndex++
                    continue
                }
                // failure is an optional assertion, just consider it passed skip it
                if (currentAssertion.isOptional) {
                    lastPassedAssertionIndex = assertionIndex
                    assertionIndex++
                    continue
                }
                if (lastPassedAssertionIndex != assertionIndex) {
                    throw e
                }
                assertionIndex++
                if (assertionIndex == assertions.size) {
                    throw e
                }
            }
        }
        // Didn't pass any assertions
        if (lastPassedAssertionIndex == -1 && assertions.isNotEmpty()) {
            val errorMsg =
                ExceptionMessageBuilder()
                    .forSubject(entries.first())
                    .setMessage("Assertion never passed ${assertions.first()}")
                    .addExtraDescription(
                        assertions.mapIndexed { idx, assertion ->
                            Fact("Assertion$idx", assertion.toString())
                        }
                    )
            throw SubjectAssertionError(errorMsg)
        }

        val untestedAssertions = assertions.drop(assertionIndex + 1)
        if (untestedAssertions.any { !it.isOptional }) {
            val passedAssertionsFacts = assertions.take(assertionIndex).map { Fact("Passed", it) }
            val untestedAssertionsFacts = untestedAssertions.map { Fact("Untested", it) }

            val errorMsg =
                ExceptionMessageBuilder()
                    .forSubject(entries.last())
                    .setMessage(
                        "Assertion $assertionIndex never failed: ${assertions[assertionIndex]}"
                    )
                    .addExtraDescription(passedAssertionsFacts)
                    .addExtraDescription(untestedAssertionsFacts)

            throw SubjectAssertionError(errorMsg)
        }
    }

    /**
     * Ignores the first entries in the trace, until the first assertion passes. If it reaches the
     * end of the trace without passing any assertion, return a failure with the name/reason from
     * the first assertion
     */
    fun skipUntilFirstAssertion() {
        skipUntilFirstAssertion = true
    }

    fun isEqual(other: Any?): Boolean {
        if (
            other !is AssertionsChecker<*> ||
                skipUntilFirstAssertion != other.skipUntilFirstAssertion
        ) {
            return false
        }
        assertions.forEachIndexed { index, assertion ->
            if (assertion != other.assertions[index]) {
                return false
            }
        }
        return true
    }
}
