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
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.wm.WindowManagerTrace
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * Defines the runner for the flicker tests. This component is responsible for running the flicker
 * tests and executing assertions on the traces to check for inconsistent behaviors on
 * [WindowManagerTrace] and [LayersTrace]
 */
@FlickerDslMarker
open class FlickerTestDataImpl(
    /** Instrumentation to run the tests */
    override val instrumentation: Instrumentation,
    /** Test automation component used to interact with the device */
    override val device: UiDevice,
    /** Output directory for test results */
    override val outputDir: File,
    /** Enabled tracing monitors */
    override val traceMonitors: List<ITransitionMonitor>,
    /** Commands to be executed before the transition */
    override val transitionSetup: List<FlickerTestData.() -> Any>,
    /** Test commands */
    override val transitions: List<FlickerTestData.() -> Any>,
    /** Commands to be executed after the transition */
    override val transitionTeardown: List<FlickerTestData.() -> Any>,
    /** Helper object for WM Synchronization */
    override val wmHelper: WindowManagerStateHelper
) : AbstractFlickerTestData()
