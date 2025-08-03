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

package android.tools.traces.inputmethod

import android.tools.Timestamps
import android.tools.TraceEntry

/**
 * Represents a single Ime InputMethodService trace entry.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
open class InputMethodServiceEntry(
    val where: String,
    val softInputWindow: SoftInputWindow,
    val viewsCreated: Boolean,
    val decorViewVisible: Boolean,
    val decorViewWasVisible: Boolean,
    val windowVisible: Boolean,
    val inShowWindow: Boolean,
    val configuration: String,
    val token: String,
    val inputBinding: String,
    val inputStarted: Boolean,
    val inputViewStarted: Boolean,
    val candidatesViewStarted: Boolean,
    val inputEditorInfo: EditorInfo,
    val showInputRequested: Boolean,
    val lastShowInputRequested: Boolean,
    val showInputFlags: Int,
    val candidatesVisibility: Int,
    val fullscreenApplied: Boolean,
    val isFullscreen: Boolean,
    val extractViewHidden: Boolean,
    val extractedToken: Int,
    val isInputViewShown: Boolean,
    val statusIcon: Int,
    val lastComputedInsets: Insets,
    val settingsObserver: String,
    //    val inputConnectionCall: InputConnectionCallState,
    val elapsedTimestamp: Long
) : TraceEntry {
    override val timestamp = Timestamps.from(systemUptimeNanos = elapsedTimestamp)
    val stableId: String
        get() = this::class.simpleName ?: error("Unable to determine class")
    val name: String
        get() = timestamp.toString()

    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        return other is InputMethodServiceEntry && other.timestamp == this.timestamp
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + where.hashCode()
        result = 31 * result + softInputWindow.hashCode()
        result = 31 * result + configuration.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + isFullscreen.hashCode()
        result = 31 * result + inputEditorInfo.hashCode()
        return result
    }
}
