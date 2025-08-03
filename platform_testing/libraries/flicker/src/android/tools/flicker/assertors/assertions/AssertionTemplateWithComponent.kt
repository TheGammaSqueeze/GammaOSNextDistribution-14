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

package android.tools.flicker.assertors.assertions

import android.tools.flicker.assertors.AssertionTemplate
import android.tools.flicker.assertors.ComponentTemplate

/** Base class for tests that require a [components] named window name */
abstract class AssertionTemplateWithComponent(vararg val components: ComponentTemplate) :
    AssertionTemplate() {

    override val name = "${super.name}(${components.joinToString { it.name }})"

    override fun equals(other: Any?): Boolean {
        if (other !is AssertionTemplateWithComponent) {
            return false
        }

        // Check both assertions are instances of the same class.
        if (this::class != other::class) {
            return false
        }

        if (components.size != other.components.size) {
            return false
        }

        // TODO: Make sure equality is properly defined on the component
        // Order of components matters
        return components.zip(other.components).all { it.first == it.second }
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + components.hashCode()
        return result
    }
}
