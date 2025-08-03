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
@file:JvmName("SatelliteModeListener")

package com.android.server.bluetooth.satellite

import android.content.ContentResolver
import android.os.Looper
import com.android.server.bluetooth.Log
import com.android.server.bluetooth.initializeRadioModeListener

/**
 * constant copied from {@link Settings.Global}
 *
 * TODO(b/274636414): Migrate to official API in Android V.
 */
internal const val SETTINGS_SATELLITE_MODE_RADIOS = "satellite_mode_radios"

/**
 * constant copied from {@link Settings.Global}
 *
 * TODO(b/274636414): Migrate to official API in Android V.
 */
internal const val SETTINGS_SATELLITE_MODE_ENABLED = "satellite_mode_enabled"

private const val TAG = "SatelliteModeListener"

public var isOn = false
    private set

/** Listen on satellite mode and trigger the callback if it has changed */
public fun initialize(looper: Looper, resolver: ContentResolver, callback: (m: Boolean) -> Unit) {
    isOn =
        initializeRadioModeListener(
            looper,
            resolver,
            SETTINGS_SATELLITE_MODE_RADIOS,
            SETTINGS_SATELLITE_MODE_ENABLED,
            fun(newMode: Boolean) {
                val previousMode = isOn
                isOn = newMode
                if (previousMode == isOn) {
                    Log.d(TAG, "Ignore satellite mode change because is already: " + isOn)
                    return
                }
                Log.i(TAG, "Trigger callback with state: $isOn")
                callback(isOn)
            }
        )
    Log.i(TAG, "Initialized successfully with state: $isOn")
}
