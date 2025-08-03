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
package com.android.permissioncontroller.privacysources

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Code reference from https://source.corp.google.com/android/frameworks/base/packages/SettingsLib/
 * src/com/android/settingslib/accessibility/AccessibilityUtils.java
 */
object AccessibilitySettingsUtil {
    private const val ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR = ':'
    private val LOG_TAG = AccessibilitySettingsUtil::class.java.simpleName
    private val lock = Mutex()

    /** Changes an accessibility component's state. */
    suspend fun disableAccessibilityService(context: Context, serviceToBeDisabled: ComponentName) {
        lock.withLock {
            val settingsEnabledA11yServices = getEnabledServicesFromSettings(context)
            if (
                settingsEnabledA11yServices.isEmpty() ||
                    !settingsEnabledA11yServices.contains(serviceToBeDisabled)
            ) {
                Log.w(
                    LOG_TAG,
                    "${serviceToBeDisabled.toShortString()} is already disabled " +
                        "or not installed."
                )
                return
            }

            settingsEnabledA11yServices.remove(serviceToBeDisabled)

            val updatedEnabledServices =
                settingsEnabledA11yServices
                    .map { it.flattenToString() }
                    .joinToString(separator = ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR.toString())

            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updatedEnabledServices
            )
        }
    }

    /** @return the mutable set of enabled accessibility services. */
    fun getEnabledServicesFromSettings(context: Context): MutableSet<ComponentName> {
        val enabledServicesSetting =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        val enabledServices = mutableSetOf<ComponentName>()
        if (TextUtils.isEmpty(enabledServicesSetting)) {
            return enabledServices
        }

        val colonSplitter: TextUtils.StringSplitter =
            TextUtils.SimpleStringSplitter(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR)
        colonSplitter.setString(enabledServicesSetting)

        for (componentNameString in colonSplitter) {
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null) {
                enabledServices.add(enabledService)
            } else {
                Log.e(LOG_TAG, "unable to parse accessibility service $componentNameString")
            }
        }

        return enabledServices
    }
}
