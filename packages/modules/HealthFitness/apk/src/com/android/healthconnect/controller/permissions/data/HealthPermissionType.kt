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
package com.android.healthconnect.controller.permissions.data

import android.health.connect.HealthPermissionCategory

// TODO (b/299880830) possibly rename "category" to something else
enum class HealthPermissionType(val category: Int) {
    // ACTIVITY
    ACTIVE_CALORIES_BURNED(HealthPermissionCategory.ACTIVE_CALORIES_BURNED),
    DISTANCE(HealthPermissionCategory.DISTANCE),
    ELEVATION_GAINED(HealthPermissionCategory.ELEVATION_GAINED),
    EXERCISE(HealthPermissionCategory.EXERCISE),
    FLOORS_CLIMBED(HealthPermissionCategory.FLOORS_CLIMBED),
    STEPS(HealthPermissionCategory.STEPS),
    TOTAL_CALORIES_BURNED(HealthPermissionCategory.TOTAL_CALORIES_BURNED),
    VO2_MAX(HealthPermissionCategory.VO2_MAX),
    WHEELCHAIR_PUSHES(HealthPermissionCategory.WHEELCHAIR_PUSHES),
    POWER(HealthPermissionCategory.POWER),
    SPEED(HealthPermissionCategory.SPEED),
    EXERCISE_ROUTE(HealthPermissionCategory.EXERCISE),

    // BODY_MEASUREMENTS
    BASAL_METABOLIC_RATE(HealthPermissionCategory.BASAL_METABOLIC_RATE),
    BODY_FAT(HealthPermissionCategory.BODY_FAT),
    BODY_WATER_MASS(HealthPermissionCategory.BODY_WATER_MASS),
    BONE_MASS(HealthPermissionCategory.BONE_MASS),
    HEIGHT(HealthPermissionCategory.HEIGHT),
    LEAN_BODY_MASS(HealthPermissionCategory.LEAN_BODY_MASS),
    WEIGHT(HealthPermissionCategory.WEIGHT),

    // CYCLE_TRACKING
    CERVICAL_MUCUS(HealthPermissionCategory.CERVICAL_MUCUS),
    MENSTRUATION(HealthPermissionCategory.MENSTRUATION),
    OVULATION_TEST(HealthPermissionCategory.OVULATION_TEST),
    SEXUAL_ACTIVITY(HealthPermissionCategory.SEXUAL_ACTIVITY),
    INTERMENSTRUAL_BLEEDING(HealthPermissionCategory.INTERMENSTRUAL_BLEEDING),

    // NUTRITION
    HYDRATION(HealthPermissionCategory.HYDRATION),
    NUTRITION(HealthPermissionCategory.NUTRITION),

    // SLEEP
    SLEEP(HealthPermissionCategory.SLEEP),

    // VITALS
    BASAL_BODY_TEMPERATURE(HealthPermissionCategory.BASAL_BODY_TEMPERATURE),
    BLOOD_GLUCOSE(HealthPermissionCategory.BLOOD_GLUCOSE),
    BLOOD_PRESSURE(HealthPermissionCategory.BLOOD_PRESSURE),
    BODY_TEMPERATURE(HealthPermissionCategory.BODY_TEMPERATURE),
    HEART_RATE(HealthPermissionCategory.HEART_RATE),
    HEART_RATE_VARIABILITY(HealthPermissionCategory.HEART_RATE_VARIABILITY),
    OXYGEN_SATURATION(HealthPermissionCategory.OXYGEN_SATURATION),
    RESPIRATORY_RATE(HealthPermissionCategory.RESPIRATORY_RATE),
    RESTING_HEART_RATE(HealthPermissionCategory.RESTING_HEART_RATE),
    SKIN_TEMPERATURE(HealthPermissionCategory.SKIN_TEMPERATURE),
}

