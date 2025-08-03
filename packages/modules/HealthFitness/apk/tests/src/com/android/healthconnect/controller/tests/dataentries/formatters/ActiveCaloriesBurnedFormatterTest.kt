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

import android.health.connect.datatypes.ActiveCaloriesBurnedRecord
import android.health.connect.datatypes.units.Energy
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.ActiveCaloriesBurnedFormatter
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
class ActiveCaloriesBurnedFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: ActiveCaloriesBurnedFormatter
    @Inject lateinit var preferences: UnitPreferences

    @Before
    fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        InstrumentationRegistry.getInstrumentation().context.setLocale(Locale.US)
        hiltRule.inject()
    }

    @Test
    fun formatValue_calories_returnsFormattedEnergy() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.CALORIE)
        val record = getActiveCaloriesBurnedRecord(calories = 3000.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("3 Cal")
    }

    @Test
    fun formatValue_kj_returnsFormattedEnergy() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.KILOJOULE)
        val record = getActiveCaloriesBurnedRecord(calories = 3107.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("13 kJ")
    }

    @Test
    fun formatA11yValue_calories_returnsFormattedEnergy() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.CALORIE)
        val record = getActiveCaloriesBurnedRecord(calories = 13000.0)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("13 calories")
    }

    @Test
    fun formatA11yValue_kj_returnsFormattedEnergy() = runBlocking {
        preferences.setEnergyUnit(EnergyUnit.KILOJOULE)
        val record = getActiveCaloriesBurnedRecord(calories = 3107.0)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("13 kilojoules")
    }

    private fun getActiveCaloriesBurnedRecord(calories: Double): ActiveCaloriesBurnedRecord {
        return ActiveCaloriesBurnedRecord.Builder(
                getMetaData(), NOW, NOW.plusSeconds(1), Energy.fromCalories(calories))
            .build()
    }
}
