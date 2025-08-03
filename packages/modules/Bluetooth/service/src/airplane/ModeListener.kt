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
@file:JvmName("AirplaneModeListener")

package com.android.server.bluetooth.airplane

import android.bluetooth.BluetoothAdapter.STATE_ON
import android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF
import android.bluetooth.BluetoothAdapter.STATE_TURNING_ON
import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.android.bluetooth.BluetoothStatsLog
import com.android.server.bluetooth.BluetoothAdapterState
import com.android.server.bluetooth.Log
import com.android.server.bluetooth.initializeRadioModeListener
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val TAG = "AirplaneModeListener"

/** @return true if Bluetooth state is impacted by airplane mode */
public var isOn = false
    private set

/**
 * The airplane ModeListener handles system airplane mode change and checks whether it need to
 * trigger the callback or not.
 *
 * <p>The information of airplane mode being turns on would not be passed when Bluetooth is on and
 * one of the following situations is met:
 * <ul>
 * <li> "Airplane Enhancement Mode" is enabled and the user asked for Bluetooth to be on previously
 * <li> A media profile is connected (one of A2DP | Hearing Aid | Le Audio)
 * </ul>
 */
@kotlin.time.ExperimentalTime
public fun initialize(
    looper: Looper,
    systemResolver: ContentResolver,
    state: BluetoothAdapterState,
    modeCallback: (m: Boolean) -> Unit,
    notificationCallback: (state: String) -> Unit,
    mediaCallback: () -> Boolean,
    userCallback: () -> Context,
    timeSource: TimeSource,
) {

    // Wifi got support for "Airplane Enhancement Mode" prior to Bluetooth.
    // In order for Wifi to be aware that Bluetooth also support the feature, Bluetooth need to set
    // the APM_ENHANCEMENT settings to `1`.
    // Value will be set to DEFAULT_APM_ENHANCEMENT_STATE only if the APM_ENHANCEMENT is not set.
    Settings.Global.putInt(
        systemResolver,
        APM_ENHANCEMENT,
        Settings.Global.getInt(systemResolver, APM_ENHANCEMENT, DEFAULT_APM_ENHANCEMENT_STATE)
    )

    val airplaneModeAtBoot =
        initializeRadioModeListener(
            looper,
            systemResolver,
            Settings.Global.AIRPLANE_MODE_RADIOS,
            Settings.Global.AIRPLANE_MODE_ON,
            fun(newMode: Boolean) {
                val previousMode = isOn
                val isBluetoothOn = state.oneOf(STATE_ON, STATE_TURNING_ON, STATE_TURNING_OFF)
                val isMediaConnected = isBluetoothOn && mediaCallback()

                isOn =
                    airplaneModeValueOverride(
                        systemResolver,
                        newMode,
                        isBluetoothOn,
                        notificationCallback,
                        userCallback,
                        isMediaConnected,
                    )

                AirplaneMetricSession.handleModeChange(
                    newMode,
                    isBluetoothOn,
                    notificationCallback,
                    userCallback,
                    isMediaConnected,
                    timeSource.markNow(),
                )

                if (previousMode == isOn) {
                    Log.d(TAG, "Ignore airplane mode change because is already: $isOn")
                    return
                }

                Log.i(TAG, "Trigger callback with state: $isOn")
                modeCallback(isOn)
            }
        )

    isOn =
        airplaneModeValueOverride(
            systemResolver,
            airplaneModeAtBoot,
            null, // Do not provide a Bluetooth on / off as we want to evaluate override
            null, // Do not provide a notification callback as we want to keep the boot silent
            userCallback,
            false,
        )

    // Bluetooth is always off during initialize, and no media profile can be connected
    AirplaneMetricSession.handleModeChange(
        airplaneModeAtBoot,
        false,
        notificationCallback,
        userCallback,
        false,
        timeSource.markNow(),
    )
    Log.i(TAG, "Initialized successfully with state: $isOn")
}

