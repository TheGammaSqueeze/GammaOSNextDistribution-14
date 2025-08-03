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

package android.tools.traces.component

import android.tools.traces.surfaceflinger.Layer
import android.tools.traces.wm.Activity
import android.tools.traces.wm.WindowContainer

/**
 * A component matcher that matches the targeted window and layer with ids windowId and layerId
 * respectively and all their children windows and layers.
 *
 * If you want to only match the window and layer with the specified ids then use
 * ExactComponentIdMatcher instead.
 */
class FullComponentIdMatcher(val windowId: Int, val layerId: Int) : IComponentMatcher {
    /**
     * @param windows to search
     * @return if any of the components matches any of [windows]
     */
    override fun windowMatchesAnyOf(windows: Collection<WindowContainer>): Boolean =
        windows.any {
            val parent = it.parent
            when {
                it.token == windowId.toString(16) -> true
                parent != null -> windowMatchesAnyOf(parent)
                else -> false
            }
        }

    /**
     * @param activities to search
     * @return if any of the components matches any of [activities]
     */
    override fun activityMatchesAnyOf(activities: Collection<Activity>) =
        activities.any { it.token == windowId.toString(16) }

    /**
     * @param layers to search
     * @return if any of the components matches any of [layers]
     */
    override fun layerMatchesAnyOf(layers: Collection<Layer>) =
        layers.any {
            val parent = it.parent
            when {
                it.id == layerId -> true
                parent != null -> layerMatchesAnyOf(parent)
                else -> false
            }
        }

    /** {@inheritDoc} */
    override fun check(
        layers: Collection<Layer>,
        condition: (Collection<Layer>) -> Boolean
    ): Boolean = condition(layers.filter { layerMatchesAnyOf(it) })

    /** {@inheritDoc} */
    override fun toActivityIdentifier() = toWindowIdentifier()

    /** {@inheritDoc} */
    override fun toWindowIdentifier() = "Window#${windowId.toString(16)} & children"

    /** {@inheritDoc} */
    override fun toLayerIdentifier(): String = "Layer#$layerId & children"
}
