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
import android.health.connect.datatypes.HeightRecord
import android.health.connect.datatypes.units.Length
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.HeightFormatter
import com.android.healthconnect.controller.dataentries.units.HeightUnit.CENTIMETERS
import com.android.healthconnect.controller.dataentries.units.HeightUnit.FEET
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
class HeightFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: HeightFormatter
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
    fun format_withZeroValue_withCentimetersUnit() = runBlocking {
        val height = getHeightRecord(0.0)
        preferences.setHeightUnit(CENTIMETERS)
        assertThat(formatter.formatValue(height, preferences)).isEqualTo("0 cm")
        assertThat(formatter.formatA11yValue(height, preferences)).isEqualTo("0 centimeters")
    }

    @Test
    fun format_withZeroValue_withFeetUnit() = runBlocking {
        val height = getHeightRecord(0.0)
        preferences.setHeightUnit(FEET)
        assertThat(formatter.formatValue(height, preferences)).isEqualTo("0′0″")
        assertThat(formatter.formatA11yValue(height, preferences)).isEqualTo("0 feet 0 inches")
    }

    @Test
    fun format_withFractionsValue_withCentimetersUnit() = runBlocking {
        val height = getHeightRecord(0.01)
        preferences.setHeightUnit(CENTIMETERS)
        assertThat(formatter.formatValue(height, preferences)).isEqualTo("1 cm")
        assertThat(formatter.formatA11yValue(height, preferences)).isEqualTo("1 centimeter")
    }

    @Test
    fun format_withFractionsValue_withFeetUnit() = runBlocking {
        val height = getHeightRecord(0.01)
        preferences.setHeightUnit(FEET)
        assertThat(formatter.formatValue(height, preferences)).isEqualTo("0′0″")
        assertThat(formatter.formatA11yValue(height, preferences)).isEqualTo("0 feet 0 inches")
    }

    @Test
    fun format_withRegularValue_withCentimetersUnit() = runBlocking {
        val height = getHeightRecord(1.75)
        preferences.setHeightUnit(CENTIMETERS)
        assertThat(formatter.formatValue(height, preferences)).isEqualTo("175 cm")
        assertThat(formatter.formatA11yValue(height, preferences)).isEqualTo("175 centimeters")
    }

    @Test
    fun format_withRegularValue_withfeetUnit() = runBlocking {
        val height = getHeightRecord(1.75)
        preferences.setHeightUnit(FEET)
        assertThat(formatter.formatValue(height, preferences)).isEqualTo("5′9″")
        assertThat(formatter.formatA11yValue(height, preferences)).isEqualTo("5 feet 9 inches")
    }

    private fun getHeightRecord(heightInMeters: Double): HeightRecord {
        return HeightRecord.Builder(getMetaData(), NOW, Length.fromMeters(heightInMeters)).build()
    }
}
