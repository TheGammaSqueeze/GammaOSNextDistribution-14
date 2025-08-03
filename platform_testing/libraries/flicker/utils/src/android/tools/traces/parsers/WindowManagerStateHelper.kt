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

package android.tools.traces.parsers

import android.app.ActivityTaskManager
import android.app.Instrumentation
import android.app.WindowConfiguration
import android.os.SystemClock
import android.os.Trace
import android.tools.AndroidLogger
import android.tools.CrossPlatform
import android.tools.Logger
import android.tools.Rotation
import android.tools.TimestampFactory
import android.tools.datatypes.Region
import android.tools.traces.Condition
import android.tools.traces.ConditionsFactory
import android.tools.traces.DeviceStateDump
import android.tools.traces.LOG_TAG
import android.tools.traces.WaitCondition
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.ComponentNameMatcher.Companion.IME
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.component.ComponentNameMatcher.Companion.SNAPSHOT
import android.tools.traces.component.ComponentNameMatcher.Companion.SPLASH_SCREEN
import android.tools.traces.component.ComponentNameMatcher.Companion.SPLIT_DIVIDER
import android.tools.traces.component.ComponentNameMatcher.Companion.TRANSITION_SNAPSHOT
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.formatRealTimestamp
import android.tools.traces.getCurrentStateDump
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.wm.Activity
import android.tools.traces.wm.WindowManagerState
import android.tools.traces.wm.WindowManagerTrace
import android.tools.traces.wm.WindowState
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry

