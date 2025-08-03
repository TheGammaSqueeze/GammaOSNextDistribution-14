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

class ComponentSplashScreenMatcher(val componentNameMatcher: IComponentNameMatcher) :
    IComponentMatcher {
    override fun windowMatchesAnyOf(windows: Collection<WindowContainer>): Boolean {
        error("Unimplemented - There are no splashscreen windows")
    }

    override fun activityMatchesAnyOf(activities: Collection<Activity>): Boolean {
        error("Unimplemented - There are no splashscreen windows")
    }

    override fun layerMatchesAnyOf(layers: Collection<Layer>): Boolean {
        return layers.any {
            if (!it.name.contains("Splash Screen")) {
                return@any false
            }
            if (it.children.isNotEmpty()) {
                // Not leaf splash screen layer but container of the splash screen layer
                return@any false
            }

            var ancestor: Layer? = it.parent?.parent
            requireNotNull(ancestor) { "Splash screen layer's grandparent shouldn't be null" }

            var hasActivityRecord = componentNameMatcher.activityRecordMatchesAnyOf(ancestor)
            var count = 0
            while (!hasActivityRecord && ancestor != null && count++ < 5) {
                ancestor = ancestor.parent
                hasActivityRecord =
                    ancestor != null && componentNameMatcher.activityRecordMatchesAnyOf(ancestor)
            }
            return@any hasActivityRecord
        }
    }

    override fun toActivityIdentifier(): String {
        error("Unimplemented - There are no splashscreen windows")
    }

    override fun toWindowIdentifier(): String {
        error("Unimplemented - There are no splashscreen windows")
    }

    override fun toLayerIdentifier(): String {
        return "Splash Screen ${componentNameMatcher.className}"
    }

    override fun check(
        layers: Collection<Layer>,
        condition: (Collection<Layer>) -> Boolean
    ): Boolean {
        val splashScreenLayer = layers.filter { layerMatchesAnyOf(it) }
        require(splashScreenLayer.size <= 1) {
            "More than on SplashScreen layer found. Only up to 1 match was expected."
        }
        return condition(splashScreenLayer)
    }
}
