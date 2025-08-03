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
import android.health.connect.datatypes.CyclingPedalingCadenceRecord
import android.health.connect.datatypes.CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.CyclingPedalingCadenceFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class CyclingPedalingCadenceFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: CyclingPedalingCadenceFormatter
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
    fun formatValue_noData() = runBlocking {
        val record = getRecord(listOf())

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("No data")
        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("No data")
    }

    @Test
    fun formatValue_onePoint() = runBlocking {
        val record = getRecord(listOf(40.0))

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("40 rpm")
    }

    @Test
    fun formatA11yValue_onePoint() = runBlocking {
        val record = getRecord(listOf(40.0))

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("40 revolutions per minute")
    }

    @Test
    fun formatValue_multiplePoints() = runBlocking {
        val record = getRecord(listOf(23.0, 40.0, 43.0))

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("23 rpm - 43 rpm")
    }

    @Test
    fun formatA11yValue_multiplePoints() = runBlocking {
        val record = getRecord(listOf(23.0, 40.0, 43.0))

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("from 23 revolutions per minute to 43 revolutions per minute")
    }

    @Test
    fun formatRecordDetails_emptySamples_returnsEmptyList() = runBlocking {
        val record = getRecord(listOf())

        assertThat(formatter.formatRecordDetails(record)).isEmpty()
    }

    @Test
    fun formatRecordDetails_multipleSamples_returnsFormattedList() = runBlocking {
        val record = getRecord(listOf(23.0, 40.0))

        val result = formatter.formatRecordDetails(record)
        assertThat(result.size).isEqualTo(2)
        assertThat(result[0])
            .isEqualTo(
                FormattedEntry.FormattedSessionDetail(
                    uuid = "test_id",
                    header = "07:06",
                    headerA11y = "07:06",
                    title = "23 rpm",
                    titleA11y = "23 revolutions per minute"))
    }

    private fun getRecord(samples: List<Double>): CyclingPedalingCadenceRecord {
        return CyclingPedalingCadenceRecord.Builder(
                getMetaData(),
                NOW,
                NOW.plusSeconds(100),
                samples.mapIndexed { index, d ->
                    CyclingPedalingCadenceRecordSample(d, NOW.plusSeconds(index.toLong()))
                })
            .build()
    }
}
