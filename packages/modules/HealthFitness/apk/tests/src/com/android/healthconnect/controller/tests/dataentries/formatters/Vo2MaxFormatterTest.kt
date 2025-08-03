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
import android.health.connect.datatypes.Vo2MaxRecord
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.*
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.Vo2MaxFormatter
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
class Vo2MaxFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: Vo2MaxFormatter
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

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("0 mL/(kg·min)")
    }

    @Test
    fun formatA11yValue_zero() = runBlocking {
        val record = getRecord(0.0)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("0 milliliters of oxygen per kilogram of body mass per minute")
    }

    @Test
    fun formatA11yValue_one() = runBlocking {
        val record = getRecord(1.0)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("1 milliliter of oxygen per kilogram of body mass per minute")
    }

    @Test
    fun formatValue() = runBlocking {
        val record = getRecord(17.3)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("17.3 mL/(kg·min)")
    }

    @Test
    fun formatA11yValue() = runBlocking {
        val record = getRecord(17.3)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("17.3 milliliters of oxygen per kilogram of body mass per minute")
    }

    @Test
    fun formatValue_hrRation_showsMethod() = runBlocking {
        val record = getRecord(17.3, MEASUREMENT_METHOD_HEART_RATE_RATIO)

        assertThat(formatter.formatValue(record, preferences))
            .isEqualTo("17.3 mL/(kg·min) Heart rate ratio")
    }

    @Test
    fun formatValue_metabolicCart_showsMethod() = runBlocking {
        val record = getRecord(17.3, MEASUREMENT_METHOD_METABOLIC_CART)

        assertThat(formatter.formatValue(record, preferences))
            .isEqualTo("17.3 mL/(kg·min) Metabolic cart")
    }

    @Test
    fun formatValue_cooperTest_showsMethod() = runBlocking {
        val record = getRecord(17.3, MEASUREMENT_METHOD_COOPER_TEST)

        assertThat(formatter.formatValue(record, preferences))
            .isEqualTo("17.3 mL/(kg·min) Cooper test")
    }

    @Test
    fun formatValue_multistageFitnessTest_showsMethod() = runBlocking {
        val record = getRecord(17.3, MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST)

        assertThat(formatter.formatValue(record, preferences))
            .isEqualTo("17.3 mL/(kg·min) Multistage fitness test")
    }

    @Test
    fun formatValue_rockportFitnessTest_showsMethod() = runBlocking {
        val record = getRecord(17.3, MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST)

        assertThat(formatter.formatValue(record, preferences))
            .isEqualTo("17.3 mL/(kg·min) Rockport fitness test")
    }

    private fun getRecord(
        value: Double,
        @Vo2MaxMeasurementMethodTypes method: Int = MEASUREMENT_METHOD_OTHER
    ): Vo2MaxRecord {
        return Vo2MaxRecord.Builder(getMetaData(), NOW, method, value).build()
    }
}
