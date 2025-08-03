/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *    http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.testapps.toolbox

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
import android.text.InputType
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlin.reflect.KClass

/** Constant variables used across the app. */
object Constants {

    const val INPUT_TYPE_DOUBLE = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    const val INPUT_TYPE_LONG = InputType.TYPE_CLASS_NUMBER
    const val INPUT_TYPE_INT = InputType.TYPE_CLASS_NUMBER
    const val INPUT_TYPE_TEXT = InputType.TYPE_CLASS_TEXT

    val ALL_PERMISSIONS =
        arrayOf(
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            "android.permission.health.READ_BASAL_BODY_TEMPERATURE",
            "android.permission.health.READ_BASAL_METABOLIC_RATE",
            "android.permission.health.READ_BLOOD_GLUCOSE",
            "android.permission.health.READ_BLOOD_PRESSURE",
            "android.permission.health.READ_BODY_FAT",
            "android.permission.health.READ_BODY_TEMPERATURE",
            "android.permission.health.READ_BODY_WATER_MASS",
            "android.permission.health.READ_BONE_MASS",
            "android.permission.health.READ_CERVICAL_MUCUS",
            "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_ELEVATION_GAINED",
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_EXERCISE_ROUTES",
            "android.permission.health.READ_FLOORS_CLIMBED",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_HEART_RATE_VARIABILITY",
            "android.permission.health.READ_HEIGHT",
            "android.permission.health.READ_HYDRATION",
            "android.permission.health.READ_LEAN_BODY_MASS",
            "android.permission.health.READ_MENSTRUATION",
            "android.permission.health.READ_NUTRITION",
            "android.permission.health.READ_OVULATION_TEST",
            "android.permission.health.READ_OXYGEN_SATURATION",
            "android.permission.health.READ_POWER",
            "android.permission.health.READ_RESPIRATORY_RATE",
            "android.permission.health.READ_RESTING_HEART_RATE",
            "android.permission.health.READ_SEXUAL_ACTIVITY",
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_SPEED",
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_TOTAL_CALORIES_BURNED",
            "android.permission.health.READ_VO2_MAX",
            "android.permission.health.READ_WEIGHT",
            "android.permission.health.READ_INTERMENSTRUAL_BLEEDING",
            "android.permission.health.READ_WHEELCHAIR_PUSHES",
            "android.permission.health.WRITE_ACTIVE_CALORIES_BURNED",
            "android.permission.health.WRITE_BASAL_BODY_TEMPERATURE",
            "android.permission.health.WRITE_BASAL_METABOLIC_RATE",
            "android.permission.health.WRITE_BLOOD_GLUCOSE",
            "android.permission.health.WRITE_BLOOD_PRESSURE",
            "android.permission.health.WRITE_BODY_FAT",
            "android.permission.health.WRITE_BODY_TEMPERATURE",
            "android.permission.health.WRITE_BODY_WATER_MASS",
            "android.permission.health.WRITE_BONE_MASS",
            "android.permission.health.WRITE_CERVICAL_MUCUS",
            "android.permission.health.WRITE_DISTANCE",
            "android.permission.health.WRITE_ELEVATION_GAINED",
            "android.permission.health.WRITE_EXERCISE",
            "android.permission.health.WRITE_FLOORS_CLIMBED",
            "android.permission.health.WRITE_HEART_RATE",
            "android.permission.health.WRITE_HEART_RATE_VARIABILITY",
            "android.permission.health.WRITE_HEIGHT",
            "android.permission.health.WRITE_HYDRATION",
            "android.permission.health.WRITE_LEAN_BODY_MASS",
            "android.permission.health.WRITE_MENSTRUATION",
            "android.permission.health.WRITE_NUTRITION",
            "android.permission.health.WRITE_OVULATION_TEST",
            "android.permission.health.WRITE_OXYGEN_SATURATION",
            "android.permission.health.WRITE_POWER",
            "android.permission.health.WRITE_RESPIRATORY_RATE",
            "android.permission.health.WRITE_RESTING_HEART_RATE",
            "android.permission.health.WRITE_SEXUAL_ACTIVITY",
            "android.permission.health.WRITE_SLEEP",
            "android.permission.health.WRITE_SPEED",
            "android.permission.health.WRITE_STEPS",
            "android.permission.health.WRITE_TOTAL_CALORIES_BURNED",
            "android.permission.health.WRITE_VO2_MAX",
            "android.permission.health.WRITE_WEIGHT",
            "android.permission.health.WRITE_WHEELCHAIR_PUSHES",
            "android.permission.health.WRITE_INTERMENSTRUAL_BLEEDING",
            "android.permission.health.WRITE_EXERCISE_ROUTE")

