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

object Utils {
    internal fun WindowContainer.traverseTopDown(): List<WindowContainer> {
        val traverseList = mutableListOf(this)

        this.children.reversed().forEach { childLayer ->
            traverseList.addAll(childLayer.traverseTopDown())
        }

        return traverseList
    }

    /**
     * For a given WindowContainer, traverse down the hierarchy and collect all children of type [T]
     * if the child passes the test [predicate].
     *
     * @param predicate Filter function
     */
    internal inline fun <reified T : WindowContainer> WindowContainer.collectDescendants(
        predicate: (T) -> Boolean = { true }
    ): Collection<T> {
        val traverseList = traverseTopDown()

        return traverseList.filterIsInstance<T>().filter { predicate(it) }
    }
}
