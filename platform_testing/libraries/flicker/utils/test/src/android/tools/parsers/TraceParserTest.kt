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

package android.tools.parsers

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.MockWindowManagerTraceBuilder
import android.tools.utils.MockWindowStateBuilder
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Test

/** Tests for [AbstractTraceParser] (for trace slicing) */
class TraceParserTest {
    @Test
    fun canSliceWithAllBefore() {
        testSliceUsingElapsedTimestamp(
            Timestamps.min().elapsedNanos,
            mockTraceForSliceTests.entries.first().timestamp.elapsedNanos - 1,
            listOf<Long>()
        )
    }

    @Test
    fun canSliceWithAllAfter() {
        val from = mockTraceForSliceTests.entries.last().elapsedTimestamp + 5
        val to = mockTraceForSliceTests.entries.last().elapsedTimestamp + 20
        val splitLayersTraceWithoutInitialEntry =
            MockTraceParser(mockTraceForSliceTests)
                .parse(
                    mockTraceForSliceTests,
                    Timestamps.from(elapsedNanos = from),
                    Timestamps.from(elapsedNanos = to),
                    addInitialEntry = false
                )
        Truth.assertThat(splitLayersTraceWithoutInitialEntry.entries).isEmpty()

        val splitLayersTraceWithInitialEntry =
            MockTraceParser(mockTraceForSliceTests)
                .parse(
                    mockTraceForSliceTests,
                    Timestamps.from(elapsedNanos = from),
                    Timestamps.from(elapsedNanos = to),
                    addInitialEntry = true
                )
        Truth.assertThat(splitLayersTraceWithInitialEntry.entries).hasSize(1)
        Truth.assertThat(splitLayersTraceWithInitialEntry.entries.first().timestamp)
            .isEqualTo(mockTraceForSliceTests.entries.last().timestamp)
    }

    @Test
    fun canSliceInMiddle() {
        testSliceUsingElapsedTimestamp(15L, 25L, listOf(15L, 18L, 25L))
    }

    @Test
    fun canSliceFromBeforeFirstEntryToMiddle() {
        testSliceUsingElapsedTimestamp(
            mockTraceForSliceTests.entries.first().timestamp.elapsedNanos - 1,
            27L,
            listOf(5L, 8L, 15L, 18L, 25L, 27L)
        )
    }

    @Test
    fun canSliceFromMiddleToAfterLastEntry() {
        testSliceUsingElapsedTimestamp(
            18L,
            mockTraceForSliceTests.entries.last().timestamp.elapsedNanos + 5,
            listOf(18L, 25L, 27L, 30L)
        )
    }

    @Test
    fun canSliceFromBeforeToAfterLastEntry() {
        testSliceUsingElapsedTimestamp(
            mockTraceForSliceTests.entries.first().timestamp.elapsedNanos - 1,
            mockTraceForSliceTests.entries.last().timestamp.elapsedNanos + 1,
            mockTraceForSliceTests.entries.map { it.timestamp }
        )
    }

    @Test
    fun canSliceFromExactStartToAfterLastEntry() {
        testSliceUsingElapsedTimestamp(
            mockTraceForSliceTests.entries.first().timestamp,
            mockTraceForSliceTests.entries.last().timestamp.elapsedNanos + 1,
            mockTraceForSliceTests.entries.map { it.timestamp }
        )
    }

    @Test
    fun canSliceFromExactStartToExactEnd() {
        testSliceUsingElapsedTimestamp(
            mockTraceForSliceTests.entries.first().timestamp,
            mockTraceForSliceTests.entries.last().timestamp,
            mockTraceForSliceTests.entries.map { it.timestamp }
        )
    }

    @Test
    fun canSliceFromExactStartToMiddle() {
        testSliceUsingElapsedTimestamp(
            mockTraceForSliceTests.entries.first().timestamp,
            18L,
            listOf(5L, 8L, 15L, 18L)
        )
    }

    @Test
    fun canSliceFromMiddleToExactEnd() {
        testSliceUsingElapsedTimestamp(
            18L,
            mockTraceForSliceTests.entries.last().timestamp,
            listOf(18L, 25L, 27L, 30L)
        )
    }

    @Test
    fun canSliceFromBeforeToExactEnd() {
        testSliceUsingElapsedTimestamp(
            mockTraceForSliceTests.entries.first().timestamp.elapsedNanos - 1,
            mockTraceForSliceTests.entries.last().timestamp,
            mockTraceForSliceTests.entries.map { it.timestamp }
        )
    }

    @Test
    fun canSliceSameStartAndEnd() {
        testSliceUsingElapsedTimestamp(15L, 15L, listOf(15L))
    }

    @JvmName("testSliceUsingElapsedTimestamp1")
    private fun testSliceUsingElapsedTimestamp(from: Long, to: Long, expected: List<Timestamp>) {
        return testSliceUsingElapsedTimestamp(from, to, expected.map { it.elapsedNanos })
    }

