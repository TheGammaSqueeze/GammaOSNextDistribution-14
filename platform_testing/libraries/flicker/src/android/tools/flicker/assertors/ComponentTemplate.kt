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

package android.tools.flicker.assertors

import android.tools.flicker.ScenarioInstance
import android.tools.traces.component.IComponentMatcher

data class ComponentTemplate(
    val name: String,
    val build: (scenarioInstance: ScenarioInstance) -> IComponentMatcher
) {
    override fun equals(other: Any?): Boolean {
        return other is ComponentTemplate && name == other.name && build == other.build
    }

    override fun hashCode(): Int {
        return name.hashCode() * 39 + build.hashCode()
    }
}
