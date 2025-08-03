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
package com.android.server.bluetooth

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

private const val TAG = "BluetoothRadioModeListener"

/**
 * Listen on radio mode and trigger the callback when it change
 *
 * @param radio: The radio to listen for, eg: Settings.Global.AIRPLANE_MODE_RADIOS
 * @param modeKey: The associated mode key, eg: Settings.Global.AIRPLANE_MODE_ON
 * @param callback: The callback to trigger when there is a mode change, pass new mode as parameter
 * @return The initial value of the radio
 */
internal fun initializeRadioModeListener(
    looper: Looper,
    resolver: ContentResolver,
    radio: String,
    modeKey: String,
    callback: (m: Boolean) -> Unit
): Boolean {
    val observer =
        object : ContentObserver(Handler(looper)) {
            override fun onChange(selfChange: Boolean) {
                callback(getRadioModeValue(resolver, radio, modeKey))
            }
        }

    val notifyForDescendants = false

    resolver.registerContentObserver(
        Settings.Global.getUriFor(radio),
        notifyForDescendants,
        observer
    )
    resolver.registerContentObserver(
        Settings.Global.getUriFor(modeKey),
        notifyForDescendants,
        observer
    )
    return getRadioModeValue(resolver, radio, modeKey)
}

/**
 * Check if Bluetooth is impacted by the radio and fetch global mode status
 *
 * @return whether Bluetooth should consider this radio or not
 */
private fun getRadioModeValue(resolver: ContentResolver, radio: String, modeKey: String): Boolean {
    return if (isSensitive(resolver, radio)) {
        isGlobalModeOn(resolver, modeKey)
    } else {
        Log.d(TAG, "Not sensitive to " + radio + " change. Forced to false")
        false
    }
}

/**
 * *Do not use outside of this file to avoid async issues*
 *
 * @return false if Bluetooth should not listen for mode change related to the {@code radio}
 */
private fun isSensitive(resolver: ContentResolver, radio: String): Boolean {
    val radios = Settings.Global.getString(resolver, radio)
    return radios != null && radios.contains(Settings.Global.RADIO_BLUETOOTH)
}

/**
 * *Do not use outside of this file to avoid async issues*
 *
 * @return whether mode {@code modeKey} is on or off in Global settings
 */
private fun isGlobalModeOn(resolver: ContentResolver, modeKey: String): Boolean {
    return Settings.Global.getInt(resolver, modeKey, 0) == 1
}
