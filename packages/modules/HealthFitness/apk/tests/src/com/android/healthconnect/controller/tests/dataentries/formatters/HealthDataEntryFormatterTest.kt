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
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedDataEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.SeriesDataEntry
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryFormatter
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.tests.utils.getBasalMetabolicRateRecord
import com.android.healthconnect.controller.tests.utils.getHeartRateRecord
import com.android.healthconnect.controller.tests.utils.getStepsRecord
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
class HealthDataEntryFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: HealthDataEntryFormatter

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        hiltRule.inject()
    }

    @Test
    fun format_formatsHeartRateRecord() {
        val heartRateRecord = getHeartRateRecord(listOf(80, 81, 100))
        runBlocking {
            assertThat(formatter.format(heartRateRecord))
                .isEqualTo(
                    SeriesDataEntry(
                        uuid = "test_id",
                        header = "07:06 - 07:06 • Health Connect test app",
                        headerA11y = "from 07:06 to 07:06 • Health Connect test app",
                        title = "80 bpm - 100 bpm",
                        titleA11y = "from 80 beats per minute to 100 beats per minute",
                        dataType = DataType.HEART_RATE))
        }
    }

    @Test
    fun format_formatsStepsRateRecord() {
        val stepsRecord = getStepsRecord(12)
        runBlocking {
            assertThat(formatter.format(stepsRecord))
                .isEqualTo(
                    FormattedDataEntry(
                        uuid = "test_id",
                        header = "07:06 - 07:06 • Health Connect test app",
                        headerA11y = "from 07:06 to 07:06 • Health Connect test app",
                        title = "12 steps",
                        titleA11y = "12 steps",
                        dataType = DataType.STEPS))
        }
    }

    @Test
    fun format_formatsBasalMetabolicRateRecord() = runBlocking {
        val record = getBasalMetabolicRateRecord(calories = 1548)

        assertThat(formatter.format(record))
            .isEqualTo(
                FormattedDataEntry(
                    uuid = "test_id",
                    header = "07:06 • Health Connect test app",
                    headerA11y = "07:06 • Health Connect test app",
                    title = "1,548 cal",
                    titleA11y = "1,548 calories",
                    dataType = DataType.BASAL_METABOLIC_RATE))
    }
}
