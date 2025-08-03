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

import android.content.Context
import android.graphics.drawable.Drawable
import android.health.connect.HealthDataCategory
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.CategoriesMappers.ACTIVITY_PERMISSION_GROUPS
import com.android.healthconnect.controller.shared.CategoriesMappers.BODY_MEASUREMENTS_PERMISSION_GROUPS
import com.android.healthconnect.controller.shared.CategoriesMappers.CYCLE_TRACKING_PERMISSION_GROUPS
import com.android.healthconnect.controller.shared.CategoriesMappers.NUTRITION_PERMISSION_GROUPS
import com.android.healthconnect.controller.shared.CategoriesMappers.SLEEP_PERMISSION_GROUPS
import com.android.healthconnect.controller.shared.CategoriesMappers.VITALS_PERMISSION_GROUPS
import com.android.healthconnect.controller.utils.AttributeResolver

object HealthDataCategoryExtensions {
    fun @receiver:HealthDataCategoryInt Int.healthPermissionTypes(): List<HealthPermissionType> {
        return when (this) {
            HealthDataCategory.ACTIVITY -> ACTIVITY_PERMISSION_GROUPS
            HealthDataCategory.BODY_MEASUREMENTS -> BODY_MEASUREMENTS_PERMISSION_GROUPS
            HealthDataCategory.CYCLE_TRACKING -> CYCLE_TRACKING_PERMISSION_GROUPS
            HealthDataCategory.NUTRITION -> NUTRITION_PERMISSION_GROUPS
            HealthDataCategory.SLEEP -> SLEEP_PERMISSION_GROUPS
            HealthDataCategory.VITALS -> VITALS_PERMISSION_GROUPS
            else -> throw IllegalArgumentException("Category $this is not supported.")
        }
    }

    @StringRes
    fun @receiver:HealthDataCategoryInt Int.lowercaseTitle(): Int {
        return when (this) {
            HealthDataCategory.ACTIVITY -> R.string.activity_category_lowercase
            HealthDataCategory.BODY_MEASUREMENTS -> R.string.body_measurements_category_lowercase
            HealthDataCategory.CYCLE_TRACKING -> R.string.cycle_tracking_category_lowercase
            HealthDataCategory.NUTRITION -> R.string.nutrition_category_lowercase
            HealthDataCategory.SLEEP -> R.string.sleep_category_lowercase
            HealthDataCategory.VITALS -> R.string.vitals_category_lowercase
            else -> throw IllegalArgumentException("Category $this is not supported.")
        }
    }

    @StringRes
    fun @receiver:HealthDataCategoryInt Int.uppercaseTitle(): Int {
        return when (this) {
            HealthDataCategory.ACTIVITY -> R.string.activity_category_uppercase
            HealthDataCategory.BODY_MEASUREMENTS -> R.string.body_measurements_category_uppercase
            HealthDataCategory.CYCLE_TRACKING -> R.string.cycle_tracking_category_uppercase
            HealthDataCategory.NUTRITION -> R.string.nutrition_category_uppercase
            HealthDataCategory.SLEEP -> R.string.sleep_category_uppercase
            HealthDataCategory.VITALS -> R.string.vitals_category_uppercase
            else -> throw IllegalArgumentException("Category $this is not supported.")
        }
    }

    fun @receiver:HealthDataCategoryInt Int.icon(context: Context): Drawable? {
        val attrRes: Int =
            when (this) {
                HealthDataCategory.ACTIVITY -> R.attr.activityCategoryIcon
                HealthDataCategory.BODY_MEASUREMENTS -> R.attr.bodyMeasurementsCategoryIcon
                HealthDataCategory.CYCLE_TRACKING -> R.attr.cycleTrackingCategoryIcon
                HealthDataCategory.NUTRITION -> R.attr.nutritionCategoryIcon
                HealthDataCategory.SLEEP -> R.attr.sleepCategoryIcon
                HealthDataCategory.VITALS -> R.attr.vitalsCategoryIcon
                else -> throw IllegalArgumentException("Category $this is not supported.")
            }
        return AttributeResolver.getDrawable(context, attrRes)
    }

    @HealthDataCategoryInt
    fun fromHealthPermissionType(type: HealthPermissionType): Int {
        for (category in HEALTH_DATA_CATEGORIES) {
            if (category.healthPermissionTypes().contains(type)) {
                return category
            }
        }
        throw IllegalArgumentException("No Category for permission type $type")
    }
}

/** Permission groups for each {@link HealthDataCategory}. */
private object CategoriesMappers {
    val ACTIVITY_PERMISSION_GROUPS =
        listOf(
            HealthPermissionType.ACTIVE_CALORIES_BURNED,
            HealthPermissionType.DISTANCE,
            HealthPermissionType.ELEVATION_GAINED,
            HealthPermissionType.EXERCISE,
            HealthPermissionType.EXERCISE_ROUTE,
            HealthPermissionType.FLOORS_CLIMBED,
            HealthPermissionType.POWER,
            HealthPermissionType.SPEED,
            HealthPermissionType.STEPS,
            HealthPermissionType.TOTAL_CALORIES_BURNED,
            HealthPermissionType.VO2_MAX,
            HealthPermissionType.WHEELCHAIR_PUSHES,
        )

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
            HealthPermissionType.INTERMENSTRUAL_BLEEDING,
            HealthPermissionType.MENSTRUATION,
            HealthPermissionType.OVULATION_TEST,
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
            HealthPermissionType.RESTING_HEART_RATE,
            HealthPermissionType.SKIN_TEMPERATURE)
}

/** List of available Health data categories. */
val HEALTH_DATA_CATEGORIES =
    listOf(
        HealthDataCategory.ACTIVITY,
        HealthDataCategory.BODY_MEASUREMENTS,
        HealthDataCategory.CYCLE_TRACKING,
        HealthDataCategory.NUTRITION,
        HealthDataCategory.SLEEP,
        HealthDataCategory.VITALS,
    )

/** Denotes that the annotated [Integer] represents a [HealthDataCategory]. */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.FIELD,
    AnnotationTarget.TYPE)
annotation class HealthDataCategoryInt