fun fromHealthPermissionCategory(healthPermissionCategory: Int): HealthPermissionType {
    return when (healthPermissionCategory) {
        HealthPermissionCategory.UNKNOWN ->
            throw IllegalArgumentException("PermissionType is UNKNOWN.")
        // ACTIVITY
        HealthPermissionCategory.ACTIVE_CALORIES_BURNED ->
            HealthPermissionType.ACTIVE_CALORIES_BURNED
        HealthPermissionCategory.DISTANCE -> HealthPermissionType.DISTANCE
        HealthPermissionCategory.ELEVATION_GAINED -> HealthPermissionType.ELEVATION_GAINED
        HealthPermissionCategory.EXERCISE -> HealthPermissionType.EXERCISE
        HealthPermissionCategory.FLOORS_CLIMBED -> HealthPermissionType.FLOORS_CLIMBED
        HealthPermissionCategory.STEPS -> HealthPermissionType.STEPS
        HealthPermissionCategory.TOTAL_CALORIES_BURNED -> HealthPermissionType.TOTAL_CALORIES_BURNED
        HealthPermissionCategory.VO2_MAX -> HealthPermissionType.VO2_MAX
        HealthPermissionCategory.WHEELCHAIR_PUSHES -> HealthPermissionType.WHEELCHAIR_PUSHES
        HealthPermissionCategory.POWER -> HealthPermissionType.POWER
        HealthPermissionCategory.SPEED -> HealthPermissionType.SPEED
        // BODY_MEASUREMENTS
        HealthPermissionCategory.BASAL_METABOLIC_RATE -> HealthPermissionType.BASAL_METABOLIC_RATE
        HealthPermissionCategory.BODY_FAT -> HealthPermissionType.BODY_FAT
        HealthPermissionCategory.BODY_WATER_MASS -> HealthPermissionType.BODY_WATER_MASS
        HealthPermissionCategory.BONE_MASS -> HealthPermissionType.BONE_MASS
        HealthPermissionCategory.HEIGHT -> HealthPermissionType.HEIGHT
        HealthPermissionCategory.LEAN_BODY_MASS -> HealthPermissionType.LEAN_BODY_MASS
        HealthPermissionCategory.WEIGHT -> HealthPermissionType.WEIGHT
        // CYCLE_TRACKING
        HealthPermissionCategory.CERVICAL_MUCUS -> HealthPermissionType.CERVICAL_MUCUS
        HealthPermissionCategory.MENSTRUATION -> HealthPermissionType.MENSTRUATION
        HealthPermissionCategory.OVULATION_TEST -> HealthPermissionType.OVULATION_TEST
        HealthPermissionCategory.SEXUAL_ACTIVITY -> HealthPermissionType.SEXUAL_ACTIVITY
        HealthPermissionCategory.INTERMENSTRUAL_BLEEDING ->
            HealthPermissionType.INTERMENSTRUAL_BLEEDING
        // NUTRITION
        HealthPermissionCategory.HYDRATION -> HealthPermissionType.HYDRATION
        HealthPermissionCategory.NUTRITION -> HealthPermissionType.NUTRITION
        // SLEEP
        HealthPermissionCategory.SLEEP -> HealthPermissionType.SLEEP
        // VITALS
        HealthPermissionCategory.BASAL_BODY_TEMPERATURE ->
            HealthPermissionType.BASAL_BODY_TEMPERATURE
        HealthPermissionCategory.BLOOD_GLUCOSE -> HealthPermissionType.BLOOD_GLUCOSE
        HealthPermissionCategory.BLOOD_PRESSURE -> HealthPermissionType.BLOOD_PRESSURE
        HealthPermissionCategory.BODY_TEMPERATURE -> HealthPermissionType.BODY_TEMPERATURE
        HealthPermissionCategory.HEART_RATE -> HealthPermissionType.HEART_RATE
        HealthPermissionCategory.HEART_RATE_VARIABILITY ->
            HealthPermissionType.HEART_RATE_VARIABILITY
        HealthPermissionCategory.OXYGEN_SATURATION -> HealthPermissionType.OXYGEN_SATURATION
        HealthPermissionCategory.RESPIRATORY_RATE -> HealthPermissionType.RESPIRATORY_RATE
        HealthPermissionCategory.RESTING_HEART_RATE -> HealthPermissionType.RESTING_HEART_RATE
        HealthPermissionCategory.SKIN_TEMPERATURE -> HealthPermissionType.SKIN_TEMPERATURE
        else -> throw IllegalArgumentException("PermissionType is not supported.")
    }
}
