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

package com.android.healthconnect.controller.dataentries.formatters.shared

import android.health.connect.datatypes.CyclingPedalingCadenceRecord
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.PowerRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.SpeedRecord
import android.health.connect.datatypes.StepsCadenceRecord
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.CyclingPedalingCadenceFormatter
import com.android.healthconnect.controller.dataentries.formatters.ExerciseSessionFormatter
import com.android.healthconnect.controller.dataentries.formatters.HeartRateFormatter
import com.android.healthconnect.controller.dataentries.formatters.PowerFormatter
import com.android.healthconnect.controller.dataentries.formatters.SleepSessionFormatter
import com.android.healthconnect.controller.dataentries.formatters.SpeedFormatter
import com.android.healthconnect.controller.dataentries.formatters.StepsCadenceFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthDataEntryDetailsFormatter
@Inject
constructor(
    private val sleepSessionFormatter: SleepSessionFormatter,
    private val exerciseSessionFormatter: ExerciseSessionFormatter,
    private val heartRateFormatter: HeartRateFormatter,
    private val speedRecordFormatter: SpeedFormatter,
    private val stepsCadenceFormatter: StepsCadenceFormatter,
    private val powerFormatter: PowerFormatter,
    private val cyclingPedalingCadenceFormatter: CyclingPedalingCadenceFormatter
) {
    suspend fun formatDetails(record: Record): List<FormattedEntry> {
        return when (record) {
            is SleepSessionRecord -> sleepSessionFormatter.formatRecordDetails(record)
            is ExerciseSessionRecord -> exerciseSessionFormatter.formatRecordDetails(record)
            is HeartRateRecord -> heartRateFormatter.formatRecordDetails(record)
            is SpeedRecord -> speedRecordFormatter.formatRecordDetails(record)
            is StepsCadenceRecord -> stepsCadenceFormatter.formatRecordDetails(record)
            is PowerRecord -> powerFormatter.formatRecordDetails(record)
            is CyclingPedalingCadenceRecord ->
                cyclingPedalingCadenceFormatter.formatRecordDetails(record)
            else -> throw IllegalArgumentException("${record::class.java} Not supported!")
        }
    }
}
