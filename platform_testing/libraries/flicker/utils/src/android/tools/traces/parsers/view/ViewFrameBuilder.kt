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

package android.tools.traces.parsers.view

import android.tools.Timestamps
import android.tools.datatypes.Point
import android.tools.datatypes.PointF
import android.tools.datatypes.Rect
import android.tools.traces.view.ViewFrame
import android.tools.traces.view.ViewNode

class ViewFrameBuilder {
    private var systemUptimeNanos: Long = 0
    private var root: com.android.app.viewcapture.data.ViewNode? = null

    fun forSystemUptime(value: Long): ViewFrameBuilder = apply { systemUptimeNanos = value }

    fun fromRootNode(value: com.android.app.viewcapture.data.ViewNode): ViewFrameBuilder = apply {
        root = value
    }

    private fun parseViewNode(node: com.android.app.viewcapture.data.ViewNode): ViewNode =
        ViewNode.from(
            classNameIndex = node.classnameIndex,
            hashCode = node.hashcode,
            id = node.id,
            bounds =
                Rect.from(
                    node.left,
                    node.top,
                    right = node.left + node.width,
                    bottom = node.top + node.height
                ),
            scroll = Point.from(node.scrollX, node.scrollY),
            translation = PointF.from(node.translationX, node.translationY),
            scale = PointF.from(node.scaleX, node.scaleY),
            alpha = node.alpha.toDouble(),
            willNotDraw = node.willNotDraw,
            clipChildren = node.clipChildren,
            visibility = node.visibility,
            elevation = node.elevation.toDouble(),
            children = node.childrenList.map { parseViewNode(it) }
        )

    fun build(): ViewFrame {
        val root = root
        val systemUptimeNanos = systemUptimeNanos
        requireNotNull(root) { "Root node not specified" }
        require(systemUptimeNanos > 0) { "Timestamp not specified" }

        return ViewFrame(
            timestamp = Timestamps.from(systemUptimeNanos = systemUptimeNanos),
            root = parseViewNode(root)
        )
    }
}
