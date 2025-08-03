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

import android.health.connect.datatypes.BasalBodyTemperatureRecord
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_ARMPIT
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_EAR
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FINGER
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FOREHEAD
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_MOUTH
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_RECTUM
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TEMPORAL_ARTERY
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TOE
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_UNKNOWN
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_VAGINA
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST
import android.health.connect.datatypes.units.Temperature
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.BasalBodyTemperatureFormatter
import com.android.healthconnect.controller.dataentries.units.TemperatureUnit
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
class BasalBodyTemperatureFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: BasalBodyTemperatureFormatter
    @Inject lateinit var preferences: UnitPreferences

    @Before
    fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        InstrumentationRegistry.getInstrumentation().context.setLocale(Locale.US)
        hiltRule.inject()
    }

    @Test
    fun formatValue_celsius() = runBlocking {
        preferences.setTemperatureUnit(TemperatureUnit.CELSIUS)

        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 0.0), preferences))
            .isEqualTo("0 ℃")
        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 1.0), preferences))
            .isEqualTo("1 ℃")
        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 43.3), preferences))
            .isEqualTo("43.3 ℃")
    }

    @Test
    fun formatA11yValue_celsius() = runBlocking {
        preferences.setTemperatureUnit(TemperatureUnit.CELSIUS)

        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 0.0), preferences))
            .isEqualTo("0 degrees Celsius")
        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 1.0), preferences))
            .isEqualTo("1 degree Celsius")
        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 43.3), preferences))
            .isEqualTo("43.3 degrees Celsius")
    }

    @Test
    fun formatValue_kelvin() = runBlocking {
        preferences.setTemperatureUnit(TemperatureUnit.KELVIN)

        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 0.0), preferences))
            .isEqualTo("273.15 K")
        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 1.0), preferences))
            .isEqualTo("274.15 K")
        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 43.3), preferences))
            .isEqualTo("316.45 K")
    }

    @Test
    fun formatA11yValue_kelvin() = runBlocking {
        preferences.setTemperatureUnit(TemperatureUnit.KELVIN)

        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 0.0), preferences))
            .isEqualTo("273.15 kelvins")
        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 1.0), preferences))
            .isEqualTo("274.15 kelvins")
        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 43.3), preferences))
            .isEqualTo("316.45 kelvins")
    }

    @Test
    fun formatValue_fahrenheit() = runBlocking {
        preferences.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)

        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 0.0), preferences))
            .isEqualTo("32 ℉")
        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 1.0), preferences))
            .isEqualTo("33.8 ℉")
        assertThat(
                formatter.formatValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 43.3), preferences))
            .isEqualTo("109.94 ℉")
    }

    @Test
    fun formatA11yValue_fahrenheit() = runBlocking {
        preferences.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)

        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 0.0), preferences))
            .isEqualTo("32 degrees Fahrenheit")
        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 1.0), preferences))
            .isEqualTo("33.8 degrees Fahrenheit")
        assertThat(
                formatter.formatA11yValue(
                    getRecord(location = MEASUREMENT_LOCATION_UNKNOWN, 43.3), preferences))
            .isEqualTo("109.94 degrees Fahrenheit")
    }

    @Test
    fun formatValue_formatsLocations() = runBlocking {
        preferences.setTemperatureUnit(TemperatureUnit.CELSIUS)

        assertThat(formatter.formatValue(getRecord(MEASUREMENT_LOCATION_EAR, 1.0), preferences))
            .isEqualTo("1 ℃ Ear")
        assertThat(formatter.formatValue(getRecord(MEASUREMENT_LOCATION_ARMPIT, 1.0), preferences))
            .isEqualTo("1 ℃ Armpit")
        assertThat(
                formatter.formatValue(getRecord(MEASUREMENT_LOCATION_FOREHEAD, 1.0), preferences))
            .isEqualTo("1 ℃ Forehead")
        assertThat(formatter.formatValue(getRecord(MEASUREMENT_LOCATION_MOUTH, 1.0), preferences))
            .isEqualTo("1 ℃ Mouth")
        assertThat(formatter.formatValue(getRecord(MEASUREMENT_LOCATION_FINGER, 1.0), preferences))
            .isEqualTo("1 ℃ Finger")
        assertThat(formatter.formatValue(getRecord(MEASUREMENT_LOCATION_RECTUM, 1.0), preferences))
            .isEqualTo("1 ℃ Rectum")
        assertThat(
                formatter.formatValue(
                    getRecord(MEASUREMENT_LOCATION_TEMPORAL_ARTERY, 1.0), preferences))
            .isEqualTo("1 ℃ Temporal artery")
        assertThat(formatter.formatValue(getRecord(MEASUREMENT_LOCATION_TOE, 1.0), preferences))
            .isEqualTo("1 ℃ Toe")
        assertThat(formatter.formatValue(getRecord(MEASUREMENT_LOCATION_VAGINA, 1.0), preferences))
            .isEqualTo("1 ℃ Vagina")
        assertThat(formatter.formatValue(getRecord(MEASUREMENT_LOCATION_WRIST, 1.0), preferences))
            .isEqualTo("1 ℃ Wrist")
    }

    private fun getRecord(location: Int, temp: Double): BasalBodyTemperatureRecord {
        return BasalBodyTemperatureRecord.Builder(
                getMetaData(), NOW, location, Temperature.fromCelsius(temp))
            .build()
    }
}