@kotlin.time.ExperimentalTime
public fun notifyUserToggledBluetooth(
    resolver: ContentResolver,
    userContext: Context,
    isBluetoothOn: Boolean,
) {
    AirplaneMetricSession.notifyUserToggledBluetooth(resolver, userContext, isBluetoothOn)
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////// PRIVATE METHODS /////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

private fun airplaneModeValueOverride(
    resolver: ContentResolver,
    currentAirplaneMode: Boolean,
    currentBluetoothStatus: Boolean?,
    sendAirplaneModeNotification: ((state: String) -> Unit)?,
    getUser: () -> Context,
    isMediaConnected: Boolean,
): Boolean {
    // Airplane mode is being disabled or bluetooth was not on: no override
    if (!currentAirplaneMode || currentBluetoothStatus == false) {
        return currentAirplaneMode
    }
    // If "Airplane Enhancement Mode" is on and the user already used the feature …
    if (isApmEnhancementEnabled(resolver) && hasUserToggledApm(getUser())) {
        // … Staying on only depend on its last action in airplane mode
        if (isBluetoothOnAPM(getUser)) {
            Log.i(TAG, "Bluetooth stay on during airplane mode because of last user action")

            val isWifiOn = isWifiOnApm(resolver, getUser)
            sendAirplaneModeNotification?.invoke(
                if (isWifiOn) APM_WIFI_BT_NOTIFICATION else APM_BT_NOTIFICATION
            )
            return false
        }
        return true
    }
    // … Else, staying on only depend on media profile being connected or not
    //
    // Note: Once the "Airplane Enhancement Mode" has been used, media override no longer apply
    //       This has been done on purpose to avoid complexe scenario like:
    //           1. User wants Bt off according to "Airplane Enhancement Mode"
    //           2. User switches airplane while there is media => so Bt stays on
    //           3. User turns airplane off, stops media and toggles airplane back on
    //       Should we turn Bt off like asked initially ? Or keep it `on` like the toggle ?
    if (isMediaConnected) {
        Log.i(TAG, "Bluetooth stay on during airplane mode because media profile are connected")
        ToastNotification.displayIfNeeded(resolver, getUser)
        return false
    }
    return true
}

internal class ToastNotification private constructor() {
    companion object {
        private const val TOAST_COUNT = "bluetooth_airplane_toast_count"
        internal const val MAX_TOAST_COUNT = 10

        private fun userNeedToBeNotified(resolver: ContentResolver): Boolean {
            val currentToastCount = Settings.Global.getInt(resolver, TOAST_COUNT, 0)
            if (currentToastCount >= MAX_TOAST_COUNT) {
                return false
            }
            Settings.Global.putInt(resolver, TOAST_COUNT, currentToastCount + 1)
            return true
        }

        fun displayIfNeeded(resolver: ContentResolver, getUser: () -> Context) {
            if (!userNeedToBeNotified(resolver)) {
                Log.d(TAG, "Dismissed Toast notification")
                return
            }
            val userContext = getUser()
            val r = userContext.getResources()
            val text: CharSequence =
                r.getString(
                    Resources.getSystem()
                        .getIdentifier("bluetooth_airplane_mode_toast", "string", "android")
                )
            Toast.makeText(userContext, text, Toast.LENGTH_LONG).show()
            Log.d(TAG, "Displayed Toast notification")
        }
    }
}

@kotlin.time.ExperimentalTime
private class AirplaneMetricSession(
    private val isBluetoothOnBeforeApmToggle: Boolean,
    private val sendAirplaneModeNotification: (state: String) -> Unit,
    private val isMediaProfileConnectedBeforeApmToggle: Boolean,
    private val sessionStartTime: TimeMark,
) {
    companion object {
        private var session: AirplaneMetricSession? = null

        fun handleModeChange(
            isAirplaneModeOn: Boolean,
            isBluetoothOn: Boolean,
            sendAirplaneModeNotification: (state: String) -> Unit,
            getUser: () -> Context,
            isMediaProfileConnected: Boolean,
            startTime: TimeMark,
        ) {
            if (isAirplaneModeOn) {
                session =
                    AirplaneMetricSession(
                        isBluetoothOn,
                        sendAirplaneModeNotification,
                        isMediaProfileConnected,
                        startTime,
                    )
            } else {
                session?.let { it.terminate(getUser, isBluetoothOn) }
                session = null
            }
        }

        fun notifyUserToggledBluetooth(
            resolver: ContentResolver,
            userContext: Context,
            isBluetoothOn: Boolean,
        ) {
            session?.let { it.notifyUserToggledBluetooth(resolver, userContext, isBluetoothOn) }
        }
    }

    private val isBluetoothOnAfterApmToggle = !isOn
    private var userToggledBluetoothDuringApm = false
    private var userToggledBluetoothDuringApmWithinMinute = false

    fun notifyUserToggledBluetooth(
        resolver: ContentResolver,
        userContext: Context,
        isBluetoothOn: Boolean,
    ) {
        val isFirstToggle = !userToggledBluetoothDuringApm
        userToggledBluetoothDuringApm = true

        if (isFirstToggle) {
            val oneMinute = sessionStartTime + 1.minutes
            userToggledBluetoothDuringApmWithinMinute = !oneMinute.hasPassedNow()
        }

        if (isApmEnhancementEnabled(resolver)) {
            // Set "Airplane Enhancement Mode" settings for a specific user
            setUserSettingsSecure(userContext, BLUETOOTH_APM_STATE, if (isBluetoothOn) 1 else 0)
            setUserSettingsSecure(userContext, APM_USER_TOGGLED_BLUETOOTH, 1)

            if (isBluetoothOn) {
                sendAirplaneModeNotification(APM_BT_ENABLED_NOTIFICATION)
            }
        }
    }

    /** Log current airplaneSession. Session cannot be re-use */
    fun terminate(getUser: () -> Context, isBluetoothOn: Boolean) {
        BluetoothStatsLog.write(
            BluetoothStatsLog.AIRPLANE_MODE_SESSION_REPORTED,
            BluetoothStatsLog.AIRPLANE_MODE_SESSION_REPORTED__PACKAGE_NAME__BLUETOOTH,
            isBluetoothOnBeforeApmToggle,
            isBluetoothOnAfterApmToggle,
            isBluetoothOn,
            hasUserToggledApm(getUser()),
            userToggledBluetoothDuringApm,
            userToggledBluetoothDuringApmWithinMinute,
            isMediaProfileConnectedBeforeApmToggle,
        )
    }
}

// Notification Id for when the airplane mode is turn on but Bluetooth stay on
internal const val APM_BT_NOTIFICATION = "apm_bt_notification"

// Notification Id for when the airplane mode is turn on but Bluetooth and Wifi stay on
internal const val APM_WIFI_BT_NOTIFICATION = "apm_wifi_bt_notification"

// Notification Id for when the Bluetooth is turned back on durin airplane mode
internal const val APM_BT_ENABLED_NOTIFICATION = "apm_bt_enabled_notification"

// Whether the "Airplane Enhancement Mode" is enabled
internal const val APM_ENHANCEMENT = "apm_enhancement_enabled"

// Whether the user has already toggled and used the "Airplane Enhancement Mode" feature
internal const val APM_USER_TOGGLED_BLUETOOTH = "apm_user_toggled_bluetooth"

// Whether Bluetooth should remain on in airplane mode
internal const val BLUETOOTH_APM_STATE = "bluetooth_apm_state"

// Whether Wifi should remain on in airplane mode
internal const val WIFI_APM_STATE = "wifi_apm_state"

private fun setUserSettingsSecure(userContext: Context, name: String, value: Int) =
    Settings.Secure.putInt(userContext.contentResolver, name, value)

// Define if the "Airplane Enhancement Mode" feature is enabled by default. `0` == disabled
private const val DEFAULT_APM_ENHANCEMENT_STATE = 1

/** Airplane Enhancement Mode: Indicate if the feature is enabled or not. */
private fun isApmEnhancementEnabled(resolver: ContentResolver) =
    Settings.Global.getInt(resolver, APM_ENHANCEMENT, DEFAULT_APM_ENHANCEMENT_STATE) == 1

/** Airplane Enhancement Mode: Return true if the wifi should stays on during airplane mode */
private fun isWifiOnApm(resolver: ContentResolver, getUser: () -> Context) =
    Settings.Global.getInt(resolver, Settings.Global.WIFI_ON, 0) != 0 &&
        Settings.Secure.getInt(getUser().contentResolver, WIFI_APM_STATE, 0) == 1

/** Airplane Enhancement Mode: Return true if this user already toggled (aka used) the feature */
fun hasUserToggledApm(userContext: Context) =
    Settings.Secure.getInt(userContext.contentResolver, APM_USER_TOGGLED_BLUETOOTH, 0) == 1

/** Airplane Enhancement Mode: Return true if the bluetooth should stays on during airplane mode */
private fun isBluetoothOnAPM(getUser: () -> Context) =
    Settings.Secure.getInt(getUser().contentResolver, BLUETOOTH_APM_STATE, 0) == 1
