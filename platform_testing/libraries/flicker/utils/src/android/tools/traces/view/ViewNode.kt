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

package android.tools.traces.view

import android.tools.datatypes.Point
import android.tools.datatypes.PointF
import android.tools.datatypes.Rect
import android.tools.withCache

/**
 * Represents a node in the view hierarchy
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
class ViewNode
private constructor(
    val classNameIndex: Int,
    val hashcode: Int,
    val id: String,
    val bounds: Rect,
    val scroll: Point,
    val translation: PointF,
    val scale: PointF, // [default = 1, 1];
    val alpha: Double, // [default = 1];
    val willNotDraw: Boolean,
    val clipChildren: Boolean,
    val visibility: Int,
    val elevation: Double,
    val children: Collection<ViewNode>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewNode) return false

        if (classNameIndex != other.classNameIndex) return false
        if (hashcode != other.hashcode) return false
        if (id != other.id) return false
        if (bounds != other.bounds) return false
        if (scroll != other.scroll) return false
        if (translation != other.translation) return false
        if (scale != other.scale) return false
        if (alpha != other.alpha) return false
        if (willNotDraw != other.willNotDraw) return false
        if (clipChildren != other.clipChildren) return false
        if (visibility != other.visibility) return false
        if (elevation != other.elevation) return false
        if (children.joinToString { it.id } != other.children.joinToString { it.id }) return false

        return true
    }

    override fun hashCode(): Int {
        var result = classNameIndex
        result = 31 * result + hashcode
        result = 31 * result + id.hashCode()
        result = 31 * result + bounds.hashCode()
        result = 31 * result + scroll.hashCode()
        result = 31 * result + translation.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + alpha.hashCode()
        result = 31 * result + willNotDraw.hashCode()
        result = 31 * result + clipChildren.hashCode()
        result = 31 * result + visibility
        result = 31 * result + elevation.hashCode()
        result = 31 * result + children.joinToString { it.id }.hashCode()
        return result
    }

    override fun toString() =
        "ViewNode(classNameIndex=$classNameIndex, hashCode=$hashcode, id='$id', bounds=$bounds)"

    companion object {
        fun from(
            classNameIndex: Int,
            hashCode: Int,
            id: String,
            bounds: Rect,
            scroll: Point,
            translation: PointF,
            scale: PointF,
            alpha: Double,
            willNotDraw: Boolean,
            clipChildren: Boolean,
            visibility: Int,
            elevation: Double,
            children: Collection<ViewNode>
        ): ViewNode = withCache {
            ViewNode(
                classNameIndex,
                hashCode,
                id,
                bounds,
                scroll,
                translation,
                scale,
                alpha,
                willNotDraw,
                clipChildren,
                visibility,
                elevation,
                children
            )
        }
    }
}
