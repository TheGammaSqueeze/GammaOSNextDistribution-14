/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.bluetooth.satellite.test

import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.android.server.bluetooth.Log
import com.android.server.bluetooth.satellite.SETTINGS_SATELLITE_MODE_ENABLED
import com.android.server.bluetooth.satellite.SETTINGS_SATELLITE_MODE_RADIOS
import com.android.server.bluetooth.satellite.initialize
import com.android.server.bluetooth.satellite.isOn
import com.android.server.bluetooth.test.disableMode
import com.android.server.bluetooth.test.disableSensitive
import com.android.server.bluetooth.test.enableMode
import com.android.server.bluetooth.test.enableSensitive
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModeListenerTest {
    companion object {
        internal fun setupSatelliteModeToOn(
            resolver: ContentResolver,
            looper: Looper,
            callback: (m: Boolean) -> Unit
        ) {
            enableSensitive(resolver, looper, SETTINGS_SATELLITE_MODE_RADIOS)
            enableMode(resolver, looper, SETTINGS_SATELLITE_MODE_ENABLED)

            initialize(looper, resolver, callback)
        }

        internal fun setupSatelliteModeToOff(resolver: ContentResolver, looper: Looper) {
            disableSensitive(resolver, looper, SETTINGS_SATELLITE_MODE_RADIOS)
            disableMode(resolver, looper, SETTINGS_SATELLITE_MODE_ENABLED)
        }
    }

    private val resolver: ContentResolver =
        ApplicationProvider.getApplicationContext<Context>().getContentResolver()
    @JvmField @Rule val testName = TestName()

    private val looper: Looper = Looper.getMainLooper()

    private lateinit var mode: ArrayList<Boolean>

    @Before
    public fun setup() {
        Log.i("SatelliteModeListener", "\t--> setup of " + testName.getMethodName())
        mode = ArrayList()
    }

    private fun enableSensitive() {
        enableSensitive(resolver, looper, SETTINGS_SATELLITE_MODE_RADIOS)
    }

    private fun disableSensitive() {
        disableSensitive(resolver, looper, SETTINGS_SATELLITE_MODE_RADIOS)
    }

    private fun disableMode() {
        disableMode(resolver, looper, SETTINGS_SATELLITE_MODE_ENABLED)
    }

    private fun enableMode() {
        enableMode(resolver, looper, SETTINGS_SATELLITE_MODE_ENABLED)
    }

    private fun callback(newMode: Boolean) = mode.add(newMode)

    @Test
    fun initialize_whenNullSensitive_isOff() {
        Settings.Global.putString(resolver, SETTINGS_SATELLITE_MODE_RADIOS, null)
        enableMode()

        initialize(looper, resolver, this::callback)

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenNotSensitive_isOff() {
        disableSensitive()
        enableMode()

        initialize(looper, resolver, this::callback)

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun enable_whenNotSensitive_isOff() {
        disableSensitive()
        disableMode()

        initialize(looper, resolver, this::callback)

        enableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenSensitive_isOff() {
        enableSensitive()
        disableMode()

        initialize(looper, resolver, this::callback)

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenSensitive_isOn() {
        enableSensitive()
        enableMode()

        initialize(looper, resolver, this::callback)

        assertThat(isOn).isTrue()
        assertThat(mode).isEmpty()
    }

    @Test
    fun toggleSensitive_whenEnabled_isOnOffOn() {
        enableSensitive()
        enableMode()

        initialize(looper, resolver, this::callback)

        disableSensitive()
        enableSensitive()

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(false, true)
    }

    @Test
    fun toggleEnable_whenSensitive_isOffOnOff() {
        enableSensitive()
        disableMode()

        initialize(looper, resolver, this::callback)

        enableMode()
        disableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).containsExactly(true, false)
    }

    @Test
    fun disable_whenDisabled_discardUpdate() {
        enableSensitive()
        disableMode()

        initialize(looper, resolver, this::callback)

        disableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun enabled_whenEnabled_discardOnChange() {
        enableSensitive()
        enableMode()

        initialize(looper, resolver, this::callback)

        enableMode()

        assertThat(isOn).isTrue()
        assertThat(mode).isEmpty()
    }

    @Test
    fun changeContent_whenDisabled_discard() {
        enableSensitive()
        disableMode()

        initialize(looper, resolver, this::callback)

        disableSensitive()
        enableMode()

        assertThat(isOn).isFalse()
        // As opposed to the bare RadioModeListener, similar consecutive event are discarded
        assertThat(mode).isEmpty()
    }
}
