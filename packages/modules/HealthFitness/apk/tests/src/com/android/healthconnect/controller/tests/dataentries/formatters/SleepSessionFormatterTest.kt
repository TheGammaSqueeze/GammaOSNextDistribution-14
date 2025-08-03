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

package com.android.healthconnect.controller.tests.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.SleepSessionRecord.Stage
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_AWAKE
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_IN_BED
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_OUT_OF_BED
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_REM
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.SleepSessionFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class SleepSessionFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: SleepSessionFormatter
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
    fun formatValue_noTitle_showsAmountOfSleep() = runTest {
        val record = getRecord()

        assertThat(formatter.formatValue(record)).isEqualTo("8h sleeping")
    }

    @Test
    fun formatA11yValue_noTitle_showsAmountOfSleep() = runTest {
        val record = getRecord()

        assertThat(formatter.formatA11yValue(record)).isEqualTo("8 hours sleeping")
    }

    @Test
    fun formatValue_title_showsTitle() = runTest {
        val record = getRecord(title = "Session title")

        assertThat(formatter.formatValue(record)).isEqualTo("Session title")
    }

    @Test
    fun formatRecordDetails_awakeStage() = runTest {
        val start = NOW
        val end = NOW.plus(1, ChronoUnit.HOURS)
        val record = getRecord(stages = listOf(Stage(start, end, STAGE_TYPE_AWAKE)))

        val stage =
            formatter.formatRecordDetails(record)[0] as FormattedEntry.FormattedSessionDetail
        assertThat(stage.title).isEqualTo("1h awake")
    }

    @Test
    fun formatRecordDetails_awakeInBedStage() = runTest {
        val start = NOW
        val end = NOW.plus(1, ChronoUnit.HOURS)
        val record = getRecord(stages = listOf(Stage(start, end, STAGE_TYPE_AWAKE_IN_BED)))

        val stage =
            formatter.formatRecordDetails(record)[0] as FormattedEntry.FormattedSessionDetail
        assertThat(stage.title).isEqualTo("1h awake in bed")
    }

    @Test
    fun formatRecordDetails_sleepingDeepStage() = runTest {
        val start = NOW
        val end = NOW.plus(1, ChronoUnit.HOURS)
        val record = getRecord(stages = listOf(Stage(start, end, STAGE_TYPE_SLEEPING_DEEP)))

        val stage =
            formatter.formatRecordDetails(record)[0] as FormattedEntry.FormattedSessionDetail
        assertThat(stage.title).isEqualTo("1h deep sleep")
    }

    @Test
    fun formatRecordDetails_sleepingLightStage() = runTest {
        val start = NOW
        val end = NOW.plus(1, ChronoUnit.HOURS)
        val record = getRecord(stages = listOf(Stage(start, end, STAGE_TYPE_SLEEPING_LIGHT)))

        val stage =
            formatter.formatRecordDetails(record)[0] as FormattedEntry.FormattedSessionDetail
        assertThat(stage.title).isEqualTo("1h light sleep")
    }

    @Test
    fun formatRecordDetails_sleepingREMStage() = runTest {
        val start = NOW
        val end = NOW.plus(1, ChronoUnit.HOURS)
        val record = getRecord(stages = listOf(Stage(start, end, STAGE_TYPE_SLEEPING_REM)))

        val stage =
            formatter.formatRecordDetails(record)[0] as FormattedEntry.FormattedSessionDetail
        assertThat(stage.title).isEqualTo("1h REM sleep")
    }

    @Test
    fun formatRecordDetails_sleepingStage() = runTest {
        val start = NOW
        val end = NOW.plus(1, ChronoUnit.HOURS)
        val record = getRecord(stages = listOf(Stage(start, end, STAGE_TYPE_SLEEPING)))

        val stage =
            formatter.formatRecordDetails(record)[0] as FormattedEntry.FormattedSessionDetail
        assertThat(stage.title).isEqualTo("1h sleeping")
    }

    @Test
    fun formatRecordDetails_outOfBedStage() = runTest {
        val start = NOW
        val end = NOW.plus(1, ChronoUnit.HOURS)
        val record = getRecord(stages = listOf(Stage(start, end, STAGE_TYPE_AWAKE_OUT_OF_BED)))

        val stage =
            formatter.formatRecordDetails(record)[0] as FormattedEntry.FormattedSessionDetail
        assertThat(stage.title).isEqualTo("1h out of bed")
    }

    @Test
    fun formatUnit_showsAmountOfSleep() {
        val startTime = Instant.parse("2023-02-11T21:20:00Z")
        val endTime = Instant.parse("2023-02-12T08:14:00Z")
        val totalSleepTime = Duration.between(startTime, endTime).toMillis()

        assertThat(formatter.formatUnit(totalSleepTime)).isEqualTo("10h 54m")
    }

    @Test
    fun formatA11yUnit_showsAmountOfSleep() {
        val startTime = Instant.parse("2023-02-11T21:20:00Z")
        val endTime = Instant.parse("2023-02-12T08:14:00Z")
        val totalSleepTime = Duration.between(startTime, endTime).toMillis()

        assertThat(formatter.formatA11yUnit(totalSleepTime)).isEqualTo("10 hours 54 minutes")
    }

    private fun getRecord(
        title: String = "",
        note: String = "",
        stages: List<Stage> = emptyList(),
    ): SleepSessionRecord {
        return SleepSessionRecord.Builder(getMetaData(), NOW, NOW.plus(8, ChronoUnit.HOURS))
            .setTitle(title)
            .setNotes(note)
            .setStages(stages)
            .build()
    }
}
