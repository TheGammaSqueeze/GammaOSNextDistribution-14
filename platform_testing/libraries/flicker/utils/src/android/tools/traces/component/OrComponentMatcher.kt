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

class OrComponentMatcher(private val componentMatchers: Collection<IComponentMatcher>) :
    IComponentMatcher {

    /** {@inheritDoc} */
    override fun windowMatchesAnyOf(window: WindowContainer): Boolean {
        return componentMatchers.any { it.windowMatchesAnyOf(window) }
    }

    /** {@inheritDoc} */
    override fun windowMatchesAnyOf(windows: Collection<WindowContainer>): Boolean {
        return componentMatchers.any { it.windowMatchesAnyOf(windows) }
    }

    /** {@inheritDoc} */
    override fun activityMatchesAnyOf(activity: Activity): Boolean {
        return componentMatchers.any { it.activityMatchesAnyOf(activity) }
    }

    /** {@inheritDoc} */
    override fun activityMatchesAnyOf(activities: Collection<Activity>): Boolean {
        return componentMatchers.any { it.activityMatchesAnyOf(activities) }
    }

    /** {@inheritDoc} */
    override fun layerMatchesAnyOf(layer: Layer): Boolean {
        return componentMatchers.any { it.layerMatchesAnyOf(layer) }
    }

    /** {@inheritDoc} */
    override fun layerMatchesAnyOf(layers: Collection<Layer>): Boolean {
        return componentMatchers.any { it.layerMatchesAnyOf(layers) }
    }

    /** {@inheritDoc} */
    override fun check(
        layers: Collection<Layer>,
        condition: (Collection<Layer>) -> Boolean
    ): Boolean {
        return componentMatchers.any { oredComponent ->
            oredComponent.check(layers) { condition(it) }
        }
    }

    /** {@inheritDoc} */
    override fun toActivityIdentifier(): String =
        componentMatchers.joinToString(" or ") { it.toActivityIdentifier() }

    /** {@inheritDoc} */
    override fun toWindowIdentifier(): String =
        componentMatchers.joinToString(" or ") { it.toWindowIdentifier() }

    /** {@inheritDoc} */
    override fun toLayerIdentifier(): String =
        componentMatchers.joinToString(" or ") { it.toLayerIdentifier() }
}
