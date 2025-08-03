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

package android.tools.flicker.subject.wm

import android.tools.Timestamp
import android.tools.flicker.assertions.Fact
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.region.RegionSubject
import android.tools.io.Reader
import android.tools.traces.wm.WindowState

/**
 * Subject for [WindowState] objects, used to make assertions over behaviors that occur on a single
 * [WindowState] of a WM state.
 *
 * To make assertions over a layer from a state it is recommended to create a subject using
 * [WindowManagerStateSubject.windowState](windowStateName)
 *
 * Alternatively, it is also possible to use [WindowStateSubject](myWindow).
 *
 * Example:
 * ```
 *    val trace = WindowManagerTraceParser().parse(myTraceFile)
 *    val subject = WindowManagerTraceSubject(trace).first()
 *        .windowState("ValidWindow")
 *        .exists()
 *        { myCustomAssertion(this) }
 * ```
 */
class WindowStateSubject(
    override val reader: Reader? = null,
    override val timestamp: Timestamp,
    val windowState: WindowState
) : FlickerSubject() {
    val isVisible: Boolean = windowState.isVisible
    val isInvisible: Boolean = !windowState.isVisible
    val name: String = windowState.name
    val frame: RegionSubject
        get() = RegionSubject(windowState.frame, timestamp, reader)

    override val selfFacts = listOf(Fact("Window title", windowState.title))

    /** If the [windowState] exists, executes a custom [assertion] on the current subject */
    operator fun invoke(assertion: (WindowState) -> Unit): WindowStateSubject = apply {
        assertion(this.windowState)
    }

    override fun toString(): String {
        return "WindowState:$name"
    }
}
