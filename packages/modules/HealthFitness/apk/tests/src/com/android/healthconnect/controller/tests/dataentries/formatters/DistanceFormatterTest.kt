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
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.units.Length
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.DistanceFormatter
import com.android.healthconnect.controller.dataentries.units.DistanceUnit.KILOMETERS
import com.android.healthconnect.controller.dataentries.units.DistanceUnit.MILES
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
class DistanceFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: DistanceFormatter
    @Inject lateinit var preferences: UnitPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
    }

    @Test
    fun formatValue_metricUnit_returnsCorrectValue() = runBlocking {
        preferences.setDistanceUnit(KILOMETERS)
        val record = getDistanceRecord(10087.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("10.087 km")
    }

    @Test
    fun formatValue_imperialUnit_returnsCorrectValue() = runBlocking {
        preferences.setDistanceUnit(MILES)
        val record = getDistanceRecord(9088.0)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("5.647 miles")
    }

    @Test
    fun formatValueA11y_metricUnit_returnsCorrectValue() = runBlocking {
        preferences.setDistanceUnit(KILOMETERS)
        val record = getDistanceRecord(1007.0)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("1.007 kilometers")
    }

    @Test
    fun formatValueA11y_imperialUnit_returnsCorrectValue() = runBlocking {
        preferences.setDistanceUnit(MILES)
        val record = getDistanceRecord(1009.0)

        assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("0.627 miles")
    }

    private fun getDistanceRecord(lengthInMeters: Double): DistanceRecord {
        return DistanceRecord.Builder(
                getMetaData(), NOW, NOW.plusSeconds(1), Length.fromMeters(lengthInMeters))
            .build()
    }
}
