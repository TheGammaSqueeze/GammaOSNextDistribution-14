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
import android.health.connect.datatypes.ExerciseLap
import android.health.connect.datatypes.ExerciseSegment
import android.health.connect.datatypes.ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_JUMPING_JACK
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING
import android.health.connect.datatypes.ExerciseSessionType.EXERCISE_SESSION_TYPE_OTHER_WORKOUT
import android.health.connect.datatypes.units.Length
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.ExerciseSessionFormatter
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
class ExerciseSessionFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: ExerciseSessionFormatter
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        hiltRule.inject()
    }

    @Test
    fun formatValue() = runBlocking {
        assertThat(formatter.formatValue(getRecord(type = EXERCISE_SESSION_TYPE_BIKING)))
            .isEqualTo("16 m, Cycling")
    }

    @Test
    fun formatA11yValue() = runBlocking {
        assertThat(formatter.formatA11yValue(getRecord(type = EXERCISE_SESSION_TYPE_BIKING)))
            .isEqualTo("16 minutes, Cycling")
    }

    @Test
    fun formatNotes() = runBlocking {
        assertThat(
                formatter.getNotes(getRecord(type = EXERCISE_SESSION_TYPE_BIKING, note = "notes")))
            .isEqualTo("notes")
    }

    @Test
    fun formatRecordDetails_returnSegments() = runBlocking {
        val segments =
            buildList<ExerciseSegment> {
                add(
                    ExerciseSegment.Builder(
                            NOW, NOW.plusSeconds(500), EXERCISE_SEGMENT_TYPE_JUMPING_JACK)
                        .setRepetitionsCount(2)
                        .build())
            }
        val laps =
            buildList<ExerciseLap> {
                add(
                    ExerciseLap.Builder(NOW, NOW.plusSeconds(500))
                        .setLength(Length.fromMeters(20.0))
                        .build())
            }
        val record =
            getRecord(type = EXERCISE_SESSION_TYPE_OTHER_WORKOUT, segments = segments, laps = laps)
        assertThat(formatter.formatRecordDetails(record))
            .isEqualTo(
                listOf(
                    FormattedEntry.SessionHeader("Exercise segments"),
                    FormattedEntry.FormattedSessionDetail(
                        uuid = record.metadata.id,
                        header = "07:06 - 07:14",
                        headerA11y = "from 07:06 to 07:14",
                        title = "Jumping jack: 2 reps",
                        titleA11y = "Jumping jack: 2 repetitions"),
                    FormattedEntry.SessionHeader("Laps"),
                    FormattedEntry.FormattedSessionDetail(
                        uuid = record.metadata.id,
                        header = "07:06 - 07:14",
                        headerA11y = "from 07:06 to 07:14",
                        title = "0.02 km",
                        titleA11y = "0.02 kilometres"),
                ))
    }

    private fun getRecord(
        type: Int = EXERCISE_SESSION_TYPE_BIKING,
        title: String? = null,
        note: String? = null,
        laps: List<ExerciseLap> = emptyList(),
        segments: List<ExerciseSegment> = emptyList()
    ): ExerciseSessionRecord {
        return ExerciseSessionRecord.Builder(getMetaData(), NOW, NOW.plusSeconds(1000), type)
            .setNotes(note)
            .setLaps(laps)
            .setTitle(title)
            .setSegments(segments)
            .build()
    }
}
