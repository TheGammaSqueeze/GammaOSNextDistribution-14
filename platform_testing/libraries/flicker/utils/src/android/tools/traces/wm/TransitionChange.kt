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

import android.tools.traces.surfaceflinger.LayersTrace

class TransitionChange(
    val transitMode: TransitionType,
    val layerId: Int,
    val windowId: Int,
) {

    override fun toString(): String = Formatter(null, null).format(this)

    override fun hashCode(): Int {
        var result = transitMode.hashCode()
        result = 31 * result + layerId
        result = 31 * result + windowId
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransitionChange) return false

        if (transitMode != other.transitMode) return false
        if (layerId != other.layerId) return false
        return windowId == other.windowId
    }

    class Formatter(val layersTrace: LayersTrace?, val wmTrace: WindowManagerTrace?) {
        fun format(change: TransitionChange): String {
            val layerName =
                layersTrace
                    ?.entries
                    ?.flatMap { it.flattenedLayers }
                    ?.firstOrNull { it.id == change.layerId }
                    ?.name

            val windowName =
                wmTrace
                    ?.entries
                    ?.flatMap { it.windowStates }
                    ?.firstOrNull { it.id == change.windowId }
                    ?.name

            return buildString {
                append("TransitionChange(")
                append("transitMode=${change.transitMode}, ")
                append("layerId=${change.layerId}, ")
                if (layerName != null) {
                    append("layerName=$layerName, ")
                }
                append("windowId=${change.windowId}, ")
                if (windowName != null) {
                    append("windowName=$windowName, ")
                }
                append(")")
            }
        }
    }
}
