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

package android.platform.helpers.foldable

import android.hardware.SensorManager
import android.platform.test.rule.TestWatcher
import kotlin.properties.Delegates.notNull
import org.junit.Assume

/**
 * Allows to inject values to a sensor. Assumes that sensor injection is supported. Note that
 * currently injection is only supported on virtual devices.
 */
class SensorInjectionController(sensorType: Int) : TestWatcher() {

    private val sensorManager = context.getSystemService(SensorManager::class.java)!!
    private val sensor = sensorManager.getDefaultSensor(sensorType)
    private var initialized = false

    var injectionSupported by notNull<Boolean>()
        private set

    fun init() {
        executeShellCommand(SENSOR_SERVICE_ENABLE)
        executeShellCommand(SENSOR_SERVICE_DATA_INJECTION + context.packageName)
        injectionSupported = sensorManager.initDataInjection(true)
        initialized = true
    }

    fun uninit() {
        if (initialized && injectionSupported) {
            sensorManager.initDataInjection(false)
        }
        initialized = false
    }

    fun setValue(value: Float) {
        check(initialized) { "Trying to set sensor value before initialization" }
        Assume.assumeTrue("Skipping as data injection is not supported", injectionSupported)
        check(
            sensorManager.injectSensorData(
                sensor,
                floatArrayOf(value),
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                System.currentTimeMillis()
            )
        ) {
            "Error while injecting sensor data."
        }
    }

    companion object {
        private const val SENSOR_SERVICE_ENABLE = "dumpsys sensorservice enable"
        private const val SENSOR_SERVICE_DATA_INJECTION = "dumpsys sensorservice data_injection "
    }
}
