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
import android.health.connect.datatypes.BasalMetabolicRateRecord
import android.health.connect.datatypes.units.Power
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.BasalMetabolicRateFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.getBasalMetabolicRateRecord
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.*
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
class BasalMetabolicRateFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: BasalMetabolicRateFormatter
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
    fun formatValue_returnsPowerValue() {
        val record = getBasalMetabolicRateRecord(calories = 1548)

        runBlocking {
            assertThat(formatter.formatValue(record, preferences)).isEqualTo("1,548 Cal")
        }
    }

    @Test
    fun formatValue_watts_returnsPowerValue() {
        val record = getBasalMetabolicRateRecord(watts = 82.8688)

        runBlocking {
            assertThat(formatter.formatValue(record, preferences)).isEqualTo("1,711 Cal")
        }
    }

    @Test
    fun formatA11yValue_pluralValue_returnsA11yPowerValues() {
        val record = getBasalMetabolicRateRecord(calories = 1720)

        runBlocking {
            assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("1,720 calories")
        }
    }

    @Test
    fun formatA11yValue_singleValue_returnsA11yPowerValues() {
        val record = getBasalMetabolicRateRecord(calories = 1)

        runBlocking {
            assertThat(formatter.formatA11yValue(record, preferences)).isEqualTo("1 calorie")
        }
    }

    private fun getBasalMetabolicRateRecord(watts: Double): BasalMetabolicRateRecord {
        return BasalMetabolicRateRecord.Builder(getMetaData(), NOW, Power.fromWatts(watts)).build()
    }
}
