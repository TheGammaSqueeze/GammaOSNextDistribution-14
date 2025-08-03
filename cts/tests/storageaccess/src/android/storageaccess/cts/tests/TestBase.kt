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

package android.storageaccess.cts.tests

import android.content.pm.PackageManager.FEATURE_AUTOMOTIVE
import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.content.pm.PackageManager.FEATURE_TELEVISION
import android.content.pm.PackageManager.FEATURE_WATCH
import android.storageaccess.cts.DEBUG
import android.storageaccess.cts.log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before

/** Base for the test classes. */
abstract class TestBase {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()!!
    private val device = UiDevice.getInstance(instrumentation)

    protected val context = instrumentation.context!!
    protected val contentResolver
        get() = context.contentResolver!!

    @Before
    fun setUp_TestBase() {
        if (DEBUG) log("setUp_TestBase")

        with(context.packageManager) {
            // Make sure we are running neither on a TV...
            assumeFalse(hasSystemFeature(FEATURE_TELEVISION) || hasSystemFeature(FEATURE_LEANBACK))
            // nor on a watch...
            assumeFalse(hasSystemFeature(FEATURE_WATCH))
            // nor in a car.
            assumeFalse(hasSystemFeature(FEATURE_AUTOMOTIVE))
        }

        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
    }

    @After
    fun tearDown_TestBase() {
        if (DEBUG) log("tearDown_TestBase")
    }

    protected fun executeShellCommand(cmd: String): String {
        if (DEBUG) log("executeShellCommand '$cmd'")
        val output = device.executeShellCommand(cmd)
        if (DEBUG) log("output: '$output'")
        return output
    }
}