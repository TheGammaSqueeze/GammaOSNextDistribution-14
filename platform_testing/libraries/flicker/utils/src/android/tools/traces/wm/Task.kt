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
import android.tools.traces.component.IComponentMatcher

/**
 * Represents a task in the window manager hierarchy
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
class Task(
    override val activityType: Int,
    override val isFullscreen: Boolean,
    override val bounds: Rect,
    val taskId: Int,
    val rootTaskId: Int,
    val displayId: Int,
    val lastNonFullscreenBounds: Rect,
    val realActivity: String,
    val origActivity: String,
    val resizeMode: Int,
    private val _resumedActivity: String,
    var animatingBounds: Boolean,
    val surfaceWidth: Int,
    val surfaceHeight: Int,
    val createdByOrganizer: Boolean,
    val minWidth: Int,
    val minHeight: Int,
    private val windowContainer: WindowContainer
) : WindowContainer by windowContainer {
    override val isVisible: Boolean = false
    override val name: String = taskId.toString()
    override val isEmpty: Boolean
        get() = tasks.isEmpty() && activities.isEmpty()
    override val stableId: String
        get() = "${this::class.simpleName} $token $title $taskId"

    val isRootTask: Boolean
        get() = taskId == rootTaskId
    val tasks: Collection<Task>
        get() = this.children.reversed().filterIsInstance<Task>()
    val taskFragments: Collection<TaskFragment>
        get() = this.children.reversed().filterIsInstance<TaskFragment>()
    val activities: Collection<Activity>
        get() = this.children.reversed().filterIsInstance<Activity>()

    /** The top task in the stack. */
    // NOTE: Unlike the WindowManager internals, we dump the state from top to bottom,
    //       so the indices are inverted
    val topTask: Task?
        get() = tasks.firstOrNull()
    val resumedActivities: Collection<String>
        get() {
            val result = mutableListOf<String>()
            if (this._resumedActivity.isNotEmpty()) {
                result.add(this._resumedActivity)
            }
            val activitiesInChildren =
                this.tasks.flatMap { it.resumedActivities }.filter { it.isNotEmpty() }
            result.addAll(activitiesInChildren)
            return result
        }

    /** @return The first [Task] matching [predicate], or null otherwise */
    fun getTask(predicate: (Task) -> Boolean) =
        tasks.firstOrNull { predicate(it) } ?: if (predicate(this)) this else null

    /** @return the first [Activity] matching [predicate], or null otherwise */
    internal fun getActivity(predicate: (Activity) -> Boolean): Activity? {
        var activity: Activity? = activities.firstOrNull { predicate(it) }
        if (activity != null) {
            return activity
        }
        for (task in tasks) {
            activity = task.getActivity(predicate)
            if (activity != null) {
                return activity
            }
        }
        for (taskFragment in taskFragments) {
            activity = taskFragment.getActivity(predicate)
            if (activity != null) {
                return activity
            }
        }
        return null
    }

    /**
     * @param componentMatcher Components to search
     * @return the first [Activity] matching [componentMatcher], or null otherwise
     */
    fun getActivity(componentMatcher: IComponentMatcher): Activity? = getActivity { activity ->
        componentMatcher.activityMatchesAnyOf(activity)
    }

    /**
     * @param componentMatcher Components to search
     * @return if any activity matches [componentMatcher]
     */
    fun containsActivity(componentMatcher: IComponentMatcher) =
        getActivity(componentMatcher) != null

    override fun toString(): String {
        return "${this::class.simpleName}: {$token $title} id=$taskId bounds=$bounds"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Task) return false

        if (activityType != other.activityType) return false
        if (isFullscreen != other.isFullscreen) return false
        if (bounds != other.bounds) return false
        if (taskId != other.taskId) return false
        if (rootTaskId != other.rootTaskId) return false
        if (displayId != other.displayId) return false
        if (realActivity != other.realActivity) return false
        if (resizeMode != other.resizeMode) return false
        if (minWidth != other.minWidth) return false
        if (minHeight != other.minHeight) return false
        if (name != other.name) return false
        if (orientation != other.orientation) return false
        if (title != other.title) return false
        if (token != other.token) return false
        if (windowContainer != other.windowContainer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + activityType
        result = 31 * result + isFullscreen.hashCode()
        result = 31 * result + bounds.hashCode()
        result = 31 * result + taskId
        result = 31 * result + rootTaskId
        result = 31 * result + displayId
        result = 31 * result + lastNonFullscreenBounds.hashCode()
        result = 31 * result + realActivity.hashCode()
        result = 31 * result + origActivity.hashCode()
        result = 31 * result + resizeMode
        result = 31 * result + _resumedActivity.hashCode()
        result = 31 * result + animatingBounds.hashCode()
        result = 31 * result + surfaceWidth
        result = 31 * result + surfaceHeight
        result = 31 * result + createdByOrganizer.hashCode()
        result = 31 * result + minWidth
        result = 31 * result + minHeight
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + windowContainer.hashCode()
        return result
    }
}
