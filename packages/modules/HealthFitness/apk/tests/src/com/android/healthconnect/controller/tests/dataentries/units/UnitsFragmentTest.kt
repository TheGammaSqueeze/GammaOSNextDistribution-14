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
package com.android.healthconnect.controller.tests.dataentries.units

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.preference.PreferenceManager.getDefaultSharedPreferencesName
import androidx.core.os.bundleOf
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.units.DistanceUnit
import com.android.healthconnect.controller.dataentries.units.EnergyUnit
import com.android.healthconnect.controller.dataentries.units.HeightUnit
import com.android.healthconnect.controller.dataentries.units.TemperatureUnit
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.dataentries.units.UnitPreferences.Companion.DEFAULT_DISTANCE_UNIT
import com.android.healthconnect.controller.dataentries.units.UnitPreferences.Companion.DEFAULT_ENERGY_UNIT
import com.android.healthconnect.controller.dataentries.units.UnitPreferences.Companion.DEFAULT_HEIGHT_UNIT
import com.android.healthconnect.controller.dataentries.units.UnitPreferences.Companion.DEFAULT_TEMPERATURE_UNIT
import com.android.healthconnect.controller.dataentries.units.UnitPreferences.Companion.DEFAULT_WEIGHT_UNIT
import com.android.healthconnect.controller.dataentries.units.UnitPreferencesStrings.getUnitLabel
import com.android.healthconnect.controller.dataentries.units.UnitsFragment
import com.android.healthconnect.controller.dataentries.units.WeightUnit
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.UnitsElement
import com.google.common.truth.Truth.*
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class UnitsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var context: Context
    @Inject lateinit var unitPreferences: UnitPreferences
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        val pref =
            context.getSharedPreferences(getDefaultSharedPreferencesName(context), MODE_PRIVATE)
        pref.edit().clear().apply()
        reset(healthConnectLogger)
    }

    @Test
    fun unitsScreen_starts() {
        launchFragment<UnitsFragment>(bundleOf())

        onView(withText("Height")).check(matches(isDisplayed()))
        onView(withText("Weight")).check(matches(isDisplayed()))
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Energy")).check(matches(isDisplayed()))
        onView(withText("Temperature")).check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.UNITS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(UnitsElement.CHANGE_UNITS_HEIGHT_BUTTON)
        verify(healthConnectLogger).logImpression(UnitsElement.CHANGE_UNITS_WEIGHT_BUTTON)
        verify(healthConnectLogger).logImpression(UnitsElement.CHANGE_UNITS_DISTANCE_BUTTON)
        verify(healthConnectLogger).logImpression(UnitsElement.CHANGE_UNITS_ENERGY_BUTTON)
        verify(healthConnectLogger).logImpression(UnitsElement.CHANGE_UNITS_TEMPERATURE_BUTTON)
    }

    @Test
    fun unitsScreen_showsDefaultSettings() {
        setUnitsDefault()

        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(getUnitLabel(DEFAULT_HEIGHT_UNIT))).check(matches(isDisplayed()))
        onView(withText(getUnitLabel(DEFAULT_DISTANCE_UNIT))).check(matches(isDisplayed()))
        onView(withText(getUnitLabel(DEFAULT_ENERGY_UNIT))).check(matches(isDisplayed()))
        onView(withText(getUnitLabel(DEFAULT_TEMPERATURE_UNIT))).check(matches(isDisplayed()))
        onView(withText(getUnitLabel(DEFAULT_WEIGHT_UNIT))).check(matches(isDisplayed()))
    }

    @Test
    fun unitsScreen_setHeightUnit_updatesValue() {
        unitPreferences.setHeightUnit(HeightUnit.FEET)

        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(getUnitLabel(HeightUnit.FEET))).check(matches(isDisplayed()))
    }

    @Test
    fun unitsScreen_setWeightUnit_updatesValue() {
        unitPreferences.setWeightUnit(WeightUnit.STONE)

        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(getUnitLabel(WeightUnit.STONE))).check(matches(isDisplayed()))
    }

    @Test
    fun unitsScreen_setTemperatureUnit_updatesValue() {
        unitPreferences.setTemperatureUnit(TemperatureUnit.KELVIN)

        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(getUnitLabel(TemperatureUnit.KELVIN))).check(matches(isDisplayed()))
    }

    @Test
    fun unitsScreen_setDistanceUnit_updatesValue() {
        unitPreferences.setDistanceUnit(DistanceUnit.MILES)

        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(getUnitLabel(DistanceUnit.MILES))).check(matches(isDisplayed()))
    }

    @Test
    fun unitsScreen_setEnergyUnit_updatesValue() {
        unitPreferences.setEnergyUnit(EnergyUnit.KILOJOULE)

        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(getUnitLabel(EnergyUnit.KILOJOULE))).check(matches(isDisplayed()))
    }

    @Test
    fun unitsScreen_modifiesHeight_updatesValue() {
        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(R.string.height_uppercase_label)).perform(click())
        onView(withText(R.string.height_unit_feet_label)).perform(click())

        assertThat(unitPreferences.getHeightUnit()).isEqualTo(HeightUnit.FEET)
    }

    @Test
    fun unitsScreen_modifiesDistance_updatesValue() {
        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(R.string.distance_uppercase_label)).perform(click())
        onView(withText(R.string.distance_unit_miles_label)).perform(click())

        assertThat(unitPreferences.getDistanceUnit()).isEqualTo(DistanceUnit.MILES)
    }

    @Test
    fun unitsScreen_modifiesWeight_updatesValue() {
        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(R.string.weight_uppercase_label)).perform(click())
        onView(withText(R.string.weight_unit_kilogram_label)).perform(click())

        assertThat(unitPreferences.getWeightUnit()).isEqualTo(WeightUnit.KILOGRAM)
    }

    @Test
    fun unitsScreen_modifiesEnergy_updatesValue() {
        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(R.string.energy_unit_title)).perform(click())
        onView(withText(R.string.energy_unit_kilojoule_label)).perform(click())

        assertThat(unitPreferences.getEnergyUnit()).isEqualTo(EnergyUnit.KILOJOULE)
    }

    @Test
    fun unitsScreen_modifiesTemperature_updatesValue() {
        launchFragment<UnitsFragment>(bundleOf())

        onView(withText(R.string.temperature_unit_title)).perform(click())
        onView(withText(R.string.temperature_unit_kelvin_label)).perform(click())

        assertThat(unitPreferences.getTemperatureUnit()).isEqualTo(TemperatureUnit.KELVIN)
    }

    private fun setUnitsDefault() {
        unitPreferences.setEnergyUnit(DEFAULT_ENERGY_UNIT)
        unitPreferences.setTemperatureUnit(DEFAULT_TEMPERATURE_UNIT)
        unitPreferences.setWeightUnit(DEFAULT_WEIGHT_UNIT)
        unitPreferences.setHeightUnit(DEFAULT_HEIGHT_UNIT)
        unitPreferences.setDistanceUnit(DEFAULT_DISTANCE_UNIT)
    }
}
