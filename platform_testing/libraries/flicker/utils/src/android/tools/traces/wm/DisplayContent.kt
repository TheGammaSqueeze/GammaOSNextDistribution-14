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

import android.tools.PlatformConsts
import android.tools.Rotation
import android.tools.datatypes.Rect
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.wm.Utils.collectDescendants
import kotlin.math.min

/**
 * Represents a display content in the window manager hierarchy
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
class DisplayContent(
    val displayId: Int,
    val focusedRootTaskId: Int,
    val resumedActivity: String,
    val singleTaskInstance: Boolean,
    val defaultPinnedStackBounds: Rect,
    val pinnedStackMovementBounds: Rect,
    val displayRect: Rect,
    val appRect: Rect,
    val dpi: Int,
    val flags: Int,
    val stableBounds: Rect,
    val surfaceSize: Int,
    val focusedApp: String,
    val lastTransition: String,
    val appTransitionState: String,
    val rotation: Rotation,
    val lastOrientation: Int,
    val cutout: DisplayCutout?,
    private val windowContainer: WindowContainer
) : WindowContainer by windowContainer {
    override val name: String = displayId.toString()
    override val isVisible: Boolean = false

    val isTablet: Boolean
        get() {
            val smallestWidth =
                dpiFromPx(min(displayRect.width.toFloat(), displayRect.height.toFloat()), dpi)
            return smallestWidth >= PlatformConsts.TABLET_MIN_DPS
        }

    val rootTasks: Collection<Task>
        get() {
            val tasks = collectDescendants<Task> { it.isRootTask }.toMutableList()
            // TODO(b/149338177): figure out how CTS tests deal with organizer. For now,
            //                    don't treat them as regular stacks
            val rootOrganizedTasks = mutableListOf<Task>()
            val reversedTaskList = tasks.reversed()
            reversedTaskList.forEach { task ->
                // Skip tasks created by an organizer
                if (task.createdByOrganizer) {
                    tasks.remove(task)
                    rootOrganizedTasks.add(task)
                }
            }
            // Add root tasks controlled by an organizer
            rootOrganizedTasks.reversed().forEach { task ->
                tasks.addAll(task.children.reversed().map { it as Task })
            }

            return tasks
        }

    /**
     * @param componentMatcher Components to search
     * @return if [componentMatcher] matches any activity
     */
    fun containsActivity(componentMatcher: IComponentMatcher): Boolean =
        rootTasks.any { it.containsActivity(componentMatcher) }

    /**
     * @param componentMatcher Components to search
     * @return THe [DisplayArea] matching [componentMatcher], or null if none matches
     */
    fun getTaskDisplayArea(componentMatcher: IComponentMatcher): DisplayArea? {
        val taskDisplayAreas =
            this.collectDescendants<DisplayArea> { it.isTaskDisplayArea }
                .filter { it.containsActivity(componentMatcher) }

        if (taskDisplayAreas.size > 1) {
            throw IllegalArgumentException(
                "There must be exactly one activity among all TaskDisplayAreas."
            )
        }

        return taskDisplayAreas.firstOrNull()
    }

    override fun toString(): String {
        return "${this::class.simpleName} #$displayId: name=$title mDisplayRect=$displayRect " +
            "mAppRect=$appRect mFlags=$flags"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisplayContent) return false
        if (!super.equals(other)) return false

        if (displayId != other.displayId) return false
        if (focusedRootTaskId != other.focusedRootTaskId) return false
        if (resumedActivity != other.resumedActivity) return false
        if (defaultPinnedStackBounds != other.defaultPinnedStackBounds) return false
        if (pinnedStackMovementBounds != other.pinnedStackMovementBounds) return false
        if (stableBounds != other.stableBounds) return false
        if (displayRect != other.displayRect) return false
        if (appRect != other.appRect) return false
        if (dpi != other.dpi) return false
        if (flags != other.flags) return false
        if (focusedApp != other.focusedApp) return false
        if (lastTransition != other.lastTransition) return false
        if (appTransitionState != other.appTransitionState) return false
        if (rotation != other.rotation) return false
        if (lastOrientation != other.lastOrientation) return false
        if (cutout != other.cutout) return false
        if (name != other.name) return false
        if (singleTaskInstance != other.singleTaskInstance) return false
        if (surfaceSize != other.surfaceSize) return false
        if (windowContainer != other.windowContainer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + displayId
        result = 31 * result + focusedRootTaskId
        result = 31 * result + resumedActivity.hashCode()
        result = 31 * result + singleTaskInstance.hashCode()
        result = 31 * result + defaultPinnedStackBounds.hashCode()
        result = 31 * result + pinnedStackMovementBounds.hashCode()
        result = 31 * result + displayRect.hashCode()
        result = 31 * result + appRect.hashCode()
        result = 31 * result + dpi
        result = 31 * result + flags
        result = 31 * result + stableBounds.hashCode()
        result = 31 * result + surfaceSize
        result = 31 * result + focusedApp.hashCode()
        result = 31 * result + lastTransition.hashCode()
        result = 31 * result + appTransitionState.hashCode()
        result = 31 * result + rotation.value
        result = 31 * result + lastOrientation
        result = 31 * result + cutout.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + windowContainer.hashCode()
        return result
    }

    companion object {
        /** From [android.util.DisplayMetrics] */
        const val DENSITY_DEFAULT = 160f

        /** From [com.android.systemui.shared.recents.utilities.Utilities] */
        const val TABLET_MIN_DPS = 600f

        fun dpiFromPx(size: Float, densityDpi: Int): Float {
            val densityRatio: Float = densityDpi.toFloat() / DENSITY_DEFAULT
            return size / densityRatio
        }
    }
}
