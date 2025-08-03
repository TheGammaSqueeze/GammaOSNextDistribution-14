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

import android.health.connect.datatypes.ActiveCaloriesBurnedRecord
import android.health.connect.datatypes.BasalBodyTemperatureRecord
import android.health.connect.datatypes.BasalMetabolicRateRecord
import android.health.connect.datatypes.BloodGlucoseRecord
import android.health.connect.datatypes.BloodPressureRecord
import android.health.connect.datatypes.BodyFatRecord
import android.health.connect.datatypes.BodyTemperatureRecord
import android.health.connect.datatypes.BodyWaterMassRecord
import android.health.connect.datatypes.BoneMassRecord
import android.health.connect.datatypes.CervicalMucusRecord
import android.health.connect.datatypes.CyclingPedalingCadenceRecord
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.ElevationGainedRecord
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.FloorsClimbedRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.HeartRateVariabilityRmssdRecord
import android.health.connect.datatypes.HeightRecord
import android.health.connect.datatypes.HydrationRecord
import android.health.connect.datatypes.IntermenstrualBleedingRecord
import android.health.connect.datatypes.LeanBodyMassRecord
import android.health.connect.datatypes.MenstruationFlowRecord
import android.health.connect.datatypes.NutritionRecord
import android.health.connect.datatypes.OvulationTestRecord
import android.health.connect.datatypes.OxygenSaturationRecord
import android.health.connect.datatypes.PowerRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.RespiratoryRateRecord
import android.health.connect.datatypes.RestingHeartRateRecord
import android.health.connect.datatypes.SexualActivityRecord
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.SpeedRecord
import android.health.connect.datatypes.StepsCadenceRecord
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.TotalCaloriesBurnedRecord
import android.health.connect.datatypes.Vo2MaxRecord
import android.health.connect.datatypes.WeightRecord
import android.health.connect.datatypes.WheelchairPushesRecord
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.ActiveCaloriesBurnedFormatter
import com.android.healthconnect.controller.dataentries.formatters.BasalBodyTemperatureFormatter
import com.android.healthconnect.controller.dataentries.formatters.BasalMetabolicRateFormatter
import com.android.healthconnect.controller.dataentries.formatters.BloodGlucoseFormatter
import com.android.healthconnect.controller.dataentries.formatters.BloodPressureFormatter
import com.android.healthconnect.controller.dataentries.formatters.BodyFatFormatter
import com.android.healthconnect.controller.dataentries.formatters.BodyTemperatureFormatter
import com.android.healthconnect.controller.dataentries.formatters.BodyWaterMassFormatter
import com.android.healthconnect.controller.dataentries.formatters.BoneMassFormatter
import com.android.healthconnect.controller.dataentries.formatters.CervicalMucusFormatter
import com.android.healthconnect.controller.dataentries.formatters.CyclingPedalingCadenceFormatter
import com.android.healthconnect.controller.dataentries.formatters.DistanceFormatter
import com.android.healthconnect.controller.dataentries.formatters.ElevationGainedFormatter
import com.android.healthconnect.controller.dataentries.formatters.ExerciseSessionFormatter
import com.android.healthconnect.controller.dataentries.formatters.FloorsFormatter
import com.android.healthconnect.controller.dataentries.formatters.HeartRateFormatter
import com.android.healthconnect.controller.dataentries.formatters.HeartRateVariabilityRmssdFormatter
import com.android.healthconnect.controller.dataentries.formatters.HeightFormatter
import com.android.healthconnect.controller.dataentries.formatters.HydrationFormatter
import com.android.healthconnect.controller.dataentries.formatters.IntermenstrualBleedingFormatter
import com.android.healthconnect.controller.dataentries.formatters.LeanBodyMassFormatter
import com.android.healthconnect.controller.dataentries.formatters.MenstruationFlowFormatter
import com.android.healthconnect.controller.dataentries.formatters.NutritionFormatter
import com.android.healthconnect.controller.dataentries.formatters.OvulationTestFormatter
import com.android.healthconnect.controller.dataentries.formatters.OxygenSaturationFormatter
import com.android.healthconnect.controller.dataentries.formatters.PowerFormatter
import com.android.healthconnect.controller.dataentries.formatters.RespiratoryRateFormatter
import com.android.healthconnect.controller.dataentries.formatters.RestingHeartRateFormatter
import com.android.healthconnect.controller.dataentries.formatters.SexualActivityFormatter
import com.android.healthconnect.controller.dataentries.formatters.SleepSessionFormatter
import com.android.healthconnect.controller.dataentries.formatters.SpeedFormatter
import com.android.healthconnect.controller.dataentries.formatters.StepsCadenceFormatter
import com.android.healthconnect.controller.dataentries.formatters.StepsFormatter
import com.android.healthconnect.controller.dataentries.formatters.TotalCaloriesBurnedFormatter
import com.android.healthconnect.controller.dataentries.formatters.Vo2MaxFormatter
import com.android.healthconnect.controller.dataentries.formatters.WeightFormatter
import com.android.healthconnect.controller.dataentries.formatters.WheelchairPushesFormatter
import com.android.healthconnect.controller.shared.app.AppInfoReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthDataEntryFormatter
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val heartRateFormatter: HeartRateFormatter,
    private val stepsFormatter: StepsFormatter,
    private val stepsCadenceFormatter: StepsCadenceFormatter,
    private val basalMetabolicRateFormatter: BasalMetabolicRateFormatter,
    private val speedFormatter: SpeedFormatter,
    private val distanceFormatter: DistanceFormatter,
    private val powerFormatter: PowerFormatter,
    private val activeCaloriesBurnedFormatter: ActiveCaloriesBurnedFormatter,
    private val totalCaloriesBurnedFormatter: TotalCaloriesBurnedFormatter,
    private val heightFormatter: HeightFormatter,
    private val bodyFatFormatter: BodyFatFormatter,
    private val oxygenSaturationFormatter: OxygenSaturationFormatter,
    private val basalBodyTemperatureFormatter: BasalBodyTemperatureFormatter,
    private val bodyTemperatureFormatter: BodyTemperatureFormatter,
    private val wheelchairPushesFormatter: WheelchairPushesFormatter,
    private val restingHeartRateFormatter: RestingHeartRateFormatter,
    private val respiratoryRateFormatter: RespiratoryRateFormatter,
    private val hydrationFormatter: HydrationFormatter,
    private val floorsFormatter: FloorsFormatter,
    private val elevationGainedFormatter: ElevationGainedFormatter,
    private val weightFormatter: WeightFormatter,
    private val leanBodyMassFormatter: LeanBodyMassFormatter,
    private val boneMassFormatter: BoneMassFormatter,
    private val bloodGlucoseFormatter: BloodGlucoseFormatter,
    private val nutritionFormatter: NutritionFormatter,
    private val bloodPressureFormatter: BloodPressureFormatter,
    private val cyclingPedalingCadenceFormatter: CyclingPedalingCadenceFormatter,
    private val vo2MaxFormatter: Vo2MaxFormatter,
    private val cervicalMucusFormatter: CervicalMucusFormatter,
    private val menstruationFlowFormatter: MenstruationFlowFormatter,
    private val ovulationTestFormatter: OvulationTestFormatter,
    private val sexualActivityFormatter: SexualActivityFormatter,
    private val sleepSessionFormatter: SleepSessionFormatter,
    private val exerciseSessionFormatter: ExerciseSessionFormatter,
    private val bodyWaterMassFormatter: BodyWaterMassFormatter,
    private val intermenstrualBleedingFormatter: IntermenstrualBleedingFormatter,
    private val heartRateVariabilityRmssdFormatter: HeartRateVariabilityRmssdFormatter,
) {

    suspend fun format(record: Record, showDataOrigin: Boolean = true): FormattedEntry {
        val appName: String = if (showDataOrigin) getAppName(record) else ""
        return when (record) {
            is HeartRateRecord -> heartRateFormatter.format(record, appName)
            is StepsRecord -> stepsFormatter.format(record, appName)
            is StepsCadenceRecord -> stepsCadenceFormatter.format(record, appName)
            is BasalMetabolicRateRecord -> basalMetabolicRateFormatter.format(record, appName)
            is SpeedRecord -> speedFormatter.format(record, appName)
            is DistanceRecord -> distanceFormatter.format(record, appName)
            is PowerRecord -> powerFormatter.format(record, appName)
            is ActiveCaloriesBurnedRecord -> activeCaloriesBurnedFormatter.format(record, appName)
            is TotalCaloriesBurnedRecord -> totalCaloriesBurnedFormatter.format(record, appName)
            is HeightRecord -> heightFormatter.format(record, appName)
            is BodyFatRecord -> bodyFatFormatter.format(record, appName)
            is OxygenSaturationRecord -> oxygenSaturationFormatter.format(record, appName)
            is BodyTemperatureRecord -> bodyTemperatureFormatter.format(record, appName)
            is BasalBodyTemperatureRecord -> basalBodyTemperatureFormatter.format(record, appName)
            is WheelchairPushesRecord -> wheelchairPushesFormatter.format(record, appName)
            is RestingHeartRateRecord -> restingHeartRateFormatter.format(record, appName)
            is RespiratoryRateRecord -> respiratoryRateFormatter.format(record, appName)
            is HydrationRecord -> hydrationFormatter.format(record, appName)
            is FloorsClimbedRecord -> floorsFormatter.format(record, appName)
            is ElevationGainedRecord -> elevationGainedFormatter.format(record, appName)
            is WeightRecord -> weightFormatter.format(record, appName)
            is LeanBodyMassRecord -> leanBodyMassFormatter.format(record, appName)
            is BoneMassRecord -> boneMassFormatter.format(record, appName)
            is BloodGlucoseRecord -> bloodGlucoseFormatter.format(record, appName)
            is NutritionRecord -> nutritionFormatter.format(record, appName)
            is BloodPressureRecord -> bloodPressureFormatter.format(record, appName)
            is CyclingPedalingCadenceRecord ->
                cyclingPedalingCadenceFormatter.format(record, appName)
            is Vo2MaxRecord -> vo2MaxFormatter.format(record, appName)
            is CervicalMucusRecord -> cervicalMucusFormatter.format(record, appName)
            is SexualActivityRecord -> sexualActivityFormatter.format(record, appName)
            is OvulationTestRecord -> ovulationTestFormatter.format(record, appName)
            is MenstruationFlowRecord -> menstruationFlowFormatter.format(record, appName)
            is SleepSessionRecord -> sleepSessionFormatter.format(record, appName)
            is ExerciseSessionRecord -> exerciseSessionFormatter.format(record, appName)
            is BodyWaterMassRecord -> bodyWaterMassFormatter.format(record, appName)
            is IntermenstrualBleedingRecord ->
                intermenstrualBleedingFormatter.format(record, appName)
            is HeartRateVariabilityRmssdRecord ->
                heartRateVariabilityRmssdFormatter.format(record, appName)
            else -> throw IllegalArgumentException("${record::class.java} Not supported!")
        }
    }

    private suspend fun getAppName(record: Record): String {
        return appInfoReader.getAppMetadata(record.metadata.dataOrigin.packageName).appName
    }
}
