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
import android.health.connect.datatypes.BloodGlucoseRecord
import android.health.connect.datatypes.BloodGlucoseRecord.RelationToMealType.*
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_CAPILLARY_BLOOD
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_INTERSTITIAL_FLUID
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_PLASMA
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_SERUM
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_TEARS
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_UNKNOWN
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_WHOLE_BLOOD
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SpecimenSourceType
import android.health.connect.datatypes.MealType
import android.health.connect.datatypes.MealType.MEAL_TYPE_BREAKFAST
import android.health.connect.datatypes.MealType.MEAL_TYPE_DINNER
import android.health.connect.datatypes.MealType.MEAL_TYPE_LUNCH
import android.health.connect.datatypes.MealType.MEAL_TYPE_SNACK
import android.health.connect.datatypes.MealType.MealTypes
import android.health.connect.datatypes.units.BloodGlucose
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.BloodGlucoseFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
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
class BloodGlucoseFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: BloodGlucoseFormatter
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
    fun formatValue_zero() = runBlocking {
        val record = getRecord(0.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("0 mmol/L")
    }

    @Test
    fun formatA11yValue_zero() = runBlocking {
        val record = getRecord(0.0)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("0 millimoles per liter")
    }

    @Test
    fun formatValue_one() = runBlocking {
        val record = getRecord(1.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("1 mmol/L")
    }

    @Test
    fun formatA11yValue_one() = runBlocking {
        val record = getRecord(1.0)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("1 millimole per liter")
    }

    @Test
    fun formatValue_normal() = runBlocking {
        val record = getRecord(25.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("25 mmol/L")
    }

    @Test
    fun formatA11yValue_normal() = runBlocking {
        val record = getRecord(25.0)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter")
    }

    @Test
    fun formatValue_dinner_showsMealTypes() = runBlocking {
        val record = getRecord(level = 25.0, mealType = MEAL_TYPE_DINNER)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Dinner")
    }

    @Test
    fun formatValue_snack_showsMealTypes() = runBlocking {
        val record = getRecord(level = 25.0, mealType = MEAL_TYPE_SNACK)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Snack")
    }

    @Test
    fun formatValue_lunch_showsMealTypes() = runBlocking {
        val record = getRecord(level = 25.0, mealType = MEAL_TYPE_LUNCH)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Lunch")
    }

    @Test
    fun formatValue_breakfast_showsMealTypes() = runBlocking {
        val record = getRecord(level = 25.0, mealType = MEAL_TYPE_BREAKFAST)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Breakfast")
    }

    @Test
    fun formatValue_beforeMeal_showsRelationToMeal() = runBlocking {
        val record = getRecord(level = 25.0, relationToMeal = RELATION_TO_MEAL_BEFORE_MEAL)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Before meal")
    }

    @Test
    fun formatValue_afterMeal_showsRelationToMeal() = runBlocking {
        val record = getRecord(level = 25.0, relationToMeal = RELATION_TO_MEAL_AFTER_MEAL)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter After meal")
    }

    @Test
    fun formatValue_fasting_showsRelationToMeal() = runBlocking {
        val record = getRecord(level = 25.0, relationToMeal = RELATION_TO_MEAL_FASTING)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Fasting")
    }

    @Test
    fun formatValue_general_showsRelationToMeal() = runBlocking {
        val record = getRecord(level = 25.0, relationToMeal = RELATION_TO_MEAL_GENERAL)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter General")
    }

    @Test
    fun formatValue_wholeBlood_showsSpecimenSource() = runBlocking {
        val record = getRecord(level = 25.0, source = SPECIMEN_SOURCE_WHOLE_BLOOD)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Whole blood")
    }

    @Test
    fun formatValue_serum_showsSpecimenSource() = runBlocking {
        val record = getRecord(level = 25.0, source = SPECIMEN_SOURCE_SERUM)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Serum")
    }

    @Test
    fun formatValue_tears_showsSpecimenSource() = runBlocking {
        val record = getRecord(level = 25.0, source = SPECIMEN_SOURCE_TEARS)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Tears")
    }

    @Test
    fun formatValue_plasma_showsSpecimenSource() = runBlocking {
        val record = getRecord(level = 25.0, source = SPECIMEN_SOURCE_PLASMA)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Plasma")
    }

    @Test
    fun formatValue_capillaryBlood_showsSpecimenSource() = runBlocking {
        val record = getRecord(level = 25.0, source = SPECIMEN_SOURCE_CAPILLARY_BLOOD)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Capillary blood")
    }

    @Test
    fun formatValue_interstitialFluid_showsSpecimenSource() = runBlocking {
        val record = getRecord(level = 25.0, source = SPECIMEN_SOURCE_INTERSTITIAL_FLUID)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Interstitial fluid")
    }

    @Test
    fun formatValue_testAll() = runBlocking {
        val record =
            getRecord(
                level = 25.0,
                source = SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                relationToMeal = RELATION_TO_MEAL_FASTING,
                mealType = MEAL_TYPE_LUNCH)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("25 millimoles per liter Interstitial fluid Lunch Fasting")
    }

    private fun getRecord(
        level: Double,
        @SpecimenSourceType source: Int = SPECIMEN_SOURCE_UNKNOWN,
        @RelationToMealTypes relationToMeal: Int = RELATION_TO_MEAL_UNKNOWN,
        @MealTypes mealType: Int = MealType.MEAL_TYPE_UNKNOWN
    ): BloodGlucoseRecord {
        return BloodGlucoseRecord.Builder(
                getMetaData(),
                NOW,
                source,
                BloodGlucose.fromMillimolesPerLiter(level),
                relationToMeal,
                mealType)
            .build()
    }
}
