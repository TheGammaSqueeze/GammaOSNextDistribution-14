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

package android.platform.helpers

import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.time.Duration

/** Utilities to launch an [App]. */
object LaunchAppUtils {

    /** Launches an [App]. */
    @JvmStatic
    fun Context.launchApp(app: App) {
        val appIntent =
            packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
            }
                ?: error("Package ${app.packageName} not available")

        startActivity(appIntent)
        assertAppInForeground(app)
    }

    /** Asserts that a given app is in the foreground. */
    @JvmStatic
    fun assertAppInForeground(app: App) {
        assertAppInForeground(app.packageName)
    }

    /** Asserts that a given app is in the foreground. */
    @JvmStatic
    fun assertAppInForeground(packageName: String) {
        check(device.wait(Until.hasObject(By.pkg(packageName).depth(0)), MAX_TIMEOUT.toMillis())) {
            "$packageName not in the foreground after ${MAX_TIMEOUT.toSeconds()} seconds"
        }
    }

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}

/** Describes an app that can be launched with [LaunchAppUtils]. */
enum class App(internal val packageName: String) {
    CALCULATOR("com.google.android.calculator"),
    MAPS("com.google.android.apps.maps"),
    CAMERA("com.google.android.GoogleCamera"),
    SETTINGS("com.android.settings")
}

private val MAX_TIMEOUT = Duration.ofSeconds(10)
