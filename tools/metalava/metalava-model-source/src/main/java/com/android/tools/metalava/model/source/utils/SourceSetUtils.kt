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

package com.android.tools.metalava.model.source.utils

import com.android.tools.lint.checks.infrastructure.ClassName
import java.io.File

const val PACKAGE_HTML = "package.html"
const val OVERVIEW_HTML = "overview.html"
const val DOT_JAVA = ".java"
const val DOT_KT = ".kt"

/** Finds the package of the given Java/Kotlin source file, if possible */
fun findPackage(file: File): String? {
    val source = file.readText(Charsets.UTF_8)
    return findPackage(source)
}

/** Finds the package of the given Java/Kotlin source code, if possible */
private fun findPackage(source: String): String? {
    return ClassName(source).packageName
}
