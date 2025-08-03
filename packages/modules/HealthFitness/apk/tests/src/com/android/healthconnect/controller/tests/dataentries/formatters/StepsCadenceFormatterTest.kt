/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.StepsCadenceRecord
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.StepsCadenceFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class StepsCadenceFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: StepsCadenceFormatter
    @Inject lateinit var preferences: UnitPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        hiltRule.inject()
    }

    @Test
    fun formatValue_noEntries_returnsNoData() = runBlocking {
        val record = getStepsCadence(listOf())
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("No data")
    }

    @Test
    fun formatA11yValue_noEntries_returnsNoData() = runBlocking {
        val record = getStepsCadence(listOf())
        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("No data")
    }

    @Test
    fun formatValue_returnsCorrectValue() = runBlocking {
        val record = getStepsCadence(listOf(1.0))
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("1 step/min")
    }

    @Test
    fun formatValue_oneEntry_returnsCorrectValue() = runBlocking {
        val record = getStepsCadence(listOf(10.3))
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("10.3 steps/min")
    }

    @Test
    fun formatValue_multipleEntries_returnsAverageValue() = runBlocking {
        val record = getStepsCadence(listOf(10.3, 20.1))
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("15.2 steps/min")
    }

    @Test
    fun formatA11yValue_returnsAverageValue() = runBlocking {
        val record = getStepsCadence(listOf(10.3, 20.1))
        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("15.2 steps per minute")
    }

    @Test
    fun formatRecordDetails_emptyList_returnsEmptyList() = runTest {
        val record = getStepsCadence(listOf())
        assertThat(formatter.formatRecordDetails(record)).isEmpty()
    }

    @Test
    fun formatRecordDetails_returnsFormattedList() = runTest {
        val record = getStepsCadence(listOf(10.3, 20.1))

        val result = formatter.formatRecordDetails(record)
        assertThat(result.size).isEqualTo(2)
        assertThat(result[0])
            .isEqualTo(
                FormattedEntry.FormattedSessionDetail(
                    uuid = "test_id",
                    header = "07:06",
                    headerA11y = "07:06",
                    titleA11y = "10.3 steps per minute",
                    title = "10.3 steps/min"))
    }

    private fun getStepsCadence(samples: List<Double>): StepsCadenceRecord {
        return StepsCadenceRecord.Builder(
                getMetaData(),
                NOW,
                NOW.plusSeconds(samples.size.toLong() + 1),
                samples.map { rate ->
                    StepsCadenceRecord.StepsCadenceRecordSample(rate, NOW.plusSeconds(1))
                })
            .build()
    }
}
