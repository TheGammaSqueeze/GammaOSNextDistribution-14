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

package android.tools.traces

import android.tools.PlatformConsts
import android.tools.Rotation
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.surfaceflinger.Layer
import android.tools.traces.surfaceflinger.Transform
import android.tools.traces.surfaceflinger.Transform.Companion.isFlagSet
import android.tools.traces.wm.WindowManagerState
import android.tools.traces.wm.WindowState

object ConditionsFactory {
    private fun getNavBarComponent(wmState: WindowManagerState) =
        if (wmState.isTablet) ComponentNameMatcher.TASK_BAR else ComponentNameMatcher.NAV_BAR

    /**
     * Condition to check if the [ComponentNameMatcher.NAV_BAR] or [ComponentNameMatcher.TASK_BAR]
     * windows are visible
     */
    fun isNavOrTaskBarVisible(): Condition<DeviceStateDump> =
        ConditionList(
            listOf(
                isNavOrTaskBarWindowVisible(),
                isNavOrTaskBarLayerVisible(),
                isNavOrTaskBarLayerOpaque()
            )
        )

    /**
     * Condition to check if the [ComponentNameMatcher.NAV_BAR] or [ComponentNameMatcher.TASK_BAR]
     * windows are visible
     */
    fun isNavOrTaskBarWindowVisible(): Condition<DeviceStateDump> =
        Condition("isNavBarOrTaskBarWindowVisible") {
            val component = getNavBarComponent(it.wmState)
            it.wmState.isWindowSurfaceShown(component)
        }

    /**
     * Condition to check if the [ComponentNameMatcher.NAV_BAR] or [ComponentNameMatcher.TASK_BAR]
     * layers are visible
     */
    fun isNavOrTaskBarLayerVisible(): Condition<DeviceStateDump> =
        Condition("isNavBarOrTaskBarLayerVisible") {
            val component = getNavBarComponent(it.wmState)
            it.layerState.isVisible(component)
        }

    /** Condition to check if the [ComponentNameMatcher.NAV_BAR] layer is opaque */
    fun isNavOrTaskBarLayerOpaque(): Condition<DeviceStateDump> =
        Condition("isNavOrTaskBarLayerOpaque") {
            val component = getNavBarComponent(it.wmState)
            it.layerState.getLayerWithBuffer(component)?.color?.isOpaque ?: false
        }

    /** Condition to check if the [ComponentNameMatcher.NAV_BAR] window is visible */
    fun isNavBarVisible(): Condition<DeviceStateDump> =
        ConditionList(
            listOf(isNavBarWindowVisible(), isNavBarLayerVisible(), isNavBarLayerOpaque())
        )

    /** Condition to check if the [ComponentNameMatcher.NAV_BAR] window is visible */
    fun isNavBarWindowVisible(): Condition<DeviceStateDump> =
        Condition("isNavBarWindowVisible") {
            it.wmState.isWindowSurfaceShown(ComponentNameMatcher.NAV_BAR)
        }

    /** Condition to check if the [ComponentNameMatcher.NAV_BAR] layer is visible */
    fun isNavBarLayerVisible(): Condition<DeviceStateDump> =
        isLayerVisible(ComponentNameMatcher.NAV_BAR)

    /** Condition to check if the [ComponentNameMatcher.NAV_BAR] layer is opaque */
    fun isNavBarLayerOpaque(): Condition<DeviceStateDump> =
        Condition("isNavBarLayerOpaque") {
            it.layerState.getLayerWithBuffer(ComponentNameMatcher.NAV_BAR)?.color?.isOpaque ?: false
        }

    /** Condition to check if the [ComponentNameMatcher.TASK_BAR] window is visible */
    fun isTaskBarVisible(): Condition<DeviceStateDump> =
        ConditionList(
            listOf(isTaskBarWindowVisible(), isTaskBarLayerVisible(), isTaskBarLayerOpaque())
        )

    /** Condition to check if the [ComponentNameMatcher.TASK_BAR] window is visible */
    fun isTaskBarWindowVisible(): Condition<DeviceStateDump> =
        Condition("isTaskBarWindowVisible") {
            it.wmState.isWindowSurfaceShown(ComponentNameMatcher.TASK_BAR)
        }

    /** Condition to check if the [ComponentNameMatcher.TASK_BAR] layer is visible */
    fun isTaskBarLayerVisible(): Condition<DeviceStateDump> =
        isLayerVisible(ComponentNameMatcher.TASK_BAR)

    /** Condition to check if the [ComponentNameMatcher.TASK_BAR] layer is opaque */
    fun isTaskBarLayerOpaque(): Condition<DeviceStateDump> =
        Condition("isTaskBarLayerOpaque") {
            it.layerState.getLayerWithBuffer(ComponentNameMatcher.TASK_BAR)?.color?.isOpaque
                ?: false
        }

    /** Condition to check if the [ComponentNameMatcher.STATUS_BAR] window is visible */
    fun isStatusBarVisible(): Condition<DeviceStateDump> =
        ConditionList(
            listOf(isStatusBarWindowVisible(), isStatusBarLayerVisible(), isStatusBarLayerOpaque())
        )

