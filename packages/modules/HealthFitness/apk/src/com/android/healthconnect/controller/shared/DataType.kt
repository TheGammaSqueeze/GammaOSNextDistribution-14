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
package com.android.healthconnect.controller.shared

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
import android.health.connect.datatypes.MenstruationPeriodRecord
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

enum class DataType(val recordClass: Class<out Record>) {
    ACTIVE_CALORIES_BURNED(ActiveCaloriesBurnedRecord::class.java),
    BASAL_METABOLIC_RATE(BasalMetabolicRateRecord::class.java),
    DISTANCE(DistanceRecord::class.java),
    HEART_RATE(HeartRateRecord::class.java),
    POWER(PowerRecord::class.java),
    SPEED(SpeedRecord::class.java),
    STEPS(StepsRecord::class.java),
    STEPS_CADENCE(StepsCadenceRecord::class.java),
    TOTAL_CALORIES_BURNED(TotalCaloriesBurnedRecord::class.java),
    HEIGHT(HeightRecord::class.java),
    BODY_FAT(BodyFatRecord::class.java),
    OXYGEN_SATURATION(OxygenSaturationRecord::class.java),
    BODY_TEMPERATURE(BodyTemperatureRecord::class.java),
    BASAL_BODY_TEMPERATURE(BasalBodyTemperatureRecord::class.java),
    WHEELCHAIR_PUSHES(WheelchairPushesRecord::class.java),
    RESTING_HEART_RATE(RestingHeartRateRecord::class.java),
    RESPIRATORY_RATE(RespiratoryRateRecord::class.java),
    HYDRATION(HydrationRecord::class.java),
    FLOORS_CLIMBED(FloorsClimbedRecord::class.java),
    ELEVATION_GAINED(ElevationGainedRecord::class.java),
    BONE_MASS(BoneMassRecord::class.java),
    LEAN_BODY_MASS(LeanBodyMassRecord::class.java),
    WEIGHT(WeightRecord::class.java),
    BLOOD_GLUCOSE(BloodGlucoseRecord::class.java),
    NUTRITION(NutritionRecord::class.java),
    BLOOD_PRESSURE(BloodPressureRecord::class.java),
    VO2_MAX(Vo2MaxRecord::class.java),
    CYCLE_PEDALING_CADENCE(CyclingPedalingCadenceRecord::class.java),
    CERVICAL_MUCUS(CervicalMucusRecord::class.java),
    SEXUAL_ACTIVITY(SexualActivityRecord::class.java),
    OVULATION_TEST(OvulationTestRecord::class.java),
    MENSTRUATION_FLOW(MenstruationFlowRecord::class.java),
    MENSTRUATION_PERIOD(MenstruationPeriodRecord::class.java),
    SLEEP(SleepSessionRecord::class.java),
    EXERCISE(ExerciseSessionRecord::class.java),
    BODY_WATER_MASS(BodyWaterMassRecord::class.java),
    INTERMENSTRUAL_BLEEDING(IntermenstrualBleedingRecord::class.java),
    HEART_RATE_VARIABILITY(HeartRateVariabilityRmssdRecord::class.java),
}
