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

package android.tools.flicker.config

import android.tools.CrossPlatform
import android.tools.PlatformConsts.SPLIT_SCREEN_TRANSITION_HANDLER
import android.tools.flicker.extractors.ITransitionMatcher
import android.tools.flicker.extractors.TransitionsTransform
import android.tools.flicker.isAppTransitionChange
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.wm.Transition
import android.tools.traces.wm.TransitionType
import android.tools.traces.wm.TransitionType.TO_BACK
import android.tools.traces.wm.TransitionType.TO_FRONT
import android.tools.traces.wm.WmTransitionData

object TransitionFilters {
    val OPEN_APP_TRANSITION_FILTER: TransitionsTransform = { ts, _, _ ->
        ts.filter { t ->
            t.changes.any {
                it.transitMode == TransitionType.OPEN || // cold launch
                it.transitMode == TO_FRONT // warm launch
            }
        }
    }

    val CLOSE_APP_TO_LAUNCHER_FILTER: TransitionsTransform = { ts, _, reader ->
        val layersTrace = reader.readLayersTrace() ?: error("Missing layers trace")
        val layers = layersTrace.entries.flatMap { it.flattenedLayers }.distinctBy { it.id }
        val launcherLayers = layers.filter { ComponentNameMatcher.LAUNCHER.layerMatchesAnyOf(it) }

        ts.filter { t ->
            t.changes.any { it.transitMode == TransitionType.CLOSE || it.transitMode == TO_BACK } &&
                t.changes.any { change ->
                    launcherLayers.any { it.id == change.layerId }
                    change.transitMode == TO_FRONT
                }
        }
    }

    val QUICK_SWITCH_TRANSITION_FILTER: TransitionsTransform = { ts, _, reader ->
        val layersTrace = reader.readLayersTrace() ?: error("Missing layers trace")
        val wmTrace = reader.readWmTrace()

        val mergedTransitions = ts.filter { it.mergeTarget != null }
        val nonMergedTransitions =
            mutableMapOf<Int, Transition>().apply {
                ts.filter { it.mergeTarget == null }.forEach { this@apply[it.id] = it }
            }
        mergedTransitions.forEach {
            val mergedInto = it.mergeTarget ?: error("Missing merged into id!")
            val mergedTransition = nonMergedTransitions[mergedInto]?.merge(it)
            if (mergedTransition != null) {
                nonMergedTransitions[mergedInto] = mergedTransition
            }
        }

        val artificiallyMergedTransitions = nonMergedTransitions.values

        val quickswitchBetweenAppsTransitions =
            artificiallyMergedTransitions.filter { transition ->
                val openingAppLayers =
                    transition.changes.filter {
                        it.transitMode == TO_FRONT &&
                            isAppTransitionChange(it, layersTrace, wmTrace) &&
                            !isWallpaperTokenLayer(it.layerId, layersTrace) &&
                            !isLauncherTopLevelTaskLayer(it.layerId, layersTrace)
                    }
                val closingAppLayers =
                    transition.changes.filter {
                        it.transitMode == TO_BACK && isAppTransitionChange(it, layersTrace, wmTrace)
                    }

                transition.handler == TransitionHandler.RECENTS &&
                    transition.changes.count {
                        it.transitMode == TO_FRONT && isWallpaperTokenLayer(it.layerId, layersTrace)
                    } == 1 &&
                    transition.changes.count {
                        it.transitMode == TO_FRONT &&
                            isLauncherTopLevelTaskLayer(it.layerId, layersTrace)
                    } == 1 &&
                    (openingAppLayers.count() == 1 || openingAppLayers.count() == 5) &&
                    (closingAppLayers.count() == 1 || closingAppLayers.count() == 5)
            }

        var quickswitchFromLauncherTransitions =
            artificiallyMergedTransitions.filter { transition ->
                val openingAppLayers =
                    transition.changes.filter {
                        it.transitMode == TO_FRONT &&
                            isAppTransitionChange(it, layersTrace, wmTrace)
                    }

                transition.handler == TransitionHandler.DEFAULT &&
                    (openingAppLayers.count() == 1 || openingAppLayers.count() == 5) &&
                    transition.changes.count {
                        it.transitMode == TO_BACK &&
                            isLauncherTopLevelTaskLayer(it.layerId, layersTrace)
                    } == 1
            }

        // TODO: (b/300068479) temporary work around to ensure transition is associated with CUJ
        val hundredMs = CrossPlatform.timestamp.from(elapsedNanos = 100000000L)

        quickswitchFromLauncherTransitions =
            quickswitchFromLauncherTransitions.map {
                // We create the transition right about the same time we end the CUJ tag
                val createTimeAdjustedForTolerance = it.wmData.createTime?.minus(hundredMs)
                Transition(
                    id = it.id,
                    wmData =
                        it.wmData.merge(
                            WmTransitionData(
                                createTime = createTimeAdjustedForTolerance,
                                sendTime = createTimeAdjustedForTolerance
                            )
                        ),
                    shellData = it.shellData
                )
            }

        quickswitchBetweenAppsTransitions + quickswitchFromLauncherTransitions
    }

