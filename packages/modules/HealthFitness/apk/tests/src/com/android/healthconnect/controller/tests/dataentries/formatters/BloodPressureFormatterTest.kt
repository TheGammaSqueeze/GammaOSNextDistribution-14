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
import android.health.connect.datatypes.BloodPressureRecord
import android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_UPPER_ARM
import android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BLOOD_PRESSURE_MEASUREMENT_LOCATION_UNKNOWN
import android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BloodPressureMeasurementLocations
import android.health.connect.datatypes.BloodPressureRecord.BodyPosition.BODY_POSITION_STANDING_UP
import android.health.connect.datatypes.BloodPressureRecord.BodyPosition.BODY_POSITION_UNKNOWN
import android.health.connect.datatypes.BloodPressureRecord.BodyPosition.BodyPositionType
import android.health.connect.datatypes.units.Pressure
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.BloodPressureFormatter
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
class BloodPressureFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: BloodPressureFormatter
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
    fun formatValue() = runBlocking {
        val record = getRecord(systolic = 123.1, diastolic = 81.7)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("123/82 mmHg")
    }

    @Test
    fun formatA11yValue() = runBlocking {
        val record = getRecord(systolic = 123.1, diastolic = 81.7)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("123/82 millimetre of mercury")
    }

    @Test
    fun formatValue_showsBodyPosition() = runBlocking {
        val record =
            getRecord(systolic = 123.1, diastolic = 81.7, position = BODY_POSITION_STANDING_UP)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("123/82 mmHg Standing up")
    }

    @Test
    fun formatA11yValue_showsBodyPosition() = runBlocking {
        val record =
            getRecord(systolic = 123.1, diastolic = 81.7, position = BODY_POSITION_STANDING_UP)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("123/82 millimetre of mercury Standing up")
    }

    @Test
    fun formatValue_showsLocation() = runBlocking {
        val record =
            getRecord(
                systolic = 123.1,
                diastolic = 81.7,
                location = BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_UPPER_ARM)

        assertThat(formatter.formatValue(record, preferences))
            .isEqualTo("123/82 mmHg Left upper arm")
    }

    @Test
    fun formatA11yValue_showsLocation() = runBlocking {
        val record =
            getRecord(
                systolic = 123.1,
                diastolic = 81.7,
                location = BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_UPPER_ARM)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("123/82 millimetre of mercury Left upper arm")
    }

    @Test
    fun formatValue_showsLocation_showsPosition() = runBlocking {
        val record =
            getRecord(
                systolic = 123.1,
                diastolic = 81.7,
                position = BODY_POSITION_STANDING_UP,
                location = BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_UPPER_ARM)

        assertThat(formatter.formatValue(record, preferences))
            .isEqualTo("123/82 mmHg Left upper arm Standing up")
    }

    @Test
    fun formatA11yValue_showsLocation_showsPosition() = runBlocking {
        val record =
            getRecord(
                systolic = 123.1,
                diastolic = 81.7,
                position = BODY_POSITION_STANDING_UP,
                location = BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_UPPER_ARM)

        assertThat(formatter.formatA11yValue(record, preferences))
            .isEqualTo("123/82 millimetre of mercury Left upper arm Standing up")
    }

    private fun getRecord(
        @BloodPressureMeasurementLocations
        location: Int = BLOOD_PRESSURE_MEASUREMENT_LOCATION_UNKNOWN,
        @BodyPositionType position: Int = BODY_POSITION_UNKNOWN,
        systolic: Double,
        diastolic: Double
    ): BloodPressureRecord {
        return BloodPressureRecord.Builder(
                getMetaData(),
                NOW,
                location,
                Pressure.fromMillimetersOfMercury(systolic),
                Pressure.fromMillimetersOfMercury(diastolic),
                position)
            .build()
    }
}
