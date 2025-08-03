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

import android.tools.datatypes.Rect

/**
 * Represents WindowContainer classes such as DisplayContent.WindowContainers and
 * DisplayContent.NonAppWindowContainers. This can be expanded into a specific class if we need
 * track and assert some state in the future.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
class WindowContainerImpl(
    override val title: String,
    final override val token: String,
    override val orientation: Int,
    override val layerId: Int,
    _isVisible: Boolean,
    private val configurationContainer: ConfigurationContainer,
    _children: Collection<WindowContainer>,
    override val computedZ: Int
) : ConfigurationContainer by configurationContainer, WindowContainer {
    override val id: Int = if (token.isEmpty()) -1 else token.toInt(16)

    override val children: Collection<WindowContainer> = _children

    override var parent: WindowContainer? = null

    init {
        _children.forEach { it.parent = this }
    }

    override val isVisible: Boolean = _isVisible
    override val name: String
        get() = title
    override val stableId: String
        get() = "${this::class.simpleName} $token $title"
    override val isFullscreen: Boolean = false
    override val bounds: Rect = Rect.EMPTY

    override fun toString(): String {
        if (
            this.title.isEmpty() ||
                listOf("WindowContainer", "Task").any { it.contains(this.title) }
        ) {
            return ""
        }

        return "$${removeRedundancyInName(this.title)}@${this.token}"
    }

    private fun removeRedundancyInName(name: String): String {
        if (!name.contains('/')) {
            return name
        }

        val split = name.split('/')
        val pkg = split[0]
        var clazz = split.slice(1..split.lastIndex).joinToString("/")

        if (clazz.startsWith("$pkg.")) {
            clazz = clazz.slice(pkg.length + 1..clazz.lastIndex)

            return "$pkg/$clazz"
        }

        return name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowContainerImpl) return false

        if (title != other.title) return false
        if (token != other.token) return false
        if (orientation != other.orientation) return false
        if (isVisible != other.isVisible) return false
        if (name != other.name) return false
        if (isFullscreen != other.isFullscreen) return false
        if (bounds != other.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + orientation
        result = 31 * result + children.hashCode()
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isFullscreen.hashCode()
        result = 31 * result + bounds.hashCode()
        return result
    }

    override val isEmpty: Boolean
        get() = configurationContainer.isEmpty && title.isEmpty() && token.isEmpty()
}
