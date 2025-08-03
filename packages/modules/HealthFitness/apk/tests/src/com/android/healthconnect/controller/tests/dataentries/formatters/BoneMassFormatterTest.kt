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
import android.health.connect.datatypes.BoneMassRecord
import android.health.connect.datatypes.units.Mass
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.BoneMassFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.dataentries.units.WeightUnit
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
class BoneMassFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: BoneMassFormatter

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
    fun formatValue_kilogram_zero() = runBlocking {
        preferences.setWeightUnit(WeightUnit.KILOGRAM)
        val record = getRecord(0.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("0 kg")
    }

    @Test
    fun formatValue_kilogram_fraction() = runBlocking {
        preferences.setWeightUnit(WeightUnit.KILOGRAM)
        val record = getRecord(0.2)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("0.2 kg")
    }

    fun formatA11yValue_kilogram_zero() = runBlocking {
        preferences.setWeightUnit(WeightUnit.KILOGRAM)
        val record = getRecord(0.0)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("0 kilograms")
    }

    @Test
    fun formatA11yValue_kilogram_fraction() = runBlocking {
        preferences.setWeightUnit(WeightUnit.KILOGRAM)
        val record = getRecord(0.2)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("0.2 kilograms")
    }

    @Test
    fun formatValue_pound_zero() = runBlocking {
        preferences.setWeightUnit(WeightUnit.POUND)
        val record = getRecord(0.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("0 lb")
    }

    @Test
    fun formatValue_pound_fraction() = runBlocking {
        preferences.setWeightUnit(WeightUnit.POUND)
        val record = getRecord(0.2)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("0.4 lb")
    }

    fun formatA11yValue_pound_zero() = runBlocking {
        preferences.setWeightUnit(WeightUnit.POUND)
        val record = getRecord(0.0)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("0 pounds")
    }

    @Test
    fun formatA11yValue_pound_fraction() = runBlocking {
        preferences.setWeightUnit(WeightUnit.POUND)
        val record = getRecord(0.2)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("0.4 pounds")
    }

    @Test
    fun formatValue_stone_zero() = runBlocking {
        preferences.setWeightUnit(WeightUnit.STONE)
        val record = getRecord(0.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("0 st")
    }

    @Test
    fun formatValue_stone_fraction() = runBlocking {
        preferences.setWeightUnit(WeightUnit.STONE)
        val record = getRecord(13.5)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("2 st 1.8 lb")
    }

    fun formatA11yValue_stone_zero() = runBlocking {
        preferences.setWeightUnit(WeightUnit.STONE)
        val record = getRecord(0.0)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("0 stones")
    }

    @Test
    fun formatA11yValue_stone_fraction() = runBlocking {
        preferences.setWeightUnit(WeightUnit.STONE)
        val record = getRecord(13.5)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("2 stones 1.8 pounds")
    }

    private fun getRecord(massInKg: Double): BoneMassRecord {
        return BoneMassRecord.Builder(getMetaData(), NOW, Mass.fromGrams(massInKg * 1000)).build()
    }
}
