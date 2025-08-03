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

package android.tools.traces.surfaceflinger

import android.tools.Position
import android.tools.Rotation
import android.tools.datatypes.Rect
import android.tools.datatypes.Size
import android.tools.traces.wm.DisplayContent
import android.tools.withCache
import kotlin.math.min

/** Wrapper for DisplayProto (frameworks/native/services/surfaceflinger/layerproto/display.proto) */
class Display
private constructor(
    val id: Long,
    val name: String,
    val layerStackId: Int,
    val size: Size,
    val layerStackSpace: Rect,
    val transform: Transform,
    val isVirtual: Boolean,
    val dpiX: Double,
    val dpiY: Double,
) {
    val isOff = layerStackId == BLANK_LAYER_STACK
    val isOn = !isOff

    // Alias for layerStackSpace, since bounds is what is used for layers
    val bounds = layerStackSpace

    fun navBarPosition(isGesturalNavigation: Boolean): Position {
        val requestedRotation = transform.getRotation()

        return when {
            // display off
            isOff -> Position.INVALID
            // nav bar is at the bottom of the screen
            !requestedRotation.isRotated() || isGesturalNavigation -> Position.BOTTOM
            // nav bar is on the right side
            requestedRotation == Rotation.ROTATION_90 -> Position.RIGHT
            // nav bar is on the left side
            requestedRotation == Rotation.ROTATION_270 -> Position.LEFT
            else -> error("Unknown rotation $requestedRotation")
        }
    }

    val isLargeScreen: Boolean
        get() {
            val dpi = dpiX.toInt()
            val smallestWidth =
                DisplayContent.dpiFromPx(min(size.width.toFloat(), size.height.toFloat()), dpi)
            return smallestWidth >= DisplayContent.TABLET_MIN_DPS
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Display) return false

        if (id != other.id) return false
        if (name != other.name) return false
        if (layerStackId != other.layerStackId) return false
        if (size != other.size) return false
        if (layerStackSpace != other.layerStackSpace) return false
        if (transform != other.transform) return false
        if (isVirtual != other.isVirtual) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + layerStackId
        result = 31 * result + size.hashCode()
        result = 31 * result + layerStackSpace.hashCode()
        result = 31 * result + transform.hashCode()
        result = 31 * result + isVirtual.hashCode()
        return result
    }

    companion object {
        const val BLANK_LAYER_STACK = -1

        val EMPTY: Display
            get() = withCache {
                Display(
                    id = 0,
                    name = "EMPTY",
                    layerStackId = BLANK_LAYER_STACK,
                    size = Size.EMPTY,
                    layerStackSpace = Rect.EMPTY,
                    transform = Transform.EMPTY,
                    isVirtual = false,
                    dpiX = 0.0,
                    dpiY = 0.0,
                )
            }

        fun from(
            id: Long,
            name: String,
            layerStackId: Int,
            size: Size,
            layerStackSpace: Rect,
            transform: Transform,
            isVirtual: Boolean,
            dpiX: Double,
            dpiY: Double,
        ): Display = withCache {
            Display(id, name, layerStackId, size, layerStackSpace, transform, isVirtual, dpiX, dpiY)
        }
    }
}
