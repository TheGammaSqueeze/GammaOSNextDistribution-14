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
 * Represents a single Ime Client trace entry.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
open class ImeClientEntry(
    val where: String,
    val displayId: Int,
    val inputMethodManager: InputMethodManager,
    val viewRootImpl: ViewRootImpl,
    val editorInfo: EditorInfo,
    // TODO(b/239021832): If needed in the future, add in the following tracing fields:
    // val insetsController: InsetsController,
    // val imeInsetsSourceConsumer: ImeInsetsSourceConsumer,
    // val imeFocusController: ImeFocusController,
    // val inputConnection: InputConnection,
    // val inputConnectionCall: InputConnectionCall,
    val elapsedTimestamp: Long
) : TraceEntry {
    override val timestamp = Timestamps.from(systemUptimeNanos = elapsedTimestamp)
    val stableId: String
        get() = this::class.simpleName ?: error("Unable to determine class")
    val name: String
        get() = timestamp.toString()
    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        return other is ImeClientEntry && other.timestamp == this.timestamp
    }
    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + where.hashCode()
        result = 31 * result + displayId
        result = 31 * result + inputMethodManager.hashCode()
        result = 31 * result + viewRootImpl.hashCode()
        result = 31 * result + editorInfo.hashCode()
        return result
    }
}
