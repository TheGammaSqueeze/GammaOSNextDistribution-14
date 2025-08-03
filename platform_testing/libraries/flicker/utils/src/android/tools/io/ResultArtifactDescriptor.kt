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

package android.tools.io

import android.tools.Tag

/** Descriptor for files inside flicker result artifacts */
class ResultArtifactDescriptor(
    /** Trace or dump type */
    val traceType: TraceType,
    /** If the trace/dump is associated with a tag */
    val tag: String = Tag.ALL
) {
    private val isTagTrace: Boolean
        get() = tag != Tag.ALL

    /** Name of the trace file in the result artifact (e.g. zip) */
    val fileNameInArtifact: String = buildString {
        if (isTagTrace) {
            append(tag)
            append("__")
        }
        append(traceType.fileName)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResultArtifactDescriptor) return false

        if (traceType != other.traceType) return false
        if (tag != other.tag) return false

        return true
    }

    override fun hashCode(): Int {
        var result = traceType.hashCode()
        result = 31 * result + tag.hashCode()
        return result
    }

    override fun toString(): String = fileNameInArtifact
}
