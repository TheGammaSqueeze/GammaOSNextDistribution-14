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
package com.android.server.bluetooth.airplane.test

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.android.server.bluetooth.BluetoothAdapterState
import com.android.server.bluetooth.Log
import com.android.server.bluetooth.airplane.APM_BT_ENABLED_NOTIFICATION
import com.android.server.bluetooth.airplane.APM_BT_NOTIFICATION
import com.android.server.bluetooth.airplane.APM_ENHANCEMENT
import com.android.server.bluetooth.airplane.APM_USER_TOGGLED_BLUETOOTH
import com.android.server.bluetooth.airplane.APM_WIFI_BT_NOTIFICATION
import com.android.server.bluetooth.airplane.BLUETOOTH_APM_STATE
import com.android.server.bluetooth.airplane.WIFI_APM_STATE
import com.android.server.bluetooth.airplane.initialize
import com.android.server.bluetooth.airplane.isOn
import com.android.server.bluetooth.airplane.notifyUserToggledBluetooth
import com.android.server.bluetooth.test.disableMode
import com.android.server.bluetooth.test.disableSensitive
import com.android.server.bluetooth.test.enableMode
import com.android.server.bluetooth.test.enableSensitive
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@kotlin.time.ExperimentalTime
class ModeListenerTest {
    companion object {
        internal fun setupAirplaneModeToOn(
            resolver: ContentResolver,
            looper: Looper,
            user: () -> Context,
            enableEnhancedMode: Boolean
        ) {
            enableSensitive(resolver, looper, Settings.Global.AIRPLANE_MODE_RADIOS)
            enableMode(resolver, looper, Settings.Global.AIRPLANE_MODE_ON)
            val mode: (m: Boolean) -> Unit = { _: Boolean -> }
            val notif: (m: String) -> Unit = { _: String -> }
            val media: () -> Boolean = { -> false }
            if (enableEnhancedMode) {
                Settings.Secure.putInt(resolver, APM_USER_TOGGLED_BLUETOOTH, 1)
            }

            initialize(
                looper,
                resolver,
                BluetoothAdapterState(),
                mode,
                notif,
                media,
                user,
                TimeSource.Monotonic,
            )
        }

        internal fun setupAirplaneModeToOff(resolver: ContentResolver, looper: Looper) {
            disableSensitive(resolver, looper, Settings.Global.AIRPLANE_MODE_RADIOS)
            disableMode(resolver, looper, Settings.Global.AIRPLANE_MODE_ON)
        }
    }

    private val looper: Looper = Looper.getMainLooper()
    private val state = BluetoothAdapterState()
    private val mContext = ApplicationProvider.getApplicationContext<Context>()
    private val resolver: ContentResolver = mContext.contentResolver
    @JvmField @Rule val testName = TestName()

    private val userContext =
        mContext.createContextAsUser(UserHandle.of(ActivityManager.getCurrentUser()), 0)

    private var isMediaProfileConnected = false
    private lateinit var mode: ArrayList<Boolean>
    private lateinit var notification: ArrayList<String>

    @Before
    public fun setup() {
        Log.i("AirplaneModeListenerTest", "\t--> setup of " + testName.getMethodName())

        // Most test will expect the system to be sensitive + off
        enableSensitive()
        disableMode()

        isMediaProfileConnected = false
        mode = ArrayList()
        notification = ArrayList()
    }

    private fun initializeAirplane() {
        initialize(
            looper,
            resolver,
            state,
            this::callback,
            this::notificationCallback,
            this::mediaCallback,
            this::userCallback,
            TimeSource.Monotonic,
        )
    }

    private fun enableSensitive() {
        enableSensitive(resolver, looper, Settings.Global.AIRPLANE_MODE_RADIOS)
    }

    private fun disableSensitive() {
        disableSensitive(resolver, looper, Settings.Global.AIRPLANE_MODE_RADIOS)
    }

    private fun disableMode() {
        disableMode(resolver, looper, Settings.Global.AIRPLANE_MODE_ON)
    }

    private fun enableMode() {
        enableMode(resolver, looper, Settings.Global.AIRPLANE_MODE_ON)
    }

    private fun callback(newMode: Boolean) = mode.add(newMode)

    private fun notificationCallback(state: String) = notification.add(state)

    private fun mediaCallback() = isMediaProfileConnected

