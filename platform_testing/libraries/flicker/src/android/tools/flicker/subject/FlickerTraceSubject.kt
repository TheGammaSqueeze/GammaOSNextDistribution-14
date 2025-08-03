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

import android.tools.Timestamps
import android.tools.flicker.assertions.AssertionsChecker
import android.tools.flicker.subject.exceptions.ExceptionMessageBuilder
import android.tools.flicker.subject.exceptions.SubjectAssertionError

/** Base subject for flicker trace assertions */
abstract class FlickerTraceSubject<EntrySubject : FlickerSubject> : FlickerSubject() {
    override val timestamp
        get() = subjects.firstOrNull()?.timestamp ?: Timestamps.empty()

    protected val assertionsChecker = AssertionsChecker<EntrySubject>()
    private var newAssertionBlock = true

    abstract val subjects: List<EntrySubject>

    fun hasAssertions() = !assertionsChecker.isEmpty()

    /**
     * Adds a new assertion block (if preceded by [then]) or appends an assertion to the latest
     * existing assertion block
     *
     * @param name Assertion name
     * @param isOptional If this assertion is optional or must pass
     */
    protected fun addAssertion(
        name: String,
        isOptional: Boolean = false,
        assertion: (EntrySubject) -> Unit
    ) {
        if (newAssertionBlock) {
            assertionsChecker.add(name, isOptional, assertion)
        } else {
            assertionsChecker.append(name, isOptional, assertion)
        }
        newAssertionBlock = false
    }

    /** Run the assertions for all trace entries */
    fun forAllEntries() {
        require(subjects.isNotEmpty()) { "Trace is empty" }
        assertionsChecker.test(subjects)
    }

    /** User-defined entry point for the first trace entry */
    fun first(): EntrySubject = subjects.firstOrNull() ?: error("Trace is empty")

    /** User-defined entry point for the last trace entry */
    fun last(): EntrySubject = subjects.lastOrNull() ?: error("Trace is empty")

    /**
     * Signal that the last assertion set is complete. The next assertion added will start a new set
     * of assertions.
     *
     * E.g.: checkA().then().checkB()
     *
     * Will produce two sets of assertions (checkA) and (checkB) and checkB will only be checked
     * after checkA passes.
     */
    open fun then(): FlickerTraceSubject<EntrySubject> = apply { startAssertionBlock() }

    /**
     * Ignores the first entries in the trace, until the first assertion passes. If it reaches the
     * end of the trace without passing any assertion, return a failure with the name/reason from
     * the first assertion
     *
     * @return
     */
    open fun skipUntilFirstAssertion(): FlickerTraceSubject<EntrySubject> = apply {
        assertionsChecker.skipUntilFirstAssertion()
    }

    /**
     * Signal that the last assertion set is complete. The next assertion added will start a new set
     * of assertions.
     *
     * E.g.: checkA().then().checkB()
     *
     * Will produce two sets of assertions (checkA) and (checkB) and checkB will only be checked
     * after checkA passes.
     */
    private fun startAssertionBlock() {
        newAssertionBlock = true
    }

    /**
     * Checks whether all the trace entries on the list are visible for more than one consecutive
     * entry
     *
     * Ignore the first and last trace subjects. This is necessary because WM and SF traces log
     * entries only when a change occurs.
     *
     * If the trace starts immediately before an animation or if it stops immediately after one, the
     * first and last entry may contain elements that are visible only for that entry. Those
     * elements, however, are not flickers, since they existed on the screen before or after the
     * test.
     *
     * @param [visibleEntriesProvider] a list of all the entries with their name and index
     */
    protected fun visibleEntriesShownMoreThanOneConsecutiveTime(
        visibleEntriesProvider: (EntrySubject) -> Set<String>
    ) {
        if (subjects.isEmpty()) {
            return
        }
        // Duplicate the first and last trace subjects to prevent them from triggering failures
        // since WM and SF traces log entries only when a change occurs
        val firstState = subjects.first()
        val lastState = subjects.last()
        val subjects =
            subjects.toMutableList().also {
                it.add(lastState)
                it.add(0, firstState)
            }
        var lastVisible = visibleEntriesProvider(subjects.first())
        val lastNew = lastVisible.toMutableSet()

        // first subject was already taken
        subjects.drop(1).forEachIndexed { index, entrySubject ->
            val currentVisible = visibleEntriesProvider(entrySubject)
            val newVisible = currentVisible.filter { it !in lastVisible }
            lastNew.removeAll(currentVisible)

            if (lastNew.isNotEmpty()) {
                val errorMsgBuilder =
                    ExceptionMessageBuilder()
                        .forSubject(subjects[index])
                        .setMessage("$lastNew is not visible for 2 entries")
                throw SubjectAssertionError(errorMsgBuilder)
            }
            lastNew.addAll(newVisible)
            lastVisible = currentVisible
        }

        if (lastNew.isNotEmpty()) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(subjects.last())
                    .setMessage("$lastNew is not visible for 2 entries")
            throw SubjectAssertionError(errorMsgBuilder)
        }
    }

    override fun toString(): String =
        "${this::class.simpleName}" +
            "(${subjects.firstOrNull()?.timestamp ?: 0},${subjects.lastOrNull()?.timestamp ?: 0})"
}
