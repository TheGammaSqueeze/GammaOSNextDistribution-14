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

package com.android.server.net

import android.util.IndentingPrintWriter
import com.android.server.net.NetworkStatsEventLogger.MAX_POLL_REASON
import com.android.server.net.NetworkStatsEventLogger.POLL_REASON_DUMPSYS
import com.android.server.net.NetworkStatsEventLogger.POLL_REASON_FORCE_UPDATE
import com.android.server.net.NetworkStatsEventLogger.POLL_REASON_GLOBAL_ALERT
import com.android.server.net.NetworkStatsEventLogger.POLL_REASON_NETWORK_STATUS_CHANGED
import com.android.server.net.NetworkStatsEventLogger.POLL_REASON_OPEN_SESSION
import com.android.server.net.NetworkStatsEventLogger.POLL_REASON_PERIODIC
import com.android.server.net.NetworkStatsEventLogger.POLL_REASON_RAT_CHANGED
import com.android.server.net.NetworkStatsEventLogger.POLL_REASON_REG_CALLBACK
import com.android.server.net.NetworkStatsEventLogger.PollEvent
import com.android.server.net.NetworkStatsEventLogger.PollEvent.pollReasonNameOf
import com.android.testutils.DevSdkIgnoreRunner
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

const val TEST_PERSIST_FLAG = 0x101

@RunWith(DevSdkIgnoreRunner::class)
class NetworkStatsEventLoggerTest {
    val logger = NetworkStatsEventLogger()
    val stringWriter = TestStringWriter()
    val pw = IndentingPrintWriter(stringWriter)

    @Test
    fun testDump_invalid() {
        // Verify it won't crash.
        logger.dump(pw)
        // Clear output buffer.
        stringWriter.getOutputAndClear()

        // Verify log invalid event throws. And nothing output in the dump.
        val invalidReasons = listOf(-1, MAX_POLL_REASON + 1)
        invalidReasons.forEach {
            assertFailsWith<IllegalArgumentException> {
                logger.logPollEvent(TEST_PERSIST_FLAG, PollEvent(it))
            }
            logger.dumpRecentPollEvents(pw)
            val output = stringWriter.getOutputAndClear()
            assertStringNotContains(output, pollReasonNameOf(it))
        }
    }

    @Test
    fun testDump_valid() {
        // Choose arbitrary set of reasons for testing.
        val loggedReasons = listOf(
            POLL_REASON_GLOBAL_ALERT,
            POLL_REASON_FORCE_UPDATE,
            POLL_REASON_DUMPSYS,
            POLL_REASON_PERIODIC,
            POLL_REASON_RAT_CHANGED
        )
        val nonLoggedReasons = listOf(
            POLL_REASON_NETWORK_STATUS_CHANGED,
            POLL_REASON_OPEN_SESSION,
            POLL_REASON_REG_CALLBACK)

        // Add some valid records.
        loggedReasons.forEach {
            logger.logPollEvent(TEST_PERSIST_FLAG, PollEvent(it))
        }

        // Collect dumps.
        logger.dumpRecentPollEvents(pw)
        val outputRecentEvents = stringWriter.getOutputAndClear()
        logger.dumpPollCountsPerReason(pw)
        val outputCountsPerReason = stringWriter.getOutputAndClear()

        // Verify the output contains at least necessary information.
        loggedReasons.forEach {
            // Verify all events are shown in the recent event dump.
            val eventString = PollEvent(it).toString()
            assertStringContains(outputRecentEvents, TEST_PERSIST_FLAG.toString())
            assertStringContains(eventString, pollReasonNameOf(it))
            assertStringContains(outputRecentEvents, eventString)
            // Verify counts are 1 for each reason.
            assertCountForReason(outputCountsPerReason, it, 1)
        }

        // Verify the output remains untouched for other reasons.
        nonLoggedReasons.forEach {
            assertStringNotContains(outputRecentEvents, PollEvent(it).toString())
            assertCountForReason(outputCountsPerReason, it, 0)
        }
    }

    @Test
    fun testDump_maxEventLogs() {
        // Choose arbitrary reason.
        val reasonToBeTested = POLL_REASON_PERIODIC
        val repeatCount = NetworkStatsEventLogger.MAX_EVENTS_LOGS * 2

        // Collect baseline.
        logger.dumpRecentPollEvents(pw)
        val lineCountBaseLine = getLineCount(stringWriter.getOutputAndClear())

        repeat(repeatCount) {
            logger.logPollEvent(TEST_PERSIST_FLAG, PollEvent(reasonToBeTested))
        }

        // Collect dump.
        logger.dumpRecentPollEvents(pw)
        val lineCountAfterTest = getLineCount(stringWriter.getOutputAndClear())

        // Verify line count increment is limited.
        assertEquals(
            NetworkStatsEventLogger.MAX_EVENTS_LOGS,
            lineCountAfterTest - lineCountBaseLine
        )

        // Verify count per reason increased for the testing reason.
        logger.dumpPollCountsPerReason(pw)
        val outputCountsPerReason = stringWriter.getOutputAndClear()
        for (reason in 0..MAX_POLL_REASON) {
            assertCountForReason(
                outputCountsPerReason,
                reason,
                if (reason == reasonToBeTested) repeatCount else 0
            )
        }
    }

    private fun getLineCount(multilineString: String) = multilineString.lines().size

    private fun assertStringContains(got: String, want: String) {
        assertTrue(got.contains(want), "Wanted: $want, but got: $got")
    }

    private fun assertStringNotContains(got: String, unwant: String) {
        assertFalse(got.contains(unwant), "Unwanted: $unwant, but got: $got")
    }

    /**
     * Assert the reason and the expected count are at the same line.
     */
    private fun assertCountForReason(dump: String, reason: Int, expectedCount: Int) {
        // Matches strings like "GLOBAL_ALERT: 50" but not too strict since the format might change.
        val regex = Regex(pollReasonNameOf(reason) + "[^0-9]+" + expectedCount)
        assertEquals(
            1,
            regex.findAll(dump).count(),
            "Unexpected output: $dump " + " for reason: " + pollReasonNameOf(reason)
        )
    }

    class TestStringWriter : StringWriter() {
        fun getOutputAndClear() = toString().also { buffer.setLength(0) }
    }
}