    private fun userCallback() = userContext

    @Test
    fun initialize_whenNullSensitive_isOff() {
        Settings.Global.putString(resolver, Settings.Global.AIRPLANE_MODE_RADIOS, null)
        enableMode()

        initializeAirplane()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenNotSensitive_isOff() {
        disableSensitive()
        enableMode()

        initializeAirplane()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun enable_whenNotSensitive_isOff() {
        disableSensitive()
        disableMode()

        initializeAirplane()

        enableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenSensitive_isOff() {
        initializeAirplane()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenSensitive_isOn() {
        enableSensitive()
        enableMode()

        initializeAirplane()

        assertThat(isOn).isTrue()
        assertThat(mode).isEmpty()
    }

    @Test
    fun initialize_whenApmToggled_isOn() {
        enableSensitive()
        enableMode()
        Settings.Secure.putInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 1)
        Settings.Secure.putInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 1)

        initializeAirplane()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun toggleSensitive_whenEnabled_isOnOffOn() {
        enableSensitive()
        enableMode()

        initializeAirplane()

        disableSensitive()
        enableSensitive()

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(false, true)
    }

    @Test
    fun toggleEnable_whenSensitive_isOffOnOff() {
        initializeAirplane()

        enableMode()
        disableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).containsExactly(true, false)
    }

    @Test
    fun disable_whenDisabled_discardUpdate() {
        initializeAirplane()

        disableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun enabled_whenEnabled_discardOnChange() {
        enableSensitive()
        enableMode()

        initializeAirplane()

        enableMode()

        assertThat(isOn).isTrue()
        assertThat(mode).isEmpty()
    }

    @Test
    fun changeContent_whenDisabled_discard() {
        initializeAirplane()

        disableSensitive()
        enableMode()

        assertThat(isOn).isFalse()
        // As opposed to the bare RadioModeListener, similar consecutive event are discarded
        assertThat(mode).isEmpty()
    }

    @Test
    fun triggerOverride_whenNoOverride_turnOff() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)

        enableMode()

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(true)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun triggerOverride_whenMedia_staysOn() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)
        isMediaProfileConnected = true

        enableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()

        assertThat(ShadowToast.shownToastCount()).isEqualTo(1)
        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(
                mContext.getString(
                    Resources.getSystem()
                        .getIdentifier("bluetooth_airplane_mode_toast", "string", "android")
                )
            )
    }

