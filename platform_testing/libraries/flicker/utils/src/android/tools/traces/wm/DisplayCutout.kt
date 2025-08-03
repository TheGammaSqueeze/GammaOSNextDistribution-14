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

import android.tools.datatypes.Insets
import android.tools.datatypes.Rect
import android.tools.withCache

/** Representation of a display cutout from a WM trace */
class DisplayCutout
private constructor(
    val insets: Insets,
    val boundLeft: Rect,
    val boundTop: Rect,
    val boundRight: Rect,
    val boundBottom: Rect,
    val waterfallInsets: Insets
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisplayCutout) return false

        if (insets != other.insets) return false
        if (boundLeft != other.boundLeft) return false
        if (boundTop != other.boundTop) return false
        if (boundRight != other.boundRight) return false
        if (boundBottom != other.boundBottom) return false
        if (waterfallInsets != other.waterfallInsets) return false

        return true
    }

    override fun hashCode(): Int {
        var result = insets.hashCode()
        result = 31 * result + boundLeft.hashCode()
        result = 31 * result + boundTop.hashCode()
        result = 31 * result + boundRight.hashCode()
        result = 31 * result + boundBottom.hashCode()
        result = 31 * result + waterfallInsets.hashCode()
        return result
    }

    override fun toString(): String {
        return "DisplayCutout(" +
            "insets=$insets, " +
            "boundLeft=$boundLeft, " +
            "boundTop=$boundTop, " +
            "boundRight=$boundRight, " +
            "boundBottom=$boundBottom, " +
            "waterfallInsets=$waterfallInsets" +
            ")"
    }

    companion object {
        fun from(
            insets: Insets,
            boundLeft: Rect,
            boundTop: Rect,
            boundRight: Rect,
            boundBottom: Rect,
            waterfallInsets: Insets
        ): DisplayCutout = withCache {
            DisplayCutout(insets, boundLeft, boundTop, boundRight, boundBottom, waterfallInsets)
        }
    }
}