    /** Condition to check if the [ComponentNameMatcher.STATUS_BAR] window is visible */
    fun isStatusBarWindowVisible(): Condition<DeviceStateDump> =
        Condition("isStatusBarWindowVisible") {
            it.wmState.isWindowSurfaceShown(ComponentNameMatcher.STATUS_BAR)
        }

    /** Condition to check if the [ComponentNameMatcher.STATUS_BAR] layer is visible */
    fun isStatusBarLayerVisible(): Condition<DeviceStateDump> =
        isLayerVisible(ComponentNameMatcher.STATUS_BAR)

    /** Condition to check if the [ComponentNameMatcher.STATUS_BAR] layer is opaque */
    fun isStatusBarLayerOpaque(): Condition<DeviceStateDump> =
        Condition("isStatusBarLayerOpaque") {
            it.layerState.getLayerWithBuffer(ComponentNameMatcher.STATUS_BAR)?.color?.isOpaque
                ?: false
        }

    fun isHomeActivityVisible(): Condition<DeviceStateDump> =
        Condition("isHomeActivityVisible") { it.wmState.isHomeActivityVisible }

    fun isRecentsActivityVisible(): Condition<DeviceStateDump> =
        Condition("isRecentsActivityVisible") {
            it.wmState.isHomeActivityVisible || it.wmState.isRecentsActivityVisible
        }

    fun isLauncherLayerVisible(): Condition<DeviceStateDump> =
        Condition("isLauncherLayerVisible") {
            it.layerState.isVisible(ComponentNameMatcher.LAUNCHER) ||
                it.layerState.isVisible(ComponentNameMatcher.AOSP_LAUNCHER)
        }

    /**
     * Condition to check if WM app transition is idle
     *
     * Because in shell transitions, active recents animation is running transition (never idle)
     * this method always assumed recents are idle
     */
    fun isAppTransitionIdle(displayId: Int): Condition<DeviceStateDump> =
        Condition("isAppTransitionIdle[$displayId]") {
            (it.wmState.isHomeRecentsComponent && it.wmState.isHomeActivityVisible) ||
                it.wmState.isRecentsActivityVisible ||
                it.wmState.getDisplay(displayId)?.appTransitionState ==
                    PlatformConsts.APP_STATE_IDLE
        }

    fun containsActivity(componentMatcher: IComponentMatcher): Condition<DeviceStateDump> =
        Condition("containsActivity[${componentMatcher.toActivityIdentifier()}]") {
            it.wmState.containsActivity(componentMatcher)
        }

    fun containsWindow(componentMatcher: IComponentMatcher): Condition<DeviceStateDump> =
        Condition("containsWindow[${componentMatcher.toWindowIdentifier()}]") {
            it.wmState.containsWindow(componentMatcher)
        }

    fun isWindowSurfaceShown(componentMatcher: IComponentMatcher): Condition<DeviceStateDump> =
        Condition("isWindowSurfaceShown[${componentMatcher.toWindowIdentifier()}]") {
            it.wmState.isWindowSurfaceShown(componentMatcher)
        }

    fun isActivityVisible(componentMatcher: IComponentMatcher): Condition<DeviceStateDump> =
        Condition("isActivityVisible[${componentMatcher.toActivityIdentifier()}]") {
            it.wmState.isActivityVisible(componentMatcher)
        }

    fun isWMStateComplete(): Condition<DeviceStateDump> =
        Condition("isWMStateComplete") { it.wmState.isComplete() }

    fun hasRotation(expectedRotation: Rotation, displayId: Int): Condition<DeviceStateDump> {
        val hasRotationCondition =
            Condition<DeviceStateDump>("hasRotation[$expectedRotation, display=$displayId]") {
                val currRotation = it.wmState.getRotation(displayId)
                currRotation == expectedRotation
            }
        return ConditionList(
            listOf(
                hasRotationCondition,
                isLayerVisible(ComponentNameMatcher.ROTATION).negate(),
                isLayerVisible(ComponentNameMatcher.BACK_SURFACE).negate(),
                hasLayersAnimating().negate()
            )
        )
    }

    fun isWindowVisible(
        componentMatcher: IComponentMatcher,
        displayId: Int = 0
    ): Condition<DeviceStateDump> =
        ConditionList(
            containsActivity(componentMatcher),
            containsWindow(componentMatcher),
            isActivityVisible(componentMatcher),
            isWindowSurfaceShown(componentMatcher),
            isAppTransitionIdle(displayId)
        )

    fun isLayerVisible(componentMatcher: IComponentMatcher): Condition<DeviceStateDump> =
        Condition("isLayerVisible[${componentMatcher.toLayerIdentifier()}]") {
            it.layerState.isVisible(componentMatcher)
        }

    fun isLayerVisible(layerId: Int): Condition<DeviceStateDump> =
        Condition("isLayerVisible[layerId=$layerId]") {
            it.layerState.getLayerById(layerId)?.isVisible ?: false
        }

