/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.safetycenter.testing

import android.Manifest.permission.MANAGE_SENSOR_PRIVACY
import android.Manifest.permission.OBSERVE_SENSOR_PRIVACY
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A JUnit [TestRule] to ensure a given [sensor] is enabled.
 *
 * This rule disables sensor privacy before a test and restores the prior state afterwards.
 */
class EnableSensorRule(context: Context, val sensor: Int) : TestRule {

    private val sensorPrivacyManager: SensorPrivacyManager =
        context.getSystemService(SensorPrivacyManager::class.java)!!

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                assumeTrue(
                    "Test device does not support toggling sensor $sensor",
                    supportsSensorToggle()
                )
                val oldSensorPrivacy = isSensorPrivacyEnabled()
                setSensorPrivacy(false)
                try {
                    base.evaluate()
                } finally {
                    setSensorPrivacy(oldSensorPrivacy)
                }
            }
        }
    }

    private fun supportsSensorToggle(): Boolean =
        sensorPrivacyManager.supportsSensorToggle(sensor) &&
            sensorPrivacyManager.supportsSensorToggle(TOGGLE_TYPE_SOFTWARE, sensor)

    private fun isSensorPrivacyEnabled(): Boolean =
        callWithShellPermissionIdentity(OBSERVE_SENSOR_PRIVACY) {
            sensorPrivacyManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, sensor)
        }

    private fun setSensorPrivacy(enabled: Boolean) {
        callWithShellPermissionIdentity(MANAGE_SENSOR_PRIVACY, OBSERVE_SENSOR_PRIVACY) {
            sensorPrivacyManager.setSensorPrivacy(sensor, enabled)
        }
    }
}
