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

package android.tools.flicker.legacy

import android.app.Instrumentation
import android.tools.traces.monitors.ITransitionMonitor
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.uiautomator.UiDevice
import java.io.File

interface FlickerTestData {
    /** Instrumentation to run the tests */
    val instrumentation: Instrumentation
    /** Test automation component used to interact with the device */
    val device: UiDevice
    /** Output directory for test results */
    val outputDir: File
    /** Enabled tracing monitors */
    val traceMonitors: List<ITransitionMonitor>
    /** Commands to be executed before the transition */
    val transitionSetup: List<FlickerTestData.() -> Any>
    /** Test commands */
    val transitions: List<FlickerTestData.() -> Any>
    /** Commands to be executed after the transition */
    val transitionTeardown: List<FlickerTestData.() -> Any>
    /** Helper object for WM Synchronization */
    val wmHelper: WindowManagerStateHelper

    fun setAssertionsCheckedCallback(callback: (Boolean) -> Unit)

    fun setCreateTagListener(callback: (String) -> Unit)

    fun clearTagListener()

    /**
     * Runs a set of commands and, at the end, creates a tag containing the device state
     *
     * @param tag Identifier for the tag to be created
     * @param commands Commands to execute before creating the tag
     * @throws IllegalArgumentException If [tag] cannot be converted to a valid filename
     */
    fun withTag(tag: String, commands: FlickerTestData.() -> Any)

    fun createTag(tag: String)
}
