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

package android.tools.utils

import android.tools.io.Artifact
import android.tools.io.ResultArtifactDescriptor
import android.tools.io.RunStatus

class TestArtifact(artifactIdentifier: String) : Artifact {

    override val absolutePath = "IN_MEMORY/$artifactIdentifier"
    override val stableId = artifactIdentifier
    override val fileName = absolutePath
    override val runStatus = RunStatus.UNDEFINED

    override fun deleteIfExists() {
        // No op
    }

    override fun traceCount(): Int = 0

    override fun hasTrace(descriptor: ResultArtifactDescriptor): Boolean = false

    override fun readBytes(descriptor: ResultArtifactDescriptor): ByteArray? = null

    override fun updateStatus(newStatus: RunStatus) {
        // No op
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestArtifact) return false

        if (absolutePath != other.absolutePath) return false

        return true
    }

    override fun hashCode(): Int {
        return absolutePath.hashCode()
    }

    companion object {
        val EMPTY = TestArtifact("Empty")
    }
}