    /** Condition to check if the given layer is opaque */
    fun isLayerOpaque(componentMatcher: IComponentMatcher): Condition<DeviceStateDump> =
        Condition("isLayerOpaque[${componentMatcher.toLayerIdentifier()}]") {
            it.layerState.getLayerWithBuffer(componentMatcher)?.color?.isOpaque ?: false
        }

    fun isLayerColorAlphaOne(componentMatcher: IComponentMatcher): Condition<DeviceStateDump> =
        Condition("isLayerColorAlphaOne[${componentMatcher.toLayerIdentifier()}]") {
            it.layerState.visibleLayers
                .filter { layer -> componentMatcher.layerMatchesAnyOf(layer) }
                .any { layer -> layer.color.isOpaque }
        }

    fun isLayerColorAlphaOne(layerId: Int): Condition<DeviceStateDump> =
        Condition("isLayerColorAlphaOne[$layerId]") {
            val layer = it.layerState.getLayerById(layerId)
            layer?.color?.a == 1.0f
        }

    fun isLayerTransformFlagSet(
        componentMatcher: IComponentMatcher,
        transform: Int
    ): Condition<DeviceStateDump> =
        Condition(
            "isLayerTransformFlagSet[" +
                "${componentMatcher.toLayerIdentifier()}," +
                "transform=$transform]"
        ) {
            it.layerState.visibleLayers
                .filter { layer -> componentMatcher.layerMatchesAnyOf(layer) }
                .any { layer -> isTransformFlagSet(layer, transform) }
        }

    fun isLayerTransformFlagSet(layerId: Int, transform: Int): Condition<DeviceStateDump> =
        Condition("isLayerTransformFlagSet[$layerId, $transform]") {
            val layer = it.layerState.getLayerById(layerId)
            layer?.transform?.type?.isFlagSet(transform) ?: false
        }

    fun isLayerTransformIdentity(layerId: Int): Condition<DeviceStateDump> =
        ConditionList(
            listOf(
                isLayerTransformFlagSet(layerId, Transform.SCALE_VAL).negate(),
                isLayerTransformFlagSet(layerId, Transform.TRANSLATE_VAL).negate(),
                isLayerTransformFlagSet(layerId, Transform.ROTATE_VAL).negate()
            )
        )

    private fun isTransformFlagSet(layer: Layer, transform: Int): Boolean =
        layer.transform.type?.isFlagSet(transform) ?: false

    fun hasLayersAnimating(): Condition<DeviceStateDump> {
        var prevState: DeviceStateDump? = null
        return ConditionList(
            Condition("hasLayersAnimating") {
                val result = it.layerState.isAnimating(prevState?.layerState)
                prevState = it
                result
            },
            isLayerVisible(ComponentNameMatcher.SNAPSHOT).negate(),
            isLayerVisible(ComponentNameMatcher.SPLASH_SCREEN).negate()
        )
    }

    fun isPipWindowLayerSizeMatch(layerId: Int): Condition<DeviceStateDump> =
        Condition("isPipWindowLayerSizeMatch[layerId=$layerId]") {
            val pipWindow =
                it.wmState.pinnedWindows.firstOrNull { pinnedWindow ->
                    pinnedWindow.layerId == layerId
                }
                    ?: error("Unable to find window with layerId $layerId")
            val windowHeight = pipWindow.frame.height.toFloat()
            val windowWidth = pipWindow.frame.width.toFloat()

            val pipLayer = it.layerState.getLayerById(layerId)
            val layerHeight =
                pipLayer?.screenBounds?.height ?: error("Unable to find layer with id $layerId")
            val layerWidth = pipLayer.screenBounds.width

            windowHeight == layerHeight && windowWidth == layerWidth
        }

    fun hasPipWindow(): Condition<DeviceStateDump> =
        Condition("hasPipWindow") { it.wmState.hasPipWindow() }

    fun isImeShown(displayId: Int): Condition<DeviceStateDump> =
        ConditionList(
            listOf(
                isImeOnDisplay(displayId),
                isLayerVisible(ComponentNameMatcher.IME),
                isLayerOpaque(ComponentNameMatcher.IME),
                isImeSurfaceShown(),
                isWindowSurfaceShown(ComponentNameMatcher.IME)
            )
        )

    private fun isImeOnDisplay(displayId: Int): Condition<DeviceStateDump> =
        Condition("isImeOnDisplay[$displayId]") {
            it.wmState.inputMethodWindowState?.displayId == displayId
        }

    private fun isImeSurfaceShown(): Condition<DeviceStateDump> =
        Condition("isImeSurfaceShown") {
            it.wmState.inputMethodWindowState?.isSurfaceShown == true &&
                it.wmState.inputMethodWindowState?.isVisible == true
        }

    fun isAppLaunchEnded(taskId: Int): Condition<DeviceStateDump> =
        Condition("containsVisibleAppLaunchWindow[taskId=$taskId]") { dump ->
            val windowStates =
                dump.wmState.getRootTask(taskId)?.activities?.flatMap {
                    it.children.filterIsInstance<WindowState>()
                }
            windowStates != null &&
                windowStates.none {
                    it.attributes.type == PlatformConsts.TYPE_APPLICATION_STARTING && it.isVisible
                }
        }
}
