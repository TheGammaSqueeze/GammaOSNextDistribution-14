/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */

package com.android.wallpaper.picker.undo.shared.model

/** Models a snapshot of the state of an undo-supporting feature at a given time. */
data class RestorableSnapshot(
    val args: Map<String, String>,
) {
    /**
     * Returns a copy of the [RestorableSnapshot] but with the [block] applied to its arguments.
     *
     * Sample usage:
     * ```
     * val previousSnapshot: RestorableSnapshot = ...
     * val nextSnapshot = previousSnapshot { args ->
     *     args.put("one", "true")
     *     args.remove("two")
     * }
     *
     * // Now, nextSnapshot is exactly like previousSnapshot but with its args having "one" mapped
     * // to "true" and without "two", since it was removed.
     * ```
     *
     * @param block A function that receives the original [args] from the current
     *   [RestorableSnapshot] and can edit them for inclusion into the returned
     *   [RestorableSnapshot].
     */
    fun copy(
        block: (MutableMap<String, String>) -> Unit,
    ): RestorableSnapshot {
        val mutableArgs = args.toMutableMap()
        block(mutableArgs)
        return RestorableSnapshot(
            args = mutableArgs.toMap(),
        )
    }
}
