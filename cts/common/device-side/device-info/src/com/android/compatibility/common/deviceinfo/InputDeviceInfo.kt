/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.compatibility.common.util.DeviceConfigStateManager
import com.android.compatibility.common.util.DeviceInfoStore

/**
 * Input device info collector.
 * Clarification: this collects input-related properties on the Android device under test.
 * The name "InputDeviceInfo" should not be read as "InputDevice" "Info", but rather
 * as "Input" "Device Info".
 */
public final class InputDeviceInfo : DeviceInfo() {
    private val LOG_TAG = "InputDeviceInfo"

    override fun collectDeviceInfo(store: DeviceInfoStore) {
        collectInputInfo(store, "input")
    }

    private fun readDeviceConfig(namespace: String, name: String, default: String): String {
        val context: Context = ApplicationProvider.getApplicationContext()
        val stateManager = DeviceConfigStateManager(context, namespace, name)
        val value = stateManager.get()
        return if (value != null) value else default
    }

    /**
     * Collect info for input into a group.
     */
    private fun collectInputInfo(store: DeviceInfoStore, groupName: String) {
        store.startGroup(groupName)

        val palmRejectionValue = readDeviceConfig("input_native_boot", "palm_rejection_enabled", "")
        val palmRejectionEnabled = palmRejectionValue == "1" || palmRejectionValue == "true"
        store.addResult("palm_rejection_enabled", palmRejectionEnabled)

        val velocityTrackerStrategyValue = readDeviceConfig(
            "input_native_boot", "velocitytracker_strategy", "default"
        )
        store.addResult("velocitytracker_strategy", velocityTrackerStrategyValue)

        store.endGroup()
    }
}