    @JvmName("testSliceUsingElapsedTimestamp1")
    private fun testSliceUsingElapsedTimestamp(
        from: Timestamp?,
        to: Long,
        expected: List<Timestamp>
    ) {
        return testSliceUsingElapsedTimestamp(from, to, expected.map { it.elapsedNanos })
    }

    private fun testSliceUsingElapsedTimestamp(
        from: Long,
        to: Timestamp?,
        expected: List<Timestamp>
    ) {
        return testSliceUsingElapsedTimestamp(from, to, expected.map { it.elapsedNanos })
    }

    @JvmName("testSliceUsingElapsedTimestamp1")
    private fun testSliceUsingElapsedTimestamp(from: Long, to: Timestamp?, expected: List<Long>) {
        return testSliceUsingElapsedTimestamp(
            from,
            to?.elapsedNanos ?: error("missing elapsed timestamp"),
            expected
        )
    }

    @JvmName("testSliceUsingElapsedTimestamp2")
    private fun testSliceUsingElapsedTimestamp(
        from: Timestamp?,
        to: Timestamp?,
        expected: List<Timestamp>
    ) {
        return testSliceUsingElapsedTimestamp(
            from?.elapsedNanos ?: error("missing elapsed timestamp"),
            to?.elapsedNanos ?: error("missing elapsed timestamp"),
            expected.map { it.elapsedNanos }
        )
    }

    private fun testSliceUsingElapsedTimestamp(from: Timestamp?, to: Long, expected: List<Long>) {
        return testSliceUsingElapsedTimestamp(
            from?.elapsedNanos ?: error("missing elapsed timestamp"),
            to,
            expected
        )
    }

    private fun testSliceUsingElapsedTimestamp(from: Long, to: Long, expected: List<Long>) {
        require(from <= to) { "`from` not before `to`" }
        val fromBefore = from < mockTraceForSliceTests.entries.first().timestamp.elapsedNanos
        val fromAfter = from < mockTraceForSliceTests.entries.first().timestamp.elapsedNanos

        val toBefore = to < mockTraceForSliceTests.entries.first().timestamp.elapsedNanos
        val toAfter = mockTraceForSliceTests.entries.last().timestamp.elapsedNanos < to

        require(
            fromBefore ||
                fromAfter ||
                mockTraceForSliceTests.entries.map { it.timestamp.elapsedNanos }.contains(from)
        ) {
            "`from` need to be in the trace or before or after all entries"
        }
        require(
            toBefore ||
                toAfter ||
                mockTraceForSliceTests.entries.map { it.timestamp.elapsedNanos }.contains(to)
        ) {
            "`to` need to be in the trace or before or after all entries"
        }

        testSliceWithOutInitialEntry(from, to, expected)
        if (!fromAfter) {
            testSliceWithOutInitialEntry(from - 1, to, expected)
            testSliceWithOutInitialEntry(from - 1, to + 1, expected)
        }
        if (!toBefore) {
            testSliceWithOutInitialEntry(from, to + 1, expected)
        }

        testSliceWithInitialEntry(from, to, expected)
        if (!fromBefore) {
            if (from < to) {
                testSliceWithInitialEntry(from + 1, to, expected)
            }
            testSliceWithInitialEntry(from + 1, to + 1, expected)
        }
        if (!toBefore) {
            testSliceWithInitialEntry(from, to + 1, expected)
        }
    }

    private fun testSliceWithOutInitialEntry(from: Long, to: Long, expected: List<Long>) {
        val splitLayersTrace =
            MockTraceParser(mockTraceForSliceTests)
                .parse(
                    mockTraceForSliceTests,
                    Timestamps.from(elapsedNanos = from),
                    Timestamps.from(elapsedNanos = to),
                    addInitialEntry = false
                )
        Truth.assertThat(splitLayersTrace.entries.map { it.timestamp.elapsedNanos })
            .isEqualTo(expected)
    }

    private fun testSliceWithInitialEntry(from: Long, to: Long, expected: List<Long>) {
        val splitLayersTraceWithStartEntry =
            MockTraceParser(mockTraceForSliceTests)
                .parse(
                    mockTraceForSliceTests,
                    Timestamps.from(elapsedNanos = from),
                    Timestamps.from(elapsedNanos = to),
                    addInitialEntry = true
                )
        Truth.assertThat(splitLayersTraceWithStartEntry.entries.map { it.timestamp.elapsedNanos })
            .isEqualTo(expected)
    }

    companion object {
        val mockTraceForSliceTests =
            MockWindowManagerTraceBuilder(
                    entries =
                        mutableListOf(
                            MockWindowStateBuilder(timestamp = 5),
                            MockWindowStateBuilder(timestamp = 8),
                            MockWindowStateBuilder(timestamp = 15),
                            MockWindowStateBuilder(timestamp = 18),
                            MockWindowStateBuilder(timestamp = 25),
                            MockWindowStateBuilder(timestamp = 27),
                            MockWindowStateBuilder(timestamp = 30),
                        )
                )
                .build()

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
