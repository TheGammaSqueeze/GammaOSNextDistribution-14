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

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.TraceEntry
import android.tools.flicker.subject.FlickerSubject
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.assertFail
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [AssertionsChecker] tests. To run this test: `atest
 * FlickerLibTest:AssertionsCheckerTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssertionsCheckerTest {
    @Test
    fun emptyRangePasses() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData42") { it.isData42() }
        checker.test(emptyList())
    }

    @Test
    fun canCheckChangingAssertions() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData42") { it.isData42() }
        checker.add("isData0") { it.isData0() }
        checker.test(getTestEntries(42, 0, 0, 0, 0))
    }

    @Test
    fun canCheckChangingAssertionsIgnoreOptionalStart() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.add("isData42") { it.isData42() }
        checker.add("isData0") { it.isData0() }
        checker.test(getTestEntries(42, 0, 0, 0, 0))
    }

    @Test
    fun canCheckChangingAssertionsIgnoreOptionalEnd() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData42") { it.isData42() }
        checker.add("isData0") { it.isData0() }
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.test(getTestEntries(42, 0, 0, 0, 0))
    }

    @Test
    fun canCheckChangingAssertionsIgnoreOptionalMiddle() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData42") { it.isData42() }
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.add("isData0") { it.isData0() }
        checker.test(getTestEntries(42, 0, 0, 0, 0))
    }

    @Test
    fun canCheckChangingAssertionsIgnoreOptionalMultiple() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.add("isData42") { it.isData42() }
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.add("isData0") { it.isData0() }
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.add("isData1", isOptional = true) { it.isData1() }
        checker.test(getTestEntries(42, 0, 0, 0, 0))
    }

    @Test
    fun canCheckChangingAssertionsWithNoAssertions() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.test(getTestEntries(42, 0, 0, 0, 0))
    }

    @Test
    fun canCheckChangingAssertionsWithSingleAssertion() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData42") { it.isData42() }
        checker.test(getTestEntries(42, 42, 42, 42, 42))
    }

    @Test
    fun canFailCheckChangingAssertionsIfStartingAssertionFails() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData42") { it.isData42() }
        checker.add("isData0") { it.isData0() }
        assertFail("data is 42") { checker.test(getTestEntries(0, 0, 0, 0, 0)) }
    }

    @Test
    fun canCheckChangingAssertionsSkipUntilFirstSuccess() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.skipUntilFirstAssertion()
        checker.add("isData42") { it.isData42() }
        checker.add("isData0") { it.isData0() }
        checker.test(getTestEntries(0, 42, 0, 0, 0))
    }

    @Test
    fun canFailCheckChangingAssertionsIfStartingAssertionAlwaysPasses() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData42") { it.isData42() }
        checker.add("isData0") { it.isData0() }
        assertFail("never failed: isData42") { checker.test(getTestEntries(42, 42, 42, 42, 42)) }
    }

    @Test
    fun canFailCheckChangingAssertionsIfUsingCompoundAssertion() {
        val checker = AssertionsChecker<SimpleEntrySubject>()
        checker.add("isData42/0") { it.isData42().isData0() }
        assertFail("data is 42") { checker.test(getTestEntries(0, 0, 0, 0, 0)) }
    }

    private class SimpleEntrySubject(private val entry: SimpleEntry) : FlickerSubject() {
        override val timestamp = Timestamps.empty()

        fun isData42() = apply { check { "data is 42" }.that(entry.mData).isEqual(42) }

        fun isData0() = apply { check { "data is 0" }.that(entry.mData).isEqual(0) }

        fun isData1() = apply { check { "data is 1" }.that(entry.mData).isEqual(1) }

        companion object {
            /** User-defined entry point */
            @JvmStatic
            fun assertThat(entry: SimpleEntry): SimpleEntrySubject {
                return SimpleEntrySubject(entry)
            }
        }
    }

    data class SimpleEntry(override val timestamp: Timestamp, val mData: Int) : TraceEntry

    companion object {
        /**
         * Returns a list of SimpleEntry objects with `data` and incremental timestamps starting
         * at 0.
         */
        private fun getTestEntries(vararg data: Int): List<SimpleEntrySubject> =
            data.indices.map {
                SimpleEntrySubject(
                    SimpleEntry(Timestamps.from(elapsedNanos = it.toLong()), data[it])
                )
            }
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