    /** Represents Category group for HealthConnect data. */
    enum class HealthDataCategory(
        val healthPermissionTypes: List<HealthPermissionType>,
        @StringRes val title: Int,
        @DrawableRes val icon: Int,
    ) {
        ACTIVITY(
            CategoriesMappers.ACTIVITY_PERMISSION_GROUPS,
            R.string.activity_category,
            R.drawable.quantum_gm_ic_directions_run_vd_theme_24),
        BODY_MEASUREMENTS(
            CategoriesMappers.BODY_MEASUREMENTS_PERMISSION_GROUPS,
            R.string.body_measurements_category,
            R.drawable.quantum_gm_ic_straighten_vd_theme_24),
        SLEEP(
            CategoriesMappers.SLEEP_PERMISSION_GROUPS,
            R.string.sleep_category,
            R.drawable.ic_sleep),
        VITALS(
            CategoriesMappers.VITALS_PERMISSION_GROUPS,
            R.string.vitals_category,
            R.drawable.ic_vitals),
        CYCLE_TRACKING(
            CategoriesMappers.CYCLE_TRACKING_PERMISSION_GROUPS,
            R.string.cycle_tracking_category,
            R.drawable.ic_cycle_tracking),
        NUTRITION(
            CategoriesMappers.NUTRITION_PERMISSION_GROUPS,
            R.string.nutrition_category,
            R.drawable.quantum_gm_ic_grocery_vd_theme_24),
    }

    /** Permission groups for each {@link HealthDataCategory}. */
    object CategoriesMappers {
        val ACTIVITY_PERMISSION_GROUPS =
            listOf(
                HealthPermissionType.ACTIVE_CALORIES_BURNED,
                HealthPermissionType.DISTANCE,
                HealthPermissionType.ELEVATION_GAINED,
                HealthPermissionType.FLOORS_CLIMBED,
                HealthPermissionType.POWER,
                HealthPermissionType.SPEED,
                HealthPermissionType.STEPS,
                HealthPermissionType.STEPS_CADENCE,
                HealthPermissionType.TOTAL_CALORIES_BURNED,
                HealthPermissionType.VO2_MAX,
                HealthPermissionType.CYCLING_PEDALING_CADENCE,
                HealthPermissionType.WHEELCHAIR_PUSHES,
                HealthPermissionType.EXERCISE_SESSION)

        val BODY_MEASUREMENTS_PERMISSION_GROUPS =
            listOf(
                HealthPermissionType.BASAL_METABOLIC_RATE,
                HealthPermissionType.BODY_FAT,
                HealthPermissionType.BODY_WATER_MASS,
                HealthPermissionType.BONE_MASS,
                HealthPermissionType.HEIGHT,
                HealthPermissionType.LEAN_BODY_MASS,
                HealthPermissionType.WEIGHT)

        val CYCLE_TRACKING_PERMISSION_GROUPS =
            listOf(
                HealthPermissionType.CERVICAL_MUCUS,
                HealthPermissionType.MENSTRUATION_FLOW,
                HealthPermissionType.MENSTRUATION_PERIOD,
                HealthPermissionType.OVULATION_TEST,
                HealthPermissionType.INTERMENSTRUAL_BLEEDING,
                HealthPermissionType.SEXUAL_ACTIVITY)

        val NUTRITION_PERMISSION_GROUPS =
            listOf(HealthPermissionType.HYDRATION, HealthPermissionType.NUTRITION)

        val SLEEP_PERMISSION_GROUPS = listOf(HealthPermissionType.SLEEP)

        val VITALS_PERMISSION_GROUPS =
            listOf(
                HealthPermissionType.BASAL_BODY_TEMPERATURE,
                HealthPermissionType.BLOOD_GLUCOSE,
                HealthPermissionType.BLOOD_PRESSURE,
                HealthPermissionType.BODY_TEMPERATURE,
                HealthPermissionType.HEART_RATE,
                HealthPermissionType.HEART_RATE_VARIABILITY,
                HealthPermissionType.OXYGEN_SATURATION,
                HealthPermissionType.RESPIRATORY_RATE,
                HealthPermissionType.RESTING_HEART_RATE)
    }

