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
package com.android.server.bluetooth.test

import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.android.server.bluetooth.Log
import com.android.server.bluetooth.initializeRadioModeListener
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

private const val RADIO = "my_awesome_radio"
private const val MODE_KEY = "is_awesome_radio_enabled"
private const val RADIO_BLUETOOTH = Settings.Global.RADIO_BLUETOOTH

internal fun enableSensitive(resolver: ContentResolver, looper: Looper, radio: String) {
    Settings.Global.putString(resolver, radio, "foo," + RADIO_BLUETOOTH + ",bar")
    shadowOf(looper).idle()
}

internal fun disableSensitive(resolver: ContentResolver, looper: Looper, radio: String) {
    Settings.Global.putString(resolver, radio, "foo,bar")
    shadowOf(looper).idle()
}

internal fun disableMode(resolver: ContentResolver, looper: Looper, modeKey: String) {
    Settings.Global.putInt(resolver, modeKey, 0)
    shadowOf(looper).idle()
}

internal fun enableMode(resolver: ContentResolver, looper: Looper, modeKey: String) {
    Settings.Global.putInt(resolver, modeKey, 1)
    shadowOf(looper).idle()
}

@RunWith(RobolectricTestRunner::class)
class RadioModeListenerTest {
    private val resolver: ContentResolver =
        ApplicationProvider.getApplicationContext<Context>().getContentResolver()
    @JvmField @Rule val testName = TestName()

    private val looper: Looper = Looper.getMainLooper()

    private lateinit var mode: ArrayList<Boolean>

    @Before
    public fun setup() {
        Log.i("RadioModeListenerTest", "\t--> setup of " + testName.getMethodName())
        mode = ArrayList()
    }

    private fun startListener(): Boolean {
        return initializeRadioModeListener(looper, resolver, RADIO, MODE_KEY, this::callback)
    }

    private fun enableSensitive() {
        enableSensitive(resolver, looper, RADIO)
    }

    private fun disableSensitive() {
        disableSensitive(resolver, looper, RADIO)
    }

    private fun disableMode() {
        disableMode(resolver, looper, MODE_KEY)
    }

    private fun enableMode() {
        enableMode(resolver, looper, MODE_KEY)
    }

    private fun callback(newMode: Boolean) = mode.add(newMode)

    @Test
    fun initialize_whenNullSensitive_isOff() {
        Settings.Global.putString(resolver, RADIO, null)
        enableMode()

        val initialValue = startListener()

        assertThat(initialValue).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenNotSensitive_isOff() {
        disableSensitive()
        enableMode()

        val initialValue = startListener()

        assertThat(initialValue).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun enable_whenNotSensitive_isOff() {
        disableSensitive()
        disableMode()

        val initialValue = startListener()

        enableMode()

        assertThat(initialValue).isFalse()
        assertThat(mode).containsExactly(false)
    }

    @Test
    fun initialize_whenSensitive_isOff() {
        enableSensitive()
        disableMode()

        val initialValue = startListener()

        assertThat(initialValue).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenSensitive_isOn() {
        enableSensitive()
        enableMode()

        val initialValue = startListener()

        assertThat(initialValue).isTrue()
        assertThat(mode).isEmpty()
    }

    @Test
    fun toggleSensitive_whenEnabled_isOnOffOn() {
        enableSensitive()
        enableMode()

        val initialValue = startListener()

        disableSensitive()
        enableSensitive()

        assertThat(initialValue).isTrue()
        assertThat(mode).containsExactly(false, true)
    }

    @Test
    fun toggleEnable_whenSensitive_isOffOnOff() {
        enableSensitive()
        disableMode()

        val initialValue = startListener()

        enableMode()
        disableMode()

        assertThat(initialValue).isFalse()
        assertThat(mode).containsExactly(true, false)
    }

    @Test
    fun disable_whenDisabled_isDiscarded() {
        enableSensitive()
        disableMode()

        val initialValue = startListener()

        disableMode()
        disableMode()

        assertThat(initialValue).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun enabled_whenEnabled_isDiscarded() {
        enableSensitive()
        enableMode()

        val initialValue = startListener()

        enableMode()
        enableMode()

        assertThat(initialValue).isTrue()
        assertThat(mode).isEmpty()
    }

    @Test
    fun changeContent_whenDisabled_noDiscard() {
        enableSensitive()
        disableMode()

        val initialValue = startListener()

        disableSensitive() // The value is changed but the result is still false
        enableMode() // The value is changed but the result is still false

        assertThat(initialValue).isFalse()
        assertThat(mode).containsExactly(false, false)
    }
}
