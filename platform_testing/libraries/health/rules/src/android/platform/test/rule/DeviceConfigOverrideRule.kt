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

package android.platform.test.rule

import android.provider.DeviceConfig
import org.junit.runner.Description

/**
 * This rule will override DeviceConfig and revert it after the test.
 *
 * Need android.permission.READ_DEVICE_CONFIG.
 */
class DeviceConfigOverrideRule(
    private val namespaceName: String,
    private val name: String,
    private val value: String,
    private val makeDefault: Boolean
) : TestWatcher() {
    // Use strings to store values as all settings stored as strings internally
    private var originalValue: String? = null
    override fun starting(description: Description) {
        // This will return null if the setting hasn't been ever set
        originalValue = DeviceConfig.getProperty(namespaceName, name)
        if (!DeviceConfig.setProperty(namespaceName, name, value, makeDefault)) {
            error("Could not update device config $namespaceName")
        }
    }

    override fun finished(description: Description) {
        DeviceConfig.setProperty(namespaceName, name, originalValue, makeDefault)
    }
}
