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
import android.health.connect.datatypes.NutritionRecord
import android.health.connect.datatypes.units.Energy.*
import android.health.connect.datatypes.units.Mass.*
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.NutritionFormatter
import com.android.healthconnect.controller.dataentries.units.EnergyUnit
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
class NutritionFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: NutritionFormatter
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
    fun formatValue_noData() = runBlocking {
        val record = getBuilder().build()
        assertThat(formatter.formatValue(record, preferences)).isEqualTo("-")
    }

    @Test
    fun formatValue_formatsMass() = runBlocking {
        val record = getBuilder().setCaffeine(fromGrams(32.0)).build()

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Caffeine: 32 g")
    }

    @Test
    fun formatA11yValue_formatsMass() = runBlocking {
        val record = getBuilder().setCaffeine(fromGrams(32.0)).build()

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("Caffeine: 32 grams")
    }

    @Test
    fun formatValue_kj_formatsEnergy() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.KILOJOULE)
        val record = getBuilder().setEnergy(fromCalories(1234567.0)).build()

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Energy: 5,165 kJ")
    }

    @Test
    fun formatA11yValue_kj_formatsMass() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.KILOJOULE)
        val record = getBuilder().setEnergy(fromCalories(1234567.0)).build()

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("Energy: 5,165 kilojoules")
    }

    @Test
    fun formatValue_cal_formatsEnergy() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.CALORIE)
        val record = getBuilder().setEnergy(fromCalories(295000.0)).build()

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Energy: 295 Cal")
    }

    @Test
    fun formatA11yValue_cal_formatsMass() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.CALORIE)
        val record = getBuilder().setEnergy(fromCalories(295000.0)).build()

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("Energy: 295 calories")
    }

    @Test
    fun formatValue_formatsAllFields() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.CALORIE)
        val record =
            getBuilder()
                .setMealName("Custom meal")
                .setBiotin(fromGrams(4.0))
                .setCaffeine(fromGrams(12.0))
                .setCalcium(fromGrams(5.0))
                .setEnergyFromFat(fromCalories(30000.0))
                .setEnergy(fromCalories(30000.0))
                .setChloride(fromGrams(11.0))
                .setCholesterol(fromGrams(20.0))
                .setChromium(fromGrams(10.0))
                .setCopper(fromGrams(20.0))
                .setDietaryFiber(fromGrams(34.0))
                .setFolate(fromGrams(100.0))
                .setFolicAcid(fromGrams(90.0))
                .setIodine(fromGrams(100.0))
                .setIron(fromGrams(30.0))
                .setMagnesium(fromGrams(30.0))
                .setManganese(fromGrams(41.0))
                .setMolybdenum(fromGrams(2.0))
                .setMonounsaturatedFat(fromGrams(40.0))
                .setNiacin(fromGrams(20.0))
                .setPantothenicAcid(fromGrams(56.0))
                .setPhosphorus(fromGrams(67.0))
                .setPolyunsaturatedFat(fromGrams(67.0))
                .setPotassium(fromGrams(23.0))
                .setProtein(fromGrams(89.0))
                .setRiboflavin(fromGrams(22.0))
                .setSaturatedFat(fromGrams(45.0))
                .setSelenium(fromGrams(43.0))
                .setSodium(fromGrams(22.0))
                .setSugar(fromGrams(32.0))
                .setThiamin(fromGrams(98.0))
                .setTotalCarbohydrate(fromGrams(98.0))
                .setTotalFat(fromGrams(23.0))
                .setTransFat(fromGrams(23.0))
                .setUnsaturatedFat(fromGrams(12.0))
                .setVitaminA(fromGrams(40.0))
                .setVitaminB12(fromGrams(50.0))
                .setVitaminB6(fromGrams(10.0))
                .setVitaminC(fromGrams(60.0))
                .setVitaminD(fromGrams(70.0))
                .setVitaminE(fromGrams(80.0))
                .setVitaminK(fromGrams(90.0))
                .setZinc(fromGrams(12.0))
                .build()
        assertThat(formatter.formatValue(record, preferences))
            .isEqualTo(
                "Name: Custom meal\n" +
                    "Biotin: 4 g\n" +
                    "Caffeine: 12 g\n" +
                    "Calcium: 5 g\n" +
                    "Chloride: 11 g\n" +
                    "Cholesterol: 20 g\n" +
                    "Chromium: 10 g\n" +
                    "Copper: 20 g\n" +
                    "Dietary fiber: 34 g\n" +
                    "Energy: 30 Cal\n" +
                    "Energy from fat: 30 Cal\n" +
                    "Folate: 100 g\n" +
                    "Folic acid: 90 g\n" +
                    "Iodine: 100 g\n" +
                    "Iron: 30 g\n" +
                    "Magnesium: 30 g\n" +
                    "manganese: 41 g\n" +
                    "Molybdenum: 2 g\n" +
                    "Monounsaturated fat: 40 g\n" +
                    "Niacin: 20 g\n" +
                    "Pantothenic acid: 56 g\n" +
                    "Phosphorus: 67 g\n" +
                    "Polyunsaturated fat: 67 g\n" +
                    "Potassium: 23 g\n" +
                    "Riboflavin: 22 g\n" +
                    "Saturated fat: 45 g\n" +
                    "Selenium: 43 g\n" +
                    "Sodium: 22 g\n" +
                    "Sugar: 32 g\n" +
                    "Thiamin: 98 g\n" +
                    "Total carbohydrate: 98 g\n" +
                    "Total fat: 23 g\n" +
                    "Trans fat: 23 g\n" +
                    "Unsaturated fat: 12 g\n" +
                    "Vitamin A: 40 g\n" +
                    "Vitamin B12: 50 g\n" +
                    "Vitamin B6: 10 g\n" +
                    "Vitamin C: 60 g\n" +
                    "Vitamin D: 70 g\n" +
                    "Vitamin E: 80 g\n" +
                    "Vitamin K: 90 g\n" +
                    "Zinc: 12 g")
    }

    private fun getBuilder(): NutritionRecord.Builder {
        return NutritionRecord.Builder(getMetaData(), NOW, NOW.plusSeconds(10))
    }
}
