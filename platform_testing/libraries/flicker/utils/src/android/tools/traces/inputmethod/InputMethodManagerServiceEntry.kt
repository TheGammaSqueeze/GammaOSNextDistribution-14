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
 * Represents a single InputMethodManagerService trace entry.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
open class InputMethodManagerServiceEntry(
    val where: String,
    val curMethodId: String,
    val curSeq: Int, // what's this
    val curClient: String,
    val curFocusedWindowName: String,
    val lastImeTargetWindowName: String,
    val curFocusedWindowSoftInputMode: String,
    val curAttribute: EditorInfo,
    val curId: String,
    val isShowRequested: Boolean,
    val isShowExplicitlyRequested: Boolean,
    val isShowForced: Boolean,
    val isImeManagerServiceEntryInputShown: Boolean,
    val isInFullscreenMode: Boolean,
    val curToken: String,
    val curTokenDisplayId: Int,
    val isSystemReady: Boolean,
    val lastSwitchUserId: Int,
    val haveConnection: Boolean,
    val isBoundToMethod: Boolean,
    val isInteractive: Boolean,
    val backDisposition: Int,
    val imeWindowVisibility: Int,
    val isShowImeWithHardKeyboard: Boolean,
    val isAccessibilityRequestingNoSoftKeyboard: Boolean,
    val elapsedTimestamp: Long
) : TraceEntry {
    override val timestamp = Timestamps.from(systemUptimeNanos = elapsedTimestamp)
    val stableId: String
        get() = this::class.simpleName ?: error("Unable to determine class")
    val name: String
        get() = timestamp.toString()

    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        return other is InputMethodManagerServiceEntry && other.timestamp == this.timestamp
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + where.hashCode()
        result = 31 * result + curMethodId.hashCode()
        result = 31 * result + curSeq
        result = 31 * result + curClient.hashCode()
        result = 31 * result + curFocusedWindowName.hashCode()
        result = 31 * result + lastImeTargetWindowName.hashCode()
        result = 31 * result + curFocusedWindowSoftInputMode.hashCode()
        result = 31 * result + curAttribute.hashCode()
        result = 31 * result + curId.hashCode()
        return result
    }
}