/** Helper class to wait on [WindowManagerState] or [LayerTraceEntry] conditions */
open class WindowManagerStateHelper
@JvmOverloads
constructor(
    /** Instrumentation to run the tests */
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    private val clearCacheAfterParsing: Boolean = true,
    /** Predicate to supply a new UI information */
    private val deviceDumpSupplier: () -> DeviceStateDump = {
        getCurrentStateDump(clearCacheAfterParsing = clearCacheAfterParsing)
    },
    /** Number of attempts to satisfy a wait condition */
    private val numRetries: Int = DEFAULT_RETRY_LIMIT,
    /** Interval between wait for state dumps during wait conditions */
    private val retryIntervalMs: Long = DEFAULT_RETRY_INTERVAL_MS
) {
    init {
        CrossPlatform.setLogger(AndroidLogger())
            .setTimestampFactory(TimestampFactory { formatRealTimestamp(it) })
    }

    private var internalState: DeviceStateDump? = null

    /** Queries the supplier for a new device state */
    val currentState: DeviceStateDump
        get() {
            if (internalState == null) {
                internalState = deviceDumpSupplier.invoke()
            } else {
                StateSyncBuilder().withValidState().waitFor()
            }
            return internalState ?: error("Unable to fetch an internal state")
        }

    protected open fun updateCurrState(value: DeviceStateDump) {
        internalState = value
    }

    /**
     * @param componentMatcher Components to search
     * @return a [WindowState] from the current device state matching [componentMatcher], or null
     *   otherwise
     */
    fun getWindow(componentMatcher: IComponentMatcher): WindowState? {
        return this.currentState.wmState.windowStates.firstOrNull {
            componentMatcher.windowMatchesAnyOf(it)
        }
    }

    /**
     * @param componentMatcher Components to search
     * @return The frame [Region] a [WindowState] matching [componentMatcher]
     */
    fun getWindowRegion(componentMatcher: IComponentMatcher): Region =
        getWindow(componentMatcher)?.frameRegion ?: Region.EMPTY

    /**
     * Class to build conditions for waiting on specific [WindowManagerTrace] and [LayersTrace]
     * conditions
     */
    inner class StateSyncBuilder {
        private val conditionBuilder = createConditionBuilder()
        private var lastMessage = ""

        private fun createConditionBuilder(): WaitCondition.Builder<DeviceStateDump> =
            WaitCondition.Builder(deviceDumpSupplier, numRetries)
                .onStart { Trace.beginSection(it) }
                .onEnd { Trace.endSection() }
                .onSuccess { updateCurrState(it) }
                .onFailure { updateCurrState(it) }
                .onLog { msg, isError ->
                    lastMessage = msg
                    if (isError) {
                        Logger.e(LOG_TAG, msg)
                    } else {
                        Logger.d(LOG_TAG, msg)
                    }
                }
                .onRetry { SystemClock.sleep(retryIntervalMs) }

        /**
         * Adds a new [condition] to the list
         *
         * @param condition to wait for
         */
        fun add(condition: Condition<DeviceStateDump>): StateSyncBuilder = apply {
            conditionBuilder.withCondition(condition)
        }

        /**
         * Adds a new [condition] to the list
         *
         * @param message describing the condition
         * @param condition to wait for
         */
        @JvmOverloads
        fun add(message: String = "", condition: (DeviceStateDump) -> Boolean): StateSyncBuilder =
            add(Condition(message, condition))

        /**
         * Waits until the list of conditions added to [conditionBuilder] are satisfied
         *
         * @return if the device state passed all conditions or not
         */
        fun waitFor(): Boolean {
            val passed = conditionBuilder.build().waitFor()
            // Ensure WindowManagerService wait until all animations have completed
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.syncInputTransactions()
            return passed
        }

        /**
         * Waits until the list of conditions added to [conditionBuilder] are satisfied and verifies
         * the device state passes all conditions
         *
         * @throws IllegalArgumentException if the conditions were not met
         */
        fun waitForAndVerify() {
            val success = waitFor()
            require(success) {
                buildString {
                    appendLine(lastMessage)

                    val wmState = internalState?.wmState
                    val layerState = internalState?.layerState

                    if (wmState != null) {
                        appendLine("Last checked WM state at ${wmState.timestamp}.")
                    }
                    if (layerState != null) {
                        appendLine("Last checked layer state at ${layerState.timestamp}.")
                    }
                }
            }
        }

        /**
         * Waits for an app matching [componentMatcher] to be visible, in full screen, and for
         * nothing to be animating
         *
         * @param componentMatcher Components to search
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withFullScreenApp(
            componentMatcher: IComponentMatcher,
            displayId: Int = Display.DEFAULT_DISPLAY
        ) =
            withFullScreenAppCondition(componentMatcher)
                .withAppTransitionIdle(displayId)
                .add(ConditionsFactory.isLayerVisible(componentMatcher))

        /**
         * Waits until the home activity is visible and nothing to be animating
         *
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withHomeActivityVisible(displayId: Int = Display.DEFAULT_DISPLAY) =
            withAppTransitionIdle(displayId)
                .withNavOrTaskBarVisible()
                .withStatusBarVisible()
                .add(ConditionsFactory.isHomeActivityVisible())
                .add(ConditionsFactory.isLauncherLayerVisible())

        /**
         * Waits until the split-screen divider is visible and nothing to be animating
         *
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withSplitDividerVisible(displayId: Int = Display.DEFAULT_DISPLAY) =
            withAppTransitionIdle(displayId).add(ConditionsFactory.isLayerVisible(SPLIT_DIVIDER))

        /**
         * Waits until the home activity is visible and nothing to be animating
         *
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withRecentsActivityVisible(displayId: Int = Display.DEFAULT_DISPLAY) =
            withAppTransitionIdle(displayId)
                .add(ConditionsFactory.isRecentsActivityVisible())
                .add(ConditionsFactory.isLayerVisible(LAUNCHER))

        /**
         * Wait for specific rotation for the display with id [displayId]
         *
         * @param rotation expected. Values are [Surface#Rotation]
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withRotation(rotation: Rotation, displayId: Int = Display.DEFAULT_DISPLAY) =
            withAppTransitionIdle(displayId).add(ConditionsFactory.hasRotation(rotation, displayId))

        /**
         * Waits until a [WindowState] matching [componentMatcher] has a state of [activityState]
         *
         * @param componentMatcher Components to search
         * @param activityStates expected activity states
         */
        fun withActivityState(componentMatcher: IComponentMatcher, vararg activityStates: String) =
            add(
                Condition(
                    "state of ${componentMatcher.toActivityIdentifier()} to be any of " +
                        activityStates.joinToString()
                ) {
                    activityStates.any { state ->
                        it.wmState.hasActivityState(componentMatcher, state)
                    }
                }
            )

        /**
         * Waits until the [ComponentNameMatcher.NAV_BAR] or [ComponentNameMatcher.TASK_BAR] are
         * visible (windows and layers)
         */
        fun withNavOrTaskBarVisible() = add(ConditionsFactory.isNavOrTaskBarVisible())

        /** Waits until the navigation and status bars are visible (windows and layers) */
        fun withStatusBarVisible() = add(ConditionsFactory.isStatusBarVisible())

        /**
         * Wait until neither an [Activity] nor a [WindowState] matching [componentMatcher] exist on
         * the display with id [displayId] and for nothing to be animating
         *
         * @param componentMatcher Components to search
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withActivityRemoved(
            componentMatcher: IComponentMatcher,
            displayId: Int = Display.DEFAULT_DISPLAY
        ) =
            withAppTransitionIdle(displayId)
                .add(ConditionsFactory.containsActivity(componentMatcher).negate())
                .add(ConditionsFactory.containsWindow(componentMatcher).negate())

        /**
         * Wait until the splash screen and snapshot starting windows no longer exist, no layers are
         * animating, and [WindowManagerState] is idle on display [displayId]
         *
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withAppTransitionIdle(displayId: Int = Display.DEFAULT_DISPLAY) =
            withSplashScreenGone()
                .withSnapshotGone()
                .add(ConditionsFactory.isAppTransitionIdle(displayId))
                .add(ConditionsFactory.hasLayersAnimating().negate())

        /**
         * Wait until least one [WindowState] matching [componentMatcher] is not visible on display
         * with idd [displayId] and nothing is animating
         *
         * @param componentMatcher Components to search
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withWindowSurfaceDisappeared(
            componentMatcher: IComponentMatcher,
            displayId: Int = Display.DEFAULT_DISPLAY
        ) =
            withAppTransitionIdle(displayId)
                .add(ConditionsFactory.isWindowSurfaceShown(componentMatcher).negate())
                .add(ConditionsFactory.isLayerVisible(componentMatcher).negate())
                .add(ConditionsFactory.isAppTransitionIdle(displayId))

        /**
         * Wait until least one [WindowState] matching [componentMatcher] is visible on display with
         * idd [displayId] and nothing is animating
         *
         * @param componentMatcher Components to search
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withWindowSurfaceAppeared(
            componentMatcher: IComponentMatcher,
            displayId: Int = Display.DEFAULT_DISPLAY
        ) =
            withAppTransitionIdle(displayId)
                .add(ConditionsFactory.isWindowSurfaceShown(componentMatcher))
                .add(ConditionsFactory.isLayerVisible(componentMatcher))

        /**
         * Wait until least one layer matching [componentMatcher] has [expectedRegion]
         *
         * @param componentMatcher Components to search
         * @param expectedRegion of the target surface
         */
        fun withSurfaceVisibleRegion(componentMatcher: IComponentMatcher, expectedRegion: Region) =
            add(
                Condition("surfaceRegion") {
                    val layer =
                        it.layerState.visibleLayers.firstOrNull { layer ->
                            componentMatcher.layerMatchesAnyOf(layer)
                        }

                    layer?.visibleRegion == expectedRegion
                }
            )

        /**
         * Waits until the IME window and layer are visible
         *
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withImeShown(displayId: Int = Display.DEFAULT_DISPLAY) =
            withAppTransitionIdle(displayId).add(ConditionsFactory.isImeShown(displayId))

        /**
         * Waits until the [IME] layer is no longer visible.
         *
         * Cannot wait for the window as its visibility information is updated at a later state and
         * is not reliable in the trace
         *
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withImeGone(displayId: Int = Display.DEFAULT_DISPLAY) =
            withAppTransitionIdle(displayId)
                .add(ConditionsFactory.isLayerVisible(IME).negate())
                .add(ConditionsFactory.isImeShown(displayId).negate())

        /**
         * Waits until a window is in PIP mode. That is:
         * - wait until a window is pinned ([WindowManagerState.pinnedWindows])
         * - no layers animating
         * - and [ComponentNameMatcher.PIP_CONTENT_OVERLAY] is no longer visible
         *
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withPipShown(displayId: Int = Display.DEFAULT_DISPLAY) =
            withAppTransitionIdle(displayId).add(ConditionsFactory.hasPipWindow())

        /**
         * Waits until a window is no longer in PIP mode. That is:
         * - wait until there are no pinned ([WindowManagerState.pinnedWindows])
         * - no layers animating
         * - and [ComponentNameMatcher.PIP_CONTENT_OVERLAY] is no longer visible
         *
         * @param displayId of the target display
         */
        @JvmOverloads
        fun withPipGone(displayId: Int = Display.DEFAULT_DISPLAY) =
            withAppTransitionIdle(displayId).add(ConditionsFactory.hasPipWindow().negate())

        /** Waits until the [SNAPSHOT] is gone */
        fun withSnapshotGone() = add(ConditionsFactory.isLayerVisible(SNAPSHOT).negate())

        /** Waits until the [SPLASH_SCREEN] is gone */
        fun withSplashScreenGone() = add(ConditionsFactory.isLayerVisible(SPLASH_SCREEN).negate())

        /** Waits until the [TRANSITION_SNAPSHOT] is gone */
        fun withTransitionSnapshotGone() =
            add(ConditionsFactory.isLayerVisible(TRANSITION_SNAPSHOT).negate())

        /** Waits until the is no top visible app window in the [WindowManagerState] */
        fun withoutTopVisibleAppWindows() =
            add("noAppWindowsOnTop") { it.wmState.topVisibleAppWindow == null }

        /** Waits until the keyguard is showing */
        fun withKeyguardShowing() = add("withKeyguardShowing") { it.wmState.isKeyguardShowing }

        /**
         * Wait for the activities to appear in proper stacks and for valid state in AM and WM.
         *
         * @param waitForActivityState array of activity states to wait for.
         */
        internal fun withValidState(vararg waitForActivityState: WaitForValidActivityState) =
            waitForValidStateCondition(*waitForActivityState)

        private fun waitForValidStateCondition(vararg waitForCondition: WaitForValidActivityState) =
            apply {
                add(ConditionsFactory.isWMStateComplete())
                if (waitForCondition.isNotEmpty()) {
                    add(
                        Condition("!shouldWaitForActivities") {
                            !shouldWaitForActivities(it, *waitForCondition)
                        }
                    )
                }
            }

        fun withFullScreenAppCondition(componentMatcher: IComponentMatcher) =
            waitForValidStateCondition(
                WaitForValidActivityState.Builder(componentMatcher)
                    .setWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
                    .setActivityType(WindowConfiguration.ACTIVITY_TYPE_STANDARD)
                    .build()
            )
    }

    companion object {
        // TODO(b/112837428): Implement a incremental retry policy to reduce the unnecessary
        // constant time, currently keep the default as 5*1s because most of the original code
        // uses it, and some tests might be sensitive to the waiting interval.
        private const val DEFAULT_RETRY_LIMIT = 20
        private const val DEFAULT_RETRY_INTERVAL_MS = 300L

        /** @return true if it should wait for some activities to become visible. */
        private fun shouldWaitForActivities(
            state: DeviceStateDump,
            vararg waitForActivitiesVisible: WaitForValidActivityState
        ): Boolean {
            if (waitForActivitiesVisible.isEmpty()) {
                return false
            }
            // If the caller is interested in waiting for some particular activity windows to be
            // visible before compute the state. Check for the visibility of those activity windows
            // and for placing them in correct stacks (if requested).
            var allActivityWindowsVisible = true
            var tasksInCorrectStacks = true
            for (activityState in waitForActivitiesVisible) {
                val matchingWindowStates =
                    state.wmState.getMatchingVisibleWindowState(
                        activityState.activityMatcher
                            ?: error("Activity name missing in $activityState")
                    )
                val activityWindowVisible = matchingWindowStates.isNotEmpty()

                if (!activityWindowVisible) {
                    Logger.i(
                        LOG_TAG,
                        "Activity window not visible: ${activityState.windowIdentifier}"
                    )
                    allActivityWindowsVisible = false
                } else if (!state.wmState.isActivityVisible(activityState.activityMatcher)) {
                    Logger.i(LOG_TAG, "Activity not visible: ${activityState.activityMatcher}")
                    allActivityWindowsVisible = false
                } else {
                    // Check if window is already the correct state requested by test.
                    var windowInCorrectState = false
                    for (ws in matchingWindowStates) {
                        if (
                            activityState.stackId != ActivityTaskManager.INVALID_STACK_ID &&
                                ws.stackId != activityState.stackId
                        ) {
                            continue
                        }
                        if (!ws.isWindowingModeCompatible(activityState.windowingMode)) {
                            continue
                        }
                        if (
                            activityState.activityType !=
                                WindowConfiguration.ACTIVITY_TYPE_UNDEFINED &&
                                ws.activityType != activityState.activityType
                        ) {
                            continue
                        }
                        windowInCorrectState = true
                        break
                    }
                    if (!windowInCorrectState) {
                        Logger.i(LOG_TAG, "Window in incorrect stack: $activityState")
                        tasksInCorrectStacks = false
                    }
                }
            }
            return !allActivityWindowsVisible || !tasksInCorrectStacks
        }
    }
}