    enum class HealthPermissionType(
        val recordClass: KClass<out Record>?,
        @StringRes val title: Int,
    ) {
        // ACTIVITY
        ACTIVE_CALORIES_BURNED(
            ActiveCaloriesBurnedRecord::class, R.string.active_calories_burned_label),
        DISTANCE(DistanceRecord::class, R.string.distance_label),
        ELEVATION_GAINED(ElevationGainedRecord::class, R.string.elevation_gained_label),
        FLOORS_CLIMBED(FloorsClimbedRecord::class, R.string.floors_climbed_label),
        STEPS(StepsRecord::class, R.string.steps_label),
        STEPS_CADENCE(StepsCadenceRecord::class, R.string.steps_cadence_label),
        TOTAL_CALORIES_BURNED(
            TotalCaloriesBurnedRecord::class, R.string.total_calories_burned_label),
        VO2_MAX(Vo2MaxRecord::class, R.string.vo2_max_label),
        WHEELCHAIR_PUSHES(WheelchairPushesRecord::class, R.string.wheelchair_pushes_label),
        POWER(PowerRecord::class, R.string.power_label),
        SPEED(SpeedRecord::class, R.string.speed_label),
        CYCLING_PEDALING_CADENCE(
            CyclingPedalingCadenceRecord::class, R.string.cycling_pedaling_cadence),
        EXERCISE_SESSION(ExerciseSessionRecord::class, R.string.exercise_session),

        // BODY_MEASUREMENTS
        BASAL_METABOLIC_RATE(BasalMetabolicRateRecord::class, R.string.basal_metabolic_rate_label),
        BODY_FAT(BodyFatRecord::class, R.string.body_fat_label),
        BODY_WATER_MASS(BodyWaterMassRecord::class, R.string.body_water_mass_label),
        BONE_MASS(BoneMassRecord::class, R.string.bone_mass_label),
        HEIGHT(HeightRecord::class, R.string.height_label),
        LEAN_BODY_MASS(LeanBodyMassRecord::class, R.string.lean_body_mass_label),
        WEIGHT(WeightRecord::class, R.string.weight_label),

        // CYCLE_TRACKING
        CERVICAL_MUCUS(CervicalMucusRecord::class, R.string.cervical_mucus_label),
        MENSTRUATION_FLOW(MenstruationFlowRecord::class, R.string.menstruation_flow),
        MENSTRUATION_PERIOD(MenstruationPeriodRecord::class, R.string.menstruation_period),
        OVULATION_TEST(OvulationTestRecord::class, R.string.ovulation_test_label),
        SEXUAL_ACTIVITY(SexualActivityRecord::class, R.string.sexual_activity_label),
        INTERMENSTRUAL_BLEEDING(
            IntermenstrualBleedingRecord::class, R.string.inter_menstrual_bleeding),

        // NUTRITION
        HYDRATION(HydrationRecord::class, R.string.hydration_label),
        NUTRITION(NutritionRecord::class, R.string.nutrition_label),

        // SLEEP
        SLEEP(SleepSessionRecord::class, R.string.sleep_label),

        // VITALS
        BASAL_BODY_TEMPERATURE(
            BasalBodyTemperatureRecord::class, R.string.basal_body_temperature_label),
        BLOOD_GLUCOSE(BloodGlucoseRecord::class, R.string.blood_glucose_label),
        BLOOD_PRESSURE(BloodPressureRecord::class, R.string.blood_pressure_label),
        BODY_TEMPERATURE(BodyTemperatureRecord::class, R.string.body_temperature_label),
        HEART_RATE(HeartRateRecord::class, R.string.heart_rate_label),
        HEART_RATE_VARIABILITY(
            HeartRateVariabilityRmssdRecord::class, R.string.heart_rate_variability_label),
        OXYGEN_SATURATION(OxygenSaturationRecord::class, R.string.oxygen_saturation_label),
        RESPIRATORY_RATE(RespiratoryRateRecord::class, R.string.respiratory_rate_label),
        RESTING_HEART_RATE(RestingHeartRateRecord::class, R.string.resting_heart_rate_label),
    }
}
