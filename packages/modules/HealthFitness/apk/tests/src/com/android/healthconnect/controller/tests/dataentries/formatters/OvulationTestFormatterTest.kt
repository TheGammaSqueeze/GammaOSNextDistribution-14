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
import android.health.connect.datatypes.OvulationTestRecord
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult.OvulationTestResults
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult.RESULT_HIGH
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult.RESULT_NEGATIVE
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult.RESULT_POSITIVE
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.OvulationTestFormatter
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
class OvulationTestFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: OvulationTestFormatter
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
    fun formatValue_positive() = runBlocking {
        val record = getRecord(result = RESULT_POSITIVE)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Positive")
    }

    @Test
    fun formatValue_negative() = runBlocking {
        val record = getRecord(result = RESULT_NEGATIVE)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Negative")
    }

    @Test
    fun formatValue_high() = runBlocking {
        val record = getRecord(result = RESULT_HIGH)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("High")
    }

    private fun getRecord(
        @OvulationTestResults result: Int,
    ): OvulationTestRecord {
        return OvulationTestRecord.Builder(getMetaData(), NOW, result).build()
    }
}
