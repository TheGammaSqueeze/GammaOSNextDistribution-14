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
import android.tools.Timestamps
import android.tools.TraceEntry
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.wm.Utils.collectDescendants

/**
 * Represents a single WindowManager trace entry.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 *
 * The timestamp constructor must be a string due to lack of Kotlin/KotlinJS Long compatibility
 */
class WindowManagerState(
    val elapsedTimestamp: Long,
    val clockTimestamp: Long?,
    val where: String,
    val policy: WindowManagerPolicy?,
    val focusedApp: String,
    val focusedDisplayId: Int,
    private val _focusedWindow: String,
    val inputMethodWindowAppToken: String,
    val isHomeRecentsComponent: Boolean,
    val isDisplayFrozen: Boolean,
    private val _pendingActivities: Collection<String>,
    val root: RootWindowContainer,
    val keyguardControllerState: KeyguardControllerState
) : TraceEntry {
    override val timestamp =
        Timestamps.from(elapsedNanos = elapsedTimestamp, unixNanos = clockTimestamp)

    val isVisible: Boolean = true

    val stableId: String
        get() = this::class.simpleName ?: error("Unable to determine class")
    val isTablet: Boolean
        get() = displays.any { it.isTablet }

    val windowContainers: Collection<WindowContainer>
        get() = root.collectDescendants()

    val children: Collection<WindowContainer>
        get() = root.children.reversed()

    /** Displays in z-order with the top most at the front of the list, starting with primary. */
    val displays: Collection<DisplayContent>
        get() = windowContainers.filterIsInstance<DisplayContent>()

    /**
     * Root tasks in z-order with the top most at the front of the list, starting with primary
     * display.
     */
    val rootTasks: Collection<Task>
        get() = displays.flatMap { it.rootTasks }

    /** TaskFragments in z-order with the top most at the front of the list. */
    val taskFragments: Collection<TaskFragment>
        get() = windowContainers.filterIsInstance<TaskFragment>()

    /** Windows in z-order with the top most at the front of the list. */
    val windowStates: Collection<WindowState>
        get() = windowContainers.filterIsInstance<WindowState>()

    @Deprecated("Please use windowStates instead", replaceWith = ReplaceWith("windowStates"))
    val windows: Collection<WindowState>
        get() = windowStates

    val appWindows: Collection<WindowState>
        get() = windowStates.filter { it.isAppWindow }
    val nonAppWindows: Collection<WindowState>
        get() = windowStates.filterNot { it.isAppWindow }
    val aboveAppWindows: Collection<WindowState>
        get() = windowStates.takeWhile { !appWindows.contains(it) }
    val belowAppWindows: Collection<WindowState>
        get() = windowStates.dropWhile { !appWindows.contains(it) }.drop(appWindows.size)

    val visibleWindows: Collection<WindowState>
        get() =
            windowStates.filter {
                val activities = getActivitiesForWindowState(it)
                val windowIsVisible = it.isVisible
                val activityIsVisible = activities.any { activity -> activity.isVisible }

                // for invisible checks it suffices if activity or window is invisible
                windowIsVisible && (activityIsVisible || activities.isEmpty())
            }
    val visibleAppWindows: Collection<WindowState>
        get() = visibleWindows.filter { it.isAppWindow }
    val topVisibleAppWindow: WindowState?
        get() = visibleAppWindows.firstOrNull()
    val pinnedWindows: Collection<WindowState>
        get() = visibleWindows.filter { it.windowingMode == PlatformConsts.WINDOWING_MODE_PINNED }
    val pendingActivities: Collection<Activity>
        get() = _pendingActivities.mapNotNull { getActivityByName(it) }

    val focusedWindow: WindowState?
        get() = visibleWindows.firstOrNull { it.name == _focusedWindow }

    val isKeyguardShowing: Boolean
        get() = keyguardControllerState.isKeyguardShowing
    val isAodShowing: Boolean
        get() = keyguardControllerState.isAodShowing

    /**
     * Checks if the device state supports rotation, i.e., if the rotation sensor is enabled (e.g.,
     * launcher) and if the rotation not fixed
     */
    val canRotate: Boolean
        get() = policy?.isFixedOrientation != true && policy?.isOrientationNoSensor != true
    val focusedDisplay: DisplayContent?
        get() = getDisplay(focusedDisplayId)
    val focusedStackId: Int
        get() = focusedDisplay?.focusedRootTaskId ?: -1

    val focusedActivity: Activity?
        get() {
            val focusedDisplay = focusedDisplay
            val focusedWindow = focusedWindow
            return when {
                focusedDisplay != null && focusedDisplay.resumedActivity.isNotEmpty() ->
                    getActivityByName(focusedDisplay.resumedActivity)
                focusedWindow != null ->
                    getActivitiesForWindowState(focusedWindow, focusedDisplayId).firstOrNull()
                else -> null
            }
        }
    val resumedActivities: Collection<Activity>
        get() = rootTasks.flatMap { it.resumedActivities }.mapNotNull { getActivityByName(it) }
    val resumedActivitiesCount: Int
        get() = resumedActivities.size
    val stackCount: Int
        get() = rootTasks.size
    val homeTask: Task?
        get() = getStackByActivityType(PlatformConsts.ACTIVITY_TYPE_HOME)?.topTask
    val recentsTask: Task?
        get() = getStackByActivityType(PlatformConsts.ACTIVITY_TYPE_RECENTS)?.topTask
    val homeActivity: Activity?
        get() = homeTask?.activities?.lastOrNull()
    val isHomeActivityVisible: Boolean
        get() {
            val activity = homeActivity
            return activity != null && activity.isVisible
        }
    val recentsActivity: Activity?
        get() = recentsTask?.activities?.lastOrNull()
    val isRecentsActivityVisible: Boolean
        get() {
            return if (isHomeRecentsComponent) {
                isHomeActivityVisible
            } else {
                val activity = recentsActivity
                activity != null && activity.isVisible
            }
        }
    val frontWindow: WindowState?
        get() = windowStates.firstOrNull()
    val inputMethodWindowState: WindowState?
        get() = getWindowStateForAppToken(inputMethodWindowAppToken)

    fun getDefaultDisplay(): DisplayContent? =
        displays.firstOrNull { it.displayId == PlatformConsts.DEFAULT_DISPLAY }

    fun getDisplay(displayId: Int): DisplayContent? =
        displays.firstOrNull { it.displayId == displayId }

    fun countStacks(windowingMode: Int, activityType: Int): Int {
        var count = 0
        for (stack in rootTasks) {
            if (
                activityType != PlatformConsts.ACTIVITY_TYPE_UNDEFINED &&
                    activityType != stack.activityType
            ) {
                continue
            }
            if (
                windowingMode != PlatformConsts.WINDOWING_MODE_UNDEFINED &&
                    windowingMode != stack.windowingMode
            ) {
                continue
            }
            ++count
        }
        return count
    }

    fun getRootTask(taskId: Int): Task? = rootTasks.firstOrNull { it.rootTaskId == taskId }

    fun getRotation(displayId: Int): Rotation =
        getDisplay(displayId)?.rotation ?: error("Default display not found")

    fun getOrientation(displayId: Int): Int =
        getDisplay(displayId)?.lastOrientation ?: error("Default display not found")

    fun getStackByActivityType(activityType: Int): Task? =
        rootTasks.firstOrNull { it.activityType == activityType }

    fun getStandardStackByWindowingMode(windowingMode: Int): Task? =
        rootTasks.firstOrNull {
            it.activityType == PlatformConsts.ACTIVITY_TYPE_STANDARD &&
                it.windowingMode == windowingMode
        }

    fun getActivitiesForWindowState(
        windowState: WindowState,
        displayId: Int = PlatformConsts.DEFAULT_DISPLAY
    ): Collection<Activity> {
        return displays
            .firstOrNull { it.displayId == displayId }
            ?.rootTasks
            ?.mapNotNull { stack ->
                stack.getActivity { activity -> activity.hasWindowState(windowState) }
            }
            ?: emptyList()
    }

    /**
     * Get the all activities on display with id [displayId], containing a matching
     * [componentMatcher]
     *
     * @param componentMatcher Components to search
     * @param displayId display where to search the activity
     */
    fun getActivitiesForWindow(
        componentMatcher: IComponentMatcher,
        displayId: Int = PlatformConsts.DEFAULT_DISPLAY
    ): Collection<Activity> {
        return displays
            .firstOrNull { it.displayId == displayId }
            ?.rootTasks
            ?.mapNotNull { stack ->
                stack.getActivity { activity -> activity.hasWindow(componentMatcher) }
            }
            ?: emptyList()
    }

    /**
     * @param componentMatcher Components to search
     * @return if any activity matches [componentMatcher]
     */
    fun containsActivity(componentMatcher: IComponentMatcher): Boolean =
        rootTasks.any { it.containsActivity(componentMatcher) }

    /**
     * @param componentMatcher Components to search
     * @return the first [Activity] matching [componentMatcher], or null otherwise
     */
    fun getActivity(componentMatcher: IComponentMatcher): Activity? =
        rootTasks.firstNotNullOfOrNull { it.getActivity(componentMatcher) }

    private fun getActivityByName(activityName: String): Activity? =
        rootTasks.firstNotNullOfOrNull { task ->
            task.getActivity { activity -> activity.title.contains(activityName) }
        }

    /**
     * @param componentMatcher Components to search
     * @return if any activity matching [componentMatcher] is visible
     */
    fun isActivityVisible(componentMatcher: IComponentMatcher): Boolean =
        getActivity(componentMatcher)?.isVisible ?: false

    /**
     * @param componentMatcher Components to search
     * @param activityState expected activity state
     * @return if any activity matching [componentMatcher] has state of [activityState]
     */
    fun hasActivityState(componentMatcher: IComponentMatcher, activityState: String): Boolean =
        rootTasks.any { it.getActivity(componentMatcher)?.state == activityState }

    /**
     * @param componentMatcher Components to search
     * @return if any pending activities match [componentMatcher]
     */
    fun pendingActivityContain(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.activityMatchesAnyOf(pendingActivities)

    /**
     * @param componentMatcher Components to search
     * @return the visible [WindowState]s matching [componentMatcher]
     */
    fun getMatchingVisibleWindowState(
        componentMatcher: IComponentMatcher
    ): Collection<WindowState> {
        return windowStates.filter { it.isSurfaceShown && componentMatcher.windowMatchesAnyOf(it) }
    }

    /** @return the [WindowState] for the nav bar in the display with id [displayId] */
    fun getNavBarWindow(displayId: Int): WindowState? {
        val navWindow = windowStates.filter { it.isValidNavBarType && it.displayId == displayId }

        // We may need some time to wait for nav bar showing.
        // It's Ok to get 0 nav bar here.
        if (navWindow.size > 1) {
            throw IllegalStateException("There should be at most one navigation bar on a display")
        }
        return navWindow.firstOrNull()
    }

    private fun getWindowStateForAppToken(appToken: String): WindowState? =
        windowStates.firstOrNull { it.token == appToken }

    /**
     * Checks if there exists a [WindowState] matching [componentMatcher]
     *
     * @param componentMatcher Components to search
     */
    fun containsWindow(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.windowMatchesAnyOf(windowStates)

    /**
     * Check if at least one [WindowState] matching [componentMatcher] is visible
     *
     * @param componentMatcher Components to search
     */
    fun isWindowSurfaceShown(componentMatcher: IComponentMatcher): Boolean =
        getMatchingVisibleWindowState(componentMatcher).isNotEmpty()

    /** Checks if the state has any window in PIP mode */
    fun hasPipWindow(): Boolean = pinnedWindows.isNotEmpty()

    /**
     * Checks that a [WindowState] matching [componentMatcher] is in PIP mode
     *
     * @param componentMatcher Components to search
     */
    fun isInPipMode(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.windowMatchesAnyOf(pinnedWindows)

    fun getZOrder(w: WindowState): Int = windowStates.size - windowStates.indexOf(w)

    fun defaultMinimalTaskSize(displayId: Int): Int =
        dpToPx(PlatformConsts.DEFAULT_RESIZABLE_TASK_SIZE_DP.toFloat(), getDisplay(displayId)!!.dpi)

    fun defaultMinimalDisplaySizeForSplitScreen(displayId: Int): Int {
        return dpToPx(
            PlatformConsts.DEFAULT_MINIMAL_SPLIT_SCREEN_DISPLAY_SIZE_DP.toFloat(),
            getDisplay(displayId)!!.dpi
        )
    }

    /**
     * Checks if a [WindowState] matching [componentMatcher] exists
     *
     * @param componentMatcher Components to search
     */
    fun contains(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.windowMatchesAnyOf(windowStates)

    /**
     * Checks if a [WindowState] matching [componentMatcher] is visible
     *
     * @param componentMatcher Components to search
     */
    fun isVisible(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.windowMatchesAnyOf(visibleWindows)

    /**
     * Checks if a [WindowState] matching [componentMatcher] exists and is a non-app window
     *
     * @param componentMatcher Components to search
     */
    fun isNonAppWindow(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.windowMatchesAnyOf(nonAppWindows)

    /**
     * Checks if a [WindowState] matching [componentMatcher] exists and is an app window
     *
     * @param componentMatcher Components to search
     */
    fun isAppWindow(componentMatcher: IComponentMatcher): Boolean {
        val activity = getActivitiesForWindow(componentMatcher).firstOrNull()
        return activity != null && componentMatcher.windowMatchesAnyOf(appWindows)
    }

    /**
     * Checks if a [WindowState] matching [componentMatcher] exists and is above all app window
     *
     * @param componentMatcher Components to search
     */
    fun isAboveAppWindow(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.windowMatchesAnyOf(aboveAppWindows)

    /**
     * Checks if a [WindowState] matching [componentMatcher] exists and is below all app window
     *
     * @param componentMatcher Components to search
     */
    fun isBelowAppWindow(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.windowMatchesAnyOf(belowAppWindows)

    fun getIsIncompleteReason(): String {
        return buildString {
            if (rootTasks.isEmpty()) {
                append("No stacks found...")
            }
            if (focusedStackId == -1) {
                append("No focused stack found...")
            }
            if (focusedActivity == null) {
                append("No focused activity found...")
            }
            if (resumedActivities.isEmpty()) {
                append("No resumed activities found...")
            }
            if (windowStates.isEmpty()) {
                append("No Windows found...")
            }
            if (focusedWindow == null) {
                append("No Focused Window...")
            }
            if (focusedApp.isEmpty()) {
                append("No Focused App...")
            }
            if (keyguardControllerState.isKeyguardShowing) {
                append("Keyguard showing...")
            }
        }
    }

    fun isComplete(): Boolean = !isIncomplete()

    fun isIncomplete(): Boolean {
        var incomplete = stackCount == 0
        // TODO: Update when keyguard will be shown on multiple displays
        if (!keyguardControllerState.isKeyguardShowing) {
            incomplete = incomplete || (resumedActivitiesCount == 0)
        }
        incomplete = incomplete || (focusedActivity == null)
        rootTasks.forEach { aStack ->
            val stackId = aStack.rootTaskId
            aStack.tasks.forEach { aTask ->
                incomplete = incomplete || (stackId != aTask.rootTaskId)
            }
        }
        incomplete = incomplete || frontWindow == null
        incomplete = incomplete || focusedWindow == null
        incomplete = incomplete || focusedApp.isEmpty()
        return incomplete
    }

    fun asTrace(): WindowManagerTrace = WindowManagerTrace(listOf(this))

    override fun toString(): String {
        return timestamp.toString()
    }

    companion object {
        fun dpToPx(dp: Float, densityDpi: Int): Int {
            return (dp * densityDpi / PlatformConsts.DENSITY_DEFAULT + 0.5f).toInt()
        }
    }
    override fun equals(other: Any?): Boolean {
        return other is WindowManagerState && other.timestamp == this.timestamp
    }

    override fun hashCode(): Int {
        var result = where.hashCode()
        result = 31 * result + (policy?.hashCode() ?: 0)
        result = 31 * result + focusedApp.hashCode()
        result = 31 * result + focusedDisplayId
        result = 31 * result + focusedWindow.hashCode()
        result = 31 * result + inputMethodWindowAppToken.hashCode()
        result = 31 * result + isHomeRecentsComponent.hashCode()
        result = 31 * result + isDisplayFrozen.hashCode()
        result = 31 * result + pendingActivities.hashCode()
        result = 31 * result + root.hashCode()
        result = 31 * result + keyguardControllerState.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isVisible.hashCode()
        return result
    }
}
