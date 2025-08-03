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
import android.health.connect.datatypes.HeartRateRecord
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.HeartRateFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.tests.utils.getHeartRateRecord
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
class HeartRateFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: HeartRateFormatter
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
    fun formatValue_returnsHeartRateValues() {
        val record: HeartRateRecord = getHeartRateRecord(listOf(100, 102))
        runBlocking {
            assertThat(formatter.formatValue(record, preferences)).isEqualTo("100 bpm - 102 bpm")
        }
    }

    @Test
    fun formatValue_singleSampleValue_returnsSingleHeartRateValue() {
        val record: HeartRateRecord = getHeartRateRecord(listOf(100))
        runBlocking { assertThat(formatter.formatValue(record, preferences)).isEqualTo("100 bpm") }
    }

    @Test
    fun formatA11yValue_pluralValue_returnsA11yHeartRateValues() {
        val record: HeartRateRecord = getHeartRateRecord(listOf(100, 102))
        runBlocking {
            assertThat(formatter.formatA11yValue(record, preferences))
                .isEqualTo("from 100 beats per minute to 102 beats per minute")
        }
    }

    @Test
    fun formatA11yValue_singleSampleValue_returnsA11yHeartRateValues() {
        val record: HeartRateRecord = getHeartRateRecord(listOf(1))
        runBlocking {
            assertThat(formatter.formatA11yValue(record, preferences))
                .isEqualTo("1 beat per minute")
        }
    }

    @Test
    fun formatRecordDetails_withData_returnsFormattedEntries() = runBlocking {
        val record: HeartRateRecord = getHeartRateRecord(listOf(80, 90))

        val result = formatter.formatRecordDetails(record)
        assertThat(result.size).isEqualTo(2)
        assertThat(result[0])
            .isEqualTo(
                FormattedEntry.FormattedSessionDetail(
                    uuid = "test_id",
                    header = "07:06",
                    headerA11y = "07:06",
                    titleA11y = "80 beats per minute",
                    title = "80 bpm"))
    }
}