    @Test
    fun triggerOverride_whenApmEnhancementNotTrigger_turnOff() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)
        Settings.Global.putInt(resolver, APM_ENHANCEMENT, 0)

        enableMode()

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(true)
    }

    @Test
    fun triggerOverride_whenApmEnhancementNotTriggerButMedia_staysOn() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)
        Settings.Global.putInt(resolver, APM_ENHANCEMENT, 0)
        isMediaProfileConnected = true

        enableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
    }

    @Test
    fun triggerOverride_whenApmEnhancementWasToggled_turnOff() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)
        Settings.Secure.putInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 1)

        enableMode()

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(true)
    }

    @Test
    fun triggerOverride_whenApmEnhancementWasToggled_staysOnWithBtNotification() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)
        Settings.Secure.putInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 1)
        Settings.Secure.putInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 1)

        enableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
        assertThat(notification).containsExactly(APM_BT_NOTIFICATION)
    }

    @Test
    fun triggerOverride_whenApmEnhancementWasToggledAndWifiOn_staysOnWithBtWifiNotification() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)
        Settings.Secure.putInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 1)
        Settings.Secure.putInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 1)

        Settings.Global.putInt(resolver, Settings.Global.WIFI_ON, 1)
        Settings.Secure.putInt(userContext.contentResolver, WIFI_APM_STATE, 1)

        enableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
        assertThat(notification).containsExactly(APM_WIFI_BT_NOTIFICATION)
    }

    @Test
    fun triggerOverride_whenApmEnhancementWasToggledAndWifiNotOn_staysOnWithBtNotification() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)
        Settings.Secure.putInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 1)
        Settings.Secure.putInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 1)

        Settings.Global.putInt(resolver, Settings.Global.WIFI_ON, 1)

        enableMode()

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
        assertThat(notification).containsExactly(APM_BT_NOTIFICATION)
    }

    @Test
    fun showToast_inLoop_stopNotifyWhenMaxToastReached() {
        initializeAirplane()

        state.set(BluetoothAdapter.STATE_ON)
        isMediaProfileConnected = true

        repeat(30) {
            enableMode()
            disableMode()
        }

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
        assertThat(notification).isEmpty()

        assertThat(ShadowToast.shownToastCount())
            .isEqualTo(com.android.server.bluetooth.airplane.ToastNotification.MAX_TOAST_COUNT)
    }

    @Test
    fun userToggleBluetooth_whenNoSession_nothingHappen() {
        initializeAirplane()

        notifyUserToggledBluetooth(resolver, userContext, false)

        assertThat(isOn).isFalse()
        assertThat(mode).isEmpty()
        assertThat(notification).isEmpty()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun userToggleBluetooth_whenSessionButNoApm_noNotificationAndNoSettingSave() {
        initializeAirplane()
        Settings.Global.putInt(resolver, APM_ENHANCEMENT, 0)

        enableMode()
        notifyUserToggledBluetooth(resolver, userContext, true)

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(true)
        assertThat(notification).isEmpty()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(Settings.Secure.getInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 0))
            .isEqualTo(0)
        assertThat(
                Settings.Secure.getInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 0)
            )
            .isEqualTo(0)
    }

    @Test
    fun userToggleBluetooth_whenSession_noNotificationAndSettingSaved() {
        initializeAirplane()

        enableMode()
        notifyUserToggledBluetooth(resolver, userContext, false)

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(true)
        assertThat(notification).isEmpty()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(Settings.Secure.getInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 0))
            .isEqualTo(0)
        assertThat(
                Settings.Secure.getInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 0)
            )
            .isEqualTo(1)
    }

    @Test
    fun userToggleBluetooth_whenSession_notificationAndSettingSaved() {
        initializeAirplane()

        enableMode()
        notifyUserToggledBluetooth(resolver, userContext, true)

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(true)
        assertThat(notification).containsExactly(APM_BT_ENABLED_NOTIFICATION)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(Settings.Secure.getInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 0))
            .isEqualTo(1)
        assertThat(
                Settings.Secure.getInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 0)
            )
            .isEqualTo(1)
    }

    @Test
    fun userToggleTwiceBluetooth_whenSession_notificationAndSettingSaved() {
        initializeAirplane()

        enableMode()
        notifyUserToggledBluetooth(resolver, userContext, true)
        notifyUserToggledBluetooth(resolver, userContext, false)

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(true)
        assertThat(notification).containsExactly(APM_BT_ENABLED_NOTIFICATION)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(Settings.Secure.getInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 0))
            .isEqualTo(0)
        assertThat(
                Settings.Secure.getInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 0)
            )
            .isEqualTo(1)
    }

    @Test
    fun userToggleBluetooth_whenSessionButNoApm_noNotificationAndNoSettingSave_skipTime() {
        val timesource = TestTimeSource()
        initialize(
            looper,
            resolver,
            state,
            this::callback,
            this::notificationCallback,
            this::mediaCallback,
            this::userCallback,
            timesource,
        )
        Settings.Global.putInt(resolver, APM_ENHANCEMENT, 0)

        enableMode()
        timesource += 2.minutes
        notifyUserToggledBluetooth(resolver, userContext, true)

        assertThat(isOn).isTrue()
        assertThat(mode).containsExactly(true)
        assertThat(notification).isEmpty()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(Settings.Secure.getInt(userContext.contentResolver, BLUETOOTH_APM_STATE, 0))
            .isEqualTo(0)
        assertThat(
                Settings.Secure.getInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 0)
            )
            .isEqualTo(0)
    }

    @Test
    fun initialize_firstTime_apmSettingIsSet() {
        initializeAirplane()
        assertThat(Settings.Global.getInt(resolver, APM_ENHANCEMENT, 0)).isEqualTo(1)
    }

    @Test
    fun initialize_secondTime_apmSettingIsNotOverride() {
        val settingValue = 42
        Settings.Global.putInt(resolver, APM_ENHANCEMENT, settingValue)

        initializeAirplane()

        assertThat(Settings.Global.getInt(resolver, APM_ENHANCEMENT, 0)).isEqualTo(settingValue)
    }
}
