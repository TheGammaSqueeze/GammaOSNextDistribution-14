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

package android.tools.traces.wm

class WindowManagerTraceEntryBuilder {
    private var policy: WindowManagerPolicy? = null
    private var focusedApp = ""
    private var focusedDisplayId = 0
    private var focusedWindow = ""
    private var inputMethodWindowAppToken = ""
    private var isHomeRecentsComponent = false
    private var isDisplayFrozen = false
    private val pendingActivities = mutableListOf<String>()
    private var root: RootWindowContainer? = null
    private var keyguardControllerState: KeyguardControllerState? = null
    private var where = ""

    private var elapsedTimestamp: Long = 0L
    private var realTimestamp: Long? = null

    fun setPolicy(value: WindowManagerPolicy?): WindowManagerTraceEntryBuilder = apply {
        policy = value
    }

    fun setFocusedApp(value: String): WindowManagerTraceEntryBuilder = apply { focusedApp = value }

    fun setFocusedDisplayId(value: Int): WindowManagerTraceEntryBuilder = apply {
        focusedDisplayId = value
    }

    fun setFocusedWindow(value: String): WindowManagerTraceEntryBuilder = apply {
        focusedWindow = value
    }

    fun setInputMethodWindowAppToken(value: String): WindowManagerTraceEntryBuilder = apply {
        inputMethodWindowAppToken = value
    }

    fun setIsHomeRecentsComponent(value: Boolean): WindowManagerTraceEntryBuilder = apply {
        isHomeRecentsComponent = value
    }

    fun setIsDisplayFrozen(value: Boolean): WindowManagerTraceEntryBuilder = apply {
        isDisplayFrozen = value
    }

    fun setPendingActivities(value: Collection<String>): WindowManagerTraceEntryBuilder = apply {
        pendingActivities.addAll(value)
    }

    fun setRoot(value: RootWindowContainer?): WindowManagerTraceEntryBuilder = apply {
        root = value
    }

    fun setKeyguardControllerState(
        value: KeyguardControllerState?
    ): WindowManagerTraceEntryBuilder = apply { keyguardControllerState = value }

    fun setWhere(value: String): WindowManagerTraceEntryBuilder = apply { where = value }

    fun setElapsedTimestamp(value: Long): WindowManagerTraceEntryBuilder = apply {
        elapsedTimestamp = value
    }

    fun setRealToElapsedTimeOffsetNs(value: Long?): WindowManagerTraceEntryBuilder = apply {
        realTimestamp =
            if (value != null && value != 0L) {
                value + elapsedTimestamp
            } else {
                null
            }
    }

    /** Constructs the window manager trace entry. */
    fun build(): WindowManagerState {
        val root = root ?: error("Root not set")
        val keyguardControllerState =
            keyguardControllerState ?: error("KeyguardControllerState not set")

        return WindowManagerState(
            elapsedTimestamp,
            realTimestamp,
            where,
            policy,
            focusedApp,
            focusedDisplayId,
            focusedWindow,
            inputMethodWindowAppToken,
            isHomeRecentsComponent,
            isDisplayFrozen,
            pendingActivities,
            root,
            keyguardControllerState
        )
    }
}
