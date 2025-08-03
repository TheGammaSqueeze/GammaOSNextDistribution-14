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
import android.health.connect.datatypes.CervicalMucusRecord
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_CREAMY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_DRY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_EGG_WHITE
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_STICKY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_UNKNOWN
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_WATERY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.CervicalMucusAppearances
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusSensation.CervicalMucusSensations
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusSensation.SENSATION_UNKNOWN
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.*
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.CervicalMucusFormatter
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
class CervicalMucusFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: CervicalMucusFormatter
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
    fun formatValue_dry_showsAppearance() = runBlocking {
        val record = getRecord(appearance = APPEARANCE_DRY)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Dry")
    }

    @Test
    fun formatValue_sticky_showsAppearance() = runBlocking {
        val record = getRecord(appearance = APPEARANCE_STICKY)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Sticky")
    }

    @Test
    fun formatValue_creamy_showsAppearance() = runBlocking {
        val record = getRecord(appearance = APPEARANCE_CREAMY)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Creamy")
    }
    @Test
    fun formatValue_watery_showsAppearance() = runBlocking {
        val record = getRecord(appearance = APPEARANCE_WATERY)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Watery")
    }
    @Test
    fun formatValue_Sticky_showsAppearance() = runBlocking {
        val record = getRecord(appearance = APPEARANCE_EGG_WHITE)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Egg white")
    }

    private fun getRecord(
        @CervicalMucusSensations sensation: Int = SENSATION_UNKNOWN,
        @CervicalMucusAppearances appearance: Int = APPEARANCE_UNKNOWN
    ): CervicalMucusRecord {
        return CervicalMucusRecord.Builder(getMetaData(), NOW, sensation, appearance).build()
    }
}
