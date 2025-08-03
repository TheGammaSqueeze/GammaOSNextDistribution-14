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

package com.android.tools.metalava.testing

import java.io.File

private const val DOT_KT = ".kt"

/** A slight modification of com.android.tools.lint.checks.infrastructure.findKotlinStdLibPath. */
fun findKotlinStdlibPaths(sources: Array<String>): List<File> {
    val classPath: String = System.getProperty("java.class.path")
    val paths = mutableListOf<File>()
    for (path in classPath.split(':')) {
        val file = File(path)
        val name = file.name
        if (name.startsWith("kotlin-stdlib") || name.startsWith("kotlin-script-runtime")) {
            paths.add(file)
        }
    }
    if (paths.isEmpty()) {
        error("Did not find kotlin-stdlib-jre8 in classpath: $classPath")
    }
    return if (sources.asSequence().any { it.endsWith(DOT_KT) }) {
        paths
    } else {
        emptyList()
    }
}
