/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.tree.Tree
import java.nio.file.Path

/**
 * A [FileLocation] that stores the [position] of the declaration in the [TurbineSourceFile] and
 * uses that to generate the [path] and [line] as needed.
 */
internal class TurbineFileLocation(
    /**
     * The [TurbineSourceFile] that contains the information needed to map the [position] to a line
     * number.
     */
    private val sourceFile: TurbineSourceFile,
    /** The position within the [sourceFile]. */
    private val position: Int
) : FileLocation() {

    override val path
        get() = sourceFile.path

    override val line
        get() = sourceFile.lineForPosition(position)

    companion object {
        /** Get the [Path] for the [TurbineSourceFile]. */
        private val TurbineSourceFile.path: Path
            get() = Path.of(compUnit.source().path())

        /** Create a [FileLocation] for the [sourceFile]. */
        fun forTree(sourceFile: TurbineSourceFile?): FileLocation {
            sourceFile ?: return UNKNOWN
            return createLocation(sourceFile.path)
        }

        /** Create a [FileLocation] for the position of [tree] inside the [sourceFile]. */
        fun forTree(sourceFile: TurbineSourceFile, tree: Tree?): FileLocation {
            tree ?: return forTree(sourceFile)
            return TurbineFileLocation(sourceFile, tree.position())
        }

        /**
         * Create a [FileLocation] for the position of [tree] inside the nested [classItem]'s
         * [TurbineSourceFile].
         */
        fun forTree(classItem: ClassItem, tree: Tree?): FileLocation {
            // Can only access the [TurbineSourceFile] from the outermost [ClassItem].
            var outermost = classItem
            while (true) {
                val containingClass = outermost.containingClass() ?: break
                outermost = containingClass
            }

            val sourceFile = outermost.sourceFile() as? TurbineSourceFile ?: return UNKNOWN
            return forTree(sourceFile, tree)
        }
    }
}
