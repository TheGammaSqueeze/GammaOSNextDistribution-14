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

package com.android.tools.metalava.reporter

import java.nio.file.Path

/** Identifies a specific line within an input file. */
abstract class FileLocation {
    /** The absolute path to the location, or `null` if it could not be found. */
    abstract val path: Path?

    /** The line number, may be non-positive indicating that it could not be found. */
    abstract val line: Int

    /** Append the string representation of this to the [builder]. */
    fun appendTo(builder: StringBuilder) {
        builder.append(path)
        if (line > 0) builder.append(":").append(line)
    }

    override fun toString() = if (line < 1) path.toString() else "$path:$line"

    /** A fixed location, known at construction time. */
    private class FixedFileLocation(
        override val path: Path?,
        override val line: Int = 0,
    ) : FileLocation()

    companion object {
        /** The unknown location. */
        val UNKNOWN: FileLocation = FixedFileLocation(null, 0)

        /** Create a [FileLocation] for a [path] and optional [line] number. */
        fun createLocation(path: Path, line: Int = 0): FileLocation = FixedFileLocation(path, line)
    }
}
