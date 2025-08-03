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
 */
package com.android.tools.metalava.apilevels

import com.android.SdkConstants
import com.android.SdkConstants.PLATFORM_WINDOWS
import java.io.File

class ExtensionSdkJarReader() {

    companion object {
        private val REGEX_JAR_PATH = run {
            var pattern = ".*/(\\d+)/public/(.*)\\.jar$"
            if (SdkConstants.currentPlatform() == PLATFORM_WINDOWS) {
                pattern = pattern.replace("/", "\\\\")
            }
            Regex(pattern)
        }

        /**
         * Find extension SDK jar files in an extension SDK tree.
         *
         * @return a mapping SDK jar file -> list of VersionAndPath objects, sorted from earliest to
         *   last version
         */
        fun findExtensionSdkJarFiles(
            root: File,
            skipVersionsGreaterThan: Int?
        ): Map<String, List<VersionAndPath>> {
            val map = mutableMapOf<String, MutableList<VersionAndPath>>()
            root
                .walk()
                .maxDepth(3)
                .mapNotNull { file ->
                    REGEX_JAR_PATH.matchEntire(file.path)?.groups?.let { groups ->
                        Triple(groups[2]!!.value, groups[1]!!.value.toInt(), file)
                    }
                }
                .filter {
                    if (skipVersionsGreaterThan != null) {
                        it.second <= skipVersionsGreaterThan
                    } else {
                        true
                    }
                }
                .sortedBy { it.second }
                .forEach {
                    map.getOrPut(it.first) { mutableListOf() }
                        .add(VersionAndPath(it.second, it.third))
                }
            return map
        }
    }
}

data class VersionAndPath(@JvmField val version: Int, @JvmField val path: File)
