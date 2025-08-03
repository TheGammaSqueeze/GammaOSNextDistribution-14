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
import android.health.connect.datatypes.SpeedRecord
import android.health.connect.datatypes.units.Velocity
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedSessionDetail
import com.android.healthconnect.controller.dataentries.formatters.SpeedFormatter
import com.android.healthconnect.controller.dataentries.units.DistanceUnit
import com.android.healthconnect.controller.dataentries.units.DistanceUnit.KILOMETERS
import com.android.healthconnect.controller.dataentries.units.DistanceUnit.MILES
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
class SpeedFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: SpeedFormatter
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
        val record = getSpeedRecord(listOf())
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("No data")
        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("No data")
    }

    @Test
    fun formatValue_oneEntry_metricUnit_returnsCorrectValue() = runBlocking {
        preferences.setDistanceUnit(KILOMETERS)

        val record = getSpeedRecord(listOf(12.0))
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("43.2 km/h")
    }

    @Test
    fun formatValue_multipleEntries_metricUnit_returnsAverageValue() = runBlocking {
        preferences.setDistanceUnit(KILOMETERS)

        val record = getSpeedRecord(listOf(4.0, 13.5, 9.3))
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("32.16 km/h")
    }

    @Test
    fun formatValue_oneEntry_imperialUnit_returnsCorrectValue() = runBlocking {
        preferences.setDistanceUnit(MILES)

        val record = getSpeedRecord(listOf(12.0))
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("26.843 mph")
    }

    @Test
    fun formatValue_multipleEntries_imperialUnit_returnsAverageValue() = runBlocking {
        preferences.setDistanceUnit(MILES)

        val record = getSpeedRecord(listOf(4.0, 13.5, 9.3))
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("19.983 mph")
    }

    @Test
    fun formatA11yValue_oneEntry_metricUnit_returnsCorrectValue() = runBlocking {
        preferences.setDistanceUnit(KILOMETERS)

        val record = getSpeedRecord(listOf(12.0))
        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("43.2 kilometres per hour")
    }

    @Test
    fun formatA11yValue_multipleEntries_metricUnit_returnsAverageValue() = runBlocking {
        preferences.setDistanceUnit(DistanceUnit.KILOMETERS)

        val record = getSpeedRecord(listOf(4.0, 13.5, 9.3))
        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("32.16 kilometres per hour")
    }

    @Test
    fun formatA11yValue_oneEntry_imperialUnit_returnsCorrectValue() = runBlocking {
        preferences.setDistanceUnit(MILES)

        val record = getSpeedRecord(listOf(12.0))
        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("26.843 miles per hour")
    }

    @Test
    fun formatA11yValue_multipleEntries_imperialUnit_returnsAverageValue() = runBlocking {
        preferences.setDistanceUnit(MILES)

        val record = getSpeedRecord(listOf(4.0, 13.5, 9.3))
        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("19.983 miles per hour")
    }

    @Test
    fun formatRecordDetails_emptyList_returnsEmpty() {
        val record = getSpeedRecord(listOf())
        runBlocking { assertThat(formatter.formatRecordDetails(record)).isEmpty() }
    }

    @Test
    fun formatRecordDetails_multipleEntries_withImperialUnit_returnsSampleList() = runBlocking {
        preferences.setDistanceUnit(MILES)

        val record = getSpeedRecord(listOf(4.0, 13.5))

        val result = formatter.formatRecordDetails(record)
        assertThat(result.size).isEqualTo(2)
        assertThat(result[0])
            .isEqualTo(
                FormattedSessionDetail(
                    uuid = "test_id",
                    header = "07:06",
                    headerA11y = "07:06",
                    titleA11y = "8.948 miles per hour",
                    title = "8.948 mph"))
    }

    @Test
    fun formatRecordDetails_multipleEntries_withMetricUnit__returnsSampleList() = runBlocking {
        preferences.setDistanceUnit(KILOMETERS)

        val record = getSpeedRecord(listOf(4.0, 13.5))

        val result = formatter.formatRecordDetails(record)
        assertThat(result.size).isEqualTo(2)
        assertThat(result[0])
            .isEqualTo(
                FormattedSessionDetail(
                    uuid = "test_id",
                    header = "07:06",
                    headerA11y = "07:06",
                    titleA11y = "14.4 kilometres per hour",
                    title = "14.4 km/h"))
    }

    private fun getSpeedRecord(samples: List<Double>): SpeedRecord {
        return SpeedRecord.Builder(
                getMetaData(),
                NOW,
                NOW.plusSeconds(samples.size.toLong() + 1),
                samples.mapIndexed { index, value ->
                    SpeedRecord.SpeedRecordSample(
                        Velocity.fromMetersPerSecond(value), NOW.plusSeconds(index.toLong()))
                })
            .build()
    }
}
