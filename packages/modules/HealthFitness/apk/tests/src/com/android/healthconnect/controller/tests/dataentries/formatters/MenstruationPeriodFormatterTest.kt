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
 *
 *
 */

package com.android.healthconnect.controller.tests.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.MenstruationPeriodRecord
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.MenstruationPeriodFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Duration.ofDays
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MenstruationPeriodFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: MenstruationPeriodFormatter
    @Inject lateinit var preferences: UnitPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        hiltRule.inject()
    }

    @Test
    fun format_dayOne_formatsMenstruationPeriod() = runBlocking {
        val start = NOW
        val end = NOW.plus(ofDays(5))
        val day = NOW
        val record = MenstruationPeriodRecord.Builder(getMetaData(), start, end).build()

        assertThat(formatter.format(day, record))
            .isEqualTo(
                FormattedEntry.FormattedDataEntry(
                    uuid = record.metadata.id,
                    header = TEST_APP_NAME,
                    headerA11y = TEST_APP_NAME,
                    title = "Period day 1 of 6",
                    titleA11y = "Period day 1 of 6",
                    dataType = DataType.MENSTRUATION_PERIOD,
                    startTime = start,
                    endTime = end))
    }

    @Test
    fun format_lastDay_formatsMenstruationPeriod() = runBlocking {
        val start = NOW
        val end = NOW.plus(ofDays(5))
        val record = MenstruationPeriodRecord.Builder(getMetaData(), start, end).build()

        assertThat(formatter.format(end, record))
            .isEqualTo(
                FormattedEntry.FormattedDataEntry(
                    uuid = record.metadata.id,
                    header = TEST_APP_NAME,
                    headerA11y = TEST_APP_NAME,
                    title = "Period day 6 of 6",
                    titleA11y = "Period day 6 of 6",
                    dataType = DataType.MENSTRUATION_PERIOD,
                    startTime = start,
                    endTime = end))
    }
}