    val QUICK_SWITCH_TRANSITION_POST_PROCESSING: TransitionsTransform = { transitions, _, reader ->
        require(transitions.size == 1) { "Expected 1 transition but got ${transitions.size}" }

        val transition = transitions.first()

        val layersTrace = reader.readLayersTrace() ?: error("Missing layers trace")
        val wallpaperId =
            transition.changes
                .map { it.layerId }
                .firstOrNull { isWallpaperTokenLayer(it, layersTrace) }
        val isSwitchFromLauncher = wallpaperId == null
        val launcherId =
            if (isSwitchFromLauncher) null
            else
                transition.changes
                    .map { it.layerId }
                    .firstOrNull { isLauncherTopLevelTaskLayer(it, layersTrace) }
                    ?: error("Missing launcher layer in transition")

        val filteredChanges =
            transition.changes.filter { it.layerId != wallpaperId && it.layerId != launcherId }

        val closingAppChange = filteredChanges.first { it.transitMode == TO_BACK }
        val openingAppChange = filteredChanges.first { it.transitMode == TO_FRONT }

        // Transition removing the intermediate launcher changes
        listOf(
            Transition(
                transition.id,
                WmTransitionData(
                    createTime = transition.wmData.createTime,
                    sendTime = transition.wmData.sendTime,
                    abortTime = transition.wmData.abortTime,
                    finishTime = transition.wmData.finishTime,
                    startingWindowRemoveTime = transition.wmData.startingWindowRemoveTime,
                    startTransactionId = transition.wmData.startTransactionId,
                    finishTransactionId = transition.wmData.finishTransactionId,
                    type = transition.wmData.type,
                    changes = listOf(closingAppChange, openingAppChange),
                ),
                transition.shellData
            )
        )
    }

    private fun isLauncherTopLevelTaskLayer(layerId: Int, layersTrace: LayersTrace): Boolean {
        return layersTrace.entries.any { entry ->
            val launcherLayer =
                entry.flattenedLayers.firstOrNull { layer ->
                    ComponentNameMatcher.LAUNCHER.or(ComponentNameMatcher.AOSP_LAUNCHER)
                        .layerMatchesAnyOf(layer)
                }
                    ?: return@any false

            var curLayer = launcherLayer
            while (!curLayer.isTask && curLayer.parent != null) {
                curLayer = curLayer.parent ?: error("unreachable")
            }
            if (!curLayer.isTask) {
                error("Expected a task layer above the launcher layer")
            }

            var launcherTopLevelTaskLayer = curLayer
            // Might have nested task layers
            while (
                launcherTopLevelTaskLayer.parent != null &&
                    launcherTopLevelTaskLayer.parent!!.isTask
            ) {
                launcherTopLevelTaskLayer = launcherTopLevelTaskLayer.parent ?: error("unreachable")
            }

            return@any launcherTopLevelTaskLayer.id == layerId
        }
    }

    private fun isWallpaperTokenLayer(layerId: Int, layersTrace: LayersTrace): Boolean {
        return layersTrace.entries.any { entry ->
            entry.flattenedLayers.any { layer ->
                layer.id == layerId &&
                    ComponentNameMatcher.WALLPAPER_WINDOW_TOKEN.layerMatchesAnyOf(layer)
            }
        }
    }

    val APP_CLOSE_TO_PIP_TRANSITION_FILTER: TransitionsTransform = { ts, _, _ ->
        ts.filter { it.type == TransitionType.PIP }
    }

    val ENTER_SPLIT_SCREEN_MATCHER =
        object : ITransitionMatcher {
            override fun findAll(transitions: Collection<Transition>): Collection<Transition> {
                return transitions.filter { isSplitscreenEnterTransition(it) }
            }
        }

    val EXIT_SPLIT_SCREEN_FILTER: TransitionsTransform = { ts, _, _ ->
        ts.filter { isSplitscreenExitTransition(it) }
    }

    val RESIZE_SPLIT_SCREEN_FILTER: TransitionsTransform = { ts, _, _ ->
        ts.filter { isSplitscreenResizeTransition(it) }
    }

    fun isSplitscreenEnterTransition(transition: Transition): Boolean {
        return transition.handler == SPLIT_SCREEN_TRANSITION_HANDLER && transition.type == TO_FRONT
    }

    fun isSplitscreenExitTransition(transition: Transition): Boolean {
        return transition.type == TransitionType.SPLIT_DISMISS ||
            transition.type == TransitionType.SPLIT_DISMISS_SNAP
    }

    fun isSplitscreenResizeTransition(transition: Transition): Boolean {
        // This transition doesn't have a special type
        return transition.type == TransitionType.CHANGE &&
            transition.changes.size == 2 &&
            transition.changes.all { change -> change.transitMode == TransitionType.CHANGE }
    }
}
